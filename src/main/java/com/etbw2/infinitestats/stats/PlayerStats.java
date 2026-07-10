package com.etbw2.infinitestats.stats;

import com.etbw2.infinitestats.Config;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 玩家属性数据 - 使用高效的数据结构和缓存机制
 * 支持快速查找、批量操作和值缓存
 */
public class PlayerStats {

    // ========== 核心数据 ==========

    private int level = 1;
    private int experience = 0;
    private int availablePoints = 0;
    private long lastReviveTime = 0;
    private float currentMana = 0;

    // 使用数组存储属性点数 - 比Map更高效
    private final int[] allocatedPoints = new int[StatType.ALL_STATS.length];

    // 属性值缓存 - 避免重复计算
    private final float[] valueCache = new float[StatType.ALL_STATS.length];
    private boolean cacheValid = false;

    // 开关属性激活状态缓存
    private final boolean[] toggleCache = new boolean[StatType.ALL_STATS.length];
    private boolean toggleCacheValid = false;

    // 每个玩家独立的被动经验tick计数器
    private int passiveTickCounter = 0;

    // 记录本模组当前正在提供的能力（用于区分本模组 vs 其他模组给予的效果）
    // 键为 toggle 属性 ID（如 "fly", "night_vision", "invisibility", "absorption_shield"）
    private final Set<String> providedAbilities = new HashSet<>();

    // ========== 构造器 ==========

    public PlayerStats() {
        Arrays.fill(allocatedPoints, 0);
        Arrays.fill(valueCache, 0);
        Arrays.fill(toggleCache, false);
    }

    // ========== 等级与经验 ==========

    public int getLevel() {
        return level;
    }

    public int getExperience() {
        return experience;
    }

    public int getAvailablePoints() {
        return availablePoints;
    }

    public int getXpForNextLevel() {
        return Config.BASE_XP_PER_LEVEL.get() + (level - 1) * Config.XP_PER_LEVEL_INCREMENT.get();
    }

