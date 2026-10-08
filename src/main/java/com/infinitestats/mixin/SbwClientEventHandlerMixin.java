package com.infinitestats.mixin;

import com.atsuishio.superbwarfare.event.ClientEventHandler;
import com.infinitestats.compat.CompatToggles;
import com.infinitestats.compat.SuperbWarfareCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.client.event.ViewportEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Superb Warfare（卓越前线）联动「无后坐力」—— 开火与瞄准时的镜头都不再动。
 * <p>
 * 为什么不是「整段取消 {@code handleGunRecoil}」（本 Mixin 早先就是这么干的，问题就出在这儿）：
 * 用 {@code javap} 反汇编逐条看过，这个方法<b>同时干两件事</b> ——
 * <ol>
 *   <li><b>衰减后坐力状态</b>：{@code recoilHorizon} / {@code recoilY} 的写入就在它里面
 *       （全类只有它写这两个量）；而枪模渲染器（{@code Aa12ItemModel}、{@code SentinelItemRenderer}）
 *       以及相机的 {@code handleWeaponFire} 都在<b>读</b>这些量 —— 整段取消会让它们停在上一次开火的值上不衰减；</li>
 *   <li><b>把偏移施加到玩家视角</b>：{@code LocalPlayer#setYRot}（水平）与 {@code #setXRot}（垂直上跳）各一处。</li>
 * </ol>
 * 所以这里改用 {@code @Redirect} 只吞掉第 2 件事的两处写入，第 1 件（状态衰减）照常跑 ——
 * 视角不动、内部状态却是干净的。
 * <p>
 * 同理处理 {@code handleWeaponBreathSway}（呼吸 / 瞄准晃动，每帧用 {@code RandomSource} 往 pitch / yaw 上加
 * <b>随机</b>偏移，屏息或架脚架才会减弱）：它才是「镜头一直抖」的正主，而属性描述里承诺的是
 * 「弹着点自己控制」，所以这两处写入也一起吞掉；方法本身照常跑（它还在算 {@code swayTime} 之类的状态量）。
 * <p>
 * 第三处是开火时的相机偏移：{@code handleWeaponFire} 每帧把 {@code cameraRot[2]} 往开火目标值 lerp，
 * {@code handlePlayerCamera} 再把它加到相机角度上 —— 这一段交给 {@link SuperbWarfareCompat#zeroFireCameraOffset()}
 * 每帧清零（反射写对方的静态数组，取不到就静默跳过）。
 * <p>
 * 为什么不用事件：这个模组的相机逻辑本身就是 Forge 事件处理器（{@code computeCameraAngles} 等），
 * 各段之间共享一批静态状态（{@code fireRecoilTime} / {@code recoilForce} / {@code turnRot}…）——
 * 在事件层把角度事后掰回来，既压不住它同一帧内对玩家朝向的写入，也容易和别的改视角模组打架。
 * <p>
 * 保留不动的部分（它们不是后坐力，属于该模组的手感 / 其它机制）：枪模自身的开火动画
 * （{@code firePosZ} / {@code fireRotTimer}）、枪身随视角转动的跟随（{@code turnRot}，枪模动画也在读它）、
 * 准星散布（{@code fireSpread}）、换弹抖动（{@code handleReloadShake}）。
 * <p>
 * 本 Mixin 在 mixin 配置的 {@code client} 列表里（目标类是纯客户端类），且只在装了 Superb Warfare 时应用；
 * 注入点可选（{@code require = 0}）；import 的目标类来自编译期存根 {@code src/compatApiStub}。
 */
@Mixin(value = ClientEventHandler.class, remap = false)
public abstract class SbwClientEventHandlerMixin {

    // ========== 后坐力（handleGunRecoil）：只吞视角写入 ==========

    /** 水平后坐力（左右偏）：无后坐力生效时不写到玩家朝向里。 */
    @Redirect(
            method = "handleGunRecoil",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;m_146922_(F)V"),
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
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;m_146926_(F)V"),
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
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;m_146922_(F)V"),
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
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;m_146926_(F)V"),
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
     * 放在这里（读这一侧）而不是写在 {@code handleWeaponFire} 里，是为了不依赖同一帧内这两个方法的先后顺序 ——
     * 无论谁先跑，相机读到的都是 0。
     */
    @Inject(method = "handlePlayerCamera", at = @At("HEAD"), require = 0, remap = false)
    private void infinitestats$skipFireCameraOffset(ViewportEvent.ComputeCameraAngles event, CallbackInfo ci) {
        if (CompatToggles.noRecoil(Minecraft.getInstance().player)) {
            SuperbWarfareCompat.zeroFireCameraOffset();
        }
    }
}
