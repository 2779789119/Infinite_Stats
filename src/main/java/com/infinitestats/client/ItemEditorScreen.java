package com.infinitestats.client;

import com.infinitestats.network.EditItemPacket;
import com.infinitestats.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.Font;
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
 * 物品编辑器 GUI — 编辑玩家主手物品的附魔与属性修饰符
 * <p>
 * 左栏编辑附魔，右栏编辑属性修饰符（属性 / 数值 / 操作 / 槽位）。
 * 点击「添加附魔 / 添加属性」会弹出可搜索的选择列表，选中条目后再填数值确认，
 * 不再需要手敲 ID。点击「应用」把当前列表整体发送给服务器重写物品 NBT。
 */
public class ItemEditorScreen extends Screen {

    static final String[] OP_LABELS = {"加算", "乘基", "乘总"};
    static final String[] SLOT_LABELS = {"任意", "主手", "副手", "脚", "腿", "胸", "头"};
    static final String[] SLOT_IDS = {"any", "mainhand", "offhand", "feet", "legs", "chest", "head"};

    record EnchantEntry(String id, int level, CompoundTag extra) {
        EnchantEntry(String id, int level) { this(id, level, new CompoundTag()); }
    }
    private record AttrEntry(String id, int op, double amount, String slot) {}

    private static final int GUI_WIDTH = 400;
    private static final int GUI_HEIGHT = 340;
    private static final int HEADER_H = 26;
    private static final int ROW_H = 22;
    private static final int LIST_TOP = 54;
    private static final int MAX_VISIBLE_ENCHANT = 6;
    private static final int MAX_VISIBLE_ATTR = 5;

    private static final int TEXT_PRIMARY = 0xFFE2E8F0;
    private static final int TEXT_SECONDARY = 0xFF94A3B8;
    private static final int TEXT_GOLD = 0xFFFFD166;
    private static final int TEXT_DANGER = 0xFFF87171;
    private static final int BG_PANEL = 0xE81A1A2E;
    private static final int BG_HEADER = 0xFF1E293B;
    private static final int BG_ROW = 0x50252535;
    private static final int BG_DEL = 0x504A2A1A;
    private static final int BG_DEL_HOVER = 0x80AA4040;
    private static final int BG_DIVIDER = 0x40252540;

    private int leftPos, topPos;

    private final List<EnchantEntry> enchants = new ArrayList<>();
    private final List<AttrEntry> attrs = new ArrayList<>();
    private int enchantScroll, attrScroll;
    private int enchantMaxScroll, attrMaxScroll;

    private String statusMsg = "";
    private long statusUntil;
    private long lastSoundTick;

    private final List<int[]> enchantDelRects = new ArrayList<>();
    private final List<int[]> attrDelRects = new ArrayList<>();
    private final List<int[]> enchantRowRects = new ArrayList<>();
    private ResourceLocation hoveredEnchantRl;
    private AttrEntry hoveredAttr;

    public ItemEditorScreen() {
        super(Component.translatable("screen.infinitestats.item_editor"));
        loadFromItem();
    }

