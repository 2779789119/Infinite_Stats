package com.infinitestats.network;

import com.infinitestats.util.ItemEditUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

/**
 * 物品特殊标签编辑数据包（客户端 → 服务器）。
 * <p>
 * 编辑主手物品的那些「平时只能用命令改」的数据组件：无法破坏（{@code UNBREAKABLE}）、
 * 隐藏提示（{@code HIDE_TOOLTIP} / {@code HIDE_ADDITIONAL_TOOLTIP}）、自定义模型数据、
 * 染料颜色、自定义药水颜色与铁砧累计惩罚。
 * <p>
 * 每个字段都是「可空」的：包里的 {@code change} 标签里没有那个键就表示这一项没改，
 * <b>不会</b>被写进物品 —— 否则每次保存都会把没碰过的组件重写一遍，多模组环境下容易互相覆盖。
 */
public final class EditItemTagsPacket {

    private final int selectedSlot;
    private final ResourceLocation itemId;
    private final CompoundTag expected;
    private final CompoundTag change;

    public EditItemTagsPacket(int selectedSlot, ResourceLocation itemId, CompoundTag expected,
                              Boolean unbreakable, Boolean hideTooltip, Boolean hideAdditional,
                              Integer customModelData, Integer dyeColor, Integer potionColor,
                              Integer repairCost) {
        this.selectedSlot = selectedSlot;
        this.itemId = itemId;
        this.expected = expected == null ? new CompoundTag() : expected.copy();
        // null = 不改；只把真正要写的项放进包里
        CompoundTag out = new CompoundTag();
        if (unbreakable != null) out.putBoolean(ItemEditUtil.KEY_UNBREAKABLE, unbreakable);
        if (hideTooltip != null) out.putBoolean(ItemEditUtil.KEY_HIDE_TOOLTIP, hideTooltip);
        if (hideAdditional != null) out.putBoolean(ItemEditUtil.KEY_HIDE_ADDITIONAL, hideAdditional);
        if (customModelData != null) out.putInt(ItemEditUtil.KEY_CUSTOM_MODEL_DATA, customModelData);
        if (dyeColor != null) out.putInt(ItemEditUtil.SNAP_DYE_COLOR, dyeColor);
        if (potionColor != null) out.putInt(ItemEditUtil.KEY_POTION_COLOR, potionColor);
        if (repairCost != null) out.putInt(ItemEditUtil.KEY_REPAIR_COST, repairCost);
        this.change = out;
    }

    public static void encode(EditItemTagsPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.selectedSlot);
        buf.writeResourceLocation(msg.itemId);
        buf.writeNbt(msg.expected);
        buf.writeNbt(msg.change);
    }

    public static EditItemTagsPacket decode(FriendlyByteBuf buf) {
        int slot = buf.readVarInt();
        ResourceLocation item = buf.readResourceLocation();
        CompoundTag expected = buf.readNbt();
        CompoundTag change = buf.readNbt();
        if (expected == null || change == null) {
            throw new IllegalArgumentException("Missing item tag edit data");
        }
        return new EditItemTagsPacket(slot, item, expected, change);
    }

    /** 解码用（change 已经是 NBT）。 */
    private EditItemTagsPacket(int selectedSlot, ResourceLocation itemId,
                               CompoundTag expected, CompoundTag change) {
        this.selectedSlot = selectedSlot;
        this.itemId = itemId;
        this.expected = expected.copy();
        this.change = change.copy();
    }

    /**
     * 按包里的 change 生成新物品；任何一项校验失败都返回 {@code EMPTY}，
     * 保证原物品不会被部分改写。
     */
    public ItemStack editedCopy(ItemStack stack) {
        if (stack.isEmpty() || !itemId.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
            return ItemStack.EMPTY;
        }
        if (!ItemEditUtil.sameSpecial(expected, stack)) return ItemStack.EMPTY;
        if (!valid()) return ItemStack.EMPTY;

        Boolean unbreakable = has(ItemEditUtil.KEY_UNBREAKABLE) ? change.getBoolean(ItemEditUtil.KEY_UNBREAKABLE) : null;
        Boolean hideTooltip = has(ItemEditUtil.KEY_HIDE_TOOLTIP) ? change.getBoolean(ItemEditUtil.KEY_HIDE_TOOLTIP) : null;
        Boolean hideAdditional = has(ItemEditUtil.KEY_HIDE_ADDITIONAL) ? change.getBoolean(ItemEditUtil.KEY_HIDE_ADDITIONAL) : null;
        Integer cmm = has(ItemEditUtil.KEY_CUSTOM_MODEL_DATA) ? change.getInt(ItemEditUtil.KEY_CUSTOM_MODEL_DATA) : null;
        Integer dye = has(ItemEditUtil.SNAP_DYE_COLOR) ? change.getInt(ItemEditUtil.SNAP_DYE_COLOR) : null;
        Integer potion = has(ItemEditUtil.KEY_POTION_COLOR) ? change.getInt(ItemEditUtil.KEY_POTION_COLOR) : null;
        Integer repair = has(ItemEditUtil.KEY_REPAIR_COST) ? change.getInt(ItemEditUtil.KEY_REPAIR_COST) : null;

        return ItemEditUtil.applySpecial(stack, unbreakable, hideTooltip, hideAdditional,
                cmm, dye, potion, repair);
    }

    private boolean has(String key) {
        return change.contains(key, net.minecraft.nbt.Tag.TAG_ANY_NUMERIC);
    }

    /** 边界检查：颜色只收合法 24 位，负数只允许 -1（= 移除），避免脏数据写进物品。 */
    private boolean valid() {
        if (has(ItemEditUtil.KEY_CUSTOM_MODEL_DATA) && change.getInt(ItemEditUtil.KEY_CUSTOM_MODEL_DATA) < -1) {
            return false;
        }
        if (has(ItemEditUtil.SNAP_DYE_COLOR)) {
            int dye = change.getInt(ItemEditUtil.SNAP_DYE_COLOR);
            if (dye < -1 || dye > 0xFFFFFF) return false;
        }
        if (has(ItemEditUtil.KEY_POTION_COLOR)) {
            int potion = change.getInt(ItemEditUtil.KEY_POTION_COLOR);
            if (potion < -1 || potion > 0xFFFFFF) return false;
        }
        if (has(ItemEditUtil.KEY_REPAIR_COST) && change.getInt(ItemEditUtil.KEY_REPAIR_COST) < -1) {
            return false;
        }
        return true;
    }

    public static void handle(EditItemTagsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (msg.selectedSlot < 0 || msg.selectedSlot > 8
                    || player.getInventory().selected != msg.selectedSlot) {
                player.displayClientMessage(
                        Component.translatable("screen.infinitestats.item_editor.changed"), true);
                return;
            }
            ItemStack edited = msg.editedCopy(player.getItemInHand(InteractionHand.MAIN_HAND));
            if (edited.isEmpty()) {
                player.displayClientMessage(
                        Component.translatable("screen.infinitestats.item_editor.changed"), true);
                return;
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, edited);
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
            player.displayClientMessage(
                    Component.translatable("screen.infinitestats.item_editor.tags_applied"), true);
        });
        ctx.get().setPacketHandled(true);
    }
}
