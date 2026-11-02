package com.infinitestats.network;

import com.infinitestats.stats.OreVisionConfig;
import com.infinitestats.stats.PlayerStatsProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 矿透设置提交包（客户端 → 服务端）。
 * <p>
 * 服务端只在 {@code sanitize()} 里夹一遍范围就落库（信任边界只有这一处），
 * 随后回发一份 {@link OreVisionConfigPacket}，客户端以服务端版本为准。
 */
public final class UpdateOreVisionConfigPacket {

    private final CompoundTag data;

    public UpdateOreVisionConfigPacket(OreVisionConfig config) {
        this.data = config == null ? new CompoundTag() : config.serialize();
    }

    private UpdateOreVisionConfigPacket(CompoundTag data) {
        this.data = data == null ? new CompoundTag() : data;
    }

    public static void encode(UpdateOreVisionConfigPacket msg, FriendlyByteBuf buf) {
        buf.writeNbt(msg.data);
    }

    public static UpdateOreVisionConfigPacket decode(FriendlyByteBuf buf) {
        return new UpdateOreVisionConfigPacket(buf.readNbt());
    }

    public static void handle(UpdateOreVisionConfigPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
                stats.setOreVisionConfig(OreVisionConfig.deserialize(msg.data));
                NetworkHandler.syncOreVisionConfig(player);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
