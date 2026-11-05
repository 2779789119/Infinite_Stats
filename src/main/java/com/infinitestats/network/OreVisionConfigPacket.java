package com.infinitestats.network;

import com.infinitestats.stats.OreVisionConfig;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

/**
 * 矿透配置下发（服务器 → 客户端）。
 * <p>
 * 只在登录与设置变更时发一次，不进高频的属性同步包 —— 这几项设置几乎不变，
 * 塞进 {@code SyncStatsPacket} 只会让每次加点都多传一份没人看的数据。
 * <p>
 * 「是否解锁」不用单独传：客户端的 {@link PlayerStats} 同样有已分配点数，
 * {@code isToggleActive("ore_vision")} 在两端结果一致。
 */
public final class OreVisionConfigPacket {

    /** 序列化后的 {@link OreVisionConfig}。 */
    private final CompoundTag data;

    public OreVisionConfigPacket(OreVisionConfig config) {
        this.data = config.serialize();
    }

    private OreVisionConfigPacket(CompoundTag data) {
        this.data = data;
    }

    public static void encode(OreVisionConfigPacket msg, FriendlyByteBuf buf) {
        buf.writeNbt(msg.data);
    }

    public static OreVisionConfigPacket decode(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        return new OreVisionConfigPacket(tag == null ? new CompoundTag() : tag);
    }

    public static void handle(OreVisionConfigPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            PlayerStatsProvider.of(player).ifPresent(stats ->
                    stats.getOreVisionConfig().copyFrom(OreVisionConfig.deserialize(msg.data)));
        });
        ctx.get().setPacketHandled(true);
    }
}
