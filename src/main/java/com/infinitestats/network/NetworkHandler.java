package com.infinitestats.network;

import com.infinitestats.InfiniteStats;
import com.infinitestats.crafting.PortableAnvil;
import com.infinitestats.crafting.PortableCraftingMenu;
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
import com.infinitestats.util.EntityPanelToggles;
import com.infinitestats.util.EntityPanelWand;
import com.infinitestats.util.TeleportUtil;
import com.infinitestats.handler.HandlerRegistry;
import com.infinitestats.client.CrossDimScreen;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.*;
import java.lang.reflect.Method;
import java.util.function.Supplier;

/**
 * 网络处理系统 - 高效的数据包设计
 * 使用简洁的协议减少带宽占用
 */
public final class NetworkHandler {

    // 8：新增 ExternalStatsSyncPacket（服务端下发外部属性清单）
    // 11：物品编辑器新增特殊标签界面 → 新增 EditItemTagsPacket
    // 12：移除内置 EMC 转化桌 → 删掉 8 个 EMC 数据包（包 id 全部前移，双端必须一起更新）
    private static final String PROTOCOL_VERSION = "12";
    public static final SimpleChannel CHANNEL = new SimpleChannel(
            ResourceLocation.fromNamespaceAndPath(InfiniteStats.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    /** 待处理的成就同步数据（由 SyncAdvancementsPacket 写入，AchievementManagerScreen 读取） */
    public static List<AchievementInfo> pendingAdvancements = null;
    /** 服务端下发的「本玩家能否改成就」与「被跳过的技术成就数」。 */
    public static boolean pendingAdvancementsCanEdit = false;
    public static int pendingAdvancementsSkipped = 0;
    /** 每次收到成就列表自增，界面据此判断是否需要重读。 */
    public static int advancementsVersion = 0;

    // ══════════ 存储网络物品缓存（供 JEI 一键转移校验）══════════
    // 缓存必须放在与 JEI 无关的类里：客户端界面在未安装 JEI 时也会访问，
    // 若缓存方法挂在实现 JEI 接口的类上，类加载会因缺少 JEI 而抛 NoClassDefFoundError。

    private static final long NETWORK_ITEM_CACHE_TTL_MS = 5000;
    private static final java.util.Set<String> NETWORK_ITEM_IDS =
            java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    private static volatile long networkItemCacheExpiresAt;

    /** 请求刷新存储网络物品列表（仅在安装 JEI 时才有意义，避免无谓流量）。 */
    public static void requestNetworkItems() {
        if (!net.neoforged.fml.ModList.get().isLoaded("jei")) return;
        CHANNEL.sendToServer(new RequestNetworkItemsPacket());
    }

    /** 缓存服务器同步的网络物品 ID 列表。 */
    public static void cacheNetworkItems(java.util.List<String> ids) {
        synchronized (NETWORK_ITEM_IDS) {
            NETWORK_ITEM_IDS.clear();
            NETWORK_ITEM_IDS.addAll(ids);
            networkItemCacheExpiresAt = System.currentTimeMillis() + NETWORK_ITEM_CACHE_TTL_MS;
        }
    }

    /** 返回当前有效的网络物品 ID 快照；缓存过期或无数据时返回空列表。 */
    public static java.util.List<String> getValidNetworkItemIds() {
        if (System.currentTimeMillis() >= networkItemCacheExpiresAt) return java.util.List.of();
        synchronized (NETWORK_ITEM_IDS) {
            return new java.util.ArrayList<>(NETWORK_ITEM_IDS);
        }
    }

    /**
     * 注册所有数据包
     */
    /**
     * 待处理的「已激活传送石碑」列表（由 {@link WaystoneListPacket} 写入，{@code WaypointScreen} 读取）。
     * 没装传送石碑时一直是空表。
     */
    public static volatile List<WaystoneEntry> pendingWaystones = List.of();
    /** 上面那份列表的版本号：每次同步自增，界面据此重建列表。 */
    public static volatile long waystoneListVersion = 0;

    /** 把「已激活的石碑」推给该玩家（由 {@link WaystoneListRequestPacket} 触发）。 */
    public static void sendWaystones(ServerPlayer player, List<WaystoneEntry> waystones) {
        CHANNEL.sendToPlayer(player, new WaystoneListPacket(waystones));
    }

    /**
     * 注册所有数据包
     */
    public static void register(RegisterPayloadHandlersEvent event) {
        CHANNEL.bind(event.registrar(PROTOCOL_VERSION));
        // ========== 传送点面板 × 传送石碑（Waystones）联动 ==========
        // 列表请求（客户端 → 服务器）
        CHANNEL.registerMessage(packetId++, WaystoneListRequestPacket.class,
                WaystoneListRequestPacket::encode,
                WaystoneListRequestPacket::decode,
                WaystoneListRequestPacket::handle);
        // 列表同步（服务器 → 客户端）
        CHANNEL.registerMessage(packetId++, WaystoneListPacket.class,
                WaystoneListPacket::encode,
                WaystoneListPacket::decode,
                WaystoneListPacket::handle);
        // 传送请求（客户端 → 服务器，免费无冷却）
        CHANNEL.registerMessage(packetId++, WaystoneTeleportPacket.class,
                WaystoneTeleportPacket::encode,
                WaystoneTeleportPacket::decode,
                WaystoneTeleportPacket::handle);

        // 拔刀剑编辑（客户端 → 服务器）
        CHANNEL.registerMessage(packetId++, EditItemBladePacket.class,
                EditItemBladePacket::encode,
                EditItemBladePacket::decode,
                EditItemBladePacket::handle);

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

        // 物品特殊标签编辑数据包（客户端 → 服务器）
        CHANNEL.registerMessage(packetId++, EditItemTagsPacket.class,
                EditItemTagsPacket::encode,
                EditItemTagsPacket::decode,
                EditItemTagsPacket::handle);

        // 收藏切换数据包（客户端 → 服务器）
        CHANNEL.registerMessage(packetId++, ToggleFavoritePacket.class,
                ToggleFavoritePacket::encode,
                ToggleFavoritePacket::decode,
                ToggleFavoritePacket::handle);

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

        // 随身铁砧菜单打开数据包（客户端 → 服务器）
        CHANNEL.registerMessage(packetId++, AnvilOpenPacket.class,
                AnvilOpenPacket::encode,
                AnvilOpenPacket::decode,
                AnvilOpenPacket::handle);

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
        CHANNEL.registerMessage(packetId++, BatchAdvancementPacket.class,
                BatchAdvancementPacket::encode,
                BatchAdvancementPacket::decode,
                BatchAdvancementPacket::handle);

        // ========== 传送点管理数据包 ==========

        // 传送点操作（客户端 → 服务器）
        CHANNEL.registerMessage(packetId++, WaypointActionPacket.class,
                WaypointActionPacket::encode,
                WaypointActionPacket::decode,
                WaypointActionPacket::handle);

        // ========== 天气控制 / 区块强加载数据包 ==========

        // 天气切换（客户端 → 服务器）
        CHANNEL.registerMessage(packetId++, WeatherControlPacket.class,
                WeatherControlPacket::encode,
                WeatherControlPacket::decode,
                WeatherControlPacket::handle);

        // 区块强加载动作：加载 / 卸载 / 请求列表（客户端 → 服务器）
        CHANNEL.registerMessage(packetId++, ChunkLoaderActionPacket.class,
                ChunkLoaderActionPacket::encode,
                ChunkLoaderActionPacket::decode,
                ChunkLoaderActionPacket::handle);

        // 区块强加载列表同步（服务器 → 客户端）
        CHANNEL.registerMessage(packetId++, ChunkLoaderSyncPacket.class,
                ChunkLoaderSyncPacket::encode,
                ChunkLoaderSyncPacket::decode,
                ChunkLoaderSyncPacket::handle);

        // 自动入库白 / 黑名单更新（客户端 → 服务器）
        CHANNEL.registerMessage(packetId++, UpdateDepositFilterPacket.class,
                UpdateDepositFilterPacket::encode,
                UpdateDepositFilterPacket::decode,
                UpdateDepositFilterPacket::handle);

        // 按功能的存储优先级更新（客户端 → 服务器）
        CHANNEL.registerMessage(packetId++, SetNetworkPriorityPacket.class,
                SetNetworkPriorityPacket::encode,
                SetNetworkPriorityPacket::decode,
                SetNetworkPriorityPacket::handle);

        // 请求打开随身站点（客户端 → 服务器）
        CHANNEL.registerMessage(packetId++, PortableStationOpenPacket.class,
                PortableStationOpenPacket::encode,
                PortableStationOpenPacket::decode,
                PortableStationOpenPacket::handle);

        // 随身进阶高级附魔台动作：附魔 / 修复 / 回收（客户端 → 服务器）
        CHANNEL.registerMessage(packetId++, InfuserActionPacket.class,
                InfuserActionPacket::encode,
                InfuserActionPacket::decode,
                InfuserActionPacket::handle);

        // 生物面板：服务端下发打开 / 刷新（服务端 → 客户端）
        CHANNEL.registerMessage(packetId++, OpenEntityPanelPacket.class,
                OpenEntityPanelPacket::encode,
                OpenEntityPanelPacket::decode,
                OpenEntityPanelPacket::handle);

        // 连锁挖掘设置：同步（服务端 → 客户端）与提交（客户端 → 服务端）
        CHANNEL.registerMessage(packetId++, VeinMinerConfigPacket.class,
                VeinMinerConfigPacket::encode,
                VeinMinerConfigPacket::decode,
                VeinMinerConfigPacket::handle);
        CHANNEL.registerMessage(packetId++, UpdateVeinMinerConfigPacket.class,
                UpdateVeinMinerConfigPacket::encode,
                UpdateVeinMinerConfigPacket::decode,
                UpdateVeinMinerConfigPacket::handle);

        // 生物面板：切换开关 / 改属性基础值（客户端 → 服务器）
        CHANNEL.registerMessage(packetId++, EntityPanelActionPacket.class,
                EntityPanelActionPacket::encode,
                EntityPanelActionPacket::decode,
                EntityPanelActionPacket::handle);
        CHANNEL.registerMessage(packetId++, EntityPanelAttributePacket.class,
                EntityPanelAttributePacket::encode,
                EntityPanelAttributePacket::decode,
                EntityPanelAttributePacket::handle);
        // 生物面板：动作 / 移除单个效果 / 附近列表点选 / 属性锁（客户端 → 服务端）
        CHANNEL.registerMessage(packetId++, EntityPanelCommandPacket.class,
                EntityPanelCommandPacket::encode,
                EntityPanelCommandPacket::decode,
                EntityPanelCommandPacket::handle);
        CHANNEL.registerMessage(packetId++, EntityPanelEffectPacket.class,
                EntityPanelEffectPacket::encode,
                EntityPanelEffectPacket::decode,
                EntityPanelEffectPacket::handle);
        CHANNEL.registerMessage(packetId++, EntityPanelRequestPacket.class,
                EntityPanelRequestPacket::encode,
                EntityPanelRequestPacket::decode,
                EntityPanelRequestPacket::handle);
        CHANNEL.registerMessage(packetId++, EntityPanelLockPacket.class,
                EntityPanelLockPacket::encode,
                EntityPanelLockPacket::decode,
                EntityPanelLockPacket::handle);
        CHANNEL.registerMessage(packetId++, EntityPanelLockSyncPacket.class,
                EntityPanelLockSyncPacket::encode,
                EntityPanelLockSyncPacket::decode,
                EntityPanelLockSyncPacket::handle);

        // 外部属性列表下发（服务器 → 客户端，登录 / 热重扫各一次）
        CHANNEL.registerMessage(packetId++, ExternalStatsSyncPacket.class,
                ExternalStatsSyncPacket::encode,
                ExternalStatsSyncPacket::decode,
                ExternalStatsSyncPacket::handle);

        // 矿透：配置下发（服务器 → 客户端）与提交（客户端 → 服务器）
        CHANNEL.registerMessage(packetId++, OreVisionConfigPacket.class,
                OreVisionConfigPacket::encode,
                OreVisionConfigPacket::decode,
                OreVisionConfigPacket::handle);
        CHANNEL.registerMessage(packetId++, UpdateOreVisionConfigPacket.class,
                UpdateOreVisionConfigPacket::encode,
                UpdateOreVisionConfigPacket::decode,
                UpdateOreVisionConfigPacket::handle);
    }

    /**
     * 下发某个玩家的矿透设置（登录、换维度、设置变更后调用）。
     * <p>
     * 不塞进 {@code syncToClient}：那个包在每个加点操作后都会发，而这几项设置几乎不变。
     */
    /**
     * 下发服务端的外部属性清单（登录、换维度、热重扫后各一次）。
     * <p>
     * 客户端靠它覆盖自己扫出来的结果：两端的黑名单 / 覆盖项可能不同，必须以服务端为准。
     */
    public static void syncExternalStats(ServerPlayer player) {
        CHANNEL.sendToPlayer(player,
                new ExternalStatsSyncPacket(com.infinitestats.stats.StatType.externalSnapshot()));
    }

    public static void syncOreVisionConfig(ServerPlayer player) {
        PlayerStatsProvider.of(player).ifPresent(stats ->
                CHANNEL.sendToPlayer(player, new OreVisionConfigPacket(stats.getOreVisionConfig())));
    }

    /**
     * 客户端更新「自动入库」的白 / 黑名单过滤（物品 ID 集合 + 模式）。
     * 服务端会剔除非法 ID 并限制条目数，避免客户端塞入垃圾数据。
     */
    public static final class UpdateDepositFilterPacket {
        private static final int MAX_ENTRIES = 4096;
        private final boolean useWhitelist;
        private final Set<String> itemIds;

        public UpdateDepositFilterPacket(boolean useWhitelist, Set<String> itemIds) {
            this.useWhitelist = useWhitelist;
            this.itemIds = itemIds == null ? Set.of() : new HashSet<>(itemIds);
        }

        public static void encode(UpdateDepositFilterPacket msg, FriendlyByteBuf buf) {
            buf.writeBoolean(msg.useWhitelist);
            buf.writeVarInt(msg.itemIds.size());
            for (String id : msg.itemIds) buf.writeUtf(id);
        }

        public static UpdateDepositFilterPacket decode(FriendlyByteBuf buf) {
            boolean useWhitelist = buf.readBoolean();
            int count = buf.readVarInt();
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < count; i++) ids.add(buf.readUtf());
            return new UpdateDepositFilterPacket(useWhitelist, ids);
        }

        public static void handle(UpdateDepositFilterPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                PlayerStatsProvider.of(player).ifPresent(stats -> {
                    // 只挡「被功能开关整条关掉」的情况：没加点的玩家也能先把名单配好
                    if (stats.isStatInactive("auto_deposit")) return;
                    stats.setAutoDepositFilterList(normalizeFilterIds(msg.itemIds), msg.useWhitelist);
                    syncToClient(player);
                });
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** 剔除非法物品 ID（只接受 {@code namespace:path} 形式）并限制条目数。 */
    private static Set<String> normalizeFilterIds(Set<String> raw) {
        Set<String> out = new HashSet<>();
        if (raw == null) return out;
        for (String id : raw) {
            if (id == null) continue;
            ResourceLocation parsed = ResourceLocation.tryParse(id);
            if (parsed != null) out.add(parsed.toString());
            if (out.size() >= UpdateDepositFilterPacket.MAX_ENTRIES) break;
        }
        return out;
    }

    /** 受支持的存储键（与 NetworkIO / 配置 {@code NetworkPriority.networkPriority} 口径一致）。 */
    private static final List<String> STORAGE_KEYS = List.of("RS", "AE2", "TOMS", "BACKPACK", "BD");

    /**
     * 客户端更新**某个功能**的存储优先级（作用域 + 存储键顺序，靠前者优先）。
     * 服务端会校验作用域与键名，并把缺失的键按配置顺序补齐，避免顺序残缺。
     * 自动入库作用域额外要求已解锁 {@code auto_deposit}；其余作用域由各自界面把关。
     */
    public static final class SetNetworkPriorityPacket {
        private final String scope;
        private final List<String> order;

        public SetNetworkPriorityPacket(String scope, List<String> order) {
            this.scope = scope == null ? "" : scope;
            this.order = order == null ? List.of() : new ArrayList<>(order);
        }

        public static void encode(SetNetworkPriorityPacket msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.scope);
            buf.writeVarInt(msg.order.size());
            for (String key : msg.order) buf.writeUtf(key);
        }

        public static SetNetworkPriorityPacket decode(FriendlyByteBuf buf) {
            String scope = buf.readUtf();
            int n = buf.readVarInt();
            List<String> list = new ArrayList<>(Math.max(0, Math.min(n, 32)));
            for (int i = 0; i < n; i++) list.add(buf.readUtf(16));
            return new SetNetworkPriorityPacket(scope, list);
        }

        public static void handle(SetNetworkPriorityPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                if (!PlayerStats.NETWORK_SCOPES.contains(msg.scope)) return;
                PlayerStatsProvider.of(player).ifPresent(stats -> {
                    // 只挡「被功能开关整条关掉」的情况：没加点的玩家也能先把优先级配好
                    if (PlayerStats.SCOPE_AUTO_DEPOSIT.equals(msg.scope)
                            && stats.isStatInactive("auto_deposit")) {
                        return;
                    }
                    stats.setNetworkPriority(msg.scope, normalizeNetworkOrder(msg.order));
                    syncToClient(player);
                });
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** 校验存储键、剔除重复与非法值，并把缺失的键按配置顺序补到末尾。 */
    private static List<String> normalizeNetworkOrder(List<String> raw) {
        List<String> out = new ArrayList<>();
        if (raw != null) {
            for (String key : raw) {
                if (key == null) continue;
                String k = key.trim().toUpperCase(Locale.ROOT);
                if (STORAGE_KEYS.contains(k) && !out.contains(k)) out.add(k);
            }
        }
        List<? extends String> cfg = Config.NETWORK_PRIORITY.get();
        if (cfg != null) {
            for (String key : cfg) {
                if (key == null) continue;
                String k = key.trim().toUpperCase(Locale.ROOT);
                if (STORAGE_KEYS.contains(k) && !out.contains(k)) out.add(k);
            }
        }
        for (String k : STORAGE_KEYS) {
            if (!out.contains(k)) out.add(k);
        }
        return out;
    }

    /**
     * 生物面板：请求给玩家打开（或刷新）某个生物的面板。
     * <p>
     * 由 {@code StatEventHandler#onEntityInteract} 在「手持生物观察杖右键生物」时调用。
     */
    public static void sendOpenEntityPanel(ServerPlayer player, Entity target) {
        if (player == null || target == null) return;
        sendEntityPanelState(player, target, Config.ALLOW_ENTITY_EDITING.get());
    }

    /**
     * 按当前真实状态回包（打开面板时、切换开关后、校验失败后都用它把客户端拉回权威状态）。
     */
    public static void sendEntityPanelState(ServerPlayer player, Entity target, boolean editable) {
        CHANNEL.sendToPlayer(player, new OpenEntityPanelPacket(target.getId(),
                EntityPanelToggles.applicableMask(target),
                EntityPanelToggles.enabledMask(target),
                editable));
        // 锁表跟着一起下发：客户端行尾的「基 / 终」要始终是服务端权威状态
        sendEntityPanelLocks(player, target);
    }

    /** 生物面板「改生物」的距离基数兜底值（格）。 */
    private static final double EDIT_RANGE_BASE = 16.0D;

    /** 诊断开关：{@code -Dinfinitestats.debugEntityPanel=true} 时，每次「改不动」都往日志打一行原因。 */
    private static final boolean DEBUG_ENTITY_PANEL =
            Boolean.getBoolean("infinitestats.debugEntityPanel");

    /**
     * 「生物面板」能不能改这个生物 —— 开关切换与属性编辑共用这一条链：
     * 身上有观察杖 + 配置允许 + 目标在可达范围内 + 属性已激活。
     *
     * <h2>距离为什么按碰撞箱算、还要跟着交互距离属性走</h2>
     * 早先直接拿「生物原点（脚底）到玩家的距离 ≤ 8 格」当闸门，结果两类正常情况被误判成「改不了」：
     * 巨型 / 飞行生物的本体原点可能在十几格外；而加了「实体交互距离」属性后能右键开出面板，
     * 却因为这个 8 格限制一次都改不动。现在按「碰撞箱 → 玩家眼睛」的距离算，
     * 上限取 {@link #EDIT_RANGE_BASE} 与「玩家交互距离 + 4 格」的较大者。
     * 真正的闸门仍是配置 {@code EntityPanel.allowEntityEditing}。
     */
    public static boolean canEditEntity(ServerPlayer player, Entity target) {
        // 只要求「身上有观察杖」，不要求「此刻正拿在手上」：面板本来就是手持它右键打开的，
        // 界面打开期间玩家换不了手；卡「必须还拿在手里」会在手持物品被别的模组改 NBT 时误判
        boolean held = EntityPanelWand.hasWand(player);
        boolean config = Config.ALLOW_ENTITY_EDITING.get();
        boolean active = PlayerStatsProvider.of(player)
                .map(stats -> stats.isToggleActive(EntityPanelWand.STAT_ID))
                .orElse(false);

        double reach = EDIT_RANGE_BASE;
        var reachAttribute = player.getAttribute(
                net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE);
        if (reachAttribute != null) reach = Math.max(reach, reachAttribute.getValue() + 4.0D);

        double distanceSqr = target.getBoundingBox().distanceToSqr(player.getEyePosition());
        boolean near = distanceSqr <= reach * reach;

        boolean allowed = held && config && active && near;
        if (!allowed && DEBUG_ENTITY_PANEL) {
            System.out.println("[InfiniteStats][EntityPanel] 拒绝编辑 " + target.getType()
                    + " —— held=" + held + " config=" + config + " active=" + active
                    + " 距离=" + String.format(java.util.Locale.ROOT, "%.2f", Math.sqrt(distanceSqr))
                    + " 上限=" + reach);
        }
        return allowed;
    }

    /** 生物面板：把某个实体身上的属性锁同步给客户端。 */
    public static void sendEntityPanelLocks(ServerPlayer player, Entity target) {
        if (player == null || target == null) return;
        CHANNEL.sendToPlayer(player, new EntityPanelLockSyncPacket(target.getId(),
                com.infinitestats.handler.AttributeLockHandler.snapshot(target)));
    }

    /**
     * 生物面板：客户端 → 服务器，执行「动作」页里的某个动作。
     * <p>
     * 与开关同一条权限链（{@link #canEditEntity}）。执行后回一发 {@link OpenEntityPanelPacket}：
     * 动作会改血量 / 效果 / 状态，客户端据此刷新，且顺便把「可编辑」标志校正回权威值。
     */
    public static final class EntityPanelCommandPacket {
        private final int entityId;
        private final int actionIndex;

        public EntityPanelCommandPacket(int entityId, int actionIndex) {
            this.entityId = entityId;
            this.actionIndex = actionIndex;
        }

        public static void encode(EntityPanelCommandPacket msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.entityId);
            buf.writeVarInt(msg.actionIndex);
        }

        public static EntityPanelCommandPacket decode(FriendlyByteBuf buf) {
            return new EntityPanelCommandPacket(buf.readVarInt(), buf.readVarInt());
        }

        public static void handle(EntityPanelCommandPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;

                Entity target = player.level().getEntity(msg.entityId);
                if (!(target instanceof LivingEntity living)) return;
                if (!canEditEntity(player, target)) {
                    sendEntityPanelState(player, target, Config.ALLOW_ENTITY_EDITING.get());
                    return;
                }

                com.infinitestats.util.EntityPanelActions.Action action =
                        com.infinitestats.util.EntityPanelActions.byIndex(msg.actionIndex);
                if (action != null) {
                    com.infinitestats.util.EntityPanelActions.apply(player, living, action);
                }
                // 杀死之后目标已经没了，客户端会自己检测到实体消失并关面板，这里不再回包
                if (target.isAlive()) {
                    sendEntityPanelState(player, target, Config.ALLOW_ENTITY_EDITING.get());
                    sendEntityPanelLocks(player, target);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /**
     * 生物面板：客户端 → 服务器，移除生物身上的单个药水效果（效果页点某一行）。
     * <p>
     * 用注册名而不是下标：药水效果来自所有模组，写注册名更抗「中途加删模组 / 数据包」。
     */
    public static final class EntityPanelEffectPacket {
        private final int entityId;
        private final String effectId;

        public EntityPanelEffectPacket(int entityId, String effectId) {
            this.entityId = entityId;
            this.effectId = effectId == null ? "" : effectId;
        }

        public static void encode(EntityPanelEffectPacket msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.entityId);
            buf.writeUtf(msg.effectId, 128);
        }

        public static EntityPanelEffectPacket decode(FriendlyByteBuf buf) {
            return new EntityPanelEffectPacket(buf.readVarInt(), buf.readUtf(128));
        }

        public static void handle(EntityPanelEffectPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;

                Entity target = player.level().getEntity(msg.entityId);
                if (!(target instanceof LivingEntity living)) return;
                if (!canEditEntity(player, target)) return;
                com.infinitestats.util.EntityPanelActions.removeEffect(living, msg.effectId);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /**
     * 生物面板：客户端 → 服务器，请求打开「附近生物列表」里点中的那只生物的面板。
     * <p>
     * 校验比「改生物」宽松：只要身上有观察杖 + 属性激活 + 距离 64 格内，不要求可编辑 ——
     * 只读服也该能翻看生物。
     */
    public static final class EntityPanelRequestPacket {
        private final int entityId;

        public EntityPanelRequestPacket(int entityId) {
            this.entityId = entityId;
        }

        public static void encode(EntityPanelRequestPacket msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.entityId);
        }

        public static EntityPanelRequestPacket decode(FriendlyByteBuf buf) {
            return new EntityPanelRequestPacket(buf.readVarInt());
        }

        public static void handle(EntityPanelRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;

                Entity target = player.level().getEntity(msg.entityId);
                if (!(target instanceof LivingEntity)) return;
                if (!EntityPanelWand.hasWand(player)) return;

                boolean active = PlayerStatsProvider.of(player)
                        .map(stats -> stats.isToggleActive(EntityPanelWand.STAT_ID))
                        .orElse(false);
                if (!active) return;
                if (target.distanceToSqr(player) > 64.0D * 64.0D) return;

                sendEntityPanelState(player, target, Config.ALLOW_ENTITY_EDITING.get());
                sendEntityPanelLocks(player, target);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /**
     * 生物面板：客户端 → 服务器，锁定 / 解锁某条属性。
     * <p>
     * {@code mode} 为 {@code 0} 表示解锁；{@code 1} 锁基础值；{@code 2} 锁最终值。
     * 真正的「每 tick 压回去」在 {@link com.infinitestats.handler.AttributeLockHandler} 里。
     */
    public static final class EntityPanelLockPacket {
        private final int entityId;
        private final String attributeId;
        private final int mode;
        private final double value;

        public EntityPanelLockPacket(int entityId, String attributeId, int mode, double value) {
            this.entityId = entityId;
            this.attributeId = attributeId == null ? "" : attributeId;
            this.mode = mode;
            this.value = value;
        }

        public static void encode(EntityPanelLockPacket msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.entityId);
            buf.writeUtf(msg.attributeId, 128);
            buf.writeVarInt(msg.mode);
            buf.writeDouble(msg.value);
        }

        public static EntityPanelLockPacket decode(FriendlyByteBuf buf) {
            return new EntityPanelLockPacket(buf.readVarInt(), buf.readUtf(128),
                    buf.readVarInt(), buf.readDouble());
        }

        public static void handle(EntityPanelLockPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;

                Entity target = player.level().getEntity(msg.entityId);
                if (!(target instanceof LivingEntity living)) return;
                if (!canEditEntity(player, target)) {
                    sendEntityPanelState(player, target, Config.ALLOW_ENTITY_EDITING.get());
                    return;
                }

                ResourceLocation id = ResourceLocation.tryParse(msg.attributeId);
                if (id == null || !BuiltInRegistries.ATTRIBUTE.containsKey(id)) return;
                if (msg.mode != com.infinitestats.handler.AttributeLockHandler.MODE_NONE
                        && msg.mode != com.infinitestats.handler.AttributeLockHandler.MODE_BASE
                        && msg.mode != com.infinitestats.handler.AttributeLockHandler.MODE_FINAL) return;
                if (msg.mode != com.infinitestats.handler.AttributeLockHandler.MODE_NONE
                        && !Double.isFinite(msg.value)) return;

                com.infinitestats.handler.AttributeLockHandler.set(living, msg.attributeId, msg.mode, msg.value);
                sendEntityPanelLocks(player, target);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /**
     * 生物面板：服务端 → 客户端，同步某个实体身上的属性锁。
     * <p>
     * 内容是 {@code 属性注册名 → {mode, value}} 的 NBT：锁的数量通常只有个位数。
     */
    public static final class EntityPanelLockSyncPacket {
        private final int entityId;
        private final net.minecraft.nbt.CompoundTag locks;

        public EntityPanelLockSyncPacket(int entityId, net.minecraft.nbt.CompoundTag locks) {
            this.entityId = entityId;
            this.locks = locks == null ? new net.minecraft.nbt.CompoundTag() : locks;
        }

        public static void encode(EntityPanelLockSyncPacket msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.entityId);
            buf.writeNbt(msg.locks);
        }

        public static EntityPanelLockSyncPacket decode(FriendlyByteBuf buf) {
            int entityId = buf.readVarInt();
            return new EntityPanelLockSyncPacket(entityId, buf.readNbt());
        }

        public static void handle(EntityPanelLockSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    com.infinitestats.client.EntityPanelScreen.applyLockSnapshot(msg.entityId, msg.locks));
            ctx.get().setPacketHandled(true);
        }
    }

    /** 把某个维度的「已强加载区块」列表同步给玩家（每次强加载操作后调用）。 */
    public static void sendForcedChunks(ServerPlayer player, ServerLevel level) {
        List<Long> list = new ArrayList<>();
        for (long packed : level.getForcedChunks()) {
            list.add(packed);
        }
        CHANNEL.sendToPlayer(player,
                new ChunkLoaderSyncPacket(list, level.dimension().location().toString()));
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

                PlayerStatsProvider.of(player).ifPresent(stats -> {
                    StatType stat = StatType.fromId(msg.statId);
                    if (stat == null) return;

                    // 「功能开关」关闭的属性不能加点（客户端界面已不显示，这里是服务端兜底）
                    if (msg.amount > 0 && stats.isStatDisabled(msg.statId)) return;

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

                PlayerStatsProvider.of(player).ifPresent(stats -> {
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

                PlayerStatsProvider.of(player).ifPresent(stats -> {
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
        /** 保护模式下的「死亡重生是否继承保护」（黑名单模式无意义，原样带回即可）。 */
        private final boolean protectAcrossDeath;

        public UpdateBuffFilterPacket(boolean useBlacklist, Set<String> effectIds) {
            this(useBlacklist, effectIds, false);
        }

        public UpdateBuffFilterPacket(boolean useBlacklist, Set<String> effectIds, boolean protectAcrossDeath) {
            this.useBlacklist = useBlacklist;
            this.effectIds = effectIds;
            this.protectAcrossDeath = protectAcrossDeath;
        }

        public static void encode(UpdateBuffFilterPacket msg, FriendlyByteBuf buf) {
            buf.writeBoolean(msg.useBlacklist);
            buf.writeBoolean(msg.protectAcrossDeath);
            buf.writeVarInt(msg.effectIds.size());
            for (String id : msg.effectIds) {
                buf.writeUtf(id);
            }
        }

        public static UpdateBuffFilterPacket decode(FriendlyByteBuf buf) {
            boolean useBlacklist = buf.readBoolean();
            boolean protectAcrossDeath = buf.readBoolean();
            int count = buf.readVarInt();
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < count; i++) {
                ids.add(buf.readUtf());
            }
            return new UpdateBuffFilterPacket(useBlacklist, ids, protectAcrossDeath);
        }

        public static void handle(UpdateBuffFilterPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;

                PlayerStatsProvider.of(player).ifPresent(stats -> {
                    stats.setBuffFilterList(new HashSet<>(msg.effectIds), msg.useBlacklist);
                    stats.setProtectAcrossDeath(msg.protectAcrossDeath);
                    // 同步回客户端
                    syncToClient(player);
                });
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // ========== 同步方法 ==========

    /**
     * 待同步属性数据的玩家。同一个玩家在一个 tick 内被请求多少次，都只发一包。
     * <p>
     * 加经验（被动经验每秒一次）、零血兜底这类路径原本每次都 {@link #syncToClient}，
     * 而它是「全量快照 + 连锁配置」两个包 —— 一个人每秒 2 包、
     * 10 人在线就是每秒 20 个全量包。改成打标记、tick 末合并。
     */
    private static final Set<UUID> PENDING_STATS_SYNC = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** 请求在本次服务端 tick 结束时同步属性数据（合并发送）。 */
    public static void requestStatsSync(ServerPlayer player) {
        if (player != null) PENDING_STATS_SYNC.add(player.getUUID());
    }

    /** 服务端 tick 结束时统一发出合并后的同步包。 */
    public static void flushStatsSync(MinecraftServer server) {
        if (server == null || PENDING_STATS_SYNC.isEmpty()) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (PENDING_STATS_SYNC.remove(player.getUUID())) {
                sendStatsSnapshot(player);
            }
        }
        if (!PENDING_STATS_SYNC.isEmpty()) {
            // 断线 / 换维度时没能发出去的标记直接丢掉，避免集合长期堆积
            PENDING_STATS_SYNC.removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        }
    }

    /** 只发属性快照（不含连锁 / 矿透这两个配置包）。 */
    public static void sendStatsSnapshot(ServerPlayer player) {
        PlayerStatsProvider.of(player).ifPresent(stats ->
                CHANNEL.sendToPlayer(player, new SyncStatsPacket(stats.createSnapshot())));
    }

    /**
     * 同步玩家数据到客户端（全量：属性快照 + 连锁配置）。
     * <p>
     * 只用在登录 / 重生 / 改设置这类低频时机；高频路径请用 {@link #requestStatsSync}。
     */
    public static void syncToClient(ServerPlayer player) {
        sendStatsSnapshot(player);
        // 连锁设置单独一份（只在改动时变，不必跟着属性同步每 tick 发）
        syncVeinMinerConfig(player);
    }

    /** 把连锁挖掘的个人设置发给客户端（登录 / 重生 / 客户端提交后调用）。 */
    public static void syncVeinMinerConfig(ServerPlayer player) {
        PlayerStatsProvider.of(player).ifPresent(stats ->
                CHANNEL.sendToPlayer(player, new VeinMinerConfigPacket(stats.getVeinMinerConfig())));
    }

    /**
     * 同步所有数据到指定客户端
     */
    public static void syncToTracking(ServerPlayer player) {
        PlayerStatsProvider.of(player).ifPresent(stats -> {
            CHANNEL.sendToTrackingEntityAndSelf(player,
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
                PlayerStatsProvider.of(player).ifPresent(stats -> {
                    if (!stats.isToggleActive("portable_furnace")) return;
                    player.openMenu(
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
                PlayerStatsProvider.of(player).ifPresent(stats -> {
                    if (!stats.isToggleActive("portable_furnace")) return;
                    player.openMenu(
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
                PlayerStatsProvider.of(player).ifPresent(stats -> {
                    if (!stats.isToggleActive("portable_furnace")) return;
                    player.openMenu(
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

                PlayerStatsProvider.of(player).ifPresent(stats -> {
                    if (!stats.isToggleActive("portable_furnace")) return;

                    var furnace = stats.getFurnaceData();
                    int cost = Config.FURNACE_SPEED_COST.get();

                    if (msg.increase) {
                        if (stats.getAvailablePoints() < cost) {
                            player.sendSystemMessage(Component.literal(
                                    "§c可分配点数不足，提升一级速度需 " + cost + " 点"));
                            return;
                        }
                        if (furnace.getSpeedLevel() >= Integer.MAX_VALUE - 1) return;
                        furnace.setSpeedLevel(furnace.getSpeedLevel() + 1);
                        stats.recalculateAvailablePoints();
                        player.sendSystemMessage(Component.literal(
                                "§a熔炉加速至 §f×" + furnace.getSpeedMultiplier()
                                        + "§a（消耗 " + cost + " 点）"));
                    } else {
                        if (furnace.getSpeedLevel() <= 0) {
                            player.sendSystemMessage(Component.literal("§e随身熔炉已处于普通速度"));
                            return;
                        }
                        furnace.setSpeedLevel(furnace.getSpeedLevel() - 1);
                        stats.recalculateAvailablePoints();
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
                PlayerStatsProvider.of(player).ifPresent(stats -> {
                    if (!stats.isToggleActive("portable_furnace")) return;
                    player.openMenu(
                            new SimpleMenuProvider(
                                    (windowId, inv, p) -> new FurnaceOrePriorityMenu(windowId, inv),
                                    Component.translatable("gui.infinitestats.furnace.ore_priority.title")));
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
                PlayerStatsProvider.of(player).ifPresent(stats ->
                        sendOrePrioritySync(player, stats.getFurnaceData()));
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** 服务器将矿石优先顺序、可加入矿石与待炼仓矿石同步到客户端（服务器 → 客户端）。 */
    public static final class FurnaceOrePrioritySyncPacket {
        public static class SyncData {
            public final List<String> available;
            public final List<String> priority;
            public final List<String> reserveIds;
            public final List<Long> reserveAmounts;

            public SyncData(List<String> available, List<String> priority,
                            List<String> reserveIds, List<Long> reserveAmounts) {
                this.available = available;
                this.priority = priority;
                this.reserveIds = reserveIds;
                this.reserveAmounts = reserveAmounts;
            }
        }

        /** 最近一次同步的数据，供界面读取（仅单玩家单界面，使用静态字段足够）。 */
        public static volatile SyncData latest;
        /** 每次同步自增，界面据此判断是否需要重建控件。 */
        public static int version = 0;

        public final List<String> available;
        public final List<String> priority;
        public final List<String> reserveIds;
        public final List<Long> reserveAmounts;

        public FurnaceOrePrioritySyncPacket(List<String> available, List<String> priority,
                                            List<String> reserveIds, List<Long> reserveAmounts) {
            this.available = available;
            this.priority = priority;
            this.reserveIds = reserveIds;
            this.reserveAmounts = reserveAmounts;
        }

        public static void encode(FurnaceOrePrioritySyncPacket msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.priority.size());
            for (String id : msg.priority) buf.writeUtf(id);
            buf.writeVarInt(msg.available.size());
            for (String id : msg.available) buf.writeUtf(id);
            buf.writeVarInt(msg.reserveIds.size());
            for (int i = 0; i < msg.reserveIds.size(); i++) {
                buf.writeUtf(msg.reserveIds.get(i));
                buf.writeVarLong(msg.reserveAmounts.get(i));
            }
        }

        public static FurnaceOrePrioritySyncPacket decode(FriendlyByteBuf buf) {
            int n = buf.readVarInt();
            List<String> priority = new ArrayList<>();
            for (int i = 0; i < n; i++) priority.add(buf.readUtf());
            int m = buf.readVarInt();
            List<String> available = new ArrayList<>();
            for (int i = 0; i < m; i++) available.add(buf.readUtf());
            int k = buf.readVarInt();
            List<String> reserveIds = new ArrayList<>();
            List<Long> reserveAmounts = new ArrayList<>();
            for (int i = 0; i < k; i++) {
                reserveIds.add(buf.readUtf());
                reserveAmounts.add(buf.readVarLong());
            }
            return new FurnaceOrePrioritySyncPacket(available, priority, reserveIds, reserveAmounts);
        }

        public static void handle(FurnaceOrePrioritySyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                latest = new SyncData(msg.available, msg.priority, msg.reserveIds, msg.reserveAmounts);
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
                PlayerStatsProvider.of(player).ifPresent(stats -> {
                    PlayerFurnaceData furnace = stats.getFurnaceData();
                    furnace.setOrePriorityIds(msg.priority);
                    sendOrePrioritySync(player, furnace);
                });
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /**
     * 收集待炼仓（矿石储备箱）当前有货的矿石，并把「优先顺序 + 可加入列表 + 仓内矿石」一并同步给客户端。
     */
    private static void sendOrePrioritySync(ServerPlayer player, PlayerFurnaceData furnace) {
        List<String> priority = furnace.getOrePriorityIds();
        List<String> reserveIds = new ArrayList<>();
        List<Long> reserveAmounts = new ArrayList<>();
        for (int i = 0; i < furnace.getInputBuffer().size(); i++) {
            ItemStack s = furnace.getInputBuffer().get(i);
            if (s.isEmpty() || furnace.getInputAmounts()[i] <= 0) continue;
            reserveIds.add(BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
            reserveAmounts.add(furnace.getInputAmounts()[i]);
        }
        List<String> available = computeAvailable(player, priority, reserveIds);
        CHANNEL.sendToPlayer(player,
                new FurnaceOrePrioritySyncPacket(available, priority, reserveIds, reserveAmounts));
    }

    /**
     * 计算“可加入的矿石”列表：游戏内所有拥有熔炼/高炉配方的物品（排除已在优先列表中的）。
     * 待炼仓里已有存货的矿石排在最前面，其余按注册名字典序排列，方便玩家一眼找到仓内矿石。
     */
    private static List<String> computeAvailable(ServerPlayer player, List<String> priority, List<String> reserveIds) {
        Set<String> inPrio = new HashSet<>(priority);
        Set<String> ids = new LinkedHashSet<>();
        RecipeManager rm = player.level().getRecipeManager();
        collect(rm, RecipeType.SMELTING, ids);
        collect(rm, RecipeType.BLASTING, ids);
        List<String> available = new ArrayList<>();
        for (String id : reserveIds) {
            if (!inPrio.contains(id) && ids.contains(id) && !available.contains(id)) {
                available.add(id);
            }
        }
        List<String> rest = new ArrayList<>();
        for (String id : ids) {
            if (!inPrio.contains(id) && !available.contains(id)) rest.add(id);
        }
        rest.sort(String::compareTo);
        available.addAll(rest);
        return available;
    }

    private static void collect(RecipeManager rm, RecipeType<?> type, Set<String> ids) {
        for (RecipeHolder<?> holder : rm.getRecipes()) {
            Recipe<?> recipe = holder.value();
            if (recipe.getType() == type) {
                for (Ingredient ing : recipe.getIngredients()) {
                    for (ItemStack s : ing.getItems()) {
                        if (!s.isEmpty()) ids.add(BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
                    }
                }
            }
        }
    }

    /** 客户端向服务器请求增减随身工作台物品倍率（消耗/返还可分配点数）。 */
    public static final class CraftingMultiplierPacket {
        private static final int MAX_MULTIPLIER = 64; // 受物品堆叠上限约束
        /** 单次请求最多调整的级数：客户端按住 Shift 即以此步长提交。 */
        private static final int MAX_STEP = 10;

        private final boolean increase; // true=提升倍率（消耗点数）；false=降低倍率（返点数）
        private final int amount;       // 本次请求调整的级数

        public CraftingMultiplierPacket(boolean increase) {
            this(increase, 1);
        }

        public CraftingMultiplierPacket(boolean increase, int amount) {
            this.increase = increase;
            this.amount = Math.max(1, Math.min(amount, MAX_STEP));
        }

        public static void encode(CraftingMultiplierPacket msg, FriendlyByteBuf buf) {
            buf.writeBoolean(msg.increase);
            buf.writeVarInt(msg.amount);
        }

        public static CraftingMultiplierPacket decode(FriendlyByteBuf buf) {
            return new CraftingMultiplierPacket(buf.readBoolean(), buf.readVarInt());
        }

        public static void handle(CraftingMultiplierPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;

                PlayerStatsProvider.of(player).ifPresent(stats -> {
                    if (!stats.isToggleActive("portable_crafting")) return;

                    int cost = Config.CRAFTING_MULTIPLIER_COST.get();
                    long mult = stats.getCraftingMultiplier();

                    if (msg.increase) {
                        int headroom = (int) Math.min(msg.amount, MAX_MULTIPLIER - mult);
                        if (headroom <= 0) {
                            player.sendSystemMessage(Component.literal(
                                    "§e随身工作台倍率已达上限 ×" + MAX_MULTIPLIER));
                            return;
                        }
                        // 点数够几级就升几级；一级都升不动时才提示
                        int affordable = (int) Math.min(headroom, stats.getAvailablePoints() / cost);
                        if (affordable <= 0) {
                            player.sendSystemMessage(Component.literal(
                                    "§c可分配点数不足，提升一级倍率需 " + cost + " 点"));
                            return;
                        }
                        stats.setCraftingMultiplier(mult + affordable);
                        stats.recalculateAvailablePoints();
                        player.sendSystemMessage(Component.literal(
                                "§a工作台倍率提升至 §f×" + stats.getCraftingMultiplier()
                                        + "§a（消耗 " + (long) cost * affordable + " 点）"));
                    } else {
                        int step = (int) Math.min(msg.amount, mult - 1);
                        if (step <= 0) {
                            player.sendSystemMessage(Component.literal("§e随身工作台已处于基础倍率 ×1"));
                            return;
                        }
                        stats.setCraftingMultiplier(mult - step);
                        stats.recalculateAvailablePoints();
                        player.sendSystemMessage(Component.literal(
                                "§a工作台倍率降至 §f×" + stats.getCraftingMultiplier()
                                        + "§a（返还 " + (long) cost * step + " 点）"));
                    }

                    // 同步点数与倍率，并立即重算已摆放配方的产物。
                    syncToClient(player);
                    if (player.containerMenu instanceof PortableCraftingMenu menu) {
                        menu.refreshResult();
                        menu.broadcastChanges();
                    }
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
                PlayerStatsProvider.of(player).ifPresent(stats -> {
                    if (!stats.isToggleActive("portable_crafting")) {
                        player.sendSystemMessage(Component.literal("未激活『内置工作台』属性，无法打开随身工作台"));
                        return;
                    }
                    player.openMenu(
                            new SimpleMenuProvider(
                                    (id, inv, p) -> new PortableCraftingMenu(id, inv),
                                    Component.translatable("container.crafting")));
                });
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** 客户端请求打开随身铁砧（需已开启 portable_anvil 开关）。 */
    public static final class AnvilOpenPacket {
        public AnvilOpenPacket() {}

        public static void encode(AnvilOpenPacket msg, FriendlyByteBuf buf) {
            // 无数据
        }

        public static AnvilOpenPacket decode(FriendlyByteBuf buf) {
            return new AnvilOpenPacket();
        }

        public static void handle(AnvilOpenPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                PlayerStatsProvider.of(player).ifPresent(stats -> {
                    if (!stats.isToggleActive("portable_anvil")) {
                        player.sendSystemMessage(Component.literal("未激活『随身铁砧』属性，无法打开随身铁砧"));
                        return;
                    }
                    PortableAnvil.open(player);
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
                            ? ResourceLocation.fromNamespaceAndPath("minecraft", dimArg)
                            : ResourceLocation.parse(dimArg);
                };
                ResourceKey<Level> targetKey = ResourceKey.create(Registries.DIMENSION, rl);
                PlayerStats stats = PlayerStatsProvider.of(player).orElse(null);
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
                CHANNEL.sendToPlayer(player, new CrossDimListPacket(dims));
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
        /** 当前玩家是否有权授予 / 撤销（受配置 achievementManagerRequireOp 影响）。 */
        private final boolean canEdit;
        /** 被跳过的无 display 技术成就数量（原版 recipes/* 等）。 */
        private final int skipped;

        public SyncAdvancementsPacket(List<AchievementInfo> achievements, boolean canEdit, int skipped) {
            this.achievements = achievements;
            this.canEdit = canEdit;
            this.skipped = skipped;
        }

        public static void encode(SyncAdvancementsPacket msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.achievements.size());
            for (AchievementInfo info : msg.achievements) {
                AchievementInfo.encode(info, buf);
            }
            buf.writeBoolean(msg.canEdit);
            buf.writeVarInt(msg.skipped);
        }

        public static SyncAdvancementsPacket decode(FriendlyByteBuf buf) {
            int count = buf.readVarInt();
            List<AchievementInfo> list = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                list.add(AchievementInfo.decode(buf));
            }
            return new SyncAdvancementsPacket(list, buf.readBoolean(), buf.readVarInt());
        }

        public static void handle(SyncAdvancementsPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                // 存储到静态字段，由 AchievementManagerScreen 轮询读取
                pendingAdvancements = msg.achievements;
                pendingAdvancementsCanEdit = msg.canEdit;
                pendingAdvancementsSkipped = msg.skipped;
                advancementsVersion++;
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /**
     * 批量授予 / 撤销数据包（客户端 → 服务器）。
     * <p>
     * 一次操作可能涉及几十上百条成就，逐条发包既慢又会把服务端队列打满，
     * 所以客户端把「选中集合」整体发上来，服务端一次性处理完只回一次列表。
     */
    public static final class BatchAdvancementPacket {
        /** 单次操作最多接受的成就数，防止客户端构造超大包把服务端卡死。 */
        private static final int MAX_IDS = 4096;

        private final List<String> advancementIds;
        private final boolean grant;

        public BatchAdvancementPacket(List<String> advancementIds, boolean grant) {
            this.advancementIds = advancementIds;
            this.grant = grant;
        }

        public static void encode(BatchAdvancementPacket msg, FriendlyByteBuf buf) {
            int size = Math.min(msg.advancementIds.size(), MAX_IDS);
            buf.writeVarInt(size);
            for (int i = 0; i < size; i++) {
                buf.writeUtf(msg.advancementIds.get(i));
            }
            buf.writeBoolean(msg.grant);
        }

        public static BatchAdvancementPacket decode(FriendlyByteBuf buf) {
            int size = Math.min(buf.readVarInt(), MAX_IDS);
            List<String> ids = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                ids.add(buf.readUtf());
            }
            return new BatchAdvancementPacket(ids, buf.readBoolean());
        }

        public static void handle(BatchAdvancementPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;

                MinecraftServer server = player.getServer();
                if (server == null) return;

                if (!canEditAdvancements(player)) {
                    denyEdit(player);
                    return;
                }

                for (String rawId : msg.advancementIds) {
                    ResourceLocation advId = ResourceLocation.tryParse(rawId);
                    if (advId == null) continue;
                    AdvancementHolder advancement = server.getAdvancements().get(advId);
                    if (advancement == null) continue;
                    applyAdvancementState(player, server, advancement, msg.grant);
                }

                syncAdvancementsToClient(player);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** 配置要求 OP 时，权限等级 2 以下不能改成就。 */
    private static boolean canEditAdvancements(ServerPlayer player) {
        return !Config.ACHIEVEMENT_MANAGER_REQUIRE_OP.get() || player.hasPermissions(2);
    }

    private static void denyEdit(ServerPlayer player) {
        player.displayClientMessage(
                Component.translatable("screen.infinitestats.achievements.no_permission"), true);
    }

    /**
     * 授予 / 撤销单个成就。
     * <p>
     * 撤销会<b>递归撤销所有子成就</b>：原版成就树里子成就的完成状态依赖父成就链，
     * 只撤销父成就的话子成就仍留着完成标记，界面与奖励会自相矛盾（父成就是「未完成」，
     * 子成就却是「已完成」且再也拿不到一遍）。
     */
    private static void applyAdvancementState(ServerPlayer player, MinecraftServer server,
                                              AdvancementHolder advancement, boolean grant) {
        var playerAdvancements = player.getAdvancements();
        if (grant) {
            // 授予所有条件 → 完成成就
            for (String criterion : advancement.value().criteria().keySet()) {
                playerAdvancements.award(advancement, criterion);
            }
        } else {
            revokeWithChildren(playerAdvancements, server, advancement);
        }
    }

    /** 1.21.1 的 {@code Advancement} 不再有 getChildren()，只能按 parent 关系反查后代。 */
    private static void revokeWithChildren(net.minecraft.server.PlayerAdvancements playerAdvancements,
                                           MinecraftServer server, AdvancementHolder target) {
        for (String criterion : target.value().criteria().keySet()) {
            playerAdvancements.revoke(target, criterion);
        }

        String targetId = target.id().toString();
        for (AdvancementHolder holder : server.getAdvancements().getAllAdvancements()) {
            if (holder == target) continue;
            if (!isDescendant(server, holder, targetId)) continue;
            for (String criterion : holder.value().criteria().keySet()) {
                playerAdvancements.revoke(holder, criterion);
            }
        }
    }

    /** 沿 parent 链向上找，判断 {@code holder} 是否是 {@code ancestorId} 的后代。 */
    private static boolean isDescendant(MinecraftServer server, AdvancementHolder holder, String ancestorId) {
        var parent = holder.value().parent();
        int guard = 0;
        while (parent.isPresent() && guard++ < 64) {
            String parentId = parent.get().toString();
            if (parentId.equals(ancestorId)) return true;
            AdvancementHolder parentHolder = server.getAdvancements().get(parent.get());
            if (parentHolder == null) return false;
            parent = parentHolder.value().parent();
        }
        return false;
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

                if (!canEditAdvancements(player)) {
                    denyEdit(player);
                    return;
                }

                ResourceLocation advId = ResourceLocation.tryParse(msg.advancementId);
                if (advId == null) return;

                AdvancementHolder advancement = server.getAdvancements().get(advId);
                if (advancement == null) return;

                applyAdvancementState(player, server, advancement, msg.grant);

                // 同步更新后的成就列表返回客户端
                syncAdvancementsToClient(player);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // ========== 成就管理同步方法 ==========

    /**
     * 构建成就信息列表并同步到客户端
     */
    public static void syncAdvancementsToClient(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        // 顺序由服务端统一排好再下发：模组命名空间 → 父成就先于子成就 → 标题 → id
        List<AdvancementHolder> all = new ArrayList<>(server.getAdvancements().getAllAdvancements());
        java.util.Map<String, Integer> depths = new java.util.HashMap<>();
        for (AdvancementHolder holder : all) {
            depthOf(holder, server, depths, 0);
        }
        all.sort(java.util.Comparator
                .comparing((AdvancementHolder a) -> a.id().getNamespace())
                .thenComparingInt(a -> depths.getOrDefault(a.id().toString(), 0))
                .thenComparing(NetworkHandler::advancementTitle, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(a -> a.id().toString()));

        var playerAdvancements = player.getAdvancements();
        List<AchievementInfo> list = new ArrayList<>(all.size());
        int skipped = 0;

        for (AdvancementHolder adv : all) {
            var display = adv.value().display().orElse(null);
            if (display == null) {
                // 没有 display 的是纯技术成就（原版 recipes/* 上千条），不进列表
                skipped++;
                continue;
            }

            String id = adv.id().toString();
            String name = display.getTitle().getString();
            String desc = display.getDescription().getString();
            String iconId = BuiltInRegistries.ITEM.getKey(display.getIcon().getItem()).toString();

            var progress = playerAdvancements.getOrStartProgress(adv);
            boolean completed = progress.isDone();
            int criteriaTotal = adv.value().criteria().size();
            int criteriaDone = 0;
            for (String criterion : adv.value().criteria().keySet()) {
                var criterionProgress = progress.getCriterion(criterion);
                if (criterionProgress != null && criterionProgress.isDone()) criteriaDone++;
            }
            int percent = completed ? 100 : Math.round(progress.getPercent() * 100f);

            list.add(new AchievementInfo(id, name, desc, iconId,
                    completed, percent, criteriaDone, criteriaTotal,
                    display.getType().ordinal(),
                    id.contains(":") ? id.substring(0, id.indexOf(':')) : "minecraft",
                    adv.value().parent().map(ResourceLocation::toString).orElse(""),
                    display.isHidden()));
        }

        CHANNEL.sendToPlayer(player,
                new SyncAdvancementsPacket(list, canEditAdvancements(player), skipped));
    }

    /** 成就树深度（根 = 0），用于「父成就排在子成就之前」。 */
    private static int depthOf(AdvancementHolder holder, MinecraftServer server,
                               java.util.Map<String, Integer> cache, int guard) {
        String id = holder.id().toString();
        Integer cached = cache.get(id);
        if (cached != null) return cached;
        if (guard > 64) return 0;

        int depth = 0;
        var parent = holder.value().parent();
        if (parent.isPresent()) {
            AdvancementHolder parentHolder = server.getAdvancements().get(parent.get());
            depth = parentHolder == null ? 1 : 1 + depthOf(parentHolder, server, cache, guard + 1);
        }
        cache.put(id, depth);
        return depth;
    }

    private static String advancementTitle(AdvancementHolder advancement) {
        var display = advancement.value().display().orElse(null);
        return display == null ? "" : display.getTitle().getString();
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
                Ingredient.CONTENTS_STREAM_CODEC.encode((RegistryFriendlyByteBuf) buf, msg.ingredients[i]);
            }
        }

        public static CraftingRecipeFillPacket decode(FriendlyByteBuf buf) {
            CraftingRecipeFillPacket msg = new CraftingRecipeFillPacket(new Ingredient[9]);
            for (int i = 0; i < 9; i++) {
                msg.ingredients[i] = Ingredient.CONTENTS_STREAM_CODEC.decode((RegistryFriendlyByteBuf) buf);
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
                PlayerStatsProvider.of(player).ifPresent(
                        stats -> {
                            if (StatType.fromId(msg.statId) == null) return;
                            stats.toggleFavorite(msg.statId);
                            syncToClient(player);
                        });
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
                CHANNEL.sendToPlayer(player,
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
                cacheNetworkItems(msg.itemIds);
            });
            ctx.get().setPacketHandled(true);
        }

        public java.util.List<String> getItemIds() { return itemIds; }
    }

}
