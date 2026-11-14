package com.infinitestats.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 物品编辑器系列界面（主编辑 / 条目选择 / 元数据编辑）的统一视觉规范。
 * <p>
 * 集中管理配色、尺寸常量与公共绘制（面板、标题栏、分隔线、滚动条、浮窗），
 * 让三个界面的风格与间距保持一致，避免各自维护一套魔法数字。
 */
final class EditorUi {

    private EditorUi() {}

    // ======================== 配色 ========================

    /** 标题与强调（绿） */
    static final int ACCENT = 0xFF4ADE80;
    /** 分区标题与数值（金） */
    static final int GOLD = 0xFFFFD166;
    /** 主文本 */
    static final int PRIMARY = 0xFFE2E8F0;
    /** 次要文本 */
    static final int SECONDARY = 0xFF94A3B8;
    /** 危险 / 错误 */
    static final int DANGER = 0xFFF87171;
    /** 信息 / 高亮（蓝） */
    static final int INFO = 0xFF60A5FA;

    static final int BG_PANEL = 0xE81A1A2E;
    static final int BG_HEADER = 0xFF1E293B;
    static final int BG_ITEM_BAR = 0xFF16202E;
    static final int BG_ROW = 0x50252535;
    static final int BG_ROW_HOVER = 0x80353550;
    static final int BG_ROW_DRAG = 0x80506A50;
    static final int BG_DEL = 0x504A2A1A;
    static final int BG_DEL_HOVER = 0x80AA4040;
    static final int BG_DIVIDER = 0x40252540;
    static final int BG_SB_TRACK = 0x30151520;
    static final int BG_SB = 0x80555570;
    static final int BG_SB_HOVER = 0xB08888A0;
    static final int BG_BAR_TRACK = 0xFF2A2A3A;

    // ======================== 尺寸 ========================

    /** 标题栏高度，三个界面统一 */
    static final int HEADER_H = 24;
    /** 列表行高 */
    static final int ROW_H = 22;
    /** 面板内边距 */
    static final int GAP = 8;
    /** 滚动条宽度 */
    static final int SCROLL_W = 5;
    /** 行内删除按钮边长 */
    static final int DEL_SIZE = 16;

    // ======================== 绘制 ========================

