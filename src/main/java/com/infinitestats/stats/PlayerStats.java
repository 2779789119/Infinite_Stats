package com.infinitestats.stats;

import com.infinitestats.Config;
import com.infinitestats.crafting.PortableBrewingData;
import com.infinitestats.furnace.PlayerFurnaceData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.*;

/**
 * 玩家属性数据 - 使用 Map 存储以支持动态属性数量
 * 支持快速查找、批量操作和值缓存
 */
public class PlayerStats {

    // ========== 核心数据 ==========

    private long level = 1;
    private long experience = 0;
    private long availablePoints = 0;
    private long lastReviveTime = 0;
    private long reviveInvulnUntilTick = 0;
    private float shieldAbsorption = 0;

    /**
     * 已移除的「魔法」分类属性 id。
     * 旧存档里投入这些属性的点数，在加载时全额退还到可用点数（见 {@link #deserializeNBT}），
     * 避免整合包升级后玩家凭空损失点数。
     */
    private static final Set<String> LEGACY_MAGIC_STATS = Set.of(
            "max_mana", "mana_regen", "magic_damage", "mana_shield", "mana_steal", "mana_on_kill");

    // 使用 Map 存储属性点数 - 支持动态属性发现
    // statId → points
    private final Map<String, Long> allocatedPoints = new HashMap<>();

    // 属性值缓存 - 避免重复计算
    private final Map<String, Float> valueCache = new HashMap<>();
    private boolean cacheValid = false;

    // 开关属性激活状态缓存
    private final Map<String, Boolean> toggleCache = new HashMap<>();
    private boolean toggleCacheValid = false;

    // 每个玩家独立的被动经验tick计数器
    private int passiveTickCounter = 0;

    // 时间加速（加速属性）的小数累加器，避免点数较小时完全不生效
    private double timeAccelAccum = 0;

    // 冷却缩减的小数累加器：每 tick 累加缩减率，整数部分用于额外推进物品冷却计时器
    private double cooldownAccum = 0;

    // 记录本模组当前正在提供的能力（用于区分本模组 vs 其他模组给予的效果）
    private final Set<String> providedAbilities = new HashSet<>();

    // ========== 功能开关 ==========

    /**
     * 「功能开关」中已被关闭的属性 id。
     * <p>
     * 面板层面：不显示该条目、无法继续加点；执行层面见 {@link #isStatInactive(String)}
     * （「整条失效」模式下数值按 0 计、开关型功能的效果与指令一并停用）。
     */
    private final Set<String> disabledStats = new HashSet<>();

    /** 是否已把作者预设的「开局默认禁用」列表写入过本玩家（只应用一次）。 */
    private boolean defaultDisabledApplied = false;

    // buff 过滤模式：true = 黑名单（列表中的拦截），false = 白名单（列表中的绝对不拦截，其他不管）
    private boolean buffUseBlacklist = true;
    // 效果 ID 过滤列表（如 "minecraft:poison"，包含正面和负面效果）
    private final Set<String> buffFilterList = new HashSet<>();

    /**
     * 白名单（保护）模式下的「保护快照」：效果 ID → 快照。
     * <p>
     * 白名单模式即「保护」语义：名单里的效果在<b>自然结束前不会被任何外力清除</b> ——
     * 本模组自己的清理（自动复活、夜视 / 隐身等 toggle 收尾）、其它模组、牛奶桶、
     * {@code /effect clear} 都算外力。快照每 tick 由 {@code DefenseHandler} 采样与补回。
     * <p>
     * 刻意<b>不参与存档</b>：效果的有效期以玩家身上那瓶药水本身为准，快照只用来区分
     * 「被外力清除」（补回）与「自然到期」（放手）。登录 / 换维度后下一 tick 会重新采样。
     */
    private final Map<String, ProtectedEffect> protectedSnapshots = new HashMap<>();

    /**
     * 保护模式的「死亡重生继承」开关（默认关）。
     * <p>
     * 开启后玩家死亡重生时保护快照一并带过去：原版死亡会抹掉效果，但被保护的那些会按剩余时长补回，
     * 直到原本的自然结束时间；关闭时死亡即视为自然终止，重生后不再补回。
     * <p>
     * 换维度（非死亡克隆）无论开关如何都必须搬运快照，否则会白丢一次剩余时长记录。
     */
    private boolean protectAcrossDeath = false;

    // ========== 随身酿造台状态（关界面也继续酿） ==========

    /** 随身酿造台：台子跟着玩家走，内容与进度随存档保存。 */
    private final PortableBrewingData brewingData = new PortableBrewingData();

    public PortableBrewingData getBrewingData() {
        return brewingData;
    }

    // ========== 按功能的存储网络优先级（作用域） ==========

    /** 作用域：自动入库。 */
    public static final String SCOPE_AUTO_DEPOSIT = "auto_deposit";
    /** 作用域：随身工作台。 */
    public static final String SCOPE_CRAFTING = "crafting";
    /** 作用域：随身熔炉。 */
    public static final String SCOPE_FURNACE = "furnace";
    /** 作用域：熔炉成品仓。 */
    public static final String SCOPE_PRODUCT_BUFFER = "product_buffer";

    /** 全部受支持的存储作用域（界面与数据包校验共用）。 */
    public static final List<String> NETWORK_SCOPES =
            List.of(SCOPE_AUTO_DEPOSIT, SCOPE_CRAFTING, SCOPE_FURNACE, SCOPE_PRODUCT_BUFFER);

    /** 作用域 → 玩家自定义的存储键顺序（空 = 未自定义，沿用配置默认）。 */
    private final Map<String, List<String>> networkPriorities = new HashMap<>();

    /** 某作用域的原始优先级（空列表 = 未自定义，沿用配置）。 */
    public List<String> getNetworkPriority(String scope) {
        List<String> list = networkPriorities.get(scope);
        return list == null ? List.of() : Collections.unmodifiableList(list);
    }

    /** 某作用域生效的优先级：玩家自定义优先，未设置时回退到配置的默认顺序。 */
    public List<String> getEffectiveNetworkPriority(String scope) {
        List<String> list = networkPriorities.get(scope);
        if (list != null && !list.isEmpty()) return new ArrayList<>(list);
        List<? extends String> cfg = Config.NETWORK_PRIORITY.get();
        return cfg == null ? new ArrayList<>() : new ArrayList<>(cfg);
    }

