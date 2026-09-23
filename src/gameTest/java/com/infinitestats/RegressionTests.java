package com.infinitestats;

import com.infinitestats.crafting.PortableCraftingMenu;
import com.infinitestats.emc.*;
import com.infinitestats.furnace.*;
import com.infinitestats.handler.*;
import com.infinitestats.network.NetworkHandler;
import com.infinitestats.network.SyncStatsPacket;
import com.infinitestats.stats.*;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;
import java.util.UUID;

/** 只在托管 GameTest 的隔离实例中加载，不进入发布 JAR。 */
@GameTestHolder(InfiniteStats.MODID)
@PrefixGameTestTemplate(false)
public final class RegressionTests {
    private static ServerPlayer player(GameTestHelper helper) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "StatsTest")) {
            @Override
            public boolean isInvulnerableTo(DamageSource source) { return false; }
        };
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static PlayerStats stats(ServerPlayer player) {
        return player.getCapability(PlayerStatsProvider.PLAYER_STATS).orElseThrow();
    }

    private static void equal(long expected, long actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": expected " + expected + ", got " + actual);
    }

    private static void close(float expected, float actual, String message) {
        if (Math.abs(expected - actual) > 0.001f) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    private static void add(PlayerStats stats, String id, int count) {
        if (!stats.addPoints(StatType.fromId(id), count)) throw new AssertionError("Cannot allocate " + id);
    }

    @GameTest(template = "empty")
    public static void accountingAndMigration(GameTestHelper helper) {
        PlayerStats stats = new PlayerStats();
        CompoundTag saved = stats.serializeNBT();
        saved.putLong("level", 1000);
        stats.deserializeNBT(saved);
        stats.recalculateAvailablePoints();
        long earned = stats.getAvailablePoints();
        add(stats, "armor", 7);
        helper.assertTrue(stats.removePoints(StatType.fromId("movement_speed"), 3), "Negative allocation failed");
        stats.getFurnaceData().setSpeedLevel(2);
        stats.setCraftingMultiplier(4);
        stats.recalculateAvailablePoints();
        long upgradeCost = 2L * Config.FURNACE_SPEED_COST.get() + 3L * Config.CRAFTING_MULTIPLIER_COST.get();
        equal(earned - 10 - upgradeCost, stats.getAvailablePoints(), "Upgrade costs and negative points");
        PlayerStats loaded = new PlayerStats();
        saved = stats.serializeNBT();
        ListTag allocations = saved.getList("allocatedPoints", 10);
        for (String id : new String[]{"cooldown_reduction", "spell_power", "teleport_distance", "follow_range"}) {
            helper.assertTrue(StatType.fromId(id) == null, "Removed stat remains available: " + id);
            CompoundTag old = new CompoundTag();
            old.putString("id", id);
            old.putLong("points", 5);
            allocations.add(old);
        }
        saved.putLong("availablePoints", stats.getAvailablePoints() - 20);
        loaded.deserializeNBT(saved);
        loaded.recalculateAvailablePoints();
        equal(stats.getAvailablePoints(), loaded.getAvailablePoints(), "Legacy removed stat refund");
        loaded.resetStat(StatType.fromId("movement_speed"));
        equal(earned - 7 - upgradeCost, loaded.getAvailablePoints(), "Negative stat refund");
        loaded.addExperience(loaded.getXpForNextLevel());
        equal(earned + Config.POINTS_PER_LEVEL.get() - 7 - upgradeCost, loaded.getAvailablePoints(), "Level-up accounting");
        loaded.resetAllPoints();
        equal(earned + Config.POINTS_PER_LEVEL.get() - upgradeCost, loaded.getAvailablePoints(), "Reset keeps upgrade costs");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void favoritesSurviveCopyAndWire(GameTestHelper helper) {
        PlayerStats source = new PlayerStats();
        source.toggleFavorite("armor");
        PlayerStats clone = new PlayerStats();
        clone.copyFrom(source);
        helper.assertTrue(clone.isFavorite("armor"), "Clone lost favorite");
        PlayerStats loaded = new PlayerStats();
        loaded.deserializeNBT(source.serializeNBT());
        helper.assertTrue(loaded.isFavorite("armor"), "Save lost favorite");
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            SyncStatsPacket.encode(new SyncStatsPacket(source.createSnapshot()), buf);
            PlayerStats client = new PlayerStats();
            client.restoreFromSnapshot(new SyncStatsPacket(buf).getSnapshot());
            helper.assertTrue(client.isFavorite("armor"), "Network sync lost favorite");
            source.toggleFavorite("armor");
            buf.clear();
            SyncStatsPacket.encode(new SyncStatsPacket(source.createSnapshot()), buf);
            client.restoreFromSnapshot(new SyncStatsPacket(buf).getSnapshot());
            helper.assertTrue(!client.isFavorite("armor"), "Network sync failed to remove favorite");
        } finally { buf.release(); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void craftingConservesMaterials(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        PortableCraftingMenu menu = new PortableCraftingMenu(1, player.getInventory());
        player.containerMenu = menu;
        menu.getSlot(1).set(new ItemStack(Items.DIAMOND, 5));
        player.getInventory().setItem(0, new ItemStack(Items.OAK_PLANKS, 1));
        Ingredient[] grid = new Ingredient[9];
        Arrays.fill(grid, Ingredient.EMPTY);
        grid[0] = Ingredient.of(Items.OAK_PLANKS);
        grid[1] = Ingredient.of(Items.OAK_PLANKS);
        menu.fillGridFromIngredients(grid);
        helper.assertTrue(menu.getSlot(1).getItem().is(Items.DIAMOND), "Failed fill overwrote grid");
        equal(5, menu.getSlot(1).getItem().getCount(), "Failed fill lost old materials");
        equal(1, player.getInventory().countItem(Items.OAK_PLANKS), "Failed fill consumed partial materials");
        player.getInventory().setItem(0, new ItemStack(Items.OAK_PLANKS, 2));
        menu.fillGridFromIngredients(grid);
        equal(5, player.getInventory().countItem(Items.DIAMOND), "Replacement lost old grid");
        equal(1, menu.getSlot(1).getItem().getCount(), "First ingredient");
        equal(1, menu.getSlot(2).getItem().getCount(), "Second ingredient");
        menu.removed(player);
        equal(2, player.getInventory().countItem(Items.OAK_PLANKS), "Close did not return ingredients");
        menu.removed(player);
        equal(2, player.getInventory().countItem(Items.OAK_PLANKS), "Close duplicated ingredients");
        PortableCraftingMenu second = new PortableCraftingMenu(2, player.getInventory());
        player.getInventory().clearContent();
        second.getSlot(1).set(new ItemStack(Items.OAK_PLANKS, 64));
        second.fillGridFromIngredients(grid);
        equal(63, second.getSlot(1).getItem().getCount(), "Reuse existing stack");
        equal(1, second.getSlot(2).getItem().getCount(), "Redistribute existing material");
        second.removed(player);
        equal(64, player.getInventory().countItem(Items.OAK_PLANKS), "Redistribution changed material total");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bulkStorageWireAndClicks(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        PlayerFurnaceData data = stats(player).getFurnaceData();
        ItemStack tagged = new ItemStack(Items.RAW_IRON, 1);
        tagged.getOrCreateTag().putString("regression", "preserve");
        data.setSlot(1, tagged);
        data.setAmountOnly(1, 65537);
        data.setSpeedLevel(40000);
        PortableFurnaceMenu furnace = new PortableFurnaceMenu(1, player.getInventory());
        verifyWire(furnace, 10);
        furnace.clicked(1, 0, ClickType.PICKUP, player);
        equal(64, furnace.getCarried().getCount(), "Bulk pickup must be an ordinary stack");
        equal(65473, data.getAmount(1), "Bulk pickup inventory debit");
        helper.assertTrue("preserve".equals(furnace.getCarried().getTag().getString("regression")), "Bulk pickup lost NBT");
        furnace.clicked(1, 0, ClickType.PICKUP, player);
        equal(65537, data.getAmount(1), "Bulk deposit inventory credit");
        helper.assertTrue(furnace.getCarried().isEmpty(), "Deposit kept cursor items");
        data.setAmountOnly(1, 129);
        furnace.quickMoveStack(player, 1);
        equal(129, player.getInventory().countItem(Items.RAW_IRON), "Shift pickup lost items at byte boundary");
        equal(0, data.getAmount(1), "Shift pickup retained phantom inventory");
        data.getInputBuffer().set(0, tagged.copyWithCount(1));
        data.getInputAmounts()[0] = 128;
        data.getOutputBuffer().set(0, new ItemStack(Items.IRON_INGOT));
        data.getOutputAmounts()[0] = Integer.MAX_VALUE;
        verifyWire(new FurnaceFuelBufferMenu(2, player.getInventory()), 0);
        verifyWire(new FurnaceProductBufferMenu(3, player.getInventory()), 0);
        PlayerFurnaceData saved = new PlayerFurnaceData();
        saved.deserializeNBT(data.serializeNBT());
        equal(128, saved.getInputAmounts()[0], "Input buffer save");
        equal(Integer.MAX_VALUE, saved.getOutputAmounts()[0], "Output buffer save");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void furnaceRoutingAndQueueCapacity(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        PlayerFurnaceData data = stats(player).getFurnaceData();
        PortableFurnaceMenu menu = new PortableFurnaceMenu(1, player.getInventory());
        helper.assertTrue(!menu.getSlot(0).mayPlace(new ItemStack(Items.DIAMOND)), "Fuel slot accepted junk");
        helper.assertTrue(!menu.getSlot(1).mayPlace(new ItemStack(Items.DIAMOND)), "Input accepted unsmeltable item");
        helper.assertTrue(!menu.getSlot(2).mayPlace(new ItemStack(Items.IRON_INGOT)), "Output accepted deposits");
        helper.assertTrue(menu.getSlot(1).mayPlace(new ItemStack(Items.OAK_LOG)), "Cannot manually make charcoal");
        player.getInventory().setItem(9, new ItemStack(Items.RAW_IRON, 32));
        player.getInventory().setItem(10, new ItemStack(Items.RAW_GOLD, 16));
        player.getInventory().setItem(11, new ItemStack(Items.COAL, 8));
        player.getInventory().setItem(12, new ItemStack(Items.DIAMOND, 3));
        for (int slot = 3; slot <= 6; slot++) menu.quickMoveStack(player, slot);
        equal(32, data.getAmount(1), "First material must enter the input");
        equal(16, Arrays.stream(data.getInputAmounts()).sum(), "Other material must enter the queue");
        equal(8, data.getAmount(0), "Fuel routing");
        equal(3, player.getInventory().countItem(Items.DIAMOND), "Unsmeltable items must stay in inventory");
        helper.assertTrue(!data.pullInputFromBuffer(), "Queue must not overwrite an occupied input");
        equal(32, data.getAmount(1), "Queue overwrote active batch");
        data.getInputBuffer().set(0, ItemStack.EMPTY);
        data.getInputAmounts()[0] = 0;
        data.getInputBuffer().set(5, new ItemStack(Items.RAW_GOLD));
        data.getInputAmounts()[5] = 16;
        player.getInventory().setItem(13, new ItemStack(Items.RAW_GOLD, 5));
        menu.quickMoveStack(player, 7);
        equal(21, data.getInputAmounts()[5], "Queue should merge existing stacks before empty slots");
        equal(0, data.getInputAmounts()[0], "Queue created an unnecessary extra stack");
        FurnaceFuelBufferMenu queue = new FurnaceFuelBufferMenu(2, player.getInventory());
        helper.assertTrue(queue.getSlot(0).mayPlace(new ItemStack(Items.OAK_LOG)), "Queue rejected logs");
        player.getInventory().setItem(14, new ItemStack(Items.RAW_GOLD, 4));
        queue.quickMoveStack(player, 27 + 5);
        equal(25, data.getInputAmounts()[5], "Storage Shift deposit must merge before filling empty slots");
        for (int i = 0; i < data.getInputBuffer().size(); i++) {
            data.getInputBuffer().set(i, new ItemStack(Items.RAW_GOLD));
            data.getInputAmounts()[i] = PlayerFurnaceData.UNBOUNDED;
        }
        player.getInventory().setItem(15, new ItemStack(Items.RAW_COPPER, 7));
        menu.quickMoveStack(player, 9);
        equal(7, player.getInventory().countItem(Items.RAW_COPPER), "Full queue must leave items in inventory");
        data.setSlot(2, new ItemStack(Items.IRON_INGOT, 8));
        menu.setCarried(new ItemStack(Items.IRON_INGOT, 60));
        menu.clicked(2, 0, ClickType.PICKUP, player);
        equal(64, menu.getCarried().getCount(), "Output click should top up a matching carried stack");
        equal(4, data.getAmount(2), "Output pickup must debit only the collected amount");
        menu.setCarried(new ItemStack(Items.DIAMOND));
        menu.clicked(2, 0, ClickType.PICKUP, player);
        equal(4, data.getAmount(2), "Output accepted an unrelated carried item");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void furnaceCollectConservesItems(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        player.setGameMode(GameType.CREATIVE);
        PlayerFurnaceData data = stats(player).getFurnaceData();
        for (int i = 0; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
        player.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 63));
        player.getInventory().setItem(40, new ItemStack(Items.DIAMOND));
        data.setSlot(2, new ItemStack(Items.IRON_INGOT, 7));
        data.getOutputBuffer().set(0, new ItemStack(Items.IRON_INGOT));
        data.getOutputAmounts()[0] = 20;
        equal(1, data.collectProducts(player.getInventory()), "Partial inventory should accept exactly one");
        equal(6, data.getAmount(2), "Partial collection output debit");
        equal(20, data.getOutputAmounts()[0], "Full inventory consumed stored products");
        equal(0, data.collectProducts(player.getInventory()), "Repeated collection with full inventory");
        player.getInventory().setItem(1, ItemStack.EMPTY);
        equal(26, data.collectProducts(player.getInventory()), "Collect both output and product storage");
        equal(26, player.getInventory().getItem(1).getCount(), "Merge output and storage in one inventory slot");
        equal(0, data.getAmount(2), "Collected output retained items");
        equal(0, data.getOutputAmounts()[0], "Collected buffer retained items");
        ItemStack tagged = new ItemStack(Items.GOLD_INGOT);
        tagged.getOrCreateTag().putString("furnace_test", "preserve");
        data.getOutputBuffer().set(1, tagged);
        data.getOutputAmounts()[1] = 65537;
        player.getInventory().setItem(2, ItemStack.EMPTY);
        equal(64, data.collectProducts(player.getInventory()), "Bulk collection must obey ordinary stack size");
        equal(65473, data.getOutputAmounts()[1], "Bulk collection lost or duplicated overflow");
        helper.assertTrue("preserve".equals(player.getInventory().getItem(2).getTag().getString("furnace_test")),
                "Collection discarded item tags");
        helper.assertTrue(player.getInventory().getItem(40).is(Items.DIAMOND), "Collection changed offhand");
        equal(0, data.collectProducts(player.getInventory()), "Creative full inventory must not delete items");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void furnaceHeatProgressAndBatchSpeed(GameTestHelper helper) {
        PlayerFurnaceData data = new PlayerFurnaceData();
        data.setSlot(0, new ItemStack(Items.COAL));
        data.setSlot(1, new ItemStack(Items.RAW_IRON));
        data.setSpeedLevel(99);
        data.tick(helper.getLevel());
        equal(1, Arrays.stream(data.getOutputAmounts()).sum(), "One boosted iron should enter product storage immediately");
        int heat = data.getData()[0];
        helper.assertTrue(heat > 0, "Initial fuel did not retain heat");
        for (int i = 0; i < 20; i++) data.tick(helper.getLevel());
        equal(heat, data.getData()[0], "Idle furnace discarded or consumed remaining fuel");
        PlayerFurnaceData saved = new PlayerFurnaceData();
        saved.deserializeNBT(data.serializeNBT());
        equal(heat, saved.getData()[0], "Idle fuel was not persisted");
        saved.setSlot(1, new ItemStack(Items.RAW_GOLD));
        saved.tick(helper.getLevel());
        equal(2, Arrays.stream(saved.getOutputAmounts()).sum(), "Saved heat did not start the next batch");
        equal(heat - 1, saved.getData()[0], "Working furnace consumed wrong amount of heat");
        PlayerFurnaceData fast = new PlayerFurnaceData();
        fast.setSlot(0, new ItemStack(Items.COAL));
        fast.setSlot(1, new ItemStack(Items.RAW_IRON, 10));
        fast.setSpeedLevel(249);
        fast.tick(helper.getLevel());
        equal(2, Arrays.stream(fast.getOutputAmounts()).sum(), "High speed was capped to one item per tick");
        equal(50, fast.getCook(0), "Fractional smelting progress discarded");
        fast.tick(helper.getLevel());
        equal(5, Arrays.stream(fast.getOutputAmounts()).sum(), "Fractional progress not used on next tick");
        fast.setSlot(1, new ItemStack(Items.SAND, 2));
        equal(0, fast.getCook(0), "Different material inherited previous recipe progress");
        fast.setSpeedLevel(Integer.MAX_VALUE);
        fast.tick(helper.getLevel());
        equal(0, fast.getAmount(1), "Maximum speed overflowed or left input unprocessed");
        equal(7, Arrays.stream(fast.getOutputAmounts()).sum(), "Maximum speed did not conserve products");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void furnaceFuelPauseAndOutputBackpressure(GameTestHelper helper) {
        PlayerFurnaceData data = new PlayerFurnaceData();
        data.setSlot(1, new ItemStack(Items.RAW_IRON, 2));
        data.setCook(0, 50);
        data.setCookTotal(0, 100);
        data.tick(helper.getLevel());
        equal(50, data.getCook(0), "Fuel shortage erased progress");
        equal(2, data.getWorkStatus(helper.getLevel()), "Fuel shortage not reported");
        data.setSlot(0, new ItemStack(Items.COAL));
        data.tick(helper.getLevel());
        equal(51, data.getCook(0), "Refuelling did not resume progress");
        for (int i = 0; i < data.getOutputBuffer().size(); i++) {
            data.getOutputBuffer().set(i, new ItemStack(Items.IRON_INGOT));
            data.getOutputAmounts()[i] = PlayerFurnaceData.UNBOUNDED;
        }
        data.setSlot(2, new ItemStack(Items.IRON_INGOT));
        data.setAmountOnly(2, PlayerFurnaceData.UNBOUNDED);
        int heat = data.getData()[0];
        data.tick(helper.getLevel());
        equal(4, data.getWorkStatus(helper.getLevel()), "Blocked output not reported");
        equal(heat, data.getData()[0], "Blocked output wasted fuel");
        equal(51, data.getCook(0), "Blocked output erased progress");
        equal(2, data.getAmount(1), "Blocked output consumed input");
        data.getOutputBuffer().set(0, ItemStack.EMPTY);
        data.getOutputAmounts()[0] = 0;
        data.tick(helper.getLevel());
        equal(52, data.getCook(0), "Freeing product storage did not resume smelting");
        data.setSlot(1, new ItemStack(Items.DIAMOND));
        equal(3, data.getWorkStatus(helper.getLevel()), "Legacy invalid input not reported");
        equal(0, data.getCook(0), "Replacing recipe input did not reset progress");
        helper.succeed();
    }

    private static void verifyWire(BulkStorageMenu source, int progressFields) {
        WireMenu mirror = new WireMenu(source.getBulkSlotCount(), source.slots.size(), progressFields);
        source.setSynchronizer(new ContainerSynchronizer() {
            public void sendInitialData(AbstractContainerMenu menu, NonNullList<ItemStack> items, ItemStack carried, int[] values) {
                for (int i = 0; i < items.size(); i++) sendSlotChange(menu, i, items.get(i));
                for (int i = 0; i < values.length; i++) sendDataChange(menu, i, values[i]);
            }
            public void sendSlotChange(AbstractContainerMenu menu, int index, ItemStack stack) {
                if (index < source.getBulkSlotCount() && stack.getCount() > 1) {
                    throw new AssertionError("Bulk slot sent count in ItemStack");
                }
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    buf.writeItem(stack);
                    mirror.getSlot(index).set(buf.readItem());
                } finally { buf.release(); }
            }
            public void sendCarriedChange(AbstractContainerMenu menu, ItemStack carried) {}
            public void sendDataChange(AbstractContainerMenu menu, int index, int value) {
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    new ClientboundContainerSetDataPacket(menu.containerId, index, value).write(buf);
                    var decoded = new ClientboundContainerSetDataPacket(buf);
                    mirror.setData(decoded.getId(), decoded.getValue());
                } finally { buf.release(); }
            }
        });
        source.broadcastChanges();
        for (int i = 0; i < source.getBulkSlotCount(); i++) {
            equal(source.getBulkAmount(i), mirror.getBulkAmount(i), "Bulk quantity wire roundtrip");
        }
        if (progressFields > 0) equal(40000, mirror.progress[4], "Speed level wire roundtrip");
        if (source instanceof PortableFurnaceMenu furnace) {
            equal(furnace.getWorkStatus(), mirror.progress[5], "Work status wire roundtrip");
            equal(furnace.getQueuedSlots(), mirror.progress[6], "Queue occupancy wire roundtrip");
            equal(furnace.getProductSlots(), mirror.progress[7], "Product occupancy wire roundtrip");
            equal(furnace.getSpeedCost(), mirror.progress[8], "Server speed cost wire roundtrip");
            equal(furnace.canUpgrade() ? 1 : 0, mirror.progress[9], "Upgrade availability wire roundtrip");
        }
    }

    private static final class WireMenu extends BulkStorageMenu {
        final int[] progress;
        WireMenu(int bulk, int size, int fields) {
            super(null, 1);
            SimpleContainer container = new SimpleContainer(size);
            for (int i = 0; i < size; i++) addSlot(new Slot(container, i, 0, 0));
            progress = new int[fields];
            trackIntData(new ContainerData() {
                public int get(int i) { return progress[i]; }
                public void set(int i, int value) { progress[i] = value; }
                public int getCount() { return progress.length; }
            });
            trackBulkAmounts(new long[bulk]);
        }
        public boolean stillValid(Player player) { return true; }
    }

    @GameTest(template = "empty")
    public static void emcPartialRefundAndPersistence(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        ResourceLocation id = new ResourceLocation("minecraft", "diamond");
        EmcPlayerData data = new EmcPlayerData();
        data.learnItem(id);
        data.setEmcBalance(10000);
        long oldPrice = EmcDatabase.getCustomEmc(id);
        double loss = Config.EMC_LOSS_RATE.get();
        boolean enabled = Config.EMC_ENABLED.get();
        try {
            Config.EMC_ENABLED.set(true);
            helper.assertTrue(EmcDatabase.setCustomEmc(id, 32), "Custom price save failed");
            for (int i = 0; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
            player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 63));
            EmcTransactions.extract(player, data, id, -1);
            equal(64, player.getInventory().countItem(Items.DIAMOND), "Partially filled purchase");
            equal(9968, data.getEmcBalance(), "Partial purchase must charge delivered item");
            EmcTransactions.extract(player, data, id, -1);
            equal(9968, data.getEmcBalance(), "Full inventory must refund everything");
            Config.EMC_LOSS_RATE.set(0.25);
            equal(72, EmcDatabase.getSellValue(new ItemStack(Items.DIAMOND), 3), "Configured EMC loss");
            EmcDatabase.reload(helper.getLevel().getServer());
            equal(32, EmcDatabase.getEmc(new ItemStack(Items.DIAMOND)), "Reload discarded custom price");
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                NetworkHandler.EmcSyncPacket.encode(new NetworkHandler.EmcSyncPacket(data.createSnapshot()), buf);
                equal(9968, buf.readVarLong(), "EMC balance wire");
                equal(1, buf.readVarInt(), "EMC learned item wire");
                helper.assertTrue(id.toString().equals(buf.readUtf()), "EMC item ID wire");
                if (buf.readBoolean()) buf.readNbt();
                equal(32, buf.readVarLong(), "Server price wire");
            } finally { buf.release(); }
            Config.EMC_ENABLED.set(false);
            player.getInventory().clearContent();
            EmcTransactions.extract(player, data, id, -1);
            equal(0, player.getInventory().countItem(Items.DIAMOND), "Disabled EMC delivered item");
            equal(0, EmcDatabase.getSellValue(new ItemStack(Items.DIAMOND), 3), "Disabled EMC credited sale");
            Config.EMC_ENABLED.set(true);
            helper.assertTrue(EmcDatabase.setCustomEmc(id, Long.MAX_VALUE), "Large price save failed");
            data.setEmcBalance(Long.MAX_VALUE);
            EmcTransactions.extract(player, data, id, 64);
            equal(1, player.getInventory().countItem(Items.DIAMOND), "Price multiplication overflow");
            equal(0, data.getEmcBalance(), "Large price settlement");
        } finally {
            Config.EMC_LOSS_RATE.set(loss);
            Config.EMC_ENABLED.set(enabled);
            EmcDatabase.setCustomEmc(id, oldPrice);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void damageTypesAndPenetration(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        PlayerStats stats = stats(player);
        stats.setAvailablePoints(1000);
        add(stats, "true_damage", 100);
        var target = EntityType.ZOMBIE.create(helper.getLevel());
        target.getAttribute(Attributes.ARMOR).setBaseValue(20);
        target.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(8);
        target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 3));
        target.invulnerableTime = 20;
        float health = target.getHealth();
        AttackHandler.applyTrueDamage(player, stats, 3, target);
        AttackHandler.applyTrueDamage(player, stats, 3, target);
        close(health - 6, target.getHealth(), "True damage must ignore armor, resistance and cooldown");
        var holder = helper.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("infinitestats", "direct_damage")));
        DamageSource direct = new DamageSource(holder, player);
        helper.assertTrue(direct.is(DamageTypeTags.BYPASSES_ARMOR) && direct.is(DamageTypeTags.BYPASSES_COOLDOWN), "True damage tags not loaded");
        helper.assertTrue(!player.damageSources().arrow(new Arrow(helper.getLevel(), player), player)
                .is(AttackHandler.MAGIC_DAMAGE), "Ordinary arrow classified as magic");
        helper.assertTrue(player.damageSources().indirectMagic(player, player)
                .is(AttackHandler.MAGIC_DAMAGE), "Magic damage tag missing");
        for (float armor : new float[]{0, 10, 20, 40}) {
            for (float toughness : new float[]{0, 8, 20}) {
                for (float penetration : new float[]{0, 0.25f, 1}) {
                    float input = AttackHandler.compensateArmor(8, armor, toughness, penetration);
                    close(CombatRules.getDamageAfterAbsorb(8, armor * (1 - penetration), toughness),
                            CombatRules.getDamageAfterAbsorb(input, armor, toughness), "Armor penetration result");
                }
            }
        }
        ServerPlayer victim = player(helper);
        stats(victim).setAvailablePoints(1000);
        add(stats(victim), "damage_reduction", 400);
        victim.invulnerableTime = 20;
        health = victim.getHealth();
        victim.hurt(direct, 3);
        close(health - 3, victim.getHealth(), "Own damage reduction reduced true damage");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void shieldAndResetEffects(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        PlayerStats stats = stats(player);
        stats.setAvailablePoints(10000);
        for (int points : new int[]{1, 4, 16, 24}) {
            stats.resetStat(StatType.fromId("absorption_shield"));
            add(stats, "absorption_shield", points);
            DefenseHandler.applyAbsorptionShield(player, stats);
            close(points, player.getAbsorptionAmount(), "Shield points scaling");
        }
        player.setAbsorptionAmount(player.getAbsorptionAmount() + 5);
        DefenseHandler.applyAbsorptionShield(player, stats);
        close(29, player.getAbsorptionAmount(), "Preserve external absorption");
        stats.resetStat(StatType.fromId("absorption_shield"));
        DefenseHandler.applyAbsorptionShield(player, stats);
        close(5, player.getAbsorptionAmount(), "Reset must keep external absorption");
        add(stats, "fly", 1);
        add(stats, "fly_speed", 100);
        add(stats, "invisibility", 1);
        add(stats, "night_vision", 1);
        MobilityHandler mobility = new MobilityHandler();
        UtilityHandler utility = new UtilityHandler();
        mobility.onTick(player, stats, 40);
        utility.onTick(player, stats, 40);
        close(0.06f, player.getAbilities().getFlyingSpeed(), "Flight speed bonus");
        helper.assertTrue(player.getAbilities().mayfly && player.hasEffect(MobEffects.INVISIBILITY), "Effects not granted");
        stats.resetAllPoints();
        mobility.onTick(player, stats, 40);
        utility.onTick(player, stats, 40);
        close(0.05f, player.getAbilities().getFlyingSpeed(), "Reset restores original flight speed");
        helper.assertTrue(!player.getAbilities().mayfly && !player.isInvisible()
                && !player.hasEffect(MobEffects.NIGHT_VISION), "Reset left owned abilities active");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void configuredRegenerationIntervals(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        PlayerStats stats = stats(player);
        stats.setAvailablePoints(1000);
        add(stats, "health_regen", 20);
        add(stats, "max_mana", 10);
        add(stats, "mana_regen", 10);
        player.setHealth(10);
        stats.setCurrentMana(0);
        int healthInterval = Config.HEALTH_REGEN_INTERVAL.get();
        int manaInterval = Config.MANA_REGEN_INTERVAL.get();
        try {
            Config.HEALTH_REGEN_INTERVAL.set(7);
            Config.MANA_REGEN_INTERVAL.set(11);
            DefenseHandler defense = new DefenseHandler();
            MagicHandler magic = new MagicHandler();
            defense.onTick(player, stats, 6);
            magic.onTick(player, stats, 10);
            close(10, player.getHealth(), "Health regenerated too early");
            close(0, stats.getCurrentMana(), "Mana regenerated too early");
            defense.onTick(player, stats, 7);
            magic.onTick(player, stats, 11);
            close(11, player.getHealth(), "Configured health interval ignored");
            close(stats.getStatValue("mana_regen"), stats.getCurrentMana(), "Configured mana interval ignored");
        } finally {
            Config.HEALTH_REGEN_INTERVAL.set(healthInterval);
            Config.MANA_REGEN_INTERVAL.set(manaInterval);
        }
        helper.succeed();
    }
}
