package com.infinitestats.util;

import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * 属性「最终值 ↔ 基础值」的换算 —— 把「我想要最终值是 N」换回「基础值该设多少」。
 *
 * <h2>原版公式（{@code AttributeInstance#getValue}）</h2>
 * <pre>
 * final = sanitize( (base + Σ加法) × (1 + Σ乘基) × Π(1 + Σ乘总) )
 * </pre>
 *
 * <h2>1.21.1 适配</h2>
 * 修饰符的标识从 UUID 改成了 {@link net.minecraft.resources.ResourceLocation}，
 * {@code getModifiers(Operation)} 返回的是 {@code Map<ResourceLocation, AttributeModifier>}，
 * 数值取 {@code amount()}。公式本身与原版一致。
 */
public final class AttributeValueMath {

    /** 乘区小于这个量级就当作 0：除以它没有意义，退化成「只靠加法凑」。 */
    private static final double EPS = 1.0E-6;

    /** 反算出来的基础值超过这个量级就不写：正常玩法远远用不到，写进去只会污染存档。 */
    private static final double BASE_LIMIT = 1.0E9;

    private AttributeValueMath() {}

    /** 所有加法修饰符之和。 */
    public static double addition(AttributeInstance instance) {
        double sum = 0;
        for (AttributeModifier modifier : instance.getModifiers()) {
            if (modifier.operation() == AttributeModifier.Operation.ADD_VALUE) sum += modifier.amount();
        }
        return sum;
    }

    /** 所有乘基修饰符之和（注意是「和」，原版的乘基是相加后再整体乘）。 */
    public static double multiplyBase(AttributeInstance instance) {
        double sum = 0;
        for (AttributeModifier modifier : instance.getModifiers()) {
            if (modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE) sum += modifier.amount();
        }
        return sum;
    }

    /** 所有乘总修饰符的整体系数 Π(1 + amount)。 */
    public static double multiplyTotalFactor(AttributeInstance instance) {
        double factor = 1.0;
        for (AttributeModifier modifier : instance.getModifiers()) {
            if (modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
                factor *= 1.0 + modifier.amount();
            }
        }
        return factor;
    }

    /** 综合乘区：(1 + Σ乘基) × Π(1 + 乘总)。 */
    public static double factor(AttributeInstance instance) {
        return (1.0 + multiplyBase(instance)) * multiplyTotalFactor(instance);
    }

    /**
     * 目标最终值 → 该写的基础值。
     *
     * @return 反算结果；算不出有效值（非有限值、超出合理量级）返回 {@link Double#NaN}
     */
    public static double baseForFinal(AttributeInstance instance, double target) {
        if (instance == null || !Double.isFinite(target)) return Double.NaN;
        double factor = factor(instance);
        double base = Math.abs(factor) < EPS
                ? target - addition(instance)          // 乘区塌成 0：只能靠加法凑
                : target / factor - addition(instance);
        if (!Double.isFinite(base) || Math.abs(base) > BASE_LIMIT) return Double.NaN;
        return base;
    }

    /**
     * 基础值 → 它会产生的最终值（不做 sanitize）。
     * <p>
     * 「恢复默认」用它把默认基础值换算成一个目标最终值，这样整条链路仍然只有
     * 「写最终值」一种语义，不必给网络包再加一个模式开关。
     */
    public static double finalForBase(AttributeInstance instance, double base) {
        if (instance == null) return Double.NaN;
        return (base + addition(instance)) * factor(instance);
    }
}