    /** 设置某作用域的优先级；传空列表表示恢复成配置默认。 */
    public void setNetworkPriority(String scope, List<String> order) {
        if (scope == null || !NETWORK_SCOPES.contains(scope)) return;
        if (order == null || order.isEmpty()) {
            networkPriorities.remove(scope);
            return;
        }
        networkPriorities.put(scope, new ArrayList<>(order));
    }

    /** 便捷入口：自动入库生效的存储优先级。 */
    public List<String> getEffectiveAutoDepositPriority() {
        return getEffectiveNetworkPriority(SCOPE_AUTO_DEPOSIT);
    }

    // ========== 自动入库过滤（白 / 黑名单） ==========
    // 模式：true = 白名单（只入库名单内的物品），false = 黑名单（名单内的物品不入库）
    private boolean autoDepositUseWhitelist = false;
    /** 自动入库黑名单：物品 ID（如 {@code minecraft:diamond}）。 */
    private final Set<String> autoDepositBlacklist = new HashSet<>();
    /** 自动入库白名单。与黑名单各自独立，切换模式不会互相污染。 */
    private final Set<String> autoDepositWhitelist = new HashSet<>();

    // 定点传送点集合：名称 → 传送点
    private final Map<String, Waypoint> waypoints = new HashMap<>();

    // 随身熔炉的持久化状态（物品 + 燃烧/冶炼进度）
    private final PlayerFurnaceData furnaceData = new PlayerFurnaceData();

    public PlayerFurnaceData getFurnaceData() {
        return furnaceData;
    }

    // 连锁挖掘的个人设置（匹配模式 / 黑白名单 / 半径 / 代价等），随属性数据一起存档
    private final VeinMinerConfig veinMinerConfig = new VeinMinerConfig();

    public VeinMinerConfig getVeinMinerConfig() {
        return veinMinerConfig;
    }

    /** 用一份新设置覆盖（服务端收到客户端提交后调用；数值与名单已在 {@link VeinMinerConfig#sanitize()} 里夹过范围）。 */
    public void setVeinMinerConfig(VeinMinerConfig config) {
        veinMinerConfig.copyFrom(config);
    }

    // 随身工作台的物品倍率（基础为 1，可通过点数提升，影响每次合成的产出数量）
    private long craftingMultiplier = 1;

    // 收藏的属性 ID 集合
    private final Set<String> favorites = new HashSet<>();

    public long getCraftingMultiplier() {
        return Math.max(1, craftingMultiplier);
    }

    public void setCraftingMultiplier(long value) {
        this.craftingMultiplier = Math.max(1, value);
    }

    // ========== 收藏 ==========

    public boolean isFavorite(String statId) {
        return favorites.contains(statId);
    }

    public void toggleFavorite(String statId) {
        if (favorites.contains(statId)) favorites.remove(statId);
        else favorites.add(statId);
    }

    public Set<String> getFavorites() {
        return Collections.unmodifiableSet(favorites);
    }

    // ========== 构造器 ==========

    public PlayerStats() {
    }

    // ========== 等级与经验 ==========

    public long getLevel() {
        return level;
    }

    public long getExperience() {
        return experience;
    }

    public long getAvailablePoints() {
        return availablePoints;
    }

    public void setAvailablePoints(long value) {
        this.availablePoints = value;
    }

    public long getXpForNextLevel() {
        return (long) Config.BASE_XP_PER_LEVEL.get() + (level - 1) * (long) Config.XP_PER_LEVEL_INCREMENT.get();
    }

    /**
     * 添加经验值，自动升级
     * @return 是否升级了
     */
    public boolean addExperience(long amount) {
        if (amount <= 0) return false;

        experience += amount;
        boolean leveledUp = false;

        while (experience >= getXpForNextLevel()) {
            experience -= getXpForNextLevel();
            level++;
            leveledUp = true;
        }

        // 升级后重新计算可用点数，避免与已分配点数产生漂移
        if (leveledUp) {
            recalculateAvailablePoints();
        }

        return leveledUp;
    }

    /**
     * 根据等级和已分配点数重新计算可用点数
     * 正常游戏过程中不会为负，因为 addPoints / removePoints 都已禁止透支
     */
    public void recalculateAvailablePoints() {
        long totalEarned = (level - 1) * (long) Config.POINTS_PER_LEVEL.get();
        long totalAllocated = getTotalAllocatedPoints();
        long upgrades = (long) furnaceData.getSpeedLevel() * Config.FURNACE_SPEED_COST.get()
                + (getCraftingMultiplier() - 1) * Config.CRAFTING_MULTIPLIER_COST.get();
        this.availablePoints = totalEarned - totalAllocated - upgrades;
    }

    // ========== 复活 ==========

    public long getLastReviveTime() {
        return lastReviveTime;
    }

    public void setLastReviveTime(long time) {
        this.lastReviveTime = time;
    }

    /** 复活无敌窗口：当前游戏时间 < 该值时，玩家免疫一切伤害。 */
    public long getReviveInvulnUntilTick() {
        return reviveInvulnUntilTick;
    }

    public void setReviveInvulnUntilTick(long tick) {
        this.reviveInvulnUntilTick = tick;
    }

    public float getShieldAbsorption() {
        return shieldAbsorption;
    }

    public void setShieldAbsorption(float value) {
        shieldAbsorption = Math.max(0, value);
    }

    // ========== 被动经验计数器 ==========

    public int getPassiveTickCounter() {
        return passiveTickCounter;
    }

    public void setPassiveTickCounter(int count) {
        this.passiveTickCounter = count;
    }

    public void incrementPassiveTickCounter() {
        this.passiveTickCounter++;
    }

    public void resetPassiveTickCounter() {
        this.passiveTickCounter = 0;
    }

    // ========== 时间加速累加器 ==========

    public double getTimeAccelAccum() {
        return timeAccelAccum;
    }

    public void setTimeAccelAccum(double value) {
        this.timeAccelAccum = value;
    }

    // ========== 冷却缩减累加器 ==========

    public double getCooldownAccum() {
        return cooldownAccum;
    }

    public void setCooldownAccum(double value) {
        this.cooldownAccum = value;
    }

    // ========== 能力提供追踪 ==========

    /**
     * 检查本模组是否当前正在提供某个能力
     */
    public boolean isProviding(String abilityId) {
        return providedAbilities.contains(abilityId);
    }

