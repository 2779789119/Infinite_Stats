package com.infinitestats.client;

import com.infinitestats.InfiniteStats;
import com.infinitestats.bag.ItemBagActions;
import com.infinitestats.bag.ItemBagProvider;
import com.infinitestats.compat.jei.JeiBridge;
import com.infinitestats.network.NetworkHandler;
import com.infinitestats.util.ItemSearch;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 「无尽背包」界面。
 *
 * <h2>两个来源</h2>
 * <ul>
 *   <li><b>背包</b>：本模组自己的无尽背包（拾取自动收进来的东西）；</li>
 *   <li><b>存储网络</b>：已连接的 RS / AE2 / BD / 汤姆存储 / 背包模组，按玩家设定的优先级读取，
 *       取出时也从对应网络扣 —— 不用另开终端。</li>
 * </ul>
 *
 * <h2>操作</h2>
 * 左键取 1、Shift+左键取 64、Ctrl+左键取该种全部；右侧详情卡上也有三个按钮，
 * 并支持 R / U 直接调 JEI 查配方。
 *
 * <h2>为什么不是 ContainerScreen</h2>
 * 背包没有槽位容量概念（同一种物品只有一条记录、数量是 long），用原版
 * {@code AbstractContainerMenu} 那套「槽位 + 同步」既装不下也不划算。这里走
 * 「请求 → 全量同步 → 本地渲染」，点击时把想取的物品（模板）发给服务端。
 */
public final class ItemBagScreen extends PanelScreen {

    // 尺寸与坐标照精致存储的 GridScreen 摆：
    //   标题 (7,7)、搜索框贴顶部右侧、网格 9 列 × 18px 从 (7,19) 起、滚动条在网格右边
    //   （RS 的 getTopHeight()=19、网格宽 162=9×18、滚动条 x=174 宽 12）
    /*
     * 坐标全部照抄精致存储 GridScreen（v1.12.4）：
     *   imageWidth = 227（主区 193 = imageWidth - 34，右侧 34 是过滤栏）
     *   getTopHeight() = 19，槽位从 (8,19) 起、9 列 × 18px
     *   滚动条 (174, 19, 12, rows*18-2)，getBottomHeight() = 99
     *   贴图切片：顶部 (0,0,193,19)；行 0/中/尾 = v 19/37/55；底部 v=73
     */
    private static final int W = 227;
    private static final int H = 195;

    private static final int SEARCH_X = 81;
    private static final int SEARCH_Y = 7;
    private static final int SEARCH_W = 82;
    private static final int SEARCH_H = 12;

    private static final int COLS = 9;
    private static final int ROWS = 7;
    private static final int CELL = 18;
    private static final int GRID_X = 8;
    private static final int GRID_Y = 19;
    private static final int GRID_W = COLS * CELL;
    private static final int GRID_H = ROWS * CELL;
    private static final int SCROLL_X = 174;
    private static final int SCROLL_W = 12;

    // 右侧竖排按钮列（RS 的 SideButton：GUI 右侧、18×18、间距 1）
    private static final int SIDE_X = 205;
    private static final int SIDE_Y = 24;
    private static final int SIDE_SIZE = 18;
    private static final int SIDE_GAP = 1;

    // 底部（RS 的 getBottomHeight() 是 99，我们只取 16px 空白段，其余自绘成按钮栏）
    private static final int FOOTER_Y = 165;
    private static final int BTN_H = 18;

    private static final net.minecraft.resources.ResourceLocation GRID_TEXTURE =
            new net.minecraft.resources.ResourceLocation(InfiniteStats.MODID, "textures/gui/rs_grid.png");
    private static final net.minecraft.resources.ResourceLocation ICONS_TEXTURE =
            new net.minecraft.resources.ResourceLocation(InfiniteStats.MODID, "textures/gui/rs_icons.png");

