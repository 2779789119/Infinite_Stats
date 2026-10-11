package com.infinitestats.handler;

import com.infinitestats.Config;
import com.infinitestats.mixin.MerchantMenuAccessor;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.StatCategory;
import com.infinitestats.stats.StatType;
import com.infinitestats.stats.VeinMinerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.level.BlockEvent;

import java.util.*;

/**
 * 功能类属性处理器
 * 处理：夜视、水下呼吸、免饥饿、物品磁铁、隐身、连锁挖掘、自动冶炼、经验磁铁等
 */
public class UtilityHandler implements StatEffectHandler {

    @Override
    public String getId() {
        return "utility";
    }

    @Override
    public StatType[] getSupportedStats() {
        return StatType.getByCategory(StatCategory.UTILITY);
    }

    @Override
    public void onTick(ServerPlayer player, PlayerStats stats, long tickCount) {
        // 夜视：每 tick 强制维持（forceAddEffect 绕过其他模组对 Applicable 事件的拦截/清除）
        applyNightVision(player, stats);

        // 每5tick处理磁铁和效果
        if (tickCount % 5 == 0) {
            applyItemMagnet(player, stats);
            applyXpMagnet(player, stats);
            applyInvisibility(player, stats);
        }

        // 每 4 tick（约 5 次/秒）处理弹射物追踪：降低扫描频率即可大幅降低开销，
        // 对追踪平滑度影响极小
        if (tickCount % 4 == 0) {
            applyProjectileTracking(player, stats);
        }

        // 每2秒处理呼吸、饥饿和幸运
        if (tickCount % 40 == 0) {
            applyWaterBreathing(player, stats);
            applyLootLuck(player, stats);
        }

        // 每0.5秒处理饥饿
        if (tickCount % 10 == 0) {
            applyNoHunger(player, stats);
        }

        // 每1秒处理自动修理
        if (tickCount % 20 == 0) {
            applyAutoRepair(player, stats);
        }

        // 自动入库：按配置间隔扫描背包，把物品写入存储网络（隔离异常，避免影响其它功能）
        int depositInterval = Math.max(1, Config.AUTO_DEPOSIT_INTERVAL.get());
        if (tickCount % depositInterval == 0 && stats.isToggleActive("auto_deposit")) {
            try {
                com.infinitestats.compat.AutoDeposit.deposit(player, stats);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        // 交易即刻补货：正在与村民交易时把已消耗的条目立刻补满
        if (tickCount % 5 == 0 && stats.isToggleActive("trade_restock")) {
            try {
                restockTrade(player);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        // 钓鱼：自动钓鱼（主手持竿自动抛竿、咬钩即收杆）+ 立即咬钩（压缩咬钩前的两段等待）。
        // 每 tick 检查，因为咬钩窗口只有 20~40 tick
        if (stats.isToggleActive("auto_fish") || stats.isToggleActive("instant_bite")) {
            try {
                AutoFish.tick(player, stats);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        // 火焰免疫时清除火焰
        if (stats.isToggleActive("fire_immunity")) {
            player.clearFire();
        }

        // Goety 联动（无限灵魂能量）：每 tick 兜底 —— 灵魂能量被消耗后立刻补满。
        // 没解锁 / 没装诡厄巫法时在 GoetyCompat 内短路，零开销。
        if (stats.isToggleActive("goety_infinite_soul")) {
            try {
                com.infinitestats.compat.GoetyCompat.onTick(player, stats);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        // 无限能源（通用 FE）：每 tick 兜底 —— 背包 / 饰品 / 骑乘实体的 FE 能量源
        // （RF / Mekanism / 热力 / EIO / 沉浸工程等最终都走 Forge Energy）保持满电；
        // 玩家自己放置的能量方块按配置间隔扫描补满（见 EnergyCompat）。
        // 没解锁时在 EnergyCompat 内第一步短路，零开销。
        if (stats.isToggleActive("infinite_energy")) {
            try {
                com.infinitestats.compat.EnergyCompat.onTick(player, stats, tickCount);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        // 枪械 / 载具模组联动（需要安装对应模组，未安装时在第一步短路、零开销）：
        //   TACZ          —— 补满弹匣 / 清空热量
        //   SuperbWarfare —— 枪械：补满弹匣 / 清空热量；载具：补满血量
        //                    （「不再变少」那一半在注入点里完成，这里只兜「解锁前就已经掉了」的）
        if (tickCount % 5 == 0) {
            try {
                com.infinitestats.compat.TaczCompat.onTick(player, stats);
            } catch (Throwable t) {
                t.printStackTrace();
            }
            try {
                com.infinitestats.compat.SuperbWarfareCompat.onTick(player, stats);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    @Override
    public void onLogin(ServerPlayer player, PlayerStats stats) {
        applyNightVision(player, stats);
        applyInvisibility(player, stats);
    }

    @Override
    public void onRespawn(ServerPlayer player, PlayerStats stats) {
        applyNightVision(player, stats);
        applyInvisibility(player, stats);
    }

    @Override
    public void onDimensionChange(ServerPlayer player, PlayerStats stats) {
        applyNightVision(player, stats);
        applyInvisibility(player, stats);
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    /**
     * 交易即刻补货：玩家正在与村民 / 流浪商人交易时，把已消耗过的报价条目立刻重置为可交易，
     * 并把新的报价列表重新推给客户端（否则界面仍显示成"已售罄"）。
     * <p>
     * 刻意不走 {@code Villager#restock()}：那条路会顺手 {@code updateDemand()}，
     * 反复调用会把需求加价一路顶上去、越补越贵；这里只重置 {@code uses}，价格保持不变。
     * <p>
     * <b>重推报价必须带商人实体侧的等级 / 经验</b>：{@code MerchantMenu#getTraderLevel()}、
     * {@code showProgressBar()}、{@code canRestock()} 这三个都是**客户端**由
     * {@code ClientPacketListener#handleMerchantOffers} 写入的字段，服务端恒为 0 / false。
     * 早先照抄菜单 getter，等于把客户端的「等级 N」标题与经验进度条一起抹掉（"村民经验等级消失"的成因）。
     * 原版 {@code Merchant#openTradingScreen} 用的也正是实体侧的值，这里与之对齐。
     */
    private static void restockTrade(ServerPlayer player) {
        if (!(player.containerMenu instanceof MerchantMenu menu)) return;

        MerchantOffers offers = menu.getOffers();
        if (offers == null || offers.isEmpty()) return;

        boolean changed = false;
        for (int i = 0; i < offers.size(); i++) {
            MerchantOffer offer = offers.get(i);
            if (offer.getUses() > 0) {
                offer.resetUses();
                changed = true;
            }
        }
        if (!changed) return;

        Merchant trader = ((MerchantMenuAccessor) menu).infinitestats$getTrader();
        player.sendMerchantOffers(menu.containerId, offers, traderLevel(trader), trader.getVillagerXp(),
                trader.showProgressBar(), trader.canRestock());
    }

    /**
     * 原版 {@code Merchant#openTradingScreen} 下发的等级：村民取职业等级，流浪商人固定为 1
     * （{@code WanderingTrader} 的 {@code showProgressBar()} 为 false，等级本身不显示）。
     */
    private static int traderLevel(Merchant trader) {
        return trader instanceof Villager villager ? villager.getVillagerData().getLevel() : 1;
    }

    /**
     * 应用夜视效果
     * toggle ON → 始终确保夜视生效；toggle OFF → 仅当由本模组提供时才移除
     * 优化：只在玩家没有夜视效果时才施加，避免反复刷新
     */
    private void applyNightVision(ServerPlayer player, PlayerStats stats) {
        boolean nightVision = stats.isToggleActive("night_vision");
        boolean weProvided = stats.isProviding("night_vision");

        if (nightVision) {
            // 使用 forceAddEffect 直接写入效果，不触发 MobEffectEvent.Applicable，
            // 从而绕过其他模组（如免疫类/夜视管理类）对该事件的 DENY 拦截。
            // 每 tick 调用一次，保证效果被中途清除时立即补回、无闪烁。
            player.forceAddEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,
                    MobEffectInstance.INFINITE_DURATION, 0, false, false, true), player);
            stats.setProviding("night_vision", true);
        } else if (weProvided) {
            player.removeEffect(MobEffects.NIGHT_VISION);
            stats.setProviding("night_vision", false);
        }
    }

    /**
     * 应用水下呼吸
     */
    private void applyWaterBreathing(ServerPlayer player, PlayerStats stats) {
        boolean waterBreathing = stats.isToggleActive("water_breathing");
        if (waterBreathing) {
            player.setAirSupply(player.getMaxAirSupply());
            player.removeEffect(MobEffects.WATER_BREATHING);
        }
    }

    /**
     * 应用免饥饿
     */
    private void applyNoHunger(ServerPlayer player, PlayerStats stats) {
        boolean noHunger = stats.isToggleActive("no_hunger");
        if (noHunger) {
            FoodData food = player.getFoodData();
            food.setFoodLevel(20);
            food.setSaturation(20.0f);
            food.setExhaustion(0.0f);
        }
    }

    /**
     * 应用隐身
     * 使用原版隐身药水效果，同时 setInvisible 作为备用视觉机制。
     * 原因：药水效果负责怪物 AI（降低检测范围）和渲染（含盔甲/手持物品），
     *       setInvisible 作为视觉备份，防止其他模组清除药水效果时导致模型闪现。
     * 优化：只在玩家没有隐身效果时才重新施加，避免每 5 tick 无意义刷新触发 mod 冲突。
     * 保护：关闭时只移除"自己的"隐身效果（特征：无限时长+等级0），避免误删其他模组的隐身。
     * toggle ON → 始终确保隐身生效；toggle OFF → 仅当效果属于本模组时才移除
     */
    private void applyInvisibility(ServerPlayer player, PlayerStats stats) {
        boolean invis = stats.isToggleActive("invisibility");
        boolean weProvided = stats.isProviding("invisibility");

        if (invis) {
            // 使用 forceAddEffect 直接写入效果，不触发 MobEffectEvent.Applicable，
            // 从而绕过其他模组对隐身效果的 DENY 拦截。
            // 仅在效果缺失时施加，避免每 5 tick 无意义刷新。
            if (!player.hasEffect(MobEffects.INVISIBILITY)) {
                player.forceAddEffect(new MobEffectInstance(MobEffects.INVISIBILITY,
                        MobEffectInstance.INFINITE_DURATION, 0, false, false, true), player);
            }
            // setInvisible 作为备用视觉机制：即使药水效果被其他模组清除，玩家模型仍不可见
            player.setInvisible(true);
            stats.setProviding("invisibility", true);
        } else if (weProvided) {
            // 关键：只移除"我们自己的"隐身效果
            // 通过检查当前效果的参数（无限时长、等级0）来判断是否由本模组施加
            // 如果参数不匹配，说明隐身已被其他模组重新施加，绝不应移除
            MobEffectInstance currentEffect = player.getEffect(MobEffects.INVISIBILITY);
            boolean isOurEffect = currentEffect != null
                    && currentEffect.isInfiniteDuration()
                    && currentEffect.getAmplifier() == 0;

            if (isOurEffect) {
                player.removeEffect(MobEffects.INVISIBILITY);
            }
            // 只有在确实没有隐身效果（来自任何模组）时才关闭 setInvisible 标志
            if (!player.hasEffect(MobEffects.INVISIBILITY)) {
                player.setInvisible(false);
            }
            stats.setProviding("invisibility", false);
        }
    }

    /**
     * 应用物品磁铁 — 立即收集范围内的掉落物
     * 范围由配置文件 magnetRange 决定（默认 10 格，3-50 可调）
     */
    private void applyItemMagnet(ServerPlayer player, PlayerStats stats) {
        if (!stats.isToggleActive("item_magnet")) return;

        double range = Config.MAGNET_RANGE.get();

        AABB area = new AABB(
                player.getX() - range, player.getY() - range, player.getZ() - range,
                player.getX() + range, player.getY() + range, player.getZ() + range
        );

        List<ItemEntity> items = player.level().getEntitiesOfClass(ItemEntity.class, area);

        for (ItemEntity item : items) {
            if (item.isRemoved() || item.hasPickUpDelay()) continue;
            item.playerTouch(player);
        }
    }

    /**
     * 应用经验磁铁 — 范围由配置文件 magnetRange 决定
     */
    private void applyXpMagnet(ServerPlayer player, PlayerStats stats) {
        if (!stats.isToggleActive("xp_magnet")) return;

        double range = Config.MAGNET_RANGE.get();
        AABB area = new AABB(
                player.getX() - range, player.getY() - range, player.getZ() - range,
                player.getX() + range, player.getY() + range, player.getZ() + range
        );

        List<ExperienceOrb> orbs = player.level().getEntitiesOfClass(ExperienceOrb.class, area);
        Vec3 playerPos = player.position();

        for (ExperienceOrb orb : orbs) {
            Vec3 diff = playerPos.subtract(orb.position());
            double dist = diff.length();
            if (dist > 1.0) {
                orb.setDeltaMovement(diff.normalize().scale(0.5));
            }
        }
    }

    /**
     * 弹射物追踪 — 让玩家发射的弹射物自动转向追踪敌人。
     * <p>
     * 目标筛选按两级进行：
     * <ol>
     *   <li><b>隔着方块的直接排除</b>：用 {@code player.hasLineOfSight} 判断玩家与目标之间
     *       是否被方块挡住，挡住的不追 —— 追过去也只会撞墙；</li>
     *   <li><b>视野锥内的优先</b>：同样看得见的目标里，落在玩家前方约 ±60° 视锥内的先选；
     *       视锥内一个都没有时才退回到视锥外但仍可见的目标。</li>
     * </ol>
     * 性能：仍在玩家周围「配置半径」内扫描（避免全维度实体遍历）。视线与视锥判定都放在
     * 弹射物循环<b>之外</b>、每个敌人只算一次，不随弹射物数量重复计算；
     * {@code hasLineOfSight} 内部自带 128 格上限与一次方块射线，开销可控。
     * 调用频率由 onTick 节流为每 4 tick 一次。
     */
    private void applyProjectileTracking(ServerPlayer player, PlayerStats stats) {
        if (!stats.isToggleActive("projectile_tracking")) return;

        Level level = player.level();
        double range = Config.PROJECTILE_TRACKING_RANGE.get();
        // 以玩家为中心的有限扫描范围，避免对全维度实体做 O(实体数) 遍历
        AABB area = new AABB(
                player.getX() - range, player.getY() - range, player.getZ() - range,
                player.getX() + range, player.getY() + range, player.getZ() + range);

        // 获取玩家发射的弹射物（限定在扫描范围内）
        List<Projectile> projectiles = level.getEntitiesOfClass(Projectile.class, area,
                p -> p.getOwner() == player && !p.isRemoved());
        if (projectiles.isEmpty()) return;

        // 获取扫描范围内的敌对生物
        List<Mob> enemies = level.getEntitiesOfClass(Mob.class, area,
                m -> m instanceof Enemy && m.isAlive() && !m.isRemoved());
        if (enemies.isEmpty()) return;

        // 先筛掉隔墙的，再分出视野锥内的两个候选池。二者都不依赖具体弹射物，
        // 提前算好供下面复用，避免每支弹射物把同一批敌人重算一遍。
        List<Mob> visible = new ArrayList<>();
        List<Mob> inView = new ArrayList<>();
        for (Mob enemy : enemies) {
            if (!player.hasLineOfSight(enemy)) continue;
            visible.add(enemy);
            if (isInPlayerViewCone(player, enemy)) inView.add(enemy);
        }
        if (visible.isEmpty()) return;

        for (Projectile proj : projectiles) {
            if (proj.isRemoved()) continue;

            // 视野内的优先；一个都没有时再退回到视野外但仍可见的目标
            LivingEntity target = closestTo(proj, inView);
            if (target == null) target = closestTo(proj, visible);
            if (target == null) continue;

            // 计算追踪方向，瞄准身体中部
            Vec3 projPos = proj.position();
            Vec3 targetPos = target.position().add(0, target.getBbHeight() * 0.6, 0);
            Vec3 direction = targetPos.subtract(projPos).normalize();

            // 直接重定向速度到目标方向，保持原速度
            double currentSpeed = proj.getDeltaMovement().length();
            proj.setDeltaMovement(direction.scale(currentSpeed));
        }
    }

    /** 视锥半角余弦，0.5 约等于玩家正前方 ±60°。 */
    private static final double VIEW_CONE_COS = 0.5;

    /** 目标是否落在玩家前方的视锥内。 */
    private static boolean isInPlayerViewCone(ServerPlayer player, LivingEntity target) {
        Vec3 look = player.getViewVector(1.0f).normalize();
        Vec3 toTarget = target.getEyePosition().subtract(player.getEyePosition());
        // 贴脸时方向向量退化，直接视作在视野内
        if (toTarget.lengthSqr() < 1.0E-4) return true;
        return look.dot(toTarget.normalize()) >= VIEW_CONE_COS;
    }

    /** 在候选集合里找离弹射物最近的一个；集合为空时返回 {@code null}。 */
    private static LivingEntity closestTo(Projectile proj, List<Mob> candidates) {
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Mob candidate : candidates) {
            double dist = proj.distanceToSqr(candidate);
            if (dist < bestDist) {
                bestDist = dist;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * 自动修理装备（auto_repair 开关 + repair_amount 控制修理量）
     * 每秒修理玩家背包和装备栏中所有可损坏物品
     */
    private void applyAutoRepair(ServerPlayer player, PlayerStats stats) {
        if (!stats.isToggleActive("auto_repair")) return;

        // 用「有效点数」：repair_amount 被「功能开关」关闭时按 0 计（退化为最小修理量 1）
        long repairAmount = stats.getEffectiveStatLevel("repair_amount");
        if (repairAmount <= 0) repairAmount = 1;

        // 修理所有物品栏（主物品栏 + 盔甲 + 副手 + Curios饰品）
        List<ItemStack> allItems = new ArrayList<>();
        allItems.addAll(player.getInventory().items);
        allItems.addAll(player.getInventory().armor);
        allItems.addAll(player.getInventory().offhand);

        for (ItemStack stack : allItems) {
            if (!stack.isEmpty() && stack.isDamaged()) {
                stack.setDamageValue(Math.max(0, stack.getDamageValue() - (int) repairAmount));
            }
        }
    }

    // ========== 连锁挖掘 & 自动冶炼 ==========

    /**
     * 查询某个掉落物的熔炼结果（数据驱动，兼容任意模组矿石）。
     * 优先匹配高炉（Blasting）配方，其次回退普通熔炉（Smelting）配方。
     * 所有注册了熔炼配方的矿物（包括模组矿、深层变体等）都会被正确冶炼。
     */
    private static ItemStack getSmeltResult(ItemStack stack, ServerLevel level) {
        if (stack.isEmpty()) return ItemStack.EMPTY;

        SimpleContainer inv = new SimpleContainer(1);
        inv.setItem(0, stack);

        RecipeManager rm = level.getRecipeManager();
        AbstractCookingRecipe recipe = rm.getRecipeFor(RecipeType.BLASTING, inv, level).orElse(null);
        if (recipe == null) {
            recipe = rm.getRecipeFor(RecipeType.SMELTING, inv, level).orElse(null);
        }
        if (recipe == null) return ItemStack.EMPTY;

        ItemStack result = recipe.getResultItem(level.registryAccess());
        return result == null ? ItemStack.EMPTY : result;
    }

    /**
     * 连锁流程的递归闸门。
     * <p>
     * 非接管模式下，连锁方块走 {@code player.gameMode.destroyBlock}，它会再触发一次方块破坏事件；
     * 没有这道闸门就会「连锁里的连锁」层层扩散（每一层都重新 BFS 一遍）。
     */
    private static final ThreadLocal<Boolean> CHAINING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /**
     * 处理连锁挖掘和自动冶炼。
     * <p>
     * 分工是这样定的（旧实现自己在事件里补主方块掉落，而原版的掉落发生得更晚，于是主方块掉两份）：
     * <ul>
     *   <li>{@code auto_smelt} 关闭 → 完全不碰掉落。主方块照旧由原版处理；连锁方块用
     *       {@code player.gameMode.destroyBlock} 走完整原版流程，保护类模组的事件、统计、
     *       耐久、经验一律正确。</li>
     *   <li>{@code auto_smelt} 开启 → 取消事件、由本模组接管这一批方块的破坏与掉落
     *       （不接管就没法往掉落里额外塞一份成品），接管时自行补发经验、统计与工具耐久。</li>
     * </ul>
     *
     * @param event        触发这次处理的事件；接管时会被取消
     * @param expFromEvent 原版为这次破坏算好的经验，接管时用它补发
     */
    public static void handleVeinMinerAndAutoSmelt(ServerPlayer player, PlayerStats stats,
            BlockPos pos, BlockState state, ServerLevel level, BlockEvent.BreakEvent event) {
        // 连锁过程中触发的破坏事件：交给原版处理这一格即可，不再二次扩散
        if (CHAINING.get()) return;

        boolean autoSmelt = stats.isToggleActive("auto_smelt");
        boolean veinMiner = stats.isToggleActive("vein_miner");

        VeinMinerConfig cfg = stats.getVeinMinerConfig();
        if (veinMiner && cfg.isRequireSneak() && !player.isShiftKeyDown()) veinMiner = false;
        if (!veinMiner && !autoSmelt) return;

        ItemStack tool = player.getMainHandItem();
        // 「自动冶炼」与「掉落直接进背包」都需要自己掌管掉落，因此都会接管这一批方块
        boolean takeover = autoSmelt || cfg.isCollectDrops();

        CHAINING.set(Boolean.TRUE);
        try {
            // 主方块：接管时才由本模组破坏（并额外产出成品 / 直接进背包），否则原封不动交给原版
            if (takeover) {
                event.setCanceled(true);
                breakBlock(player, level, pos, state, tool, true, event.getExpToDrop(), cfg, autoSmelt);
            }

            if (!veinMiner) return;

            List<BlockPos> targets = VeinMinerLogic.collectTargets(level, cfg, pos, state, tool);
            int limit = VeinMinerLogic.effectiveMaxBlocks(cfg);
            int mined = 0;
            double xpAccum = 0.0D;

            for (BlockPos target : targets) {
                if (mined >= limit) break;

                ItemStack current = player.getMainHandItem();
                if (current.isEmpty()) break;
                // 工具保护：耐久只剩最后 1 点时收手，避免连锁把工具挖爆
                if (cfg.isProtectTool() && current.isDamageableItem()
                        && current.getDamageValue() >= current.getMaxDamage() - 1) break;

                if (takeover) {
                    BlockState targetState = level.getBlockState(target);
                    if (targetState.isAir()) continue;
                    breakBlock(player, level, target, targetState, current, false, 0, cfg, autoSmelt);
                } else {
                    // 走完整原版流程：其他模组的破坏事件与保护、统计、耐久、经验都由原版负责
                    if (!player.gameMode.destroyBlock(target)) continue;
                }
                mined++;

                // 代价：饥饿与经验按「实际破坏的方块数」结算
                if (cfg.getExhaustionPerBlock() > 0.0D) {
                    player.causeFoodExhaustion((float) cfg.getExhaustionPerBlock());
                }
                if (cfg.getXpPerBlock() > 0.0D) {
                    xpAccum += cfg.getXpPerBlock();
                    int whole = (int) xpAccum;
                    if (whole > 0) {
                        xpAccum -= whole;
                        player.giveExperiencePoints(-whole);
                    }
                }
            }
        } finally {
            CHAINING.set(Boolean.FALSE);
        }
    }

    /**
     * 接管模式下破坏一格方块（主方块与连锁方块共用）。
     * <p>
     * 掉落自己算、自己发，所以处理器（时运 / 精准采集）依旧生效；精准采集时不冶炼 ——
     * 免得玩家专门附的精准采集白搭。
     * 开了「掉落直接进背包」时掉落直接塞进背包，塞不下（背包满 / 单格上限）的那部分才照常掉在地上。
     * <p>
     * 自动冶炼是**额外产出**：手挖的那一格与被连锁带走的方块一视同仁 —— 原矿照掉，
     * 另加一份（有熔炼配方的给成品，没有配方的原样再给一份原矿）。
     */
    private static void breakBlock(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state,
            ItemStack tool, boolean primary, int expFromEvent, VeinMinerConfig cfg, boolean autoSmelt) {
        List<ItemStack> drops = Block.getDrops(state, level, pos,
                level.getBlockEntity(pos), player, tool);
        if (autoSmelt && !hasSilkTouch(tool)) {
            addSmeltedDrops(drops, level);
        }

        level.destroyBlock(pos, false, player);
        for (ItemStack drop : drops) {
            if (cfg.isCollectDrops() && player.getInventory().add(drop)) {
                continue;
            }
            Block.popResource(level, pos, drop);
        }

        int exp = primary ? expFromEvent
                : state.getBlock().getExpDrop(state, level, level.random, pos, 0, 0);
        if (exp > 0) player.giveExperiencePoints(exp);

        // 原版这两项统计/耐久是在破坏流程里顺手做的，接管后由我们补上
        player.awardStat(Stats.BLOCK_MINED.get(state.getBlock()));
        if (!tool.isEmpty()) {
            player.awardStat(Stats.ITEM_USED.get(tool.getItem()));
        }

        if (tool.isDamageableItem()) {
            tool.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        }
    }

    private static boolean hasSilkTouch(ItemStack tool) {
        return EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, tool) > 0;
    }

    /**
     * 「额外产出」冶炼：为掉落里每一项**再补一份** ——
     * <ul>
     *   <li>有熔炼配方的：补成品（铁矿 → 原矿 + 铁锭）；</li>
     *   <li>没有熔炼配方的：**原样再给一份**（钻石、泥土这类就是两份原矿）。</li>
     * </ul>
     * 后半条是**特意保留**的旧版行为：旧实现是「原版掉一份原始掉落 + 本模组补一份」，
     * 所以没配方的方块自然也是两份 —— 于是「自动冶炼」实际上让每一格都是双份产出。
     * <p>
     * 有配方时数量按时运后的原掉落数量 × 配方产出倍数算（时运挖出 3 个粗铁矿 → 再给 3 个铁锭）。
     * 先收集再追加，避免边遍历边往同一个列表里塞。
     */
    private static void addSmeltedDrops(List<ItemStack> drops, ServerLevel level) {
        List<ItemStack> extra = new ArrayList<>();
        for (ItemStack stack : drops) {
            ItemStack result = getSmeltResult(stack, level);
            if (result.isEmpty()) {
                extra.add(stack.copy());
                continue;
            }
            ItemStack out = result.copy();
            out.setCount(result.getCount() * stack.getCount());
            extra.add(out);
        }
        drops.addAll(extra);
    }

    /**
     * 计算挖掘速度加成
     */
    public static float getMiningSpeedMultiplier(PlayerStats stats) {
        return 1.0f + stats.getStatValue(StatType.fromId("mining_speed"));
    }

    /**
     * 获取挖掘等级加成（每级 +1 挖掘等级）
     */
    public static int getMiningLevelBonus(PlayerStats stats) {
        return (int) stats.getStatValue(StatType.fromId("mining_level"));
    }

    /**
     * 计算经验获取加成
     */
    public static long applyXpGainBonus(PlayerStats stats, long baseXp) {
        float multiplier = 1.0f + stats.getStatValue(StatType.fromId("xp_gain"));
        return (long) (baseXp * multiplier);
    }

    /**
     * 应用掉落物幸运加成
     * 根据 loot_luck 属性值给予对应等级的幸运效果
     */
    private void applyLootLuck(ServerPlayer player, PlayerStats stats) {
        float luckValue = stats.getStatValue(StatType.fromId("loot_luck"));
        int luckLevel = (int) (luckValue * 100); // 转换为整数等级

        boolean weProvided = stats.isProviding("loot_luck");

        if (luckLevel > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.LUCK,
                    MobEffectInstance.INFINITE_DURATION, luckLevel - 1, false, false, true));
            stats.setProviding("loot_luck", true);
        } else if (weProvided) {
            player.removeEffect(MobEffects.LUCK);
            stats.setProviding("loot_luck", false);
        }
    }

    /**
     * 获取掉落物幸运等级（供外部查询）
     */
    public static int getLootLuckLevel(PlayerStats stats) {
        float luckValue = stats.getStatValue(StatType.fromId("loot_luck"));
        return (int) (luckValue * 100);
    }
}