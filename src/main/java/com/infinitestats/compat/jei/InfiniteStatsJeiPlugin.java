package com.infinitestats.compat.jei;

import com.infinitestats.InfiniteStats;
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
        // 使用自定义处理器：背包材料不足时，自动从存储网络（RS/AE2/BD/背包/汤姆）补充到 3×3 网格
        registration.addRecipeTransferHandler(
                new PortableCraftingRecipeTransferHandler(),
                RecipeTypes.CRAFTING);
    }
}
