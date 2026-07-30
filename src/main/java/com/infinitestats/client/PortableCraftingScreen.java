package com.infinitestats.client;

import com.infinitestats.compat.jei.PortableCraftingRecipeTransferHandler;
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

    private Button outputButton;

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
        // 预加载存储网络物品列表（用于 JEI 一键转移校验）
        PortableCraftingRecipeTransferHandler.requestNetworkItems();
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

        // 成品去向切换按钮（小按钮）：背包 / 存储空间
        // 取料按钮已移除——JEI 一键转移配方（+）已支持从存储网络自动补料。
        int rx = leftPos + 119;
        int ry = topPos + 50;
        this.outputButton = this.addRenderableWidget(Button.builder(
                        Component.literal("包"),
                        b -> NetworkHandler.CHANNEL.sendToServer(
                                new NetworkHandler.CraftingOutputModePacket()))
                .pos(rx, ry).size(20, 16)
                .tooltip(Tooltip.create(Component.translatable("gui.infinitestats.crafting.output_bag_tip")))
                .build());
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        if (this.outputButton != null) {
            boolean toStorage = menu.isOutputToStorage();
            this.outputButton.setMessage(Component.literal(toStorage ? "储" : "包"));
            this.outputButton.setTooltip(Tooltip.create(Component.translatable(
                    toStorage ? "gui.infinitestats.crafting.output_storage_tip"
                              : "gui.infinitestats.crafting.output_bag_tip")));
        }
        super.render(gfx, mouseX, mouseY, partialTick);
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
