package com.atsuishio.superbwarfare.data.gun;

/**
 * <b>Superb Warfare 的编译期存根（stub）—— 运行时永远不会用到这个类。</b>
 * <p>
 * 与 {@code com.tacz.guns.item.ModernKineticGunScriptAPI} 那份存根同一用途（见那里的长篇说明）：
 * Mixin 注解处理器要求目标类在<b>编译期</b>可解析，而 Superb Warfare 只是可选运行时联动。
 * <p>
 * 这里是枪械运行时数据类（Kotlin），只列出被注入的方法。
 * 本模组的「无限子弹」有两处落点：
 * <ul>
 *   <li>{@code hasInfiniteBackupAmmo} —— 取消式接管模组<b>自带</b>的无限弹药判定
 *       （创造模式、创造弹药盒、能量/经验等弹药策略最终都汇到它）；</li>
 *   <li>{@code shoot(...)} 的几个入口 —— 开火收尾时补满弹匣 / 清热量。
 *       它是<b>全枪型的统一收口</b>：这几个重载内部只是 {@code item.shoot(...)} 的薄包装
 *       （已用 {@code javap} 核对，方向是 {@code GunData.shoot → GunItem.shoot}），
 *       而外部调用者只有开火网络包 {@code ShootMessage}（玩家）与 {@code GunShootGoal}（AI）。
 *       因此连自己覆写了 {@code shoot} / {@code afterShoot} 的那几把特殊枪
 *       （Igla / Javelin / Bocek / Ql1031 / Sentinel / Taser，覆写里都不回 {@code super}）
 *       也照样覆盖。</li>
 * </ul>
 * <p>
 * 维护约定：签名必须与 Superb Warfare 0.8.x 实际实现一致
 * （已对着 {@code superbwarfare-0.8.9.2-mc1.20.1} 的 jar 用 {@code javap} 核对）。
 */
public class GunData {

    /**
     * 是否处于「无限备弹」状态。
     *
     * @param entity 持枪实体（原版签名就是带实体上下文的，因此可以按玩家判断）
     */
    public boolean hasInfiniteBackupAmmo(net.minecraft.world.entity.Entity entity) {
        throw new UnsupportedOperationException("compile-time stub");
    }

    /**
     * 开火入口之一：开火网络包 {@code ShootMessage} 与 AI 的 {@code GunShootGoal} 都调它。
     * <p>
     * 真实实现是薄包装 —— 内部直接 {@code item.shoot(this, entity, spread, zoom, uuid)}，
     * 所以它返回时整条开火链路（含各枪型自己的覆写）都已经跑完了。
     */
    public final void shoot(net.minecraft.world.entity.Entity entity, double spread, boolean zoom, java.util.UUID targetEntityUUID) {
        throw new UnsupportedOperationException("compile-time stub");
    }

    /** 同上，多带一个目标点（制导 / 锁定类武器用）。 */
    public final void shoot(net.minecraft.world.entity.Entity entity, double spread, boolean zoom, java.util.UUID targetEntityUUID, net.minecraft.world.phys.Vec3 targetPos) {
        throw new UnsupportedOperationException("compile-time stub");
    }

    /** 开火入口之一：载具 / 直接调 {@code data.shoot(params)} 的路径（{@code VehicleEntity} 等）。 */
    public final void shoot(ShootParameters parameters) {
        throw new UnsupportedOperationException("compile-time stub");
    }

    /**
     * 载具开火时的「震屏」派发：读 {@code GunProp.SHOOT_SHAKE} 后把 {@code ShakeClientMessage}
     * 发给附近玩家（全模组只有载具类调它）。参数是<b>载具实体</b>，所以要按乘员判定。
     */
    public final void shakePlayers(net.minecraft.world.entity.Entity entity) {
        throw new UnsupportedOperationException("compile-time stub");
    }

    /**
     * <b>「还有多少备弹」的唯一口径</b>：物品弹药 / 玩家背包弹药 / 能量 / 经验 / 饥饿 / 生命
     * 各类策略都由它汇总（射击前够不够、装填取多少也都问它），所以「无限子弹」在这里改返回值即可 —
     * 注意 {@code hasInfiniteBackupAmmo} 只被 HUD 与脚本代理解读，<b>不是</b>消耗的闸门。
     */
    public final int countBackupAmmo(net.minecraft.world.entity.Entity entity) {
        throw new UnsupportedOperationException("compile-time stub");
    }

    /**
     * 真正的扣备弹：内部走 {@code AmmoConsumer.consume(...)} → 对应弹药策略。
     * 「无限子弹」时整个取消，弹药就一点不少（也不会有「必须带弹药」的前置）。
     */
    public final void consumeBackupAmmo(net.minecraft.world.entity.Entity entity, int amount) {
        throw new UnsupportedOperationException("compile-time stub");
    }
}
