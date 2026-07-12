package com.infinitestats.network;

import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.HashSet;
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
    private float currentMana;
    private Map<String, Long> allocatedPoints;
    private boolean debuffUseBlacklist;
    private Set<String> debuffFilterList;

    /**
     * 从快照创建
     */
    public SyncStatsPacket(PlayerStats.StatsSnapshot snapshot) {
        this.level = snapshot.level;
        this.experience = snapshot.experience;
        this.availablePoints = snapshot.availablePoints;
        this.lastReviveTime = snapshot.lastReviveTime;
        this.currentMana = snapshot.currentMana;
        this.allocatedPoints = snapshot.allocatedPoints;
        this.debuffUseBlacklist = snapshot.debuffUseBlacklist;
        this.debuffFilterList = snapshot.debuffFilterList;
    }

    /**
     * 从缓冲区解码
     */
    public SyncStatsPacket(FriendlyByteBuf buf) {
        this.level = buf.readVarLong();
        this.experience = buf.readVarLong();
        this.availablePoints = buf.readVarLong();
        this.lastReviveTime = buf.readVarLong();
        this.currentMana = buf.readFloat();
        
        // 读取属性点数Map
        int count = buf.readVarInt();
        this.allocatedPoints = new HashMap<>();
        
        for (int i = 0; i < count; i++) {
            String statId = buf.readUtf();
            long points = buf.readVarLong();
            allocatedPoints.put(statId, points);
        }

        // 读取 debuff 过滤列表
        this.debuffUseBlacklist = buf.readBoolean();
        int filterCount = buf.readVarInt();
        this.debuffFilterList = new HashSet<>();
        for (int i = 0; i < filterCount; i++) {
            debuffFilterList.add(buf.readUtf());
        }
    }

    /**
     * 编码到缓冲区
     */
    public static void encode(SyncStatsPacket msg, FriendlyByteBuf buf) {
        buf.writeVarLong(msg.level);
        buf.writeVarLong(msg.experience);
        buf.writeVarLong(msg.availablePoints);
        buf.writeVarLong(msg.lastReviveTime);
        buf.writeFloat(msg.currentMana);

        // 写入非零属性数量
        buf.writeVarInt(msg.allocatedPoints.size());

        // 写入每个属性ID和点数
        for (Map.Entry<String, Long> entry : msg.allocatedPoints.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeVarLong(entry.getValue());
        }

        // 写入 debuff 过滤列表
        buf.writeBoolean(msg.debuffUseBlacklist);
        buf.writeVarInt(msg.debuffFilterList.size());
        for (String effectId : msg.debuffFilterList) {
            buf.writeUtf(effectId);
        }
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
                        msg.currentMana,
                        -1,
                        msg.allocatedPoints,
                        msg.debuffUseBlacklist,
                        msg.debuffFilterList
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
                lastReviveTime, currentMana,
                -1,
                allocatedPoints,
                debuffUseBlacklist,
                debuffFilterList
        );
    }
}