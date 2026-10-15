package com.infinitestats.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 传送石碑列表同步（服务端 → 客户端）：把玩家已激活的石碑下发给 {@code WaypointScreen}。
 * <p>
 * 只带显示与传送所需的最小字段（UUID / 名称 / 维度 / 坐标），<b>不引用传送石碑的任何类型</b>：
 * 没装传送石碑时这份列表是空的，本包照样能收发。
 * <p>
 * 客户端收到后写进 {@link NetworkHandler#pendingWaystones} 并让
 * {@link NetworkHandler#waystoneListVersion} 自增，界面据此重建列表
 * （与区块强加载列表同一套「版本号比对」写法，界面不必自己猜何时刷新）。
 */
public final class WaystoneListPacket {

    private final List<WaystoneEntry> waystones;

    public WaystoneListPacket(List<WaystoneEntry> waystones) {
        this.waystones = waystones == null ? List.of() : waystones;
    }

    public static void encode(WaystoneListPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.waystones.size());
        for (WaystoneEntry entry : msg.waystones) {
            buf.writeUUID(entry.uid);
            buf.writeUtf(entry.name, 256);
            buf.writeUtf(entry.dimension, 256);
            buf.writeDouble(entry.x);
            buf.writeDouble(entry.y);
            buf.writeDouble(entry.z);
        }
    }

    public static WaystoneListPacket decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<WaystoneEntry> list = new ArrayList<>(Math.max(0, size));
        for (int i = 0; i < size; i++) {
            UUID uid = buf.readUUID();
            String name = buf.readUtf(256);
            String dimension = buf.readUtf(256);
            list.add(new WaystoneEntry(uid, name, dimension,
                    buf.readDouble(), buf.readDouble(), buf.readDouble()));
        }
        return new WaystoneListPacket(list);
    }

    public static void handle(WaystoneListPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            NetworkHandler.pendingWaystones = new ArrayList<>(msg.waystones);
            NetworkHandler.waystoneListVersion++;
        });
        ctx.get().setPacketHandled(true);
    }
}
