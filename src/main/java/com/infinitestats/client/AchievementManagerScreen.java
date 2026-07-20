package com.infinitestats.client;

import com.infinitestats.network.NetworkHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 成就管理器 — 查看所有成就并自由完成/取消
 * <p>
 * 功能：搜索过滤、分类标签（全部/已完成/未完成）、
 * 一键授予/撤销成就，服务器端即时生效。
 */
public class AchievementManagerScreen extends Screen {

    private static final int GUI_WIDTH = 380;
    private static final int GUI_HEIGHT = 260;

    // 颜色
    private static final int BG_PANEL = 0xE81A1A2E;
    private static final int BG_HEADER = 0xC0222240;
    private static final int BG_ROW = 0x40252535;
    private static final int BG_ROW_HOVER = 0x60353550;
    private static final int BG_ROW_COMPLETED = 0x301A4A2E;
    private static final int BG_ROW_COMPLETED_HOVER = 0x502D6A3E;
    private static final int BG_SEARCH = 0x80151520;
    private static final int BG_TAB = 0x40252535;
    private static final int BG_TAB_ACTIVE = 0x804A4A70;
    private static final int BG_SCROLLBAR = 0x80555570;
    private static final int BG_SCROLLBAR_TRACK = 0x30151520;
    private static final int BG_BTN_GRANT = 0xFF1A5334;
    private static final int BG_BTN_GRANT_HOVER = 0xFF2D8A4E;
    private static final int BG_BTN_REVOKE = 0xFF6B2A1A;
    private static final int BG_BTN_REVOKE_HOVER = 0xFF8A3D2D;

    private static final int TEXT_PRIMARY = 0xFFE2E8F0;
    private static final int TEXT_SECONDARY = 0xFF94A3B8;
    private static final int TEXT_COMPLETED = 0xFF4ADE80;
    private static final int TEXT_UNCOMPLETED = 0xFFF87171;
    private static final int TEXT_HEADER = 0xFFFFD166;

    private static final int ROW_HEIGHT = 36;
    private static final int SEARCH_H = 18;

    // ======================== 状态 ========================

    private int leftPos, topPos;
    private List<NetworkHandler.AchievementInfo> allAchievements = new ArrayList<>();
    private List<NetworkHandler.AchievementInfo> filteredAchievements = new ArrayList<>();
    private int scrollOffset;
    private int maxScroll;

    private EditBox searchField;
    private String searchText = "";
    private int filterTab; // 0=全部, 1=已完成, 2=未完成

    private int hoveredRow = -1;
    private boolean dataReceived;

    // ======================== 构造 ========================

    public AchievementManagerScreen() {
        super(Component.translatable("screen.infinitestats.achievements"));
    }

    // ======================== 生命周期 ========================

    @Override
    protected void init() {
        super.init();
        leftPos = (width - GUI_WIDTH) / 2;
        topPos = (height - GUI_HEIGHT) / 2;

        // 搜索框
        searchField = new EditBox(font, leftPos + 8, topPos + 30, GUI_WIDTH - 16, SEARCH_H,
                Component.translatable("screen.infinitestats.achievements.search"));
        searchField.setMaxLength(100);
        searchField.setValue(searchText);
        searchField.setResponder(this::onSearchChanged);
        addRenderableWidget(searchField);

        // 关闭按钮
        addRenderableWidget(Button.builder(
                Component.literal("✕"),
                btn -> onClose())
                .pos(leftPos + GUI_WIDTH - 22, topPos + 4)
                .size(18, 18)
                .build());

        // 请求数据
        if (!dataReceived) {
            NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.RequestAdvancementsPacket());
        }

