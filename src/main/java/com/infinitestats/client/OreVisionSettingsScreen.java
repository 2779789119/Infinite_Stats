package com.infinitestats.client;

import com.infinitestats.network.NetworkHandler;
import com.infinitestats.network.UpdateOreVisionConfigPacket;
import com.infinitestats.stats.OreVisionConfig;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 「矿透」设置面板。
 * <p>
 * 只改自己角色上的那份 {@link OreVisionConfig}：本地先改（界面立刻响应），同时把改动提交给服务端，
 * 服务端夹一遍范围后回发权威值。面板本身不做任何扫描 —— 扫描在 {@link OreVisionOverlay} 里按需触发，
 * 底部会显示「当前描边数量」便于排查（0 = 扫描没命中，几十上百但看不到 = 渲染问题）。
 */
public class OreVisionSettingsScreen extends PanelScreen {

    private static final int PANEL_W = 300;
    private static final int PANEL_H = 196;

    private static final int TEXT = 0xFFE8E8F0;
    private static final int MUTED = 0xFF9A9AB0;
    private static final int ACCENT = 0xFF6BC8FF;
    private static final int BTN = 0xFF2A2A3C;
    private static final int BTN_HOVER = 0xFF3C3C58;

    /** 描边上限的几档（避免输入框，够用且好点）。 */
    private static final int[] BLOCK_STEPS = {300, 800, 1500, 3000, 6000};

    private final Screen parent;
    private final OreVisionConfig cfg;

    public OreVisionSettingsScreen(Screen parent) {
        super(Component.translatable("screen.infinitestats.ore_vision"));
        this.parent = parent;
        PlayerStats stats = Minecraft.getInstance().player == null
                ? null
                : PlayerStatsProvider.of(Minecraft.getInstance().player).orElse(null);
        // 复制一份用来编辑：取消（ESC）时直接丢掉，不影响已生效的设置
        this.cfg = stats == null ? new OreVisionConfig() : stats.getOreVisionConfig().copy();
    }

    @Override
    protected int panelW() {
        return PANEL_W;
    }

    @Override
    protected int panelH() {
        return PANEL_H;
    }

    @Override
    protected void renderPanel(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, PANEL_W, PANEL_H, 0xF0121218);
        g.fill(0, 0, PANEL_W, 26, 0xFF1B1B26);
        g.drawString(font, Component.translatable("screen.infinitestats.ore_vision"), 10, 9, ACCENT, false);

        int y = 38;
        drawRow(g, mouseX, mouseY, y, "screen.infinitestats.ore_vision.range",
                String.valueOf(cfg.getRange()), true);
        y += 26;
        drawRow(g, mouseX, mouseY, y, "screen.infinitestats.ore_vision.max_blocks",
                String.valueOf(cfg.getMaxBlocks()), true);
        y += 26;

        // 潜行开关
        boolean sneakHover = inside(mouseX, mouseY, 10, y, PANEL_W - 20, 18);
        g.fill(10, y, PANEL_W - 10, y + 18, sneakHover ? BTN_HOVER : BTN);
        g.drawString(font, Component.translatable("screen.infinitestats.ore_vision.require_sneak"),
                16, y + 5, TEXT, false);
        g.drawString(font, cfg.isRequireSneak() ? "§aON" : "§7OFF", PANEL_W - 40, y + 5, TEXT, false);
        y += 26;

        // 矿物种类
        boolean groupHover = inside(mouseX, mouseY, 10, y, PANEL_W - 20, 18);
        g.fill(10, y, PANEL_W - 10, y + 18, groupHover ? BTN_HOVER : BTN);
        g.drawString(font, Component.translatable("screen.infinitestats.ore_vision.groups"),
                16, y + 5, TEXT, false);
        g.drawString(font, cfg.visibleGroupCount() + "/" + OreVisionConfig.groups().size(),
                PANEL_W - 44, y + 5, ACCENT, false);
        y += 30;

        g.drawString(font, Component.translatable("screen.infinitestats.ore_vision.cached",
                OreVisionOverlay.cachedCount()), 12, y, MUTED, false);
        g.drawString(font, Component.translatable("screen.infinitestats.ore_vision.hint"),
                12, PANEL_H - 18, MUTED, false);
    }

    /** 一行「标题 + 数值 + [−][+]」；{@code mouseY} 用于悬停高亮。 */
    private void drawRow(GuiGraphics g, int mouseX, int mouseY, int y, String titleKey,
                         String value, boolean withButtons) {
        g.fill(10, y, PANEL_W - 10, y + 18, 0xFF1E1E2A);
        g.drawString(font, Component.translatable(titleKey), 16, y + 5, TEXT, false);
        g.drawString(font, value, PANEL_W - 96, y + 5, ACCENT, false);
        if (withButtons) {
            drawButton(g, mouseX, mouseY, PANEL_W - 66, y + 1, 22, 16, "−");
            drawButton(g, mouseX, mouseY, PANEL_W - 40, y + 1, 22, 16, "+");
        }
    }

    private void drawButton(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h, String label) {
        boolean hover = inside(mouseX, mouseY, x, y, w, h);
        g.fill(x, y, x + w, y + h, hover ? BTN_HOVER : BTN);
        g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, TEXT);
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    protected boolean onPanelClick(int mouseX, int mouseY, int button) {
        // 半径
        if (inside(mouseX, mouseY, PANEL_W - 66, 39, 22, 16)) {
            cfg.setRange(cfg.getRange() - 4);
            save();
            return true;
        }
        if (inside(mouseX, mouseY, PANEL_W - 40, 39, 22, 16)) {
            cfg.setRange(cfg.getRange() + 4);
            save();
            return true;
        }
        // 描边上限
        if (inside(mouseX, mouseY, PANEL_W - 66, 65, 22, 16)) {
            cfg.setMaxBlocks(stepBlocks(cfg.getMaxBlocks(), -1));
            save();
            return true;
        }
        if (inside(mouseX, mouseY, PANEL_W - 40, 65, 22, 16)) {
            cfg.setMaxBlocks(stepBlocks(cfg.getMaxBlocks(), 1));
            save();
            return true;
        }
        // 潜行开关
        if (inside(mouseX, mouseY, 10, 90, PANEL_W - 20, 18)) {
            cfg.setRequireSneak(!cfg.isRequireSneak());
            save();
            return true;
        }
        // 矿物种类
        if (inside(mouseX, mouseY, 10, 116, PANEL_W - 20, 18)) {
            if (minecraft != null) {
                minecraft.setScreen(new OreVisionGroupsScreen(this, cfg, this::save));
            }
            return true;
        }
        return false;
    }

    /** 找最接近的下一档（不是严格对齐档位也能往上 / 往下走）。 */
    private static int stepBlocks(int current, int direction) {
        if (direction > 0) {
            for (int step : BLOCK_STEPS) {
                if (step > current) return step;
            }
            return BLOCK_STEPS[BLOCK_STEPS.length - 1];
        }
        for (int i = BLOCK_STEPS.length - 1; i >= 0; i--) {
            if (BLOCK_STEPS[i] < current) return BLOCK_STEPS[i];
        }
        return BLOCK_STEPS[0];
    }

    /** 本地先生效，再提交给服务端（服务端会回发权威值）。 */
    private void save() {
        PlayerStats stats = Minecraft.getInstance().player == null
                ? null
                : PlayerStatsProvider.of(Minecraft.getInstance().player).orElse(null);
        if (stats != null) {
            stats.getOreVisionConfig().copyFrom(cfg);
        }
        NetworkHandler.CHANNEL.sendToServer(new UpdateOreVisionConfigPacket(cfg));
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
