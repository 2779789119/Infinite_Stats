package com.infinitestats.client;

import com.infinitestats.compat.JechCompat;
import com.infinitestats.network.EditItemPacket;
import com.infinitestats.network.NetworkHandler;
import com.infinitestats.util.ItemEditUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/**
 * 物品编辑器主界面 — 编辑玩家主手物品的附魔与属性修饰符。
 * <p>
 * 自 1.10.2 起整体重写，结构按「布局常量 / 状态 / 绘制 / 输入 / 数据操作 / 工具」分层：
 * <ul>
 *   <li><b>物品预览条</b>：面板顶部显示主手物品图标、名称、数量与耐久条；</li>
 *   <li><b>双栏等宽</b>：附魔与属性各 7 行，行数一致、底部对齐；</li>
 *   <li><b>独立搜索</b>：每栏自带过滤框（模糊匹配显示名与 ID）；</li>
 *   <li><b>拖拽排序</b>：按住条目上下拖动即可调整顺序；</li>
 *   <li><b>批量操作</b>：每栏底部提供 添加 / 复制 / 粘贴 / 清空；</li>
 *   <li><b>滚动条指示</b>：内容超出可见范围时显示滑块与比例。</li>
 * </ul>
 * 提交时只发送实际编辑过的列表，保留内置属性、原始 UUID 与扩展 NBT。
 */
public class ItemEditorScreen extends Screen {

    // ======================== 对外常量（子界面共用） ========================

    static final String[] OP_LABELS = {"加算", "乘基", "乘总"};
    static final String[] SLOT_LABELS = {"任意", "主手", "副手", "脚", "腿", "胸", "头"};
    static final String[] SLOT_IDS = {"any", "mainhand", "offhand", "feet", "legs", "chest", "head"};

    /** 附魔条目：id / 等级 / 除 id.lvl 外的扩展 NBT。 */
    record EnchantEntry(String id, int level, CompoundTag extra) {
        EnchantEntry(String id, int level) { this(id, level, new CompoundTag()); }
    }

    /** 属性条目：id / 操作 / 数值 / 生效槽位 / 原始 NBT（保留 UUID 与扩展字段）。 */
    record AttrEntry(String id, int op, double amount, String slot, CompoundTag original) {}

    // ======================== 布局常量 ========================

    private static final int GUI_W = 420;
    private static final int GUI_H = 308;

    private static final int ITEM_BAR_Y = EditorUi.HEADER_H;              // 24
    private static final int ITEM_BAR_H = 34;

    private static final int SECTION_Y = ITEM_BAR_Y + ITEM_BAR_H + 2;     // 60
    private static final int SECTION_H = 20;
    private static final int SEARCH_Y = SECTION_Y + SECTION_H + 2;        // 82
    private static final int SEARCH_H = 18;
    private static final int LIST_Y = SEARCH_Y + SEARCH_H + 2;            // 102

    private static final int VISIBLE_ROWS = 7;
    private static final int LIST_H = VISIBLE_ROWS * EditorUi.ROW_H;      // 154

    private static final int TOOLBAR_Y = LIST_Y + LIST_H + 2;             // 258
    private static final int TOOLBAR_H = 18;

    private static final int FOOTER_Y = TOOLBAR_Y + TOOLBAR_H + 6;        // 282
    private static final int FOOTER_H = 20;

    private static final int COL_W = 198;
    private static final int COL_L = 8;
    private static final int COL_R = COL_L + COL_W + 8;                   // 214
    private static final int LIST_W = COL_W - EditorUi.SCROLL_W - 2;      // 191

    /** 拖动判定阈值（像素）：超过该距离才算拖拽而不是点击。 */
    private static final int DRAG_THRESHOLD = 4;

    // ======================== 状态 ========================

    private int leftPos, topPos;

