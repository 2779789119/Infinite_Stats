package com.infinitestats.emc;

import com.infinitestats.InfiniteStats;
import com.infinitestats.crafting.PortableCraftingMenu;
import com.infinitestats.furnace.FurnaceFuelBufferMenu;
import com.infinitestats.furnace.PortableFurnaceMenu;
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

    public static final RegistryObject<MenuType<PortableCraftingMenu>> PORTABLE_CRAFTING_MENU =
            MENU_TYPES.register("portable_crafting_menu", () -> IForgeMenuType.create(
                    (windowId, inv, data) -> new PortableCraftingMenu(windowId, inv)));
}
