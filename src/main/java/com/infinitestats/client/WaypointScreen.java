package com.infinitestats.client;

import com.infinitestats.network.NetworkHandler;
import com.infinitestats.network.WaystoneEntry;
import com.infinitestats.network.WaypointActionPacket;
import com.infinitestats.stats.PlayerStatsProvider;
import com.infinitestats.stats.Waypoint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/**
 * 传送点管理面板
 * 通过此面板可以：保存当前位置、查看/传送/重命名/删除传送点。
 * 所有操作都需要『定点传送』属性已激活（与普通命令一致）。
 */
public class WaypointScreen extends PanelScreen {

    private static final int GUI_W = 420;
    private static final int GUI_H = 300;
    private static final int ROW_H = 24;
    private static final int MAX_VISIBLE = 8;
    private static final int SCROLLBAR_W = 6;

    private static final int BG_PANEL = 0xE81A1A2E;
    private static final int BG_ROW = 0x50252535;
    private static final int BG_ROW_HOVER = 0x80353550;
    private static final int TEXT_PRIMARY = 0xFFE2E8F0;
    private static final int TEXT_SECONDARY = 0xFF94A3B8;
    private static final int TEXT_GOLD = 0xFFFFD166;
    private static final int TEXT_ACTIVE = 0xFF4ADE80;
    private static final int TEXT_NEGATIVE = 0xFFF87171;

    private int listTop, listLeft, rowW, listH;
    private boolean scrollbarDragging;

    @Override
    protected int panelW() {
        return GUI_W;
    }

    @Override
    protected int panelH() {
        return GUI_H;
    }

    /** 控件由本类在 renderPanel 末尾自绘（保持原有绘制顺序），基类不再重复绘制。 */
    @Override
    protected boolean baseRendersWidgets() {
        return false;
    }

    private EditBox nameField;
    private final List<Button> rowButtons = new ArrayList<>();
    private int scrollOffset;
    private int maxScroll;

    private Map<String, Waypoint> currentWaypoints = new HashMap<>();
    /** 服务端下发的「已激活传送石碑」（读自 {@link NetworkHandler#pendingWaystones}）。 */
    private List<WaystoneEntry> currentWaystones = new ArrayList<>();
    /** 已读到的石碑列表版本号（与 {@link NetworkHandler#waystoneListVersion} 比对）。 */
    private long waystoneVersion = -1;
    private boolean toggleActive;
    private String lastSignature = "";
    private long lastSoundTick;

    public WaypointScreen() {
        super(Component.translatable("screen.infinitestats.waypoint"));
    }

    @Override
    protected void init() {
        super.init();
        listTop = topPos + 92;
        listLeft = leftPos + 10;
        rowW = GUI_W - 20 - SCROLLBAR_W - 4;
        listH = MAX_VISIBLE * ROW_H;

        // 名称输入框
        nameField = new EditBox(font, leftPos + 12, topPos + 64, 200, 18, Component.literal(""));
        nameField.setMaxLength(32);
        nameField.setHint(Component.translatable("gui.infinitestats.wp_name_hint"));
        addRenderableWidget(nameField);

        // 保存当前位置按钮
        Button saveBtn = Button.builder(Component.translatable("button.infinitestats.wp_save"),
                b -> saveCurrent()).bounds(leftPos + 220, topPos + 62, 120, 20).build();
        addRenderableWidget(saveBtn);

        // 顺带要一次「已激活传送石碑」列表（没装传送石碑时服务端回空表）
        NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.WaystoneListRequestPacket());

