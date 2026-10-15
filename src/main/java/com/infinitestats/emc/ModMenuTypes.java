package com.infinitestats.emc;

import com.infinitestats.InfiniteStats;
import com.infinitestats.crafting.PortableCraftingMenu;
import com.infinitestats.crafting.PortableInfuserMenu;
import com.infinitestats.furnace.FurnaceFuelBufferMenu;
import com.infinitestats.furnace.FurnaceOrePriorityMenu;
import com.infinitestats.furnace.FurnaceProductBufferMenu;
import com.infinitestats.furnace.PortableFurnaceMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ModMenuTypes {

    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(BuiltInRegistries.MENU, InfiniteStats.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<EmcMenu>> EMC_MENU =
            MENU_TYPES.register("emc_menu", () -> IMenuTypeExtension.create(
                    (windowId, inv, data) -> new EmcMenu(windowId, inv)));

    public static final DeferredHolder<MenuType<?>, MenuType<PortableFurnaceMenu>> PORTABLE_FURNACE_MENU =
            MENU_TYPES.register("portable_furnace_menu", () -> IMenuTypeExtension.create(
                    (windowId, inv, data) -> new PortableFurnaceMenu(windowId, inv)));

    public static final DeferredHolder<MenuType<?>, MenuType<FurnaceFuelBufferMenu>> FURNACE_FUEL_BUFFER_MENU =
            MENU_TYPES.register("furnace_fuel_buffer_menu", () -> IMenuTypeExtension.create(
                    (windowId, inv, data) -> new FurnaceFuelBufferMenu(windowId, inv)));

    public static final DeferredHolder<MenuType<?>, MenuType<FurnaceProductBufferMenu>> FURNACE_PRODUCT_BUFFER_MENU =
            MENU_TYPES.register("furnace_product_buffer_menu", () -> IMenuTypeExtension.create(
                    (windowId, inv, data) -> new FurnaceProductBufferMenu(windowId, inv)));

    public static final DeferredHolder<MenuType<?>, MenuType<FurnaceOrePriorityMenu>> FURNACE_ORE_PRIORITY_MENU =
            MENU_TYPES.register("furnace_ore_priority_menu", () -> IMenuTypeExtension.create(
                    (windowId, inv, data) -> new FurnaceOrePriorityMenu(windowId, inv)));

    public static final DeferredHolder<MenuType<?>, MenuType<PortableCraftingMenu>> PORTABLE_CRAFTING_MENU =
            MENU_TYPES.register("portable_crafting_menu", () -> IMenuTypeExtension.create(
                    (windowId, inv, data) -> new PortableCraftingMenu(windowId, inv)));

    /** 随身「进阶高级附魔台」：自建菜单（自选附魔，不复用原版附魔台模型）。 */
    public static final DeferredHolder<MenuType<?>, MenuType<PortableInfuserMenu>> PORTABLE_ENCHANTING_MENU =
            MENU_TYPES.register("portable_enchanting_menu", () -> IMenuTypeExtension.create(
                    (windowId, inv, data) -> new PortableInfuserMenu(windowId, inv)));
}
