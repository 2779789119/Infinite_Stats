package com.infinitestats.network;

import com.infinitestats.stats.PlayerStatsProvider;
import com.infinitestats.stats.VeinMinerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 连锁挖掘设置同步包（服务端 → 客户端）。
 * <p>
 * 单独走一个包而不是塞进 {@link SyncStatsPacket}：这份设置只在玩家改动时变化，
 * 没必要跟着每秒都在跑的属性同步一起传（也免得 {@code StatsSnapshot} 的构造参数继续膨胀）。
 * 发送时机与属性同步绑定：{@link NetworkHandler#syncToClient} 时顺带发一份，
 * 所以登录 / 重生 / 改设置后客户端手上一定是新的。
 */
public final class VeinMinerConfigPacket {

    private final CompoundTag data;

    public VeinMinerConfigPacket(VeinMinerConfig config) {
        this.data = config == null ? new CompoundTag() : config.serialize();
    }

    private VeinMinerConfigPacket(CompoundTag data) {
        this.data = data == null ? new CompoundTag() : data;
    }

    public static void encode(VeinMinerConfigPacket msg, FriendlyByteBuf buf) {
        buf.writeNbt(msg.data);
    }

    public static VeinMinerConfigPacket decode(FriendlyByteBuf buf) {
        return new VeinMinerConfigPacket(buf.readNbt());
    }

    public static void handle(VeinMinerConfigPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            player.getCapability(PlayerStatsProvider.PLAYER_STATS)
                    .ifPresent(stats -> stats.setVeinMinerConfig(VeinMinerConfig.deserialize(msg.data)));
        });
        ctx.get().setPacketHandled(true);
    }
}
