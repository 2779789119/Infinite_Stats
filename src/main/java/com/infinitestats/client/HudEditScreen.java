package com.infinitestats.client;

import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * HUD 编辑屏幕 — 显示光标，允许拖拽移动 HUD 面板
 * 透明背景，不暂停游戏
 */
public final class HudEditScreen extends Screen {

    private static final int BG_COLOR = 0x90101018;
    private static final int BORDER_COLOR = 0x403B82F6;
    private static final int EDIT_BORDER = 0xFFF59E0B;
    private static final int TEXT_LABEL = 0xFF94A3B8;
    private static final int TEXT_POINTS = 0xFFFFD166;
    private static final int TEXT_LEVEL = 0xFF60A5FA;
    private static final int BAR_XP_BG = 0x40252535;
    private static final int BAR_XP = 0xFF4ADE80;
    private static final int BAR_MANA_BG = 0x40252535;
    private static final int BAR_MANA = 0xFFA78BFA;
    private static final int PANEL_WIDTH = 140;
    private static final int HINT_COLOR = 0x80FFFFFF;


    private boolean dragging = false;
    private int dragStartMouseX = 0;
    private int dragStartMouseY = 0;
    private int dragStartHudX = 0;
    private int dragStartHudY = 0;

    public HudEditScreen() {
        super(Component.translatable("message.infinitestats.hud_edit_enter"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 不画暗色背景 → 游戏世界透过来
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            onClose();
            return;
        }

        var statsOpt = mc.player.getCapability(PlayerStatsProvider.PLAYER_STATS);
        if (!statsOpt.isPresent()) return;
        PlayerStats stats = statsOpt.orElse(null);
        if (stats == null) return;

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int panelHeight = getPanelHeight(stats);

        // 拖拽中 → 实时更新位置
        if (dragging) {
            ClientSettings.hudX = dragStartHudX + mouseX - dragStartMouseX;
            ClientSettings.hudY = dragStartHudY + mouseY - dragStartMouseY;
        }

        // 边界保护
        int x = Math.max(0, Math.min(ClientSettings.hudX, screenWidth - PANEL_WIDTH));
        int y = Math.max(0, Math.min(ClientSettings.hudY, screenHeight - panelHeight));
        ClientSettings.hudX = x;
        ClientSettings.hudY = y;

        // === 绘制 HUD 面板 ===
        // 金色编辑边框
        graphics.fill(x - 1, y - 1, x + PANEL_WIDTH + 1, y, EDIT_BORDER);
        graphics.fill(x - 1, y + panelHeight, x + PANEL_WIDTH + 1, y + panelHeight + 1, EDIT_BORDER);
        graphics.fill(x - 1, y, x, y + panelHeight, EDIT_BORDER);
        graphics.fill(x + PANEL_WIDTH, y, x + PANEL_WIDTH + 1, y + panelHeight, EDIT_BORDER);
        // 主体
        graphics.fill(x, y, x + PANEL_WIDTH, y + panelHeight, BG_COLOR);
        // 底部色条
        graphics.fill(x, y + panelHeight - 1, x + PANEL_WIDTH, y + panelHeight, BORDER_COLOR);
        graphics.fill(x, y, x + 2, y + panelHeight, BORDER_COLOR);

        // === 绘制内容 ===
        int cx = x + 6;
        int cy = y + 5;
        int lineH = 10;

        graphics.drawString(mc.font, "Lv." + stats.getLevel(), cx, cy, TEXT_LEVEL);
        cy += lineH + 2;

        long xpNeeded = stats.getXpForNextLevel();
        float xpPercent = xpNeeded > 0 ? Mth.clamp((float) stats.getExperience() / xpNeeded, 0, 1) : 0;
        int barWidth = 80;
        int barHeight = 6;
        graphics.fill(cx, cy, cx + barWidth, cy + barHeight, BAR_XP_BG);
        if (xpPercent > 0) {
            int filled = Math.max(1, (int) (xpPercent * barWidth));
            graphics.fill(cx, cy, cx + filled, cy + barHeight, BAR_XP);
        }
        graphics.drawString(mc.font, stats.getExperience() + "/" + xpNeeded, cx + barWidth + 4, cy - 1, TEXT_LABEL);
        cy += barHeight + 4;

        if (stats.getAvailablePoints() > 0) {
            graphics.drawString(mc.font, stats.getAvailablePoints() + " pts", cx, cy, TEXT_POINTS);
            cy += lineH + 2;
        }

        float maxMana = stats.getMaxMana();
        if (maxMana > 0) {
            float manaPercent = Mth.clamp(stats.getCurrentMana() / maxMana, 0, 1);
            graphics.fill(cx, cy, cx + barWidth, cy + 5, BAR_MANA_BG);
            if (manaPercent > 0) {
                int filled = Math.max(1, (int) (manaPercent * barWidth));
                graphics.fill(cx, cy, cx + filled, cy + 5, BAR_MANA);
            }
            graphics.drawString(mc.font, (int) stats.getCurrentMana() + "/" + (int) maxMana, cx + barWidth + 4, cy - 2, TEXT_LABEL);
        }

        // === 提示文字 ===
        String hint1 = "拖拽面板移动 · ESC 保存退出";
        String hint2 = "X=" + x + " Y=" + y;
        graphics.drawCenteredString(mc.font, hint1, screenWidth / 2, screenHeight - 28, HINT_COLOR);
        graphics.drawCenteredString(mc.font, hint2, screenWidth / 2, screenHeight - 16, 0xFFF59E0B);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(mouseX, mouseY, button);

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return true;

        var statsOpt = mc.player.getCapability(PlayerStatsProvider.PLAYER_STATS);
        if (!statsOpt.isPresent()) return true;
        PlayerStats stats = statsOpt.orElse(null);
        if (stats == null) return true;

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int panelHeight = getPanelHeight(stats);

        int x = Math.max(0, Math.min(ClientSettings.hudX, screenWidth - PANEL_WIDTH));
        int y = Math.max(0, Math.min(ClientSettings.hudY, screenHeight - panelHeight));

        // 检查点击是否在面板内
        if (mouseX >= x && mouseX <= x + PANEL_WIDTH && mouseY >= y && mouseY <= y + panelHeight) {
            dragging = true;
            dragStartMouseX = (int) mouseX;
            dragStartMouseY = (int) mouseY;
            dragStartHudX = ClientSettings.hudX;
            dragStartHudY = ClientSettings.hudY;
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && dragging) {
            dragging = false;
            ClientSettings.save();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_E) {
            ClientSettings.save();
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        ClientSettings.save();
        super.onClose();
    }

    private static int getPanelHeight(PlayerStats stats) {
        int pointLine = stats.getAvailablePoints() > 0 ? 1 : 0;
        int manaLine = stats.getMaxMana() > 0 ? 1 : 0;
        return 10 + 10 + 8 + pointLine * 11 + manaLine * 9 + 6;
    }
}
