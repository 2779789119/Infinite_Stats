package com.infinitestats.client;

import com.infinitestats.crafting.PortableCraftingMenu;
import com.infinitestats.network.NetworkHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class PortableCraftingScreen extends AbstractContainerScreen<PortableCraftingMenu> {

    @Override
    public boolean isPauseScreen() {
        // 非暂停界面：打开随身工作台时世界继续运行，避免配套熔炉/冶炼等后台进程被冻结。
        return false;
    }

    private static final ResourceLocation CRAFTING_LOCATION =
            new ResourceLocation("minecraft", "textures/gui/container/crafting_table.png");

    public PortableCraftingScreen(PortableCraftingMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        // 倍率按钮放在界面右上角，使用原版按钮画风（widgets.png 背景）
        int bx = leftPos + imageWidth - 18;
        int by = topPos + 8;
        this.addRenderableWidget(makeButton(bx, by,
                b -> NetworkHandler.CHANNEL.sendToServer(
                        new NetworkHandler.CraftingMultiplierPacket(true)),
                Component.literal("+"),
                Component.translatable("gui.infinitestats.crafting.cost_tip",
                        menu.getMultiplierCost())));
        this.addRenderableWidget(makeButton(bx, by + 20,
                b -> NetworkHandler.CHANNEL.sendToServer(
                        new NetworkHandler.CraftingMultiplierPacket(false)),
                Component.literal("-"),
                Component.translatable("gui.infinitestats.crafting.refund_tip",
                        menu.getMultiplierCost())));
    }

    private Button makeButton(int x, int y, Button.OnPress press, Component text, Component tip) {
        Button b = Button.builder(text, press).pos(x, y).size(16, 16).build();
        b.setTooltip(Tooltip.create(tip));
        return b;
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        gfx.blit(CRAFTING_LOCATION, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics gfx, int mouseX, int mouseY) {
        gfx.drawString(font, Component.translatable("container.crafting"), 28, 6, 4210752, false);
        gfx.drawString(font, Component.translatable("container.inventory"), 8, 72, 4210752, false);
        // 显示当前随身工作台物品倍率
        gfx.drawString(font, Component.translatable("gui.infinitestats.crafting.multiplier",
                menu.getCraftingMultiplier()), 114, 8, 4210752, false);
    }
}
