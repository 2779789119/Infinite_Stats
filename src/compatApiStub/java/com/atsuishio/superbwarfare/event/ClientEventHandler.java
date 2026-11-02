package com.atsuishio.superbwarfare.event;

/**
 * <b>Superb Warfare 的编译期存根（stub）—— 运行时永远不会用到这个类。</b>
 * <p>
 * 与另外几份存根同一用途（见 {@code com.tacz.guns.item.GunItem} 的长篇说明）。
 * <p>
 * 这里是客户端的相机 / 视角处理器（Kotlin 类，事件方法都是<b>实例</b>方法）。
 * 本模组的「无后坐力」注入它三个方法：
 * <ul>
 *   <li>{@code handleGunRecoil()} —— 每帧的后坐力：<b>只</b>吞掉它对 {@code LocalPlayer} 的
 *       {@code setYRot} / {@code setXRot} 两处写入，方法本身照常跑（它同时负责 {@code recoilHorizon} /
 *       {@code recoilY} 的衰减，整段取消会让枪模渲染器读到不衰减的残留值 —— 早先版本就是这么错的）；</li>
 *   <li>{@code handleWeaponBreathSway(RenderTickEvent)} —— 呼吸 / 瞄准晃动，每帧往 pitch / yaw 上加
 *       {@code RandomSource} 的<b>随机</b>偏移（「镜头一直抖」的正主）：同样只吞那两处写入；</li>
 *   <li>{@code handlePlayerCamera(ComputeCameraAngles)} —— 把 {@code cameraRot} / {@code turnRot}
 *       加到相机角度上；这里在 HEAD 处把开火造成的 {@code cameraRot[2]} 清零
 *       （{@code cameraRot} 是 {@code public static double[]}，走反射写，见
 *       {@code SuperbWarfareCompat#zeroFireCameraOffset()}，取不到就静默跳过）。</li>
 * </ul>
 * <p>
 * 维护约定：签名必须与 Superb Warfare 0.8.x 实际实现一致（已对着 0.8.9.2 的 jar 用 {@code javap} 核对；
 * 真实实现里这些方法都是 {@code private/public final void}，本存根保持同名同描述符）。
 */
public class ClientEventHandler {

    /** 每帧施加枪械后坐力，并衰减后坐力状态量。 */
    private void handleGunRecoil() {
        throw new UnsupportedOperationException("compile-time stub");
    }

    /** 每渲染帧计算呼吸 / 瞄准晃动（会直接改 {@code LocalPlayer} 的朝向）。 */
    public final void handleWeaponBreathSway(net.minecraftforge.event.TickEvent.RenderTickEvent event) {
        throw new UnsupportedOperationException("compile-time stub");
    }

    /** 每帧把相机相关的静态偏移量施加到相机角度上。 */
    private void handlePlayerCamera(net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles event) {
        throw new UnsupportedOperationException("compile-time stub");
    }
}
