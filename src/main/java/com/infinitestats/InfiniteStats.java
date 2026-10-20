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

    /**
     * 运行期版本号：直接取模组元数据（jar 里 mods.toml 的 version），
     * 不再手写常量 —— 之前那个常量常年停在 1.64.3，日志里的版本号完全不可信。
     */
    public static String version() {
        return net.minecraftforge.fml.ModList.get()
                .getModContainerById(MODID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("dev");
    }

    public InfiniteStats() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 注册配置
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // 仅在客户端注册「Config」按钮的配置界面（专用服务器不加载客户端类）
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) {
            com.infinitestats.client.ClientSetup.registerConfigScreen();
        }

        // 注册事件总线
        modEventBus.addListener(this::setup);
        // 配置文件加载 / 重载后重新把属性调参刷进 StatType
        modEventBus.addListener(this::onConfigChanged);

        // 注册服务端命令（跨维度/定点传送），兼容单人（内置服务器）与专用服务器
        MinecraftForge.EVENT_BUS.addListener(ModServerCommands::registerCommands);

        // 初始化处理器注册中心
        HandlerRegistry.initialize();

        // 注册网络通道
        NetworkHandler.register();

        // 注册 EMC 菜单类型
        ModMenuTypes.MENU_TYPES.register(modEventBus);

        // 模组信息日志
        System.out.println("[InfiniteStats] Version " + version() + " loaded successfully");
    }

    private void setup(final net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // 先把配置里的属性调参刷进 StatType，再做动态发现
            int tuned = StatType.applyTuningFromConfig();
            // 动态发现其他模组注册的属性
            if (Config.ENABLE_ATTRIBUTE_DISCOVERY.get()) {
                StatType.discoverModdedAttributes();
            }
            System.out.println("[InfiniteStats] Common setup complete — "
                    + StatType.getBuiltinCount() + " built-in + "
                    + (StatType.ALL_STATS.length - StatType.getBuiltinCount()) + " external stats"
                    + (tuned > 0 ? " (" + tuned + " stats tuned by config)" : ""));
        });
    }

    /**
     * 配置文件加载 / 重载：把「属性逐项调参」重新灌进 {@link StatType}。
     * <p>
     * 只关心本模组自己的 common 配置；其它配置（客户端、其它模组）直接忽略。
     * 重载失败不能让服务端崩，异常只记日志。
     */
    private void onConfigChanged(final net.minecraftforge.fml.event.config.ModConfigEvent event) {
        if (event.getConfig().getSpec() != Config.SPEC) return;
        try {
            int changed = StatType.applyTuningFromConfig();
            // 玩家侧的属性值缓存是按玩家算好的，调参变了必须让它失效一次，
            // 否则在线玩家会一直用旧数值直到下一次分配点数。
            // 属性修饰符由 AttributeHandler 在下一个刷新周期自然重写（默认 ≤ 2 秒）。
            net.minecraft.server.MinecraftServer server =
                    net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
                    player.getCapability(com.infinitestats.stats.PlayerStatsProvider.PLAYER_STATS)
                            .ifPresent(com.infinitestats.stats.PlayerStats::refreshFeatureMode);
                }
            }
            System.out.println("[InfiniteStats] Config reloaded — " + changed
                    + " stat tuning value(s) updated");
        } catch (Throwable t) {
            System.err.println("[InfiniteStats] Failed to apply stat tuning from config: " + t);
        }
    }
}