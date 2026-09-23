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
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
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

    private static final int ROW_H = 20;
    private static final int VISIBLE_ROWS = 3;

    // 左右两列布局常量
    private static final int LEFT_COL_X = 10;
    private static final int LEFT_COL_R = 148;
    private static final int RIGHT_COL_X = 154;
    private static final int RIGHT_COL_R = 284;
    private static final int SCROLL_L_X = 149;
    private static final int SCROLL_R_X = 285;

    // 左列行内元素（相对 leftPos 的偏移）
    private static final int ICON_X_LEFT = LEFT_COL_X + 14;
    private static final int NAME_X_LEFT = LEFT_COL_X + 32;
    private static final int NAME_W_LEFT = 50;
    private static final int PRIO_UP_X = LEFT_COL_X + 84;
    private static final int PRIO_DOWN_X = LEFT_COL_X + 101;
    private static final int PRIO_REMOVE_X = LEFT_COL_X + 118;

    // 右列行内元素
    private static final int ICON_X_RIGHT = RIGHT_COL_X + 4;
    private static final int NAME_X_RIGHT = RIGHT_COL_X + 22;
    private static final int NAME_W_RIGHT = 80;
    private static final int AVAIL_ADD_X = RIGHT_COL_R - 28;

    // 仓内矿石条
    private static final int RESERVE_Y = 38;
    private static final int RESERVE_START_X = 78;
    private static final int RESERVE_STEP = 22;
    private static final int RESERVE_MAX_SHOWN = 9;

    private final List<AbstractWidget> dynamicButtons = new ArrayList<>();
    private EditBox searchBox;

    private List<String> priorityView = new ArrayList<>();
    private List<String> availableView = new ArrayList<>();
    private List<String> reserveIds = new ArrayList<>();
    private List<Long> reserveAmounts = new ArrayList<>();
    /** 物品注册名 → 显示名 缓存（搜索过滤用）。 */
    private final Map<String, String> nameCache = new HashMap<>();

    private int scrollL = 0;
    private int scrollR = 0;
    private int contentTop;
    private int contentBottomLimit;

    private final Component title = Component.translatable("gui.infinitestats.furnace.ore_priority.title");
    private final Component subtitle = Component.translatable("gui.infinitestats.furnace.ore_priority.subtitle");
    private final Component prioHeader = Component.translatable("gui.infinitestats.furnace.ore_priority.priority_header");
    private final Component availHeader = Component.translatable("gui.infinitestats.furnace.ore_priority.available");
    private final Component reserveHeader = Component.translatable("gui.infinitestats.furnace.ore_priority.reserve");
    private final Component hint = Component.translatable("gui.infinitestats.furnace.ore_priority.hint");
    private final Component searchHint = Component.translatable("gui.infinitestats.furnace.ore_priority.search");
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
        this.contentTop = topPos + 82;
        this.contentBottomLimit = topPos + 150;

        this.dynamicButtons.clear();
        this.addRenderableWidget(Button.builder(closeLabel, b -> closeToFurnace())
                .bounds(leftPos + imageWidth - 56, topPos + 6, 52, 16).build());

        this.searchBox = new EditBox(this.font, leftPos + 200, topPos + 62, 88, 16, searchHint);
        this.searchBox.setMaxLength(64);
        this.searchBox.setResponder(s -> rebuildContent());
        this.addRenderableWidget(this.searchBox);

        NetworkHandler.CHANNEL.sendToServer(new FurnaceOrePriorityRequestPacket());
        rebuildContent();
    }

    private void closeToFurnace() {
        NetworkHandler.CHANNEL.sendToServer(new FurnaceOpenPacket());
    }

    private static String idOf(ItemStack stack) {
        return ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
    }

    private static ItemStack stackOf(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl != null) {
            Item item = ForgeRegistries.ITEMS.getValue(rl);
            if (item != null) return new ItemStack(item);
        }
        return ItemStack.EMPTY;
    }

    private static boolean isSmeltable(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || stack.isEmpty()) return false;
        return PlayerFurnaceData.hasSmeltRecipe(mc.level, stack);
    }

    private String displayNameOf(String id) {
        return nameCache.computeIfAbsent(id, k -> {
            ItemStack s = stackOf(k);
            return s.isEmpty() ? k : s.getHoverName().getString();
        });
    }

    /** 按搜索文本过滤可用列表（匹配注册名或显示名，忽略大小写）。 */
    private List<String> filteredAvailable() {
        String q = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase();
        if (q.isEmpty()) return availableView;
        List<String> out = new ArrayList<>();
        for (String id : availableView) {
            if (id.toLowerCase().contains(q) || displayNameOf(id).toLowerCase().contains(q)) {
                out.add(id);
            }
        }
        return out;
    }

    private static String formatAmount(long v) {
        if (v < 1000) return String.valueOf(v);
        if (v < 1_000_000) return (v / 1000) + "k";
        if (v < 1_000_000_000) return (v / 1_000_000) + "M";
        return (v / 1_000_000_000) + "B";
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int x = (int) mouseX - leftPos;
        int y = (int) mouseY - topPos;
        if (y >= contentTop - topPos && y <= contentBottomLimit - topPos) {
            if (x >= LEFT_COL_X && x <= LEFT_COL_R) {
                scrollL = clampScroll(scrollL - (int) Math.signum(delta), priorityView.size());
                rebuildContent();
                return true;
            }
            if (x >= RIGHT_COL_X && x <= RIGHT_COL_R) {
                scrollR = clampScroll(scrollR - (int) Math.signum(delta), filteredAvailable().size());
                rebuildContent();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            int x = (int) mx - leftPos;
            int y = (int) my - topPos;

            // 仓内矿石图标：点击直接加入优先列表
            if (y >= RESERVE_Y && y < RESERVE_Y + 16) {
                int idx = (x - RESERVE_START_X) / RESERVE_STEP;
                int off = (x - RESERVE_START_X) % RESERVE_STEP;
                if (idx >= 0 && idx < reserveIds.size() && idx < RESERVE_MAX_SHOWN && off < 16) {
                    addToPriority(reserveIds.get(idx));
                    return true;
                }
            }

            // 右列列表行：点击行主体（不含 ＋ 按钮区）加入优先列表
            if (y >= contentTop - topPos && y < contentBottomLimit - topPos
                    && x >= RIGHT_COL_X && x < AVAIL_ADD_X) {
                List<String> filtered = filteredAvailable();
                int row = scrollR + (y - (contentTop - topPos)) / ROW_H;
                if (row >= 0 && row < filtered.size()) {
                    addToPriority(filtered.get(row));
                    return true;
                }
            }

            // 玩家背包（只读槽位）：点击可熔炼物品加入优先列表
            for (Slot slot : menu.slots) {
                int sx = leftPos + slot.x;
                int sy = topPos + slot.y;
                if (mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16
                        && slot instanceof FurnaceOrePriorityMenu.ReadOnlySlot) {
                    ItemStack s = slot.getItem();
                    if (!s.isEmpty() && isSmeltable(s)) {
                        addToPriority(idOf(s));
                    }
                    return true; // 吞掉点击，防止尝试拿起物品
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void addToPriority(String id) {
        if (id == null || priorityView.contains(id)) return;
        priorityView.add(id);
        sendUpdate();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_ESCAPE && !searchBox.isFocused()) {
            closeToFurnace();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void sendUpdate() {
        FurnaceOrePrioritySyncPacket.latest = null;
        rebuildContent();
        NetworkHandler.CHANNEL.sendToServer(new FurnaceOrePriorityUpdatePacket(new ArrayList<>(priorityView)));
    }

    private void rebuildContent() {
        for (AbstractWidget w : dynamicButtons) this.removeWidget(w);
        dynamicButtons.clear();

        scrollL = clampScroll(scrollL, priorityView.size());
        List<String> filtered = filteredAvailable();
        scrollR = clampScroll(scrollR, filtered.size());

        // 左列：优先放入（每行：↑ ↓ ✕）
        int index = scrollL;
        for (int y = contentTop; y + ROW_H <= contentBottomLimit && index < priorityView.size(); y += ROW_H, index++) {
            final int i = index;
            this.addPrioBtn("↑", leftPos + PRIO_UP_X, y, i, -1);
            this.addPrioBtn("↓", leftPos + PRIO_DOWN_X, y, i, 1);
            this.addActionBtn("✕", leftPos + PRIO_REMOVE_X, y, () -> {
                priorityView.remove(i);
                sendUpdate();
            });
        }

        // 右列：可加入（每行：＋）
        index = scrollR;
        for (int y = contentTop; y + ROW_H <= contentBottomLimit && index < filtered.size(); y += ROW_H, index++) {
            final String id = filtered.get(index);
            this.addActionBtn("＋", leftPos + AVAIL_ADD_X, y, () -> {
                availableView.remove(id);
                addToPriority(id);
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
        }).bounds(x, y + 2, 16, 16).build();
        b.active = index + dir >= 0 && index + dir < priorityView.size();
        dynamicButtons.add(b);
        this.addRenderableWidget(b);
    }

    private void addActionBtn(String label, int x, int y, Runnable action) {
        Button b = Button.builder(Component.literal(label), btn -> action.run()).bounds(x, y + 2, 18, 16).build();
        dynamicButtons.add(b);
        this.addRenderableWidget(b);
    }

    private int clampScroll(int value, int rows) {
        int max = Math.max(0, rows - VISIBLE_ROWS);
        return Math.max(0, Math.min(max, value));
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        gfx.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xCC101018);
        gfx.fill(leftPos, topPos, leftPos + imageWidth, topPos + 1, 0xFF3A3A4A);
        gfx.fill(leftPos, topPos + imageHeight - 1, leftPos + imageWidth, topPos + imageHeight, 0xFF3A3A4A);

        // 左右两列表格底色
        gfx.fill(leftPos + LEFT_COL_X, contentTop, leftPos + LEFT_COL_R, contentBottomLimit, 0x88181822);
        gfx.fill(leftPos + RIGHT_COL_X, contentTop, leftPos + RIGHT_COL_R, contentBottomLimit, 0x88181822);

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
        // 左列：优先放入（带序号）
        int index = scrollL;
        for (int y = contentTop; y + ROW_H <= contentBottomLimit && index < priorityView.size(); y += ROW_H, index++) {
            String id = priorityView.get(index);
            g.drawString(this.font, String.valueOf(index + 1), leftPos + LEFT_COL_X + 2, y + 6, 0x8A8A9A, false);
            renderRow(g, id, leftPos + ICON_X_LEFT, leftPos + NAME_X_LEFT, NAME_W_LEFT, y);
        }

        // 右列：可加入（按搜索过滤）
        List<String> filtered = filteredAvailable();
        index = scrollR;
        for (int y = contentTop; y + ROW_H <= contentBottomLimit && index < filtered.size(); y += ROW_H, index++) {
            String id = filtered.get(index);
            boolean inReserve = reserveIds.contains(id);
            renderRow(g, id, leftPos + ICON_X_RIGHT, leftPos + NAME_X_RIGHT, NAME_W_RIGHT, y);
            if (inReserve) {
                g.drawString(this.font, "◆", leftPos + ICON_X_RIGHT - 1, y + 1, 0xFFD27F, false);
            }
        }
    }

    private void renderRow(GuiGraphics g, String id, int iconX, int nameX, int nameW, int y) {
        ItemStack stack = stackOf(id);
        int iconY = y + 2;
        g.renderItem(stack, iconX, iconY);
        Component name;
        if (stack.isEmpty()) {
            name = Component.literal(id);
        } else {
            name = stack.getHoverName().copy().withStyle(s -> s.withColor(0xDDDDDD));
        }
        g.drawString(this.font, font.plainSubstrByWidth(name.getString(), nameW), nameX, y + 6, 0xDDDDDD, false);
    }

    private void renderReserveBar(GuiGraphics g) {
        g.drawString(this.font, reserveHeader, leftPos + 12, topPos + 44, 0xFFD27F, false);
        int shown = Math.min(reserveIds.size(), RESERVE_MAX_SHOWN);
        for (int i = 0; i < shown; i++) {
            int x = leftPos + RESERVE_START_X + i * RESERVE_STEP;
            int y = topPos + RESERVE_Y;
            g.renderItem(stackOf(reserveIds.get(i)), x, y);
            String amt = formatAmount(reserveAmounts.get(i));
            g.drawString(this.font, amt, x + 16 - font.width(amt), y + 10, 0xFFFFC24E, false);
        }
        if (reserveIds.size() > shown) {
            g.drawString(this.font, "+" + (reserveIds.size() - shown),
                    leftPos + RESERVE_START_X + shown * RESERVE_STEP + 2, topPos + RESERVE_Y + 6, 0xAAAAAA, false);
        }
    }

    private void renderScrollbar(GuiGraphics g, int x, int value, int rows) {
        int trackTop = contentTop;
        int trackH = contentBottomLimit - contentTop;
        g.fill(x, trackTop, x + 2, contentBottomLimit, 0xFF20202C);
        int max = Math.max(0, rows - VISIBLE_ROWS);
        if (max > 0) {
            int sliderH = Math.max(14, trackH * VISIBLE_ROWS / rows);
            int sliderY = trackTop + (trackH - sliderH) * value / max;
            g.fill(x, sliderY, x + 2, sliderY + sliderH, 0xFF7A7A96);
        }
    }

    private void drawOverlay(GuiGraphics g) {
        // 标题居中
        g.drawString(this.font, title, leftPos + (imageWidth - font.width(title)) / 2, topPos + 6, 0xFFFFFF, false);
        g.drawString(this.font, subtitle, leftPos + (imageWidth - font.width(subtitle)) / 2, topPos + 22, 0xAAAAAA, false);

        // 左右两列标题
        g.drawString(this.font, prioHeader, leftPos + LEFT_COL_X, topPos + 64, 0xFFD27F, false);
        g.drawString(this.font, availHeader, leftPos + RIGHT_COL_X, topPos + 64, 0x8FB0FF, false);

        g.drawString(this.font, invLabel, leftPos + (imageWidth - font.width(invLabel)) / 2, topPos + 150, 0xAAAAAA, false);

        // 右侧操作提示（背包右侧空白区）
        int tipY = topPos + 164;
        for (FormattedCharSequence line : font.split(hint, imageWidth - 250)) {
            g.drawString(this.font, line, leftPos + 242, tipY, 0x777777, false);
            tipY += 10;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        FurnaceOrePrioritySyncPacket.SyncData sync = FurnaceOrePrioritySyncPacket.latest;
        if (sync != null) {
            if (!priorityView.equals(sync.priority) || !availableView.equals(sync.available)
                    || !reserveIds.equals(sync.reserveIds) || !reserveAmounts.equals(sync.reserveAmounts)) {
                priorityView = new ArrayList<>(sync.priority);
                availableView = new ArrayList<>(sync.available);
                reserveIds = new ArrayList<>(sync.reserveIds);
                reserveAmounts = new ArrayList<>(sync.reserveAmounts);
                rebuildContent();
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
        drawOverlay(g);
        renderReserveBar(g);
        renderScrollbar(g, leftPos + SCROLL_L_X, scrollL, priorityView.size());
        renderScrollbar(g, leftPos + SCROLL_R_X, scrollR, filteredAvailable().size());
        renderRows(g);
        renderRowTooltip(g, mouseX, mouseY);
        renderReserveTooltip(g, mouseX, mouseY);
    }

    private void renderRowTooltip(GuiGraphics gfx, int mouseX, int mouseY) {
        int relX = mouseX - leftPos;
        int relY = mouseY - topPos;
        if (relY < contentTop - topPos || relY >= contentBottomLimit - topPos) return;
        String id;
        if (relX >= ICON_X_LEFT && relX < PRIO_UP_X) {
            int row = scrollL + (relY - (contentTop - topPos)) / ROW_H;
            if (row < 0 || row >= priorityView.size()) return;
            id = priorityView.get(row);
        } else if (relX >= ICON_X_RIGHT && relX < AVAIL_ADD_X) {
            List<String> filtered = filteredAvailable();
            int row = scrollR + (relY - (contentTop - topPos)) / ROW_H;
            if (row < 0 || row >= filtered.size()) return;
            id = filtered.get(row);
        } else {
            return;
        }
        gfx.renderTooltip(font, Component.literal(displayNameOf(id)), mouseX, mouseY);
    }

    private void renderReserveTooltip(GuiGraphics gfx, int mouseX, int mouseY) {
        int relX = mouseX - leftPos;
        int relY = mouseY - topPos;
        if (relY < RESERVE_Y || relY >= RESERVE_Y + 16) return;
        int idx = (relX - RESERVE_START_X) / RESERVE_STEP;
        int off = (relX - RESERVE_START_X) % RESERVE_STEP;
        if (idx < 0 || idx >= Math.min(reserveIds.size(), RESERVE_MAX_SHOWN) || off >= 16) return;
        Component tip = Component.literal(displayNameOf(reserveIds.get(idx)) + " ×" + reserveAmounts.get(idx));
        gfx.renderTooltip(font, tip, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics gfx, int mouseX, int mouseY) {
        // 本界面的标题和物品栏标签由 drawOverlay 统一绘制。
    }
}
