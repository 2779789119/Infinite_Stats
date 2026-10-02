package com.infinitestats.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

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
    static final int BG_TOOLTIP = 0xF01A1A2E;
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

    /** 分段进度条（耐久 / 权重等），返回填充色。 */
    static int bar(GuiGraphics g, int x, int y, int w, int h, float ratio) {
        g.fill(x, y, x + w, y + h, BG_BAR_TRACK);
        ratio = Math.max(0f, Math.min(1f, ratio));
        int color = ratio > 0.5f ? ACCENT : ratio > 0.25f ? GOLD : DANGER;
        if (ratio > 0f) g.fill(x, y, x + Math.max(1, (int) (w * ratio)), y + h, color);
        return color;
    }

    /** 鼠标附近的浮窗，自动避让屏幕边界。 */
    static void tooltip(GuiGraphics g, int mx, int my, List<String> lines, int accent) {
        if (lines == null || lines.isEmpty()) return;
        Font font = Minecraft.getInstance().font;
        int lineH = font.lineHeight + 1;
        int boxW = 12;
        for (String l : lines) boxW = Math.max(boxW, font.width(l) + 12);
        int boxH = lines.size() * lineH + 8;
        int bx = mx + 14, by = my + 14;
        int sw = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int sh = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        if (bx + boxW > sw) bx = mx - 14 - boxW;
        if (by + boxH > sh) by = my - 14 - boxH;
        bx = Math.max(0, bx);
        by = Math.max(0, by);
        g.fill(bx, by, bx + boxW, by + boxH, BG_TOOLTIP);
        g.fill(bx, by, bx + boxW, by + 1, accent);
        int ty = by + 4;
        for (String l : lines) {
            g.drawString(font, l, bx + 6, ty, PRIMARY);
            ty += lineH;
        }
    }

    /** 按像素宽度折行（保留原文换行），中英文通用的逐字符折行，用于浮窗里的长文本。 */
    static List<String> wrap(Font font, String text, int maxWidth) {
        List<String> out = new java.util.ArrayList<>();
        if (text == null || text.isEmpty()) return out;
        for (String para : text.split("\n", -1)) {
            if (para.isEmpty()) { out.add(""); continue; }
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < para.length(); i++) {
                char c = para.charAt(i);
                if (line.length() > 0 && font.width(line.toString() + c) > maxWidth) {
                    out.add(line.toString());
                    line.setLength(0);
                }
                line.append(c);
            }
            if (line.length() > 0) out.add(line.toString());
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
