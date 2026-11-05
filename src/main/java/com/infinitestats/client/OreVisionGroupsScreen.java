package com.infinitestats.client;

import com.infinitestats.stats.OreVisionConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 「矿透」的矿物分组开关。
 * <p>
 * 一个分组对应一种线框颜色：关掉之后该类矿石不再描边，也正好是玩家最想要的「别给我画铁矿」。
 * 改动立刻写回 {@link OreVisionConfig} 并回调保存（父面板负责提交给服务端）。
 */
public class OreVisionGroupsScreen extends PanelScreen {

    private static final int PANEL_W = 268;
    private static final int PANEL_H = 208;

    private static final int TEXT = 0xFFE8E8F0;
    private static final int MUTED = 0xFF9A9AB0;
    private static final int ACCENT = 0xFF6BC8FF;

    private final Screen parent;
    private final OreVisionConfig cfg;
    private final Runnable onSave;

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

    /** 每个分组的矩形（两列）。 */
    private static int[] cell(int index) {
        int col = index % 2;
        int row = index / 2;
        return new int[]{12 + col * 122, 34 + row * 22, 116, 18};
    }

    @Override
    protected void renderPanel(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, PANEL_W, PANEL_H, 0xF0121218);
        g.fill(0, 0, PANEL_W, 26, 0xFF1B1B26);
        g.drawString(font, Component.translatable("screen.infinitestats.ore_vision.groups"), 10, 9, ACCENT, false);

        List<OreVisionConfig.OreGroup> groups = OreVisionConfig.groups();
        for (int i = 0; i < groups.size(); i++) {
            OreVisionConfig.OreGroup group = groups.get(i);
            int[] r = cell(i);
            boolean visible = !cfg.isGroupHidden(group);
            boolean hover = mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3];
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

        g.drawString(font, Component.translatable("screen.infinitestats.ore_vision.groups_hint"),
                12, PANEL_H - 18, MUTED, false);
    }

    @Override
    protected boolean onPanelClick(int mouseX, int mouseY, int button) {
        List<OreVisionConfig.OreGroup> groups = OreVisionConfig.groups();
        for (int i = 0; i < groups.size(); i++) {
            int[] r = cell(i);
            if (mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3]) {
                OreVisionConfig.OreGroup group = groups.get(i);
                cfg.setGroupHidden(group, !cfg.isGroupHidden(group));
                if (onSave != null) onSave.run();
                return true;
            }
        }
        return false;
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
