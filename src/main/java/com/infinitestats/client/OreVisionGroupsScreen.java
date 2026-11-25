package com.infinitestats.client;

import com.infinitestats.stats.OreVisionConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * 「矿透」的矿物分组开关。
 * <p>
 * 一个分组对应一种线框颜色：关掉之后该类矿石不再描边，也正好是玩家最想要的「别给我画铁矿」。
 * 改动立刻写回 {@link OreVisionConfig} 并回调保存（父面板负责提交给服务端）。
 *
 * <h2>为什么要滚动</h2>
 * 早先是两列网格硬排：11 组正好塞满 6 行、零余量。数据包或更新再加一组就会画到面板外面，
 * 现在固定 6 行可见，组再多也只在列表里滚动；底栏另给「全部显示 / 全部隐藏」。
 */
public class OreVisionGroupsScreen extends PanelScreen {

    private static final int PANEL_W = 268;
    private static final int PANEL_H = 220;

    /** 网格：两列，每格 116×18，行距 22。 */
    private static final int CELL_W = 116;
    private static final int CELL_H = 18;
    private static final int ROW_H = 22;
    private static final int GRID_TOP = 34;
    private static final int COL_X = 12;
    private static final int COL_GAP = 122;
    /** 可见行数：6 行 = 12 格，不够就在列表里滚。 */
    private static final int VISIBLE_ROWS = 6;
    private static final int LIST_H = VISIBLE_ROWS * ROW_H;

    private static final int FOOTER_Y = GRID_TOP + LIST_H + 6;

    private static final int TEXT = 0xFFE8E8F0;
    private static final int MUTED = 0xFF9A9AB0;
    private static final int ACCENT = 0xFF6BC8FF;

    private final Screen parent;
    private final OreVisionConfig cfg;
    private final Runnable onSave;

    private int scrollRow;
    private int maxScrollRow;
    private int hoveredGroup = -1;

    public OreVisionGroupsScreen(Screen parent, OreVisionConfig cfg, Runnable onSave) {
        super(Component.translatable("screen.infinitestats.ore_vision.groups"));
        this.parent = parent;
        this.cfg = cfg;
        this.onSave = onSave;
    }

    @Override
    protected int panelW() {
        return PANEL_W;
    }

    @Override
    protected int panelH() {
        return PANEL_H;
    }

    /** 第 index 个分组在本页里的矩形（不含滚动偏移）。 */
    private static int[] cell(int visibleRow, int col) {
        return new int[]{COL_X + col * COL_GAP, GRID_TOP + visibleRow * ROW_H, CELL_W, CELL_H};
    }

    private List<OreVisionConfig.OreGroup> groups() {
        List<OreVisionConfig.OreGroup> list = new ArrayList<>(OreVisionConfig.groups());
        return list;
    }

    private void updateScroll() {
        int rows = (groups().size() + 1) / 2;
        maxScrollRow = Math.max(0, rows - VISIBLE_ROWS);
        scrollRow = Mth.clamp(scrollRow, 0, maxScrollRow);
    }

