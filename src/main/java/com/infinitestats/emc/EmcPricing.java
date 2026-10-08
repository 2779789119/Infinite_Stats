package com.infinitestats.emc;

import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * EMC 买卖定价 —— 把属性加成统一套在基础 EMC 值上（EMC 系统与属性面板的联动点）。
 *
 * <ul>
 *   <li>{@code emc_learn_bonus}「EMC 学习收益」：学习 / 卖出物品得到的 EMC × (1 + 加成)</li>
 *   <li>{@code emc_extract_discount}「EMC 转化折扣」：提取 / 以 EMC 合成支付的价格 × (1 - 折扣)</li>
 * </ul>
 *
 * 基础值分别取自 {@link EmcDatabase#getSellValue}（含转化损耗率）与 {@link EmcDatabase#getEmc}，
 * 因此装了 ProjectE 时依旧以 ProjectE 的 EMC 值为准，属性只在其上做增减。
 */
public final class EmcPricing {

    /** 属性：学习收益加成（每点 +5%）。 */
    public static final String STAT_LEARN_BONUS = "emc_learn_bonus";
    /** 属性：转化折扣（每点 -6%）。 */
    public static final String STAT_EXTRACT_DISCOUNT = "emc_extract_discount";

    /** 折扣最多只打到原价的 5%，避免出现 0 价物品。 */
    private static final float MAX_DISCOUNT = 0.95f;

    private EmcPricing() {
    }

    /** 学习 / 卖出物品可得的 EMC（含转化损耗率与「EMC 学习收益」加成）。 */
    public static long sellValue(Player player, ItemStack stack, int count) {
        long base = EmcDatabase.getSellValue(stack, count);
        if (base <= 0) return 0;
        float bonus = statValue(player, STAT_LEARN_BONUS);
        if (bonus <= 0f) return base;
        return scale(base, 1.0 + bonus);
    }

    /** 提取 / 以 EMC 合成时，单个物品需要支付的 EMC（含「EMC 转化折扣」）。 */
    public static long buyPrice(Player player, ItemStack stack) {
        long base = EmcDatabase.getEmc(stack);
        if (base <= 0) return 0;
        double factor = buyFactor(player);
        if (factor >= 1.0) return base;
        long price = (long) Math.floor(base * factor);
        return Math.max(1L, price);
    }

    /**
     * 支付系数（1 - 折扣）。
     * 客户端界面用它把「同步来的基础价」折算成实际支付价，两端算法保持一致。
     */
    public static double buyFactor(Player player) {
        float discount = statValue(player, STAT_EXTRACT_DISCOUNT);
        if (discount <= 0f) return 1.0;
        if (discount > MAX_DISCOUNT) discount = MAX_DISCOUNT;
        return 1.0 - discount;
    }

    /** 读取玩家属性（无能力 / 客户端无数据时按 0 处理）。 */
    private static float statValue(Player player, String statId) {
        if (player == null) return 0f;
        PlayerStats stats = player.getCapability(PlayerStatsProvider.PLAYER_STATS).orElse(null);
        return stats == null ? 0f : stats.getStatValue(statId);
    }

    private static long scale(long value, double factor) {
        double v = value * factor;
        if (v >= Long.MAX_VALUE) return Long.MAX_VALUE;
        return (long) Math.floor(v);
    }
}
