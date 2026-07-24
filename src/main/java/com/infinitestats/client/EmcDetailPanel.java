package com.infinitestats.client;

import com.infinitestats.emc.EmcDatabase;
import com.infinitestats.network.NetworkHandler;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * EMC 右侧详情面板
 * 负责：选中物品的大图标/名称/EMC 值显示、提取按钮、提取请求发送
 */
public class EmcDetailPanel {

    private static final int[] EXTRACT_COUNTS = {1, 10, 64};
    private static final String[] EXTRACT_LABELS = {"x1", "x10", "x64"};

    private final Font font;
    private int extractMode = 0; // 0=x1, 1=x10, 2=x64

    // 父级 GUI 坐标
    private int leftPos, topPos;

    public EmcDetailPanel(Font font) {
        this.font = font;
    }

    public void updatePosition(int leftPos, int topPos) {
        this.leftPos = leftPos;
        this.topPos = topPos;
    }

    // ==================== 提取模式 ====================

    public int getExtractMode() {
        return extractMode;
    }

    public void setExtractMode(int mode) {
        if (mode >= 0 && mode < EXTRACT_COUNTS.length) {
            this.extractMode = mode;
        }
    }

    private int getExtractCount() {
        return EXTRACT_COUNTS[extractMode];
    }

    // ==================== 渲染 ====================

    public void render(GuiGraphics gfx, int mouseX, int mouseY, ResourceLocation selectedItem) {
        int dl = EmcLayout.detailLeft(leftPos);
        int dt = EmcLayout.detailTop(topPos);

        if (selectedItem != null) {
            renderSelectedItem(gfx, mouseX, mouseY, dl, dt, selectedItem);
        } else {
            renderEmptyState(gfx, dl, dt);
        }
    }

    private void renderSelectedItem(GuiGraphics gfx, int mouseX, int mouseY,
            int dl, int dt, ResourceLocation itemId) {
        Item item = BuiltInRegistries.ITEM.get(itemId);
        long emcValue = EmcDatabase.getEmc(new ItemStack(item));

        // 大图标
        if (item != Items.AIR) {
            gfx.renderFakeItem(new ItemStack(item),
                    dl + EmcLayout.DETAIL_ICON_X,
                    dt + EmcLayout.DETAIL_ICON_Y);
        }

        // 名称
        String name = item != Items.AIR
                ? item.getDescription().getString() : itemId.toString();
        if (font.width(name) > EmcLayout.DETAIL_WIDTH) {
            name = font.plainSubstrByWidth(name, EmcLayout.DETAIL_WIDTH - 6) + "..";
        }
        gfx.drawString(font, name, dl, dt + EmcLayout.DETAIL_NAME_Y,
                EmcColors.TEXT_PRIMARY, false);

        // EMC 值
        String emcText = formatEmc(emcValue) + " EMC/个";
        gfx.drawString(font, emcText, dl, dt + EmcLayout.DETAIL_EMC_Y,
                EmcColors.TEXT_GOLD, false);

        // 提取按钮
        renderExtractButtons(gfx, mouseX, mouseY, dl, dt);
    }

    private void renderExtractButtons(GuiGraphics gfx, int mouseX, int mouseY,
            int dl, int dt) {
        int btnY = dt + EmcLayout.EXTRACT_BTN_Y;
        for (int m = 0; m < EXTRACT_LABELS.length; m++) {
            int btnX = dl + m * (EmcLayout.EXTRACT_BTN_W + EmcLayout.EXTRACT_BTN_GAP);
            boolean hovered = isMouseOverButton(mouseX, mouseY, btnX, btnY);
            int btnColor = (m == extractMode)
                    ? EmcColors.BG_BTN_HOVER
                    : (hovered ? EmcColors.BG_BTN_HOVER : EmcColors.BG_BTN);
            gfx.fill(btnX, btnY,
                    btnX + EmcLayout.EXTRACT_BTN_W,
                    btnY + EmcLayout.EXTRACT_BTN_H, btnColor);
            gfx.drawCenteredString(font, EXTRACT_LABELS[m],
                    btnX + EmcLayout.EXTRACT_BTN_W / 2,
                    btnY + EmcLayout.EXTRACT_BTN_TEXT_Y,
                    EmcColors.TEXT_PRIMARY);
        }
    }

    private void renderEmptyState(GuiGraphics gfx, int dl, int dt) {
        gfx.drawString(font,
                Component.translatable("screen.infinitestats.emc.no_selection").getString(),
                dl, dt + EmcLayout.HINT_Y, EmcColors.TEXT_SECONDARY, false);
        gfx.drawString(font,
                Component.translatable("screen.infinitestats.emc.hint").getString(),
                dl, dt + EmcLayout.HINT2_Y, EmcColors.TEXT_HINT, false);
        gfx.drawString(font,
                Component.translatable("screen.infinitestats.emc.hint2").getString(),
                dl, dt + EmcLayout.HINT3_Y, EmcColors.TEXT_HINT, false);
    }

    // ==================== 鼠标事件 ====================

    /**
     * 检查是否点击了提取按钮
     * @return true 如果点击了提取按钮并已处理
     */
    public boolean handleExtractClick(int mouseX, int mouseY, ResourceLocation selectedItem) {
        if (selectedItem == null) return false;

        int dl = EmcLayout.detailLeft(leftPos);
        int dt = EmcLayout.detailTop(topPos);
        int btnY = dt + EmcLayout.EXTRACT_BTN_Y;

        for (int m = 0; m < EXTRACT_LABELS.length; m++) {
            int btnX = dl + m * (EmcLayout.EXTRACT_BTN_W + EmcLayout.EXTRACT_BTN_GAP);
            if (isMouseOverButton(mouseX, mouseY, btnX, btnY)) {
                extractMode = m;
                sendExtractPacket(selectedItem);
                return true;
            }
        }
        return false;
    }

    private boolean isMouseOverButton(int mouseX, int mouseY, int btnX, int btnY) {
        return mouseX >= btnX && mouseX <= btnX + EmcLayout.EXTRACT_BTN_W
                && mouseY >= btnY && mouseY <= btnY + EmcLayout.EXTRACT_BTN_H;
    }

    private void sendExtractPacket(ResourceLocation itemId) {
        NetworkHandler.CHANNEL.sendToServer(
                new NetworkHandler.EmcExtractPacket(itemId.toString(), getExtractCount()));
    }

    // ==================== 工具方法 ====================

    private static String formatEmc(long emc) {
        if (emc >= 1_000_000_000) return String.format("%.1fB", emc / 1_000_000_000.0);
        if (emc >= 1_000_000) return String.format("%.1fM", emc / 1_000_000.0);
        if (emc >= 1_000) return String.format("%.1fK", emc / 1_000.0);
        return String.valueOf(emc);
    }
}