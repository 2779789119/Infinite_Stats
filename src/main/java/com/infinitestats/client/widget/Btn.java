package com.infinitestats.client.widget;

import com.infinitestats.client.widget.Btn;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;

/**
 * 带悬浮提示的按钮 —— 用来顶替 1.20 才有的 {@code Btn.builder()...build()} 与
 * {@code AbstractWidget#setTooltip(Tooltip)}。
 *
 * <h2>为什么需要它</h2>
 * 1.19.2 既没有 {@code Button.Builder}（1.19.3 才加），也没有 {@code components.Tooltip}
 * （1.20 才加）：按钮只能 {@code new Button(x,y,w,h,msg,onPress,onTooltip)}，
 * 而提示要挂在构造期就固定下来的 {@code Button.OnTooltip} 上，事后无法再改。
 * <p>
 * 这个类把 1.20 的「构造器 + 可变提示」原样补回来：{@link #builder} 支持
 * {@code pos / bounds / size / width / tooltip / build} 这套链式写法，
 * {@link #setTip} 可以在运行期换提示（例如数值变化后重刷文案）。
 *
 * <h2>提示是怎么画出来的</h2>
 * 1.19.2 的 {@code Button.renderButton} 在「悬停或聚焦」时会回调 {@link #renderToolTip}，
 * 这里覆写它直接用当前 {@code PoseStack} 走原版 tooltip 渲染 —— 因此外观与 1.20 一致，
 * 也会跟着面板缩放走。
 */
public class Btn extends Button {

    // 配色：与面板 / 卡片同一套深色系（见 EditorUi / PanelScreen 的取值）
    private static final int BORDER = 0xFF3A3A52;
    private static final int BORDER_HOVER = 0xFF5B6B9E;
    private static final int BORDER_DISABLED = 0xFF262633;
    private static final int BG = 0xFF2A2A3C;
    private static final int BG_HOVER = 0xFF343450;
    private static final int BG_DISABLED = 0xFF22222E;
    private static final int HIGHLIGHT = 0xFF454565;
    private static final int SHADOW = 0xFF181822;
    private static final int TEXT = 0xFFE2E8F0;
    private static final int TEXT_HOVER = 0xFFFFFFFF;
    private static final int TEXT_DISABLED = 0xFF6E6E80;

    @Nullable
    private Component tip;

    public Btn(int x, int y, int width, int height, Component message,
               Button.OnPress onPress, @Nullable Component tip) {
        super(x, y, width, height, message, onPress, NO_TOOLTIP);
        this.tip = tip;
    }

    /** 替换悬浮提示（传 null 表示去掉提示）。 */
    public Btn setTip(@Nullable Component tip) {
        this.tip = tip;
        return this;
    }

    @Nullable
    public Component getTip() {
        return this.tip;
    }

    /**
     * 自己画按钮（纯色块），不碰原版 {@code widgets.png}。
     *
     * <h2>为什么不用原版贴图</h2>
     * 1.19.2 的 {@code AbstractButton#renderButton} 是「整块 blit 一块 {@code height} 高的纹理」，
     * 而 {@code widgets.png} 里按钮三态是紧挨着的三行 20px 精灵（v=46/66/86）：
     * 高度不是 20（本模组多为 24~26）就会采到下一行的边框，底部多出一条亮线；
     * 想用九宫格补救，又得同时对齐「贴图尺寸 256」「精灵 200×20」「精灵内 1px 描边+圆角」三层坐标，
     * 实测很容易糊掉（本模组已经在这上面翻过两次车）。
     * <p>
     * 本模组其余界面（面板、卡片、开关、滑块）本来就是纯色块手绘，按钮跟着走同一套配色反而更统一：
     * 描边 + 主体 + 顶部高光 + 底部阴影，四层 {@code fill}，高度随便给都不会有采样问题。
     */
    @Override
    public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean hovered = this.isHoveredOrFocused();
        int border = this.active ? (hovered ? BORDER_HOVER : BORDER) : BORDER_DISABLED;
        int body = this.active ? (hovered ? BG_HOVER : BG) : BG_DISABLED;
        int x1 = this.x;
        int y1 = this.y;
        int x2 = this.x + this.width;
        int y2 = this.y + this.height;

        fill(poseStack, x1, y1, x2, y2, border);
        fill(poseStack, x1 + 1, y1 + 1, x2 - 1, y2 - 1, body);
        if (this.height > 3) {
            // 顶部高光 / 底部阴影：一点点立体感，不影响可读性
            fill(poseStack, x1 + 1, y1 + 1, x2 - 1, y1 + 2, this.active ? HIGHLIGHT : BG_DISABLED);
            fill(poseStack, x1 + 1, y2 - 2, x2 - 1, y2 - 1, SHADOW);
        }

        int textColor = this.active ? (hovered ? TEXT_HOVER : TEXT) : TEXT_DISABLED;
        drawCenteredString(poseStack, minecraft.font, this.getMessage(),
                x1 + this.width / 2, y1 + (this.height - 8) / 2, textColor);
    }

    @Override
    public void renderToolTip(PoseStack poseStack, int mouseX, int mouseY) {
        if (this.tip == null) return;
        Screen screen = Minecraft.getInstance().screen;
        if (screen != null) {
            screen.renderTooltip(poseStack, this.tip, mouseX, mouseY);
        }
    }

    public static Builder builder(Component message, Button.OnPress onPress) {
        return new Builder(message, onPress);
    }

    /** 1.20 风格的按钮构造器。 */
    public static final class Builder {
        private final Component message;
        private final Button.OnPress onPress;
        private int x;
        private int y;
        private int width = DEFAULT_WIDTH;
        private int height = DEFAULT_HEIGHT;
        @Nullable
        private Component tip;

        private Builder(Component message, Button.OnPress onPress) {
            this.message = message;
            this.onPress = onPress;
        }

        public Builder pos(int x, int y) {
            this.x = x;
            this.y = y;
            return this;
        }

        public Builder bounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder size(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder width(int width) {
            this.width = width;
            return this;
        }

        /** 1.20 里这里收的是 {@code Tooltip}，1.19.2 没有那个类，直接收文案。 */
        public Builder tooltip(@Nullable Component tip) {
            this.tip = tip;
            return this;
        }

        public Btn build() {
            return new Btn(this.x, this.y, this.width, this.height, this.message, this.onPress, this.tip);
        }
    }
}
