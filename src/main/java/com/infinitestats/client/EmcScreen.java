package com.infinitestats.client;

import com.infinitestats.emc.EmcDatabase;
import com.infinitestats.emc.EmcMenu;
import com.infinitestats.emc.EmcPlayerData;
import com.infinitestats.emc.EmcPlayerDataProvider;
import com.infinitestats.network.NetworkHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.*;
import java.util.stream.Collectors;

/**
 * EMC 转化桌 GUI — ProjectE 风格：左侧学习槽 + 搜索列表 + 提取
 *
 * ┌──────────────────────────────────────────┐
 * │  [搜索栏]          EMC: 12,345  ┌────┐  │ ← 标题栏
 * ├──────────┬───────────────────────┤学习├──┤
 * │ 已学物品  │  选中物品详情          │槽  │  │ ← 列表+详情区
 * │ (滚动列表)│  [图标] 物品名        └────┘  │
 * │          │  EMC: 256                    │
 * │  ▪ 石头   │  [提取 x1] [x10] [x64]      │
 * │  ▪ 钻石   │                              │
 * ├──────────┴──────────────────────────────┤
 * │ [玩家物品栏]                             │ ← 玩家背包
 * └──────────────────────────────────────────┘
 */
public class EmcScreen extends AbstractContainerScreen<EmcMenu> {

    // 颜色
    private static final int BG_PANEL = 0xC01A1A2E;
    private static final int BG_HEADER = 0xC0151536;
    private static final int BG_ENTRY = 0x50252535;
    private static final int BG_ENTRY_HOVER = 0x80353550;
    private static final int BG_ENTRY_SELECTED = 0x80404080;
    private static final int BG_BTN = 0xFF1A5334;
    private static final int BG_BTN_HOVER = 0xFF2D8A4E;
    private static final int BG_LEARN_SLOT = 0x80303050;
    private static final int BG_LEARN_SLOT_HOVER = 0x80404070;
    private static final int TEXT_PRIMARY = 0xFFE2E8F0;
    private static final int TEXT_SECONDARY = 0xFF94A3B8;
    private static final int TEXT_GOLD = 0xFFFFD166;
    private static final int TEXT_EMC = 0xFF60A5FA;
    private static final int TEXT_HINT = 0xFF64748B;

    private static final int LIST_WIDTH = 120;
    private static final int DETAIL_X = 135;
    private static final int DETAIL_WIDTH = 85;
    private static final int HEADER_H = 24;
    private static final int ENTRY_H = 22;

    private EditBox searchBox;
    private String searchText = "";

    private List<ResourceLocation> filteredItems = new ArrayList<>();
    private int scrollOffset;
    private int maxScroll;
    private int hoveredEntry = -1;
    private int selectedEntry = -1;
    private boolean scrollbarDragging;

    private int extractMode = 0; // 0=x1, 1=x10, 2=x64

    // 列表可视区域（相对 leftPos, topPos）
    private int listTop, listBottom;

    public EmcScreen(EmcMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = EmcMenu.GUI_WIDTH;
        this.imageHeight = EmcMenu.GUI_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        // leftPos, topPos 由 AbstractContainerScreen 自动计算

        // 搜索框
        searchBox = new EditBox(font, leftPos + 6, topPos + 6, LIST_WIDTH - 12, 14,
                Component.translatable("screen.infinitestats.emc.search"));
        searchBox.setMaxLength(50);
        searchBox.setBordered(true);
        searchBox.setValue(searchText);
        searchBox.setResponder(this::onSearchChanged);
        addRenderableWidget(searchBox);

        // 列表边界
        listTop = topPos + HEADER_H;
        listBottom = topPos + EmcMenu.PLAYER_INV_Y - 4;

        updateFilteredList();
    }

    // ==================== 数据 ====================

    private void onSearchChanged(String text) {
        searchText = text;
        scrollOffset = 0;
        selectedEntry = -1;
        updateFilteredList();
    }

