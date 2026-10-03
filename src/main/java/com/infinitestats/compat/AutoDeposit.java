package com.infinitestats.compat;

import com.infinitestats.Config;
import com.infinitestats.stats.PlayerStats;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

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
 */
public final class AutoDeposit {

    private AutoDeposit() {
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
