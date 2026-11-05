package com.infinitestats.network;

import com.infinitestats.stats.OreVisionConfig;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

/**
 * 矿透配置提交（客户端 → 服务器）。
 * <p>
 * 客户端本地先改一份（界面不必等服务端回包），同时把改动提交上来；服务端
 * {@link OreVisionConfig#sanitize()} 夹一遍范围后落库，并回发一份权威配置
 * （{@link OreVisionConfigPacket}），客户端以服务端版本为准。
 * <p>
 * 未解锁 {@code ore_vision} 时直接忽略 —— 壳外的请求不该有副作用。
 */
public final class UpdateOreVisionConfigPacket {

    /** 序列化后的 {@link OreVisionConfig}。 */
    private final CompoundTag data;

    public UpdateOreVisionConfigPacket(OreVisionConfig config) {
        this.data = config.serialize();
    }

    private UpdateOreVisionConfigPacket(CompoundTag data) {
        this.data = data;
    }

    public static void encode(UpdateOreVisionConfigPacket msg, FriendlyByteBuf buf) {
        buf.writeNbt(msg.data);
    }

    public static UpdateOreVisionConfigPacket decode(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        return new UpdateOreVisionConfigPacket(tag == null ? new CompoundTag() : tag);
    }

    public static void handle(UpdateOreVisionConfigPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            PlayerStats stats = PlayerStatsProvider.of(player).orElse(null);
            if (stats == null) return;
            if (!stats.isToggleActive("ore_vision")) return;

            OreVisionConfig incoming = OreVisionConfig.deserialize(msg.data);
            incoming.sanitize();
            stats.getOreVisionConfig().copyFrom(incoming);
            NetworkHandler.syncOreVisionConfig(player);
        });
        ctx.get().setPacketHandled(true);
    }
}
