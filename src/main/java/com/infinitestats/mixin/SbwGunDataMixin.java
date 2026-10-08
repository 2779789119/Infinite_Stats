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
 * 两处落点：
 * <ol>
 *   <li><b>{@code hasInfiniteBackupAmmo(Entity)}</b>：取消式接管模组<b>自带</b>的无限弹药判定 ——
 *       创造模式、创造模式弹药盒、能量/经验/饥饿等弹药策略、以及「附加来源」的检查最终都会问到它
 *       （已用 {@code javap} 核对签名：{@code public final boolean hasInfiniteBackupAmmo(Entity)}，
 *       带实体上下文，因此可以按玩家判断）。置 true 等于「这个人此刻处于创造模式持枪」，
 *       保证射击永远不被弹药卡住、换弹也不消耗任何真实弹药。</li>
 *   <li><b>{@code shoot(...)} 的几个入口</b>：开火收尾时补满弹匣 / 清热量。
 *       为什么选这里当主收口 —— 用 {@code javap} 反汇编核过：
 *       <ul>
 *         <li>这几个 {@code GunData.shoot(...)} 重载都是 {@code public final} 的<b>薄包装</b>，
 *             内部直接 {@code item.shoot(this, ...)}，方向是 {@code GunData.shoot → GunItem.shoot}
 *             （{@code GunItem} 那边调 {@code data.shoot(...)} 的路径一条都没有）；</li>
 *         <li>它们的<b>外部</b>调用者只有开火网络包 {@code network/message/send/ShootMessage}（玩家开火）
 *             与 {@code entity/goal/GunShootGoal}（AI 用枪），载具那条走 {@code shoot(ShootParameters)}
 *             重载（{@code VehicleEntity} / {@code Tom6Entity}）；</li>
 *         <li>因此返回时整条开火链路已经跑完，<b>不挑枪型</b>：连自己覆写了 {@code shoot} /
 *             {@code afterShoot}、且覆写里不回 {@code super} 的那几把特殊枪
 *             （{@code Igla} / {@code Javelin} / {@code Bocek} / {@code Ql1031} / {@code Sentinel} /
 *             {@code Taser}）也照样覆盖 —— 挂在 {@code GunItem.shoot(ShootParameters)} 上就做不到这点。</li>
 *       </ul>
 *       补弹 / 清热量都做成幂等（弹匣满了不写、热量为 0 不写），所以多层收口叠加不会互相打架。
 *       <b>载具武器也走这个重载</b>（反汇编：{@code VehicleEntity} 的 {@code vehicleShoot} 里就是
 *       {@code data.shoot(params)}）—— 注意载具时 {@code ShootParameters.shooter} 是载具本体、
 *       炮手在 {@code ammoSupplier} 里，判定口径见 {@code SuperbWarfareCompat#onShootParams}。</li>
 *   <li><b>{@code shakePlayers(Entity)}</b>：载具开火的<b>镜头抖动</b>（{@code VehicleEntity.afterShoot}
 *       读 {@code GunProp.SHOOT_SHAKE} 后发给附近玩家）。无后坐力解锁时整条取消 ——
 *       炮口后坐状态与枪模 / HUD 动画照常，只让玩家镜头不再抖（与手持枪口径一致）。</li>
 * </ol>
 * 与 TACZ 那条的差别（README 里有说明）：这里同样是「弹匣恒满」，但走的是对方模组自己的
 * 容量属性与无限备弹通道，不去硬改它的换弹状态机。
 * <p>
 * 本 Mixin 只在装了 Superb Warfare 时才会被应用（{@code InfiniteStatsMixinPlugin} 按目标包名判定），
 * 注入点都可选（{@code require = 0}）；import 的目标类来自编译期存根 {@code src/compatApiStub}。
 */
@Mixin(value = GunData.class, remap = false)
public abstract class SbwGunDataMixin {

    /**
     * 无限子弹（备弹侧）：把这个玩家的备弹判定直接置为「无限」。
     * <p>
     * 传进来的实体有两种：手持枪械时是玩家本人；<b>载具武器时是载具实体</b>
     * （已核对 {@code VehicleEntity.getAmmoSupplier()} 就是 {@code return this}）——
     * 后者交给 {@link CompatToggles#isActiveForShooter(Entity, String)} 往乘员里找一个解锁者。
     */
    @Inject(method = "hasInfiniteBackupAmmo", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void infinitestats$infiniteAmmo(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (CompatToggles.isActiveForShooter(entity, "infinite_ammo")) {
            cir.setReturnValue(true);
        }
    }

    /**
     * 无后坐力（载具侧）：拦掉载具开火的<b>镜头抖动</b>。
     * <p>
     * 载具的这条链路和手持枪不同：{@code VehicleEntity.afterShoot(...)} 收尾时会调
     * {@code shakePlayers(载具)} —— 它读 {@code GunProp.SHOOT_SHAKE}，再发一个
     * {@code ShakeClientMessage} 给附近的玩家（反汇编扫描：全模组只有载具类调它，
     * 即 {@code VehicleEntity} 与 Annihilator / Mortar / Type63 / SodayoPickUpRocket）。
     * 无后坐力解锁时整条取消：炮口的后坐状态（{@code CANNON_RECOIL_FORCE} / {@code CANNON_RECOIL_TIME}、
     * 供枪模与 HUD 播放动画）照常，只让玩家的镜头不再抖 —— 与手持枪「留枪模动画、只压视角」口径一致。
     * <p>
     * 为什么不在客户端拦那个震屏包：同一个 {@code ShakeClientMessage} 也被爆炸
     * （{@code CustomExplosion} / {@code ParticleTool}）复用，包里没有来源标记，
     * 客户端拦会把爆炸震屏一起吞掉；而这里只针对「载具开火」这一条来源。
     */
    @Inject(method = "shakePlayers", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void infinitestats$noRecoilShake(Entity entity, CallbackInfo ci) {
        if (CompatToggles.isActiveForShooter(entity, "no_recoil")) {
            ci.cancel();
        }
    }

    /**
     * 开火入口（玩家开火的网络包 / AI 用枪都会走这个重载）：收尾时补满弹匣、清热量。
     * <p>
     * {@code this} 就是目标实例（{@code GunData} 本身），直接交给兼容层用，省掉一次反射取字段。
     * 射手是 AI 怪物时两条属性都不生效（它们是玩家属性）。
     */
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

    /**
     * 开火入口（载具 / 直接调 {@code data.shoot(params)} 的路径）：收尾时补满弹匣、清热量。
     * <p>
     * 这个重载拿不到「调用者」这个实参，射手在 {@code ShootParameters} 里，所以交给兼容层反射取
     * {@code shooter} / {@code data}（已核对这两个 public final 字段确实存在）。
     */
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
