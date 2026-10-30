package com.infinitestats.network;

import com.infinitestats.compat.CuriosBridge;
import com.infinitestats.util.ItemEditUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Supplier;

/** 保存实际编辑的列表；空列表表示明确清空，null 表示保持原样。 */
public final class EditItemPacket {
    private final int selectedSlot;
    private final ResourceLocation itemId;
    private final CompoundTag expected;
    private final ListTag enchants;
    private final ListTag attributes;
    private final ListTag curioAttributes;

    public EditItemPacket(int selectedSlot, ResourceLocation itemId, CompoundTag expected,
                          ListTag enchants, ListTag attributes) {
        this(selectedSlot, itemId, expected, enchants, attributes, null);
    }

    public EditItemPacket(int selectedSlot, ResourceLocation itemId, CompoundTag expected,
                          ListTag enchants, ListTag attributes, ListTag curioAttributes) {
        this.selectedSlot = selectedSlot;
        this.itemId = itemId;
        this.expected = expected.copy();
        this.enchants = enchants == null ? null : enchants.copy();
        this.attributes = attributes == null ? null : attributes.copy();
        this.curioAttributes = curioAttributes == null ? null : curioAttributes.copy();
    }

    public static void encode(EditItemPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.selectedSlot);
        buf.writeResourceLocation(msg.itemId);
        buf.writeNbt(msg.expected);
        CompoundTag changes = new CompoundTag();
        if (msg.enchants != null) changes.put("Enchantments", msg.enchants);
        if (msg.attributes != null) changes.put("AttributeModifiers", msg.attributes);
        if (msg.curioAttributes != null) changes.put(ItemEditUtil.CURIO_KEY, msg.curioAttributes);
        buf.writeNbt(changes);
    }

    public static EditItemPacket decode(FriendlyByteBuf buf) {
        int slot = buf.readVarInt();
        ResourceLocation item = buf.readResourceLocation();
        CompoundTag expected = buf.readNbt();
        CompoundTag changes = buf.readNbt();
        if (expected == null || changes == null) throw new IllegalArgumentException("Missing item edit data");
        return new EditItemPacket(slot, item, expected,
                changes.contains("Enchantments", Tag.TAG_LIST) ? changes.getList("Enchantments", Tag.TAG_COMPOUND) : null,
                changes.contains("AttributeModifiers", Tag.TAG_LIST) ? changes.getList("AttributeModifiers", Tag.TAG_COMPOUND) : null,
                changes.contains(ItemEditUtil.CURIO_KEY, Tag.TAG_LIST)
                        ? changes.getList(ItemEditUtil.CURIO_KEY, Tag.TAG_COMPOUND) : null);
    }

    /** 失败返回 EMPTY，原物品从不被部分改写。 */
    public ItemStack editedCopy(ItemStack stack) {
        if (stack.isEmpty() || !itemId.equals(ForgeRegistries.ITEMS.getKey(stack.getItem()))) return ItemStack.EMPTY;
        if (enchants != null && (!ItemEditUtil.sameSection(expected, stack, ItemEditUtil.enchantmentKey(stack))
                || !ItemEditUtil.validEnchantments(enchants))) return ItemStack.EMPTY;
        if (attributes != null && (!ItemEditUtil.sameSection(expected, stack, "AttributeModifiers")
                || !ItemEditUtil.validAttributes(attributes))) return ItemStack.EMPTY;
        if (curioAttributes != null && (!ItemEditUtil.sameSection(expected, stack, ItemEditUtil.CURIO_KEY)
                || !ItemEditUtil.validCurioAttributes(curioAttributes))) return ItemStack.EMPTY;
        return ItemEditUtil.apply(stack, enchants, attributes, curioAttributes);
    }

    public static void handle(EditItemPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (msg.selectedSlot < 0 || msg.selectedSlot > 8
                    || player.getInventory().selected != msg.selectedSlot) {
                player.displayClientMessage(Component.translatable("screen.infinitestats.item_editor.changed"), true);
                return;
            }
            ItemStack edited = msg.editedCopy(player.getMainHandItem());
            if (edited.isEmpty()) {
                player.displayClientMessage(Component.translatable("screen.infinitestats.item_editor.changed"), true);
                return;
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, edited);
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();

            int curioCount = msg.curioAttributes == null ? 0 : msg.curioAttributes.size();
            if (curioCount > 0) {
                // 饰品属性写进了 CurioAttributeModifiers，条数一并回显，方便确认到底写没写进去
                // 走聊天栏而不是快捷栏上方，免得被模组自带的 actionbar 提示抢位置
                player.displayClientMessage(Component.translatable(
                        "screen.infinitestats.item_editor.applied_detail",
                        msg.attributes == null ? 0 : msg.attributes.size(), curioCount), false);
                if (!CuriosBridge.isCurioItem(edited)) {
                    player.displayClientMessage(Component.translatable(
                            "screen.infinitestats.item_editor.curio_not_curio"), false);
                }
            } else {
                player.displayClientMessage(Component.translatable("screen.infinitestats.item_editor.applied"), true);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
