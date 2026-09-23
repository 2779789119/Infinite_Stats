package com.infinitestats.crafting;

import com.infinitestats.Config;
import com.infinitestats.emc.ModMenuTypes;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;

import com.infinitestats.compat.NetworkHandle;
import com.infinitestats.compat.NetworkIO;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 随身工作台菜单（内置工作台属性）。
 * 使用 vanilla 的 TransientCraftingContainer（其 setItem/removeItem 会主动回调
 * menu.slotsChanged）+ ResultContainer，确保放入材料后能实时计算并同步结果槽。
 * 同时通过标准槽位布局兼容 JEI 一键转移配方。
 */
public class PortableCraftingMenu extends AbstractContainerMenu {

    private final CraftingContainer craftSlots = new TransientCraftingContainer(this, 3, 3);
    private final ResultContainer resultSlots = new ResultContainer();
    private final Player player;
    private final Level level;
    private final PlayerStats stats;
    private final ContainerData data;
    /** 成品去向：false=放入玩家背包（默认），true=放入存储空间。 */
    private boolean outputToStorage = false;
    private boolean fillingGrid;

    public PortableCraftingMenu(int windowId, Inventory inv) {
        super(ModMenuTypes.PORTABLE_CRAFTING_MENU.get(), windowId);
        this.player = inv.player;
        this.level = inv.player.level();
        this.stats = inv.player.getCapability(PlayerStatsProvider.PLAYER_STATS).orElseGet(PlayerStats::new);
        // 倍率视图与成品去向：随容器数据自动同步到客户端
        //   index 0 = 倍率；index 1 = 成品去向（0=背包，1=存储）
        this.data = new ContainerData() {
            @Override
            public int getCount() {
                return 2;
            }

            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> (int) Math.min(Integer.MAX_VALUE, stats.getCraftingMultiplier());
                    case 1 -> outputToStorage ? 1 : 0;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                switch (index) {
                    case 0 -> stats.setCraftingMultiplier(value);
                    case 1 -> outputToStorage = value != 0;
                }
            }
        };
        this.addDataSlots(this.data);

