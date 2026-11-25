package com.infinitestats.handler;

import java.util.*;

/**
 * 处理器注册中心
 * 管理所有属性效果处理器
 */
public final class HandlerRegistry {

    private static final Map<String, StatEffectHandler> HANDLERS = new LinkedHashMap<>();
    /**
     * 启用列表的缓存。
     * <p>
     * 原来每次 {@link #getEnabledHandlers()} 都是 {@code stream().filter().toList()}，
     * 而它被每玩家每 tick 调用一次 —— 等于每秒给每个玩家新建若干个 List 与 Stream 对象。
     * 处理器只在 {@link #initialize()} 时注册一次、{@link StatEffectHandler#isEnabled()} 全部返回常量 true，
     * 所以在注册时重建一次就够了。
     */
    private static volatile List<StatEffectHandler> enabledCache = List.of();
    private static boolean initialized = false;

    /**
     * 初始化并注册所有处理器
     */
    public static void initialize() {
        if (initialized) return;

        register(new AttributeHandler());
        register(new AttackHandler());
        register(new DefenseHandler());
        register(new MobilityHandler());
        register(new UtilityHandler());
        register(new TimeAccelHandler());
        register(new CooldownHandler());

        initialized = true;
    }

    /**
     * 注册处理器
     */
    public static void register(StatEffectHandler handler) {
        HANDLERS.put(handler.getId(), handler);
        // 注册即重建（注册只发生在启动阶段，成本可以忽略）
        enabledCache = HANDLERS.values().stream()
                .filter(StatEffectHandler::isEnabled)
                .toList();
    }

    /**
     * 获取处理器
     */
    public static StatEffectHandler getHandler(String id) {
        return HANDLERS.get(id);
    }

    /**
     * 获取所有处理器
     */
    public static Collection<StatEffectHandler> getAllHandlers() {
        return HANDLERS.values();
    }

    /**
     * 获取所有启用的处理器
     */
    public static List<StatEffectHandler> getEnabledHandlers() {
        return enabledCache;
    }

    /**
     * 执行所有处理器的tick方法
     */
    public static void tickAll(net.minecraft.server.level.ServerPlayer player,
            com.infinitestats.stats.PlayerStats stats, long tickCount) {
        for (StatEffectHandler handler : getEnabledHandlers()) {
            try {
                handler.onTick(player, stats, tickCount);
            } catch (Throwable t) {
                // 单个 handler 异常不应中断其它 handler，也不应影响后续熔炉冶炼
                t.printStackTrace();
            }
        }
    }

    /**
     * 执行所有处理器的登录方法
     */
    public static void loginAll(net.minecraft.server.level.ServerPlayer player,
            com.infinitestats.stats.PlayerStats stats) {
        for (StatEffectHandler handler : getEnabledHandlers()) {
            handler.onLogin(player, stats);
        }
    }

    /**
     * 执行所有处理器的重生方法
     */
    public static void respawnAll(net.minecraft.server.level.ServerPlayer player,
            com.infinitestats.stats.PlayerStats stats) {
        for (StatEffectHandler handler : getEnabledHandlers()) {
            handler.onRespawn(player, stats);
        }
    }

    /**
     * 执行所有处理器的维度切换方法
     */
    public static void dimensionChangeAll(net.minecraft.server.level.ServerPlayer player,
            com.infinitestats.stats.PlayerStats stats) {
        for (StatEffectHandler handler : getEnabledHandlers()) {
            handler.onDimensionChange(player, stats);
        }
    }
}