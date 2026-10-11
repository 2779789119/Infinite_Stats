package com.infinitestats.client;

import com.infinitestats.Config;
import com.infinitestats.network.NetworkHandler;
import com.infinitestats.network.UpdateVeinMinerConfigPacket;
import com.infinitestats.stats.PlayerStatsProvider;
import com.infinitestats.stats.VeinMinerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * 「连锁挖掘」设置面板 —— 把连锁的所有可调项集中在一页，点一下就能改。
 * <p>
 * 左列是项目名，右列是控件：开关 / 多选循环 / 数值步进（{@code -} 与 {@code +} 之间显示当前值）。
 * 数值都走离散档位（8 / 16 / 32…），比拖条更适合「一眼看出当前档」的场景，也不会出现 37 这种怪数字。
 * <p>
 * 改动即时提交（{@link UpdateVeinMinerConfigPacket}）：服务端 `sanitize()` 后落库并回推，
 * 本地同时先改一份，界面不用等服务端回包。视觉沿用 {@link EditorUi} 规范。
 */
public class VeinMinerSettingsScreen extends Screen {

    // ======================== 布局 ========================

    private static final int GUI_W = 340;
    private static final int ROW_H = EditorUi.ROW_H;
    private static final int ROWS = 13;
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

    private static final int[] BLOCK_STEPS = {-1, 8, 16, 24, 32, 48, 64, 96, 128, 192, 256};
    private static final int[] RADIUS_STEPS = {-1, 3, 5, 8, 12, 16, 24, 32, 48};
    private static final double[] HUNGER_STEPS = {0.0D, 0.005D, 0.01D, 0.02D, 0.05D, 0.1D, 0.2D, 0.5D};
    private static final double[] XP_STEPS = {0.0D, 0.5D, 1.0D, 2.0D, 5.0D, 10.0D};

    // ======================== 状态 ========================

    private final Screen parent;
    private final VeinMinerConfig cfg = new VeinMinerConfig();

    private int leftPos, topPos;

    private Button matchBtn;
    private Button diagonalBtn;
    private Button correctToolBtn;
    private Button sneakBtn;
    private Button protectBtn;
    private Button collectBtn;
    private Button previewBtn;
    private Button rightClickBtn;
    private Button listBtn;

    /** 数值行的档位与读写器（渲染时用它们画当前值） */
    private int blockValueX, radiusValueX, hungerValueX, xpValueX;

