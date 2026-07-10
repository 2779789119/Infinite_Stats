package com.etbw2.infinitestats.stats;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * 属性类型 - 使用更优雅的设计模式
 * 支持无限等级属性和开关型属性
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
    private final float perPointValue;
    private final int maxLevel;
    private final String description;

    /**
     * 属性行为类型
     */
    public enum StatBehavior {
        /** 无限叠加型 - 每点增加固定值 */
        SCALING,
        /** 开关型 - 0或1，达到1即激活 */
        TOGGLE,
        /** 百分比型 - 每点增加百分比 */
        PERCENTAGE,
        /** 百分比上限型 - 有上限的百分比 */
        PERCENTAGE_CAP
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
        float calculate(int points, float perPointValue);
    }

    // ========== 默认计算器 ==========

    public static final StatValueCalculator SCALING_CALCULATOR = (points, value) -> points * value;
    public static final StatValueCalculator TOGGLE_CALCULATOR = (points, value) -> points >= 1 ? 1 : 0;
    public static final StatValueCalculator PERCENTAGE_CALCULATOR = (points, value) -> points * value;
    public static final StatValueCalculator PERCENTAGE_CAP_CALCULATOR = (points, value) -> Math.min(points * value, 0.9f);

    // ========== 构造方法（Builder模式） ==========

    private StatType(Builder builder) {
        this.id = builder.id;
        this.translationKey = builder.translationKey;
        this.category = builder.category;
        this.behavior = builder.behavior;
        this.calculator = builder.calculator;
        this.attributeSupplier = builder.attributeSupplier;
        this.perPointValue = builder.perPointValue;
        this.maxLevel = builder.maxLevel;
        this.description = builder.description;
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
        private float perPointValue = 1.0f;
        private int maxLevel = Integer.MAX_VALUE;
        private String description = "";

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
                case PERCENTAGE_CAP -> PERCENTAGE_CAP_CALCULATOR;
            };
            return this;
        }

        public Builder calculator(StatValueCalculator calculator) {
            this.calculator = calculator;
            return this;
        }

        public Builder attribute(String attributeName) {
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

        public Builder toggle() {
            return behavior(StatBehavior.TOGGLE).maxLevel(1).perPointValue(0);
        }

        public Builder percentage() {
            return behavior(StatBehavior.PERCENTAGE);
        }

        public StatType build() {
            // 开关型属性使用 maxLevel 作为激活所需点数
            if (this.behavior == StatBehavior.TOGGLE) {
                final int threshold = this.maxLevel;
                this.calculator = (points, value) -> points >= threshold ? 1 : 0;
            }
            return new StatType(this);
        }
    }

    // ========== 属性注册 ==========

    // 所有属性定义 - 使用 Builder 模式创建
    public static final StatType[] ALL_STATS = registerAllStats();

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

            create("dash_cooldown").category(StatCategory.MOBILITY)
                .percentage()
                .perPointValue(-0.01f)
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

            create("reach").category(StatCategory.UTILITY)
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

            create("no_invincibility_frames").category(StatCategory.UTILITY)
                .behavior(StatBehavior.TOGGLE).maxLevel(5).perPointValue(0)
                .build(),

            create("double_loot").category(StatCategory.UTILITY)
                .percentage()
                .perPointValue(0.005f)
                .build(),

            create("extra_loot_slot").category(StatCategory.UTILITY)
                .perPointValue(1.0f)
                .build(),

            create("teleport_distance").category(StatCategory.UTILITY)
                .perPointValue(5.0f)
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
        return behavior == StatBehavior.PERCENTAGE || behavior == StatBehavior.PERCENTAGE_CAP;
    }

    public boolean hasAttribute() {
        return attributeSupplier != null;
    }

    @Nullable
    public Attribute getAttribute() {
        return attributeSupplier != null ? attributeSupplier.get() : null;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 计算给定点数时的属性值
     */
    public float calculateValue(int points) {
        return calculator.calculate(points, getPerPointValue());
    }

    /**
     * 是否激活（用于开关型属性）
     */
    public boolean isActive(int points) {
        return behavior == StatBehavior.TOGGLE ? points >= maxLevel : calculateValue(points) > 0;
    }

    /**
     * 格式化显示值
     */
    public String formatValue(float value) {
        if (isToggle()) {
            return value >= 1 ? "开启" : "关闭";
        }
        if (isPercentage()) {
            return String.format("%.1f%%", value * 100);
        }
        if (Math.abs(value) < 1) {
            return String.format("%.2f", value);
        }
        return String.format("%.1f", value);
    }

    // ========== 工具方法 ==========

    /**
     * 根据ID查找属性
     */
    public static StatType fromId(String id) {
        for (StatType stat : ALL_STATS) {
            if (stat.getId().equals(id)) {
                return stat;
            }
        }
        return null;
    }

    /**
     * 获取某类别下的所有属性
     */
    public static StatType[] getByCategory(StatCategory category) {
        return java.util.Arrays.stream(ALL_STATS)
                .filter(s -> s.getCategory() == category)
                .toArray(StatType[]::new);
    }

    /**
     * 获取所有开关型属性
     */
    public static StatType[] getToggleStats() {
        return java.util.Arrays.stream(ALL_STATS)
                .filter(StatType::isToggle)
                .toArray(StatType[]::new);
    }

    /**
     * 获取所有基于原版属性的属性
     */
    public static StatType[] getAttributeStats() {
        return java.util.Arrays.stream(ALL_STATS)
                .filter(StatType::hasAttribute)
                .toArray(StatType[]::new);
    }

    @Override
    public String toString() {
        return String.format("StatType[%s, %s, %s]", id, category.getName(), behavior);
    }
}