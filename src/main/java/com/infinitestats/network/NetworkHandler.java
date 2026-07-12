package com.infinitestats.network;

import com.infinitestats.InfiniteStats;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import com.infinitestats.stats.StatType;
import com.infinitestats.handler.AttributeHandler;
import com.infinitestats.handler.HandlerRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 网络处理系统 - 高效的数据包设计
 * 使用简洁的协议减少带宽占用
 */
public final class NetworkHandler {

    private static final String PROTOCOL_VERSION = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(InfiniteStats.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    /**
     * 注册所有数据包
     */
    public static void register() {
        // 同步数据包（服务器 → 客户端）
        CHANNEL.registerMessage(packetId++, SyncStatsPacket.class,
                SyncStatsPacket::encode,
                SyncStatsPacket::decode,
                SyncStatsPacket::handle);

        // 属性修改数据包（客户端 → 服务器）
        CHANNEL.registerMessage(packetId++, ModifyStatPacket.class,
                ModifyStatPacket::encode,
                ModifyStatPacket::decode,
                ModifyStatPacket::handle);

        // 分类重置数据包（客户端 → 服务器）
        CHANNEL.registerMessage(packetId++, ResetCategoryPacket.class,
                ResetCategoryPacket::encode,
                ResetCategoryPacket::decode,
                ResetCategoryPacket::handle);

        // 全部重置数据包（客户端 → 服务器）
        CHANNEL.registerMessage(packetId++, ResetAllPacket.class,
                ResetAllPacket::encode,
                ResetAllPacket::decode,
                ResetAllPacket::handle);

        // Debuff 过滤列表更新数据包（客户端 → 服务器）
        CHANNEL.registerMessage(packetId++, UpdateDebuffFilterPacket.class,
                UpdateDebuffFilterPacket::encode,
                UpdateDebuffFilterPacket::decode,
                UpdateDebuffFilterPacket::handle);

        // 物品编辑器数据包（客户端 → 服务器）
        CHANNEL.registerMessage(packetId++, EditItemPacket.class,
                EditItemPacket::encode,
                EditItemPacket::decode,
                EditItemPacket::handle);

        // 物品元数据编辑数据包（客户端 → 服务器）
        CHANNEL.registerMessage(packetId++, EditItemMetaPacket.class,
                EditItemMetaPacket::encode,
                EditItemMetaPacket::decode,
                EditItemMetaPacket::handle);
    }

    // ========== 数据包类 ==========

    /**
     * 属性修改数据包
     */
    public static final class ModifyStatPacket {
        private final String statId;
        private final long amount;

        public ModifyStatPacket(String statId, long amount) {
            this.statId = statId;
            this.amount = amount;
        }

        public static void encode(ModifyStatPacket msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.statId);
            buf.writeVarLong(msg.amount);
        }

        public static ModifyStatPacket decode(FriendlyByteBuf buf) {
            return new ModifyStatPacket(buf.readUtf(), buf.readVarLong());
        }

        public static void handle(ModifyStatPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;

                player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
                    StatType stat = StatType.fromId(msg.statId);
                    if (stat == null) return;

                    boolean success;
                    if (msg.amount > 0) {
                        success = stats.addPoints(stat, msg.amount);
                    } else {
                        success = stats.removePoints(stat, -msg.amount);
                    }

                    if (success) {
                        // 同步到客户端
                        syncToClient(player);
                        // 更新属性
                        AttributeHandler.applyAllAttributes(player, stats);
                    }
                });
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /**
     * 分类重置数据包
     */
    public static final class ResetCategoryPacket {
        private final String categoryName;

        public ResetCategoryPacket(String categoryName) {
            this.categoryName = categoryName;
        }

        public static void encode(ResetCategoryPacket msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.categoryName);
        }

        public static ResetCategoryPacket decode(FriendlyByteBuf buf) {
            return new ResetCategoryPacket(buf.readUtf());
        }

        public static void handle(ResetCategoryPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;

                player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
                    for (StatType stat : StatType.ALL_STATS) {
                        if (stat.getCategory().getName().equals(msg.categoryName)) {
                            stats.resetStat(stat);
                        }
                    }
                    syncToClient(player);
                    AttributeHandler.applyAllAttributes(player, stats);
                });
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /**
     * 全部重置数据包
     */
    public static final class ResetAllPacket {
        public ResetAllPacket() {}

        public static void encode(ResetAllPacket msg, FriendlyByteBuf buf) {
            // 无数据
        }

        public static ResetAllPacket decode(FriendlyByteBuf buf) {
            return new ResetAllPacket();
        }

        public static void handle(ResetAllPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;

                player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
                    stats.resetAllPoints();
                    syncToClient(player);
                    AttributeHandler.applyAllAttributes(player, stats);
                });
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /**
     * Debuff 过滤列表更新数据包
     */
    public static final class UpdateDebuffFilterPacket {
        private final boolean useBlacklist;
        private final Set<String> effectIds;

        public UpdateDebuffFilterPacket(boolean useBlacklist, Set<String> effectIds) {
            this.useBlacklist = useBlacklist;
            this.effectIds = effectIds;
        }

        public static void encode(UpdateDebuffFilterPacket msg, FriendlyByteBuf buf) {
            buf.writeBoolean(msg.useBlacklist);
            buf.writeVarInt(msg.effectIds.size());
            for (String id : msg.effectIds) {
                buf.writeUtf(id);
            }
        }

        public static UpdateDebuffFilterPacket decode(FriendlyByteBuf buf) {
            boolean useBlacklist = buf.readBoolean();
            int count = buf.readVarInt();
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < count; i++) {
                ids.add(buf.readUtf());
            }
            return new UpdateDebuffFilterPacket(useBlacklist, ids);
        }

        public static void handle(UpdateDebuffFilterPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;

                player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
                    stats.setDebuffFilterList(new HashSet<>(msg.effectIds), msg.useBlacklist);
                    // 同步回客户端
                    syncToClient(player);
                });
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // ========== 同步方法 ==========

    /**
     * 同步玩家数据到客户端
     */
    public static void syncToClient(ServerPlayer player) {
        player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), 
                    new SyncStatsPacket(stats.createSnapshot()));
        });
    }

    /**
     * 同步所有数据到指定客户端
     */
    public static void syncToTracking(ServerPlayer player) {
        player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
            CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                    new SyncStatsPacket(stats.createSnapshot()));
        });
    }

    /**
     * 向所有玩家广播数据
     */
    public static void broadcastToAll(PlayerStats stats) {
        // 通常只在调试时使用
        // CHANNEL.send(PacketDistributor.ALL.noArg(), new SyncStatsPacket(stats.createSnapshot()));
    }
}