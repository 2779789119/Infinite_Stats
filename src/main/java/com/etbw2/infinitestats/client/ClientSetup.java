package com.etbw2.infinitestats.client;

import com.etbw2.infinitestats.InfiniteStats;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端初始化 - 注册按键映射
 */
@Mod.EventBusSubscriber(modid = InfiniteStats.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientSetup {

    /**
     * 打开属性面板按键（默认P）
     */
    public static final KeyMapping OPEN_STATS_KEY = new KeyMapping(
            "key.infinitestats.open",
            GLFW.GLFW_KEY_P,
            "key.categories.infinitestats"
    );

    /**
     * 快速加点按键（默认+）
     */
    public static final KeyMapping QUICK_ADD_KEY = new KeyMapping(
            "key.infinitestats.quick_add",
            GLFW.GLFW_KEY_EQUAL,
            "key.categories.infinitestats"
    );

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_STATS_KEY);
        event.register(QUICK_ADD_KEY);
    }
}