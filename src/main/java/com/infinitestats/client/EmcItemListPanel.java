package com.infinitestats.client;

import com.infinitestats.emc.EmcPlayerData;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.*;
import java.util.stream.Collectors;

/**
 * EMC 左侧物品列表面板
 * 负责：搜索过滤、滚动、选中/悬停状态、渲染列表条目、滚动条
 */
public class EmcItemListPanel {

    private final Font font;
    private final EmcPlayerData playerData;

    private String searchText = "";
    private List<ResourceLocation> filteredItems = new ArrayList<>();
    private int scrollOffset;
    private int maxScroll;
    private int hoveredEntry = -1;
    private int selectedEntry = -1;

    // 父级 GUI 坐标（由 EmcScreen 在 init/render 时设置）
    private int leftPos, topPos;

    public EmcItemListPanel(Font font, EmcPlayerData playerData) {
        this.font = font;
        this.playerData = playerData;
    }

    // ==================== 坐标更新 ====================

    public void updatePosition(int leftPos, int topPos) {
        this.leftPos = leftPos;
        this.topPos = topPos;
    }

    // ==================== 搜索 ====================

    public void setSearchText(String text) {
        this.searchText = text;
        this.scrollOffset = 0;
        this.selectedEntry = -1;
        updateFilteredList();
    }

    public String getSearchText() {
        return searchText;
    }

    /**
     * 从玩家数据刷新过滤列表
     */
    public void updateFilteredList() {
        if (playerData == null) {
            filteredItems.clear();
            clampScroll();
            return;
        }
        String lower = searchText.toLowerCase().trim();
        filteredItems = playerData.getLearnedItems().stream()
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

        clampScroll();
    }

    private void clampScroll() {
        int visible = getVisibleCount();
        maxScroll = Math.max(0, filteredItems.size() - visible);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
    }

    // ==================== 选中状态 ====================

    public int getSelectedEntry() {
        return selectedEntry;
    }

    public void setSelectedEntry(int index) {
        this.selectedEntry = index;
    }

    public void clearSelection() {
        this.selectedEntry = -1;
    }

    public ResourceLocation getSelectedItem() {
        if (selectedEntry >= 0 && selectedEntry < filteredItems.size()) {
            return filteredItems.get(selectedEntry);
        }
        return null;
    }

    public boolean hasSelection() {
        return selectedEntry >= 0 && selectedEntry < filteredItems.size();
    }

    public int getItemCount() {
        return filteredItems.size();
    }

    // ==================== 可视区域 ====================

    private int listTop() {
        return EmcLayout.listTop(topPos);
    }

    private int listBottom() {
        return EmcLayout.listBottom(topPos);
    }

    private int getVisibleCount() {
        return EmcLayout.listVisibleCount(topPos);
    }

    // ==================== 渲染 ====================

    public void render(GuiGraphics gfx, int mouseX, int mouseY) {
        // 列表背景
        int lt = listTop();
        int lb = listBottom();
        gfx.fill(leftPos + EmcLayout.LIST_X, lt,
                leftPos + EmcLayout.LIST_WIDTH, lb, EmcColors.BG_LIST);

        // 条目
        renderEntries(gfx, mouseX, mouseY);

        // 滚动条
        renderScrollbar(gfx, mouseX, mouseY);
    }

    private void renderEntries(GuiGraphics gfx, int mouseX, int mouseY) {
        int visible = getVisibleCount();
        int startY = listTop() + EmcLayout.LIST_PADDING_H;
        hoveredEntry = -1;

        for (int i = 0; i < Math.min(visible, filteredItems.size() - scrollOffset); i++) {
            int idx = i + scrollOffset;
            if (idx >= filteredItems.size()) break;

            ResourceLocation itemId = filteredItems.get(idx);
            int y = startY + i * EmcLayout.ENTRY_H;
            int entryRight = leftPos + EmcLayout.LIST_WIDTH - EmcLayout.SCROLLBAR_W;

            // 背景色
            int bgColor = EmcColors.BG_ENTRY;
            if (idx == selectedEntry) {
                bgColor = EmcColors.BG_ENTRY_SELECTED;
            } else if (mouseX >= leftPos + EmcLayout.LIST_PADDING_H
                    && mouseX <= entryRight
                    && mouseY >= y && mouseY < y + EmcLayout.ENTRY_H) {
                bgColor = EmcColors.BG_ENTRY_HOVER;
                hoveredEntry = idx;
            }
            gfx.fill(leftPos + EmcLayout.LIST_PADDING_H, y,
                    entryRight, y + EmcLayout.ENTRY_H - 2, bgColor);

            // 图标
            Item item = BuiltInRegistries.ITEM.get(itemId);
            if (item != Items.AIR) {
                gfx.renderFakeItem(new ItemStack(item),
                        leftPos + EmcLayout.ENTRY_ICON_X,
                        y + EmcLayout.ENTRY_ICON_Y);
            }

            // 名称
            String name = item != Items.AIR
                    ? item.getDescription().getString() : itemId.getPath();
            if (font.width(name) > EmcLayout.LIST_WIDTH - EmcLayout.ENTRY_TEXT_X - EmcLayout.SCROLLBAR_W) {
                name = font.plainSubstrByWidth(name,
                        EmcLayout.LIST_WIDTH - EmcLayout.ENTRY_TEXT_X - EmcLayout.SCROLLBAR_W - 4) + "..";
            }
            gfx.drawString(font, name,
                    leftPos + EmcLayout.ENTRY_TEXT_X,
                    y + EmcLayout.ENTRY_TEXT_Y,
                    EmcColors.TEXT_PRIMARY, false);
        }
    }

