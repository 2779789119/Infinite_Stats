package com.infinitestats.client;

import com.infinitestats.compat.JechCompat;
import com.infinitestats.network.AchievementInfo;
import com.infinitestats.network.NetworkHandler;
import net.minecraft.client.Minecraft;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 成就管理器 —— 查看全部成就、按模组筛选、批量授予 / 撤销。
 *
 * <h2>为什么要重做</h2>
 * 早期版本只有「已完成 / 未完成」两种状态、一屏 4 行、顺序每次打开都可能不同，
 * 而且<b>没有任何权限校验</b> —— 任何玩家都能给自己刷全套成就。现在：
 * <ul>
 *   <li>顺序由服务端排好（模组 → 父先于子 → 标题 → id），不再依赖集合遍历顺序；</li>
 *   <li>一屏 7 行，带框架色条、图标、名称、描述、状态与行内按钮；</li>
 *   <li>部分完成显示 {@code 3/5} 与细进度条，名字变金色；</li>
 *   <li>拼音搜索 + 模组下拉 + 三种排序 + 「另有 N 个技术成就未列出」提示；</li>
 *   <li>单击选中、Ctrl 多选、Shift 连选、Ctrl+A 全选，底栏批量授予 / 撤销（二次确认）；</li>
 *   <li>服务端新增权限开关 {@code GUI.achievementManagerRequireOp}，无权限时按钮置灰。</li>
 * </ul>
 */
public class AchievementManagerScreen extends PanelScreen {

    // ======================== 布局 ========================

    private static final int GUI_W = 460;
    private static final int GUI_H = 320;

    private static final int HEADER_H = 24;
    private static final int STATS_Y = 28;
    private static final int SEARCH_Y = 42;
    private static final int SEARCH_H = 18;
    private static final int TABS_Y = 64;
    private static final int TABS_H = 14;
    private static final int LIST_Y = 88;
    private static final int ROW_H = 28;
    private static final int VISIBLE_ROWS = 7;
    private static final int LIST_H = VISIBLE_ROWS * ROW_H;
    private static final int FOOTER_Y = 292;

    private static final int GAP = 8;
    private static final int DROPDOWN_W = 84;
    private static final int SORT_W = 64;
    /** 行内单条按钮宽度 / 高度（与主线一致：64×16，能放下「完成 / 取消」）。 */
    private static final int ROW_BTN_W = 64;
    private static final int ROW_BTN_H = 16;

    /** 列表行的左右边界与各列位置。 */
    private static final int ROW_L = 4;
    private static final int ROW_R = GUI_W - 4;
    private static final int NAME_X = 32;
    private static final int NAME_R = 336;
    private static final int BTN_X = ROW_R - 4 - ROW_BTN_W;
    private static final int STATUS_R = BTN_X - 6;

    // ======================== 配色 ========================

    private static final int BG_PANEL = 0xE81A1A2E;
    private static final int BG_HEADER = 0xC0222240;
    private static final int BG_ROW = 0x40252535;
    private static final int BG_ROW_HOVER = 0x60353550;
    /** 已完成行：淡绿底（与主线一致，扫一眼就知道哪条做完了）。 */
    private static final int BG_ROW_COMPLETED = 0x301A4A2E;
    private static final int BG_ROW_COMPLETED_HOVER = 0x502D6A3E;
    /** 选中行：蓝底；左侧色条也统一成高亮蓝。 */
    private static final int BG_ROW_SELECTED = 0x7032406A;
    private static final int BG_ROW_SELECTED_HOVER = 0x80405A8E;
    private static final int ACCENT_SELECTED = 0xFF60A5FA;
    private static final int BG_SEARCH = 0x80151520;
    private static final int BG_TAB = 0x40252535;
    private static final int BG_TAB_ACTIVE = 0x804A4A70;
    private static final int TEXT_PRIMARY = 0xFFE2E8F0;
    private static final int TEXT_SECONDARY = 0xFF94A3B8;
    private static final int TEXT_DONE = 0xFF4ADE80;
    private static final int TEXT_PARTIAL = 0xFFFFD166;
    private static final int TEXT_HEADER = 0xFFFFD166;
    private static final int FRAME_TASK_COLOR = 0xFF9EA5B8;
    private static final int FRAME_GOAL_COLOR = 0xFFE8C46A;
    private static final int FRAME_CHALLENGE_COLOR = 0xFFD06BD0;

    // ======================== 状态 ========================

    private int leftPos, topPos;
    private List<AchievementInfo> all = new ArrayList<>();
    private List<AchievementInfo> filtered = new ArrayList<>();

    private EditBox searchField;
    private String searchText = "";
    /** 0=全部 1=已完成 2=未完成 */
    private int filterTab;
    /** 0=默认树序 1=名称 2=进度 */
    private int sortMode;

    /** 当前选中的模组命名空间（null = 全部）。 */
    private String modFilter;
    private boolean dropdownOpen;
    private int dropdownScroll;
    private static final int DROPDOWN_ROWS = 8;

