package com.atsuishio.superbwarfare.item.gun;

import com.atsuishio.superbwarfare.data.gun.ShootParameters;

/**
 * <b>Superb Warfare 的编译期存根（stub）—— 运行时永远不会用到这个类。</b>
 * <p>
 * 与另外几份存根同一用途（见 {@code com.tacz.guns.item.ModernKineticGunScriptAPI} 的长篇说明）。
 * <p>
 * 这里是枪械基类（Kotlin 抽象类）。本模组的「零热量」在 {@link #shoot} 的 RETURN 处把
 * {@code heat} / {@code overHeat} 清零 —— 已用 {@code javap} 确认：这个方法内部会读
 * {@code GunProp.HEAT_PER_SHOOT} 累加热量，而 {@code canShoot} 又在同一处检查它是否会过热。
 * <p>
 * 维护约定：签名必须与 Superb Warfare 0.8.x 实际实现一致（已对着 0.8.9.2 的 jar 核对；
 * 真实实现里还有好几个 {@code shoot} 重载，这里只列被注入的那个）。
 */
public class GunItem {

    /** 每次开火的主流程（热量在这里累加）。 */
    public void shoot(ShootParameters params) {
        throw new UnsupportedOperationException("compile-time stub");
    }

    /**
     * 开火后的收尾（扣备弹、跨步 / 拉栓等状态推进）。
     * <p>
     * 「无限子弹」的补弹注入点挂在这里与 {@link #shoot} 的 RETURN 上 —— 两个都挂是为了不依赖
     * 二者的先后顺序（扣弹发生在哪一段都不影响结果）。
     */
    public void afterShoot(ShootParameters params) {
        throw new UnsupportedOperationException("compile-time stub");
    }
}
