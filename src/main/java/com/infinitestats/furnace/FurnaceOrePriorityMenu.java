package com.infinitestats.furnace;

import com.infinitestats.emc.ModMenuTypes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * “矿石优先顺序”设置界面的菜单。
 * 仅作为设置界面，不含实际冶炼槽位；但会把玩家背包渲染为只读槽位，
 * 供玩家点击背包中的矿物直接加入优先列表。
 */
public class FurnaceOrePriorityMenu extends AbstractContainerMenu {

    /** 玩家背包槽位在 GUI 内的坐标（与 FurnaceOrePriorityScreen 绘制位置一致）。 */
    public static final int INV_X = 69; // 在 300 宽界面中居中： (300 - 9*18) / 2
    public static final int INV_MAIN_Y = 160;
    public static final int INV_HOTBAR_Y = 218;

    public FurnaceOrePriorityMenu(int windowId, Inventory inv) {
        super(ModMenuTypes.FURNACE_ORE_PRIORITY_MENU.get(), windowId);
        // 主背包 3 行 × 9 列（只读展示）
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new ReadOnlySlot(inv, col + row * 9 + 9, INV_X + col * 18, INV_MAIN_Y + row * 18));
            }
        }
        // 快捷栏 9 格（只读展示）
        for (int col = 0; col < 9; col++) {
            addSlot(new ReadOnlySlot(inv, col, INV_X + col * 18, INV_HOTBAR_Y));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    /** 只读槽位：仅用于展示玩家背包，不可取出/放入；点击行为由屏幕拦截用于“加入优先列表”。 */
    public static class ReadOnlySlot extends Slot {
        public ReadOnlySlot(Inventory inv, int index, int x, int y) {
            super(inv, index, x, y);
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
