package com.infinitestats.mixin;

import com.atsuishio.superbwarfare.capability.energy.VehicleEnergyStorage;
import com.infinitestats.compat.SuperbWarfareCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Superb Warfare（卓越前线）联动「无限能源」—— 不让载具扣电。
 * <p>
 * 落点选在<b>电量存储</b>的 {@code extractEnergy(int, boolean)} 上，而不是载具的
 * {@code consumeEnergy(int)}：1.20.1 主线扫过字节码，扣电有两条路 ——
 * <ol>
 *   <li>载具自己的入口 {@code consumeEnergy(int)}：引擎行驶、部分载具武器、炮塔 AI；</li>
 *   <li>按能量能力来扣：能量弹药策略（{@code GunProp.MAX_EXTRACT_ENERGY} 就是给它用的）、外部机器抽电。</li>
 * </ol>
 * 两条最终都会走到 {@code VehicleEnergyStorage.extractEnergy(...)} —— 它同时是载具内部读数 / 扣电的
 * 对象，也是载具对外暴露的能量能力载体。拦这一处即全覆盖且<b>不挑来源</b>；
 * 只拦 {@code consumeEnergy} 会漏掉能量弹药那条。
 * <p>
 * 返回的是「本该抽到的量」（{@code min(想抽的量, 当前电量)}）而不是 0 —— 调用方拿它当
 * 「这一次抽电成功了」，于是载具自己的「够不够电」判断继续放行，电量本身却一点没少。
 * <p>
 * <b>1.21.1 警示</b>：卓越前线在 1.21.1 上换用了 NeoForge 的能力体系，这个类名 / 包路径可能已经变了。
 * 注入点写成可选（{@code require = 0}）：对不上时「无限能源」的载具那半退化成空操作，
 * 其余功能与 {@code EnergyCompat} 的通用补满都不受影响。
 * <p>
 * 本 Mixin 只在装了卓越前线时才会被应用（{@code InfiniteStatsMixinPlugin} 按目标包名判定）。
 */
@Mixin(value = VehicleEnergyStorage.class, remap = false)
public abstract class SbwVehicleEnergyStorageMixin {

    /**
     * 抽取电量：解锁「无限能源」时原样返回「本该抽到的量」，电量一点不动。
     * <p>
     * 兼容层返回 {@code null} 表示「不拦截」（属性没解锁 / 不是卓越前线的载具 / 版本对不上），
     * 此时照常执行原逻辑。
     */
    @Inject(method = "extractEnergy", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void infinitestats$keepEnergyFull(int maxExtract, boolean simulate, CallbackInfoReturnable<Integer> cir) {
        Integer kept = SuperbWarfareCompat.interceptVehicleExtract(this, maxExtract);
        if (kept != null) {
            cir.setReturnValue(kept);
        }
    }
}
