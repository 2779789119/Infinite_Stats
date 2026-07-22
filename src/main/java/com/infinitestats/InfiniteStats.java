package com.infinitestats;

import com.infinitestats.command.ModServerCommands;
import com.infinitestats.emc.ModMenuTypes;
import com.infinitestats.handler.HandlerRegistry;
import com.infinitestats.network.NetworkHandler;
import com.infinitestats.stats.StatType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * 模组入口 - Infinite Stats System
 * 实现类似泰拉瑞亚的无限加点系统
 */
@Mod(InfiniteStats.MODID)
public final class InfiniteStats {

    public static final String MODID = "infinitestats";
    public static final String VERSION = "1.6.0";

    public InfiniteStats() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 注册配置
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // 注册事件总线
        modEventBus.addListener(this::setup);

        // 注册服务端命令（跨维度/定点传送），兼容单人（内置服务器）与专用服务器
        MinecraftForge.EVENT_BUS.addListener(ModServerCommands::registerCommands);

        // 初始化处理器注册中心
        HandlerRegistry.initialize();

        // 注册网络通道
        NetworkHandler.register();

        // 注册 EMC 菜单类型
        ModMenuTypes.MENU_TYPES.register(modEventBus);

        // 模组信息日志
        System.out.println("[InfiniteStats] Version " + VERSION + " loaded successfully");
    }

    private void setup(final net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) {
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