    @Override
    protected void renderPanel(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        updateScroll();
        g.fill(0, 0, PANEL_W, PANEL_H, 0xF0121218);
        g.fill(0, 0, PANEL_W, 26, 0xFF1B1B26);
        g.drawString(font, Component.translatable("screen.infinitestats.ore_vision.groups"), 10, 9, ACCENT, false);

        List<OreVisionConfig.OreGroup> groups = groups();
        hoveredGroup = -1;

        beginPanelScissor(g, 4, GRID_TOP, PANEL_W - 4, GRID_TOP + LIST_H);
        for (int i = scrollRow * 2; i < groups.size(); i++) {
            int row = i / 2 - scrollRow;
            if (row < 0) continue;
            if (row >= VISIBLE_ROWS) break;

            OreVisionConfig.OreGroup group = groups.get(i);
            int[] r = cell(row, i % 2);
            boolean visible = !cfg.isGroupHidden(group);
            boolean hover = mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3];
            if (hover) hoveredGroup = i;

            g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3],
                    hover ? 0xFF3C3C58 : (visible ? 0xFF23233A : 0xFF1A1A22));

            float[] color = group.color();
            int rgb = 0xFF000000
                    | ((int) (color[0] * 255) << 16)
                    | ((int) (color[1] * 255) << 8)
                    | (int) (color[2] * 255);
            g.fill(r[0] + 4, r[1] + 6, r[0] + 10, r[1] + 12, rgb);
            g.drawString(font, Component.translatable("screen.infinitestats.ore_vision.group."
                    + group.key()), r[0] + 15, r[1] + 5, visible ? TEXT : MUTED, false);
            g.drawString(font, visible ? "§a●" : "§7○", r[0] + r[2] - 14, r[1] + 5, TEXT, false);
        }
        endPanelScissor(g);

        // 滚动条（组多了才有）
        if (maxScrollRow > 0) {
            EditorUi.scrollbar(g, mouseX, mouseY, PANEL_W - 8, GRID_TOP, LIST_H,
                    scrollRow, maxScrollRow, VISIBLE_ROWS);
        }

        // 底栏：全部显示 / 全部隐藏 / 说明
        drawFooterButton(g, mouseX, mouseY, COL_X, FOOTER_Y,
                Component.translatable("screen.infinitestats.ore_vision.show_all").getString());
        drawFooterButton(g, mouseX, mouseY, COL_X + 76, FOOTER_Y,
                Component.translatable("screen.infinitestats.ore_vision.hide_all").getString());
        drawFooterButton(g, mouseX, mouseY, COL_X + 152, FOOTER_Y,
                Component.translatable("screen.infinitestats.deposit_filter.done").getString());

        g.drawString(font, Component.translatable("screen.infinitestats.ore_vision.groups_hint"),
                COL_X, PANEL_H - 16, MUTED, false);
    }

    private void drawFooterButton(GuiGraphics g, int mouseX, int mouseY, int x, int y, String label) {
        int w = 72;
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 16;
        g.drawString(font, label, x + 4, y + 4, hover ? ACCENT : TEXT, false);
    }

    @Override
    protected void renderPanelOverlay(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (hoveredGroup < 0) return;
        List<OreVisionConfig.OreGroup> groups = groups();
        if (hoveredGroup >= groups.size()) return;
        OreVisionConfig.OreGroup group = groups.get(hoveredGroup);
        boolean visible = !cfg.isGroupHidden(group);
        List<String> lines = new ArrayList<>();
        lines.add("§f" + Component.translatable("screen.infinitestats.ore_vision.group." + group.key()).getString());
        lines.add(Component.translatable(visible
                ? "gui.infinitestats.entity_panel.on"
                : "gui.infinitestats.entity_panel.off").getString());
        queueTooltip(g, mouseX, mouseY, lines, ACCENT);
    }

    @Override
    protected boolean onPanelClick(int mouseX, int mouseY, int button) {
        updateScroll();

        // 滚动条拖动（组多了才有）
        if (button == 0 && beginScrollbarDrag(mouseX, mouseY, PANEL_W - 8, GRID_TOP, LIST_H,
                scrollRow, maxScrollRow, VISIBLE_ROWS,
                v -> scrollRow = Mth.clamp(v, 0, maxScrollRow))) {
            return true;
        }

        // 底栏
        if (mouseY >= FOOTER_Y && mouseY < FOOTER_Y + 16) {
            if (mouseX >= COL_X && mouseX < COL_X + 72) {
                setAllVisible(true);
                return true;
            }
            if (mouseX >= COL_X + 76 && mouseX < COL_X + 148) {
                setAllVisible(false);
                return true;
            }
            if (mouseX >= COL_X + 152 && mouseX < COL_X + 224) {
                onClose();
                return true;
            }
        }

        List<OreVisionConfig.OreGroup> groups = groups();
        for (int i = scrollRow * 2; i < groups.size(); i++) {
            int row = i / 2 - scrollRow;
            if (row < 0) continue;
            if (row >= VISIBLE_ROWS) break;
            int[] r = cell(row, i % 2);
            if (mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3]) {
                OreVisionConfig.OreGroup group = groups.get(i);
                cfg.setGroupHidden(group, !cfg.isGroupHidden(group));
                if (onSave != null) onSave.run();
                return true;
            }
        }
        return false;
    }

    /** 一键全显示 / 全隐藏（隐藏 = 该类矿石不再描边）。 */
    private void setAllVisible(boolean visible) {
        for (OreVisionConfig.OreGroup group : groups()) {
            cfg.setGroupHidden(group, !visible);
        }
        if (onSave != null) onSave.run();
    }

    @Override
    protected boolean onPanelScroll(int mouseX, int mouseY, double delta) {
        updateScroll();
        if (maxScrollRow <= 0 || delta == 0) return false;
        scrollRow = Mth.clamp(scrollRow - (int) Math.signum(delta), 0, maxScrollRow);
        return true;
    }

    @Override
    protected boolean onPanelDrag(int mouseX, int mouseY, int button, int dragX, int dragY) {
        return button == 0 && updateScrollbarDrag(mouseY);
    }

    @Override
    protected boolean onPanelRelease(int mouseX, int mouseY, int button) {
        return button == 0 && endScrollbarDrag();
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
