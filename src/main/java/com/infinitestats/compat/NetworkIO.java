package com.infinitestats.compat;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.infinitestats.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 统一网络存储外观：自动在 Refined Storage、Beyond Dimensions、Applied Energistics 2、
 * Sophisticated Backpacks 与 Tom's Storage 之间选择当前玩家可用的存储。
 *
 * 选择顺序由配置文件 {@code NetworkPriority.networkPriority} 决定（列表中靠前的优先），
 * 默认值：RS → AE2 → TOMS → BACKPACK → BD。把 BD 放在最后作为兜底，使其在手持其他
 * 无线终端时不再抢占；若想恢复旧行为（BD 始终优先），把列表改为
 * {@code [RS, BD, AE2, BACKPACK, TOMS]} 即可。
 *
 * 随身工作台 / 熔炉的联动按钮即复用此外观，因此同一组按钮可同时驱动
 * 上述五种存储，无需为每种存储各放一套按钮。
 */
public final class NetworkIO {
    private NetworkIO() {
    }

    public static boolean isAnyLoaded() {
        return RSNetworkBridge.isRSLoaded() || BDNetworkBridge.isBDLoaded()
                || AE2NetworkBridge.isAE2Loaded() || BackpackNetworkBridge.isSBLoaded()
                || TomsStorageNetworkBridge.isTSLoaded();
    }

    /**
     * 取得玩家当前可用的全部存储句柄（按配置文件 {@code networkPriority} 排序）。
     * 与旧版“只取第一个可用网络”不同，这里返回所有可用网络的并集，
     * 使后续 listItems/extract/insert 能在多个存储之间自动回落：
     * 例如 RS 有 A 没有 B、AE2 有 B 没有 A 时，取 A 走 RS、取 B 走 AE2 都能成功。
     * 列表为空表示没有任何可用存储。
     */
    public static List<NetworkHandle> getNetworks(Player player) {
        List<NetworkHandle> result = new ArrayList<>();
        List<? extends String> order = Config.NETWORK_PRIORITY.get();
        if (order == null) order = List.of("RS", "AE2", "TOMS", "BACKPACK", "BD");
        for (String raw : order) {
            NetworkHandle h = tryBridge(raw, player);
            if (h != null) result.add(h);
        }
        return result;
    }

    /** 按配置名尝试对应桥接；命中可用存储时返回句柄，否则返回 null。 */
    private static NetworkHandle tryBridge(String name, Player player) {
        switch (name.trim().toUpperCase(Locale.ROOT)) {
            case "RS": {
                Object n = RSNetworkBridge.getNetwork(player);
                return n != null ? new NetworkHandle(0, n) : null;
            }
            case "BD": {
                Object n = BDNetworkBridge.getNetwork(player);
                return n != null ? new NetworkHandle(1, n) : null;
            }
            case "AE2": {
                Object n = AE2NetworkBridge.getNetwork(player);
                return n != null ? new NetworkHandle(2, n) : null;
            }
            case "BACKPACK": {
                List<?> bp = BackpackNetworkBridge.getHandlers(player);
                return (bp != null && !bp.isEmpty()) ? new NetworkHandle(3, bp) : null;
            }
            case "TOMS": {
                List<?> ts = TomsStorageNetworkBridge.getHandlers(player);
                return (ts != null && !ts.isEmpty()) ? new NetworkHandle(4, ts) : null;
            }
            default:
                return null;
        }
    }

    /**
     * 从全部可用网络中取出指定模板的物品（上限 count）。
     * 按优先级逐个网络回落：先在第一个网络尝试，取不够则到下一个网络补齐，
     * 直到取满或所有网络都尝试完毕。返回汇总后的物品（可能来自多个网络）。
     */
    public static ItemStack extract(List<NetworkHandle> nets, ItemStack template, int count) {
        if (nets == null || nets.isEmpty() || template.isEmpty()) return ItemStack.EMPTY;
        int remaining = count;
        ItemStack collected = ItemStack.EMPTY;
        for (NetworkHandle h : nets) {
            if (remaining <= 0) break;
            ItemStack got = extractSingle(h, template, remaining);
            if (!got.isEmpty()) {
                if (collected.isEmpty()) collected = got.copy();
                else collected.grow(got.getCount());
                remaining -= got.getCount();
            }
        }
        return collected;
    }

    /**
     * 把物品存入可用网络：依次尝试每个网络直到存完，剩余存不下的退回调用方。
     * 例如先存入 RS，RS 满则把余下部分存入 AE2。
     */
    public static ItemStack insert(List<NetworkHandle> nets, ItemStack stack) {
        if (nets == null || nets.isEmpty() || stack.isEmpty()) return stack;
        ItemStack remaining = stack.copy();
        for (NetworkHandle h : nets) {
            if (remaining.isEmpty()) break;
            remaining = insertSingle(h, remaining);
        }
        return remaining;
    }

    /** 列出全部可用网络中的物品（相同物品按 NBT 合并，避免多网络重复条目）。 */
    public static List<ItemStack> listItems(List<NetworkHandle> nets) {
        if (nets == null || nets.isEmpty()) return java.util.Collections.emptyList();
        List<ItemStack> out = new ArrayList<>();
        for (NetworkHandle h : nets) {
            out.addAll(listItemsSingle(h));
        }
        return mergeStacks(out);
    }

