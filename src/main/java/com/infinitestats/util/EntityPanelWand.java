package com.infinitestats.util;

import com.infinitestats.stats.PlayerStats;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 「生物面板」（{@code entity_panel}）配套的<b>生物观察杖</b> —— 一根打了 NBT 标记的普通木棍。
 *
 * <h2>为什么是木棍 + NBT，而不是注册一个自定义物品</h2>
 * 需求就是「给予一根带有 NBT 的木棍」：木棍在原版对生物没有任何交互（不是牵引绳也不是命名牌），
 * 不会与任何原版 / 模组行为打架；而 NBT 标记足够做「这根是我的观察杖」的判定，
 * 不必为它新增物品注册项（也就省掉了一整套模型 / 贴图 / 创造物品栏 / 语言键）。
 *
 * <h2>发放与收回</h2>
 * 规则只有一条：<b>属性可用 ⇔ 背包里有观察杖</b>。由 {@link #sync} 单向对齐：
 * <ul>
 *   <li>属性激活但身上没有 → 补发一根（刚解锁、死亡掉落、被烧掉 / 被其它模组清掉都能自愈）；</li>
 *   <li>属性关闭 / 被「功能开关」关掉但身上还留着 → 全部收回，避免背包里留下一根没用的木棍。</li>
 * </ul>
 * 每 20 tick（1 秒）调用一次即可，见 {@code StatEventHandler#onPlayerTick}。
 */
public final class EntityPanelWand {

    /** 开关型属性 id（与 {@code StatType} 里注册的一致）。 */
    public static final String STAT_ID = "entity_panel";

    /** 木棍上标记「这是生物观察杖」的 NBT 键。 */
    private static final String TAG_WAND = "infinitestats:entity_panel_wand";

    private static final String NAME_KEY = "item.infinitestats.entity_panel_wand";
    private static final String HINT_KEY = "item.infinitestats.entity_panel_wand.hint";

    private EntityPanelWand() {}

    /** 造一根观察杖（木棍 + 标记 + 名称 + 一行用法说明）。 */
    public static ItemStack create() {
        ItemStack stack = new ItemStack(Items.STICK);
        stack.getOrCreateTag().putBoolean(TAG_WAND, true);
        stack.setHoverName(Component.translatable(NAME_KEY)
                .withStyle(ChatFormatting.GOLD)
                .withStyle(style -> style.withItalic(false)));

        ListTag lore = new ListTag();
        lore.add(StringTag.valueOf(Component.Serializer.toJson(
                Component.translatable(HINT_KEY)
                        .withStyle(ChatFormatting.GRAY)
                        .withStyle(style -> style.withItalic(false)))));
        stack.getOrCreateTagElement("display").put("Lore", lore);
        return stack;
    }

    /** 该物品是不是生物观察杖（木棍 + 标记，两者都要满足）。 */
    public static boolean isWand(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(Items.STICK)
                && stack.hasTag() && stack.getTag().getBoolean(TAG_WAND);
    }

    /** 手里（主手或副手）是否拿着观察杖。 */
    public static boolean isHeld(Player player) {
        return player != null
                && (isWand(player.getMainHandItem()) || isWand(player.getOffhandItem()));
    }

    /** 身上是否带着观察杖（含快捷栏 / 背包 / 护甲 / 副手）。 */
    public static boolean hasWand(Player player) {
        return findSlot(player) >= 0;
    }

    /** 观察杖所在槽位，没找到返回 -1。 */
    public static int findSlot(Player player) {
        if (player == null) return -1;
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (isWand(inventory.getItem(i))) return i;
        }
        return -1;
    }

    /**
     * 把「观察杖是否存在」对齐到属性状态：缺则补发、属性失效则收回。
     * 只在服务端调用。
     */
    public static void sync(ServerPlayer player, PlayerStats stats) {
        if (player == null) return;
        boolean shouldHave = stats != null && stats.isToggleActive(STAT_ID);
        int slot = findSlot(player);

        if (shouldHave) {
            if (slot < 0) give(player);
        } else if (slot >= 0) {
            removeAll(player);
        }
    }

    private static void give(ServerPlayer player) {
        ItemStack wand = create();
        // 背包塞满就掉在脚下，和 /give 的处理方式一致，不凭空消失
        if (!player.getInventory().add(wand)) player.drop(wand, false);
    }

    private static void removeAll(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (isWand(inventory.getItem(i))) inventory.setItem(i, ItemStack.EMPTY);
        }
    }
}
