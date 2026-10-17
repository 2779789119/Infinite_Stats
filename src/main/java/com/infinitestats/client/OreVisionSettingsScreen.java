package com.infinitestats.client;

import com.infinitestats.network.NetworkHandler;
import com.infinitestats.network.UpdateOreVisionConfigPacket;
import com.infinitestats.stats.OreVisionConfig;
import com.infinitestats.stats.PlayerStatsProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * 「矿透」设置面板 —— 扫描半径、描边上限、是否需潜行、要显示哪些矿物，都在这一页改。
 * <p>
 * 交互与 {@link VeinMinerSettingsScreen} 完全一致：左列项目名、右列控件，改动即时提交
 * （{@link UpdateOreVisionConfigPacket}），本地先改一份所以界面不必等服务端回包。
 * 矿物种类那一行点开 {@link OreVisionGroupsScreen} 逐类开关。
 */
public class OreVisionSettingsScreen extends PanelScreen {

    // ======================== 布局 ========================

    private static final int GUI_W = 340;
    private static final int ROW_H = EditorUi.ROW_H;
    private static final int ROWS = 4;
    private static final int HINT_Y = EditorUi.HEADER_H + 4;
    private static final int LIST_Y = HINT_Y + 20;
    private static final int FOOTER_H = 20;
    private static final int FOOTER_Y = LIST_Y + ROWS * ROW_H + 6;
    private static final int GUI_H = FOOTER_Y + FOOTER_H + EditorUi.GAP;

    private static final int BTN_H = 16;
    private static final int STEP_W = 18;
    private static final int VALUE_W = 76;
    private static final int SWITCH_W = 92;

    // ======================== 档位 ========================

    /** 半径档位：8 格只够看脚边，64 格能一眼看穿一整条矿脉走向，但单次扫描要遍历的方块位翻好几倍。 */
    private static final int[] RANGE_STEPS = {8, 16, 24, 32, 48, 64};
    private static final int[] BLOCK_STEPS = {100, 300, 500, 1000, 1500, 3000, 5000};

    // ======================== 状态 ========================

    private final Screen parent;
    private final OreVisionConfig cfg = new OreVisionConfig();

    private Button sneakBtn;
    private Button groupsBtn;
    private int rangeValueX;
    private int blocksValueX;

    public OreVisionSettingsScreen(Screen parent) {
        super(Component.translatable("screen.infinitestats.ore_settings"));
        this.parent = parent;
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
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    protected void init() {
        super.init();
        loadFromPlayer();
        buildWidgets();
    }

    /** 从本地（已同步的）玩家数据取一份副本编辑，避免改到共享对象。 */
    private void loadFromPlayer() {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> copyInto(cfg, stats.getOreVisionConfig()));
    }

    private static void copyInto(OreVisionConfig target, OreVisionConfig source) {
        if (source == null) return;
        target.setRange(source.getRange());
        target.setMaxBlocks(source.getMaxBlocks());
        target.setRequireSneak(source.isRequireSneak());
        for (OreVisionConfig.OreGroup group : OreVisionConfig.groups()) {
            target.setGroupHidden(group, source.isGroupHidden(group));
        }
    }

    // ======================== 控件 ========================

