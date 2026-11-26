package com.infinitestats.compat.jei;

import net.minecraft.world.item.ItemStack;

/**
 * JEI 运行期桥接：让「无尽背包」界面里按 R / U 直接查配方。
 *
 * <p>★ 与 {@code PinyinSearchBridge} 同一条规矩：JEI 的类型只出现在<b>方法体</b>里，
 * 不写进字段类型 / 方法签名 —— 否则没装 JEI 的客户端在类加载阶段就会 NoClassDefFoundError。
 * 因此运行期引用用 {@code Object} 存，需要时再强转。
 */
public final class JeiBridge {

    /** 实际类型是 IJeiRuntime，没装 JEI 或还没就绪时为 null。 */
    private static volatile Object runtime;

    private JeiBridge() {
    }

    /** 由 {@code InfiniteStatsJeiPlugin#onRuntimeAvailable} 调用（JEI 已就绪的环境）。 */
    public static void setRuntime(Object jeiRuntime) {
        runtime = jeiRuntime;
    }

    public static void clearRuntime() {
        runtime = null;
    }

    /** JEI 是否可用（不触碰任何 JEI 类型）。 */
    public static boolean available() {
        return runtime != null;
    }

    /**
     * 把搜索词同步给 JEI 的物品列表（精致存储 / 超越维度都这么做）。
     * <p>
     * 传空串等于清空过滤 —— 关闭界面时务必调一次，否则 JEI 的物品列表会一直只剩
     * 上次搜索的结果，玩家还以为 JEI 坏了。
     */
    public static void syncFilter(String query) {
        Object ref = runtime;
        if (ref == null) return;
        try {
            ((mezz.jei.api.runtime.IJeiRuntime) ref)
                    .getIngredientFilter().setFilterText(query == null ? "" : query);
        } catch (Throwable ignored) {
            // JEI 版本差异 / 运行期卸载：忽略
        }
    }

    /**
     * 打开某个物品的 JEI 配方界面。
     *
     * @param uses true = 这个物品能做什么（作为材料）；false = 怎么做出这个物品（作为产物）
     */
    public static void show(ItemStack stack, boolean uses) {
        Object ref = runtime;
        if (ref == null || stack == null || stack.isEmpty()) return;
        try {
            mezz.jei.api.runtime.IJeiRuntime rt = (mezz.jei.api.runtime.IJeiRuntime) ref;
            mezz.jei.api.recipe.RecipeIngredientRole role = uses
                    ? mezz.jei.api.recipe.RecipeIngredientRole.INPUT
                    : mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT;
            rt.getRecipesGui().show(rt.getJeiHelpers().getFocusFactory()
                    .createFocus(role, mezz.jei.api.constants.VanillaTypes.ITEM_STACK, stack));
        } catch (Throwable ignored) {
            // JEI 版本差异 / 运行期卸载：静默降级，不影响背包本身
        }
    }
}
