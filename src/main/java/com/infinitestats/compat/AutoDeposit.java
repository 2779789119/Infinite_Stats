package com.infinitestats.compat;

import com.infinitestats.Config;
import com.infinitestats.stats.PlayerStats;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「自动入库」：把玩家背包里的物品自动写入存储网络（RS / AE2 / 汤姆存储 / 背包 / BD）。
 * <p>
 * 写入顺序由玩家在属性面板里设置的存储优先级决定（见 {@link PlayerStats#getEffectiveAutoDepositPriority()}），
 * 优先级靠前的网络先写入，装不下的部分自动回落到下一个网络。
 * <p>
 * 安全约束：
 * <ul>
 *   <li>存储终端（RS / AE2 无线终端）与背包本身永不入库 —— 否则一次扫描就会把「连网凭证」自己存走，
 *       玩家当场失去对存储网络的访问能力；</li>
 *   <li>支持配置黑名单（物品 ID，前缀 {@code #} 表示物品标签）；</li>
 *   <li>默认不碰快捷栏（0-8 号槽），避免把随身工具/武器吸走，可由配置改为只保留当前手持那一格。</li>
 * </ul>
 * <p>
 * <b>触发方式：背包变了才查存储</b>。原先是每 tick 取模固定间隔无条件扫一遍背包，即使玩家一直站着不动、
 * 背包里什么也没发生，也照样每秒去问一次存储网络（这一步要反射进 RS / AE2 / 汤姆存储，是本模块最贵的部分）。
 * 现在改成变更驱动：{@code InventoryChangeMixin} 在 {@code Inventory#setChanged / setItem / add} 上打标记
 * （见 {@link #markInventoryChanged(Player)}），{@link #tick} 每 tick 只做一次 {@code Set} 查询，
 * 有标记才真正执行 {@link #deposit}。配置的项目间隔从「扫描间隔」变成「两次执行之间的最短间隔」，
 * 用来避免一次批量拾取 / 连续合成里反复查询存储网络。
 */
public final class AutoDeposit {

    /** 背包内容发生过变化、等着下次入库的玩家。 */
    private static final Set<UUID> DIRTY = ConcurrentHashMap.newKeySet();
    /** 玩家 UUID → 上一次真正执行入库的 tickCount（节流用，tickCount 取 {@code ServerPlayer#tickCount}）。 */
    private static final Map<UUID, Long> LAST_RUN = new ConcurrentHashMap<>();
    /**
     * 正在执行入库：入库自己也会写背包（{@code items.set} + {@code setChanged}），
     * 不屏蔽的话每次执行都会重新打上标记，变成「每 tick 空跑一次存储网络查询」。
     */
    private static boolean depositing;

    private AutoDeposit() {
    }

    /**
     * 背包内容变化时由 Mixin 调用：只记一个标记，不做任何查询。
     * <p>
     * 客户端背包同样会走到这里，非 {@link ServerPlayer} 直接返回；入库自己写的背包也不记。
     */
    public static void markInventoryChanged(Player player) {
        if (depositing) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        DIRTY.add(serverPlayer.getUUID());
    }

    /** 玩家退出时清掉会话级记录（{@code PlayerEvent.PlayerLoggedOutEvent} 调用）。 */
    public static void forget(Player player) {
        if (player == null) return;
        DIRTY.remove(player.getUUID());
        LAST_RUN.remove(player.getUUID());
    }

    /**
     * 每 tick 调用一次：背包这一 tick 内确实变过、并且已经过了配置的最短间隔，才真正查一次存储网络。
     * <p>
     * 没变化时只做一次 {@code Set.contains}，代价与「背包有多大」无关。
     */
    public static void tick(ServerPlayer player, PlayerStats stats, long tickCount) {
        UUID id = player.getUUID();
        if (!DIRTY.contains(id)) return;
        // 功能没解锁 / 被功能开关关掉：标记就地丢弃，别留到下次解锁
        if (!stats.isToggleActive("auto_deposit")) {
            DIRTY.remove(id);
            return;
        }
        // 当前不能入库（死亡 / 旁观 / 正在开别的容器）：标记<b>留着</b>，等条件满足再处理。
        // 这一步必须在消费标记之前 —— 否则玩家一边开箱子一边拾取，标记被吃掉、箱子关掉后又没有新的变更，
        // 这批物品就永远等不到入库了。
        if (player.isDeadOrDying() || player.isSpectator() || player.containerMenu != player.inventoryMenu) {
            return;
        }
        long interval = Math.max(1, Config.AUTO_DEPOSIT_INTERVAL.get());
        Long last = LAST_RUN.get(id);
        if (last != null) {
            long elapsed = tickCount - last;
            // elapsed < 0 ＝ 玩家实体换过（重生 / 换维度后 tickCount 归零），当作冷却已过
            if (elapsed >= 0 && elapsed < interval) return; // 标记保留，冷却结束的那个 tick 再处理
        }
        LAST_RUN.put(id, tickCount);
        DIRTY.remove(id);
        depositing = true;
        try {
            deposit(player, stats);
        } finally {
            depositing = false;
        }
    }

    /**
     * 执行一次自动入库扫描。
     *
     * @return 是否有物品被存入（供调用方决定是否需要广播容器变更）
     */
    public static boolean deposit(ServerPlayer player, PlayerStats stats) {
        if (player.isDeadOrDying() || player.isSpectator()) return false;
        // 玩家正在和别的容器交互时不打扰（例如开箱子、交易、随身工作台）
        if (player.containerMenu != player.inventoryMenu) return false;

        List<NetworkHandle> nets = NetworkIO.getNetworks(player, PlayerStats.SCOPE_AUTO_DEPOSIT);
        if (nets.isEmpty()) return false;

        List<ItemStack> protectedStacks = collectProtectedStacks(player);
        List<String> idBlacklist = new ArrayList<>();
        List<TagKey<Item>> tagBlacklist = new ArrayList<>();
        parseBlacklist(idBlacklist, tagBlacklist);

        Inventory inv = player.getInventory();
        boolean keepHotbar = Config.AUTO_DEPOSIT_KEEP_HOTBAR.get();
        int start = keepHotbar ? Inventory.getSelectionSize() : 0;

        boolean changed = false;
        for (int i = start; i < inv.items.size(); i++) {
            // 未保留快捷栏时，仍然放行当前手持的那一格（玩家此刻正在用它）
            if (!keepHotbar && i == inv.selected) continue;

            ItemStack stack = inv.items.get(i);
            if (stack.isEmpty()) continue;

            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            String idStr = id != null ? id.toString() : null;
            if (isProtected(stack, id, idStr, protectedStacks, idBlacklist, tagBlacklist)) continue;
            // 白 / 黑名单：由玩家在「自动入库过滤」界面维护（列表为空时不限制）
            if (!stats.allowsAutoDeposit(idStr)) continue;

            ItemStack remaining = NetworkIO.insert(nets, stack.copy());
            int moved = stack.getCount() - remaining.getCount();
            if (moved <= 0) continue;

            if (remaining.isEmpty()) {
                inv.items.set(i, ItemStack.EMPTY);
            } else {
                inv.items.set(i, remaining);
            }
            changed = true;
        }
        if (changed) inv.setChanged();
        return changed;
    }

    /** 收集「连网凭证」类物品，这些物品永远不能被自动存入存储。 */
    private static List<ItemStack> collectProtectedStacks(ServerPlayer player) {
        List<ItemStack> out = new ArrayList<>();
        try {
            out.addAll(RSNetworkBridge.findWirelessTerminals(player));
        } catch (Throwable ignored) {
            // RS 未加载 / 反射失败：忽略
        }
        try {
            ItemStack ae2 = AE2NetworkBridge.findWirelessTerminal(player);
            if (ae2 != null && !ae2.isEmpty()) out.add(ae2);
        } catch (Throwable ignored) {
            // AE2 未加载 / 反射失败：忽略
        }
        return out;
    }

    /** 解析配置黑名单：以 {@code #} 开头的是物品标签，其余按物品 ID 处理。 */
    private static void parseBlacklist(List<String> ids, List<TagKey<Item>> tags) {
        List<? extends String> raw = Config.AUTO_DEPOSIT_BLACKLIST.get();
        if (raw == null) return;
        for (String entry : raw) {
            if (entry == null) continue;
            String value = entry.trim();
            if (value.isEmpty()) continue;
            if (value.startsWith("#")) {
                ResourceLocation tagId = ResourceLocation.tryParse(value.substring(1));
                if (tagId != null) tags.add(TagKey.create(Registries.ITEM, tagId));
            } else {
                ResourceLocation id = ResourceLocation.tryParse(value);
                if (id != null) ids.add(id.toString());
            }
        }
    }

    private static boolean isProtected(ItemStack stack, ResourceLocation id, String idStr,
            List<ItemStack> protectedStacks, List<String> idBlacklist, List<TagKey<Item>> tagBlacklist) {
        for (ItemStack guarded : protectedStacks) {
            if (!guarded.isEmpty() && ItemStack.isSameItemSameTags(guarded, stack)) return true;
        }
        // Sophisticated Backpacks：背包本身不入库（避免把正在用的背包存进网络）
        try {
            if (BackpackNetworkBridge.isBackpack(stack)) return true;
        } catch (Throwable ignored) {
            // 未加载：忽略
        }

        for (TagKey<Item> tag : tagBlacklist) {
            if (stack.is(tag)) return true;
        }

        if (id == null) return false;
        // 汤姆存储的无线终端（该桥未暴露查找方法，按命名空间 + 路径识别）
        if ("toms_storage".equals(id.getNamespace()) && id.getPath().contains("wireless")) return true;
        return idBlacklist.contains(idStr);
    }
}