    /** 仅在构造时从主手物品读取一次，避免子界面返回后覆盖编辑结果 */
    private void loadFromItem() {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        ItemStack stack = player.getMainHandItem();
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            if (tag.contains("Enchantments", Tag.TAG_LIST)) {
                ListTag l = tag.getList("Enchantments", Tag.TAG_COMPOUND);
                for (int i = 0; i < l.size(); i++) {
                    CompoundTag c = l.getCompound(i);
                    // 保留除 id/lvl 之外的扩展 NBT（Apotheosis 等模组的附魔附加数据）
                    CompoundTag extra = new CompoundTag();
                    for (String k : c.getAllKeys()) {
                        if (!k.equals("id") && !k.equals("lvl")) extra.put(k, c.get(k));
                    }
                    enchants.add(new EnchantEntry(c.getString("id"), c.getShort("lvl"), extra));
                }
            }
            if (tag.contains("AttributeModifiers", Tag.TAG_LIST)) {
                ListTag l = tag.getList("AttributeModifiers", Tag.TAG_COMPOUND);
                for (int i = 0; i < l.size(); i++) {
                    CompoundTag c = l.getCompound(i);
                    String slot = c.contains("Slot") ? c.getString("Slot") : "any";
                    attrs.add(new AttrEntry(c.getString("AttributeName"),
                            c.getInt("Operation"), c.getDouble("Amount"), slot));
                }
            }
        }
    }

    @Override
    protected void init() {
        super.init();
        leftPos = (width - GUI_WIDTH) / 2;
        topPos = (height - GUI_HEIGHT) / 2;
        updateScrollBounds();

        int x0 = leftPos + 8;
        int x1 = leftPos + 206;

        addRenderableWidget(makeButton(x0, topPos + 190, 186, 16,
                Component.translatable("screen.infinitestats.item_editor.add_enchant"),
                b -> minecraft.setScreen(new ItemEditSelectScreen(this, false))));
        addRenderableWidget(makeButton(x1, topPos + 190, 186, 16,
                Component.translatable("screen.infinitestats.item_editor.add_attr"),
                b -> minecraft.setScreen(new ItemEditSelectScreen(this, true))));

        addRenderableWidget(makeButton(leftPos + 8, topPos + GUI_HEIGHT - 28, 100, 18,
                Component.translatable("screen.infinitestats.item_editor.more"),
                b -> minecraft.setScreen(new EditItemMetaScreen(this))));
        addRenderableWidget(makeButton(leftPos + 116, topPos + GUI_HEIGHT - 28, 100, 18,
                Component.translatable("screen.infinitestats.item_editor.apply"), b -> apply()));
        addRenderableWidget(makeButton(leftPos + 224, topPos + GUI_HEIGHT - 28, 100, 18,
                Component.translatable("screen.infinitestats.item_editor.cancel"), b -> onClose()));
    }

    private Button makeButton(int x, int y, int w, int h, Component msg, Button.OnPress press) {
        return Button.builder(msg, press).bounds(x, y, w, h).build();
    }

    // ======================== 供子界面调用的数据操作 ========================

    void addEnchant(String id, int level, CompoundTag extra) {
        for (int i = 0; i < enchants.size(); i++) {
            if (enchants.get(i).id.equals(id)) {
                enchants.set(i, new EnchantEntry(id, level, extra));
                return;
            }
        }
        enchants.add(new EnchantEntry(id, level, extra));
    }

    void replaceEnchant(EnchantEntry old, String id, int level, CompoundTag extra) {
        int idx = enchants.indexOf(old);
        if (idx < 0) enchants.add(new EnchantEntry(id, level, extra));
        else enchants.set(idx, new EnchantEntry(id, level, extra));
    }

    void editEnchant(int idx) {
        if (idx < 0 || idx >= enchants.size()) return;
        minecraft.setScreen(new ItemEditSelectScreen(this, false, enchants.get(idx)));
    }

    void addAttr(String id, int op, double amount, String slot) {
        for (int i = 0; i < attrs.size(); i++) {
            AttrEntry a = attrs.get(i);
            if (a.id.equals(id) && a.op == op && a.slot.equals(slot)) {
                attrs.set(i, new AttrEntry(id, op, amount, slot));
                return;
            }
        }
        attrs.add(new AttrEntry(id, op, amount, slot));
    }

    void refreshScroll() {
        updateScrollBounds();
    }

    // ======================== 渲染 ========================

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);

        g.fill(leftPos, topPos, leftPos + GUI_WIDTH, topPos + GUI_HEIGHT, BG_PANEL);
        g.fill(leftPos, topPos, leftPos + GUI_WIDTH, topPos + HEADER_H, BG_HEADER);
        g.drawCenteredString(font, title.getString(), leftPos + GUI_WIDTH / 2, topPos + 8, 0xFF4ADE80);

        var player = Minecraft.getInstance().player;
        if (player != null) {
            ItemStack stack = player.getMainHandItem();
            if (!stack.isEmpty()) {
                String name = stack.getHoverName().getString();
                if (font.width(name) > 150) name = font.plainSubstrByWidth(name, 146) + "…";
                g.drawString(font, name, leftPos + GUI_WIDTH - font.width(name) - 8, topPos + 8, TEXT_SECONDARY);
            } else {
                g.drawString(font,
                        Component.translatable("screen.infinitestats.item_editor.empty").getString(),
                        leftPos + GUI_WIDTH - 120, topPos + 8, TEXT_DANGER);
            }
        }

        g.fill(leftPos + 200, topPos + HEADER_H, leftPos + 201, topPos + GUI_HEIGHT - 30, BG_DIVIDER);

        g.drawString(font, "附魔", leftPos + 8, topPos + 36, TEXT_GOLD);
        g.drawString(font, "属性", leftPos + 206, topPos + 36, TEXT_GOLD);

        enchantDelRects.clear();
        attrDelRects.clear();
        enchantRowRects.clear();
        hoveredEnchantRl = null;
        hoveredAttr = null;
        renderEnchantList(g, mx, my);
        renderAttrList(g, mx, my);

        if (hoveredEnchantRl != null) {
            renderEnchantDescription(g, mx, my, hoveredEnchantRl);
        }

        if (System.currentTimeMillis() < statusUntil) {
            g.drawCenteredString(font, statusMsg, leftPos + GUI_WIDTH / 2, topPos + GUI_HEIGHT - 7, TEXT_DANGER);
        }

        for (var r : renderables) r.render(g, mx, my, pt);

        if (hoveredAttr != null) renderAttrTooltip(g, mx, my);
    }

    /** 悬停在属性条目上时，绘制说明浮窗：中文名 / 原始 ID / 操作 / 数值 / 槽位 */
    private void renderAttrTooltip(GuiGraphics g, int mx, int my) {
        List<String> lines = new ArrayList<>();
        lines.add("§e" + attrDisplayName(hoveredAttr.id));
        lines.add("§7原始 ID: " + hoveredAttr.id);
        lines.add("§7操作: " + OP_LABELS[clampOp(hoveredAttr.op)]
                + "  §7数值: " + hoveredAttr.amount);
        lines.add("§7生效槽位: " + slotLabel(hoveredAttr.slot));

        int lineH = font.lineHeight + 1;
        int boxW = 12;
        for (String l : lines) boxW = Math.max(boxW, font.width(l) + 12);
        int boxH = lines.size() * lineH + 8;
        int bx = mx + 14, by = my + 14;
        int sw = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int sh = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        if (bx + boxW > sw) bx = mx - 14 - boxW;
        if (by + boxH > sh) by = my - 14 - boxH;
        g.fill(bx, by, bx + boxW, by + boxH, 0xE81A1A2E);
        g.fill(bx, by, bx + boxW, by + 1, 0xFF60A5FA);
        int ty = by + 4;
        for (String l : lines) {
            g.drawString(font, l, bx + 6, ty, 0xFFE2E8F0);
            ty += lineH;
        }
    }

    private void renderEnchantList(GuiGraphics g, int mx, int my) {
        int x0 = leftPos + 8;
        int w = 186;
        for (int i = 0; i < MAX_VISIBLE_ENCHANT; i++) {
            int idx = i + enchantScroll;
            if (idx >= enchants.size()) break;
            EnchantEntry e = enchants.get(idx);
            int y = topPos + LIST_TOP + i * ROW_H;
            if (mx >= x0 && mx < x0 + w && my >= y && my < y + ROW_H - 2)
                hoveredEnchantRl = ResourceLocation.tryParse(e.id);
            g.fill(x0, y, x0 + w, y + ROW_H - 2, BG_ROW);

            String name = enchantDisplayName(e.id);
            if (font.width(name) > 126) name = font.plainSubstrByWidth(name, 122) + "…";
            g.drawString(font, name, x0 + 6, y + 4, TEXT_PRIMARY);
            g.drawString(font, "Lv." + e.level, x0 + 6, y + 13, TEXT_GOLD);
            // 标记该附魔带有扩展 NBT
            if (!e.extra.isEmpty()) {
                g.drawString(font, "[+]", x0 + w - 36, y + 13, TEXT_GOLD);
            }

            enchantRowRects.add(new int[]{x0, y, w, ROW_H - 2, idx});
            int[] rect = drawDelButton(g, mx, my, x0 + w - 18, y + 3);
            rect[2] = idx;
            enchantDelRects.add(rect);
        }
    }

    private void renderAttrList(GuiGraphics g, int mx, int my) {
        int x1 = leftPos + 206;
        int w = 186;
        for (int i = 0; i < MAX_VISIBLE_ATTR; i++) {
            int idx = i + attrScroll;
            if (idx >= attrs.size()) break;
            AttrEntry a = attrs.get(idx);
            int y = topPos + LIST_TOP + i * ROW_H;
            g.fill(x1, y, x1 + w, y + ROW_H - 2, BG_ROW);

            String name = attrDisplayName(a.id);
            if (font.width(name) > 116) name = font.plainSubstrByWidth(name, 112) + "…";
            g.drawString(font, name, x1 + 6, y + 3, TEXT_PRIMARY);
            String info = OP_LABELS[clampOp(a.op)] + " " + a.amount + " [" + slotLabel(a.slot) + "]";
            g.drawString(font, info, x1 + 6, y + 13, TEXT_SECONDARY);

            if (mx >= x1 && mx < x1 + w && my >= y && my < y + ROW_H - 2) hoveredAttr = a;

            int[] rect = drawDelButton(g, mx, my, x1 + w - 18, y + 3);
            rect[2] = idx;
            attrDelRects.add(rect);
        }
    }

    private int[] drawDelButton(GuiGraphics g, int mx, int my, int dx, int dy) {
        boolean hover = mx >= dx && mx < dx + 14 && my >= dy && my < dy + 14;
        g.fill(dx, dy, dx + 14, dy + 14, hover ? BG_DEL_HOVER : BG_DEL);
        g.drawCenteredString(font, "x", dx + 7, dy + 3, TEXT_DANGER);
        return new int[]{dx, dy, 0};
    }

    // ======================== 输入 ========================

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            if (handleDelClick((int) mx, (int) my)) return true;
            if (handleRowClick((int) mx, (int) my)) return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private boolean handleRowClick(int mx, int my) {
        for (int[] r : enchantRowRects) {
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                editEnchant(r[4]);
                return true;
            }
        }
        return false;
    }

    private boolean handleDelClick(int mx, int my) {
        for (int[] r : enchantDelRects) {
            if (mx >= r[0] && mx < r[0] + 14 && my >= r[1] && my < r[1] + 14) {
                enchants.remove(r[2]);
                updateScrollBounds();
                playClick();
                return true;
            }
        }
        for (int[] r : attrDelRects) {
            if (mx >= r[0] && mx < r[0] + 14 && my >= r[1] && my < r[1] + 14) {
                attrs.remove(r[2]);
                updateScrollBounds();
                playClick();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        boolean left = mx < leftPos + 200;
        if (left) {
            if (enchantMaxScroll > 0)
                enchantScroll = clamp(enchantScroll + (delta > 0 ? -1 : 1), 0, enchantMaxScroll);
        } else {
            if (attrMaxScroll > 0)
                attrScroll = clamp(attrScroll + (delta > 0 ? -1 : 1), 0, attrMaxScroll);
        }
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

    // ======================== 逻辑 ========================

    private void apply() {
        var player = Minecraft.getInstance().player;
        if (player != null && player.getMainHandItem().isEmpty()) {
            setStatus(Component.translatable("screen.infinitestats.item_editor.empty").getString());
            return;
        }
        List<EditItemPacket.EnchantData> en = new ArrayList<>();
        for (EnchantEntry e : enchants) en.add(new EditItemPacket.EnchantData(e.id, e.level, e.extra));
        List<EditItemPacket.AttrData> at = new ArrayList<>();
        for (AttrEntry a : attrs) at.add(new EditItemPacket.AttrData(a.id, a.op, a.amount, a.slot));
        NetworkHandler.CHANNEL.sendToServer(new EditItemPacket(en, at));
        setStatus(Component.translatable("screen.infinitestats.item_editor.applied").getString());
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

    private void updateScrollBounds() {
        enchantMaxScroll = Math.max(0, enchants.size() - MAX_VISIBLE_ENCHANT);
        attrMaxScroll = Math.max(0, attrs.size() - MAX_VISIBLE_ATTR);
        enchantScroll = clamp(enchantScroll, 0, enchantMaxScroll);
        attrScroll = clamp(attrScroll, 0, attrMaxScroll);
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
            String path = rl.getPath().replace('_', ' ').replace('/', '/');
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
        int lineH = font.lineHeight + 1;
        int boxW = 12;
        for (String l : lines) boxW = Math.max(boxW, font.width(l) + 12);
        int boxH = lines.size() * lineH + 8;
        int bx = mx + 14, by = my + 14;
        int sw = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int sht = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        if (bx + boxW > sw) bx = mx - 14 - boxW;
        if (by + boxH > sht) by = my - 14 - boxH;
        g.fill(bx, by, bx + boxW, by + boxH, 0xE81A1A2E);
        g.fill(bx, by, bx + boxW, by + 1, 0xFF4ADE80);
        int ty = by + 4;
        for (String l : lines) {
            g.drawString(font, l, bx + 6, ty, 0xFFE2E8F0);
            ty += lineH;
        }
    }
}
