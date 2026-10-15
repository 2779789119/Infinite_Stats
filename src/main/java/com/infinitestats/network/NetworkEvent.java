package com.infinitestats.network;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Compatibility shim mimicking the legacy Forge {@code NetworkEvent.Context} API
 * on top of NeoForge 1.21.1's {@link IPayloadContext}.
 */
public final class NetworkEvent {

    public static final class Context {
        private final IPayloadContext payloadContext;

        public Context(IPayloadContext payloadContext) {
            this.payloadContext = payloadContext;
        }

        public void enqueueWork(Runnable runnable) {
            payloadContext.enqueueWork(runnable);
        }

        public ServerPlayer getSender() {
            Player player = payloadContext.player();
            return player instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        }

        public void setPacketHandled(boolean value) {
            // No-op: NeoForge payload handling is explicit and needs no acknowledgement.
        }
    }

    private NetworkEvent() {}
}