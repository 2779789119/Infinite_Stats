package com.infinitestats.util;

import net.minecraft.nbt.CompoundTag;

/**
 * 附魔等级的 NBT 读写（突破原版上限的关键一环，供 Mixin 与物品编辑器共用）。
 * <p>
 * 原版这一对方法本身就是两道硬瓶颈：
 * <ul>
 *   <li>{@code EnchantmentHelper.storeEnchantment} 与 {@code setEnchantmentLevel} 都写
 *       {@code putShort("lvl", (short) level)} —— <b>超过 32767 直接截断</b>；</li>
 *   <li>{@code getEnchantmentLevel(CompoundTag)} 读回来还要 {@code Mth.clamp(..., 0, 255)} ——
 *       <b>超过 255 一律当 255</b>。</li>
 * </ul>
 * 所以「附魔上限突破」不只是把上限判断放开，还得把这两处也接管（见
 * {@code mixin/EnchantmentHelperMixin}）。
 * <p>
 * <b>写入时刻意优先沿用 short：</b>等级能塞进 short 就照原版写 short，
 * 这样不突破的存档 NBT 与其它模组看到的完全一致（它们是按 {@code getInt} 读的，两种都能吃）；
 * 只有真的超过 32767 才写 int。
 */
public final class EnchantLevelStore {

    /** 原版附魔等级字段名。 */
    public static final String TAG_LEVEL = "lvl";

    private EnchantLevelStore() {}

    /** 读等级：不夹到 255，负数按 0（与原版下限一致），short / int 两种写法都能读。 */
    public static int read(CompoundTag tag) {
        return Math.max(0, tag.getInt(TAG_LEVEL));
    }

    /** 写等级：能塞进 short 就用 short（保持 NBT 兼容），超出才用 int。 */
    public static void write(CompoundTag tag, int level) {
        if (level >= Short.MIN_VALUE && level <= Short.MAX_VALUE) {
            tag.putShort(TAG_LEVEL, (short) level);
        } else {
            tag.putInt(TAG_LEVEL, level);
        }
    }
}