    // ---- 单网络分发（内部使用） ----

    private static ItemStack extractSingle(NetworkHandle h, ItemStack template, int count) {
        switch (h.kind) {
            case 0: return RSNetworkBridge.extract(h.net, template, count);
            case 1: return BDNetworkBridge.extract(h.net, template, count);
            case 2: return AE2NetworkBridge.extract(h.net, template, count);
            case 3: return BackpackNetworkBridge.extract(h.net, template, count);
            default: return BackpackNetworkBridge.extract(h.net, template, count);
        }
    }

    private static ItemStack insertSingle(NetworkHandle h, ItemStack stack) {
        switch (h.kind) {
            case 0: return RSNetworkBridge.insert(h.net, stack);
            case 1: return BDNetworkBridge.insert(h.net, stack);
            case 2: return AE2NetworkBridge.insert(h.net, stack);
            case 3: return BackpackNetworkBridge.insert(h.net, stack);
            default: return BackpackNetworkBridge.insert(h.net, stack);
        }
    }

    private static List<ItemStack> listItemsSingle(NetworkHandle h) {
        switch (h.kind) {
            case 0: return RSNetworkBridge.listItems(h.net);
            case 1: return BDNetworkBridge.listItems(h.net);
            case 2: return AE2NetworkBridge.listItems(h.net);
            case 3: return BackpackNetworkBridge.listItems(h.net);
            default: return BackpackNetworkBridge.listItems(h.net);
        }
    }

    /** 合并相同物品（同物品同 NBT）的堆，避免多个网络下列表出现重复条目。 */
    private static List<ItemStack> mergeStacks(List<ItemStack> src) {
        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack s : src) {
            if (s.isEmpty()) continue;
            boolean added = false;
            for (ItemStack m : merged) {
                if (ItemStack.isSameItemSameTags(m, s)) {
                    m.grow(s.getCount());
                    added = true;
                    break;
                }
            }
            if (!added) merged.add(s.copy());
        }
        return merged;
    }

    public static String noNetworkMessage() {
        return "§c未检测到 RS 无线终端、refinedstorageaddons 无线合成网格、Beyond Dimensions 维度网络、AE2 无线终端、Sophisticated Backpacks 背包，或附近/绑定的 Tom's Storage 存储终端";
    }

    /**
     * 针对“取不到网络”的细分诊断：逐条列出每个已安装存储模组的未通过原因，
     * 让玩家一眼看清到底卡在哪一步，而不是只看到笼统的“未检测到”。
     *
     * 若玩家实际上持有已绑定的 RS/AE2 无线终端且网络在线，调用方应先走
     * {@link #getNetwork(Player)}，非 null 时根本不会调用本方法。
     */
    public static String diagnose(Player player) {
        boolean anyLoaded = isAnyLoaded();
        if (!anyLoaded) {
            return "§c未安装任何受支持的存储模组（Refined Storage / Beyond Dimensions / Applied Energistics 2 / Sophisticated Backpacks / Tom's Storage）";
        }
        StringBuilder sb = new StringBuilder("§c未能连接到存储网络，请检查：\n");
        if (RSNetworkBridge.isRSLoaded()) {
            if (!RSNetworkBridge.hasWirelessTerminal(player)) {
                sb.append("§e• 未持有 RS 无线终端（或 refinedstorageaddons 无线合成网格）：请放入背包 / 盔甲 / 副手 / Curios 饰品栏\n");
            } else if (!RSNetworkBridge.hasBoundWirelessTerminal(player)) {
                sb.append("§e• 持有 RS 无线终端但未绑定：请在游戏内§f潜行右键 RS 控制器或线缆§e完成绑定（refinedstorageaddons 无线合成网格同理）\n");
            } else {
                sb.append("§e• 已绑定 RS 无线终端，但对应网络不存在或离线：请确认控制器已通电、网络在线\n");
            }
        }
        if (AE2NetworkBridge.isAE2Loaded()) {
            if (!AE2NetworkBridge.hasWirelessTerminal(player)) {
                sb.append("§e• 未持有 AE2 无线终端（请放入背包 / 盔甲 / 副手 / Curios 饰品栏）\n");
            } else if (AE2NetworkBridge.getNetwork(player) == null) {
                sb.append("§e• 持有 AE2 无线终端但未链接或已离线：请在游戏内将终端链接到无线接入点并保持供电\n");
            }
        }
        if (BDNetworkBridge.isBDLoaded()) {
            if (BDNetworkBridge.getNetwork(player) == null) {
                sb.append("§e• Beyond Dimensions 维度网络未就绪：需先创建维度网络，且当前维度可访问\n");
            }
        }
        if (BackpackNetworkBridge.isSBLoaded()) {
            List<?> bp = BackpackNetworkBridge.getHandlers(player);
            if (bp == null || bp.isEmpty()) {
                sb.append("§e• 未装备 Sophisticated Backpacks 背包：请背在身上或放入 Curios 背部槽\n");
            }
        }
        if (TomsStorageNetworkBridge.isTSLoaded()) {
            List<?> ts = TomsStorageNetworkBridge.getHandlers(player);
            if (ts == null || ts.isEmpty()) {
                sb.append("§e• 未检测到 Tom's Storage 终端：请手持已绑定的高级无线终端，或站在存储终端附近\n");
            }
        }
        return sb.toString();
    }
}
