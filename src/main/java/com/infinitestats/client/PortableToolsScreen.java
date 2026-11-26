package com.infinitestats.client;

import com.infinitestats.network.NetworkHandler;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import com.infinitestats.stats.StatType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * 随身工具枢纽 —— 把所有随身站点收进同一个面板，属性面板只保留一个入口。
 * <p>
 * <b>左键 / 右键两种操作：</b>
 * <ul>
 *   <li><b>左键</b>：已解锁 → 打开对应界面；未解锁 → 直接花点数解锁（等价于属性面板里点「+」），
 *       不用再跑去属性面板找那条属性；</li>
 *   <li><b>右键</b>：关闭该工具并退还投入的点数（等价于属性面板里点「-」）。</li>
 * </ul>
 * 未解锁的按钮文字呈灰色，鼠标悬停会说明「左键花 1 点解锁」；解锁后可点，点击即向服务端请求打开对应界面
 * （服务端仍会再校验一次开关，改造过的客户端无法绕过）。解锁 / 关闭都复用属性面板同一个
 * {@link NetworkHandler.ModifyStatPacket}，点数校验与结算全在服务端。
 */
public final class PortableToolsScreen extends PanelScreen {

    private static final int GUI_WIDTH = 440;
    private static final int GUI_HEIGHT = 376;

    // 工具网格布局（滚动时按行偏移）
    private static final int GRID_MARGIN = 16;
    private static final int GRID_GAP = 8;
    private static final int BTN_H = 24;
    private static final int ROW_GAP = 6;
    private static final int GRID_TOP = 62;
    private static final int GRID_BOTTOM = GUI_HEIGHT - 50;
    private static final int ROW_STRIDE = BTN_H + ROW_GAP;
    private static final int VISIBLE_ROWS = Math.max(1, (GRID_BOTTOM - GRID_TOP) / ROW_STRIDE);
    private static final int SCROLL_X = GUI_WIDTH - 8 - EditorUi.SCROLL_W;
    private static final int BG_PANEL = 0xE81A1A2E;
    private static final int BORDER_COLOR = 0x403B82F6;
    private static final int TEXT_TITLE = 0xFFFFD166;
    private static final int TEXT_SUBTITLE = 0xFF94A3B8;
    private static final int TEXT_HINT = 0xFF64748B;

    /**
     * 工具清单：顺序即界面顺序（两列、自上而下逐行填充）。
     * 前五个沿用各自既有的打开数据包，其余统一走 {@link NetworkHandler.PortableToolOpenPacket}。
     */
    private static final List<String> TOOL_IDS = List.of(
            "item_bag",
            "portable_crafting",
            "portable_furnace",
            "portable_anvil",
            "portable_smithing",
            "portable_ender_chest",
            "portable_enchanting",
            "portable_stonecutter",
            "portable_loom",
            "portable_cartography",
            "portable_grindstone",
            "portable_brewing");


    @Override
    protected int panelW() {
        return GUI_WIDTH;
    }

    @Override
    protected int panelH() {
        return GUI_HEIGHT;
    }

    private PlayerStats stats;
    /** 当前可见的按钮（只创建可见行，滚出视野的不存在）。 */
    private final List<Button> toolButtons = new ArrayList<>();
    /** 与 {@link #toolButtons} 一一对应的工具 id，右键关闭时按位置命中。 */
    private final List<String> visibleToolIds = new ArrayList<>();

    /** 当前滚到第几行（工具多于可见行数时用）。 */
    private int toolScrollRow;
    private int toolMaxScroll;

    public PortableToolsScreen() {
        super(Component.translatable("gui.infinitestats.tools.title"));
    }