    // 原版容器配色（和「合成终端」那种界面同源）：浅灰面板 + 凹陷边框 + 3D 按钮
    private static final int BG = 0xFFC6C6C6;
    private static final int LINE = 0xFF8B8B8B;
    private static final int GOLD = 0xFF404040;
    private static final int TEXT = 0xFF404040;
    private static final int MUTED = 0xFF6B6B6B;
    private static final int BORDER_DARK = 0xFF373737;
    private static final int BORDER_LIGHT = 0xFFFFFFFF;
    private static final int BTN_BG = 0xFFC6C6C6;
    private static final int BTN_BG_HOVER = 0xFFD6D6D6;
    private static final int BTN_BG_PRIMARY = 0xFFA8A8A8;

    /** 搜索词跨界面保留（精致存储的做法：关掉再开还在，不用重打一遍）。 */
    private static String savedQuery = "";

    /** 数量文本缓存：同一数字反复格式化太浪费（每帧要画 63 个格子）。 */
    private static final Map<Long, String> COUNT_TEXT = new HashMap<>();

    /** 搜索历史（精致存储的做法：Enter 记一条，上下键翻）。 */
    private static final List<String> QUERY_HISTORY = new ArrayList<>();
    private static final int QUERY_HISTORY_MAX = 32;
    private static int queryHistoryIndex = -1;

    private final Screen parent;

    private EditBox search;
    private byte source = ItemBagProvider.SOURCE_BAG;

    private int scrollRow;
    private int sortMode = ItemBagActions.SORT_NAME_ASC;
    private boolean bagRequested;
    /** 上一次已经发给服务端的网络查询（搜索词 / 排序），用于去重与 debounce。 */
    private String lastRequestedQuery = null;
    private int lastRequestedSort = -1;
    /** 上一次已经同步给 JEI 物品列表的搜索词。 */
    private String lastJeiQuery = null;
    private long lastQueryEditAt;
    /** 上一次发出「取出」请求的时间（连点节流）。 */
    private long lastWithdrawAt;
    private boolean draggingScroll;

    private int lastMouseX;
    private int lastMouseY;

    private List<ItemStack> filtered = List.of();
    private long builtVersion = -1;
    private String builtQuery = "";
    private int builtSort = -1;
    private byte builtSource = -1;

    /** 合计件数缓存（按来源版本号失效）。 */
    private long cachedCount;
    private long cachedCountVersion = -1;
    private byte cachedCountSource = -1;

    public ItemBagScreen() {
        this(null);
    }

    public ItemBagScreen(Screen parent) {
        super(Component.translatable("screen.infinitestats.bag.title"));
        this.parent = parent;
    }

    @Override
    protected int panelW() {
        return W;
    }

    @Override
    protected int panelH() {
        return H;
    }

    // ===== 给 JEI 报位置用（见 compat/jei/BagJeiGuiHandler）=====

    /** 面板在屏幕上的实际左边界（已含缩放 / 拖动）。 */
    public int bagGuiLeft() {
        layout();
        return panelOriginX();
    }

    public int bagGuiTop() {
        layout();
        return panelOriginY();
    }

    public int bagGuiWidth() {
        layout();
        return Math.round(panelW() * panelScale());
    }

    public int bagGuiHeight() {
        layout();
        return Math.round(panelH() * panelScale());
    }

    @Override
    protected void init() {
        super.init();
        scrollRow = 0;

        search = new EditBox(font, SEARCH_X + 4, SEARCH_Y, SEARCH_W - 8, 12,
                Component.translatable("screen.infinitestats.bag.search"));
        search.setBordered(false);
        search.setMaxLength(64);
        // 自绘提示文字（EditBox 自带的 hint 在深色底上几乎看不见，而且只在未聚焦时画）
        search.setHint(Component.empty());
        search.setTextColor(0xFF000000);
        search.setTextColorUneditable(0xFF505050);
        search.setValue(savedQuery);
        search.setResponder(value -> {
            savedQuery = value;
            builtVersion = -1;
            lastQueryEditAt = System.currentTimeMillis();
        });
        addRenderableWidget(search);

        if (!bagRequested) {
            bagRequested = true;
            NetworkHandler.CHANNEL.sendToServer(NetworkHandler.ItemBagActionPacket.request());
        }
        // 网络标签的列表是「服务端按搜索词过滤 + 排序」后发来的，重开界面 / 切换回去都要重新问一次
        if (source == ItemBagProvider.SOURCE_NETWORK) {
            requestNetworkNow();
        }
    }

