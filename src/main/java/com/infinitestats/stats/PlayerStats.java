package com.infinitestats.stats;

import com.infinitestats.Config;
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
    private float currentMana = 0;

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

    // 记录本模组当前正在提供的能力（用于区分本模组 vs 其他模组给予的效果）
    private final Set<String> providedAbilities = new HashSet<>();

    // debuff_immunity 过滤模式：true = 黑名单（列表中的拦截），false = 白名单（列表中的绝对不拦截，其他不管）
    private boolean debuffUseBlacklist = true;
    // debuff 效果 ID 列表（如 "minecraft:poison"）
    private final Set<String> debuffFilterList = new HashSet<>();

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
     * 允许负值——支持属性扣到负数（借用未来点数）
     */
    public void recalculateAvailablePoints() {
        long totalEarned = (level - 1) * (long) Config.POINTS_PER_LEVEL.get();
        long totalAllocated = allocatedPoints.values().stream().mapToLong(Long::longValue).sum();
        this.availablePoints = totalEarned - totalAllocated;
    }

    // ========== 复活 ==========

    public long getLastReviveTime() {
        return lastReviveTime;
    }

    public void setLastReviveTime(long time) {
        this.lastReviveTime = time;
    }

    // ========== 法力 ==========

    public float getCurrentMana() {
        return currentMana;
    }

    public void setCurrentMana(float mana) {
        this.currentMana = Math.max(0, mana);
    }

    public float getMaxMana() {
        return getStatValue("max_mana");
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

    // ========== Debuff 过滤系统 ==========

    /**
     * 获取过滤模式：true=黑名单（不拦截列表中的），false=白名单（只拦截列表中的）
     */
    public boolean isDebuffUseBlacklist() {
        return debuffUseBlacklist;
    }

    /**
     * 设置过滤模式
     */
    public void setDebuffUseBlacklist(boolean blacklist) {
        this.debuffUseBlacklist = blacklist;
    }

    /**
     * 获取过滤列表（不可变副本）
     */
    public Set<String> getDebuffFilterList() {
        return Collections.unmodifiableSet(debuffFilterList);
    }

    /**
     * 添加效果到过滤列表
     */
    public void addDebuffFilter(String effectId) {
        debuffFilterList.add(effectId);
    }

    /**
     * 从过滤列表移除效果
     */
    public void removeDebuffFilter(String effectId) {
        debuffFilterList.remove(effectId);
    }

    /**
     * 清空过滤列表
     */
    public void clearDebuffFilters() {
        debuffFilterList.clear();
    }

    /**
     * 设置完整过滤列表（从网络同步）
     */
    public void setDebuffFilterList(Set<String> list, boolean useBlacklist) {
        debuffFilterList.clear();
        debuffFilterList.addAll(list);
        this.debuffUseBlacklist = useBlacklist;
    }

    /**
     * 检查某个效果是否应该被拦截
     * @return true = 应该拦截
     */
    public boolean shouldBlockEffect(String effectId) {
        boolean inList = debuffFilterList.contains(effectId);
        if (debuffUseBlacklist) {
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

    // ========== 属性分配 ==========

    /**
     * 添加属性点
     */
    public boolean addPoint(StatType stat) {
        return addPoints(stat, 1L);
    }

    /**
     * 批量添加属性点（允许可用点数为负——借用未来点数）
     * "+" 统一语义：值往正向走（current += count）
     *   current >= 0 → 消耗点数；current < 0 → 返还点数（因为绝对值变小）
     */
    public boolean addPoints(StatType stat, long count) {
        if (count <= 0) return false;

        long current = allocatedPoints.getOrDefault(stat.getId(), 0L);

        // 开关型属性：一次性加到激活阈值
        if (stat.isToggle() && current < stat.getMaxLevel()) {
            count = stat.getMaxLevel() - current;
        }

        if (current >= 0) {
            // 正值方向：往上加（最多到 maxLevel），消耗点数
            long max = stat.getMaxLevel();
            long space = max - current;
            long toAdd = Math.min(count, space);
            if (toAdd <= 0) return false;
            allocatedPoints.put(stat.getId(), current + toAdd);
            availablePoints -= toAdd;
        } else {
            // 负值方向：靠近 0，返还点数（绝对值变小）
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
     * 移除属性点（允许降入负数）
     * "-" 统一语义：值往负向走（current -= count）
     *   current > 0：正向部分返还点数，越过 0 后的负向部分消耗点数
     *   current <= 0：继续往负走，消耗点数
     */
    public boolean removePoints(StatType stat, long count) {
        if (count <= 0) return false;

        long current = allocatedPoints.getOrDefault(stat.getId(), 0L);

        // 开关型属性：一次性全部移除
        if (stat.isToggle()) {
            count = current;
        }

        if (count <= 0) return false;

        long newVal = current - count;

        if (newVal == 0) {
            allocatedPoints.remove(stat.getId());
        } else {
            allocatedPoints.put(stat.getId(), newVal);
        }

        // 点数核算
        if (current > 0) {
            // 从正值往负走：正向部分 (min(current, count)) 返还点数
            // 若跨过 0 进入负数，超出部分 (count - min(current, count)) 消耗点数
            long returned = Math.min(current, count);
            availablePoints += returned;
            availablePoints -= (count - returned);
        } else {
            // 从 0 或负数继续往负走：消耗点数
            availablePoints -= count;
        }

        invalidateCache();
        return true;
    }

    /**
     * 重置所有属性点
     */
    public void resetAllPoints() {
        long totalAllocated = allocatedPoints.values().stream().mapToLong(Long::longValue).sum();
        allocatedPoints.clear();
        availablePoints += totalAllocated;
        clearAllProvided();
        invalidateCache();
    }

    /**
     * 重置单个属性（清零，返还所有已分配点数，含负数）
     */
    public void resetStat(StatType stat) {
        long points = allocatedPoints.getOrDefault(stat.getId(), 0L);
        if (points != 0) {
            availablePoints += points; // 负数时 also adjusts availablePoints
            allocatedPoints.remove(stat.getId());
            if (stat.isToggle()) {
                providedAbilities.remove(stat.getId());
            }
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
        return allocatedPoints.values().stream().mapToLong(Long::longValue).sum();
    }

    /**
     * 获取某类别下的总点数
     */
    public long getCategoryPoints(StatCategory category) {
        long total = 0;
        for (Map.Entry<String, Long> entry : allocatedPoints.entrySet()) {
            StatType stat = StatType.fromId(entry.getKey());
            if (stat != null && stat.getCategory() == category) {
                total += entry.getValue();
            }
        }
        return total;
    }

    // ========== NBT序列化 ==========

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("level", level);
        tag.putLong("experience", experience);
        tag.putLong("availablePoints", availablePoints);
        tag.putLong("lastReviveTime", lastReviveTime);
        tag.putFloat("currentMana", currentMana);

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

        // 序列化 debuff 过滤列表
        tag.putBoolean("debuffUseBlacklist", debuffUseBlacklist);
        ListTag filterList = new ListTag();
        for (String effectId : debuffFilterList) {
            CompoundTag fTag = new CompoundTag();
            fTag.putString("id", effectId);
            filterList.add(fTag);
        }
        tag.put("debuffFilterList", filterList);

        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        level = tag.getLong("level");
        experience = tag.getLong("experience");
        availablePoints = tag.getLong("availablePoints");
        lastReviveTime = tag.getLong("lastReviveTime");
        currentMana = tag.getFloat("currentMana");

        // 重置所有属性点
        allocatedPoints.clear();

        ListTag pointsList = tag.getList("allocatedPoints", Tag.TAG_COMPOUND);
        for (int i = 0; i < pointsList.size(); i++) {
            CompoundTag entryTag = pointsList.getCompound(i);
            String statId = entryTag.getString("id");
            long points = entryTag.getLong("points");
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
        debuffUseBlacklist = tag.contains("debuffUseBlacklist") ? tag.getBoolean("debuffUseBlacklist") : true;
        debuffFilterList.clear();
        if (tag.contains("debuffFilterList")) {
            ListTag filterList = tag.getList("debuffFilterList", Tag.TAG_COMPOUND);
            for (int i = 0; i < filterList.size(); i++) {
                debuffFilterList.add(filterList.getCompound(i).getString("id"));
            }
        }

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
        this.currentMana = other.currentMana;
        this.passiveTickCounter = other.passiveTickCounter;
        this.providedAbilities.clear();
        this.providedAbilities.addAll(other.providedAbilities);
        this.debuffUseBlacklist = other.debuffUseBlacklist;
        this.debuffFilterList.clear();
        this.debuffFilterList.addAll(other.debuffFilterList);
        this.allocatedPoints.clear();
        this.allocatedPoints.putAll(other.allocatedPoints);
        invalidateCache();
    }

    /**
     * 创建快照用于同步
     */
    public StatsSnapshot createSnapshot() {
        return new StatsSnapshot(
                level, experience, availablePoints,
                lastReviveTime, currentMana, passiveTickCounter,
                new HashMap<>(allocatedPoints),
                debuffUseBlacklist,
                new HashSet<>(debuffFilterList)
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
        this.currentMana = snapshot.currentMana;
        this.passiveTickCounter = snapshot.passiveTickCounter;
        this.allocatedPoints.clear();
        this.allocatedPoints.putAll(snapshot.allocatedPoints);
        this.debuffUseBlacklist = snapshot.debuffUseBlacklist;
        this.debuffFilterList.clear();
        this.debuffFilterList.addAll(snapshot.debuffFilterList);
        invalidateCache();
    }

    // ========== 快照类（用于网络同步） ==========

    public static class StatsSnapshot {
        public final long level;
        public final long experience;
        public final long availablePoints;
        public final long lastReviveTime;
        public final float currentMana;
        public final int passiveTickCounter;
        public final Map<String, Long> allocatedPoints;
        public final boolean debuffUseBlacklist;
        public final Set<String> debuffFilterList;

        public StatsSnapshot(long level, long experience, long availablePoints,
                long lastReviveTime, float currentMana, int passiveTickCounter,
                Map<String, Long> allocatedPoints,
                boolean debuffUseBlacklist, Set<String> debuffFilterList) {
            this.level = level;
            this.experience = experience;
            this.availablePoints = availablePoints;
            this.lastReviveTime = lastReviveTime;
            this.currentMana = currentMana;
            this.passiveTickCounter = passiveTickCounter;
            this.allocatedPoints = allocatedPoints;
            this.debuffUseBlacklist = debuffUseBlacklist;
            this.debuffFilterList = debuffFilterList;
        }
    }
}