    /** 面板底色 + 标题栏底色（不含标题文字）。 */
    static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, BG_PANEL);
        g.fill(x, y, x + w, y + HEADER_H, BG_HEADER);
    }

    /** 居中标题，垂直居中于标题栏。 */
    static void title(GuiGraphics g, Font font, int x, int y, int w, String text) {
        g.drawCenteredString(font, text, x + w / 2, y + (HEADER_H - font.lineHeight) / 2 + 1, ACCENT);
    }

    static void dividerH(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, BG_DIVIDER);
    }

    static void dividerV(GuiGraphics g, int x, int y, int h) {
        g.fill(x, y, x + 1, y + h, BG_DIVIDER);
    }

    /** 分区标题（左侧色条 + 金色文字）。 */
    static void section(GuiGraphics g, Font font, int x, int y, String text) {
        g.fill(x, y, x + 2, y + font.lineHeight, GOLD);
        g.drawString(font, text, x + 6, y, GOLD);
    }

    /**
     * 滚动条：仅在内容超出可见范围时绘制。
     *
     * @param visible 可见行数，用于计算滑块比例
     */
    static void scrollbar(GuiGraphics g, int mx, int my, int x, int y, int h,
                          int scroll, int maxScroll, int visible) {
        if (maxScroll <= 0 || h <= 0) return;
        g.fill(x, y, x + SCROLL_W, y + h, BG_SB_TRACK);
        int sliderH = Math.max(16, (int) ((float) visible / (maxScroll + visible) * h));
        sliderH = Math.min(sliderH, h);
        int sliderY = y + (h - sliderH) * scroll / maxScroll;
        boolean hover = mx >= x && mx < x + SCROLL_W && my >= sliderY && my < sliderY + sliderH;
        g.fill(x, sliderY, x + SCROLL_W, sliderY + sliderH, hover ? BG_SB_HOVER : BG_SB);
    }

    /** 滑块高度：绘制 / 命中判定 / 拖拽共用同一套算法，保证三者一致。 */
    static int scrollThumbH(int trackH, int maxScroll, int visible) {
        return Math.min(trackH, Math.max(16, (int) ((float) visible / (maxScroll + visible) * trackH)));
    }

    /**
     * 按鼠标 Y 换算滚动位置：滑块<b>中心跟随鼠标</b>。
     * <p>
     * 刻意做成无状态的纯函数：老实现用「抓取偏移」累积（滑块顶到夹取边界后偏移量就失真），
     * 拖到底再拖回来时滑块会卡住甚至跳回原处 —— 玩家看到的就是「回弹」。
     */
    static int scrollFromMouseY(int mouseY, int trackY, int trackH, int maxScroll, int visible) {
        if (maxScroll <= 0 || trackH <= 0) return 0;
        int thumbH = scrollThumbH(trackH, maxScroll, visible);
        int range = trackH - thumbH;
        if (range <= 0) return 0;
        float progress = (mouseY - trackY - thumbH / 2f) / range;
        return Math.max(0, Math.min(maxScroll, Math.round(progress * maxScroll)));
    }

    /** 鼠标是否落在滚动条轨道上（左右各放宽 3px，方便点中）。 */
    static boolean onScrollbar(int mouseX, int mouseY, int trackX, int trackY, int trackH) {
        return mouseX >= trackX - 3 && mouseX < trackX + SCROLL_W + 3
                && mouseY >= trackY - 2 && mouseY < trackY + trackH + 2;
    }

    /** 分段进度条（耐久 / 权重等），返回填充色。 */
    static int bar(GuiGraphics g, int x, int y, int w, int h, float ratio) {
        g.fill(x, y, x + w, y + h, BG_BAR_TRACK);
        ratio = Math.max(0f, Math.min(1f, ratio));
        int color = ratio > 0.5f ? ACCENT : ratio > 0.25f ? GOLD : DANGER;
        if (ratio > 0f) g.fill(x, y, x + Math.max(1, (int) (w * ratio)), y + h, color);
        return color;
    }

    /**
     * 全模组统一的浮窗入口 —— <b>直接画原版浮窗</b>（灰底 + 渐变边框，位置由原版自动避让屏幕边缘）。
     *
     * <h2>为什么不再自绘</h2>
     * 曾经这里是自己画的「深色底 + 顶部彩线」框，和原版浮窗并存，导致同一个模组里有两种观感；
     * 现在统一交给原版 {@code renderTooltip}，界面上所有浮窗的外观与其它模组一致。
     *
     * <h2>为什么还要 flush</h2>
     * {@link GuiGraphics} 不是「调用一次画一次」：顶点按 {@code RenderType} 分批，
     * <b>所有 {@code fill} 一批、所有文字一批</b>。浮窗背景会跟着「所有背景」那批走在前面，
     * 界面上已有的列表 / 按钮文字在「所有文字」那批里排在后面 —— 表现为「文字浮在浮窗上」。
     * 画之前先把已有内容落地，浮窗的背景才不会被压住。
     * （原版全流程还有 {@code GuiGraphicsTooltipMixin} 兜底，这里再明确一次。）
     *
     * @param accent 已废弃：自绘时代的顶部彩线颜色，原版浮窗没有这回事，保留参数只为不动几十处调用点
     */
    static void tooltip(GuiGraphics g, int mx, int my, List<String> lines, int accent) {
        if (lines == null || lines.isEmpty()) return;
        List<net.minecraft.util.FormattedCharSequence> visual =
                new java.util.ArrayList<>(lines.size());
        for (String line : lines) {
            // language 代码（§）由字体渲染器解析，与之前的自绘版本口径一致
            visual.add(Component.literal(line == null ? "" : line).getVisualOrderText());
        }
        vanillaTooltip(g, visual, mx, my);
    }

    /**
     * 原版物品浮窗（内容由原版收集：名称 / 附魔 / 词条 / 模组追加说明）。
     * <p>
     * 物品提示不重写成自绘浮窗，但仍要前后 {@code flush()}：否则它的<b>背景</b>会落进
     * 「所有背景」那批、排在界面已有文字的前面，被列表文字压住。
     * （原版 {@code renderTooltip} 全流程的批次隔离由 {@code GuiGraphicsTooltipMixin} 兜底，
     * 这里再明确一次，调用点在缩放姿态里也不会错。）
     */
    static void itemTooltip(GuiGraphics g, net.minecraft.world.item.ItemStack stack, int mx, int my) {
        g.flush();
        g.renderTooltip(Minecraft.getInstance().font, stack, mx, my);
        g.flush();
    }

    /** 原版 Component 浮窗（单段文本，内部换行由原版处理）。 */
    static void vanillaTooltip(GuiGraphics g, Component text, int mx, int my) {
        g.flush();
        g.renderTooltip(Minecraft.getInstance().font, text, mx, my);
        g.flush();
    }

    /** 原版行列表浮窗（已折好行的 {@code FormattedCharSequence}）。 */
    static void vanillaTooltip(GuiGraphics g, List<net.minecraft.util.FormattedCharSequence> lines,
                               int mx, int my) {
        if (lines == null || lines.isEmpty()) return;
        g.flush();
        g.renderTooltip(Minecraft.getInstance().font, lines, mx, my);
        g.flush();
    }

    /**
     * 原版浮窗底框（暗底 + 紫渐变边框），给「不是浮窗但需要压在内容之上」的浮层用，
     * 例如成就管理器的模组下拉列表 —— 让自绘浮层和浮窗观感统一。
     * <p>
     * 底色调成<b>完全不透明</b>：原版浮窗是 94% 不透明，垫在下拉列表上会透出底下的
     * 列表文字（看起来像「菜单没盖住」），浮层必须实心。
     */
    static void vanillaBackdrop(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF101010);
        // 原版浮窗边框：上蓝紫 → 下深紫，上下各 1px 渐变
        g.fillGradient(x, y, x + w, y + 1, 0xFF5000FF, 0xFF28007F);
        g.fillGradient(x, y + h - 1, x + w, y + h, 0xFF28007F, 0xFF5000FF);
        g.fill(x, y + 1, x + 1, y + h - 1, 0xFF28007F);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, 0xFF28007F);
    }

    /**
     * 按像素宽度折行（保留原文换行），中英文通用的逐字符折行，用于浮窗里的长文本。
     *
     * <h2>折行时必须把 {@code §} 格式码带到下一行</h2>
     * 调用点普遍是「前缀一个颜色码 + 一整段文字」（例如 {@code "§7" + 说明}）。
     * 如果只按字符切，颜色码留在第一行末尾，后面几行就没有任何格式码了 ——
     * 渲染出来是<b>默认白色</b>，在深色浮窗上看起来像「第二行颜色变了」。
     * 所以这里跟踪当前生效的颜色与样式，每开一行都重新写一遍。
     */
    static List<String> wrap(Font font, String text, int maxWidth) {
        List<String> out = new java.util.ArrayList<>();
        if (text == null || text.isEmpty()) return out;
        for (String para : text.split("\n", -1)) {
            if (para.isEmpty()) { out.add(""); continue; }

            StringBuilder color = new StringBuilder();
            StringBuilder styles = new StringBuilder();
            StringBuilder line = new StringBuilder();
            // 本行已有的可见字符数：只有它 > 0 才允许折行，否则「前缀 + 一个超宽字符」会反复空折
            int visible = 0;

            for (int i = 0; i < para.length(); i++) {
                char c = para.charAt(i);

                if (c == '§' && i + 1 < para.length()) {
                    char code = Character.toLowerCase(para.charAt(i + 1));
                    if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
                        color.setLength(0);
                        color.append('§').append(para.charAt(i + 1));
                    } else if (code == 'r') {
                        color.setLength(0);
                        styles.setLength(0);
                    } else if (code == 'l' || code == 'o' || code == 'n' || code == 'm' || code == 'k') {
                        if (styles.indexOf("§" + code) < 0) styles.append('§').append(code);
                    }
                    line.append(c).append(para.charAt(i + 1));
                    i++;
                    continue;
                }

                if (visible > 0 && font.width(line.toString() + c) > maxWidth) {
                    out.add(line.toString());
                    line.setLength(0);
                    line.append(color).append(styles);
                    visible = 0;
                }
                line.append(c);
                visible++;
            }
            if (visible > 0) out.add(line.toString());
        }
        return out;
    }

    /** 末尾省略号截断。 */
    static String ellipsize(Font font, String text, int maxWidth) {
        if (text == null) return "";
        if (font.width(text) <= maxWidth) return text;
        return font.plainSubstrByWidth(text, Math.max(4, maxWidth - font.width("…"))) + "…";
    }

    /** 数值格式化：整数不带小数，小数最多保留 4 位并去掉尾随 0。 */
    static String amount(double v) {
        if (!Double.isFinite(v)) return String.valueOf(v);
        if (v == Math.rint(v) && Math.abs(v) < 1e9) return String.valueOf((long) v);
        String s = String.format(java.util.Locale.ROOT, "%.4f", v);
        s = s.replaceAll("0+$", "");
        if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        return s;
    }
}
