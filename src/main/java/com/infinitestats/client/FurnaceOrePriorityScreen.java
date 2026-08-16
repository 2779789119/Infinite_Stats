package com.infinitestats.client;

import com.infinitestats.furnace.FurnaceOrePriorityMenu;
import com.infinitestats.furnace.PlayerFurnaceData;
import com.infinitestats.network.NetworkHandler;
import com.infinitestats.network.NetworkHandler.FurnaceOrePrioritySyncPacket;
import com.infinitestats.network.NetworkHandler.FurnaceOrePriorityUpdatePacket;
import com.infinitestats.network.NetworkHandler.FurnaceOrePriorityRequestPacket;
import com.infinitestats.network.NetworkHandler.FurnaceOpenPacket;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

@OnlyIn(Dist.CLIENT)
public class FurnaceOrePriorityScreen extends AbstractContainerScreen<FurnaceOrePriorityMenu> {

    private static final int ROW_H = 22;

    // 左右两列布局常量
    private static final int LEFT_COL_X = 10;
    private static final int RIGHT_COL_X = 165;
    private static final int ICON_X_LEFT = LEFT_COL_X + 4;
    private static final int NAME_X_LEFT = LEFT_COL_X + 26;
    private static final int ICON_X_RIGHT = RIGHT_COL_X + 4;
    private static final int NAME_X_RIGHT = RIGHT_COL_X + 26;

    private static final int PRIO_UP_X = LEFT_COL_X + 95;
    private static final int PRIO_DOWN_X = LEFT_COL_X + 117;
    private static final int PRIO_REMOVE_X = LEFT_COL_X + 139;
    private static final int AVAIL_ADD_X = RIGHT_COL_X + 90;

    private final List<AbstractWidget> dynamicButtons = new ArrayList<>();

    private List<String> priorityView = new ArrayList<>();
    private List<String> availableView = new ArrayList<>();

    private int scroll = 0;
    private int contentTop;
    private int contentBottomLimit;

    private final Component title = Component.translatable("gui.infinitestats.furnace.ore_priority.title");
    private final Component subtitle = Component.translatable("gui.infinitestats.furnace.ore_priority.subtitle");
    private final Component prioHeader = Component.translatable("gui.infinitestats.furnace.ore_priority.priority_header");
    private final Component availHeader = Component.translatable("gui.infinitestats.furnace.ore_priority.available");
    private final Component invLabel = Component.translatable("container.inventory");
    private final Component closeLabel = Component.translatable("gui.infinitestats.furnace.ore_priority.close");

    public FurnaceOrePriorityScreen(FurnaceOrePriorityMenu menu, Inventory inv, Component unused) {
        super(menu, inv, Component.translatable("gui.infinitestats.furnace.ore_priority.title"));
        this.imageWidth = 300;
        this.imageHeight = 240;
    }

    @Override
    protected void init() {
        super.init();
        this.contentTop = topPos + 50;
        this.contentBottomLimit = topPos + 150;

        this.dynamicButtons.clear();
        this.addRenderableWidget(Button.builder(closeLabel, b -> closeToFurnace())
                .bounds(leftPos + imageWidth - 56, topPos + 6, 52, 16).build());

        NetworkHandler.CHANNEL.sendToServer(new FurnaceOrePriorityRequestPacket());
        rebuildContent();
    }

    private void closeToFurnace() {
        NetworkHandler.CHANNEL.sendToServer(new FurnaceOpenPacket());
    }

    private static String idOf(ItemStack stack) {
        return ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
    }

