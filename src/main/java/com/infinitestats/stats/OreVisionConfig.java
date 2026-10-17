package com.infinitestats.stats;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 「矿透」的个人设置 —— 每个玩家一份，跟着属性数据一起存档并同步到客户端。
 * <p>
 * 与 {@link VeinMinerConfig} 走同一条链路：面板改 → 提交到服务端 {@code sanitize()} 后落库 → 回同步。
 * 之所以不走纯客户端配置（{@code config/infinite_stats-client.json}）：矿透的可见范围与筛选
 * 在多人环境下属于「玩家自己的能力设置」，跟着角色存档走才不会换台电脑就丢。
 * <p>
 * 服务端只做范围夹取（{@link #MAX_RANGE} / {@link #MAX_BLOCKS_LIMIT}），
 * 真正的扫描与绘制全在客户端本地完成 —— 服务端不发也不认任何方块数据。
 */
public class OreVisionConfig {

    /** 矿物分组：决定线框颜色与「显示哪些矿物」的筛选粒度。 */
    public enum OreGroup {
        COAL,
        IRON,
        COPPER,
        GOLD,
        REDSTONE,
        LAPIS,
        DIAMOND,
        EMERALD,
        QUARTZ,
        DEBRIS,
        /** 其余矿石（模组矿石多半落在这里）。 */
        OTHER;

        public static OreGroup byName(String name) {
            if (name != null) {
                for (OreGroup group : values()) {
                    if (group.name().equalsIgnoreCase(name)) return group;
                }
            }
            return OTHER;
        }

        /** 语言文件 / 界面用的键后缀。 */
        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** 线框颜色（RGB，各分量 0~1）。 */
        public float[] color() {
            return switch (this) {
                case COAL -> new float[]{0.62F, 0.62F, 0.62F};
                case IRON -> new float[]{0.90F, 0.72F, 0.58F};
                case COPPER -> new float[]{0.90F, 0.52F, 0.28F};
                case GOLD -> new float[]{1.00F, 0.85F, 0.25F};
                case REDSTONE -> new float[]{1.00F, 0.30F, 0.28F};
                case LAPIS -> new float[]{0.32F, 0.48F, 1.00F};
                case DIAMOND -> new float[]{0.36F, 0.92F, 0.90F};
                case EMERALD -> new float[]{0.28F, 0.90F, 0.42F};
                case QUARTZ -> new float[]{0.95F, 0.92F, 0.85F};
                case DEBRIS -> new float[]{0.74F, 0.50F, 0.70F};
                case OTHER -> new float[]{0.95F, 0.45F, 0.85F};
            };
        }
    }

    /** 扫描半径下限（方块，切比雪夫距离）。 */
    public static final int MIN_RANGE = 8;
    /** 扫描半径上限：再往上单次扫描就要跑十几万个方块位，客户端会掉帧。 */
    public static final int MAX_RANGE = 64;
    /** 同屏描边的矿石数量下限 / 上限（上限防止成片矿区把帧率拖垮）。 */
    public static final int MIN_BLOCKS = 100;
    public static final int MAX_BLOCKS_LIMIT = 8000;

    // ======================== 字段 ========================

    /** 扫描半径（方块）。 */
    private int range = 24;

    /** 单次最多描边多少格矿石。 */
    private int maxBlocks = 1500;

    /** true = 必须潜行才显示（免得平时一直挂着满屏边框）。 */
    private boolean requireSneak = false;

    /** 被关掉的分组（默认空 = 全部显示）。 */
    private final Set<OreGroup> hiddenGroups = new LinkedHashSet<>();

    // ======================== 读写 ========================

    public int getRange() {
        return range;
    }

    public void setRange(int range) {
        this.range = clampInt(range, MIN_RANGE, MAX_RANGE);
    }

    public int getMaxBlocks() {
        return maxBlocks;
    }

    public void setMaxBlocks(int maxBlocks) {
        this.maxBlocks = clampInt(maxBlocks, MIN_BLOCKS, MAX_BLOCKS_LIMIT);
    }

    public boolean isRequireSneak() {
        return requireSneak;
    }

    public void setRequireSneak(boolean requireSneak) {
        this.requireSneak = requireSneak;
    }

    /** 该分组是否被隐藏。 */
    public boolean isGroupHidden(OreGroup group) {
        return group != null && hiddenGroups.contains(group);
    }

    public void setGroupHidden(OreGroup group, boolean hidden) {
        if (group == null) return;
        if (hidden) {
            hiddenGroups.add(group);
        } else {
            hiddenGroups.remove(group);
        }
    }

    /** 展示用的「显示中的分组数 / 总分组数」。 */
    public int visibleGroupCount() {
        return OreGroup.values().length - hiddenGroups.size();
    }

    public boolean hasHiddenGroups() {
        return !hiddenGroups.isEmpty();
    }

    public Set<OreGroup> getHiddenGroups() {
        return Collections.unmodifiableSet(hiddenGroups);
    }

    // ======================== 工具方法 ========================

    public void resetToDefault() {
        range = 24;
        maxBlocks = 1500;
        requireSneak = false;
        hiddenGroups.clear();
    }

    public OreVisionConfig copy() {
        OreVisionConfig out = new OreVisionConfig();
        out.range = range;
        out.maxBlocks = maxBlocks;
        out.requireSneak = requireSneak;
        out.hiddenGroups.addAll(hiddenGroups);
        return out;
    }

    /** 客户端提交过来的数据一律夹一遍范围（服务端唯一的信任边界）。 */
    public void sanitize() {
        range = clampInt(range, MIN_RANGE, MAX_RANGE);
        maxBlocks = clampInt(maxBlocks, MIN_BLOCKS, MAX_BLOCKS_LIMIT);
    }

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("range", range);
        tag.putInt("maxBlocks", maxBlocks);
        tag.putBoolean("requireSneak", requireSneak);
        if (!hiddenGroups.isEmpty()) {
            ListTag list = new ListTag();
            for (OreGroup group : hiddenGroups) list.add(StringTag.valueOf(group.name()));
            tag.put("hiddenGroups", list);
        }
        return tag;
    }

    public static OreVisionConfig deserialize(CompoundTag tag) {
        OreVisionConfig cfg = new OreVisionConfig();
        if (tag == null || tag.isEmpty()) return cfg;

        cfg.range = tag.contains("range") ? tag.getInt("range") : 24;
        cfg.maxBlocks = tag.contains("maxBlocks") ? tag.getInt("maxBlocks") : 1500;
        cfg.requireSneak = tag.contains("requireSneak") && tag.getBoolean("requireSneak");

        if (tag.contains("hiddenGroups")) {
            ListTag list = tag.getList("hiddenGroups", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                String name = list.getString(i);
                if (!name.isBlank()) cfg.hiddenGroups.add(OreGroup.byName(name));
            }
        }
        cfg.sanitize();
        return cfg;
    }

    /** 界面里的分组清单（顺序固定，便于画成两列网格）。 */
    public static List<OreGroup> groups() {
        return List.of(OreGroup.values());
    }

    private static int clampInt(int v, int min, int max) {
        if (v < min) return min;
        return Math.min(v, max);
    }
}
