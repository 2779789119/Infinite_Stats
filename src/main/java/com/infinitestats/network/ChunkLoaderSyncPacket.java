package com.infinitestats.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 区块强加载列表同步（服务器 → 客户端）。
 * <p>
 * 列表元素是 {@code ChunkPos#toLong()} 打包后的区块坐标；客户端把它缓存进本类的静态字段，
 * 由 {@code ChunkLoaderScreen} 读取。界面不改动自身数据，只按 {@link #version} 判断是否需要重建列表行，
 * 因此永远不会和真实状态脱节。
 */
public final class ChunkLoaderSyncPacket {

    /** 客户端缓存：已强加载区块列表（元素为 ChunkPos 打包值）。 */
    public static volatile List<Long> cachedChunks = List.of();

    /** 上面那份列表所属的维度 id（仅用于界面显示）。 */
    public static volatile String cachedDimension = "";

    /** 列表版本号：每次同步自增，界面据此判断是否需要重建行控件。 */
    public static volatile long version = 0;

    private final List<Long> chunks;
    private final String dimension;

    public ChunkLoaderSyncPacket(List<Long> chunks, String dimension) {
        this.chunks = chunks == null ? List.of() : List.copyOf(chunks);
        this.dimension = dimension == null ? "" : dimension;
    }

    public static void encode(ChunkLoaderSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.chunks.size());
        for (long packed : msg.chunks) {
            buf.writeLong(packed);
        }
        buf.writeUtf(msg.dimension);
    }

    public static ChunkLoaderSyncPacket decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<Long> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(buf.readLong());
        }
        return new ChunkLoaderSyncPacket(list, buf.readUtf());
    }

    public static void handle(ChunkLoaderSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            cachedChunks = msg.chunks;
            cachedDimension = msg.dimension;
            version++;
        });
        ctx.get().setPacketHandled(true);
    }
}
