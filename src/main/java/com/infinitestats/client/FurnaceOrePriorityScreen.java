package com.infinitestats.client;

import com.infinitestats.furnace.FurnaceOrePriorityMenu;
import com.infinitestats.furnace.PlayerFurnaceData;
import com.infinitestats.network.NetworkHandler;
import com.infinitestats.network.NetworkHandler.FurnaceOrePriorityRequestPacket;
import com.infinitestats.network.NetworkHandler.FurnaceOrePrioritySyncPacket;
import com.infinitestats.network.NetworkHandler.FurnaceOrePriorityUpdatePacket;
import com.infinitestats.network.NetworkHandler.FurnaceOpenPacket;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 「矿石优先顺序」设置界面（重制版）。
 *
 * <pre>
 * ┌────────────────────────────────────────────────────┐ y=0
 * │            矿石优先顺序                    [返回]  │ 标题栏
 * │        数字越小越优先放入熔炼                      │
 * ├────────────────────────────────────────────────────┤ y=24
 * │ 待炼仓：  [矿1] [矿2] [矿3] …                      │ 仓内矿石条
 * ├──────────────────────┬─────────────────────────────┤ y=46
 * │ 优先顺序：    [清空] │ 可加入：          [搜索…]   │ 分栏标题
 * │  1 [矿] 名称  ↑ ↓ ✕  │ [矿] 名称           ＋      │
 * │  2 [矿] 名称  ↑ ↓ ✕  │ [矿] 名称           ＋      │ 列表区（5 行）
 * │  …                   │  …                          │
 * ├──────────────────────┴─────────────────────────────┤ y=164
 * │                      物品栏                        │
 * │                 [ 3 × 9 主背包 ]                   │
 * │                 [ 1 × 9 快捷栏 ]                   │
 * └────────────────────────────────────────────────────┘ y=258
 * </pre>
 *
 * <p>交互：
 * <ul>
 *   <li>点击右侧矿石行 / 顶部待炼仓矿石 / 背包内可熔炼物品 → 加入优先顺序；</li>
 *   <li>左栏行按下后可直接拖动排序，也可用 {@code ↑} {@code ↓} 微调、{@code ✕} 移除；</li>
 *   <li>鼠标滚轮按所在列独立滚动，两列各有滚动条；</li>
 *   <li>搜索框按注册名 / 显示名过滤右栏。</li>
 * </ul>
 *
 * <p>绘制分层：{@link #renderBg} 负责底板与悬停高亮（绝对坐标）；
 * {@link #renderLabels} 负责文字 / 图标 / 自绘按钮（已由父类平移到面板原点，用相对坐标）；
 * {@link #render} 在父类渲染之后补画拖拽残影与自定义 tooltip。
 */
@OnlyIn(Dist.CLIENT)
public class FurnaceOrePriorityScreen extends AbstractContainerScreen<FurnaceOrePriorityMenu> {

    // ══════════════════════════ 布局常量 ══════════════════════════

    /** 面板尺寸（同时决定 {@code imageWidth / imageHeight}）。 */
    private static final int PANEL_W = 320;
    private static final int PANEL_H = 258;

    /** 标题栏高度。 */
    private static final int HEADER_H = 24;

    /** 待炼仓矿石条（相对面板左上角）。 */
    private static final int RESERVE_LABEL_X = 10;
    private static final int RESERVE_LABEL_Y = 32;
    private static final int RESERVE_ICON_X = 70;
    private static final int RESERVE_ICON_Y = 28;
    private static final int RESERVE_STEP = 20;
    private static final int RESERVE_MAX = 11;

    /** 分栏标题行。 */
    private static final int HEADER_LINE_Y = 46;
    private static final int COL_TITLE_Y = 48;

    /** 列表区。 */
    private static final int LIST_TOP = 62;
    private static final int LIST_ROWS = 5;
    private static final int ROW_H = 20;
    private static final int LIST_BOTTOM = LIST_TOP + LIST_ROWS * ROW_H; // 162

    /** 两栏横向布局。 */
    private static final int LEFT_X = 10;
    private static final int LEFT_W = 142;
    private static final int LEFT_SCROLL_X = 154;
    private static final int RIGHT_X = 162;
    private static final int RIGHT_W = 142;
    private static final int RIGHT_SCROLL_X = 306;

    /** 左栏行内元素。 */
    private static final int BTN_W = 16;
    private static final int BTN_H = 16;
    private static final int L_INDEX_X = LEFT_X + 2;   // 12
    private static final int L_ICON_X = LEFT_X + 12;   // 22
    private static final int L_NAME_X = LEFT_X + 30;   // 40
    private static final int L_NAME_W = 52;
    private static final int L_UP_X = LEFT_X + 88;     // 98
    private static final int L_DOWN_X = LEFT_X + 106;  // 116
    private static final int L_DEL_X = LEFT_X + 124;   // 134

    /** 右栏行内元素。 */
    private static final int R_ICON_X = RIGHT_X + 4;   // 166
    private static final int R_NAME_X = RIGHT_X + 24;  // 186
    private static final int R_NAME_W = 92;
    private static final int R_ADD_X = RIGHT_X + 120;  // 282
    private static final int R_ADD_W = 20;

    /** 搜索框 / 清空按钮。 */
    private static final int SEARCH_X = 210;
    private static final int SEARCH_Y = 44;
    private static final int SEARCH_W = 94;
    private static final int SEARCH_H = 16;
    private static final int CLEAR_X = 110;
    private static final int CLEAR_Y = 44;
    private static final int CLEAR_W = 42;
    private static final int CLEAR_H = 16;

    /** 底部分隔线与背包标签。 */
    private static final int SEP_Y = LIST_BOTTOM + 2;  // 164
    private static final int INV_LABEL_Y = 168;

    // ══════════════════════════ 配色 ══════════════════════════

    private static final int C_PANEL = 0xF00E0E15;
    private static final int C_BORDER = 0xFF34344A;
    private static final int C_SEP = 0xFF2C2C3A;
    private static final int C_LIST_BG = 0xFF15151D;
    private static final int C_ROW_HOVER = 0xFF23233A;
    private static final int C_GOLD = 0xFFFFC24E;
    private static final int C_BLUE = 0xFF8FB0FF;
    private static final int C_TEXT = 0xFFCDCDD8;
    private static final int C_TEXT_DIM = 0xFF8A8A9A;
    private static final int C_TEXT_OFF = 0xFF5A5A68;
    private static final int C_TRACK = 0xFF20202C;
    private static final int C_SLIDER = 0xFF7A7A96;
    private static final int C_BTN = 0xFF2A2A38;
    private static final int C_BTN_HOVER = 0xFF46465E;

    // ══════════════════════════ 状态 ══════════════════════════

    private EditBox searchBox;

    private List<String> priorityView = new ArrayList<>();
    private List<String> availableView = new ArrayList<>();
    private List<String> filteredCache = new ArrayList<>();
    private List<String> reserveIds = new ArrayList<>();
    private List<Long> reserveAmounts = new ArrayList<>();

    /** 物品注册名 → 显示名 缓存（搜索过滤与行渲染复用）。 */
    private final Map<String, String> nameCache = new HashMap<>();

    private int scrollL = 0;
    private int scrollR = 0;

    /** 列表区绝对坐标范围。 */
    private int contentTop;
    private int contentBottom;

    /** 拖拽排序状态：源行下标，-1 表示未拖拽。 */
    private int dragIndex = -1;
    private boolean dragging = false;
    private double dragMouseX;
    private double dragMouseY;

    // ══════════════════════════ 文本 ══════════════════════════

    private final Component title = Component.translatable("gui.infinitestats.furnace.ore_priority.title");
    private final Component subtitle = Component.translatable("gui.infinitestats.furnace.ore_priority.subtitle");
    private final Component prioHeader = Component.translatable("gui.infinitestats.furnace.ore_priority.priority_header");
    private final Component availHeader = Component.translatable("gui.infinitestats.furnace.ore_priority.available");
    private final Component reserveHeader = Component.translatable("gui.infinitestats.furnace.ore_priority.reserve");
    private final Component searchHint = Component.translatable("gui.infinitestats.furnace.ore_priority.search");
    private final Component invLabel = Component.translatable("container.inventory");
    private final Component closeLabel = Component.translatable("gui.infinitestats.furnace.ore_priority.close");
    private final Component clearLabel = Component.translatable("gui.infinitestats.furnace.ore_priority.clear");
    private final Component emptyPriority = Component.translatable("gui.infinitestats.furnace.ore_priority.empty_priority");
    private final Component emptyAvailable = Component.translatable("gui.infinitestats.furnace.ore_priority.empty_available");
    private final Component emptyReserve = Component.translatable("gui.infinitestats.furnace.ore_priority.empty_reserve");
    private final Component tipReserve = Component.translatable("gui.infinitestats.furnace.ore_priority.tip_reserve");
    private final Component tipDrag = Component.translatable("gui.infinitestats.furnace.ore_priority.tip_drag");

    public FurnaceOrePriorityScreen(FurnaceOrePriorityMenu menu, Inventory inv, Component unused) {
        super(menu, inv, Component.translatable("gui.infinitestats.furnace.ore_priority.title"));
        this.imageWidth = PANEL_W;
        this.imageHeight = PANEL_H;
    }

    // ══════════════════════════ 初始化 ══════════════════════════

    @Override
    protected void init() {
        super.init();
        this.contentTop = topPos + LIST_TOP;
        this.contentBottom = topPos + LIST_BOTTOM;

        this.addRenderableWidget(Button.builder(closeLabel, b -> closeToFurnace())
                .bounds(leftPos + PANEL_W - 56, topPos + 4, 50, 16).build());

        this.searchBox = new EditBox(this.font, leftPos + SEARCH_X, topPos + SEARCH_Y,
                SEARCH_W, SEARCH_H, searchHint);
        this.searchBox.setMaxLength(64);
        this.searchBox.setResponder(s -> {
            refreshFilter();
            scrollR = 0;
        });
        this.addRenderableWidget(this.searchBox);

        refreshFilter();
        NetworkHandler.CHANNEL.sendToServer(new FurnaceOrePriorityRequestPacket());
    }

    private void closeToFurnace() {
        NetworkHandler.CHANNEL.sendToServer(new FurnaceOpenPacket());
    }

    // ══════════════════════════ 数据工具 ══════════════════════════

    private static String idOf(ItemStack stack) {
        return ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
    }

    private static ItemStack stackOf(String id) {
        if (id == null) return ItemStack.EMPTY;
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return ItemStack.EMPTY;
        Item item = ForgeRegistries.ITEMS.getValue(rl);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static boolean isSmeltable(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || stack.isEmpty()) return false;
        return PlayerFurnaceData.hasSmeltRecipe(mc.level, stack);
    }

    private String displayNameOf(String id) {
        return nameCache.computeIfAbsent(id, k -> {
            ItemStack s = stackOf(k);
            return s.isEmpty() ? k : s.getHoverName().getString();
        });
    }

    /** 依据搜索框内容重建右栏缓存（避免每帧重复过滤整张表）。 */
    private void refreshFilter() {
        String q = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            filteredCache = availableView;
            return;
        }
        List<String> out = new ArrayList<>();
        for (String id : availableView) {
            if (id.toLowerCase(Locale.ROOT).contains(q)
                    || displayNameOf(id).toLowerCase(Locale.ROOT).contains(q)) {
                out.add(id);
            }
        }
        filteredCache = out;
    }

    private List<String> filteredAvailable() {
        return filteredCache;
    }

    private static String formatAmount(long v) {
        if (v < 1000) return String.valueOf(v);
        if (v < 1_000_000) return (v / 1000) + "k";
        if (v < 1_000_000_000) return (v / 1_000_000) + "M";
        return (v / 1_000_000_000) + "B";
    }

    private static int clampScroll(int value, int total) {
        int max = Math.max(0, total - LIST_ROWS);
        return Math.max(0, Math.min(max, value));
    }

    private static boolean isOver(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** 鼠标落在列表区内时返回可见行号（0 ~ LIST_ROWS-1），否则 -1。左右列由调用方按 x 区分。 */
    private int rowAt(double mouseX, double mouseY) {
        int relY = (int) mouseY - topPos;
        if (relY < LIST_TOP || relY >= LIST_BOTTOM) return -1;
        int relX = (int) mouseX - leftPos;
        if (relX < LEFT_X || relX >= RIGHT_X + RIGHT_W) return -1;
        return (relY - LIST_TOP) / ROW_H;
    }

    // ══════════════════════════ 数据变更 ══════════════════════════

    private void addToPriority(String id) {
        if (id == null || id.isEmpty() || priorityView.contains(id)) return;
        priorityView.add(id);
        sendUpdate();
    }

    private void sendUpdate() {
        // 清掉最近一次同步，避免服务器回包覆盖本地的乐观更新
        FurnaceOrePrioritySyncPacket.latest = null;
        scrollL = clampScroll(scrollL, priorityView.size());
        NetworkHandler.CHANNEL.sendToServer(
                new FurnaceOrePriorityUpdatePacket(new ArrayList<>(priorityView)));
    }

    /** 把服务器同步数据合并进本地视图（本地乐观更新期间不覆盖）。 */
    private void applyServerSync() {
        FurnaceOrePrioritySyncPacket.SyncData sync = FurnaceOrePrioritySyncPacket.latest;
        if (sync == null) return;
        boolean same = priorityView.equals(sync.priority)
                && availableView.equals(sync.available)
                && reserveIds.equals(sync.reserveIds)
                && reserveAmounts.equals(sync.reserveAmounts);
        if (same) return;

        priorityView = new ArrayList<>(sync.priority);
        availableView = new ArrayList<>(sync.available);
        reserveIds = new ArrayList<>(sync.reserveIds);
        reserveAmounts = new ArrayList<>(sync.reserveAmounts);
        refreshFilter();
        scrollL = clampScroll(scrollL, priorityView.size());
        scrollR = clampScroll(scrollR, filteredAvailable().size());
    }

    // ══════════════════════════ 输入 ══════════════════════════

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int r = rowAt(mouseX, mouseY);
        if (r >= 0) {
            int relX = (int) mouseX - leftPos;
            int step = -(int) Math.signum(delta);
            if (relX >= LEFT_X && relX < RIGHT_X) {
                scrollL = clampScroll(scrollL + step, priorityView.size());
                return true;
            }
            scrollR = clampScroll(scrollR + step, filteredAvailable().size());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int relX = (int) mouseX - leftPos;
        int relY = (int) mouseY - topPos;

        if (button == 0) {
            // 1) 顶部待炼仓矿石：点击直接加入优先列表
            if (relY >= RESERVE_ICON_Y && relY < RESERVE_ICON_Y + 16) {
                int i = (relX - RESERVE_ICON_X) / RESERVE_STEP;
                int off = (relX - RESERVE_ICON_X) % RESERVE_STEP;
                if (i >= 0 && i < Math.min(reserveIds.size(), RESERVE_MAX) && off < 16) {
                    addToPriority(reserveIds.get(i));
                    return true;
                }
            }

            // 2) 清空按钮
            if (isOver(relX, relY, CLEAR_X, CLEAR_Y, CLEAR_W, CLEAR_H)) {
                if (!priorityView.isEmpty()) {
                    priorityView.clear();
                    sendUpdate();
                }
                return true;
            }

            // 3) 列表区
            int r = rowAt(mouseX, mouseY);
            if (r >= 0) {
                int rowY = LIST_TOP + r * ROW_H;
                if (relX >= LEFT_X && relX < LEFT_X + LEFT_W) {
                    int idx = scrollL + r;
                    if (idx < priorityView.size()) {
                        if (isOver(relX, relY, L_UP_X, rowY + 2, BTN_W, BTN_H)) {
                            if (idx > 0) {
                                Collections.swap(priorityView, idx, idx - 1);
                                sendUpdate();
                            }
                            return true;
                        }
                        if (isOver(relX, relY, L_DOWN_X, rowY + 2, BTN_W, BTN_H)) {
                            if (idx < priorityView.size() - 1) {
                                Collections.swap(priorityView, idx, idx + 1);
                                sendUpdate();
                            }
                            return true;
                        }
                        if (isOver(relX, relY, L_DEL_X, rowY + 2, BTN_W, BTN_H)) {
                            priorityView.remove(idx);
                            sendUpdate();
                            return true;
                        }
                        // 行主体：按下即视为拖拽起点（拖动后松手才真正排序）
                        dragIndex = idx;
                        dragging = false;
                        return true;
                    }
                } else if (relX >= RIGHT_X && relX < RIGHT_X + RIGHT_W) {
                    int idx = scrollR + r;
                    List<String> filtered = filteredAvailable();
                    if (idx < filtered.size()) {
                        addToPriority(filtered.get(idx));
                        return true;
                    }
                }
            }

            // 4) 背包只读槽位：点击可熔炼物品加入优先列表，并吞掉点击防止拿起物品
            for (Slot slot : menu.slots) {
                if (mouseX >= leftPos + slot.x && mouseX < leftPos + slot.x + 16
                        && mouseY >= topPos + slot.y && mouseY < topPos + slot.y + 16) {
                    ItemStack s = slot.getItem();
                    if (!s.isEmpty() && isSmeltable(s)) {
                        addToPriority(idOf(s));
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragIndex >= 0) {
            dragging = true;
            dragMouseX = mouseX;
            dragMouseY = mouseY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragIndex >= 0) {
            if (dragging) {
                int target = dropTargetAt(mouseY);
                if (target >= 0 && target != dragIndex && dragIndex < priorityView.size()) {
                    String moved = priorityView.remove(dragIndex);
                    priorityView.add(Math.max(0, Math.min(target, priorityView.size())), moved);
                    sendUpdate();
                }
            }
            dragIndex = -1;
            dragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** 拖拽落点：把鼠标 y 映射为优先列表中的目标下标。 */
    private int dropTargetAt(double mouseY) {
        int n = priorityView.size();
        if (n == 0) return -1;
        int r = ((int) mouseY - topPos - LIST_TOP) / ROW_H;
        if (r < 0) r = 0;
        return Math.min(scrollL + r, n - 1);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_ESCAPE && (searchBox == null || !searchBox.isFocused())) {
            closeToFurnace();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ══════════════════════════ 绘制：底层 ══════════════════════════

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x0 = leftPos;
        int y0 = topPos;
        int x1 = x0 + PANEL_W;
        int y1 = y0 + PANEL_H;

        // 面板底板与边框
        g.fill(x0, y0, x1, y1, C_PANEL);
        g.fill(x0, y0, x1, y0 + 1, C_BORDER);
        g.fill(x0, y1 - 1, x1, y1, C_BORDER);
        g.fill(x0, y0, x0 + 1, y1, C_BORDER);
        g.fill(x1 - 1, y0, x1, y1, C_BORDER);

        // 标题栏 / 待炼仓条分隔线
        g.fill(x0 + 1, y0 + HEADER_H, x1 - 1, y0 + HEADER_H + 1, C_SEP);
        g.fill(x0 + 1, y0 + HEADER_LINE_Y, x1 - 1, y0 + HEADER_LINE_Y + 1, C_SEP);
        g.fill(x0 + 1, y0 + SEP_Y, x1 - 1, y0 + SEP_Y + 1, C_SEP);

        // 两栏列表底板
        g.fill(x0 + LEFT_X, contentTop, x0 + LEFT_X + LEFT_W, contentBottom, C_LIST_BG);
        g.fill(x0 + RIGHT_X, contentTop, x0 + RIGHT_X + RIGHT_W, contentBottom, C_LIST_BG);

        // 悬停行高亮
        int row = rowAt(mouseX, mouseY);
        if (row >= 0) {
            int relX = mouseX - x0;
            int ry = contentTop + row * ROW_H;
            if (relX >= LEFT_X && relX < LEFT_X + LEFT_W) {
                g.fill(x0 + LEFT_X, ry, x0 + LEFT_X + LEFT_W, ry + ROW_H, C_ROW_HOVER);
            } else if (relX >= RIGHT_X && relX < RIGHT_X + RIGHT_W) {
                g.fill(x0 + RIGHT_X, ry, x0 + RIGHT_X + RIGHT_W, ry + ROW_H, C_ROW_HOVER);
            }
        }

        // 拖拽插入位置指示线
        if (dragging) {
            int target = dropTargetAt(mouseY);
            if (target >= 0) {
                int ly = contentTop + (target - scrollL) * ROW_H;
                if (ly >= contentTop && ly <= contentBottom) {
                    g.fill(x0 + LEFT_X, ly - 1, x0 + LEFT_X + LEFT_W, ly + 1, C_GOLD);
                }
            }
        }

        // 玩家背包只读槽位（原版风格）
        for (Slot slot : menu.slots) {
            int sx = x0 + slot.x;
            int sy = y0 + slot.y;
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF0A0A0F);
            g.fill(sx, sy, sx + 16, sy + 16, 0xFF262630);
            g.fill(sx, sy, sx + 16, sy + 1, 0xFF3C3C4C);
            g.fill(sx, sy, sx + 1, sy + 16, 0xFF3C3C4C);
            g.fill(sx + 15, sy + 1, sx + 16, sy + 16, 0xFF101018);
            g.fill(sx + 1, sy + 15, sx + 16, sy + 16, 0xFF101018);
        }
    }

    // ══════════════════════════ 绘制：内容层 ══════════════════════════

    /**
     * 内容层。父类在调用本方法前已把坐标系平移到面板左上角，
     * 因此这里的 x / y 全部使用相对面板的偏移。
     */
    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        int relX = mouseX - leftPos;
        int relY = mouseY - topPos;

        drawHeader(g);
        drawReserveBar(g, relX, relY);
        drawColumnHeaders(g, relX, relY);
        drawPriorityRows(g, relX, relY);
        drawAvailableRows(g, relX, relY);

        drawScrollbar(g, LEFT_SCROLL_X, scrollL, priorityView.size());
        drawScrollbar(g, RIGHT_SCROLL_X, scrollR, filteredAvailable().size());

        drawEmptyStates(g);

        // 物品栏标签
        g.drawString(font, invLabel, (PANEL_W - font.width(invLabel)) / 2, INV_LABEL_Y, C_TEXT_DIM, false);
    }

    private void drawHeader(GuiGraphics g) {
        g.drawString(font, title, (PANEL_W - font.width(title)) / 2, 4, 0xFFFFFFFF, false);
        g.drawString(font, subtitle, (PANEL_W - font.width(subtitle)) / 2, 14, C_TEXT_DIM, false);
    }

    private void drawReserveBar(GuiGraphics g, int relX, int relY) {
        g.drawString(font, reserveHeader, RESERVE_LABEL_X, RESERVE_LABEL_Y, C_GOLD, false);

        if (reserveIds.isEmpty()) {
            g.drawString(font, emptyReserve, RESERVE_LABEL_X + font.width(reserveHeader) + 4,
                    RESERVE_LABEL_Y, C_TEXT_DIM, false);
            return;
        }

        int shown = Math.min(reserveIds.size(), RESERVE_MAX);
        for (int i = 0; i < shown; i++) {
            String id = reserveIds.get(i);
            int ix = RESERVE_ICON_X + i * RESERVE_STEP;
            boolean hover = isOver(relX, relY, ix, RESERVE_ICON_Y, 16, 16);
            if (hover) {
                g.fill(ix - 1, RESERVE_ICON_Y - 1, ix + 17, RESERVE_ICON_Y + 17, 0x60FFFFFF);
            }
            g.renderItem(stackOf(id), ix, RESERVE_ICON_Y);

            String amt = formatAmount(reserveAmounts.get(i));
            g.drawString(font, amt, ix + 16 - font.width(amt), RESERVE_ICON_Y + 9, 0xFFFFE08A, true);

            if (priorityView.contains(id)) {
                g.drawString(font, "✔", ix, RESERVE_ICON_Y - 2, 0xFF6BD66B, false);
            }
        }
        if (reserveIds.size() > shown) {
            g.drawString(font, "+" + (reserveIds.size() - shown),
                    RESERVE_ICON_X + shown * RESERVE_STEP + 1, RESERVE_ICON_Y + 4, C_TEXT_DIM, false);
        }
    }

    private void drawColumnHeaders(GuiGraphics g, int relX, int relY) {
        g.drawString(font, prioHeader, LEFT_X, COL_TITLE_Y, C_GOLD, false);
        g.drawString(font, availHeader, RIGHT_X, COL_TITLE_Y, C_BLUE, false);

        drawButton(g, CLEAR_X, CLEAR_Y, CLEAR_W, CLEAR_H, clearLabel.getString(),
                relX, relY, !priorityView.isEmpty());
    }

    private void drawPriorityRows(GuiGraphics g, int relX, int relY) {
        int idx = scrollL;
        for (int r = 0; r < LIST_ROWS && idx < priorityView.size(); r++, idx++) {
            String id = priorityView.get(idx);
            int ry = LIST_TOP + r * ROW_H;

            // 正在被拖拽的行画成半透明占位
            boolean ghosted = dragging && idx == dragIndex;
            int nameColor = ghosted ? C_TEXT_OFF : C_TEXT;

            g.drawString(font, String.valueOf(idx + 1), L_INDEX_X, ry + 6, C_TEXT_DIM, false);
            g.renderItem(stackOf(id), L_ICON_X, ry + 2);
            g.drawString(font, font.plainSubstrByWidth(displayNameOf(id), L_NAME_W),
                    L_NAME_X, ry + 6, nameColor, false);

            drawButton(g, L_UP_X, ry + 2, BTN_W, BTN_H, "↑", relX, relY, idx > 0);
            drawButton(g, L_DOWN_X, ry + 2, BTN_W, BTN_H, "↓", relX, relY, idx < priorityView.size() - 1);
            drawButton(g, L_DEL_X, ry + 2, BTN_W, BTN_H, "✕", relX, relY, true);
        }
    }

    private void drawAvailableRows(GuiGraphics g, int relX, int relY) {
        List<String> filtered = filteredAvailable();
        int idx = scrollR;
        for (int r = 0; r < LIST_ROWS && idx < filtered.size(); r++, idx++) {
            String id = filtered.get(idx);
            int ry = LIST_TOP + r * ROW_H;
            boolean inReserve = reserveIds.contains(id);

            // 仓内已有存货的矿石：金色名称 + 图标描边，方便优先选择
            g.renderItem(stackOf(id), R_ICON_X, ry + 2);
            if (inReserve) {
                g.fill(R_ICON_X - 1, ry + 1, R_ICON_X + 17, ry + 2, C_GOLD);
                g.fill(R_ICON_X - 1, ry + 18, R_ICON_X + 17, ry + 19, C_GOLD);
                g.fill(R_ICON_X - 1, ry + 2, R_ICON_X, ry + 18, C_GOLD);
                g.fill(R_ICON_X + 16, ry + 2, R_ICON_X + 17, ry + 18, C_GOLD);
            }
            g.drawString(font, font.plainSubstrByWidth(displayNameOf(id), R_NAME_W),
                    R_NAME_X, ry + 6, inReserve ? C_GOLD : C_TEXT, false);

            drawButton(g, R_ADD_X, ry + 2, R_ADD_W, BTN_H, "＋", relX, relY, true);
        }
    }

    /** 自绘按钮：启用态 / 悬停态 / 禁用态。坐标为相对面板偏移。 */
    private void drawButton(GuiGraphics g, int x, int y, int w, int h, String label,
                            int relX, int relY, boolean enabled) {
        boolean hover = enabled && isOver(relX, relY, x, y, w, h);
        g.fill(x, y, x + w, y + h, enabled ? (hover ? C_BTN_HOVER : C_BTN) : 0xFF1B1B24);
        g.fill(x, y, x + w, y + 1, enabled ? 0xFF4A4A60 : 0xFF2A2A34);
        g.fill(x, y + h - 1, x + w, y + h, 0xFF14141C);

        int color = enabled ? (hover ? 0xFFFFFFFF : 0xFFCFCFDA) : C_TEXT_OFF;
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, color, false);
    }

    private void drawScrollbar(GuiGraphics g, int x, int value, int total) {
        int trackH = LIST_BOTTOM - LIST_TOP;
        g.fill(x, LIST_TOP, x + 2, LIST_BOTTOM, C_TRACK);
        int max = Math.max(0, total - LIST_ROWS);
        if (max <= 0) return;
        int sliderH = Math.max(16, trackH * LIST_ROWS / Math.max(1, total));
        int sliderY = LIST_TOP + (trackH - sliderH) * value / max;
        g.fill(x, sliderY, x + 2, sliderY + sliderH, C_SLIDER);
    }

    private void drawEmptyStates(GuiGraphics g) {
        int midY = LIST_TOP + (LIST_BOTTOM - LIST_TOP) / 2 - 4;
        if (priorityView.isEmpty()) {
            drawCentered(g, emptyPriority, LEFT_X, LEFT_X + LEFT_W, midY, C_TEXT_DIM);
        }
        if (filteredAvailable().isEmpty()) {
            drawCentered(g, emptyAvailable, RIGHT_X, RIGHT_X + RIGHT_W, midY, C_TEXT_DIM);
        }
    }

    private void drawCentered(GuiGraphics g, Component text, int x0, int x1, int y, int color) {
        g.drawString(font, text, x0 + (x1 - x0 - font.width(text)) / 2, y, color, false);
    }

    // ══════════════════════════ 绘制：顶层（tooltip / 拖拽残影） ══════════════════════════

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        applyServerSync();
        super.render(g, mouseX, mouseY, partialTick);
        drawDragGhost(g, mouseX, mouseY);
        drawTooltips(g, mouseX, mouseY);
    }

    private void drawDragGhost(GuiGraphics g, int mouseX, int mouseY) {
        if (dragIndex < 0 || !dragging || dragIndex >= priorityView.size()) return;
        String id = priorityView.get(dragIndex);
        int x = mouseX + 8;
        int y = mouseY - 8;

        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 300.0F);
        g.fill(x - 2, y - 2, x + 102, y + 20, 0xE0181826);
        g.fill(x - 2, y - 2, x + 102, y - 1, C_GOLD);
        g.fill(x - 2, y + 19, x + 102, y + 20, C_GOLD);
        g.renderItem(stackOf(id), x + 2, y);
        g.drawString(font, font.plainSubstrByWidth(displayNameOf(id), 76), x + 22, y + 4,
                0xFFEDEDED, false);
        g.pose().popPose();
    }

    private void drawTooltips(GuiGraphics g, int mouseX, int mouseY) {
        int relX = mouseX - leftPos;
        int relY = mouseY - topPos;

        // 待炼仓矿石
        if (relY >= RESERVE_ICON_Y && relY < RESERVE_ICON_Y + 16) {
            int i = (relX - RESERVE_ICON_X) / RESERVE_STEP;
            int off = (relX - RESERVE_ICON_X) % RESERVE_STEP;
            if (i >= 0 && i < Math.min(reserveIds.size(), RESERVE_MAX) && off < 16) {
                String id = reserveIds.get(i);
                List<Component> lines = new ArrayList<>();
                lines.add(Component.literal(displayNameOf(id)).withStyle(s -> s.withColor(0xFFFFFF)));
                lines.add(Component.translatable("gui.infinitestats.furnace.ore_priority.tip_amount",
                        reserveAmounts.get(i)).withStyle(s -> s.withColor(0xFFAAAAAA)));
                g.renderTooltip(font, lines, Optional.<TooltipComponent>empty(), mouseX, mouseY);
                return;
            }
        }

        // 列表行
        int r = rowAt(mouseX, mouseY);
        if (r < 0) return;
        String id = null;
        if (relX >= LEFT_X && relX < LEFT_X + LEFT_W) {
            int idx = scrollL + r;
            if (idx < priorityView.size()) id = priorityView.get(idx);
        } else if (relX >= RIGHT_X && relX < RIGHT_X + RIGHT_W) {
            List<String> filtered = filteredAvailable();
            int idx = scrollR + r;
            if (idx < filtered.size()) id = filtered.get(idx);
        }
        if (id == null) return;

        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(displayNameOf(id)).withStyle(s -> s.withColor(0xFFFFFF)));
        int pIdx = priorityView.indexOf(id);
        if (pIdx >= 0) {
            lines.add(Component.translatable("gui.infinitestats.furnace.ore_priority.tip_priority",
                    pIdx + 1).withStyle(s -> s.withColor(0xFFFFC24E)));
            lines.add(tipDrag.copy().withStyle(s -> s.withColor(0xFF888899)));
        } else if (reserveIds.contains(id)) {
            lines.add(tipReserve.copy().withStyle(s -> s.withColor(0xFFFFC24E)));
        }
        g.renderTooltip(font, lines, Optional.<TooltipComponent>empty(), mouseX, mouseY);
    }
}
