package com.infinitestats.network;

import com.infinitestats.stats.PlayerStatsProvider;
import com.infinitestats.stats.VeinMinerConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 连锁挖掘设置提交包（客户端 → 服务端）。
 * <p>
 * 服务端只在 {@code sanitize()} 里夹一遍范围就落库（信任边界只有这一处），
 * 随后回发一份 {@link VeinMinerConfigPacket}，客户端以服务端版本为准 —— 与
 * 「存储优先级」「入库名单」等设置走的是同一套「提交 → 保存 → 回同步」流程。
 */
public final class UpdateVeinMinerConfigPacket {

    private final CompoundTag data;

    public UpdateVeinMinerConfigPacket(VeinMinerConfig config) {
        this.data = config == null ? new CompoundTag() : config.serialize();
    }

    private UpdateVeinMinerConfigPacket(CompoundTag data) {
        this.data = data == null ? new CompoundTag() : data;
    }

    public static void encode(UpdateVeinMinerConfigPacket msg, FriendlyByteBuf buf) {
        buf.writeNbt(msg.data);
    }

    public static UpdateVeinMinerConfigPacket decode(FriendlyByteBuf buf) {
        return new UpdateVeinMinerConfigPacket(buf.readNbt());
    }

    public static void handle(UpdateVeinMinerConfigPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
                VeinMinerConfig submitted = VeinMinerConfig.deserialize(msg.data);
                stats.setVeinMinerConfig(submitted);
                NetworkHandler.syncVeinMinerConfig(player);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
