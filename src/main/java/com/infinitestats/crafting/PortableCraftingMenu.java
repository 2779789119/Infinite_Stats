package com.infinitestats.crafting;

import com.infinitestats.Config;
import com.infinitestats.compat.PolymorphCompat;
import com.infinitestats.emc.EmcPlayerData;
import com.infinitestats.emc.EmcPlayerDataProvider;
import com.infinitestats.emc.EmcPricing;
import com.infinitestats.emc.EmcTransactions;
import com.infinitestats.emc.ModMenuTypes;
import com.infinitestats.network.NetworkHandler;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;

import com.infinitestats.compat.NetworkHandle;
import com.infinitestats.compat.NetworkIO;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 随身工作台菜单（内置工作台属性）。
 * 使用 vanilla 的 TransientCraftingContainer（其 setItem/removeItem 会主动回调
 * menu.slotsChanged）+ ResultContainer，确保放入材料后能实时计算并同步结果槽。
 * 同时通过标准槽位布局兼容 JEI 一键转移配方。
 */
public class PortableCraftingMenu extends AbstractContainerMenu {

    private final CraftingContainer craftSlots = new TransientCraftingContainer(this, 3, 3);
    private final ResultContainer resultSlots = new ResultContainer();
    private final Player player;
    private final Level level;
    private final PlayerStats stats;
    private final ContainerData data;
    /** 成品去向：false=放入玩家背包（默认），true=放入存储空间。 */
    private boolean outputToStorage = false;
    private boolean fillingGrid;
    /** 「消耗材料 + 自动补充」批量更新深度：>0 时抑制 slotsChanged，整批结束再统一重算一次。 */
    private int gridBatchDepth;
    /** 连做封顶用：本游戏刻已连做的次数与所属游戏刻。 */
    private long craftBudgetTick = Long.MIN_VALUE;
    private int craftsMadeThisTick;
    /**
     * 自上次提示以来，因材料不足而由 EMC 自动补齐所花掉的 EMC（服务端累计）。
     * 只在真正花钱时累加，用于给玩家一条动作栏提示，避免「悄悄扣钱」。
     */
    private long emcSpentSinceLastReport;

    /**
     * 同一次点击（按住 Shift 连续取出成品）在**同一个游戏刻内**最多连做多少次。
     * <p>
     * 原版的 Shift 取出是「一次点击 = 一个循环里反复合成」，而本模组的结果槽会从存储网络
     * 自动补料 —— 只要网络里有货，这个循环就能一直转。每做一次都要重算配方并给客户端发包，
     * 次数多了（尤其配方数以千计的大型整合包）主线程就会被卡住。这里封个顶：超出后
     * {@link #quickMoveStack} 返回空，原版循环随即结束，玩家松手再点一次即可继续。
     */
    private static final int MAX_CRAFTS_PER_CLICK = 64;

