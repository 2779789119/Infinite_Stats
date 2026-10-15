package com.infinitestats.mixin;

import com.atsuishio.superbwarfare.data.gun.ShootParameters;
import com.atsuishio.superbwarfare.item.gun.GunItem;
import com.infinitestats.compat.SuperbWarfareCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Superb Warfare（卓越前线）联动的<b>第二层收口</b>：补满弹匣 + 清零热量。
 * <p>
 * 主收口在 {@code GunData.shoot(...)}（见 {@code SbwGunDataMixin}，不挑枪型）；这里兜「绕过
 * {@code GunData.shoot(...)} 直接调 item」的路径。{@code shoot} 与 {@code afterShoot}
 * <b>两个都挂</b>：扣弹究竟发生在哪一段不影响结果，也不需猜它们的先后顺序。
 * <p>
 * 判开关与读写都交给 {@code compat/SuperbWarfareCompat}（{@code ShootParameters} 身上就有
 * {@code shooter} / {@code data} 两个 public final 字段）。方法名用完整描述符指定：
 * 这个类有好几个 {@code shoot} 重载，只写名字会命中歧义。
 * <p>
 * 本 Mixin 只在装了卓越前线时应用；注入点可选（{@code require = 0}）。
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