    private final List<EnchantEntry> enchants = new ArrayList<>();
    private final List<AttrEntry> attrs = new ArrayList<>();
    private boolean enchantsChanged, attrsChanged;
    private int selectedSlot;
    private ResourceLocation itemId;
    private CompoundTag expected = new CompoundTag();

    /** 过滤后的视图，元素为源列表下标；索引 0 = 附魔，1 = 属性。 */
    private final List<Integer> enchantView = new ArrayList<>();
    private final List<Integer> attrView = new ArrayList<>();
    private final String[] searchQuery = {"", ""};
    private int enchantScroll, attrScroll;
    private int enchantMaxScroll, attrMaxScroll;

    /** 跨界面共用的条目剪贴板（同一会话内复制 / 粘贴）。 */
    static final List<EnchantEntry> enchantClipboard = new ArrayList<>();
    static final List<AttrEntry> attrClipboard = new ArrayList<>();

    // 按压 / 拖拽
    private int pressCol = -1, pressRow = -1, pressX, pressY;
    private boolean dragging;
    private int dragCol = -1, dragFrom = -1, dragTo = -1;

    private String statusMsg = "";
    private long statusUntil;
    private long lastSoundTick;

    /** 每帧重建的命中区：{x, y, col, viewIdx} */
    private final List<int[]> delRects = new ArrayList<>();
    private final List<int[]> rowRects = new ArrayList<>();
    private ResourceLocation hoveredEnchant;
    private AttrEntry hoveredAttr;

    public ItemEditorScreen() {
        super(Component.translatable("screen.infinitestats.item_editor"));
        loadFromItem();
    }

    /** 仅在构造时从主手物品读取一次，避免子界面返回后覆盖编辑结果。 */
    private void loadFromItem() {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        ItemStack stack = player.getMainHandItem();
        selectedSlot = player.getInventory().selected;
        itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        expected = ItemEditUtil.editableSnapshot(stack);

        ListTag ench = ItemEditUtil.readEnchantments(stack);
        for (int i = 0; i < ench.size(); i++) {
            CompoundTag c = ench.getCompound(i);
            // 保留除 id/lvl 之外的扩展 NBT（Apotheosis 等模组的附魔附加数据）
            CompoundTag extra = new CompoundTag();
            for (String k : c.getAllKeys()) {
                if (!k.equals("id") && !k.equals("lvl")) extra.put(k, c.get(k).copy());
            }
            enchants.add(new EnchantEntry(c.getString("id"), c.getShort("lvl"), extra));
        }

        ListTag att = ItemEditUtil.readAttributes(stack);
        for (int i = 0; i < att.size(); i++) {
            CompoundTag c = att.getCompound(i);
            String slot = c.contains("Slot") ? c.getString("Slot") : "any";
            attrs.add(new AttrEntry(c.getString("AttributeName"),
                    c.getInt("Operation"), c.getDouble("Amount"), slot, c.copy()));
        }
    }

    // ======================== 初始化 ========================

    @Override
    protected void init() {
        super.init();
        leftPos = Math.max(0, (width - GUI_W) / 2);
        topPos = Math.max(0, (height - GUI_H) / 2);
        rebuildViews();

        addColumnWidgets(0, colX(0));
        addColumnWidgets(1, colX(1));

        addRenderableWidget(button(leftPos + COL_L, topPos + FOOTER_Y, 100, FOOTER_H,
                "screen.infinitestats.item_editor.more",
                b -> minecraft.setScreen(new EditItemMetaScreen(this))));
        addRenderableWidget(button(leftPos + (GUI_W - 120) / 2, topPos + FOOTER_Y, 120, FOOTER_H,
                "screen.infinitestats.item_editor.cancel", b -> onClose()));
        addRenderableWidget(button(leftPos + GUI_W - COL_L - 100, topPos + FOOTER_Y, 100, FOOTER_H,
                "screen.infinitestats.item_editor.apply", b -> apply()));
    }

