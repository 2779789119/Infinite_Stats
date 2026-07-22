package com.infinitestats.client;

import com.infinitestats.furnace.PortableFurnaceMenu;
import com.infinitestats.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

public class PortableFurnaceScreen extends AbstractContainerScreen<PortableFurnaceMenu> {

    @Override
    public boolean isPauseScreen() {
        // 非暂停界面：打开熔炉时世界继续运行，冶炼进度与火焰动画实时更新，
        // 否则单人模式下世界被冻结，火焰不再变化、进度条不动，看起来像“没在烧”。
        return false;
    }

    private static final ResourceLocation FURNACE_LOCATION =
            new ResourceLocation("minecraft", "textures/gui/container/furnace.png");

    private Button speedUpButton;
    private Button speedDownButton;
    private Button fuelBufferButton;

    public PortableFurnaceScreen(PortableFurnaceMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 166; // 与原版熔炉一致
    }

    @Override
    protected void init() {
        super.init();
        // 加快速度（消耗点数）—— 顶部右侧两个并排按钮（原版画风）
        this.speedUpButton = this.addRenderableWidget(Button.builder(
                        Component.literal("+"),
                        b -> onSpeed(true))
                .pos(leftPos + 140, topPos + 6).size(16, 16).build());
        this.speedDownButton = this.addRenderableWidget(Button.builder(
                        Component.literal("-"),
                        b -> onSpeed(false))
                .pos(leftPos + 158, topPos + 6).size(16, 16).build());

        // 矿石储备箱 —— 点击打开类似箱子的 GUI，输入槽为空时自动取下一种矿石
        this.fuelBufferButton = this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.infinitestats.furnace.fuel_buffer"),
                        b -> openFuelBuffer())
                .pos(leftPos + 138, topPos + 24).size(36, 16).build());
    }

    private void openFuelBuffer() {
        NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.FurnaceFuelOpenPacket());
    }

    private void onSpeed(boolean increase) {
        NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.FurnaceSpeedPacket(increase));
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        // 直接 blit 完整 furnace.png 作为背景（高度 166，与原版一致）
        gfx.blit(FURNACE_LOCATION, x, y, 0, 0, imageWidth, imageHeight);

        // 火焰（燃料燃烧进度）—— 原版 FurnaceScreen 位置
        if (menu.isLit()) {
            int l = (int) (14 * menu.getLitProgress());
            // 轻微抖动，强调“正在燃烧”，避免燃料耐久长时火焰高度几乎不变而误以为没在烧
            long tick = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0;
            int flicker = (tick % 8 < 4) ? 0 : 1;
            l = Mth.clamp(l + flicker, 1, 14);
            if (l > 0) {
                gfx.blit(FURNACE_LOCATION, x + 56, y + 36 + 12 - l, 176, 12 - l, 14, l + 1);
            }
        }

        // 进度箭头 —— 原版 FurnaceScreen 位置（单输入）
        int i = (int) (24 * menu.getCookProgress(0));
        if (i > 0) {
            gfx.blit(FURNACE_LOCATION, x + 79, y + 34, 176, 14, i + 1, 16);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics gfx, int mouseX, int mouseY) {
        // 原版标题
        gfx.drawString(font, Component.translatable("container.furnace"), 8, 6, 4210752, false);
        // 原版物品栏标签
        gfx.drawString(font, Component.translatable("container.inventory"), 8, this.imageHeight - 96 + 2, 4210752, false);

        // 加速状态（一行紧凑显示，放在标题右侧、按钮左侧）
        int spd = menu.getSpeedMultiplier();
        int lvl = menu.getSpeedLevel();
        int cost = menu.getSpeedCost();
        String status = "x" + spd + "  Lv." + lvl + "  " + cost + "点/级";
        gfx.drawString(font, Component.literal(status), 40, 6, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        // 按钮悬停时显示详细 tooltip
        if (speedUpButton != null && speedUpButton.isMouseOver(mouseX, mouseY)) {
            graphics.renderTooltip(font,
                    Component.translatable("gui.infinitestats.furnace.cost_tip", menu.getSpeedCost()),
                    mouseX, mouseY);
        } else if (speedDownButton != null && speedDownButton.isMouseOver(mouseX, mouseY)) {
            graphics.renderTooltip(font,
                    Component.translatable("gui.infinitestats.furnace.refund_tip", menu.getSpeedCost()),
                    mouseX, mouseY);
        } else if (fuelBufferButton != null && fuelBufferButton.isMouseOver(mouseX, mouseY)) {
            graphics.renderTooltip(font,
                    Component.translatable("gui.infinitestats.furnace.fuel_buffer_tip"),
                    mouseX, mouseY);
        }
    }
}
