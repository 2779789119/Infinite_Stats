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
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * 「矿透 · 矿物种类」界面 —— 逐类决定哪些矿石要描边。
 * <p>
 * 关掉的分组不只是不画，连扫描阶段都会跳过（{@code isGroupHidden} 在收集时就过滤掉了），
 * 所以在成片矿区里「只看钻石」能实打实省下一截开销，而不只是视觉上的清爽。
 * <p>
 * 按钮底色就是该类矿物的线框颜色，隐藏时整体压暗 —— 不必对比图例也知道关了哪几种。
 */
public class OreVisionGroupsScreen extends PanelScreen {

    // ======================== 布局 ========================

    private static final int GUI_W = 320;
    private static final int COLS = 2;
    private static final int COL_GAP = 8;
    private static final int COL_W = (GUI_W - EditorUi.GAP * 2 - COL_GAP) / COLS;
    private static final int ROW_H = 22;
    private static final int BTN_H = 18;
    private static final int HINT_Y = EditorUi.HEADER_H + 4;
    private static final int LIST_Y = HINT_Y + 20;
    private static final int ROWS = 6;
    private static final int FOOTER_H = 20;
    private static final int FOOTER_Y = LIST_Y + ROWS * ROW_H + 6;
    private static final int GUI_H = FOOTER_Y + FOOTER_H + EditorUi.GAP;

    // ======================== 状态 ========================

    private final Screen parent;
    private final OreVisionConfig cfg = new OreVisionConfig();

    public OreVisionGroupsScreen(Screen parent) {
        super(Component.translatable("screen.infinitestats.ore_groups"));
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

    private void loadFromPlayer() {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
            OreVisionConfig source = stats.getOreVisionConfig();
            if (source == null) return;
            cfg.setRange(source.getRange());
            cfg.setMaxBlocks(source.getMaxBlocks());
            cfg.setRequireSneak(source.isRequireSneak());
            for (OreVisionConfig.OreGroup group : OreVisionConfig.groups()) {
                cfg.setGroupHidden(group, source.isGroupHidden(group));
            }
        });
    }

    private void buildWidgets() {
        var groups = OreVisionConfig.groups();
        for (int i = 0; i < groups.size(); i++) {
            OreVisionConfig.OreGroup group = groups.get(i);
            int col = i / ROWS;
            int row = i % ROWS;
            int x = leftPos + EditorUi.GAP + col * (COL_W + COL_GAP);
            int y = topPos + LIST_Y + row * ROW_H;
            addRenderableWidget(new GroupButton(x, y, COL_W, BTN_H, group, b -> {
                cfg.setGroupHidden(group, !cfg.isGroupHidden(group));
                playClick();
                submit();
            }));
        }

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.infinitestats.ore_groups.show_all"),
                        b -> {
                            for (OreVisionConfig.OreGroup group : OreVisionConfig.groups()) {
                                cfg.setGroupHidden(group, false);
                            }
                            playClick();
                            submit();
                        })
                .bounds(leftPos + EditorUi.GAP, topPos + FOOTER_Y, 80, FOOTER_H).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("screen.infinitestats.deposit_filter.done"),
                        b -> onClose())
                .bounds(leftPos + GUI_W - EditorUi.GAP - 64, topPos + FOOTER_Y, 64, FOOTER_H).build());
    }

    /** 提交给服务端，并同步改一份本地数据（按钮状态立刻反映结果）。 */
    private void submit() {
        NetworkHandler.CHANNEL.sendToServer(new UpdateOreVisionConfigPacket(cfg));
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.getCapability(PlayerStatsProvider.PLAYER_STATS)
                    .ifPresent(stats -> stats.setOreVisionConfig(cfg.copy()));
        }
    }

    // ======================== 绘制 ========================

    @Override
    protected void renderPanel(GuiGraphics g, int mx, int my, float pt) {
        EditorUi.panel(g, leftPos, topPos, GUI_W, GUI_H);
        EditorUi.title(g, font, leftPos, topPos, GUI_W, title.getString());
        g.drawString(font, Component.translatable("screen.infinitestats.ore_groups.hint").getString(),
                leftPos + EditorUi.GAP, topPos + HINT_Y, EditorUi.SECONDARY);
    }

    private void playClick() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    /** 一种矿物一个按钮：底色 = 该类的线框颜色，隐藏时压暗。 */
    private final class GroupButton extends Button {

        private final OreVisionConfig.OreGroup group;

        private GroupButton(int x, int y, int w, int h, OreVisionConfig.OreGroup group, OnPress onPress) {
            super(x, y, w, h, Component.empty(), onPress, DEFAULT_NARRATION);
            this.group = group;
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            boolean hidden = cfg.isGroupHidden(group);
            int accent = accentColor(group);
            int bg;
            if (hidden) {
                bg = isHoveredOrFocused() ? 0x80353550 : 0x50252535;
            } else {
                bg = isHoveredOrFocused() ? 0x90404058 : 0x60303040;
            }
            g.fill(getX(), getY(), getX() + width, getY() + height, bg);
            g.fill(getX(), getY(), getX() + 3, getY() + height, hidden ? withAlpha(accent, 0x40) : accent);

            Component label = Component.translatable("screen.infinitestats.ore_settings.group." + group.key())
                    .append(Component.literal(" · "))
                    .append(Component.translatable(hidden
                            ? "screen.infinitestats.ore_settings.state.hidden"
                            : "screen.infinitestats.ore_settings.state.shown"));
            g.drawString(Minecraft.getInstance().font, label, getX() + 8,
                    getY() + (height - Minecraft.getInstance().font.lineHeight) / 2 + 1,
                    hidden ? EditorUi.SECONDARY : EditorUi.PRIMARY);
        }

        @Override
        public void playDownSound(SoundManager handler) {
            // 音效统一在提交时播一次，避免连点叠出多重音
        }

        private static int accentColor(OreVisionConfig.OreGroup group) {
            float[] c = group.color();
            return 0xFF000000
                    | ((int) (c[0] * 255F) << 16)
                    | ((int) (c[1] * 255F) << 8)
                    | (int) (c[2] * 255F);
        }

        private static int withAlpha(int argb, int alpha) {
            return (alpha << 24) | (argb & 0x00FFFFFF);
        }
    }
}