    private void addColumnWidgets(int col, int colX) {
        EditBox filter = new EditBox(font, colX, topPos + SEARCH_Y, COL_W, SEARCH_H, Component.empty());
        filter.setMaxLength(48);
        filter.setTextColor(EditorUi.PRIMARY);
        filter.setHint(Component.translatable("screen.infinitestats.item_editor.filter_hint"));
        filter.setValue(searchQuery[col]);
        filter.setResponder(v -> { searchQuery[col] = v; rebuildViews(); });
        addRenderableWidget(filter);

        int ty = topPos + TOOLBAR_Y;
        int x = colX;
        addRenderableWidget(button(x, ty, 56, TOOLBAR_H,
                "screen.infinitestats.item_editor.add_short", b -> openSelect(col)));
        x += 60;
        addRenderableWidget(button(x, ty, 44, TOOLBAR_H,
                "screen.infinitestats.item_editor.copy", b -> copyColumn(col)));
        x += 48;
        addRenderableWidget(button(x, ty, 44, TOOLBAR_H,
                "screen.infinitestats.item_editor.paste", b -> pasteColumn(col)));
        x += 48;
        addRenderableWidget(button(x, ty, 42, TOOLBAR_H,
                "screen.infinitestats.item_editor.clear", b -> clearColumn(col)));
    }

    private Button button(int x, int y, int w, int h, String key, Button.OnPress press) {
        return Button.builder(Component.translatable(key), press).bounds(x, y, w, h).build();
    }

    private int colX(int col) {
        return leftPos + (col == 0 ? COL_L : COL_R);
    }

    // ======================== 供子界面调用的数据操作 ========================

    void addEnchant(String id, int level, CompoundTag extra) {
        enchantsChanged = true;
        for (int i = 0; i < enchants.size(); i++) {
            if (enchants.get(i).id().equals(id)) {
                enchants.set(i, new EnchantEntry(id, level, extra));
                afterEdit();
                return;
            }
        }
        enchants.add(new EnchantEntry(id, level, extra));
        afterEdit();
    }

    void replaceEnchant(EnchantEntry old, String id, int level, CompoundTag extra) {
        enchantsChanged = true;
        int idx = enchants.indexOf(old);
        if (idx < 0) enchants.add(new EnchantEntry(id, level, extra));
        else enchants.set(idx, new EnchantEntry(id, level, extra));
        afterEdit();
    }

    void addAttr(String id, int op, double amount, String slot) {
        attrsChanged = true;
        for (int i = 0; i < attrs.size(); i++) {
            AttrEntry a = attrs.get(i);
            if (a.id().equals(id) && a.op() == op && a.slot().equals(slot)) {
                attrs.set(i, new AttrEntry(id, op, amount, slot, a.original()));
                afterEdit();
                return;
            }
        }
        attrs.add(new AttrEntry(id, op, amount, slot, ItemEditUtil.newAttribute(id, op, amount, slot)));
        afterEdit();
    }

    void replaceAttr(AttrEntry old, String id, int op, double amount, String slot) {
        attrsChanged = true;
        int idx = attrs.indexOf(old);
        CompoundTag original = idx < 0 ? ItemEditUtil.newAttribute(id, op, amount, slot) : old.original();
        AttrEntry entry = new AttrEntry(id, op, amount, slot, original);
        if (idx < 0) attrs.add(entry);
        else attrs.set(idx, entry);
        afterEdit();
    }

    void editEnchant(int srcIdx) {
        if (srcIdx < 0 || srcIdx >= enchants.size()) return;
        minecraft.setScreen(new ItemEditSelectScreen(this, false, enchants.get(srcIdx), null));
    }

    void editAttr(int srcIdx) {
        if (srcIdx < 0 || srcIdx >= attrs.size()) return;
        minecraft.setScreen(new ItemEditSelectScreen(this, true, null, attrs.get(srcIdx)));
    }

    /** 子界面提交后刷新过滤视图与滚动范围（旧接口名保留）。 */
    void refreshScroll() {
        afterEdit();
    }

