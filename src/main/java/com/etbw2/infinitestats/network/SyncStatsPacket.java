package com.etbw2.infinitestats.network;

import com.etbw2.infinitestats.stats.PlayerStats;
import com.etbw2.infinitestats.stats.PlayerStatsProvider;
import com.etbw2.infinitestats.stats.StatType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.Arrays;
import java.util.function.Supplier;

/**
 * 同步数据包 - 高效的快照同步
 * 使用紧凑的二进制格式减少网络流量
 */
public final class SyncStatsPacket {

    // 快照数据
    private int level;
    private int experience;
    private int availablePoints;
    private long lastReviveTime;
    private float currentMana;
    private int[] allocatedPoints;

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
        // passiveTickCounter 仅服务端使用，不同步到客户端
    }

    /**
     * 从缓冲区解码
     */
    public SyncStatsPacket(FriendlyByteBuf buf) {
        this.level = buf.readVarInt();
        this.experience = buf.readVarInt();
        this.availablePoints = buf.readVarInt();
        this.lastReviveTime = buf.readVarLong();
        this.currentMana = buf.readFloat();
        
        // 读取属性点数数组
        int count = buf.readVarInt();
        this.allocatedPoints = new int[StatType.ALL_STATS.length];
        Arrays.fill(allocatedPoints, 0);
        
        // 只传输非零值，节省带宽
        for (int i = 0; i < count; i++) {
            int index = buf.readVarInt();
            int points = buf.readVarInt();
            if (index < allocatedPoints.length) {
                allocatedPoints[index] = points;
            }
        }
    }

    /**
     * 编码到缓冲区
     */
    public static void encode(SyncStatsPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.level);
        buf.writeVarInt(msg.experience);
        buf.writeVarInt(msg.availablePoints);
        buf.writeVarLong(msg.lastReviveTime);
        buf.writeFloat(msg.currentMana);

        // 计算非零属性数量
        int nonZeroCount = 0;
        for (int points : msg.allocatedPoints) {
            if (points > 0) nonZeroCount++;
        }

        buf.writeVarInt(nonZeroCount);

        // 只写入非零值
        for (int i = 0; i < msg.allocatedPoints.length; i++) {
            if (msg.allocatedPoints[i] > 0) {
                buf.writeVarInt(i);
                buf.writeVarInt(msg.allocatedPoints[i]);
            }
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
            // 仅在客户端处理
            var player = Minecraft.getInstance().player;
            if (player == null) return;

            player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
                // 创建快照并恢复（passiveTickCounter 客户端不同步，使用-1占位）
                PlayerStats.StatsSnapshot snapshot = new PlayerStats.StatsSnapshot(
                        msg.level,
                        msg.experience,
                        msg.availablePoints,
                        msg.lastReviveTime,
                        msg.currentMana,
                        -1,
                        msg.allocatedPoints
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
                allocatedPoints
        );
    }
}