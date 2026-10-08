package com.infinitestats.client;

import com.infinitestats.compat.PinyinSearchBridge;
import com.infinitestats.emc.EmcMenu;
import com.infinitestats.emc.EmcPlayerDataProvider;
import com.infinitestats.emc.EmcPricing;
import com.infinitestats.network.NetworkHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * EMC 奥术转化石板界面（Arcane Transmutation Tablet）。
 *
 * 布局（尺寸 {@code EmcMenu.GUI_WIDTH} × {@code EmcMenu.GUI_HEIGHT} = 342 × 256）：
 * <pre>
 *   顶部：EMC 余额（左）  搜索框（右）
 *   左区：已学物品网格 12 列 × 7 行（每页 84 个，按 EMC 升序），下方翻页按钮
 *   右区：3×3 EMC 合成网格 + 结果槽（材料不进实物消耗，取出结果时按材料 EMC 总价扣款）
 *         + 学习槽（放入物品 → 学习并入账 EMC）
 *   底部：玩家主背包 3×9 + 快捷栏 9
 * </pre>
 *
 * 取物交互对齐原版转化石板：
 * <ul>
 *   <li>左键 —— 取一整组（受 EMC 余额与堆叠上限限制）</li>
 *   <li>右键 —— 取 1 个</li>
 *   <li>Shift + 左键 —— 尽可能取满背包（用尽 EMC）</li>
 * </ul>
 */
public class EmcScreen extends Screen implements MenuAccess<EmcMenu> {

    private static final ResourceLocation SLOT_TEX = new ResourceLocation("textures/gui/container/generic_54.png");
    private static final ResourceLocation FURNACE_TEX = new ResourceLocation("textures/gui/container/furnace.png");

    private static final int CANVAS_W = EmcMenu.GUI_WIDTH;
    private static final int CANVAS_H = EmcMenu.GUI_HEIGHT;

    // === 已学物品网格 ===
    private static final int CELL = 18;
    private static final int GRID_COLS = 12;
    private static final int GRID_ROWS = 7;
    private static final int PER_PAGE = GRID_COLS * GRID_ROWS;
    private static final int GRID_X = 8;
    private static final int GRID_Y = 20;

    // === 合成区 ===
    private static final int CRAFT_X = 234;
    private static final int CRAFT_Y = 34;
    private static final int RESULT_X = 316;
    private static final int RESULT_Y = 52;
    private static final int LEARN_X = 234;
    private static final int LEARN_Y = 130;

    // === 玩家背包 ===
    private static final int INV_X = 8;
    private static final int INV_Y = 176;
    private static final int HOTBAR_Y = 236;

    // === 翻页 / 回收按钮 ===
    private static final int NAV_Y = 152;
    private static final int RECYCLE_X = 60;
    private static final int RECYCLE_W = 68;

    private final EmcMenu menu;
    private EditBox searchBox;
    private String searchText = "";
    private int page = 0;
    private int pages = 1;
    private long costPreview = -1;
    private final List<ItemStack> display = new ArrayList<>();

    public EmcScreen(EmcMenu menu, Inventory inv, Component title) {
        super(title);
        this.menu = menu;
    }

    @Override
    public EmcMenu getMenu() {
        return this.menu;
    }

    private int left() {
        return (this.width - CANVAS_W) / 2;
    }

    private int top() {
        return (this.height - CANVAS_H) / 2;
    }

    private static boolean hovered(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    protected void init() {
        super.init();

        searchBox = new EditBox(this.font, left() + 196, top() + 5, 138, 13, Component.empty());
        searchBox.setHint(Component.translatable("screen.infinitestats.emc.search_hint"));
        searchBox.setMaxLength(64);
        searchBox.setValue(searchText);
        searchBox.setResponder(v -> {
            searchText = v == null ? "" : v;
            page = 0;
        });
        addRenderableWidget(searchBox);

        // 3×3 合成网格
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                addSlotBox(left() + CRAFT_X + c * CELL, top() + CRAFT_Y + r * CELL,
                        EmcMenu.SLOT_GRID_START + r * 3 + c);
            }
        }
        // 结果槽 / 学习槽
        addSlotBox(left() + RESULT_X, top() + RESULT_Y, EmcMenu.SLOT_RESULT);
        addSlotBox(left() + LEARN_X, top() + LEARN_Y, EmcMenu.SLOT_LEARN);