    @Override
    protected void init() {
        super.init();
        toolButtons.clear();
        visibleToolIds.clear();
        stats = getClientStats();

        int btnW = (GUI_WIDTH - GRID_MARGIN * 2 - GRID_GAP) / 2;
        int totalRows = (TOOL_IDS.size() + 1) / 2;
        toolMaxScroll = Math.max(0, totalRows - VISIBLE_ROWS);
        toolScrollRow = Mth.clamp(toolScrollRow, 0, toolMaxScroll);

        for (int i = 0; i < TOOL_IDS.size(); i++) {
            int row = i / 2;
            int visibleRow = row - toolScrollRow;
            if (visibleRow < 0 || visibleRow >= VISIBLE_ROWS) continue;

            String id = TOOL_IDS.get(i);
            int col = i % 2;
            int x = leftPos + GRID_MARGIN + col * (btnW + GRID_GAP);
            int y = topPos + GRID_TOP + visibleRow * ROW_STRIDE;

            boolean unlocked = isUnlocked(id);
            Component name = Component.translatable("stat.infinitestats." + id);
            // 未解锁用灰字标明状态（按钮仍可点：左键即解锁）
            Component label = unlocked ? name : name.copy().withStyle(ChatFormatting.DARK_GRAY);
            Component tip = unlocked
                    ? Component.translatable("gui.infinitestats.tools.tip_unlocked")
                    : Component.translatable("gui.infinitestats.tools.tip_locked", name);

            Button btn = Button.builder(label, b -> onLeftClick(id))
                    .bounds(x, y, btnW, BTN_H)
                    .tooltip(Tooltip.create(tip))
                    .build();
            addRenderableWidget(btn);
            toolButtons.add(btn);
            visibleToolIds.add(id);
        }

        int footerY = topPos + GUI_HEIGHT - 32;
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.infinitestats.tools.unlock_all"),
                        b -> unlockAll())
                .bounds(leftPos + GRID_MARGIN, footerY, 84, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.infinitestats.tools.lock_all"),
                        b -> lockAll())
                .bounds(leftPos + GRID_MARGIN + 88, footerY, 84, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.infinitestats.tools.back"),
                        b -> backToStats())
                .bounds(leftPos + GUI_WIDTH - GRID_MARGIN - 120, footerY, 120, 20)
                .build());
    }

    /**
     * 一键解锁全部随身工具：点数够就一直开到底，不够就停在买不起的那一个，
     * 已经解锁的跳过（不重复扣点）。
     */
    private void unlockAll() {
        if (stats == null) return;
        int unlocked = 0;
        for (String id : TOOL_IDS) {
            if (isUnlocked(id)) continue;
            StatType stat = StatType.fromId(id);
            if (stat == null) continue;
            // 点数投满了却仍不可用 → 是「功能开关」把整条关掉了，跳过
            if (stats.getStatLevel(stat) >= stat.getMaxLevel()) continue;

            long needed = Math.max(1, stat.getMaxLevel() - stats.getStatLevel(stat));
            if (stats.getAvailablePoints() < needed) continue;
            if (!stats.addPoints(stat, needed)) continue;

            NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.ModifyStatPacket(id, needed));
            unlocked++;
        }
        showMessage(unlocked > 0
                ? Component.translatable("gui.infinitestats.tools.unlocked_n", unlocked)
                : Component.translatable("message.infinitestats.not_enough_points"));
        rebuildWidgets();
    }

    /** 一键关闭全部随身工具：把每条已经投进去的点数原样退回来。 */
    private void lockAll() {
        if (stats == null) return;
        int locked = 0;
        for (String id : TOOL_IDS) {
            StatType stat = StatType.fromId(id);
            if (stat == null) continue;
            long level = stats.getStatLevel(stat);
            if (level <= 0) continue;
            if (!stats.removePoints(stat, level)) continue;

            NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.ModifyStatPacket(id, -level));
            locked++;
        }
        if (locked > 0) {
            showMessage(Component.translatable("gui.infinitestats.tools.locked_n", locked));
        }
        rebuildWidgets();
    }

    /** 左键：已解锁就打开界面，未解锁就花点数解锁。 */
    private void onLeftClick(String id) {
        if (isUnlocked(id)) {
            openTool(id);
        } else {
            activateTool(id);
        }
    }

    @Override
    protected boolean onPanelClick(int mouseX, int mouseY, int button) {
        // 右键 = 关闭该工具（退还点数）；未解锁的按钮右键无操作，直接吞掉
        if (button == 1) {
            for (int i = 0; i < toolButtons.size(); i++) {
                if (toolButtons.get(i).isMouseOver(mouseX, mouseY)) {
                    deactivateTool(visibleToolIds.get(i));
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected boolean onPanelScroll(int mouseX, int mouseY, double delta) {
        if (toolMaxScroll <= 0 || delta == 0) return false;
        toolScrollRow = Mth.clamp(toolScrollRow - (int) Math.signum(delta), 0, toolMaxScroll);
        rebuildWidgets();
        return true;
    }

    /**
     * 解锁某个随身工具：按开关型属性一次到位（1 点），与属性面板里点「+」完全等价 ——
     * 本地先做一次校验（避免发无效请求），成功才发包，服务端会再结算一次。
     */
    private void activateTool(String id) {
        if (stats == null) return;
        StatType stat = StatType.fromId(id);
        if (stat == null) return;

        // 点数已经投过却仍不可用 → 多半是「功能开关」把整条关掉了，这里不给解锁
        if (stats.getStatLevel(stat) >= stat.getMaxLevel()) {
            showMessage(Component.translatable("message.infinitestats.tools_feature_disabled"));
            return;
        }

        long needed = stat.getMaxLevel() - stats.getStatLevel(stat);
        if (needed <= 0) needed = 1;
        if (stats.getAvailablePoints() < needed) {
            showMessage(Component.translatable("message.infinitestats.not_enough_points"));
            return;
        }
        if (!stats.addPoints(stat, needed)) {
            showMessage(Component.translatable("message.infinitestats.not_enough_points"));
            return;
        }
        NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.ModifyStatPacket(id, needed));
        rebuildWidgets();
    }

    /** 关闭某个随身工具：退还投入的点数（等价于属性面板里点「-」）。 */
    private void deactivateTool(String id) {
        if (stats == null) return;
        StatType stat = StatType.fromId(id);
        if (stat == null) return;
        if (stats.getStatLevel(stat) <= 0) return;
        if (!stats.removePoints(stat, 1)) return;
        NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.ModifyStatPacket(id, -1));
        rebuildWidgets();
    }

    private void showMessage(Component message) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.displayClientMessage(message, true);
        }
    }

    /** 点击某个已解锁的工具：按 id 发送对应的打开数据包。 */
    private void openTool(String id) {
        switch (id) {
            case "item_bag" -> {
                // 背包内容本来就只从服务端同步，界面纯客户端；打开时自己会请求一次全量
                if (minecraft != null) minecraft.setScreen(new ItemBagScreen(this));
            }
            case "portable_crafting" ->
                    NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.CraftingOpenPacket());
            case "portable_furnace" ->
                    NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.FurnaceOpenPacket());
            case "portable_anvil" ->
                    NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.AnvilOpenPacket());
            case "portable_ender_chest" ->
                    NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.EnderChestOpenPacket());
            default ->
                    NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.PortableToolOpenPacket(id));
        }
    }

    private void backToStats() {
        if (minecraft != null) {
            minecraft.setScreen(new StatsScreen());
        }
    }

    private static PlayerStats getClientStats() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        return mc.player.getCapability(PlayerStatsProvider.PLAYER_STATS).orElse(null);
    }

    private int unlockedCount() {
        int n = 0;
        for (String id : TOOL_IDS) {
            if (isUnlocked(id)) n++;
        }
        return n;
    }

    /**
     * 该工具是否可用：用「有效等级 &gt; 0」判断，开关型属性激活时等级为 1，
     * 且天然尊重「功能开关」把整条关掉的情况。
     */
    private boolean isUnlocked(String id) {
        return stats != null && stats.getEffectiveStatLevel(id) > 0;
    }

    @Override
    protected void renderPanel(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(leftPos - 2, topPos - 2, leftPos + GUI_WIDTH + 2, topPos + GUI_HEIGHT + 2, BORDER_COLOR);
        graphics.fill(leftPos, topPos, leftPos + GUI_WIDTH, topPos + GUI_HEIGHT, BG_PANEL);

        graphics.drawCenteredString(font, title, leftPos + GUI_WIDTH / 2, topPos + 14, TEXT_TITLE);
        graphics.drawCenteredString(font,
                Component.translatable("gui.infinitestats.tools.subtitle", unlockedCount(), TOOL_IDS.size()),
                leftPos + GUI_WIDTH / 2, topPos + 28, TEXT_SUBTITLE);
        graphics.drawCenteredString(font, Component.translatable("gui.infinitestats.tools.hint"),
                leftPos + GUI_WIDTH / 2, topPos + GUI_HEIGHT - 44, TEXT_HINT);

        if (toolMaxScroll > 0) {
            EditorUi.scrollbar(graphics, mouseX, mouseY, leftPos + SCROLL_X, topPos + GRID_TOP,
                    VISIBLE_ROWS * ROW_STRIDE - ROW_GAP, toolScrollRow, toolMaxScroll, VISIBLE_ROWS);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
