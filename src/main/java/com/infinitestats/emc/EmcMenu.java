package com.infinitestats.emc;

import com.infinitestats.InfiniteStats;
import com.infinitestats.network.NetworkHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;

/**
 * EMC 转化桌容器 — 服务端菜单
 *
 * 槽位布局:
 *   Slot 0:                学习槽（放入物品→自动消耗→学习+获得EMC）
 *   Slot 1-9:              玩家快捷栏
 *   Slot 10-36:            玩家主物品栏 (3×9)
 */
public class EmcMenu extends AbstractContainerMenu {

    private final Player player;
    private final Container learnContainer = new SimpleContainer(1);

    // === 槽位像素位置 ===
    public static final int LEARN_X = 230;
    public static final int LEARN_Y = 4;
    public static final int PLAYER_INV_X = 8;
    public static final int PLAYER_INV_Y = 132;
    public static final int PLAYER_HOTBAR_Y = 190;

    // === GUI 尺寸 ===
    public static final int GUI_WIDTH = 300;
    public static final int GUI_HEIGHT = 216;

    public EmcMenu(int windowId, Inventory playerInv) {
        super(ModMenuTypes.EMC_MENU.get(), windowId);
        this.player = playerInv.player;

        // 学习槽 (index 0)
        this.addSlot(new LearnSlot(learnContainer, 0, LEARN_X, LEARN_Y));

        // 玩家快捷栏 (indices 1-9)
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, PLAYER_INV_X + col * 18, PLAYER_HOTBAR_Y));
        }

        // 玩家主物品栏 (indices 10-36)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9,
                        PLAYER_INV_X + col * 18, PLAYER_INV_Y + row * 18));
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();

        if (index == 0) {
            // 学习槽 → 玩家背包
            if (!this.moveItemStackTo(stack, 1, 37, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // 玩家背包 → 学习槽
            if (!this.moveItemStackTo(stack, 0, 1, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        slot.onTake(player, stack);
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    // ==================== 学习槽 ====================

    /**
     * 放入物品时自动消耗、学习并返还 EMC
     */
    private class LearnSlot extends Slot {

        public LearnSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (stack.isEmpty()) return false;
            long emc = EmcDatabase.getEmc(stack);
            return emc > 0;
        }

        @Override
        public void setByPlayer(ItemStack stack) {
            // 玩家手动放入（非 quickMoveStack）
            if (!stack.isEmpty() && player instanceof ServerPlayer sp) {
                consumeAndLearn(sp, stack);
                return;
            }
            super.setByPlayer(stack);
        }

        @Override
        public void set(ItemStack stack) {
            // quickMoveStack 或代码调用
            if (!stack.isEmpty() && player instanceof ServerPlayer sp) {
                consumeAndLearn(sp, stack);
                return;
            }
            super.set(stack);
        }

        private void consumeAndLearn(ServerPlayer sp, ItemStack stack) {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            long emcValue = EmcDatabase.getEmc(stack);
            CompoundTag nbt = stack.getTag();

            sp.getCapability(EmcPlayerDataProvider.EMC_PLAYER_DATA).ifPresent(data -> {
                if (!data.hasLearned(itemId)) {
                    data.learnAndConvert(itemId, emcValue, nbt);
                    NetworkHandler.syncEmcToClient(sp);
                    sp.displayClientMessage(
                            Component.translatable("message.infinitestats.emc.learned",
                                    stack.getHoverName(), formatEmc(emcValue)), false);
                } else {
                    // 已学过：仅返还 EMC（不重复标记），更新 NBT（可能放入了不同版本的手册）
                    if (nbt != null && !nbt.isEmpty()) {
                        data.learnItem(itemId, nbt);
                    }
                    data.addEmc(emcValue);
                    NetworkHandler.syncEmcToClient(sp);
                    sp.displayClientMessage(
                            Component.translatable("message.infinitestats.emc.converted",
                                    stack.getHoverName(), formatEmc(emcValue)), false);
                }
            });

            // 消耗物品（不真正放入槽位）
            this.container.setItem(0, ItemStack.EMPTY);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    // ==================== 工具方法 ====================

    private static String formatEmc(long emc) {
        if (emc >= 1_000_000_000) return String.format("%.1fB", emc / 1_000_000_000.0);
        if (emc >= 1_000_000) return String.format("%.1fM", emc / 1_000_000.0);
        if (emc >= 1_000) return String.format("%.1fK", emc / 1_000.0);
        return String.valueOf(emc);
    }
}
