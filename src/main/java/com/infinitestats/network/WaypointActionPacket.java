package com.infinitestats.network;

import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import com.infinitestats.stats.Waypoint;
import com.infinitestats.util.TeleportUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 传送点操作数据包（客户端 → 服务器）
 * 用于传送点面板的保存 / 删除 / 传送 / 重命名
 */
public final class WaypointActionPacket {

    public enum Action {
        SET, DELETE, TELEPORT, RENAME
    }

    private final Action action;
    private final String name;      // SET / DELETE / TELEPORT / RENAME(旧名)
    private final String newName;   // RENAME 新名
    private final String dimension; // SET
    private final double x, y, z;   // SET
    private final float yaw, pitch; // SET

    public WaypointActionPacket(Action action, String name, String newName,
                                String dimension, double x, double y, double z,
                                float yaw, float pitch) {
        this.action = action;
        this.name = name;
        this.newName = newName;
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public static void encode(WaypointActionPacket msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.action.ordinal());
        buf.writeUtf(msg.name == null ? "" : msg.name);
        buf.writeUtf(msg.newName == null ? "" : msg.newName);
        buf.writeUtf(msg.dimension == null ? "" : msg.dimension);
        buf.writeDouble(msg.x);
        buf.writeDouble(msg.y);
        buf.writeDouble(msg.z);
        buf.writeFloat(msg.yaw);
        buf.writeFloat(msg.pitch);
    }

    public static WaypointActionPacket decode(FriendlyByteBuf buf) {
        Action action = Action.values()[buf.readByte()];
        String name = buf.readUtf();
        String newName = buf.readUtf();
        String dimension = buf.readUtf();
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        float yaw = buf.readFloat();
        float pitch = buf.readFloat();
        return new WaypointActionPacket(action, name, newName, dimension, x, y, z, yaw, pitch);
    }

    public static void handle(WaypointActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            PlayerStats stats = player.getCapability(PlayerStatsProvider.PLAYER_STATS).orElse(null);
            if (stats == null) return;

            // 所有传送点操作都需要『定点传送』属性激活
            if (!stats.isToggleActive("fixed_point_teleport")) {
                player.displayClientMessage(Component.translatable("message.infinitestats.teleport_disabled"), true);
                return;
            }

            switch (msg.action) {
                case SET -> {
                    if (msg.name.isBlank()) {
                        player.displayClientMessage(Component.translatable("message.infinitestats.wp_name_empty"), true);
                        return;
                    }
                    stats.setWaypoint(msg.name, new Waypoint(msg.dimension, msg.x, msg.y, msg.z, msg.yaw, msg.pitch));
                    player.displayClientMessage(Component.translatable("message.infinitestats.wp_saved", msg.name), true);
                    NetworkHandler.syncToClient(player);
                }
                case DELETE -> {
                    if (!stats.hasWaypoint(msg.name)) {
                        player.displayClientMessage(Component.translatable("message.infinitestats.waypoint_not_found", msg.name), true);
                        return;
                    }
                    stats.removeWaypoint(msg.name);
                    player.displayClientMessage(Component.translatable("message.infinitestats.wp_deleted", msg.name), true);
                    NetworkHandler.syncToClient(player);
                }
                case TELEPORT -> {
                    Waypoint wp = stats.getWaypoint(msg.name);
                    if (wp == null) {
                        player.displayClientMessage(Component.translatable("message.infinitestats.waypoint_not_found", msg.name), true);
                        return;
                    }
                    ResourceKey<Level> dimKey = ResourceKey.create(
                            Registries.DIMENSION, new ResourceLocation(wp.dimension));
                    ServerLevel target = player.getServer().getLevel(dimKey);
                    if (target == null) {
                        player.displayClientMessage(Component.translatable("message.infinitestats.wp_dim_unloaded", wp.dimension), true);
                        return;
                    }
                    // 强制传送：同样绕过外部「维度进入权限」（含跨维度的路点）
                    TeleportUtil.forceTeleportTo(player, target, wp.x, wp.y, wp.z, wp.yaw, wp.pitch);
                    player.displayClientMessage(Component.translatable("message.infinitestats.waypoint_teleported", msg.name), true);
                }
                case RENAME -> {
                    if (msg.newName.isBlank()) {
                        player.displayClientMessage(Component.translatable("message.infinitestats.wp_name_empty"), true);
                        return;
                    }
                    if (!stats.hasWaypoint(msg.name)) {
                        player.displayClientMessage(Component.translatable("message.infinitestats.waypoint_not_found", msg.name), true);
                        return;
                    }
                    Waypoint wp = stats.getWaypoint(msg.name);
                    stats.removeWaypoint(msg.name);
                    stats.setWaypoint(msg.newName, wp);
                    player.displayClientMessage(Component.translatable("message.infinitestats.wp_renamed", msg.name, msg.newName), true);
                    NetworkHandler.syncToClient(player);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
