package com.infinitestats.util;

import com.infinitestats.compat.ApotheosisEnchantCompat;
import com.infinitestats.stats.PlayerStatsProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * 「附魔上限突破」属性：开启后附魔等级不再受上限约束。
 * <p>
 * 上限按需求直接取 {@link #UNLIMITED}（{@link Integer#MAX_VALUE}）：实际能点到多高由<b>价格</b>决定
 * （进阶附魔台按等级线性收费、铁砧每次合成只 +1），所以不需要另一套人为封顶。
 * <p>
 * <b>刻意不改 {@code Enchantment#getMaxLevel()} 本身</b>：那个方法被原版到处用
 * （附魔台随机抽选的权重、书本上限、其它模组的判定…），全局抬高会连带改变一堆行为，
 * 而且没有玩家上下文、无从按人区分。这里改成只在<b>该改的那几个点</b>上放宽：
 * <ul>
 *   <li>进阶高级附魔台的自选等级上限；</li>
 *   <li>铁砧合成时的等级钳制（见 {@code mixin/AnvilMenuMixin}）;</li>
 *   <li>OP 指令 {@code /infstats enchant}。</li>
 * </ul>
 * <p>
 * <b>未解锁时的上限 = 整合包实际生效的上限</b>（{@link #packCap(Enchantment)}），不是写死的原版
 * {@code getMaxLevel()}：装了 Apotheosis 的包里由它的配置说了算（本整合包里锋利 9 级，原版 5 级），
 * 否则会出现「铁砧能合到 9、我们自己的附魔台只让选到 5」这种同包内自相矛盾的行为。
 */
public final class EnchantLimits {

    /** 「附魔上限突破」属性 id（1 点解锁的开关）。 */
    public static final String STAT_ID = "enchant_limit";

    /** 解锁后的等级上限：直接放开到 int 上限。 */
    public static final int UNLIMITED = Integer.MAX_VALUE;

    private EnchantLimits() {}

    /** 该玩家是否已解锁突破（属性被「功能开关」关掉时同样视为未解锁）。 */
    public static boolean isUnlocked(Player player) {
        if (player == null) return false;
        return player.getCapability(PlayerStatsProvider.PLAYER_STATS)
                .map(stats -> stats.isToggleActive(STAT_ID))
                .orElse(false);
    }

    /**
     * 整合包里「这个附魔实际能到几级」—— 未解锁突破时的上限就是它。
     * <p>
     * 装了 Apotheosis 且它的附魔模块开着时取它配置的上限（通常比原版高）；
     * 否则（未安装 / 读不到 / 模块关闭）就是原版 {@code getMaxLevel()}。
     */
    public static int packCap(Enchantment enchantment) {
        if (enchantment == null) return 0;
        int modded = ApotheosisEnchantCompat.maxLevel(enchantment);
        return modded > 0 ? modded : enchantment.getMaxLevel();
    }

    /** 该玩家能给的最高等级：未解锁 = 整合包上限，解锁 = 不限。 */
    public static int maxLevel(Player player, Enchantment enchantment) {
        if (enchantment == null) return 0;
        return isUnlocked(player) ? UNLIMITED : packCap(enchantment);
    }

    /** 把等级夹到合法区间（至少 1，最多该玩家的上限）。 */
    public static int clampLevel(Player player, Enchantment enchantment, int level) {
        return Math.max(1, Math.min(maxLevel(player, enchantment), level));
    }
}
