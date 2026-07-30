package com.infinitestats.crafting;

import com.infinitestats.Config;
import com.infinitestats.emc.ModMenuTypes;
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

    public PortableCraftingMenu(int windowId, Inventory inv) {
        super(ModMenuTypes.PORTABLE_CRAFTING_MENU.get(), windowId);
        this.player = inv.player;
        this.level = inv.player.level();
        this.stats = inv.player.getCapability(PlayerStatsProvider.PLAYER_STATS).orElseGet(PlayerStats::new);
        // 倍率视图与成品去向：随容器数据自动同步到客户端
        //   index 0 = 倍率；index 1 = 成品去向（0=背包，1=存储）
        this.data = new ContainerData() {
            @Override
            public int getCount() {
                return 2;
            }

            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> (int) Math.min(Integer.MAX_VALUE, stats.getCraftingMultiplier());
                    case 1 -> outputToStorage ? 1 : 0;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                switch (index) {
                    case 0 -> stats.setCraftingMultiplier(value);
                    case 1 -> outputToStorage = value != 0;
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

    /** 当前随身工作台物品倍率（用于界面显示）。 */
    public int getCraftingMultiplier() {
        return (int) Math.max(1, stats.getCraftingMultiplier());
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
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        ItemStack result = ItemStack.EMPTY;
        Optional<CraftingRecipe> optional = level.getServer().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, craftSlots, level);
        if (optional.isPresent()) {
                CraftingRecipe recipe = optional.get();
            if (resultSlots.setRecipeUsed(level, serverPlayer, recipe)) {
                ItemStack assembled = recipe.assemble(craftSlots, level.registryAccess());
                if (assembled.isItemEnabled(level.enabledFeatures())) {
                    result = assembled;
                }
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

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index == 0) {
                // 结果槽：按当前去向设置，把成品送往背包或存储空间
                if (this.outputToStorage) {
                    List<NetworkHandle> nets = NetworkIO.getNetworks(player);
                    if (!nets.isEmpty()) {
                        // 优先把成品存入网络，存不下的剩余再退回背包
                        ItemStack remaining = NetworkIO.insert(nets, stack.copy());
                        int stored = stack.getCount() - remaining.getCount();
                        if (stored > 0) stack.split(stored);
                        if (!stack.isEmpty()
                                && !this.moveItemStackTo(stack, 10, 46, true)) {
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
        }
        return result;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);

        List<NetworkHandle> nets = NetworkIO.getNetworks(player);
        List<ItemStack> overflow = new ArrayList<>();

        for (int i = 0; i < craftSlots.getContainerSize(); i++) {
            ItemStack stack = craftSlots.getItem(i);
            if (stack.isEmpty()) continue;

            // 优先归还到存储网络（从哪取材就退回哪）
            if (!nets.isEmpty()) {
                ItemStack remaining = NetworkIO.insert(nets, stack.copy());
                if (remaining.isEmpty()) continue; // 全部存入网络
                // 网络满了 → 剩余部分放回背包
                if (!player.getInventory().add(remaining)) {
                    overflow.add(remaining);
                }
            } else {
                // 无网络 → 放回玩家背包
                if (!player.getInventory().add(stack.copy())) {
                    overflow.add(stack.copy());
                }
            }
        }
        craftSlots.clearContent();
        resultSlots.clearContent();

        // 背包满了 → 掉落在地
        for (ItemStack s : overflow) {
            player.drop(s, false);
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
        if (level.isClientSide() || !(player instanceof ServerPlayer sp)) return;
        List<NetworkHandle> nets = NetworkIO.getNetworks(player);
        boolean hasNet = !nets.isEmpty();

        // ── 第一趟：只检查，不从背包/网络取出（避免半路失败导致材料丢失）──
        boolean[] ok = new boolean[9];
        for (int i = 0; i < 9; i++) {
            Ingredient ing = grid[i];
            if (ing == null || ing.isEmpty()) {
                ok[i] = true;
                continue;
            }
            Slot s = getSlot(i + 1);
            ItemStack cur = s.getItem();
            if (!cur.isEmpty() && ing.test(cur)) {
                ok[i] = true; // 已有正确的
                continue;
            }
            // 检查背包
            if (inventoryHas(ing)) { ok[i] = true; continue; }
            // 检查存储网络
            if (hasNet && networkHas(nets, ing)) { ok[i] = true; continue; }
        }

        // ── 任一格子不满足 → 完全不操作，不退材料 ──
        for (int i = 0; i < 9; i++) {
            if (!ok[i]) {
                sp.sendSystemMessage(Component.literal(hasNet
                        ? "§c材料不足：背包和存储网络中缺少所需材料，配方未填充"
                        : "§c材料不足：背包中缺少所需材料，请先连接存储网络或备齐材料"));
                return;
            }
        }

        // ── 第二趟：所有材料确认充足，才真正取料 ──
        for (int i = 0; i < 9; i++) {
            Slot s = getSlot(i + 1);
            Ingredient ing = grid[i];
            if (ing == null || ing.isEmpty()) {
                s.set(ItemStack.EMPTY);
                continue;
            }
            ItemStack cur = s.getItem();
            if (!cur.isEmpty() && ing.test(cur)) continue;

            ItemStack fromInv = takeFromInventory(ing, 1);
            if (fromInv == null && hasNet) {
                // 标签 ingredient 可能包含多种物品（如 #minecraft:logs 含橡木/白桦木/...）
                // 必须遍历所有匹配项，否则网络里只有非第一个物品时会被误判失败
                for (ItemStack match : ing.getItems()) {
                    ItemStack got = NetworkIO.extract(nets, match.copy(), 1);
                    if (!got.isEmpty()) { fromInv = got.copyWithCount(1); break; }
                }
            }
            s.set(fromInv != null ? fromInv.copyWithCount(1) : ItemStack.EMPTY);
        }

        slotsChanged(craftSlots);
        player.getInventory().setChanged();
        this.broadcastChanges();
        sp.sendSystemMessage(Component.literal("§a已从背包" + (hasNet ? "/网络" : "") + "补充合成材料"));
    }

    /** 检查背包中是否有匹配 ingredient 的物品（不实际取出）。 */
    private boolean inventoryHas(Ingredient ing) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!s.isEmpty() && ing.test(s)) return true;
        }
        return false;
    }

    /** 检查存储网络中是否有匹配 ingredient 的物品（不实际取出）。 */
    private boolean networkHas(List<NetworkHandle> nets, Ingredient ing) {
        List<ItemStack> items = NetworkIO.listItems(nets);
        for (ItemStack netItem : items) {
            if (!netItem.isEmpty() && ing.test(netItem)) return true;
        }
        return false;
    }

    /** 从玩家背包取出匹配 ingredient 的 count 个物品（真实取出，会改动背包）。 */
    private ItemStack takeFromInventory(Ingredient ing, int count) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!s.isEmpty() && ing.test(s)) {
                ItemStack taken = s.split(count);
                if (taken.isEmpty()) continue;
                if (s.isEmpty()) player.getInventory().setItem(i, ItemStack.EMPTY);
                player.getInventory().setChanged();
                return taken;
            }
        }
        return null;
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
            // 交给原版逻辑消耗一份材料（每个槽 -1，并处理余料/容器物）
            super.onTake(player, stack);
            if (player.level().isClientSide() || !(player instanceof ServerPlayer)) return;
            List<NetworkHandle> nets = NetworkIO.getNetworks(player);
            if (nets.isEmpty()) return;
            for (int i = 0; i < before.length; i++) {
                ItemStack pre = before[i];
                if (pre.isEmpty()) continue; // 取出前为空的槽不补充，避免误拉无关物品
                ItemStack now = craftSlots.getItem(i);
                int deficit = pre.getCount() - now.getCount();
                if (deficit > 0) {
                    ItemStack got = NetworkIO.extract(nets, pre.copyWithCount(deficit), deficit);
                    if (!got.isEmpty()) {
                        if (now.isEmpty()) {
                            craftSlots.setItem(i, got);
                        } else {
                            now.grow(got.getCount());
                        }
                        craftSlots.setChanged();
                    }
                }
            }
            menu.broadcastChanges();
        }
    }
}
