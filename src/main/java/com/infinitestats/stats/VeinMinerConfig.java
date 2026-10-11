package com.infinitestats.stats;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 连锁挖掘的个人设置 —— 每个玩家一份，跟着属性数据一起存档并同步到客户端。
 * <p>
 * 之所以做成「玩家个人设置」而不是纯服务端配置：属性面板里已经有一套
 * 「玩家自己的设置 + 数据包提交 → 服务端保存 → 回同步」的链路（存储优先级、入库名单），
 * 连锁的细调项跟着走同一条路，才能在游戏里用面板改。
 * <p>
 * 服务端仍然握着两道硬上限：{@code Config.VEIN_MINER_MAX_BLOCKS}（面板里「跟随服务端」时用的默认值）
 * 与 {@link #MAX_BLOCKS_LIMIT} / {@link #MAX_RADIUS_LIMIT}（提交时的夹取范围），
 * 避免客户端改出离谱数值。
 */
public class VeinMinerConfig {

    /** 方块匹配方式 */
    public enum MatchMode {
        /**
         * 自动（默认，对齐 FTB Ultimine 的口径）：原方块命中「算同一类」的标签就用标签组匹配；
         * 否则若它属于作物，则按**同一种作物**匹配（避免所有作物共享的宽泛标签把胡萝卜一起带走）；
         * 再否则只连完全相同的方块。
         */
        AUTO,
        /** 只连锁完全相同的方块（最保守） */
        SAME_BLOCK,
        /** 一律按标签组连锁：共享「矿物 / 原木」这类标签即视为同类 */
        TAG_GROUP,
        /** 按下面的自定义名单连锁（白名单 / 黑名单） */
        FILTER_LIST;

        public static MatchMode byName(String name) {
            if (name != null) {
                for (MatchMode mode : values()) {
                    if (mode.name().equalsIgnoreCase(name)) return mode;
                }
            }
            return AUTO;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** 单次连锁方块数的绝对上限 */
    public static final int MAX_BLOCKS_LIMIT = 256;
    /** 搜索半径的绝对上限（方块，切比雪夫距离） */
    public static final int MAX_RADIUS_LIMIT = 48;
    /** 每方块饥饿消耗的绝对上限 */
    public static final double MAX_EXHAUSTION = 1.0D;
    /** 每方块经验消耗的绝对上限（点） */
    public static final double MAX_XP_PER_BLOCK = 100.0D;

    /** 「跟随服务端配置」的哨兵值 */
    public static final int USE_SERVER_LIMIT = -1;

    // ======================== 字段 ========================

    private MatchMode matchMode = MatchMode.AUTO;

    /** true = 右键整片：斧剥皮 / 铲平路 / 锄耕地 / 锄收作物（对同类方块批量执行） */
    private boolean rightClickVein = true;

    /** FILTER_LIST 模式下：true = 白名单（只连锁名单内的），false = 黑名单 */
    private boolean useWhitelist = true;

    /**
     * 白名单 / 黑名单条目：方块 id（{@code minecraft:iron_ore}）或方块标签（{@code #forge:ores/iron}）。
     * <p>
     * 两份名单各自独立保存：切换模式时看到的是该模式自己的名单，
     * 不会把另一边勾选的条目带过来。
     */
    private final Set<String> whitelist = new LinkedHashSet<>();
    private final Set<String> blacklist = new LinkedHashSet<>();

    /** 单次连锁方块数；{@link #USE_SERVER_LIMIT} = 用服务端配置里的默认值 */
    private int maxBlocks = USE_SERVER_LIMIT;

    /** 搜索半径（方块）；{@link #USE_SERVER_LIMIT} = 不限（仅受方块数限制） */
    private int maxRadius = USE_SERVER_LIMIT;

    /** true = 26 邻域（含斜向），false = 6 邻域（只连上下左右前后） */
    private boolean diagonal = true;

    /** true = 工具必须能正常采集该方块才连锁 */
    private boolean requireCorrectTool = true;

    /** true = 必须潜行才连锁（避免想单挖一格时被连锁） */
    private boolean requireSneak = false;

    /** true = 工具耐久只剩最后 1 点时停止连锁，避免连锁把工具挖爆 */
    private boolean protectTool = true;

    /** true = 连锁破坏的掉落直接进背包（背包放不下才掉在地上，省得满地捡） */
    private boolean collectDrops = false;

    /** true = 用准星对着方块时，把会被连锁的方块描出边框（客户端本地计算，纯显示） */
    private boolean showPreview = true;

    /** 每破坏一格的饥饿消耗（0 = 不消耗） */
    private double exhaustionPerBlock = 0.005D;

    /** 每破坏一格消耗的经验点数（0 = 不消耗） */
    private double xpPerBlock = 0.0D;

    // ======================== 读写 ========================

    public MatchMode getMatchMode() {
        return matchMode;
    }

    public void setMatchMode(MatchMode matchMode) {
        this.matchMode = matchMode == null ? MatchMode.AUTO : matchMode;
    }

    public boolean isRightClickVein() {
        return rightClickVein;
    }

    public void setRightClickVein(boolean rightClickVein) {
        this.rightClickVein = rightClickVein;
    }

    public boolean isUseWhitelist() {
        return useWhitelist;
    }

    public void setUseWhitelist(boolean useWhitelist) {
        this.useWhitelist = useWhitelist;
    }

    /** 当前模式对应的名单（白名单模式取白名单，黑名单模式取黑名单）。 */
    public Set<String> getFilterList() {
        return Collections.unmodifiableSet(activeList());
    }

    /** 覆盖当前模式对应的名单；另一份名单保持不变。 */
    public void setFilterList(Set<String> ids) {
        Set<String> target = activeList();
        target.clear();
        if (ids != null) {
            for (String id : ids) {
                if (id != null && !id.isBlank()) target.add(id.trim());
            }
        }
    }

    /** 当前模式生效的名单（内部可变引用，仅本类使用）。 */
    private Set<String> activeList() {
        return useWhitelist ? whitelist : blacklist;
    }

    public int getMaxBlocks() {
        return maxBlocks;
    }

    public void setMaxBlocks(int maxBlocks) {
        this.maxBlocks = clampInt(maxBlocks, USE_SERVER_LIMIT, MAX_BLOCKS_LIMIT);
    }

    public int getMaxRadius() {
        return maxRadius;
    }

    public void setMaxRadius(int maxRadius) {
        this.maxRadius = clampInt(maxRadius, USE_SERVER_LIMIT, MAX_RADIUS_LIMIT);
    }

    public boolean isDiagonal() {
        return diagonal;
    }

    public void setDiagonal(boolean diagonal) {
        this.diagonal = diagonal;
    }

    public boolean isRequireCorrectTool() {
        return requireCorrectTool;
    }

    public void setRequireCorrectTool(boolean requireCorrectTool) {
        this.requireCorrectTool = requireCorrectTool;
    }

    public boolean isRequireSneak() {
        return requireSneak;
    }

    public void setRequireSneak(boolean requireSneak) {
        this.requireSneak = requireSneak;
    }

    public boolean isProtectTool() {
        return protectTool;
    }

    public void setProtectTool(boolean protectTool) {
        this.protectTool = protectTool;
    }

    public boolean isCollectDrops() {
        return collectDrops;
    }

    public void setCollectDrops(boolean collectDrops) {
        this.collectDrops = collectDrops;
    }

    public boolean isShowPreview() {
        return showPreview;
    }

    public void setShowPreview(boolean showPreview) {
        this.showPreview = showPreview;
    }

    public double getExhaustionPerBlock() {
        return exhaustionPerBlock;
    }

    public void setExhaustionPerBlock(double v) {
        this.exhaustionPerBlock = clampDouble(v, 0.0D, MAX_EXHAUSTION);
    }

    public double getXpPerBlock() {
        return xpPerBlock;
    }

    public void setXpPerBlock(double v) {
        this.xpPerBlock = clampDouble(v, 0.0D, MAX_XP_PER_BLOCK);
    }

    // ======================== 工具方法 ========================

    /** 恢复默认值（默认值就是「跟服务端配置一致」的那套保守行为）。 */
    public void resetToDefault() {
        matchMode = MatchMode.AUTO;
        rightClickVein = true;
        useWhitelist = true;
        whitelist.clear();
        blacklist.clear();
        maxBlocks = USE_SERVER_LIMIT;
        maxRadius = USE_SERVER_LIMIT;
        diagonal = true;
        requireCorrectTool = true;
        requireSneak = false;
        protectTool = true;
        collectDrops = false;
        showPreview = true;
        exhaustionPerBlock = 0.005D;
        xpPerBlock = 0.0D;
    }

    public VeinMinerConfig copy() {
        VeinMinerConfig out = new VeinMinerConfig();
        out.matchMode = matchMode;
        out.rightClickVein = rightClickVein;
        out.useWhitelist = useWhitelist;
        out.whitelist.addAll(whitelist);
        out.blacklist.addAll(blacklist);
        out.maxBlocks = maxBlocks;
        out.maxRadius = maxRadius;
        out.diagonal = diagonal;
        out.requireCorrectTool = requireCorrectTool;
        out.requireSneak = requireSneak;
        out.protectTool = protectTool;
        out.collectDrops = collectDrops;
        out.showPreview = showPreview;
        out.exhaustionPerBlock = exhaustionPerBlock;
        out.xpPerBlock = xpPerBlock;
        return out;
    }

    /** 客户端提交过来的数据一律夹一遍范围（服务端唯一的信任边界）。 */
    public void sanitize() {
        matchMode = matchMode == null ? MatchMode.TAG_GROUP : matchMode;
        maxBlocks = clampInt(maxBlocks, USE_SERVER_LIMIT, MAX_BLOCKS_LIMIT);
        maxRadius = clampInt(maxRadius, USE_SERVER_LIMIT, MAX_RADIUS_LIMIT);
        exhaustionPerBlock = clampDouble(exhaustionPerBlock, 0.0D, MAX_EXHAUSTION);
        xpPerBlock = clampDouble(xpPerBlock, 0.0D, MAX_XP_PER_BLOCK);
        // 名单最多保留 256 条，避免客户端塞爆存档（黑白名单各自计）
        trimTo256(whitelist);
        trimTo256(blacklist);
    }

    private static void trimTo256(Set<String> list) {
        if (list.size() > 256) {
            List<String> keep = new ArrayList<>(list).subList(0, 256);
            list.clear();
            list.addAll(keep);
        }
    }

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.putString("matchMode", matchMode.name());
        tag.putBoolean("rightClickVein", rightClickVein);
        tag.putBoolean("useWhitelist", useWhitelist);
        tag.putInt("maxBlocks", maxBlocks);
        tag.putInt("maxRadius", maxRadius);
        tag.putBoolean("diagonal", diagonal);
        tag.putBoolean("requireCorrectTool", requireCorrectTool);
        tag.putBoolean("requireSneak", requireSneak);
        tag.putBoolean("protectTool", protectTool);
        tag.putBoolean("collectDrops", collectDrops);
        tag.putBoolean("showPreview", showPreview);
        tag.putDouble("exhaustionPerBlock", exhaustionPerBlock);
        tag.putDouble("xpPerBlock", xpPerBlock);
        if (!whitelist.isEmpty()) tag.put("filterWhitelist", toListTag(whitelist));
        if (!blacklist.isEmpty()) tag.put("filterBlacklist", toListTag(blacklist));
        return tag;
    }

    private static ListTag toListTag(Set<String> ids) {
        ListTag list = new ListTag();
        for (String id : ids) list.add(StringTag.valueOf(id));
        return list;
    }

    public static VeinMinerConfig deserialize(CompoundTag tag) {
        VeinMinerConfig cfg = new VeinMinerConfig();
        if (tag == null || tag.isEmpty()) return cfg;

        cfg.matchMode = MatchMode.byName(tag.contains("matchMode") ? tag.getString("matchMode") : null);
        cfg.rightClickVein = !tag.contains("rightClickVein") || tag.getBoolean("rightClickVein");
        cfg.useWhitelist = !tag.contains("useWhitelist") || tag.getBoolean("useWhitelist");
        cfg.maxBlocks = tag.contains("maxBlocks") ? tag.getInt("maxBlocks") : USE_SERVER_LIMIT;
        cfg.maxRadius = tag.contains("maxRadius") ? tag.getInt("maxRadius") : USE_SERVER_LIMIT;
        cfg.diagonal = !tag.contains("diagonal") || tag.getBoolean("diagonal");
        cfg.requireCorrectTool = !tag.contains("requireCorrectTool") || tag.getBoolean("requireCorrectTool");
        cfg.requireSneak = tag.contains("requireSneak") && tag.getBoolean("requireSneak");
        cfg.protectTool = !tag.contains("protectTool") || tag.getBoolean("protectTool");
        cfg.collectDrops = tag.contains("collectDrops") && tag.getBoolean("collectDrops");
        cfg.showPreview = !tag.contains("showPreview") || tag.getBoolean("showPreview");
        cfg.exhaustionPerBlock = tag.contains("exhaustionPerBlock") ? tag.getDouble("exhaustionPerBlock") : 0.005D;
        cfg.xpPerBlock = tag.contains("xpPerBlock") ? tag.getDouble("xpPerBlock") : 0.0D;

        readInto(tag, "filterWhitelist", cfg.whitelist);
        readInto(tag, "filterBlacklist", cfg.blacklist);
        // 兼容旧存档：只有单份 filterList 时，按当前模式归入对应名单
        if (!tag.contains("filterWhitelist") && !tag.contains("filterBlacklist")) {
            readInto(tag, "filterList", cfg.activeList());
        }
        cfg.sanitize();
        return cfg;
    }

    private static void readInto(CompoundTag tag, String key, Set<String> target) {
        if (!tag.contains(key)) return;
        ListTag list = tag.getList(key, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            String id = list.getString(i);
            if (!id.isBlank()) target.add(id);
        }
    }

    private static int clampInt(int v, int min, int max) {
        if (v < min) return min;
        return Math.min(v, max);
    }

    private static double clampDouble(double v, double min, double max) {
        if (v < min) return min;
        return Math.min(v, max);
    }
}
