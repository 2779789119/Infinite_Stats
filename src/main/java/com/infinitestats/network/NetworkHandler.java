package com.infinitestats.network;

import com.infinitestats.InfiniteStats;
import com.infinitestats.crafting.PortableCraftingMenu;
import com.infinitestats.emc.EmcDatabase;
import com.infinitestats.emc.EmcMenu;
import com.infinitestats.emc.EmcPlayerData;
import com.infinitestats.emc.EmcPlayerDataProvider;
import com.infinitestats.furnace.FurnaceFuelBufferMenu;
import com.infinitestats.furnace.FurnaceOrePriorityMenu;
import com.infinitestats.furnace.FurnaceProductBufferMenu;
import com.infinitestats.furnace.PlayerFurnaceData;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.ForgeRegistries;
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

        // ========== 随身工作台 / 熔炉 联动 Refined Storage ==========
        CHANNEL.registerMessage(packetId++, CraftingRecipeFillPacket.class,
                CraftingRecipeFillPacket::encode,
                CraftingRecipeFillPacket::decode,
                CraftingRecipeFillPacket::handle);

        // 网络库存查询（客户端 → 服务器）
        CHANNEL.registerMessage(packetId++, RequestNetworkItemsPacket.class,
                RequestNetworkItemsPacket::encode,
                RequestNetworkItemsPacket::decode,
                RequestNetworkItemsPacket::handle);
        // 网络库存同步（服务器 → 客户端）
        CHANNEL.registerMessage(packetId++, SyncNetworkItemsPacket.class,
                SyncNetworkItemsPacket::encode,
                SyncNetworkItemsPacket::decode,
                SyncNetworkItemsPacket::handle);

        CHANNEL.registerMessage(packetId++, CraftingOutputModePacket.class,
                CraftingOutputModePacket::encode,
                CraftingOutputModePacket::decode,
                CraftingOutputModePacket::handle);
        CHANNEL.registerMessage(packetId++, FurnaceRSRefillOrePacket.class,
                FurnaceRSRefillOrePacket::encode,
                FurnaceRSRefillOrePacket::decode,
                FurnaceRSRefillOrePacket::handle);
        CHANNEL.registerMessage(packetId++, FurnaceRSRefillFuelPacket.class,
                FurnaceRSRefillFuelPacket::encode,
                FurnaceRSRefillFuelPacket::decode,
                FurnaceRSRefillFuelPacket::handle);
        CHANNEL.registerMessage(packetId++, FurnaceRSDepositPacket.class,
                FurnaceRSDepositPacket::encode,
                FurnaceRSDepositPacket::decode,
                FurnaceRSDepositPacket::handle);

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

        // 一键卖出数据包（客户端 → 服务器）：把背包内所有可转化物品倾销成 EMC
        CHANNEL.registerMessage(packetId++, EmcSellAllPacket.class,
                EmcSellAllPacket::encode,
                EmcSellAllPacket::decode,
                EmcSellAllPacket::handle);

        // 单个槽位卖出数据包（客户端 → 服务器）：Shift+左键卖出指定槽位物品
        CHANNEL.registerMessage(packetId++, EmcSellSlotPacket.class,
                EmcSellSlotPacket::encode,
                EmcSellSlotPacket::decode,
                EmcSellSlotPacket::handle);

        // 自定义定价数据包（客户端 → 服务器）：设置物品的 EMC 值
        CHANNEL.registerMessage(packetId++, EmcSetPricePacket.class,
                EmcSetPricePacket::encode,
                EmcSetPricePacket::decode,
                EmcSetPricePacket::handle);

        // 收藏切换数据包（客户端 → 服务器）
        CHANNEL.registerMessage(packetId++, ToggleFavoritePacket.class,
                ToggleFavoritePacket::encode,
                ToggleFavoritePacket::decode,
                ToggleFavoritePacket::handle);

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

        // 成品仓菜单打开数据包（客户端 → 服务器）
        CHANNEL.registerMessage(packetId++, FurnaceProductOpenPacket.class,
                FurnaceProductOpenPacket::encode,
                FurnaceProductOpenPacket::decode,
                FurnaceProductOpenPacket::handle);

        // 成品仓：将成品存入 RS 网络（客户端 → 服务器）
        CHANNEL.registerMessage(packetId++, FurnaceProductRSDepositPacket.class,
                FurnaceProductRSDepositPacket::encode,
                FurnaceProductRSDepositPacket::decode,
                FurnaceProductRSDepositPacket::handle);

        // 随身熔炉速度调节数据包（客户端 → 服务器，消耗/返还可分配点数）
        CHANNEL.registerMessage(packetId++, FurnaceSpeedPacket.class,
                FurnaceSpeedPacket::encode,
                FurnaceSpeedPacket::decode,
                FurnaceSpeedPacket::handle);

        // 矿石优先顺序界面打开数据包（客户端 → 服务器）
        CHANNEL.registerMessage(packetId++, FurnaceOrePriorityOpenPacket.class,
                FurnaceOrePriorityOpenPacket::encode,
                FurnaceOrePriorityOpenPacket::decode,
                FurnaceOrePriorityOpenPacket::handle);

        // 矿石优先顺序请求数据包（客户端 → 服务器）：拉取当前优先顺序与可加入矿石
        CHANNEL.registerMessage(packetId++, FurnaceOrePriorityRequestPacket.class,
                FurnaceOrePriorityRequestPacket::encode,
                FurnaceOrePriorityRequestPacket::decode,
                FurnaceOrePriorityRequestPacket::handle);

        // 矿石优先顺序同步数据包（服务器 → 客户端）
        CHANNEL.registerMessage(packetId++, FurnaceOrePrioritySyncPacket.class,
                FurnaceOrePrioritySyncPacket::encode,
                FurnaceOrePrioritySyncPacket::decode,
                FurnaceOrePrioritySyncPacket::handle);

        // 矿石优先顺序更新数据包（客户端 → 服务器）：提交新的优先顺序列表
        CHANNEL.registerMessage(packetId++, FurnaceOrePriorityUpdatePacket.class,
                FurnaceOrePriorityUpdatePacket::encode,
                FurnaceOrePriorityUpdatePacket::decode,
                FurnaceOrePriorityUpdatePacket::handle);

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
                CompoundTag nbt = msg.snapshot.itemNbt.get(id);
                buf.writeBoolean(nbt != null && !nbt.isEmpty());
                if (nbt != null && !nbt.isEmpty()) {
                    buf.writeNbt(nbt);
                }
            }
        }

        public static EmcSyncPacket decode(FriendlyByteBuf buf) {
            long balance = buf.readVarLong();
            int count = buf.readVarInt();
            List<ResourceLocation> items = new ArrayList<>();
            Map<ResourceLocation, CompoundTag> nbtMap = new HashMap<>();
            for (int i = 0; i < count; i++) {
                ResourceLocation rl = ResourceLocation.tryParse(buf.readUtf());
                if (rl != null) {
                    items.add(rl);
                    boolean hasNbt = buf.readBoolean();
                    if (hasNbt) {
                        nbtMap.put(rl, buf.readNbt());
                    }
                }
            }
            return new EmcSyncPacket(new EmcPlayerData.EmcSnapshot(balance, items, nbtMap));
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

                player.getCapability(EmcPlayerDataProvider.EMC_PLAYER_DATA).ifPresent(data -> {
                    // 只卖出光标上实际持有的那一份（避免误删背包中其它同类物品且不同步其槽位）
                    int count = 0;
                    CompoundTag itemNbt = null;
                    // 手持（光标）中已经拖入学习槽的同类物品（光标在打开的菜单上，而非玩家背包）
                    var openMenu = player.containerMenu;
                    ItemStack carried = openMenu.getCarried();
                    // 仅比较物品类型（不比较 NBT），因为手册等物品有额外标签
                    if (!carried.isEmpty() && carried.getItem() == item) {
                        count += carried.getCount();
                        itemNbt = carried.getTag();
                        // 用实际手持（含 NBT）计算 EMC，避免附魔书等带 NBT 物品单价误判为 0
                        long emcValue = EmcDatabase.getEmc(carried);
                        if (emcValue <= 0) return;
                        openMenu.setCarried(ItemStack.EMPTY);
                        player.connection.send(new ClientboundContainerSetSlotPacket(
                                openMenu.containerId,
                                openMenu.getStateId(), -1, ItemStack.EMPTY));
                        openMenu.broadcastChanges();
                        // 学习物品并返还 数量×EMC，保留 NBT
                        data.learnAndConvert(itemId, emcValue * count, itemNbt);
                        syncEmcToClient(player);
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

                player.getCapability(EmcPlayerDataProvider.EMC_PLAYER_DATA).ifPresent(data -> {
                    if (!data.hasLearned(itemId)) return;

                    // 获取已存储的 NBT 数据（手册/附魔书等需保留标签的物品）
                    CompoundTag storedNbt = data.getItemNbt(itemId);

                    // 用与产出一致的模板堆（含已存 NBT）计算单价，避免书等带 NBT 物品 EMC 误判为 0
                    ItemStack template = new ItemStack(item);
                    if (storedNbt != null) {
                        template.setTag(storedNbt.copy());
                    }
                    long emcPerItem = EmcDatabase.getEmc(template);
                    if (emcPerItem <= 0) return;

                    int maxStack = item.getMaxStackSize();
                    if (msg.count < 0) {
                        // 快捷买入「买满」：用尽 EMC，按堆叠上限分批给入背包
                        long affordable = data.getEmcBalance() / emcPerItem;
                        while (affordable > 0) {
                            int give = (int) Math.min(affordable, maxStack);
                            long cost = (long) give * emcPerItem;
                            if (!data.consumeEmc(cost)) break;
                            ItemStack result = new ItemStack(item, give);
                            if (storedNbt != null) {
                                result.setTag(storedNbt.copy());
                            }
                            if (!player.getInventory().add(result)) {
                                data.addEmc(cost); // 背包已满，退还 EMC
                                break;
                            }
                            affordable -= give;
                        }
                    } else {
                        int maxGive = Math.min(msg.count, maxStack);
                        long totalCost = emcPerItem * maxGive;
                        if (!data.consumeEmc(totalCost)) {
                            // 余额不足，给尽可能多的
                            long affordable = data.getEmcBalance() / emcPerItem;
                            if (affordable <= 0) return;
                            maxGive = (int) Math.min(affordable, maxStack);
                            totalCost = emcPerItem * maxGive;
                            data.consumeEmc(totalCost);
                        }
                        if (maxGive > 0) {
                            ItemStack result = new ItemStack(item, maxGive);
                            if (storedNbt != null) {
                                result.setTag(storedNbt.copy());
                            }
                            if (!player.getInventory().add(result)) {
                                player.drop(result, false);
                            }
                        }
                    }

                    syncEmcToClient(player);
                    // 提取后刷新当前容器菜单（含背包槽位）使客户端立即显示
                    if (player.containerMenu != null) {
                        player.containerMenu.broadcastChanges();
                    }
                });
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /**
     * 一键卖出数据包（客户端 → 服务器）
     * 把玩家背包（主背包 + 快捷栏）中所有「已学且有 EMC 值」的物品倾销成 EMC。
     */
    public static final class EmcSellAllPacket {
        public EmcSellAllPacket() {}

        public static void encode(EmcSellAllPacket msg, FriendlyByteBuf buf) {
            // 无数据
        }

        public static EmcSellAllPacket decode(FriendlyByteBuf buf) {
            return new EmcSellAllPacket();
        }

        public static void handle(EmcSellAllPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;

                player.getCapability(EmcPlayerDataProvider.EMC_PLAYER_DATA).ifPresent(data -> {
                    long total = 0;
                    var inv = player.getInventory();
                    // 仅遍历主背包 + 快捷栏（0~35），不碰盔甲 / 副手，避免误卖穿戴装备
                    for (int i = 0; i < 36; i++) {
                        ItemStack s = inv.getItem(i);
                        if (s.isEmpty()) continue;
                        ResourceLocation id = BuiltInRegistries.ITEM.getKey(s.getItem());
                        if (!data.hasLearned(id)) continue;     // 只卖已学物品
                        long emc = EmcDatabase.getEmc(s);
                        if (emc <= 0) continue;
                        total += emc * s.getCount();
                        inv.setItem(i, ItemStack.EMPTY);
                    }
                    if (total > 0) {
                        data.addEmc(total);
                        syncEmcToClient(player);
                        player.displayClientMessage(
                                Component.literal("§a已将背包内可转化物品卖出，获得 §f" + total + " EMC"), false);
                    } else {
                        player.displayClientMessage(
                                Component.literal("§e背包中没有可卖出的已学物品"), false);
                    }
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
                // 打开界面时同步一次 EMC/已学数据，保证已学习列表立即有内容
                syncEmcToClient(player);
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

    /** 客户端请求打开成品仓（需已开启 portable_furnace 开关）。 */
    public static final class FurnaceProductOpenPacket {
        public FurnaceProductOpenPacket() {}

        public static void encode(FurnaceProductOpenPacket msg, FriendlyByteBuf buf) {
            // 无数据
        }

        public static FurnaceProductOpenPacket decode(FriendlyByteBuf buf) {
            return new FurnaceProductOpenPacket();
        }

        public static void handle(FurnaceProductOpenPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
                    if (!stats.isToggleActive("portable_furnace")) return;
                    NetworkHooks.openScreen(player,
                            new SimpleMenuProvider(
                                    (id, inv, p) -> new FurnaceProductBufferMenu(id, inv),
                                    Component.translatable("screen.infinitestats.furnace.product_buffer")));
                });
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** 成品仓：把成品储备箱中的成品存入 RS 网络。 */
    public static final class FurnaceProductRSDepositPacket {
        public FurnaceProductRSDepositPacket() {}

        public static void encode(FurnaceProductRSDepositPacket msg, FriendlyByteBuf buf) {
            // 无数据
        }

        public static FurnaceProductRSDepositPacket decode(FriendlyByteBuf buf) {
            return new FurnaceProductRSDepositPacket();
        }

        public static void handle(FurnaceProductRSDepositPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                if (player.containerMenu instanceof FurnaceProductBufferMenu m) m.depositToNetwork();
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

    /** 打开“矿石优先顺序”界面（客户端 → 服务器）。 */
    public static final class FurnaceOrePriorityOpenPacket {
        public FurnaceOrePriorityOpenPacket() {}

        public static void encode(FurnaceOrePriorityOpenPacket msg, FriendlyByteBuf buf) {}

        public static FurnaceOrePriorityOpenPacket decode(FriendlyByteBuf buf) {
            return new FurnaceOrePriorityOpenPacket();
        }

        public static void handle(FurnaceOrePriorityOpenPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
                    if (!stats.isToggleActive("portable_furnace")) return;
                    NetworkHooks.openScreen(player,
                            new SimpleMenuProvider(
                                    (windowId, inv, p) -> new FurnaceOrePriorityMenu(windowId, inv),
                                    Component.translatable("gui.infinitestats.furnace.ore_priority.title")),
                            BlockPos.ZERO);
                });
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** 客户端请求当前矿石优先顺序与可加入矿石列表（客户端 → 服务器）。 */
    public static final class FurnaceOrePriorityRequestPacket {
        public FurnaceOrePriorityRequestPacket() {}

        public static void encode(FurnaceOrePriorityRequestPacket msg, FriendlyByteBuf buf) {}

        public static FurnaceOrePriorityRequestPacket decode(FriendlyByteBuf buf) {
            return new FurnaceOrePriorityRequestPacket();
        }

        public static void handle(FurnaceOrePriorityRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
                PlayerFurnaceData furnace = stats.getFurnaceData();
                List<String> priority = furnace.getOrePriorityIds();
                List<String> available = computeAvailable(player, priority);
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                        new FurnaceOrePrioritySyncPacket(available, priority));
                });
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** 服务器将矿石优先顺序与可加入矿石列表同步到客户端（服务器 → 客户端）。 */
    public static final class FurnaceOrePrioritySyncPacket {
        public static class SyncData {
            public final List<String> available;
            public final List<String> priority;

            public SyncData(List<String> available, List<String> priority) {
                this.available = available;
                this.priority = priority;
            }
        }

        /** 最近一次同步的数据，供界面读取（仅单玩家单界面，使用静态字段足够）。 */
        public static volatile SyncData latest;
        /** 每次同步自增，界面据此判断是否需要重建控件。 */
        public static int version = 0;

        public final List<String> available;
        public final List<String> priority;

        public FurnaceOrePrioritySyncPacket(List<String> available, List<String> priority) {
            this.available = available;
            this.priority = priority;
        }

        public static void encode(FurnaceOrePrioritySyncPacket msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.priority.size());
            for (String id : msg.priority) buf.writeUtf(id);
            buf.writeVarInt(msg.available.size());
            for (String id : msg.available) buf.writeUtf(id);
        }

        public static FurnaceOrePrioritySyncPacket decode(FriendlyByteBuf buf) {
            int n = buf.readVarInt();
            List<String> priority = new ArrayList<>();
            for (int i = 0; i < n; i++) priority.add(buf.readUtf());
            int m = buf.readVarInt();
            List<String> available = new ArrayList<>();
            for (int i = 0; i < m; i++) available.add(buf.readUtf());
            return new FurnaceOrePrioritySyncPacket(available, priority);
        }

        public static void handle(FurnaceOrePrioritySyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                latest = new SyncData(msg.available, msg.priority);
                version++;
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** 客户端提交新的矿石优先顺序列表（客户端 → 服务器）。 */
    public static final class FurnaceOrePriorityUpdatePacket {
        public final List<String> priority;

        public FurnaceOrePriorityUpdatePacket(List<String> priority) {
            this.priority = priority;
        }

        public static void encode(FurnaceOrePriorityUpdatePacket msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.priority.size());
            for (String id : msg.priority) buf.writeUtf(id);
        }

        public static FurnaceOrePriorityUpdatePacket decode(FriendlyByteBuf buf) {
            int n = buf.readVarInt();
            List<String> priority = new ArrayList<>();
            for (int i = 0; i < n; i++) priority.add(buf.readUtf());
            return new FurnaceOrePriorityUpdatePacket(priority);
        }

        public static void handle(FurnaceOrePriorityUpdatePacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
                PlayerFurnaceData furnace = stats.getFurnaceData();
                furnace.setOrePriorityIds(msg.priority);
                List<String> available = computeAvailable(player, furnace.getOrePriorityIds());
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                        new FurnaceOrePrioritySyncPacket(available, furnace.getOrePriorityIds()));
                });
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /**
     * 计算“可加入的矿石”列表：游戏内所有拥有熔炼/高炉配方的物品（排除已在优先列表中的）。
     * 不再依赖矿石储备箱是否已有存货，保证界面永远有内容可选。
     */
    private static List<String> computeAvailable(ServerPlayer player, List<String> priority) {
        Set<String> inPrio = new HashSet<>(priority);
        Set<String> ids = new LinkedHashSet<>();
        RecipeManager rm = player.level().getRecipeManager();
        collect(rm, RecipeType.SMELTING, ids);
        collect(rm, RecipeType.BLASTING, ids);
        List<String> available = new ArrayList<>();
        for (String id : ids) {
            if (!inPrio.contains(id)) available.add(id);
        }
        return available;
    }

    private static void collect(RecipeManager rm, RecipeType<?> type, Set<String> ids) {
        for (Recipe<?> recipe : rm.getRecipes()) {
            if (recipe.getType() == type) {
                for (Ingredient ing : recipe.getIngredients()) {
                    for (ItemStack s : ing.getItems()) {
                        if (!s.isEmpty()) ids.add(ForgeRegistries.ITEMS.getKey(s.getItem()).toString());
                    }
                }
            }
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

    // ========== 随身工作台 / 熔炉 联动 Refined Storage ==========

    /** 工作台：JEI 一键转移配方时，按给定 3x3 材料布局从背包/存储网络补充材料。 */
    public static final class CraftingRecipeFillPacket {
        private final Ingredient[] ingredients = new Ingredient[9];

        public CraftingRecipeFillPacket(Ingredient[] grid) {
            System.arraycopy(grid, 0, ingredients, 0, 9);
        }

        public static void encode(CraftingRecipeFillPacket msg, FriendlyByteBuf buf) {
            for (int i = 0; i < 9; i++) {
                msg.ingredients[i].toNetwork(buf);
            }
        }

        public static CraftingRecipeFillPacket decode(FriendlyByteBuf buf) {
            CraftingRecipeFillPacket msg = new CraftingRecipeFillPacket(new Ingredient[9]);
            for (int i = 0; i < 9; i++) {
                msg.ingredients[i] = Ingredient.fromNetwork(buf);
            }
            return msg;
        }

        public static void handle(CraftingRecipeFillPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                if (player.containerMenu instanceof PortableCraftingMenu m) {
                    m.fillGridFromIngredients(msg.ingredients);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** 工作台：切换合成成品的去向（背包 / 存储空间）。 */
    public static final class CraftingOutputModePacket {
        public CraftingOutputModePacket() {
        }

        public static void encode(CraftingOutputModePacket msg, FriendlyByteBuf buf) {
        }

        public static CraftingOutputModePacket decode(FriendlyByteBuf buf) {
            return new CraftingOutputModePacket();
        }

        public static void handle(CraftingOutputModePacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                if (player.containerMenu instanceof PortableCraftingMenu m) m.toggleOutputToStorage();
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** 熔炉：从 RS 网络提取可熔炼矿物补入输入槽。 */
    public static final class FurnaceRSRefillOrePacket {
        public FurnaceRSRefillOrePacket() {
        }

        public static void encode(FurnaceRSRefillOrePacket msg, FriendlyByteBuf buf) {
        }

        public static FurnaceRSRefillOrePacket decode(FriendlyByteBuf buf) {
            return new FurnaceRSRefillOrePacket();
        }

        public static void handle(FurnaceRSRefillOrePacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                if (player.containerMenu instanceof PortableFurnaceMenu m) m.refillOreFromNetwork();
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** 熔炉：从 RS 网络提取燃料补入燃料槽。 */
    public static final class FurnaceRSRefillFuelPacket {
        public FurnaceRSRefillFuelPacket() {
        }

        public static void encode(FurnaceRSRefillFuelPacket msg, FriendlyByteBuf buf) {
        }

        public static FurnaceRSRefillFuelPacket decode(FriendlyByteBuf buf) {
            return new FurnaceRSRefillFuelPacket();
        }

        public static void handle(FurnaceRSRefillFuelPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                if (player.containerMenu instanceof PortableFurnaceMenu m) m.refillFuelFromNetwork();
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /**
     * 收藏切换：切换属性的收藏状态（客户端 → 服务器）。
     */
    public static final class ToggleFavoritePacket {
        private final String statId;

        public ToggleFavoritePacket(String statId) { this.statId = statId; }

        public static void encode(ToggleFavoritePacket msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.statId);
        }

        public static ToggleFavoritePacket decode(FriendlyByteBuf buf) {
            return new ToggleFavoritePacket(buf.readUtf());
        }

        public static void handle(ToggleFavoritePacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(
                        stats -> stats.toggleFavorite(msg.statId));
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** 熔炉：将输出槽的成品存入 RS 网络。 */
    public static final class FurnaceRSDepositPacket {
        public FurnaceRSDepositPacket() {
        }

        public static void encode(FurnaceRSDepositPacket msg, FriendlyByteBuf buf) {
        }

        public static FurnaceRSDepositPacket decode(FriendlyByteBuf buf) {
            return new FurnaceRSDepositPacket();
        }

        public static void handle(FurnaceRSDepositPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                if (player.containerMenu instanceof PortableFurnaceMenu m) m.depositProductsToNetwork();
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // ══════════ 网络库存查询 / 同步 ══════════

    /**
     * 客户端 → 服务端：请求当前可用的存储网络物品列表。
     */
    public static final class RequestNetworkItemsPacket {
        public RequestNetworkItemsPacket() {}

        public static void encode(RequestNetworkItemsPacket msg, FriendlyByteBuf buf) {}
        public static RequestNetworkItemsPacket decode(FriendlyByteBuf buf) {
            return new RequestNetworkItemsPacket();
        }

        public static void handle(RequestNetworkItemsPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                java.util.List<com.infinitestats.compat.NetworkHandle> nets =
                        com.infinitestats.compat.NetworkIO.getNetworks(player);
                java.util.List<ItemStack> items = com.infinitestats.compat.NetworkIO.listItems(nets);
                java.util.List<String> ids = new java.util.ArrayList<>();
                for (ItemStack s : items) {
                    if (!s.isEmpty()) ids.add(
                            net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                                    s.getItem()).toString());
                }
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                        new SyncNetworkItemsPacket(ids));
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /**
     * 服务端 → 客户端：同步当前存储网络的物品 ID 列表。
     */
    public static final class SyncNetworkItemsPacket {
        private final java.util.List<String> itemIds;

        public SyncNetworkItemsPacket(java.util.List<String> itemIds) { this.itemIds = itemIds; }

        public static void encode(SyncNetworkItemsPacket msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.itemIds.size());
            for (String id : msg.itemIds) buf.writeUtf(id);
        }

        public static SyncNetworkItemsPacket decode(FriendlyByteBuf buf) {
            int count = buf.readVarInt();
            java.util.List<String> ids = new java.util.ArrayList<>(count);
            for (int i = 0; i < count; i++) ids.add(buf.readUtf());
            return new SyncNetworkItemsPacket(ids);
        }

        public static void handle(SyncNetworkItemsPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                com.infinitestats.compat.jei.PortableCraftingRecipeTransferHandler
                        .cacheNetworkItems(msg.itemIds);
            });
            ctx.get().setPacketHandled(true);
        }

        public java.util.List<String> getItemIds() { return itemIds; }
    }

    /**
     * 单个槽位卖出数据包（客户端 → 服务器）
     * Shift+左键点击背包槽位 → 卖出该槽位物品换取 EMC
     */
    public static final class EmcSellSlotPacket {
        private final int slotIndex;

        public EmcSellSlotPacket(int slotIndex) {
            this.slotIndex = slotIndex;
        }

        public static void encode(EmcSellSlotPacket msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.slotIndex);
        }

        public static EmcSellSlotPacket decode(FriendlyByteBuf buf) {
            return new EmcSellSlotPacket(buf.readVarInt());
        }

        public static void handle(EmcSellSlotPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;

                var menu = player.containerMenu;
                if (!(menu instanceof EmcMenu)) return;

                int slot = msg.slotIndex;
                if (slot < 0 || slot >= menu.slots.size()) return;

                ItemStack stack = menu.slots.get(slot).getItem();
                if (stack.isEmpty()) return;

                long emcValue = EmcDatabase.getEmc(stack);
                if (emcValue <= 0) return;

                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
                int count = stack.getCount();
                long totalEmc = emcValue * count;

                player.getCapability(EmcPlayerDataProvider.EMC_PLAYER_DATA).ifPresent(data -> {
                    if (!data.hasLearned(itemId)) {
                        data.learnAndConvert(itemId, totalEmc, stack.getTag());
                    } else {
                        // 更新 NBT（可能放入了不同版本的手册）
                        CompoundTag nbt = stack.getTag();
                        if (nbt != null && !nbt.isEmpty()) {
                            data.learnItem(itemId, nbt);
                        }
                        data.addEmc(totalEmc);
                    }
                    syncEmcToClient(player);
                    player.displayClientMessage(
                            Component.translatable("message.infinitestats.emc.converted",
                                    stack.getHoverName(), totalEmc), false);
                });

                menu.slots.get(slot).set(ItemStack.EMPTY);
                menu.broadcastChanges();
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /**
     * 自定义定价数据包（客户端 → 服务器）
     * 给指定物品设置自定义 EMC 值（0 表示删除自定义值，恢复自动计算）
     */
    public static final class EmcSetPricePacket {
        private final String itemId;
        private final long emc;

        public EmcSetPricePacket(String itemId, long emc) {
            this.itemId = itemId;
            this.emc = emc;
        }

        public static void encode(EmcSetPricePacket msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.itemId);
            buf.writeVarLong(msg.emc);
        }

        public static EmcSetPricePacket decode(FriendlyByteBuf buf) {
            return new EmcSetPricePacket(buf.readUtf(), buf.readVarLong());
        }

        public static void handle(EmcSetPricePacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;

                ResourceLocation itemId = ResourceLocation.tryParse(msg.itemId);
                if (itemId == null) return;

                Item item = BuiltInRegistries.ITEM.get(itemId);
                if (item == null) return;

                EmcDatabase.setCustomEmc(itemId, msg.emc);
                player.getCapability(EmcPlayerDataProvider.EMC_PLAYER_DATA).ifPresent(data -> {
                    if (msg.emc > 0) {
                        data.learnItem(itemId);
                        syncEmcToClient(player);
                    }
                });
                if (msg.emc > 0) {
                    player.displayClientMessage(
                            Component.translatable("message.infinitestats.emc.priced",
                                    new ItemStack(item).getHoverName(), msg.emc), false);
                } else {
                    player.displayClientMessage(
                            Component.translatable("message.infinitestats.emc.price_removed",
                                    new ItemStack(item).getHoverName()), false);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }
}