    // ======================== 过滤 / 排序 ========================

    private void rebuildIfNeeded() {
        String query = search == null ? "" : search.getValue().trim();
        if (builtVersion == ClientItemBag.version(source)
                && builtSource == source
                && builtSort == sortMode
                && builtQuery.equals(query)) {
            return;
        }
        builtVersion = ClientItemBag.version(source);
        builtSource = source;
        builtSort = sortMode;
        builtQuery = query;

        List<ItemStack> next = new ArrayList<>();
        boolean network = source == ItemBagProvider.SOURCE_NETWORK;
        for (ItemStack stack : ClientItemBag.items(source)) {
            if (stack.isEmpty()) continue;
            // 网络来源的列表已经是服务端按同一套规则过滤好的；这里再筛一次只是为了
            // 「刚敲下搜索词、服务端还没回包」这一小段时间里也有即时反馈。
            if (!query.isEmpty() && !ItemSearch.matches(stack, query)) continue;
            next.add(stack);
        }
        // 服务端已经按当前 sortMode 排好序，客户端不再重排（避免两边顺序不一致）
        if (!network) sortLocal(next, query);
        filtered = next;
        scrollRow = Mth.clamp(scrollRow, 0, maxScrollRow());
    }

    private void sortLocal(List<ItemStack> items, String query) {
        Comparator<ItemStack> byName = Comparator.comparing(
                (ItemStack s) -> s.getHoverName().getString(), String.CASE_INSENSITIVE_ORDER);
        switch (sortMode) {
            case ItemBagActions.SORT_NAME_DESC -> items.sort(byName.reversed());
            case ItemBagActions.SORT_COUNT_DESC -> items.sort(
                    Comparator.comparingInt(ItemStack::getCount).reversed().thenComparing(byName));
            case ItemBagActions.SORT_MOD -> items.sort(Comparator
                    .comparing((ItemStack s) -> ItemSearch.namespaceOf(s))
                    .thenComparing(byName));
            default -> items.sort(byName);
        }
    }

    private int maxScrollRow() {
        return Math.max(0, (filtered.size() + COLS - 1) / COLS - ROWS);
    }

    // ======================== 命中 ========================

