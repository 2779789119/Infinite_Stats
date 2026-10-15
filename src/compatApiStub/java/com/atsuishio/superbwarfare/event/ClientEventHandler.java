package com.atsuishio.superbwarfare.event;

/**
 * <b>Superb Warfare（卓越前线）的编译期存根 —— 运行时永远不会用到这个类。</b>
 * <p>
 * 这是<b>纯客户端类</b>（专用服务器上根本加载不了），所以 {@code SbwClientEventHandlerMixin}
 * 挂在 mixin 配置的 {@code client} 列表里。
 * <p>
 * 参数类型统一写成 {@code Object}：这个存根只用来让 {@code method = "方法名"} 能找到目标，
 * 注入点里面用的 {@code @Redirect} 目标是原版 {@code LocalPlayer}（与本类无关），
 * 所以不需要（也不该）照抄事件类的真实参数 —— 那些事件类型在 1.21 的 NeoForge 上换过一轮
 * （例如渲染 tick 事件），照抄反而会让存根编译不过。
 */
public class ClientEventHandler {

    /** 后坐力：衰减状态量 + 把偏移写进玩家朝向。 */
    private void handleGunRecoil() {
        throw new UnsupportedOperationException("compile-time stub");
    }

    /** 呼吸 / 瞄准晃动：每帧往 pitch / yaw 上加随机偏移。 */
    public void handleWeaponBreathSway(Object event) {
        throw new UnsupportedOperationException("compile-time stub");
    }

    /** 相机角度计算：把开火偏移加到相机上。 */
    private void handlePlayerCamera(Object event) {
        throw new UnsupportedOperationException("compile-time stub");
    }
}
