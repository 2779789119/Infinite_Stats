package com.infinitestats.util;

import net.minecraft.world.item.ItemStack;

/**
 * ItemStack 的跨版本小工具。
 *
 * <p>{@code ItemStack#copyWithCount(int)} 是 1.20 才加的方法，1.19.2 只能 copy 之后手动改数量。
 * 本模组在烧炼 / 批量存储 / 网络同步里大量用到它，统一收到这里，避免每处都写两行。
 */
public final class ItemStacks {

    private ItemStacks() {
    }

    /** 复制一份并把数量改成 count（count ≤ 0 时返回空栈）。 */
    public static ItemStack copyWithCount(ItemStack stack, int count) {
        if (stack == null || stack.isEmpty() || count <= 0) return ItemStack.EMPTY;
        ItemStack copy = stack.copy();
        copy.setCount(count);
        return copy;
    }
}
