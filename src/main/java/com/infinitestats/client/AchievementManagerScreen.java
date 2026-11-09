package com.infinitestats.client;

import com.infinitestats.compat.PinyinSearchBridge;
import com.infinitestats.network.AchievementInfo;
import com.infinitestats.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/**
 * 成就管理器 — 查看所有成就并自由完成 / 取消。
 *
 * <h2>功能</h2>
 * <ul>
 *   <li>拼音 / 原文搜索，按「全部 / 已完成 / 未完成」过滤，按模组下拉筛选；</li>
 *   <li>三种排序：<b>默认</b>（服务端排好的模组 → 父成就 → 名称树序）、名称、进度；</li>
 *   <li>显示部分完成态（进度条 + {@code 3/5}），而不是只有「已完成 / 未完成」两档；</li>
 *   <li>单击行选中、Ctrl 点选多条、Shift 连选，底部批量完成 / 撤销（带二次确认）；</li>
 *   <li>键盘：↑↓ 移动、Enter 切换状态、Delete 撤销、Ctrl+A 全选筛选项、Ctrl+C 复制成就 ID。</li>
 * </ul>
 *
 * <h2>坐标</h2>
 * 全部使用 {@link PanelScreen} 的面板局部坐标（左上角 0,0），缩放由基类处理。
 */
public class AchievementManagerScreen extends PanelScreen {

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    // ======================== 尺寸 ========================

    private static final int GUI_WIDTH = 460;
    private static final int GUI_HEIGHT = 320;
    private static final int ROW_HEIGHT = 28;

    /** 列表区顶（面板坐标）：标题栏 + 统计行 + 搜索框 + 标签行。 */
    private static final int LIST_TOP = 88;
    private static final int LIST_HEIGHT = GUI_HEIGHT - LIST_TOP - 30;
    private static final int VISIBLE_ROWS = LIST_HEIGHT / ROW_HEIGHT;

    private static final int SCROLL_X = GUI_WIDTH - EditorUi.SCROLL_W - 2;
    private static final int ROW_L = 4;
    private static final int ROW_R = GUI_WIDTH - 4;

    private static final int NAME_X = 32;
    private static final int NAME_R = 336;

    private static final int BTN_W = 64;
    private static final int BTN_X = ROW_R - 4 - BTN_W;
    private static final int STATUS_R = BTN_X - 6;

    private static final int SORT_BTN_W = 58;
    private static final int SORT_BTN_X = GUI_WIDTH - 8 - SORT_BTN_W;
    private static final int MODULE_BTN_W = 84;
    private static final int MODULE_BTN_X = SORT_BTN_X - 4 - MODULE_BTN_W;

    private static final int SEARCH_Y = 45;
    private static final int TAB_Y = 68;
    private static final int TAB_W = 56;

    private static final int BOTTOM_Y = 292;
    private static final int BOTTOM_BTN_H = 14;

    // ======================== 颜色 ========================

    private static final int BG_PANEL = 0xE81A1A2E;
    private static final int BG_HEADER = 0xC0222240;
    private static final int BG_ROW = 0x40252535;
    private static final int BG_ROW_HOVER = 0x60353550;
    private static final int BG_ROW_COMPLETED = 0x301A4A2E;
    private static final int BG_ROW_COMPLETED_HOVER = 0x502D6A3E;
    private static final int BG_ROW_SELECTED = 0x7032406A;
    private static final int BG_ROW_SELECTED_HOVER = 0x80405A8E;
    private static final int BG_SEARCH = 0x80151520;
    private static final int BG_TAB = 0x40252535;
    private static final int BG_TAB_ACTIVE = 0x804A4A70;

    private static final int TEXT_PRIMARY = 0xFFE2E8F0;
    private static final int TEXT_SECONDARY = 0xFF94A3B8;
    private static final int TEXT_COMPLETED = 0xFF4ADE80;
    private static final int TEXT_PARTIAL = 0xFFFFD166;
    private static final int TEXT_UNCOMPLETED = 0xFFF87171;
    private static final int TEXT_HEADER = 0xFFFFD166;
    private static final int TEXT_DISABLED = 0xFF6B7280;

    private static final int ACCENT_SELECTED = 0xFF60A5FA;

    private static final int BTN_GRANT = 0xFF1A5334;
    private static final int BTN_GRANT_HOVER = 0xFF2D8A4E;
    private static final int BTN_REVOKE = 0xFF6B2A1A;
    private static final int BTN_REVOKE_HOVER = 0xFF8A3D2D;

    /** 批量操作的二次确认窗口（毫秒）。 */
    private static final long CONFIRM_WINDOW_MS = 2500;

    // ======================== 状态 ========================

    private List<AchievementInfo> allAchievements = new ArrayList<>();
    private List<AchievementInfo> filteredAchievements = new ArrayList<>();
    private final List<ModuleEntry> modules = new ArrayList<>();
    private final Map<String, ItemStack> iconCache = new HashMap<>();

    private EditBox searchField;
    private String searchText = "";
    private int filterTab;              // 0=全部, 1=已完成, 2=未完成
    private String moduleFilter;        // null=全部模组
    private int sortMode;               // 0=默认树序, 1=名称, 2=进度

    private int scrollOffset;
    private int maxScroll;
    private boolean scrollbarDragging;