    /**
     * 设置本模组是否正在提供某个能力
     */
    public void setProviding(String abilityId, boolean providing) {
        if (providing) {
            providedAbilities.add(abilityId);
        } else {
            providedAbilities.remove(abilityId);
        }
    }

    /**
     * 清除所有能力提供记录（重置属性点时调用）
     */
    public void clearAllProvided() {
        providedAbilities.clear();
    }

    // ========== Buff 过滤系统 ==========

    /**
     * 获取过滤模式：true=黑名单（列表中的拦截），false=白名单（列表中的绝对不拦截，其他不管）
     */
    public boolean isBuffUseBlacklist() {
        return buffUseBlacklist;
    }

    /**
     * 设置过滤模式
     */
    public void setBuffUseBlacklist(boolean blacklist) {
        this.buffUseBlacklist = blacklist;
    }

    /**
     * 获取过滤列表（不可变副本）
     */
    public Set<String> getBuffFilterList() {
        return Collections.unmodifiableSet(buffFilterList);
    }

    /**
     * 添加效果到过滤列表
     */
    public void addBuffFilter(String effectId) {
        buffFilterList.add(effectId);
    }

    /**
     * 从过滤列表移除效果
     */
    public void removeBuffFilter(String effectId) {
        buffFilterList.remove(effectId);
    }

    /**
     * 清空过滤列表
     */
    public void clearBuffFilters() {
        buffFilterList.clear();
    }

    /**
     * 设置完整过滤列表（从网络同步）
     */
    public void setBuffFilterList(Set<String> list, boolean useBlacklist) {
        buffFilterList.clear();
        buffFilterList.addAll(list);
        this.buffUseBlacklist = useBlacklist;
        // 被取消保护的条目立即停止保护（快照里的记录一并清掉），不必等 tick 逻辑回头清理
        if (!buffUseBlacklist) {
            protectedSnapshots.keySet().removeIf(id -> !isProtectedEffect(id));
        }
    }

    // ========== 白名单「保护」 ==========

    /**
     * 白名单模式即「保护」模式：名单中的效果在<b>自然结束前不会被任何外力清除</b>。
     * <p>
     * 与黑名单的「拦截」互斥：同一玩家同一时刻只会处于其中一种模式。
     */
    public boolean isProtectionMode() {
        return !buffUseBlacklist;
    }

    /** 该效果当前是否受保护（仅白名单 / 保护模式生效）。 */
    public boolean isProtectedEffect(String effectId) {
        return !buffUseBlacklist && effectId != null && buffFilterList.contains(effectId);
    }

    /** 保护名单是否非空（供每 tick 的维护逻辑做零开销短路）。 */
    public boolean hasProtectedEffects() {
        return !buffUseBlacklist && !buffFilterList.isEmpty();
    }

    /** 保护快照表：由 {@code DefenseHandler} 每 tick 采样 / 补回，见 {@link ProtectedEffect}。 */
    public Map<String, ProtectedEffect> getProtectedSnapshots() {
        return protectedSnapshots;
    }

    /** 死亡重生是否继承保护（仅白名单 / 保护模式有意义）。 */
    public boolean isProtectAcrossDeath() {
        return protectAcrossDeath;
    }

    /** 设置死亡重生是否继承保护。 */
    public void setProtectAcrossDeath(boolean inherit) {
        this.protectAcrossDeath = inherit;
    }

    /**
     * 玩家实体克隆时搬运保护快照。
     * <p>
     * 换维度（{@code wasDeath == false}）必须搬：效果本身跟着玩家走，不搬等于白丢一次剩余时长记录。
     * 死亡重生则看 {@link #isProtectAcrossDeath()}：关闭时死亡就是自然终止，重生后不再补回。
     *
     * @param other    克隆前的老数据（需已 {@code copyFrom}，开关以本对象为准）
     * @param wasDeath 是否死亡克隆
     */
    public void inheritProtection(PlayerStats other, boolean wasDeath) {
        protectedSnapshots.clear();
        if (other == null || (wasDeath && !protectAcrossDeath)) return;
        for (Map.Entry<String, ProtectedEffect> entry : other.protectedSnapshots.entrySet()) {
            protectedSnapshots.put(entry.getKey(), entry.getValue().copy());
        }
    }

    /**
     * 保护中的效果快照。
     * <p>
     * 每 tick 只要该效果还在身上就刷新一次（记录等级 / 粒子 / 剩余时长）；
     * 一旦消失，按剩余时长逐 tick 递减并补回 —— 递减到 0 说明是<b>自然结束</b>，
     * 撤销保护、不再补回。无限时长（{@code -1}）视为永不自然结束。
     * <p>
     * <b>1.21.1 适配</b>：{@code MobEffectInstance} 的构造与 {@code forceAddEffect} 现在都收
     * {@code Holder<MobEffect>}（1.20.1 收裸 {@code MobEffect}），所以快照里存的是 holder。
     */
    public static final class ProtectedEffect {

        private int amplifier;
        private boolean ambient;
        private boolean visible;
        private boolean showIcon;
        private boolean infinite;
        private int remaining;

        /** 用玩家身上当前的实例刷新快照。 */
        public void capture(net.minecraft.world.effect.MobEffectInstance instance) {
            this.amplifier = instance.getAmplifier();
            this.ambient = instance.isAmbient();
            this.visible = instance.isVisible();
            this.showIcon = instance.showIcon();
            int duration = instance.getDuration();
            this.infinite = duration == net.minecraft.world.effect.MobEffectInstance.INFINITE_DURATION;
            this.remaining = duration;
        }

        /**
         * 效果已从玩家身上消失时调用：按 tick 递减剩余时长。
         *
         * @return {@code true} = 还没到自然结束时间（应当补回）；{@code false} = 已自然结束
         */
        public boolean tickDown() {
            if (infinite) return true;
            remaining--;
            return remaining > 0;
        }

        /** 补回时使用的剩余时长（无限时长原样返回 {@code -1}）。 */
        public int reapplyDuration() {
            return infinite ? net.minecraft.world.effect.MobEffectInstance.INFINITE_DURATION
                    : Math.max(1, remaining);
        }

        /** 按快照重建一个效果实例。 */
        public net.minecraft.world.effect.MobEffectInstance toInstance(
                net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
            return new net.minecraft.world.effect.MobEffectInstance(
                    effect, reapplyDuration(), amplifier, ambient, visible, showIcon);
        }

