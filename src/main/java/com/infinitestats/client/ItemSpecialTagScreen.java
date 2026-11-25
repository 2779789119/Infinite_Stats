package com.infinitestats.client;

import com.infinitestats.network.EditItemTagsPacket;
import com.infinitestats.network.NetworkHandler;
import com.infinitestats.util.ItemEditUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 特殊标签编辑界面 —— 在物品编辑器里改那些「平时只能用命令改」的数据组件。
 * <p>
 * 分两块：
 * <ul>
 *   <li><b>特殊开关</b>：无法破坏（{@code UNBREAKABLE}）、隐藏全部提示（{@code HIDE_TOOLTIP}）、
 *       隐藏附加信息（{@code HIDE_ADDITIONAL_TOOLTIP}），每行一个勾选框；</li>
 *   <li><b>数值标签</b>：自定义模型数据 / 染料颜色 / 药水颜色 / 铁砧累计惩罚，
 *       每个输入框留空 = 从物品上移除该组件。</li>
 * </ul>
 * 只发真正被改过的字段（其余不写进包），未改动的组件不会被重写。
 * <p>
 * <b>与 1.20.1 主线的差异</b>：主线用 {@code HideFlags} 位掩码逐项隐藏（附魔 / 属性 / 染料 / 升级），
 * 1.21.1 只有「整条提示」与「附加信息」两个整体开关（逐项隐藏要等 1.21.2 的 TooltipDisplay），
 * 所以这里只保留这两个。视觉规范复用 {@link EditorUi}，布局按「面板坐标」写，缩放交给 {@link PanelScreen}。
 */
public class ItemSpecialTagScreen extends PanelScreen {

    // ======================== 布局常量 ========================

    private static final int GUI_W = 420;

    private static final int ITEM_BAR_Y = EditorUi.HEADER_H;                  // 24
    private static final int ITEM_BAR_H = 34;

    private static final int NOTE_Y = ITEM_BAR_Y + ITEM_BAR_H + 4;            // 62
    private static final int TOGGLE_SECTION_Y = NOTE_Y + 12;                  // 74
    private static final int TOGGLE_Y = TOGGLE_SECTION_Y + 16;                // 90
    private static final int TOGGLE_ROW_H = 22;
    private static final int TOGGLE_ROWS = 3;
    private static final int TOGGLE_X = 8;
    private static final int TOGGLE_W = 404;

    private static final int NUM_SECTION_Y = TOGGLE_Y + TOGGLE_ROWS * TOGGLE_ROW_H + 6;  // 162
    private static final int BOX_H = 18;
    private static final int NUM_X = 116;
    private static final int NUM_W = 150;
    private static final int CM_Y = NUM_SECTION_Y + 14;                       // 176
    private static final int DYE_Y = CM_Y + 24;                               // 200
    private static final int SWATCH_Y = DYE_Y + BOX_H + 2;                    // 220
    private static final int SWATCH_SZ = 14;
    private static final int SWATCH_GAP = 2;
    private static final int POTION_Y = SWATCH_Y + SWATCH_SZ + 6;             // 240
    private static final int REPAIR_Y = POTION_Y + 24;                        // 264

    private static final int FOOTER_H = 20;
    private static final int FOOTER_Y = REPAIR_Y + BOX_H + 8;                 // 290
    private static final int GUI_H = FOOTER_Y + FOOTER_H + EditorUi.GAP;      // 318

    /** 三个开关的行序号 → 语义编码（0 = 无法破坏，1 = 隐藏全部提示，2 = 隐藏附加信息）。 */
    private static final int[] TOGGLE_CODES = {0, 1, 2};
    /** 开关标签（下标 = 编码）。 */
    private static final String[] TOGGLE_KEYS = {
            "screen.infinitestats.item_editor.tag.unbreakable",
            "screen.infinitestats.item_editor.tag.hide_tooltip",
            "screen.infinitestats.item_editor.tag.hide_additional",
    };
    /** 开关说明（悬停浮窗用，下标 = 编码）。 */
    private static final String[] TOGGLE_HINT_KEYS = {
            "screen.infinitestats.item_editor.tag.unbreakable.hint",
            "screen.infinitestats.item_editor.tag.hide_tooltip.hint",
            "screen.infinitestats.item_editor.tag.hide_additional.hint",
    };

    // ======================== 状态 ========================

    private final ItemEditorScreen parent;

    @Override
    protected int panelW() {
        return GUI_W;
    }

    @Override
    protected int panelH() {
        return GUI_H;
    }

