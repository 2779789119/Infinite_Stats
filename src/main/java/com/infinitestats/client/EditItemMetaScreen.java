package com.infinitestats.client;

import com.infinitestats.network.EditItemMetaPacket;
import com.infinitestats.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * 物品元数据编辑子界面 — 编辑显示名称、Lore 描述、剩余耐久与堆叠数量。
 * <p>
 * 重写要点：
 * <ul>
 *   <li>与主界面共用 {@link EditorUi} 配色与尺寸，顶部同样带物品预览条；</li>
 *   <li>面板高度随 Lore 行数自适应，行内自带删除按钮（可删除任意一行，不再只能删末行）；</li>
 *   <li>Lore 上限 {@value #MAX_LORE_LINES} 行，超出时给出提示而不是把控件挤出面板。</li>
 * </ul>
 */
public class EditItemMetaScreen extends Screen {

    // ======================== 布局常量 ========================

    private static final int GUI_W = 420;
    private static final int ITEM_BAR_H = 34;
    private static final int BOX_H = 18;
    private static final int LORE_ROW_H = 22;
    private static final int MAX_LORE_LINES = 8;

    private static final int CONTENT_TOP = EditorUi.HEADER_H + ITEM_BAR_H + 6;      // 64
    private static final int NAME_LABEL_Y = CONTENT_TOP;                            // 64
    private static final int NAME_BOX_Y = NAME_LABEL_Y + 12;                        // 76
    private static final int LORE_LABEL_Y = NAME_BOX_Y + BOX_H + 10;                // 104
    private static final int LORE_BOX_Y = LORE_LABEL_Y + 12;                        // 116
    private static final int FOOTER_H = 20;
    private static final int FOOTER_GAP = 8;
    private static final int DEL_SIZE = 18;
    private static final int BTN_ADD_W = 76;

    // ======================== 状态 ========================

    private final ItemEditorScreen parent;
    private int leftPos, topPos, guiH;

    private EditBox nameBox;
    private final List<EditBox> loreBoxes = new ArrayList<>();
    private final List<String> loreLines = new ArrayList<>();
    private EditBox damageBox;
    private EditBox countBox;

    private boolean hasDamage;
    private int curDamage;
    private int curCount;
    private String originalName = "";
    private final List<String> originalLore = new ArrayList<>();
    private String damageInput = "";
    private String countInput = "";

    private String nameInit = "";

    /** Lore 行内删除按钮命中区：{x, y, lineIndex} */
    private final List<int[]> loreDelRects = new ArrayList<>();

    private String statusMsg = "";
    private long statusUntil;

    public EditItemMetaScreen(ItemEditorScreen parent) {
        super(Component.translatable("screen.infinitestats.item_editor.meta"));
        this.parent = parent;
        loadFromItem();
    }

    private void loadFromItem() {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) return;

        hasDamage = stack.getMaxDamage() > 0;
        curDamage = stack.getDamageValue();
        curCount = stack.getCount();
        damageInput = Integer.toString(curDamage);
        countInput = Integer.toString(curCount);

        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("display", Tag.TAG_COMPOUND)) {
            CompoundTag display = tag.getCompound("display");
            if (display.contains("Name", Tag.TAG_STRING)) {
                try {
                    Component c = Component.Serializer.fromJson(display.getString("Name"));
                    if (c != null) nameInit = c.getString();
                } catch (Exception ignored) { }
            }
            if (display.contains("Lore", Tag.TAG_LIST)) {
                var lore = display.getList("Lore", Tag.TAG_STRING);
                for (int i = 0; i < lore.size(); i++) {
                    try {
                        Component c = Component.Serializer.fromJson(lore.getString(i));
                        loreLines.add(c != null ? c.getString() : lore.getString(i));
                    } catch (Exception e) {
                        loreLines.add(lore.getString(i));
                    }
                }
            }
        }
        originalName = nameInit;
        originalLore.addAll(loreLines);
        if (loreLines.isEmpty()) loreLines.add("");
    }

    // ======================== 初始化 ========================

    @Override
    protected void init() {
        super.init();
        int statsY = LORE_BOX_Y + LORE_ROW_H * Math.max(1, loreLines.size()) + 10;
        int footerY = statsY + BOX_H + 10;
        guiH = footerY + FOOTER_H + FOOTER_GAP;
        leftPos = Math.max(0, (width - GUI_W) / 2);
        topPos = Math.max(0, (height - guiH) / 2);
        loreDelRects.clear();
        buildWidgets(statsY, footerY);
    }

    private void buildWidgets(int statsY, int footerY) {
        int x = leftPos + EditorUi.GAP;
        int w = GUI_W - EditorUi.GAP * 2;
        int boxY = topPos + statsY;

        nameBox = new EditBox(font, x, topPos + NAME_BOX_Y, w, BOX_H, Component.empty());
        nameBox.setMaxLength(128);
        nameBox.setTextColor(EditorUi.PRIMARY);
        nameBox.setValue(nameInit);
        nameBox.setResponder(v -> nameInit = v);
        addRenderableWidget(nameBox);

        int loreBoxW = w - DEL_SIZE - 4;
        for (int i = 0; i < loreLines.size(); i++) {
            int rowY = topPos + LORE_BOX_Y + i * LORE_ROW_H;
            EditBox box = new EditBox(font, x, rowY, loreBoxW, BOX_H, Component.empty());
            box.setMaxLength(256);
            box.setTextColor(EditorUi.PRIMARY);
            box.setValue(loreLines.get(i));
            box.setHint(i == 0
                    ? Component.translatable("screen.infinitestats.item_editor.lore_hint")
                    : Component.empty());
            final int row = i;
            box.setResponder(v -> loreLines.set(row, v));
            addRenderableWidget(box);
            loreBoxes.add(box);
            loreDelRects.add(new int[]{x + loreBoxW + 4, rowY, i});
        }

        // Lore 添加行
        addRenderableWidget(Button.builder(
                        Component.translatable("screen.infinitestats.item_editor.lore_add"),
                        b -> addLoreLine())
                .bounds(leftPos + GUI_W - EditorUi.GAP - BTN_ADD_W, topPos + LORE_LABEL_Y - 2,
                        BTN_ADD_W, 14).build());

        // 耐久 / 数量
        int half = (w - 20) / 2;
        if (hasDamage) {
            damageBox = new EditBox(font, x + 66, boxY, half - 66, BOX_H, Component.empty());
            damageBox.setMaxLength(9);
            damageBox.setTextColor(EditorUi.PRIMARY);
            damageBox.setValue(damageInput);
            damageBox.setResponder(v -> damageInput = v);
            addRenderableWidget(damageBox);
        }

        int cx = x + half + 20;
        countBox = new EditBox(font, cx + 66, boxY, half - 66, BOX_H, Component.empty());
        countBox.setMaxLength(4);
        countBox.setTextColor(EditorUi.PRIMARY);
        countBox.setValue(countInput);
        countBox.setResponder(v -> countInput = v);
        addRenderableWidget(countBox);

        // 底栏
        int by = topPos + footerY;
        addRenderableWidget(Button.builder(
                        Component.translatable("screen.infinitestats.item_editor.back"), b -> onClose())
                .bounds(leftPos + EditorUi.GAP, by, 100, FOOTER_H).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("screen.infinitestats.item_editor.cancel"), b -> onClose())
                .bounds(leftPos + (GUI_W - 120) / 2, by, 120, FOOTER_H).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("screen.infinitestats.item_editor.apply"), b -> apply())
                .bounds(leftPos + GUI_W - EditorUi.GAP - 100, by, 100, FOOTER_H).build());
    }

    private void addLoreLine() {
        if (loreLines.size() >= MAX_LORE_LINES) {
            setStatus(Component.translatable("screen.infinitestats.item_editor.lore_limit",
                    MAX_LORE_LINES).getString());
            return;
        }
        loreLines.add("");
        rebuildWidgets();
    }

    private void removeLoreLine(int index) {
        if (index < 0 || index >= loreLines.size()) return;
        loreLines.remove(index);
        if (loreLines.isEmpty()) loreLines.add("");
        rebuildWidgets();
    }

    // ======================== 绘制 ========================

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        EditorUi.panel(g, leftPos, topPos, GUI_W, guiH);
        EditorUi.title(g, font, leftPos, topPos, GUI_W, title.getString());
        drawItemBar(g);

        EditorUi.dividerH(g, leftPos + 4, topPos + ITEM_BAR_Y_END() , GUI_W - 8);

        EditorUi.section(g, font, leftPos + EditorUi.GAP - 6, topPos + NAME_LABEL_Y,
                Component.translatable("screen.infinitestats.item_editor.name").getString());
        EditorUi.section(g, font, leftPos + EditorUi.GAP - 6, topPos + LORE_LABEL_Y,
                Component.translatable("screen.infinitestats.item_editor.lore").getString());

        int x = leftPos + EditorUi.GAP;
        int w = GUI_W - EditorUi.GAP * 2;

        // Lore 行内删除按钮
        for (int[] r : loreDelRects) {
            if (r[2] >= loreBoxes.size()) continue;
            boolean hover = mx >= r[0] && mx < r[0] + DEL_SIZE && my >= r[1] && my < r[1] + DEL_SIZE;
            g.fill(r[0], r[1], r[0] + DEL_SIZE, r[1] + DEL_SIZE,
                    hover ? EditorUi.BG_DEL_HOVER : EditorUi.BG_DEL);
            g.drawCenteredString(font, "×", r[0] + DEL_SIZE / 2, r[1] + 5, EditorUi.DANGER);
        }

        // 耐久 / 数量标签
        int statsY = LORE_BOX_Y + LORE_ROW_H * Math.max(1, loreLines.size()) + 10;
        int half = (w - 20) / 2;
        if (hasDamage) {
            g.drawString(font, Component.translatable("screen.infinitestats.item_editor.damage").getString(),
                    x, topPos + statsY + 5, EditorUi.GOLD);
        } else {
            g.drawString(font, Component.translatable("screen.infinitestats.item_editor.no_damage").getString(),
                    x, topPos + statsY + 5, EditorUi.SECONDARY);
        }
        g.drawString(font, Component.translatable("screen.infinitestats.item_editor.count").getString(),
                x + half + 20, topPos + statsY + 5, EditorUi.GOLD);

        super.render(g, mx, my, pt);

        if (System.currentTimeMillis() < statusUntil) {
            g.drawCenteredString(font, statusMsg, leftPos + GUI_W / 2, topPos + guiH - 10, EditorUi.DANGER);
        }
    }

    private int ITEM_BAR_Y_END() {
        return EditorUi.HEADER_H + ITEM_BAR_H;
    }

    /** 顶部物品预览条（与主界面一致）。 */
    private void drawItemBar(GuiGraphics g) {
        int x = leftPos, y = topPos + EditorUi.HEADER_H;
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
    }

    // ======================== 输入 ========================

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (int[] r : loreDelRects) {
                if (mx >= r[0] && mx < r[0] + DEL_SIZE && my >= r[1] && my < r[1] + DEL_SIZE) {
                    removeLoreLine(r[2]);
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
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

    private void apply() {
        var player = Minecraft.getInstance().player;
        if (player == null || player.getMainHandItem().isEmpty()) {
            setStatus(Component.translatable("screen.infinitestats.item_editor.empty").getString());
            return;
        }

        List<String> lore = new ArrayList<>(loreLines);
        if (lore.size() == 1 && lore.get(0).isEmpty() && originalLore.isEmpty()) lore.clear();

        int damage = -1;
        if (hasDamage && damageBox != null) {
            try {
                damage = Integer.parseInt(damageBox.getValue().trim());
            } catch (NumberFormatException ignored) { }
        }
        int count = -1;
        try {
            count = Integer.parseInt(countBox.getValue().trim());
        } catch (NumberFormatException ignored) { }

        boolean changeName = !nameBox.getValue().equals(originalName);
        boolean changeLore = !lore.equals(originalLore);
        if (damage == curDamage) damage = -1;
        if (count == curCount) count = -1;

        if (!changeName && !changeLore && damage < 0 && count < 0) {
            setStatus(Component.translatable("screen.infinitestats.item_editor.no_changes").getString());
            return;
        }
        NetworkHandler.CHANNEL.sendToServer(new EditItemMetaPacket(
                changeName, nameBox.getValue(), changeLore, lore, damage, count));
        setStatus(Component.translatable("screen.infinitestats.item_editor.applied").getString());
    }

    private void setStatus(String msg) {
        statusMsg = msg;
        statusUntil = System.currentTimeMillis() + 2500;
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
