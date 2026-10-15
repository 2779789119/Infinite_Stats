package com.infinitestats.util;

import com.infinitestats.compat.ApotheosisEnchantCompat;

import com.infinitestats.stats.PlayerStatsProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * 「附魔上限突破」属性：开启后附魔等级不再受上限约束。
 * <p>
 * 上限按需求直接取 {@link #UNLIMITED}（{@link Integer#MAX_VALUE}）：实际能点到多高由<b>价格</b>决定
 * （进阶高级附魔台按等级线性收费、铁砧每次合成只 +1），所以不需要另一套人为封顶。
 * <p>
 * <b>刻意不改 {@code Enchantment#getMaxLevel()} 本身</b>：那个方法被原版到处用
 * （附魔台随机抽选的权重、书本上限、其它模组的判定…），全局抬高会连带改变一堆行为，
 * 而且没有玩家上下文、无从按人区分。这里改成只在<b>该改的那几个点</b>上放宽：
 * <ul>
 *   <li>进阶高级附魔台的自选等级上限；</li>
 *   <li>铁砧合成时的等级钳制（见 {@code mixin/AnvilMenuMixin}）；</li>
 *   <li>OP 指令 {@code /infstats enchant}。</li>
 * </ul>
 * <p>
 * <b>1.21.1 与 1.20.1 的差别</b>：1.21 起附魔等级存在 {@code ItemEnchantments} 里、内存里是
 * {@code int}，所以 1.20.1 那个「读时夹 255」的钳制点不复存在；但<b>存档用的 codec 仍写着
 * {@code intRange(0, 255)}</b>，超过 255 的等级在序列化时会被拒 —— 这一环由
 * {@code mixin/ItemEnchantmentsMixin} 放开（等价于 1.20.1 的
 * {@code EnchantmentHelperMixin}，只是落点从「NBT 读写」换到了「codec 构造」）。
 * <p>
 * 附魔的 {@code max_level} 在 1.21 是数据包驱动的字段，装了会改写它的模组（如 Apotheosis）时，
 * 它<b>不一定</b>覆盖到我们自己这几处调用点，所以 {@link #packCap} 会显式问一次
 * {@code compat/ApotheosisEnchantCompat}。
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
        return PlayerStatsProvider.of(player)
                .map(stats -> stats.isToggleActive(STAT_ID))
                .orElse(false);
    }

    /**
     * 整合包里「这个附魔实际能到几级」—— 未解锁突破时的上限就是它。
     * <p>
     * 先问 Apotheosis 的静态钩子 {@code EnchHooks#getMaxLevel}（见
     * {@code compat/ApotheosisEnchantCompat}）：装了它时，它配置里的 {@code Max Level} 才是整合包的
     * 实际规则（例如锋利 9）；问不到（没装 / 解析失败返回 -1）再回退原版 {@code getMaxLevel()}。
     * <p>
     * 为什么要显式问一次：1.21 起 {@code max_level} 是数据包驱动的字段，Apotheosis 的注入会改写
     * <b>一部分</b>调用点，而我们这几处（进阶高级附魔台、随身铁砧、OP 指令）是直接从自己的代码里调的
     * —— 不显式问就会出现「铁砧能合到锋利 9、我们自己的附魔台却只让选到 5」这种同包内自相矛盾的行为。
     */
    public static int packCap(Enchantment enchantment) {
        if (enchantment == null) return 0;
        int apotheosis = ApotheosisEnchantCompat.maxLevel(enchantment);
        if (apotheosis > 0) return apotheosis;
        return enchantment.getMaxLevel();
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