        /** 复制一份（跨实体搬运 / 死亡重生继承用）。 */
        public ProtectedEffect copy() {
            ProtectedEffect copy = new ProtectedEffect();
            copy.amplifier = amplifier;
            copy.ambient = ambient;
            copy.visible = visible;
            copy.showIcon = showIcon;
            copy.infinite = infinite;
            copy.remaining = remaining;
            return copy;
        }
    }

    /**
     * 检查某个效果是否应该被拦截
     * @return true = 应该拦截
     */
    public boolean shouldBlockEffect(String effectId) {
        boolean inList = buffFilterList.contains(effectId);
        if (buffUseBlacklist) {
            // 黑名单模式：列表中的就是要拦截的
            // 空列表 = 没有要拦截的 = 全放行
            return inList;
        } else {
            // 白名单模式：列表中的绝对不拦截，其他的不管（不处理）
            return false;
        }
    }

    // ========== 属性点数 ==========

    /**
     * 获取属性点数
     */
    public long getStatLevel(StatType stat) {
        if (stat == null) return 0L;
        return allocatedPoints.getOrDefault(stat.getId(), 0L);
    }

    /**
     * 通过ID获取属性点数
     */
    public long getStatLevel(String statId) {
        return allocatedPoints.getOrDefault(statId, 0L);
    }

    /**
     * 获取属性值 - 使用缓存
     */
    public float getStatValue(StatType stat) {
        if (stat == null) return 0f;
        ensureCacheValid();
        return valueCache.getOrDefault(stat.getId(), 0f);
    }

    /**
     * 通过ID获取属性值
     */
    public float getStatValue(String statId) {
        ensureCacheValid();
        return valueCache.getOrDefault(statId, 0f);
    }

    /**
     * 检查开关属性是否激活
     */
    public boolean isToggleActive(String statId) {
        ensureToggleCacheValid();
        return toggleCache.getOrDefault(statId, false);
    }

    // ========== 功能开关 ==========

    /**
     * 该属性是否已被关闭（<b>面板层面</b>：不显示、不能加点）。
     * 与执行模式无关 —— 三种模式下面板都不显示它。
     */
    public boolean isStatDisabled(String statId) {
        return statId != null && disabledStats.contains(statId);
    }

    /**
     * 该属性是否**整条失效**（数值按 0 计、开关型功能的效果与指令停用）。
     * 「只隐藏」（{@link FeatureDisableMode#HIDDEN_ONLY}）模式下恒为 {@code false}。
     */
    public boolean isStatInactive(String statId) {
        return statId != null && FeatureDisableMode.current().isInactive()
                && disabledStats.contains(statId);
    }

    /**
     * 设置某个属性的开关状态（true = 关闭）。
     * 在 {@link FeatureDisableMode#INACTIVE_REFUND_POINTS} 模式下，关闭会同时返还已投入的点数。
     */
    public void setStatDisabled(String statId, boolean disabled) {
        if (statId == null) return;
        if (disabled) {
            if (!disabledStats.add(statId)) return;
        } else if (!disabledStats.remove(statId)) {
            return;
        }
        if (disabled && FeatureDisableMode.current().isRefund()) refundAllocatedPoints(statId);
        invalidateCache();
    }

    /** 批量设置（用于「全部关闭 / 全部开启」）。 */
    public void setStatsDisabled(Collection<String> statIds, boolean disabled) {
        if (statIds == null) return;
        boolean changed = false;
        for (String id : statIds) {
            if (id == null) continue;
            if (disabled) {
                if (!disabledStats.add(id)) continue;
                if (FeatureDisableMode.current().isRefund()) refundAllocatedPoints(id);
                changed = true;
            } else {
                changed |= disabledStats.remove(id);
            }
        }
        if (changed) invalidateCache();
    }

    /** 把该属性已投入的点数全额退回到可用点数（并清掉该属性的分配）。 */
    private void refundAllocatedPoints(String statId) {
        Long points = allocatedPoints.remove(statId);
        if (points != null) availablePoints += Math.abs(points);
    }

    /** 已关闭的属性 id（只读视图，供界面显示）。 */
    public Set<String> getDisabledStats() {
        return Collections.unmodifiableSet(disabledStats);
    }

    /**
     * 首次加载玩家数据时，把整合包作者预设的「开局默认禁用」列表写入本玩家。
     * 只执行一次（用 NBT 标志保证幂等）；之后作者改配置不影响已有玩家，
     * 功能开放由任务奖励指令 {@code /infstats feature <id> on} 负责。
     */
    public void applyDefaultDisabledStatsIfNeeded() {
        if (defaultDisabledApplied) return;
        defaultDisabledApplied = true;
        List<String> defaults = Config.defaultDisabledStats();
        if (defaults.isEmpty()) return;
        boolean changed = false;
        for (String id : defaults) {
            if (StatType.fromId(id) != null && disabledStats.add(id)) changed = true;
        }
        if (changed) invalidateCache();
    }

    /**
     * 重新读取「功能开关」的执行模式（全局配置）并让缓存失效。
     * <p>
     * 模式本身不按玩家存，读取时直接查配置；所以运行时改了 {@code GUI.featureDisableMode}
     * 后，调一次这个方法即可让 {@link #isStatInactive(String)} 与数值缓存按新模式重算。
     */
    public void refreshFeatureMode() {
        invalidateCache();
    }

    // ========== 属性分配 ==========

    /**
     * 添加属性点
     */
    public boolean addPoint(StatType stat) {
        return addPoints(stat, 1L);
    }

    /**
     * 批量添加属性点（禁止透支可用点数）
     * "+" 统一语义：值往正向走（current += count）
     *   current >= 0 → 远离零点，消耗可用点数
     *   current < 0 → 往 0 靠近，返还可用点数
     */
    public boolean addPoints(StatType stat, long count) {
        if (count <= 0) return false;

        long current = allocatedPoints.getOrDefault(stat.getId(), 0L);

        // 开关型属性：一次性加到激活阈值（不允许透支点数）
        if (stat.isToggle()) {
            if (current >= stat.getMaxLevel()) return false;
            long toAdd = stat.getMaxLevel() - current;
            if (toAdd <= 0 || availablePoints < toAdd) return false;
            allocatedPoints.put(stat.getId(), current + toAdd);
            availablePoints -= toAdd;
            invalidateCache();
            return true;
        }

        if (current >= 0) {
            // 正值方向：往上加（最多到 maxLevel），消耗可用点数
            long max = stat.getMaxLevel();
            long space = max - current;
            long toAdd = Math.min(count, space);
            if (toAdd <= 0) return false;
            if (availablePoints < toAdd) return false;
            allocatedPoints.put(stat.getId(), current + toAdd);
            availablePoints -= toAdd;
        } else {
            // 属性已为负：往 0 靠近，返还可用点数
            long newVal = Math.min(0, current + count);
            long moved = newVal - current; // 正值，如 -10→-3 则 moved=7
            if (moved <= 0) return false;
            if (newVal == 0) {
                allocatedPoints.remove(stat.getId());
            } else {
                allocatedPoints.put(stat.getId(), newVal);
            }
            availablePoints += moved; // 返还点数
        }

        invalidateCache();
        return true;
    }

