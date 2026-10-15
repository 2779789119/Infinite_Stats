package com.infinitestats.mixin;

import com.atsuishio.superbwarfare.event.ClientEventHandler;
import com.infinitestats.compat.CompatToggles;
import com.infinitestats.compat.SuperbWarfareCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Superb Warfare（卓越前线）联动「无后坐力」—— 开火与瞄准时的镜头都不再动。
 * <p>
 * 为什么不是「整段取消 {@code handleGunRecoil}」：1.20.1 主线反汇编逐条看过，这个方法
 * <b>同时干两件事</b> ——
 * <ol>
 *   <li><b>衰减后坐力状态</b>：{@code recoilHorizon} / {@code recoilY} 的写入就在它里面
 *       （全类只有它写这两个量），而枪模渲染器与相机的 {@code handleWeaponFire} 都在<b>读</b>它们 ——
 *       整段取消会让枪模停在上一次开火的值上不衰减；</li>
 *   <li><b>把偏移施加到玩家视角</b>：{@code LocalPlayer#setYRot}（水平）与 {@code #setXRot}（垂直上跳）。</li>
 * </ol>
 * 所以这里用 {@code @Redirect} 只吞掉第 2 件事的两处写入，第 1 件（状态衰减）照常跑 ——
 * 视角不动、内部状态却是干净的。
 * <p>
 * 同理处理 {@code handleWeaponBreathSway}（呼吸 / 瞄准晃动，每帧往 pitch / yaw 上加<b>随机</b>偏移，
 * 它才是「镜头一直抖」的正主）：两处写入一起吞掉，方法本身照常跑（它还在算 {@code swayTime}
 * 之类的状态量）。
 * <p>
 * 第三处是开火时的相机偏移：{@code handleWeaponFire} 每帧把 {@code cameraRot[2]} 往目标值 lerp，
 * {@code handlePlayerCamera} 再把它加到相机角度上 —— 这一段交给
 * {@link SuperbWarfareCompat#zeroFireCameraOffset()} 每帧清零。放在「读」这一侧是为了不依赖
 * 同一帧内两个方法的先后顺序：无论谁先跑，相机读到的都是 0。
 * <p>
 * 为什么不走事件：该模组的相机逻辑本身就是事件处理器，各段之间共享一批静态状态
 * （{@code fireRecoilTime} / {@code recoilForce} / {@code turnRot}…）—— 在事件层把角度事后掰回来，
 * 既压不住它同一帧内对玩家朝向的写入，也容易和别的改视角模组打架。
 * <p>
 * 保留不动（不是后坐力，属于该模组的手感 / 其它机制）：枪模自身的开火动画、枪身随视角转动、
 * 准星散布、换弹抖动。
 * <p>
 * <b>1.21.1 适配（重要）</b>：注入目标里 {@code LocalPlayer} 的朝向方法从 SRG 名
 * （{@code m_146922_} / {@code m_146926_}）改成 <b>official 名</b>（{@code setYRot} / {@code setXRot}）
 * —— NeoForge 运行在 official 映射上，方法名不再需要经 refmap 转换。
 * 本 Mixin 在 mixin 配置的 {@code client} 列表里（目标类是纯客户端类）；注入点可选
 * （{@code require = 0}）。
 */
@Mixin(value = ClientEventHandler.class, remap = false)
public abstract class SbwClientEventHandlerMixin {

    // ========== 后坐力（handleGunRecoil）：只吞视角写入 ==========

    /** 水平后坐力（左右偏）：无后坐力生效时不写到玩家朝向里。 */
    @Redirect(
            method = "handleGunRecoil",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;setYRot(F)V"),
            require = 0,
            remap = false
    )
    private void infinitestats$skipRecoilYaw(LocalPlayer player, float yaw) {
        if (!CompatToggles.noRecoil(player)) {
            player.setYRot(yaw);
        }
    }

    /** 垂直后坐力（视角被往上顶）：无后坐力生效时不写到玩家朝向里。 */
    @Redirect(
            method = "handleGunRecoil",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;setXRot(F)V"),
            require = 0,
            remap = false
    )
    private void infinitestats$skipRecoilPitch(LocalPlayer player, float pitch) {
        if (!CompatToggles.noRecoil(player)) {
            player.setXRot(pitch);
        }
    }

    // ========== 呼吸 / 瞄准晃动（handleWeaponBreathSway）：同上 ==========

    /** 呼吸晃动的水平分量（随机抖动）：无后坐力生效时不写到玩家朝向里。 */
    @Redirect(
            method = "handleWeaponBreathSway",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;setYRot(F)V"),
            require = 0,
            remap = false
    )
    private void infinitestats$skipSwayYaw(LocalPlayer player, float yaw) {
        if (!CompatToggles.noRecoil(player)) {
            player.setYRot(yaw);
        }
    }

    /** 呼吸晃动的垂直分量（随机抖动）：无后坐力生效时不写到玩家朝向里。 */
    @Redirect(
            method = "handleWeaponBreathSway",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;setXRot(F)V"),
            require = 0,
            remap = false
    )
    private void infinitestats$skipSwayPitch(LocalPlayer player, float pitch) {
        if (!CompatToggles.noRecoil(player)) {
            player.setXRot(pitch);
        }
    }

    // ========== 开火时的相机偏移（handlePlayerCamera）==========

    /**
     * 每帧在相机角度被算出来之前把开火造成的 {@code cameraRot[2]} 清零。
     * <p>
     * {@code handlePlayerCamera} 在 1.21.1 上仍是 private，参数是
     * {@code ViewportEvent.ComputeCameraAngles}（由公开的 {@code computeCameraAngles} 调用，已用
     * {@code javap} 核对）；参数类型照抄真实签名，避免依赖 Mixin 对回调参数的宽松转换。
     */
    @Inject(method = "handlePlayerCamera", at = @At("HEAD"), require = 0, remap = false)
    private void infinitestats$skipFireCameraOffset(
            net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles event, CallbackInfo ci) {
        if (CompatToggles.noRecoil(Minecraft.getInstance().player)) {
            SuperbWarfareCompat.zeroFireCameraOffset();
        }
    }
}