    private void afterEdit() {
        rebuildViews();
    }

    // ======================== 视图 / 滚动 ========================

    private void rebuildViews() {
        filterInto(enchantView, 0, i -> {
            EnchantEntry e = enchants.get(i);
            return enchantDisplayName(e.id()) + " " + e.id();
        });
        filterInto(attrView, 1, i -> {
            AttrEntry a = attrs.get(i);
            return attrDisplayName(a.id()) + " " + a.id() + " " + slotLabel(a.slot());
        });
        enchantMaxScroll = Math.max(0, enchantView.size() - VISIBLE_ROWS);
        attrMaxScroll = Math.max(0, attrView.size() - VISIBLE_ROWS);
        enchantScroll = clamp(enchantScroll, 0, enchantMaxScroll);
        attrScroll = clamp(attrScroll, 0, attrMaxScroll);
    }

    private void filterInto(List<Integer> view, int col, java.util.function.IntFunction<String> text) {
        view.clear();
        String q = searchQuery[col].toLowerCase(Locale.ROOT).trim();
        int size = col == 0 ? enchants.size() : attrs.size();
        for (int i = 0; i < size; i++) {
            if (q.isEmpty() || JechCompat.matches(text.apply(i).toLowerCase(Locale.ROOT), q)) view.add(i);
        }
    }

    private List<Integer> viewOf(int col) {
        return col == 0 ? enchantView : attrView;
    }

    private int scrollOf(int col) {
        return col == 0 ? enchantScroll : attrScroll;
    }

    private void setScrollOf(int col, int value) {
        if (col == 0) enchantScroll = clamp(value, 0, enchantMaxScroll);
        else attrScroll = clamp(value, 0, attrMaxScroll);
    }

