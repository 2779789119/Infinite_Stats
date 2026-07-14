package com.infinitestats.emc;

import com.infinitestats.InfiniteStats;
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
}
