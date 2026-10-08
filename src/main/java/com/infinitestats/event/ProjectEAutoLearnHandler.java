package com.infinitestats.event;

import com.infinitestats.Config;
import com.infinitestats.InfiniteStats;
import com.infinitestats.compat.ProjectEBridge;
import com.infinitestats.emc.EmcDatabase;
import com.infinitestats.emc.EmcPlayerData;
import com.infinitestats.emc.EmcPlayerDataProvider;
import com.infinitestats.network.NetworkHandler;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 等价交换「自动学习」事件处理器（常驻功能，不再依赖是否安装 ProjectE）。
 *
 * 双重门控：
 *  1. 全局开关 Config.PE_AUTO_LEARN —— 主开关，关闭则所有人的自动学习都无效
 *  2. 玩家加点 pe_auto_learn —— 每个玩家需在属性面板投入 5 点解锁
 *
 * 学习目标（两者同时写，谁在就写谁）：
 *  - 本模组内置 EMC 系统（{@link EmcPlayerData}）：把物品加进「已学」列表，
 *    之后可以在本模组的转化界面里直接用 EMC 取出。注意只学知识、不返还 EMC，
 *    与 ProjectE 的「知识共享」一致（白拿物品不会凭空变成 EMC）。
 *  - 原版 ProjectE（反射，{@link ProjectEBridge}）：安装时同时写入其转化知识库。
 *
 * 机制：
 *  - 即时触发：监听拾取、合成、烧炼等事件，立即学习。
 *  - 兜底扫描：每隔 2 秒对玩家「主背包 + 护甲 + 副手 + 末影箱」做一次全量扫描，
 *    覆盖宝箱 / 潜影盒 / 村民交易 / 酿造 / 钓鱼 / 铁砧 / 直接放入等所有获取途径。
 *  - 本地缓存：每个玩家记录已处理过的物品注册名，避免重复调用 API / 重复发包。
 */
@Mod.EventBusSubscriber(modid = InfiniteStats.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ProjectEAutoLearnHandler {

    /** 玩家 UUID -> 已处理过的物品注册名集合（本地缓存，避免重复学习）。 */
    private static final Map<UUID, Set<String>> LEARNED = new ConcurrentHashMap<>();

    /** 兜底扫描间隔（tick）：40 tick = 2 秒。 */
    private static final int SCAN_INTERVAL = 40;

    private ProjectEAutoLearnHandler() {
    }

    /**
     * 检查自动学习是否对给定玩家生效。
     * 条件：全局主开关开启 + 该玩家已投入足够点数解锁 pe_auto_learn 属性。
     * 不再要求安装 ProjectE —— 本模组内置的 EMC 系统同样会接收自动学习。
     */
    private static boolean enabled(Player player) {
        if (!Config.PE_AUTO_LEARN.get() || player == null) return false;
        PlayerStats stats = player.getCapability(PlayerStatsProvider.PLAYER_STATS).orElse(null);
        return stats != null && stats.isToggleActive("pe_auto_learn");
    }

    /**
     * 学习一个物品（带本地缓存）。每个玩家的同一物品只处理一次。
     * 本模组 EMC 系统与原版 ProjectE 的知识库都会被写入（各自存在时）。
     */
    private static void learn(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) return;
        if (player.level().isClientSide) return;

        boolean emcSystemOn = Config.EMC_ENABLED.get();
        boolean projectEOn = ProjectEBridge.isProjectELoaded();
        if (!emcSystemOn && !projectEOn) return; // 两套 EMC 系统都没启用，无需处理

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        Set<String> known = LEARNED.computeIfAbsent(player.getUUID(), k -> ConcurrentHashMap.newKeySet());
        if (known.contains(itemId.toString())) return;

        boolean handled = false;
        boolean changed = false;

        // 1) 本模组内置 EMC 系统：只要有 EMC 值就记入「已学」
        if (emcSystemOn && EmcDatabase.getEmc(stack) > 0) {
            EmcPlayerData data = player.getCapability(EmcPlayerDataProvider.EMC_PLAYER_DATA).orElse(null);
            if (data != null) {
                if (data.hasLearned(itemId)) {
                    handled = true;                     // 早已学过，无需再写
                } else {
                    data.learnItem(itemId, stack.getTag());
                    handled = true;
                    changed = true;
                }
            }
        }

        // 2) 原版 ProjectE：写入其转化知识库
        if (projectEOn) {
            if (ProjectEBridge.hasKnowledge(player, stack)) {
                handled = true;                         // ProjectE 那边已有该知识
            } else if (ProjectEBridge.learn(player, stack)) {
                handled = true;
            }
        }

        // 双方都不认这个物品时也记账，避免每 2 秒重复尝试
        if (handled || !emcSystemOn || EmcDatabase.getEmc(stack) <= 0) {
            known.add(itemId.toString());
        }

        if (changed && player instanceof ServerPlayer sp) {
            NetworkHandler.syncEmcToClient(sp);
        }
    }

    // ============ 即时触发事件 ============

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPickup(EntityItemPickupEvent event) {
        if (!enabled(event.getEntity())) return;
        learn(event.getEntity(), event.getItem().getItem());
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!enabled(event.getEntity())) return;
        learn(event.getEntity(), event.getCrafting());
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onSmelted(PlayerEvent.ItemSmeltedEvent event) {
        if (!enabled(event.getEntity())) return;
        learn(event.getEntity(), event.getSmelting());
    }

    // ============ 兜底全量扫描 ============

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player == null || player.level().isClientSide) return;
        if (!enabled(player)) return;
        // 每隔 SCAN_INTERVAL tick 扫描一次；tickCount 为玩家存活总 tick，取模即可均匀分布
        if ((player.tickCount % SCAN_INTERVAL) != 0) return;
        scanInventory(player);
    }

    /** 扫描玩家主背包、护甲、副手、末影箱，学习其中所有未学过的物品。 */
    private static void scanInventory(Player player) {
        var inv = player.getInventory();
        for (ItemStack s : inv.items) learn(player, s);
        for (ItemStack s : inv.armor) learn(player, s);
        for (ItemStack s : inv.offhand) learn(player, s);
        var ec = player.getEnderChestInventory();
        for (int i = 0; i < ec.getContainerSize(); i++) learn(player, ec.getItem(i));
    }

    // ============ 登录 / 登出 ============

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!enabled(event.getEntity())) return;
        scanInventory(event.getEntity());
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        // 无论自动学习是否激活，都释放缓存，避免内存泄漏
        LEARNED.remove(event.getEntity().getUUID());
    }
}
