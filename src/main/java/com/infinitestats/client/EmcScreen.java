package com.infinitestats.client;

import com.infinitestats.emc.EmcMenu;
import com.infinitestats.emc.EmcPlayerDataProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

/**
 * EMC 转化桌 GUI — 组件编排器
 *
 * 布局结构：
 * ┌──────────────────────────────────────────┐
 * │  [搜索栏]          EMC: 12,345  ┌────┐  │ ← 顶部栏
 * ├──────────┬───────────────────────┤学习├──┤
 * │ 已学物品  │  选中物品详情          │槽  │  │ ← 列表+详情区
 * │ (滚动列表)│  [图标] 物品名        └────┘  │
 * │          │  EMC: 256                    │
 * │  ▪ 石头   │  [x1] [x10] [x64]           │
 * │  ▪ 钻石   │                              │
 * ├──────────┴──────────────────────────────┤
 * │ [玩家物品栏]                             │ ← 玩家背包
 * └──────────────────────────────────────────┘
 *
 * 子组件：
 * - EmcItemListPanel : 左侧物品列表（搜索过滤、滚动、选中）
 * - EmcDetailPanel   : 右侧详情面板（大图标、EMC值、提取按钮）
 */
public class EmcScreen extends AbstractContainerScreen<EmcMenu> {

    // ==================== 子组件 ====================

    private EmcItemListPanel itemListPanel;
    private EmcDetailPanel detailPanel;

    // ==================== 搜索框 ====================

    private EditBox searchBox;

    public EmcScreen(EmcMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = EmcLayout.GUI_WIDTH;
        this.imageHeight = EmcLayout.GUI_HEIGHT;
    }

    // ==================== 初始化 ====================

    @Override
    protected void init() {
        super.init();

        // 初始化子组件
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.getCapability(EmcPlayerDataProvider.EMC_PLAYER_DATA).ifPresent(data -> {
                itemListPanel = new EmcItemListPanel(font, data);
                itemListPanel.updatePosition(leftPos, topPos);
                itemListPanel.updateFilteredList();
            });
        }
        if (itemListPanel == null) {
            // 兜底：如果 capability 不可用，用空面板
            itemListPanel = new EmcItemListPanel(font, null);
        }

        detailPanel = new EmcDetailPanel(font);
        detailPanel.updatePosition(leftPos, topPos);

