package com.etbw2.infinitestats.client;

import com.etbw2.infinitestats.InfiniteStats;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 客户端事件处理器 — 处理按键输入和 GUI 覆盖层注册
 */
@Mod.EventBusSubscriber(modid = InfiniteStats.MODID, value = Dist.CLIENT)
public final class ClientEventHandler {

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        // 打开属性面板
        while (ClientSetup.OPEN_STATS_KEY.consumeClick()) {
            mc.setScreen(new StatsScreen());
        }

        // 快速加点（预留功能）
        while (ClientSetup.QUICK_ADD_KEY.consumeClick()) {
            // TODO: 实现快速加点功能
        }
    }

    /**
     * 注册 HUD 覆盖层（在 MOD 事件总线上）
     */
    @Mod.EventBusSubscriber(modid = InfiniteStats.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            // 游戏启动时加载保存的 HUD 位置
            ClientSettings.load();
        }

        @SubscribeEvent
        public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("infinitestats_hud",
                    (gui, graphics, partialTick, screenWidth, screenHeight) ->
                            StatsHudOverlay.render(graphics, partialTick));
        }
    }
}