    private static boolean inside(int x, int y, int w, int h, int mx, int my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private ItemStack stackAt(int mx, int my) {
        if (!inside(GRID_X, GRID_Y, GRID_W, GRID_H, mx, my)) return ItemStack.EMPTY;
        int col = (mx - GRID_X) / CELL;
        int row = (my - GRID_Y) / CELL;
        int index = (scrollRow + row) * COLS + col;
        return index >= 0 && index < filtered.size() ? filtered.get(index) : ItemStack.EMPTY;
    }

    /**
     * 右侧竖排按钮（RS 的 SideButton 位置）：0 = 来源切换（背包 / 存储网络），1 = 排序。
     * 其余操作（全部存入 / 关闭）放底部一行。
     */
    private int[] sideButton(int index) {
        return new int[]{SIDE_X, SIDE_Y + index * (SIDE_SIZE + SIDE_GAP), SIDE_SIZE, SIDE_SIZE};
    }

    private int[] depositButton() {
        return sideButton(2);
    }

    private int[] doneButton() {
        return new int[]{W - 6 - 60, FOOTER_Y, 60, BTN_H};
    }

    private Component sideLabel(int index) {
        return switch (index) {
            case 0 -> Component.translatable(source == ItemBagProvider.SOURCE_NETWORK
                    ? "screen.infinitestats.bag.side_network"
                    : "screen.infinitestats.bag.side_bag");
            case 1 -> Component.translatable("screen.infinitestats.bag.side_sort");
            default -> Component.translatable("screen.infinitestats.bag.side_deposit");
        };
    }

    private Component sideTooltip(int index) {
        return switch (index) {
            case 1 -> Component.translatable("screen.infinitestats.bag.side_tip_sort",
                    Component.translatable(switch (sortMode) {
                        case ItemBagActions.SORT_NAME_DESC -> "screen.infinitestats.bag.sort_name_desc";
                        case ItemBagActions.SORT_COUNT_DESC -> "screen.infinitestats.bag.sort_count";
                        case ItemBagActions.SORT_MOD -> "screen.infinitestats.bag.sort_mod";
                        default -> "screen.infinitestats.bag.sort_name";
                    }).getString());
            case 2 -> Component.translatable("screen.infinitestats.bag.side_tip_deposit");
            default -> Component.translatable("screen.infinitestats.bag.side_tip_source");
        };
    }

    private void toggleSource() {
        source = source == ItemBagProvider.SOURCE_BAG
                ? ItemBagProvider.SOURCE_NETWORK
                : ItemBagProvider.SOURCE_BAG;
        scrollRow = 0;
        builtVersion = -1;
        if (source == ItemBagProvider.SOURCE_NETWORK) requestNetworkNow();
    }

    private void cycleSort() {
        sortMode = (sortMode + 1) % 4;
        builtVersion = -1;
        if (source == ItemBagProvider.SOURCE_NETWORK) requestNetworkNow();
    }

    /** 网络标签的查询：搜索词 / 排序变化后 200ms 才发（打字期间不刷服务端）。 */
    private void requestNetworkNow() {
        String query = search == null ? "" : search.getValue().trim();
        lastRequestedQuery = query;
        lastRequestedSort = sortMode;
        NetworkHandler.CHANNEL.sendToServer(
                NetworkHandler.ItemBagActionPacket.requestNetwork(query, sortMode));
    }

    @Override
    public void tick() {
        super.tick();
        if (search == null) return;
        String query = search.getValue().trim();
        long now = System.currentTimeMillis();

        // 搜索词顺带过滤 JEI 的物品列表（精致存储 / 超越维度都这么做）。
        // 防抖 200ms：否则每敲一个字母 JEI 都要重算整张物品表。
        if (!query.equals(lastJeiQuery) && now - lastQueryEditAt >= 200) {
            lastJeiQuery = query;
            JeiBridge.syncFilter(query);
        }

        if (source != ItemBagProvider.SOURCE_NETWORK) return;
        boolean changed = !query.equals(lastRequestedQuery) || sortMode != lastRequestedSort;
        if (!changed) return;
        if (now - lastQueryEditAt < 200) return;
        requestNetworkNow();
    }

    // ======================== 输入 ========================

    @Override
    protected boolean onPanelClick(int mouseX, int mouseY, int button) {
        if (button != 0) return false;

        // 搜索框：交给 EditBox 自己处理焦点
        if (inside(SEARCH_X, SEARCH_Y, SEARCH_W, SEARCH_H, mouseX, mouseY)) return false;
        if (search != null) search.setFocused(false);

        // 右侧竖排按钮：来源切换 / 排序 / 全部存入
        for (int i = 0; i < 3; i++) {
            int[] b = sideButton(i);
            if (!inside(b[0], b[1], b[2], b[3], mouseX, mouseY)) continue;
            if (i == 0) {
                toggleSource();
            } else if (i == 1) {
                cycleSort();
            } else {
                NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.ItemBagActionPacket(
                        NetworkHandler.ItemBagActionPacket.DEPOSIT_ALL, ItemStack.EMPTY, 0));
            }
            return true;
        }

        int[] done = doneButton();
        if (inside(done[0], done[1], done[2], done[3], mouseX, mouseY)) {
            onClose();
            return true;
        }

        // 滚动条
        if (inside(SCROLL_X, GRID_Y, SCROLL_W, GRID_H, mouseX, mouseY) && maxScrollRow() > 0) {
            draggingScroll = true;
            scrollRow = EditorUi.scrollFromMouseY(mouseY, GRID_Y, GRID_H, maxScrollRow(), ROWS);
            return true;
        }

        // 网格格子：左键 1 / Shift 64 / Ctrl 全部
        if (inside(GRID_X, GRID_Y, GRID_W, GRID_H, mouseX, mouseY)) {
            ItemStack hovered = stackAt(mouseX, mouseY);
            if (!hovered.isEmpty()) {
                int amount = Screen.hasControlDown() ? Integer.MAX_VALUE
                        : (Screen.hasShiftDown() ? 64 : 1);
                sendWithdraw(hovered, amount);
            }
            return true;
        }
        return false;
    }