        // 玩家主背包 3×9
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotBox(left() + INV_X + col * CELL, top() + INV_Y + row * CELL,
                        EmcMenu.SLOT_INV_START + row * 9 + col);
            }
        }
        // 快捷栏 9
        for (int col = 0; col < 9; col++) {
            addSlotBox(left() + INV_X + col * CELL, top() + HOTBAR_Y, EmcMenu.SLOT_HOTBAR_START + col);
        }
    }

    private void addSlotBox(int x, int y, int menuIndex) {
        addRenderableWidget(new SlotWidget(x, y, menuIndex));
    }

    // ==================== 渲染 ====================

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int l = left(), t = top();

        drawPanel(g, l, t, CANVAS_W, CANVAS_H);

        // 已学物品网格
        refreshDisplay();
        renderItemGrid(g, mx, my);

        // 文本
        g.drawString(this.font, Component.translatable("screen.infinitestats.emc.balance"),
                l + 8, t + 7, 0x404040, false);
        g.drawString(this.font, formatEmc(balance()), l + 34, t + 7, 0x1A6B1A, false);

        g.drawString(this.font, Component.translatable("screen.infinitestats.emc.craft"),
                l + CRAFT_X, t + 22, 0x404040, false);
        g.drawString(this.font, Component.translatable("screen.infinitestats.emc.learn"),
                l + LEARN_X, t + 119, 0x404040, false);

        // 材料 EMC 预览
        if (costPreview >= 0) {
            g.drawString(this.font, Component.translatable("screen.infinitestats.emc.cost", formatEmc(costPreview)),
                    l + CRAFT_X, t + 90, 0x1A6B1A, false);
        } else if (hasRecipe()) {
            g.drawString(this.font, Component.translatable("screen.infinitestats.emc.cost_unknown"),
                    l + CRAFT_X, t + 90, 0x8B1A1A, false);
        }

        // 合成网格 → 结果槽 的箭头
        g.blit(FURNACE_TEX, l + CRAFT_X + 58, t + CRAFT_Y + 18, 24, 16, 176.0F, 14.0F, 24, 16, 256, 256);

        renderPager(g, mx, my);

        // 学习槽为空时叠加燃烧图标（提示"放入即转化"）
        ItemStack learnStack = stackAt(EmcMenu.SLOT_LEARN);
        if (learnStack.isEmpty()) {
            g.blit(FURNACE_TEX, l + LEARN_X + 1, t + LEARN_Y + 1, 16, 16, 176.0F, 0.0F, 14, 14, 256, 256);
        }

        // 交互组件（搜索框 / 槽位）
        super.render(g, mx, my, pt);

        // tooltip
        ItemStack hoveredLearned = learnedAt((int) mx, (int) my);
        if (!hoveredLearned.isEmpty()) {
            long emc = NetworkHandler.getClientEmc(BuiltInRegistries.ITEM.getKey(hoveredLearned.getItem()));
            Component tip = hoveredLearned.getHoverName().copy()
                    .append(Component.literal("\n"))
                    .append(Component.translatable("screen.infinitestats.emc.tooltip_value", formatEmc(emc)));
            g.renderTooltip(this.font, tip, (int) mx, (int) my);
        } else {
            ItemStack hoveredSlot = hoveredMenuStack((int) mx, (int) my);
            if (!hoveredSlot.isEmpty()) {
                g.renderTooltip(this.font, hoveredSlot, (int) mx, (int) my);
            }
        }

        renderCursor(g, mx, my);
    }

    private void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF000000);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFC6C6C6);
        g.fill(x + 1, y + 1, x + w - 2, y + 3, 0xFFFFFFFF);
        g.fill(x + 1, y + 1, x + 3, y + h - 2, 0xFFFFFFFF);
        g.fill(x + 2, y + h - 3, x + w - 1, y + h - 1, 0xFF555555);
        g.fill(x + w - 3, y + 2, x + w - 1, y + h - 1, 0xFF555555);
        // 顶部标题带
        g.fill(x + 2, y + 3, x + w - 2, y + 17, 0xFF8B8B8B);
    }

    private void renderItemGrid(GuiGraphics g, int mx, int my) {
        int l = left(), t = top();
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int x = l + GRID_X + col * CELL;
                int y = t + GRID_Y + row * CELL;
                g.blit(SLOT_TEX, x, y, CELL, CELL, 7.0F, 17.0F, CELL, CELL, 256, 256);

                int idx = page * PER_PAGE + row * GRID_COLS + col;
                if (idx >= display.size()) continue;
                ItemStack s = display.get(idx);
                if (s.isEmpty()) continue;
                g.renderItem(s, x + 1, y + 1);
                g.renderItemDecorations(this.font, s, x + 1, y + 1);
                if (hovered(mx, my, x, y, CELL, CELL)) {
                    g.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, 0x60FFFFFF);
                }
            }
        }
        if (display.isEmpty()) {
            g.drawString(this.font, Component.translatable("screen.infinitestats.emc.empty"),
                    l + GRID_X + 2, t + GRID_Y + 2, 0x7A7A7A, false);
        }
    }

    private void renderPager(GuiGraphics g, int mx, int my) {
        int l = left(), t = top();
        int navY = t + NAV_Y;
        boolean prevHover = hovered(mx, my, l + 8, navY, 12, 12);
        boolean nextHover = hovered(mx, my, l + 24, navY, 12, 12);

        g.fill(l + 8, navY, l + 20, navY + 12, 0xFF000000);
        g.fill(l + 9, navY + 1, l + 19, navY + 11, prevHover ? 0xFF9A9A9A : 0xFF6E6E6E);
        g.fill(l + 24, navY, l + 36, navY + 12, 0xFF000000);
        g.fill(l + 25, navY + 1, l + 35, navY + 11, nextHover ? 0xFF9A9A9A : 0xFF6E6E6E);
        g.drawCenteredString(this.font, "<", l + 14, navY + 2, 0xFFFFFFFF);
        g.drawCenteredString(this.font, ">", l + 30, navY + 2, 0xFFFFFFFF);
        g.drawString(this.font, (page + 1) + "/" + pages, l + 42, navY + 2, 0x404040, false);

        // 「回收背包」：Shift + 点击才执行，避免误卖随身物品
        boolean recycleHover = hovered(mx, my, l + RECYCLE_X, navY, RECYCLE_W, 12);
        int recycleFill = recycleHover ? (hasShiftDown() ? 0xFF9A5A5A : 0xFF9A9A9A) : 0xFF6E6E6E;
        g.fill(l + RECYCLE_X, navY, l + RECYCLE_X + RECYCLE_W, navY + 12, 0xFF000000);
        g.fill(l + RECYCLE_X + 1, navY + 1, l + RECYCLE_X + RECYCLE_W - 1, navY + 11, recycleFill);
        g.drawCenteredString(this.font,
                Component.translatable("screen.infinitestats.emc.recycle").getString(),
                l + RECYCLE_X + RECYCLE_W / 2, navY + 2, 0xFFFFFFFF);
    }

    private void renderCursor(GuiGraphics g, int mx, int my) {
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty()) return;
        g.renderItem(carried, mx - 8, my - 8);
        g.renderItemDecorations(this.font, carried, mx - 8, my - 8);
    }

    // ==================== 数据 ====================

    private long balance() {
        var player = Minecraft.getInstance().player;
        if (player == null) return 0;
        return player.getCapability(EmcPlayerDataProvider.EMC_PLAYER_DATA)
                .map(d -> d.getEmcBalance())
                .orElse(0L);
    }

    private ItemStack stackAt(int menuIndex) {
        if (menuIndex < 0 || menuIndex >= menu.slots.size()) return ItemStack.EMPTY;
        return menu.slots.get(menuIndex).getItem();
    }

    private boolean hasRecipe() {
        return !stackAt(EmcMenu.SLOT_RESULT).isEmpty();
    }

    /** 缓存当前页要显示的已学物品（搜索过滤 + 按 EMC 升序 + 还原 NBT）。 */
    private void refreshDisplay() {
        display.clear();
        var player = Minecraft.getInstance().player;
        if (player == null) {
            pages = 1;
            return;
        }
        var data = player.getCapability(EmcPlayerDataProvider.EMC_PLAYER_DATA).orElse(null);
        if (data == null) {
            pages = 1;
            return;
        }

        String f = searchText.toLowerCase();
        List<ResourceLocation> ids = new ArrayList<>(data.getLearnedItems());
        ids.sort((a, b) -> Long.compare(
                NetworkHandler.getClientEmc(a), NetworkHandler.getClientEmc(b)));

        for (ResourceLocation id : ids) {
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item == Items.AIR) continue;

            ItemStack s = new ItemStack(item);
            CompoundTag nbt = data.getItemNbt(id);
            if (nbt != null && !nbt.isEmpty()) {
                s.setTag(nbt.copy());
            }
            if (!f.isEmpty() && !matchesSearch(id, s, f)) continue;
            display.add(s);
        }

        pages = Math.max(1, (display.size() + PER_PAGE - 1) / PER_PAGE);
        if (page >= pages) page = pages - 1;
        if (page < 0) page = 0;

        costPreview = computeCostPreview();
    }

    /**
     * 计算当前网格配方的 EMC 总价（客户端本地预览，服务端会再校验一次）。
     * 与「EMC 转化折扣」属性联动：按同一系数把同步来的基础价折算成实际支付价。
     */
    private long computeCostPreview() {
        var mcPlayer = Minecraft.getInstance().player;
        double factor = mcPlayer == null ? 1.0 : EmcPricing.buyFactor(mcPlayer);
        long total = 0;
        boolean any = false;
        for (int i = EmcMenu.SLOT_GRID_START; i <= EmcMenu.SLOT_GRID_END; i++) {
            ItemStack s = stackAt(i);
            if (s.isEmpty()) continue;
            long base = NetworkHandler.getClientEmc(BuiltInRegistries.ITEM.getKey(s.getItem()));
            if (base <= 0) return -1;
            total += Math.max(1L, (long) Math.floor(base * factor)) * s.getCount();
            any = true;
        }
        if (!any) return -1;
        return total;
    }

    /**
     * 搜索匹配，支持 JEI 风格的前缀：
     * - {@code @modid}：按来源模组匹配
     * - {@code #tag}：按物品标签匹配
     * - 其他：物品 ID / 显示名子串匹配（含拼音）
     */
    private boolean matchesSearch(ResourceLocation id, ItemStack s, String f) {
        if (f.startsWith("@")) {
            String mod = f.substring(1);
            return mod.isEmpty() || id.getNamespace().contains(mod);
        }
        if (f.startsWith("#")) {
            String tagQuery = f.substring(1);
            return !tagQuery.isEmpty() && itemHasTag(id, tagQuery);
        }
        String name = s.getHoverName().getString().toLowerCase();
        return PinyinSearchBridge.matches(id.toString(), f) || PinyinSearchBridge.matches(name, f);
    }

    private boolean itemHasTag(ResourceLocation id, String tagQuery) {
        var holder = BuiltInRegistries.ITEM.getHolder(ResourceKey.create(Registries.ITEM, id));
        if (holder.isEmpty()) return false;
        return holder.get().tags().anyMatch(tk -> tk.location().toString().contains(tagQuery));
    }

    // ==================== 交互 ====================

    /** 已学物品网格命中。 */
    private ItemStack learnedAt(int mx, int my) {
        int ix = mx - (left() + GRID_X);
        int iy = my - (top() + GRID_Y);
        if (ix < 0 || iy < 0) return ItemStack.EMPTY;
        int col = ix / CELL, row = iy / CELL;
        if (col >= GRID_COLS || row >= GRID_ROWS) return ItemStack.EMPTY;
        int idx = page * PER_PAGE + row * GRID_COLS + col;
        return idx >= 0 && idx < display.size() ? display.get(idx) : ItemStack.EMPTY;
    }

    private ItemStack hoveredMenuStack(int mx, int my) {
        for (var w : this.renderables) {
            if (w instanceof SlotWidget sw && sw.isMouseOver(mx, my)) {
                return stackAt(sw.menuIndex);
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int l = left(), t = top();

        // 翻页
        if (hovered(mx, my, l + 8, t + NAV_Y, 12, 12)) {
            page = Math.max(0, page - 1);
            return true;
        }
        if (hovered(mx, my, l + 24, t + NAV_Y, 12, 12)) {
            page = Math.min(pages - 1, page + 1);
            return true;
        }

        // 回收背包：Shift + 点击把背包中所有已学物品卖出为 EMC
        if (hovered(mx, my, l + RECYCLE_X, t + NAV_Y, RECYCLE_W, 12)) {
            var mc = Minecraft.getInstance();
            if (hasShiftDown()) {
                NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.EmcSellAllPacket());
            } else if (mc.player != null) {
                mc.player.displayClientMessage(
                        Component.translatable("screen.infinitestats.emc.recycle_hint"), true);
            }
            return true;
        }

        // 已学物品：左键一组 / 右键一个 / Shift 左键买满
        ItemStack target = learnedAt((int) mx, (int) my);
        if (!target.isEmpty()) {
            int amount;
            if (hasShiftDown()) {
                amount = -1;                                          // 用尽 EMC 取满背包
            } else if (button == 1) {
                amount = 1;                                           // 右键取 1 个
            } else {
                amount = Math.max(1, target.getMaxStackSize());        // 左键取一整组
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(target.getItem());
            NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.EmcExtractPacket(id.toString(), amount));
            return true;
        }

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (hovered(mx, my, left() + GRID_X, top() + GRID_Y, GRID_COLS * CELL, GRID_ROWS * CELL)) {
            if (delta < 0) page = Math.min(pages - 1, page + 1);
            else if (delta > 0) page = Math.max(0, page - 1);
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public void onClose() {
        super.onClose();
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.closeContainer();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ==================== 槽位组件 ====================

    /** 与菜单槽位一一对应的可视槽位：点击直接转发给真实容器菜单。 */
    private final class SlotWidget extends AbstractWidget {
        private final int menuIndex;

        SlotWidget(int x, int y, int menuIndex) {
            super(x, y, CELL, CELL, Component.empty());
            this.menuIndex = menuIndex;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            g.blit(SLOT_TEX, getX(), getY(), CELL, CELL, 7.0F, 17.0F, CELL, CELL, 256, 256);
            ItemStack s = stackAt(menuIndex);
            if (!s.isEmpty()) {
                g.renderItem(s, getX() + 1, getY() + 1);
                g.renderItemDecorations(Minecraft.getInstance().font, s, getX() + 1, getY() + 1);
            }
            if (isMouseOver(mx, my)) {
                RenderSystem.disableDepthTest();
                RenderSystem.depthMask(false);
                g.fill(getX(), getY(), getX() + CELL, getY() + CELL, 0x80FFFFFF);
                RenderSystem.depthMask(true);
                RenderSystem.enableDepthTest();
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (!this.isMouseOver(mx, my)) return false;
            var mc = Minecraft.getInstance();
            if (mc.player == null || mc.gameMode == null) return false;
            ClickType type = hasShiftDown() ? ClickType.QUICK_MOVE : ClickType.PICKUP;
            mc.gameMode.handleInventoryMouseClick(menu.containerId, menuIndex, button, type, mc.player);
            return true;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            ItemStack s = stackAt(menuIndex);
            out.add(NarratedElementType.TITLE, s.isEmpty() ? Component.empty() : s.getHoverName());
        }
    }

    // ==================== 数值格式 ====================

    /** 大数值缩写（K/M/B/T/Qa/Qi）。 */
    private static String formatEmc(long value) {
        if (value < 1000L) return Long.toString(value);
        String[] units = {"", "K", "M", "B", "T", "Qa", "Qi"};
        int tier = (int) (Math.log10(value) / 3);
        if (tier >= units.length) tier = units.length - 1;
        double scaled = value / Math.pow(1000.0, tier);
        String num = scaled >= 100 ? String.format("%.0f", scaled)
                : scaled >= 10 ? String.format("%.1f", scaled)
                : String.format("%.2f", scaled);
        return num + units[tier];
    }
}