    /**
     * 移除属性点
     * "-" 统一语义：值往负向走
     *   current > 0 → 往 0 靠近，返还可用点数
     *   current <= 0 → 远离零点（继续往负），消耗可用点数，下限 = -maxLevel
     */
    public boolean removePoints(StatType stat, long count) {
        if (count <= 0) return false;

        long current = allocatedPoints.getOrDefault(stat.getId(), 0L);

        // 开关型属性：一次性全部移除
        if (stat.isToggle()) {
            if (current <= 0) return false;
            allocatedPoints.remove(stat.getId());
            availablePoints += current;
            invalidateCache();
            return true;
        }

        if (current > 0) {
            // 正值方向：往 0 靠近，返还点数
            long toRemove = Math.min(count, current);
            long newVal = current - toRemove;
            if (newVal == 0) {
                allocatedPoints.remove(stat.getId());
            } else {
                allocatedPoints.put(stat.getId(), newVal);
            }
            availablePoints += toRemove;
        } else {
            // 已为 0 或负数：继续往负方向（远离零点），消耗可用点数
            long minLevel = -(long) stat.getMaxLevel();
            long newVal = Math.max(minLevel, current - count);
            long moved = current - newVal; // 正值，如 0→-3 则 moved=3
            if (moved <= 0) return false;
            if (availablePoints < moved) return false; // 点数不足
            if (newVal == 0) {
                allocatedPoints.remove(stat.getId());
            } else {
                allocatedPoints.put(stat.getId(), newVal);
            }
            availablePoints -= moved; // 消耗点数
        }

        invalidateCache();
        return true;
    }

    /**
     * 重置所有属性点
     */
    public void resetAllPoints() {
        long totalAllocated = getTotalAllocatedPoints();
        allocatedPoints.clear();
        availablePoints += totalAllocated;
        // 保留提供记录，供各处理器在下一 tick 撤销旧能力。
        invalidateCache();
    }

    /**
     * 重置单个属性（清零，返还所有已分配点数，含负数）
     */
    public void resetStat(StatType stat) {
        long points = allocatedPoints.getOrDefault(stat.getId(), 0L);
        if (points != 0) {
            availablePoints += Math.abs(points);
            allocatedPoints.remove(stat.getId());
            invalidateCache();
        }
    }

    // ========== 缓存管理 ==========

    private void invalidateCache() {
        cacheValid = false;
        toggleCacheValid = false;
    }

    private void ensureCacheValid() {
        if (cacheValid) return;

        valueCache.clear();
        for (Map.Entry<String, Long> entry : allocatedPoints.entrySet()) {
            // 「功能开关」整条失效时数值按 0 计（点数仍保留在 allocatedPoints 里）
            if (isStatInactive(entry.getKey())) continue;
            StatType stat = StatType.fromId(entry.getKey());
            if (stat != null) {
                valueCache.put(entry.getKey(), stat.calculateValue(entry.getValue()));
            }
        }
        cacheValid = true;
    }

    private void ensureToggleCacheValid() {
        if (toggleCacheValid) return;

        toggleCache.clear();
        for (Map.Entry<String, Long> entry : allocatedPoints.entrySet()) {
            // 整条失效时开关型功能一并停用（效果与指令都读这里）
            if (isStatInactive(entry.getKey())) continue;
            StatType stat = StatType.fromId(entry.getKey());
            if (stat != null && stat.isToggle()) {
                toggleCache.put(entry.getKey(), entry.getValue() >= stat.getMaxLevel());
            }
        }
        toggleCacheValid = true;
    }

    // ========== 工具方法 ==========

    /**
     * 获取所有已分配属性的总点数
     */
    public long getTotalAllocatedPoints() {
        return allocatedPoints.values().stream().mapToLong(points -> Math.abs(points)).sum();
    }

    /**
     * 获取某类别下的总点数
     */
    public long getCategoryPoints(StatCategory category) {
        long total = 0;
        for (Map.Entry<String, Long> entry : allocatedPoints.entrySet()) {
            StatType stat = StatType.fromId(entry.getKey());
            if (stat != null && stat.getCategory() == category) {
                total += Math.abs(entry.getValue());
            }
        }
        return total;
    }

    // ========== 定点传送点管理 ==========

    public void setWaypoint(String name, Waypoint wp) {
        waypoints.put(name, wp);
    }

    public Waypoint getWaypoint(String name) {
        return waypoints.get(name);
    }

    public boolean hasWaypoint(String name) {
        return waypoints.containsKey(name);
    }

    public void removeWaypoint(String name) {
        waypoints.remove(name);
    }

    public Map<String, Waypoint> getWaypoints() {
        return Collections.unmodifiableMap(waypoints);
    }

    // ========== NBT序列化 ==========

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("level", level);
        tag.putLong("experience", experience);
        tag.putLong("availablePoints", availablePoints);
        tag.putLong("lastReviveTime", lastReviveTime);
        tag.putLong("reviveInvulnUntilTick", reviveInvulnUntilTick);
        tag.putFloat("shieldAbsorption", shieldAbsorption);

        // 使用 ID 格式存储属性点数（支持负值）
        ListTag pointsList = new ListTag();
        for (Map.Entry<String, Long> entry : allocatedPoints.entrySet()) {
            if (entry.getValue() != 0) {
                CompoundTag entryTag = new CompoundTag();
                entryTag.putString("id", entry.getKey());
                entryTag.putLong("points", entry.getValue());
                pointsList.add(entryTag);
            }
        }
        tag.put("allocatedPoints", pointsList);

