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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 编辑器只改指定列表；保留物品其余 NBT、能力、修饰符 UUID 与扩展字段。 */
public final class ItemEditUtil {
    private ItemEditUtil() {}

    // ======================== 饰品（Curios）槽位 ========================

    /**
     * Curios 的属性修饰符列表名。
     * <p>
     * 饰品栏不是原版 {@link EquipmentSlot}，Curios 不会去读 {@code AttributeModifiers}：
     * 它只认这个列表，并且要求条目里的 {@code Slot} 与饰品槽位类型标识（ring / necklace / belt …）
     * **完全相等**。所以编辑器把「饰品」属性单独存一份，写的时候按槽位类型展开。
     */
    public static final String CURIO_KEY = "CurioAttributeModifiers";
    /** 编辑器内部标记：在全部已注册饰品槽位上都生效。 */
    public static final String CURIO_SLOT = "curio";
    /** 编辑器内部标记：只在某个具体饰品槽位生效，后接槽位类型标识。 */
    public static final String CURIO_PREFIX = "curio:";

    public static boolean isCurioSlot(String slot) {
        return slot != null && (slot.equals(CURIO_SLOT) || slot.startsWith(CURIO_PREFIX));
    }

    public static boolean isCurioSpecificSlot(String slot) {
        return slot != null && slot.startsWith(CURIO_PREFIX) && slot.length() > CURIO_PREFIX.length();
    }

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

    /**
     * 读取饰品属性并转成编辑器条目：{@code Slot} 被改写成 {@link #CURIO_SLOT}
     * （覆盖全部已注册槽位）或 {@code curio:<槽位类型>}（只对一个槽位生效），
     * 这样它就不会和同名的原版槽位（head / feet / body / hands）混淆。
     *
     * @param slotTypes 当前已注册的饰品槽位类型（见 CuriosBridge#slotTypeIds）；空表时不合并
     */
    public static ListTag readCurioAttributes(ItemStack stack, Set<String> slotTypes) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(CURIO_KEY, Tag.TAG_LIST)) return new ListTag();
        ListTag raw = tag.getList(CURIO_KEY, Tag.TAG_COMPOUND);

        // 同一属性 + 操作 + 数值 + 名称的条目若覆盖了全部槽位类型，就是编辑器写出的「饰品」一条
        Map<String, List<CompoundTag>> groups = new LinkedHashMap<>();
        for (int i = 0; i < raw.size(); i++) {
            CompoundTag entry = raw.getCompound(i);
            groups.computeIfAbsent(curioGroupKey(entry), k -> new ArrayList<>()).add(entry);
        }

        ListTag result = new ListTag();
        for (List<CompoundTag> group : groups.values()) {
            Set<String> covered = new HashSet<>();
            for (CompoundTag entry : group) covered.add(entry.getString("Slot"));
            boolean all = slotTypes != null && !slotTypes.isEmpty() && covered.containsAll(slotTypes);
            if (all) {
                CompoundTag merged = group.get(0).copy();
                merged.putString("Slot", CURIO_SLOT);
                result.add(merged);
            } else {
                for (CompoundTag entry : group) {
                    CompoundTag copy = entry.copy();
                    copy.putString("Slot", CURIO_PREFIX + entry.getString("Slot"));
                    result.add(copy);
                }
            }
        }
        return result;
    }

    /** 把编辑器里的一条饰品属性展开成 Curios 认识的 NBT 条目（每条一个具体槽位类型）。 */
    public static ListTag curioSaveEntries(CompoundTag original, int operation, double amount, String slot,
                                           Set<String> slotTypes) {
        ListTag out = new ListTag();
        CompoundTag base = original == null ? new CompoundTag() : original.copy();
        String name = base.contains("Name") ? base.getString("Name") : "infinitestats.editor";
        UUID uuid = base.hasUUID("UUID") ? base.getUUID("UUID") : UUID.randomUUID();

        if (isCurioSpecificSlot(slot)) {
            out.add(curioEntry(base, slot.substring(CURIO_PREFIX.length()), operation, amount, name, uuid));
            return out;
        }
        if (slotTypes == null || slotTypes.isEmpty()) {
            // 读不到槽位类型时退回 Curios 的通用槽位，装上 Curios 后仍能生效
            out.add(curioEntry(base, CURIO_SLOT, operation, amount, name, uuid));
            return out;
        }
        for (String type : slotTypes) {
            // 每条用从原 UUID 派生的稳定 UUID：多个槽位同时佩戴不会互相覆盖，重复保存也不会越积越多
            out.add(curioEntry(base, type, operation, amount, name, deriveUuid(uuid, type)));
        }
        return out;
    }

    private static CompoundTag curioEntry(CompoundTag base, String slotId, int operation, double amount,
                                          String name, UUID uuid) {
        CompoundTag entry = base.copy();
        entry.putString("Slot", slotId);
        entry.putString("Name", name);
        entry.putUUID("UUID", uuid);
        entry.putInt("Operation", operation);
        entry.putDouble("Amount", amount);
        return entry;
    }

    private static UUID deriveUuid(UUID base, String slotId) {
        return UUID.nameUUIDFromBytes((base + "|" + slotId).getBytes(StandardCharsets.UTF_8));
    }

    private static String curioGroupKey(CompoundTag entry) {
        return entry.getString("AttributeName") + '|' + entry.getInt("Operation") + '|'
                + entry.getDouble("Amount") + '|' + entry.getString("Name");
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
            if (tag.contains(CURIO_KEY)) snapshot.put(CURIO_KEY, tag.get(CURIO_KEY).copy());
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

    /** 饰品属性校验：槽位必须是具体的饰品槽位类型标识（Curios 按字符串相等匹配）。 */
    public static boolean validCurioAttributes(ListTag entries) {
        if (entries == null || entries.size() > 512) return false;
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(entry.getString("AttributeName"));
            if (id == null || !ForgeRegistries.ATTRIBUTES.containsKey(id)
                    || !entry.hasUUID("UUID") || !Double.isFinite(entry.getDouble("Amount"))
                    || entry.getInt("Operation") < 0 || entry.getInt("Operation") > 2) return false;
            UUID uuid = entry.getUUID("UUID");
            if (uuid.getMostSignificantBits() == 0 || uuid.getLeastSignificantBits() == 0) return false;
            String slot = entry.getString("Slot");
            if (slot.isEmpty() || slot.indexOf(':') >= 0 || slot.length() > 64) return false;
        }
        return true;
    }

    public static boolean validEnchantments(ListTag entries) {
        if (entries.size() > 512) return false;
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(entry.getString("id"));
            if (id == null || !ForgeRegistries.ENCHANTMENTS.containsKey(id)
                    || EnchantLevelStore.read(entry) < 1) return false;
        }
        return true;
    }

    /** 在副本上修改，调用者完成校验后再一次性替换手持物品。 */
    public static ItemStack apply(ItemStack original, ListTag enchants, ListTag attributes) {
        return apply(original, enchants, attributes, null);
    }

    /** 在副本上修改，调用者完成校验后再一次性替换手持物品。 */
    public static ItemStack apply(ItemStack original, ListTag enchants, ListTag attributes, ListTag curioAttributes) {
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
        if (curioAttributes != null) {
            // 饰品属性不存在「内置值」，清空就整段删掉，免得留个空列表骗 Curios
            if (curioAttributes.isEmpty()) result.removeTagKey(CURIO_KEY);
            else result.addTagElement(CURIO_KEY, curioAttributes.copy());
        }
        return result;
    }
}
