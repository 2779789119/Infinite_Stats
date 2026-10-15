package com.infinitestats.mixin;

import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import com.infinitestats.util.EnchantLimits;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 铁砧消耗接管（两条属性都落在这里）：
 * <ul>
 *   <li>{@code enchant_limit}「附魔上限突破」：放开合成时的等级钳制；</li>
 *   <li>{@code anvil_cost}「铁砧经验减免」：按比例降低最终等级消耗，并同时取消「过于昂贵」。</li>
 * </ul>
 * <p>
 * 为什么必须走 Mixin：NeoForge 的 {@code AnvilUpdateEvent#setCost} 只在<b>同时设置了 output</b> 时
 * 才被采用（内部走 {@code setMaximumCost}），否则原版会继续用自己算出来的 {@code cost}；
 * 而且该事件在附魔消耗计算<b>之前</b>触发，参数只是基础修理费，连最终值都看不到。
 * 原版把「过于昂贵」门槛硬编码为 40，也没有任何事件可以拦截，所以只能直接改字节码。
 * <p>
 * 三处注入点（已用 {@code javap}/{@code -sources.jar} 对着 1.21.1 的 {@code AnvilMenu#createResult} 核对）：
 * <ul>
 *   <li>附魔等级钳制：{@code createResult} 里唯一一处 {@code Enchantment#getMaxLevel()}；</li>
 *   <li>「过于昂贵」门槛：{@code createResult} 里第 2 次 {@code DataSlot#get()}（{@code this.cost.get() >= 40}）；</li>
 *   <li>最终写入消耗：{@code createResult} 里唯一一处 {@code Mth.clamp(long,long,long)}
 *       （{@code (int) Mth.clamp(j + (long) i, 0L, Integer.MAX_VALUE)}；原版随后把它写进 {@code cost}）。</li>
 * </ul>
 * 门槛这里刻意<b>不用 {@code @ModifyConstant} 改那个常量 40</b>：Apotheosis 一类模组很可能也在同一常量上
 * 做 {@code @ModifyConstant}，同优先级下 Mixin 会判为冲突并跳过我们的注入。改成 {@code @Redirect}
 * 掉门槛比较里的取数指令后，注入目标与任何 {@code @ModifyConstant} 都不同。
 * <p>
 * <b>全部注入点都是「可选」的（{@code injectors.defaultRequire = 0}）</b>：AnvilMenu 是大包里的热门
 * 改写目标，任何一处锚点被别人挪走都不该让整个游戏起不来 —— 注入失败时 Mixin 只打印一条警告，
 * 对应功能退化为原版行为，其余部分照常工作。
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {

    /** 经验减免最多打到 -90%（避免出现 0 级消耗）。 */
    private static final float MAX_DISCOUNT = 0.9f;

    /** 原版「过于昂贵」门槛（硬编码 40）。 */
    private static final int TOO_EXPENSIVE = 40;

    /**
     * 「附魔上限突破」：原版在这里把结果等级夹回 {@code getMaxLevel()}，
     * 换成「该玩家的上限」（未解锁 = 整合包上限，解锁 = 不限）。
     */
    @Redirect(
            method = "createResult",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/enchantment/Enchantment;getMaxLevel()I"))
    private int infinitestats$enchantMaxLevel(Enchantment enchantment) {
        return EnchantLimits.maxLevel(infinitestats$player(), enchantment);
    }

    /**
     * 取消「过于昂贵」：原版在 {@code cost >= 40} 时把结果槽清空（生存模式），
     * 这里把那次比较读到的值夹到 39 以下，于是门槛永远不成立 —— 消耗数字本身照常显示，
     * 由下面的 {@link #infinitestats$discountCost} 与属性面板的减免共同决定实际花费。
     */
    @Redirect(
            method = "createResult",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/inventory/DataSlot;get()I",
                    ordinal = 1))
    private int infinitestats$neverTooExpensive(DataSlot slot) {
        return Math.min(slot.get(), TOO_EXPENSIVE - 1);
    }

    /**
     * 「铁砧经验减免」：把最终写进 {@code cost} 的数字按属性打折
     * （每点 -5%，最多 -90%）。减免发生在上面的门槛判定<b>之前</b>，
     * 所以它同时也降低了触发「过于昂贵」的概率。
     */
    @Redirect(
            method = "createResult",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(JJJ)J"))
    private long infinitestats$discountCost(long value, long min, long max) {
        long clamped = Mth.clamp(value, min, max);
        Player player = infinitestats$player();
        if (player == null) return clamped;

        float rate = infinitestats$anvilCostRate(player);
        if (rate <= 0f) return clamped;

        double factor = 1.0 - Math.min(MAX_DISCOUNT, rate);
        return Math.max(0L, (long) Math.floor(clamped * factor));
    }

    /** 当前菜单的玩家（{@code player} 声明在父类 {@code ItemCombinerMenu} 上，走访问器拿）。 */
    private Player infinitestats$player() {
        return ((ItemCombinerMenuAccessor) (Object) this).infinitestats$getPlayer();
    }

    private static float infinitestats$anvilCostRate(Player player) {
        PlayerStats stats = PlayerStatsProvider.of(player).orElse(null);
        return stats == null ? 0f : stats.getStatValue("anvil_cost");
    }
}
