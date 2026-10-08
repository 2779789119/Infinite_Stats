package com.infinitestats.crafting;

import com.infinitestats.emc.ModMenuTypes;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 随身「进阶高级附魔台」菜单：<b>一个物品槽 + 玩家背包</b>，没有别的槽。
 * <p>
 * 之所以和原来的随身附魔台（继承原版 {@code EnchantmentMenu} + 青金石槽）完全换掉：
 * 进阶高级附魔台是「自己挑附魔」的玩法 —— 附魔列表、等级、价格、修复与回收全部由
 * {@link PortableInfuser} 现算，原版那套「随机三档 + 数据槽同步线索」的模型用不上，
 * 硬塞进去反而要绕开一大堆原版状态。现在菜单只负责最基础的事：物品进出与背包同步。
 * <p>
 * <b>双端同一个类</b>：没有世界侧依赖（不读书架、不写方块），所以客户端不需要另建一份菜单，
 * 直接注册菜单类型即可（见 {@link ModMenuTypes#PORTABLE_ENCHANTING_MENU}）。
 * <p>
 * 界面里的列表与价格由客户端按 {@link PortableInfuser} 的纯函数自行计算（和服务端同源），
 * 只有「点了附魔 / 修复 / 回收」才发包，服务端再整体校验一遍。
 */
public class PortableInfuserMenu extends AbstractContainerMenu {

    /** 附魔台上的物品槽（唯一的功能槽）。 */
    public static final int SLOT_ITEM = 0;

    /** 玩家背包区间（含快捷栏）：[INV_START, INV_END)。 */
    private static final int INV_START = 1;
    private static final int INV_END = 37;

    // ===== 槽位坐标：与 PortableInfuserScreen 的布局共用（界面直接引用这些常量） =====

    /** 物品槽（16×16 物品区左上角）。 */
    public static final int ITEM_SLOT_X = 24;
    public static final int ITEM_SLOT_Y = 30;

    /** 玩家背包首行 / 快捷栏（9 列，列距 18）。 */
    public static final int INV_X = 43;
    public static final int INV_Y = 186;
    public static final int HOTBAR_Y = 244;

    private final Container input = new SimpleContainer(1);

    public PortableInfuserMenu(int windowId, Inventory inventory) {
        super(ModMenuTypes.PORTABLE_ENCHANTING_MENU.get(), windowId);

        this.addSlot(new Slot(this.input, 0, ITEM_SLOT_X, ITEM_SLOT_Y) {
            /** 只收能处理的物品：可附魔物品、已带附魔的物品、书（做附魔书用）。 */
            @Override
            public boolean mayPlace(ItemStack stack) {
                return PortableInfuser.isSupported(stack);
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inventory, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inventory, col, INV_X + col * 18, HOTBAR_Y));
        }
    }

    /** 附魔台上的物品（界面与服务端的操作对象）。 */
    public Container getInput() {
        return this.input;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index == SLOT_ITEM) {
            if (!this.moveItemStackTo(stack, INV_START, INV_END, true)) return ItemStack.EMPTY;
        } else {
            if (!this.moveItemStackTo(stack, SLOT_ITEM, SLOT_ITEM + 1, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    /** 关界面时把台上的物品还给玩家，避免东西随界面一起消失。 */
    @Override
    public void removed(Player player) {
        super.removed(player);
        this.clearContainer(player, this.input);
    }
}
