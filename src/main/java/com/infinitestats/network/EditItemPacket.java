package com.infinitestats.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 物品编辑器数据包（客户端 → 服务器）
 * <p>
 * 用客户端编辑好的附魔列表与属性修饰符列表，整体覆盖重写玩家主手物品的
 * Enchantments 与 AttributeModifiers 标签。
 */
public final class EditItemPacket {

    private final List<EnchantData> enchants;
    private final List<AttrData> attrs;

    public EditItemPacket(List<EnchantData> enchants, List<AttrData> attrs) {
        this.enchants = enchants;
        this.attrs = attrs;
    }

    public static void encode(EditItemPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.enchants.size());
        for (EnchantData e : msg.enchants) {
            buf.writeUtf(e.id);
            buf.writeVarInt(e.level);
            buf.writeNbt(e.extra);
        }
        buf.writeVarInt(msg.attrs.size());
        for (AttrData a : msg.attrs) {
            buf.writeUtf(a.id);
            buf.writeByte(a.operation);
            buf.writeDouble(a.amount);
            buf.writeUtf(a.slot);
        }
    }

    public static EditItemPacket decode(FriendlyByteBuf buf) {
        int ec = buf.readVarInt();
        List<EnchantData> enchants = new ArrayList<>();
        for (int i = 0; i < ec; i++) {
            enchants.add(new EnchantData(buf.readUtf(), buf.readVarInt(),
                    Objects.requireNonNullElse(buf.readNbt(), new CompoundTag())));
        }
        int ac = buf.readVarInt();
        List<AttrData> attrs = new ArrayList<>();
        for (int i = 0; i < ac; i++) {
            attrs.add(new AttrData(buf.readUtf(), buf.readByte(), buf.readDouble(), buf.readUtf()));
        }
        return new EditItemPacket(enchants, attrs);
    }

    public static void handle(EditItemPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
            if (stack.isEmpty()) return;

            // ===== 重写附魔 =====
            if (msg.enchants.isEmpty()) {
                stack.removeTagKey("Enchantments");
            } else {
                ListTag list = new ListTag();
                for (EnchantData e : msg.enchants) {
                    ResourceLocation rl = ResourceLocation.tryParse(e.id);
                    if (rl == null) continue;
                    if (ForgeRegistries.ENCHANTMENTS.getValue(rl) == null) continue;
                    CompoundTag c = new CompoundTag();
                    c.putString("id", e.id);
                    c.putShort("lvl", (short) Math.max(0, Math.min(e.level, Short.MAX_VALUE)));
                    // 合并扩展 NBT（Apotheosis 等模组的附魔附加数据）
                    if (e.extra != null) {
                        for (String k : e.extra.getAllKeys()) {
                            c.put(k, e.extra.get(k));
                        }
                    }
                    list.add(c);
                }
                if (list.isEmpty()) stack.removeTagKey("Enchantments");
                else stack.addTagElement("Enchantments", list);
            }

            // ===== 重写属性修饰符 =====
            if (msg.attrs.isEmpty()) {
                stack.removeTagKey("AttributeModifiers");
            } else {
                ListTag list = new ListTag();
                int idx = 0;
                for (AttrData a : msg.attrs) {
                    ResourceLocation rl = ResourceLocation.tryParse(a.id);
                    if (rl == null) continue;
                    if (ForgeRegistries.ATTRIBUTES.getValue(rl) == null) continue;
                    CompoundTag c = new CompoundTag();
                    c.putString("AttributeName", a.id);
                    c.putDouble("Amount", a.amount);
                    c.putInt("Operation", a.operation);
                    c.putString("Slot", a.slot);
                    // 确定性 UUID，保证同名条目刷新后仍稳定
                    c.putUUID("UUID", UUID.nameUUIDFromBytes(
                            (a.id + "|" + a.operation + "|" + a.amount + "|" + a.slot + "|" + idx).getBytes()));
                    c.putString("Name", a.id);
                    list.add(c);
                    idx++;
                }
                if (list.isEmpty()) stack.removeTagKey("AttributeModifiers");
                else stack.addTagElement("AttributeModifiers", list);
            }

            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        });
        ctx.get().setPacketHandled(true);
    }

    /** 附魔数据 */
    public static final class EnchantData {
        public final String id;
        public final int level;
        public final CompoundTag extra;
        public EnchantData(String id, int level) {
            this(id, level, new CompoundTag());
        }
        public EnchantData(String id, int level, CompoundTag extra) {
            this.id = id;
            this.level = level;
            this.extra = extra;
        }
    }

    /** 属性修饰符数据：operation 0=加算 1=乘基 2=乘总 */
    public static final class AttrData {
        public final String id;
        public final int operation;
        public final double amount;
        public final String slot;
        public AttrData(String id, int operation, double amount, String slot) {
            this.id = id;
            this.operation = operation;
            this.amount = amount;
            this.slot = slot;
        }
    }
}