        applyFilter();
    }

    @Override
    public void tick() {
        super.tick();
        // 检查是否有新的同步数据到达
        if (NetworkHandler.pendingAdvancements != null) {
            allAchievements = new ArrayList<>(NetworkHandler.pendingAdvancements);
            NetworkHandler.pendingAdvancements = null;
            dataReceived = true;
            applyFilter(false); // 数据刷新不重置滚动位置
        }
    }

    @Override
    public void onClose() {
        super.onClose();
        NetworkHandler.pendingAdvancements = null;
    }

    // ======================== 数据接收 ========================

    /**
     * 由 SyncAdvancementsPacket 的 handle 通过静态字段间接调用
     */
    public void receiveAdvancements(List<NetworkHandler.AchievementInfo> list) {
        allAchievements = new ArrayList<>(list);
        dataReceived = true;
        applyFilter(false); // 数据刷新不重置滚动位置
    }

    // ======================== 过滤 ========================

    private void onSearchChanged(String text) {
        searchText = text;
        applyFilter(true);
    }

    private void applyFilter() {
        applyFilter(true);
    }

    private void applyFilter(boolean resetScroll) {
        String lower = searchText.toLowerCase().trim();

        int oldSize = filteredAchievements.size();

        filteredAchievements = allAchievements.stream()
                .filter(info -> {
                    // 标签过滤
                    if (filterTab == 1 && !info.completed) return false;
                    if (filterTab == 2 && info.completed) return false;
                    // 搜索过滤
                    if (lower.isEmpty()) return true;
                    return info.displayName.toLowerCase().contains(lower)
                            || info.description.toLowerCase().contains(lower)
                            || info.id.toLowerCase().contains(lower);
                })
                .collect(Collectors.toList());

        if (resetScroll || filteredAchievements.size() != oldSize) {
            scrollOffset = 0;
        }
        updateMaxScroll();
    }

    private void updateMaxScroll() {
        int listHeight = GUI_HEIGHT - 76 - 24;
        int visibleRows = listHeight / ROW_HEIGHT;
        maxScroll = Math.max(0, filteredAchievements.size() - visibleRows);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
    }

    // ======================== 渲染 ========================

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        // 面板背景
        graphics.fill(leftPos, topPos, leftPos + GUI_WIDTH, topPos + GUI_HEIGHT, BG_PANEL);

        // 标题栏
        graphics.fill(leftPos, topPos, leftPos + GUI_WIDTH, topPos + 24, BG_HEADER);
        graphics.drawCenteredString(font,
                Component.translatable("screen.infinitestats.achievements"),
                leftPos + GUI_WIDTH / 2, topPos + 7, TEXT_HEADER);

        // 统计信息
        String stats = Component.translatable("screen.infinitestats.achievements.stats",
                allAchievements.size(),
                allAchievements.stream().filter(a -> a.completed).count()).getString();
        graphics.drawString(font, stats, leftPos + 8, topPos + 24, TEXT_SECONDARY);

        // 过滤标签
        renderFilterTabs(graphics, mouseX, mouseY);

        // 搜索框已由 addRenderableWidget 处理

        // 列表
        renderAchievementList(graphics, mouseX, mouseY);

        // 底部提示
        String hint = Component.translatable("screen.infinitestats.achievements.hint").getString();
        int hintY = topPos + GUI_HEIGHT - 12;
        graphics.drawCenteredString(font, hint, leftPos + GUI_WIDTH / 2, hintY, TEXT_SECONDARY);

        // 无数据提示
        if (!dataReceived) {
            String loading = Component.translatable("screen.infinitestats.achievements.loading").getString();
            graphics.drawCenteredString(font, loading,
                    leftPos + GUI_WIDTH / 2, topPos + GUI_HEIGHT / 2, TEXT_SECONDARY);
        } else if (filteredAchievements.isEmpty()) {
            String empty = Component.translatable("screen.infinitestats.achievements.empty").getString();
            graphics.drawCenteredString(font, empty,
                    leftPos + GUI_WIDTH / 2, topPos + GUI_HEIGHT / 2, TEXT_SECONDARY);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderFilterTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        String[] tabs = {
                Component.translatable("screen.infinitestats.achievements.tab_all").getString(),
                Component.translatable("screen.infinitestats.achievements.tab_done").getString(),
                Component.translatable("screen.infinitestats.achievements.tab_undone").getString()
        };
        int tabW = 60;
        int tabX = leftPos + GUI_WIDTH - 8 - tabW * 3;

        for (int i = 0; i < 3; i++) {
            int x = tabX + i * (tabW + 2);
            int y = topPos + 28;
            boolean active = filterTab == i;
            boolean hover = mouseX >= x && mouseX < x + tabW && mouseY >= y && mouseY < y + 14;

            graphics.fill(x, y, x + tabW, y + 14, active ? BG_TAB_ACTIVE : (hover ? BG_ROW_HOVER : BG_TAB));
            graphics.drawCenteredString(font, tabs[i], x + tabW / 2, y + 3, active ? TEXT_PRIMARY : TEXT_SECONDARY);
        }
    }

    private void renderAchievementList(GuiGraphics graphics, int mouseX, int mouseY) {
        int listTop = topPos + 76;
        int listHeight = GUI_HEIGHT - 76 - 24;
        int visibleRows = Math.min(filteredAchievements.size() - scrollOffset,
                Math.max(0, listHeight / ROW_HEIGHT));

        // 裁剪区域
        graphics.enableScissor(leftPos, listTop, leftPos + GUI_WIDTH, listTop + listHeight);

        hoveredRow = -1;

        for (int i = 0; i < visibleRows; i++) {
            int idx = scrollOffset + i;
            if (idx >= filteredAchievements.size()) break;

            NetworkHandler.AchievementInfo info = filteredAchievements.get(idx);
            int y = listTop + i * ROW_HEIGHT;

            // 行背景
            boolean hover = mouseX >= leftPos + 4 && mouseX < leftPos + GUI_WIDTH - 4
                    && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (hover) hoveredRow = idx;

            int bgColor = info.completed
                    ? (hover ? BG_ROW_COMPLETED_HOVER : BG_ROW_COMPLETED)
                    : (hover ? BG_ROW_HOVER : BG_ROW);
            graphics.fill(leftPos + 4, y, leftPos + GUI_WIDTH - 4, y + ROW_HEIGHT, bgColor);

            // 图标
            ItemStack icon = getIcon(info.iconItemId);
            if (!icon.isEmpty()) {
                graphics.renderItem(icon, leftPos + 10, y + (ROW_HEIGHT - 16) / 2);
            }

            // 名称
            int nameX = leftPos + 32;
            int nameY = y + 3;
            graphics.drawString(font, info.displayName, nameX, nameY, TEXT_PRIMARY);

            // 描述
            String desc = info.description;
            if (font.width(desc) > GUI_WIDTH - 130) {
                desc = font.plainSubstrByWidth(desc, GUI_WIDTH - 130) + "...";
            }
            graphics.drawString(font, desc, nameX, nameY + 12, TEXT_SECONDARY);

            // 状态文字
            int statusX = leftPos + GUI_WIDTH - 82;
            String status = info.completed
                    ? Component.translatable("screen.infinitestats.achievements.completed").getString()
                    : Component.translatable("screen.infinitestats.achievements.uncompleted").getString();
            int statusColor = info.completed ? TEXT_COMPLETED : TEXT_UNCOMPLETED;
            graphics.drawString(font, status, statusX, y + (ROW_HEIGHT - 8) / 2, statusColor);

            // 切换按钮
            renderToggleButton(graphics, info, y, mouseX, mouseY);
        }

        graphics.disableScissor();

        // 滚动条
        if (maxScroll > 0) {
            renderScrollbar(graphics, listTop, listHeight);
        }
    }

    private void renderToggleButton(GuiGraphics graphics, NetworkHandler.AchievementInfo info,
                                     int y, int mouseX, int mouseY) {
        int btnX = leftPos + GUI_WIDTH - 54;
        int btnY = y + (ROW_HEIGHT - 14) / 2;
        boolean hover = mouseX >= btnX && mouseX < btnX + 44 && mouseY >= btnY && mouseY < btnY + 14;

        int bgColor;
        if (info.completed) {
            bgColor = hover ? BG_BTN_REVOKE_HOVER : BG_BTN_REVOKE;
        } else {
            bgColor = hover ? BG_BTN_GRANT_HOVER : BG_BTN_GRANT;
        }

        graphics.fill(btnX, btnY, btnX + 44, btnY + 14, bgColor);

        String label = info.completed
                ? Component.translatable("screen.infinitestats.achievements.revoke").getString()
                : Component.translatable("screen.infinitestats.achievements.grant").getString();
        graphics.drawCenteredString(font, label, btnX + 22, btnY + 3, 0xFFFFFFFF);
    }

    private void renderScrollbar(GuiGraphics graphics, int listTop, int listHeight) {
        int scrollX = leftPos + GUI_WIDTH - 4;
        int trackHeight = listHeight;
        graphics.fill(scrollX, listTop, scrollX + 4, listTop + trackHeight, BG_SCROLLBAR_TRACK);

        int barHeight = Math.max(16, (int) (trackHeight * trackHeight / (float) ((filteredAchievements.size()) * ROW_HEIGHT)));
        int barY = listTop + (int) ((trackHeight - barHeight) * ((float) scrollOffset / maxScroll));

        graphics.fill(scrollX, barY, scrollX + 4, barY + barHeight, BG_SCROLLBAR);
    }

    // ======================== 鼠标 ========================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            // 标签切换
            int tabW = 60;
            int tabX = leftPos + GUI_WIDTH - 8 - tabW * 3;
            for (int i = 0; i < 3; i++) {
                int x = tabX + i * (tabW + 2);
                if (mouseX >= x && mouseX < x + tabW && mouseY >= topPos + 28 && mouseY < topPos + 42) {
                    filterTab = i;
                    applyFilter();
                    return true;
                }
            }

            // 切换按钮
            int btnX = leftPos + GUI_WIDTH - 54;
            int listTop = topPos + 76;
            int listHeight = GUI_HEIGHT - 76 - 24;
            int visibleRows = Math.min(filteredAchievements.size() - scrollOffset,
                    Math.max(0, listHeight / ROW_HEIGHT));

            for (int i = 0; i < visibleRows; i++) {
                int idx = scrollOffset + i;
                if (idx >= filteredAchievements.size()) break;

                int y = listTop + i * ROW_HEIGHT;
                int btnY = y + (ROW_HEIGHT - 14) / 2;
                if (mouseX >= btnX && mouseX < btnX + 44 && mouseY >= btnY && mouseY < btnY + 14) {
                    NetworkHandler.AchievementInfo info = filteredAchievements.get(idx);
                    NetworkHandler.CHANNEL.sendToServer(
                            new NetworkHandler.ToggleAdvancementPacket(info.id, !info.completed));
                    return true;
                }
            }

            // 滚动条
            if (maxScroll > 0) {
                int scrollX = leftPos + GUI_WIDTH - 4;
                if (mouseX >= scrollX && mouseX <= scrollX + 4
                        && mouseY >= listTop && mouseY <= listTop + listHeight) {
                    // 跳转到对应位置
                    float ratio = (float) (mouseY - listTop) / listHeight;
                    scrollOffset = Math.min(maxScroll, Math.max(0,
                            (int) (ratio * (filteredAchievements.size()))));
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        if (scrollDelta != 0) {
            scrollOffset = Mth.clamp(scrollOffset - (int) Math.signum(scrollDelta), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollDelta);
    }

    // ======================== 键盘 ========================

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ======================== 工具 ========================

    private ItemStack getIcon(String itemId) {
        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        if (rl == null) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.get(rl);
        if (item == Items.AIR) {
            // 图标 item 未注册时回退为空
            return ItemStack.EMPTY;
        }
        return new ItemStack(item);
    }
}
