package com.infinitestats.mixin;

import com.atsuishio.superbwarfare.data.gun.ShootParameters;
import com.atsuishio.superbwarfare.item.gun.GunItem;
import com.infinitestats.compat.SuperbWarfareCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Superb Warfare（卓越前线）联动的开火注入点：补满弹匣 + 清零热量。
 * <p>
 * <b>零热量</b>：用 {@code javap} 扫过该类字节码，全类只有 {@code shoot(ShootParameters)} 读
 * {@code GunProp.HEAT_PER_SHOOT}（唯一的加热点，也是判定过热的地方），而热量 / 过热标记存在枪械 NBT 里
 * （{@code GunData.heat} 键 {@code "Heat"}、{@code GunData.overHeat} 键 {@code "OverHeat"}）——
 * 在开火流程返回时清掉，HUD 在同一 tick 内读到的就是 0，不会出现「先涨一下再掉回去」的闪动。
 * <p>
 * <b>无限子弹（弹匣恒满）</b>：只挂 {@code GunData.hasInfiniteBackupAmmo}（见 {@code SbwGunDataMixin}）
 * 会变成「能一直打但弹匣照常掉空、游戏仍要求换弹」，所以这里在开火流程返回时把弹匣写回容量上限
 * （容量取 {@code GunData.get(GunProp.MAGAZINE)}，是该模组自己的口径）。
 * <p>
 * {@code shoot} 与 {@code afterShoot} <b>两个都挂</b>：扣弹究竟发生在哪一段（主流程还是收尾）不影响结果，
 * 也不需要去猜它们的先后顺序；每 5 tick 的兜底（{@code SuperbWarfareCompat#onTick}）再补上
 * 「捡来的 / 属性解锁前就已经半匣」的枪。
 * <p>
 * 判开关与读写都交给 {@code compat/SuperbWarfareCompat}（反射；判开关需要射手，而
 * {@code ShootParameters} 身上就有 —— 已核对它的 {@code shooter} / {@code data} 两个 public final 字段）。
 * <p>
 * 方法名用完整描述符指定：这个类有好几个 {@code shoot} 重载，只写名字会命中歧义。
 * 本 Mixin 只在装了 Superb Warfare 时应用；注入点可选（{@code require = 0}）；
 * import 的目标类来自编译期存根 {@code src/compatApiStub}。
 */
@Mixin(value = GunItem.class, remap = false)
public abstract class SbwGunItemMixin {

    /** 开火主流程结束：补满弹匣 / 清掉这次开火攒下的热量与过热标记。 */
    @Inject(
            method = "shoot(Lcom/atsuishio/superbwarfare/data/gun/ShootParameters;)V",
            at = @At("RETURN"),
            require = 0,
            remap = false
    )
    private void infinitestats$afterShoot(ShootParameters params, CallbackInfo ci) {
        SuperbWarfareCompat.onShootParams(params);
    }

    /** 开火收尾结束：同上（两处都在，避免依赖 {@code shoot} 与 {@code afterShoot} 的先后顺序）。 */
    @Inject(
            method = "afterShoot(Lcom/atsuishio/superbwarfare/data/gun/ShootParameters;)V",
            at = @At("RETURN"),
            require = 0,
            remap = false
    )
    private void infinitestats$afterShootTail(ShootParameters params, CallbackInfo ci) {
        SuperbWarfareCompat.onShootParams(params);
    }
}
