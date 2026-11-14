package com.infinitestats.mixin;

import com.infinitestats.client.PanelScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 给面板界面提供一个「一帧里最后画」的钩子：{@link PanelScreen#renderTopLayer}。
 *
 * <h2>为什么需要它</h2>
 * 原版浮窗（控件 {@code Tooltip}、物品提示）不是界面自己画的 —— {@code AbstractWidget} 只把提示
 * 交给 {@code Screen#setTooltipForNextRenderPass}，由 {@link Screen#renderWithTooltip} 在
 * {@code render()} <b>返回之后</b>再补画一次。也就是说「原版浮窗」天然享有「最后一道工序」的特权。
 * <p>
 * 自绘浮层只能画在自己的 {@code render()} 里：即使放在覆盖层（最后一个绘制回调）也仍然早于
 * 上面那次延迟绘制，而且还可能被本帧更晚提交的批次（列表文字、控件文字、裁剪区切换前的几何）
 * 反压上来 —— 表现就是「浮层的底色盖不住底下的文字」。
 * <p>
 * 所以这里在 {@code renderWithTooltip} 的末尾（延迟提示也画完之后）回调面板界面，
 * 让它把需要「绝对置顶」的自绘浮层（例如成就管理器的模组下拉列表）画在这里。
 */
@Mixin(Screen.class)
public abstract class ScreenTopLayerMixin {

    @Inject(method = "renderWithTooltip", at = @At("TAIL"))
    private void infinitestats$renderTopLayer(GuiGraphics g, int mouseX, int mouseY,
                                              float partialTick, CallbackInfo ci) {
        // PanelScreen 的 render / renderScaleBadge 都是 final，唯一的扩展点就是这个回调
        if ((Object) this instanceof PanelScreen panel) {
            panel.renderTopLayer(g, mouseX, mouseY, partialTick);
        }
    }
}
