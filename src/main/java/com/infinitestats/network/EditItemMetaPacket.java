package com.infinitestats.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 物品元数据编辑数据包（客户端 → 服务器）
 * <p>
 * 编辑主手物品的显示名称、Lore 描述行、剩余耐久、堆叠数量。
 * 与附魔/属性分开处理，避免相互覆盖。
 */
public final class EditItemMetaPacket {

    private final boolean changeName;
    private final String name;          // changeName=true 时有效，空串表示清除名称
    private final boolean changeLore;
    private final List<String> lore;    // changeLore=true 时有效，空列表表示清空 Lore
    private final int damage;           // >=0 表示设置剩余耐久；-1 表示不改
    private final int count;            // >=0 表示设置堆叠；-1 表示不改

    public EditItemMetaPacket(boolean changeName, String name, boolean changeLore,
                              List<String> lore, int damage, int count) {
        this.changeName = changeName;
        this.name = name;
        this.changeLore = changeLore;
        this.lore = lore;
        this.damage = damage;
        this.count = count;
    }

    public static void encode(EditItemMetaPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.changeName);
        if (msg.changeName) buf.writeUtf(msg.name);
        buf.writeBoolean(msg.changeLore);
        buf.writeVarInt(msg.lore.size());
        for (String s : msg.lore) buf.writeUtf(s);
        buf.writeInt(msg.damage);
        buf.writeInt(msg.count);
    }

    public static EditItemMetaPacket decode(FriendlyByteBuf buf) {
        boolean changeName = buf.readBoolean();
        String name = changeName ? buf.readUtf() : "";
        boolean changeLore = buf.readBoolean();
        int lc = buf.readVarInt();
        List<String> lore = new ArrayList<>();
        for (int i = 0; i < lc; i++) lore.add(buf.readUtf());
        int damage = buf.readInt();
        int count = buf.readInt();
        return new EditItemMetaPacket(changeName, name, changeLore, lore, damage, count);
    }

    public static void handle(EditItemMetaPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
            if (stack.isEmpty()) return;

            if (msg.changeName) {
                CompoundTag display = stack.getOrCreateTagElement("display");
                if (msg.name.isEmpty()) {
                    display.remove("Name");
                    if (display.isEmpty()) stack.removeTagKey("display");
                } else {
                    display.putString("Name", Component.Serializer.toJson(Component.literal(msg.name)));
                }
            }

            if (msg.changeLore) {
                CompoundTag display = stack.getOrCreateTagElement("display");
                if (msg.lore.isEmpty()) {
                    display.remove("Lore");
                } else {
                    ListTag loreTag = new ListTag();
                    for (String line : msg.lore) {
                        loreTag.add(StringTag.valueOf(
                                Component.Serializer.toJson(Component.literal(line))));
                    }
                    display.put("Lore", loreTag);
                }
                if (display.isEmpty()) stack.removeTagKey("display");
            }

            if (msg.damage >= 0 && stack.getMaxDamage() > 0) {
                stack.setDamageValue(Mth.clamp(msg.damage, 0, stack.getMaxDamage()));
            }

            if (msg.count >= 0) {
                stack.setCount(Mth.clamp(msg.count, 1, stack.getMaxStackSize()));
            }

            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        });
        ctx.get().setPacketHandled(true);
    }
}