        // 序列化能力提供记录
        ListTag abilitiesList = new ListTag();
        for (String ability : providedAbilities) {
            CompoundTag aTag = new CompoundTag();
            aTag.putString("id", ability);
            abilitiesList.add(aTag);
        }
        tag.put("providedAbilities", abilitiesList);

        // 序列化 buff 过滤列表
        tag.putBoolean("debuffUseBlacklist", buffUseBlacklist);
        ListTag filterList = new ListTag();
        for (String effectId : buffFilterList) {
            CompoundTag fTag = new CompoundTag();
            fTag.putString("id", effectId);
            filterList.add(fTag);
        }
        tag.put("debuffFilterList", filterList);

        // 序列化自动入库过滤（白 / 黑名单各存一份，切换模式不互相污染）
        tag.putBoolean("depositUseWhitelist", autoDepositUseWhitelist);
        tag.put("depositBlacklist", idListToTag(autoDepositBlacklist));
        tag.put("depositWhitelist", idListToTag(autoDepositWhitelist));

        // 序列化「按功能的存储优先级」（作用域 → 存储键顺序）
        CompoundTag priorityTag = new CompoundTag();
        for (Map.Entry<String, List<String>> entry : networkPriorities.entrySet()) {
            ListTag orderList = new ListTag();
            for (String key : entry.getValue()) {
                CompoundTag keyTag = new CompoundTag();
                keyTag.putString("k", key);
                orderList.add(keyTag);
            }
            priorityTag.put(entry.getKey(), orderList);
        }
        tag.put("networkPriorities", priorityTag);

        // 序列化随身酿造台（内容 + 酿造进度）
        tag.put("brewing", brewingData.serializeNBT());

        // 序列化定点传送点
        ListTag wpList = new ListTag();
        for (Map.Entry<String, Waypoint> entry : waypoints.entrySet()) {
            CompoundTag wpTag = new CompoundTag();
            wpTag.putString("name", entry.getKey());
            wpTag.put("pos", entry.getValue().toTag());
            wpList.add(wpTag);
        }
        tag.put("waypoints", wpList);

        // 序列化随身熔炉状态
        tag.put("furnace", furnaceData.serializeNBT());

        // 序列化连锁挖掘个人设置
        tag.put("veinMiner", veinMinerConfig.serialize());

        // 序列化收藏列表
        ListTag favList = new ListTag();
        for (String id : favorites) {
            CompoundTag f = new CompoundTag();
            f.putString("id", id);
            favList.add(f);
        }
        tag.put("favorites", favList);

        // 序列化随身工作台倍率
        tag.putLong("craftingMultiplier", craftingMultiplier);

        // 序列化「功能开关」中已关闭的属性
        ListTag disabledList = new ListTag();
        for (String id : disabledStats) {
            CompoundTag d = new CompoundTag();
            d.putString("id", id);
            disabledList.add(d);
        }
        tag.put("disabledStats", disabledList);
        tag.putBoolean("defaultDisabledApplied", defaultDisabledApplied);

        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        level = tag.getLong("level");
        experience = tag.getLong("experience");
        availablePoints = tag.getLong("availablePoints");
        lastReviveTime = tag.getLong("lastReviveTime");
        reviveInvulnUntilTick = tag.getLong("reviveInvulnUntilTick");
        shieldAbsorption = tag.contains("shieldAbsorption") ? tag.getFloat("shieldAbsorption") : -1;

        // 重置所有属性点
        allocatedPoints.clear();

        ListTag pointsList = tag.getList("allocatedPoints", Tag.TAG_COMPOUND);
        for (int i = 0; i < pointsList.size(); i++) {
            CompoundTag entryTag = pointsList.getCompound(i);
            String statId = entryTag.getString("id");
            long points = entryTag.getLong("points");
            if (LEGACY_MAGIC_STATS.contains(statId)) {
                // 「魔法」分类已移除。必须放在 StatType.fromId 判断之前：
                // 属性定义删掉后 fromId 会返回 null，条目会被直接丢弃，点数就白丢了。
                // 按绝对值退还，负值（透支方向）同样返还。
                availablePoints += Math.abs(points);
                continue;
            }
            if (StatType.fromId(statId) != null) {
                allocatedPoints.put(statId, points);
            }
        }

        // 反序列化能力提供记录
        providedAbilities.clear();
        if (tag.contains("providedAbilities")) {
            ListTag abilitiesList = tag.getList("providedAbilities", Tag.TAG_COMPOUND);
            for (int i = 0; i < abilitiesList.size(); i++) {
                CompoundTag aTag = abilitiesList.getCompound(i);
                providedAbilities.add(aTag.getString("id"));
            }
        }
        buffUseBlacklist = tag.contains("debuffUseBlacklist") ? tag.getBoolean("debuffUseBlacklist") : true;
        buffFilterList.clear();
        if (tag.contains("debuffFilterList")) {
            ListTag filterList = tag.getList("debuffFilterList", Tag.TAG_COMPOUND);
            for (int i = 0; i < filterList.size(); i++) {
                buffFilterList.add(filterList.getCompound(i).getString("id"));
            }
        }
        // 旧存档没有这个键 → 默认关闭（保持原本「死亡即终止保护」的行为）
        protectAcrossDeath = tag.contains("debuffProtectAcrossDeath")
                && tag.getBoolean("debuffProtectAcrossDeath");

        // 反序列化自动入库过滤（v1.20.1 主线用的键名一致，便于日后迁移存档）
        autoDepositUseWhitelist = tag.contains("depositUseWhitelist") && tag.getBoolean("depositUseWhitelist");
        autoDepositBlacklist.clear();
        readIdList(tag, "depositBlacklist", autoDepositBlacklist);
        autoDepositWhitelist.clear();
        readIdList(tag, "depositWhitelist", autoDepositWhitelist);

        // 反序列化按功能的存储优先级
        networkPriorities.clear();
        if (tag.contains("networkPriorities")) {
            CompoundTag priorityTag = tag.getCompound("networkPriorities");
            for (String scope : NETWORK_SCOPES) {
                if (!priorityTag.contains(scope)) continue;
                ListTag orderList = priorityTag.getList(scope, Tag.TAG_COMPOUND);
                List<String> order = new ArrayList<>();
                for (int i = 0; i < orderList.size(); i++) {
                    String key = orderList.getCompound(i).getString("k");
                    if (!key.isEmpty()) order.add(key);
                }
                if (!order.isEmpty()) networkPriorities.put(scope, order);
            }
        }