    public VeinMinerSettingsScreen(Screen parent) {
        super(Component.translatable("screen.infinitestats.vein_settings"));
        this.parent = parent;
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    protected void init() {
        super.init();
        leftPos = Math.max(0, (width - GUI_W) / 2);
        topPos = Math.max(0, (height - GUI_H) / 2);

        loadFromPlayer();
        buildWidgets();
    }

    /** 从本地（已同步的）玩家数据取一份副本编辑，避免改到共享对象。 */
    private void loadFromPlayer() {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        player.getCapability(PlayerStatsProvider.PLAYER_STATS)
                .ifPresent(stats -> copyInto(cfg, stats.getVeinMinerConfig()));
    }

    private static void copyInto(VeinMinerConfig target, VeinMinerConfig source) {
        if (source == null) return;
        target.setMatchMode(source.getMatchMode());
        target.setUseWhitelist(source.isUseWhitelist());
        target.setFilterList(source.getFilterList());
        target.setMaxBlocks(source.getMaxBlocks());
        target.setMaxRadius(source.getMaxRadius());
        target.setDiagonal(source.isDiagonal());
        target.setRequireCorrectTool(source.isRequireCorrectTool());
        target.setRequireSneak(source.isRequireSneak());
        target.setProtectTool(source.isProtectTool());
        target.setCollectDrops(source.isCollectDrops());
        target.setShowPreview(source.isShowPreview());
        target.setRightClickVein(source.isRightClickVein());
        target.setExhaustionPerBlock(source.getExhaustionPerBlock());
        target.setXpPerBlock(source.getXpPerBlock());
    }

    // ======================== 控件 ========================

    private void buildWidgets() {
        int y0 = topPos + LIST_Y;

        // 0 匹配方式
        matchBtn = addRenderableWidget(simpleButton(rowX(), y0, SWITCH_W, this::cycleMatchMode));

        // 1 单次方块数
        blockValueX = addIntStepper(y0 + ROW_H, BLOCK_STEPS, cfg::getMaxBlocks, cfg::setMaxBlocks);
        // 2 搜索半径
        radiusValueX = addIntStepper(y0 + ROW_H * 2, RADIUS_STEPS, cfg::getMaxRadius, cfg::setMaxRadius);

        // 3 斜向连锁
        diagonalBtn = addRenderableWidget(simpleButton(rowX(), y0 + ROW_H * 3, SWITCH_W, () -> {
            cfg.setDiagonal(!cfg.isDiagonal());
            refreshLabels();
            submit();
        }));

        // 4 需正确工具
        correctToolBtn = addRenderableWidget(simpleButton(rowX(), y0 + ROW_H * 4, SWITCH_W, () -> {
            cfg.setRequireCorrectTool(!cfg.isRequireCorrectTool());
            refreshLabels();
            submit();
        }));

        // 5 需潜行激活
        sneakBtn = addRenderableWidget(simpleButton(rowX(), y0 + ROW_H * 5, SWITCH_W, () -> {
            cfg.setRequireSneak(!cfg.isRequireSneak());
            refreshLabels();
            submit();
        }));

        // 6 工具保护
        protectBtn = addRenderableWidget(simpleButton(rowX(), y0 + ROW_H * 6, SWITCH_W, () -> {
            cfg.setProtectTool(!cfg.isProtectTool());
            refreshLabels();
            submit();
        }));

        // 7 掉落直接进背包
        collectBtn = addRenderableWidget(simpleButton(rowX(), y0 + ROW_H * 7, SWITCH_W, () -> {
            cfg.setCollectDrops(!cfg.isCollectDrops());
            refreshLabels();
            submit();
        }));

        // 8 范围预览
        previewBtn = addRenderableWidget(simpleButton(rowX(), y0 + ROW_H * 8, SWITCH_W, () -> {
            cfg.setShowPreview(!cfg.isShowPreview());
            refreshLabels();
            submit();
        }));

        // 9 饥饿消耗
        hungerValueX = addDoubleStepper(y0 + ROW_H * 9, HUNGER_STEPS,
                cfg::getExhaustionPerBlock, cfg::setExhaustionPerBlock);
        // 10 经验消耗
        xpValueX = addDoubleStepper(y0 + ROW_H * 10, XP_STEPS, cfg::getXpPerBlock, cfg::setXpPerBlock);

        // 11 右键整片
        rightClickBtn = addRenderableWidget(simpleButton(rowX(), y0 + ROW_H * 11, SWITCH_W, () -> {
            cfg.setRightClickVein(!cfg.isRightClickVein());
            refreshLabels();
            submit();
        }));

        // 12 自定义名单
        listBtn = addRenderableWidget(simpleButton(rowX(), y0 + ROW_H * 12, SWITCH_W, this::openFilter));

        // 页脚
        addRenderableWidget(Button.builder(
                        Component.translatable("screen.infinitestats.vein_settings.reset"),
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
            setter.accept(shiftInt(steps, getter.getAsInt(), -1));
            playClick();
            refreshLabels();
            submit();
        }).bounds(minusX, y + 3, STEP_W, BTN_H).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            setter.accept(shiftInt(steps, getter.getAsInt(), 1));
            playClick();
            refreshLabels();
            submit();
        }).bounds(plusX, y + 3, STEP_W, BTN_H).build());
        return minusX + STEP_W + 2;
    }

    private int addDoubleStepper(int y, double[] steps, DoubleSupplier getter, DoubleConsumer setter) {
        int plusX = leftPos + GUI_W - EditorUi.GAP - STEP_W;
        int minusX = plusX - VALUE_W - 4 - STEP_W;
        addRenderableWidget(Button.builder(Component.literal("-"), b -> {
            setter.accept(shiftDouble(steps, getter.getAsDouble(), -1));
            playClick();
            refreshLabels();
            submit();
        }).bounds(minusX, y + 3, STEP_W, BTN_H).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            setter.accept(shiftDouble(steps, getter.getAsDouble(), 1));
            playClick();
            refreshLabels();
            submit();
        }).bounds(plusX, y + 3, STEP_W, BTN_H).build());
        return minusX + STEP_W + 2;
    }

    /** 在档位表里往前 / 往后挪一格（当前值不在表里时取最接近的一格）。 */
    private static int shiftInt(int[] steps, int current, int delta) {
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

    private static double shiftDouble(double[] steps, double current, int delta) {
        int index = 0;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < steps.length; i++) {
            double diff = Math.abs(steps[i] - current);
            if (diff < best) {
                best = diff;
                index = i;
            }
        }
        int next = Math.max(0, Math.min(steps.length - 1, index + delta));
        return steps[next];
    }

    // ======================== 行为 ========================

    private void cycleMatchMode() {
        cfg.setMatchMode(switch (cfg.getMatchMode()) {
            case AUTO -> VeinMinerConfig.MatchMode.TAG_GROUP;
            case TAG_GROUP -> VeinMinerConfig.MatchMode.FILTER_LIST;
            case FILTER_LIST -> VeinMinerConfig.MatchMode.SAME_BLOCK;
            case SAME_BLOCK -> VeinMinerConfig.MatchMode.AUTO;
        });
        refreshLabels();
        submit();
    }

    private void openFilter() {
        if (minecraft == null) return;
        minecraft.setScreen(new VeinMinerFilterScreen(this));
    }

    /** 提交给服务端，并同步改一份本地数据（界面立即反映结果，不必等服务端回包）。 */
    private void submit() {
        NetworkHandler.CHANNEL.sendToServer(new UpdateVeinMinerConfigPacket(cfg));
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.getCapability(PlayerStatsProvider.PLAYER_STATS)
                    .ifPresent(stats -> stats.setVeinMinerConfig(cfg.copy()));
        }
    }

    /** 刷新所有按钮上的文字（数值行由 render 直接画，不走按钮文本）。 */
    private void refreshLabels() {
        if (matchBtn != null) {
            matchBtn.setMessage(Component.translatable(
                    "screen.infinitestats.vein_settings.match." + cfg.getMatchMode().key()));
        }
        setToggle(diagonalBtn, cfg.isDiagonal());
        setToggle(correctToolBtn, cfg.isRequireCorrectTool());
        setToggle(sneakBtn, cfg.isRequireSneak());
        setToggle(protectBtn, cfg.isProtectTool());
        setToggle(collectBtn, cfg.isCollectDrops());
        setToggle(previewBtn, cfg.isShowPreview());
        setToggle(rightClickBtn, cfg.isRightClickVein());
        if (listBtn != null) {
            listBtn.setMessage(Component.translatable(
                    cfg.getMatchMode() == VeinMinerConfig.MatchMode.FILTER_LIST
                            ? "screen.infinitestats.vein_settings.edit_list"
                            : "screen.infinitestats.vein_settings.list_locked"));
            listBtn.active = cfg.getMatchMode() == VeinMinerConfig.MatchMode.FILTER_LIST;
        }
    }

    private static void setToggle(Button button, boolean on) {
        if (button == null) return;
        button.setMessage(Component.translatable(on
                ? "screen.infinitestats.vein_settings.on"
                : "screen.infinitestats.vein_settings.off"));
    }

    // ======================== 绘制 ========================

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        EditorUi.panel(g, leftPos, topPos, GUI_W, GUI_H);
        EditorUi.title(g, font, leftPos, topPos, GUI_W, title.getString());

        g.drawString(font,
                Component.translatable("screen.infinitestats.vein_settings.hint",
                        Config.VEIN_MINER_MAX_BLOCKS.get()).getString(),
                leftPos + EditorUi.GAP, topPos + HINT_Y, EditorUi.SECONDARY);

        for (int i = 0; i < ROWS; i++) {
            int y = topPos + LIST_Y + i * ROW_H;
            g.fill(leftPos + EditorUi.GAP - 2, y, leftPos + GUI_W - EditorUi.GAP + 2, y + ROW_H - 2,
                    (i % 2 == 0) ? EditorUi.BG_ROW : EditorUi.BG_ROW_HOVER);
            g.drawString(font, rowLabel(i), leftPos + EditorUi.GAP + 4, y + 6,
                    i == 12 && cfg.getMatchMode() != VeinMinerConfig.MatchMode.FILTER_LIST
                            ? EditorUi.SECONDARY : EditorUi.PRIMARY);
        }

        drawValue(g, 1, blockValueX, blockLabel());
        drawValue(g, 2, radiusValueX, radiusLabel());
        drawValue(g, 9, hungerValueX, trim(cfg.getExhaustionPerBlock()));
        drawValue(g, 10, xpValueX, trim(cfg.getXpPerBlock()));

        super.render(g, mx, my, pt);
    }

    private String rowLabel(int row) {
        return Component.translatable("screen.infinitestats.vein_settings.row." + switch (row) {
            case 0 -> "match";
            case 1 -> "blocks";
            case 2 -> "radius";
            case 3 -> "diagonal";
            case 4 -> "correct_tool";
            case 5 -> "sneak";
            case 6 -> "protect_tool";
            case 7 -> "collect";
            case 8 -> "preview";
            case 9 -> "hunger";
            case 10 -> "xp";
            case 11 -> "right_click";
            default -> "list";
        }).getString();
    }

    private void drawValue(GuiGraphics g, int row, int valueX, String text) {
        int y = topPos + LIST_Y + row * ROW_H + 6;
        g.drawCenteredString(font, text, valueX + VALUE_W / 2, y, EditorUi.GOLD);
    }

    private String blockLabel() {
        int value = cfg.getMaxBlocks();
        if (value <= 0) {
            return Component.translatable("screen.infinitestats.vein_settings.value.server").getString();
        }
        return String.valueOf(value);
    }

    private String radiusLabel() {
        int value = cfg.getMaxRadius();
        if (value <= 0) {
            return Component.translatable("screen.infinitestats.vein_settings.value.unlimited").getString();
        }
        return String.valueOf(value);
    }

    private static String trim(double value) {
        if (value <= 0.0D) return "0";
        if (value == Math.floor(value)) return String.valueOf((int) value);
        return String.format(java.util.Locale.ROOT, "%.3f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private void playClick() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }
}
