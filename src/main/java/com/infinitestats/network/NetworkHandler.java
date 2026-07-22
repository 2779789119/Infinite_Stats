package com.infinitestats.network;

import com.infinitestats.InfiniteStats;
import com.infinitestats.crafting.PortableCraftingMenu;
import com.infinitestats.emc.EmcDatabase;
import com.infinitestats.emc.EmcMenu;
import com.infinitestats.emc.EmcPlayerData;
import com.infinitestats.emc.EmcPlayerDataProvider;
import com.infinitestats.furnace.FurnaceFuelBufferMenu;
import com.infinitestats.furnace.PortableFurnaceMenu;
import com.infinitestats.Config;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import com.infinitestats.stats.StatType;
import com.infinitestats.handler.AttributeHandler;
import com.infinitestats.util.TeleportUtil;
import com.infinitestats.handler.HandlerRegistry;
import com.infinitestats.client.CrossDimScreen;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.*;
import java.lang.reflect.Method;
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

    /** 待处理的成就同步数据（由 SyncAdvancementsPacket 写入，AchievementManagerScreen 读取） */
    public static List<AchievementInfo> pendingAdvancements = null;

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

        // Buff 过滤列表更新数据包（客户端 → 服务器）
        CHANNEL.registerMessage(packetId++, UpdateBuffFilterPacket.class,
                UpdateBuffFilterPacket::encode,
                UpdateBuffFilterPacket::decode,
                UpdateBuffFilterPacket::handle);

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

        // ========== EMC 数据包 ==========

        // EMC 同步数据包（服务器 → 客户端）
        CHANNEL.registerMessage(packetId++, EmcSyncPacket.class,
                EmcSyncPacket::encode,
                EmcSyncPacket::decode,
                EmcSyncPacket::handle);

        // 物品学习数据包（客户端 → 服务器）
        CHANNEL.registerMessage(packetId++, EmcLearnPacket.class,
                EmcLearnPacket::encode,
                EmcLearnPacket::decode,
                EmcLearnPacket::handle);

        // 物品提取数据包（客户端 → 服务器）
        CHANNEL.registerMessage(packetId++, EmcExtractPacket.class,
                EmcExtractPacket::encode,
                EmcExtractPacket::decode,
                EmcExtractPacket::handle);

        // EMC 菜单打开数据包（客户端 → 服务器）
        CHANNEL.registerMessage(packetId++, EmcOpenPacket.class,
                EmcOpenPacket::encode,
                EmcOpenPacket::decode,
                EmcOpenPacket::handle);

        // 便携式熔炉菜单打开数据包（客户端 → 服务器）
        CHANNEL.registerMessage(packetId++, FurnaceOpenPacket.class,
                FurnaceOpenPacket::encode,
                FurnaceOpenPacket::decode,
                FurnaceOpenPacket::handle);

        // 燃料仓菜单打开数据包（客户端 → 服务器）
        CHANNEL.registerMessage(packetId++, FurnaceFuelOpenPacket.class,
                FurnaceFuelOpenPacket::encode,
                FurnaceFuelOpenPacket::decode,
                FurnaceFuelOpenPacket::handle);

        // 随身熔炉速度调节数据包（客户端 → 服务器，消耗/返还可分配点数）
        CHANNEL.registerMessage(packetId++, FurnaceSpeedPacket.class,
                FurnaceSpeedPacket::encode,
                FurnaceSpeedPacket::decode,
                FurnaceSpeedPacket::handle);

        // 随身工作台倍率调节数据包（客户端 → 服务器，消耗/返还可分配点数）
        CHANNEL.registerMessage(packetId++, CraftingMultiplierPacket.class,
                CraftingMultiplierPacket::encode,
                CraftingMultiplierPacket::decode,
                CraftingMultiplierPacket::handle);

        // 随身工作台菜单打开数据包（客户端 → 服务器）
        CHANNEL.registerMessage(packetId++, CraftingOpenPacket.class,
                CraftingOpenPacket::encode,
                CraftingOpenPacket::decode,
                CraftingOpenPacket::handle);

        // 跨维度传送请求数据包（客户端 → 服务器）
        CHANNEL.registerMessage(packetId++, CrossDimRequestPacket.class,
                CrossDimRequestPacket::encode,
                CrossDimRequestPacket::decode,
                CrossDimRequestPacket::handle);

        // 跨维度维度列表请求 / 响应（客户端 ↔ 服务器）
        CHANNEL.registerMessage(packetId++, CrossDimListRequestPacket.class,
                CrossDimListRequestPacket::encode,
                CrossDimListRequestPacket::decode,
                CrossDimListRequestPacket::handle);
        CHANNEL.registerMessage(packetId++, CrossDimListPacket.class,
                CrossDimListPacket::encode,
                CrossDimListPacket::decode,
                CrossDimListPacket::handle);

        // ========== 成就管理数据包 ==========

        // 请求成就列表（客户端 → 服务器）
        CHANNEL.registerMessage(packetId++, RequestAdvancementsPacket.class,
                RequestAdvancementsPacket::encode,
                RequestAdvancementsPacket::decode,
                RequestAdvancementsPacket::handle);

        // 同步成就列表（服务器 → 客户端）
        CHANNEL.registerMessage(packetId++, SyncAdvancementsPacket.class,
                SyncAdvancementsPacket::encode,
                SyncAdvancementsPacket::decode,
                SyncAdvancementsPacket::handle);

        // 切换成就状态（客户端 → 服务器）
        CHANNEL.registerMessage(packetId++, ToggleAdvancementPacket.class,
                ToggleAdvancementPacket::encode,
                ToggleAdvancementPacket::decode,
                ToggleAdvancementPacket::handle);

        // ========== 传送点管理数据包 ==========

        // 传送点操作（客户端 → 服务器）
        CHANNEL.registerMessage(packetId++, WaypointActionPacket.class,
                WaypointActionPacket::encode,
                WaypointActionPacket::decode,
                WaypointActionPacket::handle);
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
     * Buff 过滤列表更新数据包
     */
    public static final class UpdateBuffFilterPacket {
        private final boolean useBlacklist;
        private final Set<String> effectIds;

        public UpdateBuffFilterPacket(boolean useBlacklist, Set<String> effectIds) {
            this.useBlacklist = useBlacklist;
            this.effectIds = effectIds;
        }

        public static void encode(UpdateBuffFilterPacket msg, FriendlyByteBuf buf) {
            buf.writeBoolean(msg.useBlacklist);
            buf.writeVarInt(msg.effectIds.size());
            for (String id : msg.effectIds) {
                buf.writeUtf(id);
            }
        }

        public static UpdateBuffFilterPacket decode(FriendlyByteBuf buf) {
            boolean useBlacklist = buf.readBoolean();
            int count = buf.readVarInt();
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < count; i++) {
                ids.add(buf.readUtf());
            }
            return new UpdateBuffFilterPacket(useBlacklist, ids);
        }

        public static void handle(UpdateBuffFilterPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;

                player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
                    stats.setBuffFilterList(new HashSet<>(msg.effectIds), msg.useBlacklist);
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

    // ========== EMC 数据包类 ==========

    /**
     * EMC 同步数据包（服务器 → 客户端）
     */
    public static final class EmcSyncPacket {
        private final EmcPlayerData.EmcSnapshot snapshot;

        public EmcSyncPacket(EmcPlayerData.EmcSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        public static void encode(EmcSyncPacket msg, FriendlyByteBuf buf) {
            buf.writeVarLong(msg.snapshot.emcBalance);
            buf.writeVarInt(msg.snapshot.learnedItems.size());
            for (ResourceLocation id : msg.snapshot.learnedItems) {
                buf.writeUtf(id.toString());
            }
        }

        public static EmcSyncPacket decode(FriendlyByteBuf buf) {
            long balance = buf.readVarLong();
            int count = buf.readVarInt();
            List<ResourceLocation> items = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                ResourceLocation rl = ResourceLocation.tryParse(buf.readUtf());
                if (rl != null) items.add(rl);
            }
            return new EmcSyncPacket(new EmcPlayerData.EmcSnapshot(balance, items));
        }

        public static void handle(EmcSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                var player = net.minecraft.client.Minecraft.getInstance().player;
                if (player != null) {
                    player.getCapability(EmcPlayerDataProvider.EMC_PLAYER_DATA).ifPresent(data -> {
                        data.restoreFromSnapshot(msg.snapshot);
                    });
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /**
     * 物品学习数据包（客户端 → 服务器）
     */
    public static final class EmcLearnPacket {
        private final String itemId;

        public EmcLearnPacket(String itemId) {
            this.itemId = itemId;
        }

        public static void encode(EmcLearnPacket msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.itemId);
        }

        public static EmcLearnPacket decode(FriendlyByteBuf buf) {
            return new EmcLearnPacket(buf.readUtf());
        }

        public static void handle(EmcLearnPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;

                ResourceLocation itemId = ResourceLocation.tryParse(msg.itemId);
                if (itemId == null) return;

                Item item = BuiltInRegistries.ITEM.get(itemId);
                long emcValue = EmcDatabase.getEmc(new ItemStack(item));
                if (emcValue <= 0) return;

                player.getCapability(EmcPlayerDataProvider.EMC_PLAYER_DATA).ifPresent(data -> {
                    // 查找玩家背包中是否有此物品
                    for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                        ItemStack slotStack = player.getInventory().getItem(i);
                        if (!slotStack.isEmpty() && BuiltInRegistries.ITEM.getKey(slotStack.getItem()).equals(itemId)) {
                            // 消耗1个物品，返还EMC
                            slotStack.shrink(1);
                            data.learnAndConvert(itemId, emcValue);
                            syncEmcToClient(player);
                            break;
                        }
                    }
                });
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /**
     * 物品提取数据包（客户端 → 服务器）
     */
    public static final class EmcExtractPacket {
        private final String itemId;
        private final int count;

        public EmcExtractPacket(String itemId, int count) {
            this.itemId = itemId;
            this.count = count;
        }

        public static void encode(EmcExtractPacket msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.itemId);
            buf.writeVarInt(msg.count);
        }

        public static EmcExtractPacket decode(FriendlyByteBuf buf) {
            return new EmcExtractPacket(buf.readUtf(), buf.readVarInt());
        }

        public static void handle(EmcExtractPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;

                ResourceLocation itemId = ResourceLocation.tryParse(msg.itemId);
                if (itemId == null) return;

                Item item = BuiltInRegistries.ITEM.get(itemId);
                if (item == null) return;

                long emcPerItem = EmcDatabase.getEmc(new ItemStack(item));
                if (emcPerItem <= 0) return;

                player.getCapability(EmcPlayerDataProvider.EMC_PLAYER_DATA).ifPresent(data -> {
                    if (!data.hasLearned(itemId)) return;

                    int maxGive = Math.min(msg.count, 64);
                    long totalCost = emcPerItem * maxGive;
                    if (!data.consumeEmc(totalCost)) {
                        // 余额不足，给尽可能多的
                        long affordable = data.getEmcBalance() / emcPerItem;
                        if (affordable <= 0) return;
                        maxGive = (int) Math.min(affordable, 64);
                        totalCost = emcPerItem * maxGive;
                        data.consumeEmc(totalCost);
                    }

                    ItemStack result = new ItemStack(item, maxGive);
                    if (!player.getInventory().add(result)) {
                        player.drop(result, false);
                    }
                    syncEmcToClient(player);
                });
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /**
     * EMC 菜单打开数据包（客户端 → 服务器）
     */
    public static final class EmcOpenPacket {
        public EmcOpenPacket() {}

        public static void encode(EmcOpenPacket msg, FriendlyByteBuf buf) {
            // 无数据
        }

        public static EmcOpenPacket decode(FriendlyByteBuf buf) {
            return new EmcOpenPacket();
        }

        public static void handle(EmcOpenPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                NetworkHooks.openScreen(player,
                        new SimpleMenuProvider(
                                (id, inv, p) -> new EmcMenu(id, inv),
                                Component.translatable("screen.infinitestats.emc")));
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** 客户端请求打开便携式熔炉（需已开启 portable_furnace 开关）。 */
    public static final class FurnaceOpenPacket {
        public FurnaceOpenPacket() {}

        public static void encode(FurnaceOpenPacket msg, FriendlyByteBuf buf) {
            // 无数据
        }

        public static FurnaceOpenPacket decode(FriendlyByteBuf buf) {
            return new FurnaceOpenPacket();
        }

        public static void handle(FurnaceOpenPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
                    if (!stats.isToggleActive("portable_furnace")) return;
                    NetworkHooks.openScreen(player,
                            new SimpleMenuProvider(
                                    (id, inv, p) -> new PortableFurnaceMenu(id, inv),
                                    Component.translatable("screen.infinitestats.furnace")));
                });
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** 客户端请求打开燃料仓（需已开启 portable_furnace 开关）。 */
    public static final class FurnaceFuelOpenPacket {
        public FurnaceFuelOpenPacket() {}

        public static void encode(FurnaceFuelOpenPacket msg, FriendlyByteBuf buf) {
            // 无数据
        }

        public static FurnaceFuelOpenPacket decode(FriendlyByteBuf buf) {
            return new FurnaceFuelOpenPacket();
        }

        public static void handle(FurnaceFuelOpenPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
                    if (!stats.isToggleActive("portable_furnace")) return;
                    NetworkHooks.openScreen(player,
                            new SimpleMenuProvider(
                                    (id, inv, p) -> new FurnaceFuelBufferMenu(id, inv),
                                    Component.translatable("screen.infinitestats.furnace.fuel_buffer")));
                });
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** 客户端向服务器请求增减随身熔炉速度（消耗/返还可分配点数）。 */
    public static final class FurnaceSpeedPacket {
        private final boolean increase; // true=加速（消耗点数）；false=减速（返点数）

        public FurnaceSpeedPacket(boolean increase) {
            this.increase = increase;
        }

        public static void encode(FurnaceSpeedPacket msg, FriendlyByteBuf buf) {
            buf.writeBoolean(msg.increase);
        }

        public static FurnaceSpeedPacket decode(FriendlyByteBuf buf) {
            return new FurnaceSpeedPacket(buf.readBoolean());
        }

        public static void handle(FurnaceSpeedPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;

                player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
                    if (!stats.isToggleActive("portable_furnace")) return;

                    var furnace = stats.getFurnaceData();
                    int cost = Config.FURNACE_SPEED_COST.get();

                    if (msg.increase) {
                        if (stats.getAvailablePoints() < cost) {
                            player.sendSystemMessage(Component.literal(
                                    "§c可分配点数不足，提升一级速度需 " + cost + " 点"));
                            return;
                        }
                        stats.setAvailablePoints(stats.getAvailablePoints() - cost);
                        furnace.setSpeedLevel(furnace.getSpeedLevel() + 1);
                        player.sendSystemMessage(Component.literal(
                                "§a熔炉加速至 §f×" + furnace.getSpeedMultiplier()
                                        + "§a（消耗 " + cost + " 点）"));
                    } else {
                        if (furnace.getSpeedLevel() <= 0) {
                            player.sendSystemMessage(Component.literal("§e随身熔炉已处于普通速度"));
                            return;
                        }
                        furnace.setSpeedLevel(furnace.getSpeedLevel() - 1);
                        stats.setAvailablePoints(stats.getAvailablePoints() + cost);
                        player.sendSystemMessage(Component.literal(
                                "§a熔炉减速至 §f×" + furnace.getSpeedMultiplier()
                                        + "§a（返还 " + cost + " 点）"));
                    }

                    // 同步点数变动到客户端
                    syncToClient(player);
                });
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** 客户端向服务器请求增减随身工作台物品倍率（消耗/返还可分配点数）。 */
    public static final class CraftingMultiplierPacket {
        private static final int MAX_MULTIPLIER = 64; // 受物品堆叠上限约束
        private final boolean increase; // true=提升倍率（消耗点数）；false=降低倍率（返点数）

        public CraftingMultiplierPacket(boolean increase) {
            this.increase = increase;
        }

        public static void encode(CraftingMultiplierPacket msg, FriendlyByteBuf buf) {
            buf.writeBoolean(msg.increase);
        }

        public static CraftingMultiplierPacket decode(FriendlyByteBuf buf) {
            return new CraftingMultiplierPacket(buf.readBoolean());
        }

        public static void handle(CraftingMultiplierPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;

                player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
                    if (!stats.isToggleActive("portable_crafting")) return;

                    int cost = Config.CRAFTING_MULTIPLIER_COST.get();
                    long mult = stats.getCraftingMultiplier();

                    if (msg.increase) {
                        if (mult >= MAX_MULTIPLIER) {
                            player.sendSystemMessage(Component.literal(
                                    "§e随身工作台倍率已达上限 ×" + MAX_MULTIPLIER));
                            return;
                        }
                        if (stats.getAvailablePoints() < cost) {
                            player.sendSystemMessage(Component.literal(
                                    "§c可分配点数不足，提升一级倍率需 " + cost + " 点"));
                            return;
                        }
                        stats.setAvailablePoints(stats.getAvailablePoints() - cost);
                        stats.setCraftingMultiplier(mult + 1);
                        player.sendSystemMessage(Component.literal(
                                "§a工作台倍率提升至 §f×" + stats.getCraftingMultiplier()
                                        + "§a（消耗 " + cost + " 点）"));
                    } else {
                        if (mult <= 1) {
                            player.sendSystemMessage(Component.literal("§e随身工作台已处于基础倍率 ×1"));
                            return;
                        }
                        stats.setCraftingMultiplier(mult - 1);
                        stats.setAvailablePoints(stats.getAvailablePoints() + cost);
                        player.sendSystemMessage(Component.literal(
                                "§a工作台倍率降至 §f×" + stats.getCraftingMultiplier()
                                        + "§a（返还 " + cost + " 点）"));
                    }

                    // 同步点数与倍率变动到客户端
                    syncToClient(player);
                });
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** 客户端请求打开随身工作台（需已开启 portable_crafting 开关）。 */
    public static final class CraftingOpenPacket {
        public CraftingOpenPacket() {}

        public static void encode(CraftingOpenPacket msg, FriendlyByteBuf buf) {
            // 无数据
        }

        public static CraftingOpenPacket decode(FriendlyByteBuf buf) {
            return new CraftingOpenPacket();
        }

        public static void handle(CraftingOpenPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
                    if (!stats.isToggleActive("portable_crafting")) {
                        player.sendSystemMessage(Component.literal("未激活『内置工作台』属性，无法打开随身工作台"));
                        return;
                    }
                    NetworkHooks.openScreen(player,
                            new SimpleMenuProvider(
                                    (id, inv, p) -> new PortableCraftingMenu(id, inv),
                                    Component.translatable("container.crafting")));
                });
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** 客户端请求跨维度传送（需已开启 cross_dimension_teleport 开关）。 */
    public static final class CrossDimRequestPacket {
        private final String dimension;

        public CrossDimRequestPacket(String dimension) {
            this.dimension = dimension;
        }

        public static void encode(CrossDimRequestPacket msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.dimension == null ? "" : msg.dimension);
        }

        public static CrossDimRequestPacket decode(FriendlyByteBuf buf) {
            return new CrossDimRequestPacket(buf.readUtf());
        }

        public static void handle(CrossDimRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                // 兼容别名（overworld / nether / end）与完整注册名（modid:dimension）
                String dimArg = msg.dimension.trim().toLowerCase(Locale.ROOT);
                ResourceLocation rl = switch (dimArg) {
                    case "overworld" -> Level.OVERWORLD.location();
                    case "nether" -> Level.NETHER.location();
                    case "end" -> Level.END.location();
                    default -> dimArg.indexOf(':') < 0
                            ? new ResourceLocation("minecraft", dimArg)
                            : new ResourceLocation(dimArg);
                };
                ResourceKey<Level> targetKey = ResourceKey.create(Registries.DIMENSION, rl);
                PlayerStats stats = player.getCapability(PlayerStatsProvider.PLAYER_STATS).orElse(null);
                if (stats == null || !stats.isToggleActive("cross_dimension_teleport")) {
                    player.sendSystemMessage(Component.literal("未激活『跨维度传送』属性，无法跨维度传送"));
                    return;
                }
                if (player.level().dimension() == targetKey) {
                    player.sendSystemMessage(Component.literal("你已经在该维度"));
                    return;
                }
                ServerLevel target = getOrLoadLevel(player.getServer(), targetKey);
                if (target == null) {
                    player.sendSystemMessage(Component.literal("未知或无法加载的维度: " + rl + "（可用命令 /infstats crossdim 查看）"));
                    return;
                }
                // 强制跨维度传送：绕过外部「维度进入权限」（不触发可取消的 EntityTravelToDimensionEvent）
                TeleportUtil.forceTeleportTo(player, target, player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
                player.sendSystemMessage(Component.literal("§a已跨维度传送到 §f" + rl));
            });
            ctx.get().setPacketHandled(true);
        }

        /**
         * 获取指定维度；若维度已注册（存在于 levelKeys）但当前尚未加载到内存，
         * 则通过反射调用 MinecraftServer#loadLevel() 按需加载（该方法仅补充缺失维度，
         * 不会重复加载已加载的维度），之后再尝试获取。
         */
        private static ServerLevel getOrLoadLevel(MinecraftServer server, ResourceKey<Level> key) {
            ServerLevel level = server.getLevel(key);
            if (level != null) return level;
            if (server.levelKeys().contains(key)) {
                try {
                    Method loadLevel = MinecraftServer.class.getDeclaredMethod("loadLevel");
                    loadLevel.setAccessible(true);
                    loadLevel.invoke(server);
                } catch (Throwable ignored) {
                    // 加载失败则下方再次获取，仍可能为 null，由调用方给出提示
                }
                level = server.getLevel(key);
            }
            return level;
        }
    }

    /** 客户端向服务器请求当前世界已加载的维度列表。 */
    public static final class CrossDimListRequestPacket {
        public CrossDimListRequestPacket() {}

        public static void encode(CrossDimListRequestPacket msg, FriendlyByteBuf buf) {}

        public static CrossDimListRequestPacket decode(FriendlyByteBuf buf) {
            return new CrossDimListRequestPacket();
        }

        public static void handle(CrossDimListRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                MinecraftServer server = player.getServer();
                if (server == null) return;
                List<String> dims = new ArrayList<>();
                // 枚举所有已知维度（levelKeys 包含已注册但当前未加载的维度，比 getAllLevels 更全）
                for (ResourceKey<Level> key : server.levelKeys()) {
                    dims.add(key.location().toString());
                }
                // 主世界/下界/末地排前面，其余按字典序，方便查找
                dims.sort((a, b) -> {
                    boolean am = a.startsWith("minecraft:");
                    boolean bm = b.startsWith("minecraft:");
                    if (am != bm) return am ? -1 : 1;
                    return a.compareTo(b);
                });
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new CrossDimListPacket(dims));
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** 服务器将维度列表回传给客户端用于构建 UI。 */
    public static final class CrossDimListPacket {
        private final List<String> dimensions;

        public CrossDimListPacket(List<String> dimensions) {
            this.dimensions = dimensions;
        }

        public static void encode(CrossDimListPacket msg, FriendlyByteBuf buf) {
            buf.writeCollection(msg.dimensions, FriendlyByteBuf::writeUtf);
        }

        public static CrossDimListPacket decode(FriendlyByteBuf buf) {
            return new CrossDimListPacket(buf.readList(FriendlyByteBuf::readUtf));
        }

        public static void handle(CrossDimListPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> CrossDimScreen.setServerDimensions(msg.dimensions));
            ctx.get().setPacketHandled(true);
        }
    }

    // ========== 成就管理数据包类 ==========
    // 注意：AchievementInfo 已提升为顶层类（com.infinitestats.network.AchievementInfo），
    // 以避免 Forge ModuleClassLoader 对嵌套类二进制名（NetworkHandler$AchievementInfo）解析失败。

    /**
     * 请求成就列表数据包（客户端 → 服务器）
     */
    public static final class RequestAdvancementsPacket {
        public RequestAdvancementsPacket() {}

        public static void encode(RequestAdvancementsPacket msg, FriendlyByteBuf buf) {}

        public static RequestAdvancementsPacket decode(FriendlyByteBuf buf) {
            return new RequestAdvancementsPacket();
        }

        public static void handle(RequestAdvancementsPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                syncAdvancementsToClient(player);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /**
     * 同步成就列表数据包（服务器 → 客户端）
     */
    public static final class SyncAdvancementsPacket {
        private final List<AchievementInfo> achievements;

        public SyncAdvancementsPacket(List<AchievementInfo> achievements) {
            this.achievements = achievements;
        }

        public static void encode(SyncAdvancementsPacket msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.achievements.size());
            for (AchievementInfo info : msg.achievements) {
                AchievementInfo.encode(info, buf);
            }
        }

        public static SyncAdvancementsPacket decode(FriendlyByteBuf buf) {
            int count = buf.readVarInt();
            List<AchievementInfo> list = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                list.add(AchievementInfo.decode(buf));
            }
            return new SyncAdvancementsPacket(list);
        }

        public static void handle(SyncAdvancementsPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                // 存储到静态字段，由 AchievementManagerScreen 轮询读取
                pendingAdvancements = msg.achievements;
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /**
     * 切换成就状态数据包（客户端 → 服务器）
     */
    public static final class ToggleAdvancementPacket {
        private final String advancementId;
        private final boolean grant; // true=授予, false=撤销

        public ToggleAdvancementPacket(String advancementId, boolean grant) {
            this.advancementId = advancementId;
            this.grant = grant;
        }

        public static void encode(ToggleAdvancementPacket msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.advancementId);
            buf.writeBoolean(msg.grant);
        }

        public static ToggleAdvancementPacket decode(FriendlyByteBuf buf) {
            return new ToggleAdvancementPacket(buf.readUtf(), buf.readBoolean());
        }

        public static void handle(ToggleAdvancementPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;

                MinecraftServer server = player.getServer();
                if (server == null) return;

                ResourceLocation advId = ResourceLocation.tryParse(msg.advancementId);
                if (advId == null) return;

                Advancement advancement = server.getAdvancements().getAdvancement(advId);
                if (advancement == null) return;

                var playerAdvancements = player.getAdvancements();

                if (msg.grant) {
                    // 授予所有条件 → 完成成就
                    for (String criterion : advancement.getCriteria().keySet()) {
                        playerAdvancements.award(advancement, criterion);
                    }
                } else {
                    // 撤销所有条件 → 取消成就
                    for (String criterion : advancement.getCriteria().keySet()) {
                        playerAdvancements.revoke(advancement, criterion);
                    }
                }

                // 同步更新后的成就列表返回客户端
                syncAdvancementsToClient(player);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // ========== EMC 同步方法 ==========

    /**
     * 同步 EMC 数据到客户端
     */
    public static void syncEmcToClient(ServerPlayer player) {
        player.getCapability(EmcPlayerDataProvider.EMC_PLAYER_DATA).ifPresent(data -> {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new EmcSyncPacket(data.createSnapshot()));
        });
    }

    // ========== 成就管理同步方法 ==========

    /**
     * 构建成就信息列表并同步到客户端
     */
    public static void syncAdvancementsToClient(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        List<AchievementInfo> list = new ArrayList<>();
        for (Advancement adv : server.getAdvancements().getAllAdvancements()) {
            var display = adv.getDisplay();
            if (display == null) continue; // 跳过隐藏成就

            String id = adv.getId().toString();
            String name = display.getTitle().getString();
            String desc = display.getDescription().getString();
            String iconId = BuiltInRegistries.ITEM.getKey(display.getIcon().getItem()).toString();
            boolean completed = player.getAdvancements().getOrStartProgress(adv).isDone();

            list.add(new AchievementInfo(id, name, desc, iconId, completed));
        }

        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SyncAdvancementsPacket(list));
    }
}