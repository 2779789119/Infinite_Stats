package com.infinitestats.stats;

import com.infinitestats.Config;
import com.infinitestats.furnace.PlayerFurnaceData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

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
     * 已经移除的属性 id。
     * 旧存档里投入这些属性的点数，在加载时全额退还到可用点数（见 {@link #deserializeNBT}），
     * 避免整合包升级后玩家凭空损失点数（属性定义删掉后 {@code StatType.fromId} 会返回 null，
     * 条目会被静默丢弃 —— 必须在这里显式退还）。
     */
    private static final Set<String> LEGACY_REMOVED_STATS = Set.of(
            // 「魔法」分类（1.9.x 移除）
            "max_mana", "mana_regen", "magic_damage", "mana_shield", "mana_steal", "mana_on_kill",
            // 弹射物暴击（1.21.0 移除：收益不明显，近战与弹射物回到同一条蓄力判定）
            "crit_projectile");

    // 使用 Map 存储属性点数 - 支持动态属性发现
    // statId → points
    private final Map<String, Long> allocatedPoints = new HashMap<>();

    // 属性值缓存 - 避免重复计算
    private final Map<String, Float> valueCache = new HashMap<>();
    private boolean cacheValid = false;

    // 开关属性激活状态缓存
    private final Map<String, Boolean> toggleCache = new HashMap<>();
    private boolean toggleCacheValid = false;

    /**
     * 「功能开关」中被玩家关闭的属性 id（每个玩家独立，不是全局配置）。
     * 关闭后：属性面板不再显示该条目、无法继续加点，并且<b>整条失效</b> ——
     * 数值按 0 计（见 {@link #ensureCacheValid()}）、开关型功能的效果与指令一并停用；
     * 已投入的点数保留，重新开启即恢复。
     */
    private final Set<String> disabledStats = new HashSet<>();

    /**
     * 是否已把作者预设的「开局默认禁用」列表（{@code Config#DISABLED_STATS}）写入过本玩家。
     * 只在玩家数据首次加载时应用一次：之后作者改配置不影响已有玩家，任务奖励指令负责后续开放。
     */
    private boolean defaultDisabledApplied = false;

    // 每个玩家独立的被动经验tick计数器
    private int passiveTickCounter = 0;

    // 时间加速（加速属性）的小数累加器，避免点数较小时完全不生效
    private double timeAccelAccum = 0;

    // 冷却缩减的小数累加器：每 tick 累加缩减率，整数部分用于额外推进物品冷却计时器
    private double cooldownAccum = 0;

    // 记录本模组当前正在提供的能力（用于区分本模组 vs 其他模组给予的效果）
    private final Set<String> providedAbilities = new HashSet<>();

    // buff 过滤模式：true = 黑名单（列表中的拦截），false = 白名单（列表中的绝对不拦截，其他不管）
    private boolean buffUseBlacklist = true;
    // 效果 ID 过滤列表（如 "minecraft:poison"，包含正面和负面效果）
    private final Set<String> buffFilterList = new HashSet<>();

    // 定点传送点集合：名称 → 传送点
    private final Map<String, Waypoint> waypoints = new HashMap<>();

    // 随身熔炉的持久化状态（物品 + 燃烧/冶炼进度）
    private final PlayerFurnaceData furnaceData = new PlayerFurnaceData();

    public PlayerFurnaceData getFurnaceData() {
        return furnaceData;
    }

    // 随身工作台的物品倍率（基础为 1，可通过点数提升，影响每次合成的产出数量）
    private long craftingMultiplier = 1;

    // 收藏的属性 ID 集合
    private final Set<String> favorites = new HashSet<>();

    // ========== 存储优先级（按功能分作用域） ==========

    /** 作用域：自动入库 —— 背包物品写入存储网络时的顺序 */
    public static final String SCOPE_AUTO_DEPOSIT = "auto_deposit";
    /** 作用域：随身工作台 —— 取料 / 补料 / 退回材料 / 成品入库 */
    public static final String SCOPE_CRAFTING = "crafting";
    /** 作用域：随身熔炉 —— 抽矿物 / 抽燃料 / 成品入库 */
    public static final String SCOPE_FURNACE = "furnace";
    /** 作用域：成品仓 —— 出库到存储网络 */
    public static final String SCOPE_PRODUCT_BUFFER = "product_buffer";

    /** 全部作用域（用于校验与界面枚举） */
    public static final List<String> NETWORK_SCOPES =
            List.of(SCOPE_AUTO_DEPOSIT, SCOPE_CRAFTING, SCOPE_FURNACE, SCOPE_PRODUCT_BUFFER);

    /**
     * 各功能的存储优先级：作用域 → 存储键顺序（RS / AE2 / TOMS / BACKPACK / BD，靠前者优先）。
     * <p>
     * 某作用域不存在（或列表为空）表示**沿用配置** {@code NetworkPriority.networkPriority}
     * 的默认顺序 —— 所以各功能默认行为与旧版本完全一致，只有玩家主动调整过的那个功能才会不同。
     */
    private final Map<String, List<String>> networkPriorities = new HashMap<>();

    /**
     * 自动入库的过滤模式：true = 白名单（只入库列表中的物品），false = 黑名单（列表中的物品不入库）。
     */
    private boolean autoDepositUseWhitelist = false;

    /** 自动入库过滤列表：物品 ID（如 {@code minecraft:diamond}）。 */
    private final Set<String> autoDepositFilterList = new HashSet<>();

    /**
     * 死亡不掉落（keep_inventory）的暂存物品。
     * <p>
     * 死亡瞬间把主背包 / 盔甲 / 副手清空并暂存在这里，重生时（{@code PlayerEvent.Clone}）
     * 原样归还，从而绕过原版「未开启 keepInventory 游戏规则就掉落全部物品」的行为。
     * 顺序固定为：36 格主背包 → 4 格盔甲 → 1 格副手。
     */
    private final List<ItemStack> pendingKeptInventory = new ArrayList<>();

    /**
     * 死亡不掉落（keep_inventory）暂存的 Curios 饰品。
     * <p>
     * 饰品栏不在原版 {@code Inventory} 里，也不受 keepInventory 游戏规则保护，所以和背包一样
     * 在死亡瞬间抓走清空（{@link com.infinitestats.compat.CuriosBridge#takeAll}），重生后放回
     * （{@link com.infinitestats.compat.CuriosBridge#restore}）。
     * 每条记录形如 {@code {slot: 槽位ID, index: 槽位下标, stack: 物品}}，可直接落盘。
     */
    private final List<CompoundTag> pendingKeptCurios = new ArrayList<>();

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

    public boolean isAutoDepositUseWhitelist() {
        return autoDepositUseWhitelist;
    }

    public Set<String> getAutoDepositFilterList() {
        return Collections.unmodifiableSet(autoDepositFilterList);
    }

    /** 是否设置了过滤条目（为空时无论白/黑名单都不做限制）。 */
    public boolean hasAutoDepositFilter() {
        return !autoDepositFilterList.isEmpty();
    }

    public void addAutoDepositFilter(String itemId) {
        if (itemId != null && !itemId.isEmpty()) autoDepositFilterList.add(itemId);
    }

    public void removeAutoDepositFilter(String itemId) {
        autoDepositFilterList.remove(itemId);
    }

    public void clearAutoDepositFilters() {
        autoDepositFilterList.clear();
    }

    /** 设置完整过滤列表（从网络同步 / 界面提交时调用）。 */
    public void setAutoDepositFilterList(Set<String> list, boolean useWhitelist) {
        autoDepositFilterList.clear();
        if (list != null) autoDepositFilterList.addAll(list);
        this.autoDepositUseWhitelist = useWhitelist;
    }

    /**
     * 判断某个物品是否允许自动入库。
     * <p>
     * 列表为空时视为「不做限制」——白名单为空若理解为「什么都不入库」，
     * 玩家一旦清空列表就会完全失去自动入库能力，容易误操作；这里统一按放行处理。
     * 白名单：只允许列表内的物品；黑名单：不允许列表内的物品。
     */
    public boolean allowsAutoDeposit(String itemId) {
        if (itemId == null || autoDepositFilterList.isEmpty()) return true;
        return autoDepositUseWhitelist == autoDepositFilterList.contains(itemId);
    }

    // ========== 死亡不掉落暂存 ==========

    public boolean hasPendingKeptInventory() {
        return !pendingKeptInventory.isEmpty();
    }

    public void setPendingKeptInventory(List<ItemStack> items) {
        pendingKeptInventory.clear();
        if (items == null) return;
        for (ItemStack stack : items) {
            pendingKeptInventory.add(stack == null ? ItemStack.EMPTY : stack.copy());
        }
    }

    /** 取出并清空暂存物品（重生归还时调用，避免重复归还）。 */
    public List<ItemStack> takePendingKeptInventory() {
        List<ItemStack> out = new ArrayList<>(pendingKeptInventory);
        pendingKeptInventory.clear();
        return out;
    }

    /** 暂存的 Curios 饰品是否为空。 */
    public boolean hasPendingKeptCurios() {
        return !pendingKeptCurios.isEmpty();
    }

    public void setPendingKeptCurios(List<CompoundTag> entries) {
        pendingKeptCurios.clear();
        if (entries == null) return;
        for (CompoundTag tag : entries) {
            if (tag != null) pendingKeptCurios.add(tag.copy());
        }
    }

    /** 取出并清空暂存的饰品（重生归还时调用，避免重复归还）。 */
    public List<CompoundTag> takePendingKeptCurios() {
        List<CompoundTag> out = new ArrayList<>(pendingKeptCurios);
        pendingKeptCurios.clear();
        return out;
    }

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
     * 有效属性点数：被「功能开关」关闭时按 0 计（投入的点数仍然保留，重新开启即恢复）。
     * 效果类代码若需要按「等级」而非「数值」计算，请用这个而不是 {@link #getStatLevel(String)}。
     */
    public long getEffectiveStatLevel(String statId) {
        return isStatInactive(statId) ? 0L : getStatLevel(statId);
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
     * 检查开关属性是否激活。
     * <p>
     * 被「功能开关」关闭的属性一律视为未激活：这样效果、指令、GUI 入口都会自动停用，
     * 无需在每个使用点重复判断。
     */
    public boolean isToggleActive(String statId) {
        if (isStatInactive(statId)) return false;
        ensureToggleCacheValid();
        return toggleCache.getOrDefault(statId, false);
    }

    // ========== 功能开关（每个玩家独立） ==========

    /**
     * 该属性是否已被玩家在「功能开关」中关闭（<b>面板层面</b>：不显示、不能加点）。
     * 与执行模式无关 —— 三种模式下面板都不显示它。
     */
    public boolean isStatDisabled(String statId) {
        return statId != null && disabledStats.contains(statId);
    }

    /**
     * 该属性是否**整条失效**（数值按 0 计、开关型功能的效果与指令停用）。
     * 「只隐藏」（{@link FeatureDisableMode#HIDDEN_ONLY}）模式下恒为 {@code false}。
     * <p>
     * 模式来自整合包配置（{@code GUI.featureDisableMode}），不是玩家个人设置。
     */
    public boolean isStatInactive(String statId) {
        return statId != null && FeatureDisableMode.current().isInactive()
                && disabledStats.contains(statId);
    }

    /**
     * 设置某个属性的开关状态（true = 关闭）。
     * 在 {@link FeatureDisableMode#INACTIVE_REFUND_POINTS} 模式下，关闭会同时返还该属性已投入的点数。
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

    /** 返还某个属性已投入的点数（整条失效 + 返还模式使用）。 */
    private void refundAllocatedPoints(String statId) {
        StatType stat = StatType.fromId(statId);
        if (stat != null) resetStat(stat);
    }

    /** 已关闭的属性 id（只读视图，供界面显示）。 */
    public Set<String> getDisabledStats() {
        return Collections.unmodifiableSet(disabledStats);
    }

    /**
     * 首次加载玩家数据时，把整合包作者预设的「开局默认禁用」列表写入本玩家。
     * 只执行一次（用 NBT 标志 {@code defaultDisabledApplied} 保证幂等）；
     * 之后作者改配置不会影响已有玩家，功能开放由任务奖励指令 {@code /infstats feature <id> on} 负责。
     */
    public void applyDefaultDisabledStatsIfNeeded() {
        if (defaultDisabledApplied) return;
        defaultDisabledApplied = true;
        List<String> defaults = Config.defaultDisabledStats();
        if (defaults.isEmpty()) return;
        boolean changed = false;
        for (String id : defaults) {
            if (StatType.fromId(id) != null && disabledStats.add(id)) {
                changed = true;
            }
        }
        if (changed) invalidateCache();
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

    /**
     * 重新读取「功能开关」的执行模式（全局配置）并让缓存失效。
     * <p>
     * 模式本身不按玩家存，读取时直接查配置；所以运行时改了 {@code GUI.featureDisableMode}
     * （例如 OP 用 {@code /infstats feature mode} 切换）后，调一次这个方法即可让
     * {@link #isStatInactive(String)} 与数值缓存按新模式重算。
     */
    public void refreshFeatureMode() {
        invalidateCache();
    }

    private void ensureCacheValid() {
        if (cacheValid) return;

        valueCache.clear();
        for (Map.Entry<String, Long> entry : allocatedPoints.entrySet()) {
            // 「功能开关」里整条失效的属性：数值按 0 计（点数是否保留取决于模式）。
            // 放在缓存层统一处理，所有读取 getStatValue 的效果、属性加成都自动跟随。
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

        // 序列化各功能的存储优先级（作用域 → 存储键列表）
        CompoundTag priorityTag = new CompoundTag();
        for (Map.Entry<String, List<String>> entry : networkPriorities.entrySet()) {
            ListTag orderList = new ListTag();
            for (String key : entry.getValue()) {
                CompoundTag keyTag = new CompoundTag();
                keyTag.putString("id", key);
                orderList.add(keyTag);
            }
            priorityTag.put(entry.getKey(), orderList);
        }
        tag.put("networkPriorities", priorityTag);

        // 序列化自动入库过滤（白 / 黑名单）
        tag.putBoolean("depositUseWhitelist", autoDepositUseWhitelist);
        ListTag depositFilter = new ListTag();
        for (String id : autoDepositFilterList) {
            CompoundTag fTag = new CompoundTag();
            fTag.putString("id", id);
            depositFilter.add(fTag);
        }
        tag.put("depositFilterList", depositFilter);

        // 序列化死亡不掉落暂存物品（必须落盘：玩家在死亡界面断线 / 服务器重启也不能丢）
        ListTag keptList = new ListTag();
        for (ItemStack stack : pendingKeptInventory) {
            keptList.add(stack.save(new CompoundTag()));
        }
        tag.put("pendingKeptInventory", keptList);

        // 序列化死亡不掉落暂存的 Curios 饰品（同样必须落盘，理由同上）
        ListTag keptCurios = new ListTag();
        for (CompoundTag entry : pendingKeptCurios) {
            keptCurios.add(entry.copy());
        }
        tag.put("pendingKeptCurios", keptCurios);

        // 序列化「功能开关」中已关闭的属性与执行模式
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

    /**
     * 旧存档迁移：「随身附魔台」由点数型（等级＝附魔强度，1~15）改为 1 点解锁的开关。
     * <p>
     * 附魔强度本身已经取消（进阶高级附魔台不再限制附魔等级），因此旧存档里超过 1 点的部分
     * <b>全部退回可用点数</b>：只留 1 点在开关上，多投的 N-1 点原样返还，玩家不亏。
     * 曾经被搬到隐藏属性 {@code enchant_power} 上的点数同样退回并清掉该键（幂等，重复加载不会重复退）。
     */
    private void migratePortableEnchanting() {
        Long legacy = allocatedPoints.get("portable_enchanting");
        if (legacy != null && legacy > 1L) {
            allocatedPoints.put("portable_enchanting", 1L);
            availablePoints += legacy - 1L;
        }
        Long power = allocatedPoints.remove("enchant_power");
        if (power != null && power > 0L) {
            availablePoints += power;
        }
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
            if (LEGACY_REMOVED_STATS.contains(statId)) {
                // 该属性定义已移除。必须放在 StatType.fromId 判断之前：
                // 属性定义删掉后 fromId 会返回 null，条目会被直接丢弃，点数就白丢了。
                // 按绝对值退还，负值（透支方向）同样返还。
                availablePoints += Math.abs(points);
                continue;
            }
            if (StatType.fromId(statId) != null) {
                allocatedPoints.put(statId, points);
            }
        }

        // 旧存档迁移：「随身附魔台」由点数型改为 1 点解锁的开关，多投的点数与旧附魔强度一并退回
        migratePortableEnchanting();

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

        // 反序列化各功能的存储优先级；老存档只有 autoDepositPriority，迁移成自动入库作用域
        networkPriorities.clear();
        if (tag.contains("networkPriorities")) {
            CompoundTag priorityTag = tag.getCompound("networkPriorities");
            for (String scope : priorityTag.getAllKeys()) {
                if (!NETWORK_SCOPES.contains(scope)) continue;
                ListTag orderList = priorityTag.getList(scope, Tag.TAG_COMPOUND);
                List<String> order = new ArrayList<>();
                for (int i = 0; i < orderList.size(); i++) {
                    String key = orderList.getCompound(i).getString("id");
                    if (!key.isEmpty()) order.add(key);
                }
                if (!order.isEmpty()) networkPriorities.put(scope, order);
            }
        } else if (tag.contains("autoDepositPriority")) {
            ListTag depositList = tag.getList("autoDepositPriority", Tag.TAG_COMPOUND);
            List<String> order = new ArrayList<>();
            for (int i = 0; i < depositList.size(); i++) {
                String key = depositList.getCompound(i).getString("id");
                if (!key.isEmpty()) order.add(key);
            }
            if (!order.isEmpty()) networkPriorities.put(SCOPE_AUTO_DEPOSIT, order);
        }

        // 反序列化自动入库过滤（白 / 黑名单）
        autoDepositUseWhitelist = tag.contains("depositUseWhitelist") && tag.getBoolean("depositUseWhitelist");
        autoDepositFilterList.clear();
        if (tag.contains("depositFilterList")) {
            ListTag depositFilter = tag.getList("depositFilterList", Tag.TAG_COMPOUND);
            for (int i = 0; i < depositFilter.size(); i++) {
                String id = depositFilter.getCompound(i).getString("id");
                if (!id.isEmpty()) autoDepositFilterList.add(id);
            }
        }

        // 反序列化死亡不掉落暂存物品
        pendingKeptInventory.clear();
        if (tag.contains("pendingKeptInventory")) {
            ListTag keptList = tag.getList("pendingKeptInventory", Tag.TAG_COMPOUND);
            for (int i = 0; i < keptList.size(); i++) {
                pendingKeptInventory.add(ItemStack.of(keptList.getCompound(i)));
            }
        }

        // 反序列化死亡不掉落暂存的 Curios 饰品
        pendingKeptCurios.clear();
        if (tag.contains("pendingKeptCurios")) {
            ListTag keptCurios = tag.getList("pendingKeptCurios", Tag.TAG_COMPOUND);
            for (int i = 0; i < keptCurios.size(); i++) {
                pendingKeptCurios.add(keptCurios.getCompound(i).copy());
            }
        }

        // 反序列化「功能开关」中已关闭的属性与执行模式
        disabledStats.clear();
        if (tag.contains("disabledStats")) {
            ListTag disabledList = tag.getList("disabledStats", Tag.TAG_COMPOUND);
            for (int i = 0; i < disabledList.size(); i++) {
                String id = disabledList.getCompound(i).getString("id");
                if (!id.isEmpty()) disabledStats.add(id);
            }
        }
        defaultDisabledApplied = tag.contains("defaultDisabledApplied") && tag.getBoolean("defaultDisabledApplied");


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
        this.allocatedPoints.clear();
        this.allocatedPoints.putAll(other.allocatedPoints);
        this.waypoints.clear();
        this.waypoints.putAll(other.waypoints);
        this.furnaceData.copyFrom(other.furnaceData);
        this.craftingMultiplier = other.craftingMultiplier;
        this.favorites.clear();
        this.favorites.addAll(other.favorites);
        // 死亡不掉落暂存的物品必须随玩家实体克隆一起带走，否则重生后就找不回来了
        this.pendingKeptInventory.clear();
        for (ItemStack stack : other.pendingKeptInventory) {
            this.pendingKeptInventory.add(stack.copy());
        }
        // 饰品暂存同理
        this.pendingKeptCurios.clear();
        for (CompoundTag entry : other.pendingKeptCurios) {
            this.pendingKeptCurios.add(entry.copy());
        }
        this.networkPriorities.clear();
        for (Map.Entry<String, List<String>> entry : other.networkPriorities.entrySet()) {
            this.networkPriorities.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        this.autoDepositUseWhitelist = other.autoDepositUseWhitelist;
        this.autoDepositFilterList.clear();
        this.autoDepositFilterList.addAll(other.autoDepositFilterList);
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
                new HashMap<>(waypoints),
                reviveInvulnUntilTick,
                new HashSet<>(favorites),
                copyNetworkPriorities(),
                autoDepositUseWhitelist,
                new HashSet<>(autoDepositFilterList),
                new HashSet<>(disabledStats)
        );
    }

    /** 深拷贝各功能的存储优先级（快照/同步用）。 */
    private Map<String, List<String>> copyNetworkPriorities() {
        Map<String, List<String>> copy = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : networkPriorities.entrySet()) {
            copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return copy;
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
        this.waypoints.clear();
        this.waypoints.putAll(snapshot.waypoints);
        this.favorites.clear();
        this.favorites.addAll(snapshot.favorites);
        this.networkPriorities.clear();
        if (snapshot.networkPriorities != null) {
            for (Map.Entry<String, List<String>> entry : snapshot.networkPriorities.entrySet()) {
                this.networkPriorities.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }
        this.autoDepositUseWhitelist = snapshot.autoDepositUseWhitelist;
        this.autoDepositFilterList.clear();
        if (snapshot.autoDepositFilterList != null) this.autoDepositFilterList.addAll(snapshot.autoDepositFilterList);
        this.disabledStats.clear();
        if (snapshot.disabledStats != null) this.disabledStats.addAll(snapshot.disabledStats);
        invalidateCache();
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
        public final Map<String, Waypoint> waypoints;
        public final long reviveInvulnUntilTick;
        public final Set<String> favorites;
        /** 各功能的存储优先级（作用域 → 存储键顺序；缺失的作用域 = 使用配置默认顺序） */
        public final Map<String, List<String>> networkPriorities;
        /** 自动入库过滤模式：true = 白名单 */
        public final boolean autoDepositUseWhitelist;
        /** 自动入库过滤列表（物品 ID） */
        public final Set<String> autoDepositFilterList;
        /** 「功能开关」中已关闭的属性 id */
        public final Set<String> disabledStats;

        public StatsSnapshot(long level, long experience, long availablePoints,
                long lastReviveTime, int passiveTickCounter,
                Map<String, Long> allocatedPoints,
                boolean buffUseBlacklist, Set<String> buffFilterList,
                Map<String, Waypoint> waypoints, long reviveInvulnUntilTick, Set<String> favorites,
                Map<String, List<String>> networkPriorities,
                boolean autoDepositUseWhitelist, Set<String> autoDepositFilterList,
                Set<String> disabledStats) {
            this.level = level;
            this.experience = experience;
            this.availablePoints = availablePoints;
            this.lastReviveTime = lastReviveTime;
            this.passiveTickCounter = passiveTickCounter;
            this.allocatedPoints = allocatedPoints;
            this.buffUseBlacklist = buffUseBlacklist;
            this.buffFilterList = buffFilterList;
            this.waypoints = waypoints;
            this.reviveInvulnUntilTick = reviveInvulnUntilTick;
            this.favorites = favorites;
            this.networkPriorities = networkPriorities;
            this.autoDepositUseWhitelist = autoDepositUseWhitelist;
            this.autoDepositFilterList = autoDepositFilterList;
            this.disabledStats = disabledStats;
        }
    }
}