        // 反序列化随身酿造台
        if (tag.contains("brewing")) {
            brewingData.deserializeNBT(tag.getCompound("brewing"));
        }

        // 反序列化定点传送点
        waypoints.clear();
        if (tag.contains("waypoints")) {
            ListTag wpList = tag.getList("waypoints", Tag.TAG_COMPOUND);
            for (int i = 0; i < wpList.size(); i++) {
                CompoundTag wpTag = wpList.getCompound(i);
                String name = wpTag.getString("name");
                if (wpTag.contains("pos")) {
                    waypoints.put(name, Waypoint.fromTag(wpTag.getCompound("pos")));
                }
            }
        }

        // 反序列化随身熔炉状态
        if (tag.contains("furnace")) {
            furnaceData.deserializeNBT(tag.getCompound("furnace"));
        }

        // 反序列化连锁挖掘个人设置（原地覆盖，保持 final 引用不变）
        if (tag.contains("veinMiner")) {
            veinMinerConfig.copyFrom(VeinMinerConfig.deserialize(tag.getCompound("veinMiner")));
        }

        // 反序列化收藏列表
        favorites.clear();
        if (tag.contains("favorites")) {
            ListTag favList = tag.getList("favorites", Tag.TAG_COMPOUND);
            for (int i = 0; i < favList.size(); i++) {
                String id = favList.getCompound(i).getString("id");
                if (StatType.fromId(id) != null) favorites.add(id);
            }
        }

        // 反序列化随身工作台倍率
        if (tag.contains("craftingMultiplier")) {
            craftingMultiplier = tag.getLong("craftingMultiplier");
        }

        // 反序列化「功能开关」中已关闭的属性
        disabledStats.clear();
        if (tag.contains("disabledStats")) {
            ListTag disabledList = tag.getList("disabledStats", Tag.TAG_COMPOUND);
            for (int i = 0; i < disabledList.size(); i++) {
                String id = disabledList.getCompound(i).getString("id");
                if (!id.isEmpty()) disabledStats.add(id);
            }
        }
        defaultDisabledApplied = tag.contains("defaultDisabledApplied")
                && tag.getBoolean("defaultDisabledApplied");

