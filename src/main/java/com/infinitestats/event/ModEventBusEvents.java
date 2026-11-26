package com.infinitestats.event;

import com.infinitestats.InfiniteStats;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 模组事件总线事件 - Capability注册
 */
public final class ModEventBusEvents {

    @Mod.EventBusSubscriber(modid = InfiniteStats.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {

        @SubscribeEvent
        public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
            event.register(PlayerStats.class);
            event.register(com.infinitestats.bag.ItemBag.class);
        }
    }

    @Mod.EventBusSubscriber(modid = InfiniteStats.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ForgeBus {

        @SubscribeEvent
        public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
            if (event.getObject() instanceof Player) {
                event.addCapability(
                        new ResourceLocation(InfiniteStats.MODID, "player_stats"),
                        new PlayerStatsProvider()
                );
                // 「无尽背包」独立成一份数据：它是可变长的物品账，不适合塞进属性数据里
                // 跟着每次属性同步一起来回传。
                event.addCapability(
                        new ResourceLocation(InfiniteStats.MODID, "item_bag"),
                        new com.infinitestats.bag.ItemBagProvider()
                );
            }
        }
    }
}