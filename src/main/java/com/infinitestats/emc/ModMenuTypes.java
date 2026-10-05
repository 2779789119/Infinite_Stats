package com.infinitestats.emc;

import com.infinitestats.InfiniteStats;
import com.infinitestats.crafting.PortableAnvilMenu;
import com.infinitestats.crafting.PortableCraftingMenu;
import com.infinitestats.crafting.PortableEnchantingMenu;
import com.infinitestats.furnace.FurnaceFuelBufferMenu;
import com.infinitestats.furnace.FurnaceOrePriorityMenu;
import com.infinitestats.furnace.FurnaceProductBufferMenu;
import com.infinitestats.furnace.PortableFurnaceMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenuTypes {

    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, InfiniteStats.MODID);

    public static final RegistryObject<MenuType<EmcMenu>> EMC_MENU =
            MENU_TYPES.register("emc_menu", () -> IForgeMenuType.create(
                    (windowId, inv, data) -> new EmcMenu(windowId, inv)));

    public static final RegistryObject<MenuType<PortableFurnaceMenu>> PORTABLE_FURNACE_MENU =
            MENU_TYPES.register("portable_furnace_menu", () -> IForgeMenuType.create(
                    (windowId, inv, data) -> new PortableFurnaceMenu(windowId, inv)));

    public static final RegistryObject<MenuType<FurnaceFuelBufferMenu>> FURNACE_FUEL_BUFFER_MENU =
            MENU_TYPES.register("furnace_fuel_buffer_menu", () -> IForgeMenuType.create(
                    (windowId, inv, data) -> new FurnaceFuelBufferMenu(windowId, inv)));

    public static final RegistryObject<MenuType<FurnaceProductBufferMenu>> FURNACE_PRODUCT_BUFFER_MENU =
            MENU_TYPES.register("furnace_product_buffer_menu", () -> IForgeMenuType.create(
                    (windowId, inv, data) -> new FurnaceProductBufferMenu(windowId, inv)));

    public static final RegistryObject<MenuType<FurnaceOrePriorityMenu>> FURNACE_ORE_PRIORITY_MENU =
            MENU_TYPES.register("furnace_ore_priority_menu", () -> IForgeMenuType.create(
                    (windowId, inv, data) -> new FurnaceOrePriorityMenu(windowId, inv)));

    public static final RegistryObject<MenuType<PortableCraftingMenu>> PORTABLE_CRAFTING_MENU =
            MENU_TYPES.register("portable_crafting_menu", () -> IForgeMenuType.create(
                    (windowId, inv, data) -> new PortableCraftingMenu(windowId, inv)));

    public static final RegistryObject<MenuType<PortableAnvilMenu>> PORTABLE_ANVIL_MENU =
            MENU_TYPES.register("portable_anvil_menu", () -> IForgeMenuType.create(
                    (windowId, inv, data) -> new PortableAnvilMenu(windowId, inv)));

    /**
     * 随身附魔台菜单（客户端用 {@link PortableEnchantingMenu}，服务端用
     * {@link com.infinitestats.crafting.PortableStationMenus.Enchanting}）。
     * <p>
     * 泛型参数刻意写成 {@link EnchantmentMenu}（而非子类）：客户端界面
     * {@code PortableEnchantingScreen} 继承自原版 {@code EnchantmentScreen}，
     * 而 {@code MenuScreens.register} 要求「界面类型 == 菜单类型的泛型」，
     * 因此菜单类型必须声明为父类 {@code EnchantmentMenu}。
     */
    public static final RegistryObject<MenuType<EnchantmentMenu>> PORTABLE_ENCHANTING_MENU =
            MENU_TYPES.register("portable_enchanting_menu", () -> IForgeMenuType.create(
                    (windowId, inv, data) -> (EnchantmentMenu) new PortableEnchantingMenu(windowId, inv)));
}
