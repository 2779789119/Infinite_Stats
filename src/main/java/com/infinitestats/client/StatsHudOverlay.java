package com.infinitestats.client;

import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * HUD 叠加层 — 在游戏画面上显示关键属性信息
 * 轻量化设计，不干扰游戏体验
 */
public final class StatsHudOverlay {

    private static final int BG_COLOR = 0x00000000; // 透明背景
    private static final int BORDER_COLOR = 0x00000000; // 边框透明
    private static final int TEXT_LABEL = 0xFF94A3B8;
    private static final int TEXT_POINTS = 0xFFFFD166;
    private static final int TEXT_LEVEL = 0xFF60A5FA;
    private static final int BAR_XP_BG = 0x40252535;
    private static final int BAR_XP = 0xFF4ADE80;
    private static final int BAR_MANA_BG = 0x40252535;
    private static final int BAR_MANA = 0xFFA78BFA;

    public static final int PANEL_WIDTH = 140;


    private StatsHudOverlay() {}

    public static void render(GuiGraphics graphics, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        // 编辑界面打开时不渲染普通 HUD（编辑界面自己画）
        if (mc.screen instanceof HudEditScreen) return;
        // HUD 关闭时不渲染
        if (!ClientSettings.hudVisible) return;

        var statsOpt = mc.player.getCapability(PlayerStatsProvider.PLAYER_STATS);
        if (!statsOpt.isPresent()) return;

        PlayerStats stats = statsOpt.orElse(null);
        if (stats == null) return;

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int panelHeight = getPanelHeight(stats);

        // 边界保护
        int x = Math.max(0, Math.min(ClientSettings.hudX, screenWidth - PANEL_WIDTH));
        int y = Math.max(0, Math.min(ClientSettings.hudY, screenHeight - panelHeight));

        // 渲染面板
        renderPanel(graphics, x, y, panelHeight);

        // 内容
        int contentX = x + 6;
        int contentY = y + 5;
        int lineH = 10;

        // 等级
        graphics.drawString(mc.font, "Lv." + stats.getLevel(), contentX, contentY, TEXT_LEVEL);
        contentY += lineH + 2;

        // XP 条
        long xpNeeded = stats.getXpForNextLevel();
        float xpPercent = xpNeeded > 0 ? Mth.clamp((float) stats.getExperience() / xpNeeded, 0, 1) : 0;
        int barWidth = 80;
        int barHeight = 6;
        graphics.fill(contentX, contentY, contentX + barWidth, contentY + barHeight, BAR_XP_BG);
        if (xpPercent > 0) {
            int filled = Math.max(1, (int) (xpPercent * barWidth));
            graphics.fill(contentX, contentY, contentX + filled, contentY + barHeight, BAR_XP);
        }
        graphics.drawString(mc.font, stats.getExperience() + "/" + xpNeeded, contentX + barWidth + 4, contentY - 1, TEXT_LABEL);
        contentY += barHeight + 4;

        // 可用点数
        if (stats.getAvailablePoints() > 0) {
            graphics.drawString(mc.font, stats.getAvailablePoints() + " pts", contentX, contentY, TEXT_POINTS);
            contentY += lineH + 2;
        }

        // 法力条
        float maxMana = stats.getMaxMana();
        if (maxMana > 0) {
            float manaPercent = Mth.clamp(stats.getCurrentMana() / maxMana, 0, 1);
            graphics.fill(contentX, contentY, contentX + barWidth, contentY + 5, BAR_MANA_BG);
            if (manaPercent > 0) {
                int filled = Math.max(1, (int) (manaPercent * barWidth));
                graphics.fill(contentX, contentY, contentX + filled, contentY + 5, BAR_MANA);
            }
            graphics.drawString(mc.font, (int) stats.getCurrentMana() + "/" + (int) maxMana, contentX + barWidth + 4, contentY - 2, TEXT_LABEL);
        }
    }

    private static int getPanelHeight(PlayerStats stats) {
        int pointLine = stats.getAvailablePoints() > 0 ? 1 : 0;
        int manaLine = stats.getMaxMana() > 0 ? 1 : 0;
        return 10 + 10 + 8 + pointLine * 11 + manaLine * 9 + 6;
    }

    private static void renderPanel(GuiGraphics g, int x, int y, int height) {
        g.fill(x, y + height - 1, x + PANEL_WIDTH, y + height, BORDER_COLOR);
        g.fill(x, y, x + 2, y + height, BORDER_COLOR);
        g.fill(x, y, x + PANEL_WIDTH, y + height, BG_COLOR);
    }
}
