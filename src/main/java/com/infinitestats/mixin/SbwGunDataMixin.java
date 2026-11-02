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
 *   <li><b>{@code countBackupAmmo(Entity)} + {@code consumeBackupAmmo(Entity, int)}</b>：
 *       真正让「备弹无限」生效的一对 —— 前者报出无限备弹（所有弹药策略的口径），
 *       后者把实扣那一步整个取消。</li>
 *   <li><b>{@code hasInfiniteBackupAmmo(Entity)}</b>：置 true（创造模式 / 创造弹药盒 / 弹药策略的
 *       「无限」语义）。<b>注意它只是界面与脚本语义</b> —— 反汇编核对过 {@code GunData} 自己从不调用它，
 *       全模组只有 HUD（{@code AmmoBarOverlay}）与脚本代理（{@code GunDataProxy}）读它。
 *       早先只挂这一条，所以 HUD 显示「∞」但弹药照样被扣、载具机炮还得手动装弹。</li>
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

    /** 「无限子弹」时报告给模组的备弹数：够它算就行，不必是 {@code Integer.MAX_VALUE}。 */
    private static final int INFINITE_BACKUP_AMMO = 9999;

    /**
     * 无限子弹（备弹侧·**真正生效的那一份**）：把「还有多少备弹」直接报成无限。
     * <p>
     * 为什么不是挂在 {@code hasInfiniteBackupAmmo} 上就够了：反汇编核对过，
     * <b>{@code GunData} 自己从不调用 {@code hasInfiniteBackupAmmo}</b> ——
     * 全模组只有 HUD（{@code AmmoBarOverlay}）与脚本代理（{@code GunDataProxy}）读它，
     * 也就是说那条只让界面显示「∞」，扣弹药这一步照样发生。
     * <p>
     * 真正问「还有多少备弹」的是这里 —— {@code countBackupAmmo(Entity)} 把
     * 物品弹药 / 玩家背包弹药 / 能量 / 经验 / 饥饿 / 生命各类策略汇总成一个数，
     * 射击前够不够（{@code hasEnoughAmmoToShoot}）、装填取多少（{@code reloadAmmo}）都看它。
     * 所以从这里改返回值，等于「备弹无限」这件事对<b>所有</b>弹药类型成立 ——
     * 载具机炮那种「吃 {@code small_shell_*} 物品、弹匣容量为 0」的武器也就不用再手动装弹了。
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

    /**
     * 无限子弹（消耗侧）：真的扣备弹这一步整个取消。
     * <p>
     * {@code consumeBackupAmmo(Entity, int)} 内部是 {@code AmmoConsumer.consume(...)} → 对应弹药策略
     * （物品 / 背包 / 能量 / 经验 / 饥饿 / 生命），是唯一的实扣入口；取消它，弹药就一点不少，
     * 也不会出现「必须先带弹药」的前置。装填（{@code reloadAmmo}）不受影响 —— 它先问
     * {@link #infinitestats$infiniteBackupAmmo} 拿到无限备弹，再把弹匣填满。
     * <p>
     * 与弹匣补满的分工：这里保证「备弹不吃」，弹匣那几处保证「匣里恒满」，两边一起才是完整的无限子弹。
     */
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
