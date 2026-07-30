package com.infinitestats.stats;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
    private final float perPointValue;
    private final int maxLevel;
    private final String description;
    private final boolean hidden;

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
        this.calculator = builder.calculator;
        this.attributeSupplier = builder.attributeSupplier;
        this.attributeName = builder.attributeName;
        this.perPointValue = builder.perPointValue;
        this.maxLevel = builder.maxLevel;
        this.description = builder.description;
        this.hidden = builder.hidden;
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
        private float perPointValue = 1.0f;
        private int maxLevel = Integer.MAX_VALUE;
        private String description = "";
        private boolean hidden = false;

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

        public Builder attribute(String attributeName) {
            this.attributeName = attributeName;
            this.attributeSupplier = () -> ForgeRegistries.ATTRIBUTES.getValue(
                    new ResourceLocation(attributeName));
            return this;
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

        public Builder toggle() {
            return behavior(StatBehavior.TOGGLE).maxLevel(1).perPointValue(0);
        }

        public Builder percentage() {
            return behavior(StatBehavior.PERCENTAGE);
        }

        public StatType build() {
            // 开关型属性使用 maxLevel 作为激活所需点数
            if (this.behavior == StatBehavior.TOGGLE) {
                final long threshold = this.maxLevel;
                this.calculator = (points, value) -> points >= threshold ? 1 : 0;
            }
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

    /**
     * 动态发现其他模组注册的属性
     * 应在 FMLCommonSetup 中调用，此时 ForgeRegistries 已完成填充
     */
    public static void discoverModdedAttributes() {
        if (discoveryDone) return;
        discoveryDone = true;

        int added = 0;
        for (Map.Entry<net.minecraft.resources.ResourceKey<Attribute>, Attribute> entry : ForgeRegistries.ATTRIBUTES.getEntries()) {
            ResourceLocation rl = entry.getKey().location();
            String attrName = rl.toString();

            // 跳过已覆盖的属性
            if (COVERED_ATTRIBUTES.contains(attrName)) continue;

            // 生成唯一的 stat ID
            String statId = "attr." + rl.getNamespace() + "." + rl.getPath();

            // 跳过已存在的
            if (STAT_LOOKUP.containsKey(statId)) continue;

            // 跳过 player 命名空间下过于通用的基础属性（已在内置列表中覆盖）
            if (rl.getNamespace().equals("minecraft")) {
                // 原版属性但不在内置列表中的——仍然添加
                // 例如 follow_range, max_absorption, step_height, gravity 等
            }

            // 自动决定合理的每点值
            float defaultValue = guessDefaultPerPoint(rl);

            StatType newStat = builder(statId)
                    .translationKey("stat.infinitestats." + statId)
                    .category(StatCategory.EXTERNAL)
                    .attribute(attrName)
                    .perPointValue(defaultValue)
                    .description("")
                    .build();

            registerStat(newStat);
            COVERED_ATTRIBUTES.add(attrName);
            added++;
        }

        // 重建 ALL_STATS
        ALL_STATS = ALL_STATS_LIST.toArray(new StatType[0]);
        System.out.println("[InfiniteStats] Discovered " + added + " external attributes from mods");

        // 生成翻译模板文件（仅首次发现时）
        if (added > 0) {
            generateTranslationTemplate();
        }
    }

    /**
     * 生成翻译模板文件到 config 目录
     * 用户填好翻译后发回，我们将它内置进模组的语言文件
     */
    private static void generateTranslationTemplate() {
        try {
            java.nio.file.Path configDir = java.nio.file.Path.of("config", "infinitestats");
            java.nio.file.Files.createDirectories(configDir);
            java.nio.file.Path templatePath = configDir.resolve("external_translations.json");

            // 收集所有外部属性的翻译键
            Map<String, String> keys = new java.util.LinkedHashMap<>();
            for (StatType stat : ALL_STATS_LIST) {
                if (stat.getCategory() == StatCategory.EXTERNAL) {
                    // 显示名称键
                    keys.put(stat.getTranslationKey(), "");
                    // 描述键
                    keys.put(stat.getTranslationKey() + ".desc", stat.getAttributeName() != null
                            ? "[" + stat.getAttributeName() + "]" : "");
                }
            }

            // 格式化 JSON
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
            System.out.println("[InfiniteStats] Translation template written to config/infinitestats/external_translations.json ("
                    + keys.size() + " keys)");
        } catch (Exception e) {
            System.err.println("[InfiniteStats] Failed to generate translation template: " + e.getMessage());
        }
    }

    /**
     * 根据属性名猜测合理的每点默认值
     */
    private static float guessDefaultPerPoint(ResourceLocation rl) {
        String path = rl.getPath().toLowerCase();

        // 移动速度类 → 微小增量
        if (path.contains("speed") || path.contains("movement")) return 0.001f;

        // 伤害/攻击类 → 0.05~0.1
        if (path.contains("damage") || path.contains("attack")) return 0.05f;

        // 血量/生命类 → 2.0
        if (path.contains("health") || path.contains("life") || path.contains("max")) return 2.0f;

        // 护甲类 → 0.5
        if (path.contains("armor")) return 0.5f;

        // 韧性类 → 0.25
        if (path.contains("toughness")) return 0.25f;

        // 抗性类 → 0.01
        if (path.contains("resist") || path.contains("knockback")) return 0.01f;

        // 距离类 → 0.5
        if (path.contains("reach") || path.contains("range") || path.contains("distance")) return 0.5f;

        // 幸运类 → 0.1
        if (path.contains("luck")) return 0.1f;

        // 步高/跳跃类 → 0.5
        if (path.contains("step") || path.contains("jump")) return 0.5f;

        // 飞行类 → 0.002
        if (path.contains("fly")) return 0.002f;

        // 吸收类 → 1.0
        if (path.contains("absorption")) return 1.0f;

        // 重力 → 0.001
        if (path.contains("gravity")) return 0.001f;

        // 默认值
        return 1.0f;
    }

    // ========== 属性注册 ==========

    private static StatType[] registerAllStats() {
        return new StatType[] {
            // ===== 攻击属性 =====
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
                .description("每次攻击附加基于伤害的额外真实伤害，无视护甲与减伤")
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
                .description("持续排斥周围的敌对生物，将它们推开（半径每点 +0.5 格）")
                .build(),

            // ===== 防御属性 =====
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

            create("auto_revive").category(StatCategory.DEFENSE)
                .behavior(StatBehavior.TOGGLE).maxLevel(8).perPointValue(0)
                .build(),

            create("block_chance").category(StatCategory.DEFENSE)
                .percentage()
                .perPointValue(0.01f)
                .build(),

            create("absorption_shield").category(StatCategory.DEFENSE)
                .perPointValue(1.0f)
                .build(),

            create("dodge_chance").category(StatCategory.DEFENSE)
                .percentage()
                .perPointValue(0.008f)
                .build(),

            create("debuff_immunity").category(StatCategory.DEFENSE)
                .behavior(StatBehavior.TOGGLE).maxLevel(4).perPointValue(0)
                .build(),

            create("invincibility").category(StatCategory.DEFENSE)
                .behavior(StatBehavior.TOGGLE).maxLevel(1).perPointValue(0)
                .hidden()
                .build(),

            // ===== 机动属性 =====
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

            create("step_height").category(StatCategory.MOBILITY)
                .percentage()
                .perPointValue(0.006f)
                .build(),

            create("fly_speed").category(StatCategory.MOBILITY)
                .attribute("minecraft:generic.flying_speed")
                .percentage()
                .perPointValue(0.002f)
                .build(),

            create("fly").category(StatCategory.MOBILITY)
                .behavior(StatBehavior.TOGGLE).maxLevel(8).perPointValue(0)
                .build(),

            create("no_fall_damage").category(StatCategory.MOBILITY)
                .behavior(StatBehavior.TOGGLE).maxLevel(2).perPointValue(0)
                .build(),

            create("auto_step").category(StatCategory.MOBILITY)
                .behavior(StatBehavior.TOGGLE).maxLevel(2).perPointValue(0)
                .build(),

            create("follow_range").category(StatCategory.MOBILITY)
                .attribute("minecraft:generic.follow_range")
                .perPointValue(0.5f)
                .build(),

            // ===== 功能属性 =====
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

            create("reach").category(StatCategory.UTILITY)
                .attribute("forge:block_reach")
                .perPointValue(0.04f)
                .build(),

            create("entity_reach").category(StatCategory.UTILITY)
                .attribute("forge:entity_reach")
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

            create("night_vision").category(StatCategory.UTILITY)
                .behavior(StatBehavior.TOGGLE).maxLevel(2).perPointValue(0)
                .build(),

            create("water_breathing").category(StatCategory.UTILITY)
                .behavior(StatBehavior.TOGGLE).maxLevel(2).perPointValue(0)
                .build(),

            create("no_hunger").category(StatCategory.UTILITY)
                .behavior(StatBehavior.TOGGLE).maxLevel(3).perPointValue(0)
                .build(),

            create("item_magnet").category(StatCategory.UTILITY)
                .behavior(StatBehavior.TOGGLE).maxLevel(2).perPointValue(0)
                .description("立即收集范围内的掉落物，范围由配置 magnetRange 决定（默认 10 格）")
                .build(),

            create("invisibility").category(StatCategory.UTILITY)
                .behavior(StatBehavior.TOGGLE).maxLevel(3).perPointValue(0)
                .build(),

            create("vein_miner").category(StatCategory.UTILITY)
                .behavior(StatBehavior.TOGGLE).maxLevel(3).perPointValue(0)
                .build(),

            create("auto_smelt").category(StatCategory.UTILITY)
                .behavior(StatBehavior.TOGGLE).maxLevel(2).perPointValue(0)
                .build(),

            create("xp_magnet").category(StatCategory.UTILITY)
                .behavior(StatBehavior.TOGGLE).maxLevel(2).perPointValue(0)
                .build(),

            create("projectile_tracking").category(StatCategory.UTILITY)
                .behavior(StatBehavior.TOGGLE).maxLevel(5).perPointValue(0)
                .description("解锁后发射的弹射物（箭矢、雪球、三叉戟等）会追踪最近的敌人，无范围限制")
                .build(),

            create("no_invincibility_frames").category(StatCategory.UTILITY)
                .behavior(StatBehavior.TOGGLE).maxLevel(5).perPointValue(0)
                .build(),

            create("double_loot").category(StatCategory.UTILITY)
                .percentage()
                .perPointValue(0.005f)
                .build(),

            create("teleport_distance").category(StatCategory.UTILITY)
                .perPointValue(5.0f)
                .build(),

            create("crafting_bonus").category(StatCategory.UTILITY)
                .percentage()
                .perPointValue(0.005f)
                .build(),

            create("bow_draw_speed").category(StatCategory.UTILITY)
                .percentage()
                .perPointValue(0.03f)
                .build(),

            create("use_speed").category(StatCategory.UTILITY)
                .percentage()
                .perPointValue(0.02f)
                .build(),

            create("auto_repair").category(StatCategory.UTILITY)
                .behavior(StatBehavior.TOGGLE).maxLevel(2).perPointValue(0)
                .build(),

            create("repair_amount").category(StatCategory.UTILITY)
                .perPointValue(1.0f)
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

            create("pe_auto_learn").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(5)
                .description("投入5点解锁：获得物品时自动学习到ProjectE知识库，无需卖入转化桌即可用EMC转化")
                .build(),

            // ===== 魔法属性 =====
            create("max_mana").category(StatCategory.MAGIC)
                .perPointValue(10f)
                .build(),

            create("mana_regen").category(StatCategory.MAGIC)
                .perPointValue(0.5f)
                .build(),

            create("magic_damage").category(StatCategory.MAGIC)
                .percentage()
                .perPointValue(0.03f)
                .build(),

            create("cooldown_reduction").category(StatCategory.MAGIC)
                .percentage()
                .perPointValue(0.003f)
                .build(),

            create("mana_shield").category(StatCategory.MAGIC)
                .percentage()
                .perPointValue(0.01f)
                .build(),

            create("mana_steal").category(StatCategory.MAGIC)
                .percentage()
                .perPointValue(0.01f)
                .build(),

            create("spell_power").category(StatCategory.MAGIC)
                .percentage()
                .perPointValue(0.025f)
                .build(),

            create("mana_on_kill").category(StatCategory.MAGIC)
                .perPointValue(2.0f)
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

    public String getDescription() {
        return description;
    }

    /** 是否为隐藏属性（不在 GUI 中显示，除非解锁） */
    public boolean isHidden() {
        return hidden;
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
        if (category == StatCategory.EXTERNAL && attributeName != null) {
            return attributeName;
        }
        return "";
    }

    /**
     * 获取属性显示名称的翻译回退键
     */
    public String getDisplayFallback() {
        if (category == StatCategory.EXTERNAL && attributeName != null) {
            return attributeName;
        }
        return id;
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