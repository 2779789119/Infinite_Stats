package com.infinitestats.network;

import com.infinitestats.util.ItemEditUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Supplier;

/**
 * 物品特殊标签编辑数据包（客户端 → 服务器）。
 * <p>
 * 编辑主手物品的原版特殊标签：无法破坏（{@code Unbreakable}）、隐藏提示位掩码
 * （{@code HideFlags}）、自定义模型数据、染料颜色（{@code display.color}）、
 * 自定义药水颜色与铁砧累计惩罚。
 * <p>
 * 每个字段都是「可空」的：{@code null} 表示这一项没改，<b>不会</b>被写进物品 ——
 * 否则每次保存都会把没碰过的标签重写一遍，多模组环境下容易和别的编辑器互相覆盖。
 */
public final class EditItemTagsPacket {

    private final int selectedSlot;
    private final ResourceLocation itemId;
    private final CompoundTag expected;

    private final Boolean unbreakable;
    private final Integer hideFlags;
    private final Integer customModelData;
    private final Integer dyeColor;
    private final Integer potionColor;
    private final Integer repairCost;

    public EditItemTagsPacket(int selectedSlot, ResourceLocation itemId, CompoundTag expected,
                              Boolean unbreakable, Integer hideFlags, Integer customModelData,
                              Integer dyeColor, Integer potionColor, Integer repairCost) {
        this.selectedSlot = selectedSlot;
        this.itemId = itemId;
        this.expected = expected == null ? new CompoundTag() : expected.copy();
        this.unbreakable = unbreakable;
        this.hideFlags = hideFlags;
        this.customModelData = customModelData;
        this.dyeColor = dyeColor;
        this.potionColor = potionColor;
        this.repairCost = repairCost;
    }

    public static void encode(EditItemTagsPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.selectedSlot);
        buf.writeResourceLocation(msg.itemId);
        buf.writeNbt(msg.expected);

        // null = 不改；payload 里只放真正要写的项，服务端按 contains 决定写不写
        CompoundTag change = new CompoundTag();
        if (msg.unbreakable != null) change.putBoolean("Unbreakable", msg.unbreakable);
        if (msg.hideFlags != null) change.putInt("HideFlags", msg.hideFlags);
        if (msg.customModelData != null) change.putInt("CustomModelData", msg.customModelData);
        if (msg.dyeColor != null) change.putInt("DyeColor", msg.dyeColor);
        if (msg.potionColor != null) change.putInt("PotionColor", msg.potionColor);
        if (msg.repairCost != null) change.putInt("RepairCost", msg.repairCost);
        buf.writeNbt(change);
    }

    public static EditItemTagsPacket decode(FriendlyByteBuf buf) {
        int slot = buf.readVarInt();
        ResourceLocation item = buf.readResourceLocation();
        CompoundTag expected = buf.readNbt();
        CompoundTag change = buf.readNbt();
        if (expected == null || change == null) {
            throw new IllegalArgumentException("Missing item tag edit data");
        }
        return new EditItemTagsPacket(slot, item, expected,
                change.contains("Unbreakable") ? change.getBoolean("Unbreakable") : null,
                change.contains("HideFlags") ? change.getInt("HideFlags") : null,
                change.contains("CustomModelData") ? change.getInt("CustomModelData") : null,
                change.contains("DyeColor") ? change.getInt("DyeColor") : null,
                change.contains("PotionColor") ? change.getInt("PotionColor") : null,
                change.contains("RepairCost") ? change.getInt("RepairCost") : null);
    }

    /** 失败返回 EMPTY，原物品从不被部分改写。 */
    public ItemStack editedCopy(ItemStack stack) {
        if (stack.isEmpty() || !itemId.equals(ForgeRegistries.ITEMS.getKey(stack.getItem()))) {
            return ItemStack.EMPTY;
        }
        if (!ItemEditUtil.sameSpecial(expected, stack)) return ItemStack.EMPTY;
        if (!valid()) return ItemStack.EMPTY;
        return ItemEditUtil.applySpecial(stack, unbreakable, hideFlags, customModelData,
                dyeColor, potionColor, repairCost);
    }

    /** 边界检查：位掩码与颜色只收合法范围，避免脏数据写进物品。 */
    private boolean valid() {
        if (hideFlags != null && (hideFlags < 0 || hideFlags > ItemEditUtil.HIDE_ALL)) return false;
        if (customModelData != null && customModelData < -1) return false;
        if (dyeColor != null && (dyeColor < -1 || dyeColor > 0xFFFFFF)) return false;
        if (potionColor != null && (potionColor < -1 || potionColor > 0xFFFFFF)) return false;
        if (repairCost != null && repairCost < -1) return false;
        return true;
    }

    public static void handle(EditItemTagsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (msg.selectedSlot < 0 || msg.selectedSlot > 8
                    || player.getInventory().selected != msg.selectedSlot) {
                player.displayClientMessage(Component.translatable("screen.infinitestats.item_editor.changed"), true);
                return;
            }
            ItemStack edited = msg.editedCopy(player.getItemInHand(InteractionHand.MAIN_HAND));
            if (edited.isEmpty()) {
                player.displayClientMessage(Component.translatable("screen.infinitestats.item_editor.changed"), true);
                return;
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, edited);
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
            player.displayClientMessage(Component.translatable(
                    "screen.infinitestats.item_editor.tags_applied"), true);
        });
        ctx.get().setPacketHandled(true);
    }
}
