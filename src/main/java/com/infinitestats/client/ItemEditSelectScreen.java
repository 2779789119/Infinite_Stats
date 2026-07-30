package com.infinitestats.client;

import com.infinitestats.compat.JechCompat;
import net.minecraft.client.Minecraft;
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
 * 物品编辑器 - 条目选择子界面
 * <p>
 * 显示一个可搜索的候选列表（附魔或属性），玩家点击选中后，在底部输入数值确认，
 * 把条目加入到父界面 {@link ItemEditorScreen} 的编辑列表中。
 */
public class ItemEditSelectScreen extends Screen {

    private static final int GUI_WIDTH = 360;
    private static final int GUI_HEIGHT = 320;
    private static final int HEADER_H = 26;
    private static final int ROW_H = 20;
    private static final int LIST_TOP = 50;
    private static final int MAX_VISIBLE = 11;
    private static final int SCROLLBAR_W = 6;

    private static final int TEXT_PRIMARY = 0xFFE2E8F0;
    private static final int TEXT_SECONDARY = 0xFF94A3B8;
    private static final int TEXT_GOLD = 0xFFFFD166;
    private static final int TEXT_DANGER = 0xFFF87171;
    private static final int BG_PANEL = 0xE81A1A2E;
    private static final int BG_HEADER = 0xFF1E293B;
    private static final int BG_ROW = 0x50252535;
    private static final int BG_ROW_HOVER = 0x80353550;
    private static final int BG_SCROLLBAR_TRACK = 0x30151520;
    private static final int BG_SCROLLBAR = 0x80555570;
    private static final int BG_SCROLLBAR_HOVER = 0xB08888A0;

    private final ItemEditorScreen parent;
    private final boolean isAttr;
    private final ItemEditorScreen.EnchantEntry editing;
    private int leftPos, topPos;
    private Candidate hovered;
    private int listRows = MAX_VISIBLE;

    private record Candidate(ResourceLocation rl, String display) {}

    private final List<Candidate> all = new ArrayList<>();
    private List<Candidate> filtered = new ArrayList<>();
    private String searchText = "";
    private int scroll, maxScroll;
    private Candidate selected;

    private EditBox searchBox;
    private EditBox valueBox;
    private EditBox extraBox;
    private CycleButton<String> opCycle, slotCycle;
    private final List<int[]> rowRects = new ArrayList<>();
    private long lastSoundTick;

    private String statusMsg = "";
    private long statusUntil;

    public ItemEditSelectScreen(ItemEditorScreen parent, boolean isAttr) {
        this(parent, isAttr, null);
    }

