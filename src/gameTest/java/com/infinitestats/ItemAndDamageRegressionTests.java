package com.infinitestats;

import com.infinitestats.event.StatEventHandler;
import com.infinitestats.handler.AttackHandler;
import com.infinitestats.network.EditItemPacket;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import com.infinitestats.stats.StatType;
import com.infinitestats.util.EnchantLimits;
import com.infinitestats.util.ItemEditUtil;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

/** 仅在启用托管 GameTest 时运行，不进入交付 JAR。 */
@GameTestHolder(InfiniteStats.MODID)
@PrefixGameTestTemplate(false)
public final class ItemAndDamageRegressionTests {
    /** 1.19.2 的 GameTestHelper 没有 assertTrue（1.20 才加），这里补一个等价的。 */
    private static void assertTrue(GameTestHelper helper, boolean condition, String message) {
        if (!condition) throw new net.minecraft.gametest.framework.GameTestAssertException(message);
    }

    private static void close(float expected, float actual, String message) {
        if (Math.abs(expected - actual) > 0.001f) throw new AssertionError(message + ": " + actual + " != " + expected);
    }

    private static EditItemPacket packet(ItemStack original, ListTag enchants, ListTag attrs) {
        return new EditItemPacket(0, ForgeRegistries.ITEMS.getKey(original.getItem()),
                ItemEditUtil.editableSnapshot(original), enchants, attrs);
    }

