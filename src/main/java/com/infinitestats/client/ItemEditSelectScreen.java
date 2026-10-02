package com.infinitestats.client;

import com.infinitestats.compat.JechCompat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/**
 * 物品编辑器 - 条目选择 / 编辑子界面。
 * <p>
 * 与主界面共用 {@link EditorUi} 视觉规范。列出可搜索的候选（附魔或属性），
 * 选中后在底部填写数值（属性另加操作与槽位，附魔可填扩展 NBT）再确认。
 * <p>
 * 重写要点：
 * <ul>
 *   <li>列表高度固定，不再随选中状态伸缩，切换时不会跳动；</li>
 *   <li>编辑已有条目时同样保留候选列表，可直接改选成别的条目；</li>
 *   <li>新增对「属性」的编辑支持（此前只能编辑附魔）；</li>
 *   <li>数值 / 操作 / 槽位 / 扩展 NBT 输入非法时在底部给出明确提示。</li>
 * </ul>
 */
public class ItemEditSelectScreen extends Screen {

    // ======================== 布局常量 ========================

    private static final int GUI_W = 420;
    private static final int GUI_H = 320;

    private static final int SEARCH_Y = EditorUi.HEADER_H + 4;              // 28
    private static final int SEARCH_H = 18;
    private static final int LIST_Y = SEARCH_Y + SEARCH_H + 4;              // 50
    private static final int VISIBLE_ROWS = 9;
    private static final int LIST_H = VISIBLE_ROWS * EditorUi.ROW_H;        // 198

    private static final int INPUT_Y = LIST_Y + LIST_H + 22;                // 270
    private static final int INPUT_H = 18;
    private static final int ROW2_Y = INPUT_Y + 22;                         // 292

    private static final int BTN_CONFIRM_W = 82;
    private static final int BTN_BACK_W = 100;

    // ======================== 状态 ========================

    private final ItemEditorScreen parent;
    private final boolean isAttr;
    private final ItemEditorScreen.EnchantEntry editingEnchant;
    private final ItemEditorScreen.AttrEntry editingAttr;

    private record Candidate(ResourceLocation rl, String display) {}

    private final List<Candidate> all = new ArrayList<>();
    private List<Candidate> filtered = new ArrayList<>();
    private String searchText = "";
    private int scroll, maxScroll;
    private Candidate selected;
    private Candidate hovered;

    private EditBox searchBox;
    private EditBox valueBox;
    private EditBox extraBox;
    private CycleButton<String> opCycle, slotCycle;
    private final List<int[]> rowRects = new ArrayList<>();
    private long lastSoundTick;
    private String statusMsg = "";
    private long statusUntil;

    private int leftPos, topPos;

    public ItemEditSelectScreen(ItemEditorScreen parent, boolean isAttr,
                                ItemEditorScreen.EnchantEntry editingEnchant,
                                ItemEditorScreen.AttrEntry editingAttr) {
        super(Component.translatable(titleKey(isAttr, editingEnchant, editingAttr)));
        this.parent = parent;
        this.isAttr = isAttr;
        this.editingEnchant = editingEnchant;
        this.editingAttr = editingAttr;
        buildCandidates();
        // 编辑模式：预选中原条目
        String presetId = editingAttr != null ? editingAttr.id()
                : editingEnchant != null ? editingEnchant.id() : null;
        if (presetId != null) {
            for (Candidate c : all) {
                if (c.rl.toString().equals(presetId)) { selected = c; break; }
            }
        }
    }

    private static String titleKey(boolean isAttr, ItemEditorScreen.EnchantEntry ee,
                                   ItemEditorScreen.AttrEntry ea) {
        if (isAttr) return ea != null
                ? "screen.infinitestats.item_editor.edit_attr"
                : "screen.infinitestats.item_editor.select_attr";
        return ee != null
                ? "screen.infinitestats.item_editor.edit_enchant"
                : "screen.infinitestats.item_editor.select_enchant";
    }

