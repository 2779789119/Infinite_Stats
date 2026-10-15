package com.infinitestats.network;

import com.infinitestats.client.EntityPanelScreen;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

/**
 * 生物面板：服务端 → 客户端，要求打开（或刷新）目标生物的面板。
 * <p>
 * 由服务端在「手持生物观察杖右键生物」时下发（见 {@code StatEventHandler#onEntityInteract}），
 * 也用于每次切换开关后把权威状态推回客户端。属性数值从客户端本地那份实体数据里读
 * （属性表本来就随实体追踪同步），开关状态则只能来自这里 ——
 * {@code NoAI} / {@code Silent} 这些字段大多不在同步实体数据里，客户端本地读到的可能是旧值。
 */
public final class OpenEntityPanelPacket {

    private final int entityId;
    private final int applicableMask;
    private final int enabledMask;
    private final boolean editable;

    public OpenEntityPanelPacket(int entityId, int applicableMask, int enabledMask, boolean editable) {
        this.entityId = entityId;
        this.applicableMask = applicableMask;
        this.enabledMask = enabledMask;
        this.editable = editable;
    }

    public static void encode(OpenEntityPanelPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeVarInt(msg.applicableMask);
        buf.writeVarInt(msg.enabledMask);
        buf.writeBoolean(msg.editable);
    }

    public static OpenEntityPanelPacket decode(FriendlyByteBuf buf) {
        return new OpenEntityPanelPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(OpenEntityPanelPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> EntityPanelScreen.openForEntity(
                msg.entityId, msg.applicableMask, msg.enabledMask, msg.editable));
        ctx.get().setPacketHandled(true);
    }
}
