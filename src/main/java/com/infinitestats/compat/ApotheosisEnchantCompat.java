package com.infinitestats.compat;

import net.minecraft.world.item.enchantment.Enchantment;

import java.lang.reflect.Method;

/**
 * Apotheosis 附魔模块的等级上限读取。
 * <p>
 * Apotheosis 的 coremod（{@code coremods/ench/ench_info_redirector.js}）会把原版若干类里的
 * {@code Enchantment#getMaxLevel()} 整段换成它自己的静态钩子
 * {@code dev.shadowsoffire.apotheosis.ench.asm.EnchHooks#getMaxLevel}。也就是说，装了 Apotheosis 的包里
 * 「这个附魔最多能到几级」由它的配置说了算 —— {@code config/apotheosis/enchantments.cfg} 里每个附魔的
 * {@code Max Level}（本来就比原版高，本整合包里锋利是 **9**，原版是 5），原版 {@code getMaxLevel()} 只是
 * 它关闭附魔模块时的回退值。它自己的钩子也是这么写的：
 * <pre>
 * getMaxLevel(ench) = Apotheosis.enableEnch ? EnchModule.getEnchInfo(ench).getMaxLevel() : ench.getMaxLevel()
 * </pre>
 * <p>
 * 本模组自己的那几处上限判断（进阶高级附魔台、随身铁砧、OP 指令）都走 {@code util/EnchantLimits}，
 * <b>未</b>解锁「附魔上限突破」时要跟整合包的实际规则对齐：否则会出现「铁砧能合到锋利 9、
 * 我们自己的附魔台却只让选到 5」这种同包内自相矛盾的行为。
 * <p>
 * 全程<b>反射</b>（和 {@code mixin/AnvilMenuMixin} 里那个针对 {@code EnchHooks} 的注入点同一套思路）：
 * 没装 Apotheosis 时连它的类都不会被加载，更不会往本模组的字节码里塞引用；读不到就返回 -1，
 * 由调用方回退到原版上限。
 */
public final class ApotheosisEnchantCompat {

    private static final String HOOKS_CLASS = "dev.shadowsoffire.apotheosis.ench.asm.EnchHooks";

    /** 反射句柄；{@code null} = 尚未解析。 */
    private static Method getMaxLevel;
    /** true = 已确认不可用（没装 Apotheosis 或解析失败），不再重复尝试。 */
    private static volatile boolean unavailable;

    private ApotheosisEnchantCompat() {}

    /**
     * Apotheosis 配置的该附魔上限。
     *
     * @return 上限值；未安装 Apotheosis / 读取失败 / 值不合法时返回 <b>-1</b>
     */
    public static int maxLevel(Enchantment enchantment) {
        if (enchantment == null) return -1;
        Method method = resolve();
        if (method == null) return -1;
        try {
            Object value = method.invoke(null, enchantment);
            return value instanceof Integer level && level > 0 ? level : -1;
        } catch (Throwable t) {
            return -1;
        }
    }

    /** 解析并缓存 {@code EnchHooks#getMaxLevel}；解析失败后永久回退（不再每次调用都抛一遍）。 */
    private static Method resolve() {
        if (unavailable) return null;
        Method method = getMaxLevel;
        if (method != null) return method;

        synchronized (ApotheosisEnchantCompat.class) {
            if (unavailable) return null;
            if (getMaxLevel != null) return getMaxLevel;
            try {
                getMaxLevel = Class.forName(HOOKS_CLASS).getMethod("getMaxLevel", Enchantment.class);
                return getMaxLevel;
            } catch (Throwable t) {
                unavailable = true;
                return null;
            }
        }
    }
}
