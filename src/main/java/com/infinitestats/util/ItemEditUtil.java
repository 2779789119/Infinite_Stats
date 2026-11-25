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
import net.minecraft.util.Unit;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Unbreakable;
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

    /**
     * 读取「运行时才有、但不在 {@code ATTRIBUTE_MODIFIERS} 组件里」的属性修饰符。
     * <p>
     * 词缀（重铸台生成的 affix）和其它模组的属性事件都是通过 {@code ItemAttributeModifierEvent}
     * 在 {@code ItemStack#getAttributeModifiers()} 里临时塞进去的，数据组件里查不到 ——
     * 所以只读组件的编辑器会漏掉它们（典型表现：工具提示上明明有「+125 最大法力值」，
     * 编辑器属性栏却只列出物品自带的几条）。
     * <p>
     * 这里把整份运行时结果读出来，再减去已经由 {@code base} 覆盖的部分，剩下的就是外部来源。
     *
     * @param base {@link #readAttributes} 的结果，用于去重（组件条目或物品内置属性）
     * @return 与 {@code AttributeModifiers} 同格式的条目（AttributeName / id / Operation / Amount / [Slot]）；
     *         全部槽位都生效的条目不写 {@code Slot}
     */
    public static ListTag readInjectedAttributes(ItemStack stack, ListTag base) {
        java.util.Set<String> baseIds = new java.util.HashSet<>();
        java.util.Set<String> baseKeys = new java.util.HashSet<>();
        for (int i = 0; i < base.size(); i++) {
            CompoundTag c = base.getCompound(i);
            if (c.contains("id")) baseIds.add(c.getString("id"));
            String slot = c.contains("Slot") && !c.getString("Slot").isEmpty() ? c.getString("Slot") : "any";
            baseKeys.add(attributeKey(c.getString("AttributeName"), c.getInt("Operation"),
                    c.getDouble("Amount"), slot));
        }

        // 同一个修饰符会在多个槽位组上重复出现，按标识合并；同时记下它出现在哪些槽位组
        java.util.Map<String, CompoundTag> found = new java.util.LinkedHashMap<>();
        java.util.Map<String, java.util.Set<String>> slots = new java.util.LinkedHashMap<>();
        int groupCount = EquipmentSlotGroup.values().length;
        for (ItemAttributeModifiers.Entry entry : stack.getAttributeModifiers().modifiers()) {
            ResourceLocation attrId = entry.attribute().unwrapKey().map(key -> key.location()).orElse(null);
            if (attrId == null) continue;
            String ident = entry.modifier().id().toString();
            if (baseIds.contains(ident)) continue;
            int operation = entry.modifier().operation().id();
            double amount = entry.modifier().amount();
            String slot = entry.slot().getSerializedName();
            // 值完全相同、只是标识被重建成另一个的条目也算已覆盖，避免同一行显示两遍
            if (baseKeys.contains(attributeKey(attrId.toString(), operation, amount, "any"))
                    || baseKeys.contains(attributeKey(attrId.toString(), operation, amount, slot))) {
                continue;
            }
            if (found.get(ident) == null) {
                CompoundTag c = new CompoundTag();
                c.putString("AttributeName", attrId.toString());
                c.putString("id", ident);
                c.putInt("Operation", operation);
                c.putDouble("Amount", amount);
                found.put(ident, c);
            }
            slots.computeIfAbsent(ident, k -> new java.util.HashSet<>()).add(slot);
        }

        ListTag result = new ListTag();
        for (var e : found.entrySet()) {
            CompoundTag entry = e.getValue();
            java.util.Set<String> used = slots.get(e.getKey());
            if (used == null || used.isEmpty()) continue;
            if (used.size() >= groupCount) entry.remove("Slot");   // 所有槽位都生效 → 「任意」
            else entry.putString("Slot", used.iterator().next());
            result.add(entry);
        }
        return result;
    }

    private static String attributeKey(String id, int operation, double amount, String slot) {
        return id + '|' + operation + '|' + amount + '|' + slot;
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

    // ======================== 特殊标签（无法破坏 / 隐藏提示 / 模型数据 / 颜色 / 铁砧惩罚） ========================
    // 1.21.1 的「隐藏提示」只剩两个整体开关：
    //   HIDE_TOOLTIP           → 整条提示都不显示
    //   HIDE_ADDITIONAL_TOOLTIP→ 只隐藏药水效果 / 地图编号 / 书本信息这类附加信息
    // 1.20.1 的 HideFlags 位掩码（逐项隐藏附魔 / 属性 / 染料 / 升级）在 1.21.1 没有对应组件，
    // 要到 1.21.2 的 TooltipDisplay 才支持，所以界面只给这两个开关。

    /** 快照 / 网络包里的键名（与 1.20.1 的同名键保持一致，便于两条线对照）。 */
    public static final String KEY_UNBREAKABLE = "Unbreakable";
    public static final String KEY_HIDE_TOOLTIP = "HideTooltip";
    public static final String KEY_HIDE_ADDITIONAL = "HideAdditional";
    public static final String KEY_CUSTOM_MODEL_DATA = "CustomModelData";
    public static final String KEY_POTION_COLOR = "PotionColor";
    public static final String KEY_REPAIR_COST = "RepairCost";
    /** 快照里的染料颜色键（实际存在 {@code DYED_COLOR} 组件上）。 */
    public static final String SNAP_DYE_COLOR = "display.color";

    public static boolean isUnbreakable(ItemStack stack) {
        return stack.has(DataComponents.UNBREAKABLE);
    }

    public static boolean isHideTooltip(ItemStack stack) {
        return stack.has(DataComponents.HIDE_TOOLTIP);
    }

    public static boolean isHideAdditional(ItemStack stack) {
        return stack.has(DataComponents.HIDE_ADDITIONAL_TOOLTIP);
    }

    /** 自定义模型数据；没有该组件时返回 null。 */
    public static Integer customModelData(ItemStack stack) {
        CustomModelData data = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        return data == null ? null : data.value();
    }

    /** 染料颜色（RGB）；没有染色时返回 null。 */
    public static Integer dyeColor(ItemStack stack) {
        DyedItemColor color = stack.get(DataComponents.DYED_COLOR);
        return color == null ? null : color.rgb();
    }

    /** 药水的自定义颜色（RGB）；没有自定义颜色时返回 null。 */
    public static Integer potionColor(ItemStack stack) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        return contents == null ? null : contents.customColor().orElse(null);
    }

    /** 铁砧累计惩罚；没有该组件时返回 null。 */
    public static Integer repairCost(ItemStack stack) {
        return stack.get(DataComponents.REPAIR_COST);
    }

    /**
     * 特殊标签的原值快照：用于服务端并发检查。
     * <p>
     * 只记「编辑器会写的那几项」，其中染料颜色统一压成一个平铺键，省得为一个 int 建两层 compound。
     */
    public static CompoundTag specialSnapshot(ItemStack stack) {
        CompoundTag snapshot = new CompoundTag();
        if (isUnbreakable(stack)) snapshot.putBoolean(KEY_UNBREAKABLE, true);
        if (isHideTooltip(stack)) snapshot.putBoolean(KEY_HIDE_TOOLTIP, true);
        if (isHideAdditional(stack)) snapshot.putBoolean(KEY_HIDE_ADDITIONAL, true);
        Integer cmm = customModelData(stack);
        if (cmm != null) snapshot.putInt(KEY_CUSTOM_MODEL_DATA, cmm);
        Integer dye = dyeColor(stack);
        if (dye != null) snapshot.putInt(SNAP_DYE_COLOR, dye);
        Integer potion = potionColor(stack);
        if (potion != null) snapshot.putInt(KEY_POTION_COLOR, potion);
        Integer repair = repairCost(stack);
        if (repair != null) snapshot.putInt(KEY_REPAIR_COST, repair);
        return snapshot;
    }

    public static boolean sameSpecial(CompoundTag expected, ItemStack stack) {
        return java.util.Objects.equals(expected, specialSnapshot(stack));
    }

    /**
     * 在副本上写特殊标签，调用者完成校验后再一次性替换手持物品。
     *
     * @param unbreakable     null = 不改
     * @param hideTooltip     null = 不改
     * @param hideAdditional  null = 不改
     * @param customModelData null = 不改；负数 = 移除
     * @param dyeColor        null = 不改；负数 = 移除
     * @param potionColor     null = 不改；负数 = 移除自定义颜色
     * @param repairCost      null = 不改；负数 = 移除
     */
    public static ItemStack applySpecial(ItemStack original, Boolean unbreakable,
                                         Boolean hideTooltip, Boolean hideAdditional,
                                         Integer customModelData, Integer dyeColor,
                                         Integer potionColor, Integer repairCost) {
        ItemStack result = original.copy();

        if (unbreakable != null) {
            if (unbreakable) result.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
            else result.remove(DataComponents.UNBREAKABLE);
        }
        if (hideTooltip != null) {
            if (hideTooltip) result.set(DataComponents.HIDE_TOOLTIP, Unit.INSTANCE);
            else result.remove(DataComponents.HIDE_TOOLTIP);
        }
        if (hideAdditional != null) {
            if (hideAdditional) result.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
            else result.remove(DataComponents.HIDE_ADDITIONAL_TOOLTIP);
        }
        if (customModelData != null) {
            if (customModelData < 0) result.remove(DataComponents.CUSTOM_MODEL_DATA);
            else result.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(customModelData));
        }
        if (dyeColor != null) {
            if (dyeColor < 0) result.remove(DataComponents.DYED_COLOR);
            else result.set(DataComponents.DYED_COLOR, new DyedItemColor(dyeColor & 0xFFFFFF, true));
        }
        if (potionColor != null) {
            PotionContents contents = result.get(DataComponents.POTION_CONTENTS);
            if (contents != null) {
                java.util.Optional<Integer> color = potionColor < 0
                        ? java.util.Optional.empty()
                        : java.util.Optional.of(potionColor & 0xFFFFFF);
                result.set(DataComponents.POTION_CONTENTS,
                        new PotionContents(contents.potion(), color, contents.customEffects()));
            }
        }
        if (repairCost != null) {
            if (repairCost < 0) result.remove(DataComponents.REPAIR_COST);
            else result.set(DataComponents.REPAIR_COST, Math.max(0, repairCost));
        }
        return result;
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