    private void buildCandidates() {
        if (isAttr) {
            for (var e : ForgeRegistries.ATTRIBUTES) {
                ResourceLocation rl = ForgeRegistries.ATTRIBUTES.getKey(e);
                if (rl != null) all.add(new Candidate(rl, attrDisplayName(e, rl)));
            }
        } else {
            for (Enchantment e : ForgeRegistries.ENCHANTMENTS) {
                ResourceLocation rl = ForgeRegistries.ENCHANTMENTS.getKey(e);
                if (rl != null) {
                    all.add(new Candidate(rl, Component.translatable(e.getDescriptionId()).getString()));
                }
            }
        }
        all.sort(Comparator.comparing(c -> c.display));
        applyFilter();
    }

    /** 获取属性的可读显示名（翻译 → 回退到简化 ID）。 */
    private static String attrDisplayName(Attribute attr, ResourceLocation rl) {
        String descId = attr.getDescriptionId();
        String translated = Component.translatable(descId).getString();
        if (translated.equals(descId)) {
            String path = rl.getPath().replace('_', ' ');
            if (path.length() > 36) path = path.substring(0, 34) + "…";
            return rl.getNamespace() + ": " + path;
        }
        return translated;
    }

    private void applyFilter() {
        String q = searchText.toLowerCase(Locale.ROOT).trim();
        filtered = new ArrayList<>();
        for (Candidate c : all) {
            if (q.isEmpty()
                    || JechCompat.matches(c.display.toLowerCase(Locale.ROOT), q)
                    || JechCompat.matches(c.rl.toString().toLowerCase(Locale.ROOT), q)) {
                filtered.add(c);
            }
        }
        maxScroll = Math.max(0, filtered.size() - VISIBLE_ROWS);
        scroll = clamp(scroll, 0, maxScroll);
    }

    // ======================== 初始化 ========================

