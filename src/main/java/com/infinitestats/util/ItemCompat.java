package com.infinitestats.util;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * 跨版本判断原版物品类型的兜底工具。
 *
 * <h2>为什么不用 instanceof</h2>
 * 1.20 才有 {@code BrushItem}（刷子），1.19.2 的原版根本没有这个类，直接写
 * {@code instanceof BrushItem} 连编译都过不去。而「刷子不能被使用速度加速」这条规则
 * 仍然值得保留 —— 模组刷子（含后续版本移植回来的原版刷子）注册名里都带 {@code brush}。
 * <p>
 * 这里按注册名判断：命中原版类时行为与 1.20 完全一致，未命中时也不会误伤普通物品。
 */
public final class ItemCompat {

    private ItemCompat() {
    }

    /** 是否是「刷子」类物品（1.20 的 BrushItem，或任何注册名带 brush 的模组物品）。 */
    public static boolean isBrush(Item item) {
        if (item == null) return false;
        ResourceLocation id = Registry.ITEM.getKey(item);
        return id != null && id.getPath().contains("brush");
    }
}