    private void sendWithdraw(ItemStack stack, int amount) {
        // 连点节流：一次点击就是一个包 + 一次全量回推，手抖连点会把服务端刷爆
        long now = System.currentTimeMillis();
        if (now - lastWithdrawAt < 60) return;
        lastWithdrawAt = now;

        boolean network = source == ItemBagProvider.SOURCE_NETWORK;
        int action = network
                ? NetworkHandler.ItemBagActionPacket.WITHDRAW_NETWORK
                : NetworkHandler.ItemBagActionPacket.WITHDRAW;
        NetworkHandler.CHANNEL.sendToServer(
                new NetworkHandler.ItemBagActionPacket(action, stack, amount));
        // 取出后服务端那边缓存已失效，带上当前搜索词重新要一次，界面数量才会更新
        if (network) requestNetworkNow();
    }

    @Override
    protected boolean onPanelDrag(int mouseX, int mouseY, int button, int dragX, int dragY) {
        if (!draggingScroll) return false;
        scrollRow = EditorUi.scrollFromMouseY(mouseY, GRID_Y, GRID_H, maxScrollRow(), ROWS);
        return true;
    }

    @Override
    protected boolean onPanelRelease(int mouseX, int mouseY, int button) {
        if (draggingScroll) {
            draggingScroll = false;
            return true;
        }
        return false;
    }

    @Override
    protected boolean onPanelScroll(int mouseX, int mouseY, double delta) {
        if (delta == 0) return false;
        int maxScroll = maxScrollRow();
        if (maxScroll <= 0) return false;
        if (!inside(GRID_X, GRID_Y, GRID_W, GRID_H, mouseX, mouseY)
                && !inside(SCROLL_X, GRID_Y, SCROLL_W, GRID_H, mouseX, mouseY)) {
            return false;
        }
        scrollRow = Mth.clamp(scrollRow - (int) Math.signum(delta), 0, maxScroll);
        return true;
    }

