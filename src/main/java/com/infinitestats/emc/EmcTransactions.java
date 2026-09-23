package com.infinitestats.emc;

import com.infinitestats.Config;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** 服务端提取结算：只对实际交付的物品扣款。 */
public final class EmcTransactions {
    private EmcTransactions() {}

    public static void extract(ServerPlayer player, EmcPlayerData data, ResourceLocation id, int count) {
        if (!Config.EMC_ENABLED.get() || !data.hasLearned(id)) return;
        ItemStack template = new ItemStack(BuiltInRegistries.ITEM.get(id));
        var tag = data.getItemNbt(id);
        if (tag != null) template.setTag(tag.copy());
        long price = EmcDatabase.getEmc(template);
        if (price <= 0) return;
        int maxStack = Math.max(1, template.getMaxStackSize());
        int batches = count < 0 ? player.getInventory().getContainerSize() : 1;
        for (int batch = 0; batch < batches; batch++) {
            long affordable = data.getEmcBalance() / price;
            int wanted = count < 0 ? maxStack : Math.min(Math.max(0, count), maxStack);
            int give = (int) Math.min(affordable, wanted);
            if (give <= 0 || !data.consumeEmc(give * price)) break;
            ItemStack result = template.copyWithCount(give);
            player.getInventory().add(result);
            if (!result.isEmpty()) {
                if (count < 0) data.addEmc(result.getCount() * price);
                else player.drop(result, false);
                break;
            }
        }
    }
}
