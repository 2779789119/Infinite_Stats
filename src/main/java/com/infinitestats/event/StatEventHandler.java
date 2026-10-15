package com.infinitestats.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.infinitestats.Config;
import com.infinitestats.InfiniteStats;
import com.infinitestats.handler.*;
import com.infinitestats.network.NetworkHandler;
import com.infinitestats.compat.CuriosBridge;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import com.infinitestats.stats.StatType;
import com.infinitestats.util.EntityPanelWand;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
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
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.ArrowLooseEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * 主事件处理器 - 整合所有属性效果
 * 使用HandlerRegistry统一调度各个专用处理器
 */
@EventBusSubscriber(modid = InfiniteStats.MODID)
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

    /**
     * 「无箭放箭」的同 tick 去重表：键为玩家 UUID，值为该玩家上一次补箭时的 {@code gameTime}。
     * <p>
     * 1.21.1 上弓的补箭走 {@link #onUseItemStop}（{@code ArrowLooseEvent} 在无箭时压根不触发），
     * 弩的虚拟箭则由原版 {@code performShooting} 走到 {@link #onArrowLoose}；两条入口对同一件武器互斥。
     * 这里再加一道保险：万一将来 NeoForge 又把事件挪回判定之外，同一 tick 也只补一支箭。
     * 值带 gameTime，过期即自动失效（不需要额外清理，登出时顺手移除）。
     */
    private static final Map<UUID, Long> AMMO_LESS_SHOTS = new ConcurrentHashMap<>();

    // ========== 死亡事件 ==========

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        // 自动复活
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerStatsProvider.of(player).ifPresent(stats -> {
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

            PlayerStatsProvider.of(player).ifPresent(stats -> {
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
        PlayerStatsProvider.of(player).ifPresent(stats -> {
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

        PlayerStatsProvider.of(player).ifPresent(stats -> {
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

    // ========== 村民交易折扣 ==========

    /**
     * 每个报价的折扣记账：[0] = 本模组写进去的折扣（正数，0 表示没写），[1] = 写入后的 specialPriceDiff。
     * <p>
     * 键用 {@link MerchantOffer} 自身（它没有覆写 equals/hashCode，等价于按实例），WeakHashMap 保证
     * 商人被卸载后条目能自动回收。之所以要记账：{@code specialPriceDiff} **会随商人一起存档**，
     * 而只有村民会在 {@code stopTrading} 里清零、流浪商人不会 —— 若无脑叠加，反复开关界面
     * 会把价格一路刷到 1 并被永久保存。
     */
    private static final Map<MerchantOffer, int[]> TRADE_DISCOUNT_STATE = new java.util.WeakHashMap<>();

    /**
     * 村民交易折扣（{@code trade_discount}，与声望 / 英雄效果折扣**叠加**）。
     * <p>
     * 交易价格由 {@code MerchantOffer.getCostA()} 现算：{@code 基础数量 + 需求加价 + specialPriceDiff}。
     * 原版 {@code Villager#startTrading} 先按声望与英雄效果 {@code addToSpecialPriceDiff}，
     * 之后 {@code Merchant#openTradingScreen} 才 openMenu（本事件在此触发）并发送交易列表，
     * 所以在事件里改价，客户端收到的是折后价，服务端扣物品也走同一份 offers，价格不会分叉。
     * <p>
     * 叠加方式：把本模组的折扣**加在原版折扣之上**。为了不累加，每次先按记账剥离出
     * 「本模组上一次写进去的折扣」，得到原版此刻的价值。
     */
    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getContainer() instanceof MerchantMenu menu)) return;

        PlayerStatsProvider.of(player).ifPresent(stats -> {
            float rate = Math.min(0.9f, Math.max(0f, stats.getStatValue("trade_discount")));
            MerchantOffers offers = menu.getOffers();
            for (int i = 0; i < offers.size(); i++) {
                applyTradeDiscount(offers.get(i), rate);
            }
        });
    }

    /**
     * 把本模组的交易折扣叠加到单个报价上。
     * <p>
     * 即使 {@code rate} 为 0 也会走一遍：玩家把点数退掉之后，需要把之前写进流浪商人报价里的
     * 折扣收回来（村民自己会清零，流浪商人不会）。
     *
     * @param rate 折扣率（已夹在 [0, 0.9]）
     */
    private static void applyTradeDiscount(MerchantOffer offer, float rate) {
        ItemStack base = offer.getBaseCostA();
        if (base.isEmpty()) return;

        int[] state = TRADE_DISCOUNT_STATE.get(offer);
        if (state == null && rate <= 0f) return; // 没折扣也没历史，不动价格

        int current = offer.getSpecialPriceDiff();
        int vanilla = (state != null && current <= state[1]) ? current + state[0] : current;

        // 至少保留 1 个基础物品（getCostA 自身也会把结果夹在 [1, 堆叠上限]）
        int room = Math.max(0, base.getCount() - 1 + vanilla);
        int discount = Math.min(room, Mth.floor(base.getCount() * rate));
        int total = vanilla - discount;

        offer.setSpecialPriceDiff(total);
        TRADE_DISCOUNT_STATE.put(offer, new int[]{discount, total});
    }

    // ========== 交易即刻补货 ==========

    /**
     * 交易即刻补货（{@code trade_restock}）：村民 / 流浪商人成交后立刻把该条目补满，可以连续交易同一条目。
     * <p>
     * 1.21.1 的 NeoForge 提供 {@link TradeWithVillagerEvent}（1.20.2+ 新增），落点比 1.20.1 的
     * 「每 5 tick 扫一遍菜单」干净得多：只在真正成交那一次动手。
     * <p>
     * 补货只改服务端的 offers，客户端界面仍显示「已用完」，所以要重发一次交易列表
     * （{@code sendMerchantOffers}）把权威状态推过去。
     */
    @SubscribeEvent
    public static void onTradeWithVillager(TradeWithVillagerEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MerchantOffer offer = event.getMerchantOffer();
        if (offer == null) return;

        PlayerStatsProvider.of(player).ifPresent(stats -> {
            if (!stats.isToggleActive("trade_restock")) return;
            if (offer.getUses() <= 0) return;

            offer.resetUses();
            if (player.containerMenu instanceof MerchantMenu menu) {
                AbstractVillager trader = event.getAbstractVillager();
                player.sendMerchantOffers(menu.containerId, menu.getOffers(),
                        menu.getTraderLevel(), menu.getTraderXp(),
                        trader.showProgressBar(), trader.canRestock());
            }
        });
    }

    // ========== 满饱食度进食 ==========

    /**
     * 「满饱食度进食」（{@code always_eat}）：饱食度已经满了也能继续吃普通食物。
     * <p>
     * 原版进食是 {@code Item#use} 开头的一段固定分支：
     * <pre>
     * Item.use()
     *   ├─ food = itemstack.getFoodProperties(player)
     *   ├─ if (player.canEat(food.canAlwaysEat())) { player.startUsingItem(hand); return consume; }
     *   └─ return fail;                        ← 饱食度满 + 普通食物 卡在这里
     * </pre>
     * {@code RightClickItem} 正好在 {@code Item#use} <b>之前</b>触发且可取消，所以这里直接接替
     * 原版那两行：自己调 {@code startUsingItem} 启动进食，再取消事件返回 {@code CONSUME}。
     * <p>
     * <b>只在必须接管时才接管</b>：玩家还饿着、食物本身 {@code canAlwaysEat()}、
     * 未解锁属性或食物被配置名单排除 —— 一律不取消事件、交回原版，不会出现
     * 「本来能吃、加了本模组反而吃不了」。
     */
    @SubscribeEvent
    public static void onRightClickItemAlwaysEat(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (player.getFoodData().needsFood()) return;      // 不饿：原版本来就管，不插手
        if (player.isUsingItem()) return;                  // 正在吃 / 拉弓：让原版流程自己走完

        ItemStack stack = event.getItemStack();

        FoodProperties food = stack.getFoodProperties(player);
        if (food == null) return;                          // 不可食用（1.21 起没有 ItemStack#isEdible）
        if (food.canAlwaysEat()) return;                   // 原版满饱食度也能吃，交回原版

        boolean active = PlayerStatsProvider.of(player)
                .map(stats -> stats.isToggleActive("always_eat"))
                .orElse(false);
        if (!active) return;
        if (!isAlwaysEatAllowed(stack)) return;            // 配置名单限制
        if (!player.canEat(true)) return;                  // 创造 / 旁观等，与原版口径一致

        player.startUsingItem(event.getHand());
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);
    }

    /** 「满饱食度进食」名单里的 {@code #标签} 解析结果缓存（标签不可变，可长期复用）。 */
    private static final Map<String, TagKey<Item>> ALWAYS_EAT_TAG_CACHE = new ConcurrentHashMap<>();

    /**
     * 该食物是否适用「满饱食度进食」。
     * <p>
     * 【例外名单】优先：命中就直接否掉，即使它也在【限制名单】里；
     * 【限制名单】非空时只有命中它的食物才算数，留空则视为「所有食物都适用」。
     * 名单条目既可以是物品 ID，也可以是 {@code #命名空间:标签}。
     */
    private static boolean isAlwaysEatAllowed(ItemStack stack) {
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        String idStr = id == null ? "" : id.toString();

        List<? extends String> exclude = Config.ALWAYS_EAT_EXCLUDE_LIST.get();
        if (matchesItemList(stack, idStr, exclude)) return false;

        List<? extends String> only = Config.ALWAYS_EAT_ONLY_LIST.get();
        return only == null || only.isEmpty() || matchesItemList(stack, idStr, only);
    }

    /** 物品是否命中名单（物品 ID 精确匹配 / {@code #标签} 归属匹配）。 */
    private static boolean matchesItemList(ItemStack stack, String idStr, List<? extends String> list) {
        if (list == null || list.isEmpty()) return false;
        for (String entry : list) {
            if (entry == null) continue;
            String value = entry.trim();
            if (value.isEmpty()) continue;
            if (value.startsWith("#")) {
                TagKey<Item> tag = ALWAYS_EAT_TAG_CACHE.computeIfAbsent(value, key -> {
                    ResourceLocation tagId = ResourceLocation.tryParse(key.substring(1));
                    return tagId == null ? null : TagKey.create(Registries.ITEM, tagId);
                });
                if (tag != null && stack.is(tag)) return true;
            } else if (value.equals(idStr)) {
                return true;
            }
        }
        return false;
    }

    // ========== 死亡不掉经验 ==========

    /**
     * 死亡不掉经验（{@code keep_xp}）：把死亡掉落的经验量改成 0，经验球便不会生成；
     * 等级 / 经验条留存在老实体上，由 {@link #onPlayerClone} 原样搬给重生后的新实体。
     */
    @SubscribeEvent
    public static void onLivingExperienceDrop(LivingExperienceDropEvent event) {
        if (event.getDroppedExperience() <= 0) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerStatsProvider.of(player).ifPresent(stats -> {
            if (stats.isToggleActive("keep_xp")) event.setDroppedExperience(0);
        });
    }

    // ========== 死亡不掉落 ==========

    /**
     * 死亡时暂存的背包（36 主背包 + 4 盔甲 + 1 副手），键为玩家 UUID。
     * <p>
     * 只在「死亡 → 重生」这一小段窗口内存在，重生时即取走并清空。
     */
    private static final Map<UUID, List<ItemStack>> KEPT_INVENTORIES = new ConcurrentHashMap<>();

    /**
     * 死亡时暂存的 Curios 饰品（NBT 形式），与 {@link #KEPT_INVENTORIES} 同生命周期。
     * <p>
     * 饰品栏不在原版 {@code Inventory} 里，也不受 {@code keepInventory} 游戏规则保护，
     * 所以必须单独抓走 —— 否则 Curios 会按它自己的规则把饰品掉在地上。
     * 抓取 / 归还的全部安全性判断都在 {@link CuriosBridge}（宁可掉落，绝不凭空消失）。
     */
    private static final Map<UUID, List<CompoundTag>> KEPT_CURIOS = new ConcurrentHashMap<>();

    /**
     * 死亡不掉落（{@code keep_inventory}）：原版只在游戏规则 {@code keepInventory} 开启时才保留背包，
     * 而「掉落」发生在 {@code Player.dropAllDeathLoot → Inventory.dropAll()} 里，全程没有可拦截的事件。
     * 因此改为在死亡事件的最后一刻把主背包 / 盔甲 / 副手整体挪进 {@link #KEPT_INVENTORIES} 暂存：
     * 原版随后执行的 {@code dropAll()} 面对空背包自然什么都不会掉；玩家重生时
     * （{@code PlayerEvent.Clone}）再把物品原样归还。
     * <p>
     * 必须挂在 LOWEST：自动复活（HIGHEST）会在自己那一轮取消死亡，取消后不应清空背包。
     * <p>
     * 注意：死亡事件对玩家会触发两次（{@code Player.die} 与 {@code super.die} 各一次），
     * 因此用 {@code containsKey} 做幂等保护，避免第二轮用空背包覆盖暂存。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeathKeepInventory(LivingDeathEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        PlayerStatsProvider.of(player).ifPresent(stats -> {
            if (!stats.isToggleActive("keep_inventory")) return;
            if (KEPT_INVENTORIES.containsKey(player.getUUID())) return;

            var inv = player.getInventory();
            List<ItemStack> saved = new ArrayList<>();
            for (ItemStack stack : inv.items) saved.add(stack.copy());
            for (ItemStack stack : inv.armor) saved.add(stack.copy());
            for (ItemStack stack : inv.offhand) saved.add(stack.copy());

            KEPT_INVENTORIES.put(player.getUUID(), saved);
            inv.items.clear();
            inv.armor.clear();
            inv.offhand.clear();
            inv.setChanged();

            // Curios 饰品栏同理：takeAll 返回 null 表示反射失败，此时饰品栏保持原样、按原版掉落
            List<CompoundTag> curios = CuriosBridge.takeAll(player);
            if (curios != null) KEPT_CURIOS.put(player.getUUID(), curios);
        });
    }

    /** 把暂存的 41 格（36 主背包 + 4 盔甲 + 1 副手）物品放回玩家背包，多出来的就地掉落。 */
    private static void restoreKeptInventory(Player player, List<ItemStack> saved) {
        if (saved == null || saved.isEmpty()) return;
        var inv = player.getInventory();
        for (int i = 0; i < saved.size(); i++) {
            ItemStack stack = saved.get(i);
            if (stack == null || stack.isEmpty()) continue;
            if (i < inv.items.size()) {
                inv.items.set(i, stack);
            } else if (i < inv.items.size() + inv.armor.size()) {
                inv.armor.set(i - inv.items.size(), stack);
            } else {
                int k = i - inv.items.size() - inv.armor.size();
                if (k < inv.offhand.size()) inv.offhand.set(k, stack);
                else player.drop(stack, false);
            }
        }
        inv.setChanged();
    }

    // ========== 伤害事件 ==========

    /**
     * 取消无敌帧 —— 关键在于<b>时机</b>。
     * <p>
     * {@code LivingAttackEvent} 触发在 {@code LivingEntity.hurt()} 的
     * <b>无敌帧检查之前</b>：
     * <pre>
     * hurt()
     *   ├─ EventHooks.onLivingAttack(...)      ← 本事件在这里
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
    public static void onLivingAttack(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        // 与攻击侧保持一致：PvP 时不破坏对方玩家的无敌帧
        if (target instanceof ServerPlayer) return;

        ServerPlayer attacker = getPlayerAttacker(event.getSource());
        if (attacker == null) return;

        PlayerStatsProvider.of(attacker).ifPresent(stats -> {
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

        PlayerStatsProvider.of(player).ifPresent(stats -> {
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
     * 无限弓箭（第二步·放箭·兜底）：{@code ArrowLooseEvent} 触发时的补箭。
     * <p>
     * <b>⚠ 1.21.1 上这条入口在「背包无箭」时不会被触发</b> —— 原版 {@code BowItem.releaseUsing}
     * 把事件调用挪进了 {@code if (!itemstack.isEmpty())} <b>内部</b>：
     * <pre>
     * 1.21.1 BowItem.releaseUsing()
     *   ├─ itemstack = player.getProjectile(bow)
     *   ├─ if (!itemstack.isEmpty()) {                   ← 空手时整段跳过，事件根本不触发
     *   │     i = EventHooks.onArrowLoose(...)
     *   │     if (i &lt; 0) return;
     *   │     ...
     *   └─ }
     * </pre>
     * 而 1.20.1 的补丁把事件调用放在该判定<b>之外</b>，所以「取消事件即可接管放箭」的写法
     * 在 1.21.1 上会退化成「拉得起弓、射不出箭」。弓的无箭放箭因此改由 {@link #onUseItemStop}
     * 承担（该事件在 {@code ItemStack#releaseUsing} 之前触发）；此处保留一条兜底，
     * 两条入口靠 {@link #AMMO_LESS_SHOTS} 做同 tick 去重，只有先到的那条真正放箭。
     * <p>
     * 弩那一支（下面的分支）不受影响：虚拟箭是被写进 {@code ChargedProjectiles} 组件的，
     * 原版 {@code performShooting} 走到这里时 {@code hasAmmo} 为真，仍然需要这里记录发射时刻。
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

    // ========== 生物面板（entity_panel） ==========

    /**
     * 手持「生物观察杖」右键生物 → 请求服务端下发一次「打开这个生物的面板」。
     * <p>
     * 只在服务端处理（客户端不做本地预测）：面板内容以服务端下发的为准，
     * 拉取时就把「哪些开关可用 / 当前开着哪些 / 这个玩家能不能改」一起带过去。
     */
    // ========== 一键长大 ==========

    /**
     * 一键长大（{@code instant_grow}）：手持该动物的饲料右键幼年动物时，一次喂食直接长到成年。
     * <p>
     * 用 {@link PlayerInteractEvent.EntityInteract}：它在 {@code Player#interactOn} 里、
     * 原版 {@code mobInteract} 之前触发，可以取消，所以不会出现「原版先长 10%、我们再补满」的双重结算。
     * 两端都会跑（客户端做同样的本地预测，服务端权威）。
     */
    @SubscribeEvent
    public static void onEntityInteractInstantGrow(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Animal animal)) return;
        if (!animal.isBaby()) return;

        ItemStack stack = event.getItemStack();
        if (!animal.isFood(stack)) return;

        Player player = event.getEntity();
        boolean active = PlayerStatsProvider.of(player)
                .map(stats -> stats.isToggleActive("instant_grow"))
                .orElse(false);
        if (!active) return;

        // 消耗一个（与原版 Animal#usePlayerItem 一致）
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        // 剩余年龄按「秒」换算后一次性补满：ageUp 会把结果钳到 0，并播放长大粒子
        int remaining = -animal.getAge();
        animal.ageUp(Math.max(1, remaining / 20 + 1), true);

        event.setCancellationResult(InteractionResult.sidedSuccess(animal.level().isClientSide));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (handleWandInteract(event.getEntity(), event.getTarget(), event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    /**
     * 同上，但覆盖「精确交互」那条路径（盔甲架之类会走 {@code Entity#interactAt}）。
     * <p>
     * 两条路径共用同一判定；重复触发也无害 —— 客户端收到同一个生物的包时只刷新开关状态，
     * 不会重建界面（见 {@code EntityPanelScreen#openForEntity}）。
     */
    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (handleWandInteract(event.getEntity(), event.getTarget(), event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    /** 两处入口用同一条判定，返回 true 表示这次右键已经被「生物面板」接管。 */
    private static boolean handleWandInteract(Player player, Entity target, ItemStack stack) {
        if (player == null || player.level().isClientSide) return false;
        if (!(player instanceof ServerPlayer serverPlayer)) return false;
        if (!(target instanceof LivingEntity living)) return false;
        if (!EntityPanelWand.isWand(stack)) return false;
        if (!PlayerStatsProvider.of(serverPlayer)
                .map(stats -> stats.isToggleActive(EntityPanelWand.STAT_ID))
                .orElse(false)) {
            return false;
        }

        NetworkHandler.sendOpenEntityPanel(serverPlayer, living);
        return true;
    }

    /** 玩家是否解锁了「无限弓箭」。两处判定共用，避免条件写歪。 */
    private static boolean hasInfiniteArrows(Player player) {
        return PlayerStatsProvider.of(player)
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

        // 同 tick 去重：onUseItemStop 与 onArrowLoose 两条入口理论上互斥，这里兜一层，
        // 保证一次松手只补一支箭（拉弓太短被下面的阈值挡掉时，记录也已写入，不影响手感）
        long now = level.getGameTime();
        Long last = AMMO_LESS_SHOTS.put(player.getUUID(), now);
        if (last != null && last == now) return;

        float power = BowItem.getPowerForTime(charge);
        if (power < 0.1f) return;                     // 与原版相同的「拉弓太短」阈值

        AbstractArrow arrow = ((ArrowItem) Items.ARROW)
                .createArrow(level, new ItemStack(Items.ARROW), player, bow);
        arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, power * 3.0F, 1.0F);
        if (power == 1.0F) arrow.setCritArrow(true);

        // 1.21.1 起「力量 / 冲击 / 火矢」由发射武器承载：把弓作为 createArrow 的第 4 个参数
        // 传入后，原版会在命中时经 EnchantmentHelper.modifyDamage / modifyKnockback 施加伤害与击退，
        // 在生成时经 onProjectileSpawned 施加火矢（AbstractArrow 已无 setKnockback / setSecondsOnFire），
        // 与 1.20.1 手动加成的结果一致，故不再重复叠加。

        // 白得来的箭不该能被捡回，否则等于无限刷箭
        arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;

        bow.hurtAndBreak(1, player,
                player.getUsedItemHand() == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
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
        if (!(event.getEntity() instanceof Player player)) return;

        // ===== 弓（无限弓箭·放箭）=====
        // 1.21.1 的 BowItem.releaseUsing 把 ArrowLooseEvent 挪进了 if (!projectile.isEmpty())
        // 内部 —— 背包无箭时那个事件根本不会触发（详见 onArrowLoose 的说明），所以弓的无箭放箭
        // 只能在这一步自己接管：本事件正好在 ItemStack#releaseUsing 之前触发。
        // 判定与第一步 onRightClickItem 完全一致，避免「拉得起来但射不出去」这类半残状态。
        if (stack.getItem() instanceof BowItem) {
            if (player.isCreative()) return;                                // 原版已支持
            if (!player.getProjectile(stack).isEmpty()) return;             // 背包有箭，交回原版
            if (!hasInfiniteArrows(player)) return;
            // 已蓄力 tick 与原版同式：useDuration - 剩余 tick（强度阈值由 getPowerForTime 把）
            int charge = stack.getUseDuration(player) - event.getDuration();
            shootAmmoLessArrow(player, stack, charge, event.getEntity().level());
            return;
        }

        if (!(stack.getItem() instanceof CrossbowItem)) return;
        if (player.level().isClientSide()) return;                          // NBT 以服务端为准
        if (CrossbowItem.isCharged(stack)) return;                          // 已装上实弹，交回原版
        if (!player.getProjectile(stack).isEmpty()) return;                 // 背包有箭，交回原版
        if (!hasInfiniteArrows(player)) return;

        // 装填是否已蓄满：原版判据 getPowerForTime(已用 tick) >= 1.0F，等价于已用 >= 装填时长
        int used = stack.getUseDuration(player) - event.getDuration();
        if (used < CrossbowItem.getChargeDuration(stack, player)) return;   // 提前松手，不装填

        loadVirtualArrows(stack, player);

        // 原版的装填完成音效挂在 releaseUsing 的成功分支里，会被上面的短路跳过，这里补上
        Level level = player.level();
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.CROSSBOW_LOADING_END, SoundSource.PLAYERS, 1.0F,
                1.0F / (level.getRandom().nextFloat() * 0.5F + 1.0F) + 0.2F);
    }

    /**
     * 把虚拟箭写进弩的 {@code ChargedProjectiles} 数据组件，数量与原版 {@code tryLoadProjectiles} 一致
     * （多重射击 3 支、否则 1 支），否则玩家会感觉自己附的「多重射击」失效了。
     * <p>
     * 1.21.1 起装填物存放在 {@link DataComponents#CHARGED_PROJECTILES} 组件里，
     * {@code CrossbowItem.isCharged} 也据此判定，因此这里直接 set 组件即可（无需再调 setCharged）。
     * 整段不碰玩家背包，因此不会扣箭也不会凭空塞物品栏。
     */
    private static void loadVirtualArrows(ItemStack crossbow, Player player) {
        int count = EnchantmentHelper.getItemEnchantmentLevel(
                player.registryAccess().holderOrThrow(Enchantments.MULTISHOT), crossbow) > 0 ? 3 : 1;

        java.util.List<ItemStack> projectiles = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            projectiles.add(new ItemStack(Items.ARROW));
        }
        crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(projectiles));
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
        if (!(arrow.getOwner() instanceof Player player)) return;

        // 多重射击：为同一次射击补出额外的箭矢（复制体带标签，不会二次触发）
        if (player instanceof ServerPlayer shooter
                && !arrow.getTags().contains(MULTI_SHOT_CLONE)) {
            handleMultiShot(shooter, arrow, event.getLevel());
        }

        if (!arrow.shotFromCrossbow()) return;

        Long shotTick = VIRTUAL_CROSSBOW_SHOTS.get(player.getUUID());
        if (shotTick == null || shotTick != event.getLevel().getGameTime()) return;

        arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
    }

    // ========== 多重射击 ==========

    /** 多重射击复制体的标记（写进实体标签），避免复制体再触发一次复制。 */
    private static final String MULTI_SHOT_CLONE = "infinitestats_multi_shot_clone";
    /** 玩家 UUID → 最近一次补箭的 gameTime：同一次射击（同 tick）只补一轮。 */
    private static final Map<UUID, Long> MULTI_SHOT_TICKS = new ConcurrentHashMap<>();

    /**
     * 多重射击（{@code multi_shot}）：把首发箭矢复制成 N 支并按小角度散开。
     * <p>
     * 用 NBT 复制而不是自己 {@code new Arrow}，是为了让光谱箭、模组自定义箭矢也能被正确复制
     * （实体类型由 {@code id} 标签决定）；原版的箭矢 NBT 不保存发射者，所以复制后必须手动
     * {@link net.minecraft.world.entity.projectile.Projectile#setOwner}，否则伤害无法归因到玩家、
     * 一系列攻击加成都会失效。
     * <p>
     * 复制体一律设为 {@code CREATIVE_ONLY}：它们没消耗任何背包资源，落地被捡回就等于凭空刷箭。
     */
    private static void handleMultiShot(ServerPlayer shooter, AbstractArrow arrow, Level level) {
        PlayerStatsProvider.of(shooter).ifPresent(stats -> {
            int extra = (int) Math.round(stats.getStatValue("multi_shot"));
            if (extra <= 0) return;

            long now = level.getGameTime();
            Long last = MULTI_SHOT_TICKS.get(shooter.getUUID());
            if (last != null && last == now) return;
            MULTI_SHOT_TICKS.put(shooter.getUUID(), now);

            Vec3 velocity = arrow.getDeltaMovement();
            if (velocity.lengthSqr() < 1.0E-4) return;

            ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(arrow.getType());
            if (typeId == null) return;

            CompoundTag tag = arrow.saveWithoutId(new CompoundTag());
            tag.putString("id", typeId.toString());
            // 必须去掉 UUID：否则 N 支复制箭会与首发箭共用同一个实体 UUID，
            // 实体索引会互相覆盖，删除 / 存档都会出问题；去掉后新实体沿用构造时生成的随机 UUID。
            tag.remove("UUID");

            for (int i = 0; i < extra; i++) {
                // 左右交替散开，越后面的箭偏得越多
                double angle = Math.toRadians((i % 2 == 0 ? 1.0 : -1.0) * (3.0 + i * 2.0));
                double cos = Math.cos(angle);
                double sin = Math.sin(angle);
                Vec3 dir = new Vec3(velocity.x * cos - velocity.z * sin,
                        velocity.y, velocity.x * sin + velocity.z * cos);

                net.minecraft.world.entity.EntityType.create(tag, level).ifPresent(created -> {
                    if (!(created instanceof AbstractArrow copy)) return;
                    copy.setOwner(arrow.getOwner());
                    copy.setPos(arrow.getX(), arrow.getY(), arrow.getZ());
                    copy.setDeltaMovement(dir);
                    copy.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
                    copy.addTag(MULTI_SHOT_CLONE);
                    level.addFreshEntity(copy);
                });
            }
        });
    }

    /** 玩家背包（含副手）里是否还有弩能用的箭，判定口径与 {@code Player#getProjectile} 一致。 */
    private static boolean hasArrowInInventory(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(ItemTags.ARROWS)) return true;
        }
        return false;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(LivingDamageEvent.Pre event) {
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
            PlayerStatsProvider.of(player).ifPresent(stats -> {
                float amount = event.getNewDamage();

                // 暴击
                boolean isFullAttack = player.getAttackStrengthScale(0.5f) > 0.9f;
                amount *= AttackHandler.calculateCritMultiplier(player, stats, isFullAttack);

                // 弹射物伤害
                boolean isProjectile = event.getSource().getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile;
                amount *= AttackHandler.calculateProjectileBonus(stats, isProjectile);

                event.setNewDamage(amount);

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
                        event.setNewDamage(event.getNewDamage() + bonus);
                    }
                }

                // 记录减伤前的基数，最终扣血阶段再叠加，禁止在本次 hurt 内嵌套另一次 hurt。
                trueDamage[0] = AttackHandler.calculateTrueDamage(stats, event.getNewDamage());

                // 攻击降低目标最大生命值
                AttackHandler.applyReduceMaxHealth(player, stats, event.getEntity());

                // 范围攻击：波及周围敌人
                AttackHandler.applyScopeAttack(player, stats, event.getNewDamage(), event.getEntity());

                event.setNewDamage(AttackHandler.applyArmorPenetration(stats, event.getEntity(),
                        event.getSource(), event.getNewDamage()));

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
            PlayerStatsProvider.of(hurtPlayer).ifPresent(stats -> {
                float amount = event.getNewDamage();

                // 复活无敌窗口：取消一切伤害（最高优先级，防止复活瞬间原地再死）
                if (DefenseHandler.isReviveInvulnerable(hurtPlayer, stats)) {
                    event.setNewDamage(0f);
                    return;
                }

                // 无敌：取消所有伤害
                if (DefenseHandler.isInvincible(stats)) {
                    event.setNewDamage(0f);
                    return;
                }

                // 真实伤害遵循伤害类型的绕过效果标签，不再被本模组减伤或法力护盾削减。
                if (event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_EFFECTS)) return;

                // 检查免疫
                if (DefenseHandler.isImmune(stats, event.getSource(), hurtPlayer)) {
                    event.setNewDamage(0f);
                    return;
                }

                // 闪避
                if (DefenseHandler.handleDodge(hurtPlayer, stats)) {
                    event.setNewDamage(0f);
                    hurtPlayer.displayClientMessage(
                            net.minecraft.network.chat.Component.translatable("message.infinitestats.dodged"),
                            true
                    );
                    return;
                }

                // 格挡
                if (DefenseHandler.handleBlock(hurtPlayer, stats)) {
                    event.setNewDamage(0f);
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
                    event.setNewDamage(0f);
                    return;
                }

                event.setNewDamage(amount);

                // 伤害反射
                if (event.getSource().getEntity() instanceof LivingEntity attacker) {
                    DefenseHandler.applyDamageReflection(hurtPlayer, stats, event.getNewDamage(), attacker);
                }
            });
        }
        if (event.getNewDamage() > 0) {
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

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent.Post event) {
        float bonus = AttackHandler.takeTrueDamage(event.getEntity(), event.getSource());
        if (bonus > 0 && Float.isFinite(bonus)) {
            // 真伤改为直接扣血结算，不叠加进 event.getAmount()：
            // 叠加会与主伤害一起被后续的抗性提升 / 保护附魔 / 吸收护盾，
            // 以及其它模组的「单次伤害上限（限伤）」一并削减，导致真伤形同虚设。
            // event.getAmount() > 0 表示主伤害有效，原版随后会走完 setHealth 与死亡判定，
            // 死亡 / 掉落 / 击杀归属仍由原版收尾。
            AttackHandler.applyUnavoidableTrueDamage(event.getEntity(), bonus, event.getNewDamage() > 0);
        }
        // 生命偷取：此时护甲与减伤已结算，用实际伤害作为基数（而非 LivingHurtEvent 的面板值）。
        // 被取消的伤害（格挡 / 闪避 / 无敌）会在此处提前返回，因此不会出现「打空也吸血」。
        // 次级直接伤害（范围攻击 / 真伤 / 降上限）不是玩家主动攻击，不计入吸血。
        if (event.getNewDamage() > 0 && !AttackHandler.isDirectDamageSource(event.getSource())) {
            ServerPlayer attacker = getPlayerAttacker(event.getSource());
            if (attacker != null) {
                PlayerStatsProvider.of(attacker).ifPresent(stats -> {
                    AttackHandler.applyLifeSteal(attacker, stats, event.getNewDamage());
                    // 额外打击：在本次伤害之外追加 N 次独立结算的属性伤害
                    AttackHandler.applyExtraStrikes(stats, event.getNewDamage(), event.getEntity());
                });
            }
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerStatsProvider.of(player).ifPresent(
                    stats -> DefenseHandler.trackAbsorptionUse(player, stats));
        }
    }

    @SubscribeEvent
    public static void onServerTickEnd(ServerTickEvent.Post event) {
        // 后续模组取消伤害或提前返回时可能没有 LivingDamage；不将残余记录带入下一 tick。
        AttackHandler.clearPendingTrueDamage();
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

        PlayerStatsProvider.of(player).ifPresent(stats -> {
            if (!stats.isToggleActive("debuff_immunity")) return;

            String effectId = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT
                    .getKey(event.getEffectInstance().getEffect().value()).toString();

            // 绝不拦截本模组自己施加的效果（夜视/隐身/幸运等 Utility toggle）
            if (isOwnUtilityEffect(stats, effectId)) return;

            if (stats.shouldBlockEffect(effectId)) {
                event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
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
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        long tickCount = player.tickCount;

        // 使用HandlerRegistry处理所有效果（含跳跃检测、被动经验，每玩家独立计数）
        PlayerStatsProvider.of(player).ifPresent(stats -> {
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
            // 随身酿造台：同理，关掉界面也继续酿（一轮 400 tick，进度存在玩家数据里）
            try {
                stats.getBrewingData().tick(player.level(), player);
            } catch (Throwable t) {
                t.printStackTrace();
            }
            // 生物面板：每秒对齐一次观察杖（缺则补发、属性关闭后收回），
            // 所以刚解锁 / 死亡掉落 / 木棍被烧掉都能在一秒内自愈
            try {
                if (tickCount % 20 == 0) EntityPanelWand.sync(player, stats);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });
    }

    // ========== 挖掘速度 ==========

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        PlayerStatsProvider.of(event.getEntity()).ifPresent(stats -> {
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
                        && EnchantmentHelper.getEnchantmentLevel(
                                event.getEntity().registryAccess().holderOrThrow(Enchantments.AQUA_AFFINITY),
                                event.getEntity()) <= 0) {
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

        PlayerStatsProvider.of(player).ifPresent(stats -> {
            // 需要事件本身：自动冶炼 / 掉落进背包要取消原版破坏并接管掉落
            UtilityHandler.handleVeinMinerAndAutoSmelt(player, stats, pos, state, level, event);
        });
    }

    // ========== 挖掘等级加成 ==========

    /**
     * 1.21.1 移除了 {@code TierSortingRegistry}，用原版固定顺序（低→高）替代其排序列表。
     * 未纳入 {@code Tiers.GOLD}，与 {@link #getToolTierIndex} 返回 -1 时的兜底处理保持一致。
     */
    private static final java.util.List<net.minecraft.world.item.Tier> SORTED_TIERS = java.util.List.of(
            net.minecraft.world.item.Tiers.WOOD,
            net.minecraft.world.item.Tiers.STONE,
            net.minecraft.world.item.Tiers.IRON,
            net.minecraft.world.item.Tiers.DIAMOND,
            net.minecraft.world.item.Tiers.NETHERITE);

    @SubscribeEvent
    public static void onHarvestCheck(PlayerEvent.HarvestCheck event) {
        if (event.canHarvest()) return;

        PlayerStatsProvider.of(event.getEntity()).ifPresent(stats -> {
            int bonus = UtilityHandler.getMiningLevelBonus(stats);
            if (bonus <= 0) return;

            ItemStack tool = event.getEntity().getMainHandItem();
            int toolIndex = getToolTierIndex(tool);

            // 空手或手持非工具时，视为最低等级（索引 0），使挖掘等级加成能生效
            if (toolIndex < 0) {
                toolIndex = 0;
            }

            java.util.List<net.minecraft.world.item.Tier> sortedTiers = SORTED_TIERS;
            int enhancedIndex = Math.min(toolIndex + bonus, sortedTiers.size() - 1);
            if (enhancedIndex <= toolIndex) return;

            net.minecraft.world.item.Tier enhancedTier = sortedTiers.get(enhancedIndex);
            if (!event.getTargetBlock().is(enhancedTier.getIncorrectBlocksForDrops())) {
                event.setCanHarvest(true);
            }
        });
    }

    /**
     * 获取手持工具在 {@link #SORTED_TIERS} 排序列表中的索引
     * 空手/非工具 = -1
     */
    private static int getToolTierIndex(ItemStack tool) {
        if (!(tool.getItem() instanceof TieredItem ti)) return -1;
        return SORTED_TIERS.indexOf(ti.getTier());
    }

    // ========== 玩家登录 ==========

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        PlayerStatsProvider.of(player).ifPresent(stats -> {
            // 重新计算可用点数，修复旧存档可能为负的问题
            stats.recalculateAvailablePoints();
            // 「功能开关」：首次加载时把作者预设的「开局默认禁用」列表写入本玩家（只应用一次）
            stats.applyDefaultDisabledStatsIfNeeded();
            NetworkHandler.syncToClient(player);
            HandlerRegistry.loginAll(player, stats);
        });
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        // 虚拟弩箭的发射标记只在「当个 tick」有意义，玩家离开时顺手清掉，避免长期留存
        VIRTUAL_CROSSBOW_SHOTS.remove(event.getEntity().getUUID());
        AMMO_LESS_SHOTS.remove(event.getEntity().getUUID());
        MULTI_SHOT_TICKS.remove(event.getEntity().getUUID());
        AutoFish.forget(event.getEntity().getUUID());
    }

    // ========== 玩家重生 ==========

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        PlayerStatsProvider.of(player).ifPresent(stats -> {
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
        PlayerStatsProvider.of(event.getOriginal()).ifPresent(oldStats -> {
            PlayerStatsProvider.of(event.getEntity()).ifPresent(newStats -> {
                newStats.copyFrom(oldStats);
                // 保护快照：换维度（非死亡）必搬；死亡重生看玩家的「重生继承保护」开关
                newStats.inheritProtection(oldStats, event.isWasDeath());
            });
        });

        // 死亡不掉落：把死亡时暂存的背包与饰品还给重生后的新实体
        if (event.isWasDeath()) {
            List<ItemStack> saved = KEPT_INVENTORIES.remove(event.getEntity().getUUID());
            if (saved != null) restoreKeptInventory(event.getEntity(), saved);

            List<CompoundTag> curios = KEPT_CURIOS.remove(event.getEntity().getUUID());
            if (curios != null) CuriosBridge.restore(event.getEntity(), curios);
        }
    }

    // ========== 维度切换 ==========

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        PlayerStatsProvider.of(player).ifPresent(stats -> {
            NetworkHandler.syncToClient(player);
            HandlerRegistry.dimensionChangeAll(player, stats);
        });
    }

    // ========== 使用速度加速 ==========

    @SubscribeEvent
    public static void onItemUseTick(LivingEntityUseItemEvent.Tick event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getDuration() <= 0) return;

        PlayerStatsProvider.of(player).ifPresent(stats -> {
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

        PlayerStatsProvider.of(player).ifPresent(stats -> {
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
    private static ServerPlayer getPlayerAttacker(LivingDamageEvent.Pre event) {
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