    /** 选中集合（存 id，刷新数据后依然对得上）。 */
    private final Set<String> selected = new LinkedHashSet<>();
    /** Shift 连选的锚点（filtered 列表索引）。 */
    private int anchorIndex = -1;

    private AchievementInfo hoveredInfo;
    private boolean dataReceived;
    private boolean canEdit = true;
    private int skippedCount;
    private long seenVersion = -1;

    private boolean moduleMenuOpen;
    private int moduleMenuScroll;

    private long confirmUntil;
    private boolean confirmGrant;

    // ======================== 构造 / 生命周期 ========================

    public AchievementManagerScreen() {
        super(Component.translatable("screen.infinitestats.achievements"));
    }

    @Override
    protected int panelW() {
        return GUI_WIDTH;
    }

    @Override
    protected int panelH() {
        return GUI_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();

        searchField = new EditBox(font, leftPos + 8, topPos + SEARCH_Y, GUI_WIDTH - 16, 18,
                Component.translatable("screen.infinitestats.achievements.search"));
        searchField.setMaxLength(100);
        searchField.setValue(searchText);
        searchField.setResponder(this::onSearchChanged);
        addRenderableWidget(searchField);

        addRenderableWidget(Button.builder(Component.literal("✕"), btn -> onClose())
                .pos(leftPos + GUI_WIDTH - 22, topPos + 4)
                .size(18, 18)
                .build());

        if (!dataReceived) {
            NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.RequestAdvancementsPacket());
        }

