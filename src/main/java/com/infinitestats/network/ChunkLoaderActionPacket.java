package com.infinitestats.network;

import com.infinitestats.Config;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.function.Supplier;

/**
 * 区块强加载动作（客户端 → 服务器）：加载 / 卸载 / 请求列表。
 * <p>
 * 坐标一律以**区块坐标**（chunkX / chunkZ）传递，客户端界面负责把方块坐标换算过来
 * （{@code blockX >> 4}）。每次处理完都会把该维度的最新列表回推给玩家
 * （{@link ChunkLoaderSyncPacket}），因此界面无需自己猜测结果。
 * <p>
 * 每维度的数量上限由配置 {@code ChunkLoader.maxForcedChunks} 决定，超限时拒绝并提示。
 * 强加载区块会随存档保存，与 {@code /forceload} 指令写的是同一份数据。
 */
public final class ChunkLoaderActionPacket {

    public static final int ACTION_ADD = 0;
    public static final int ACTION_REMOVE = 1;
    public static final int ACTION_REQUEST = 2;

    private final int action;
    private final int chunkX;
    private final int chunkZ;

    public ChunkLoaderActionPacket(int action, int chunkX, int chunkZ) {
        this.action = action;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public static void encode(ChunkLoaderActionPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.action);
        buf.writeVarInt(msg.chunkX);
        buf.writeVarInt(msg.chunkZ);
    }

    public static ChunkLoaderActionPacket decode(FriendlyByteBuf buf) {
        return new ChunkLoaderActionPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(ChunkLoaderActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            PlayerStats stats = PlayerStatsProvider.of(player).orElse(null);
            if (stats == null) return;

            if (!stats.isToggleActive("chunk_loader")) {
                player.displayClientMessage(
                        Component.translatable("message.infinitestats.chunk_loader_locked"), true);
                return;
            }

            ServerLevel level = player.serverLevel();
            int cx = msg.chunkX;
            int cz = msg.chunkZ;

            if (msg.action == ACTION_ADD || msg.action == ACTION_REMOVE) {
                ChunkPos pos = new ChunkPos(cx, cz);
                if (!level.getWorldBorder().isWithinBounds(pos)) {
                    player.displayClientMessage(
                            Component.translatable("message.infinitestats.chunk_loader.out_of_bounds"), true);
                } else if (msg.action == ACTION_ADD) {
                    int max = Config.CHUNK_LOADER_MAX_FORCED_CHUNKS.get();
                    var forced = level.getForcedChunks();
                    if (!forced.contains(pos.toLong()) && forced.size() >= max) {
                        player.displayClientMessage(Component.translatable(
                                "message.infinitestats.chunk_loader.limit", max), true);
                    } else {
                        level.setChunkForced(cx, cz, true);
                        player.displayClientMessage(Component.translatable(
                                "message.infinitestats.chunk_loader.loaded", cx, cz), true);
                    }
                } else {
                    level.setChunkForced(cx, cz, false);
                    player.displayClientMessage(Component.translatable(
                            "message.infinitestats.chunk_loader.unloaded", cx, cz), true);
                }
            }

            NetworkHandler.sendForcedChunks(player, level);
        });
        ctx.get().setPacketHandled(true);
    }
}