    /**
     * 键盘：
     * <ul>
     *   <li>搜索框聚焦时：Enter 收焦点并存历史、Esc 先收焦点、上下键翻历史（精致存储的做法）；</li>
     *   <li>未聚焦时：悬停物品 R = 看怎么做出它，U = 看它能做什么（JEI）。</li>
     * </ul>
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (search != null && search.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                rememberQuery();
                search.setFocused(false);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                // 第一次 Esc 只是退出搜索，再按一次才关界面
                search.setFocused(false);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_UP) {
                recallQuery(-1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DOWN) {
                recallQuery(1);
                return true;
            }
        } else if (keyCode == GLFW.GLFW_KEY_R || keyCode == GLFW.GLFW_KEY_U) {
            rebuildIfNeeded();
            ItemStack hovered = stackAt(lastMouseX, lastMouseY);
            if (!hovered.isEmpty() && JeiBridge.available()) {
                JeiBridge.show(hovered, keyCode == GLFW.GLFW_KEY_U);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ======================== 搜索历史 ========================

    private void rememberQuery() {
        String query = search == null ? "" : search.getValue().trim();
        if (query.isEmpty()) return;
        QUERY_HISTORY.remove(query);
        QUERY_HISTORY.add(0, query);
        while (QUERY_HISTORY.size() > QUERY_HISTORY_MAX) {
            QUERY_HISTORY.remove(QUERY_HISTORY.size() - 1);
        }
        queryHistoryIndex = -1;
    }

    private void recallQuery(int delta) {
        if (QUERY_HISTORY.isEmpty() || search == null) return;
        if (queryHistoryIndex < 0) {
            queryHistoryIndex = delta < 0 ? 0 : -1;
        } else {
            queryHistoryIndex = Mth.clamp(queryHistoryIndex + delta, 0, QUERY_HISTORY.size() - 1);
        }
        String value = queryHistoryIndex < 0 ? "" : QUERY_HISTORY.get(queryHistoryIndex);
        search.setValue(value);
        search.setCursorPosition(value.length());
    }

    // ======================== 渲染 ========================

    @Override
    protected void renderPanel(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        rebuildIfNeeded();
        boolean network = source == ItemBagProvider.SOURCE_NETWORK;

        // ===== 背景：照 RS 的 grid.png 切片 =====
        // 顶部（标题 + 搜索框凹槽）
        g.blit(GRID_TEXTURE, 0, 0, 0, 0, 193, 19);
        // 每一行：首行 v=19，中间行 v=37，末行 v=55（RS 的 yTextureStart 规则）
        for (int i = 0; i < ROWS; i++) {
            int v = 19;
            if (i > 0) v = (i == ROWS - 1) ? 55 : 37;
            g.blit(GRID_TEXTURE, 0, 19 + i * 18, 0, v, 193, 18);
        }
        // 底部空白段（RS 的 getTopHeight() + 54，我们只取 16px 高）
        g.blit(GRID_TEXTURE, 0, 145, 0, 73, 193, 16);
        // 右侧 34px 过滤栏：不画贴图，留给侧边按钮
        g.fill(193, 0, W, 161, BG);
        g.fill(193, 0, 194, 161, 0xFF555555);
        // 自绘底栏
        g.fill(0, 161, W, H, BG);
        g.fill(0, 161, W, 162, BORDER_LIGHT);
        g.fill(0, H - 1, W, H, 0xFF555555);
        g.fill(W - 1, 161, W, H, 0xFF555555);

        // 标题（RS 的 (7,7)）
        g.drawString(font, title, 7, 7, 0xFF404040, false);

        // 搜索框提示（凹槽本身在贴图里）
        if (search != null && search.getValue().isEmpty()) {
            g.drawString(font, font.plainSubstrByWidth(
                            Component.translatable("screen.infinitestats.bag.search_hint").getString(),
                            SEARCH_W - 10),
                    SEARCH_X + 3, SEARCH_Y + 2, 0xFF4A4A4A, false);
        }

        // ===== 物品（16×16 画在槽位内，槽位底纹由贴图提供） =====
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int x = GRID_X + col * CELL;
                int y = GRID_Y + row * CELL;
                int index = (scrollRow + row) * COLS + col;
                ItemStack stack = index < filtered.size() ? filtered.get(index) : ItemStack.EMPTY;
                boolean hover = !stack.isEmpty()
                        && inside(x, y, CELL, CELL, mouseX, mouseY);
                if (stack.isEmpty()) continue;
                g.renderItem(stack, x, y);
                if (stack.getCount() > 1) {
                    String count = formatCount(stack.getCount());
                    g.drawString(font, count, x + 16 - font.width(count), y + 9,
                            0xFFFFFFFF, true);
                }
                if (hover) {
                    // RS 的悬停：z=300 + 半透明蓝铺在 16×16 上（-2130706433 = 0x810000FF）
                    g.pose().pushPose();
                    g.pose().translate(0, 0, 300);
                    g.fill(x, y, x + 16, y + 16, 0x810000FF);
                    g.pose().popPose();
                }
            }
        }

        if (filtered.isEmpty()) {
            Component empty = !ClientItemBag.loaded(source)
                    ? Component.translatable("screen.infinitestats.bag.loading")
                    : (ClientItemBag.items(source).isEmpty()
                            ? Component.translatable(network
                                    ? "screen.infinitestats.bag.network_empty"
                                    : "screen.infinitestats.bag.empty")
                            : Component.translatable("screen.infinitestats.bag.no_match"));
            g.drawCenteredString(font, empty, GRID_X + GRID_W / 2, GRID_Y + GRID_H / 2 - 4, MUTED);
        }

        if (maxScrollRow() > 0) {
            drawScrollbar(g);
        }

        // 右侧竖排按钮（RS 的 SideButton：icons.png 的 (238,16) 18×18 + 图标）
        for (int i = 0; i < 3; i++) {
            int[] b = sideButton(i);
            boolean hover = inside(b, mouseX, mouseY);
            g.blit(ICONS_TEXTURE, b[0], b[1], 238, hover ? 35 : 16, SIDE_SIZE, SIDE_SIZE);
            g.drawCenteredString(font, sideLabel(i), b[0] + SIDE_SIZE / 2, b[1] + 5, 0xFF404040);
            if (hover) {
                queueTooltip(g, mouseX, mouseY, List.of(sideTooltip(i).getString()), EditorUi.INFO);
            }
        }

        // 底部：完成（右下） + 统计一行
        drawPill(g, doneButton(), Component.translatable("gui.done"),
                false, inside(doneButton(), mouseX, mouseY));
        int totalKinds = ClientItemBag.items(source).size();
        long totalItems = countItems();
        Component stats = network && ClientItemBag.networkTotalKinds() > totalKinds
                ? Component.translatable("screen.infinitestats.bag.stats_truncated",
                        filtered.size(), ClientItemBag.networkTotalKinds(), totalItems, totalKinds)
                : Component.translatable("screen.infinitestats.bag.stats",
                        filtered.size(), totalKinds, totalItems);
        g.drawString(font, font.plainSubstrByWidth(stats.getString(), 150),
                7, FOOTER_Y + 15, MUTED, false);

        // 悬停提示代替详情卡（RS / AE2 都是这么做的：格子里只留图标和数量）
        ItemStack hover = stackAt(mouseX, mouseY);
        if (!hover.isEmpty()) {
            List<String> lines = new ArrayList<>();
            lines.add(hover.getHoverName().getString());
            lines.add(ItemSearch.modNameOf(hover));
            lines.add(Component.translatable("screen.infinitestats.bag.entry_count",
                    formatCount(hover.getCount())).getString());
            lines.add(Component.translatable("screen.infinitestats.bag.tip_click").getString());
            if (JeiBridge.available()) {
                lines.add(Component.translatable("screen.infinitestats.bag.jei_hint").getString());
            }
            queueTooltip(g, mouseX, mouseY, lines, EditorUi.INFO);
        }
    }

    /** 合计件数：按来源版本号缓存（否则每帧都要遍历整张表）。 */
    private long countItems() {
        long version = ClientItemBag.version(source);
        if (cachedCountVersion == version && cachedCountSource == source) return cachedCount;
        long total = 0;
        for (ItemStack stack : ClientItemBag.items(source)) {
            total += stack.getCount();
        }
        cachedCount = total;
        cachedCountVersion = version;
        cachedCountSource = source;
        return total;
    }

