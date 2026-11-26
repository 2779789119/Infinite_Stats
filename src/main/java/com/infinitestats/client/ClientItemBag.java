package com.infinitestats.client;

import com.infinitestats.bag.ItemBagProvider;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端侧的「无尽背包」内容缓存 —— 两个来源各存一份：
 * <ul>
 *   <li>{@link ItemBagProvider#SOURCE_BAG}：本模组背包里的物品；</li>
 *   <li>{@link ItemBagProvider#SOURCE_NETWORK}：已连接的其它存储（RS / AE2 / BD / 汤姆 / 背包模组）。</li>
 * </ul>
 * 内容由 {@code SyncItemBagPacket} 分批写入：{@code first} 的那批先清空再放，
 * 这样一次全量同步（多批）不会和上一份数据混在一起。
 */
public final class ClientItemBag {

    private static volatile List<ItemStack> bagItems = List.of();
    private static volatile List<ItemStack> networkItems = List.of();

    private static volatile long bagVersion;
    private static volatile long networkVersion;

    private static volatile boolean bagLoaded;
    private static volatile boolean networkLoaded;

    /** 网络真实种类数（可能大于实际收到的条数 —— 服务端会截断）。 */
    private static volatile int networkTotalKinds;

    private ClientItemBag() {
    }

    public static List<ItemStack> items(byte source) {
        return source == ItemBagProvider.SOURCE_NETWORK ? networkItems : bagItems;
    }

    public static long version(byte source) {
        return source == ItemBagProvider.SOURCE_NETWORK ? networkVersion : bagVersion;
    }

    public static boolean loaded(byte source) {
        return source == ItemBagProvider.SOURCE_NETWORK ? networkLoaded : bagLoaded;
    }

    public static int networkTotalKinds() {
        return networkTotalKinds;
    }

    public static void apply(byte source, List<ItemStack> batch, boolean first, int totalKinds) {
        boolean network = source == ItemBagProvider.SOURCE_NETWORK;
        List<ItemStack> current = network ? networkItems : bagItems;

        List<ItemStack> next;
        if (first) {
            next = new ArrayList<>(batch == null ? 0 : batch.size());
        } else {
            next = new ArrayList<>(current.size() + (batch == null ? 0 : batch.size()));
            next.addAll(current);
        }
        if (batch != null) {
            for (ItemStack stack : batch) {
                if (stack != null && !stack.isEmpty()) next.add(stack);
            }
        }

        if (network) {
            networkItems = next;
            networkLoaded = true;
            networkVersion++;
            networkTotalKinds = Math.max(totalKinds, next.size());
        } else {
            bagItems = next;
            bagLoaded = true;
            bagVersion++;
        }
    }

    public static void reset() {
        bagItems = List.of();
        networkItems = List.of();
        bagLoaded = false;
        networkLoaded = false;
        networkTotalKinds = 0;
        bagVersion++;
        networkVersion++;
    }
}