    // ======================== 绘制 ========================

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);

        EditorUi.panel(g, leftPos, topPos, GUI_W, GUI_H);
        EditorUi.title(g, font, leftPos, topPos, GUI_W, title.getString());
        drawItemBar(g);

        // 中缝：分隔左右两栏
        EditorUi.dividerV(g, leftPos + COL_L + COL_W + 4, topPos + ITEM_BAR_Y + 4,
                GUI_H - ITEM_BAR_Y - 8);

        delRects.clear();
        rowRects.clear();
        hoveredEnchant = null;
        hoveredAttr = null;

        drawColumn(g, mx, my, 0);
        drawColumn(g, mx, my, 1);

        super.render(g, mx, my, pt);

        if (hoveredEnchant != null) {
            renderEnchantDescription(g, mx, my, hoveredEnchant);
        } else if (hoveredAttr != null) {
            renderAttrTooltip(g, mx, my, hoveredAttr);
        }

        if (System.currentTimeMillis() < statusUntil) {
            g.drawCenteredString(font, statusMsg, leftPos + GUI_W / 2, topPos + GUI_H - 8, EditorUi.DANGER);
        }
    }

    /** 顶部物品预览条：图标 / 名称 / 数量 / 耐久条 / 物品 ID。 */
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

        String id = itemId == null ? "" : itemId.toString();
        g.drawString(font, EditorUi.ellipsize(font, id, 150),
                x + GUI_W - EditorUi.GAP - Math.min(150, font.width(id)), y + 5, EditorUi.SECONDARY);
    }

    private void drawColumn(GuiGraphics g, int mx, int my, int col) {
        boolean isAttr = col == 1;
        int colX = colX(col);
        List<Integer> view = viewOf(col);
        int total = isAttr ? attrs.size() : enchants.size();

        // 分区标题 + 计数（过滤时显示 命中/总数）
        EditorUi.section(g, font, colX, topPos + SECTION_Y + 5,
                Component.translatable(isAttr
                        ? "screen.infinitestats.item_editor.section_attr"
                        : "screen.infinitestats.item_editor.section_enchant").getString());
        String count = view.size() == total
                ? "(" + total + ")"
                : view.size() + "/" + total;
        g.drawString(font, count, colX + COL_W - font.width(count), topPos + SECTION_Y + 5,
                view.size() == total ? EditorUi.SECONDARY : EditorUi.INFO);

        int ly = topPos + LIST_Y;
        int scroll = scrollOf(col);

        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int viewIdx = row + scroll;
            if (viewIdx >= view.size()) break;
            int srcIdx = view.get(viewIdx);
            int ry = ly + row * EditorUi.ROW_H;
            boolean hover = mx >= colX && mx < colX + LIST_W
                    && my >= ry && my < ry + EditorUi.ROW_H - 2;
            boolean dragTarget = dragging && dragCol == col && dragTo == viewIdx;

            int bg = dragTarget ? EditorUi.BG_ROW_DRAG
                    : hover ? EditorUi.BG_ROW_HOVER : EditorUi.BG_ROW;
            g.fill(colX, ry, colX + LIST_W, ry + EditorUi.ROW_H - 2, bg);

            if (isAttr) {
                AttrEntry a = attrs.get(srcIdx);
                if (hover) hoveredAttr = a;
                g.drawString(font, EditorUi.ellipsize(font, attrDisplayName(a.id()), LIST_W - 26),
                        colX + 5, ry + 3, EditorUi.PRIMARY);
                String info = OP_LABELS[clampOp(a.op())] + " " + EditorUi.amount(a.amount())
                        + " · " + slotLabel(a.slot());
                g.drawString(font, EditorUi.ellipsize(font, info, LIST_W - 26),
                        colX + 5, ry + 12, EditorUi.SECONDARY);
            } else {
                EnchantEntry e = enchants.get(srcIdx);
                ResourceLocation rl = ResourceLocation.tryParse(e.id());
                if (hover && rl != null) hoveredEnchant = rl;

                int delX = colX + LIST_W - EditorUi.DEL_SIZE - 1;
                String lvl = "Lv." + e.level();
                int lvX = delX - 4 - font.width(lvl);
                g.drawString(font, EditorUi.ellipsize(font, enchantDisplayName(e.id()), lvX - colX - 10),
                        colX + 5, ry + 6, EditorUi.PRIMARY);
                g.drawString(font, lvl, lvX, ry + 6, EditorUi.GOLD);
                // 带扩展 NBT 的条目标记
                if (!e.extra().isEmpty()) g.drawString(font, "+", lvX - 8, ry + 6, EditorUi.INFO);
            }

            rowRects.add(new int[]{colX, ry, LIST_W, EditorUi.ROW_H - 2, col, viewIdx});
            delRects.add(drawDel(g, mx, my, col, viewIdx,
                    colX + LIST_W - EditorUi.DEL_SIZE - 1, ry + 2));
        }

        if (view.isEmpty()) {
            String hint = searchQuery[col].isBlank()
                    ? Component.translatable("screen.infinitestats.item_editor.none").getString()
                    : Component.translatable("screen.infinitestats.item_editor.no_match").getString();
            g.drawString(font, hint, colX + 5, ly + 6, EditorUi.SECONDARY);
        }

        EditorUi.scrollbar(g, mx, my, colX + COL_W - EditorUi.SCROLL_W,
                ly, LIST_H - 2, scroll, col == 0 ? enchantMaxScroll : attrMaxScroll, VISIBLE_ROWS);

        EditorUi.dividerH(g, colX, topPos + TOOLBAR_Y - 4, COL_W);
    }

    private int[] drawDel(GuiGraphics g, int mx, int my, int col, int viewIdx, int dx, int dy) {
        boolean hover = mx >= dx && mx < dx + EditorUi.DEL_SIZE
                && my >= dy && my < dy + EditorUi.DEL_SIZE;
        g.fill(dx, dy, dx + EditorUi.DEL_SIZE, dy + EditorUi.DEL_SIZE,
                hover ? EditorUi.BG_DEL_HOVER : EditorUi.BG_DEL);
        g.drawCenteredString(font, "×", dx + EditorUi.DEL_SIZE / 2, dy + 4, EditorUi.DANGER);
        return new int[]{dx, dy, col, viewIdx};
    }

    private void renderAttrTooltip(GuiGraphics g, int mx, int my, AttrEntry a) {
        List<String> lines = new ArrayList<>();
        lines.add("§e" + attrDisplayName(a.id()));
        lines.add("§7原始 ID: " + a.id());
        lines.add("§7操作: " + OP_LABELS[clampOp(a.op())] + "  §7数值: " + EditorUi.amount(a.amount()));
        lines.add("§7生效槽位: " + slotLabel(a.slot()));
        EditorUi.tooltip(g, mx, my, lines, EditorUi.INFO);
    }

    // ======================== 输入 ========================

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            if (handleDelClick((int) mx, (int) my)) return true;

            int col = colAt(mx);
            int row = rowIndexAt(my);
            if (col >= 0 && row >= 0) {
                int viewIdx = row + scrollOf(col);
                if (viewIdx < viewOf(col).size()) {
                    pressCol = col;
                    pressRow = viewIdx;
                    pressX = (int) mx;
                    pressY = (int) my;
                    dragging = false;
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button == 0 && pressCol >= 0) {
            if (!dragging && (Math.abs(mx - pressX) > DRAG_THRESHOLD
                    || Math.abs(my - pressY) > DRAG_THRESHOLD)) {
                dragging = true;
                dragCol = pressCol;
                dragFrom = pressRow;
                dragTo = pressRow;
            }
            if (dragging) {
                int row = rowIndexAt(my);
                if (row >= 0 && colAt(mx) == dragCol) {
                    int size = viewOf(dragCol).size();
                    if (size > 0) dragTo = Math.min(size - 1, row + scrollOf(dragCol));
                }
                return true;
            }
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && pressCol >= 0) {
            if (dragging) {
                if (dragTo >= 0 && dragTo != dragFrom) moveInList(dragCol, dragFrom, dragTo);
            } else {
                activateRow(pressCol, pressRow);
            }
            resetPress();
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int col = colAt(mx);
        if (col < 0) return super.mouseScrolled(mx, my, delta);
        setScrollOf(col, scrollOf(col) + (delta > 0 ? -1 : 1));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private int colAt(double mx) {
        for (int col = 0; col < 2; col++) {
            int x = colX(col);
            if (mx >= x && mx < x + COL_W) return col;
        }
        return -1;
    }

    /** 列表区域内的行号（0 起，未加滚动偏移）；不在列表区域返回 -1。 */
    private int rowIndexAt(double my) {
        int y = topPos + LIST_Y;
        if (my < y || my >= y + LIST_H) return -1;
        return (int) ((my - y) / EditorUi.ROW_H);
    }

    private void resetPress() {
        pressCol = -1;
        pressRow = -1;
        dragging = false;
        dragCol = -1;
        dragFrom = -1;
        dragTo = -1;
    }

    private void activateRow(int col, int viewIdx) {
        List<Integer> view = viewOf(col);
        if (viewIdx < 0 || viewIdx >= view.size()) return;
        int srcIdx = view.get(viewIdx);
        if (col == 0) editEnchant(srcIdx);
        else editAttr(srcIdx);
    }

    private boolean handleDelClick(int mx, int my) {
        for (int[] r : delRects) {
            if (mx >= r[0] && mx < r[0] + EditorUi.DEL_SIZE
                    && my >= r[1] && my < r[1] + EditorUi.DEL_SIZE) {
                int col = r[2], viewIdx = r[3];
                List<Integer> view = viewOf(col);
                if (viewIdx < 0 || viewIdx >= view.size()) return true;
                int srcIdx = view.get(viewIdx);
                if (col == 0) {
                    enchants.remove(srcIdx);
                    enchantsChanged = true;
                } else {
                    attrs.remove(srcIdx);
                    attrsChanged = true;
                }
                rebuildViews();
                playClick();
                return true;
            }
        }
        return false;
    }

    /** 视图内拖拽排序：把源下标条目移动到目标视图位置。 */
    private void moveInList(int col, int fromView, int toView) {
        List<Integer> view = viewOf(col);
        if (fromView < 0 || toView < 0 || fromView >= view.size() || toView >= view.size()) return;
        int from = view.get(fromView), to = view.get(toView);
        if (from == to) return;
        if (col == 0) {
            enchants.add(to, enchants.remove(from));
            enchantsChanged = true;
        } else {
            attrs.add(to, attrs.remove(from));
            attrsChanged = true;
        }
        rebuildViews();
    }

    // ======================== 批量操作 ========================

    private void openSelect(int col) {
        minecraft.setScreen(new ItemEditSelectScreen(this, col == 1, null, null));
    }

    private void copyColumn(int col) {
        if (col == 0) {
            if (enchants.isEmpty()) { setStatus(Component.translatable(
                    "screen.infinitestats.item_editor.nothing_to_copy").getString()); return; }
            enchantClipboard.clear();
            enchantClipboard.addAll(enchants);
        } else {
            if (attrs.isEmpty()) { setStatus(Component.translatable(
                    "screen.infinitestats.item_editor.nothing_to_copy").getString()); return; }
            attrClipboard.clear();
            attrClipboard.addAll(attrs);
        }
        playClick();
        setStatus(Component.translatable("screen.infinitestats.item_editor.copied").getString());
    }

    private void pasteColumn(int col) {
        if (col == 0) {
            if (enchantClipboard.isEmpty()) { setStatus(Component.translatable(
                    "screen.infinitestats.item_editor.clipboard_empty").getString()); return; }
            for (EnchantEntry e : enchantClipboard) {
                boolean merged = false;
                for (int i = 0; i < enchants.size(); i++) {
                    if (enchants.get(i).id().equals(e.id())) {
                        enchants.set(i, e);
                        merged = true;
                        break;
                    }
                }
                if (!merged) enchants.add(e);
            }
            enchantsChanged = true;
        } else {
            if (attrClipboard.isEmpty()) { setStatus(Component.translatable(
                    "screen.infinitestats.item_editor.clipboard_empty").getString()); return; }
            for (AttrEntry a : attrClipboard) {
                boolean merged = false;
                for (int i = 0; i < attrs.size(); i++) {
                    AttrEntry cur = attrs.get(i);
                    if (cur.id().equals(a.id()) && cur.op() == a.op() && cur.slot().equals(a.slot())) {
                        attrs.set(i, new AttrEntry(a.id(), a.op(), a.amount(), a.slot(), cur.original()));
                        merged = true;
                        break;
                    }
                }
                if (!merged) {
                    attrs.add(new AttrEntry(a.id(), a.op(), a.amount(), a.slot(),
                            ItemEditUtil.newAttribute(a.id(), a.op(), a.amount(), a.slot())));
                }
            }
            attrsChanged = true;
        }
        rebuildViews();
        playClick();
        setStatus(Component.translatable("screen.infinitestats.item_editor.pasted").getString());
    }

    private void clearColumn(int col) {
        if (col == 0) {
            if (enchants.isEmpty()) return;
            enchants.clear();
            enchantsChanged = true;
        } else {
            if (attrs.isEmpty()) return;
            attrs.clear();
            attrsChanged = true;
        }
        rebuildViews();
        playClick();
        setStatus(Component.translatable("screen.infinitestats.item_editor.cleared").getString());
    }

    // ======================== 提交 ========================

    private void apply() {
        var player = Minecraft.getInstance().player;
        if (player == null || player.getMainHandItem().isEmpty()) {
            setStatus(Component.translatable("screen.infinitestats.item_editor.empty").getString());
            return;
        }
        if (!enchantsChanged && !attrsChanged) {
            setStatus(Component.translatable("screen.infinitestats.item_editor.no_changes").getString());
            return;
        }
        ListTag en = null;
        if (enchantsChanged) {
            en = new ListTag();
            for (EnchantEntry e : enchants) {
                CompoundTag entry = e.extra().copy();
                entry.putString("id", e.id());
                entry.putShort("lvl", (short) Math.max(1, Math.min(e.level(), Short.MAX_VALUE)));
                en.add(entry);
            }
        }
        ListTag at = null;
        if (attrsChanged) {
            at = new ListTag();
            for (AttrEntry a : attrs) {
                CompoundTag entry = a.original().copy();
                ItemEditUtil.updateAttribute(entry, a.op(), a.amount(), a.slot());
                at.add(entry);
            }
        }
        NetworkHandler.CHANNEL.sendToServer(new EditItemPacket(selectedSlot, itemId, expected, en, at));
        // 保存结果由服务端提示；再次编辑重新读取服务端已同步的物品。
        onClose();
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

    // ======================== 工具 ========================

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private static int clampOp(int op) { return clamp(op, 0, OP_LABELS.length - 1); }

    static int indexOf(String[] arr, String v) {
        for (int i = 0; i < arr.length; i++) if (arr[i].equals(v)) return i;
        return -1;
    }

    String enchantDisplayName(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl != null) {
            Enchantment e = ForgeRegistries.ENCHANTMENTS.getValue(rl);
            if (e != null) return Component.translatable(e.getDescriptionId()).getString();
        }
        return id;
    }

    private String attrDisplayName(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl != null) {
            Attribute attr = ForgeRegistries.ATTRIBUTES.getValue(rl);
            if (attr != null) {
                String descId = attr.getDescriptionId();
                String translated = Component.translatable(descId).getString();
                if (!translated.equals(descId)) return translated;
            }
            // 无翻译时显示可读简化名
            String path = rl.getPath().replace('_', ' ');
            if (path.length() > 28) path = path.substring(0, 26) + "…";
            return rl.getNamespace() + ": " + path;
        }
        return id;
    }

    private String slotLabel(String id) {
        int i = indexOf(SLOT_IDS, id);
        return i >= 0 ? SLOT_LABELS[i] : id;
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    // ======================== 附魔描述（兼容 Enchantment-Descriptions） ========================

    /** 查询某附魔的描述文本。Enchantment-Descriptions 将其存放在本地化键
     *  <code>enchantment.&lt;命名空间&gt;.&lt;路径&gt;.desc</code> 中；
     *  若该键不存在（未安装该模组或该附魔无描述）则返回 null。 */
    static String enchantDescription(ResourceLocation rl) {
        String key = "enchantment." + rl.getNamespace() + "." + rl.getPath() + ".desc";
        String val = Language.getInstance().getOrDefault(key, "");
        if (val == null || val.isEmpty() || val.equals(key)) return null;
        return val;
    }

    /** 在鼠标附近绘制附魔描述浮窗（多行自动折行），供选择列表与编辑列表 hover 时调用。 */
    static void renderEnchantDescription(GuiGraphics g, int mx, int my, ResourceLocation rl) {
        String desc = enchantDescription(rl);
        if (desc == null) return;
        Font font = Minecraft.getInstance().font;
        int maxWidth = 220;
        List<String> lines = new ArrayList<>();
        for (String para : desc.split("\n", -1)) {
            if (para.isEmpty()) { lines.add(""); continue; }
            StringBuilder cur = new StringBuilder();
            for (String word : para.split(" ")) {
                String test = cur.length() == 0 ? word : cur + " " + word;
                if (font.width(test) > maxWidth && cur.length() > 0) {
                    lines.add(cur.toString());
                    cur = new StringBuilder(word);
                } else {
                    cur = new StringBuilder(test);
                }
            }
            if (cur.length() > 0) lines.add(cur.toString());
        }
        EditorUi.tooltip(g, mx, my, lines, EditorUi.ACCENT);
    }
}
