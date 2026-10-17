package com.infinitestats.network;

import com.infinitestats.stats.OreVisionConfig;
import com.infinitestats.stats.PlayerStatsProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 矿透设置同步包（服务端 → 客户端）。
 * <p>
 * 与 {@link VeinMinerConfigPacket} 同款：只在玩家改动（或登录 / 重生）时下发一份，
 * 不跟着每秒都在跑的属性同步走。客户端收到后整份替换本地配置，渲染层下一帧就是新设置。
 */
public final class OreVisionConfigPacket {

    private final CompoundTag data;

    public OreVisionConfigPacket(OreVisionConfig config) {
        this.data = config == null ? new CompoundTag() : config.serialize();
    }

    private OreVisionConfigPacket(CompoundTag data) {
        this.data = data == null ? new CompoundTag() : data;
    }

    public static void encode(OreVisionConfigPacket msg, FriendlyByteBuf buf) {
        buf.writeNbt(msg.data);
    }

    public static OreVisionConfigPacket decode(FriendlyByteBuf buf) {
        return new OreVisionConfigPacket(buf.readNbt());
    }

    public static void handle(OreVisionConfigPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            player.getCapability(PlayerStatsProvider.PLAYER_STATS)
                    .ifPresent(stats -> stats.setOreVisionConfig(OreVisionConfig.deserialize(msg.data)));
        });
        ctx.get().setPacketHandled(true);
    }
}
