package com.etbw2.infinitestats.handler;

import com.etbw2.infinitestats.stats.PlayerStats;
import com.etbw2.infinitestats.stats.StatType;
import net.minecraft.server.level.ServerPlayer;

/**
 * 属性效果处理器接口
 * 使用策略模式处理不同类型的属性效果
 */
public interface StatEffectHandler {

    /**
     * 处理器ID，用于标识
     */
    String getId();

    /**
     * 该处理器支持的属性类型
     */
    StatType[] getSupportedStats();

    /**
     * 在玩家tick时应用效果
     * @param player 玩家
     * @param stats 玩家属性数据
     * @param tickCount 当前tick
     */
    void onTick(ServerPlayer player, PlayerStats stats, long tickCount);

    /**
     * 在玩家登录时应用效果
     */
    void onLogin(ServerPlayer player, PlayerStats stats);

    /**
     * 在玩家死亡后重生时应用效果
     */
    void onRespawn(ServerPlayer player, PlayerStats stats);

    /**
     * 在玩家切换维度时应用效果
     */
    void onDimensionChange(ServerPlayer player, PlayerStats stats);

    /**
     * 是否启用
     */
    boolean isEnabled();
}