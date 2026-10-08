package com.infinitestats.mixin;

import com.infinitestats.compat.CreateStressCompat;
import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Create（机械动力）联动「无限应力」—— 让<b>自己的</b>动力网络永远不算超载。
 * <p>
 * 落点选在 {@code KineticNetwork.calculateCapacity()} 的 <b>RETURN</b> 上，即「产能已经算完、
 * 还没交给别人」的那一瞬间。为什么是这里（已用 {@code javap} 对 {@code create-1.20.1-6.0.8} 核对）：
 * <ul>
 *   <li>它是整个网络产能的<b>唯一汇总点</b>：{@code updateCapacity()} 与 {@code updateNetwork()}
 *       都取它的结果，之后这条数字会同时流向——成员方块的 {@code capacity} 字段
 *       （{@code getSpeed()} 据此决定要不要停机）、应力表（{@code StressGaugeBlockEntity}
 *       的指针位置就是 {@code 耗能 / 产能}）、以及发给客户端的同步包
 *       （{@code write(tag, true)} 里写的 {@code Capacity}，客户端 {@code read} 会用它重算超载判定）；</li>
 *   <li>因此只改这一处，服务端、客户端、应力表、护目镜就会得到<b>同一个</b>「不过载」的结论 ——
 *       若改成只拦服务端的超载判定（例如把超载布尔量掰成 false），客户端仍会按真实数字自行
 *       算出「超载」，表现为机器在地上转、屏幕上不动。</li>
 * </ul>
 * 「谁的机器」这个口径由 {@link CreateStressCompat} 回答：网络里<b>任意一块</b>方块属于当前在线且
 * 已解锁的玩家，整张网络即视为生效 —— 动力网络是一整套连在一起的传动结构，一部分转、一部分停
 * 会直接穿帮，整网一起生效才与其他机器表现一致。
 * <p>
 * 改写之前先把真实产能交给 {@code CreateStressCompat} 记住：存档必须落真值，否则「无限产能」会被
 * 烤进存档（属性关掉、甚至卸载本模组之后机器仍然不过载）。存档侧的还原见
 * {@code CreateKineticBlockEntityMixin}。
 * <p>
 * 本 Mixin 只在装了机械动力时才会被应用（{@code InfiniteStatsMixinPlugin} 按目标包名判定），
 * 注入点可选（{@code require = 0}）；import 的目标类来自编译期存根 {@code src/compatApiStub}。
 */
@Mixin(value = KineticNetwork.class, remap = false)
public abstract class CreateKineticNetworkMixin {

    /**
     * 产能汇总的出口：解锁者的网络一律报「无限产能」。
     * <p>
     * {@code members} 同时装着动力源与耗能成员（{@code add} / {@code addSilently} 都会往里放），
     * 所以定位维度、判定归属都只看它一份。
     */
    @Inject(method = "calculateCapacity", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private void infinitestats$infiniteStress(CallbackInfoReturnable<Float> cir) {
        KineticNetwork network = (KineticNetwork) (Object) this;

        // 先记账：真实产能要在改写之前取，存档还原时还要用
        CreateStressCompat.rememberRealCapacity(network, cir.getReturnValueF());

        // 快速短路：没装机械动力以外的情形（没有解锁者在线）连方块位置都不必收集
        ServerLevel level = infinitestats$networkLevel(network);
        if (level == null || !CreateStressCompat.hasAnyUnlocked(level)) return;

        List<BlockPos> positions = new ArrayList<>(network.members.size());
        for (KineticBlockEntity be : network.members.keySet()) {
            if (be.getLevel() == level) {
                positions.add(be.getBlockPos());
            }
        }

        if (CreateStressCompat.isEmpowered(level, positions)) {
            cir.setReturnValue(CreateStressCompat.INFINITE_CAPACITY);
        }
    }

    /** 网络所在维度：取第一个已加载成员所在的 {@code ServerLevel}（网络不会跨维度）。 */
    @Unique
    private static ServerLevel infinitestats$networkLevel(KineticNetwork network) {
        for (KineticBlockEntity be : network.members.keySet()) {
            if (be.getLevel() instanceof ServerLevel level) {
                return level;
            }
        }
        return null;
    }
}
