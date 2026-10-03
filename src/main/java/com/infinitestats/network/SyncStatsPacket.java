package com.infinitestats.network;

import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import com.infinitestats.stats.Waypoint;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 同步数据包 - 使用基于ID的编码以支持动态属性
 * 使用紧凑的二进制格式减少网络流量
 */
public final class SyncStatsPacket {

    // 快照数据
    private long level;
    private long experience;
    private long availablePoints;
    private long lastReviveTime;
    private long reviveInvulnUntilTick;
    private Map<String, Long> allocatedPoints;
    private boolean buffUseBlacklist;
    private Set<String> buffFilterList;
    private Map<String, Waypoint> waypoints;
    private Set<String> favorites;
    private Map<String, List<String>> networkPriorities;
    private boolean autoDepositUseWhitelist;
    private Set<String> autoDepositFilterList;
    private Set<String> disabledStats;

    /**
     * 从快照创建
     */
    public SyncStatsPacket(PlayerStats.StatsSnapshot snapshot) {
        this.level = snapshot.level;
        this.experience = snapshot.experience;
        this.availablePoints = snapshot.availablePoints;
        this.lastReviveTime = snapshot.lastReviveTime;
        this.reviveInvulnUntilTick = snapshot.reviveInvulnUntilTick;
        this.allocatedPoints = snapshot.allocatedPoints;
        this.buffUseBlacklist = snapshot.buffUseBlacklist;
        this.buffFilterList = snapshot.buffFilterList;
        this.waypoints = snapshot.waypoints;
        this.favorites = snapshot.favorites;
        this.networkPriorities = snapshot.networkPriorities;
        this.autoDepositUseWhitelist = snapshot.autoDepositUseWhitelist;
        this.autoDepositFilterList = snapshot.autoDepositFilterList;
        this.disabledStats = snapshot.disabledStats;
    }

    /**
     * 从缓冲区解码
     */
    public SyncStatsPacket(FriendlyByteBuf buf) {
        this.level = buf.readVarLong();
        this.experience = buf.readVarLong();
        this.availablePoints = buf.readVarLong();
        this.lastReviveTime = buf.readVarLong();
        this.reviveInvulnUntilTick = buf.readVarLong();
        
        // 读取属性点数Map
        int count = buf.readVarInt();
        this.allocatedPoints = new HashMap<>();
        
        for (int i = 0; i < count; i++) {
            String statId = buf.readUtf();
            long points = buf.readVarLong();
            allocatedPoints.put(statId, points);
        }

        // 读取 buff 过滤列表
        this.buffUseBlacklist = buf.readBoolean();
        int filterCount = buf.readVarInt();
        this.buffFilterList = new HashSet<>();
        for (int i = 0; i < filterCount; i++) {
            buffFilterList.add(buf.readUtf());
        }

        // 读取传送点
        int wpCount = buf.readVarInt();
        this.waypoints = new HashMap<>();
        for (int i = 0; i < wpCount; i++) {
            String name = buf.readUtf();
            String dimension = buf.readUtf();
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            float yaw = buf.readFloat();
            float pitch = buf.readFloat();
            waypoints.put(name, new Waypoint(dimension, x, y, z, yaw, pitch));
        }
        favorites = new HashSet<>();
        int favoriteCount = buf.readVarInt();
        for (int i = 0; i < favoriteCount; i++) favorites.add(buf.readUtf());

        // 读取各功能的存储优先级（作用域 → 存储键列表）
        int scopeCount = buf.readVarInt();
        this.networkPriorities = new HashMap<>();
        for (int i = 0; i < scopeCount; i++) {
            String scope = buf.readUtf();
            int keyCount = buf.readVarInt();
            List<String> keys = new ArrayList<>();
            for (int k = 0; k < keyCount; k++) keys.add(buf.readUtf());
            networkPriorities.put(scope, keys);
        }

        // 读取自动入库过滤（白 / 黑名单）
        this.autoDepositUseWhitelist = buf.readBoolean();
        int depositFilterCount = buf.readVarInt();
        this.autoDepositFilterList = new HashSet<>();
        for (int i = 0; i < depositFilterCount; i++) autoDepositFilterList.add(buf.readUtf());

        // 读取「功能开关」中已关闭的属性
        int disabledCount = buf.readVarInt();
        this.disabledStats = new HashSet<>();
        for (int i = 0; i < disabledCount; i++) disabledStats.add(buf.readUtf());
    }