        // 搜索框
        searchBox = new EditBox(font,
                leftPos + EmcLayout.SEARCH_X,
                topPos + EmcLayout.SEARCH_Y,
                EmcLayout.SEARCH_WIDTH,
                EmcLayout.SEARCH_HEIGHT,
                Component.translatable("screen.infinitestats.emc.search"));
        searchBox.setMaxLength(50);
        searchBox.setBordered(true);
        searchBox.setResponder(itemListPanel::setSearchText);
        addRenderableWidget(searchBox);
    }

    // ==================== 渲染 ====================

    @Override
    protected void renderBg(GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        // 主面板背景
        gfx.fill(leftPos, topPos, leftPos + EmcLayout.GUI_WIDTH,
                topPos + EmcLayout.GUI_HEIGHT, EmcColors.BG_PANEL);

        // 顶部标题栏
        gfx.fill(leftPos, topPos, leftPos + EmcLayout.GUI_WIDTH,
                topPos + EmcLayout.HEADER_H, EmcColors.BG_HEADER);

        // EMC 余额
        String emcText = getClientEmcText();
        gfx.drawString(font, emcText,
                leftPos + EmcLayout.HEADER_EMC_LABEL_X,
                topPos + EmcLayout.HEADER_EMC_LABEL_Y,
                EmcColors.TEXT_EMC, false);

        // 左侧物品列表
        itemListPanel.updatePosition(leftPos, topPos);
        itemListPanel.render(gfx, mouseX, mouseY);

        // 右侧详情面板
        detailPanel.updatePosition(leftPos, topPos);
        detailPanel.render(gfx, mouseX, mouseY, itemListPanel.getSelectedItem());

        // 玩家物品栏标签
        gfx.drawString(font, Component.translatable("container.inventory"),
                leftPos + EmcLayout.PLAYER_INV_LABEL_X,
                topPos + EmcLayout.PLAYER_INV_LABEL_Y,
                EmcColors.TEXT_SECONDARY, false);

        // 学习槽
        renderLearnSlot(gfx, mouseX, mouseY);
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);
        super.render(gfx, mouseX, mouseY, partialTick);
        this.renderTooltip(gfx, mouseX, mouseY);

        // 悬停学习槽提示
        if (isHoveringLearnSlot(mouseX, mouseY)) {
            gfx.renderTooltip(font,
                    Component.translatable("screen.infinitestats.emc.learn_slot_hint"),
                    mouseX, mouseY);
        }
    }

    // ==================== 学习槽渲染 ====================

    private void renderLearnSlot(GuiGraphics gfx, int mouseX, int mouseY) {
        int lx = EmcLayout.learnX(leftPos);
        int ly = EmcLayout.learnY(topPos);
        int s = EmcLayout.LEARN_SIZE;
        boolean hovered = isHoveringLearnSlot(mouseX, mouseY);

        // 背景
        gfx.fill(lx - 1, ly - 1, lx + s - 1, ly + s - 1,
                hovered ? EmcColors.BG_LEARN_SLOT_HOVER : EmcColors.BG_LEARN_SLOT);

        // 金色边框
        int border = EmcColors.BORDER_LEARN_SLOT;
        gfx.fill(lx - 1, ly - 1, lx + s - 1, ly, border);       // 上
        gfx.fill(lx - 1, ly + s - 2, lx + s - 1, ly + s - 1, border); // 下
        gfx.fill(lx - 1, ly, lx, ly + s - 2, border);           // 左
        gfx.fill(lx + s - 2, ly, lx + s - 1, ly + s - 2, border); // 右

        // 学习图标
        gfx.drawString(font, "✦",
                lx + EmcLayout.LEARN_ICON_X,
                ly + EmcLayout.LEARN_ICON_Y,
                EmcColors.TEXT_GOLD, false);
    }

    private boolean isHoveringLearnSlot(int mouseX, int mouseY) {
        int lx = EmcLayout.learnX(leftPos);
        int ly = EmcLayout.learnY(topPos);
        return mouseX >= lx && mouseX < lx + EmcLayout.LEARN_SIZE
                && mouseY >= ly && mouseY < ly + EmcLayout.LEARN_SIZE;
    }

    // ==================== 鼠标事件 ====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX;
        int my = (int) mouseY;

        // 搜索框优先
        if (searchBox.isMouseOver(mouseX, mouseY)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (button == 0) {
            // 学习槽
            if (isHoveringLearnSlot(mx, my)) {
                return true;
            }

            // 右侧提取按钮
            if (detailPanel.handleExtractClick(mx, my, itemListPanel.getSelectedItem())) {
                return true;
            }

            // 左侧列表条目
            if (itemListPanel.mouseClicked(mx, my, button)) {
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return itemListPanel.mouseScrolled(mouseX, mouseY, delta);
    }

    // ==================== 键盘事件 ====================

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (searchBox.isFocused()) {
            return searchBox.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchBox.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                searchBox.setFocused(false);
                return true;
            }
            // 搜索框内上下键导航列表
            if (keyCode == GLFW.GLFW_KEY_DOWN) {
                itemListPanel.selectNext();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_UP) {
                itemListPanel.selectPrevious();
                return true;
            }
            return searchBox.keyPressed(keyCode, scanCode, modifiers);
        }

        // 非搜索框状态下的键盘导航
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            itemListPanel.selectNext();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            itemListPanel.selectPrevious();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ==================== 工具方法 ====================

    private String getClientEmcText() {
        var player = Minecraft.getInstance().player;
        if (player == null) return "0 EMC";
        long[] balance = {0};
        player.getCapability(EmcPlayerDataProvider.EMC_PLAYER_DATA)
                .ifPresent(d -> balance[0] = d.getEmcBalance());
        return formatEmc(balance[0]) + " EMC";
    }

    private static String formatEmc(long emc) {
        if (emc >= 1_000_000_000) return String.format("%.1fB", emc / 1_000_000_000.0);
        if (emc >= 1_000_000) return String.format("%.1fM", emc / 1_000_000.0);
        if (emc >= 1_000) return String.format("%.1fK", emc / 1_000.0);
        return String.valueOf(emc);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}