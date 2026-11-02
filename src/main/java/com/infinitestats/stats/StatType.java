package com.infinitestats.stats;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Supplier;

/**
 * 属性类型 - 使用更优雅的设计模式
 * 支持无限等级属性、开关型属性以及动态发现其他模组的属性
 * 使用函数式接口实现属性效果计算
 */
public final class StatType {

    // ========== 核心数据结构 ==========

    private final String id;
    private final String translationKey;
    private final StatCategory category;
    private final StatBehavior behavior;
    private final StatValueCalculator calculator;
    private final Supplier<Attribute> attributeSupplier;
    private final String attributeName; // 储存 attribute 注册名，用于排重
    /** 镜像写入的额外属性注册名（例如 Connector 环境下 Fabric 侧的等价属性） */
    private final List<String> mirrorAttributeNames;
    private final List<Supplier<Attribute>> mirrorAttributeSuppliers;
    /** 当前生效的每点数值：默认取 {@link #defaultPerPointValue}，可被配置文件覆盖。 */
    private float perPointValue;
    /** 当前生效的点数上限：默认取 {@link #defaultMaxLevel}，可被配置文件覆盖。 */
    private int maxLevel;
    /** 出厂默认值（配置缺失 / 未加载时的兜底）。 */
    private final float defaultPerPointValue;
    private final int defaultMaxLevel;
    private final String description;
    private final boolean hidden;
    /** 覆盖配置里手写的显示名（外部属性专用）；为 null 时继续用翻译键。 */
    @Nullable
    private final String customDisplayName;

    /**
     * 属性行为类型
     */
    public enum StatBehavior {
        /** 无限叠加型 - 每点增加固定值 */
        SCALING,
        /** 开关型 - 0或1，达到1即激活 */
        TOGGLE,
        /** 百分比型 - 每点增加百分比，无上限 */
        PERCENTAGE
    }

    /**
     * 属性值计算器
     */
    @FunctionalInterface
    public interface StatValueCalculator {
        /**
         * 计算属性值
         * @param points 已分配点数
         * @param perPointValue 每点增加值
         * @return 最终属性值
         */
        float calculate(long points, float perPointValue);
    }

    // ========== 默认计算器 ==========

    public static final StatValueCalculator SCALING_CALCULATOR = (points, value) -> points * value;
    public static final StatValueCalculator TOGGLE_CALCULATOR = (points, value) -> points >= 1 ? 1 : 0;
    public static final StatValueCalculator PERCENTAGE_CALCULATOR = (points, value) -> points * value;

    // ========== 构造方法（Builder模式） ==========

    private StatType(Builder builder) {
        this.id = builder.id;
        this.translationKey = builder.translationKey;
        this.category = builder.category;
        this.behavior = builder.behavior;
        this.attributeSupplier = builder.attributeSupplier;
        this.attributeName = builder.attributeName;
        this.mirrorAttributeNames = List.copyOf(builder.mirrorAttributeNames);
        this.mirrorAttributeSuppliers = List.copyOf(builder.mirrorAttributeSuppliers);
        this.perPointValue = builder.perPointValue;
        this.maxLevel = builder.maxLevel;
        this.defaultPerPointValue = builder.perPointValue;
        this.defaultMaxLevel = builder.maxLevel;
        // 开关型属性的激活阈值会被配置改写，所以计算器必须读实例字段而不是构造期的快照。
        this.calculator = builder.behavior == StatBehavior.TOGGLE
                ? (points, value) -> points >= this.maxLevel ? 1 : 0
                : builder.calculator;
        this.description = builder.description;
        this.hidden = builder.hidden;
        this.customDisplayName = builder.customDisplayName;
    }

    // ========== Builder ==========

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static class Builder {
        private final String id;
        private String translationKey;
        private StatCategory category = StatCategory.UTILITY;
        private StatBehavior behavior = StatBehavior.SCALING;
        private StatValueCalculator calculator = SCALING_CALCULATOR;
        private Supplier<Attribute> attributeSupplier = null;
        private String attributeName = null;
        private final List<String> mirrorAttributeNames = new ArrayList<>();
        private final List<Supplier<Attribute>> mirrorAttributeSuppliers = new ArrayList<>();
        private float perPointValue = 1.0f;
        private int maxLevel = Integer.MAX_VALUE;
        private String description = "";
        private boolean hidden = false;
        private String customDisplayName = null;

        public Builder(String id) {
            this.id = id;
            this.translationKey = "stat.infinitestats." + id;
        }

        public Builder translationKey(String key) {
            this.translationKey = key;
            return this;
        }

        public Builder category(StatCategory category) {
            this.category = category;
            return this;
        }

        public Builder behavior(StatBehavior behavior) {
            this.behavior = behavior;
            this.calculator = switch (behavior) {
                case SCALING -> SCALING_CALCULATOR;
                case TOGGLE -> TOGGLE_CALCULATOR;
                case PERCENTAGE -> PERCENTAGE_CALCULATOR;
            };
            return this;
        }

        public Builder calculator(StatValueCalculator calculator) {
            this.calculator = calculator;
            return this;
        }

        /**
         * 绑定属性
         * @param attributeName 主属性注册名（用于排重与界面显示）
         * @param mirrors 需要同时写入的等价属性注册名（例如 reach-entity-attributes:reach），不存在时自动跳过
         */
        public Builder attribute(String attributeName, String... mirrors) {
            this.attributeName = attributeName;
            this.attributeSupplier = attributeSupplier(attributeName);
            for (String mirror : mirrors) {
                if (mirror == null || mirror.equals(attributeName)) continue;
                this.mirrorAttributeNames.add(mirror);
                this.mirrorAttributeSuppliers.add(attributeSupplier(mirror));
            }
            return this;
        }

        private static Supplier<Attribute> attributeSupplier(String name) {
            return () -> ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation(name));
        }

        public Builder perPointValue(float value) {
            this.perPointValue = value;
            return this;
        }

        public Builder maxLevel(int max) {
            this.maxLevel = max;
            return this;
        }

        public Builder description(String desc) {
            this.description = desc;
            return this;
        }

        public Builder hidden() {
            this.hidden = true;
            return this;
        }

        /**
         * 自定义显示名（外部属性专用）：覆盖「注册名 / 翻译键」那条路径。
         * 留空时仍走 {@code getDescriptionId()} 的翻译。
         */
        public Builder displayName(String name) {
            this.customDisplayName = (name == null || name.isBlank()) ? null : name;
            return this;
        }

        public Builder toggle() {
            return behavior(StatBehavior.TOGGLE).maxLevel(1).perPointValue(0);
        }

        public Builder percentage() {
            return behavior(StatBehavior.PERCENTAGE);
        }

