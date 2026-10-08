package com.infinitestats.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * 附魔名的等级后缀（供界面与 {@code mixin/EnchantmentMixin} 共用）。
 * <p>
 * 原版 {@code Enchantment#getFullname} 直接拼 {@code Component.translatable("enchantment.level." + level)}，
 * 而原版语言文件里**只有 1~10 的译文**：等级 11 以上会原样显示成 {@code enchantment.level.11} 这种键名。
 * 原版靠 {@code getMaxLevel()} 挡着所以见不到，突破上限后这就是常态，必须自己补：
 * 1~10 继续用原版译文（跟随客户端语言），11~100 用罗马数字（与原版风格一致），再往上用阿拉伯数字
 * （罗马数字到这个量级会长得没法看）。
 */
public final class EnchantText {

    /** 罗马数字的上限：再往上改用阿拉伯数字。 */
    private static final int MAX_ROMAN = 100;

    private EnchantText() {}

    /** 完整附魔名（含等级），与 {@code Enchantment#getFullname} 同格式同配色。 */
    public static Component name(Enchantment enchantment, int level) {
        MutableComponent name = Component.translatable(enchantment.getDescriptionId());
        name.withStyle(enchantment.isCurse() ? ChatFormatting.RED : ChatFormatting.GRAY);
        if (level != 1 || enchantment.getMaxLevel() != 1) {
            name.append(" ").append(levelSuffix(level));
        }
        return name;
    }

    /** 等级后缀文本（不带空格）。 */
    public static String levelSuffix(int level) {
        if (level >= 1 && level <= 10) {
            // 原版译文（客户端语言，例如中文是 I~X 的罗马数字）
            return Component.translatable("enchantment.level." + level).getString();
        }
        if (level <= MAX_ROMAN) return roman(level);
        return String.valueOf(level);
    }

    /** 阿拉伯数字 → 罗马数字（1~100 够用；越界时退回阿拉伯数字）。 */
    private static String roman(int value) {
        if (value <= 0 || value > MAX_ROMAN) return String.valueOf(value);
        int[] values = {100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] symbols = {"C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder builder = new StringBuilder();
        int rest = value;
        for (int i = 0; i < values.length; i++) {
            while (rest >= values[i]) {
                builder.append(symbols[i]);
                rest -= values[i];
            }
        }
        return builder.toString();
    }
}