    private static boolean isSmeltable(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || stack.isEmpty()) return false;
        return PlayerFurnaceData.hasSmeltRecipe(mc.level, stack);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int max = maxScroll(Math.max(priorityView.size(), availableView.size()));
        if (max > 0) {
            scroll = (int) Math.max(0, Math.min(max, scroll - Math.signum(delta)));
            rebuildContent();
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (Slot slot : menu.slots) {
            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            if (mx >= x && mx < x + 16 && my >= y && my < y + 16
                    && slot instanceof FurnaceOrePriorityMenu.ReadOnlySlot) {
                ItemStack s = slot.getItem();
                if (!s.isEmpty() && isSmeltable(s)) {
                    String id = idOf(s);
                    if (!priorityView.contains(id)) {
                        priorityView.add(id);
                        sendUpdate();
                    }
                }
                return true; // 吞掉点击，防止尝试拿起物品
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_ESCAPE) {
            closeToFurnace();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void sendUpdate() {
        NetworkHandler.CHANNEL.sendToServer(new FurnaceOrePriorityUpdatePacket(new ArrayList<>(priorityView)));
    }

    private void rebuildContent() {
        for (AbstractWidget w : dynamicButtons) this.removeWidget(w);
        dynamicButtons.clear();

        int max = maxScroll(Math.max(priorityView.size(), availableView.size()));
        scroll = Math.max(0, Math.min(scroll, max));

        // 左列：优先放入
        int index = scroll;
        for (int y = contentTop; y + ROW_H <= contentBottomLimit && index < priorityView.size(); y += ROW_H, index++) {
            final int i = index;
            this.addPrioBtn("↑", leftPos + PRIO_UP_X, y, i, -1);
            this.addPrioBtn("↓", leftPos + PRIO_DOWN_X, y, i, 1);
            this.addActionBtn("✕", leftPos + PRIO_REMOVE_X, y, () -> {
                priorityView.remove(i);
                sendUpdate();
            });
        }

        // 右列：可加入
        index = scroll;
        for (int y = contentTop; y + ROW_H <= contentBottomLimit && index < availableView.size(); y += ROW_H, index++) {
            final String id = availableView.get(index);
            this.addActionBtn("＋", leftPos + AVAIL_ADD_X, y, () -> {
                priorityView.add(id);
                sendUpdate();
            });
        }
    }

    private void addPrioBtn(String label, int x, int y, int index, int dir) {
        Button b = Button.builder(Component.literal(label), btn -> {
            int target = index + dir;
            if (target >= 0 && target < priorityView.size()) {
                Collections.swap(priorityView, index, target);
                sendUpdate();
            }
        }).bounds(x, y + 2, 20, 18).build();
        dynamicButtons.add(b);
        this.addRenderableWidget(b);
    }

    private void addActionBtn(String label, int x, int y, Runnable action) {
        Button b = Button.builder(Component.literal(label), btn -> action.run()).bounds(x, y + 2, 20, 18).build();
        dynamicButtons.add(b);
        this.addRenderableWidget(b);
    }

    private int maxScroll(int rows) {
        int visible = (contentBottomLimit - contentTop) / ROW_H;
        return Math.max(0, rows - visible);
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        gfx.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xCC101018);
        gfx.fill(leftPos, topPos, leftPos + imageWidth, topPos + 1, 0xFF3A3A4A);
        gfx.fill(leftPos, topPos + imageHeight - 1, leftPos + imageWidth, topPos + imageHeight, 0xFF3A3A4A);

        // 玩家背包槽位背景（原版风格）
        for (Slot slot : menu.slots) {
            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            gfx.fill(x - 1, y - 1, x + 17, y + 17, 0xFF000000);
            gfx.fill(x, y, x + 16, y + 16, 0xFF2A2A33);
            gfx.fill(x, y, x + 16, y + 1, 0xFF555555);
            gfx.fill(x, y, x + 1, y + 16, 0xFF555555);
            gfx.fill(x + 15, y + 1, x + 16, y + 16, 0xFF111111);
            gfx.fill(x + 1, y + 15, x + 16, y + 16, 0xFF111111);
        }
    }

    private void renderRows(GuiGraphics g) {
        int max = maxScroll(Math.max(priorityView.size(), availableView.size()));
        scroll = Math.max(0, Math.min(scroll, max));

        // 左列：优先放入
        int index = scroll;
        for (int y = contentTop; y + ROW_H <= contentBottomLimit && index < priorityView.size(); y += ROW_H, index++) {
            String id = priorityView.get(index);
            renderRow(g, id, leftPos + ICON_X_LEFT, leftPos + NAME_X_LEFT, y);
        }

        // 右列：可加入
        index = scroll;
        for (int y = contentTop; y + ROW_H <= contentBottomLimit && index < availableView.size(); y += ROW_H, index++) {
            String id = availableView.get(index);
            renderRow(g, id, leftPos + ICON_X_RIGHT, leftPos + NAME_X_RIGHT, y);
        }
    }

    private void renderRow(GuiGraphics g, String id, int iconX, int nameX, int y) {
        ItemStack stack = ItemStack.EMPTY;
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl != null) {
            Item item = ForgeRegistries.ITEMS.getValue(rl);
            if (item != null) stack = new ItemStack(item);
        }
        int iconY = y + 3;
        g.renderItem(stack, iconX, iconY);
        Component name;
        if (stack.isEmpty()) {
            name = Component.literal(id);
        } else {
            name = stack.getHoverName().copy().withStyle(s -> s.withColor(0xDDDDDD));
        }
        g.drawString(this.font, name, nameX, y + 6, 0xDDDDDD, false);
    }

    private void drawOverlay(GuiGraphics g) {
        // 标题居中
        g.drawString(this.font, title, leftPos + (imageWidth - font.width(title)) / 2, topPos + 6, 0xFFFFFF, false);
        g.drawString(this.font, subtitle, leftPos + (imageWidth - font.width(subtitle)) / 2, topPos + 22, 0xAAAAAA, false);

        // 左右两列标题
        g.drawString(this.font, prioHeader, leftPos + LEFT_COL_X, topPos + 40, 0xFFD27F, false);
        g.drawString(this.font, availHeader, leftPos + RIGHT_COL_X, topPos + 40, 0x8FB0FF, false);

        g.drawString(this.font, invLabel, leftPos + (imageWidth - font.width(invLabel)) / 2, topPos + 148, 0xAAAAAA, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        FurnaceOrePrioritySyncPacket.SyncData sync = FurnaceOrePrioritySyncPacket.latest;
        if (sync != null) {
            if (!priorityView.equals(sync.priority)) {
                priorityView = new ArrayList<>(sync.priority);
            }
            if (!availableView.equals(sync.available)) {
                availableView = new ArrayList<>(sync.available);
                rebuildContent();
            }
        }
        drawOverlay(g);
        renderRows(g);
    }
}