    private void updateFilteredList() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            filteredItems.clear();
            return;
        }
        player.getCapability(EmcPlayerDataProvider.EMC_PLAYER_DATA).ifPresent(data -> {
            String lower = searchText.toLowerCase().trim();
            filteredItems = data.getLearnedItems().stream()
                    .filter(id -> {
                        if (lower.isEmpty()) return true;
                        Item item = BuiltInRegistries.ITEM.get(id);
                        String name = id.toString();
                        String displayName = item != Items.AIR
                                ? item.getDescription().getString().toLowerCase() : "";
                        return name.contains(lower) || displayName.contains(lower);
                    })
                    .sorted(Comparator.comparing(ResourceLocation::toString))
                    .collect(Collectors.toList());
        });

        int visible = (listBottom - listTop) / ENTRY_H;
        maxScroll = Math.max(0, filteredItems.size() - visible);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
    }

    // ==================== 渲染 ====================

    @Override
    protected void renderBg(GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        // 主面板背景
        gfx.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, BG_PANEL);

        // 标题栏
        gfx.fill(leftPos, topPos, leftPos + imageWidth, topPos + HEADER_H, BG_HEADER);

        // EMC 余额
        String emcText = getClientEmcText();
        gfx.drawString(font, emcText, leftPos + LIST_WIDTH + 6, topPos + 7, TEXT_EMC, true);

        // 已学列表背景
        gfx.fill(leftPos, listTop, leftPos + LIST_WIDTH, listBottom, 0x80101020);

        // 物品列表
        renderItemList(gfx, mouseX, mouseY);

        // 右侧详情
        renderDetail(gfx, mouseX, mouseY);

        // 物品栏标签
        gfx.drawString(font, Component.translatable("container.inventory"),
                leftPos + 8, topPos + EmcMenu.PLAYER_INV_Y - 10, TEXT_SECONDARY, false);

        // 学习槽背景（高亮边框，仿 ProjectE）
        int learnX = leftPos + EmcMenu.LEARN_X;
        int learnY = topPos + EmcMenu.LEARN_Y;
        boolean learnHover = isHoveringLearnSlot(mouseX, mouseY);
        gfx.fill(learnX - 1, learnY - 1, learnX + 17, learnY + 17,
                learnHover ? BG_LEARN_SLOT_HOVER : BG_LEARN_SLOT);
        // 金色边框提示
        int borderColor = 0xFFB8860B;
        gfx.fill(learnX - 1, learnY - 1, learnX + 17, learnY, borderColor);
        gfx.fill(learnX - 1, learnY + 16, learnX + 17, learnY + 17, borderColor);
        gfx.fill(learnX - 1, learnY, learnX, learnY + 16, borderColor);
        gfx.fill(learnX + 16, learnY, learnX + 17, learnY + 16, borderColor);
        // 学习图标（书本符号 ✦）
        gfx.drawString(font, "✦", learnX + 4, learnY + 3, TEXT_GOLD, false);
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);
        super.render(gfx, mouseX, mouseY, partialTick);
        this.renderTooltip(gfx, mouseX, mouseY);

        // 悬停学习槽时显示提示
        if (isHoveringLearnSlot(mouseX, mouseY)) {
            gfx.renderTooltip(font,
                    Component.translatable("screen.infinitestats.emc.learn_slot_hint"),
                    mouseX, mouseY);
        }
    }

    private boolean isHoveringLearnSlot(int mouseX, int mouseY) {
        int sx = leftPos + EmcMenu.LEARN_X;
        int sy = topPos + EmcMenu.LEARN_Y;
        return mouseX >= sx && mouseX < sx + 18 && mouseY >= sy && mouseY < sy + 18;
    }

    // ==================== 物品列表 ====================

    private void renderItemList(GuiGraphics gfx, int mouseX, int mouseY) {
        int visible = (listBottom - listTop) / ENTRY_H;
        hoveredEntry = -1;

        int startY = listTop + 2;
        for (int i = 0; i < Math.min(visible, filteredItems.size() - scrollOffset); i++) {
            int idx = i + scrollOffset;
            if (idx >= filteredItems.size()) break;

            ResourceLocation itemId = filteredItems.get(idx);
            int y = startY + i * ENTRY_H;
            int entryRight = leftPos + LIST_WIDTH - 4;

            int bgColor = BG_ENTRY;
            if (idx == selectedEntry) {
                bgColor = BG_ENTRY_SELECTED;
            } else if (mouseX >= leftPos + 2 && mouseX <= entryRight
                    && mouseY >= y && mouseY < y + ENTRY_H) {
                bgColor = BG_ENTRY_HOVER;
                hoveredEntry = idx;
            }
            gfx.fill(leftPos + 2, y, entryRight, y + ENTRY_H - 2, bgColor);

            // 图标
            Item item = BuiltInRegistries.ITEM.get(itemId);
            if (item != Items.AIR) {
                gfx.renderFakeItem(new ItemStack(item), leftPos + 5, y + 2);
            }

            // 名称
            String name = item != Items.AIR
                    ? item.getDescription().getString() : itemId.getPath();
            if (font.width(name) > LIST_WIDTH - 26) {
                name = font.plainSubstrByWidth(name, LIST_WIDTH - 30) + "..";
            }
            gfx.drawString(font, name, leftPos + 23, y + 5, TEXT_PRIMARY, false);
        }

        // 滚动条
        if (maxScroll > 0 && filteredItems.size() > 0) {
            int trackTop = listTop + 2;
            int trackBottom = listBottom - 2;
            int trackH = trackBottom - trackTop;
            int barH = Math.max(20, trackH * visible / filteredItems.size());
            int barY = trackTop + (trackH - barH) * scrollOffset / maxScroll;

            gfx.fill(leftPos + LIST_WIDTH - 6, trackTop,
                    leftPos + LIST_WIDTH - 2, trackBottom, 0x30151520);
            boolean scrollHover = mouseX >= leftPos + LIST_WIDTH - 6
                    && mouseX <= leftPos + LIST_WIDTH - 2
                    && mouseY >= barY && mouseY <= barY + barH;
            gfx.fill(leftPos + LIST_WIDTH - 6, barY,
                    leftPos + LIST_WIDTH - 2, barY + barH,
                    scrollHover ? 0xB08888A0 : 0x80555570);
        }
    }

    // ==================== 右侧详情 ====================

    private void renderDetail(GuiGraphics gfx, int mouseX, int mouseY) {
        int dl = leftPos + DETAIL_X;  // detail left
        int dt = listTop + 10;        // detail top

        if (selectedEntry >= 0 && selectedEntry < filteredItems.size()) {
            ResourceLocation itemId = filteredItems.get(selectedEntry);
            Item item = BuiltInRegistries.ITEM.get(itemId);
            long emcValue = EmcDatabase.getEmc(new ItemStack(item));

            // 大图标
            if (item != Items.AIR) {
                gfx.renderFakeItem(new ItemStack(item), dl + 28, dt);
            }

            // 名称
            String name = item != Items.AIR
                    ? item.getDescription().getString() : itemId.toString();
            if (font.width(name) > DETAIL_WIDTH) {
                name = font.plainSubstrByWidth(name, DETAIL_WIDTH - 6) + "..";
            }
            gfx.drawString(font, name, dl, dt + 20, TEXT_PRIMARY, false);

            // EMC 值
            gfx.drawString(font, formatEmc(emcValue) + " EMC/个",
                    dl, dt + 34, TEXT_GOLD, false);

            // 提取按钮
            int btnY = dt + 52;
            String[] modes = {"x1", "x10", "x64"};
            for (int m = 0; m < 3; m++) {
                int btnW = 24;
                int btnX = dl + m * (btnW + 3);
                boolean hovered = mouseX >= btnX && mouseX <= btnX + btnW
                        && mouseY >= btnY && mouseY <= btnY + 14;
                int btnColor = (m == extractMode)
                        ? BG_BTN_HOVER : (hovered ? BG_BTN_HOVER : BG_BTN);
                gfx.fill(btnX, btnY, btnX + btnW, btnY + 14, btnColor);
                gfx.drawCenteredString(font, modes[m], btnX + btnW / 2,
                        btnY + 3, TEXT_PRIMARY);
            }
        } else {
            gfx.drawString(font,
                    Component.translatable("screen.infinitestats.emc.no_selection").getString(),
                    dl, dt, TEXT_SECONDARY, false);
            gfx.drawString(font,
                    Component.translatable("screen.infinitestats.emc.hint").getString(),
                    dl, dt + 16, TEXT_HINT, false);
            gfx.drawString(font,
                    Component.translatable("screen.infinitestats.emc.hint2").getString(),
                    dl, dt + 30, TEXT_HINT, false);
        }
    }

    // ==================== 鼠标事件 ====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 先检查搜索框
        if (searchBox.isMouseOver(mouseX, mouseY)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (button == 0) {
            // 学习槽点击（放物品）
            if (isHoveringLearnSlot((int) mouseX, (int) mouseY)) {
                handleLearnSlotClick();
                return true;
            }

            // 列表条目点击
            int relX = (int) mouseX - leftPos - 2;
            int relY = (int) mouseY - listTop - 2;
            if (relX >= 0 && relX < LIST_WIDTH - 6 && relY >= 0) {
                int idx = relY / ENTRY_H + scrollOffset;
                if (idx >= 0 && idx < filteredItems.size()) {
                    selectedEntry = idx;
                    return true;
                }
            }

            // 提取按钮点击
            if (selectedEntry >= 0 && selectedEntry < filteredItems.size()) {
                int dl = leftPos + DETAIL_X;
                int dt = listTop + 10;
                int btnY = dt + 52;
                for (int m = 0; m < 3; m++) {
                    int btnW = 24;
                    int btnX = dl + m * (btnW + 3);
                    if (mouseX >= btnX && mouseX <= btnX + btnW
                            && mouseY >= btnY && mouseY <= btnY + 14) {
                        extractMode = m;
                        extractSelected();
                        return true;
                    }
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta < 0 && scrollOffset < maxScroll) scrollOffset++;
        else if (delta > 0 && scrollOffset > 0) scrollOffset--;
        return true;
    }

    // ==================== 操作 ====================

    /**
     * 点击学习槽 → 如果玩家光标上有物品，放入学习槽
     */
    private void handleLearnSlotClick() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 简化处理：交给父类处理槽位交互
        // AbstractContainerScreen 会处理点击 slot 的交互
        // 这里我们可以直接模拟点击学习槽的行为
        // 由于学习槽是真实 Slot，slotClicked/mouseClicked 会自动处理
    }

    private void extractSelected() {
        if (selectedEntry < 0 || selectedEntry >= filteredItems.size()) return;
        ResourceLocation itemId = filteredItems.get(selectedEntry);
        int[] counts = {1, 10, 64};
        int count = counts[extractMode];

        NetworkHandler.CHANNEL.sendToServer(
                new NetworkHandler.EmcExtractPacket(itemId.toString(), count));
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
        return false;
    }

    // ==================== EDitBox 焦点恢复 ====================

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
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                searchBox.setFocused(false);
                return true;
            }
            return searchBox.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