    /**
     * 添加经验值，自动升级
     * @return 是否升级了
     */
    public boolean addExperience(int amount) {
        if (amount <= 0) return false;

        experience += amount;
        boolean leveledUp = false;

        while (experience >= getXpForNextLevel()) {
            experience -= getXpForNextLevel();
            level++;
            availablePoints += Config.POINTS_PER_LEVEL.get();
            leveledUp = true;
        }

        return leveledUp;
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

    // ========== 属性点数 ==========

    /**
     * 获取属性点数 - 使用索引快速查找
     */
    public int getStatLevel(StatType stat) {
        int index = getStatIndex(stat);
        return index >= 0 ? allocatedPoints[index] : 0;
    }

    /**
     * 通过ID获取属性点数
     */
    public int getStatLevel(String statId) {
        StatType stat = StatType.fromId(statId);
        return stat != null ? getStatLevel(stat) : 0;
    }

    /**
     * 获取属性值 - 使用缓存
     */
    public float getStatValue(StatType stat) {
        ensureCacheValid();
        int index = getStatIndex(stat);
        return index >= 0 ? valueCache[index] : 0;
    }

    /**
     * 通过ID获取属性值
     */
    public float getStatValue(String statId) {
        StatType stat = StatType.fromId(statId);
        return stat != null ? getStatValue(stat) : 0;
    }

    /**
     * 检查开关属性是否激活
     */
    public boolean isToggleActive(String statId) {
        ensureToggleCacheValid();
        StatType stat = StatType.fromId(statId);
        if (stat == null) return false;
        int index = getStatIndex(stat);
        return index >= 0 ? toggleCache[index] : false;
    }

    // ========== 属性分配 ==========

    /**
     * 添加属性点
     */
    public boolean addPoint(StatType stat) {
        return addPoints(stat, 1);
    }

    /**
     * 批量添加属性点
     */
    public boolean addPoints(StatType stat, int count) {
        if (count <= 0 || availablePoints <= 0) return false;

        int index = getStatIndex(stat);
        if (index < 0) return false;

        int current = allocatedPoints[index];

        // 开关型属性：一次性加到激活阈值
        if (stat.isToggle() && current < stat.getMaxLevel()) {
            count = stat.getMaxLevel() - current;
        }

        int max = stat.getMaxLevel();
        int space = max - current;
        int toAdd = Math.min(Math.min(count, space), availablePoints);

        if (toAdd <= 0) return false;

        allocatedPoints[index] = current + toAdd;
        availablePoints -= toAdd;
        invalidateCache();

        return true;
    }

    /**
     * 移除属性点（返还）
     */
    public boolean removePoints(StatType stat, int count) {
        if (count <= 0) return false;

        int index = getStatIndex(stat);
        if (index < 0) return false;

        int current = allocatedPoints[index];

        // 开关型属性：一次性全部移除
        if (stat.isToggle()) {
            count = current;
        }

        int toRemove = Math.min(count, current);

        if (toRemove <= 0) return false;

        allocatedPoints[index] = current - toRemove;
        availablePoints += toRemove;
        invalidateCache();

        return true;
    }

    /**
     * 重置所有属性点
     */
    public void resetAllPoints() {
        int totalAllocated = Arrays.stream(allocatedPoints).sum();
        Arrays.fill(allocatedPoints, 0);
        availablePoints += totalAllocated;
        clearAllProvided(); // 清除所有能力提供记录
        invalidateCache();
    }

    /**
     * 重置单个属性
     */
    public void resetStat(StatType stat) {
        int index = getStatIndex(stat);
        if (index >= 0) {
            availablePoints += allocatedPoints[index];
            allocatedPoints[index] = 0;
            // 如果重置的是开关型属性，清除对应的能力提供记录
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

        for (int i = 0; i < StatType.ALL_STATS.length; i++) {
            StatType stat = StatType.ALL_STATS[i];
            valueCache[i] = stat.calculateValue(allocatedPoints[i]);
        }
        cacheValid = true;
    }

    private void ensureToggleCacheValid() {
        if (toggleCacheValid) return;

        for (int i = 0; i < StatType.ALL_STATS.length; i++) {
            StatType stat = StatType.ALL_STATS[i];
            toggleCache[i] = stat.isToggle() && allocatedPoints[i] >= stat.getMaxLevel();
        }
        toggleCacheValid = true;
    }

    // ========== 工具方法 ==========

    private int getStatIndex(StatType stat) {
        for (int i = 0; i < StatType.ALL_STATS.length; i++) {
            if (StatType.ALL_STATS[i] == stat) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 获取所有已分配属性的总点数
     */
    public int getTotalAllocatedPoints() {
        return Arrays.stream(allocatedPoints).sum();
    }

    /**
     * 获取某类别下的总点数
     */
    public int getCategoryPoints(StatCategory category) {
        int total = 0;
        for (int i = 0; i < StatType.ALL_STATS.length; i++) {
            if (StatType.ALL_STATS[i].getCategory() == category) {
                total += allocatedPoints[i];
            }
        }
        return total;
    }

    // ========== NBT序列化 ==========

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("level", level);
        tag.putInt("experience", experience);
        tag.putInt("availablePoints", availablePoints);
        tag.putLong("lastReviveTime", lastReviveTime);
        tag.putFloat("currentMana", currentMana);

        // 使用更紧凑的格式存储属性点数
        ListTag pointsList = new ListTag();
        for (int i = 0; i < StatType.ALL_STATS.length; i++) {
            if (allocatedPoints[i] > 0) {
                CompoundTag entryTag = new CompoundTag();
                entryTag.putString("id", StatType.ALL_STATS[i].getId());
                entryTag.putInt("points", allocatedPoints[i]);
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

        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        level = tag.getInt("level");
        experience = tag.getInt("experience");
        availablePoints = tag.getInt("availablePoints");
        lastReviveTime = tag.getLong("lastReviveTime");
        currentMana = tag.getFloat("currentMana");

        // 重置所有属性点
        Arrays.fill(allocatedPoints, 0);

        ListTag pointsList = tag.getList("allocatedPoints", Tag.TAG_COMPOUND);
        for (int i = 0; i < pointsList.size(); i++) {
            CompoundTag entryTag = pointsList.getCompound(i);
            String statId = entryTag.getString("id");
            int points = entryTag.getInt("points");
            StatType stat = StatType.fromId(statId);
            if (stat != null) {
                int index = getStatIndex(stat);
                if (index >= 0) {
                    allocatedPoints[index] = points;
                }
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
        int len = Math.min(this.allocatedPoints.length, other.allocatedPoints.length);
        System.arraycopy(other.allocatedPoints, 0, this.allocatedPoints, 0, len);
        invalidateCache();
    }

    /**
     * 创建快照用于同步
     */
    public StatsSnapshot createSnapshot() {
        return new StatsSnapshot(
                level, experience, availablePoints,
                lastReviveTime, currentMana, passiveTickCounter,
                Arrays.copyOf(allocatedPoints, allocatedPoints.length)
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
        int len = Math.min(this.allocatedPoints.length, snapshot.allocatedPoints.length);
        System.arraycopy(snapshot.allocatedPoints, 0, this.allocatedPoints, 0, len);
        invalidateCache();
    }

    // ========== 快照类（用于网络同步） ==========

    public static class StatsSnapshot {
        public final int level;
        public final int experience;
        public final int availablePoints;
        public final long lastReviveTime;
        public final float currentMana;
        public final int passiveTickCounter;
        public final int[] allocatedPoints;

        public StatsSnapshot(int level, int experience, int availablePoints,
                long lastReviveTime, float currentMana, int passiveTickCounter, int[] allocatedPoints) {
            this.level = level;
            this.experience = experience;
            this.availablePoints = availablePoints;
            this.lastReviveTime = lastReviveTime;
            this.currentMana = currentMana;
            this.passiveTickCounter = passiveTickCounter;
            this.allocatedPoints = allocatedPoints;
        }
    }
}