    private void buildWidgets() {
        int y0 = topPos + LIST_Y;

        rangeValueX = addIntStepper(y0, RANGE_STEPS, cfg::getRange, cfg::setRange);
        blocksValueX = addIntStepper(y0 + ROW_H, BLOCK_STEPS, cfg::getMaxBlocks, cfg::setMaxBlocks);

        sneakBtn = addRenderableWidget(simpleButton(rowX(), y0 + ROW_H * 2, SWITCH_W, () -> {
            cfg.setRequireSneak(!cfg.isRequireSneak());
            refreshLabels();
            submit();
        }));

        groupsBtn = addRenderableWidget(simpleButton(rowX(), y0 + ROW_H * 3, SWITCH_W, this::openGroups));

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.infinitestats.ore_settings.reset"),
                        b -> {
                            cfg.resetToDefault();
                            playClick();
                            refreshLabels();
                            submit();
                        })
                .bounds(leftPos + EditorUi.GAP, topPos + FOOTER_Y, 76, FOOTER_H).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("screen.infinitestats.deposit_filter.done"),
                        b -> onClose())
                .bounds(leftPos + GUI_W - EditorUi.GAP - 64, topPos + FOOTER_Y, 64, FOOTER_H).build());

        refreshLabels();
    }

    private int rowX() {
        return leftPos + GUI_W - EditorUi.GAP - SWITCH_W;
    }

    private Button simpleButton(int x, int y, int w, Runnable action) {
        return Button.builder(Component.empty(), b -> {
            playClick();
            action.run();
        }).bounds(x, y + 3, w, BTN_H).build();
    }

    /** 数值行：{@code -} / 当前值 / {@code +}，返回画当前值用的坐标。 */
    private int addIntStepper(int y, int[] steps, IntSupplier getter, IntConsumer setter) {
        int plusX = leftPos + GUI_W - EditorUi.GAP - STEP_W;
        int minusX = plusX - VALUE_W - 4 - STEP_W;
        addRenderableWidget(Button.builder(Component.literal("-"), b -> {
            setter.accept(shift(steps, getter.getAsInt(), -1));
            playClick();
            refreshLabels();
            submit();
        }).bounds(minusX, y + 3, STEP_W, BTN_H).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            setter.accept(shift(steps, getter.getAsInt(), 1));
            playClick();
            refreshLabels();
            submit();
        }).bounds(plusX, y + 3, STEP_W, BTN_H).build());
        return minusX + STEP_W + 2;
    }

    /** 在档位表里往前 / 往后挪一格（当前值不在表里时取最接近的一格）。 */
    private static int shift(int[] steps, int current, int delta) {
        int index = 0;
        int best = Integer.MAX_VALUE;
        for (int i = 0; i < steps.length; i++) {
            int diff = Math.abs(steps[i] - current);
            if (diff < best) {
                best = diff;
                index = i;
            }
        }
        int next = Math.max(0, Math.min(steps.length - 1, index + delta));
        return steps[next];
    }

    // ======================== 行为 ========================

    private void openGroups() {
        if (minecraft == null) return;
        minecraft.setScreen(new OreVisionGroupsScreen(this));
    }

    /** 提交给服务端，并同步改一份本地数据（界面立即反映结果，不必等服务端回包）。 */
    private void submit() {
        NetworkHandler.CHANNEL.sendToServer(new UpdateOreVisionConfigPacket(cfg));
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.getCapability(PlayerStatsProvider.PLAYER_STATS)
                    .ifPresent(stats -> stats.setOreVisionConfig(cfg.copy()));
        }
    }

    private void refreshLabels() {
        if (sneakBtn != null) {
            sneakBtn.setMessage(Component.translatable(cfg.isRequireSneak()
                    ? "screen.infinitestats.ore_settings.on"
                    : "screen.infinitestats.ore_settings.off"));
        }
        if (groupsBtn != null) {
            int total = OreVisionConfig.groups().size();
            int visible = cfg.visibleGroupCount();
            groupsBtn.setMessage(Component.translatable(
                    cfg.hasHiddenGroups()
                            ? "screen.infinitestats.ore_settings.groups.partial"
                            : "screen.infinitestats.ore_settings.groups.all",
                    visible, total));
        }
    }

    // ======================== 绘制 ========================

    @Override
    protected void renderPanel(GuiGraphics g, int mx, int my, float pt) {
        EditorUi.panel(g, leftPos, topPos, GUI_W, GUI_H);
        EditorUi.title(g, font, leftPos, topPos, GUI_W, title.getString());

        // 顺带把「当前缓存了多少格」画出来：出现「属性开着却看不到边框」时，
        // 0 与非 0 能立刻把问题定位到扫描侧还是渲染侧
        int outlined = OreVisionOverlay.cachedCount();
        g.drawString(font, Component.translatable("screen.infinitestats.ore_settings.hint", outlined).getString(),
                leftPos + EditorUi.GAP, topPos + HINT_Y,
                outlined > 0 ? EditorUi.ACCENT : EditorUi.SECONDARY);

        for (int i = 0; i < ROWS; i++) {
            int y = topPos + LIST_Y + i * ROW_H;
            g.fill(leftPos + EditorUi.GAP - 2, y, leftPos + GUI_W - EditorUi.GAP + 2, y + ROW_H - 2,
                    (i % 2 == 0) ? EditorUi.BG_ROW : EditorUi.BG_ROW_HOVER);
            g.drawString(font, rowLabel(i), leftPos + EditorUi.GAP + 4, y + 6, EditorUi.PRIMARY);
        }

        drawValue(g, 0, rangeValueX, String.valueOf(cfg.getRange()));
        drawValue(g, 1, blocksValueX, String.valueOf(cfg.getMaxBlocks()));
    }

    private String rowLabel(int row) {
        return Component.translatable("screen.infinitestats.ore_settings.row." + switch (row) {
            case 0 -> "range";
            case 1 -> "blocks";
            case 2 -> "sneak";
            default -> "groups";
        }).getString();
    }

    private void drawValue(GuiGraphics g, int row, int valueX, String text) {
        int y = topPos + LIST_Y + row * ROW_H + 6;
        g.drawCenteredString(font, text, valueX + VALUE_W / 2, y, EditorUi.GOLD);
    }

    private void playClick() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }
}