    @Override
    protected void init() {
        super.init();
        leftPos = Math.max(0, (width - GUI_W) / 2);
        topPos = Math.max(0, (height - GUI_H) / 2);
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        rowRects.clear();

        searchBox = new EditBox(font, leftPos + EditorUi.GAP, topPos + SEARCH_Y,
                GUI_W - EditorUi.GAP * 2, SEARCH_H, Component.empty());
        searchBox.setMaxLength(60);
        searchBox.setTextColor(EditorUi.PRIMARY);
        searchBox.setHint(Component.translatable("screen.infinitestats.item_editor.search_hint"));
        searchBox.setValue(searchText);
        searchBox.setResponder(s -> { searchText = s; applyFilter(); });
        addRenderableWidget(searchBox);

        String prefillValue = "1";
        if (editingAttr != null) prefillValue = EditorUi.amount(editingAttr.amount());
        else if (editingEnchant != null) prefillValue = String.valueOf(editingEnchant.level());

        int x = leftPos + EditorUi.GAP;
        valueBox = new EditBox(font, x, topPos + INPUT_Y, 56, INPUT_H, Component.empty());
        valueBox.setMaxLength(16);
        valueBox.setTextColor(EditorUi.PRIMARY);
        valueBox.setValue(prefillValue);
        addRenderableWidget(valueBox);
        x += 60;

        if (isAttr) {
            // CycleButton 会把 name 参数与当前值渲染成「name: value」，
            // 所以这里传「操作 / 槽位」标签本身，而不是把标签拼进值里
            //（拼进值里会多出一个前缀冒号，显示为「: 操作: 加算」）。
            opCycle = CycleButton.<String>builder(Component::literal)
                    .withValues(ItemEditorScreen.OP_LABELS)
                    .withInitialValue(ItemEditorScreen.OP_LABELS[
                            editingAttr != null ? clampOp(editingAttr.op()) : 0])
                    .create(x, topPos + INPUT_Y, 100, INPUT_H,
                            Component.translatable("screen.infinitestats.item_editor.op"), (b, v) -> {});
            addRenderableWidget(opCycle);

            // x 从「返回」按钮右侧起排，二者此前 x 相同导致完全重叠
            slotCycle = CycleButton.<String>builder(Component::literal)
                    .withValues(ItemEditorScreen.SLOT_LABELS)
                    .withInitialValue(ItemEditorScreen.SLOT_LABELS[
                            editingAttr != null
                                    ? Math.max(0, ItemEditorScreen.indexOf(ItemEditorScreen.SLOT_IDS, editingAttr.slot()))
                                    : 0])
                    .create(leftPos + EditorUi.GAP + BTN_BACK_W + EditorUi.GAP, topPos + ROW2_Y,
                            100, INPUT_H,
                            Component.translatable("screen.infinitestats.item_editor.slot"), (b, v) -> {});
            addRenderableWidget(slotCycle);
        } else {
            extraBox = new EditBox(font, x, topPos + INPUT_Y,
                    leftPos + GUI_W - EditorUi.GAP - BTN_CONFIRM_W - 12 - x,
                    INPUT_H, Component.empty());
            extraBox.setMaxLength(256);
            extraBox.setTextColor(EditorUi.PRIMARY);
            extraBox.setHint(Component.translatable("screen.infinitestats.item_editor.extra_nbt_hint"));
            if (editingEnchant != null && !editingEnchant.extra().isEmpty()) {
                extraBox.setValue(editingEnchant.extra().toString());
            }
            addRenderableWidget(extraBox);
        }

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.infinitestats.item_editor.confirm"), b -> confirm())
                .bounds(leftPos + GUI_W - EditorUi.GAP - BTN_CONFIRM_W, topPos + INPUT_Y,
                        BTN_CONFIRM_W, INPUT_H).build());

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.infinitestats.item_editor.back"), b -> back())
                .bounds(leftPos + EditorUi.GAP, topPos + ROW2_Y, BTN_BACK_W, INPUT_H).build());
    }

    private static int clampOp(int op) {
        return Math.max(0, Math.min(ItemEditorScreen.OP_LABELS.length - 1, op));
    }

    // ======================== 绘制 ========================

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        EditorUi.panel(g, leftPos, topPos, GUI_W, GUI_H);
        EditorUi.title(g, font, leftPos, topPos, GUI_W, title.getString());

        int listX = leftPos + EditorUi.GAP;
        int listW = GUI_W - EditorUi.GAP * 2 - EditorUi.SCROLL_W - 2;
        int ly = topPos + LIST_Y;

        EditorUi.dividerH(g, leftPos + 4, ly - 3, GUI_W - 8);

        rowRects.clear();
        hovered = null;
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int idx = i + scroll;
            if (idx >= filtered.size()) break;
            Candidate c = filtered.get(idx);
            int y = ly + i * EditorUi.ROW_H;
            boolean hover = mx >= listX && mx < listX + listW
                    && my >= y && my < y + EditorUi.ROW_H - 2;
            if (hover) hovered = c;

            boolean isSelected = c == selected;
            int bg = isSelected ? EditorUi.BG_ROW_DRAG
                    : hover ? EditorUi.BG_ROW_HOVER : EditorUi.BG_ROW;
            g.fill(listX, y, listX + listW, y + EditorUi.ROW_H - 2, bg);
            if (isSelected) {
                g.fill(listX, y, listX + 2, y + EditorUi.ROW_H - 2, EditorUi.ACCENT);
            }

            String idStr = c.rl.toString();
            int idW = font.width(idStr);
            int nameMax = Math.max(40, listW - 12 - idW - 8);
            g.drawString(font, EditorUi.ellipsize(font, c.display, nameMax),
                    listX + 6, y + 6, isSelected ? EditorUi.ACCENT : EditorUi.PRIMARY);
            g.drawString(font, idStr, listX + listW - idW - 6, y + 6, EditorUi.SECONDARY);

            rowRects.add(new int[]{listX, y, idx});
        }

        if (filtered.isEmpty()) {
            g.drawString(font,
                    Component.translatable("screen.infinitestats.item_editor.no_match").getString(),
                    listX + 6, ly + 6, EditorUi.SECONDARY);
        }

        EditorUi.scrollbar(g, mx, my, leftPos + GUI_W - EditorUi.GAP - EditorUi.SCROLL_W,
                ly, LIST_H - 2, scroll, maxScroll, VISIBLE_ROWS);

        EditorUi.dividerH(g, leftPos + 4, ly + LIST_H - 2, GUI_W - 8);

        // 底部：选中项提示 + 输入区标签
        if (selected != null) {
            g.drawString(font, EditorUi.ellipsize(font, selected.display, GUI_W - 120),
                    leftPos + EditorUi.GAP, topPos + INPUT_Y - 13, EditorUi.GOLD);
        } else {
            g.drawString(font,
                    Component.translatable("screen.infinitestats.item_editor.pick_entry").getString(),
                    leftPos + EditorUi.GAP, topPos + INPUT_Y - 13, EditorUi.SECONDARY);
        }

        super.render(g, mx, my, pt);

        if (!isAttr && hovered != null) {
            ItemEditorScreen.renderEnchantDescription(g, mx, my, hovered.rl);
        }

        if (System.currentTimeMillis() < statusUntil) {
            g.drawCenteredString(font, statusMsg, leftPos + GUI_W / 2, topPos + GUI_H - 10, EditorUi.DANGER);
        }
    }

    // ======================== 输入 ========================

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (int[] r : rowRects) {
                int listX = leftPos + EditorUi.GAP;
                int listW = GUI_W - EditorUi.GAP * 2 - EditorUi.SCROLL_W - 2;
                if (mx >= listX && mx < listX + listW
                        && my >= r[1] && my < r[1] + EditorUi.ROW_H - 2) {
                    selected = filtered.get(r[2]);
                    playClick();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (maxScroll > 0) {
            scroll = clamp(scroll + (delta > 0 ? -1 : 1), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            back();
            return true;
        }
        // 回车 = 确认，省去鼠标移动
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            confirm();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ======================== 逻辑 ========================

    private void confirm() {
        if (selected == null) {
            setStatus(Component.translatable("screen.infinitestats.item_editor.pick_entry").getString());
            return;
        }
        double val;
        try {
            val = Double.parseDouble(valueBox.getValue().trim());
        } catch (Exception ex) {
            setStatus(Component.translatable("screen.infinitestats.item_editor.invalid_value").getString());
            return;
        }
        if (!Double.isFinite(val)) {
            setStatus(Component.translatable("screen.infinitestats.item_editor.invalid_value").getString());
            return;
        }

        String id = selected.rl.toString();
        if (isAttr) {
            int op = clampOp(ItemEditorScreen.indexOf(ItemEditorScreen.OP_LABELS, opCycle.getValue()));
            int si = Math.max(0, ItemEditorScreen.indexOf(ItemEditorScreen.SLOT_LABELS, slotCycle.getValue()));
            String slot = ItemEditorScreen.SLOT_IDS[Math.min(si, ItemEditorScreen.SLOT_IDS.length - 1)];
            if (editingAttr != null) parent.replaceAttr(editingAttr, id, op, val, slot);
            else parent.addAttr(id, op, val, slot);
        } else {
            int lvl = (int) Math.max(1, val);
            CompoundTag extra = parseExtraNbt();
            if (extra == null) {
                setStatus(Component.translatable("screen.infinitestats.item_editor.invalid_extra_nbt").getString());
                return;
            }
            if (editingEnchant != null) parent.replaceEnchant(editingEnchant, id, lvl, extra);
            else parent.addEnchant(id, lvl, extra);
        }
        parent.refreshScroll();
        minecraft.setScreen(parent);
    }

    /** 解析扩展 NBT 输入框（SNBT），空内容返回空白 CompoundTag，格式错误返回 null。 */
    private CompoundTag parseExtraNbt() {
        if (extraBox == null) return new CompoundTag();
        String t = extraBox.getValue().trim();
        if (t.isEmpty()) return new CompoundTag();
        try {
            CompoundTag tag = TagParser.parseTag(t);
            return tag != null ? tag : new CompoundTag();
        } catch (Exception ex) {
            return null;
        }
    }

    private void setStatus(String msg) {
        statusMsg = msg;
        statusUntil = System.currentTimeMillis() + 2500;
    }

    private void back() {
        minecraft.setScreen(parent);
    }

    private void playClick() {
        long now = System.currentTimeMillis();
        if (now - lastSoundTick < 50) return;
        lastSoundTick = now;
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
