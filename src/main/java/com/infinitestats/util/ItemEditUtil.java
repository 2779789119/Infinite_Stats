package com.infinitestats.util;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.UUID;

/**
 * 编辑器只改指定列表；保留物品其余数据组件与内置属性。
 * <p>
 * 1.21.1 中附魔与属性已改为数据组件：附魔存于 {@link DataComponents#ENCHANTMENTS} /
 * {@link DataComponents#STORED_ENCHANTMENTS}（{@link ItemEnchantments}），属性存于
 * {@link DataComponents#ATTRIBUTE_MODIFIERS}（{@link ItemAttributeModifiers}）。
 * 与界面之间仍以 NBT {@link ListTag} 传输，但读写都落到数据组件上。
 */
public final class ItemEditUtil {
    private ItemEditUtil() {}

    /** 附魔书用 StoredEnchantments，其余用 Enchantments。 */
    public static String enchantmentKey(ItemStack stack) {
        return stack.is(Items.ENCHANTED_BOOK) ? "StoredEnchantments" : "Enchantments";
    }

    private static DataComponentType<ItemEnchantments> enchantmentType(ItemStack stack) {
        return stack.is(Items.ENCHANTED_BOOK)
                ? DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS;
    }

    /** 读取当前附魔，输出条目 {id, lvl}。 */
    public static ListTag readEnchantments(ItemStack stack) {
        ItemEnchantments enchantments = stack.getOrDefault(enchantmentType(stack), ItemEnchantments.EMPTY);
        ListTag list = new ListTag();
        for (var entry : enchantments.entrySet()) {
            ResourceLocation id = entry.getKey().unwrapKey().map(key -> key.location()).orElse(null);
            if (id == null) continue;
            CompoundTag c = new CompoundTag();
            c.putString("id", id.toString());
            c.putShort("lvl", (short) entry.getIntValue());
            list.add(c);
        }
        return list;
    }

    /** 读取当前属性修饰符，输出条目 {AttributeName, id, Operation, Amount, [Slot]}。 */
    public static ListTag readAttributes(ItemStack stack) {
        ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        ListTag list = new ListTag();
        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            ResourceLocation attrId = entry.attribute().unwrapKey().map(key -> key.location()).orElse(null);
            if (attrId == null) continue;
            CompoundTag c = new CompoundTag();
            c.putString("AttributeName", attrId.toString());
            c.putString("id", entry.modifier().id().toString());
            c.putInt("Operation", entry.modifier().operation().id());
            c.putDouble("Amount", entry.modifier().amount());
            String slot = entry.slot().getSerializedName();
            if (!"any".equals(slot)) c.putString("Slot", slot);
            list.add(c);
        }
        return list;
    }

    public static CompoundTag newAttribute(String id, int operation, double amount, String slot) {
        CompoundTag entry = new CompoundTag();
        entry.putString("AttributeName", id);
        // 1.21.1 的修饰符标识由 UUID 改为 ResourceLocation。
        entry.putString("id", ResourceLocation.fromNamespaceAndPath("infinitestats",
                "editor/" + UUID.randomUUID()).toString());
        updateAttribute(entry, operation, amount, slot);
        return entry;
    }

    public static void updateAttribute(CompoundTag entry, int operation, double amount, String slot) {
        entry.putInt("Operation", operation);
        entry.putDouble("Amount", amount);
        if ("any".equals(slot) || slot.isEmpty()) entry.remove("Slot");
        else entry.putString("Slot", slot);
    }

    /** 原始值用于并发检查，避免旧界面覆盖已变化的附魔或属性。 */
    public static CompoundTag editableSnapshot(ItemStack stack) {
        CompoundTag snapshot = new CompoundTag();
        snapshot.put(enchantmentKey(stack), readEnchantments(stack));
        snapshot.put("AttributeModifiers", readAttributes(stack));
        return snapshot;
    }

    public static boolean sameSection(CompoundTag expected, ItemStack stack, String key) {
        CompoundTag current = editableSnapshot(stack);
        return java.util.Objects.equals(expected.get(key), current.get(key));
    }

    public static boolean validAttributes(ListTag entries) {
        if (entries.size() > 512) return false;
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(entry.getString("AttributeName"));
            ResourceLocation modId = ResourceLocation.tryParse(entry.getString("id"));
            if (id == null || !BuiltInRegistries.ATTRIBUTE.containsKey(id) || modId == null
                    || !Double.isFinite(entry.getDouble("Amount"))
                    || entry.getInt("Operation") < 0 || entry.getInt("Operation") > 2) return false;
            if (entry.contains("Slot")) {
                String slot = entry.getString("Slot");
                if (!"any".equals(slot) && !slot.isEmpty()
                        && java.util.Arrays.stream(EquipmentSlotGroup.values())
                            .noneMatch(group -> group.getSerializedName().equals(slot))) return false;
            }
        }
        return true;
    }

    public static boolean validEnchantments(ListTag entries) {
        if (entries.size() > 512) return false;
        var server = ServerLifecycleHooks.getCurrentServer();
        var enchantments = server == null ? null
                : server.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(entry.getString("id"));
            if (id == null || entry.getShort("lvl") < 1) return false;
            if (enchantments != null && !enchantments.containsKey(id)) return false;
        }
        return true;
    }

    /** 在副本上修改，调用者完成校验后再一次性替换手持物品。 */
    public static ItemStack apply(ItemStack original, ListTag enchants, ListTag attributes) {
        ItemStack result = original.copy();
        if (enchants != null) {
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                var lookup = server.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
                ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                for (int i = 0; i < enchants.size(); i++) {
                    CompoundTag entry = enchants.getCompound(i);
                    ResourceLocation id = ResourceLocation.tryParse(entry.getString("id"));
                    if (id == null) continue;
                    lookup.getHolder(id).ifPresent(holder ->
                            mutable.set(holder, entry.getShort("lvl")));
                }
                result.set(enchantmentType(result), mutable.toImmutable());
            }
        }
        if (attributes != null) {
            ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
            for (int i = 0; i < attributes.size(); i++) {
                CompoundTag entry = attributes.getCompound(i);
                ResourceLocation attrId = ResourceLocation.tryParse(entry.getString("AttributeName"));
                if (attrId == null) continue;
                Holder<Attribute> holder = BuiltInRegistries.ATTRIBUTE.getHolder(attrId).orElse(null);
                if (holder == null) continue;
                ResourceLocation modId = ResourceLocation.tryParse(entry.getString("id"));
                if (modId == null) {
                    modId = ResourceLocation.fromNamespaceAndPath("infinitestats",
                            "editor/" + UUID.randomUUID());
                }
                AttributeModifier.Operation op = AttributeModifier.Operation.BY_ID.apply(entry.getInt("Operation"));
                builder.add(holder,
                        new AttributeModifier(modId, entry.getDouble("Amount"), op),
                        slotByName(entry.getString("Slot")));
            }
            // 显式删除全部属性应写回空组件，不能跳过写回。
            result.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build());
        }
        return result;
    }

    private static EquipmentSlotGroup slotByName(String name) {
        if (name == null || name.isEmpty()) return EquipmentSlotGroup.ANY;
        for (EquipmentSlotGroup group : EquipmentSlotGroup.values()) {
            if (group.getSerializedName().equals(name)) return group;
        }
        return EquipmentSlotGroup.ANY;
    }
}