    /** 滚动条滑块直接取 RS 的 icons.png（u=232, v=0, 12×15），轨道在 grid.png 的行贴图里。 */
    private void drawScrollbar(GuiGraphics g) {
        int maxScroll = maxScrollRow();
        int trackH = GRID_H - 2;
        int thumbH = 15;
        int thumbY = GRID_Y + (maxScroll <= 0 ? 0
                : (int) ((float) scrollRow / maxScroll * (trackH - thumbH)));
        g.blit(ICONS_TEXTURE, SCROLL_X, thumbY, 232, 0, 12, 15);
    }

    /**
     * 原版按钮：默认「凸起」（左上白高光、右下深灰阴影），选中 / 按下时反过来变成凹陷。
     * 和原版容器界面里的按钮完全同构，所以放在浅灰面板上不会有违和感。
     */
    private void drawPill(GuiGraphics g, int[] rect, Component label, boolean active, boolean hover) {
        int x = rect[0], y = rect[1], w = rect[2], h = rect[3];
        int face = active ? BTN_BG_PRIMARY : (hover ? BTN_BG_HOVER : BTN_BG);
        g.fill(x, y, x + w, y + h, face);
        if (active) {
            g.fill(x, y, x + w, y + 1, BORDER_DARK);
            g.fill(x, y, x + 1, y + h, BORDER_DARK);
            g.fill(x, y + h - 1, x + w, y + h, BORDER_LIGHT);
            g.fill(x + w - 1, y, x + w, y + h, BORDER_LIGHT);
        } else {
            g.fill(x, y, x + w, y + 1, BORDER_LIGHT);
            g.fill(x, y, x + 1, y + h, BORDER_LIGHT);
            g.fill(x, y + h - 1, x + w, y + h, BORDER_DARK);
            g.fill(x + w - 1, y, x + w, y + h, BORDER_DARK);
        }
        g.drawCenteredString(font, label, x + w / 2, y + (h - font.lineHeight) / 2 + 1,
                active ? 0xFFFFFFFF : TEXT);
    }