    public PortableCraftingMenu(int windowId, Inventory inv) {
        super(ModMenuTypes.PORTABLE_CRAFTING_MENU.get(), windowId);
        this.player = inv.player;
        this.level = inv.player.level();
        this.stats = inv.player.getCapability(PlayerStatsProvider.PLAYER_STATS).orElseGet(PlayerStats::new);
        // 倍率视图与成品去向：随容器数据自动同步到客户端
        //   index 0 = 倍率；index 1 = 成品去向（0=背包，1=存储）；index 2 = 点数是否够再升一级
        this.data = new ContainerData() {
            /** 客户端读这份同步值，服务端按实时数据计算（同随身熔炉的写法）。 */
            private final int[] synced = new int[3];

            @Override
            public int getCount() {
                return synced.length;
            }

            @Override
            public int get(int index) {
                if (level.isClientSide()) return synced[index];
                return switch (index) {
                    case 0 -> (int) Math.min(Integer.MAX_VALUE, stats.getCraftingMultiplier());
                    case 1 -> outputToStorage ? 1 : 0;
                    case 2 -> stats.getAvailablePoints() >= Config.CRAFTING_MULTIPLIER_COST.get() ? 1 : 0;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                if (index >= 0 && index < synced.length) synced[index] = value;
                switch (index) {
                    case 0 -> stats.setCraftingMultiplier(value);
                    case 1 -> outputToStorage = value != 0;
                    default -> { }
                }
            }
        };
        this.addDataSlots(this.data);

        // 结果槽（index 0）：取出成品后自动从存储网络补充被消耗的材料
        this.addSlot(new AutoRefillResultSlot(this.player, this.craftSlots, this.resultSlots, 0, 124, 35, this));
        // 3x3 合成网格（index 1..9）
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                this.addSlot(new Slot(this.craftSlots, col + row * 3, 30 + col * 18, 17 + row * 18));
            }
        }
        // 玩家背包（27 格，index 10..36）
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        // 快捷栏（9 格，index 37..45）
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inv, col, 8 + col * 18, 142));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    /** 当前随身工作台物品倍率（用于界面显示，读取已同步的容器数据）。 */
    public int getCraftingMultiplier() {
        return Math.max(1, data.get(0));
    }

    /** 可分配点数是否足够再升一级倍率（用于界面按钮状态与提示）。 */
    public boolean canMultiplyUp() {
        return data.get(2) != 0;
    }

    /** 提升一级倍率所需的可分配点数（用于界面显示）。 */
    public int getMultiplierCost() {
        return Config.CRAFTING_MULTIPLIER_COST.get();
    }

    /** 当前成品是否送往存储空间（true=存储网络，false=玩家背包）。 */
    public boolean isOutputToStorage() {
        return this.outputToStorage;
    }

    /** 切换成品去向（背包 ↔ 存储空间）。 */
    public void toggleOutputToStorage() {
        if (level.isClientSide()) return;
        this.outputToStorage = !this.outputToStorage;
        this.broadcastChanges();
    }

    /** 任意网格槽变化 → 重新计算合成结果并同步到客户端（复刻 vanilla CraftingMenu 逻辑） */
    @Override
    public void slotsChanged(Container container) {
        if (fillingGrid || gridBatchDepth > 0
                || level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        // 装了 Polymorph（多态合成）时交给它挑配方：它会按玩家此前在原版合成台 / 随身工作台
        // 做过的选择决定用哪一个，并把候选列表同步给客户端的配方选择控件；
        // 没装或它没能给出结果时（返回 null）回退到原版「取第一个匹配配方」。
        CraftingRecipe recipe = PolymorphCompat.getSelectedRecipe(serverPlayer, this, craftSlots, level);
        if (recipe == null) {
            recipe = level.getServer().getRecipeManager()
                    .getRecipeFor(RecipeType.CRAFTING, craftSlots, level).orElse(null);
        }

        ItemStack result = ItemStack.EMPTY;
        if (recipe != null && resultSlots.setRecipeUsed(level, serverPlayer, recipe)) {
            ItemStack assembled = recipe.assemble(craftSlots, level.registryAccess());
            if (assembled.isItemEnabled(level.enabledFeatures())) {
                result = assembled;
            }
        }
        // 应用随身工作台物品倍率（影响每次合成的产出数量）
        // 限制单次合成最大产出为一组，防止Shift合成时一次性产出过多物品导致卡顿
        if (!result.isEmpty()) {
            int multiplier = (int) Math.max(1, stats.getCraftingMultiplier());
            result = result.copy();
            result.setCount(Math.min(result.getCount() * multiplier, result.getMaxStackSize()));
        }
        resultSlots.setItem(0, result);
        setRemoteSlot(0, result);
        serverPlayer.connection.send(
                new ClientboundContainerSetSlotPacket(containerId, incrementStateId(), 0, result));
    }

    public void refreshResult() {
        slotsChanged(craftSlots);
    }

    /**
     * 开始一次网格批量更新：期间 {@link #slotsChanged} 被抑制。
     * 一次取料会连续改动多个网格槽，逐个重算（配方查找 + 发包）是卡顿的主要来源。
     */
    void beginGridBatch() {
        gridBatchDepth++;
    }

    /** 结束批量更新；回到最外层时立刻重算一次并把结果槽同步给客户端。 */
    void endGridBatch() {
        if (gridBatchDepth > 0) gridBatchDepth--;
        if (gridBatchDepth == 0) refreshResult();
    }

    /** 领取一次「连做」额度；同一游戏刻内超过 {@link #MAX_CRAFTS_PER_CLICK} 次即拒绝。 */
    private boolean takeCraftBudget() {
        long now = level.getGameTime();
        if (now != craftBudgetTick) {
            craftBudgetTick = now;
            craftsMadeThisTick = 0;
        }
        if (craftsMadeThisTick >= MAX_CRAFTS_PER_CLICK) return false;
        craftsMadeThisTick++;
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index == 0) {
                // 连做封顶：超出后返回空，原版的「连续取出」循环会立即结束，
                // 避免一次点击在同一个游戏刻里做上百次（每次都要重算配方 + 发包）
                if (!takeCraftBudget()) return ItemStack.EMPTY;
                // 结果槽：按当前去向设置，把成品送往背包或存储空间
                if (this.outputToStorage) {
                    List<NetworkHandle> nets = NetworkIO.getNetworks(player, PlayerStats.SCOPE_CRAFTING);
                    if (!nets.isEmpty()) {
                        // 优先把成品存入网络，存不下的剩余再退回背包
                        ItemStack remaining = NetworkIO.insert(nets, stack.copy());
                        int stored = stack.getCount() - remaining.getCount();
                        if (stored > 0) stack.split(stored);
                        if (!stack.isEmpty()
                                && !this.moveItemStackTo(stack, 10, 46, true) && stored == 0) {
                            return ItemStack.EMPTY;
                        }
                    } else if (!this.moveItemStackTo(stack, 10, 46, true)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!this.moveItemStackTo(stack, 10, 46, true)) {
                    return ItemStack.EMPTY;
                }
                slot.onQuickCraft(stack, result);
            } else if (index >= 10) {
                // 玩家背包 → 合成网格
                if (!this.moveItemStackTo(stack, 1, 10, false)) return ItemStack.EMPTY;
            } else {
                // 合成网格 → 玩家背包
                if (!this.moveItemStackTo(stack, 10, 46, false)) return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
            else slot.setChanged();
            slot.onTake(player, stack);
            if (index == 0 && !stack.isEmpty()) player.drop(stack, false);
        }
        return result;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (level.isClientSide()) return;
        List<NetworkHandle> nets = NetworkIO.getNetworks(player, PlayerStats.SCOPE_CRAFTING);
        for (int i = 0; i < craftSlots.getContainerSize(); i++) {
            ItemStack stack = craftSlots.removeItemNoUpdate(i);
            returnMaterial(nets, stack);
        }
        resultSlots.clearContent();
    }

    private void returnMaterial(List<NetworkHandle> nets, ItemStack stack) {
        if (stack.isEmpty()) return;
        ItemStack remaining = NetworkIO.insert(nets, stack.copy());
        if (!player.getInventory().add(remaining) && !remaining.isEmpty()) {
            player.drop(remaining, false);
        }
    }

    // ========== Refined Storage 联动 ==========

    /**
     * 按照给定 3×3 材料布局（左上对齐）从背包优先、再从存储网络补充材料到合成网格。
     * 用于 JEI 一键转移配方：即使材料存放在 RS / AE2 / Beyond Dimensions / 背包 / 汤姆存储 等网络中也能填入。
     *
     * @param grid 长度 9 的 {@link Ingredient} 数组，下标对应 3×3 网格槽位（0..8），空位用 {@link Ingredient#EMPTY}
     */
    public void fillGridFromIngredients(Ingredient[] grid) {
        if (level.isClientSide() || !(player instanceof ServerPlayer sp) || grid.length != 9) return;
        List<NetworkHandle> nets = NetworkIO.getNetworks(player, PlayerStats.SCOPE_CRAFTING);
        ItemStack[] spare = new ItemStack[9];
        ItemStack[] result = new ItemStack[9];
        ItemStack[] inventory = new ItemStack[player.getInventory().getContainerSize()];
        int[] originalCounts = new int[inventory.length];
        List<ItemStack> borrowed = new ArrayList<>();
        List<ItemStack> emcBought = new ArrayList<>();
        for (int i = 0; i < inventory.length; i++) {
            inventory[i] = player.getInventory().getItem(i).copy();
            originalCounts[i] = inventory[i].getCount();
        }

        // 先保留位置和配方都匹配的整堆，其余材料进入可复用的临时库存。
        for (int i = 0; i < 9; i++) {
            ItemStack current = craftSlots.getItem(i).copy();
            boolean keep = grid[i] != null && !grid[i].isEmpty() && grid[i].test(current);
            result[i] = keep ? current : ItemStack.EMPTY;
            spare[i] = keep ? ItemStack.EMPTY : current;
        }
        for (int i = 0; i < 9; i++) {
            Ingredient ingredient = grid[i];
            if (ingredient == null || ingredient.isEmpty() || !result[i].isEmpty()) continue;
            ItemStack taken = takeOne(spare, ingredient);
            if (taken.isEmpty()) {
                for (ItemStack kept : result) {
                    if (kept.getCount() > 1 && ingredient.test(kept)) {
                        taken = kept.split(1);
                        break;
                    }
                }
            }
            if (taken.isEmpty()) taken = takeOne(inventory, ingredient);
            if (taken.isEmpty()) {
                for (ItemStack match : ingredient.getItems()) {
                    taken = NetworkIO.extract(nets, match.copyWithCount(1), 1);
                    if (!taken.isEmpty()) {
                        borrowed.add(taken.copy());
                        break;
                    }
                }
            }
            if (taken.isEmpty()) {
                // 与 EMC 联动：背包与存储网络都取不到时，用 EMC 从知识库采购 1 个（需已学过该物品）
                taken = buyOneWithEmc(sp, ingredient);
                if (!taken.isEmpty()) emcBought.add(taken.copy());
            }
            if (taken.isEmpty()) {
                for (ItemStack stack : borrowed) returnMaterial(nets, stack);
                refundEmc(sp, emcBought);
                sp.sendSystemMessage(Component.literal("§c材料不足，已保留原配方和材料"));
                return;
            }
            result[i] = taken;
        }

        // 所有材料已预留，才提交背包和网格，失败路径不会清空原材料。
        fillingGrid = true;
        try {
            // 网络取料可能更新背包中存储物品的 NBT，只扣除实际预留的数量。
            for (int i = 0; i < inventory.length; i++) {
                int consumed = originalCounts[i] - inventory[i].getCount();
                if (consumed > 0) player.getInventory().removeItem(i, consumed);
            }
            for (int i = 0; i < 9; i++) craftSlots.setItem(i, result[i]);
            for (ItemStack stack : spare) returnMaterial(nets, stack);
        } finally {
            fillingGrid = false;
        }
        refreshResult();
        player.getInventory().setChanged();
        broadcastChanges();

        // 用了 EMC 采购就同步余额并提示，避免「悄悄扣钱」
        if (!emcBought.isEmpty()) reportEmcSpent(sp);
    }

    private static ItemStack takeOne(ItemStack[] inventory, Ingredient ingredient) {
        for (ItemStack stack : inventory) {
            if (!stack.isEmpty() && ingredient.test(stack)) return stack.split(1);
        }
        return ItemStack.EMPTY;
    }

    // ========== EMC 联动：材料不足时用 EMC 从知识库采购 ==========

    /** 用 EMC 采购 1 个匹配该原料的物品（需玩家已学过该物品），失败返回空。 */
    private ItemStack buyOneWithEmc(ServerPlayer sp, Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) return ItemStack.EMPTY;
        EmcPlayerData data = sp.getCapability(EmcPlayerDataProvider.EMC_PLAYER_DATA).orElse(null);
        if (data == null) return ItemStack.EMPTY;
        for (ItemStack match : ingredient.getItems()) {
            ItemStack got = EmcTransactions.purchase(sp, data, match.copyWithCount(1), 1);
            if (!got.isEmpty()) {
                emcSpentSinceLastReport += EmcPricing.buyPrice(sp, got) * got.getCount();
                return got;
            }
        }
        return ItemStack.EMPTY;
    }

    /** 用 EMC 采购至多 count 个 template（自动补料路径），并累计花费。 */
    private ItemStack buyWithEmc(Player player, ItemStack template, int count) {
        if (count <= 0 || !(player instanceof ServerPlayer sp)) return ItemStack.EMPTY;
        EmcPlayerData data = sp.getCapability(EmcPlayerDataProvider.EMC_PLAYER_DATA).orElse(null);
        if (data == null) return ItemStack.EMPTY;
        ItemStack got = EmcTransactions.purchase(sp, data, template, count);
        if (!got.isEmpty()) {
            emcSpentSinceLastReport += EmcPricing.buyPrice(sp, got) * got.getCount();
        }
        return got;
    }

    /** 回滚一批「用 EMC 采购」的材料：按同一价格公式把钱退回。 */
    private void refundEmc(ServerPlayer sp, List<ItemStack> bought) {
        if (bought.isEmpty()) return;
        sp.getCapability(EmcPlayerDataProvider.EMC_PLAYER_DATA).ifPresent(data -> {
            long refund = 0;
            for (ItemStack s : bought) refund += EmcPricing.buyPrice(sp, s) * s.getCount();
            data.addEmc(refund);
            emcSpentSinceLastReport = Math.max(0, emcSpentSinceLastReport - refund);
        });
    }

    /** 若自上次提示以来花过 EMC，则同步余额并给一条动作栏提示。 */
    private void reportEmcSpent(ServerPlayer sp) {
        long spent = emcSpentSinceLastReport;
        emcSpentSinceLastReport = 0;
        if (spent <= 0) return;
        NetworkHandler.syncEmcToClient(sp);
        sp.displayClientMessage(Component.translatable("message.infinitestats.emc.craft_paid",
                EmcTransactions.format(spent)), true);
    }

    /**
     * 自带「自动补充材料」的结果槽：当玩家取出一份成品后，原版逻辑会消耗网格中
     * 对应的一份材料（每个槽 -1，并处理余料/容器物）。取出之后，这里按取出前的网格
     * 快照，把每个被消耗掉的槽位从存储网络补回差额，使网格始终保持满料，可连续制作。
     * 仅在服务端执行补充；若未连接任何存储网络则不做补充（玩家仍可手动放料）。
     */
    private static class AutoRefillResultSlot extends ResultSlot {
        private final PortableCraftingMenu menu;
        private final CraftingContainer craftSlots;

        AutoRefillResultSlot(Player player, CraftingContainer craftingContainer,
                             ResultContainer resultContainer, int slot, int x, int y,
                             PortableCraftingMenu menu) {
            super(player, craftingContainer, resultContainer, slot, x, y);
            this.menu = menu;
            this.craftSlots = craftingContainer;
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            // 记录取出前各网格槽的物品与数量
            ItemStack[] before = new ItemStack[craftSlots.getContainerSize()];
            for (int i = 0; i < before.length; i++) {
                before[i] = craftSlots.getItem(i).copy();
            }
            // 「消耗材料 + 自动补充」期间抑制网格变更回调：原版消耗一份材料会连续改动 9 个网格槽，
            // 而每改一个槽都会触发一次 slotsChanged（重算配方 + 给客户端发包）。Shift 连做几十次时
            // 这会放大成上千次配方查找 —— 配方数以千计的大型整合包里就是一次明显的卡顿甚至假死。
            // 因此整段流程走完后再统一重算一次（见 endGridBatch）。
            menu.beginGridBatch();
            boolean changed = false;
            try {
                // 交给原版逻辑消耗一份材料（每个槽 -1，并处理余料/容器物）
                super.onTake(player, stack);
                if (!player.level().isClientSide() && player instanceof ServerPlayer sp) {
                    menu.emcSpentSinceLastReport = 0;
                    changed = refill(player, before);
                    // 补料时若用掉了 EMC，同步余额并提示玩家
                    menu.reportEmcSpent(sp);
                }
            } finally {
                menu.endGridBatch();
            }
            if (changed) menu.broadcastChanges();
        }

        /**
         * 按取出前的网格快照，把被消耗掉的槽位从存储网络 / 玩家背包补回差额，使网格保持满料，
         * 从而可以连续取出。仅在服务端调用。
         *
         * @return 是否真的补进了东西（没补就不用再广播一次容器内容）
         */
        private boolean refill(Player player, ItemStack[] before) {
            List<NetworkHandle> nets = NetworkIO.getNetworks(player, PlayerStats.SCOPE_CRAFTING);
            boolean changed = false;
            for (int i = 0; i < before.length; i++) {
                ItemStack pre = before[i];
                if (pre.isEmpty()) continue; // 取出前为空的槽不补充，避免误拉无关物品
                ItemStack now = craftSlots.getItem(i);
                if (!now.isEmpty() && !ItemStack.isSameItemSameTags(pre, now)) continue;
                int deficit = pre.getCount() - now.getCount();
                if (deficit <= 0) continue;

                // 先向存储网络要，要不够的差额再从玩家背包补：这样即便一个网络都没连，
                // 也不会出现「取一次就断料、每次都要手动重新摆材料」的卡顿感。
                ItemStack got = nets.isEmpty()
                        ? ItemStack.EMPTY
                        : NetworkIO.extract(nets, pre.copyWithCount(deficit), deficit);
                int missing = deficit - got.getCount();
                if (missing > 0) {
                    ItemStack fromBag = takeFromInventory(player, pre, missing);
                    if (!fromBag.isEmpty()) {
                        if (got.isEmpty()) got = fromBag;
                        else got.grow(fromBag.getCount());
                    }
                }
                // 与 EMC 联动：网络与背包都补不齐时，用 EMC 采购剩余差额（需已学过该物品）
                int stillMissing = deficit - got.getCount();
                if (stillMissing > 0) {
                    ItemStack fromEmc = menu.buyWithEmc(player, pre, stillMissing);
                    if (!fromEmc.isEmpty()) {
                        if (got.isEmpty()) got = fromEmc;
                        else got.grow(fromEmc.getCount());
                    }
                }
                if (got.isEmpty()) continue;

                if (now.isEmpty()) {
                    craftSlots.setItem(i, got);
                } else {
                    now.grow(got.getCount());
                    craftSlots.setItem(i, now);
                }
                changed = true;
            }
            return changed;
        }

        /** 从玩家背包抽取至多 amount 个与模板同种（含 NBT）的物品，返回实际取得的堆。 */
        private static ItemStack takeFromInventory(Player player, ItemStack template, int amount) {
            ItemStack taken = ItemStack.EMPTY;
            Inventory inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize() && taken.getCount() < amount; i++) {
                ItemStack slot = inv.getItem(i);
                if (slot.isEmpty() || !ItemStack.isSameItemSameTags(template, slot)) continue;
                int take = Math.min(amount - taken.getCount(), slot.getCount());
                ItemStack part = slot.copyWithCount(take);
                slot.shrink(take);
                if (slot.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
                if (taken.isEmpty()) taken = part;
                else taken.grow(part.getCount());
            }
            if (!taken.isEmpty()) inv.setChanged();
            return taken;
        }
    }
}
