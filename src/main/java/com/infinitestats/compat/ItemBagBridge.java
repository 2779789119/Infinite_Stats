package com.infinitestats.compat;

import com.infinitestats.bag.ItemBag;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 本模组「无尽背包」的网络桥接。
 * <p>
 * 与其它存储桥接不同，这里没有反射：{@link ItemBag} 是自己的类，直接调用。
 * 之所以仍然放在 {@code compat} 包里，是为了让 {@link NetworkIO} 的五种外部存储
 * 与自带背包走同一套 {@code listItems / extract / insert} 外观 —— 随身工作台、
 * 随身熔炉、自动入库、成品仓因此不需要为背包写任何专门分支。
 */
public final class ItemBagBridge {

    private ItemBagBridge() {
    }

    /** 列出背包里的全部物品（同物品同 NBT 已合并，数量被夹到 int 范围）。 */
    public static List<ItemStack> listItems(Object bagObj) {
        if (!(bagObj instanceof ItemBag bag)) return List.of();
        return bag.snapshot();
    }

    /** 取出最多 {@code count} 个与模板同类同 NBT 的物品。 */
    public static ItemStack extract(Object bagObj, ItemStack template, int count) {
        if (!(bagObj instanceof ItemBag bag) || template == null || template.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return bag.extract(template, count);
    }

    /** 存入，返回没能存下的剩余（空表示全部入包）。 */
    public static ItemStack insert(Object bagObj, ItemStack stack) {
        if (!(bagObj instanceof ItemBag bag) || stack.isEmpty()) return stack;
        long remaining = bag.insert(stack);
        if (remaining <= 0) return ItemStack.EMPTY;
        ItemStack out = stack.copy();
        out.setCount((int) Math.min(remaining, Integer.MAX_VALUE));
        return out;
    }
}