    private static boolean inside(int[] rect, int mx, int my) {
        return inside(rect[0], rect[1], rect[2], rect[3], mx, my);
    }

    /**
     * 大数量用 K / M / B 后缀，避免文字把格子挤爆。
     * <p>
     * 结果缓存：一帧要画 63 个格子，{@code String.format} 每次都分配一堆临时对象，
     * 长列表滚动时这部分是实打实的帧时间（汤姆存储也是缓存数量文本的做法）。
     */
    private static String formatCount(long count) {
        if (count < 10_000L) return Long.toString(count);
        String cached = COUNT_TEXT.get(count);
        if (cached != null) return cached;

        String text;
        if (count >= 1_000_000_000L) {
            text = String.format(Locale.ROOT, "%.1fB", count / 1_000_000_000.0D);
        } else if (count >= 1_000_000L) {
            text = String.format(Locale.ROOT, "%.1fM", count / 1_000_000.0D);
        } else {
            text = String.format(Locale.ROOT, "%.1fK", count / 1_000.0D);
        }
        if (COUNT_TEXT.size() > 512) COUNT_TEXT.clear();
        COUNT_TEXT.put(count, text);
        return text;
    }

    /**
     * 关界面时把 JEI 的物品列表过滤清掉。
     * <p>
     * 不清的话玩家搜过的词会一直留在 JEI 里 —— 下次打开别的容器界面，物品列表还是只剩
     * 上次搜出来的那几样，看着像 JEI 坏了。{@link #removed()} 也会调一次（双保险，幂等）。
     */
    @Override
    public void removed() {
        super.removed();
        lastJeiQuery = null;
        JeiBridge.syncFilter("");
    }

    @Override
    public void onClose() {
        JeiBridge.syncFilter("");
        if (parent != null && minecraft != null) {
            minecraft.setScreen(parent);
            return;
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
