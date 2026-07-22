package com.infinitestats.compat.jei;

import com.infinitestats.InfiniteStats;
import com.infinitestats.crafting.PortableCraftingMenu;
import com.infinitestats.emc.ModMenuTypes;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.resources.ResourceLocation;

/**
 * JEI 集成：为随身工作台注册合成配方的一键转移（"+"按钮）。
 * 槽位布局：0=结果槽，1..9=3x3 合成网格，10..45=玩家背包+快捷栏。
 */
@JeiPlugin
public class InfiniteStatsJeiPlugin implements IModPlugin {

    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(InfiniteStats.MODID, "jei_plugin");
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(
                PortableCraftingMenu.class,
                ModMenuTypes.PORTABLE_CRAFTING_MENU.get(),
                RecipeTypes.CRAFTING,
                1,   // recipeSlotStart：合成网格从 index 1 开始
                9,   // recipeSlotCount：3x3 = 9
                10,  // inventorySlotStart：玩家背包从 index 10 开始
                36   // inventorySlotCount：27 背包 + 9 快捷栏
        );
    }
}