        refreshData();
        rebuildRows();
    }

    @Override
    public void tick() {
        super.tick();
        refreshData();
        // 数据变化（保存/删除/重命名/服务器同步/石碑列表更新）时重建行
        String sig = buildSignature();
        if (!sig.equals(lastSignature)) {
            lastSignature = sig;
            rebuildRows();
        }
    }

    private void refreshData() {
        Player player = Minecraft.getInstance().player;
        currentWaypoints = new HashMap<>();
        toggleActive = false;
        if (player != null) {
            player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
                currentWaypoints.putAll(stats.getWaypoints());
                toggleActive = stats.isToggleActive("fixed_point_teleport");
            });
        }

        // 石碑列表：只在服务端推了新版本时重读（列表本体放在 NetworkHandler 的静态字段里）
        if (waystoneVersion != NetworkHandler.waystoneListVersion) {
            waystoneVersion = NetworkHandler.waystoneListVersion;
            currentWaystones = new ArrayList<>(NetworkHandler.pendingWaystones);
        }

        maxScroll = Math.max(0, rowCount() - MAX_VISIBLE);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
    }

    /** 列表总行数：自己的路径点在前，已激活的传送石碑排在后面。 */
    private int rowCount() {
        return currentWaypoints.size() + currentWaystones.size();
    }

    /** 石碑按名称排序后的副本（绘制与重建行都用它，保证两次的顺序一致）。 */
    private List<WaystoneEntry> sortedWaystones() {
        List<WaystoneEntry> list = new ArrayList<>(currentWaystones);
        list.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.name, b.name));
        return list;
    }

    private String buildSignature() {
        StringBuilder sb = new StringBuilder();
        List<String> names = new ArrayList<>(currentWaypoints.keySet());
        names.sort(String::compareToIgnoreCase);
        for (String n : names) {
            Waypoint w = currentWaypoints.get(n);
            sb.append(n).append('|').append(w.dimension).append('|')
                    .append((int) w.x).append(',').append((int) w.y).append(',').append((int) w.z).append(';');
        }
        // 石碑也进签名：服务端重推列表（新激活 / 改名 / 删除）时要能触发重建行
        sb.append('#');
        for (WaystoneEntry e : sortedWaystones()) {
            sb.append(e.name).append('|').append(e.dimension).append('|')
                    .append((int) e.x).append(',').append((int) e.y).append(',').append((int) e.z).append(';');
        }
        return sb.toString() + (toggleActive ? "1" : "0");
    }

    private void rebuildRows() {
        for (Button b : rowButtons) {
            removeWidget(b);
        }
        rowButtons.clear();

        List<String> names = sortedNames();
        List<WaystoneEntry> waystones = sortedWaystones();
        int ownSize = names.size();

        for (int i = 0; i < MAX_VISIBLE; i++) {
            int idx = i + scrollOffset;
            if (idx >= ownSize + waystones.size()) break;

            int rowY = listTop + i * ROW_H;
            int delX = listLeft + rowW - 48;
            int renameX = delX - 6 - 48;
            int tpX = renameX - 6 - 56;

            if (idx < ownSize) {
                // 自己的路径点：传送 / 重命名 / 删除
                String name = names.get(idx);
                Button tpBtn = Button.builder(Component.translatable("button.infinitestats.wp_teleport"),
                        b -> sendAction(WaypointActionPacket.Action.TELEPORT, name))
                        .bounds(tpX, rowY + 2, 56, ROW_H - 4).build();
                Button renameBtn = Button.builder(Component.translatable("button.infinitestats.wp_rename"),
                        b -> openRename(name)).bounds(renameX, rowY + 2, 48, ROW_H - 4).build();
                Button delBtn = Button.builder(Component.translatable("button.infinitestats.wp_delete"),
                        b -> sendAction(WaypointActionPacket.Action.DELETE, name))
                        .bounds(delX, rowY + 2, 48, ROW_H - 4).build();

                delBtn.active = toggleActive;
                renameBtn.active = toggleActive;
                tpBtn.active = toggleActive;

                addRenderableWidget(tpBtn);
                addRenderableWidget(renameBtn);
                addRenderableWidget(delBtn);
                rowButtons.add(tpBtn);
                rowButtons.add(renameBtn);
                rowButtons.add(delBtn);
            } else {
                // 传送石碑：只有「传送」（免费无冷却）；改名 / 删除在传送石碑自己的界面里做
                WaystoneEntry entry = waystones.get(idx - ownSize);
                Button tpBtn = Button.builder(Component.translatable("button.infinitestats.wp_teleport"),
                        b -> teleportToWaystone(entry))
                        .bounds(tpX, rowY + 2, 56, ROW_H - 4).build();
                tpBtn.active = toggleActive;
                addRenderableWidget(tpBtn);
                rowButtons.add(tpBtn);
            }
        }
    }

    /** 请求传送到某块已激活的传送石碑（免费无冷却；服务端会先核对这块石碑是否真的已激活）。 */
    private void teleportToWaystone(WaystoneEntry entry) {
        NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.WaystoneTeleportPacket(entry.uid));
        playClickSound();
    }

    private List<String> sortedNames() {
        List<String> names = new ArrayList<>(currentWaypoints.keySet());
        names.sort(String::compareToIgnoreCase);
        return names;
    }

    private void saveCurrent() {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        if (!toggleActive) {
            player.displayClientMessage(Component.translatable("message.infinitestats.teleport_disabled"), true);
            return;
        }
        String name = nameField.getValue().trim();
        if (name.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.infinitestats.wp_name_empty"), true);
            return;
        }
        String dim = player.level().dimension().location().toString();
        NetworkHandler.CHANNEL.sendToServer(new WaypointActionPacket(
                WaypointActionPacket.Action.SET, name, null, dim,
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot()));
        nameField.setValue("");
        playClickSound();
    }

    private void sendAction(WaypointActionPacket.Action action, String name) {
        NetworkHandler.CHANNEL.sendToServer(
                new WaypointActionPacket(action, name, null, null, 0, 0, 0, 0, 0));
        playClickSound();
    }

    private void openRename(String oldName) {
        if (minecraft != null) {
            minecraft.setScreen(new WaypointRenameScreen(this, oldName));
        }
    }

    private void playClickSound() {
        if (minecraft == null) return;
        long now = System.currentTimeMillis();
        if (now - lastSoundTick < 50) return;
        lastSoundTick = now;
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    protected void renderPanel(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 面板
        graphics.fill(leftPos - 2, topPos - 2, leftPos + GUI_W + 2, topPos + GUI_H + 2, 0x403B82F6);
        graphics.fill(leftPos - 1, topPos - 1, leftPos + GUI_W + 1, topPos + GUI_H + 1, 0xFF1E293B);
        graphics.fill(leftPos, topPos, leftPos + GUI_W, topPos + GUI_H, BG_PANEL);

        // 标题
        graphics.drawCenteredString(font, Component.translatable("screen.infinitestats.waypoint").getString(),
                leftPos + GUI_W / 2, topPos + 8, TEXT_GOLD);

        // 当前位置信息
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            String dim = player.level().dimension().location().toString();
            String pos = String.format("%.1f, %.1f, %.1f", player.getX(), player.getY(), player.getZ());
            graphics.drawString(font,
                    Component.translatable("gui.infinitestats.wp_current", dim, pos).getString(),
                    leftPos + 12, topPos + 34, TEXT_SECONDARY);
        }

        // 定点传送状态
        int toggleColor = toggleActive ? TEXT_ACTIVE : TEXT_NEGATIVE;
        Component toggleText = toggleActive
                ? Component.translatable("gui.infinitestats.wp_fixed_on")
                : Component.translatable("gui.infinitestats.wp_fixed_off");
        graphics.drawString(font, Component.translatable("gui.infinitestats.wp_fixed", toggleText).getString(),
                leftPos + 12, topPos + 46, toggleColor);
        if (!toggleActive) {
            graphics.drawCenteredString(font,
                    Component.translatable("gui.infinitestats.wp_fixed_hint").getString(),
                    leftPos + GUI_W / 2, topPos + GUI_H - 12, TEXT_NEGATIVE);
        }

        // 列表区域背景
        graphics.fill(listLeft - 2, listTop - 2, listLeft + rowW + 2, listTop + listH + 2, 0x30151525);

        // 绘制行（自己的路径点在前，已激活的传送石碑在后）
        List<String> names = sortedNames();
        List<WaystoneEntry> waystones = sortedWaystones();
        int ownSize = names.size();
        for (int i = 0; i < MAX_VISIBLE; i++) {
            int idx = i + scrollOffset;
            if (idx >= ownSize + waystones.size()) break;

            int rowY = listTop + i * ROW_H;
            boolean hovered = mouseX >= listLeft && mouseX < listLeft + rowW
                    && mouseY >= rowY && mouseY < rowY + ROW_H;
            graphics.fill(listLeft, rowY, listLeft + rowW, rowY + ROW_H - 1, hovered ? BG_ROW_HOVER : BG_ROW);

            int coordsColor = toggleActive ? TEXT_SECONDARY : TEXT_NEGATIVE;
            int textY = rowY + (ROW_H - font.lineHeight) / 2 + 1;

            if (idx < ownSize) {
                String name = names.get(idx);
                Waypoint wp = currentWaypoints.get(name);

                String shownName = font.width(name) > 110 ? truncate(name, 110) : name;
                graphics.drawString(font, shownName, listLeft + 6, textY, TEXT_PRIMARY);

                String coords = wp.dimension.substring(wp.dimension.indexOf(':') + 1) + "  "
                        + String.format("%.0f, %.0f, %.0f", wp.x, wp.y, wp.z);
                graphics.drawString(font, truncate(coords, 130), listLeft + 120, textY, coordsColor);
            } else {
                // 石碑行用金色名字，与自己的点区分开
                WaystoneEntry entry = waystones.get(idx - ownSize);

                String shownName = font.width(entry.name) > 110 ? truncate(entry.name, 110) : entry.name;
                graphics.drawString(font, shownName, listLeft + 6, textY, TEXT_GOLD);

                String dim = entry.dimension.contains(":")
                        ? entry.dimension.substring(entry.dimension.indexOf(':') + 1)
                        : entry.dimension;
                String coords = dim + "  " + String.format("%.0f, %.0f, %.0f", entry.x, entry.y, entry.z);
                graphics.drawString(font, truncate(coords, 130), listLeft + 120, textY, coordsColor);
            }
        }

        // 滚动条
        renderScrollbar(graphics, mouseX, mouseY);

        // 底部提示
        graphics.drawCenteredString(font,
                Component.translatable("gui.infinitestats.wp_hint").getString(),
                leftPos + GUI_W / 2, topPos + GUI_H - 26, TEXT_SECONDARY);

        // 渲染按钮等控件
        for (var renderable : this.renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private void renderScrollbar(GuiGraphics g, int mouseX, int mouseY) {
        if (maxScroll <= 0) return;
        // 与拖拽共用 EditorUi 的几何算法：滑块中心跟手，点不中/回弹都不会再出现
        EditorUi.scrollbar(g, mouseX, mouseY, scrollbarX(), listTop, listH,
                scrollOffset, maxScroll, MAX_VISIBLE);
    }

    /** 滚动条轨道的 x（与列表右边留 2px）。 */
    private int scrollbarX() {
        return listLeft + rowW + 2;
    }

    // ======================== 滚动条拖拽 ========================

    @Override
    protected boolean onPanelClick(int mouseX, int mouseY, int button) {
        if (button == 0 && maxScroll > 0
                && EditorUi.onScrollbar(mouseX, mouseY, scrollbarX(), listTop, listH)) {
            scrollbarDragging = true;
            scrollOffset = EditorUi.scrollFromMouseY(mouseY, listTop, listH, maxScroll, MAX_VISIBLE);
            rebuildRows();
            return true;
        }
        return false;
    }

    @Override
    protected boolean onPanelDrag(int mouseX, int mouseY, int button, int dragX, int dragY) {
        if (scrollbarDragging && maxScroll > 0) {
            scrollOffset = EditorUi.scrollFromMouseY(mouseY, listTop, listH, maxScroll, MAX_VISIBLE);
            rebuildRows();
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

    private String truncate(String text, int maxWidth) {
        if (text == null || font.width(text) <= maxWidth) return text == null ? "" : text;
        int limit = maxWidth - font.width("…");
        if (limit <= 0) return "…";
        StringBuilder sb = new StringBuilder();
        int w = 0;
        for (int i = 0; i < text.length(); i++) {
            int cw = font.width(text.substring(i, i + 1));
            if (w + cw > limit) break;
            w += cw;
            sb.append(text.charAt(i));
        }
        return sb.toString() + "…";
    }

    @Override
    protected boolean onPanelScroll(int mouseX, int mouseY, double delta) {
        if (maxScroll <= 0) return false;
        int newOffset = Mth.clamp(scrollOffset + (delta > 0 ? -1 : 1), 0, maxScroll);
        if (newOffset != scrollOffset) {
            scrollOffset = newOffset;
            rebuildRows();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || ClientSetup.OPEN_WAYPOINT_KEY.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
