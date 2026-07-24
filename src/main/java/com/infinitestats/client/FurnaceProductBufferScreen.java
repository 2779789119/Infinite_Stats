package com.infinitestats.client;

import com.infinitestats.furnace.FurnaceProductBufferMenu;
import com.infinitestats.network.NetworkHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class FurnaceProductBufferScreen extends AbstractContainerScreen<FurnaceProductBufferMenu> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation("minecraft", "textures/gui/container/generic_54.png");

    /** 箱子 UI 尺寸：箱子背景 3 行（54 槽背景图）。 */
    private static final int BOX_BG_HEIGHT = 3 * 18 + 17; // 71

    public FurnaceProductBufferScreen(FurnaceProductBufferMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = BOX_BG_HEIGHT + 97; // = 168，与玩家背包组合高度一致
        this.inventoryLabelY = this.imageHeight - 94; // = 74
        this.titleLabelY = 6;
    }

    @Override
    protected void init() {
        super.init();
        // 成品仓 → 网络 按钮：将成品储备箱中的所有成品存入 RS 网络
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.infinitestats.furnace.product_rs_deposit"),
                        b -> NetworkHandler.CHANNEL.sendToServer(
                                new NetworkHandler.FurnaceProductRSDepositPacket()))
                .pos(leftPos + 130, topPos + 3).size(40, 16)
                .tooltip(Tooltip.create(Component.translatable("gui.infinitestats.furnace.product_rs_deposit_tip")))
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, TEXTURE);

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        // 上半：成品仓 3 行格子背景（从纹理顶部截取 rows*18+17）
        int topHeight = BOX_BG_HEIGHT; // 3*18+17 = 71
        graphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, topHeight);
        // 下半：玩家背包背景（从纹理 y=125 截取，即原版 6行容器之后的位置）
        graphics.blit(TEXTURE, x, y + topHeight, 0, 125, this.imageWidth, this.imageHeight - topHeight);
    }
}
