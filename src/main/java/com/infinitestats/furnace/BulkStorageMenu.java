package com.infinitestats.furnace;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** 大容量槽位分别同步单件物品模板和数量，避免原版单字节数量截断。 */
public abstract class BulkStorageMenu extends AbstractContainerMenu {
    private long[] amounts = new long[0];

    protected BulkStorageMenu(MenuType<?> type, int id) {
        super(type, id);
    }

    protected void trackIntData(ContainerData values) {
        addDataSlots(new ContainerData() {
            public int getCount() { return values.getCount() * 2; }
            public int get(int index) { return (values.get(index / 2) >>> (16 * (index % 2))) & 0xffff; }
            public void set(int index, int value) {
                int shift = 16 * (index % 2);
                values.set(index / 2, (values.get(index / 2) & ~(0xffff << shift))
                        | ((value & 0xffff) << shift));
            }
        });
    }

    protected void trackBulkAmounts(long[] values) {
        amounts = values;
        addDataSlots(new ContainerData() {
            public int getCount() { return amounts.length * 4; }
            public int get(int index) {
                return (int) ((amounts[index / 4] >>> (16 * (index % 4))) & 0xffffL);
            }
            public void set(int index, int value) {
                int shift = 16 * (index % 4);
                amounts[index / 4] = (amounts[index / 4] & ~(0xffffL << shift))
                        | ((value & 0xffffL) << shift);
            }
        });
    }

    public int getBulkSlotCount() { return amounts.length; }
    public long getBulkAmount(int slot) { return amounts[slot]; }

    protected int[] getBulkTargets(ItemStack stack) {
        int[] result = new int[amounts.length];
        for (int i = 0; i < result.length; i++) result[i] = i;
        return result;
    }

    /** 子菜单可将主槽放不下的 Shift 输入继续存入待炼队列。 */
    protected void storeQuickMovedStack(ItemStack stack) {
        int[] targets = getBulkTargets(stack);
        for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
            for (int target : targets) {
                if (getSlot(target).hasItem() != (pass == 0)) continue;
                insert(target, stack, stack.getCount());
                if (stack.isEmpty()) break;
            }
        }
    }

    private void setAmount(int index, long amount) {
        amounts[index] = Math.max(0, amount);
        if (amounts[index] == 0) getSlot(index).set(ItemStack.EMPTY);
        else getSlot(index).setChanged();
    }

    private int insert(int index, ItemStack stack, int requested) {
        Slot slot = getSlot(index);
        ItemStack stored = slot.getItem();
        if (stack.isEmpty() || !slot.mayPlace(stack)
                || (!stored.isEmpty() && !ItemStack.isSameItemSameTags(stored, stack))) return 0;
        int moved = (int) Math.min(Math.min(requested, stack.getCount()),
                Math.max(0, PlayerFurnaceData.UNBOUNDED - amounts[index]));
        if (moved <= 0) return 0;
        long total = amounts[index] + moved;
        if (stored.isEmpty()) slot.set(stack.copyWithCount(1));
        setAmount(index, total);
        stack.shrink(moved);
        return moved;
    }

    private ItemStack take(int index, int requested) {
        ItemStack stored = getSlot(index).getItem();
        if (stored.isEmpty()) return ItemStack.EMPTY;
        int moved = (int) Math.min(amounts[index], Math.min(requested, stored.getMaxStackSize()));
        if (moved <= 0) return ItemStack.EMPTY;
        ItemStack result = stored.copyWithCount(moved);
        setAmount(index, amounts[index] - moved);
        return result;
    }

    @Override
    public final ItemStack quickMoveStack(Player player, int index) {
        if (player.level().isClientSide() || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot source = getSlot(index);
        if (!source.hasItem() || !source.mayPickup(player)) return ItemStack.EMPTY;
        if (index < amounts.length) {
            // 每次操作最多转移一个背包的容量，避免巨量库存导致主线程长循环。
            for (int n = 0; n < slots.size() - amounts.length && amounts[index] > 0; n++) {
                ItemStack stack = source.getItem().copyWithCount(
                        (int) Math.min(amounts[index], source.getItem().getMaxStackSize()));
                int before = stack.getCount();
                moveItemStackTo(stack, amounts.length, slots.size(), true);
                int moved = before - stack.getCount();
                if (moved == 0) break;
                setAmount(index, amounts[index] - moved);
            }
        } else {
            ItemStack remaining = source.getItem().copy();
            storeQuickMovedStack(remaining);
            source.setByPlayer(remaining);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void clicked(int index, int button, ClickType type, Player player) {
        if (index < 0 || index >= amounts.length || type == ClickType.QUICK_CRAFT) {
            super.clicked(index, button, type, player);
            return;
        }
        // 库存以服务端为准，客户端模板中的 1 不是实际数量。
        if (player.level().isClientSide()) return;
        Slot slot = getSlot(index);
        if (!slot.mayPickup(player)) return;
        ItemStack stored = slot.getItem();
        ItemStack carried = getCarried();
        if (type == ClickType.QUICK_MOVE) {
            quickMoveStack(player, index);
        } else if (type == ClickType.PICKUP && (button == 0 || button == 1)) {
            if (carried.isEmpty()) {
                long count = button == 0 ? amounts[index] : (amounts[index] + 1) / 2;
                setCarried(take(index, (int) Math.min(Integer.MAX_VALUE, count)));
            } else if (stored.isEmpty() || ItemStack.isSameItemSameTags(stored, carried)) {
                if (!slot.mayPlace(carried) && !stored.isEmpty()) {
                    carried.grow(take(index, carried.getMaxStackSize() - carried.getCount()).getCount());
                } else {
                    insert(index, carried, button == 0 ? carried.getCount() : 1);
                }
                setCarried(carried);
            } else if (slot.mayPlace(carried) && amounts[index] <= stored.getMaxStackSize()) {
                ItemStack old = take(index, stored.getMaxStackSize());
                slot.set(carried.copy());
                setCarried(old);
            }
        } else if (type == ClickType.SWAP && (button >= 0 && button < 9 || button == 40)) {
            ItemStack hotbar = player.getInventory().getItem(button);
            if ((hotbar.isEmpty() || slot.mayPlace(hotbar))
                    && (stored.isEmpty() || amounts[index] <= stored.getMaxStackSize())) {
                ItemStack old = stored.isEmpty() ? ItemStack.EMPTY : take(index, stored.getMaxStackSize());
                slot.set(hotbar.copy());
                player.getInventory().setItem(button, old);
            }
        } else if (type == ClickType.THROW && carried.isEmpty() && !stored.isEmpty()) {
            player.drop(take(index, button == 0 ? 1 : stored.getMaxStackSize()), true);
        } else if (type == ClickType.CLONE && player.getAbilities().instabuild && !stored.isEmpty()) {
            setCarried(stored.copyWithCount(stored.getMaxStackSize()));
        } else if (type == ClickType.PICKUP_ALL && !carried.isEmpty()
                && ItemStack.isSameItemSameTags(stored, carried)) {
            carried.grow(take(index, carried.getMaxStackSize() - carried.getCount()).getCount());
            setCarried(carried);
        }
        broadcastChanges();
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return slot.index >= amounts.length && super.canDragTo(slot);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.index >= amounts.length && super.canTakeItemForPickAll(stack, slot);
    }
}
