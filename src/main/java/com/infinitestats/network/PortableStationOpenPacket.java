package com.infinitestats.network;

import com.infinitestats.crafting.PortableStations;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

/**
 * 请求打开某个随身站点（客户端 → 服务器）。
 * <p>
 * 只传属性 id，服务端自行判定该属性是否已激活；未激活时回一句提示并拒绝，
 * 因此客户端界面即便被绕过也无法打开未解锁的站点。
 * 支持的 id 见 {@link PortableStations#open}。
 */
public final class PortableStationOpenPacket {

    private final String toolId;

    public PortableStationOpenPacket(String toolId) {
        this.toolId = toolId == null ? "" : toolId;
    }

    public static void encode(PortableStationOpenPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.toolId);
    }

    public static PortableStationOpenPacket decode(FriendlyByteBuf buf) {
        return new PortableStationOpenPacket(buf.readUtf());
    }

    public static void handle(PortableStationOpenPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            PlayerStats stats = PlayerStatsProvider.of(player).orElse(null);
            if (stats == null) return;

            String id = msg.toolId;
            if (!stats.isToggleActive(id)) {
                player.displayClientMessage(
                        Component.translatable("message.infinitestats.portable_station_locked",
                                Component.translatable("stat.infinitestats." + id)), true);
                return;
            }

            // 这里只做入口校验（属性是否激活），具体菜单由各自实现负责
            PortableStations.open(player, id);
        });
        ctx.get().setPacketHandled(true);
    }
}