        applyFilter();
    }

    @Override
    public void tick() {
        super.tick();
        if (NetworkHandler.advancementsVersion != seenVersion
                && NetworkHandler.pendingAdvancements != null) {
            seenVersion = NetworkHandler.advancementsVersion;
            allAchievements = new ArrayList<>(NetworkHandler.pendingAdvancements);
            canEdit = NetworkHandler.pendingAdvancementsCanEdit;
            skippedCount = NetworkHandler.pendingAdvancementsSkipped;
            dataReceived = true;
            iconCache.clear();
            rebuildModules();
            applyFilter(false);
            pruneSelection();
        }
    }

    @Override
    public void onClose() {
        super.onClose();
        NetworkHandler.pendingAdvancements = null;
        moduleMenuOpen = false;
    }

    // ======================== 数据处理 ========================

    private void onSearchChanged(String text) {
        searchText = text;
        applyFilter(true);
    }

    private void applyFilter() {
        applyFilter(true);
    }

    private void applyFilter(boolean resetScroll) {
        String lower = searchText.toLowerCase(Locale.ROOT).trim();

        List<AchievementInfo> next = new ArrayList<>(allAchievements.size());
        for (AchievementInfo info : allAchievements) {
            if (filterTab == 1 && !info.completed) continue;
            if (filterTab == 2 && info.completed) continue;
            if (moduleFilter != null && !moduleFilter.equals(info.namespace)) continue;
            if (!lower.isEmpty()) {
                String name = info.displayName == null || info.displayName.isEmpty()
                        ? fallbackName(info.id) : info.displayName;
                if (!PinyinSearchBridge.matches(name.toLowerCase(Locale.ROOT), lower)
                        && !PinyinSearchBridge.matches(info.description.toLowerCase(Locale.ROOT), lower)
                        && !PinyinSearchBridge.matches(info.id.toLowerCase(Locale.ROOT), lower)) {
                    continue;
                }
            }
            next.add(info);
        }

        sortList(next);
        filteredAchievements = next;

        if (resetScroll) scrollOffset = 0;
        updateMaxScroll();
    }

    /** 排序：默认保持服务端下发的树序，其余在客户端重排。 */
    private void sortList(List<AchievementInfo> list) {
        if (sortMode == 1) {
            list.sort(Comparator
                    .comparing((AchievementInfo a) -> sortName(a), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(a -> a.id));
        } else if (sortMode == 2) {
            list.sort(Comparator
                    .comparing((AchievementInfo a) -> a.completed)
                    .thenComparingInt(a -> -a.progressPercent)
                    .thenComparing(AchievementManagerScreen::sortName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(a -> a.id));
        }
    }

    private static String sortName(AchievementInfo info) {
        return info.displayName == null || info.displayName.isEmpty()
                ? fallbackName(info.id) : info.displayName;
    }

    private void rebuildModules() {
        modules.clear();
        Map<String, Integer> counts = new TreeMap<>();
        for (AchievementInfo info : allAchievements) {
            counts.merge(info.namespace, 1, Integer::sum);
        }
        modules.add(new ModuleEntry(null, allAchievements.size()));
        Integer mc = counts.remove("minecraft");
        if (mc != null) modules.add(new ModuleEntry("minecraft", mc));
        counts.forEach((ns, count) -> modules.add(new ModuleEntry(ns, count)));

        // 当前筛选的模组如果已经不存在了（换存档 / 数据包变化），退回「全部」
        if (moduleFilter != null && !counts.containsKey(moduleFilter) && !"minecraft".equals(moduleFilter)) {
            moduleFilter = null;
        }
    }

    private void pruneSelection() {
        if (selected.isEmpty()) return;
        Set<String> alive = new HashSet<>();
        for (AchievementInfo info : allAchievements) alive.add(info.id);
        selected.retainAll(alive);
        if (selected.isEmpty()) anchorIndex = -1;
    }

    private void updateMaxScroll() {
        maxScroll = Math.max(0, filteredAchievements.size() - VISIBLE_ROWS);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
        anchorIndex = Mth.clamp(anchorIndex, -1, filteredAchievements.size() - 1);
    }

    // ======================== 渲染：主面板 ========================

    @Override
    protected void renderPanel(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        hoveredInfo = null;

        graphics.fill(leftPos, topPos, leftPos + GUI_WIDTH, topPos + GUI_HEIGHT, BG_PANEL);
        graphics.fill(leftPos, topPos, leftPos + GUI_WIDTH, topPos + 24, BG_HEADER);
        graphics.drawCenteredString(font,
                Component.translatable("screen.infinitestats.achievements"),
                leftPos + GUI_WIDTH / 2, topPos + 7, TEXT_HEADER);

        renderStatsLine(graphics);
        renderSortButton(graphics, mouseX, mouseY);
        renderModuleButton(graphics, mouseX, mouseY);
        renderFilterTabs(graphics, mouseX, mouseY);
        renderAchievementList(graphics, mouseX, mouseY);
        renderBottomBar(graphics, mouseX, mouseY);

        if (!dataReceived) {
            graphics.drawCenteredString(font,
                    Component.translatable("screen.infinitestats.achievements.loading").getString(),
                    leftPos + GUI_WIDTH / 2, topPos + GUI_HEIGHT / 2, TEXT_SECONDARY);
        } else if (filteredAchievements.isEmpty()) {
            graphics.drawCenteredString(font,
                    Component.translatable("screen.infinitestats.achievements.empty").getString(),
                    leftPos + GUI_WIDTH / 2, topPos + GUI_HEIGHT / 2, TEXT_SECONDARY);
        }
    }

    private void renderStatsLine(GuiGraphics graphics) {
        long completed = allAchievements.stream().filter(a -> a.completed).count();
        String stats = Component.translatable("screen.infinitestats.achievements.stats",
                allAchievements.size(), completed, filteredAchievements.size()).getString();
        graphics.drawString(font, stats, leftPos + 8, topPos + 28, TEXT_SECONDARY);
    }

    private void renderSortButton(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = leftPos + SORT_BTN_X;
        int y = topPos + 27;
        boolean hover = inside(mouseX, mouseY, x, y, SORT_BTN_W, 14);
        graphics.fill(x, y, x + SORT_BTN_W, y + 14, hover ? BG_ROW_HOVER : BG_TAB);
        String mode = switch (sortMode) {
            case 1 -> tr("screen.infinitestats.achievements.sort.name");
            case 2 -> tr("screen.infinitestats.achievements.sort.progress");
            default -> tr("screen.infinitestats.achievements.sort.default");
        };
        graphics.drawCenteredString(font,
                tr("screen.infinitestats.achievements.sort", mode),
                x + SORT_BTN_W / 2, y + 3, TEXT_SECONDARY);
    }

    private void renderModuleButton(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = leftPos + MODULE_BTN_X;
        int y = topPos + 27;
        boolean hover = inside(mouseX, mouseY, x, y, MODULE_BTN_W, 14);
        boolean active = moduleFilter != null;
        graphics.fill(x, y, x + MODULE_BTN_W, y + 14,
                moduleMenuOpen ? BG_TAB_ACTIVE : (hover ? BG_ROW_HOVER : BG_TAB));
        String label = active ? modDisplayName(moduleFilter)
                : tr("screen.infinitestats.achievements.module_all");
        graphics.drawCenteredString(font,
                EditorUi.ellipsize(font, label, MODULE_BTN_W - 8),
                x + MODULE_BTN_W / 2, y + 3, active ? TEXT_PRIMARY : TEXT_SECONDARY);
    }

    private void renderFilterTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        String[] tabs = {
                tr("screen.infinitestats.achievements.tab_all"),
                tr("screen.infinitestats.achievements.tab_done"),
                tr("screen.infinitestats.achievements.tab_undone")
        };
        for (int i = 0; i < 3; i++) {
            int x = leftPos + 8 + i * (TAB_W + 2);
            int y = topPos + TAB_Y;
            boolean active = filterTab == i;
            boolean hover = inside(mouseX, mouseY, x, y, TAB_W, 14);
            graphics.fill(x, y, x + TAB_W, y + 14, active ? BG_TAB_ACTIVE : (hover ? BG_ROW_HOVER : BG_TAB));
            graphics.drawCenteredString(font, tabs[i], x + TAB_W / 2, y + 3,
                    active ? TEXT_PRIMARY : TEXT_SECONDARY);
        }

        if (skippedCount > 0) {
            String note = tr("screen.infinitestats.achievements.tech_skipped", skippedCount);
            graphics.drawString(font, note,
                    leftPos + GUI_WIDTH - 8 - font.width(note), topPos + TAB_Y + 3, 0xFF5A6478);
        }
    }

    private void renderAchievementList(GuiGraphics graphics, int mouseX, int mouseY) {
        int listTop = topPos + LIST_TOP;
        int visibleRows = Math.min(filteredAchievements.size() - scrollOffset,
                Math.max(0, LIST_HEIGHT / ROW_HEIGHT));

        beginPanelScissor(graphics, leftPos, listTop, leftPos + GUI_WIDTH, listTop + LIST_HEIGHT);

        for (int i = 0; i < visibleRows; i++) {
            int idx = scrollOffset + i;
            if (idx >= filteredAchievements.size()) break;
            renderRow(graphics, filteredAchievements.get(idx), idx,
                    listTop + i * ROW_HEIGHT, mouseX, mouseY);
        }

        endPanelScissor(graphics);

        if (maxScroll > 0) {
            EditorUi.scrollbar(graphics, mouseX, mouseY, leftPos + SCROLL_X, listTop, LIST_HEIGHT,
                    scrollOffset, maxScroll, VISIBLE_ROWS);
        }
    }

    private void renderRow(GuiGraphics graphics, AchievementInfo info, int idx,
                           int y, int mouseX, int mouseY) {
        boolean isSelected = selected.contains(info.id);
        boolean hover = mouseX >= leftPos + ROW_L && mouseX < leftPos + ROW_R
                && mouseY >= y && mouseY < y + ROW_HEIGHT;
        if (hover) hoveredInfo = info;

        int bg;
        if (isSelected) {
            bg = hover ? BG_ROW_SELECTED_HOVER : BG_ROW_SELECTED;
        } else if (info.completed) {
            bg = hover ? BG_ROW_COMPLETED_HOVER : BG_ROW_COMPLETED;
        } else {
            bg = hover ? BG_ROW_HOVER : BG_ROW;
        }
        graphics.fill(leftPos + ROW_L, y, leftPos + ROW_R, y + ROW_HEIGHT, bg);

        // 左侧色条：框架类型（普通 / 目标 / 挑战），选中时统一成高亮蓝
        int barColor = isSelected ? ACCENT_SELECTED : frameColor(info.frame);
        graphics.fill(leftPos + ROW_L, y, leftPos + ROW_L + 3, y + ROW_HEIGHT, barColor);

        // 图标
        ItemStack icon = getIcon(info.iconItemId);
        if (!icon.isEmpty()) {
            graphics.renderItem(icon, leftPos + 12, y + (ROW_HEIGHT - 16) / 2);
        }

        // 名称
        String name = info.displayName == null || info.displayName.isEmpty()
                ? fallbackName(info.id) : info.displayName;
        int nameColor = info.completed ? TEXT_COMPLETED : info.isPartial() ? TEXT_PARTIAL : TEXT_PRIMARY;
        String shownName = EditorUi.ellipsize(font, name, NAME_R - NAME_X - (info.hidden ? 14 : 0));
        graphics.drawString(font, shownName, leftPos + NAME_X, y + 3, nameColor, false);
        if (info.hidden) {
            int hx = leftPos + NAME_X + font.width(shownName) + 3;
            graphics.drawString(font, "◇", hx, y + 3, TEXT_SECONDARY, false);
        }

        // 描述
        String desc = info.description == null ? "" : info.description;
        graphics.drawString(font,
                EditorUi.ellipsize(font, desc, NAME_R - NAME_X),
                leftPos + NAME_X, y + 14, TEXT_SECONDARY, false);

        // 部分完成：底部细进度条
        if (info.isPartial() && info.criteriaTotal > 0) {
            graphics.fill(leftPos + NAME_X, y + 24, leftPos + NAME_R, y + 25, 0x30FFFFFF);
            int filled = Math.round((NAME_R - NAME_X) * (info.criteriaDone / (float) info.criteriaTotal));
            graphics.fill(leftPos + NAME_X, y + 24, leftPos + NAME_X + Math.max(1, filled), y + 25, TEXT_PARTIAL);
        }

        // 状态文字（右对齐）
        String status;
        int statusColor;
        if (info.completed) {
            status = tr("screen.infinitestats.achievements.completed");
            statusColor = TEXT_COMPLETED;
        } else if (info.isPartial()) {
            status = info.criteriaDone + "/" + info.criteriaTotal;
            statusColor = TEXT_PARTIAL;
        } else {
            status = tr("screen.infinitestats.achievements.uncompleted");
            statusColor = TEXT_UNCOMPLETED;
        }
        graphics.drawString(font, status,
                leftPos + STATUS_R - font.width(status), y + (ROW_HEIGHT - 8) / 2, statusColor, false);

        // 行内单条操作按钮
        renderToggleButton(graphics, info, y, mouseX, mouseY);
    }

    private void renderToggleButton(GuiGraphics graphics, AchievementInfo info,
                                    int y, int mouseX, int mouseY) {
        int btnX = leftPos + BTN_X;
        int btnY = y + (ROW_HEIGHT - 16) / 2;
        boolean hover = canEdit && inside(mouseX, mouseY, btnX, btnY, BTN_W, 16);

        int bgColor;
        if (!canEdit) {
            bgColor = 0x30202030;
        } else if (info.completed) {
            bgColor = hover ? BTN_REVOKE_HOVER : BTN_REVOKE;
        } else {
            bgColor = hover ? BTN_GRANT_HOVER : BTN_GRANT;
        }
        graphics.fill(btnX, btnY, btnX + BTN_W, btnY + 16, bgColor);

        String label = info.completed
                ? tr("screen.infinitestats.achievements.revoke")
                : tr("screen.infinitestats.achievements.grant");
        graphics.drawCenteredString(font, label, btnX + BTN_W / 2, btnY + 4,
                canEdit ? 0xFFFFFFFF : TEXT_DISABLED);
    }

    private void renderBottomBar(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = topPos + BOTTOM_Y;

        if (selected.isEmpty()) {
            String hint = tr("screen.infinitestats.achievements.hint");
            graphics.drawString(font, EditorUi.ellipsize(font, hint, GUI_WIDTH - 16),
                    leftPos + 8, y + 4, 0xFF5A6478, false);
            return;
        }

        String sel = tr("screen.infinitestats.achievements.selected", selected.size());
        graphics.drawString(font, sel, leftPos + 8, y + 4, TEXT_PRIMARY, false);

        boolean confirmingRevoke = isConfirming(false);
        boolean confirmingGrant = isConfirming(true);

        drawBottomButton(graphics, leftPos + 294, y, 54,
                confirmingGrant ? tr("screen.infinitestats.achievements.confirm")
                        : tr("screen.infinitestats.achievements.batch_grant"),
                canEdit, true, confirmingGrant, mouseX, mouseY);

        drawBottomButton(graphics, leftPos + 352, y, 54,
                confirmingRevoke ? tr("screen.infinitestats.achievements.confirm")
                        : tr("screen.infinitestats.achievements.batch_revoke"),
                canEdit, false, confirmingRevoke, mouseX, mouseY);

        drawBottomButton(graphics, leftPos + 410, y, 42,
                tr("screen.infinitestats.achievements.clear"),
                true, false, false, mouseX, mouseY);
    }

    private void drawBottomButton(GuiGraphics graphics, int x, int y, int w, String label,
                                  boolean enabled, boolean primary, boolean confirming,
                                  int mouseX, int mouseY) {
        boolean hover = enabled && inside(mouseX, mouseY, x, y, w, BOTTOM_BTN_H);
        int bg;
        if (!enabled) {
            bg = 0x30202030;
        } else if (primary) {
            bg = confirming ? 0xFF3EA86A : (hover ? BTN_GRANT_HOVER : BTN_GRANT);
        } else {
            bg = confirming ? 0xFFC04A32 : (hover ? BTN_REVOKE_HOVER : BTN_REVOKE);
        }
        graphics.fill(x, y, x + w, y + BOTTOM_BTN_H, bg);
        graphics.drawCenteredString(font, label, x + w / 2, y + 3,
                enabled ? 0xFFFFFFFF : TEXT_DISABLED);
    }

    // ======================== 渲染：浮层 ========================

    @Override
    protected void renderPanelOverlay(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderModuleMenu(graphics, mouseX, mouseY);
        if (hoveredInfo != null && !moduleMenuOpen) {
            renderRowTooltip(graphics, mouseX, mouseY);
        }
    }

    private void renderModuleMenu(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!moduleMenuOpen) return;

        // 下拉菜单是自绘的：先 flush 让列表里的按钮文字落地，
        // 否则菜单背景会进「背景批次」、排在文字下面，文字就浮在菜单上了
        graphics.flush();

        int x = leftPos + MODULE_BTN_X;
        int y = topPos + 43;
        int w = MODULE_BTN_W + 62;
        int h = moduleMenuVisible() * 14 + 2;

        graphics.fill(x, y, x + w, y + h, 0xF0121524);
        graphics.fill(x, y, x + w, y + 1, EditorUi.INFO);

        for (int i = 0; i < moduleMenuVisible(); i++) {
            int idx = moduleMenuScroll + i;
            if (idx >= modules.size()) break;
            ModuleEntry entry = modules.get(idx);
            int iy = y + 1 + i * 14;
            boolean hover = inside(mouseX, mouseY, x + 1, iy, w - 2, 14);
            boolean active = Objects.equals(moduleFilter, entry.id);

            if (hover) {
                graphics.fill(x + 1, iy, x + w - 1, iy + 14, 0x503A5A9E);
            } else if (active) {
                graphics.fill(x + 1, iy, x + w - 1, iy + 14, 0x30FFFFFF);
            }

            String label = entry.id == null
                    ? tr("screen.infinitestats.achievements.module_all") : modDisplayName(entry.id);
            graphics.drawString(font, EditorUi.ellipsize(font, label, w - 42),
                    x + 5, iy + 3, active ? EditorUi.ACCENT : TEXT_PRIMARY, false);
            String count = String.valueOf(entry.count);
            graphics.drawString(font, count, x + w - 6 - font.width(count), iy + 3,
                    TEXT_SECONDARY, false);
        }

        // 菜单画完立刻落地，保证它压在列表文字之上
        graphics.flush();
    }

    private void renderRowTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        AchievementInfo info = hoveredInfo;
        List<String> lines = new ArrayList<>();
        String name = info.displayName == null || info.displayName.isEmpty()
                ? fallbackName(info.id) : info.displayName;
        lines.add("§f" + name);
        if (info.hidden) lines.add("§7" + tr("screen.infinitestats.achievements.tip.hidden"));
        for (String line : EditorUi.wrap(font, info.description == null ? "" : info.description, 220)) {
            lines.add("§7" + line);
        }
        lines.add("§8" + tr("screen.infinitestats.achievements.tip.id", info.id));
        lines.add("§8" + tr("screen.infinitestats.achievements.tip.module", modDisplayName(info.namespace)));
        lines.add("§8" + tr("screen.infinitestats.achievements.tip.type", frameName(info.frame)));
        if (info.criteriaTotal > 1) {
            lines.add("§8" + tr("screen.infinitestats.achievements.tip.progress",
                    info.criteriaDone, info.criteriaTotal, info.progressPercent));
        }

        // 交给基类排队：等控件与下拉浮层都画完再绘制，保证浮窗永远在最上层
        queueTooltip(graphics, mouseX, mouseY, lines,
                info.completed ? TEXT_COMPLETED : frameColor(info.frame));
    }

    // ======================== 鼠标 ========================

    @Override
    protected boolean onPanelClick(int mouseX, int mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;

        // 模组下拉优先吃掉点击（点外面关闭）
        if (moduleMenuOpen) {
            if (pickModuleMenu(mouseX, mouseY)) return true;
            moduleMenuOpen = false;
            return true;
        }

        // 排序按钮
        if (inside(mouseX, mouseY, leftPos + SORT_BTN_X, topPos + 27, SORT_BTN_W, 14)) {
            sortMode = (sortMode + 1) % 3;
            applyFilter(false);
            return true;
        }

        // 模组按钮
        if (inside(mouseX, mouseY, leftPos + MODULE_BTN_X, topPos + 27, MODULE_BTN_W, 14)) {
            moduleMenuOpen = true;
            moduleMenuScroll = 0;
            return true;
        }

        // 过滤标签
        for (int i = 0; i < 3; i++) {
            int x = leftPos + 8 + i * (TAB_W + 2);
            if (inside(mouseX, mouseY, x, topPos + TAB_Y, TAB_W, 14)) {
                filterTab = i;
                applyFilter();
                return true;
            }
        }

        // 底部批量按钮
        int bottomY = topPos + BOTTOM_Y;
        if (!selected.isEmpty()) {
            if (inside(mouseX, mouseY, leftPos + 294, bottomY, 54, BOTTOM_BTN_H)) {
                doBatch(true);
                return true;
            }
            if (inside(mouseX, mouseY, leftPos + 352, bottomY, 54, BOTTOM_BTN_H)) {
                doBatch(false);
                return true;
            }
            if (inside(mouseX, mouseY, leftPos + 410, bottomY, 42, BOTTOM_BTN_H)) {
                selected.clear();
                anchorIndex = -1;
                confirmUntil = 0;
                return true;
            }
        }

        // 滚动条：按下即定位 + 开始拖拽
        if (maxScroll > 0 && EditorUi.onScrollbar(mouseX, mouseY,
                leftPos + SCROLL_X, topPos + LIST_TOP, LIST_HEIGHT)) {
            scrollbarDragging = true;
            scrollOffset = EditorUi.scrollFromMouseY(mouseY, topPos + LIST_TOP, LIST_HEIGHT,
                    maxScroll, VISIBLE_ROWS);
            return true;
        }

        // 列表行
        int listTop = topPos + LIST_TOP;
        int visibleRows = Math.min(filteredAchievements.size() - scrollOffset,
                Math.max(0, LIST_HEIGHT / ROW_HEIGHT));
        for (int i = 0; i < visibleRows; i++) {
            int idx = scrollOffset + i;
            if (idx >= filteredAchievements.size()) break;
            int y = listTop + i * ROW_HEIGHT;

            if (mouseX < leftPos + ROW_L || mouseX >= leftPos + ROW_R
                    || mouseY < y || mouseY >= y + ROW_HEIGHT) {
                continue;
            }

            AchievementInfo info = filteredAchievements.get(idx);

            // 行内单条按钮
            int btnX = leftPos + BTN_X;
            int btnY = y + (ROW_HEIGHT - 16) / 2;
            if (inside(mouseX, mouseY, btnX, btnY, BTN_W, 16)) {
                toggleSingle(info);
                return true;
            }

            clickRow(info, idx);
            return true;
        }

        return false;
    }

    /** 单击 / Ctrl 多选 / Shift 连选。 */
    private void clickRow(AchievementInfo info, int idx) {
        if (Screen.hasShiftDown() && anchorIndex >= 0 && anchorIndex < filteredAchievements.size()) {
            int from = Math.min(anchorIndex, idx);
            int to = Math.max(anchorIndex, idx);
            if (!Screen.hasControlDown()) selected.clear();
            for (int i = from; i <= to; i++) {
                selected.add(filteredAchievements.get(i).id);
            }
        } else if (Screen.hasControlDown()) {
            if (!selected.remove(info.id)) selected.add(info.id);
        } else {
            selected.clear();
            selected.add(info.id);
        }
        anchorIndex = idx;
        confirmUntil = 0;
    }

    private boolean pickModuleMenu(int mouseX, int mouseY) {
        int x = leftPos + MODULE_BTN_X;
        int y = topPos + 43;
        int w = MODULE_BTN_W + 62;
        for (int i = 0; i < moduleMenuVisible(); i++) {
            int idx = moduleMenuScroll + i;
            if (idx >= modules.size()) break;
            int iy = y + 1 + i * 14;
            if (inside(mouseX, mouseY, x + 1, iy, w - 2, 14)) {
                moduleFilter = modules.get(idx).id;
                moduleMenuOpen = false;
                applyFilter();
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean onPanelDrag(int mouseX, int mouseY, int button, int dx, int dy) {
        if (scrollbarDragging && maxScroll > 0) {
            scrollOffset = EditorUi.scrollFromMouseY(mouseY, topPos + LIST_TOP, LIST_HEIGHT,
                    maxScroll, VISIBLE_ROWS);
            return true;
        }
        return false;
    }

    @Override
    protected boolean onPanelRelease(int mouseX, int mouseY, int button) {
        if (scrollbarDragging) {
            scrollbarDragging = false;
            return true;
        }
        return false;
    }

    @Override
    protected boolean onPanelScroll(int mouseX, int mouseY, double scrollDelta) {
        if (scrollDelta == 0) return false;

        if (moduleMenuOpen) {
            int x = leftPos + MODULE_BTN_X;
            int y = topPos + 43;
            int w = MODULE_BTN_W + 62;
            int h = moduleMenuVisible() * 14 + 2;
            if (inside(mouseX, mouseY, x, y, w, h)) {
                int maxMenuScroll = Math.max(0, modules.size() - moduleMenuVisible());
                moduleMenuScroll = Mth.clamp(moduleMenuScroll - (int) Math.signum(scrollDelta),
                        0, maxMenuScroll);
                return true;
            }
            return false;
        }

        if (maxScroll <= 0) return false;

        int step = (int) Math.signum(scrollDelta) * (Screen.hasShiftDown() ? VISIBLE_ROWS - 1 : 1);
        scrollOffset = Mth.clamp(scrollOffset - step, 0, maxScroll);
        return true;
    }

    // ======================== 键盘 ========================

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean searchFocused = searchField != null && searchField.isFocused();

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (moduleMenuOpen) {
                moduleMenuOpen = false;
                return true;
            }
            if (searchFocused) {
                searchField.setFocused(false);
                return true;
            }
            onClose();
            return true;
        }

        if (searchFocused) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> {
                moveSelection(-1);
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                moveSelection(1);
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                toggleSelected();
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                revokeSelected();
                return true;
            }
            case GLFW.GLFW_KEY_A -> {
                if (Screen.hasControlDown()) {
                    selectAllFiltered();
                    return true;
                }
            }
            case GLFW.GLFW_KEY_C -> {
                if (Screen.hasControlDown()) {
                    copySelectedId();
                    return true;
                }
            }
            default -> { }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void moveSelection(int delta) {
        if (filteredAchievements.isEmpty()) return;

        int current = -1;
        for (int i = 0; i < filteredAchievements.size(); i++) {
            if (selected.contains(filteredAchievements.get(i).id)) {
                current = i;
                break;
            }
        }
        int next = current < 0
                ? (delta > 0 ? 0 : filteredAchievements.size() - 1)
                : Mth.clamp(current + delta, 0, filteredAchievements.size() - 1);

        selected.clear();
        selected.add(filteredAchievements.get(next).id);
        anchorIndex = next;
        confirmUntil = 0;
        ensureVisible(next);
    }

    private void ensureVisible(int idx) {
        if (idx < scrollOffset) {
            scrollOffset = idx;
        } else if (idx >= scrollOffset + VISIBLE_ROWS) {
            scrollOffset = idx - VISIBLE_ROWS + 1;
        }
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
    }

    private void selectAllFiltered() {
        selected.clear();
        for (AchievementInfo info : filteredAchievements) selected.add(info.id);
        anchorIndex = filteredAchievements.isEmpty() ? -1 : 0;
        confirmUntil = 0;
    }

    private void copySelectedId() {
        AchievementInfo info = firstSelected();
        if (info == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) mc.keyboardHandler.setClipboard(info.id);
    }

    private void toggleSelected() {
        AchievementInfo info = firstSelected();
        if (info == null) return;
        toggleSingle(info);
    }

    private void revokeSelected() {
        if (selected.isEmpty()) return;
        if (!canEdit) {
            warnNoPermission();
            return;
        }
        NetworkHandler.CHANNEL.sendToServer(
                new NetworkHandler.BatchAdvancementPacket(new ArrayList<>(selected), false));
    }

    private AchievementInfo firstSelected() {
        for (AchievementInfo info : filteredAchievements) {
            if (selected.contains(info.id)) return info;
        }
        for (AchievementInfo info : allAchievements) {
            if (selected.contains(info.id)) return info;
        }
        return null;
    }

    // ======================== 操作 ========================

    private void toggleSingle(AchievementInfo info) {
        if (!canEdit) {
            warnNoPermission();
            return;
        }
        confirmUntil = 0;
        NetworkHandler.CHANNEL.sendToServer(
                new NetworkHandler.ToggleAdvancementPacket(info.id, !info.completed));
    }

    /**
     * 批量操作：第一次点击只是进入「确认」态（按钮上出现「确认？」），
     * 2.5 秒内再点一次才真正执行 —— 一键撤销几百条成就没有回头路。
     */
    private void doBatch(boolean grant) {
        if (selected.isEmpty()) return;
        if (!canEdit) {
            warnNoPermission();
            return;
        }

        long now = System.currentTimeMillis();
        if (now > confirmUntil || confirmGrant != grant) {
            confirmUntil = now + CONFIRM_WINDOW_MS;
            confirmGrant = grant;
            return;
        }

        confirmUntil = 0;
        NetworkHandler.CHANNEL.sendToServer(
                new NetworkHandler.BatchAdvancementPacket(new ArrayList<>(selected), grant));
    }

    private boolean isConfirming(boolean grant) {
        return System.currentTimeMillis() <= confirmUntil && confirmGrant == grant;
    }

    private void warnNoPermission() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) {
            mc.player.displayClientMessage(
                    Component.translatable("screen.infinitestats.achievements.no_permission"), true);
        }
    }

    // ======================== 工具 ========================

    private int moduleMenuVisible() {
        return Math.min(modules.size(), 12);
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /**
     * 常见模组的命名空间 → 中文名：<b>只在模组自己报的是英文名时</b>用来替换。
     * 模组已经把 displayName 写成中文的，一律以模组为准。
     */
    private static final Map<String, String> KNOWN_MOD_NAMES = Map.ofEntries(
            Map.entry("minecraft", "原版"),
            Map.entry("create", "机械动力"),
            Map.entry("twilightforest", "暮色森林"),
            Map.entry("botania", "植物魔法"),
            Map.entry("tconstruct", "匠魂"),
            Map.entry("mekanism", "通用机械"),
            Map.entry("immersiveengineering", "沉浸工程"),
            Map.entry("ae2", "应用能源 2"),
            Map.entry("appliedenergistics2", "应用能源 2"),
            Map.entry("refinedstorage", "精致存储"),
            Map.entry("apotheosis", "神化"),
            Map.entry("farmersdelight", "农夫乐事"),
            Map.entry("alexscaves", "Alex 的洞穴"),
            Map.entry("alexsmobs", "Alex 的生物"),
            Map.entry("iceandfire", "冰火传说"),
            Map.entry("goety", "阴森"),
            Map.entry("sophisticatedbackpacks", "精妙背包"),
            Map.entry("sophisticatedcore", "精妙核心"),
            Map.entry("waystones", "传送石碑"),
            Map.entry("patchouli", "帕秋莉手册"),
            Map.entry("kubejs", "KubeJS"),
            Map.entry("jade", "Jade"),
            Map.entry("geckolib", "GeckoLib"),
            Map.entry("curios", "Curios API"));

    /**
     * 模组命名空间的显示名。
     * <p>
     * 原版没有「模组中文名」的查询接口，但 Forge 能拿到每个模组在 {@code mods.toml} 里
     * 自己声明的 {@code displayName}，所以优先级是：
     * <ol>
     *   <li>语言文件覆盖 {@code gui.infinitestats.mod.<命名空间>}（整合包想自定义时用）；</li>
     *   <li>模组自己声明的 displayName；如果它已经是中文就直接用；</li>
     *   <li>displayName 是英文、且内置表里有对应中文名时，用内置中文名；</li>
     *   <li>都没有就显示 displayName（再没有就显示原始命名空间）。</li>
     * </ol>
     */
    static String modDisplayName(String namespace) {
        if (namespace == null || namespace.isEmpty()) return "?";

        String key = "gui.infinitestats.mod." + namespace;
        String localized = Language.getInstance().getOrDefault(key, "");
        if (localized != null && !localized.isEmpty() && !localized.equals(key)) return localized;

        String declared = null;
        ModList modList = ModList.get();
        if (modList != null) {
            var container = modList.getModContainerById(namespace);
            if (container.isPresent()) {
                String name = container.get().getModInfo().getDisplayName();
                if (name != null && !name.isBlank() && !name.equalsIgnoreCase(namespace)) {
                    declared = name;
                }
            }
        }

        if (declared != null) {
            // 模组自己给的就是中文 → 用它；还是英文才考虑内置表
            if (containsChinese(declared)) return declared;
            String known = KNOWN_MOD_NAMES.get(namespace);
            return known != null ? known : declared;
        }

        return KNOWN_MOD_NAMES.getOrDefault(namespace, namespace);
    }

    private static boolean containsChinese(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '\u4e00' && c <= '\u9fff') return true;
        }
        return false;
    }

    private static int frameColor(int frame) {
        return switch (frame) {
            case AchievementInfo.FRAME_GOAL -> 0xFFFFD166;
            case AchievementInfo.FRAME_CHALLENGE -> 0xFFC084FC;
            default -> 0xFF7A8CA6;
        };
    }

    private static String frameName(int frame) {
        return switch (frame) {
            case AchievementInfo.FRAME_GOAL -> tr("screen.infinitestats.achievements.type.goal");
            case AchievementInfo.FRAME_CHALLENGE -> tr("screen.infinitestats.achievements.type.challenge");
            default -> tr("screen.infinitestats.achievements.type.task");
        };
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    /** 标题为空时的兜底显示名：取 ID 的 path 段，下划线换成空格。 */
    private static String fallbackName(String id) {
        if (id == null || id.isEmpty()) return "?";
        int colon = id.indexOf(':');
        String path = colon >= 0 ? id.substring(colon + 1) : id;
        return path.replace('_', ' ');
    }

    private ItemStack getIcon(String itemId) {
        return iconCache.computeIfAbsent(itemId == null ? "" : itemId, key -> {
            ResourceLocation rl = ResourceLocation.tryParse(key);
            if (rl == null) return ItemStack.EMPTY;
            Item item = BuiltInRegistries.ITEM.get(rl);
            if (item == Items.AIR) return ItemStack.EMPTY;
            return new ItemStack(item);
        });
    }

    /** 下拉里的一行：{@code id == null} 表示「全部模组」。 */
    private static final class ModuleEntry {
        final String id;
        final int count;

        ModuleEntry(String id, int count) {
            this.id = id;
            this.count = count;
        }
    }
}
