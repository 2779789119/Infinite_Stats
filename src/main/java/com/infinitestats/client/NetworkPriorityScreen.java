package com.infinitestats.client;

import com.infinitestats.Config;
import com.infinitestats.compat.AE2NetworkBridge;
import com.infinitestats.compat.BDNetworkBridge;
import com.infinitestats.compat.BackpackNetworkBridge;
import com.infinitestats.compat.RSNetworkBridge;
import com.infinitestats.compat.TomsStorageNetworkBridge;
import com.infinitestats.network.NetworkHandler;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 存储优先级界面（**按功能分作用域**）—— 逐个把某个存储往上 / 往下挪一位。
 * <p>
 * 每个用到存储网络的功能各有一份独立优先级，互不影响（作用域见
 * {@link PlayerStats#NETWORK_SCOPES}）：自动入库、随身工作台、随身熔炉、成品仓。
 * 打开时传入对应作用域，标题与说明按作用域取语言键。
 * <p>
 * 取代了原先「点按钮把队首移到队尾」的循环式调整：那个做法要转到想要的顺序得点很多次，
 * 而且中途看不出每一步是谁越过了谁。现在每行右侧的 ▲ / ▼ 只挪动相邻一位，顺序一眼可见；
 * 底部还可一键恢复成配置 {@code NetworkPriority.networkPriority} 的顺序。
 * <p>
 * 视觉与交互沿用 {@link EditorUi} 规范；提交走数据包 {@code SetNetworkPriorityPacket}。
 */
public class NetworkPriorityScreen extends Screen {

    /** 支持的全部存储键（与 NetworkIO / NetworkHandler#normalizeNetworkOrder 口径一致） */
    private static final List<String> ALL_KEYS = List.of("RS", "AE2", "TOMS", "BACKPACK", "BD");

    // ======================== 布局 ========================

    private static final int GUI_W = 300;
    private static final int ROW_H = EditorUi.ROW_H;          // 22
    private static final int MOVE_BTN_W = 20;
    private static final int FOOTER_H = 20;
    private static final int HINT_Y = EditorUi.HEADER_H + 4;  // 28
    private static final int LIST_Y = HINT_Y + 20;            // 48
    private static final int FOOTER_Y = LIST_Y + 5 * ROW_H + 8;
    private static final int GUI_H = FOOTER_Y + FOOTER_H + EditorUi.GAP;

    // ======================== 状态 ========================

    /** 作用域：决定读写的优先级是哪份、标题与说明取哪套语言键 */
    private final String scope;

    /** 当前顺序（靠前者优先）。本地立即生效，同时提交服务端。 */
    private final List<String> order = new ArrayList<>();

    /** 关闭时返回的父界面 */
    private final Screen parent;

    private int leftPos, topPos;
    private int hoveredRow = -1;

    public NetworkPriorityScreen(Screen parent, String scope) {
        super(Component.translatable("screen.infinitestats.network_priority." + scope + ".title"));
        this.parent = parent;
        this.scope = scope;
        order.addAll(loadOrder());
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    /** 当前生效顺序（玩家自定义 → 配置默认），并把缺失的键补到末尾，与服务器归一化口径一致。 */
    private List<String> loadOrder() {
        List<String> out = new ArrayList<>();
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.getCapability(PlayerStatsProvider.PLAYER_STATS)
                    .ifPresent(stats -> out.addAll(stats.getEffectiveNetworkPriority(scope)));
        }
        appendMissing(out);
        return out;
    }

    /** 把 ALL_KEYS 里没出现过的键按标准顺序补到末尾。 */
    private static void appendMissing(List<String> out) {
        for (String key : ALL_KEYS) {
            if (!out.contains(key)) out.add(key);
        }
    }

    @Override
    protected void init() {
        leftPos = (width - GUI_W) / 2;
        topPos = (height - GUI_H) / 2;

        for (int i = 0; i < order.size(); i++) {
            final int index = i;
            int rowY = topPos + LIST_Y + i * ROW_H;
            int downX = leftPos + GUI_W - EditorUi.GAP - MOVE_BTN_W;
            int upX = downX - MOVE_BTN_W - 2;

            Button up = Button.builder(Component.literal("\u25B2"), b -> move(index, -1))
                    .bounds(upX, rowY + 3, MOVE_BTN_W, ROW_H - 6).build();
            up.active = i > 0;
            addRenderableWidget(up);

            Button down = Button.builder(Component.literal("\u25BC"), b -> move(index, 1))
                    .bounds(downX, rowY + 3, MOVE_BTN_W, ROW_H - 6).build();
            down.active = i < order.size() - 1;
            addRenderableWidget(down);
        }

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.infinitestats.network_priority.reset"),
                        b -> resetToDefault())
                .bounds(leftPos + EditorUi.GAP, topPos + FOOTER_Y, 92, FOOTER_H).build());

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.infinitestats.deposit_filter.done"),
                        b -> onClose())
                .bounds(leftPos + GUI_W - EditorUi.GAP - 72, topPos + FOOTER_Y, 72, FOOTER_H).build());
    }

    /** 把第 index 个存储与相邻的一位交换。 */
    private void move(int index, int delta) {
        int target = index + delta;
        if (target < 0 || target >= order.size()) return;
        order.add(target, order.remove(index));
        playClick();
        submit();
        rebuildWidgets();
    }

    /** 恢复配置里的默认顺序：给服务端发空列表，服务端会按配置补全。 */
    private void resetToDefault() {
        NetworkHandler.CHANNEL.sendToServer(
                new NetworkHandler.SetNetworkPriorityPacket(scope, List.of()));

        order.clear();
        List<? extends String> cfg = Config.NETWORK_PRIORITY.get();
        if (cfg != null) {
            for (String raw : cfg) {
                if (raw == null) continue;
                String key = raw.trim().toUpperCase(Locale.ROOT);
                if (ALL_KEYS.contains(key) && !order.contains(key)) order.add(key);
            }
        }
        appendMissing(order);
        playClick();
        rebuildWidgets();
    }

    private void submit() {
        NetworkHandler.CHANNEL.sendToServer(
                new NetworkHandler.SetNetworkPriorityPacket(scope, new ArrayList<>(order)));
    }

    // ======================== 绘制 ========================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        EditorUi.panel(g, leftPos, topPos, GUI_W, GUI_H);
        EditorUi.title(g, font, leftPos, topPos, GUI_W, title.getString());
        g.drawString(font,
                Component.translatable("screen.infinitestats.network_priority." + scope + ".hint").getString(),
                leftPos + EditorUi.GAP, topPos + HINT_Y, EditorUi.SECONDARY);

        hoveredRow = -1;
        int textYOffset = (ROW_H - 8) / 2;
        for (int i = 0; i < order.size(); i++) {
            int rowY = topPos + LIST_Y + i * ROW_H;
            boolean hover = mouseX >= leftPos + EditorUi.GAP && mouseX < leftPos + GUI_W - EditorUi.GAP
                    && mouseY >= rowY && mouseY < rowY + ROW_H;
            if (hover) hoveredRow = i;

            g.fill(leftPos + EditorUi.GAP - 2, rowY, leftPos + GUI_W - EditorUi.GAP + 2, rowY + ROW_H,
                    hover ? EditorUi.BG_ROW_HOVER : EditorUi.BG_ROW);

            String key = order.get(i);
            g.drawString(font, String.valueOf(i + 1),
                    leftPos + EditorUi.GAP + 4, rowY + textYOffset, EditorUi.SECONDARY);
            g.drawString(font, displayName(key),
                    leftPos + EditorUi.GAP + 18, rowY + textYOffset,
                    hover ? EditorUi.ACCENT : EditorUi.PRIMARY);

            boolean loaded = isLoaded(key);
            String badge = Component.translatable(loaded
                    ? "screen.infinitestats.network_priority.installed"
                    : "screen.infinitestats.network_priority.missing").getString();
            int badgeRight = leftPos + GUI_W - EditorUi.GAP - MOVE_BTN_W * 2 - 10;
            g.drawString(font, badge, badgeRight - font.width(badge), rowY + textYOffset,
                    loaded ? EditorUi.INFO : EditorUi.DANGER);
        }

        if (hoveredRow >= 0) {
            List<String> lines = new ArrayList<>();
            lines.add(Component.translatable(
                    "screen.infinitestats.network_priority.row_tip").getString());
            if (!isLoaded(order.get(hoveredRow))) {
                lines.add(Component.translatable(
                        "screen.infinitestats.network_priority.missing_tip").getString());
            }
            EditorUi.tooltip(g, mouseX, mouseY, lines, EditorUi.INFO);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    // ======================== 工具 ========================

    private static String displayName(String key) {
        return Component.translatable("gui.infinitestats.storage." + key).getString();
    }

    /** 对应模组是否已安装（纯客户端 `ModList` 查询，查不到就是未安装）。 */
    private static boolean isLoaded(String key) {
        return switch (key) {
            case "RS" -> RSNetworkBridge.isRSLoaded();
            case "AE2" -> AE2NetworkBridge.isAE2Loaded();
            case "TOMS" -> TomsStorageNetworkBridge.isTSLoaded();
            case "BACKPACK" -> BackpackNetworkBridge.isSBLoaded();
            case "BD" -> BDNetworkBridge.isBDLoaded();
            default -> false;
        };
    }

    private void playClick() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }
}
