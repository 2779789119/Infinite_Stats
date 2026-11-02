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
 * {@code consumeEnergy(int)}：已用 {@code javap} 扫过整合包里的那个 jar，
 * 扣电有两条路 ——
 * <ul>
 *   <li>载具自己的入口 {@code VehicleEntity.consumeEnergy(int)}：引擎行驶
 *       （{@code VehicleEngineUtils}）、部分载具武器、炮塔 AI；</li>
 *   <li>按 Forge Energy 能力来扣：能量弹药策略 {@code EnergyAmmoStrategy}
 *       （{@code GunProp.MAX_EXTRACT_ENERGY} 就是给它用的）、外部机器抽电。</li>
 * </ul>
 * 而这两条最终都会走到 {@code VehicleEnergyStorage.extractEnergy(...)} ——
 * 它同时是载具内部读数 / 扣电的对象，也是载具 {@code ForgeCapabilities.ENERGY} 能力暴露出去的对象。
 * 拦这一处即全覆盖，且<b>不挑来源</b>；只拦 {@code consumeEnergy} 会漏掉能量弹药那条。
 * <p>
 * 返回的是「本该抽到的量」（{@code min(想抽的量, 当前电量)}）而不是 0 ——
 * 调用方拿它当「这一次抽电成功了」，于是载具自己的「够不够电」判断
 * （{@code canConsume} / 弹药策略）继续放行，电量本身却一点没少。
 * <p>
 * 判定口径与另外三条枪械属性一致（{@code CompatToggles.isActiveForShooter}）：
 * <b>该载具的乘员里有解锁者</b>即生效。拿不到载具（版本对不上）时这个注入点变成空操作，
 * 不会影响载具本身的供电 / 充电。
 * <p>
 * 本 Mixin 只在装了 Superb Warfare 时才会被应用（{@code InfiniteStatsMixinPlugin} 按目标包名判定），
 * 注入点可选（{@code require = 0}）；import 的目标类来自编译期存根 {@code src/compatApiStub}。
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
