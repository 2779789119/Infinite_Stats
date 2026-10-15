package com.infinitestats.client;

import com.infinitestats.network.NetworkHandler;
import com.infinitestats.network.PortableStationOpenPacket;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import com.infinitestats.stats.StatType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * 随身工具面板 —— 所有「随身站点」的统一入口。
 * <p>
 * 属性面板页脚只留这一个按钮，站点的开关也在这里：<b>左键 = 打开（未解锁则尝试花 1 点解锁）</b>，
 * <b>右键 = 开启 / 关闭</b>。这样属性面板不必为每个站点各摆一个按钮，也不会因为站点变多而挤爆页脚。
 * <p>
 * 打开 / 关闭都走服务端：打开前服务端会再校验一次属性是否激活，因此这里的状态只是展示，
 * 改不了真正能不能用。
 */
public class PortableToolsScreen extends PanelScreen {

    // ======================== 布局 ========================

    private static final int GUI_W = 380;
    private static final int GUI_H = 292;
    private static final int HINT_Y = EditorUi.HEADER_H + 4;    // 28
    private static final int LIST_Y = HINT_Y + 22;              // 50
    private static final int ROW_H = 20;
    /** 站点行数（新增站点时记得同步它）。 */
    private static final int ROWS = 11;
    private static final int FOOTER_Y = LIST_Y + ROWS * ROW_H + 8;
    private static final int FOOTER_H = 20;

    /** 一个随身站点：属性 id + 图标 + 打开方式。 */
    private record Station(String statId, ItemStack icon, OpenKind kind) {}

    /** 打开方式：原版菜单自建包（工作台 / 熔炉 / 铁砧）或统一随身站点包。 */
    private enum OpenKind { CRAFTING, FURNACE, ANVIL, STATION }

    private static final List<Station> STATIONS = List.of(
            new Station("portable_crafting", new ItemStack(Items.CRAFTING_TABLE), OpenKind.CRAFTING),
            new Station("portable_furnace", new ItemStack(Items.FURNACE), OpenKind.FURNACE),
            new Station("portable_anvil", new ItemStack(Items.ANVIL), OpenKind.ANVIL),
            new Station("portable_ender_chest", new ItemStack(Items.ENDER_CHEST), OpenKind.STATION),
            new Station("portable_smithing", new ItemStack(Items.SMITHING_TABLE), OpenKind.STATION),
            new Station("portable_stonecutter", new ItemStack(Items.STONECUTTER), OpenKind.STATION),
            new Station("portable_loom", new ItemStack(Items.LOOM), OpenKind.STATION),
            new Station("portable_cartography", new ItemStack(Items.CARTOGRAPHY_TABLE), OpenKind.STATION),
            new Station("portable_grindstone", new ItemStack(Items.GRINDSTONE), OpenKind.STATION),
            new Station("portable_brewing", new ItemStack(Items.BREWING_STAND), OpenKind.STATION),
            new Station("portable_enchanting", new ItemStack(Items.ENCHANTING_TABLE), OpenKind.STATION)
    );

    // ======================== 状态 ========================

    private int leftPos;
    private int topPos;
    private int hoveredRow = -1;
    private long lastSoundTick;
    private String statusMsg = "";
    private long statusUntil;

