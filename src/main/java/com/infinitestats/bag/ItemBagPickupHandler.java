package com.infinitestats.bag;

import com.infinitestats.Config;
import com.infinitestats.InfiniteStats;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * 「获得物品直接进背包」：在原版把物品塞进 36 格之前抢先收进 {@link ItemBag}。
 *
 * <h2>拦截点为什么是 {@link EntityItemPickupEvent}</h2>
 * 所有「捡起地上的东西」的路径（玩家走过去碰、磁铁属性调 {@code ItemEntity#playerTouch}、
 * 掉落物被吸过来）最终都会在 {@code ItemEntity#playerTouch} 里发这个事件，
 * 取消后原版不再往玩家背包里 {@code add}。合成产物、/give、容器移动这类「直接进背包」
 * 的操作不经过这里 —— 那些仍按原版行为走，玩家想收进来就用界面里的「全部存入」。
 *
 * <h2>为什么还要自己调 {@code Player#take}</h2>
 * 取消事件只是让原版「不要往背包里放」，物品实体仍然躺在地上。{@code take} 负责
 * 播放拾取音效、走统计 / 成就、把实体数量扣掉或删掉 —— 少了它，物品会一直跟着玩家闪。
 */
@Mod.EventBusSubscriber(modid = InfiniteStats.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ItemBagPickupHandler {

    private ItemBagPickupHandler() {
    }

    /**
     * 优先级用 HIGH：要排在「背包模组自己的拾取逻辑」之前，否则物品会先被别人的背包吃掉，
     * 轮不到这里判断。
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onItemPickup(EntityItemPickupEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isSpectator() || player.isDeadOrDying()) return;
        if (!Config.ITEM_BAG_AUTO_PICKUP.get()) return;

        PlayerStats stats = player.getCapability(PlayerStatsProvider.PLAYER_STATS).orElse(null);
        if (stats == null || !stats.isToggleActive("item_bag")) return;

        ItemEntity itemEntity = event.getItem();
        if (itemEntity == null || itemEntity.isRemoved() || itemEntity.hasPickUpDelay()) return;

        // 自己刚丢出来的东西 5 秒内不自动收：否则「丢掉背包里的垃圾」会原地被捡回来，
        // 玩家看到的就是「丢不掉」。5 秒后恢复正常（真的想扔远点就走开再丢）。
        if (itemEntity.getAge() < 100 && player.getUUID().equals(itemEntity.getOwner())) return;

        ItemStack stack = itemEntity.getItem();
        if (stack.isEmpty()) return;
        if (isBlacklisted(stack)) return;

        ItemBag bag = player.getCapability(ItemBagProvider.ITEM_BAG).orElse(null);
        if (bag == null) return;

        int before = stack.getCount();
        long remaining = bag.insert(stack);
        if (remaining >= before) return; // 一个都没进去：交回原版处理

        int moved = (int) (before - remaining);
        event.setCanceled(true);
        // 物品实体里剩下的部分（正常为 0）会留在原地，下一 tick 还能再捡
        player.take(itemEntity, moved);
        SoundSource source = SoundSource.PLAYERS;
        player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, source,
                0.2F, 1.0F + (player.getRandom().nextFloat() - 0.5F) * 0.4F);
    }

    /** 配置黑名单：物品 id，前缀 {@code #} 表示物品标签。 */
    private static boolean isBlacklisted(ItemStack stack) {
        List<? extends String> raw = Config.ITEM_BAG_PICKUP_BLACKLIST.get();
        if (raw == null || raw.isEmpty()) return false;

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String idStr = id != null ? id.toString() : null;
        List<TagKey<Item>> tags = null;
        for (String entry : raw) {
            if (entry == null) continue;
            String value = entry.trim();
            if (value.isEmpty()) continue;
            if (value.startsWith("#")) {
                ResourceLocation tagId = ResourceLocation.tryParse(value.substring(1));
                if (tagId == null) continue;
                if (tags == null) tags = new ArrayList<>(2);
                tags.add(TagKey.create(Registries.ITEM, tagId));
            } else if (value.equals(idStr)) {
                return true;
            }
        }
        if (tags != null) {
            for (TagKey<Item> tag : tags) {
                if (stack.is(tag)) return true;
            }
        }
        return false;
    }
}
