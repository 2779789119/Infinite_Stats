package com.infinitestats.emc;

import com.infinitestats.Config;
import com.infinitestats.compat.NetworkIO;
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
        long price = EmcPricing.buyPrice(player, template);
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
                // 与存储网络联动：背包放不下的部分优先写入已连接的存储（RS / AE2 / 汤姆存储 / 背包 / BD）
                result = NetworkIO.insert(NetworkIO.getNetworks(player), result);
            }
            if (!result.isEmpty()) {
                if (count < 0) data.addEmc(result.getCount() * price);
                else player.drop(result, false);
                break;
            }
        }
    }

    /**
     * 以 EMC 采购物品并**直接交给调用方**（随身工作台 / 随身熔炉把材料塞进自己的槽位用）。
     * 与 {@link #extract} 的区别：完全不碰玩家背包，也不做存储网络兜底。
     *
     * @param template 采购的物品模板（其 NBT 会被保留；是否已学按其物品 id 判定）
     * @param count    期望数量（超过该物品的堆叠上限或余额承受力时自动减少）
     * @return 实际采购到的物品堆；未学 / 没有 EMC 值 / 余额不足 / EMC 关闭时返回空
     */
    public static ItemStack purchase(ServerPlayer player, EmcPlayerData data, ItemStack template, int count) {
        if (!Config.EMC_ENABLED.get() || data == null || template.isEmpty() || count <= 0) return ItemStack.EMPTY;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(template.getItem());
        if (!data.hasLearned(id)) return ItemStack.EMPTY;
        long price = EmcPricing.buyPrice(player, template);
        if (price <= 0) return ItemStack.EMPTY;
        long affordable = data.getEmcBalance() / price;
        int give = (int) Math.min(affordable, (long) Math.min(count, Math.max(1, template.getMaxStackSize())));
        if (give <= 0) return ItemStack.EMPTY;
        if (!data.consumeEmc(give * price)) return ItemStack.EMPTY;
        return template.copyWithCount(give);
    }

    /** 大数值缩写（K/M/B/T/Qa/Qi），各界面统一用它显示 EMC。 */
    public static String format(long value) {
        if (value < 1000L) return Long.toString(value);
        String[] units = {"", "K", "M", "B", "T", "Qa", "Qi"};
        int tier = (int) (Math.log10(value) / 3);
        if (tier >= units.length) tier = units.length - 1;
        double scaled = value / Math.pow(1000.0, tier);
        String num = scaled >= 100 ? String.format("%.0f", scaled)
                : scaled >= 10 ? String.format("%.1f", scaled)
                : String.format("%.2f", scaled);
        return num + units[tier];
    }
}
