package com.infinitestats.client;

import com.infinitestats.network.NetworkHandler;
import com.infinitestats.network.WaypointActionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 传送点重命名对话框
 */
public class WaypointRenameScreen extends Screen {

    private final Screen parent;
    private final String oldName;
    private EditBox nameField;
    private int leftPos, topPos;

    public WaypointRenameScreen(Screen parent, String oldName) {
        super(Component.translatable("gui.infinitestats.wp_rename_title"));
        this.parent = parent;
        this.oldName = oldName;
    }

    @Override
    protected void init() {
        super.init();
        int w = 240, h = 100;
        leftPos = (width - w) / 2;
        topPos = (height - h) / 2;

        nameField = new EditBox(font, leftPos + 20, topPos + 36, w - 40, 18, Component.literal(oldName));
        nameField.setMaxLength(32);
        nameField.setValue(oldName);
        nameField.setFocused(true);
        addRenderableWidget(nameField);

        Button confirm = Button.builder(Component.translatable("button.infinitestats.wp_confirm"),
                b -> confirm()).bounds(leftPos + 20, topPos + 64, (w - 50) / 2, 20).build();
        Button cancel = Button.builder(Component.translatable("button.infinitestats.wp_cancel"),
                b -> close()).bounds(leftPos + 30 + (w - 50) / 2, topPos + 64, (w - 50) / 2, 20).build();
        addRenderableWidget(confirm);
        addRenderableWidget(cancel);
    }

    private void confirm() {
        String newName = nameField.getValue().trim();
        if (!newName.isEmpty() && !newName.equals(oldName)) {
            NetworkHandler.CHANNEL.sendToServer(new WaypointActionPacket(
                    WaypointActionPacket.Action.RENAME, oldName, newName, null, 0, 0, 0, 0, 0));
        }
        close();
    }

    private void close() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int w = 240, h = 100;
        graphics.fill(leftPos - 2, topPos - 2, leftPos + w + 2, topPos + h + 2, 0x403B82F6);
        graphics.fill(leftPos - 1, topPos - 1, leftPos + w + 1, topPos + h + 1, 0xFF1E293B);
        graphics.fill(leftPos, topPos, leftPos + w, topPos + h, 0xE81A1A2E);
        graphics.drawCenteredString(font, Component.translatable("gui.infinitestats.wp_rename_title").getString(),
                leftPos + w / 2, topPos + 12, 0xFFFFD166);
        graphics.drawString(font, Component.translatable("gui.infinitestats.wp_rename_old", oldName).getString(),
                leftPos + 20, topPos + 22, 0xFF94A3B8);

        for (var renderable : this.renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            close();
            return true;
        }
        if (keyCode == 257 && nameField.isFocused()) { // Enter
            confirm();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
