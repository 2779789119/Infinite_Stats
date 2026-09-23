package com.infinitestats.client;

import com.infinitestats.furnace.PortableFurnaceMenu;
import com.infinitestats.network.NetworkHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** 熔炼与背包在左侧，仓库、加速和网络操作集中在右侧。 */
public class PortableFurnaceScreen extends AbstractContainerScreen<PortableFurnaceMenu> {
    private static final ResourceLocation FURNACE =
            new ResourceLocation("minecraft", "textures/gui/container/furnace.png");
    private Button speedUp;
    private Button speedDown;
    private Button collect;
    private Button queue;
    private Button products;

    public PortableFurnaceScreen(PortableFurnaceMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = 300;
        imageHeight = 222;
        inventoryLabelY = 124;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private Component text(String key, Object... args) {
        return Component.translatable("gui.infinitestats.furnace." + key, args);
    }

    private Button button(Component label, int x, int y, int width, Runnable action, Component tip) {
        return addRenderableWidget(Button.builder(label, b -> action.run())
                .bounds(leftPos + x, topPos + y, width, 18)
                .tooltip(Tooltip.create(tip)).build());
    }

    @Override
    protected void init() {
        super.init();
        collect = button(text("collect"), 8, 102, 160, () -> {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, PortableFurnaceMenu.COLLECT_PRODUCTS);
            }
        }, text("collect_tip"));
        queue = button(text("fuel_buffer"), 184, 20, 106,
                () -> NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.FurnaceFuelOpenPacket()), text("fuel_buffer_tip"));
        products = button(text("product_buffer"), 184, 42, 106,
                () -> NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.FurnaceProductOpenPacket()), text("product_buffer_tip"));
        button(text("ore_priority"), 184, 64, 106,
                () -> NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.FurnaceOrePriorityOpenPacket()), text("ore_priority_tip"));
        speedDown = button(Component.literal("-"), 184, 108, 24,
                () -> NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.FurnaceSpeedPacket(false)), text("refund_tip", menu.getSpeedCost()));
        speedUp = button(Component.literal("+"), 266, 108, 24,
                () -> NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.FurnaceSpeedPacket(true)), text("cost_tip", menu.getSpeedCost()));
        button(text("network_ore"), 184, 154, 106,
                () -> NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.FurnaceRSRefillOrePacket()), text("rs_ore_tip"));
        button(text("network_fuel"), 184, 175, 106,
                () -> NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.FurnaceRSRefillFuelPacket()), text("rs_fuel_tip"));
        button(text("network_deposit"), 184, 196, 106,
                () -> NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.FurnaceRSDepositPacket()), text("rs_deposit_tip"));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        speedUp.active = menu.canUpgrade();
        speedDown.active = menu.getSpeedLevel() > 0;
        speedUp.setTooltip(Tooltip.create(text(menu.canUpgrade() ? "cost_tip" : "upgrade_unavailable", menu.getSpeedCost())));
        speedDown.setTooltip(Tooltip.create(text("refund_tip", menu.getSpeedCost())));
        collect.active = menu.getProductSlots() > 0 || menu.getBulkAmount(2) > 0;
        queue.setMessage(text("queue_count", menu.getQueuedSlots()));
        products.setMessage(text("products_count", menu.getProductSlots()));
    }

    @Override
    protected java.util.List<Component> getTooltipFromContainerItem(net.minecraft.world.item.ItemStack stack) {
        return BulkCountRenderer.withAmount(super.getTooltipFromContainerItem(stack), menu, hoveredSlot);
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        gfx.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF373737);
        gfx.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, 0xFFFFFFFF);
        gfx.fill(leftPos + 3, topPos + 3, leftPos + imageWidth - 3, topPos + imageHeight - 3, 0xFFC6C6C6);
        gfx.fill(leftPos + 176, topPos + 7, leftPos + 177, topPos + imageHeight - 7, 0xFF909090);
        for (Slot slot : menu.slots) {
            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            gfx.fill(x - 1, y - 1, x + 17, y + 17, 0xFFFFFFFF);
            gfx.fill(x - 1, y - 1, x + 16, y + 16, 0xFF373737);
            gfx.fill(x, y, x + 16, y + 16, 0xFF8B8B8B);
        }
        gfx.blit(FURNACE, leftPos + 78, topPos + 49, 79, 34, 24, 17);
        int progress = Mth.clamp((int) (24 * menu.getCookProgress(0)), 0, 24);
        if (progress > 0) gfx.blit(FURNACE, leftPos + 78, topPos + 49, 176, 14, progress, 16);
        gfx.blit(FURNACE, leftPos + 47, topPos + 50, 56, 36, 14, 14);
        if (menu.isLit()) {
            int flame = Mth.clamp((int) (14 * menu.getLitProgress()), 1, 14);
            gfx.blit(FURNACE, leftPos + 47, topPos + 64 - flame, 176, 14 - flame, 14, flame);
        }
    }

    private void label(GuiGraphics gfx, Component label, int x, int y, int width, int color) {
        gfx.drawString(font, font.plainSubstrByWidth(label.getString(), width), x, y, color, false);
    }

    @Override
    protected void renderLabels(GuiGraphics gfx, int mouseX, int mouseY) {
        label(gfx, title, 8, 8, 160, 0x404040);
        label(gfx, text("input"), 8, 35, 35, 0x404040);
        label(gfx, text("fuel"), 8, 71, 35, 0x404040);
        label(gfx, text("output"), 105, 34, 60, 0x404040);
        label(gfx, text("auto_store"), 77, 73, 93, 0x666666);
        int status = menu.getWorkStatus();
        String state = switch (status) {
            case 1 -> "working";
            case 2 -> "no_fuel";
            case 3 -> "invalid_input";
            case 4 -> "output_full";
            default -> "idle";
        };
        label(gfx, text("state." + state), 8, 90, 160, status == 1 ? 0x25652C : status > 1 ? 0x963A1D : 0x555555);
        label(gfx, playerInventoryTitle, 8, inventoryLabelY, 160, 0x404040);
        label(gfx, text("storage"), 184, 8, 106, 0x404040);
        label(gfx, text("speed", menu.getSpeedMultiplier()), 184, 94, 106, 0x404040);
        label(gfx, text("level", menu.getSpeedLevel()), 212, 113, 50, 0x404040);
        label(gfx, text("cost", menu.getSpeedCost()), 184, 130, 106, 0x666666);
        label(gfx, text("network"), 184, 143, 106, 0x404040);
        BulkCountRenderer.render(gfx, font, menu);
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        renderBackground(gfx);
        super.render(gfx, mouseX, mouseY, partialTick);
        renderTooltip(gfx, mouseX, mouseY);
        if (isHovering(8, 90, 160, 10, mouseX, mouseY)) {
            gfx.renderTooltip(font, text("workflow_tip", menu.getQueuedSlots(), menu.getProductSlots()), mouseX, mouseY);
        }
    }
}
