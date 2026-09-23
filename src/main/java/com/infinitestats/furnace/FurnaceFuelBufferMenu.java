package com.infinitestats.furnace;

import com.infinitestats.emc.ModMenuTypes;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * 矿石储备箱菜单：一个类似箱子的 GUI，用于存放“待熔炼的矿石/物品”。
 * 箱子里的矿石会在随身熔炉的输入槽为空时，按格子顺序自动被取出放入输入槽。
 *
 * 格子布局 3 行 × 9 列，数量通过 BulkStorageMenu 单独同步。
 */
public class FurnaceFuelBufferMenu extends BulkStorageMenu {

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
    private final InputBufferContainer buffer;
    private final Level level;

    public FurnaceFuelBufferMenu(int windowId, Inventory inv) {
        super(ModMenuTypes.FURNACE_FUEL_BUFFER_MENU.get(), windowId);

        Player player = inv.player;
        this.furnaceData = player.getCapability(PlayerStatsProvider.PLAYER_STATS)
                .orElseGet(PlayerStats::new).getFurnaceData();
        this.level = player.level();
        this.buffer = new InputBufferContainer(furnaceData.getInputBuffer(), furnaceData.getInputAmounts(), level.isClientSide());

        trackBulkAmounts(furnaceData.getInputAmounts());

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
    public boolean stillValid(Player player) {
        return true;
    }

    /** 手动入仓按配方判断；木头既可当燃料，也可以排队烧成木炭。 */
    private static boolean isSmeltable(Level level, ItemStack stack) {
        if (stack.isEmpty()) return false;
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

    /** 绑定到储备箱 NonNullList 的容器视图，槽位直接读写持久化数据；单格堆叠无上限。 */
    private static class InputBufferContainer implements Container {
        private final NonNullList<ItemStack> list;
        private final long[] amounts;
        private final boolean clientSide;

        InputBufferContainer(NonNullList<ItemStack> list, long[] amounts, boolean clientSide) {
            this.list = list;
            this.amounts = amounts;
            this.clientSide = clientSide;
        }

        @Override
        public int getContainerSize() {
            return list.size();
        }

        @Override
        public boolean isEmpty() {
            for (int i = 0; i < list.size(); i++) {
                if (!list.get(i).isEmpty() && amounts[i] > 0) return false;
            }
            return true;
        }

        @Override
        public ItemStack getItem(int i) {
            ItemStack t = list.get(i);
            if (t.isEmpty()) return ItemStack.EMPTY;
            ItemStack s = t.copy();
            s.setCount(1);
            return s;
        }

        @Override
        public ItemStack removeItem(int i, int count) {
            ItemStack t = list.get(i);
            if (t.isEmpty()) return ItemStack.EMPTY;
            int take = (int) Math.min(count, amounts[i]);
            amounts[i] -= take;
            ItemStack out = t.copy();
            PlayerFurnaceData.rawSetCount(out, take);
            if (amounts[i] <= 0) {
                list.set(i, ItemStack.EMPTY);
                amounts[i] = 0;
            }
            return out;
        }

        @Override
        public ItemStack removeItemNoUpdate(int i) {
            ItemStack t = list.get(i);
            if (t.isEmpty()) return ItemStack.EMPTY;
            ItemStack out = t.copy();
            PlayerFurnaceData.rawSetCount(out, (int) Math.min(amounts[i], PlayerFurnaceData.UNBOUNDED));
            list.set(i, ItemStack.EMPTY);
            amounts[i] = 0;
            return out;
        }

        @Override
        public void setItem(int i, ItemStack stack) {
            if (clientSide) {
                list.set(i, stack.copyWithCount(1));
                return;
            }
            if (stack.isEmpty()) {
                list.set(i, ItemStack.EMPTY);
                amounts[i] = 0;
            } else {
                list.set(i, stack.copyWithCount(1));
                amounts[i] = stack.getCount();
            }
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
            for (int i = 0; i < list.size(); i++) {
                list.set(i, ItemStack.EMPTY);
                amounts[i] = 0;
            }
        }

        /** 将物品存入矿石仓：先合并同类已有堆叠（无上限），再填入空槽；返回剩余。 */
        public ItemStack deposit(ItemStack stack) {
            if (stack.isEmpty()) return stack;
            ItemStack left = stack.copy();
            long count = left.getCount();
            for (int i = 0; i < list.size() && count > 0; i++) {
                ItemStack s = list.get(i);
                if (!s.isEmpty() && ItemStack.isSameItemSameTags(s, left)) {
                    long space = (long) PlayerFurnaceData.UNBOUNDED - amounts[i];
                    if (space > 0) {
                        long move = Math.min(space, count);
                        amounts[i] += move;
                        count -= move;
                    }
                }
            }
            for (int i = 0; i < list.size() && count > 0; i++) {
                if (!list.get(i).isEmpty()) continue;
                long move = Math.min((long) PlayerFurnaceData.UNBOUNDED, count);
                list.set(i, left.copyWithCount(1));
                amounts[i] = move;
                count -= move;
            }
            if (count <= 0) return ItemStack.EMPTY;
            ItemStack res = left.copy();
            PlayerFurnaceData.rawSetCount(res, (int) count);
            return res;
        }
    }

    /** 仅接受可被熔炼物品的槽位，单格堆叠无上限。 */
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

        @Override
        public int getMaxStackSize() {
            return PlayerFurnaceData.UNBOUNDED;
        }
    }
}