    @GameTest(template = "empty")
    public static void attributeEditPreservesEquipmentAndNbt(GameTestHelper helper) {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.setHoverName(Component.literal("Named sword"));
        sword.enchant(Enchantments.SHARPNESS, 4);
        sword.setDamageValue(15);
        CompoundTag custom = new CompoundTag();
        custom.putString("rarity", "legendary");
        custom.putInt("socket_count", 3);
        sword.getOrCreateTag().put("other_mod_data", custom);
        ListTag lore = new ListTag();
        lore.add(StringTag.valueOf("{\"text\":\"Keep this lore\",\"color\":\"gold\"}"));
        sword.getOrCreateTagElement("display").put("Lore", lore);
        CompoundTag before = sword.save(new CompoundTag());
        ListTag attrs = ItemEditUtil.readAttributes(sword);
        assertTrue(helper, !attrs.isEmpty(), "Built-in sword attributes were not loaded");
        attrs.add(ItemEditUtil.newAttribute("minecraft:generic.max_health", 0, 10, "any"));
        ItemStack edited = packet(sword, null, attrs).editedCopy(sword);
        assertTrue(helper, !edited.isEmpty(), "Valid edit rejected");
        // 注意：Multimap#get 返回的是 Guava ImmutableCollection，它的 equals 是**引用比较**，
        // 内容一样也比不出相等 —— 两边各自包一层 ArrayList 再比内容，换版本也不会假失败。
        assertTrue(helper, new java.util.ArrayList<>(edited.getAttributeModifiers(EquipmentSlot.MAINHAND)
                .get(Attributes.ATTACK_DAMAGE)).equals(new java.util.ArrayList<>(
                sword.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE))),
                "Editing erased built-in attack damage or modifier UUID");
        assertTrue(helper, !edited.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_SPEED).isEmpty(),
                "Editing erased built-in attack speed");
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            assertTrue(helper, !edited.getAttributeModifiers(slot).get(Attributes.MAX_HEALTH).isEmpty(),
                    "Any-slot modifier did not apply in " + slot);
        }
        assertTrue(helper, ItemEditUtil.readEnchantments(sword).equals(ItemEditUtil.readEnchantments(edited)),
                "Attribute edit changed enchantments");
        assertTrue(helper, sword.getTag().get("display").equals(edited.getTag().get("display")), "Lost display data");
        assertTrue(helper, custom.equals(edited.getTag().getCompound("other_mod_data")), "Lost external item data");
        assertTrue(helper, edited.getDamageValue() == 15 && before.equals(sword.save(new CompoundTag())),
                "Edit mutated original item or durability");

        ItemStack armor = new ItemStack(Items.DIAMOND_CHESTPLATE);
        ListTag armorAttrs = ItemEditUtil.readAttributes(armor);
        armorAttrs.add(ItemEditUtil.newAttribute("minecraft:generic.max_health", 0, 4, "chest"));
        ItemStack editedArmor = packet(armor, null, armorAttrs).editedCopy(armor);
        assertTrue(helper, !editedArmor.getAttributeModifiers(EquipmentSlot.CHEST).get(Attributes.ARMOR).isEmpty(),
                "Armor turned into a blank item");
        assertTrue(helper, !editedArmor.getAttributeModifiers(EquipmentSlot.CHEST).get(Attributes.ARMOR_TOUGHNESS).isEmpty(),
                "Armor toughness disappeared");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void editorWirePreservesUnchangedAndCustomFields(GameTestHelper helper) {
        ItemStack stack = new ItemStack(Items.IRON_SWORD);
        ListTag originalAttrs = ItemEditUtil.readAttributes(stack);
        originalAttrs.getCompound(0).putString("external_marker", "keep");
        stack.addTagElement("AttributeModifiers", originalAttrs.copy());
        stack.enchant(Enchantments.UNBREAKING, 2);
        ListTag changes = originalAttrs.copy();
        changes.add(ItemEditUtil.newAttribute("minecraft:generic.luck", 0, 2, "mainhand"));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            EditItemPacket.encode(packet(stack, null, changes), buf);
            ItemStack edited = EditItemPacket.decode(buf).editedCopy(stack);
            assertTrue(helper, !edited.isEmpty(), "Packet roundtrip rejected valid edit");
            assertTrue(helper, ItemEditUtil.readAttributes(edited).getCompound(0).equals(originalAttrs.getCompound(0)),
                    "Packet dropped original UUID, name or extension NBT");
            assertTrue(helper, EnchantmentHelper.getItemEnchantmentLevel(Enchantments.UNBREAKING, edited) == 2,
                    "Packet erased untouched enchantments");
        } finally { buf.release(); }
        ItemStack noChange = packet(stack, null, null).editedCopy(stack);
        assertTrue(helper, stack.save(new CompoundTag()).equals(noChange.save(new CompoundTag())), "No-op edit changed NBT");
        ItemStack clearEnchant = packet(stack, new ListTag(), null).editedCopy(stack);
        assertTrue(helper, ItemEditUtil.readEnchantments(clearEnchant).isEmpty()
                && ItemEditUtil.readAttributes(clearEnchant).equals(originalAttrs), "Clear enchant changed attributes");
        ItemStack clearAttr = packet(stack, null, new ListTag()).editedCopy(stack);
        assertTrue(helper, clearAttr.getAttributeModifiers(EquipmentSlot.MAINHAND).isEmpty(),
                "Explicitly cleared attributes reverted to defaults");
        assertTrue(helper, !ItemEditUtil.readEnchantments(clearAttr).isEmpty(), "Clear attributes erased enchantments");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void enchantedBooksAndStaleEdits(GameTestHelper helper) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        CompoundTag enchant = new CompoundTag();
        enchant.putString("id", "minecraft:mending");
        enchant.putShort("lvl", (short) 1);
        enchant.putString("extension", "retain");
        ListTag original = new ListTag();
        original.add(enchant);
        book.addTagElement("StoredEnchantments", original.copy());
        ListTag changes = ItemEditUtil.readEnchantments(book);
        changes.getCompound(0).putShort("lvl", (short) 2);
        ItemStack edited = packet(book, changes, null).editedCopy(book);
        assertTrue(helper, edited.getTag().contains("StoredEnchantments") && !edited.getTag().contains("Enchantments"),
                "Book saved into the wrong enchantment tag");
        assertTrue(helper, "retain".equals(ItemEditUtil.readEnchantments(edited).getCompound(0).getString("extension")),
                "Book edit lost extended enchantment data");
        EditItemPacket stale = packet(book, changes, null);
        book.addTagElement("StoredEnchantments", changes.copy());
        assertTrue(helper, stale.editedCopy(book).isEmpty(), "Stale edit overwrote newer data");
        ListTag invalid = new ListTag();
        invalid.add(ItemEditUtil.newAttribute("minecraft:generic.max_health", 0, Double.NaN, "any"));
        assertTrue(helper, packet(book, null, invalid).editedCopy(book).isEmpty(), "Invalid number changed item data");
        assertTrue(helper, packet(book, null, null).editedCopy(new ItemStack(Items.DIAMOND)).isEmpty(),
                "Edit applied to a different item");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void trueDamageOnRegularAttack(GameTestHelper helper) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "TrueDamageTest"));
        PlayerStats stats = player.getCapability(PlayerStatsProvider.PLAYER_STATS)
                .orElseThrow(() -> new IllegalStateException("Missing player stats capability"));
        stats.setAvailablePoints(1000);
        assertTrue(helper, stats.addPoints(StatType.fromId("true_damage"), 100), "Could not allocate true damage");
        var target = EntityType.ZOMBIE.create(helper.getLevel());
        target.getAttribute(Attributes.ARMOR).setBaseValue(20);
        target.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(8);
        target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 4));
        target.setAbsorptionAmount(10);
        float health = target.getHealth();
        try {
            // 走玩家普通伤害的完整事件链，不能仅测试 applyTrueDamage 辅助方法。
            target.hurt(DamageSource.playerAttack(player), 6);
            close(health - 6, target.getHealth(), "Regular hit lost true damage after full resistance");
            close(10, target.getAbsorptionAmount(), "True damage was absorbed");
            target.hurt(DamageSource.playerAttack(player), 6);
            close(health - 6, target.getHealth(), "Rejected cooldown hit dealt duplicate true damage");
            target.invulnerableTime = 0;
            target.hurt(DamageSource.arrow(new net.minecraft.world.entity.projectile.Arrow(helper.getLevel(), player), player), 2);
            close(health - 8, target.getHealth(), "Player projectile did not apply true damage");
        } finally { AttackHandler.clearPendingTrueDamage(); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void trueDamageCancellationAndIsolation(GameTestHelper helper) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "TrueDamageCancel"));
        PlayerStats stats = player.getCapability(PlayerStatsProvider.PLAYER_STATS)
                .orElseThrow(() -> new IllegalStateException("Missing player stats capability"));
        stats.setAvailablePoints(1000);
        assertTrue(helper, stats.addPoints(StatType.fromId("true_damage"), 100), "Could not allocate true damage");
        var first = EntityType.ZOMBIE.create(helper.getLevel());
        var second = EntityType.ZOMBIE.create(helper.getLevel());
        DamageSource source = DamageSource.playerAttack(player);
        float firstHealth = first.getHealth();
        float secondHealth = second.getHealth();
        try {
            StatEventHandler.onLivingHurt(new LivingHurtEvent(first, source, 4));
            StatEventHandler.onLivingHurt(new LivingHurtEvent(second, source, 2));
            LivingDamageEvent cancelled = new LivingDamageEvent(first, source, 1);
            cancelled.setCanceled(true);
            StatEventHandler.onLivingDamage(cancelled);
            close(1, cancelled.getAmount(), "Cancelled hit received bonus");
            // 第三方取消只影响主伤害：真伤是独立结算，必须照常落地
            close(firstHealth - 4, first.getHealth(), "Third-party cancellation swallowed true damage");
            LivingDamageEvent finalHit = new LivingDamageEvent(second, source, 1);
            StatEventHandler.onLivingDamage(finalHit);
            close(secondHealth - 2, second.getHealth(), "Concurrent target lost or inherited another hit's bonus");
            StatEventHandler.onLivingDamage(finalHit);
            close(secondHealth - 2, second.getHealth(), "Bonus applied more than once");
            StatEventHandler.onLivingHurt(new LivingHurtEvent(first, source, 8));
            AttackHandler.clearPendingTrueDamage();
            StatEventHandler.onLivingDamage(new LivingDamageEvent(first, source, 1));
            close(firstHealth - 4, first.getHealth(), "Abandoned hit leaked into next tick");
        } finally { AttackHandler.clearPendingTrueDamage(); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void extraStrikeDealsBonusAttributeDamage(GameTestHelper helper) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ExtraStrikeTest"));
        PlayerStats stats = player.getCapability(PlayerStatsProvider.PLAYER_STATS)
                .orElseThrow(() -> new IllegalStateException("Missing player stats capability"));
        stats.setAvailablePoints(1000);
        assertTrue(helper, stats.addPoints(StatType.fromId("extra_strike"), 4), "Could not allocate extra strike");
        var target = EntityType.ZOMBIE.create(helper.getLevel());
        DamageSource source = DamageSource.playerAttack(player);
        float ratio = (float) AttackHandler.extraStrikeDamageRatio();

        // 4 级 = 追加 4 次「本次伤害 × 比例」。
        // 这里手动构造 LivingDamageEvent（没有真实 hurt() 流程），主伤害的 5 点由原版负责落地，
        // 本方法只结算「额外打击」那部分，所以期望值里不含主伤害。
        float health = target.getHealth();
        StatEventHandler.onLivingDamage(new LivingDamageEvent(target, source, 5));
        close(health - 5 * ratio * 4, target.getHealth(), "Extra strike bonus damage wrong");

        // 次数上限 8：点数堆满后也只按 8 次结算
        assertTrue(helper, stats.addPoints(StatType.fromId("extra_strike"), 100), "Could not stack extra strike");
        close(8, AttackHandler.extraStrikeCount(stats), "Extra strike count should cap at 8");

        // 自伤不触发（与其它进攻属性口径一致）
        float playerHealth = player.getHealth();
        StatEventHandler.onLivingDamage(new LivingDamageEvent(
                player, DamageSource.playerAttack(player), 3));
        close(playerHealth, player.getHealth(), "Self damage still received extra strikes");

        // 非玩家来源（生物互殴）不触发：血量一点都不该动
        var mobTarget = EntityType.ZOMBIE.create(helper.getLevel());
        var mobSource = EntityType.ZOMBIE.create(helper.getLevel());
        float mobHealth = mobTarget.getHealth();
        StatEventHandler.onLivingDamage(new LivingDamageEvent(
                mobTarget, DamageSource.mobAttack(mobSource), 5));
        close(mobHealth, mobTarget.getHealth(), "Mob damage received player extra strikes");

        // 「功能开关」把整条属性关掉后不再追加（主伤害同样由原版流程负责，不在断言里）
        stats.setStatDisabled("extra_strike", true);
        float disabledHealth = target.getHealth();
        StatEventHandler.onLivingDamage(new LivingDamageEvent(target, source, 5));
        close(disabledHealth, target.getHealth(), "Disabled extra strike still dealt bonus damage");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void enchantCapFollowsUnlockState(GameTestHelper helper) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "EnchantCapTest"));
        PlayerStats stats = player.getCapability(PlayerStatsProvider.PLAYER_STATS)
                .orElseThrow(() -> new IllegalStateException("Missing player stats capability"));
        stats.setAvailablePoints(1000);
        var sharpness = Enchantments.SHARPNESS;

        // 未解锁 = 整合包实际生效的上限。开发环境没装 Apotheosis，这里应回退到原版上限。
        close(sharpness.getMaxLevel(), EnchantLimits.maxLevel(player, sharpness),
                "Locked cap must follow the pack cap");
        close(sharpness.getMaxLevel(), EnchantLimits.packCap(sharpness), "Pack cap must fall back to vanilla");

        assertTrue(helper, stats.addPoints(StatType.fromId("enchant_limit"), 1), "Could not unlock enchant limit");
        close(EnchantLimits.UNLIMITED, EnchantLimits.maxLevel(player, sharpness), "Unlocked cap must be unlimited");

        // 「功能开关」关掉后视为未解锁，上限回到整合包上限
        stats.setStatDisabled("enchant_limit", true);
        close(sharpness.getMaxLevel(), EnchantLimits.maxLevel(player, sharpness),
                "Disabled enchant limit must fall back to the pack cap");
        helper.succeed();
    }
}