    private boolean unbreakable;
    private boolean hideTooltip;
    private boolean hideAdditional;
    private boolean origUnbreakable;
    private boolean origHideTooltip;
    private boolean origHideAdditional;

    private String origCmm = "",
            origDye = "",
            origPotion = "",
            origRepair = "";

    private EditBox cmmBox, dyeBox, potionBox, repairBox;

    /** 每帧重建的命中区：{x, y, w, h, code} / {x, y, size, rgb}。 */
    private final List<int[]> toggleRects = new ArrayList<>();
    private final List<int[]> swatchRects = new ArrayList<>();
    private int hoverCode = -1;
    private Integer hoverSwatch;

    private String statusMsg = "";
    private long statusUntil;
    private long lastSoundTick;

    /**
     * 「暂停放行」的截止时间。
     * <p>
     * 面板是暂停界面：单人游戏里服务端不 tick，我们发出去的数据包就躺在队列里，
     * 点了「应用」当场没反应，要关掉面板才生效。所以每次发包后临时放行 80ms，
     * 让服务端把包处理掉（一个服务端 tick 是 50ms，80ms 保证至少跑一次）。
     */
    private long pauseFreeUntil;

    /** 发包后放行世界的时长（毫秒）。 */
    private static final long PAUSE_FREE_MS = 80L;

    public ItemSpecialTagScreen(ItemEditorScreen parent) {
        super(Component.translatable("screen.infinitestats.item_editor.tags"));
        this.parent = parent;
        loadFromItem();
    }

    /** 只在构造时读一次，避免子界面往返覆盖编辑结果。 */
    private void loadFromItem() {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        ItemStack stack = player.getMainHandItem();

        unbreakable = origUnbreakable = ItemEditUtil.isUnbreakable(stack);
        hideTooltip = origHideTooltip = ItemEditUtil.isHideTooltip(stack);
        hideAdditional = origHideAdditional = ItemEditUtil.isHideAdditional(stack);

        Integer cmm = ItemEditUtil.customModelData(stack);
        origCmm = cmm == null ? "" : Integer.toString(cmm);

        Integer dye = ItemEditUtil.dyeColor(stack);
        origDye = dye == null ? "" : hex(dye);

        Integer potion = ItemEditUtil.potionColor(stack);
        origPotion = potion == null ? "" : hex(potion);

        Integer repair = ItemEditUtil.repairCost(stack);
        origRepair = repair == null ? "" : Integer.toString(repair);
    }

    // ======================== 初始化 ========================

