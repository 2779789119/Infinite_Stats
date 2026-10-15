package com.infinitestats.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Compatibility shim presenting the legacy Forge {@code SimpleChannel} surface on top of
 * NeoForge 1.21.1's payload registration API. A single {@link RawPayload} type carries every
 * message, demultiplexed by the numeric id assigned through {@link #registerMessage}.
 */
public final class SimpleChannel {

    @FunctionalInterface
    public interface PacketEncoder<T> {
        void encode(T msg, FriendlyByteBuf buf);
    }

    @FunctionalInterface
    public interface PacketDecoder<T> {
        T decode(FriendlyByteBuf buf);
    }

    @FunctionalInterface
    public interface PacketHandler<T> {
        void handle(T msg, Supplier<NetworkEvent.Context> ctx);
    }

    private static final class Entry {
        final PacketEncoder<?> encoder;
        final PacketDecoder<?> decoder;
        final PacketHandler<?> handler;

        Entry(PacketEncoder<?> encoder, PacketDecoder<?> decoder, PacketHandler<?> handler) {
            this.encoder = encoder;
            this.decoder = decoder;
            this.handler = handler;
        }
    }

    private final Map<Integer, Entry> byId = new HashMap<>();
    private final Map<Class<?>, Integer> idByClass = new HashMap<>();

    public SimpleChannel(ResourceLocation name, Supplier<String> version,
                         Predicate<String> clientAccepted, Predicate<String> serverAccepted) {
        // The legacy negotiation predicates are unused: NeoForge handles protocol matching.
    }

    /** Registers the shared transport payload with the current NeoForge payload registrar. */
    public void bind(PayloadRegistrar registrar) {
        registrar.playBidirectional(RawPayload.TYPE, RawPayload.CODEC, this::dispatch);
    }

    public <T> void registerMessage(int id, Class<T> type,
                                    PacketEncoder<T> encoder, PacketDecoder<T> decoder,
                                    PacketHandler<T> handler) {
        byId.put(id, new Entry(encoder, decoder, handler));
        idByClass.put(type, id);
    }

    @SuppressWarnings("unchecked")
    private void dispatch(RawPayload payload, IPayloadContext context) {
        Entry entry = byId.get(payload.id());
        if (entry == null) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload.data()));
        Object msg = ((PacketDecoder<Object>) entry.decoder).decode(buf);
        ((PacketHandler<Object>) entry.handler).handle(msg, () -> new NetworkEvent.Context(context));
    }

    @SuppressWarnings("unchecked")
    private RawPayload wrap(Object msg) {
        Integer id = idByClass.get(msg.getClass());
        if (id == null) {
            throw new IllegalArgumentException("Unregistered packet type: " + msg.getClass().getName());
        }
        Entry entry = byId.get(id);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        ((PacketEncoder<Object>) entry.encoder).encode(msg, buf);
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        return new RawPayload(id, data);
    }

    public void sendToServer(Object msg) {
        PacketDistributor.sendToServer(wrap(msg));
    }

    public void sendToPlayer(ServerPlayer player, Object msg) {
        PacketDistributor.sendToPlayer(player, wrap(msg));
    }

    public void sendToTrackingEntityAndSelf(Entity entity, Object msg) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, wrap(msg));
    }
}