package com.infinitestats.client;

import com.infinitestats.furnace.FurnaceFuelBufferMenu;
import com.infinitestats.network.NetworkHandler;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 矿石储备箱界面：外观与普通箱子一致（3 行 × 9 列），用于存放待熔炼的矿石。
 */
@OnlyIn(Dist.CLIENT)
public class FurnaceFuelBufferScreen extends AbstractContainerScreen<FurnaceFuelBufferMenu> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("minecraft", "textures/gui/container/generic_54.png");

    private final int rows = FurnaceFuelBufferMenu.ROWS;

    public FurnaceFuelBufferScreen(FurnaceFuelBufferMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 194;
        this.inventoryLabelY = 74;
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(Button.builder(Component.translatable("gui.infinitestats.furnace.back"),
                        b -> NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.FurnaceOpenPacket()))
                .bounds(leftPos + 8, topPos + 171, 76, 18).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.infinitestats.furnace.ore_priority"),
                        b -> NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.FurnaceOrePriorityOpenPacket()))
                .bounds(leftPos + 92, topPos + 171, 76, 18)
                .tooltip(Tooltip.create(Component.translatable("gui.infinitestats.furnace.ore_priority_tip"))).build());
    }

    @Override
    public void onClose() {
        NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.FurnaceOpenPacket());
    }

    @Override
    public boolean isPauseScreen() {
        // 非暂停界面：打开矿石仓时世界继续运行，熔炉的自动投料（pullInputFromBuffer）
        // 和冶炼进度才会持续驱动，否则单人模式下世界被冻结，矿石看起来“不会自动放入输入槽”。
        return false;
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);
        super.render(gfx, mouseX, mouseY, partialTick);
        this.renderTooltip(gfx, mouseX, mouseY);
    }

    @Override
    protected java.util.List<Component> getTooltipFromContainerItem(net.minecraft.world.item.ItemStack stack) {
        return BulkCountRenderer.withAmount(super.getTooltipFromContainerItem(stack), menu, hoveredSlot);
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, TEXTURE);

        // 上半：矿石仓 3 行格子背景（从纹理顶部截取 rows*18+17）
        int topHeight = this.rows * 18 + 17;
        gfx.blit(TEXTURE, x, y, 0, 0, this.imageWidth, topHeight);

        // 下半：玩家背包背景（从纹理 y=125 截取，即原版 6行容器+分隔线之后的位置）
        gfx.blit(TEXTURE, x, y + topHeight, 0, 125, this.imageWidth, 97);
        gfx.fill(x, y + 166, x + imageWidth, y + imageHeight, 0xFF373737);
        gfx.fill(x + 2, y + 166, x + imageWidth - 2, y + imageHeight - 2, 0xFFC6C6C6);
    }

    @Override
    protected void renderLabels(GuiGraphics gfx, int mouseX, int mouseY) {
        super.renderLabels(gfx, mouseX, mouseY);
        BulkCountRenderer.render(gfx, font, menu);
    }
}
