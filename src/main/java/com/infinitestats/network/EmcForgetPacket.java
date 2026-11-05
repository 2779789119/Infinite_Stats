package com.infinitestats.network;

import com.infinitestats.Config;
import com.infinitestats.emc.EmcPlayerDataProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

/**
 * EMC 遗忘（客户端 → 服务器）：把一条已学知识从玩家的已学列表里删掉。
 * <p>
 * 已学列表此前只增不减 —— 长期游玩后几百上千个条目会把转化桌的网格挤满，搜索之外没有别的清理手段；
 * 带 NBT 的物品（手册之类）学错了版本，也没有重新学习的入口。
 * <p>
 * <b>不返还 EMC</b>：遗忘只是清掉这条知识，不是退货。学习时已经到账的 EMC 不会扣回，也不会多给，
 * 因此不存在「学一遍 → 遗忘 → 再学」的刷 EMC 空子。遗忘由服务端执行并重新下发快照，
 * 客户端的列表与可提取状态立即同步；{@code EMC.emcEnabled} 关闭时请求直接忽略。
 */
public final class EmcForgetPacket {

    private final String itemId;

    public EmcForgetPacket(String itemId) {
        this.itemId = itemId == null ? "" : itemId;
    }

    public static void encode(EmcForgetPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.itemId);
    }

    public static EmcForgetPacket decode(FriendlyByteBuf buf) {
        return new EmcForgetPacket(buf.readUtf());
    }

    public static void handle(EmcForgetPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !Config.EMC_ENABLED.get()) return;

            ResourceLocation itemId = ResourceLocation.tryParse(msg.itemId);
            if (itemId == null) return;

            EmcPlayerDataProvider.of(player).ifPresent(data -> {
                if (!data.forgetItem(itemId)) return;

                NetworkHandler.syncEmcToClient(player);
                Component name = new ItemStack(BuiltInRegistries.ITEM.get(itemId)).getHoverName();
                player.displayClientMessage(
                        Component.translatable("message.infinitestats.emc.forgotten", name), false);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
