package com.infinitestats.client;

import com.infinitestats.network.EditItemMetaPacket;
import com.infinitestats.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * 物品元数据编辑子界面 — 编辑显示名称、Lore 描述、剩余耐久、堆叠数量。
 * 从主界面「更多属性」按钮进入。
 */
public class EditItemMetaScreen extends Screen {

    private static final int GUI_WIDTH = 360;
    private static final int GUI_HEIGHT = 300;
    private static final int HEADER_H = 26;

    private final ItemEditorScreen parent;
    private int leftPos, topPos;

    private EditBox nameBox;
    private final List<EditBox> loreBoxes = new ArrayList<>();
    private final List<String> loreLines = new ArrayList<>();
    private EditBox damageBox;
    private EditBox countBox;

    private boolean hasDamage;
    private int curDamage;
    private int curCount;

    // 各字段标签的 Y 坐标，供 init() 摆放输入框与 render() 绘制标签共用
    private int nameLabelY, loreLabelY, damageLabelY = -1, countLabelY;

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

        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("display", Tag.TAG_COMPOUND)) {
            CompoundTag display = tag.getCompound("display");
            if (display.contains("Name", Tag.TAG_STRING)) {
                try {
                    Component c = Component.Serializer.fromJson(display.getString("Name"));
                    if (c != null) nameBoxInit = c.getString();
                } catch (Exception ignored) {}
            }
            if (display.contains("Lore", Tag.TAG_LIST)) {
                ListTag lore = display.getList("Lore", Tag.TAG_STRING);
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
        if (loreLines.isEmpty()) loreLines.add("");
    }

    private String nameBoxInit = "";

    @Override
    protected void init() {
        super.init();
        leftPos = (width - GUI_WIDTH) / 2;
        topPos = (height - GUI_HEIGHT) / 2;

        int x = leftPos + 8;
        int w = GUI_WIDTH - 16;

        nameLabelY = topPos + 28;
        nameBox = new EditBox(font, x, nameLabelY + 12, w, 18, Component.empty());
        nameBox.setMaxLength(128);
        nameBox.setValue(nameBoxInit);
        addRenderableWidget(nameBox);

        loreLabelY = nameLabelY + 12 + 18 + 10;
        rebuildLoreBoxes();

        int dy = loreLabelY + 12 + loreBoxes.size() * 22 + 10;
        if (hasDamage) {
            damageLabelY = dy;
            damageBox = new EditBox(font, x, dy + 12, w, 18, Component.empty());
            damageBox.setMaxLength(8);
            damageBox.setValue(String.valueOf(curDamage));
            addRenderableWidget(damageBox);
            dy += 12 + 18 + 10;
        } else {
            damageLabelY = -1;
            addRenderableWidget(makeLabel(
                    Component.translatable("screen.infinitestats.item_editor.no_damage").getString(),
                    x, dy + 12));
            dy += 12 + 18 + 4;
        }

        countLabelY = dy;
        countBox = new EditBox(font, x, dy + 12, w, 18, Component.empty());
        countBox.setMaxLength(4);
        countBox.setValue(String.valueOf(curCount));
        addRenderableWidget(countBox);
        dy += 12 + 18 + 10;

        addRenderableWidget(makeButton(leftPos + 8, topPos + GUI_HEIGHT - 28, 100, 20,
                Component.translatable("screen.infinitestats.item_editor.apply"), b -> apply()));
        addRenderableWidget(makeButton(leftPos + 124, topPos + GUI_HEIGHT - 28, 100, 20,
                Component.translatable("screen.infinitestats.item_editor.cancel"), b -> onClose()));
    }

    private void rebuildLoreBoxes() {
        for (EditBox box : loreBoxes) {
            // 先移除旧的
        }
        loreBoxes.clear();
        int x = leftPos + 8;
        int w = GUI_WIDTH - 16 - 20;
        int y = loreLabelY + 12;
        for (int i = 0; i < loreLines.size(); i++) {
            EditBox box = new EditBox(font, x, y, w, 18, Component.empty());
            box.setMaxLength(256);
            box.setValue(loreLines.get(i));
            addRenderableWidget(box);
            loreBoxes.add(box);
            y += 22;
        }
        // 添加一行 / 删除末行 按钮
        int bx = x + w + 2;
        int by = loreLabelY + 12;
        addRenderableWidget(makeButton(bx, by, 18, 18,
                Component.literal("+"), b -> { loreLines.add(""); rebuildAndKeepFocus(); }));
        if (loreLines.size() > 1) {
            addRenderableWidget(makeButton(bx, by + 22, 18, 18,
                    Component.literal("-"), b -> { loreLines.remove(loreLines.size() - 1); rebuildAndKeepFocus(); }));
        }
    }

    private void rebuildAndKeepFocus() {
        // 重新构建整个界面以反映行数变化
        this.children().clear();
        this.renderables.clear();
        init();
    }

    private Button makeButton(int x, int y, int w, int h, Component msg, Button.OnPress press) {
        return Button.builder(msg, press).bounds(x, y, w, h).build();
    }

    private net.minecraft.client.gui.components.AbstractWidget makeLabel(String text, int x, int y) {
        return new net.minecraft.client.gui.components.AbstractWidget(
                x, y, font.width(text), 12, Component.literal(text)) {
            @Override
            protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                g.drawString(font, text, getX(), getY(), 0xFF94A3B8);
            }
            @Override
            public boolean mouseClicked(double mx, double my, int b) { return false; }
            @Override
            protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput p_259858_) {}
        };
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(leftPos, topPos, leftPos + GUI_WIDTH, topPos + GUI_HEIGHT, 0xE81A1A2E);
        g.fill(leftPos, topPos, leftPos + GUI_WIDTH, topPos + HEADER_H, 0xFF1E293B);
        g.drawCenteredString(font, title.getString(), leftPos + GUI_WIDTH / 2, topPos + 8, 0xFF4ADE80);

        g.drawString(font, Component.translatable("screen.infinitestats.item_editor.name").getString(),
                leftPos + 8, nameLabelY, 0xFFFFD166);
        g.drawString(font, Component.translatable("screen.infinitestats.item_editor.lore").getString(),
                leftPos + 8, loreLabelY, 0xFFFFD166);
        if (hasDamage && damageLabelY >= 0) {
            g.drawString(font, Component.translatable("screen.infinitestats.item_editor.damage").getString(),
                    leftPos + 8, damageLabelY, 0xFFFFD166);
        }
        g.drawString(font, Component.translatable("screen.infinitestats.item_editor.count").getString(),
                leftPos + 8, countLabelY, 0xFFFFD166);

        if (System.currentTimeMillis() < statusUntil) {
            g.drawCenteredString(font, statusMsg, leftPos + GUI_WIDTH / 2, topPos + GUI_HEIGHT - 6, 0xFFF87171);
        }
        super.render(g, mx, my, pt);
    }

    private void apply() {
        var player = Minecraft.getInstance().player;
        if (player != null && player.getMainHandItem().isEmpty()) {
            setStatus(Component.translatable("screen.infinitestats.item_editor.empty").getString());
            return;
        }
        // 收集 Lore 行
        List<String> lore = new ArrayList<>();
        for (EditBox box : loreBoxes) {
            if (!box.getValue().isEmpty()) lore.add(box.getValue());
        }
        int damage = -1;
        if (hasDamage && damageBox != null) {
            try { damage = Integer.parseInt(damageBox.getValue()); } catch (NumberFormatException ignored) {}
        }
        int count = -1;
        try { count = Integer.parseInt(countBox.getValue()); } catch (NumberFormatException ignored) {}

        NetworkHandler.CHANNEL.sendToServer(new EditItemMetaPacket(
                true, nameBox.getValue(), true, lore, damage, count));
        setStatus(Component.translatable("screen.infinitestats.item_editor.applied").getString());
    }

    private void setStatus(String msg) {
        statusMsg = msg;
        statusUntil = System.currentTimeMillis() + 2500;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
