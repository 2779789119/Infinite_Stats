package com.infinitestats.compat.jei;

import com.infinitestats.InfiniteStats;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
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
                new PortableCraftingRecipeTransferHandler(
                        registration.getTransferHelper()),
                RecipeTypes.CRAFTING);
    }

    /**
     * 让 JEI 在「无尽背包」界面右侧显示物品列表。
     * <p>
     * JEI 默认只对 {@code AbstractContainerScreen} 这么做；背包是自绘 Screen，
     * 必须通过 {@code addGuiScreenHandler} 告诉它面板的位置与尺寸。
     */
    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiScreenHandler(
                com.infinitestats.client.ItemBagScreen.class, new BagJeiGuiHandler());
    }

    /**
     * JEI 就绪后记住运行期句柄：背包界面里对物品按 R / U 直接用 JEI 查配方。
     * <p>
     * 传 {@code Object} 而不是 {@code IJeiRuntime}：{@link JeiBridge} 的约定是
     * 任何 JEI 类型都不出现在它的方法签名里，没装 JEI 也能安全加载。
     */
    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        JeiBridge.setRuntime(jeiRuntime);
    }

    @Override
    public void onRuntimeUnavailable() {
        JeiBridge.clearRuntime();
    }
}