    private int scrollOffset;
    private int maxScroll;
    private int hoveredRow = -1;
    private int cursorRow = -1;
    private int anchorRow = -1;

    private final Set<String> selectedIds = new LinkedHashSet<>();
    private final ConfirmAction confirm = new ConfirmAction();

    private boolean dataReceived;
    private boolean canEdit = true;
    private int skipped;

    private boolean hoveredStats;

    public AchievementManagerScreen() {
        super(Component.translatable("screen.infinitestats.achievements"));
    }

    @Override
    protected int panelW() {
        return GUI_W;
    }

    @Override
    protected int panelH() {
        return GUI_H;
    }

    @Override
    protected void init() {
        super.init();
        leftPos = 0;
        topPos = 0;

        searchField = new EditBox(font, leftPos + GAP, topPos + SEARCH_Y,
                GUI_W - GAP * 2 - DROPDOWN_W - SORT_W - 8, SEARCH_H, Component.empty());
        searchField.setMaxLength(64);
        searchField.setValue(searchText);
        searchField.setHint(Component.translatable("screen.infinitestats.achievements.search_hint"));
        searchField.setResponder(v -> {
            searchText = v;
            applyFilter(true);
        });
        addRenderableWidget(searchField);

        // 右上角关闭（与主线一致）
        addRenderableWidget(Button.builder(Component.literal("✕"), b -> onClose())
                .bounds(leftPos + GUI_W - 22, topPos + 4, 18, 18).build());

        if (!dataReceived) {
            NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.RequestAdvancementsPacket());
        }
        applyFilter(false);
    }

    @Override
    public void tick() {
        super.tick();
        if (NetworkHandler.pendingAdvancements != null) {
            all = new ArrayList<>(NetworkHandler.pendingAdvancements);
            NetworkHandler.pendingAdvancements = null;
            canEdit = NetworkHandler.pendingAdvancementsCanEdit;
            skipped = NetworkHandler.pendingAdvancementsSkipped;
            dataReceived = true;
            // 保留现有选择：刷新后同一条成就仍然存在就继续选中
            selectedIds.removeIf(id -> all.stream().noneMatch(a -> a.id.equals(id)));
            applyFilter(false);
        }
    }

    @Override
    public void onClose() {
        super.onClose();
        NetworkHandler.pendingAdvancements = null;
    }

    // ======================== 过滤 / 排序 ========================

    private void applyFilter(boolean resetScroll) {
        String q = searchText == null ? "" : searchText.toLowerCase(Locale.ROOT).trim();

        List<AchievementInfo> out = new ArrayList<>();
        for (AchievementInfo a : all) {
            if (filterTab == 1 && !a.completed) continue;
            if (filterTab == 2 && a.completed) continue;
            if (modFilter != null && !modFilter.equals(a.namespace)) continue;
            if (!q.isEmpty() && !matches(a, q)) continue;
            out.add(a);
        }

        if (sortMode == 1) {
            out.sort(Comparator.comparing((AchievementInfo a) -> a.displayName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(a -> a.id));
        } else if (sortMode == 2) {
            out.sort(Comparator.comparingInt((AchievementInfo a) -> a.completed ? 1000 : a.progressPercent)
                    .reversed().thenComparing(a -> a.displayName, String.CASE_INSENSITIVE_ORDER));
        }
        // sortMode == 0：沿用服务端的树序，不再排序

        filtered = out;
        if (resetScroll) {
            scrollOffset = 0;
            cursorRow = -1;
            anchorRow = -1;
        }
        maxScroll = Math.max(0, filtered.size() - VISIBLE_ROWS);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
    }

    /** 搜索匹配：名称 / 描述 / 注册名 / 模组显示名 / 命名空间（都支持拼音）。 */
    private boolean matches(AchievementInfo a, String q) {
        if (JechCompat.matches(a.displayName.toLowerCase(Locale.ROOT), q)) return true;
        if (JechCompat.matches(a.description.toLowerCase(Locale.ROOT), q)) return true;
        if (a.id.toLowerCase(Locale.ROOT).contains(q)) return true;
        if (a.namespace.toLowerCase(Locale.ROOT).contains(q)) return true;
        return JechCompat.matches(modDisplayName(a.namespace).toLowerCase(Locale.ROOT), q);
    }

    /** 模组 → 条数（用于下拉列表），按显示名排序。 */
    private Map<String, Integer> modCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (AchievementInfo a : all) {
            counts.merge(a.namespace, 1, Integer::sum);
        }
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort(Comparator.comparing(e -> modDisplayName(e.getKey()), String.CASE_INSENSITIVE_ORDER));
        Map<String, Integer> out = new LinkedHashMap<>();
        for (var e : entries) out.put(e.getKey(), e.getValue());
        return out;
    }

    // ======================== 绘制 ========================

    @Override
    protected void renderPanel(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(leftPos, topPos, leftPos + GUI_W, topPos + GUI_H, BG_PANEL);
        g.fill(leftPos, topPos, leftPos + GUI_W, topPos + HEADER_H, BG_HEADER);
        g.drawCenteredString(font, Component.translatable("screen.infinitestats.achievements"),
                leftPos + GUI_W / 2, topPos + 7, TEXT_HEADER);

        // 统计行：总数 / 已完成 / 跳过提示
        String stats = Component.translatable("screen.infinitestats.achievements.stats",
                all.size(), all.stream().filter(a -> a.completed).count()).getString();
        g.drawString(font, stats, leftPos + GAP, topPos + STATS_Y, TEXT_SECONDARY);
        hoveredStats = mouseX >= leftPos + GAP && mouseX < leftPos + GAP + font.width(stats)
                && mouseY >= topPos + STATS_Y - 2 && mouseY < topPos + STATS_Y + 10;

        if (skipped > 0) {
            String s = Component.translatable("screen.infinitestats.achievements.skipped", skipped).getString();
            g.drawString(font, s, leftPos + GUI_W - GAP - font.width(s), topPos + STATS_Y, TEXT_SECONDARY);
        }

        // 筛选标签（右对齐）
        String[] tabs = {
                Component.translatable("screen.infinitestats.achievements.tab_all").getString(),
                Component.translatable("screen.infinitestats.achievements.tab_done").getString(),
                Component.translatable("screen.infinitestats.achievements.tab_undone").getString()
        };
        int tabW = 58;
        int tabX = leftPos + GUI_W - GAP - tabW * 3 - 4;
        for (int i = 0; i < 3; i++) {
            int x = tabX + i * (tabW + 2);
            int y = topPos + TABS_Y;
            boolean active = filterTab == i;
            boolean hover = mouseX >= x && mouseX < x + tabW && mouseY >= y && mouseY < y + TABS_H;
            g.fill(x, y, x + tabW, y + TABS_H, active ? BG_TAB_ACTIVE : hover ? BG_ROW_HOVER : BG_TAB);
            g.drawCenteredString(font, tabs[i], x + tabW / 2, y + 3,
                    active ? TEXT_PRIMARY : TEXT_SECONDARY);
        }

        // 模组下拉按钮 + 排序按钮（在搜索框右侧）
        int dropX = leftPos + GUI_W - GAP - SORT_W - 4 - DROPDOWN_W;
        int dropY = topPos + SEARCH_Y;
        boolean dropHover = mouseX >= dropX && mouseX < dropX + DROPDOWN_W
                && mouseY >= dropY && mouseY < dropY + SEARCH_H;
        g.fill(dropX, dropY, dropX + DROPDOWN_W, dropY + SEARCH_H, dropHover ? BG_ROW_HOVER : BG_SEARCH);
        String modLabel = EditorUi.ellipsize(font,
                modFilter == null ? Component.translatable("screen.infinitestats.achievements.mod_all").getString()
                        : modDisplayName(modFilter),
                DROPDOWN_W - 12);
        g.drawString(font, modLabel, dropX + 4, dropY + 5, TEXT_PRIMARY);
        g.drawString(font, "▾", dropX + DROPDOWN_W - 10, dropY + 5, TEXT_SECONDARY);

        int sortX = leftPos + GUI_W - GAP - SORT_W;
        boolean sortHover = mouseX >= sortX && mouseX < sortX + SORT_W
                && mouseY >= dropY && mouseY < dropY + SEARCH_H;
        g.fill(sortX, dropY, sortX + SORT_W, dropY + SEARCH_H, sortHover ? BG_ROW_HOVER : BG_SEARCH);
        g.drawCenteredString(font, Component.translatable(sortLabelKey()).getString(),
                sortX + SORT_W / 2, dropY + 5, TEXT_PRIMARY);

        // 列表
        renderList(g, mouseX, mouseY);

        // 底栏
        renderFooter(g, mouseX, mouseY);

        if (!dataReceived) {
            g.drawCenteredString(font, Component.translatable("screen.infinitestats.achievements.loading").getString(),
                    leftPos + GUI_W / 2, topPos + LIST_Y + 40, TEXT_SECONDARY);
        } else if (filtered.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("screen.infinitestats.achievements.empty").getString(),
                    leftPos + GUI_W / 2, topPos + LIST_Y + 40, TEXT_SECONDARY);
        }
    }

    private String sortLabelKey() {
        return switch (sortMode) {
            case 1 -> "screen.infinitestats.achievements.sort_name";
            case 2 -> "screen.infinitestats.achievements.sort_progress";
            default -> "screen.infinitestats.achievements.sort_default";
        };
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY) {
        int listTop = topPos + LIST_Y;
        beginPanelScissor(g, leftPos, listTop, leftPos + GUI_W, listTop + LIST_H);

        hoveredRow = -1;
        int visibleRows = Math.min(filtered.size() - scrollOffset, VISIBLE_ROWS);
        for (int i = 0; i < visibleRows; i++) {
            int idx = scrollOffset + i;
            renderRow(g, filtered.get(idx), idx, listTop + i * ROW_H, mouseX, mouseY);
        }

        endPanelScissor(g);

        if (maxScroll > 0) {
            EditorUi.scrollbar(g, mouseX, mouseY, leftPos + GUI_W - 6, listTop, LIST_H,
                    scrollOffset, maxScroll, VISIBLE_ROWS);
        }
    }

    private void renderRow(GuiGraphics g, AchievementInfo a, int idx, int y, int mouseX, int mouseY) {
        boolean sel = selectedIds.contains(a.id);
        boolean hover = mouseX >= leftPos + ROW_L && mouseX < leftPos + ROW_R
                && mouseY >= y && mouseY < y + ROW_H;
        if (hover) hoveredRow = idx;

        // 行底色：选中（蓝）→ 已完成（淡绿）→ 普通（悬停更亮）
        int bg;
        if (sel) {
            bg = hover ? BG_ROW_SELECTED_HOVER : BG_ROW_SELECTED;
        } else if (a.completed) {
            bg = hover ? BG_ROW_COMPLETED_HOVER : BG_ROW_COMPLETED;
        } else {
            bg = hover ? BG_ROW_HOVER : BG_ROW;
        }
        g.fill(leftPos + ROW_L, y, leftPos + ROW_R, y + ROW_H - 1, bg);

        // 左侧色条：框架类型（普通 / 目标 / 挑战），选中时统一成高亮蓝
        int frameColor = switch (a.frame) {
            case AchievementInfo.FRAME_GOAL -> FRAME_GOAL_COLOR;
            case AchievementInfo.FRAME_CHALLENGE -> FRAME_CHALLENGE_COLOR;
            default -> FRAME_TASK_COLOR;
        };
        g.fill(leftPos + ROW_L, y, leftPos + ROW_L + 3, y + ROW_H - 1,
                sel ? ACCENT_SELECTED : frameColor);

        // 图标
        ItemStack icon = getIcon(a.iconItemId);
        if (!icon.isEmpty()) {
            g.renderItem(icon, leftPos + 12, y + (ROW_H - 16) / 2);
        }

        // 名称（完成绿 / 部分完成金 / 未完成白）+ 隐藏标记
        String name = a.displayName == null || a.displayName.isEmpty() ? a.id : a.displayName;
        int nameColor = a.completed ? TEXT_DONE : a.isPartial() ? TEXT_PARTIAL : TEXT_PRIMARY;
        String shownName = EditorUi.ellipsize(font, name, NAME_R - NAME_X - (a.hidden ? 14 : 0));
        g.drawString(font, shownName, leftPos + NAME_X, y + 3, nameColor, false);
        if (a.hidden) {
            g.drawString(font, "◇", leftPos + NAME_X + font.width(shownName) + 3, y + 3,
                    TEXT_SECONDARY, false);
        }

        // 描述
        g.drawString(font, EditorUi.ellipsize(font, a.description, NAME_R - NAME_X),
                leftPos + NAME_X, y + 14, TEXT_SECONDARY, false);

        // 部分完成：底部细进度条（横跨名称栏）
        if (a.isPartial() && a.criteriaTotal > 0) {
            int barW = NAME_R - NAME_X;
            g.fill(leftPos + NAME_X, y + 24, leftPos + NAME_R, y + 25, 0x30FFFFFF);
            int filled = Math.round(barW * (a.criteriaDone / (float) a.criteriaTotal));
            g.fill(leftPos + NAME_X, y + 24, leftPos + NAME_X + Math.max(1, filled), y + 25, TEXT_PARTIAL);
        }

        // 状态文字（右对齐）：已完成 / 未完成 / 3/5
        String status;
        int statusColor;
        if (a.completed) {
            status = Component.translatable("screen.infinitestats.achievements.completed").getString();
            statusColor = TEXT_DONE;
        } else if (a.isPartial()) {
            status = a.criteriaDone + "/" + a.criteriaTotal;
            statusColor = TEXT_PARTIAL;
        } else {
            status = Component.translatable("screen.infinitestats.achievements.uncompleted").getString();
            statusColor = 0xFFF87171;
        }
        g.drawString(font, status, leftPos + STATUS_R - font.width(status),
                y + (ROW_H - 8) / 2, statusColor, false);

        renderToggleButton(g, a, y, mouseX, mouseY);
    }

    private void renderToggleButton(GuiGraphics g, AchievementInfo a, int y, int mouseX, int mouseY) {
        int btnX = leftPos + BTN_X;
        int btnY = y + (ROW_H - ROW_BTN_H) / 2;
        boolean hover = canEdit && mouseX >= btnX && mouseX < btnX + ROW_BTN_W
                && mouseY >= btnY && mouseY < btnY + ROW_BTN_H;

        int bg;
        if (!canEdit) {
            bg = 0x30202030;
        } else if (a.completed) {
            bg = hover ? 0xFF8A3D2D : 0xFF6B2A1A;
        } else {
            bg = hover ? 0xFF2D8A4E : 0xFF1A5334;
        }
        g.fill(btnX, btnY, btnX + ROW_BTN_W, btnY + ROW_BTN_H, bg);
        g.drawCenteredString(font,
                Component.translatable(a.completed
                        ? "screen.infinitestats.achievements.revoke"
                        : "screen.infinitestats.achievements.grant").getString(),
                btnX + ROW_BTN_W / 2, btnY + 4, canEdit ? 0xFFFFFFFF : 0xFF6B7280);
    }

    private void renderFooter(GuiGraphics g, int mouseX, int mouseY) {
        int y = topPos + FOOTER_Y;

        // 没选中时只给一行操作提示，不画按钮（与主线一致）
        if (selectedIds.isEmpty()) {
            String hint = Component.translatable(canEdit
                    ? "screen.infinitestats.achievements.hint"
                    : "screen.infinitestats.achievements.no_permission").getString();
            g.drawString(font, EditorUi.ellipsize(font, hint, GUI_W - 16),
                    leftPos + GAP, y + 3, canEdit ? TEXT_SECONDARY : 0xFFF87171);
            return;
        }

        String sel = Component.translatable("screen.infinitestats.achievements.selected",
                selectedIds.size()).getString();
        g.drawString(font, sel, leftPos + GAP, y + 3, TEXT_SECONDARY);

        boolean grantConfirming = confirm.isConfirming("grant");
        boolean revokeConfirming = confirm.isConfirming("revoke");
        // 「完成所选 / 撤销所选 / 清空」三个按钮，位置与宽度与主版本一致
        drawFooterButton(g, leftPos + 294, y, 54, Component.translatable(grantConfirming
                ? "screen.infinitestats.ui.confirm"
                : "screen.infinitestats.achievements.grant_selected").getString(),
                canEdit, mouseX, mouseY, 0xFF1A5334);
        drawFooterButton(g, leftPos + 352, y, 54, Component.translatable(revokeConfirming
                ? "screen.infinitestats.ui.confirm"
                : "screen.infinitestats.achievements.revoke_selected").getString(),
                canEdit, mouseX, mouseY, 0xFF6B2A1A);
        drawFooterButton(g, leftPos + 410, y, 42,
                Component.translatable("screen.infinitestats.achievements.clear").getString(),
                true, mouseX, mouseY, 0xFF2A2A3E);
    }

    private void drawFooterButton(GuiGraphics g, int x, int y, int w, String label,
                                  boolean enabled, int mouseX, int mouseY, int bg) {
        boolean hover = enabled && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 14;
        g.fill(x, y, x + w, y + 14, enabled ? (hover ? bg | 0xFF000000 : bg) : 0xFF2A2A36);
        g.drawCenteredString(font, EditorUi.ellipsize(font, label, w - 4), x + w / 2, y + 3,
                enabled ? TEXT_PRIMARY : 0xFF777777);
    }

    // ======================== 浮层：模组下拉 ========================

    @Override
    public void renderTopLayer(GuiGraphics g, int screenMouseX, int screenMouseY, float partialTick) {
        if (!dropdownOpen) return;

        beginPanelPose(g);
        int mx = toPanelX(screenMouseX);
        int my = toPanelY(screenMouseY);

        int x = leftPos + GUI_W - GAP - SORT_W - 4 - DROPDOWN_W;
        int y = topPos + SEARCH_Y + SEARCH_H + 2;
        Map<String, Integer> counts = modCounts();
        int rowH = 14;
        int visible = Math.min(DROPDOWN_ROWS, counts.size() + 1);
        int h = visible * rowH + 4;

        EditorUi.vanillaBackdrop(g, x, y, DROPDOWN_W, h);

        int row = 0;
        // 第一行「全部模组」
        int allY = y + 2;
        boolean allHover = mx >= x + 1 && mx < x + DROPDOWN_W - 1 && my >= allY && my < allY + rowH;
        if (modFilter == null) g.fill(x + 1, allY, x + 3, allY + rowH, 0xFF8060E0);
        g.fill(x + 1, allY, x + DROPDOWN_W - 1, allY + rowH, allHover ? 0x40FFFFFF : 0);
        g.drawString(font, Component.translatable("screen.infinitestats.achievements.mod_all").getString(),
                x + 6, allY + 3, 0xFFFFFFFF);
        row++;

        int i = 0;
        for (var e : counts.entrySet()) {
            if (i++ < dropdownScroll) continue;
            if (row >= visible) break;
            int ry = y + 2 + row * rowH;
            boolean hover = mx >= x + 1 && mx < x + DROPDOWN_W - 1 && my >= ry && my < ry + rowH;
            boolean active = e.getKey().equals(modFilter);
            if (active) g.fill(x + 1, ry, x + 3, ry + rowH, 0xFF8060E0);
            g.fill(x + 1, ry, x + DROPDOWN_W - 1, ry + rowH, hover ? 0x40FFFFFF : 0);
            g.drawString(font, EditorUi.ellipsize(font, modDisplayName(e.getKey()), DROPDOWN_W - 30),
                    x + 6, ry + 3, 0xFFFFFFFF);
            String n = String.valueOf(e.getValue());
            g.drawString(font, n, x + DROPDOWN_W - 6 - font.width(n), ry + 3, 0xFFAAAAAA);
            row++;
        }

        if (counts.size() + 1 > visible) {
            int thumbH = Math.max(10, (int) ((float) visible / (counts.size() + 1) * h));
            int thumbY = y + (int) ((h - thumbH) * ((float) dropdownScroll
                    / Math.max(1, counts.size() + 1 - visible)));
            g.fill(x + DROPDOWN_W - 3, y, x + DROPDOWN_W - 1, y + h, 0x60000000);
            g.fill(x + DROPDOWN_W - 3, thumbY, x + DROPDOWN_W - 1, thumbY + thumbH, 0xFFAAAAAA);
        }

        endPanelPose(g);
    }

    @Override
    protected void renderPanelOverlay(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (hoveredRow >= 0 && hoveredRow < filtered.size()) {
            AchievementInfo a = filtered.get(hoveredRow);
            List<String> lines = new ArrayList<>();
            lines.add("§f" + a.displayName);
            for (String line : EditorUi.wrap(font, "§7" + a.description, 220)) lines.add(line);
            lines.add("§8" + a.id);
            lines.add("§7" + Component.translatable("screen.infinitestats.achievements.mod", modDisplayName(a.namespace)).getString());
            lines.add("§7" + Component.translatable(frameKey(a.frame)).getString()
                    + " §8· §7" + Component.translatable(a.completed
                    ? "screen.infinitestats.achievements.completed"
                    : "screen.infinitestats.achievements.uncompleted").getString());
            if (a.criteriaTotal > 0) {
                lines.add("§7" + Component.translatable("screen.infinitestats.achievements.progress",
                        a.criteriaDone, a.criteriaTotal, a.progressPercent).getString());
            }
            if (a.hidden) {
                lines.add("§8" + Component.translatable("screen.infinitestats.achievements.hidden").getString());
            }
            queueTooltip(g, mouseX, mouseY, lines, EditorUi.ACCENT);
        }

        if (hoveredStats) {
            queueTooltip(g, mouseX, mouseY, List.of(
                    "§7" + Component.translatable("screen.infinitestats.achievements.stats_tip").getString(),
                    "§7" + Component.translatable("screen.infinitestats.achievements.stats_skip_tip", skipped).getString()
            ), EditorUi.INFO);
        }
    }

    private static String frameKey(int frame) {
        return switch (frame) {
            case AchievementInfo.FRAME_GOAL -> "screen.infinitestats.achievements.frame_goal";
            case AchievementInfo.FRAME_CHALLENGE -> "screen.infinitestats.achievements.frame_challenge";
            default -> "screen.infinitestats.achievements.frame_task";
        };
    }

    // ======================== 输入 ========================

    /** 滚动条：先接管，别让点击落到行 / 按钮上。 */
    @Override
    protected boolean onPanelClick(int mouseX, int mouseY, int button) {
        if (button == 0 && beginScrollbarDrag(mouseX, mouseY, leftPos + GUI_W - 6,
                topPos + LIST_Y, LIST_H, scrollOffset, maxScroll, VISIBLE_ROWS,
                v -> scrollOffset = Mth.clamp(v, 0, maxScroll))) {
            return true;
        }
        if (button != 0) return false;

        // 模组下拉展开时，优先处理下拉里的点击
        if (dropdownOpen) {
            if (handleDropdownClick(mouseX, mouseY)) return true;
            dropdownOpen = false;
            return true;
        }

        // 搜索框区域交给控件
        if (mouseY >= topPos + SEARCH_Y && mouseY < topPos + SEARCH_Y + SEARCH_H) {
            int dropX = leftPos + GUI_W - GAP - SORT_W - 4 - DROPDOWN_W;
            int sortX = leftPos + GUI_W - GAP - SORT_W;
            if (mouseX >= dropX && mouseX < dropX + DROPDOWN_W) {
                dropdownOpen = true;
                dropdownScroll = 0;
                playClick();
                return true;
            }
            if (mouseX >= sortX && mouseX < sortX + SORT_W) {
                sortMode = (sortMode + 1) % 3;
                applyFilter(false);
                playClick();
                return true;
            }
        }

        // 筛选标签
        int tabW = 58;
        int tabX = leftPos + GUI_W - GAP - tabW * 3 - 4;
        if (mouseY >= topPos + TABS_Y && mouseY < topPos + TABS_Y + TABS_H) {
            for (int i = 0; i < 3; i++) {
                int x = tabX + i * (tabW + 2);
                if (mouseX >= x && mouseX < x + tabW) {
                    filterTab = i;
                    applyFilter(true);
                    playClick();
                    return true;
                }
            }
        }

        // 底栏按钮：完成所选 / 撤销所选 / 清空（没选中时不响应）
        int y = topPos + FOOTER_Y;
        if (!selectedIds.isEmpty() && mouseY >= y && mouseY < y + 14) {
            if (mouseX >= leftPos + 294 && mouseX < leftPos + 348) {
                batchAction(true);
                return true;
            }
            if (mouseX >= leftPos + 352 && mouseX < leftPos + 406) {
                batchAction(false);
                return true;
            }
            if (mouseX >= leftPos + 410 && mouseX < leftPos + 452) {
                selectedIds.clear();
                confirm.reset();
                return true;
            }
        }

        // 列表行
        int idx = rowAt(mouseX, mouseY);
        if (idx >= 0) {
            AchievementInfo a = filtered.get(idx);
            int btnX = leftPos + BTN_X;
            int rowTop = topPos + LIST_Y + (idx - scrollOffset) * ROW_H;
            int btnY = rowTop + (ROW_H - ROW_BTN_H) / 2;
            if (mouseX >= btnX && mouseX < btnX + ROW_BTN_W
                    && mouseY >= btnY && mouseY < btnY + ROW_BTN_H) {
                toggleOne(a);
                return true;
            }

            // Ctrl = 切换选中；Shift = 连选；普通点击 = 单选并移动光标
            if (Screen.hasControlDown()) {
                if (!selectedIds.remove(a.id)) selectedIds.add(a.id);
            } else if (Screen.hasShiftDown() && anchorRow >= 0) {
                int from = Math.min(anchorRow, idx);
                int to = Math.max(anchorRow, idx);
                selectedIds.clear();
                for (int i = from; i <= to && i < filtered.size(); i++) {
                    selectedIds.add(filtered.get(i).id);
                }
            } else {
                selectedIds.clear();
                selectedIds.add(a.id);
                anchorRow = idx;
            }
            cursorRow = idx;
            playClick();
            return true;
        }
        return false;
    }

    private boolean handleDropdownClick(int mouseX, int mouseY) {
        int x = leftPos + GUI_W - GAP - SORT_W - 4 - DROPDOWN_W;
        int y = topPos + SEARCH_Y + SEARCH_H + 2;
        Map<String, Integer> counts = modCounts();
        int rowH = 14;
        int visible = Math.min(DROPDOWN_ROWS, counts.size() + 1);

        if (mouseX < x || mouseX >= x + DROPDOWN_W) return false;
        if (mouseY < y + 2 || mouseY >= y + 2 + visible * rowH) return false;

        int row = (mouseY - y - 2) / rowH;
        if (row == 0) {
            modFilter = null;
        } else {
            int i = 0;
            String picked = null;
            for (var e : counts.entrySet()) {
                if (i++ < dropdownScroll) continue;
                if (row - 1 == i - 1 - dropdownScroll) {
                    picked = e.getKey();
                    break;
                }
            }
            if (picked == null) return false;
            modFilter = picked;
        }
        dropdownOpen = false;
        applyFilter(true);
        playClick();
        return true;
    }

    private int rowAt(int mouseX, int mouseY) {
        if (mouseX < leftPos + 2 || mouseX >= leftPos + GUI_W - 2) return -1;
        int listTop = topPos + LIST_Y;
        if (mouseY < listTop || mouseY >= listTop + LIST_H) return -1;
        int idx = scrollOffset + (mouseY - listTop) / ROW_H;
        return idx >= 0 && idx < filtered.size() ? idx : -1;
    }

    @Override
    protected boolean onPanelDrag(int mouseX, int mouseY, int button, int dragX, int dragY) {
        return button == 0 && updateScrollbarDrag(mouseY);
    }

    @Override
    protected boolean onPanelRelease(int mouseX, int mouseY, int button) {
        return button == 0 && endScrollbarDrag();
    }

    @Override
    protected boolean onPanelScroll(int mouseX, int mouseY, double delta) {
        if (delta == 0) return false;
        if (dropdownOpen) {
            Map<String, Integer> counts = modCounts();
            int max = Math.max(0, counts.size() + 1 - DROPDOWN_ROWS);
            dropdownScroll = Mth.clamp(dropdownScroll + (delta > 0 ? -1 : 1), 0, max);
            return true;
        }
        if (maxScroll <= 0) return false;
        scrollOffset = Mth.clamp(scrollOffset - (int) Math.signum(delta), 0, maxScroll);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (dropdownOpen) {
                dropdownOpen = false;
                return true;
            }
            onClose();
            return true;
        }
        if (dropdownOpen && (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN)) {
            Map<String, Integer> counts = modCounts();
            int max = Math.max(0, counts.size() + 1 - DROPDOWN_ROWS);
            dropdownScroll = Mth.clamp(dropdownScroll
                    + (keyCode == GLFW.GLFW_KEY_DOWN ? 1 : -1), 0, max);
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_UP) {
            moveCursor(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            moveCursor(1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (cursorRow >= 0 && cursorRow < filtered.size()) {
                toggleOne(filtered.get(cursorRow));
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            if (cursorRow >= 0 && cursorRow < filtered.size()) {
                AchievementInfo a = filtered.get(cursorRow);
                if (a.completed) {
                    sendToggle(a.id, false);
                    return true;
                }
            }
        }
        if (keyCode == GLFW.GLFW_KEY_A && Screen.hasControlDown()) {
            selectAll();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_C && Screen.hasControlDown()) {
            if (cursorRow >= 0 && cursorRow < filtered.size() && minecraft != null && minecraft.keyboardHandler != null) {
                minecraft.keyboardHandler.setClipboard(filtered.get(cursorRow).id);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void moveCursor(int delta) {
        if (filtered.isEmpty()) return;
        int next = cursorRow < 0 ? (delta > 0 ? 0 : 0) : cursorRow + delta;
        next = Mth.clamp(next, 0, filtered.size() - 1);
        cursorRow = next;
        selectedIds.clear();
        selectedIds.add(filtered.get(next).id);
        anchorRow = next;
        if (next < scrollOffset) scrollOffset = next;
        if (next >= scrollOffset + VISIBLE_ROWS) scrollOffset = next - VISIBLE_ROWS + 1;
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
    }

    private void selectAll() {
        if (!canEdit) return;
        selectedIds.clear();
        for (AchievementInfo a : filtered) selectedIds.add(a.id);
        playClick();
    }

    /** 行内按钮：单条立即执行（不需要二次确认，误点可以再点回来）。 */
    private void toggleOne(AchievementInfo a) {
        if (!canEdit) return;
        sendToggle(a.id, !a.completed);
    }

    /** 底栏批量：第一次点进入确认态，2.5 秒内再点才执行。 */
    private void batchAction(boolean grant) {
        if (!canEdit || selectedIds.isEmpty()) return;
        String key = grant ? "grant" : "revoke";
        if (!confirm.confirm(key)) {
            playClick();
            return;
        }
        List<String> ids = new ArrayList<>();
        for (String id : selectedIds) {
            AchievementInfo a = findById(id);
            if (a == null) continue;
            // 只处理状态确实不同的，避免无意义请求
            if (a.completed == grant) continue;
            ids.add(id);
        }
        if (!ids.isEmpty()) {
            NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.BatchAdvancementPacket(ids, grant));
        }
        playClick();
    }

    private AchievementInfo findById(String id) {
        for (AchievementInfo a : all) {
            if (a.id.equals(id)) return a;
        }
        return null;
    }

    private void sendToggle(String id, boolean grant) {
        if (!canEdit) return;
        NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.ToggleAdvancementPacket(id, grant));
        playClick();
    }

    private void playClick() {
        if (minecraft == null) return;
        minecraft.getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    // ======================== 模组名 / 图标 ========================

    /** 常见模组的中文名（模组自己报英文时才用）。 */
    private static final Map<String, String> KNOWN_MOD_NAMES = Map.ofEntries(
            Map.entry("create", "机械动力"),
            Map.entry("twilightforest", "暮色森林"),
            Map.entry("mekanism", "通用机械"),
            Map.entry("botania", "植物魔法"),
            Map.entry("ae2", "应用能源2"),
            Map.entry("thermal", "热力"),
            Map.entry("immersiveengineering", "沉浸工程"),
            Map.entry("farmersdelight", "农夫乐事"),
            Map.entry("apotheosis", "神化"));

    /**
     * 模组显示名：语言文件覆盖 → 模组自己声明的 displayName → 内置常见中文名 → 命名空间。
     */
    public static String modDisplayName(String namespace) {
        if (namespace == null || namespace.isEmpty()) return "?";
        if ("minecraft".equals(namespace)) {
            return Component.translatable("gui.infinitestats.mod.minecraft").getString();
        }

        String key = "gui.infinitestats.mod." + namespace;
        String localized = net.minecraft.locale.Language.getInstance().getOrDefault(key, "");
        if (!localized.isEmpty() && !localized.equals(key)) return localized;

        try {
            var container = net.neoforged.fml.ModList.get().getModContainerById(namespace);
            if (container.isPresent()) {
                String name = container.get().getModInfo().getDisplayName();
                if (name != null && !name.isBlank()) {
                    // 模组报的已经是中文就直接用；报英文时先看内置表
                    for (int i = 0; i < name.length(); i++) {
                        if (name.charAt(i) > 0x2E80) return name;
                    }
                    String known = KNOWN_MOD_NAMES.get(namespace);
                    return known != null ? known : name;
                }
            }
        } catch (Throwable ignored) {
            // ModList 未就绪 / 接口变化：回退到命名空间
        }
        String known = KNOWN_MOD_NAMES.get(namespace);
        return known != null ? known : namespace;
    }

    private ItemStack getIcon(String itemId) {
        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        if (rl == null) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.get(rl);
        if (item == Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(item);
    }
}