        invalidateCache();
    }

    /**
     * 从另一个PlayerStats复制数据
     */
    public void copyFrom(PlayerStats other) {
        this.level = other.level;
        this.experience = other.experience;
        this.availablePoints = other.availablePoints;
        this.lastReviveTime = other.lastReviveTime;
        this.reviveInvulnUntilTick = other.reviveInvulnUntilTick;
        this.passiveTickCounter = other.passiveTickCounter;
        this.providedAbilities.clear();
        this.providedAbilities.addAll(other.providedAbilities);
        this.buffUseBlacklist = other.buffUseBlacklist;
        this.buffFilterList.clear();
        this.buffFilterList.addAll(other.buffFilterList);
        this.protectAcrossDeath = other.protectAcrossDeath;
        // 保护快照不在这里搬：由 PlayerEvent.Clone 显式调用 inheritProtection(其他, 是否死亡)，
        // 因为「死亡重生是否继承」取决于本开关，只有克隆入口知道是不是死亡。
        this.autoDepositUseWhitelist = other.autoDepositUseWhitelist;
        this.autoDepositBlacklist.clear();
        this.autoDepositBlacklist.addAll(other.autoDepositBlacklist);
        this.autoDepositWhitelist.clear();
        this.autoDepositWhitelist.addAll(other.autoDepositWhitelist);
        this.networkPriorities.clear();
        for (Map.Entry<String, List<String>> entry : other.networkPriorities.entrySet()) {
            this.networkPriorities.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        this.brewingData.copyFrom(other.brewingData);
        this.allocatedPoints.clear();
        this.allocatedPoints.putAll(other.allocatedPoints);
        this.waypoints.clear();
        this.waypoints.putAll(other.waypoints);
        this.furnaceData.copyFrom(other.furnaceData);
        this.craftingMultiplier = other.craftingMultiplier;
        this.favorites.clear();
        this.favorites.addAll(other.favorites);
        this.disabledStats.clear();
        this.disabledStats.addAll(other.disabledStats);
        this.defaultDisabledApplied = other.defaultDisabledApplied;
        invalidateCache();
    }

    /**
     * 创建快照用于同步
     */
    public StatsSnapshot createSnapshot() {
        return new StatsSnapshot(
                level, experience, availablePoints,
                lastReviveTime, passiveTickCounter,
                new HashMap<>(allocatedPoints),
                buffUseBlacklist,
                new HashSet<>(buffFilterList),
                protectAcrossDeath,
                new HashMap<>(waypoints),
                reviveInvulnUntilTick,
                new HashSet<>(favorites),
                autoDepositUseWhitelist,
                new HashSet<>(autoDepositBlacklist),
                new HashSet<>(autoDepositWhitelist),
                copyNetworkPriorities(),
                new HashSet<>(disabledStats)
        );
    }

    /**
     * 从快照恢复
     */
    public void restoreFromSnapshot(StatsSnapshot snapshot) {
        this.level = snapshot.level;
        this.experience = snapshot.experience;
        this.availablePoints = snapshot.availablePoints;
        this.lastReviveTime = snapshot.lastReviveTime;
        this.reviveInvulnUntilTick = snapshot.reviveInvulnUntilTick;
        this.passiveTickCounter = snapshot.passiveTickCounter;
        this.allocatedPoints.clear();
        this.allocatedPoints.putAll(snapshot.allocatedPoints);
        this.buffUseBlacklist = snapshot.buffUseBlacklist;
        this.buffFilterList.clear();
        this.buffFilterList.addAll(snapshot.buffFilterList);
        this.protectAcrossDeath = snapshot.protectAcrossDeath;
        this.autoDepositUseWhitelist = snapshot.autoDepositUseWhitelist;
        this.autoDepositBlacklist.clear();
        if (snapshot.autoDepositBlacklist != null) {
            this.autoDepositBlacklist.addAll(snapshot.autoDepositBlacklist);
        }
        this.autoDepositWhitelist.clear();
        if (snapshot.autoDepositWhitelist != null) {
            this.autoDepositWhitelist.addAll(snapshot.autoDepositWhitelist);
        }
        this.networkPriorities.clear();
        if (snapshot.networkPriorities != null) {
            for (Map.Entry<String, List<String>> entry : snapshot.networkPriorities.entrySet()) {
                this.networkPriorities.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }
        this.waypoints.clear();
        this.waypoints.putAll(snapshot.waypoints);
        this.favorites.clear();
        this.favorites.addAll(snapshot.favorites);
        this.disabledStats.clear();
        if (snapshot.disabledStats != null) this.disabledStats.addAll(snapshot.disabledStats);
        invalidateCache();
    }

    // ========== 自动入库过滤（白 / 黑名单） ==========

    public boolean isAutoDepositUseWhitelist() {
        return autoDepositUseWhitelist;
    }

    /** 只切换模式，不动任何名单（界面切换模式时调用）。 */
    public void setAutoDepositUseWhitelist(boolean useWhitelist) {
        this.autoDepositUseWhitelist = useWhitelist;
    }

    /** 当前模式生效的名单（只读副本）。 */
    public Set<String> getAutoDepositFilterList() {
        return Collections.unmodifiableSet(autoDepositActiveList());
    }

    /** 是否设置了过滤条目（为空时无论白 / 黑名单都不做限制）。 */
    public boolean hasAutoDepositFilter() {
        return !autoDepositActiveList().isEmpty();
    }

    public void addAutoDepositFilter(String itemId) {
        if (itemId != null && !itemId.isEmpty()) autoDepositActiveList().add(itemId);
    }

    public void removeAutoDepositFilter(String itemId) {
        autoDepositActiveList().remove(itemId);
    }

    public void clearAutoDepositFilters() {
        autoDepositActiveList().clear();
    }

    /**
     * 设置当前模式的过滤列表（从网络同步 / 界面提交时调用）。
     * <p>
     * 只覆盖当前模式对应的那一份名单，另一份（黑或白）保持原样，
     * 所以「黑名单选完切到白名单」不会再看到黑名单的条目。
     */
    public void setAutoDepositFilterList(Set<String> list, boolean useWhitelist) {
        this.autoDepositUseWhitelist = useWhitelist;
        Set<String> target = autoDepositActiveList();
        target.clear();
        if (list != null) target.addAll(list);
    }

    /** 当前模式生效的名单（内部可变引用，仅本类使用）。 */
    private Set<String> autoDepositActiveList() {
        return autoDepositUseWhitelist ? autoDepositWhitelist : autoDepositBlacklist;
    }

    /**
     * 判断某个物品是否允许自动入库。
     * <p>
     * 列表为空时视为「不做限制」—— 白名单为空若理解为「什么都不入库」，
     * 玩家一旦清空列表就会完全失去自动入库能力，容易误操作；这里统一按放行处理。
     * 白名单：只允许列表内的物品；黑名单：不允许列表内的物品。
     */
    public boolean allowsAutoDeposit(String itemId) {
        if (itemId == null) return true;
        Set<String> list = autoDepositActiveList();
        if (list.isEmpty()) return true;
        return autoDepositUseWhitelist == list.contains(itemId);
    }

    /** 物品 ID 集合 → NBT 列表（每项一个 {@code id} 字符串，与 buff 过滤列表同格式）。 */
    private static ListTag idListToTag(Set<String> ids) {
        ListTag list = new ListTag();
        for (String id : ids) {
            if (id == null || id.isEmpty()) continue;
            CompoundTag entry = new CompoundTag();
            entry.putString("id", id);
            list.add(entry);
        }
        return list;
    }

    /** 从 NBT 读取物品 ID 列表（缺失时保持空集，不抛异常）。 */
    private static void readIdList(CompoundTag tag, String key, Set<String> out) {
        if (tag == null || !tag.contains(key)) return;
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            String id = list.getCompound(i).getString("id");
            if (!id.isEmpty()) out.add(id);
        }
    }

    /** 深拷贝按功能的存储优先级，供快照使用。 */
    private Map<String, List<String>> copyNetworkPriorities() {
        Map<String, List<String>> copy = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : networkPriorities.entrySet()) {
            copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return copy;
    }

    // ========== 快照类（用于网络同步） ==========

    public static class StatsSnapshot {
        public final long level;
        public final long experience;
        public final long availablePoints;
        public final long lastReviveTime;
        public final int passiveTickCounter;
        public final Map<String, Long> allocatedPoints;
        public final boolean buffUseBlacklist;
        public final Set<String> buffFilterList;
        /** 保护模式：死亡重生是否继承保护 */
        public final boolean protectAcrossDeath;
        public final Map<String, Waypoint> waypoints;
        public final long reviveInvulnUntilTick;
        public final Set<String> favorites;
        /** 自动入库过滤模式（true = 白名单） */
        public final boolean autoDepositUseWhitelist;
        public final Set<String> autoDepositBlacklist;
        public final Set<String> autoDepositWhitelist;
        /** 作用域 → 存储键顺序（按功能的存储优先级） */
        public final Map<String, List<String>> networkPriorities;
        /** 「功能开关」中已关闭的属性 id */
        public final Set<String> disabledStats;

        public StatsSnapshot(long level, long experience, long availablePoints,
                long lastReviveTime, int passiveTickCounter,
                Map<String, Long> allocatedPoints,
                boolean buffUseBlacklist, Set<String> buffFilterList,
                boolean protectAcrossDeath,
                Map<String, Waypoint> waypoints, long reviveInvulnUntilTick, Set<String> favorites,
                boolean autoDepositUseWhitelist, Set<String> autoDepositBlacklist,
                Set<String> autoDepositWhitelist,
                Map<String, List<String>> networkPriorities,
                Set<String> disabledStats) {
            this.level = level;
            this.experience = experience;
            this.availablePoints = availablePoints;
            this.lastReviveTime = lastReviveTime;
            this.passiveTickCounter = passiveTickCounter;
            this.allocatedPoints = allocatedPoints;
            this.buffUseBlacklist = buffUseBlacklist;
            this.buffFilterList = buffFilterList;
            this.protectAcrossDeath = protectAcrossDeath;
            this.waypoints = waypoints;
            this.reviveInvulnUntilTick = reviveInvulnUntilTick;
            this.favorites = favorites;
            this.autoDepositUseWhitelist = autoDepositUseWhitelist;
            this.autoDepositBlacklist = autoDepositBlacklist;
            this.autoDepositWhitelist = autoDepositWhitelist;
            this.networkPriorities = networkPriorities;
            this.disabledStats = disabledStats;
        }
    }
}