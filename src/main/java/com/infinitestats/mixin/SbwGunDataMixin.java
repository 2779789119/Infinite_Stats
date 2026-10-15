package com.infinitestats.mixin;

import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.data.gun.ShootParameters;
import com.infinitestats.compat.CompatToggles;
import com.infinitestats.compat.SuperbWarfareCompat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * Superb Warfare（卓越前线）联动的弹药 / 热量的<b>统一收口</b>。
 * <p>
 * 落点（1.20.1 主线已用 {@code javap} 反汇编逐个核对）：
 * <ol>
 *   <li><b>{@code countBackupAmmo(Entity)} + {@code consumeBackupAmmo(Entity, int)}</b>：
 *       真正让「备弹无限」生效的一对 —— 前者报出无限备弹（所有弹药策略的口径），后者把实扣那一步整个取消；</li>
 *   <li><b>{@code hasInfiniteBackupAmmo(Entity)}</b>：置 true。<b>注意它只是界面与脚本语义</b> ——
 *       反汇编核过 {@code GunData} 自己从不调用它，全模组只有 HUD 与脚本代理读它。
 *       早先只挂这一条，所以 HUD 显示「∞」但弹药照样被扣、载具机炮还得手动装弹；</li>
 *   <li><b>{@code shoot(...)} 的几个入口</b>：开火收尾时补满弹匣 / 清热量。为什么选这里当主收口：
 *       这几个重载都是 {@code public final} 的<b>薄包装</b>，内部直接 {@code item.shoot(this, ...)}，
 *       方向是 {@code GunData.shoot → GunItem.shoot}（反过来一条都没有）；因此返回时整条开火链路
 *       已经跑完，<b>不挑枪型</b> —— 连自己覆写了 {@code shoot} / {@code afterShoot}、
 *       且覆写里不回 {@code super} 的那几把特殊枪也覆盖（挂在 {@code GunItem} 上做不到这点）。
 *       <b>载具武器也走 {@code shoot(ShootParameters)} 重载</b>；</li>
 *   <li><b>{@code shakePlayers(Entity)}</b>：载具开火的镜头抖动。无后坐力解锁时整条取消 ——
 *       炮口后坐状态与枪模 / HUD 动画照常，只让玩家镜头不再抖。为什么不在客户端拦那个震屏包：
 *       同一个包也被爆炸复用、包里没有来源标记，拦了会把爆炸震屏一起吞掉。</li>
 * </ol>
 * 补弹 / 清热量都是幂等的（弹匣满了不写、热量为 0 不写），所以多层收口叠加不会互相打架。
 * <p>
 * 本 Mixin 只在装了卓越前线时才会被应用（{@code InfiniteStatsMixinPlugin} 按目标包名判定），
 * 注入点都可选（{@code require = 0}）；import 的目标类来自编译期存根 {@code src/compatApiStub}。
 */
@Mixin(value = GunData.class, remap = false)
public abstract class SbwGunDataMixin {

    /** 无限子弹（备弹侧·界面语义）：把这个玩家的备弹判定直接置为「无限」。 */
    @Inject(method = "hasInfiniteBackupAmmo", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void infinitestats$infiniteAmmo(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (CompatToggles.isActiveForShooter(entity, "infinite_ammo")) {
            cir.setReturnValue(true);
        }
    }

    /** 「无限子弹」时报告给模组的备弹数：够它算就行，不必是 {@code Integer.MAX_VALUE}。 */
    private static final int INFINITE_BACKUP_AMMO = 9999;

    /**
     * 无限子弹（备弹侧·<b>真正生效的那一份</b>）：把「还有多少备弹」直接报成无限。
     * <p>
     * {@code countBackupAmmo} 把物品弹药 / 背包弹药 / 能量 / 经验 / 饥饿 / 生命各类策略汇总成一个数，
     * 射击前够不够、装填取多少都看它 —— 从这里改返回值等于「备弹无限」对<b>所有</b>弹药类型成立。
     */
    @Inject(
            method = "countBackupAmmo(Lnet/minecraft/world/entity/Entity;)I",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private void infinitestats$infiniteBackupAmmo(Entity entity, CallbackInfoReturnable<Integer> cir) {
        if (CompatToggles.isActiveForShooter(entity, "infinite_ammo")) {
            cir.setReturnValue(INFINITE_BACKUP_AMMO);
        }
    }

    /** 无限子弹（消耗侧）：真的扣备弹这一步整个取消（唯一的实扣入口）。 */
    @Inject(
            method = "consumeBackupAmmo(Lnet/minecraft/world/entity/Entity;I)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private void infinitestats$skipBackupAmmoConsumption(Entity entity, int amount, CallbackInfo ci) {
        if (CompatToggles.isActiveForShooter(entity, "infinite_ammo")) {
            ci.cancel();
        }
    }

    /** 无后坐力（载具侧）：拦掉载具开火的镜头抖动。 */
    @Inject(method = "shakePlayers", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void infinitestats$noRecoilShake(Entity entity, CallbackInfo ci) {
        if (CompatToggles.isActiveForShooter(entity, "no_recoil")) {
            ci.cancel();
        }
    }

    /** 开火入口（玩家开火的网络包 / AI 用枪）：收尾时补满弹匣、清热量。 */
    @Inject(
            method = "shoot(Lnet/minecraft/world/entity/Entity;DZLjava/util/UUID;)V",
            at = @At("RETURN"),
            require = 0,
            remap = false
    )
    private void infinitestats$afterShoot(Entity entity, double spread, boolean zoom, UUID targetEntityUUID,
                                          CallbackInfo ci) {
        SuperbWarfareCompat.onGunFired(entity, this);
    }

    /** 开火入口（同上，多带目标点的制导 / 锁定类武器）：收尾时补满弹匣、清热量。 */
    @Inject(
            method = "shoot(Lnet/minecraft/world/entity/Entity;DZLjava/util/UUID;Lnet/minecraft/world/phys/Vec3;)V",
            at = @At("RETURN"),
            require = 0,
            remap = false
    )
    private void infinitestats$afterShootTargeted(Entity entity, double spread, boolean zoom, UUID targetEntityUUID,
                                                  Vec3 targetPos, CallbackInfo ci) {
        SuperbWarfareCompat.onGunFired(entity, this);
    }

    /** 开火入口（载具 / 直接调 {@code data.shoot(params)} 的路径）：射手在参数包里，交给兼容层反射取。 */
    @Inject(
            method = "shoot(Lcom/atsuishio/superbwarfare/data/gun/ShootParameters;)V",
            at = @At("RETURN"),
            require = 0,
            remap = false
    )
    private void infinitestats$afterShootParams(ShootParameters parameters, CallbackInfo ci) {
        SuperbWarfareCompat.onShootParams(parameters);
    }
}