        public StatType build() {
            // 开关型的激活阈值在 StatType 构造器里绑定到实例字段，便于配置改写。
            return new StatType(this);
        }
    }

    // ========== 动态属性注册系统 ==========

    /** 内置属性列表（不可变） */
    private static final StatType[] BUILTIN_STATS = registerAllStats();

    /** 全部属性列表（内置 + 动态发现），通过 ALL_STATS 访问 */
    private static final List<StatType> ALL_STATS_LIST = new ArrayList<>();
    /** ID → StatType 快速查找 */
    private static final Map<String, StatType> STAT_LOOKUP = new HashMap<>();

    /** 已覆盖的 attribute 注册名（用于排重） */
    private static final Set<String> COVERED_ATTRIBUTES = new HashSet<>();

    /** 是否已经完成动态发现 */
    private static boolean discoveryDone = false;

    static {
        // 先加载内置属性
        for (StatType stat : BUILTIN_STATS) {
            registerStat(stat);
            if (stat.attributeName != null) {
                COVERED_ATTRIBUTES.add(stat.attributeName);
            }
            // 镜像属性同样视为已覆盖，避免又被动态发现成一条独立的外部属性
            COVERED_ATTRIBUTES.addAll(stat.getMirrorAttributeNames());
        }
    }

    /**
     * 所有属性数组（向后兼容，动态更新）
     * 初始值为内置属性，在 discoverModdedAttributes() 后更新为全部属性
     */
    public static StatType[] ALL_STATS = BUILTIN_STATS;

    /**
     * 注册单个属性到全局列表
     */
    private static void registerStat(StatType stat) {
        ALL_STATS_LIST.add(stat);
        STAT_LOOKUP.put(stat.getId(), stat);
    }

    /** 玩家根本不消费的生物专属属性：不收录，免得白占面板。 */
    private static final Set<String> EXCLUDED_ATTRIBUTES = Set.of(
            "minecraft:generic.follow_range",
            "minecraft:generic.flying_speed");

    /**
     * 动态发现其他模组注册的属性
     * 应在 FMLCommonSetup 中调用，此时 ForgeRegistries 已完成填充
     */
    public static void discoverModdedAttributes() {
        if (discoveryDone) return;
        discoveryDone = true;

        // 覆盖配置先读：发现阶段要用它决定排除谁、改写谁的数值
        ExternalAttributeConfig.reload();
        int added = discoverExternalInternal();

        // 重建 ALL_STATS
        ALL_STATS = ALL_STATS_LIST.toArray(new StatType[0]);
        logExternalSummary(added);
    }

    /**
     * 热重扫外部属性：清掉现有外部条目后按「当前注册表 + 当前配置文件」重新生成。
     * <p>
     * 内置属性、玩家等级与已投入的点数都不动 —— 玩家数据按 statId 存，重扫后 id 不变，
     * 点数继续生效。被黑名单排除的属性只是从列表里消失，NBT 里的点数仍保留，
     * 之后从黑名单里移掉就会恢复。
     *
     * @return 重扫后收录的外部属性总数
     */
    public static synchronized int reloadExternalAttributes() {
        ALL_STATS_LIST.removeIf(stat -> stat.getCategory() == StatCategory.EXTERNAL);
        rebuildLookups();
        ExternalAttributeConfig.reload();
        int total = discoverExternalInternal();
        ALL_STATS = ALL_STATS_LIST.toArray(new StatType[0]);
        logExternalSummary(0);
        return total;
    }

    /** 重建 ID 查找表与「已覆盖属性」集合（外部条目增删后必须调一次）。 */
    private static void rebuildLookups() {
        STAT_LOOKUP.clear();
        COVERED_ATTRIBUTES.clear();
        for (StatType stat : ALL_STATS_LIST) {
            STAT_LOOKUP.put(stat.getId(), stat);
            if (stat.getAttributeName() != null) {
                COVERED_ATTRIBUTES.add(stat.getAttributeName());
            }
            COVERED_ATTRIBUTES.addAll(stat.getMirrorAttributeNames());
        }
    }

    /**
     * 扫描属性注册表，把尚未覆盖且未被配置排除的属性注册成外部属性。
     *
     * @return 本次新增条数
     */
    private static int discoverExternalInternal() {
        int added = 0;
        for (Map.Entry<net.minecraft.resources.ResourceKey<Attribute>, Attribute> entry
                : ForgeRegistries.ATTRIBUTES.getEntries()) {
            ResourceLocation rl = entry.getKey().location();
            String attrName = rl.toString();

            if (EXCLUDED_ATTRIBUTES.contains(attrName)) continue;
            // 跳过已覆盖的属性（内置属性自己占用的注册名 + 镜像属性）
            if (COVERED_ATTRIBUTES.contains(attrName)) continue;
            if (!ExternalAttributeConfig.isAllowed(attrName)) continue;

            // 生成唯一的 stat ID
            String statId = "attr." + rl.getNamespace() + "." + rl.getPath();
            if (STAT_LOOKUP.containsKey(statId)) continue;

            StatType newStat = createExternalStat(rl, entry.getValue());
            registerStat(newStat);
            COVERED_ATTRIBUTES.add(attrName);
            added++;
        }
        return added;
    }

    /**
     * 按「自动推导 + 覆盖配置」造一条外部属性。
     * <p>
     * 数值来源优先级：覆盖配置里手写的值 &gt; 属性自身取值范围推导 &gt; 注册名关键字经验值。
     */
    private static StatType createExternalStat(ResourceLocation rl, Attribute attribute) {
        String attrName = rl.toString();
        String statId = "attr." + rl.getNamespace() + "." + rl.getPath();
        ExternalAttributeConfig.Override override = ExternalAttributeConfig.get(attrName);

        float perPoint = (override != null && override.getPerPoint() != null)
                ? override.getPerPoint().floatValue()
                : inferPerPoint(attribute, rl);
        int maxPoints = (override != null && override.getMaxPoints() != null)
                ? override.getMaxPoints()
                : inferMaxPoints(attribute, perPoint);

        // 翻译键直接复用属性自带的 getDescriptionId()：模组的语言文件里通常已经
        // 有这个键的翻译（游戏内显示属性名用的就是它），因此外部属性无需再手工补译文。
        // 仅在该键缺失（模组自己都没提供翻译）时回退到本模组的自建键。
        String descId = attribute.getDescriptionId();
        String translationKey = (descId != null && !descId.isBlank())
                ? descId
                : "stat.infinitestats." + statId;

        Builder builder = builder(statId)
                .translationKey(translationKey)
                .category(StatCategory.EXTERNAL)
                .attribute(attrName)
                .perPointValue(perPoint)
                .maxLevel(maxPoints)
                .description("");

        if (override != null) {
            if (override.getDisplayName() != null) builder.displayName(override.getDisplayName());
            if (override.getDescription() != null) builder.description(override.getDescription());
            if (override.isHidden()) builder.hidden();
        }
        return builder.build();
    }

    /** 打印外部属性总览；{@code added > 0} 时顺手导出缺译文的清单。 */
    private static void logExternalSummary(int added) {
        int external = 0, translated = 0;
        for (StatType stat : ALL_STATS_LIST) {
            if (stat.getCategory() != StatCategory.EXTERNAL) continue;
            external++;
            String key = stat.getTranslationKey();
            if (!net.minecraft.network.chat.Component.translatable(key).getString().equals(key)) translated++;
        }
        System.out.println("[InfiniteStats] External attributes: " + external
                + " (" + translated + " auto-translated from their own mods"
                + (added > 0 ? ", " + added + " newly discovered" : "") + ")");

        // 生成翻译模板文件（仅首次发现时）
        if (added > 0) {
            generateTranslationTemplate();
        }
    }

    /**
     * 一条可序列化的外部属性快照（服务端 → 客户端同步用）。
     * <p>
     * 客户端必须和服务端看到<b>同一份列表</b>：否则面板上会多出服务端已经排除的属性
     * （点下去没有效果），或者少掉服务端刚热重扫出来的属性。因此登录与
     * {@code /infstats reload external} 之后都由服务端下发这份快照覆盖客户端自己的发现结果。
     */
    public static final class ExternalSpec {
        private final String attributeName;
        private final String translationKey;
        @Nullable
        private final String displayName;
        @Nullable
        private final String description;
        private final float perPoint;
        private final int maxPoints;
        private final boolean hidden;

        public ExternalSpec(String attributeName, String translationKey, @Nullable String displayName,
                            @Nullable String description, float perPoint, int maxPoints, boolean hidden) {
            this.attributeName = attributeName;
            this.translationKey = translationKey;
            this.displayName = displayName;
            this.description = description;
            this.perPoint = perPoint;
            this.maxPoints = maxPoints;
            this.hidden = hidden;
        }

        public String getAttributeName() {
            return attributeName;
        }

        public String getTranslationKey() {
            return translationKey;
        }

        @Nullable
        public String getDisplayName() {
            return displayName;
        }

        @Nullable
        public String getDescription() {
            return description;
        }

        public float getPerPoint() {
            return perPoint;
        }

        public int getMaxPoints() {
            return maxPoints;
        }

        public boolean isHidden() {
            return hidden;
        }
    }

    /** 服务端当前的全部外部属性（供网络下发）。 */
    public static List<ExternalSpec> externalSnapshot() {
        List<ExternalSpec> list = new ArrayList<>();
        for (StatType stat : ALL_STATS_LIST) {
            if (stat.getCategory() != StatCategory.EXTERNAL || stat.getAttributeName() == null) continue;
            list.add(new ExternalSpec(stat.getAttributeName(), stat.getTranslationKey(),
                    stat.getCustomDisplayName(), stat.getDescription(),
                    stat.getPerPointValue(), stat.getMaxLevel(), stat.isHidden()));
        }
        return list;
    }

    /**
     * 客户端按服务端下发的快照重建外部属性列表。
     * <p>
     * 属性在本客户端没注册（两边模组不一致）或 id 冲突的条目会被跳过 —— 面板宁可少一条，
     * 也不能出现「点了没反应」的条目。
     */
    public static synchronized void applyExternalSnapshot(List<ExternalSpec> specs) {
        ALL_STATS_LIST.removeIf(stat -> stat.getCategory() == StatCategory.EXTERNAL);
        rebuildLookups();

        for (ExternalSpec spec : specs) {
            ResourceLocation rl = ResourceLocation.tryParse(spec.getAttributeName());
            if (rl == null || ForgeRegistries.ATTRIBUTES.getValue(rl) == null) continue;

            String statId = "attr." + rl.getNamespace() + "." + rl.getPath();
            if (STAT_LOOKUP.containsKey(statId)) continue;

            Builder builder = builder(statId)
                    .translationKey(spec.getTranslationKey() != null
                            ? spec.getTranslationKey()
                            : "stat.infinitestats." + statId)
                    .category(StatCategory.EXTERNAL)
                    .attribute(spec.getAttributeName())
                    .perPointValue(spec.getPerPoint())
                    .maxLevel(Math.max(1, spec.getMaxPoints()))
                    .description(spec.getDescription() != null ? spec.getDescription() : "");

            if (spec.getDisplayName() != null) builder.displayName(spec.getDisplayName());
            if (spec.isHidden()) builder.hidden();

            StatType stat = builder.build();
            registerStat(stat);
            COVERED_ATTRIBUTES.add(spec.getAttributeName());
        }

        ALL_STATS = ALL_STATS_LIST.toArray(new StatType[0]);
    }

    /**
     * 生成「待补翻译」清单到 config 目录。
     * <p>
     * 外部属性的显示名已自动复用属性自带的 {@code getDescriptionId()}，绝大多数情况下
     * 直接使用其所属模组自带的译文，无需人工干预。这里只列出那些<b>连所属模组都没有
     * 提供译文</b>的属性，便于按需补充。
     */
    private static void generateTranslationTemplate() {
        try {
            Map<String, String> keys = new java.util.LinkedHashMap<>();
            for (StatType stat : ALL_STATS_LIST) {
                if (stat.getCategory() != StatCategory.EXTERNAL) continue;
                String displayKey = stat.getTranslationKey();
                // 显示名已有译文 → 不需要补
                if (!net.minecraft.network.chat.Component.translatable(displayKey).getString().equals(displayKey)) {
                    continue;
                }
                keys.put(displayKey, stat.getAttributeName() != null ? "[" + stat.getAttributeName() + "]" : "");
            }

            if (keys.isEmpty()) {
                System.out.println("[InfiniteStats] Every external attribute already has its own "
                        + "translation; nothing to fill in.");
                return;
            }

            java.nio.file.Path configDir = java.nio.file.Path.of("config", "infinitestats");
            java.nio.file.Files.createDirectories(configDir);
            java.nio.file.Path templatePath = configDir.resolve("external_translations.json");

            // 已存在就原样保留。这份清单的用途是「提示还有哪些外部属性没译文」，
            // 而整合包作者往往会在里面手填补充译文 —— 直接覆盖会把人工内容冲掉。
            if (java.nio.file.Files.exists(templatePath)) {
                System.out.println("[InfiniteStats] " + keys.size()
                        + " external attribute(s) lack a built-in translation; "
                        + templatePath + " already exists and was left untouched.");
                return;
            }

            StringBuilder json = new StringBuilder();
            json.append("{\n");
            boolean first = true;
            for (Map.Entry<String, String> e : keys.entrySet()) {
                if (!first) json.append(",\n");
                first = false;
                json.append("  \"").append(e.getKey()).append("\": \"").append(e.getValue()).append("\"");
            }
            json.append("\n}\n");

            java.nio.file.Files.writeString(templatePath, json.toString());
            System.out.println("[InfiniteStats] " + keys.size()
                    + " external attribute(s) still lack a translation; the list was written to "
                    + "config/infinitestats/external_translations.json");
        } catch (Exception e) {
            System.err.println("[InfiniteStats] Failed to generate translation template: " + e.getMessage());
        }
    }

    /**
     * 自动推导「每点数值」。
     * <p>
     * 两步走：
     * <ol>
     *   <li>先用注册名关键字拿一个<b>经验值</b>（内置属性多年调出来的量级：
     *       伤害 0.05 / 生命 2.0 / 速度 0.001 …）；</li>
     *   <li>再用属性<b>自身的取值范围</b>纠正 —— 关键字是按「宽范围属性」调的，
     *       照搬到 {@code 0~1} 的抗性 / 减伤 / 概率上，十几个点就顶满，等于白送。</li>
     * </ol>
     * 属性没有 {@code RangedAttribute} 的上下限（或无界）时只能信关键字；
     * 关键字也没命中时才退回 1.0（与旧版行为一致）。
     */
    private static float inferPerPoint(Attribute attribute, ResourceLocation rl) {
        float hint = keywordPerPoint(rl.getPath().toLowerCase(Locale.ROOT));
        double[] range = attributeRange(attribute);
        if (range == null) {
            return Float.isNaN(hint) ? 1.0f : hint;
        }

        double span = range[1] - range[0];
        // 窄范围：按「1000 点走完全程」的量级定值（0~1 → 0.001）
        if (span <= 4.0D) {
            float byRange = niceFloor(span / 1000.0D);
            if (Float.isNaN(hint)) return byRange;
            // 关键字值 20 点内就能顶满 → 明显是宽范围的经验值，改用范围推导
            return hint > span / 20.0D ? byRange : hint;
        }

        // 宽范围：关键字命中且不会 20 点内顶满，就用经验值
        if (!Float.isNaN(hint) && hint <= span / 20.0D) return hint;

        // 无先验 / 先验过粗：按「500 点吃下四分之一范围」起步，并钳在合理量级
        float byRange = niceFloor(span / 2000.0D);
        return Math.min(2.0f, Math.max(0.0001f, byRange));
    }

    /**
     * 自动推导「可投入点数上限」。
     * <p>
     * 属性最终值会被原版 {@code RangedAttribute} 钳在 {@code [min, max]}，投到超过容量的
     * 点数纯属浪费 —— 这里按「从默认值加到上下限需要多少点」定上限，刚好打满即停。
     * 无界属性继续返回 {@link Integer#MAX_VALUE}（保持无限加点）。
     */
    private static int inferMaxPoints(Attribute attribute, float perPoint) {
        double[] range = attributeRange(attribute);
        if (range == null || perPoint <= 0) return Integer.MAX_VALUE;

        double base = attribute.getDefaultValue();
        double capacity = 0;
        if (range[1] > base) capacity = Math.max(capacity, (range[1] - base) / perPoint);
        if (range[0] < base) capacity = Math.max(capacity, (base - range[0]) / perPoint);
        if (!Double.isFinite(capacity) || capacity <= 0) return Integer.MAX_VALUE;

        // 1e6 只是 int 溢出保护，正常属性远远到不了
        double capped = Math.min(capacity, 1_000_000.0D);
        return (int) Math.max(20.0D, Math.ceil(capped));
    }

    /**
     * 取属性的有效取值范围；不是 {@code RangedAttribute}、上下限无效或范围大到等同无界时返回 null。
     */
    @Nullable
    private static double[] attributeRange(Attribute attribute) {
        if (!(attribute instanceof RangedAttribute ranged)) return null;
        double min = ranged.getMinValue();
        double max = ranged.getMaxValue();
        if (!Double.isFinite(min) || !Double.isFinite(max) || max <= min) return null;
        if (max - min > 1.0e7D) return null;
        return new double[] {min, max};
    }

    /**
     * 向下取整到 1/2/5 × 10^n 的「顺眼数值」，避免导出 0.001333 这种读不懂的每点值。
     */
    private static float niceFloor(double value) {
        if (!(value > 0.0D) || !Double.isFinite(value)) return 0.0001f;
        double exponent = Math.floor(Math.log10(value));
        double base = Math.pow(10.0D, exponent);
        double mantissa = value / base;
        double nice = mantissa >= 5.0D ? 5.0D : mantissa >= 2.0D ? 2.0D : 1.0D;
        double result = nice * base;
        // 浮点误差下别把 0.005 变成 0.0049999998
        return (float) (Math.round(result * 1.0e6D) / 1.0e6D);
    }

    /**
     * 注册名关键字先验（内置属性的经验值）。返回 {@link Float#NaN} 表示没命中任何规则。
     * <p>
     * 顺序有讲究：必须从具体词走到泛化词 —— {@code attack_speed} 要走速度分支（0.001），
     * 不能因为带 {@code attack} 就按伤害（0.05）算。
     */
    private static float keywordPerPoint(String path) {
        if (path.contains("speed") || path.contains("movement") || path.contains("velocity")) return 0.001f;
        if (path.contains("regen") || path.contains("heal")) return 0.02f;
        if (path.contains("resist") || path.contains("reduction")) return 0.01f;
        if (path.contains("absorption")) return 0.01f;
        if (path.contains("damage") || path.contains("attack")) return 0.05f;
        if (path.contains("health") || path.contains("life")) return 2.0f;
        if (path.contains("armor") || path.contains("defence") || path.contains("defense")) return 0.5f;
        if (path.contains("toughness")) return 0.25f;
        if (path.contains("crit")) return 0.005f;
        if (path.contains("penetrat") || path.contains("pierc")) return 0.01f;
        if (path.contains("reach") || path.contains("range") || path.contains("distance")) return 0.5f;
        if (path.contains("luck")) return 0.1f;
        if (path.contains("step") || path.contains("jump")) return 0.5f;
        if (path.contains("fly")) return 0.002f;
        if (path.contains("gravity")) return 0.001f;
        if (path.contains("cooldown") || path.contains("charge")) return 0.005f;
        if (path.contains("energy") || path.contains("mana") || path.contains("soul")) return 5.0f;
        if (path.contains("knockback")) return 0.01f;
        if (path.contains("max")) return 2.0f;
        return Float.NaN;
    }

    // ========== 属性注册 ==========

    private static StatType[] registerAllStats() {
        return new StatType[] {
            create("attack_damage").category(StatCategory.ATTACK)
                .attribute("minecraft:generic.attack_damage")
                .perPointValue(0.05f)
                .build(),

            create("attack_speed").category(StatCategory.ATTACK)
                .attribute("minecraft:generic.attack_speed")
                .perPointValue(0.005f)
                .build(),

            create("crit_chance").category(StatCategory.ATTACK)
                .percentage()
                .perPointValue(0.005f)
                .build(),

            create("crit_damage").category(StatCategory.ATTACK)
                .percentage()
                .perPointValue(0.02f)
                .build(),

            create("armor_penetration").category(StatCategory.ATTACK)
                .percentage()
                .perPointValue(0.01f)
                .build(),

            create("knockback_power").category(StatCategory.ATTACK)
                .attribute("minecraft:generic.attack_knockback")
                .perPointValue(0.02f)
                .build(),

            create("projectile_damage").category(StatCategory.ATTACK)
                .percentage()
                .perPointValue(0.03f)
                .build(),

            create("life_steal").category(StatCategory.ATTACK)
                .percentage()
                .perPointValue(0.01f)
                .build(),

            create("life_steal_aoe").category(StatCategory.ATTACK)
                .percentage()
                .perPointValue(0.01f)
                .build(),

            create("damage_reflection").category(StatCategory.ATTACK)
                .percentage()
                .perPointValue(0.01f)
                .build(),

            create("execute").category(StatCategory.ATTACK)
                .percentage()
                .perPointValue(0.02f)
                .build(),

            create("true_damage").category(StatCategory.ATTACK)
                .percentage()
                .perPointValue(0.01f)
                .description("每次攻击附加基于伤害的额外真实伤害：无视护甲 / 抗性 / 保护附魔 / 吸收护盾与其它模组的限伤、免伤、复活；"
                        + "本模组自己的闪避 / 格挡 / 免疫 / 无敌仍可挡下")
                .build(),

            create("reduce_max_health").category(StatCategory.ATTACK)
                .perPointValue(1.0f)
                .description("每次攻击降低目标最大生命值（每点 -1 点，最低保留 1 点）")
                .build(),

            create("scope_attack").category(StatCategory.ATTACK)
                .perPointValue(0.3f)
                .description("攻击时波及周围敌人，对范围内敌人造成 50% 伤害（半径每点 +0.3 格）")
                .build(),

            create("repulsion").category(StatCategory.ATTACK)
                .perPointValue(0.5f)
                .description("持续推开周围的敌对生物与它们射出的箭矢等弹射物（半径每点 +0.5 格）")
                .build(),

            create("infinite_arrows").category(StatCategory.ATTACK)
                .toggle()
                .maxLevel(5)
                .description("投入5点解锁：背包中没有箭也能拉弓 / 弩蓄力并射出箭矢，箭不消耗背包")
                .build(),

            create("infinite_ammo").category(StatCategory.ATTACK)
                .toggle()
                .maxLevel(5)
                .description("投入5点解锁：手持 TACZ（永恒枪械工坊：零）/ 卓越前线（Superb Warfare）的枪械、"
                        + "或操作卓越前线的载具武器时弹匣恒满 —— 射击不扣子弹、也永远不用换弹"
                        + "（需要安装对应模组，两个都没装时该属性无效果）")
                .build(),

            create("no_heat").category(StatCategory.ATTACK)
                .toggle()
                .maxLevel(3)
                .description("投入3点解锁：手持 TACZ（永恒枪械工坊：零）/ 卓越前线（Superb Warfare）的枪械、"
                        + "或操作卓越前线的载具武器时热量恒为 0，带热量数据的枪既不会升温也不会过热锁死"
                        + "（需要安装对应模组，两个都没装时该属性无效果）")
                .build(),

            create("no_recoil").category(StatCategory.ATTACK)
                .toggle()
                .maxLevel(3)
                .description("投入3点解锁：TACZ（永恒枪械工坊：零）/ 卓越前线（Superb Warfare）的枪械开火时视角不再被后坐力往上顶，"
                        + "卓越前线的瞄准呼吸晃动与载具开火震屏也一并消除，弹着点自己控制"
                        + "（需要安装对应模组，两个都没装时该属性无效果）")
                .build(),

            create("multi_shot").category(StatCategory.ATTACK)
                .perPointValue(1.0f)
                .maxLevel(8)
                .description("每点让每次射击额外射出一支箭（等级＝额外箭数，最多 8 支）；额外箭矢小幅散开且不可拾取")
                .build(),

            create("extra_strike").category(StatCategory.ATTACK)
                .perPointValue(1.0f)
                .maxLevel(8)
                .description("每次造成伤害后追加额外打击（等级＝额外打击次数，最多 8 次）："
                        + "每次造成本次伤害 20% 的额外属性伤害，独立结算、无视护甲与减伤；"
                        + "近战 / 远程 / 法术以及你的召唤物造成的伤害都算")
                .build(),

            create("max_health").category(StatCategory.DEFENSE)
                .attribute("minecraft:generic.max_health")
                .perPointValue(2.0f)
                .build(),

            create("armor").category(StatCategory.DEFENSE)
                .attribute("minecraft:generic.armor")
                .perPointValue(0.5f)
                .build(),

            create("armor_toughness").category(StatCategory.DEFENSE)
                .attribute("minecraft:generic.armor_toughness")
                .perPointValue(0.25f)
                .build(),

            create("health_regen").category(StatCategory.DEFENSE)
                .perPointValue(0.05f)
                .build(),

            create("damage_reduction").category(StatCategory.DEFENSE)
                .percentage()
                .perPointValue(0.002f)
                .build(),

            create("knockback_resist").category(StatCategory.DEFENSE)
                .attribute("minecraft:generic.knockback_resistance")
                .perPointValue(0.01f)
                .build(),

            create("fall_resist").category(StatCategory.DEFENSE)
                .percentage()
                .perPointValue(0.01f)
                .build(),

            create("block_chance").category(StatCategory.DEFENSE)
                .percentage()
                .perPointValue(0.01f)
                .build(),

            create("dodge_chance").category(StatCategory.DEFENSE)
                .percentage()
                .perPointValue(0.008f)
                .build(),

            create("absorption_shield").category(StatCategory.DEFENSE)
                .perPointValue(1.0f)
                .build(),

            create("auto_revive").category(StatCategory.DEFENSE)
                .behavior(StatBehavior.TOGGLE).maxLevel(8).perPointValue(0)
                .build(),

            create("fire_immunity").category(StatCategory.DEFENSE)
                .behavior(StatBehavior.TOGGLE).maxLevel(3).perPointValue(0)
                .build(),

            create("projectile_immunity").category(StatCategory.DEFENSE)
                .behavior(StatBehavior.TOGGLE).maxLevel(5).perPointValue(0)
                .build(),

            create("explosion_immunity").category(StatCategory.DEFENSE)
                .behavior(StatBehavior.TOGGLE).maxLevel(5).perPointValue(0)
                .build(),

            create("suffocation_immunity").category(StatCategory.DEFENSE)
                .behavior(StatBehavior.TOGGLE).maxLevel(2).perPointValue(0)
                .build(),

            create("debuff_immunity").category(StatCategory.DEFENSE)
                .behavior(StatBehavior.TOGGLE).maxLevel(4).perPointValue(0)
                .build(),

            create("invincibility").category(StatCategory.DEFENSE)
                .behavior(StatBehavior.TOGGLE).maxLevel(1).perPointValue(0)
                .hidden()
                .build(),

            create("movement_speed").category(StatCategory.MOBILITY)
                .attribute("minecraft:generic.movement_speed")
                .perPointValue(0.001f)
                .build(),

            create("swim_speed").category(StatCategory.MOBILITY)
                .percentage()
                .perPointValue(0.003f)
                .build(),

            create("jump_height").category(StatCategory.MOBILITY)
                .percentage()
                .perPointValue(0.005f)
                .build(),

            create("multi_jump").category(StatCategory.MOBILITY)
                .perPointValue(1.0f)
                .maxLevel(10)
                .description("每点获得一次空中跳跃机会（等级＝可跳次数，最多 10 次）：起跳后松开再按跳跃键即可二段跳")
                .build(),

            create("step_height").category(StatCategory.MOBILITY)
                .percentage()
                .perPointValue(0.006f)
                .build(),

            create("auto_step").category(StatCategory.MOBILITY)
                .behavior(StatBehavior.TOGGLE).maxLevel(2).perPointValue(0)
                .build(),

            create("fly_speed").category(StatCategory.MOBILITY)
                .percentage()
                .perPointValue(0.002f)
                .build(),

            create("fly").category(StatCategory.MOBILITY)
                .behavior(StatBehavior.TOGGLE).maxLevel(8).perPointValue(0)
                .build(),

            create("climb_speed").category(StatCategory.MOBILITY)
                .percentage()
                .perPointValue(0.2f)
                .description("爬梯子 / 藤蔓 / 脚手架时的上升速度 +20% / 点")
                .build(),

            create("no_fall_damage").category(StatCategory.MOBILITY)
                .behavior(StatBehavior.TOGGLE).maxLevel(2).perPointValue(0)
                .build(),

            create("luck").category(StatCategory.UTILITY)
                .attribute("minecraft:generic.luck")
                .perPointValue(0.1f)
                .build(),

            create("mining_speed").category(StatCategory.UTILITY)
                .percentage()
                .perPointValue(0.01f)
                .build(),

            create("mining_level").category(StatCategory.UTILITY)
                .perPointValue(1.0f)
                .build(),

            // 方块交互距离：同时写入 Forge 与 Fabric(REA) 两套属性。
            // reach-entity-attributes 由 Connector 系整合包（ConnectorExtras 的 jarJar）提供，
            // 它的 mixin 会接管原版交互距离，只写 forge:block_reach 会完全失效。
            create("reach").category(StatCategory.UTILITY)
                .attribute("forge:block_reach", "reach-entity-attributes:reach")
                .perPointValue(0.04f)
                .build(),

            // 实体攻击距离：同理，Fabric 侧对应 reach-entity-attributes:attack_range
            create("entity_reach").category(StatCategory.UTILITY)
                .attribute("forge:entity_reach", "reach-entity-attributes:attack_range")
                .perPointValue(0.04f)
                .build(),

            create("xp_gain").category(StatCategory.UTILITY)
                .percentage()
                .perPointValue(0.02f)
                .build(),

            create("loot_luck").category(StatCategory.UTILITY)
                .percentage()
                .perPointValue(0.01f)
                .build(),

            create("double_loot").category(StatCategory.UTILITY)
                .percentage()
                .perPointValue(0.005f)
                .build(),

            create("item_magnet").category(StatCategory.UTILITY)
                .behavior(StatBehavior.TOGGLE).maxLevel(2).perPointValue(0)
                .description("立即收集范围内的掉落物，范围由配置 magnetRange 决定（默认 10 格）")
                .build(),

            create("xp_magnet").category(StatCategory.UTILITY)
                .behavior(StatBehavior.TOGGLE).maxLevel(2).perPointValue(0)
                .build(),

            create("auto_deposit").category(StatCategory.UTILITY)
                .behavior(StatBehavior.TOGGLE).maxLevel(3).perPointValue(0)
                .description("投入3点解锁：自动把背包物品存入存储网络（RS / AE2 / 汤姆存储 / 背包等），存入优先级可在属性面板调整")
                .build(),

            create("vein_miner").category(StatCategory.UTILITY)
                .behavior(StatBehavior.TOGGLE).maxLevel(3).perPointValue(0)
                .build(),

            create("ore_vision").category(StatCategory.UTILITY)
                .behavior(StatBehavior.TOGGLE).maxLevel(3).perPointValue(0)
                .build(),

            create("auto_smelt").category(StatCategory.UTILITY)
                .behavior(StatBehavior.TOGGLE).maxLevel(2).perPointValue(0)
                .build(),

            create("auto_fish").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(3)
                .description("投入3点解锁：主手持钓鱼竿并看向水面时自动抛竿，鱼一咬钩就自动收杆并重新抛出（重抛间隔可在配置里调）")
                .build(),

            create("instant_bite").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(3)
                .description("投入3点解锁：鱼几乎立刻咬钩（跳过原版 5~30 秒的等待），手动钓鱼也生效；配合自动钓鱼即高速全自动")
                .build(),

            create("breed_no_cooldown").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(3)
                .description("投入3点解锁：自己喂食繁殖出的动物不再进入冷却，可以立刻再次繁殖")
                .build(),

            create("instant_grow").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(2)
                .description("投入2点解锁：手持该动物的饲料右键幼年动物，一次喂食即可长大（原版约需 10 次）")
                .build(),

            create("crafting_bonus").category(StatCategory.UTILITY)
                .percentage()
                .perPointValue(0.005f)
                .build(),

            create("auto_repair").category(StatCategory.UTILITY)
                .behavior(StatBehavior.TOGGLE).maxLevel(2).perPointValue(0)
                .build(),

            create("repair_amount").category(StatCategory.UTILITY)
                .perPointValue(1.0f)
                .build(),

            create("use_speed").category(StatCategory.UTILITY)
                .percentage()
                .perPointValue(0.02f)
                .build(),

            create("bow_draw_speed").category(StatCategory.UTILITY)
                .percentage()
                .perPointValue(0.03f)
                .build(),

            create("cooldown_reduction").category(StatCategory.UTILITY)
                .percentage()
                .perPointValue(0.005f)
                .description("减少物品冷却时间（每点 -0.5%，最高 -100%）")
                .build(),

            create("projectile_tracking").category(StatCategory.UTILITY)
                .behavior(StatBehavior.TOGGLE).maxLevel(5).perPointValue(0)
                .description("解锁后你发射的弹射物（箭矢、雪球、三叉戟等）会自动转向追踪敌人，优先追踪视野内的目标，被方块挡住的不会追。仅在配置半径内（默认64格，可在配置中调整）生效。")
                .build(),

            create("no_invincibility_frames").category(StatCategory.UTILITY)
                .behavior(StatBehavior.TOGGLE).maxLevel(5).perPointValue(0)
                .build(),

            create("night_vision").category(StatCategory.UTILITY)
                .behavior(StatBehavior.TOGGLE).maxLevel(2).perPointValue(0)
                .build(),

            create("water_breathing").category(StatCategory.UTILITY)
                .behavior(StatBehavior.TOGGLE).maxLevel(2).perPointValue(0)
                .build(),

            create("no_hunger").category(StatCategory.UTILITY)
                .behavior(StatBehavior.TOGGLE).maxLevel(3).perPointValue(0)
                .build(),

            create("always_eat").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(3)
                .description("投入3点解锁：饱食度满时也能继续吃普通食物（原版满饱食度只有金苹果一类能吃，"
                        + "其余食物右键只会失败）；金苹果这类原版就允许满饱食度进食的不受影响。"
                        + "可用配置 AlwaysEat 分组里的限制 / 例外名单限定哪些食物适用")
                .build(),

            create("invisibility").category(StatCategory.UTILITY)
                .behavior(StatBehavior.TOGGLE).maxLevel(3).perPointValue(0)
                .build(),

            create("keep_inventory").category(StatCategory.UTILITY)
                .behavior(StatBehavior.TOGGLE).maxLevel(5).perPointValue(0)
                .description("投入5点解锁：死亡时保留背包、装备栏与饰品栏物品（即使未开启游戏规则 keepInventory 也不掉落）")
                .build(),

            create("keep_xp").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(3)
                .description("投入3点解锁：死亡不掉经验等级与经验条（配合「死亡不掉落」可完整保命）")
                .build(),

            create("time_accel").category(StatCategory.UTILITY)
                .perPointValue(0.05f)
                .description("加速玩家周围的时间流速：作物生长、熔炉冶炼、刷怪笼等更快")
                .build(),

            create("time_accel_radius").category(StatCategory.UTILITY)
                .perPointValue(1.0f)
                .description("扩大「加速」的影响半径（每点 +1 格），玩家可自行加点扩展范围")
                .build(),

            create("cross_dimension_teleport").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(1)
                .description("开启后可使用 /infstats crossdim 进行跨维度传送")
                .build(),

            create("fixed_point_teleport").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(1)
                .description("开启后可使用 /infstats wp 保存与传送到固定坐标点")
                .build(),

            create("portable_crafting").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(1)
                .description("开启后可使用 /infstats craft 打开随身工作台")
                .build(),

            create("portable_furnace").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(1)
                .description("开启后可使用 /infstats furnace 打开随身熔炉")
                .build(),

            create("portable_anvil").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(1)
                .description("开启后可使用 /infstats anvil 打开随身铁砧（修复与重命名，正常消耗经验）")
                .build(),

            create("portable_ender_chest").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(1)
                .description("开启后可随时打开自己的末影箱（属性面板页脚「末影箱」按钮或 /infstats enderchest）")
                .build(),

            create("portable_smithing").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(1)
                .description("开启后可随时打开锻造台（属性面板「随身工具」入口或 /infstats smithing）")
                .build(),

            create("portable_enchanting").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(1)
                .description("开启后可随时打开进阶高级附魔台（属性面板「随身工具」入口）：自己挑附魔、不用随机，可升级/移除已有附魔、用经验等级修复或回收物品，也能直接做附魔书；付款可用经验等级或属性点数（界面里切换），不消耗青金石")
                .build(),

            create("enchant_limit").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(1)
                .description("开启后附魔等级不再受原版上限约束：进阶高级附魔台可以选到任意等级，铁砧（含随身铁砧）里同等级的附魔也能继续往上叠，另可用 /infstats enchant <附魔> <等级> 直接指定。等级越高越贵（进阶附魔台按等级线性收费、铁砧每次合成只 +1），因此不再另设封顶")
                .build(),

            create("portable_stonecutter").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(1)
                .description("开启后可随时打开切石机（属性面板「随身工具」入口）")
                .build(),

            create("portable_loom").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(1)
                .description("开启后可随时打开织布机（属性面板「随身工具」入口）")
                .build(),

            create("portable_cartography").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(1)
                .description("开启后可随时打开制图台（属性面板「随身工具」入口）")
                .build(),

            create("portable_grindstone").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(1)
                .description("开启后可随时打开磨石（属性面板「随身工具」入口）")
                .build(),

            create("portable_brewing").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(1)
                .description("开启后可随时打开酿造台（自带无限燃料、不需要烈焰粉；属性面板「随身工具」入口或 /infstats brewing）")
                .build(),

            create("weather_control").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(1)
                .description("开启后可用属性面板「天气」按钮切换主世界天气：晴 → 雨 → 雷 → 晴")
                .build(),

            create("chunk_loader").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(1)
                .description("开启后可用属性面板「强加载」按钮输入区块坐标强加载/卸载区块（每维度有数量上限）")
                .build(),

            create("entity_panel").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(1)
                .description("开启后获得一根『生物观察杖』（带标记的木棍）：手持它右键任意生物即可打开该生物的面板，"
                        + "查看它的全部属性（生命上限 / 护甲 / 攻击力 / 移动速度……连其它模组添加的属性也在内）；"
                        + "关闭该属性时木棍会被收回")
                .build(),

            create("infinite_energy").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(3)
                .description("投入3点解锁：所有能量恒满 —— 背包 / 饰品栏 / 骑乘载具上的 FE 能量源"
                        + "（RF、Mekanism、热力、EnderIO、沉浸工程等最终都走 Forge Energy）每 tick 自动补满，"
                        + "卓越前线载具的行驶耗电 / 能量弹药武器也不再扣电；不消耗燃料、永远满电")
                .build(),

            create("sbw_infinite_health").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(3)
                .description("投入3点解锁：乘坐 / 驾驶卓越前线（Superb Warfare）的载具时血量恒满 —— "
                        + "载具打不坏、打不死，也不会因为亏血而瘫掉部位（履带 / 引擎 / 炮塔）"
                        + "（需要安装卓越前线，未安装时该属性无效果）")
                .build(),

            create("goety_infinite_soul").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(3)
                .description("投入3点解锁：诡厄巫法（Goety）的灵魂能量恒满 —— 施法、仪式、灵魂修补器……"
                        + "都不会消耗灵魂能量，灵魂能量条一直满格；随身携带的「灵魂图腾」（Totem of Souls）"
                        + "也会一直填满（需要安装诡厄巫法，未安装时该属性无效果）")
                .build(),

            create("create_infinite_stress").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(3)
                .description("投入3点解锁：机械动力（Create）—— 你自己放置的动力网络不再因为应力（SU）不足"
                        + "而超载停机，机器照常运转、应力表指针回到低位；整张网络只要有你放置的方块即生效，"
                        + "别人基地与公共机器不受影响（需要安装机械动力，未安装时该属性无效果）")
                .build(),

            create("no_thirst").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(3)
                .description("投入3点解锁：口渴值恒满 —— 「口渴」（Thirst Was Taken）的口渴条 / 解渴度一直是满的、"
                        + "消耗值归零，跑步挖矿回血都不会把水耗干，也不会因为脱水扣血"
                        + "（需要安装口渴模组，未安装时该属性无效果）")
                .build(),

            create("no_temperature").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(3)
                .description("投入3点解锁：体温恒定在舒适区 —— 「冷汗」（Cold Sweat）的体温不再随严寒 / 酷暑 / "
                        + "下矿 / 下界 / 火焰雪地偏离，不会因为太冷太热掉血、减速、视线模糊或发抖"
                        + "（需要安装冷汗模组，未安装时该属性无效果）")
                .build(),

            create("infinite_mana").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(3)
                .description("投入3点解锁：魔力恒满 —— 同时覆盖三个模组，装哪个哪个生效："
                        + "Iron's 法术与魔法书（玩家法力）、Ars Nouveau（魔源）、"
                        + "Botania 植物魔法（魔力石板 / 魔力戒指等携带魔力，外加你自己放下的魔力池）")
                .build(),

            create("trade_discount").category(StatCategory.UTILITY)
                .percentage()
                .perPointValue(0.01f)
                .description("村民交易折扣：每点使交易价格降低 1%（最高 90%），对流浪商人生效；与声望折扣取更优惠的一者")
                .build(),

            create("trade_restock").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(3)
                .description("投入3点解锁：与村民交易后立刻补货，可以连续交易同一条目")
                .build(),

            create("anvil_cost").category(StatCategory.UTILITY)
                .percentage()
                .perPointValue(0.05f)
                .description("铁砧操作的等级消耗 -5% / 点（最多 -90%），同时降低「过于昂贵」的门槛")
                .build(),

            create("pe_auto_learn").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(5)
                .description("投入5点解锁：获得物品时自动记入EMC知识库（本模组与ProjectE同时生效），无需卖入转化桌即可用EMC转化")
                .build(),

            create("emc_learn_bonus").category(StatCategory.UTILITY)
                .percentage()
                .maxLevel(5)
                .perPointValue(0.05f)
                .description("学习 / 卖出物品获得的 EMC +5% / 点（最多 +25%）")
                .build(),

            create("emc_extract_discount").category(StatCategory.UTILITY)
                .percentage()
                .maxLevel(5)
                .perPointValue(0.06f)
                .description("从 EMC 中取出 / 以 EMC 合成物品的支付价格 -6% / 点（最多 -30%）")
                .build(),

        };
    }

    private static Builder create(String id) {
        return builder(id).translationKey("stat.infinitestats." + id);
    }

    // ========== Getter 方法 ==========

    public String getId() {
        return id;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public StatCategory getCategory() {
        return category;
    }

    public StatBehavior getBehavior() {
        return behavior;
    }

    public float getPerPointValue() {
        return perPointValue;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    /** 出厂默认的每点数值（配置未覆盖时生效）。 */
    public float getDefaultPerPointValue() {
        return defaultPerPointValue;
    }

    /** 出厂默认的点数上限 / 开关解锁点数（配置未覆盖时生效）。 */
    public int getDefaultMaxLevel() {
        return defaultMaxLevel;
    }

    // ========== 配置调参 ==========

    /**
     * 内置属性的出厂数值快照。
     * <p>
     * 这份快照只服务于 {@link com.infinitestats.Config} 生成配置项的默认值，
     * <b>刻意不读配置</b>：Config 的静态初始化会回头引用本类，这里若再读 Config 就会形成
     * 类初始化循环。因此快照值恒等于代码里写死的默认值。
     */
    public static final class TuningDefault {
        private final String id;
        private final float perPoint;
        private final int maxPoints;
        private final boolean toggle;

        TuningDefault(String id, float perPoint, int maxPoints, boolean toggle) {
            this.id = id;
            this.perPoint = perPoint;
            this.maxPoints = maxPoints;
            this.toggle = toggle;
        }

        public String getId() {
            return id;
        }

        public float getPerPoint() {
            return perPoint;
        }

        public int getMaxPoints() {
            return maxPoints;
        }

        public boolean isToggle() {
            return toggle;
        }
    }

    /** 内置属性的出厂数值列表（顺序与注册顺序一致）。 */
    public static List<TuningDefault> tuningDefaults() {
        List<TuningDefault> list = new ArrayList<>(BUILTIN_STATS.length);
        for (StatType stat : BUILTIN_STATS) {
            list.add(new TuningDefault(stat.id, stat.defaultPerPointValue, stat.defaultMaxLevel,
                    stat.behavior == StatBehavior.TOGGLE));
        }
        return list;
    }

    /**
     * 把配置文件中的「每点数值 / 点数上限」应用到内置属性。
     * <p>
     * <b>外部属性不参与</b>：它们的数值由属性自身取值范围与
     * {@code config/infinitestats/external_attributes.json} 决定。Forge 配置项是按内置属性表
     * 静态生成的，外部属性没有对应键；如果这里照样覆盖，会把配置文件里的手写值冲掉。
     * 配置读取失败（尚未加载）时保留当前值，不会把属性打成 0。
     *
     * @return 实际发生变化的属性数量（用于日志）
     */
    public static int applyTuningFromConfig() {
        int changed = 0;
        for (StatType stat : ALL_STATS_LIST) {
            if (stat.category == StatCategory.EXTERNAL) continue;
            float perPoint = com.infinitestats.Config.statPerPoint(stat.id, stat.defaultPerPointValue);
            int maxPoints = com.infinitestats.Config.statMaxPoints(stat.id, stat.defaultMaxLevel);
            if (maxPoints < 1) maxPoints = stat.defaultMaxLevel;
            if (perPoint != stat.perPointValue || maxPoints != stat.maxLevel) {
                changed++;
            }
            stat.perPointValue = perPoint;
            stat.maxLevel = maxPoints;
        }
        return changed;
    }

    public boolean isToggle() {
        return behavior == StatBehavior.TOGGLE;
    }

    public boolean isPercentage() {
        return behavior == StatBehavior.PERCENTAGE;
    }

    public boolean hasAttribute() {
        return attributeSupplier != null;
    }

    @Nullable
    public Attribute getAttribute() {
        return attributeSupplier != null ? attributeSupplier.get() : null;
    }

    /** 获取关联的 attribute 注册名（可能为 null） */
    @Nullable
    public String getAttributeName() {
        return attributeName;
    }

    /** 获取镜像写入的属性注册名（不含主属性） */
    public List<String> getMirrorAttributeNames() {
        return mirrorAttributeNames;
    }

    /**
     * 解析该属性生效的所有真实属性实例（主属性 + 镜像属性），
     * 未注册的属性（模组不存在）会被自动跳过。
     * @return 注册名 → Attribute，保持插入顺序，主属性在最前
     */
    public Map<String, Attribute> resolveAttributes() {
        Map<String, Attribute> result = new LinkedHashMap<>();
        Attribute primary = getAttribute();
        if (primary != null && attributeName != null) {
            result.put(attributeName, primary);
        }
        for (int i = 0; i < mirrorAttributeNames.size(); i++) {
            Attribute mirror = mirrorAttributeSuppliers.get(i).get();
            if (mirror != null) {
                result.putIfAbsent(mirrorAttributeNames.get(i), mirror);
            }
        }
        return result;
    }

    public String getDescription() {
        return description;
    }

    /** 是否为隐藏属性（不在 GUI 中显示，除非解锁） */
    public boolean isHidden() {
        return hidden;
    }

    /**
     * 随身工具属性：解锁 / 关闭都在「随身工具」面板里用左键 / 右键完成，
     * 属性面板不再单独列出它们（见 {@code StatsScreen#buildCategoryMap}）；
     * 「重置分类」也不会连带清掉它们 —— 面板里看不到的东西不该被面板的整体操作动到。
     */
    private static final Set<String> PORTABLE_TOOL_IDS = Set.of(
            "portable_crafting", "portable_furnace", "portable_anvil", "portable_ender_chest",
            "portable_smithing", "portable_enchanting", "portable_stonecutter", "portable_loom",
            "portable_cartography", "portable_grindstone", "portable_brewing");

    /** 是否为随身工具属性（统一在「随身工具」面板里开关，属性面板默认不列出）。 */
    public boolean isPortableTool() {
        return PORTABLE_TOOL_IDS.contains(id);
    }

    /**
     * 计算给定点数时的属性值
     */
    public float calculateValue(long points) {
        return calculator.calculate(points, getPerPointValue());
    }

    /**
     * 是否激活（用于开关型属性，负数不算激活）
     */
    public boolean isActive(long points) {
        if (behavior == StatBehavior.TOGGLE) {
            return points >= maxLevel;
        }
        // 非开关型属性：值非零即为"激活"（支持负值效果）
        return calculateValue(points) != 0;
    }

    /**
     * 格式化显示值（适配负数）
     */
    public String formatValue(float value) {
        if (isToggle()) {
            return value >= 1 ? "开启" : "关闭";
        }
        if (isPercentage()) {
            return String.format("%+.1f%%", value * 100);
        }
        if (Math.abs(value) < 1) {
            return String.format("%+.2f", value);
        }
        return String.format("%+.1f", value);
    }

    /**
     * 获取属性显示名称（外部属性显示注册名，等翻译文件内置后由翻译键接管）
     */
    public String getDisplayName() {
        if (customDisplayName != null) {
            return customDisplayName;
        }
        if (category == StatCategory.EXTERNAL && attributeName != null) {
            return attributeName;
        }
        return "";
    }

    /**
     * 获取属性显示名称的翻译回退键
     */
    public String getDisplayFallback() {
        if (customDisplayName != null) {
            return customDisplayName;
        }
        if (category == StatCategory.EXTERNAL && attributeName != null) {
            return attributeName;
        }
        return id;
    }

    /** 覆盖配置里手写的显示名；没配过时为 null（此时继续走翻译键）。 */
    @Nullable
    public String getCustomDisplayName() {
        return customDisplayName;
    }

    // ========== 工具方法 ==========

    /**
     * 根据ID查找属性（O(1) HashMap 查找）
     */
    public static StatType fromId(String id) {
        return STAT_LOOKUP.get(id);
    }

    /**
     * 获取某类别下的所有属性
     */
    public static StatType[] getByCategory(StatCategory category) {
        return ALL_STATS_LIST.stream()
                .filter(s -> s.getCategory() == category)
                .toArray(StatType[]::new);
    }

    /**
     * 获取所有开关型属性
     */
    public static StatType[] getToggleStats() {
        return ALL_STATS_LIST.stream()
                .filter(StatType::isToggle)
                .toArray(StatType[]::new);
    }

    /**
     * 获取所有基于原版/模组属性的属性
     */
    public static StatType[] getAttributeStats() {
        return ALL_STATS_LIST.stream()
                .filter(StatType::hasAttribute)
                .toArray(StatType[]::new);
    }

    /**
     * 获取内置属性数量（不包含动态发现的）
     */
    public static int getBuiltinCount() {
        return BUILTIN_STATS.length;
    }

    /**
     * 判断是否为内置属性
     */
    public boolean isBuiltin() {
        for (StatType s : BUILTIN_STATS) {
            if (s == this) return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format("StatType[%s, %s, %s]", id, category.getName(), behavior);
    }
}
