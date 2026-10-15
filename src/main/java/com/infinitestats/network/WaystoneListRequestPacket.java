package com.infinitestats.network;

import com.infinitestats.compat.WaystonesCompat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

/**
 * 传送石碑列表请求（客户端 → 服务端）：打开传送点面板时发一次。
 * <p>
 * 服务端用 {@link WaystonesCompat#getActivated(ServerPlayer)} 读该玩家已激活的石碑，
 * 再通过 {@link WaystoneListPacket} 回推。没装传送石碑时列表恒为空，面板里不会多出任何行。
 */
public final class WaystoneListRequestPacket {

    public WaystoneListRequestPacket() {}

    public static void encode(WaystoneListRequestPacket msg, FriendlyByteBuf buf) {
        // 无字段
    }

    public static WaystoneListRequestPacket decode(FriendlyByteBuf buf) {
        return new WaystoneListRequestPacket();
    }

    public static void handle(WaystoneListRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            NetworkHandler.sendWaystones(player, WaystonesCompat.getActivated(player));
        });
        ctx.get().setPacketHandled(true);
    }
}
