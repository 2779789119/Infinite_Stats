package com.atsuishio.superbwarfare.data.gun;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * <b>Superb Warfare（卓越前线）的编译期存根 —— 运行时永远不会用到这个类。</b>
 * <p>
 * 用途与维护约定同 {@code com.simibubi.create.content.kinetics.KineticNetwork} 的说明：
 * 该模组对本模组只是「装了才生效」的可选运行时联动，但 {@code SbwGunDataMixin} 的注入点需要
 * 这些符号在编译期存在。只列被注入 / 被调用的成员。
 * <p>
 * 1.20.1 主线已用 {@code javap} 对 {@code superbwarfare-0.8.9.2} 核对过；
 * 本分支尚未拿到卓越前线 1.21.1 的 jar 复核，所有注入点都写成可选（{@code require = 0}），
 * 签名不符时只让这条联动退化为原版行为。
 */
public class GunData {

    /** 是否拥有无限备弹（界面上显示「∞」的那条语义）。 */
    public boolean hasInfiniteBackupAmmo(Entity entity) {
        throw new UnsupportedOperationException("compile-time stub");
    }

    /** 开火（玩家 / AI 用枪：网络包与射击目标都走这几个重载）。 */
    public final void shoot(Entity entity, double spread, boolean zoom, UUID targetEntityUUID) {
        throw new UnsupportedOperationException("compile-time stub");
    }

    /** 开火（带目标点，制导 / 锁定类武器）。 */
    public final void shoot(Entity entity, double spread, boolean zoom, UUID targetEntityUUID, Vec3 targetPos) {
        throw new UnsupportedOperationException("compile-time stub");
    }

    /** 开火（载具武器等直接传参数包的路径）。 */
    public final void shoot(ShootParameters parameters) {
        throw new UnsupportedOperationException("compile-time stub");
    }

    /** 载具开火的镜头抖动（发给附近玩家的震屏包）。 */
    public final void shakePlayers(Entity entity) {
        throw new UnsupportedOperationException("compile-time stub");
    }

    /** 汇总各类弹药策略后「还有多少备弹」。 */
    public final int countBackupAmmo(Entity entity) {
        throw new UnsupportedOperationException("compile-time stub");
    }

    /** 真正扣备弹的唯一入口。 */
    public final void consumeBackupAmmo(Entity entity, int amount) {
        throw new UnsupportedOperationException("compile-time stub");
    }
}
