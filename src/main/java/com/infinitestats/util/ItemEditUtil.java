package com.infinitestats.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

/** 编辑器只改指定列表；保留物品其余 NBT、能力、修饰符 UUID 与扩展字段。 */
public final class ItemEditUtil {
    private ItemEditUtil() {}

    public static String enchantmentKey(ItemStack stack) {
        return stack.is(Items.ENCHANTED_BOOK) ? "StoredEnchantments" : "Enchantments";
    }

    public static ListTag readEnchantments(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? new ListTag() : tag.getList(enchantmentKey(stack), Tag.TAG_COMPOUND).copy();
    }

    public static ListTag readAttributes(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("AttributeModifiers", Tag.TAG_LIST)) {
            return tag.getList("AttributeModifiers", Tag.TAG_COMPOUND).copy();
        }
        ListTag result = new ListTag();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            // 使用物品自身提供的属性；不要将 Forge 事件动态添加的词缀再次固化进 NBT。
            stack.getItem().getAttributeModifiers(slot, stack).forEach((attribute, modifier) -> {
                ResourceLocation id = ForgeRegistries.ATTRIBUTES.getKey(attribute);
                if (id == null) return;
                CompoundTag entry = modifier.save();
                entry.putString("AttributeName", id.toString());
                entry.putString("Slot", slot.getName());
                result.add(entry);
            });
        }
        return result;
    }

    public static CompoundTag newAttribute(String id, int operation, double amount, String slot) {
        CompoundTag entry = new CompoundTag();
        entry.putString("AttributeName", id);
        entry.putString("Name", "infinitestats.editor");
        entry.putUUID("UUID", UUID.randomUUID());
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
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            String key = enchantmentKey(stack);
            if (tag.contains(key)) snapshot.put(key, tag.get(key).copy());
            if (tag.contains("AttributeModifiers")) snapshot.put("AttributeModifiers", tag.get("AttributeModifiers").copy());
        }
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
            if (id == null || !ForgeRegistries.ATTRIBUTES.containsKey(id)
                    || !entry.hasUUID("UUID") || !Double.isFinite(entry.getDouble("Amount"))
                    || entry.getInt("Operation") < 0 || entry.getInt("Operation") > 2) return false;
            UUID uuid = entry.getUUID("UUID");
            if (uuid.getMostSignificantBits() == 0 || uuid.getLeastSignificantBits() == 0) return false;
            if (entry.contains("Slot")) {
                String slot = entry.getString("Slot");
                if (!"any".equals(slot) && !slot.isEmpty()
                        && java.util.Arrays.stream(EquipmentSlot.values()).noneMatch(s -> s.getName().equals(slot))) return false;
            }
        }
        return true;
    }

    public static boolean validEnchantments(ListTag entries) {
        if (entries.size() > 512) return false;
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(entry.getString("id"));
            if (id == null || !ForgeRegistries.ENCHANTMENTS.containsKey(id)
                    || entry.getShort("lvl") < 1) return false;
        }
        return true;
    }

    /** 在副本上修改，调用者完成校验后再一次性替换手持物品。 */
    public static ItemStack apply(ItemStack original, ListTag enchants, ListTag attributes) {
        ItemStack result = original.copy();
        if (enchants != null) {
            if (enchants.isEmpty()) result.removeTagKey(enchantmentKey(result));
            else result.addTagElement(enchantmentKey(result), enchants.copy());
        }
        if (attributes != null) {
            ListTag normalized = attributes.copy();
            for (int i = 0; i < normalized.size(); i++) {
                CompoundTag entry = normalized.getCompound(i);
                if (entry.getString("Slot").isEmpty() || "any".equals(entry.getString("Slot"))) entry.remove("Slot");
            }
            // 显式删除全部属性应保留空列表，不能删除标签导致内置属性重新出现。
            result.addTagElement("AttributeModifiers", normalized);
        }
        return result;
    }
}