        // 结果槽（index 0）：取出成品后自动从存储网络补充被消耗的材料
        this.addSlot(new AutoRefillResultSlot(this.player, this.craftSlots, this.resultSlots, 0, 124, 35, this));
        // 3x3 合成网格（index 1..9）
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                this.addSlot(new Slot(this.craftSlots, col + row * 3, 30 + col * 18, 17 + row * 18));
            }
        }
        // 玩家背包（27 格，index 10..36）
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        // 快捷栏（9 格，index 37..45）
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inv, col, 8 + col * 18, 142));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    /** 当前随身工作台物品倍率（用于界面显示）。 */
    public int getCraftingMultiplier() {
        return (int) Math.max(1, stats.getCraftingMultiplier());
    }

    /** 提升一级倍率所需的可分配点数（用于界面显示）。 */
    public int getMultiplierCost() {
        return Config.CRAFTING_MULTIPLIER_COST.get();
    }

    /** 当前成品是否送往存储空间（true=存储网络，false=玩家背包）。 */
    public boolean isOutputToStorage() {
        return this.outputToStorage;
    }

    /** 切换成品去向（背包 ↔ 存储空间）。 */
    public void toggleOutputToStorage() {
        if (level.isClientSide()) return;
        this.outputToStorage = !this.outputToStorage;
        this.broadcastChanges();
    }

    /** 任意网格槽变化 → 重新计算合成结果并同步到客户端（复刻 vanilla CraftingMenu 逻辑） */
    @Override
    public void slotsChanged(Container container) {
        if (fillingGrid || level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        ItemStack result = ItemStack.EMPTY;
        Optional<CraftingRecipe> optional = level.getServer().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, craftSlots, level);
        if (optional.isPresent()) {
                CraftingRecipe recipe = optional.get();
            if (resultSlots.setRecipeUsed(level, serverPlayer, recipe)) {
                ItemStack assembled = recipe.assemble(craftSlots, level.registryAccess());
                if (assembled.isItemEnabled(level.enabledFeatures())) {
                    result = assembled;
                }
            }
        }
        // 应用随身工作台物品倍率（影响每次合成的产出数量）
        // 限制单次合成最大产出为一组，防止Shift合成时一次性产出过多物品导致卡顿
        if (!result.isEmpty()) {
            int multiplier = (int) Math.max(1, stats.getCraftingMultiplier());
            result = result.copy();
            result.setCount(Math.min(result.getCount() * multiplier, result.getMaxStackSize()));
        }
        resultSlots.setItem(0, result);
        setRemoteSlot(0, result);
        serverPlayer.connection.send(
                new ClientboundContainerSetSlotPacket(containerId, incrementStateId(), 0, result));
    }

    public void refreshResult() {
        slotsChanged(craftSlots);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index == 0) {
                // 结果槽：按当前去向设置，把成品送往背包或存储空间
                if (this.outputToStorage) {
                    List<NetworkHandle> nets = NetworkIO.getNetworks(player);
                    if (!nets.isEmpty()) {
                        // 优先把成品存入网络，存不下的剩余再退回背包
                        ItemStack remaining = NetworkIO.insert(nets, stack.copy());
                        int stored = stack.getCount() - remaining.getCount();
                        if (stored > 0) stack.split(stored);
                        if (!stack.isEmpty()
                                && !this.moveItemStackTo(stack, 10, 46, true) && stored == 0) {
                            return ItemStack.EMPTY;
                        }
                    } else if (!this.moveItemStackTo(stack, 10, 46, true)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!this.moveItemStackTo(stack, 10, 46, true)) {
                    return ItemStack.EMPTY;
                }
                slot.onQuickCraft(stack, result);
            } else if (index >= 10) {
                // 玩家背包 → 合成网格
                if (!this.moveItemStackTo(stack, 1, 10, false)) return ItemStack.EMPTY;
            } else {
                // 合成网格 → 玩家背包
                if (!this.moveItemStackTo(stack, 10, 46, false)) return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
            else slot.setChanged();
            slot.onTake(player, stack);
            if (index == 0 && !stack.isEmpty()) player.drop(stack, false);
        }
        return result;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (level.isClientSide()) return;
        List<NetworkHandle> nets = NetworkIO.getNetworks(player);
        for (int i = 0; i < craftSlots.getContainerSize(); i++) {
            ItemStack stack = craftSlots.removeItemNoUpdate(i);
            returnMaterial(nets, stack);
        }
        resultSlots.clearContent();
    }

    private void returnMaterial(List<NetworkHandle> nets, ItemStack stack) {
        if (stack.isEmpty()) return;
        ItemStack remaining = NetworkIO.insert(nets, stack.copy());
        if (!player.getInventory().add(remaining) && !remaining.isEmpty()) {
            player.drop(remaining, false);
        }
    }

    // ========== Refined Storage 联动 ==========

    /**
     * 按照给定 3×3 材料布局（左上对齐）从背包优先、再从存储网络补充材料到合成网格。
     * 用于 JEI 一键转移配方：即使材料存放在 RS / AE2 / Beyond Dimensions / 背包 / 汤姆存储 等网络中也能填入。
     *
     * @param grid 长度 9 的 {@link Ingredient} 数组，下标对应 3×3 网格槽位（0..8），空位用 {@link Ingredient#EMPTY}
     */
    public void fillGridFromIngredients(Ingredient[] grid) {
        if (level.isClientSide() || !(player instanceof ServerPlayer sp) || grid.length != 9) return;
        List<NetworkHandle> nets = NetworkIO.getNetworks(player);
        ItemStack[] spare = new ItemStack[9];
        ItemStack[] result = new ItemStack[9];
        ItemStack[] inventory = new ItemStack[player.getInventory().getContainerSize()];
        int[] originalCounts = new int[inventory.length];
        List<ItemStack> borrowed = new ArrayList<>();
        for (int i = 0; i < inventory.length; i++) {
            inventory[i] = player.getInventory().getItem(i).copy();
            originalCounts[i] = inventory[i].getCount();
        }

        // 先保留位置和配方都匹配的整堆，其余材料进入可复用的临时库存。
        for (int i = 0; i < 9; i++) {
            ItemStack current = craftSlots.getItem(i).copy();
            boolean keep = grid[i] != null && !grid[i].isEmpty() && grid[i].test(current);
            result[i] = keep ? current : ItemStack.EMPTY;
            spare[i] = keep ? ItemStack.EMPTY : current;
        }
        for (int i = 0; i < 9; i++) {
            Ingredient ingredient = grid[i];
            if (ingredient == null || ingredient.isEmpty() || !result[i].isEmpty()) continue;
            ItemStack taken = takeOne(spare, ingredient);
            if (taken.isEmpty()) {
                for (ItemStack kept : result) {
                    if (kept.getCount() > 1 && ingredient.test(kept)) {
                        taken = kept.split(1);
                        break;
                    }
                }
            }
            if (taken.isEmpty()) taken = takeOne(inventory, ingredient);
            if (taken.isEmpty()) {
                for (ItemStack match : ingredient.getItems()) {
                    taken = NetworkIO.extract(nets, match.copyWithCount(1), 1);
                    if (!taken.isEmpty()) {
                        borrowed.add(taken.copy());
                        break;
                    }
                }
            }
            if (taken.isEmpty()) {
                for (ItemStack stack : borrowed) returnMaterial(nets, stack);
                sp.sendSystemMessage(Component.literal("§c材料不足，已保留原配方和材料"));
                return;
            }
            result[i] = taken;
        }

        // 所有材料已预留，才提交背包和网格，失败路径不会清空原材料。
        fillingGrid = true;
        try {
            // 网络取料可能更新背包中存储物品的 NBT，只扣除实际预留的数量。
            for (int i = 0; i < inventory.length; i++) {
                int consumed = originalCounts[i] - inventory[i].getCount();
                if (consumed > 0) player.getInventory().removeItem(i, consumed);
            }
            for (int i = 0; i < 9; i++) craftSlots.setItem(i, result[i]);
            for (ItemStack stack : spare) returnMaterial(nets, stack);
        } finally {
            fillingGrid = false;
        }
        refreshResult();
        player.getInventory().setChanged();
        broadcastChanges();
    }

    private static ItemStack takeOne(ItemStack[] inventory, Ingredient ingredient) {
        for (ItemStack stack : inventory) {
            if (!stack.isEmpty() && ingredient.test(stack)) return stack.split(1);
        }
        return ItemStack.EMPTY;
    }

    /**
     * 自带「自动补充材料」的结果槽：当玩家取出一份成品后，原版逻辑会消耗网格中
     * 对应的一份材料（每个槽 -1，并处理余料/容器物）。取出之后，这里按取出前的网格
     * 快照，把每个被消耗掉的槽位从存储网络补回差额，使网格始终保持满料，可连续制作。
     * 仅在服务端执行补充；若未连接任何存储网络则不做补充（玩家仍可手动放料）。
     */
    private static class AutoRefillResultSlot extends ResultSlot {
        private final PortableCraftingMenu menu;
        private final CraftingContainer craftSlots;

        AutoRefillResultSlot(Player player, CraftingContainer craftingContainer,
                             ResultContainer resultContainer, int slot, int x, int y,
                             PortableCraftingMenu menu) {
            super(player, craftingContainer, resultContainer, slot, x, y);
            this.menu = menu;
            this.craftSlots = craftingContainer;
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            // 记录取出前各网格槽的物品与数量
            ItemStack[] before = new ItemStack[craftSlots.getContainerSize()];
            for (int i = 0; i < before.length; i++) {
                before[i] = craftSlots.getItem(i).copy();
            }
            // 交给原版逻辑消耗一份材料（每个槽 -1，并处理余料/容器物）
            super.onTake(player, stack);
            if (player.level().isClientSide() || !(player instanceof ServerPlayer)) return;
            List<NetworkHandle> nets = NetworkIO.getNetworks(player);
            if (nets.isEmpty()) return;
            for (int i = 0; i < before.length; i++) {
                ItemStack pre = before[i];
                if (pre.isEmpty()) continue; // 取出前为空的槽不补充，避免误拉无关物品
                ItemStack now = craftSlots.getItem(i);
                if (!now.isEmpty() && !ItemStack.isSameItemSameTags(pre, now)) continue;
                int deficit = pre.getCount() - now.getCount();
                if (deficit > 0) {
                    ItemStack got = NetworkIO.extract(nets, pre.copyWithCount(deficit), deficit);
                    if (!got.isEmpty()) {
                        if (now.isEmpty()) {
                            craftSlots.setItem(i, got);
                        } else {
                            now.grow(got.getCount());
                            craftSlots.setItem(i, now);
                        }
                    }
                }
            }
            menu.broadcastChanges();
        }
    }
}
