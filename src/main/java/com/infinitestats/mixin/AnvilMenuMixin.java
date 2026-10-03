package com.infinitestats.mixin;

import com.infinitestats.crafting.PortableAnvilMenu;
import com.infinitestats.stats.PlayerStatsProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 铁砧消耗接管。
 * <p>
 * 为什么必须走 Mixin：Forge 的 {@code AnvilUpdateEvent#setCost} 只在**同时设置了 output** 时
 * 才被 {@code ForgeHooks.onAnvilChange} 采用（内部调 {@code setMaximumCost}），否则原版会继续用
 * 自己算出来的 {@code this.cost}；而且该事件在附魔消耗计算**之前**触发，参数只是基础修理费，
 * 连最终值都看不到。原版把「过于昂贵」门槛硬编码为 40，也没有任何事件可以拦截，
 * 所以只能直接改字节码。
 * <p>
 * 两处注入的锚点（均已用 {@code javap} 对字节码核对）：
 * <ul>
 *   <li>最终写入消耗：{@code createResult} 内第 6 次 {@code DataSlot.set(I)}（{@code this.cost.set(j + i)}，序号 5）；</li>
 *   <li>「过于昂贵」门槛：{@code createResult} 内第 2 次 {@code DataSlot.get()}（序号 1）。</li>
 * </ul>
 * 门槛这里**刻意不用 {@code @ModifyConstant} 改那个常量 40**：Apotheosis 的
 * {@code apotheosis.mixins.json:AnvilMenuMixin#apoth_removeLevelCap} 也在同一常量上做
 * {@code @ModifyConstant}，同优先级下 Mixin 会判为冲突并跳过我们的注入，配合配置里的
 * {@code require = 1} 直接变成 {@code Critical injection failure} —— 实测就是这样崩在启动阶段的。
 * 改成 {@code @Redirect} 掉门槛比较里的取数指令后，注入目标与任何 {@code @ModifyConstant} 都不同，
 * 可以和 Apotheosis 共存。
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {

    /** 随身铁砧的经验收费封顶 */
    private static final int PORTABLE_MAX_COST = 50;

    /** 只读访问父类 {@code ItemCombinerMenu} 的玩家字段（见 {@link ItemCombinerMenuAccessor}） */
    private Player infinitestats$player() {
        return ((ItemCombinerMenuAccessor) (Object) this).infinitestats$getPlayer();
    }

    /**
     * 改写「最终消耗」写入数据槽的值。
     * <p>
     * 此处仍在「过于昂贵」判定之前，因此 {@code anvil_cost} 的减免能真正把高消耗操作压到门槛以下，
     * 界面显示的数值与实际扣费也必然一致（同一个值）。
     * 只在随身铁砧上额外封顶 —— 原版铁砧保持自由的减免行为。
     */
    @ModifyArg(
            method = "createResult",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/inventory/DataSlot;set(I)V",
                    ordinal = 5),
            index = 0)
    private int infinitestats$adjustFinalCost(int cost) {
        if (cost <= 0) return cost;

        int adjusted = cost;
        float rate = infinitestats$anvilCostRate();
        if (rate > 0.0F) {
            adjusted = Math.max(1, (int) Math.ceil(cost * (1.0F - rate)));
        }
        if (infinitestats$isPortable()) {
            adjusted = Math.min(adjusted, PORTABLE_MAX_COST);
        }
        return adjusted;
    }

    /**
     * 拔掉「过于昂贵」门槛：原版在 {@code cost >= 40} 且非创造时会清空结果槽，
     * 随身铁砧改为永不触发（收费由上面封顶兜底）。
     * <p>
     * 这里拦的是门槛比较里的取数指令（{@code createResult} 内第 2 次 {@code DataSlot.get()}），
     * 随身铁砧直接返回 {@code Integer.MIN_VALUE} —— 于是无论门槛被原版还是被别的模组
     * （例如 Apotheosis 的 {@code apoth_removeLevelCap}）改成多少，比较恒为 false。
     */
    @Redirect(
            method = "createResult",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/inventory/DataSlot;get()I",
                    ordinal = 1))
    private int infinitestats$liftPortableGate(DataSlot slot) {
        int cost = slot.get();
        return infinitestats$isPortable() ? Integer.MIN_VALUE : cost;
    }

    private boolean infinitestats$isPortable() {
        return ((Object) this) instanceof PortableAnvilMenu;
    }

    private float infinitestats$anvilCostRate() {
        Player player = infinitestats$player();
        if (player == null) return 0.0F;
        return player.getCapability(PlayerStatsProvider.PLAYER_STATS)
                .map(stats -> Math.min(0.9F, Math.max(0.0F, stats.getStatValue("anvil_cost"))))
                .orElse(0.0F);
    }
}