    public ItemEditSelectScreen(ItemEditorScreen parent, boolean isAttr, ItemEditorScreen.EnchantEntry editing) {
        super(Component.translatable(isAttr
                ? "screen.infinitestats.item_editor.select_attr"
                : (editing != null ? "screen.infinitestats.item_editor.edit_enchant"
                : "screen.infinitestats.item_editor.select_enchant")));
        this.parent = parent;
        this.isAttr = isAttr;
        this.editing = editing;
        buildCandidates();
        // 编辑模式：预选中原条目，直接进入数值输入
        if (editing != null) {
            ResourceLocation rl = ResourceLocation.tryParse(editing.id());
            selected = new Candidate(rl, parent.enchantDisplayName(editing.id()));
        }
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
                if (rl != null) all.add(new Candidate(rl, Component.translatable(e.getDescriptionId()).getString()));
            }
        }
        all.sort(Comparator.comparing(c -> c.display));
        applyFilter();
    }

    /** 获取属性的可读显示名（翻译 → 回退到简化 ID） */
    private static String attrDisplayName(Attribute attr, ResourceLocation rl) {
        String descId = attr.getDescriptionId();
        String translated = Component.translatable(descId).getString();
        // 翻译键 == 翻译值 → 没有对应翻译，用简化名称
        if (translated.equals(descId)) {
            String path = rl.getPath();
            // 路径中的下划线替换为空格，斜杠替换为「/」（带空格）
            path = path.replace('_', ' ').replace('/', '/');
            if (path.length() > 36) path = path.substring(0, 34) + "…";
            return rl.getNamespace() + ": " + path;
        }
        return translated;
    }

    private void applyFilter() {
        String q = searchText.toLowerCase().trim();
        filtered = new ArrayList<>();
        for (Candidate c : all) {
            if (q.isEmpty() || JechCompat.matches(c.display.toLowerCase(), q) || JechCompat.matches(c.rl.toString().toLowerCase(), q)) {
                filtered.add(c);
            }
        }
        maxScroll = Math.max(0, filtered.size() - MAX_VISIBLE);
        scroll = clamp(scroll, 0, maxScroll);
    }

    @Override
    protected void init() {
        super.init();
        leftPos = (width - GUI_WIDTH) / 2;
        topPos = (height - GUI_HEIGHT) / 2;
        rebuildInput();
    }

    private void rebuildInput() {
        clearWidgets();
        rowRects.clear();

        int sx = leftPos + 8, sy = topPos + HEADER_H + 4, sw = GUI_WIDTH - 16;
        // 编辑模式不显示搜索框与候选列表
        if (editing == null) {
            searchBox = new EditBox(font, sx, sy, sw, 16, Component.literal(""));
            searchBox.setMaxLength(60);
            searchBox.setBordered(true);
            searchBox.setTextColor(TEXT_PRIMARY);
            searchBox.setHint(Component.translatable("screen.infinitestats.item_editor.search_hint"));
            searchBox.setValue(searchText);
            searchBox.setResponder(s -> { searchText = s; applyFilter(); });
            addRenderableWidget(searchBox);
        }

        if (selected != null) {
            int iy = topPos + GUI_HEIGHT - 58;
            valueBox = new EditBox(font, leftPos + 8, iy, 56, 16, Component.literal(""));
            valueBox.setMaxLength(12);
            valueBox.setBordered(true);
            valueBox.setTextColor(TEXT_PRIMARY);
            valueBox.setValue(editing != null ? String.valueOf(editing.level()) : "1");
            addRenderableWidget(valueBox);

            if (!isAttr) {
                extraBox = new EditBox(font, leftPos + 70, iy, GUI_WIDTH - 70 - 98, 16, Component.literal(""));
                extraBox.setMaxLength(256);
                extraBox.setBordered(true);
                extraBox.setTextColor(TEXT_PRIMARY);
                extraBox.setHint(Component.translatable("screen.infinitestats.item_editor.extra_nbt_hint"));
                if (editing != null && !editing.extra().isEmpty()) extraBox.setValue(editing.extra().toString());
                addRenderableWidget(extraBox);
            }

            addRenderableWidget(makeButton(leftPos + GUI_WIDTH - 90, iy, 82, 16,
                    Component.translatable("screen.infinitestats.item_editor.confirm"), b -> confirm()));

            if (isAttr) {
                opCycle = CycleButton.<String>builder(v -> Component.literal(v))
                        .withValues(ItemEditorScreen.OP_LABELS)
                        .withInitialValue(ItemEditorScreen.OP_LABELS[0])
                        .create(leftPos + 96, iy, 88, 16,
                                Component.translatable("screen.infinitestats.item_editor.op"), (b, v) -> {});
                addRenderableWidget(opCycle);
                slotCycle = CycleButton.<String>builder(v -> Component.literal(v))
                        .withValues(ItemEditorScreen.SLOT_LABELS)
                        .withInitialValue(ItemEditorScreen.SLOT_LABELS[0])
                        .create(leftPos + 96, iy + 20, 88, 16,
                                Component.translatable("screen.infinitestats.item_editor.slot"), (b, v) -> {});
                addRenderableWidget(slotCycle);
            }

            // 选中条目名提示
            String sel = isAttr ? selected.display : selected.display;
            if (font.width(sel) > GUI_WIDTH - 20) sel = font.plainSubstrByWidth(sel, GUI_WIDTH - 24) + "…";
            // 由 render 绘制
        }

        addRenderableWidget(makeButton(leftPos + 8, topPos + GUI_HEIGHT - 28, 100, 18,
                Component.translatable("screen.infinitestats.item_editor.back"), b -> back()));
    }

    private Button makeButton(int x, int y, int w, int h, Component msg, Button.OnPress press) {
        return Button.builder(msg, press).bounds(x, y, w, h).build();
    }

    // ======================== 渲染 ========================

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(leftPos, topPos, leftPos + GUI_WIDTH, topPos + GUI_HEIGHT, BG_PANEL);
        g.fill(leftPos, topPos, leftPos + GUI_WIDTH, topPos + HEADER_H, BG_HEADER);
        g.drawCenteredString(font, title.getString(), leftPos + GUI_WIDTH / 2, topPos + 8, 0xFF4ADE80);

        // 列表（编辑模式隐藏，仅显示输入区）
        listRows = computeListRows();
        maxScroll = Math.max(0, filtered.size() - listRows);
        scroll = clamp(scroll, 0, maxScroll);
        rowRects.clear();
        hovered = null;
        if (editing == null) {
            for (int i = 0; i < listRows; i++) {
                int idx = i + scroll;
                if (idx >= filtered.size()) break;
                Candidate c = filtered.get(idx);
                int y = topPos + LIST_TOP + i * ROW_H;
                boolean hover = mx >= leftPos + 6 && mx < leftPos + GUI_WIDTH - SCROLLBAR_W - 10
                        && my >= y && my < y + ROW_H - 2;
                if (hover) hovered = c;
                g.fill(leftPos + 6, y, leftPos + GUI_WIDTH - SCROLLBAR_W - 10, y + ROW_H - 2,
                        hover ? BG_ROW_HOVER : BG_ROW);

                String name = c.display;
                String idStr = c.rl.toString();
                int idW = font.width(idStr);
                int idRight = leftPos + GUI_WIDTH - SCROLLBAR_W - 10;
                int idX = idRight - idW;
                int nameMaxWidth = Math.max(40, idX - (leftPos + 12) - 8);
                if (font.width(name) > nameMaxWidth) {
                    name = font.plainSubstrByWidth(name, Math.max(4, nameMaxWidth - font.width("…"))) + "…";
                }
                g.drawString(font, name, leftPos + 12, y + 4, TEXT_PRIMARY);
                g.drawString(font, idStr, idX, y + 4, TEXT_SECONDARY);
                rowRects.add(new int[]{leftPos + 6, y, idx});
            }
            renderScrollbar(g, mx, my);
        }

        // 选中提示
        if (selected != null) {
            String sel = selected.display;
            if (font.width(sel) > GUI_WIDTH - 100) sel = font.plainSubstrByWidth(sel, GUI_WIDTH - 104) + "…";
            g.drawString(font, sel, leftPos + 8,
                    topPos + GUI_HEIGHT - 58 - 14, TEXT_GOLD);
            g.drawString(font, isAttr ? Component.translatable("screen.infinitestats.item_editor.value_hint").getString()
                    : Component.translatable("screen.infinitestats.item_editor.level_nbt_hint").getString(),
                    leftPos + 8, topPos + GUI_HEIGHT - 40, TEXT_SECONDARY);
        }

        if (System.currentTimeMillis() < statusUntil) {
            g.drawCenteredString(font, statusMsg, leftPos + GUI_WIDTH / 2, topPos + GUI_HEIGHT - 6, TEXT_DANGER);
        }

        if (!isAttr && hovered != null) {
            ItemEditorScreen.renderEnchantDescription(g, mx, my, hovered.rl);
        }

        for (var r : renderables) r.render(g, mx, my, pt);
    }

    private void renderScrollbar(GuiGraphics g, int mx, int my) {
        if (maxScroll <= 0) return;
        int sx = leftPos + GUI_WIDTH - SCROLLBAR_W - 4;
        int sy = topPos + LIST_TOP;
        int sh = listRows * ROW_H - 2;
        g.fill(sx, sy, sx + SCROLLBAR_W, sy + sh, BG_SCROLLBAR_TRACK);

        int sliderH = Math.max(20, (int) ((float) MAX_VISIBLE / (maxScroll + MAX_VISIBLE) * sh));
        int sliderY = sy + (sh - sliderH) * scroll / maxScroll;
        boolean hover = mx >= sx && mx < sx + SCROLLBAR_W && my >= sliderY && my < sliderY + sliderH;
        g.fill(sx, sliderY, sx + SCROLLBAR_W, sliderY + sliderH, hover ? BG_SCROLLBAR_HOVER : BG_SCROLLBAR);
    }

    // ======================== 输入 ========================

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && editing == null) {
            for (int[] r : rowRects) {
                if (mx >= r[0] && mx < leftPos + GUI_WIDTH - SCROLLBAR_W - 10
                        && my >= r[1] && my < r[1] + ROW_H - 2) {
                    selected = filtered.get(r[2]);
                    playClick();
                    rebuildInput();
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
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ======================== 逻辑 ========================

    private void confirm() {
        if (selected == null) return;
        double val;
        try { val = Double.parseDouble(valueBox.getValue().trim()); } catch (Exception ex) { val = 1; }
        String id = selected.rl.toString();
        if (isAttr) {
            int op = ItemEditorScreen.indexOf(ItemEditorScreen.OP_LABELS, opCycle.getValue());
            if (op < 0) op = 0;
            String slot = ItemEditorScreen.SLOT_IDS[
                    ItemEditorScreen.indexOf(ItemEditorScreen.SLOT_LABELS, slotCycle.getValue())];
            parent.addAttr(id, op, val, slot);
        } else {
            int lvl = (int) Math.max(1, val);
            CompoundTag extra = parseExtraNbt();
            if (extra == null) {
                setStatus(Component.translatable("screen.infinitestats.item_editor.invalid_extra_nbt").getString());
                return;
            }
            if (editing != null) parent.replaceEnchant(editing, id, lvl, extra);
            else parent.addEnchant(id, lvl, extra);
        }
        parent.refreshScroll();
        minecraft.setScreen(parent);
    }

    /** 解析扩展 NBT 输入框（JSON/SNBT），空内容返回空白 CompoundTag，格式错误返回 null */
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

    /** 计算当前列表可显示的行数：编辑模式下隐藏列表；\n     *  选中附魔（add 模式）后为避免与底部输入栏重叠，缩短列表。 */
    private int computeListRows() {
        if (editing != null) return 0;
        int bottom = (selected != null) ? topPos + 244 : topPos + GUI_HEIGHT - 8;
        int rows = (bottom - (topPos + LIST_TOP)) / ROW_H;
        return Math.max(0, Math.min(MAX_VISIBLE, rows));
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
