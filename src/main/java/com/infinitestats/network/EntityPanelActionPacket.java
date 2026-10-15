package com.infinitestats.network;

import com.infinitestats.util.EntityPanelToggles;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Supplier;

/**
 * 生物面板：客户端 → 服务器，请求切换目标生物的某个开关。
 * <p>
 * 只传「实体 id + 开关下标 + 想要的值」，真正能不能改由服务端说了算
 * （属性是否激活、手里有没有观察杖、距离、配置开关都在这边查），
 * 执行完再回一发 {@link OpenEntityPanelPacket} 把客户端刷新成权威状态 ——
 * 所以客户端就算乱发包也只能改到自己有权改的生物，改不动就原地回弹。
 */
public final class EntityPanelActionPacket {

    private final int entityId;
    private final int index;
    private final boolean value;

    public EntityPanelActionPacket(int entityId, int index, boolean value) {
        this.entityId = entityId;
        this.index = index;
        this.value = value;
    }

    public static void encode(EntityPanelActionPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeVarInt(msg.index);
        buf.writeBoolean(msg.value);
    }

    public static EntityPanelActionPacket decode(FriendlyByteBuf buf) {
        return new EntityPanelActionPacket(buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(EntityPanelActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            // 目标不一定还是生物 / 可能已经被打死或卸载
            Entity target = player.level().getEntity(msg.entityId);
            if (!(target instanceof LivingEntity)) return;

            boolean allowed = NetworkHandler.canEditEntity(player, target);
            if (allowed) {
                EntityPanelToggles.apply(target, msg.index, msg.value);
            }
            // 无论成功与否都回一发现状：客户端据此覆盖自己的乐观更新
            NetworkHandler.sendEntityPanelState(player, target, allowed);
        });
        ctx.get().setPacketHandled(true);
    }
}
