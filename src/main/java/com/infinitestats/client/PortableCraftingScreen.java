package com.infinitestats.client;

import com.infinitestats.compat.jei.PortableCraftingRecipeTransferHandler;
import com.infinitestats.crafting.PortableCraftingMenu;
import com.infinitestats.network.NetworkHandler;
import com.infinitestats.stats.PlayerStats;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * 随身工作台界面。
 * <p>
 * 布局与随身熔炉保持一致：左侧是合成区与背包，右侧一列纵排功能按钮；
 * 面板、槽位全部程序化绘制（不贴原版 GUI 贴图），因此按钮不会因为界面太窄、
 * 贴图被裁切或资源包替换而看不见。合成箭头取自本模组自带的合成台贴图。
 */
public class PortableCraftingScreen extends AbstractContainerScreen<PortableCraftingMenu> {

    /** 箭头所在贴图（与本模组其它 GUI 同一套扁平画风）。 */
    private static final ResourceLocation CRAFTING_TABLE =
            new ResourceLocation("infinitestats", "textures/gui/container/crafting_table.png");
    private static final int ARROW_U = 90;
    private static final int ARROW_V = 35;
    private static final int ARROW_W = 22;
    private static final int ARROW_H = 15;

    /** 右栏几何：与随身熔炉同一列（起点 184、宽 106，两端各留 10 像素）。 */
    private static final int COL_X = 184;
    private static final int COL_W = 106;
    private static final int PLUS_X = 266;
    private static final int BTN_H = 18;

    private Button outputButton;
    private Button multiplierUp;
    private Button multiplierDown;

    public PortableCraftingScreen(PortableCraftingMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 300;
        this.imageHeight = 166;
        this.inventoryLabelY = 72;
    }

    @Override
    public boolean isPauseScreen() {
        // 非暂停界面：打开随身工作台时世界继续运行，避免配套熔炉/冶炼等后台进程被冻结。
        return false;
    }

    private Component text(String key, Object... args) {
        return Component.translatable("gui.infinitestats.crafting." + key, args);
    }

    private Button button(Component label, int x, int y, int width, Runnable action, Component tip) {
        return addRenderableWidget(Button.builder(label, b -> action.run())
                .bounds(leftPos + x, topPos + y, width, BTN_H)
                .tooltip(Tooltip.create(tip)).build());
    }

    @Override
    protected void init() {
        super.init();
        // 先把控件建好，再做其它初始化：后续任何一步出问题都不该让整列按钮消失。
        multiplierDown = button(Component.literal("-"), COL_X, 20, 24,
                () -> NetworkHandler.CHANNEL.sendToServer(
                        new NetworkHandler.CraftingMultiplierPacket(false, multiplierStep())),
                text("refund_tip", menu.getMultiplierCost()));
        multiplierUp = button(Component.literal("+"), PLUS_X, 20, 24,
                () -> NetworkHandler.CHANNEL.sendToServer(
                        new NetworkHandler.CraftingMultiplierPacket(true, multiplierStep())),
                text("cost_tip", menu.getMultiplierCost()));
        outputButton = button(text("output_bag"), COL_X, 74, COL_W,
                () -> NetworkHandler.CHANNEL.sendToServer(
                        new NetworkHandler.CraftingOutputModePacket()),
                text("output_bag_tip"));
        button(text("priority"), COL_X, 96, COL_W,
                () -> {
                    if (minecraft != null) {
                        minecraft.setScreen(new NetworkPriorityScreen(
                                this, PlayerStats.SCOPE_CRAFTING));
                    }
                },
                Component.translatable("gui.infinitestats.network.priority_tip_crafting"));
        // 预加载存储网络物品列表（用于 JEI 一键转移校验）
        try {
            PortableCraftingRecipeTransferHandler.requestNetworkItems();
        } catch (Throwable ignored) {
            // JEI 缺失或网络未就绪时忽略：界面与按钮保持可用
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        multiplierUp.active = menu.canMultiplyUp();
        multiplierDown.active = menu.getCraftingMultiplier() > 1;
        multiplierUp.setTooltip(Tooltip.create(text(
                menu.canMultiplyUp() ? "cost_tip" : "upgrade_unavailable",
                menu.getMultiplierCost())));
        multiplierDown.setTooltip(Tooltip.create(
                text("refund_tip", menu.getMultiplierCost())));
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        if (this.outputButton != null) {
            boolean toStorage = menu.isOutputToStorage();
            this.outputButton.setMessage(text(toStorage ? "output_storage" : "output_bag"));
            this.outputButton.setTooltip(Tooltip.create(
                    text(toStorage ? "output_storage_tip" : "output_bag_tip")));
        }
        super.render(gfx, mouseX, mouseY, partialTick);
    }

    /** Shift 按住时一次调整 10 级倍率，否则 1 级。 */
    private static int multiplierStep() {
        return hasShiftDown() ? 10 : 1;
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        // 与随身熔炉同一套程序化面板：外框 / 白描边 / 底板 / 中缝分隔线 / 槽位
        gfx.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF373737);
        gfx.fill(leftPos + 1, topPos + 1,
                leftPos + imageWidth - 1, topPos + imageHeight - 1, 0xFFFFFFFF);
        gfx.fill(leftPos + 3, topPos + 3,
                leftPos + imageWidth - 3, topPos + imageHeight - 3, 0xFFC6C6C6);
        gfx.fill(leftPos + 176, topPos + 7,
                leftPos + 177, topPos + imageHeight - 7, 0xFF909090);
        for (Slot slot : menu.slots) {
            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            gfx.fill(x - 1, y - 1, x + 17, y + 17, 0xFFFFFFFF);
            gfx.fill(x - 1, y - 1, x + 16, y + 16, 0xFF373737);
            gfx.fill(x, y, x + 16, y + 16, 0xFF8B8B8B);
        }
        // 合成箭头
        gfx.blit(CRAFTING_TABLE, leftPos + ARROW_U, topPos + ARROW_V,
                ARROW_U, ARROW_V, ARROW_W, ARROW_H);
    }

    private void label(GuiGraphics gfx, Component label, int x, int y, int width, int color) {
        gfx.drawString(font, font.plainSubstrByWidth(label.getString(), width), x, y, color, false);
    }

    @Override
    protected void renderLabels(GuiGraphics gfx, int mouseX, int mouseY) {
        label(gfx, title, 8, 6, 160, 0x404040);
        label(gfx, playerInventoryTitle, 8, inventoryLabelY, 160, 0x404040);

        // 右栏：倍率
        label(gfx, text("multiplier_label"), COL_X, 8, COL_W, 0x404040);
        Component multiplier = text("multiplier", menu.getCraftingMultiplier());
        label(gfx, multiplier, COL_X + 24 + (PLUS_X - COL_X - 24 - font.width(multiplier)) / 2,
                25, PLUS_X - COL_X - 24, 0x404040);
        label(gfx, text("cost_hint", menu.getMultiplierCost()), COL_X, 42, COL_W, 0x666666);

        // 右栏：存储
        label(gfx, text("storage_label"), COL_X, 62, COL_W, 0x404040);
        label(gfx, text("jei_hint"), COL_X, 126, COL_W, 0x777777);
    }
}
