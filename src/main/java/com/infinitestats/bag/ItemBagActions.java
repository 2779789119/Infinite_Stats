package com.infinitestats.bag;

import com.infinitestats.Config;
import com.infinitestats.compat.NetworkHandle;
import com.infinitestats.compat.NetworkIO;
import com.infinitestats.network.NetworkHandler;
import com.infinitestats.util.ItemSearch;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 服务端侧的背包动作：取出、全部存入、把最新内容推回客户端。
 * <p>
 * 所有入口（界面点击、数据包处理）都必须先过 {@link #unlocked(ServerPlayer)}，
 * 属性没解锁时既不读也不写 —— 界面被改造过的客户端也无法凭空开箱。
 */
public final class ItemBagActions {

    /** 每次同步包携带的最大条目数：太多了会撞上 Forge 的包大小上限。 */
    private static final int SYNC_BATCH = 200;

    // ===== 排序模式（客户端与服务端共用编号）=====
    public static final int SORT_NAME_ASC = 0;
    public static final int SORT_NAME_DESC = 1;
    public static final int SORT_COUNT_DESC = 2;
    public static final int SORT_MOD = 3;

    /**
     * 外部存储的全量列表缓存（按玩家，{@link #NET_CACHE_TTL_MS} 内复用）。
     * <p>
     * 学的是汤姆存储的做法：搜索框每敲一个字都要重算列表，而 {@code NetworkIO.listItems}
     * 要反射遍历 RS / AE2 / 汤姆存储的全部槽位 —— 不缓存的话打字会卡。取出 / 存入之后
     * 必须显式 {@link #invalidateNetworkCache}，否则界面上的数量会停在 1 秒前。
     */
    private static final Map<UUID, NetCache> NET_CACHE = new HashMap<>();
    private static final long NET_CACHE_TTL_MS = 1000L;

    private static final class NetCache {
        List<ItemStack> items;
        long expiresAt;
    }

    /**
     * 「存储网络」标签页一次最多同步多少种物品。
     * <p>
     * RS / AE2 的大型网络动辄上千种，全量发过去既卡界面也卡网络；这里按数量从多到少截断，
     * 界面底部会写明「仅显示前 N 种」，玩家要找冷门物品仍然可以在各自的终端里搜。
     */
    private static final int NETWORK_KIND_LIMIT = 800;

    private ItemBagActions() {
    }

    /** 玩家是否已解锁「无尽背包」属性（受「功能开关」影响）。 */
    public static boolean unlocked(ServerPlayer player) {
        return player.getCapability(com.infinitestats.stats.PlayerStatsProvider.PLAYER_STATS)
                .map(stats -> stats.isToggleActive("item_bag"))
                .orElse(false);
    }

    public static ItemBag bagOf(ServerPlayer player) {
        return player.getCapability(ItemBagProvider.ITEM_BAG).orElse(null);
    }

    /**
     * 取出指定物品。
     *
     * @param template 只要物品与 NBT 对得上即可，数量被忽略
     * @param amount   想取出的数量（≤0 按 1 处理）
     */
    public static void withdraw(ServerPlayer player, ItemStack template, int amount) {
        if (template == null || template.isEmpty()) return;
        ItemBag bag = bagOf(player);
        if (bag == null) return;

        int want = amount <= 0 ? 1 : amount;
        ItemStack got = bag.extract(template, want);
        if (got.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("message.infinitestats.bag.empty_entry"), true);
            sync(player);
            return;
        }

        // Inventory#add 会把传入的栈改写成「没放下」的剩余部分
        ItemStack left = got.copy();
        player.getInventory().add(left);
        if (!left.isEmpty()) {
            // 玩家身上放不下：原样退回背包，不丢在地上
            bag.insert(left);
            player.displayClientMessage(
                    Component.translatable("message.infinitestats.bag.inventory_full"), true);
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        sync(player);
    }

    /**
     * 把玩家 36 格里的东西整体收进背包。
     * <p>
     * 默认保留快捷栏（配置 {@code itemBagDepositKeepHotbar}），并且<b>永远保留当前手持那一格</b>
     * —— 把玩家正握着的工具/武器收走，体验上是「手里的东西凭空消失」。
     *
     * @return 收进去的条目数
     */
    public static int depositAll(ServerPlayer player) {
        ItemBag bag = bagOf(player);
        if (bag == null) return 0;

        Inventory inv = player.getInventory();
        boolean keepHotbar = Config.ITEM_BAG_DEPOSIT_KEEP_HOTBAR.get();
        int start = keepHotbar ? Inventory.getSelectionSize() : 0;

        int moved = 0;
        for (int i = start; i < inv.items.size(); i++) {
            if (i == inv.selected) continue;
            ItemStack stack = inv.items.get(i);
            if (stack.isEmpty()) continue;
            bag.insert(stack);
            inv.items.set(i, ItemStack.EMPTY);
            moved++;
        }
        if (moved > 0) {
            inv.setChanged();
            player.containerMenu.broadcastChanges();
            sync(player);
        }
        return moved;
    }

    /** 把背包内容全量推给客户端（按 {@link #SYNC_BATCH} 条分包）。 */
    public static void sync(ServerPlayer player) {
        ItemBag bag = bagOf(player);
        sendBatches(player, ItemBagProvider.SOURCE_BAG,
                bag == null ? List.of() : bag.snapshot(), 0);
    }

    /**
     * 把「已连接的其它存储」（RS / AE2 / BD / 汤姆存储 / Sophisticated Backpacks）的内容推给客户端。
     * <p>
     * 本模组自己的背包（kind 5）被排除 —— 它已经有独立的标签页，混进来会出现两份同样的物品。
     * <p>
     * <b>过滤与排序都在服务端做</b>（学精致存储的 GridView）：客户端的列表是截断过的，
     * 如果在客户端过滤，第 800 种之后的物品永远搜不到。这里对全量扫描结果先按搜索词过滤，
     * 再排序，最后才截断到 {@link #NETWORK_KIND_LIMIT} 种。
     *
     * @param query    搜索词，空串表示不过滤
     * @param sortMode {@link #SORT_NAME_ASC} / {@link #SORT_NAME_DESC} / {@link #SORT_COUNT_DESC} / {@link #SORT_MOD}
     */
    public static void syncNetwork(ServerPlayer player, String query, int sortMode) {
        List<NetworkHandle> nets = networksWithoutBag(player);
        if (nets.isEmpty()) {
            sendBatches(player, ItemBagProvider.SOURCE_NETWORK, List.of(), 0);
            return;
        }

        List<ItemStack> matched = new ArrayList<>();
        for (ItemStack stack : networkItems(player, nets)) {
            if (stack.isEmpty()) continue;
            if (!query.isEmpty() && !ItemSearch.matches(stack, query)) continue;
            matched.add(stack);
        }
        sortStacks(matched, sortMode);

        int total = matched.size();
        if (matched.size() > NETWORK_KIND_LIMIT) {
            matched = new ArrayList<>(matched.subList(0, NETWORK_KIND_LIMIT));
        }
        sendBatches(player, ItemBagProvider.SOURCE_NETWORK, matched, total);
    }

    /** 外部存储的全量物品（带 1 秒缓存）。 */
    private static List<ItemStack> networkItems(ServerPlayer player, List<NetworkHandle> nets) {
        UUID id = player.getUUID();
        long now = System.currentTimeMillis();
        NetCache cached = NET_CACHE.get(id);
        if (cached != null && cached.expiresAt > now) {
            return cached.items;
        }
        List<ItemStack> items = NetworkIO.listItems(nets);
        NetCache entry = new NetCache();
        entry.items = items;
        entry.expiresAt = now + NET_CACHE_TTL_MS;
        NET_CACHE.put(id, entry);
        return items;
    }

    /** 取出 / 存入之后必须让缓存失效，否则界面数量会停在 1 秒前。 */
    public static void invalidateNetworkCache(ServerPlayer player) {
        if (player != null) NET_CACHE.remove(player.getUUID());
    }

    /** 玩家退出时清掉缓存（与其它会话级记录一起）。 */
    public static void forget(net.minecraft.world.entity.player.Player player) {
        if (player != null) NET_CACHE.remove(player.getUUID());
    }

    private static void sortStacks(List<ItemStack> items, int sortMode) {
        Comparator<ItemStack> byName = Comparator.comparing(
                (ItemStack s) -> s.getHoverName().getString(), String.CASE_INSENSITIVE_ORDER);
        switch (sortMode) {
            case SORT_NAME_DESC -> items.sort(byName.reversed());
            case SORT_COUNT_DESC -> items.sort(
                    Comparator.comparingInt(ItemStack::getCount).reversed().thenComparing(byName));
            case SORT_MOD -> items.sort(Comparator
                    .comparing((ItemStack s) -> ItemSearch.namespaceOf(s))
                    .thenComparing(byName));
            default -> items.sort(byName);
        }
    }

    private static void sendBatches(ServerPlayer player, byte source, List<ItemStack> items, int totalKinds) {
        if (items.isEmpty()) {
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new NetworkHandler.SyncItemBagPacket(source, List.of(), true, true, totalKinds));
            return;
        }
        for (int from = 0; from < items.size(); from += SYNC_BATCH) {
            int to = Math.min(from + SYNC_BATCH, items.size());
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new NetworkHandler.SyncItemBagPacket(
                            source, items.subList(from, to), from == 0, to >= items.size(), totalKinds));
        }
    }

    /** 取出「存储网络」里的物品：按玩家设定的存储优先级依次尝试，放不下的退回网络。 */
    public static void withdrawNetwork(ServerPlayer player, ItemStack template, int amount) {
        if (template == null || template.isEmpty()) return;
        List<NetworkHandle> nets = networksWithoutBag(player);
        if (nets.isEmpty()) {
            player.displayClientMessage(Component.literal(NetworkIO.noNetworkMessage()), true);
            invalidateNetworkCache(player);
            return;
        }

        int want = amount <= 0 ? 1 : amount;
        ItemStack got = NetworkIO.extract(nets, template.copyWithCount(1), want);
        if (got.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("message.infinitestats.bag.empty_entry"), true);
            invalidateNetworkCache(player);
            return;
        }

        ItemStack left = got.copy();
        player.getInventory().add(left);
        if (!left.isEmpty()) {
            NetworkIO.insert(nets, left);
            player.displayClientMessage(
                    Component.translatable("message.infinitestats.bag.inventory_full"), true);
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        // 缓存里的数量已经过时；刷新由客户端带上当前搜索词重新请求（这样不会把界面重置成全量）
        invalidateNetworkCache(player);
    }

    /** 当前可用的外部存储（排除本模组背包）。 */
    private static List<NetworkHandle> networksWithoutBag(ServerPlayer player) {
        List<NetworkHandle> out = new ArrayList<>();
        for (NetworkHandle h : NetworkIO.getNetworks(player)) {
            if (h.kind != 5) out.add(h);
        }
        return out;
    }
}
