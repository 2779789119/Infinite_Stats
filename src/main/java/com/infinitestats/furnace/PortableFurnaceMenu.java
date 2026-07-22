package com.infinitestats.furnace;

import com.infinitestats.emc.ModMenuTypes;
import com.infinitestats.Config;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeHooks;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * 随身熔炉菜单。
 * 所有物品与燃烧/冶炼进度都存在玩家 PlayerStats 的 PlayerFurnaceData 中，
 * 因此关闭界面后状态不会丢失，且冶炼在后台持续进行。
 *
 * 支持 {@link PlayerFurnaceData#INPUT_COUNT} 个并行输入槽，每个输入槽对应一个
 * 输出槽，可同时熔炼多种矿物，共享同一份燃料。
 */
public class PortableFurnaceMenu extends AbstractContainerMenu {

    private final PlayerFurnaceData furnaceData;
    private final FurnaceContainer furnace;
    private final ContainerData data;

    public PortableFurnaceMenu(int windowId, Inventory inv) {
        super(ModMenuTypes.PORTABLE_FURNACE_MENU.get(), windowId);

        Player player = inv.player;
        this.furnaceData = player.getCapability(PlayerStatsProvider.PLAYER_STATS)
                .orElseGet(PlayerStats::new).getFurnaceData();

        this.furnace = new FurnaceContainer(furnaceData);
        this.data = new FurnaceData(furnaceData.getData());

        // 原版熔炉槽位布局：单输入 + 单输出 + 燃料槽
        this.addSlot(new UnlimitedSlot(furnace, PlayerFurnaceData.FUEL_SLOT, 56, 53));
        this.addSlot(new UnlimitedSlot(furnace, PlayerFurnaceData.inputSlot(0), 56, 17));
        this.addSlot(new UnlimitedSlot(furnace, PlayerFurnaceData.outputSlot(0), 116, 35));

        // 玩家背包
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 9; j++) {
                this.addSlot(new Slot(inv, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));
            }
        }
        // 玩家快捷栏
        for (int i = 0; i < 9; i++) {
            this.addSlot(new Slot(inv, i, 8 + i * 18, 142));
        }

        this.addDataSlots(data);
    }

    public PlayerFurnaceData getFurnaceData() {
        return furnaceData;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = slot.getItem();

        if (index < PlayerFurnaceData.TOTAL_SLOTS) {
            // 熔炉槽 -> 玩家背包（TOTAL_SLOTS..），熔炉槽可能持有超过 64 的数量，需分批放入背包
            int remaining = PlayerFurnaceData.rawGetCount(source);
            ItemStack template = source.copyWithCount(1);
            for (int i = PlayerFurnaceData.TOTAL_SLOTS; i < this.slots.size() && remaining > 0; i++) {
                Slot dst = this.slots.get(i);
                ItemStack d = dst.getItem();
                if (d.isEmpty()) {
                    int put = Math.min(remaining, Math.min(64, source.getMaxStackSize()));
                    dst.set(template.copyWithCount(put));
                    remaining -= put;
                } else if (ItemStack.isSameItemSameTags(d, source)) {
                    int space = Math.min(64, d.getMaxStackSize()) - d.getCount();
                    if (space > 0) {
                        int put = Math.min(remaining, space);
                        d.grow(put);
                        remaining -= put;
                    }
                }
            }
            furnace.setAmountOnly(index, remaining);
            slot.set(furnace.getItem(index));
            return ItemStack.EMPTY;
        } else {
            // 玩家背包 -> 熔炉（燃料进燃料槽，矿物进输入槽：优先同类输入槽，其次空输入槽）
            int target;
            if (ForgeHooks.getBurnTime(source, RecipeType.SMELTING) > 0) {
                target = PlayerFurnaceData.FUEL_SLOT;
            } else {
                target = -1;
                for (int i = 0; i < PlayerFurnaceData.INPUT_COUNT; i++) {
                    int idx = PlayerFurnaceData.inputSlot(i);
                    ItemStack d = furnace.getItem(idx);
                    if (!d.isEmpty() && ItemStack.isSameItemSameTags(d, source)) {
                        target = idx;
                        break;
                    }
                }
                if (target == -1) {
                    for (int i = 0; i < PlayerFurnaceData.INPUT_COUNT; i++) {
                        int idx = PlayerFurnaceData.inputSlot(i);
                        if (furnace.getItem(idx).isEmpty()) {
                            target = idx;
                            break;
                        }
                    }
                }
                if (target == -1) return ItemStack.EMPTY; // 没有空余输入槽
            }

            int remaining = source.getCount();
            ItemStack cur = furnace.getItem(target);
            if (cur.isEmpty()) {
                furnaceData.setSlot(target, source.copyWithCount(Math.min(remaining, PlayerFurnaceData.UNBOUNDED)));
                remaining = 0;
            } else if (ItemStack.isSameItemSameTags(cur, source)) {
                long curAmt = furnaceData.getAmount(target);
                long space = (long) PlayerFurnaceData.UNBOUNDED - curAmt;
                int put = (int) Math.min(remaining, space);
                furnaceData.setAmountOnly(target, curAmt + put);
                remaining -= put;
            } else {
                // 目标槽已被其他物品占用，放弃本次转移
                remaining = source.getCount();
            }
            // 同步熔炉槽显示
            this.slots.get(target).set(furnace.getItem(target));
            // 更新来源背包槽
            if (remaining <= 0) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.set(source.copyWithCount(remaining));
            }
            return ItemStack.EMPTY;
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void removed(Player player) {
        // 不在关闭时把物品退回背包——它们保存在玩家的 PlayerStats 中
        super.removed(player);
    }

    /** 进度查询，供界面使用（读取已同步的 ContainerData） */
    public boolean isLit() {
        return data.get(0) > 0;
    }

    public float getLitProgress() {
        int lit = data.get(0);
        int dur = data.get(1);
        if (dur <= 0) return 0;
        return lit / (float) dur;
    }

    public float getCookProgress(int i) {
        int prog = data.get(2 + 2 * i);
        int total = data.get(3 + 2 * i);
        if (total <= 0) return 0;
        return prog / (float) total;
    }

    /** 当前熔炉加速等级（读取已同步的 ContainerData）。 */
    public int getSpeedLevel() {
        return Math.max(0, data.get(2 + 2 * PlayerFurnaceData.INPUT_COUNT));
    }

    /** 当前熔炼速度倍率（加速等级 + 1）。 */
    public int getSpeedMultiplier() {
        return 1 + getSpeedLevel();
    }

    /** 每级加速消耗的可分配点数。 */
    public int getSpeedCost() {
        return Config.FURNACE_SPEED_COST.get();
    }

    /** 绑定到 PlayerFurnaceData 的容器视图，槽位直接读写持久化数据（数量无上限）。 */
    private static class FurnaceContainer implements Container {
        private final PlayerFurnaceData data;

        FurnaceContainer(PlayerFurnaceData data) {
            this.data = data;
        }

        @Override
        public int getContainerSize() {
            return PlayerFurnaceData.TOTAL_SLOTS;
        }

        @Override
        public boolean isEmpty() {
            for (int i = 0; i < PlayerFurnaceData.TOTAL_SLOTS; i++) {
                if (data.getAmount(i) > 0) return false;
            }
            return true;
        }

        @Override
        public ItemStack getItem(int index) {
            return data.getStack(index);
        }

        @Override
        public ItemStack removeItem(int index, int count) {
            return data.removeSlot(index, count);
        }

        @Override
        public ItemStack removeItemNoUpdate(int index) {
            return data.removeSlot(index, (int) Math.min(data.getAmount(index), PlayerFurnaceData.UNBOUNDED));
        }

        @Override
        public void setItem(int index, ItemStack stack) {
            data.setSlot(index, stack);
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
            for (int i = 0; i < PlayerFurnaceData.TOTAL_SLOTS; i++) {
                data.clearSlot(i);
            }
        }

        /** 仅更新某槽数量（模板不变），供 shift 转移逻辑使用。 */
        void setAmountOnly(int index, long amount) {
            data.setAmountOnly(index, amount);
        }
    }

    /** 无上限堆叠的槽位：解除 64 限制，并绕过 setCount 钳制进行 merge。 */
    private static class UnlimitedSlot extends Slot {
        UnlimitedSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public int getMaxStackSize() {
            return PlayerFurnaceData.UNBOUNDED;
        }

        @Override
        public ItemStack safeInsert(ItemStack stack, int amount) {
            ItemStack cur = getItem();
            if (!cur.isEmpty() && !ItemStack.isSameItemSameTags(cur, stack)) {
                return stack;
            }
            int limit = getMaxStackSize(stack);
            long space = (long) limit - PlayerFurnaceData.rawGetCount(cur);
            int take = (int) Math.min(amount, space);
            if (take <= 0) {
                return stack;
            }
            if (cur.isEmpty()) {
                set(stack.split(take));
            } else {
                PlayerFurnaceData.rawSetCount(cur, (int) (PlayerFurnaceData.rawGetCount(cur) + take));
            }
            return stack;
        }
    }

    /** 绑定到 PlayerFurnaceData 进度数组的 ContainerData 视图。 */
    private static class FurnaceData implements ContainerData {
        private final int[] arr;

        FurnaceData(int[] arr) {
            this.arr = arr;
        }

        @Override
        public int get(int index) {
            return arr[index];
        }

        @Override
        public void set(int index, int value) {
            arr[index] = value;
        }

        @Override
        public int getCount() {
            return arr.length;
        }
    }
}
