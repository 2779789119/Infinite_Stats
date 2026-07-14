package com.infinitestats.client;

import com.infinitestats.InfiniteStats;
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

    /**
     * 开关 HUD 显示按键（默认 H）
     */
    public static final KeyMapping HUD_TOGGLE_KEY = new KeyMapping(
            "key.infinitestats.hud_toggle",
            GLFW.GLFW_KEY_H,
            "key.categories.infinitestats"
    );

    /**
     * 打开物品编辑器按键（默认 O）
     */
    public static final KeyMapping OPEN_ITEM_EDITOR_KEY = new KeyMapping(
            "key.infinitestats.item_editor",
            GLFW.GLFW_KEY_O,
            "key.categories.infinitestats"
    );

    /**
     * 打开 EMC 转化桌按键（默认 V）
     */
    public static final KeyMapping OPEN_EMC_KEY = new KeyMapping(
            "key.infinitestats.emc",
            GLFW.GLFW_KEY_V,
            "key.categories.infinitestats"
    );

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_STATS_KEY);
        event.register(QUICK_ADD_KEY);
        event.register(HUD_TOGGLE_KEY);
        event.register(OPEN_ITEM_EDITOR_KEY);
        event.register(OPEN_EMC_KEY);
    }
}