package com.infinitestats.client;

import com.infinitestats.network.NetworkHandler;
import com.infinitestats.network.UpdateVeinMinerConfigPacket;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import com.infinitestats.stats.VeinMinerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 「连锁挖掘设置」面板 —— 每个玩家自己的连锁行为的全部旋钮。
 *
 * <h2>怎么改</h2>
 * 编辑的是一份<b>本地副本</b>（{@link VeinMinerConfig#copy()}），只有在点「保存」时才发包提交；
 * 服务端 {@code sanitize()} 夹一遍范围后落库，并回发一份权威设置 —— 客户端以服务端那份为准，
 * 所以面板上看到的数字永远是可用的（不会被服务端夹掉）。
 *
 * <h2>行怎么点</h2>
 * 每行是三段固定列：<b>名称</b> · <b>&lt; 值 &gt;</b>。
 * 点左右箭头切档；开关型两个箭头都是「切换」；「编辑名单…」点哪个都进过滤器界面；
 * 点中间的值区等于「下一档」。底部三个按钮（恢复默认 / 保存 / 关闭）为自绘，
 * 与面板同一套配色 —— 不用原版 {@code Button}，避免它自带的灰底和外框把面板撑花。
 */
public class VeinMinerSettingsScreen extends PanelScreen {

    private static final int GUI_W = 360;
    /** 14 行 × 17 = 238（48..286）+ 底部提示 + 按钮 20 + 边距。 */
    private static final int GUI_H = 328;

    private static final int HEADER_H = 24;
    private static final int HINT_Y = HEADER_H + 4;
    private static final int LIST_Y = HINT_Y + 20;
    private static final int ROW_H = 17;

    private static final int FOOTER_Y = GUI_H - 28;
    private static final int BTN_W = 96;
    private static final int BTN_H = 20;

    /** 三段列：名称 / 左箭头 / 值区 / 右箭头。 */
    private static final int NAME_X = 10;
    private static final int NAME_W = 120;
    private static final int ARROW_L_X = NAME_X + NAME_W + 6;
    private static final int ARROW_W = 14;
    private static final int VALUE_X = ARROW_L_X + ARROW_W + 6;
    private static final int ARROW_R_X = GUI_W - ARROW_W - 10;
    private static final int VALUE_W = ARROW_R_X - VALUE_X - 6;

    private static final int MAX_BLOCKS_STEP = 8;
    private static final int RADIUS_STEP = 1;

    /** 一行设置项：显示值与「上一档 / 下一档」。两个操作都为 null 表示纯展示。 */
    private record Option(String label, String help, Supplier<String> value, Runnable prev, Runnable next) {}

    /** 本地编辑副本；保存时才提交。 */
    private VeinMinerConfig draft = new VeinMinerConfig();
    private final List<Option> options = new ArrayList<>();
    private int hovered = -1;
    /** 悬停的底部按钮：0 恢复默认 / 1 保存 / 2 关闭；-1 无。 */
    private int hoveredButton = -1;

    public VeinMinerSettingsScreen() {
        super(Component.translatable("screen.infinitestats.vein_settings"));
    }

    private static PlayerStats statsOrNull() {
        var player = Minecraft.getInstance().player;
        if (player == null) return null;
        return PlayerStatsProvider.of(player).orElse(null);
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
        VeinMinerConfig current = statsOrNull() == null ? null : statsOrNull().getVeinMinerConfig();
        draft.copyFrom(current == null ? new VeinMinerConfig() : current);
        buildOptions();
    }

    /** 设置项（每次「恢复默认」后重建；值本身是 supplier，重建只是为了保持一致）。 */
    private void buildOptions() {
        options.clear();
        options.add(new Option("match_mode",
                "screen.infinitestats.vein_settings.match_mode.help",
                () -> Component.translatable("screen.infinitestats.vein_settings.match_mode."
                        + draft.getMatchMode().key()).getString(),
                () -> draft.setMatchMode(cycle(draft.getMatchMode(), -1)),
                () -> draft.setMatchMode(cycle(draft.getMatchMode(), 1))));
        options.add(new Option("filter_side",
                "screen.infinitestats.vein_settings.filter_side.help",
                () -> Component.translatable(draft.isUseWhitelist()
                        ? "screen.infinitestats.vein_settings.whitelist"
                        : "screen.infinitestats.vein_settings.blacklist").getString(),
                () -> draft.setUseWhitelist(!draft.isUseWhitelist()),
                () -> draft.setUseWhitelist(!draft.isUseWhitelist())));
        options.add(new Option("filter_edit",
                "screen.infinitestats.vein_settings.filter_edit.help",
                () -> Component.translatable("screen.infinitestats.vein_settings.filter_count",
                        draft.getFilterList().size()).getString(),
                null, this::openFilter));
        options.add(new Option("max_blocks",
                "screen.infinitestats.vein_settings.max_blocks.help",
                () -> draft.getMaxBlocks() <= 0
                        ? Component.translatable("screen.infinitestats.vein_settings.use_server").getString()
                        : String.valueOf(draft.getMaxBlocks()),
                () -> stepMaxBlocks(-1), () -> stepMaxBlocks(1)));
        options.add(new Option("max_radius",
                "screen.infinitestats.vein_settings.max_radius.help",
                () -> draft.getMaxRadius() <= 0
                        ? Component.translatable("screen.infinitestats.vein_settings.unlimited").getString()
                        : String.valueOf(draft.getMaxRadius()),
                () -> stepRadius(-1), () -> stepRadius(1)));
        options.add(new Option("diagonal",
                "screen.infinitestats.vein_settings.diagonal.help",
                () -> Component.translatable(draft.isDiagonal()
                        ? "screen.infinitestats.vein_settings.diagonal_26"
                        : "screen.infinitestats.vein_settings.diagonal_6").getString(),
                () -> draft.setDiagonal(!draft.isDiagonal()),
                () -> draft.setDiagonal(!draft.isDiagonal())));
        options.add(booleanOption("require_tool", draft::isRequireCorrectTool, draft::setRequireCorrectTool));
        options.add(booleanOption("require_sneak", draft::isRequireSneak, draft::setRequireSneak));
        options.add(booleanOption("protect_tool", draft::isProtectTool, draft::setProtectTool));
        options.add(booleanOption("collect_drops", draft::isCollectDrops, draft::setCollectDrops));
        options.add(booleanOption("show_preview", draft::isShowPreview, draft::setShowPreview));
        options.add(booleanOption("right_click_vein", draft::isRightClickVein, draft::setRightClickVein));
        options.add(new Option("exhaustion",
                "screen.infinitestats.vein_settings.exhaustion.help",
                () -> EditorUi.amount(draft.getExhaustionPerBlock()),
                () -> draft.setExhaustionPerBlock(draft.getExhaustionPerBlock() - 0.005D),
                () -> draft.setExhaustionPerBlock(draft.getExhaustionPerBlock() + 0.005D)));
        options.add(new Option("xp_per_block",
                "screen.infinitestats.vein_settings.xp_per_block.help",
                () -> EditorUi.amount(draft.getXpPerBlock()),
                () -> draft.setXpPerBlock(draft.getXpPerBlock() - 0.5D),
                () -> draft.setXpPerBlock(draft.getXpPerBlock() + 0.5D)));
    }

    private Option booleanOption(String key,
            java.util.function.BooleanSupplier getter,
            java.util.function.Consumer<Boolean> setter) {
        return new Option(key,
                "screen.infinitestats.vein_settings." + key + ".help",
                () -> Component.translatable(getter.getAsBoolean()
                        ? "screen.infinitestats.vein_settings.on"
                        : "screen.infinitestats.vein_settings.off").getString(),
                () -> setter.accept(!getter.getAsBoolean()),
                () -> setter.accept(!getter.getAsBoolean()));
    }

    private static VeinMinerConfig.MatchMode cycle(VeinMinerConfig.MatchMode mode, int delta) {
        VeinMinerConfig.MatchMode[] values = VeinMinerConfig.MatchMode.values();
        int index = (mode.ordinal() + delta + values.length) % values.length;
        return values[index];
    }

    /** 方块数档位：0（跟随服务端）→ 8 → 16 … → 256。 */
    private void stepMaxBlocks(int delta) {
        int current = draft.getMaxBlocks();
        if (current <= 0) {
            draft.setMaxBlocks(delta > 0 ? MAX_BLOCKS_STEP : VeinMinerConfig.MAX_BLOCKS_LIMIT);
            return;
        }
        int next = current + delta * MAX_BLOCKS_STEP;
        if (next < MAX_BLOCKS_STEP) {
            draft.setMaxBlocks(0);   // 回到「跟随服务端」
        } else {
            draft.setMaxBlocks(Math.min(next, VeinMinerConfig.MAX_BLOCKS_LIMIT));
        }
    }

    /** 半径档位：0（不限）→ 1 → 2 … → 48。 */
    private void stepRadius(int delta) {
        int current = draft.getMaxRadius();
        if (current <= 0) {
            draft.setMaxRadius(delta > 0 ? RADIUS_STEP : VeinMinerConfig.MAX_RADIUS_LIMIT);
            return;
        }
        int next = current + delta * RADIUS_STEP;
        if (next < RADIUS_STEP) {
            draft.setMaxRadius(0);
        } else {
            draft.setMaxRadius(Math.min(next, VeinMinerConfig.MAX_RADIUS_LIMIT));
        }
    }

    private void openFilter() {
        if (minecraft != null) minecraft.setScreen(new VeinMinerFilterScreen(draft, this));
    }

    /** 保存：把本地副本提交给服务端（服务端夹范围后回发权威版本）。 */
    private void save() {
        NetworkHandler.CHANNEL.sendToServer(new UpdateVeinMinerConfigPacket(draft));
        onClose();
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(new StatsScreen());
    }

    // ======================== 绘制 ========================

    @Override
    protected void renderPanel(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        EditorUi.panel(g, 0, 0, GUI_W, GUI_H);
        EditorUi.title(g, font, 0, 0, GUI_W, title.getString());

        PlayerStats stats = statsOrNull();
        boolean unlocked = stats != null && stats.isToggleActive("vein_miner");
        g.drawString(font, Component.translatable(unlocked
                        ? "screen.infinitestats.vein_settings.hint_on"
                        : "screen.infinitestats.vein_settings.hint_off").getString(),
                EditorUi.GAP, HINT_Y, unlocked ? EditorUi.SECONDARY : EditorUi.DANGER);

        EditorUi.dividerH(g, 4, LIST_Y - 4, GUI_W - 8);

        hovered = -1;
        for (int i = 0; i < options.size(); i++) {
            Option option = options.get(i);
            int rowTop = LIST_Y + i * ROW_H;
            boolean rowHover = mouseX >= 4 && mouseX < GUI_W - 4 && mouseY >= rowTop && mouseY < rowTop + ROW_H - 1;
            if (rowHover) hovered = i;

            g.fill(4, rowTop, GUI_W - 4, rowTop + ROW_H - 1,
                    rowHover ? EditorUi.BG_ROW_HOVER : EditorUi.BG_ROW);

            // 名称列
            String label = Component.translatable(
                    "screen.infinitestats.vein_settings." + option.label()).getString();
            g.drawString(font, EditorUi.ellipsize(font, label, NAME_W), NAME_X, rowTop + 4, EditorUi.PRIMARY);

            // 值区（居中）：长文本先截断，绝不压到箭头
            String value = option.value().get();
            String shown = EditorUi.ellipsize(font, value, VALUE_W);
            int valueColor = optionalDim(option) ? EditorUi.SECONDARY : EditorUi.ACCENT;
            g.drawCenteredString(font, shown, VALUE_X + VALUE_W / 2, rowTop + 4, valueColor);

            // 左右箭头（纯动作行只有右侧）
            if (option.prev() != null) {
                drawArrow(g, ARROW_L_X, rowTop + 1, "<", mouseX, mouseY);
            }
            if (option.next() != null) {
                drawArrow(g, ARROW_R_X, rowTop + 1, ">", mouseX, mouseY);
            }
        }

        drawFooter(g, mouseX, mouseY);

        // 悬停说明
        if (hovered >= 0) {
            Option option = options.get(hovered);
            List<String> lines = new ArrayList<>();
            lines.add("§f" + Component.translatable(
                    "screen.infinitestats.vein_settings." + option.label()).getString());
            for (String line : EditorUi.wrap(font, "§7"
                    + Component.translatable(option.help()).getString(), 200)) {
                lines.add(line);
            }
            EditorUi.tooltip(g, mouseX, mouseY, lines, EditorUi.ACCENT);
        }
    }

    /** 底部三个按钮：等宽、等间距，自绘（不用原版 Button 的灰底）。 */
    private void drawFooter(GuiGraphics g, int mouseX, int mouseY) {
        hoveredButton = -1;
        int gap = (GUI_W - BTN_W * 3 - 20) / 2;
        int x0 = 10;
        int x1 = x0 + BTN_W + gap;
        int x2 = x1 + BTN_W + gap;

        if (drawFooterButton(g, x0, FOOTER_Y, "screen.infinitestats.vein_settings.reset",
                EditorUi.SECONDARY, mouseX, mouseY)) hoveredButton = 0;
        if (drawFooterButton(g, x1, FOOTER_Y, "screen.infinitestats.vein_settings.save",
                EditorUi.ACCENT, mouseX, mouseY)) hoveredButton = 1;
        if (drawFooterButton(g, x2, FOOTER_Y, "screen.infinitestats.vein_settings.close",
                EditorUi.PRIMARY, mouseX, mouseY)) hoveredButton = 2;

        // 底部说明：保存前不会生效
        g.drawCenteredString(font,
                Component.translatable("screen.infinitestats.vein_settings.footer_hint").getString(),
                GUI_W / 2, FOOTER_Y - 13, EditorUi.SECONDARY);
    }

    /** @return 是否悬停在该按钮上 */
    private boolean drawFooterButton(GuiGraphics g, int x, int y, String labelKey, int color,
                                     int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX < x + BTN_W && mouseY >= y && mouseY < y + BTN_H;
        int bg = hover ? 0xFF3A3A52 : 0xFF23233A;
        g.fill(x, y, x + BTN_W, y + BTN_H, bg);
        g.fill(x, y, x + BTN_W, y + 1, hover ? color : 0x60FFFFFF);
        g.fill(x, y + BTN_H - 1, x + BTN_W, y + BTN_H, hover ? color : 0x30000000);
        g.drawCenteredString(font, Component.translatable(labelKey).getString(),
                x + BTN_W / 2, y + (BTN_H - font.lineHeight) / 2 + 1, hover ? color : EditorUi.PRIMARY);
        return hover;
    }

    /** 当前值是否处于「不生效」状态（非自定义名单模式下的名单行等）。 */
    private boolean optionalDim(Option option) {
        if (option.label().equals("filter_side") || option.label().equals("filter_edit")) {
            return draft.getMatchMode() != VeinMinerConfig.MatchMode.FILTER_LIST;
        }
        return false;
    }

    private void drawArrow(GuiGraphics g, int x, int y, String glyph, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX < x + ARROW_W && mouseY >= y && mouseY < y + ROW_H - 3;
        g.fill(x, y, x + ARROW_W, y + ROW_H - 3, hover ? 0x60FFFFFF : 0x30FFFFFF);
        g.drawCenteredString(font, glyph, x + ARROW_W / 2, y + (ROW_H - 3 - font.lineHeight) / 2 + 1,
                EditorUi.PRIMARY);
    }

    @Override
    protected boolean onPanelClick(int mouseX, int mouseY, int button) {
        // 底部按钮优先
        int gap = (GUI_W - BTN_W * 3 - 20) / 2;
        int x0 = 10, x1 = x0 + BTN_W + gap, x2 = x1 + BTN_W + gap;
        if (mouseY >= FOOTER_Y && mouseY < FOOTER_Y + BTN_H) {
            if (mouseX >= x0 && mouseX < x0 + BTN_W) {
                draft.resetToDefault();   // 值都是 supplier，重建不是必须的，但保持一致
                buildOptions();
                return true;
            }
            if (mouseX >= x1 && mouseX < x1 + BTN_W) {
                save();
                return true;
            }
            if (mouseX >= x2 && mouseX < x2 + BTN_W) {
                onClose();
                return true;
            }
        }

        if (mouseX >= 0 && mouseX < GUI_W && mouseY >= LIST_Y && mouseY < LIST_Y + options.size() * ROW_H) {
            int index = (mouseY - LIST_Y) / ROW_H;
            if (index >= 0 && index < options.size()) {
                Option option = options.get(index);
                boolean onLeftArrow = option.prev() != null
                        && mouseX >= ARROW_L_X && mouseX < ARROW_L_X + ARROW_W;
                boolean onRightArrow = option.next() != null
                        && mouseX >= ARROW_R_X && mouseX < ARROW_R_X + ARROW_W;
                if (onLeftArrow) {
                    option.prev().run();
                } else if (onRightArrow) {
                    option.next().run();
                } else if (option.prev() == null && option.next() != null) {
                    option.next().run();    // 纯动作行（编辑名单）
                } else if (option.next() != null) {
                    option.next().run();    // 点值区 = 下一档
                }
                return true;
            }
        }
        return false;
    }
}