    private void renderScrollbar(GuiGraphics gfx, int mouseX, int mouseY) {
        if (maxScroll <= 0 || filteredItems.isEmpty()) return;

        int trackTop = listTop() + EmcLayout.SCROLLBAR_PADDING;
        int trackBottom = listBottom() - EmcLayout.SCROLLBAR_PADDING;
        int trackH = trackBottom - trackTop;
        int visible = getVisibleCount();
        int barH = Math.max(EmcLayout.SCROLLBAR_MIN_H,
                trackH * visible / filteredItems.size());
        int barY = trackTop + (trackH - barH) * scrollOffset / maxScroll;

        int sbLeft = leftPos + EmcLayout.LIST_WIDTH - EmcLayout.SCROLLBAR_W;
        int sbRight = leftPos + EmcLayout.LIST_WIDTH - EmcLayout.SCROLLBAR_PADDING;

        // 轨道
        gfx.fill(sbLeft, trackTop, sbRight, trackBottom, EmcColors.SCROLLBAR_TRACK);

        // 滑块
        boolean hovered = mouseX >= sbLeft && mouseX <= sbRight
                && mouseY >= barY && mouseY <= barY + barH;
        gfx.fill(sbLeft, barY, sbRight, barY + barH,
                hovered ? EmcColors.SCROLLBAR_BAR_HOVER : EmcColors.SCROLLBAR_BAR);
    }

    // ==================== 鼠标事件 ====================

    /**
     * 处理列表区域内的鼠标点击
     * @return true 如果事件被处理
     */
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0) return false;

        int relX = mouseX - leftPos - EmcLayout.LIST_PADDING_H;
        int relY = mouseY - listTop() - EmcLayout.LIST_PADDING_H;

        if (relX < 0 || relX >= EmcLayout.LIST_WIDTH - EmcLayout.SCROLLBAR_W) return false;
        if (relY < 0) return false;

        int idx = relY / EmcLayout.ENTRY_H + scrollOffset;
        if (idx >= 0 && idx < filteredItems.size()) {
            selectedEntry = idx;
            return true;
        }
        return false;
    }

    /**
     * 处理滚动
     * @return true 如果事件被处理
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta < 0 && scrollOffset < maxScroll) {
            scrollOffset++;
        } else if (delta > 0 && scrollOffset > 0) {
            scrollOffset--;
        } else {
            return false;
        }
        return true;
    }

    /**
     * 选中下一个条目（键盘导航）
     */
    public void selectNext() {
        if (filteredItems.isEmpty()) return;
        if (selectedEntry < 0) {
            selectedEntry = 0;
        } else if (selectedEntry < filteredItems.size() - 1) {
            selectedEntry++;
        }
        ensureVisible();
    }

    /**
     * 选中上一个条目（键盘导航）
     */
    public void selectPrevious() {
        if (filteredItems.isEmpty()) return;
        if (selectedEntry < 0) {
            selectedEntry = 0;
        } else if (selectedEntry > 0) {
            selectedEntry--;
        }
        ensureVisible();
    }

    private void ensureVisible() {
        if (selectedEntry < 0) return;
        int visible = getVisibleCount();
        if (selectedEntry < scrollOffset) {
            scrollOffset = selectedEntry;
        } else if (selectedEntry >= scrollOffset + visible) {
            scrollOffset = selectedEntry - visible + 1;
        }
        clampScroll();
    }
}