    public PortableToolsScreen() {
        super(Component.translatable("screen.infinitestats.portable_tools"));
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
        // 面板坐标从 (0,0) 起：居中、缩放与拖动都由 PanelScreen 负责
        leftPos = 0;
        topPos = 0;

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.infinitestats.portable_tools.done"),
                        b -> onClose())
                .bounds(leftPos + GUI_W / 2 - 40, topPos + FOOTER_Y, 80, FOOTER_H).build());
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(new StatsScreen());
    }

    // ======================== 数据 ========================

    private static PlayerStats statsOrNull() {
        var player = Minecraft.getInstance().player;
        if (player == null) return null;
        return PlayerStatsProvider.of(player).orElse(null);
    }

    private static boolean isActive(PlayerStats stats, String statId) {
        return stats != null && stats.isToggleActive(statId);
    }

    /** 是否已投点（开关型：>= 1 点即激活）。 */
    private static long pointsOf(PlayerStats stats, String statId) {
        if (stats == null) return 0;
        StatType stat = StatType.fromId(statId);
        return stat == null ? 0 : stats.getStatLevel(stat);
    }

    // ======================== 交互 ========================

    /** 左键：已开启就打开，未开启则尝试花 1 点解锁。 */
    private void openOrUnlock(Station station) {
        PlayerStats stats = statsOrNull();
        if (stats == null) return;

        if (!isActive(stats, station.statId())) {
            StatType stat = StatType.fromId(station.statId());
            if (stat == null) return;
            long current = pointsOf(stats, station.statId());
            long needed = stat.getMaxLevel() - current;
            if (needed <= 0) needed = 1;
            if (stats.getAvailablePoints() < needed) {
                setStatus(Component.translatable(
                        "screen.infinitestats.portable_tools.no_points").getString());
                return;
            }
            playClick();
            NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.ModifyStatPacket(station.statId(), needed));
            setStatus(Component.translatable(
                    "screen.infinitestats.portable_tools.unlocked").getString());
            return;
        }

        playClick();
        switch (station.kind()) {
            case CRAFTING -> NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.CraftingOpenPacket());
            case FURNACE -> NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.FurnaceOpenPacket());
            case ANVIL -> NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.AnvilOpenPacket());
            case STATION -> NetworkHandler.CHANNEL.sendToServer(
                    new PortableStationOpenPacket(station.statId()));
        }
    }

    /** 右键：开启 / 关闭（开关型，1 点）。 */
    private void toggle(Station station) {
        PlayerStats stats = statsOrNull();
        if (stats == null) return;
        boolean active = isActive(stats, station.statId());
        if (!active) {
            openOrUnlock(station);
            return;
        }
        playClick();
        NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.ModifyStatPacket(station.statId(), -1));
        setStatus(Component.translatable(
                "screen.infinitestats.portable_tools.disabled").getString());
    }

    private void setStatus(String msg) {
        statusMsg = msg;
        statusUntil = System.currentTimeMillis() + 2000;
    }

    // ======================== 绘制 ========================

    @Override
    protected void renderPanel(GuiGraphics g, int mx, int my, float pt) {

        EditorUi.panel(g, leftPos, topPos, GUI_W, GUI_H);
        EditorUi.title(g, font, leftPos, topPos, GUI_W, title.getString());

        PlayerStats stats = statsOrNull();
        g.drawString(font,
                Component.translatable("screen.infinitestats.portable_tools.hint").getString(),
                leftPos + EditorUi.GAP, topPos + HINT_Y, EditorUi.SECONDARY);
        String pts = Component.translatable("screen.infinitestats.portable_tools.points",
                stats == null ? 0 : stats.getAvailablePoints()).getString();
        g.drawString(font, pts, leftPos + GUI_W - EditorUi.GAP - font.width(pts),
                topPos + HINT_Y, EditorUi.GOLD);

        int listX = leftPos + EditorUi.GAP;
        int listW = GUI_W - EditorUi.GAP * 2;
        int ly = topPos + LIST_Y;
        EditorUi.dividerH(g, leftPos + 4, ly - 3, GUI_W - 8);

        hoveredRow = -1;
        for (int i = 0; i < STATIONS.size(); i++) {
            Station station = STATIONS.get(i);
            int y = ly + i * ROW_H;
            boolean hover = mx >= listX && mx < listX + listW && my >= y && my < y + ROW_H - 2;
            if (hover) hoveredRow = i;

            boolean active = isActive(stats, station.statId());
            g.fill(listX, y, listX + listW, y + ROW_H - 2,
                    hover ? EditorUi.BG_ROW_HOVER : EditorUi.BG_ROW);
            if (active) g.fill(listX, y, listX + 2, y + ROW_H - 2, EditorUi.ACCENT);

            g.renderItem(station.icon(), listX + 3, y + 1);

            g.drawString(font, Component.translatable("stat.infinitestats." + station.statId()).getString(),
                    listX + 24, y + 5, active ? EditorUi.ACCENT : EditorUi.PRIMARY);

            String state = Component.translatable(active
                    ? "screen.infinitestats.portable_tools.on"
                    : "screen.infinitestats.portable_tools.off").getString();
            g.drawString(font, state, listX + listW - font.width(state) - 6, y + 5,
                    active ? EditorUi.ACCENT : EditorUi.DANGER);
        }

        EditorUi.dividerH(g, leftPos + 4, ly + ROWS * ROW_H - 2, GUI_W - 8);

        if (hoveredRow >= 0) {
            List<String> lines = new ArrayList<>();
            lines.add(Component.translatable(
                    "screen.infinitestats.portable_tools.row_tip").getString());
            EditorUi.tooltip(g, mx, my, lines, EditorUi.INFO);
        }

        if (System.currentTimeMillis() < statusUntil) {
            g.drawCenteredString(font, statusMsg, leftPos + GUI_W / 2,
                    topPos + GUI_H - 10, EditorUi.GOLD);
        }

    }

    // ======================== 输入 ========================

    @Override
    protected boolean onPanelClick(int mouseX, int mouseY, int button) {
        if (hoveredRow >= 0 && (button == 0 || button == 1)) {
            Station station = STATIONS.get(hoveredRow);
            if (button == 0) openOrUnlock(station);
            else toggle(station);
            return true;
        }
        return false;   // 未命中时交回基类，继续派发给控件
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void playClick() {
        long now = System.currentTimeMillis();
        if (now - lastSoundTick < 50) return;
        lastSoundTick = now;
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
