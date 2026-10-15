package com.infinitestats.network;

import com.infinitestats.InfiniteStats;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Generic transport payload. All legacy "simple channel" messages are serialized
 * into a raw byte array and tagged with their numeric id, so the existing
 * encode/decode/handle implementations keep working unmodified.
 */
public record RawPayload(int id, byte[] data) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RawPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(InfiniteStats.MODID, "raw"));

    public static final StreamCodec<FriendlyByteBuf, RawPayload> CODEC =
            CustomPacketPayload.codec(RawPayload::write, RawPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.id);
        buf.writeByteArray(this.data);
    }

    private static RawPayload read(FriendlyByteBuf buf) {
        return new RawPayload(buf.readVarInt(), buf.readByteArray());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}