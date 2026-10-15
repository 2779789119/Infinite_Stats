package com.infinitestats.network;

import com.infinitestats.compat.WaystonesCompat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 传送请求（客户端 → 服务端）：把玩家免费（不扣经验、不进冷却）传送到指定石碑。
 * <p>
 * 服务端只认「该玩家已激活的石碑」—— 客户端递上来的 UUID 会被拿去和他自己的列表比对，
 * 不在列表里就什么都不做（见 {@link WaystonesCompat#teleport}），
 * 因此改包也传不到自己没踩过的石碑。
 */
public final class WaystoneTeleportPacket {

    private final UUID uid;

    public WaystoneTeleportPacket(UUID uid) {
        this.uid = uid;
    }

    public static void encode(WaystoneTeleportPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.uid);
    }

    public static WaystoneTeleportPacket decode(FriendlyByteBuf buf) {
        return new WaystoneTeleportPacket(buf.readUUID());
    }

    public static void handle(WaystoneTeleportPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                WaystonesCompat.teleport(player, msg.uid);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
