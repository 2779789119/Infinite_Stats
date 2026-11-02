package com.infinitestats.mixin;

import com.infinitestats.compat.CompatToggles;
import com.tacz.guns.client.event.CameraSetupEvent;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TACZ 联动「无后坐力」—— 开枪时视角不再被往上顶。
 * <p>
 * TACZ 的后坐力全在客户端的 {@code CameraSetupEvent} 里（已对着
 * {@code tacz-1.20.1-1.1.8-hotfix2.jar} 逐字节核对过方法名）：
 * <pre>
 * initialCameraRecoil(GunFireEvent)         开火瞬间按枪械数据 + 配件/瞄准/趴下修正算出
 *                                           pitch / yaw 两条后坐力曲线，记下开火时间戳
 * applyCameraRecoil(ComputeCameraAngles)    之后每一帧求值曲线，把增量直接减到
 *                                           player.setXRot / setYRot 上 ← 就是「枪口上抬」
 * </pre>
 * 注意它是改**玩家真实朝向**（客户端会把朝向发给服务端），不是只动渲染用的相机，
 * 所以这里必须把这两个方法一起取消；只取消其中一个都会留下瑕疵
 * （只取消每帧求值，仍会残留上一次曲线的状态）。
 * <p>
 * <b>为什么不用事件</b>：这段逻辑本身就是 Forge 事件处理器，而且改的是玩家旋转这一「状态」——
 * 事件层排在它后面只能事后把角度掰回来，会和其它改视角的模组互相打架；直接让这段逻辑不执行才是干净的。
 * <p>
 * 本 Mixin 在 mixin JSON 的 {@code client} 列表里（目标类是纯客户端类，服务端不会加载它），
 * 且只有在装了 TACZ 时才会被应用（{@code InfiniteStatsMixinPlugin} 按目标包名判定）。
 * 注入点可选（{@code require = 0}）；判开关走 {@link CompatToggles#noRecoil}，
 * 拿不到本地玩家（主菜单等）时按「未解锁」处理。
 * <p>
 * 这里 import 的 {@code CameraSetupEvent} 来自本仓库的编译期存根（{@code src/taczApiStub/java}，
 * 运行时永远不存在）。用到的是 TACZ 自己的成员名，因此 {@code remap = false}。
 */
@Mixin(value = CameraSetupEvent.class, remap = false)
public abstract class TaczCameraRecoilMixin {

    /** 开火时不再初始化后坐力曲线。 */
    @Inject(method = "initialCameraRecoil", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void infinitestats$skipRecoilSetup(CallbackInfo ci) {
        if (CompatToggles.noRecoil(Minecraft.getInstance().player)) {
            ci.cancel();
        }
    }

    /** 每帧不再把后坐力曲线施加到玩家视角上。 */
    @Inject(method = "applyCameraRecoil", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void infinitestats$skipRecoilApply(CallbackInfo ci) {
        if (CompatToggles.noRecoil(Minecraft.getInstance().player)) {
            ci.cancel();
        }
    }
}
