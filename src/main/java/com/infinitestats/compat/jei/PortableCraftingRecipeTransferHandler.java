package com.infinitestats.compat.jei;

import com.infinitestats.crafting.PortableCraftingMenu;
import com.infinitestats.emc.ModMenuTypes;
import com.infinitestats.network.NetworkHandler;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 随身工作台的 JEI 一键转移配方处理器。
 * 与默认实现不同：默认实现仅从玩家背包（槽位 10..45）取材料；
 * 本处理器在背包不足时，会把缺失的材料从存储网络（RS / AE2 / Beyond Dimensions / 背包 / 汤姆存储）补充到 3×3 网格。
 *
 * <p>实现要点：{@code transferRecipe(doTransfer=false)} 仅在客户端做乐观校验；
 * {@code doTransfer=true} 时由客户端把 3×3 材料布局发送给服务端，由服务端从背包/网络实际取料并填入网格，
 * 因此网络取料（需要服务端 {@code ServerLevel}）能够正确执行。</p>
 */
public class PortableCraftingRecipeTransferHandler implements IRecipeTransferHandler<PortableCraftingMenu, CraftingRecipe> {

    @Override
    public Class<? extends PortableCraftingMenu> getContainerClass() {
        return PortableCraftingMenu.class;
    }

    @Override
    public Optional<MenuType<PortableCraftingMenu>> getMenuType() {
        return Optional.of(ModMenuTypes.PORTABLE_CRAFTING_MENU.get());
    }

    @Override
    public mezz.jei.api.recipe.RecipeType<CraftingRecipe> getRecipeType() {
        return RecipeTypes.CRAFTING;
    }

    @Override
    public IRecipeTransferError transferRecipe(PortableCraftingMenu menu, CraftingRecipe recipe,
                                               IRecipeSlotsView recipeSlots, Player player,
                                               boolean maxTransfer, boolean doTransfer) {
        if (!doTransfer) {
            // 校验阶段：乐观允许。具体可行性（背包/网络是否齐全）由服务端最终判定。
            return null;
        }
        Ingredient[] grid = buildGrid(recipe);
        NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.CraftingRecipeFillPacket(grid));
        return null;
    }

    /**
     * 将配方的材料布局转换为左上对齐的 3×3 {@link Ingredient} 数组。
     * 对于有序（Shaped）配方，按配方的宽高放置在网格左上角，与 vanilla 合成匹配一致。
     */
    private static Ingredient[] buildGrid(CraftingRecipe recipe) {
        Ingredient[] grid = new Ingredient[9];
        Arrays.fill(grid, Ingredient.EMPTY);
        List<Ingredient> ings = recipe.getIngredients();
        int w = 3, h = 3;
        if (recipe instanceof ShapedRecipe sr) {
            w = sr.getWidth();
            h = sr.getHeight();
        } else if (!ings.isEmpty()) {
            h = (ings.size() + 2) / 3;
        }
        for (int ry = 0; ry < h; ry++) {
            for (int rx = 0; rx < w; rx++) {
                int idx = ry * w + rx;
                if (idx >= ings.size()) continue;
                int slot = ry * 3 + rx;
                if (slot < 9) grid[slot] = ings.get(idx);
            }
        }
        return grid;
    }
}