    @Override
    protected void init() {
        super.init();
        leftPos = 0;
        topPos = 0;

        cmmBox = numericBox(CM_Y, origCmm, 10, "");
        dyeBox = numericBox(DYE_Y, origDye, 8, "RRGGBB");
        potionBox = numericBox(POTION_Y, origPotion, 8, "RRGGBB");
        repairBox = numericBox(REPAIR_Y, origRepair, 10, "");

        int by = topPos + FOOTER_Y;
        addRenderableWidget(Button.builder(
                        Component.translatable("screen.infinitestats.item_editor.back"), b -> onClose())
                .bounds(leftPos + EditorUi.GAP, by, 100, FOOTER_H).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("screen.infinitestats.item_editor.clear_tags"), b -> clearAll())
                .bounds(leftPos + (GUI_W - 140) / 2, by, 140, FOOTER_H).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("screen.infinitestats.item_editor.apply"), b -> apply())
                .bounds(leftPos + GUI_W - EditorUi.GAP - 100, by, 100, FOOTER_H).build());
    }

    private EditBox numericBox(int panelY, String value, int maxLength, String hint) {
        EditBox box = new EditBox(font, leftPos + NUM_X, topPos + panelY, NUM_W, BOX_H, Component.empty());
        box.setMaxLength(maxLength);
        box.setTextColor(EditorUi.PRIMARY);
        box.setValue(value);
        if (!hint.isEmpty()) box.setHint(Component.literal(hint));
        addRenderableWidget(box);
        return box;
    }

    // ======================== 绘制 ========================

    @Override
    protected void renderPanel(GuiGraphics g, int mx, int my, float pt) {
        EditorUi.panel(g, leftPos, topPos, GUI_W, GUI_H);
        EditorUi.title(g, font, leftPos, topPos, GUI_W, title.getString());
        drawItemBar(g);

        g.drawString(font, Component.translatable(
                        "screen.infinitestats.item_editor.tag.version_note").getString(),
                leftPos + EditorUi.GAP, topPos + NOTE_Y, EditorUi.SECONDARY);

        EditorUi.section(g, font, leftPos + EditorUi.GAP - 6, topPos + TOGGLE_SECTION_Y,
                Component.translatable("screen.infinitestats.item_editor.section_tags").getString());
        drawToggles(g, mx, my);

        EditorUi.dividerH(g, leftPos + EditorUi.GAP, topPos + NUM_SECTION_Y - 6, GUI_W - EditorUi.GAP * 2);
        EditorUi.section(g, font, leftPos + EditorUi.GAP - 6, topPos + NUM_SECTION_Y,
                Component.translatable("screen.infinitestats.item_editor.section_values").getString());

        drawNumRow(g, CM_Y, "screen.infinitestats.item_editor.tag.custom_model_data",
                "screen.infinitestats.item_editor.tag.int_hint");
        drawNumRow(g, DYE_Y, "screen.infinitestats.item_editor.tag.dye_color",
                "screen.infinitestats.item_editor.tag.hex_hint");
        drawSwatches(g, mx, my);
        drawNumRow(g, POTION_Y, "screen.infinitestats.item_editor.tag.potion_color",
                "screen.infinitestats.item_editor.tag.hex_hint");
        drawNumRow(g, REPAIR_Y, "screen.infinitestats.item_editor.tag.repair_cost",
                "screen.infinitestats.item_editor.tag.int_hint");
    }

    private void drawNumRow(GuiGraphics g, int panelY, String labelKey, String hintKey) {
        g.drawString(font, Component.translatable(labelKey).getString(),
                leftPos + EditorUi.GAP, topPos + panelY + 5, EditorUi.GOLD);
        g.drawString(font, Component.translatable(hintKey).getString(),
                leftPos + NUM_X + NUM_W + 6, topPos + panelY + 5, EditorUi.SECONDARY);
    }

    /** 单列勾选开关。 */
    private void drawToggles(GuiGraphics g, int mx, int my) {
        toggleRects.clear();
        hoverCode = -1;

        for (int i = 0; i < TOGGLE_CODES.length; i++) {
            int x = TOGGLE_X;
            int y = TOGGLE_Y + i * TOGGLE_ROW_H;
            int code = TOGGLE_CODES[i];

            boolean hover = mx >= x && mx < x + TOGGLE_W
                    && my >= y && my < y + TOGGLE_ROW_H - 2;
            if (hover) hoverCode = code;

            boolean on = isChecked(code);
            g.fill(x, y, x + TOGGLE_W, y + TOGGLE_ROW_H - 2,
                    hover ? EditorUi.BG_ROW_HOVER : EditorUi.BG_ROW);

            int bx = x + 5;
            int by = y + (TOGGLE_ROW_H - 2 - 10) / 2;
            g.fill(bx, by, bx + 10, by + 10, on ? EditorUi.ACCENT : EditorUi.BG_SB_TRACK);
            if (on) g.drawString(font, "✓", bx + 1, by + 1, 0xFF0F1A12);

            g.drawString(font, Component.translatable(TOGGLE_KEYS[code]).getString(),
                    x + 20, y + 6, on ? EditorUi.ACCENT : EditorUi.PRIMARY);

            toggleRects.add(new int[]{x, y, TOGGLE_W, TOGGLE_ROW_H - 2, code});
        }
    }

    /** 染料色板（原版 16 个染料色的火焰星颜色，正好是纯 RGB）。 */
    private void drawSwatches(GuiGraphics g, int mx, int my) {
        swatchRects.clear();
        hoverSwatch = null;

        Integer current = parseHex(dyeBox == null ? "" : dyeBox.getValue());
        DyeColor[] colors = DyeColor.values();
        for (int i = 0; i < colors.length; i++) {
            int rgb = colors[i].getFireworkColor() & 0xFFFFFF;
            int x = leftPos + NUM_X + i * (SWATCH_SZ + SWATCH_GAP);
            int y = topPos + SWATCH_Y;

            g.fill(x, y, x + SWATCH_SZ, y + SWATCH_SZ, 0xFF000000 | rgb);
            boolean selected = current != null && current == rgb;
            boolean hover = mx >= x && mx < x + SWATCH_SZ && my >= y && my < y + SWATCH_SZ;
            if (hover) hoverSwatch = rgb;
            if (selected || hover) {
                int border = selected ? EditorUi.ACCENT : EditorUi.PRIMARY;
                g.fill(x - 1, y - 1, x + SWATCH_SZ + 1, y, border);
                g.fill(x - 1, y + SWATCH_SZ, x + SWATCH_SZ + 1, y + SWATCH_SZ + 1, border);
                g.fill(x - 1, y, x, y + SWATCH_SZ, border);
                g.fill(x + SWATCH_SZ, y, x + SWATCH_SZ + 1, y + SWATCH_SZ, border);
            }
            swatchRects.add(new int[]{x, y, SWATCH_SZ, rgb});
        }
    }

    @Override
    protected void renderPanelOverlay(GuiGraphics g, int mx, int my, float pt) {
        if (hoverCode >= 0) {
            List<String> lines = new ArrayList<>();
            lines.add("§e" + Component.translatable(TOGGLE_KEYS[hoverCode]).getString());
            lines.add("§7" + Component.translatable(TOGGLE_HINT_KEYS[hoverCode]).getString());
            queueTooltip(g, mx, my, lines, EditorUi.INFO);
        } else if (hoverSwatch != null) {
            List<String> lines = new ArrayList<>();
            lines.add("§e#" + String.format(Locale.ROOT, "%06X", hoverSwatch));
            lines.add("§7" + Component.translatable(
                    "screen.infinitestats.item_editor.tag.dye_hint").getString());
            queueTooltip(g, mx, my, lines, EditorUi.INFO);
        }

        if (System.currentTimeMillis() < statusUntil) {
            g.drawCenteredString(font, statusMsg, leftPos + GUI_W / 2, topPos + GUI_H - 8, EditorUi.DANGER);
        }
    }

    /** 顶部物品预览条（与主界面一致）。 */
    private void drawItemBar(GuiGraphics g) {
        int x = leftPos, y = topPos + ITEM_BAR_Y;
        g.fill(x, y, x + GUI_W, y + ITEM_BAR_H, EditorUi.BG_ITEM_BAR);

        var player = Minecraft.getInstance().player;
        ItemStack stack = player == null ? ItemStack.EMPTY : player.getMainHandItem();
        if (stack.isEmpty()) {
            g.drawString(font, Component.translatable("screen.infinitestats.item_editor.empty").getString(),
                    x + EditorUi.GAP, y + (ITEM_BAR_H - font.lineHeight) / 2, EditorUi.DANGER);
            return;
        }

        int ix = x + EditorUi.GAP;
        int iy = y + (ITEM_BAR_H - 16) / 2;
        g.renderItem(stack, ix, iy);
        if (stack.getCount() > 1) {
            g.drawString(font, "x" + stack.getCount(), ix + 17, iy + 9, EditorUi.GOLD);
        }

        int tx = ix + 24;
        g.drawString(font, EditorUi.ellipsize(font, stack.getHoverName().getString(), 150),
                tx, y + 5, EditorUi.PRIMARY);

        if (stack.isDamaged()) {
            int max = stack.getMaxDamage();
            int remain = max - stack.getDamageValue();
            int barY = y + ITEM_BAR_H - 12;
            EditorUi.bar(g, tx, barY, 110, 5, (float) remain / max);
            g.drawString(font, remain + "/" + max, tx + 116, barY - 2, EditorUi.SECONDARY);
        }

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String idStr = id == null ? "" : id.toString();
        g.drawString(font, EditorUi.ellipsize(font, idStr, 150),
                x + GUI_W - EditorUi.GAP - Math.min(150, font.width(idStr)), y + 5, EditorUi.SECONDARY);
    }

    // ======================== 输入 ========================

    @Override
    protected boolean onPanelClick(int mx, int my, int button) {
        if (button != 0) return false;

        for (int[] r : toggleRects) {
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                toggle(r[4]);
                playClick();
                return true;
            }
        }
        for (int[] r : swatchRects) {
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[2]) {
                pickSwatch(r[3]);
                playClick();
                return true;
            }
        }
        return false;
    }

    private boolean isChecked(int code) {
        return switch (code) {
            case 0 -> unbreakable;
            case 1 -> hideTooltip;
            case 2 -> hideAdditional;
            default -> false;
        };
    }

    private void toggle(int code) {
        switch (code) {
            case 0 -> unbreakable = !unbreakable;
            case 1 -> hideTooltip = !hideTooltip;
            case 2 -> hideAdditional = !hideAdditional;
            default -> { }
        }
    }

    /** 点色块：与当前值相同则清空（= 移除染色），否则设为该颜色。 */
    private void pickSwatch(int rgb) {
        if (dyeBox == null) return;
        Integer current = parseHex(dyeBox.getValue());
        dyeBox.setValue(current != null && current == rgb ? "" : hex(rgb));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ======================== 提交 ========================

    private void clearAll() {
        unbreakable = false;
        hideTooltip = false;
        hideAdditional = false;
        if (cmmBox != null) cmmBox.setValue("");
        if (dyeBox != null) dyeBox.setValue("");
        if (potionBox != null) potionBox.setValue("");
        if (repairBox != null) repairBox.setValue("");
        setStatus(Component.translatable("screen.infinitestats.item_editor.tags_cleared").getString());
    }

    private void apply() {
        var player = Minecraft.getInstance().player;
        if (player == null || player.getMainHandItem().isEmpty()) {
            setStatus(Component.translatable("screen.infinitestats.item_editor.empty").getString());
            return;
        }

        parsedOk = true;
        Boolean ub = unbreakable == origUnbreakable ? null : unbreakable;
        Boolean ht = hideTooltip == origHideTooltip ? null : hideTooltip;
        Boolean ha = hideAdditional == origHideAdditional ? null : hideAdditional;
        Integer cmm = decimalChange(cmmBox, origCmm);
        Integer dye = hexChange(dyeBox, origDye);
        Integer potion = hexChange(potionBox, origPotion);
        Integer repair = decimalChange(repairBox, origRepair);
        if (!parsedOk) {
            setStatus(Component.translatable("screen.infinitestats.item_editor.invalid_value").getString());
            return;
        }

        if (ub == null && ht == null && ha == null
                && cmm == null && dye == null && potion == null && repair == null) {
            setStatus(Component.translatable("screen.infinitestats.item_editor.no_changes").getString());
            return;
        }

        ItemStack stack = player.getMainHandItem();
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        NetworkHandler.CHANNEL.sendToServer(new EditItemTagsPacket(
                player.getInventory().selected, itemId, ItemEditUtil.specialSnapshot(stack),
                ub, ht, ha, cmm, dye, potion, repair));
        // 暂停界面里包会一直躺在队列里：放行 80ms 让服务端处理这次写入
        pauseFreeUntil = System.currentTimeMillis() + PAUSE_FREE_MS;

        // 更新基准：连续微调时只发真正变化的字段
        origUnbreakable = unbreakable;
        origHideTooltip = hideTooltip;
        origHideAdditional = hideAdditional;
        if (cmm != null) origCmm = cmm < 0 ? "" : Integer.toString(cmm);
        if (dye != null) origDye = dye < 0 ? "" : hex(dye);
        if (potion != null) origPotion = potion < 0 ? "" : hex(potion);
        if (repair != null) origRepair = repair < 0 ? "" : Integer.toString(repair);

        setStatus(Component.translatable("screen.infinitestats.item_editor.applied").getString());
    }

    /** 解析失败的标记：一处非法就整次不提交，避免写半个物品。 */
    private boolean parsedOk;

    /** 十进制字段：未改 → null；清空 → -1（移除）；非法 → 置 parsedOk=false。 */
    private Integer decimalChange(EditBox box, String orig) {
        if (box == null) return null;
        String now = box.getValue();
        if (now.equals(orig)) return null;
        String t = now.trim();
        if (t.isEmpty()) return -1;
        try {
            return Math.max(0, Integer.parseInt(t));
        } catch (NumberFormatException e) {
            parsedOk = false;
            return null;
        }
    }

    /** 颜色字段：未改 → null；清空 → -1（移除）；非法 → 置 parsedOk=false。 */
    private Integer hexChange(EditBox box, String orig) {
        if (box == null) return null;
        String now = box.getValue();
        if (now.equals(orig)) return null;
        if (now.trim().isEmpty()) return -1;
        Integer rgb = parseHex(now);
        if (rgb == null) parsedOk = false;
        return rgb;
    }

    // ======================== 工具 ========================

    /** 解析 {@code #RRGGBB} / {@code RRGGBB} / {@code AARRGGBB}，取低 24 位；非法返回 null。 */
    private static Integer parseHex(String text) {
        if (text == null) return null;
        String t = text.trim();
        if (t.startsWith("#")) t = t.substring(1);
        if (t.isEmpty() || t.length() > 8) return null;
        try {
            return (int) (Long.parseLong(t, 16) & 0xFFFFFFL);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String hex(int rgb) {
        return String.format(Locale.ROOT, "%06X", rgb & 0xFFFFFF);
    }

    private void setStatus(String msg) {
        statusMsg = msg;
        statusUntil = System.currentTimeMillis() + 2500;
    }

    private void playClick() {
        long now = System.currentTimeMillis();
        if (now - lastSoundTick < 50) return;
        lastSoundTick = now;
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    /** 平时暂停；刚发过包的那几十毫秒放行，让服务端真的处理这次写入。 */
    @Override
    public boolean isPauseScreen() {
        return System.currentTimeMillis() >= pauseFreeUntil;
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
