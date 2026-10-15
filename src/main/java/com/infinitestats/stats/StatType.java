package com.infinitestats.stats;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.core.registries.BuiltInRegistries;

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
            this.attributeSupplier = () -> BuiltInRegistries.ATTRIBUTE.get(
                    ResourceLocation.parse(attributeName));
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
     * 应在 FMLCommonSetup 中调用，此时 BuiltInRegistries 已完成填充
     */
    public static void discoverModdedAttributes() {
        if (discoveryDone) return;
        discoveryDone = true;

        int added = 0;
        for (Map.Entry<net.minecraft.resources.ResourceKey<Attribute>, Attribute> entry : BuiltInRegistries.ATTRIBUTE.entrySet()) {
            ResourceLocation rl = entry.getKey().location();
            String attrName = rl.toString();
            // 玩家不消费生物的追踪范围，避免删除后又作为外部属性出现。
            if ("minecraft:generic.follow_range".equals(attrName)
                    || "minecraft:generic.flying_speed".equals(attrName)) continue;

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

            // 翻译键直接复用属性自带的 getDescriptionId()：模组的语言文件里通常已经
            // 有这个键的翻译（游戏内显示属性名用的就是它），因此外部属性无需再手工补译文。
            // 仅在该键缺失（模组自己都没提供翻译）时回退到本模组的自建键。
            String descId = entry.getValue().getDescriptionId();
            String translationKey = (descId != null && !descId.isBlank())
                    ? descId
                    : "stat.infinitestats." + statId;

            StatType newStat = builder(statId)
                    .translationKey(translationKey)
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

        // 统计有多少外部属性能直接复用其所属模组自带的译文（翻译键 = getDescriptionId()）
        int external = 0, translated = 0;
        for (StatType stat : ALL_STATS_LIST) {
            if (stat.getCategory() != StatCategory.EXTERNAL) continue;
            external++;
            String key = stat.getTranslationKey();
            if (!net.minecraft.network.chat.Component.translatable(key).getString().equals(key)) translated++;
        }
        System.out.println("[InfiniteStats] Discovered " + added + " external attributes ("
                + translated + "/" + external + " auto-translated from their own mods)");

        // 生成翻译模板文件（仅首次发现时）
        if (added > 0) {
            generateTranslationTemplate();
        }
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

            create("infinite_arrows").category(StatCategory.ATTACK)
                .toggle()
                .maxLevel(5)
                .description("投入5点解锁：背包中没有箭也能拉弓 / 弩蓄力并射出箭矢，箭不消耗背包")
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
                .description("解锁后你发射的弹射物（箭矢、雪球、三叉戟等）会自动转向追踪敌人，优先追踪视野内的目标，被方块挡住的不会追。仅在配置半径内（默认64格，可在配置中调整）生效。")
                .build(),

            create("no_invincibility_frames").category(StatCategory.UTILITY)
                .behavior(StatBehavior.TOGGLE).maxLevel(5).perPointValue(0)
                .build(),

            create("double_loot").category(StatCategory.UTILITY)
                .percentage()
                .perPointValue(0.005f)
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

            create("cooldown_reduction").category(StatCategory.UTILITY)
                .percentage()
                .perPointValue(0.005f)
                .description("减少物品冷却时间（每点 -0.5%，最高 -100%）")
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
                .hidden()
                .build(),

            create("portable_furnace").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(1)
                .description("开启后可使用 /infstats furnace 打开随身熔炉")
                .hidden()
                .build(),

            create("portable_anvil").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(1)
                .description("开启后可使用 /infstats anvil 打开随身铁砧（修复与重命名，正常消耗经验）")
                .hidden()
                .build(),

            create("pe_auto_learn").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(5)
                .description("投入5点解锁：获得物品时自动学习到ProjectE知识库，无需卖入转化桌即可用EMC转化")
                .build(),

            create("portable_ender_chest").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(1)
                .description("开启后可随时打开自己的末影箱（属性面板「随身」入口），与原版末影箱共用同一份库存")
                .hidden()
                .build(),

            create("portable_smithing").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(1)
                .description("开启后可随时打开锻造台（属性面板「随身」入口）")
                .hidden()
                .build(),

            create("portable_stonecutter").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(1)
                .description("开启后可随时打开切石机（属性面板「随身」入口）")
                .hidden()
                .build(),

            create("portable_loom").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(1)
                .description("开启后可随时打开织布机（属性面板「随身」入口）")
                .hidden()
                .build(),

            create("portable_cartography").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(1)
                .description("开启后可随时打开制图台（属性面板「随身」入口）")
                .hidden()
                .build(),

            create("portable_grindstone").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(1)
                .description("开启后可随时打开磨石（属性面板「随身」入口）")
                .hidden()
                .build(),

            create("entity_panel").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(1)
                .description("开启后获得一根『生物观察杖』（带标记的木棍）：手持它右键任意生物即可打开该生物的面板，"
                        + "查看它的全部属性（生命上限 / 护甲 / 攻击力 / 移动速度……连其它模组添加的属性也在内）；"
                        + "关闭该属性时木棍会被收回")
                .build(),

            create("portable_enchanting").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(1)
                .description("开启后可随时打开进阶高级附魔台（属性面板「随身」入口）：自己挑附魔与等级、"
                        + "改已有附魔、花经验修耐久、把附魔拆下来回收，还能把附魔做成书；"
                        + "付款可用经验等级或属性点数，不消耗青金石")
                .hidden()
                .build(),

            create("portable_brewing").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(1)
                .description("开启后可随时打开酿造台（属性面板「随身」入口）：自带无限燃料、不需要烈焰粉，关掉界面也继续酿，东西留在台子里")
                .hidden()
                .build(),

            create("auto_deposit").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(3)
                .description("投入3点解锁：自动把背包物品存入存储网络（RS / AE2 / 汤姆存储 / 背包），"
                        + "存入顺序跟随配置的存储优先级；默认不碰快捷栏，可用配置改为只保留手持那一格")
                .build(),

            create("weather_control").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(1)
                .description("开启后可用属性面板「天气」按钮打开天气面板，逐档切换主世界天气：晴 / 下雨 / 雷暴")
                .build(),

            create("chunk_loader").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(1)
                .description("开启后可用属性面板「强加载」按钮输入坐标强加载 / 卸载区块，使该区块在无人时也保持加载（每维度有数量上限）")
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
                        + "每次造成本次伤害 20% 的额外属性伤害，独立结算、无视护甲与减伤")
                .build(),

            create("multi_jump").category(StatCategory.MOBILITY)
                .perPointValue(1.0f)
                .maxLevel(10)
                .description("每点获得一次空中跳跃机会（等级＝可跳次数，最多 10 次）：起跳后松开再按跳跃键即可二段跳")
                .build(),

            create("climb_speed").category(StatCategory.MOBILITY)
                .percentage()
                .perPointValue(0.2f)
                .description("爬梯子 / 藤蔓 / 脚手架时的上升速度 +20% / 点")
                .build(),

            create("always_eat").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(3)
                .description("投入3点解锁：饱食度满时也能继续吃普通食物（原版满饱食度只有金苹果一类能吃，"
                        + "其余食物右键只会失败）；金苹果这类原版就允许满饱食度进食的不受影响。"
                        + "可用配置 AlwaysEat 分组里的限制 / 例外名单限定哪些食物适用")
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

            create("trade_discount").category(StatCategory.UTILITY)
                .percentage()
                .perPointValue(0.01f)
                .description("村民交易折扣：每点使交易价格降低 1%（最高 90%），对流浪商人生效；与原版声望折扣叠加")
                .build(),

            create("trade_restock").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(3)
                .description("投入3点解锁：与村民交易后立刻补货，可以连续交易同一条目")
                .build(),

            create("instant_grow").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(2)
                .description("投入2点解锁：手持该动物的饲料右键幼年动物，一次喂食即可长大（原版约需 10 次）")
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

            create("infinite_energy").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(3)
                .description("投入3点解锁：所有能量恒满 —— 背包 / 饰品栏 / 骑乘载具上的 FE 能量源"
                        + "（RF、Mekanism、热力、EnderIO、沉浸工程等最终都走 NeoForge Energy）每 tick 自动补满，"
                        + "卓越前线载具的行驶耗电 / 能量弹药武器也不再扣电；不消耗燃料、永远满电")
                .build(),

            create("infinite_mana").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(3)
                .description("投入3点解锁：魔力恒满 —— 同时覆盖三个模组，装哪个哪个生效："
                        + "Iron's 法术与魔法书（玩家法力）、Ars Nouveau（魔源）、"
                        + "Botania 植物魔法（魔力石板 / 魔力戒指等携带魔力，外加你自己放下的魔力池）")
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

            create("infinite_ammo").category(StatCategory.ATTACK)
                .toggle()
                .maxLevel(5)
                .description("投入5点解锁：手持卓越前线（Superb Warfare）的枪械、或操作卓越前线的载具武器时"
                        + "弹匣恒满 —— 射击不扣子弹、也永远不用换弹，载具机炮与干扰弹同样无限"
                        + "（需要安装卓越前线，未安装时该属性无效果）")
                .build(),

            create("no_heat").category(StatCategory.ATTACK)
                .toggle()
                .maxLevel(3)
                .description("投入3点解锁：手持卓越前线的枪械、或操作卓越前线的载具武器时热量恒为 0，"
                        + "既不会升温也不会过热锁死（需要安装卓越前线，未安装时该属性无效果）")
                .build(),

            create("no_recoil").category(StatCategory.ATTACK)
                .toggle()
                .maxLevel(3)
                .description("投入3点解锁：卓越前线的枪械开火时视角不再被后坐力往上顶，"
                        + "瞄准时的呼吸晃动与载具开火震屏也一并消除，弹着点自己控制"
                        + "（需要安装卓越前线，未安装时该属性无效果）")
                .build(),

            create("sbw_infinite_health").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(3)
                .description("投入3点解锁：乘坐 / 驾驶卓越前线（Superb Warfare）的载具时血量恒满 —— "
                        + "载具打不坏、打不死，也不会因为亏血而瘫掉部位（履带 / 引擎 / 炮塔）"
                        + "（需要安装卓越前线，未安装时该属性无效果）")
                .build(),

            create("breed_no_cooldown").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(3)
                .description("投入3点解锁：自己喂食繁殖出的动物不再进入 5 分钟冷却，可以立刻再次繁殖")
                .build(),

            create("enchant_limit").category(StatCategory.UTILITY)
                .toggle()
                .maxLevel(1)
                .description("开启后附魔等级不再受原版上限约束：进阶高级附魔台可以选到任意等级，"
                        + "铁砧（含随身铁砧）里同等级的附魔也能继续往上叠，另可用 /infstats enchant <附魔> <等级> 直接指定。"
                        + "等级越高越贵（进阶附魔台按等级线性收费、铁砧每次合成只 +1），因此不再另设封顶")
                .build(),

            create("anvil_cost").category(StatCategory.UTILITY)
                .percentage()
                .perPointValue(0.05f)
                .description("铁砧操作的等级消耗 -5% / 点（最多 -90%），减免在「过于昂贵」判定前生效，"
                        + "因此同时降低该门槛；随身铁砧另受 50 级封顶")
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
