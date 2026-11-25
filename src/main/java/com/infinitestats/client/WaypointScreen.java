package com.infinitestats.client;

import com.infinitestats.network.NetworkHandler;
import com.infinitestats.network.WaypointActionPacket;
import com.infinitestats.network.WaystoneEntry;
import com.infinitestats.network.WaystoneListRequestPacket;
import com.infinitestats.network.WaystoneTeleportPacket;
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
    private static final int GUI_H = 340;
    private static final int ROW_H = 24;
    private static final int MAX_VISIBLE = 7;
    private static final int SCROLLBAR_W = 6;

    private static final int BG_PANEL = 0xE81A1A2E;
    private static final int BG_ROW = 0x50252535;
    private static final int BG_ROW_HOVER = 0x80353550;
    private static final int TEXT_PRIMARY = 0xFFE2E8F0;
    private static final int TEXT_SECONDARY = 0xFF94A3B8;
    private static final int TEXT_GOLD = 0xFFFFD166;
    private static final int TEXT_ACTIVE = 0xFF4ADE80;
    private static final int TEXT_NEGATIVE = 0xFFF87171;

    private int leftPos, topPos;
    private int listTop, listLeft, rowW, listH;

    private EditBox nameField;
    /** 搜索框：名称 / 维度 / 坐标都能匹配（名称支持拼音）。 */
    private EditBox searchField;
    private String searchText = "";
    /** 排序：0 = 名称，1 = 坐标 X，2 = 维度。 */
    private int sortMode;
    /** 删除传送点需要二次点击确认（与主版本一致）。 */
    private final ConfirmAction confirm = new ConfirmAction();
    private boolean confirmPending;
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
        listTop = topPos + 116;
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

        // 搜索框：名称 / 维度 / 坐标都能筛（右侧留 84px 给排序按钮）
        searchText = "";
        searchField = new EditBox(font, leftPos + 12, topPos + 88, GUI_W - 24 - 84, 18, Component.empty());
        searchField.setMaxLength(48);
        searchField.setHint(Component.translatable("gui.infinitestats.wp_search_hint"));
        searchField.setResponder(v -> {
            searchText = v;
            scrollOffset = 0;
            rebuildRows();
        });
        addRenderableWidget(searchField);

        // 传送石碑（Waystones）联动：开面板时请求一次「已激活的石碑」。
        // 没装传送石碑时服务端回的是空表，面板里不会多出任何行
        NetworkHandler.CHANNEL.sendToServer(new WaystoneListRequestPacket());

        refreshData();
        rebuildRows();
    }

    @Override
    public void tick() {
        super.tick();
        refreshData();
        // 删除确认超时后把「确认？」还原成「删除」
        boolean pending = confirm.isPending();
        if (pending != confirmPending) {
            confirmPending = pending;
            rebuildRows();
        }

        // 传送石碑列表变化（服务端刚回包）→ 取走并重建行
        if (waystoneVersion != NetworkHandler.waystoneListVersion) {
            waystoneVersion = NetworkHandler.waystoneListVersion;
            currentWaystones = new ArrayList<>(NetworkHandler.pendingWaystones);
            maxScroll = Math.max(0, currentWaypoints.size() + currentWaystones.size() - MAX_VISIBLE);
            scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
            rebuildRows();
            return;
        }

        // 数据变化（保存/删除/重命名/服务器同步）时重建行
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
            PlayerStatsProvider.of(player).ifPresent(stats -> {
                currentWaypoints.putAll(stats.getWaypoints());
                toggleActive = stats.isToggleActive("fixed_point_teleport");
            });
        }
        // 滚动范围要把「已激活传送石碑」那些行也算进去
        maxScroll = Math.max(0, currentWaypoints.size() + currentWaystones.size() - MAX_VISIBLE);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
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
        return sb.toString() + (toggleActive ? "1" : "0");
    }

    private void rebuildRows() {
        for (Button b : rowButtons) {
            removeWidget(b);
        }
        rowButtons.clear();

        List<String> names = sortedNames();
        List<WaystoneEntry> filteredWaystones = sortedWaystones();
        // 滚动范围按「筛选后」的行数算：搜索把列表缩短时不会留下滚不回去的空档
        maxScroll = Math.max(0, names.size() + filteredWaystones.size() - MAX_VISIBLE);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

        for (int i = 0; i < MAX_VISIBLE; i++) {
            int idx = i + scrollOffset;
            // 必须是 continue 而不是 break：自有点之后还接着传送石碑那些行，不能提前结束整轮
            if (idx >= names.size()) continue;
            String name = names.get(idx);

            int rowY = listTop + i * ROW_H;
            int delX = listLeft + rowW - 48;
            int renameX = delX - 6 - 48;
            int tpX = renameX - 6 - 56;

            Button tpBtn = Button.builder(Component.translatable("button.infinitestats.wp_teleport"),
                    b -> sendAction(WaypointActionPacket.Action.TELEPORT, name))
                    .bounds(tpX, rowY + 2, 56, ROW_H - 4).build();
            Button renameBtn = Button.builder(Component.translatable("button.infinitestats.wp_rename"),
                    b -> openRename(name)).bounds(renameX, rowY + 2, 48, ROW_H - 4).build();
            boolean confirmingDelete = confirm.isConfirming("del:" + name);
            Button delBtn = Button.builder(
                    Component.translatable(confirmingDelete
                            ? "screen.infinitestats.ui.confirm"
                            : "button.infinitestats.wp_delete"),
                    b -> requestDelete(name))
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
        }

        // 传送石碑（Waystones）联动：接在自有点之后，只有一个「传送」按钮
        List<WaystoneEntry> waystones = filteredWaystones;
        int ownCount = names.size();
        for (int i = 0; i < MAX_VISIBLE; i++) {
            int idx = i + scrollOffset;
            if (idx >= ownCount + waystones.size()) break;
            if (idx < ownCount) continue;          // 自有点那几行上面已经建过

            WaystoneEntry entry = waystones.get(idx - ownCount);
            int rowY = listTop + i * ROW_H;
            Button tpBtn = Button.builder(Component.translatable("button.infinitestats.wp_teleport"),
                    b -> teleportToWaystone(entry))
                    .bounds(listLeft + rowW - 62, rowY + 2, 56, ROW_H - 4).build();
            addRenderableWidget(tpBtn);
            rowButtons.add(tpBtn);
        }
    }

    /** 当前搜索词（小写、去空白）；空串表示不筛。 */
    private String needle() {
        return searchText == null ? "" : searchText.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /** 已激活的传送石碑，按名称（再按维度、UUID）排序，保证列表顺序稳定。 */
    private List<WaystoneEntry> sortedWaystones() {
        String q = needle();
        List<WaystoneEntry> list = new ArrayList<>();
        for (WaystoneEntry e : currentWaystones) {
            if (q.isEmpty()
                    || e.name.toLowerCase(java.util.Locale.ROOT).contains(q)
                    || com.infinitestats.compat.PinyinSearchBridge.matches(
                            e.name.toLowerCase(java.util.Locale.ROOT), q)
                    || (e.dimension != null && e.dimension.toLowerCase(java.util.Locale.ROOT).contains(q))) {
                list.add(e);
            }
        }
        list.sort(Comparator.comparing((WaystoneEntry e) -> e.name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(e -> e.dimension)
                .thenComparing(e -> e.uid));
        return list;
    }

    /** 免费（不扣经验、不进冷却）传送到某块已激活的石碑 —— 服务端会再核一遍归属。 */
    private void teleportToWaystone(WaystoneEntry entry) {
        NetworkHandler.CHANNEL.sendToServer(new WaystoneTeleportPacket(entry.uid));
        playClickSound();
    }

    private List<String> sortedNames() {
        String q = needle();
        List<String> names = new ArrayList<>();
        for (Map.Entry<String, Waypoint> entry : currentWaypoints.entrySet()) {
            String name = entry.getKey();
            if (q.isEmpty()) {
                names.add(name);
                continue;
            }
            Waypoint wp = entry.getValue();
            String lower = name.toLowerCase(java.util.Locale.ROOT);
            boolean hit = lower.contains(q)
                    || com.infinitestats.compat.PinyinSearchBridge.matches(lower, q)
                    || (wp != null && wp.dimension != null
                            && wp.dimension.toLowerCase(java.util.Locale.ROOT).contains(q));
            if (!hit && wp != null) {
                // 坐标：允许「123」这种整数部分命中任意一轴
                String coords = String.format(java.util.Locale.ROOT, "%.0f %.0f %.0f",
                        wp.x, wp.y, wp.z);
                hit = coords.contains(q);
            }
            if (hit) names.add(name);
        }

        if (sortMode == 1) {
            Comparator<String> byX = Comparator.comparingDouble((String name) -> {
                Waypoint wp = currentWaypoints.get(name);
                return wp == null ? 0 : wp.x;
            });
            names.sort(byX.thenComparing(String::compareToIgnoreCase));
        } else if (sortMode == 2) {
            names.sort(Comparator.comparing((String name) -> {
                Waypoint wp = currentWaypoints.get(name);
                return wp == null || wp.dimension == null ? "" : wp.dimension;
            }).thenComparing(String::compareToIgnoreCase));
        } else {
            names.sort(String::compareToIgnoreCase);
        }
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

    /** 删除：第一次点只把按钮变成「确认？」，2.5 秒内再点才真的删。 */
    private void requestDelete(String name) {
        if (!confirm.confirm("del:" + name)) {
            playClickSound();
            rebuildRows();
            return;
        }
        sendAction(WaypointActionPacket.Action.DELETE, name);
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

        // 排序按钮（搜索框右侧）：名称 → 坐标 X → 维度
        int sortX = leftPos + GUI_W - 12 - 80;
        int sortY = topPos + 88;
        boolean sortHover = mouseX >= sortX && mouseX < sortX + 80 && mouseY >= sortY && mouseY < sortY + 18;
        graphics.fill(sortX, sortY, sortX + 80, sortY + 18, sortHover ? BG_ROW_HOVER : BG_ROW);
        graphics.drawCenteredString(font,
                Component.translatable(switch (sortMode) {
                    case 1 -> "screen.infinitestats.waypoint.sort_x";
                    case 2 -> "screen.infinitestats.waypoint.sort_dim";
                    default -> "screen.infinitestats.waypoint.sort_name";
                }).getString(),
                sortX + 40, sortY + 5, sortHover ? TEXT_ACTIVE : TEXT_PRIMARY);

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

        // 绘制行
        List<String> names = sortedNames();
        List<WaystoneEntry> waystones = sortedWaystones();
        int ownCount = names.size();
        for (int i = 0; i < MAX_VISIBLE; i++) {
            int idx = i + scrollOffset;
            if (idx >= ownCount + waystones.size()) break;
            int rowY = listTop + i * ROW_H;

            boolean hovered = mouseX >= listLeft && mouseX < listLeft + rowW
                    && mouseY >= rowY && mouseY < rowY + ROW_H;
            graphics.fill(listLeft, rowY, listLeft + rowW, rowY + ROW_H - 1, hovered ? BG_ROW_HOVER : BG_ROW);

            int textY = rowY + (ROW_H - font.lineHeight) / 2 + 1;
            if (idx < ownCount) {
                String name = names.get(idx);
                Waypoint wp = currentWaypoints.get(name);

                // 名称
                String shownName = font.width(name) > 110 ? truncate(name, 110) : name;
                graphics.drawString(font, shownName, listLeft + 6, textY, TEXT_PRIMARY);

                // 维度 + 坐标
                String coords = wp.dimension.substring(wp.dimension.indexOf(':') + 1) + "  "
                        + String.format("%.0f, %.0f, %.0f", wp.x, wp.y, wp.z);
                int coordsColor = toggleActive ? TEXT_SECONDARY : TEXT_NEGATIVE;
                graphics.drawString(font, truncate(coords, 130), listLeft + 120, textY, coordsColor);
            } else {
                // 传送石碑：名称用金色区分（这些点不在本模组的存档里，是传送石碑那边激活的），
                // 不受『定点传送』属性开关限制 —— 能不能传由服务端按归属判定
                WaystoneEntry entry = waystones.get(idx - ownCount);
                graphics.drawString(font, truncate(entry.name, 110), listLeft + 6, textY, TEXT_GOLD);

                String coords = entry.dimension.substring(entry.dimension.indexOf(':') + 1) + "  "
                        + String.format("%.0f, %.0f, %.0f", entry.x, entry.y, entry.z);
                graphics.drawString(font, truncate(coords, 130), listLeft + 120, textY, TEXT_SECONDARY);
            }
        }

        // 滚动条
        renderScrollbar(graphics, mouseX, mouseY);

        // 底部提示
        graphics.drawCenteredString(font,
                Component.translatable("gui.infinitestats.wp_hint").getString(),
                leftPos + GUI_W / 2, topPos + GUI_H - 26, TEXT_SECONDARY);

    }

    private void renderScrollbar(GuiGraphics g, int mouseX, int mouseY) {
        if (maxScroll <= 0) return;
        // 几何交给 EditorUi：绘制 / 命中 / 拖动用同一套算法，滑块画在哪就能拖在哪
        EditorUi.scrollbar(g, mouseX, mouseY, scrollbarX(), listTop, listH,
                scrollOffset, maxScroll, MAX_VISIBLE);
    }

    /** 滚动条轨道左缘（面板坐标）。 */
    private int scrollbarX() {
        return listLeft + rowW + 2;
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
    protected boolean onPanelClick(int mouseX, int mouseY, int button) {
        if (button != 0) return false;
        // 滚动条拖动（排在前面：它在列表右缘，不能被别的命中区抢走）
        if (beginScrollbarDrag(mouseX, mouseY, scrollbarX(), listTop, listH,
                scrollOffset, maxScroll, MAX_VISIBLE, this::setScrollOffset)) {
            return true;
        }
        // 排序按钮：名称 → 坐标 X → 维度
        int sortX = leftPos + GUI_W - 12 - 80;
        int sortY = topPos + 88;
        if (mouseX >= sortX && mouseX < sortX + 80 && mouseY >= sortY && mouseY < sortY + 18) {
            sortMode = (sortMode + 1) % 3;
            scrollOffset = 0;
            rebuildRows();
            playClickSound();
            return true;
        }
        return false;
    }

    @Override
    protected boolean onPanelScroll(int mouseX, int mouseY, double delta) {
        if (maxScroll <= 0) return false;
        int before = scrollOffset;
        setScrollOffset(scrollOffset + (delta > 0 ? -1 : 1));
        return before != scrollOffset;
    }

    @Override
    protected boolean onPanelDrag(int mouseX, int mouseY, int button, int dragX, int dragY) {
        return button == 0 && updateScrollbarDrag(mouseY);
    }

    @Override
    protected boolean onPanelRelease(int mouseX, int mouseY, int button) {
        return button == 0 && endScrollbarDrag();
    }

    /** 滚动位置变化才重建行控件（拖动时每移动一像素都重建会闪）。 */
    private void setScrollOffset(int value) {
        int next = Mth.clamp(value, 0, maxScroll);
        if (next == scrollOffset) return;
        scrollOffset = next;
        rebuildRows();
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
