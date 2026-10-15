package com.infinitestats;

import com.infinitestats.command.ModServerCommands;
import com.infinitestats.emc.EmcPlayerDataProvider;
import com.infinitestats.emc.ModMenuTypes;
import com.infinitestats.handler.HandlerRegistry;
import com.infinitestats.network.NetworkHandler;
import com.infinitestats.stats.PlayerStatsProvider;
import com.infinitestats.stats.StatType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * 模组入口 - Infinite Stats System
 * 实现类似泰拉瑞亚的无限加点系统
 */
@Mod(InfiniteStats.MODID)
public final class InfiniteStats {

    public static final String MODID = "infinitestats";
    public static final String VERSION = "1.9.16";

    public InfiniteStats(IEventBus modEventBus, ModContainer modContainer) {
        // 注册配置
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // 把「模组列表 → 本模组 → Config」按钮指向 Cloth Config 画出来的配置界面
        // （扩展点只在客户端被解析，专用服务器上不会用到这部分）
        modContainer.registerExtensionPoint(
                net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                (net.neoforged.fml.ModContainer container,
                 net.minecraft.client.gui.screens.Screen parent) ->
                        com.infinitestats.client.ClothConfigScreen.create(parent));

        // 注册事件总线
        modEventBus.addListener(this::setup);

        // 注册网络载荷（RegisterPayloadHandlersEvent 属于 MOD 总线）
        modEventBus.addListener(NetworkHandler::register);

        // 注册服务端命令（跨维度/定点传送），兼容单人（内置服务器）与专用服务器
        NeoForge.EVENT_BUS.addListener(ModServerCommands::registerCommands);

        // 初始化处理器注册中心
        HandlerRegistry.initialize();

        // 注册附加数据（Data Attachment）
        PlayerStatsProvider.ATTACHMENT_TYPES.register(modEventBus);
        EmcPlayerDataProvider.ATTACHMENT_TYPES.register(modEventBus);

        // 注册 EMC 菜单类型
        ModMenuTypes.MENU_TYPES.register(modEventBus);

        // 模组信息日志
        System.out.println("[InfiniteStats] Version " + VERSION + " loaded successfully");
    }

    private void setup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // 动态发现其他模组注册的属性
            if (Config.ENABLE_ATTRIBUTE_DISCOVERY.get()) {
                StatType.discoverModdedAttributes();
            }
            System.out.println("[InfiniteStats] Common setup complete — "
                    + StatType.getBuiltinCount() + " built-in + "
                    + (StatType.ALL_STATS.length - StatType.getBuiltinCount()) + " external stats");
        });
    }
}