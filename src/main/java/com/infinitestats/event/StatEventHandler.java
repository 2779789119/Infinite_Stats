package com.infinitestats.event;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.infinitestats.Config;
import com.infinitestats.InfiniteStats;
import com.infinitestats.handler.*;
import com.infinitestats.network.NetworkHandler;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import com.infinitestats.stats.StatType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.TierSortingRegistry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 主事件处理器 - 整合所有属性效果
 * 使用HandlerRegistry统一调度各个专用处理器
 */
@Mod.EventBusSubscriber(modid = InfiniteStats.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StatEventHandler {

    /**
     * 重入锁：攻击分支对次级受击（范围/真实/降上限直接伤害）会同步再次进入本方法，
     * 若此时再执行攻击逻辑则形成同步无限递归（StackOverflow → 被 log4j 类加载冲突放大为服务端崩溃）。
     * 用 ThreadLocal 锁保证"一次攻击链"中攻击分支只执行一次，与 direct_damage 伤害类型是否解析无关，
     * 即便 applyDirectDamage 回退为普通玩家攻击也能彻底杜绝递归。
     */
    private static final ThreadLocal<Boolean> ATTACK_BRANCH_ACTIVE = ThreadLocal.withInitial(() -> false);

    /**
     * 「无箭装填」发射窗口：玩家 UUID → 发射当时的 gameTime。
     * <p>
     * 弩的弹丸存在 {@code ChargedProjectiles} 里，模组补进去的虚拟箭与玩家自己装的实弹
     * 在 NBT 上无法区分；但「发射那一刻背包里一支箭都没有」就足以说明这次射出的必然是虚拟箭
     * （有实弹时原版根本不需要模组插手）。{@link #onArrowLoose} 记下这一时刻，
     * {@link #onEntityJoinLevel} 在实体生成时据此收紧拾取权限，避免落地的箭被捡回形成刷箭。
     * 值带 gameTime，查询时比对当前时间，记录过期即自动失效。
     */
    private static final Map<UUID, Long> VIRTUAL_CROSSBOW_SHOTS = new ConcurrentHashMap<>();

    // ========== 死亡事件 ==========

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        // 自动复活
        if (event.getEntity() instanceof ServerPlayer player) {
            player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
                if (DefenseHandler.handleAutoRevive(player, stats)) {
                    event.setCanceled(true);
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component.translatable("message.infinitestats.auto_revive"),
                            true
                    );
                    // 自动复活的 removeAllEffects() 会清除所有效果（包括夜视/隐身等），
                    // 必须立即重新施加被清除的 Utility 效果，否则要等最多 2 秒才会在 tick 中恢复
                    HandlerRegistry.loginAll(player, stats);
                    NetworkHandler.syncToClient(player);
                    return;
                }
            });
            if (event.isCanceled()) return;
        }

        // 击杀获得经验 + 击杀回蓝
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            var target = event.getEntity();
            long baseXp = (long) Config.XP_PER_KILL_BASE.get()
                    + (long) (target.getMaxHealth() * Config.XP_PER_KILL_HEALTH_FACTOR.get());

            player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
                long xp = UtilityHandler.applyXpGainBonus(stats, baseXp);
                addXpAndSync(player, stats, xp);
            });
        }
    }

    // ========== 跳跃事件 ==========

    /**
     * 使用 LOWEST 优先级的 Tick 阶段检测起跳，比 LivingJumpEvent 更可靠。
     * LivingJumpEvent 虽然理论上有效，但在巨型整合包中容易被其他模组的事件处理器覆盖。
     * 这里改用 ServerPlayer tick 中检测地面→离地转换，在 Phase.START 修改速度，
     * 并通过 setPos 补偿第一个 tick 的运动偏差（因为 travel() 会在 aiStep 中跑完）。
     */
    private static void handleJumpBoost(ServerPlayer player, PlayerStats stats) {
        float multiplier = MobilityHandler.getJumpMultiplier(stats);
        if (multiplier <= 1.0f) return;

        boolean onGround = player.onGround();
        java.util.UUID uuid = player.getUUID();

        Boolean wasOnGround = playerGroundState.get(uuid);
        playerGroundState.put(uuid, onGround);

        // 检测：上一tick在地面、当前tick离地、Y速度为正 → 起跳瞬间
        if (wasOnGround != null && wasOnGround && !onGround && player.getDeltaMovement().y > 0) {
            Vec3 motion = player.getDeltaMovement();
            double boostedY = motion.y * multiplier;
            double extraY = boostedY - motion.y;

            // 补偿第一tick运动（travel()已用原始速度跑完，手动修正位置以匹配倍率）
            if (extraY > 0.001) {
                player.setPos(player.getX(), player.getY() + extraY, player.getZ());
            }

            // 修正速度（影响后续 tick 的运动）
            player.setDeltaMovement(motion.x, boostedY, motion.z);
            player.hasImpulse = true;
        }
    }

    /** 记录各玩家上一tick是否在地面（用于起跳检测） */
    private static final java.util.Map<java.util.UUID, Boolean> playerGroundState = new java.util.HashMap<>();

    /**
     * 保留 LivingJumpEvent 作为第一道防线（在 jumpFromGround 内、travel 前执行，最精确）。
     * 使用 LOWEST 优先级确保覆盖其他模组的修改。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
            float multiplier = MobilityHandler.getJumpMultiplier(stats);
            if (multiplier > 1.0f) {
                Vec3 motion = player.getDeltaMovement();
                player.setDeltaMovement(motion.x, motion.y * multiplier, motion.z);
            }
        });
    }

    // ========== 掉落物事件 ==========

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;

        player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
            // 双倍掉落
            float doubleLootChance = stats.getStatValue(StatType.fromId("double_loot"));
            if (doubleLootChance > 0 && player.getRandom().nextFloat() < doubleLootChance) {
                // 先复制一份再遍历，防止 ConcurrentModificationException
                java.util.List<ItemEntity> copies = new java.util.ArrayList<>();
                for (ItemEntity drop : event.getDrops()) {
                    ItemStack stack = drop.getItem().copy();
                    ItemEntity extraDrop = new ItemEntity(
                            drop.level(), drop.getX(), drop.getY(), drop.getZ(), stack
                    );
                    copies.add(extraDrop);
                }
                event.getDrops().addAll(copies);
            }
        });
    }

    // ========== 伤害事件 ==========

    /**
     * 取消无敌帧 —— 关键在于<b>时机</b>。
     * <p>
     * {@code LivingAttackEvent} 触发在 {@code LivingEntity.hurt()} 的
     * <b>无敌帧检查之前</b>：
     * <pre>
     * hurt()
     *   ├─ ForgeHooks.onLivingAttack(...)      ← 本事件在这里
     *   ├─ if (invulnerableTime &gt; 10) return false;   ← 被吞就再也收不到任何事件
     *   └─ else { invulnerableTime = 20; actuallyHurt() → LivingHurtEvent }
     * </pre>
     * （1.20.2+ 里这个事件被改名为 {@code LivingIncomingDamageEvent}，时机与语义不变；
     * 本项目基于 1.20.1，所以用 {@code LivingAttackEvent}。）
     * <p>
     * 所以只有在这里清零，才能保证「本次攻击」不被无敌帧吞掉。
     * 若像之前那样只在 {@code LivingHurtEvent} 里清零，那么当目标身上带着
     * <b>其他来源</b>设置的无敌帧（被别人打、被别的怪打、着火、中毒都会重设为 20）时，
     * 玩家这一击会被上面的 {@code return false} 直接丢弃，事件根本不触发，无从补救。
     * <p>
     * {@code onLivingHurt} 里保留了同样的清零作为兜底（覆盖下一次攻击），两处配合下
     * 玩家的每一击都能全额生效。
     */
    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        LivingEntity target = event.getEntity();
        // 与攻击侧保持一致：PvP 时不破坏对方玩家的无敌帧
        if (target instanceof ServerPlayer) return;

        ServerPlayer attacker = getPlayerAttacker(event.getSource());
        if (attacker == null) return;

        attacker.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
            if (stats.isToggleActive("no_invincibility_frames")) {
                target.invulnerableTime = 0;
            }
        });
    }

    /**
     * 再早一层的清零 —— 覆盖「在 {@code hurt()} 之前就自行比较无敌帧」的实现。
     * <p>
     * {@link #onLivingAttack} 已经能挡住绝大多数「重复添加无敌帧」的情况：
     * 它在每次攻击的最开头执行，所以无论对方在什么时候（受伤后、结算后、自己的 tick 里）
     * 又把 {@code invulnerableTime} 设回去，玩家这一击都不会被吞。
     * <p>
     * 但若某个模组在<b>调用 {@code hurt()} 之前</b>就先比较 {@code invulnerableTime}
     * 并直接跳过，{@code hurt()} 压根不会执行，上面那个事件也就不会触发。
     * {@code AttackEntityEvent} 在 {@code Player.attack()} 里、真正施伤之前触发，
     * 是近战路径上最早的可介入点，在这里清零可以连这类实现一并覆盖。
     * <p>
     * （投射物没有对应的「施伤前」事件，仍由 {@link #onLivingAttack} 兜住；
     * 两处合起来覆盖近战与远程的全部路径。）
     */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getTarget() instanceof LivingEntity target)) return;
        // 与其余两处保持一致：PvP 时不破坏对方玩家的无敌帧
        if (target instanceof ServerPlayer) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
            if (stats.isToggleActive("no_invincibility_frames")) {
                target.invulnerableTime = 0;
            }
        });
    }

    /**
     * 无限弓箭（第一步·开始蓄力）：背包里没有箭时也能把弓 / 弩架起来。
     * <p>
     * 原版 {@code BowItem.use} 先看 {@code player.getProjectile(bow)} 是否为空，为空直接
     * 返回 {@code fail} —— <b>玩家连拉弓动作都做不出来</b>，后面的射箭逻辑自然无从谈起：
     * <pre>
     * BowItem.use()
     *   ├─ flag = !player.getProjectile(bow).isEmpty()
     *   ├─ ArrowNockEvent（hasAmmo 是 final 改不了，事件本身也不是 @Cancelable）
     *   └─ if (!instabuild &amp;&amp; !flag) return fail;      ← 卡在这里
     * </pre>
     * 弩（{@code CrossbowItem.use}）同理，区别只是它把 {@code startUsingItem} 放在
     * {@code else if (!getProjectile(itemstack).isEmpty())} 分支里，空手时一样直接 {@code fail}。
     * <p>
     * {@code RightClickItem} 正好在 {@code Item#use} <b>之前</b>触发且可取消，所以这里直接
     * 接替原版：自己调 {@code startUsingItem} 启动蓄力，再把事件取消掉、返回
     * {@code CONSUME} 告诉两端「这次交互已处理」。
     * <p>
     * 判定条件必须与后续接管步骤 <b>完全一致</b>，否则会出现「拉得起来但射不出去」或
     * 「能射但拉不起来」这种半残状态：弓对应 {@link #onArrowLoose}，弩对应 {@link #onUseItemStop}。
     */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        boolean isBow = stack.getItem() instanceof BowItem;
        boolean isCrossbow = stack.getItem() instanceof CrossbowItem;
        if (!isBow && !isCrossbow) return;

        Player player = event.getEntity();
        if (player.isUsingItem()) return;
        // 弩已装填时右键是「发射」，必须交回原版 performShooting
        if (isCrossbow && CrossbowItem.isCharged(stack)) return;
        if (!player.getProjectile(stack).isEmpty()) return;               // 背包有箭，交回原版
        if (!hasInfiniteArrows(player)) return;

        player.startUsingItem(event.getHand());
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);
    }

    /**
     * 无限弓箭（第二步·放箭）：没有实弹时自行发射一支箭。
     * <p>
     * 这里不能沿用原版流程，因为 {@code ArrowLooseEvent.hasAmmo} 是 {@code final} 且没有
     * setter，而 {@code BowItem.releaseUsing} 的判定用的是局部变量
     * {@code itemstack = player.getProjectile(bow)}，与事件参数无关：
     * <pre>
     * releaseUsing()
     *   ├─ itemstack = player.getProjectile(bow)         ← 空
     *   ├─ i = ForgeEventFactory.onArrowLoose(...)       ← 取消后返回 -1
     *   ├─ if (i &lt; 0) return;                            ← 拦在这里，原版不再继续
     *   └─ if (!itemstack.isEmpty() || flag) { ... }     ← 本来也进不去
     * </pre>
     * 所以「取消事件 + 本模组按原版规则补一支箭」是唯一可行的落点。
     * <p>
     * 补的箭是<b>独立新建的 ItemStack</b>，既不读也不改背包，因此不会扣掉玩家装的箭，
     * 也不会凭空塞进物品栏。拾取权限沿用原版「无限附魔」的做法设为 {@code CREATIVE_ONLY}，
     * 否则地上落箭被捡回等于无限刷箭。
     */
    @SubscribeEvent
    public static void onArrowLoose(ArrowLooseEvent event) {
        Player player = event.getEntity();
        ItemStack weapon = event.getBow();

        // 弩：原版 CrossbowItem.performShooting 的第一行也会走到这里（Forge 补丁固定以
        // charge=1 / hasAmmo=true 触发），此处只「记录 + 放行」，绝不能取消 —— 取消会让
        // 整发弩箭都射不出去（performShooting 直接 return）。
        // 此刻背包里一支箭都没有，说明这次射出的必然是「无箭装填」补进去的虚拟箭
        // （有实弹时压根轮不到模组插手），记下玩家与当前时刻，
        // 供 onEntityJoinLevel 收紧落箭的拾取权限。
        if (weapon.getItem() instanceof CrossbowItem) {
            // 1.20.1 的 Forge 补丁把这一行放在 isClientSide 判断之外，两端都会触发；
            // 而客户端的 shootProjectile 有 isClientSide 守卫、不会真的生成箭实体，
            // 所以标记只需要服务端那一份。
            if (!player.level().isClientSide()
                    && !player.isCreative()
                    && hasInfiniteArrows(player)
                    && !hasArrowInInventory(player)) {
                VIRTUAL_CROSSBOW_SHOTS.put(player.getUUID(), event.getLevel().getGameTime());
            }
            return;
        }

        if (player.isCreative()) return;                                    // 原版已支持
        if (!player.getProjectile(weapon).isEmpty()) return;                // 背包有箭，交回原版
        if (!hasInfiniteArrows(player)) return;

        event.setCanceled(true);
        shootAmmoLessArrow(player, weapon, event.getCharge(), event.getLevel());
    }

    /** 玩家是否解锁了「无限弓箭」。两处判定共用，避免条件写歪。 */
    private static boolean hasInfiniteArrows(Player player) {
        return player.getCapability(PlayerStatsProvider.PLAYER_STATS)
                .map(stats -> stats.isToggleActive("infinite_arrows"))
                .orElse(false);
    }

    /**
     * 按原版 {@code BowItem.releaseUsing} 的规则发射一支不来自背包的箭。
     * 蓄力曲线、暴击阈值、力量 / 冲击 / 火矢附魔加成与弓的耐久损耗均与原版一致，
     * 保证「有箭」和「没箭」两种情况下手感相同。
     */
    private static void shootAmmoLessArrow(Player player, ItemStack bow, int charge, Level level) {
        // 实体只在服务端生成（客户端靠同步），否则一次射击会冒出两支箭
        if (level.isClientSide()) return;

        float power = BowItem.getPowerForTime(charge);
        if (power < 0.1f) return;                     // 与原版相同的「拉弓太短」阈值

        AbstractArrow arrow = ((ArrowItem) Items.ARROW)
                .createArrow(level, new ItemStack(Items.ARROW), player);
        arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, power * 3.0F, 1.0F);
        if (power == 1.0F) arrow.setCritArrow(true);

        int powerLevel = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.POWER_ARROWS, bow);
        if (powerLevel > 0) arrow.setBaseDamage(arrow.getBaseDamage() + powerLevel * 0.5 + 0.5);

        int punchLevel = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PUNCH_ARROWS, bow);
        if (punchLevel > 0) arrow.setKnockback(punchLevel);

        if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FLAMING_ARROWS, bow) > 0) {
            arrow.setSecondsOnFire(100);
        }

        // 白得来的箭不该能被捡回，否则等于无限刷箭
        arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;

        bow.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(player.getUsedItemHand()));
        level.addFreshEntity(arrow);
    }

    /**
     * 无限弓箭（弩·装填）：没有实弹时把一支虚拟箭装进弩里。
     * <p>
     * 弩的装填判定在 {@code CrossbowItem.releaseUsing} 里，而它要求
     * {@code player.getProjectile(crossbow)} 非空 —— 空手时 {@code tryLoadProjectiles}
     * 直接返回 {@code false}，弩永远装不上：
     * <pre>
     * releaseUsing()
     *   ├─ f = getPowerForTime(已蓄力 tick, crossbow)
     *   └─ if (f &gt;= 1.0F &amp;&amp; !isCharged(&amp;&amp; tryLoadProjectiles(...))   ← 空手时第三个条件挂掉
     * </pre>
     * Forge 把 {@code LivingEntityUseItemEvent.Stop} 挂在 {@code LivingEntity.releaseUsingItem()}
     * 里、正好在 {@code ItemStack.releaseUsing} <b>之前</b>（见 1.20.1 的 LivingEntity 补丁），
     * 所以这里先把 {@code ChargedProjectiles} 和 {@code Charged} 补好，
     * 原版那一步就会因 {@code !isCharged(stack)} 不成立而整体短路，状态原地保留。
     * <p>
     * 只做「补齐」，不取消事件：命中不足或已有实弹时一律放行给原版，判定与
     * {@link #onRightClickItem} 保持一致，避免出现半残状态。
     */
    @SubscribeEvent
    public static void onUseItemStop(LivingEntityUseItemEvent.Stop event) {
        ItemStack stack = event.getItem();
        if (!(stack.getItem() instanceof CrossbowItem)) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;                          // NBT 以服务端为准
        if (CrossbowItem.isCharged(stack)) return;                          // 已装上实弹，交回原版
        if (!player.getProjectile(stack).isEmpty()) return;                 // 背包有箭，交回原版
        if (!hasInfiniteArrows(player)) return;

        // 装填是否已蓄满：原版判据 getPowerForTime(已用 tick) >= 1.0F，等价于已用 >= 装填时长
        int used = stack.getUseDuration() - event.getDuration();
        if (used < CrossbowItem.getChargeDuration(stack)) return;           // 提前松手，不装填

        loadVirtualArrows(stack);
        CrossbowItem.setCharged(stack, true);

        // 原版的装填完成音效挂在 releaseUsing 的成功分支里，会被上面的短路跳过，这里补上
        Level level = player.level();
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.CROSSBOW_LOADING_END, SoundSource.PLAYERS, 1.0F,
                1.0F / (level.getRandom().nextFloat() * 0.5F + 1.0F) + 0.2F);
    }

    /**
     * 把虚拟箭写进弩的 {@code ChargedProjectiles}，数量与原版 {@code tryLoadProjectiles} 一致
     * （多重射击 3 支、否则 1 支），否则玩家会感觉自己附的「多重射击」失效了。
     * <p>
     * NBT 格式直接沿用原版：{@code ChargedProjectiles} 是一个 ListTag，每项是一支箭的
     * {@code ItemStack.save()} 结果。整段不碰玩家背包，因此不会扣箭也不会凭空塞物品栏。
     */
    private static void loadVirtualArrows(ItemStack crossbow) {
        int count = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MULTISHOT, crossbow) > 0 ? 3 : 1;

        CompoundTag tag = crossbow.getOrCreateTag();
        ListTag list = tag.contains("ChargedProjectiles", 9)
                ? tag.getList("ChargedProjectiles", 10)
                : new ListTag();
        for (int i = 0; i < count; i++) {
            list.add(new ItemStack(Items.ARROW).save(new CompoundTag()));
        }
        tag.put("ChargedProjectiles", list);
    }

    /**
     * 收紧「无箭装填」射出的弩箭的拾取权限。
     * <p>
     * 原版 {@code CrossbowItem.shootProjectile} 只对「创造模式」或「多重射击的第 2、3 支」
     * 设 {@code CREATIVE_ONLY}，生存玩家射出的第一支会保持可拾取；而虚拟箭没消耗任何背包资源，
     * 落回地面被捡回就等于<b>凭空刷箭</b>（弓那一路是无条件设死的，弩必须在这里补一刀）。
     * <p>
     * 借助 {@link #onArrowLoose} 留下的「发射时刻」标记：只有同一 tick、由同一位
     * 「当时背包无箭」的玩家生成的弩箭才被改写，因此玩家用真箭射出的弩箭不受影响。
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof AbstractArrow arrow)) return;
        if (!arrow.shotFromCrossbow()) return;
        if (!(arrow.getOwner() instanceof Player player)) return;

        Long shotTick = VIRTUAL_CROSSBOW_SHOTS.get(player.getUUID());
        if (shotTick == null || shotTick != event.getLevel().getGameTime()) return;

        arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
    }

    /** 玩家背包（含副手）里是否还有弩能用的箭，判定口径与 {@code Player#getProjectile} 一致。 */
    private static boolean hasArrowInInventory(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(ItemTags.ARROWS)) return true;
        }
        return false;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(LivingHurtEvent event) {
        AttackHandler.takeTrueDamage(event.getEntity(), event.getSource());
        final float[] trueDamage = {0};
        try {
        // 玩家攻击（覆盖近战、弓箭/三叉戟等玩家拥有的抛射物，
        // 避免"伤害来源实体不是玩家"导致范围伤害等攻击效果时灵时不灵）
        ServerPlayer player = getPlayerAttacker(event);
        // 重入锁：次级受击（直接伤害）进入本方法时跳过攻击分支，彻底杜绝同步无限递归。
        if (player != null && !ATTACK_BRANCH_ACTIVE.get()) {
            ATTACK_BRANCH_ACTIVE.set(true);
            try {
            if (!AttackHandler.isDirectDamageSource(event.getSource())) {
            player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
                float amount = event.getAmount();

                // 暴击
                boolean isFullAttack = player.getAttackStrengthScale(0.5f) > 0.9f;
                amount *= AttackHandler.calculateCritMultiplier(player, stats, isFullAttack);

                // 弹射物伤害
                boolean isProjectile = event.getSource().getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile;
                amount *= AttackHandler.calculateProjectileBonus(stats, isProjectile);

                event.setAmount(amount);

                // 生命偷取不在此处结算：这里取到的仍是面板伤害（未经护甲 / 减伤 / 限伤），
                // 打高护甲或带限伤的目标会严重虚高。改到 LivingDamageEvent（LOWEST）
                // 按结算后的实际伤害计算，见 onLivingDamage。

                // 范围吸血（AOE 伤害部分）
                AttackHandler.applyAoeLifeSteal(player, stats, amount, event.getEntity());

                // 处决：对低血量目标额外伤害
                float executeDmg = stats.getStatValue(StatType.fromId("execute"));
                if (executeDmg > 0) {
                    LivingEntity target = event.getEntity();
                    float hpPct = target.getHealth() / target.getMaxHealth();
                    if (hpPct < 0.3f) {
                        float bonus = (1.0f - hpPct) * executeDmg * target.getMaxHealth();
                        event.setAmount(event.getAmount() + bonus);
                    }
                }

                // 记录减伤前的基数，最终扣血阶段再叠加，禁止在本次 hurt 内嵌套另一次 hurt。
                trueDamage[0] = AttackHandler.calculateTrueDamage(stats, event.getAmount());

                // 攻击降低目标最大生命值
                AttackHandler.applyReduceMaxHealth(player, stats, event.getEntity());

                // 范围攻击：波及周围敌人
                AttackHandler.applyScopeAttack(player, stats, event.getAmount(), event.getEntity());

                event.setAmount(AttackHandler.applyArmorPenetration(stats, event.getEntity(),
                        event.getSource(), event.getAmount()));

                // 取消无敌帧：攻击非玩家实体时取消目标的受伤无敌帧，提高攻击频率
                if (stats.isToggleActive("no_invincibility_frames")
                        && !(event.getEntity() instanceof ServerPlayer)) {
                    event.getEntity().invulnerableTime = 0;
                }
            });
            }
            } finally {
                ATTACK_BRANCH_ACTIVE.set(false);
            }
        }

        // 玩家受伤
        if (event.getEntity() instanceof ServerPlayer hurtPlayer) {
            hurtPlayer.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
                float amount = event.getAmount();

                // 复活无敌窗口：取消一切伤害（最高优先级，防止复活瞬间原地再死）
                if (DefenseHandler.isReviveInvulnerable(hurtPlayer, stats)) {
                    event.setCanceled(true);
                    return;
                }

                // 无敌：取消所有伤害
                if (DefenseHandler.isInvincible(stats)) {
                    event.setCanceled(true);
                    return;
                }

                // 真实伤害遵循伤害类型的绕过效果标签，不再被本模组减伤或法力护盾削减。
                if (event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_EFFECTS)) return;

                // 检查免疫
                if (DefenseHandler.isImmune(stats, event.getSource(), hurtPlayer)) {
                    event.setCanceled(true);
                    return;
                }

                // 闪避
                if (DefenseHandler.handleDodge(hurtPlayer, stats)) {
                    event.setCanceled(true);
                    hurtPlayer.displayClientMessage(
                            net.minecraft.network.chat.Component.translatable("message.infinitestats.dodged"),
                            true
                    );
                    return;
                }

                // 格挡
                if (DefenseHandler.handleBlock(hurtPlayer, stats)) {
                    event.setCanceled(true);
                    hurtPlayer.displayClientMessage(
                            net.minecraft.network.chat.Component.translatable("message.infinitestats.blocked"),
                            true
                    );
                    return;
                }

                // 伤害减免
                amount = DefenseHandler.applyDamageReduction(stats, amount);

                // 摔落伤害减免
                boolean isFall = event.getSource() == hurtPlayer.level().damageSources().fall();
                amount = DefenseHandler.applyFallDamageReduction(stats, amount, isFall);
                if (amount <= 0) {
                    event.setCanceled(true);
                    return;
                }

                event.setAmount(amount);

                // 伤害反射
                if (event.getSource().getEntity() instanceof LivingEntity attacker) {
                    DefenseHandler.applyDamageReflection(hurtPlayer, stats, event.getAmount(), attacker);
                }
            });
        }
        if (!event.isCanceled() && event.getAmount() > 0) {
            AttackHandler.rememberTrueDamage(event.getEntity(), event.getSource(), trueDamage[0]);
        }
        } catch (Throwable t) {
            // 任何异常都不应冒泡到 Forge 事件总线：EventBus.handleException 在记录日志时
            // 会撞上 log4j 类加载冲突（LinkageError），直接拖垮整个服务端。
            // 这里就地捕获并打印真实堆栈，既保住服务器，也把根因暴露到控制台/日志。
            System.err.println("[infinitestats] StatEventHandler.onLivingHurt 抛出异常（已抑制，避免服务端崩溃）：");
            t.printStackTrace();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onLivingDamage(LivingDamageEvent event) {
        float bonus = AttackHandler.takeTrueDamage(event.getEntity(), event.getSource());
        if (event.isCanceled()) return;
        if (bonus > 0 && Float.isFinite(bonus)) {
            // 真伤改为直接扣血结算，不叠加进 event.getAmount()：
            // 叠加会与主伤害一起被后续的抗性提升 / 保护附魔 / 吸收护盾，
            // 以及其它模组的「单次伤害上限（限伤）」一并削减，导致真伤形同虚设。
            // event.getAmount() > 0 表示主伤害有效，原版随后会走完 setHealth 与死亡判定，
            // 死亡 / 掉落 / 击杀归属仍由原版收尾。
            AttackHandler.applyUnavoidableTrueDamage(event.getEntity(), bonus, event.getAmount() > 0);
        }
        // 生命偷取：此时护甲与减伤已结算，用实际伤害作为基数（而非 LivingHurtEvent 的面板值）。
        // 被取消的伤害（格挡 / 闪避 / 无敌）会在此处提前返回，因此不会出现「打空也吸血」。
        // 次级直接伤害（范围攻击 / 真伤 / 降上限）不是玩家主动攻击，不计入吸血。
        if (event.getAmount() > 0 && !AttackHandler.isDirectDamageSource(event.getSource())) {
            ServerPlayer attacker = getPlayerAttacker(event.getSource());
            if (attacker != null) {
                attacker.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(
                        stats -> AttackHandler.applyLifeSteal(attacker, stats, event.getAmount()));
            }
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(
                    stats -> DefenseHandler.trackAbsorptionUse(player, stats));
        }
    }

    @SubscribeEvent
    public static void onServerTickEnd(TickEvent.ServerTickEvent event) {
        // 后续模组取消伤害或提前返回时可能没有 LivingDamage；不将残余记录带入下一 tick。
        if (event.phase == TickEvent.Phase.END) AttackHandler.clearPendingTrueDamage();
    }

    // ========== 负面效果拦截 ==========

    /**
     * 在药水效果施加前直接拦截（debuff_immunity 开关）
     * 支持玩家自定义过滤列表（黑名单/白名单模式），可选择过滤所有效果（包括正面buff和负面debuff）
     * <p>
     * 注意：绝不拦截本模组自己的 Utility 效果（夜视、隐身等），
     * 否则 UtilityHandler 施加的效果会被这里反向拦截，导致 toggle 功能失效。
     */
    @SubscribeEvent
    public static void onMobEffectApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
            if (!stats.isToggleActive("debuff_immunity")) return;

            String effectId = net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS
                    .getKey(event.getEffectInstance().getEffect()).toString();

            // 绝不拦截本模组自己施加的效果（夜视/隐身/幸运等 Utility toggle）
            if (isOwnUtilityEffect(stats, effectId)) return;

            if (stats.shouldBlockEffect(effectId)) {
                event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
            }
        });
    }

    /**
     * 检查某个药水效果是否来自本模组的 Utility 系统
     * 如果对应的 toggle 已激活，说明该效果由我们自己施加，不应被 debuff_immunity 拦截
     */
    private static boolean isOwnUtilityEffect(PlayerStats stats, String effectId) {
        return switch (effectId) {
            case "minecraft:night_vision" -> stats.isToggleActive("night_vision");
            case "minecraft:invisibility" -> stats.isToggleActive("invisibility");
            case "minecraft:luck" -> stats.getStatValue("loot_luck") > 0;
            default -> false;
        };
    }

    // ========== 玩家Tick事件 ==========

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        long tickCount = player.tickCount;

        // 使用HandlerRegistry处理所有效果（含跳跃检测、被动经验，每玩家独立计数）
        player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
            try {
                HandlerRegistry.tickAll(player, stats, tickCount);
            } catch (Throwable t) {
                t.printStackTrace();
            }
            try {
                // 跳跃加成检测（tick 级兜底，比 LivingJumpEvent 更可靠）
                handleJumpBoost(player, stats);
            } catch (Throwable t) {
                t.printStackTrace();
            }
            try {
                // 被动经验 - 每玩家独立计数器
                stats.incrementPassiveTickCounter();
                if (stats.getPassiveTickCounter() >= Config.PASSIVE_XP_INTERVAL.get()) {
                    stats.resetPassiveTickCounter();
                    addXpAndSync(player, stats, Config.PASSIVE_XP_AMOUNT.get());
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
            // 随身熔炉：每个玩家 tick 都驱动冶炼进度（与界面是否打开无关）
            // 独立 try，确保即使上述逻辑异常也照常冶炼
            try {
                stats.getFurnaceData().tick(player.level());
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });
    }

    // ========== 挖掘速度 ==========

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        event.getEntity().getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
            float bonus = UtilityHandler.getMiningSpeedMultiplier(stats);
            if (bonus > 1) {
                float effectiveBonus = bonus;
                // 原版 getDigSpeed 在此事件后对「不在地面」执行 ÷5 惩罚。
                // 提前 ×5 补偿以消除飞行/跳跃对挖掘速度的严重影响。
                if (!event.getEntity().onGround()) {
                    effectiveBonus *= 5.0f;
                }
                // 水下无「水下速掘」附魔时同样有 ÷5 惩罚（水上附魔则 ÷25）。
                // 仅在玩家没有 Aqua Affinity 时补偿，避免与附魔叠加出 5 倍速。
                if (event.getEntity().isEyeInFluid(FluidTags.WATER)
                        && !EnchantmentHelper.hasAquaAffinity(event.getEntity())) {
                    effectiveBonus *= 5.0f;
                }
                event.setNewSpeed(event.getOriginalSpeed() * effectiveBonus);
            }
        });
    }

    // ========== 方块破坏事件 ==========

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (player.isCreative()) return;

        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        ServerLevel level = (ServerLevel) event.getLevel();

        player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
            UtilityHandler.handleVeinMinerAndAutoSmelt(player, stats, pos, state, level);
        });
    }

    // ========== 挖掘等级加成 ==========

    @SubscribeEvent
    public static void onHarvestCheck(PlayerEvent.HarvestCheck event) {
        if (event.canHarvest()) return;

        event.getEntity().getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
            int bonus = UtilityHandler.getMiningLevelBonus(stats);
            if (bonus <= 0) return;

            ItemStack tool = event.getEntity().getMainHandItem();
            int toolIndex = getToolTierIndex(tool);

            // 空手或手持非工具时，视为最低等级（索引 0），使挖掘等级加成能生效
            if (toolIndex < 0) {
                toolIndex = 0;
            }

            java.util.List<net.minecraft.world.item.Tier> sortedTiers =
                    TierSortingRegistry.getSortedTiers();
            int enhancedIndex = Math.min(toolIndex + bonus, sortedTiers.size() - 1);
            if (enhancedIndex <= toolIndex) return;

            net.minecraft.world.item.Tier enhancedTier = sortedTiers.get(enhancedIndex);
            if (TierSortingRegistry.isCorrectTierForDrops(enhancedTier, event.getTargetBlock())) {
                event.setCanHarvest(true);
            }
        });
    }

    /**
     * 获取手持工具在 TierSortingRegistry 排序列表中的索引
     * 空手/非工具 = -1
     */
    private static int getToolTierIndex(ItemStack tool) {
        if (!(tool.getItem() instanceof TieredItem ti)) return -1;
        java.util.List<net.minecraft.world.item.Tier> sorted =
                TierSortingRegistry.getSortedTiers();
        return sorted.indexOf(ti.getTier());
    }

    // ========== 玩家登录 ==========

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
            // 重新计算可用点数，修复旧存档可能为负的问题
            stats.recalculateAvailablePoints();
            NetworkHandler.syncToClient(player);
            HandlerRegistry.loginAll(player, stats);
        });
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        // 虚拟弩箭的发射标记只在「当个 tick」有意义，玩家离开时顺手清掉，避免长期留存
        VIRTUAL_CROSSBOW_SHOTS.remove(event.getEntity().getUUID());
    }

    // ========== 玩家重生 ==========

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
            NetworkHandler.syncToClient(player);
            HandlerRegistry.respawnAll(player, stats);
        });
    }

    // ========== 玩家克隆 ==========

    /**
     * 玩家实体克隆时保留所有数据（死亡重生、末地传送门返回等场景均会触发 Clone 事件）
     * 不区分死亡/非死亡克隆，与 EMC 系统保持一致的处理方式
     */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        event.getOriginal().reviveCaps();
        event.getOriginal().getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(oldStats -> {
            event.getEntity().getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(newStats -> {
                newStats.copyFrom(oldStats);
            });
        });
        event.getOriginal().invalidateCaps();
    }

    // ========== 维度切换 ==========

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
            NetworkHandler.syncToClient(player);
            HandlerRegistry.dimensionChangeAll(player, stats);
        });
    }

    // ========== 使用速度加速 ==========

    @SubscribeEvent
    public static void onItemUseTick(LivingEntityUseItemEvent.Tick event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getDuration() <= 0) return;

        player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
            // 通用使用速度（吃东西、喝药水、用盾牌等，不对弓/弩/三叉戟生效，那些由 bow_draw_speed 负责）
            boolean isBow = event.getItem().getItem() instanceof BowItem
                    || event.getItem().getItem() instanceof CrossbowItem
                    || event.getItem().getItem() instanceof TridentItem;

            float totalSpeed = 0;
            if (!isBow) {
                totalSpeed = stats.getStatValue(StatType.fromId("use_speed"));
            } else {
                totalSpeed = stats.getStatValue(StatType.fromId("bow_draw_speed"));
            }

            if (totalSpeed > 0) {
                int extraReduction = Math.max(1, (int) (totalSpeed * 100));
                event.setDuration(Math.max(0, event.getDuration() - extraReduction));
            }
        });
    }

    // ========== 合成奖励 ==========

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide) return;

        player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
            float chance = stats.getStatValue(StatType.fromId("crafting_bonus"));
            if (chance > 0 && player.getRandom().nextFloat() < chance) {
                ItemStack bonus = event.getCrafting().copy();
                if (!player.getInventory().add(bonus)) {
                    player.drop(bonus, false);
                }
            }
        });
    }

    // ========== 辅助方法 ==========

    /**
     * 判断本次伤害是否由玩家造成：
     * 1) 伤害来源实体直接就是玩家（近战挥砍、多数模组技能/召唤物以玩家为来源）
     * 2) 直接实体是玩家拥有的抛射物（弓箭、三叉戟等）
     * 用于让范围伤害、吸血等攻击附加效果在更多攻击方式下稳定触发
     */
    private static ServerPlayer getPlayerAttacker(LivingHurtEvent event) {
        return getPlayerAttacker(event.getSource());
    }

    private static ServerPlayer getPlayerAttacker(DamageSource source) {
        if (source.getEntity() instanceof ServerPlayer sp) return sp;
        var direct = source.getDirectEntity();
        if (direct instanceof net.minecraft.world.entity.projectile.Projectile p
                && p.getOwner() instanceof ServerPlayer sp) {
            return sp;
        }
        return null;
    }

    private static void addXpAndSync(ServerPlayer player, PlayerStats stats, long xp) {
        boolean leveledUp = stats.addExperience(xp);
        NetworkHandler.syncToClient(player);
        if (leveledUp) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("message.infinitestats.level_up",
                            stats.getLevel(), stats.getAvailablePoints()),
                    true
            );
        }
    }
}
