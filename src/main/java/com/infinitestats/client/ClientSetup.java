package com.infinitestats.client;

import com.infinitestats.InfiniteStats;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * 客户端初始化 - 注册按键映射
 */
@EventBusSubscriber(modid = InfiniteStats.MODID, value = Dist.CLIENT)
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
     * 打开成就管理器按键（默认 U）
     */
    public static final KeyMapping OPEN_ACHIEVEMENTS_KEY = new KeyMapping(
            "key.infinitestats.achievements",
            GLFW.GLFW_KEY_U,
            "key.categories.infinitestats"
    );

    /**
     * 打开传送点面板按键（默认 Y）
     */
    public static final KeyMapping OPEN_WAYPOINT_KEY = new KeyMapping(
            "key.infinitestats.waypoint",
            GLFW.GLFW_KEY_Y,
            "key.categories.infinitestats"
    );

    /**
     * 打开随身工作台按键（默认 G）。
     * <p>
     * 打开前不弹面板：已解锁就直接发 {@code CraftingOpenPacket}（服务端还会再校验一次），
     * 未解锁时由按键处理打开「随身工具」页，让玩家在那里花点数解锁 ——
     * 这样按一下键永远有反馈，不会出现「按了没反应」。
     */
    public static final KeyMapping OPEN_PORTABLE_CRAFTING_KEY = new KeyMapping(
            "key.infinitestats.portable_crafting",
            GLFW.GLFW_KEY_G,
            "key.categories.infinitestats"
    );

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_STATS_KEY);
        event.register(QUICK_ADD_KEY);
        event.register(HUD_TOGGLE_KEY);
        event.register(OPEN_ITEM_EDITOR_KEY);
        event.register(OPEN_ACHIEVEMENTS_KEY);
        event.register(OPEN_WAYPOINT_KEY);
        event.register(OPEN_PORTABLE_CRAFTING_KEY);
    }
}