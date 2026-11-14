package com.infinitestats.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 原版浮窗（物品提示、按钮 {@code Tooltip}、{@code renderTooltip} 各重载）的批次隔离。
 *
 * <h2>为什么必须在这里动手，而不是逐个界面改</h2>
 * {@link GuiGraphics} 不是「画一次提交一次」：顶点按 {@code RenderType} 分批，
 * <b>所有 {@code fill} 一批、所有文字一批</b>，最后统一提交。于是浮窗的<b>背景</b>会落进
 * 「所有背景」那批（排在界面上已有文字的前面），而界面上已有的列表 / 按钮文字在
 * 「所有文字」那批里 —— 结果就是<b>背景被盖住、文字亮晃晃地浮在浮窗上</b>。
 * <p>
 * 本模组自绘的 {@code EditorUi.tooltip} 已经自己前后 flush 过了，但原版这条路走的是
 * {@code GuiGraphics} 里的私有实现，界面代码插不进去：
 * <ul>
 *   <li>控件 {@code Tooltip}（{@code Button.tooltip} / {@code setTooltip}）根本不在控件里画 ——
 *       {@code AbstractWidget} 只是把提示丢给 {@code Screen#setTooltipForNextRenderPass}，
 *       由最终的 {@code Screen#renderWithTooltip} 在 {@code render()} 返回后再调一次
 *       {@code renderTooltip}，中间没有任何 flush；</li>
 *   <li>容器界面的物品提示（{@code AbstractContainerScreen} 子类自己调 {@code renderTooltip}）、
 *       以及 {@code Cloth Config} 这类第三方界面同样如此。</li>
 * </ul>
 * 而<b>所有</b> {@code renderTooltip} 重载（物品 / Component / 行列表 / 带
 * {@link ClientTooltipPositioner} 的延迟提示）最终都汇入
 * {@code renderTooltipInternal}，所以只在这一处前后 flush，全项目、全界面、包括别的模组
 * 的界面一起修好，不必每个界面各写一遍。
 *
 * <h2>为什么前后都要 flush</h2>
 * 画之前 flush：把已有内容（含列表文字）先落地，浮窗背景才不会被它们压住。
 * 画完再 flush：让浮窗自成一票，后续内容（例如延迟提示之后的收尾绘制）也压不到它。
 */
@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsTooltipMixin {

    @Inject(method = "renderTooltipInternal", at = @At("HEAD"))
    private void infinitestats$flushBeforeTooltip(CallbackInfo ci) {
        ((GuiGraphics) (Object) this).flush();
    }

    @Inject(method = "renderTooltipInternal", at = @At("RETURN"))
    private void infinitestats$flushAfterTooltip(Font font, List<ClientTooltipComponent> components,
                                                 int mouseX, int mouseY,
                                                 ClientTooltipPositioner positioner,
                                                 CallbackInfo ci) {
        ((GuiGraphics) (Object) this).flush();
    }
}
