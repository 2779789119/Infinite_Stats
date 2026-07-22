package com.infinitestats.furnace;

import com.infinitestats.emc.ModMenuTypes;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeHooks;

/**
 * 矿石储备箱菜单：一个类似箱子的 GUI，用于存放“待熔炼的矿石/物品”。
 * 箱子里的矿石会在随身熔炉的输入槽为空时，按格子顺序自动被取出放入输入槽。
 *
 * 格子布局 3 行 × 9 列（与外部箱子 UI 一致），每个槽位为普通堆叠上限（64）。
 */
public class FurnaceFuelBufferMenu extends AbstractContainerMenu {

    /** 储备箱行 / 列数（与外部箱子一致）。 */
    public static final int ROWS = 3;
    public static final int COLS = 9;
    public static final int BUFFER_SLOTS = ROWS * COLS;

    /**
     * 玩家背包槽位的 y 起点：取“标签下方 + 1 像素间距”。
     * 标签由 FurnaceFuelBufferScreen 的 {@code inventoryLabelY = imageHeight - 94 = 74} 绘制，
     * 文字占 ~8 像素，因此玩家背包从 y = 83 开始，正好落在 imageHeight=168 的背景内（快捷栏 141+18=159）。
     */
    private static final int PLAYER_INV_Y = 86;
    private static final int HOTBAR_Y = PLAYER_INV_Y + 3 * 18 + 4; // 141

    private final PlayerFurnaceData furnaceData;
    private final Container buffer;
    private final Level level;

    public FurnaceFuelBufferMenu(int windowId, Inventory inv) {
        super(ModMenuTypes.FURNACE_FUEL_BUFFER_MENU.get(), windowId);

        Player player = inv.player;
        this.furnaceData = player.getCapability(PlayerStatsProvider.PLAYER_STATS)
                .orElseGet(PlayerStats::new).getFurnaceData();
        this.level = player.level();
        this.buffer = new InputBufferContainer(furnaceData.getInputBuffer());

        // 储备箱格子（3 行 × 9 列），仅接受可被熔炼的物品（矿石等）
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                this.addSlot(new SmeltableOnlySlot(buffer, col + row * COLS, 8 + col * 18, 18 + row * 18, level));
            }
        }
        // 玩家背包
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, PLAYER_INV_Y + row * 18));
            }
        }
        // 玩家快捷栏
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inv, col, 8 + col * 18, HOTBAR_Y));
        }
    }

    public PlayerFurnaceData getFurnaceData() {
        return furnaceData;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = slot.getItem();

        if (index < BUFFER_SLOTS) {
            // 储备箱 -> 玩家背包
            moveItemStackTo(source, BUFFER_SLOTS, this.slots.size(), true);
        } else {
            // 玩家背包 -> 储备箱（仅可熔炼物品可入，SmeltableOnlySlot.mayPlace 已限制）
            if (isSmeltable(level, source)) {
                moveItemStackTo(source, 0, BUFFER_SLOTS, false);
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    /** 判断某物品是否为“可被熔炼的输入”（有熔炼/高炉配方且本身不是燃料）。 */
    private static boolean isSmeltable(Level level, ItemStack stack) {
        if (stack.isEmpty()) return false;
        // 燃料不算“被熔炼的矿石”
        if (ForgeHooks.getBurnTime(stack, RecipeType.SMELTING) > 0) return false;
        Container view = new Container() {
            @Override public int getContainerSize() { return 1; }
            @Override public boolean isEmpty() { return stack.isEmpty(); }
            @Override public ItemStack getItem(int index) { return index == 0 ? stack : ItemStack.EMPTY; }
            @Override public ItemStack removeItem(int index, int count) { return ItemStack.EMPTY; }
            @Override public ItemStack removeItemNoUpdate(int index) { return ItemStack.EMPTY; }
            @Override public void setItem(int index, ItemStack s) { }
            @Override public void setChanged() { }
            @Override public boolean stillValid(Player p) { return true; }
            @Override public void clearContent() { }
        };
        return level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, view, level).isPresent()
                || level.getRecipeManager().getRecipeFor(RecipeType.BLASTING, view, level).isPresent();
    }

    /** 绑定到储备箱 NonNullList 的容器视图，槽位直接读写持久化数据。 */
    private static class InputBufferContainer implements Container {
        private final NonNullList<ItemStack> list;

        InputBufferContainer(NonNullList<ItemStack> list) {
            this.list = list;
        }

        @Override
        public int getContainerSize() {
            return list.size();
        }

        @Override
        public boolean isEmpty() {
            for (ItemStack s : list) {
                if (!s.isEmpty()) return false;
            }
            return true;
        }

        @Override
        public ItemStack getItem(int i) {
            return list.get(i);
        }

        @Override
        public ItemStack removeItem(int i, int count) {
            return ContainerHelper.removeItem(list, i, count);
        }

        @Override
        public ItemStack removeItemNoUpdate(int i) {
            return ContainerHelper.takeItem(list, i);
        }

        @Override
        public void setItem(int i, ItemStack stack) {
            list.set(i, stack);
        }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
            list.clear();
        }
    }

    /** 仅接受可被熔炼物品的槽位。 */
    private static class SmeltableOnlySlot extends Slot {
        private final Level level;

        SmeltableOnlySlot(Container container, int index, int x, int y, Level level) {
            super(container, index, x, y);
            this.level = level;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return isSmeltable(level, stack);
        }
    }
}
