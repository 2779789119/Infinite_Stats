package com.tacz.guns.client.event;

/**
 * <b>TACZ 的编译期存根（stub）—— 运行时永远不会用到这个类。</b>
 * <p>
 * 与另外两份存根同一用途（见 {@code com.tacz.guns.item.ModernKineticGunScriptAPI} 的长篇说明）：
 * Mixin 注解处理器要求 {@code @Mixin} 的目标类在<b>编译期</b>可解析，而 TACZ 只是可选运行时联动。
 * <p>
 * 这里是后坐力那个类：开火时算曲线、每帧把玩家的 {@code setXRot/setYRot} 往后坐方向推。
 * 本模组「无后坐力」正是取消这两个方法（见 {@code mixin/TaczCameraRecoilMixin}）。
 * 参数一律写成 {@code Object}：注入处理器按<b>方法名</b>定位即可，不必把 TACZ 的
 * {@code GunFireEvent}、Forge 的 {@code ViewportEvent.ComputeCameraAngles} 也搬进存根。
 * <p>
 * 维护约定：方法名必须与 TACZ 1.1.x 实际实现一致（已对着 {@code tacz-1.20.1-1.1.8-hotfix2.jar} 核对）。
 */
public class CameraSetupEvent {

    /** 开火时初始化后坐力曲线（内部把当时的时间戳记下来，供每帧求值）。 */
    public static void initialCameraRecoil(Object event) {
        throw new UnsupportedOperationException("compile-time stub");
    }

    /** 每帧把后坐力曲线上的位移直接加到玩家的视角旋转上（就是「枪口上抬」）。 */
    public static void applyCameraRecoil(Object event) {
        throw new UnsupportedOperationException("compile-time stub");
    }
}
