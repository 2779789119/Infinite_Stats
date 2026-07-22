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
import net.minecraft.world.level.Level;

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

    public PortableCraftingMenu(int windowId, Inventory inv) {
        super(ModMenuTypes.PORTABLE_CRAFTING_MENU.get(), windowId);
        this.player = inv.player;
        this.level = inv.player.level();
        this.stats = inv.player.getCapability(PlayerStatsProvider.PLAYER_STATS).orElseGet(PlayerStats::new);
        // 倍率视图：与 PlayerStats 中的倍率字段实时联动，并随容器数据自动同步到客户端
        this.data = new ContainerData() {
            @Override
            public int getCount() {
                return 1;
            }

            @Override
            public int get(int index) {
                return (int) Math.min(Integer.MAX_VALUE, stats.getCraftingMultiplier());
            }

            @Override
            public void set(int index, int value) {
                stats.setCraftingMultiplier(value);
            }
        };
        this.addDataSlots(this.data);

        // 结果槽（index 0）
        this.addSlot(new ResultSlot(this.player, this.craftSlots, this.resultSlots, 0, 124, 35));
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

    /** 任意网格槽变化 → 重新计算合成结果并同步到客户端（复刻 vanilla CraftingMenu 逻辑） */
    @Override
    public void slotsChanged(Container container) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
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
        if (!result.isEmpty()) {
            int multiplier = (int) Math.max(1, stats.getCraftingMultiplier());
            result = result.copy();
            result.setCount(result.getCount() * multiplier);
        }
        resultSlots.setItem(0, result);
        setRemoteSlot(0, result);
        serverPlayer.connection.send(
                new ClientboundContainerSetSlotPacket(containerId, incrementStateId(), 0, result));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index == 0) {
                // 结果槽 → 玩家背包
                if (!this.moveItemStackTo(stack, 10, 46, true)) return ItemStack.EMPTY;
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
        }
        return result;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        for (int i = 0; i < craftSlots.getContainerSize(); i++) {
            ItemStack stack = craftSlots.getItem(i);
            if (!stack.isEmpty()) {
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
            }
        }
        craftSlots.clearContent();
        resultSlots.clearContent();
    }
}
