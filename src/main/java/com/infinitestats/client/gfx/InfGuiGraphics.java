package com.infinitestats.client.gfx;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 自绘 GUI 层（1.19.2 版）。
 *
 * <h2>为什么需要它</h2>
 * {@code net.minecraft.client.gui.GuiGraphics} 是 1.20 才有的类，1.19.2 的绘制入口是
 * {@code PoseStack} + 一堆 {@code GuiComponent.xxx(poseStack, ...)} 静态方法。本模组 164 个类
 * 全是按 1.20 的写法写的（{@code g.fill(...)} / {@code g.drawString(...)} / {@code g.renderItem(...)}），
 * 直接改成静态调用会牵动 400+ 处调用点。
 * <p>
 * 所以这里自己做一层「画笔」：内部只持有一个 {@link PoseStack}，把 1.20.1 {@code GuiGraphics}
 * 的那几个常用方法在 1.19.2 上重新实现一遍。界面代码只换 import 与参数类型，方法体不用动。
 *
 * <h2>和原版 GuiGraphics 的差异</h2>
 * <ul>
 *   <li>没有顶点批处理：每次 {@link #fill} 立即提交一次 draw call（1.19.2 的
 *       {@code GuiComponent.fill} 本来也是这么干的），开销与原版 1.19.2 界面一致；</li>
 *   <li>{@link #enableScissor} 收的是<b>屏幕坐标</b>：1.20 的版本会乘上当前 pose 的矩阵，
 *       1.19.2 只能自己换算成物理像素。本模组调用点（{@code PanelScreen}）都会先把 pose
 *       压回单位矩阵再调用，口径一致；</li>
 *   <li>{@link #renderItem} 需要把当前 pose 手动压进 modelview 栈：1.19.2 的
 *       {@code ItemRenderer.renderGuiItem} 没有 PoseStack 参数，位置完全取自
 *       {@code RenderSystem.getModelViewStack()}，不手动同步的话物品会画在未缩放的位置。</li>
 * </ul>
 *
 * <p>刻意不提供 {@code fillGradient / blitNineSliced / blitSprite}：本模组没有用到，
 * 真要用时按需在这里补，别把 1.20 的整份 API 搬过来。
 */
public final class InfGuiGraphics {

    private final PoseStack pose;
    /** 触发绘制的界面；tooltip 需要它（原版 tooltip 渲染是 Screen 的实例方法）。可为 null。 */
    private final Screen screen;

    public InfGuiGraphics(PoseStack pose, Screen screen) {
        this.pose = pose;
        this.screen = screen;
    }

    public PoseStack pose() {
        return this.pose;
    }

    // ======================== 基础矩形 ========================

    /** 实心矩形（1.20 语义：x2/y2 是右下角，不含）。 */
    public void fill(int x1, int y1, int x2, int y2, int color) {
        fill(x1, y1, x2, y2, 0, color);
    }

    /** 带 z 偏移的实心矩形。 */
    public void fill(int x1, int y1, int x2, int y2, int z, int color) {
        if (x1 == x2 || y1 == y2) return;
        // 1.19.2 的原版实现：内部走 Tesselator + POSITION_COLOR，不依赖任何贴图/着色器状态
        GuiComponent.fill(this.pose, x1, y1, x2, y2, color);
    }

    /**
     * 1 像素描边（1.20 的 {@code GuiGraphics.renderOutline}）。
     * <p>等价于画四条 1px 的矩形，没有额外状态。
     */
    public void renderOutline(int x, int y, int width, int height, int color) {
        fill(x, y, x + width, y + 1, color);
        fill(x, y + height - 1, x + width, y + height, color);
        fill(x, y + 1, x + 1, y + height - 1, color);
        fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    // ======================== 文本 ========================

    public int drawString(Font font, String text, int x, int y, int color) {
        return drawString(font, text, x, y, color, true);
    }

    public int drawString(Font font, String text, int x, int y, int color, boolean shadow) {
        if (text == null || text.isEmpty()) return x;
        if (shadow) {
            font.drawShadow(this.pose, text, (float) x, (float) y, color);
        } else {
            font.draw(this.pose, text, (float) x, (float) y, color);
        }
        return x + font.width(text);
    }

    public int drawString(Font font, Component text, int x, int y, int color) {
        return drawString(font, text, x, y, color, true);
    }

    public int drawString(Font font, Component text, int x, int y, int color, boolean shadow) {
        if (text == null) return x;
        if (shadow) {
            font.drawShadow(this.pose, text, (float) x, (float) y, color);
        } else {
            font.draw(this.pose, text, (float) x, (float) y, color);
        }
        return x + font.width(text);
    }

    /** 居中文本（1.20 的 x 是「中心点」）。 */
    public int drawCenteredString(Font font, String text, int x, int y, int color) {
        if (text == null) return x;
        GuiComponent.drawCenteredString(this.pose, font, text, x, y, color);
        return x + font.width(text) / 2;
    }

    public int drawCenteredString(Font font, Component text, int x, int y, int color) {
        if (text == null) return x;
        GuiComponent.drawCenteredString(this.pose, font, text, x, y, color);
        return x + font.width(text) / 2;
    }

    // ======================== 物品 ========================

    public void renderItem(ItemStack stack, int x, int y) {
        renderItem(stack, x, y, 0);
    }

    /** seed 在 1.19.2 的物品渲染里没有对应参数，忽略即可。 */
    public void renderItem(ItemStack stack, int x, int y, int seed) {
        if (stack == null || stack.isEmpty()) return;
        syncPoseToModelView(() -> Minecraft.getInstance().getItemRenderer().renderGuiItem(stack, x, y));
    }

    public void renderItemDecorations(Font font, ItemStack stack, int x, int y) {
        renderItemDecorations(font, stack, x, y, null);
    }

    public void renderItemDecorations(Font font, ItemStack stack, int x, int y, String text) {
        if (stack == null || stack.isEmpty()) return;
        syncPoseToModelView(() -> Minecraft.getInstance().getItemRenderer()
                .renderGuiItemDecorations(font, stack, x, y, text));
    }

    /**
     * 把当前 UI pose 压进 modelview 栈再执行物品绘制，结束后原样恢复。
     * <p>
     * 1.19.2 的 {@code ItemRenderer.renderGuiItem(ItemStack,int,int)} 没有 PoseStack 参数，
     * 它直接往 {@code RenderSystem.getModelViewStack()} 上压平移 —— 不手动同步的话，
     * 面板缩放 / 平移对物品完全不生效（图标会画在屏幕左上角）。
     */
    private void syncPoseToModelView(Runnable draw) {
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        try {
            modelView.mulPoseMatrix(this.pose.last().pose());
            RenderSystem.applyModelViewMatrix();
            draw.run();
        } finally {
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
        }
    }

    // ======================== 提示框 ========================

    public void renderTooltip(Font font, ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty() || this.screen == null) return;
        List<Component> lines = this.screen.getTooltipFromItem(stack);
        this.screen.renderTooltip(this.pose, lines, Optional.empty(), x, y, stack);
    }

    public void renderTooltip(Font font, Component text, int x, int y) {
        if (text == null || this.screen == null) return;
        this.screen.renderTooltip(this.pose, text, x, y);
    }

    public void renderTooltip(Font font, List<Component> lines, int x, int y) {
        if (lines == null || lines.isEmpty() || this.screen == null) return;
        this.screen.renderTooltip(this.pose, lines, Optional.empty(), x, y, font);
    }

    public void renderTooltip(Font font, List<Component> lines, Optional<TooltipComponent> data, int x, int y) {
        if (lines == null || lines.isEmpty() || this.screen == null) return;
        this.screen.renderTooltip(this.pose, lines, data, x, y, font);
    }

    public void renderComponentTooltip(Font font, List<Component> lines, int x, int y) {
        renderTooltip(font, lines, x, y);
    }

    /**
     * 直接给「已经折好行」的 {@link net.minecraft.util.FormattedCharSequence} 画浮窗。
     * <p>
     * 不能叫 {@code renderTooltip} 重载：{@code List<Component>} 与
     * {@code List<FormattedCharSequence>} 擦除后同为 {@code List}，Java 不允许同名重载。
     */
    public void renderTooltipLines(Font font, List<FormattedCharSequence> lines, int x, int y) {
        if (lines == null || lines.isEmpty() || this.screen == null) return;
        this.screen.renderTooltip(this.pose, lines, x, y);
    }

    /** 只给「没有 Screen 上下文」的调用点用：拿不到 tooltip 渲染器时干脆不画，也不炸。 */
    public static List<Component> emptyTooltip() {
        return Collections.emptyList();
    }

    // ======================== 贴图 ========================

    /** 1.20 语义：贴图尺寸按 256×256 处理。 */
    public void blit(ResourceLocation texture, int x, int y, int u, int v, int width, int height) {
        blit(texture, x, y, 0, (float) u, (float) v, width, height, 256, 256);
    }

    public void blit(ResourceLocation texture, int x, int y, int u, int v, int width, int height,
                     int textureWidth, int textureHeight) {
        blit(texture, x, y, 0, (float) u, (float) v, width, height, textureWidth, textureHeight);
    }

    public void blit(ResourceLocation texture, int x, int y, int z, float u, float v, int width, int height,
                     int textureWidth, int textureHeight) {
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.enableBlend();
        GuiComponent.blit(this.pose, x, y, z, u, v, width, height, textureWidth, textureHeight);
        RenderSystem.disableBlend();
    }

    /**
     * 「从图集里抠一块贴到任意大小的矩形上」——1.20 的
     * {@code blit(rl, x, y, width, height, u, v, uWidth, vHeight, texWidth, texHeight)}。
     * 本模组拿它画原版容器底图（34×17 的槽位底纹之类）。
     */
    public void blit(ResourceLocation texture, int x, int y, int width, int height,
                     float u, float v, int uWidth, int vHeight, int textureWidth, int textureHeight) {
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.enableBlend();
        GuiComponent.blit(this.pose, x, y, width, height, u, v, uWidth, vHeight, textureWidth, textureHeight);
        RenderSystem.disableBlend();
    }

    // ======================== 裁剪 ========================

    /**
     * 开裁剪区，参数是<b>屏幕坐标</b>（左上 / 右下）。
     * <p>
     * 1.20 的 {@code GuiGraphics.enableScissor} 会乘上当前 pose；1.19.2 没有这层封装，
     * 只能自己换算成 OpenGL 的物理像素（原点在左下角，所以 Y 要翻过来）。
     */
    public void enableScissor(int x1, int y1, int x2, int y2) {
        Window window = Minecraft.getInstance().getWindow();
        double scale = window.getGuiScale();
        int fbHeight = window.getHeight();
        int sx = (int) Math.round(x1 * scale);
        int sw = (int) Math.round((x2 - x1) * scale);
        int sh = (int) Math.round((y2 - y1) * scale);
        int sy = fbHeight - (int) Math.round(y2 * scale);
        if (sw <= 0 || sh <= 0) {
            // 空区域：给一个 0 尺寸的裁剪框，等价于「什么都不画」，避免负值把整屏剪掉
            RenderSystem.enableScissor(0, 0, 0, 0);
            return;
        }
        RenderSystem.enableScissor(sx, sy, sw, sh);
    }

    public void disableScissor() {
        RenderSystem.disableScissor();
    }
}
