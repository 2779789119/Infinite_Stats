package com.etbw2.infinitestats.handler;

import java.util.*;

/**
 * 处理器注册中心
 * 管理所有属性效果处理器
 */
public final class HandlerRegistry {

    private static final Map<String, StatEffectHandler> HANDLERS = new LinkedHashMap<>();
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
        register(new MagicHandler());

        initialized = true;
    }

    /**
     * 注册处理器
     */
    public static void register(StatEffectHandler handler) {
        HANDLERS.put(handler.getId(), handler);
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
        return HANDLERS.values().stream()
                .filter(StatEffectHandler::isEnabled)
                .toList();
    }

    /**
     * 执行所有处理器的tick方法
     */
    public static void tickAll(net.minecraft.server.level.ServerPlayer player, 
            com.etbw2.infinitestats.stats.PlayerStats stats, long tickCount) {
        for (StatEffectHandler handler : getEnabledHandlers()) {
            handler.onTick(player, stats, tickCount);
        }
    }

    /**
     * 执行所有处理器的登录方法
     */
    public static void loginAll(net.minecraft.server.level.ServerPlayer player,
            com.etbw2.infinitestats.stats.PlayerStats stats) {
        for (StatEffectHandler handler : getEnabledHandlers()) {
            handler.onLogin(player, stats);
        }
    }

    /**
     * 执行所有处理器的重生方法
     */
    public static void respawnAll(net.minecraft.server.level.ServerPlayer player,
            com.etbw2.infinitestats.stats.PlayerStats stats) {
        for (StatEffectHandler handler : getEnabledHandlers()) {
            handler.onRespawn(player, stats);
        }
    }

    /**
     * 执行所有处理器的维度切换方法
     */
    public static void dimensionChangeAll(net.minecraft.server.level.ServerPlayer player,
            com.etbw2.infinitestats.stats.PlayerStats stats) {
        for (StatEffectHandler handler : getEnabledHandlers()) {
            handler.onDimensionChange(player, stats);
        }
    }
}