    /**
     * 编码到缓冲区
     */
    public static void encode(SyncStatsPacket msg, FriendlyByteBuf buf) {
        buf.writeVarLong(msg.level);
        buf.writeVarLong(msg.experience);
        buf.writeVarLong(msg.availablePoints);
        buf.writeVarLong(msg.lastReviveTime);
        buf.writeVarLong(msg.reviveInvulnUntilTick);

        // 写入非零属性数量
        buf.writeVarInt(msg.allocatedPoints.size());

        // 写入每个属性ID和点数
        for (Map.Entry<String, Long> entry : msg.allocatedPoints.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeVarLong(entry.getValue());
        }

        // 写入 buff 过滤列表
        buf.writeBoolean(msg.buffUseBlacklist);
        buf.writeVarInt(msg.buffFilterList.size());
        for (String effectId : msg.buffFilterList) {
            buf.writeUtf(effectId);
        }

        // 写入传送点
        buf.writeVarInt(msg.waypoints.size());
        for (Map.Entry<String, Waypoint> entry : msg.waypoints.entrySet()) {
            buf.writeUtf(entry.getKey());
            Waypoint wp = entry.getValue();
            buf.writeUtf(wp.dimension);
            buf.writeDouble(wp.x);
            buf.writeDouble(wp.y);
            buf.writeDouble(wp.z);
            buf.writeFloat(wp.yaw);
            buf.writeFloat(wp.pitch);
        }
        buf.writeVarInt(msg.favorites.size());
        for (String id : msg.favorites) buf.writeUtf(id);

        // 写入各功能的存储优先级（作用域 → 存储键列表）
        Map<String, List<String>> priorities =
                msg.networkPriorities != null ? msg.networkPriorities : Map.of();
        buf.writeVarInt(priorities.size());
        for (Map.Entry<String, List<String>> entry : priorities.entrySet()) {
            buf.writeUtf(entry.getKey());
            List<String> keys = entry.getValue() != null ? entry.getValue() : List.of();
            buf.writeVarInt(keys.size());
            for (String key : keys) buf.writeUtf(key);
        }

        // 写入自动入库过滤（白 / 黑名单）
        buf.writeBoolean(msg.autoDepositUseWhitelist);
        Set<String> depositFilter = msg.autoDepositFilterList != null ? msg.autoDepositFilterList : Set.of();
        buf.writeVarInt(depositFilter.size());
        for (String id : depositFilter) buf.writeUtf(id);

        // 写入「功能开关」中已关闭的属性
        Set<String> disabled = msg.disabledStats != null ? msg.disabledStats : Set.of();
        buf.writeVarInt(disabled.size());
        for (String id : disabled) buf.writeUtf(id);
    }

    /**
     * 解码
     */
    public static SyncStatsPacket decode(FriendlyByteBuf buf) {
        return new SyncStatsPacket(buf);
    }

    /**
     * 处理（客户端）
     */
    public static void handle(SyncStatsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var player = Minecraft.getInstance().player;
            if (player == null) return;

            player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
                PlayerStats.StatsSnapshot snapshot = new PlayerStats.StatsSnapshot(
                        msg.level,
                        msg.experience,
                        msg.availablePoints,
                        msg.lastReviveTime,
                        -1,
                        msg.allocatedPoints,
                        msg.buffUseBlacklist,
                        msg.buffFilterList,
                        msg.waypoints,
                        msg.reviveInvulnUntilTick,
                        msg.favorites,
                        msg.networkPriorities,
                        msg.autoDepositUseWhitelist,
                        msg.autoDepositFilterList,
                        msg.disabledStats
                );
                stats.restoreFromSnapshot(snapshot);
            });
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * 获取快照数据
     */
    public PlayerStats.StatsSnapshot getSnapshot() {
        return new PlayerStats.StatsSnapshot(
                level, experience, availablePoints,
                lastReviveTime,
                -1,
                allocatedPoints,
                buffUseBlacklist,
                buffFilterList,
                waypoints,
                reviveInvulnUntilTick,
                favorites,
                networkPriorities,
                autoDepositUseWhitelist,
                autoDepositFilterList,
                disabledStats
        );
    }
}