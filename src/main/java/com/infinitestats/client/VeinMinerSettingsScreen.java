package com.infinitestats.client;

import com.infinitestats.network.NetworkHandler;
import com.infinitestats.network.UpdateVeinMinerConfigPacket;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import com.infinitestats.stats.VeinMinerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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
 * 每行中间是当前值，两侧是 {@code <} / {@code >}：点箭头切换选中项。
 * 开关型的行两个箭头都是「切换」，点哪边都一样；「编辑名单…」行点中间进过滤器界面。
 *
 * <h2>1.21.1 说明</h2>
 * 按本分支 {@link Screen} + {@link EditorUi} 风格实现（主线那版依赖自适应的 {@code PanelScreen} 缩放基类，
 * 属后续「界面自适应」批次），选项与默认值全部对齐主线。
 */
public class VeinMinerSettingsScreen extends PanelScreen {

    private static final int GUI_W = 340;
    private static final int GUI_H = 306;

    private static final int HEADER_H = 24;
    private static final int HINT_Y = HEADER_H + 4;
    private static final int LIST_Y = HINT_Y + 20;
    private static final int ROW_H = 18;
    private static final int FOOTER_Y = GUI_H - 26;

    /** 行内：名称列宽 / 值区（左右箭头 + 中间值）。 */
    private static final int NAME_W = 132;
    private static final int VALUE_LEFT = NAME_W + 12;
    private static final int VALUE_RIGHT = GUI_W - 12;

    private static final int MAX_BLOCKS_STEP = 8;
    private static final int RADIUS_STEP = 1;

    /** 一行设置项：显示值与「上一档 / 下一档」。两个操作都为 null 表示纯展示。 */
    private record Option(String label, String help, Supplier<String> value, Runnable prev, Runnable next) {}

    private int leftPos;
    private int topPos;

    /** 本地编辑副本；保存时才提交。 */
    private VeinMinerConfig draft = new VeinMinerConfig();
    private final List<Option> options = new ArrayList<>();
    private int hovered = -1;

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
        // 面板坐标从 (0,0) 起：居中、缩放与拖动都由 PanelScreen 负责
        leftPos = 0;
        topPos = 0;

        VeinMinerConfig current = statsOrNull() == null ? null : statsOrNull().getVeinMinerConfig();
        draft.copyFrom(current == null ? new VeinMinerConfig() : current);

        buildOptions();

        int y = topPos + FOOTER_Y;
        addRenderableWidget(Button.builder(
                        Component.translatable("screen.infinitestats.vein_settings.reset"),
                        b -> {
                            draft.resetToDefault();
                            buildOptions();
                        })
                .bounds(leftPos + 8, y, 90, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("screen.infinitestats.vein_settings.save"),
                        b -> save())
                .bounds(leftPos + GUI_W / 2 - 45, y, 90, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("screen.infinitestats.vein_settings.close"),
                        b -> onClose())
                .bounds(leftPos + GUI_W - 98, y, 90, 20).build());
    }

    /** 设置项（每次「恢复默认」后重建，让显示值跟着变）。 */
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

        EditorUi.panel(g, leftPos, topPos, GUI_W, GUI_H);
        EditorUi.title(g, font, leftPos, topPos, GUI_W, title.getString());

        PlayerStats stats = statsOrNull();
        boolean unlocked = stats != null && stats.isToggleActive("vein_miner");
        g.drawString(font, Component.translatable(unlocked
                        ? "screen.infinitestats.vein_settings.hint_on"
                        : "screen.infinitestats.vein_settings.hint_off").getString(),
                leftPos + EditorUi.GAP, topPos + HINT_Y,
                unlocked ? EditorUi.SECONDARY : EditorUi.DANGER);
        EditorUi.dividerH(g, leftPos + 4, topPos + LIST_Y - 4, GUI_W - 8);

        hovered = -1;
        int mx = (int) mouseX - leftPos;
        int my = (int) mouseY - topPos;

        for (int i = 0; i < options.size(); i++) {
            Option option = options.get(i);
            int rowTop = LIST_Y + i * ROW_H;
            boolean hover = mx >= 0 && mx < GUI_W && my >= rowTop && my < rowTop + ROW_H - 1;
            if (hover) hovered = i;

            g.fill(leftPos + 4, topPos + rowTop, leftPos + GUI_W - 4, topPos + rowTop + ROW_H - 1,
                    hover ? EditorUi.BG_ROW_HOVER : EditorUi.BG_ROW);
            g.drawString(font, Component.translatable(
                            "screen.infinitestats.vein_settings." + option.label()).getString(),
                    leftPos + 8, topPos + rowTop + 5, EditorUi.PRIMARY);

            String value = option.value().get();
            int valueColor = optionalDim(option) ? EditorUi.SECONDARY : EditorUi.ACCENT;
            g.drawString(font, value, leftPos + VALUE_LEFT, topPos + rowTop + 5, valueColor);

            if (option.prev() != null) {
                drawArrow(g, VALUE_LEFT + 76, rowTop + 1, "<", mx, my, true);
            }
            if (option.next() != null) {
                drawArrow(g, VALUE_RIGHT - 12, rowTop + 1, ">", mx, my, true);
            }
        }

        // 悬停说明
        if (hovered >= 0) {
            Option option = options.get(hovered);
            List<String> lines = new ArrayList<>();
            lines.add("§f" + Component.translatable(
                    "screen.infinitestats.vein_settings." + option.label()).getString());
            for (String line : EditorUi.wrap(font, "§7"
                    + Component.translatable(option.help()).getString(), 190)) {
                lines.add(line);
            }
            EditorUi.tooltip(g, mouseX, mouseY, lines, EditorUi.ACCENT);
        }

    }

    /** 当前值是否处于「不生效」状态（非自定义名单模式下的名单行等）。 */
    private boolean optionalDim(Option option) {
        if (option.label().equals("filter_side") || option.label().equals("filter_edit")) {
            return draft.getMatchMode() != VeinMinerConfig.MatchMode.FILTER_LIST;
        }
        return false;
    }

    private void drawArrow(GuiGraphics g, int x, int y, String glyph, int mx, int my, boolean active) {
        boolean hover = active && mx >= x && mx < x + 12 && my >= y && my < y + 16;
        g.fill(leftPos + x, topPos + y, leftPos + x + 12, topPos + y + 16,
                hover ? 0x60FFFFFF : 0x30FFFFFF);
        g.drawString(font, glyph, leftPos + x + 3, topPos + y + 4,
                active ? EditorUi.PRIMARY : EditorUi.SECONDARY);
    }

    @Override
    protected boolean onPanelClick(int mouseX, int mouseY, int button) {
        int mx = mouseX - leftPos;
        int my = mouseY - topPos;
        if (mx >= 0 && mx < GUI_W && my >= LIST_Y && my < LIST_Y + options.size() * ROW_H) {
            int index = (my - LIST_Y) / ROW_H;
            if (index >= 0 && index < options.size()) {
                Option option = options.get(index);
                boolean onLeftArrow = option.prev() != null && mx >= VALUE_LEFT + 76 && mx < VALUE_LEFT + 88;
                boolean onRightArrow = option.next() != null && mx >= VALUE_RIGHT - 12 && mx < VALUE_RIGHT;
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
        return false;   // 未命中时交回基类，继续派发给控件（按钮 / 输入框）
    }
}
