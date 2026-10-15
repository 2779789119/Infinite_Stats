package com.infinitestats.network;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

import java.util.function.Supplier;

/**
 * 生物面板：客户端 → 服务器，把某条属性的**基础值**改成指定数值。
 * <p>
 * 与开关同一条权限链（{@link NetworkHandler#canEditEntity}）；数值会被夹进属性自己的取值范围
 * （{@code RangedAttribute} 的 min / max），改「生命上限」后会顺手把当前生命夹回上限之内。
 * 属性值的同步走原版机制（{@code ClientboundUpdateAttributesPacket}），不需要额外回包 ——
 * 客户端面板每 10 tick 重读一次本地实体，权威值会自动盖掉乐观更新。
 *
 * <h3>1.21.1 适配</h3>
 * 属性注册表从 {@code ForgeRegistries.ATTRIBUTES} 换成 {@code BuiltInRegistries.ATTRIBUTE}，
 * 属性以 {@link Holder} 参与判定（{@code LivingEntity#getAttribute(Holder)}）；
 * 属性 id 也去掉了 {@code generic.} 前缀（如 {@code minecraft:max_health}）。
 */
public final class EntityPanelAttributePacket {

    private final int entityId;
    private final ResourceLocation attributeId;
    private final double value;

    public EntityPanelAttributePacket(int entityId, ResourceLocation attributeId, double value) {
        this.entityId = entityId;
        this.attributeId = attributeId;
        this.value = value;
    }

    public static void encode(EntityPanelAttributePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeResourceLocation(msg.attributeId);
        buf.writeDouble(msg.value);
    }

    public static EntityPanelAttributePacket decode(FriendlyByteBuf buf) {
        return new EntityPanelAttributePacket(buf.readVarInt(), buf.readResourceLocation(), buf.readDouble());
    }

    public static void handle(EntityPanelAttributePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            Entity target = player.level().getEntity(msg.entityId);
            if (!(target instanceof LivingEntity living) || !NetworkHandler.canEditEntity(player, target)) return;

            Holder<Attribute> holder = BuiltInRegistries.ATTRIBUTE.getHolder(msg.attributeId).orElse(null);
            if (holder == null) return;
            AttributeInstance instance = living.getAttribute(holder);
            if (instance == null) return;

            double value = msg.value;
            if (!Double.isFinite(value)) return;
            // 取值范围：RangedAttribute 自带 min / max（没有范围的属性不夹）
            if (holder.value() instanceof RangedAttribute ranged) {
                value = Mth.clamp(value, ranged.getMinValue(), ranged.getMaxValue());
            }

            try {
                instance.setBaseValue(value);
            } catch (Throwable ignored) {
                return; // 个别属性在特殊状态下会拒绝写入，当作没改
            }
            // 生命上限被改小后，当前生命要跟着夹回来，否则会顶着一截超出上限的血
            if (holder.is(Attributes.MAX_HEALTH)) {
                living.setHealth(Math.min(living.getHealth(), living.getMaxHealth()));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
