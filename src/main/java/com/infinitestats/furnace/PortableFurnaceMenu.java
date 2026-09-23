package com.infinitestats.furnace;

import com.infinitestats.emc.ModMenuTypes;
import com.infinitestats.Config;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeHooks;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import com.infinitestats.compat.NetworkHandle;
import com.infinitestats.compat.NetworkIO;
import java.util.List;
import java.util.function.Predicate;

/**
 * 随身熔炉菜单。
 * 所有物品与燃烧/冶炼进度都存在玩家 PlayerStats 的 PlayerFurnaceData 中，
 * 因此关闭界面后状态不会丢失，且冶炼在后台持续进行。
 *
 * 单输入、单燃料槽，其他材料通过待炼仓排队；成品自动收纳到成品仓。
 */
public class PortableFurnaceMenu extends BulkStorageMenu {

    private final PlayerFurnaceData furnaceData;
    private final FurnaceContainer furnace;
    private final ContainerData data;
    private final ContainerData controls;
    private final Player player;
    public static final int COLLECT_PRODUCTS = 0;

    public PortableFurnaceMenu(int windowId, Inventory inv) {
        super(ModMenuTypes.PORTABLE_FURNACE_MENU.get(), windowId);

        Player player = inv.player;
        this.player = player;
        PlayerStats stats = player.getCapability(PlayerStatsProvider.PLAYER_STATS).orElseGet(PlayerStats::new);
        this.furnaceData = stats.getFurnaceData();

        this.furnace = new FurnaceContainer(furnaceData, player.level().isClientSide());
        this.data = new FurnaceData(furnaceData.getData());
        this.controls = new ContainerData() {
            private final int[] synced = new int[5];
            public int getCount() { return synced.length; }
            public void set(int index, int value) { synced[index] = value; }
            public int get(int index) {
                if (player.level().isClientSide()) return synced[index];
                return switch (index) {
                    case 0 -> furnaceData.getWorkStatus(player.level());
                    case 1 -> usedSlots(furnaceData.getInputAmounts());
                    case 2 -> usedSlots(furnaceData.getOutputAmounts());
                    case 3 -> Config.FURNACE_SPEED_COST.get();
                    case 4 -> stats.getAvailablePoints() >= Config.FURNACE_SPEED_COST.get()
                            && furnaceData.getSpeedLevel() < Integer.MAX_VALUE - 1 ? 1 : 0;
                    default -> 0;
                };
            }
        };

        // 原版熔炉槽位布局：单输入 + 单输出 + 燃料槽
        this.addSlot(new UnlimitedSlot(furnace, PlayerFurnaceData.FUEL_SLOT, 46, 67,
                stack -> ForgeHooks.getBurnTime(stack, RecipeType.SMELTING) > 0));
        this.addSlot(new UnlimitedSlot(furnace, PlayerFurnaceData.inputSlot(0), 46, 31,
                stack -> PlayerFurnaceData.hasSmeltRecipe(player.level(), stack)));
        this.addSlot(new UnlimitedSlot(furnace, PlayerFurnaceData.outputSlot(0), 116, 49, stack -> false));

        // 玩家背包
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 9; j++) {
                this.addSlot(new Slot(inv, j + i * 9 + 9, 8 + j * 18, 135 + i * 18));
            }
        }
        // 玩家快捷栏
        for (int i = 0; i < 9; i++) {
            this.addSlot(new Slot(inv, i, 8 + i * 18, 193));
        }

        trackIntData(data);
        trackIntData(controls);
        trackBulkAmounts(furnaceData.getAmounts());
    }

    public PlayerFurnaceData getFurnaceData() {
        return furnaceData;
    }

    @Override
    protected int[] getBulkTargets(ItemStack stack) {
        if (ForgeHooks.getBurnTime(stack, RecipeType.SMELTING) > 0) return new int[] { PlayerFurnaceData.FUEL_SLOT };
        if (PlayerFurnaceData.hasSmeltRecipe(player.level(), stack)) return new int[] { PlayerFurnaceData.inputSlot(0) };
        return new int[0];
    }

    @Override
    protected void storeQuickMovedStack(ItemStack stack) {
        super.storeQuickMovedStack(stack);
        if (!stack.isEmpty() && ForgeHooks.getBurnTime(stack, RecipeType.SMELTING) <= 0
                && PlayerFurnaceData.hasSmeltRecipe(player.level(), stack)) {
            furnaceData.enqueueInput(stack);
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (player != this.player || player.level().isClientSide() || id != COLLECT_PRODUCTS) return false;
        long collected = furnaceData.collectProducts(player.getInventory());
        player.displayClientMessage(Component.translatable(collected > 0
                ? "gui.infinitestats.furnace.collected" : "gui.infinitestats.furnace.collect_none", collected), true);
        broadcastChanges();
        return true;
    }

    private static int usedSlots(long[] amounts) {
        int used = 0;
        for (long amount : amounts) if (amount > 0) used++;
        return used;
    }

    public int getWorkStatus() { return controls.get(0); }
    public int getQueuedSlots() { return controls.get(1); }
    public int getProductSlots() { return controls.get(2); }
    public boolean canUpgrade() { return controls.get(4) != 0; }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void removed(Player player) {
        // 不在关闭时把物品退回背包——它们保存在玩家的 PlayerStats 中
        super.removed(player);
    }

    /** 进度查询，供界面使用（读取已同步的 ContainerData） */
    public boolean isLit() {
        return data.get(0) > 0;
    }

    public float getLitProgress() {
        int lit = data.get(0);
        int dur = data.get(1);
        if (dur <= 0) return 0;
        return lit / (float) dur;
    }

    public float getCookProgress(int i) {
        int prog = data.get(2 + 2 * i);
        int total = data.get(3 + 2 * i);
        if (total <= 0) return 0;
        return prog / (float) total;
    }

    /** 当前熔炉加速等级（读取已同步的 ContainerData）。 */
    public int getSpeedLevel() {
        return Math.max(0, data.get(2 + 2 * PlayerFurnaceData.INPUT_COUNT));
    }

    /** 当前熔炼速度倍率（加速等级 + 1）。 */
    public int getSpeedMultiplier() {
        return (int) Math.min(Integer.MAX_VALUE, 1L + getSpeedLevel());
    }

    /** 每级加速消耗的可分配点数。 */
    public int getSpeedCost() {
        return controls.get(3);
    }

    /** 绑定到 PlayerFurnaceData 的容器视图，槽位直接读写持久化数据（数量无上限）。 */
    private static class FurnaceContainer implements Container {
        private final PlayerFurnaceData data;
        private final boolean clientSide;

        FurnaceContainer(PlayerFurnaceData data, boolean clientSide) {
            this.data = data;
            this.clientSide = clientSide;
        }

        @Override
        public int getContainerSize() {
            return PlayerFurnaceData.TOTAL_SLOTS;
        }

        @Override
        public boolean isEmpty() {
            for (int i = 0; i < PlayerFurnaceData.TOTAL_SLOTS; i++) {
                if (data.getAmount(i) > 0) return false;
            }
            return true;
        }

        @Override
        public ItemStack getItem(int index) {
            ItemStack template = data.getItems().get(index);
            return template.isEmpty() ? ItemStack.EMPTY : template.copyWithCount(1);
        }

        @Override
        public ItemStack removeItem(int index, int count) {
            return data.removeSlot(index, count);
        }

        @Override
        public ItemStack removeItemNoUpdate(int index) {
            return data.removeSlot(index, (int) Math.min(data.getAmount(index), PlayerFurnaceData.UNBOUNDED));
        }

        @Override
        public void setItem(int index, ItemStack stack) {
            if (clientSide) data.getItems().set(index, stack.copyWithCount(1));
            else data.setSlot(index, stack);
        }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
            for (int i = 0; i < PlayerFurnaceData.TOTAL_SLOTS; i++) {
                data.clearSlot(i);
            }
        }

        /** 仅更新某槽数量（模板不变），供 shift 转移逻辑使用。 */
        void setAmountOnly(int index, long amount) {
            data.setAmountOnly(index, amount);
        }
    }

    /** 无上限堆叠的槽位：解除 64 限制，并绕过 setCount 钳制进行 merge。 */
    private static class UnlimitedSlot extends Slot {
        private final Predicate<ItemStack> accepts;

        UnlimitedSlot(Container container, int index, int x, int y, Predicate<ItemStack> accepts) {
            super(container, index, x, y);
            this.accepts = accepts;
        }

        @Override
        public boolean mayPlace(ItemStack stack) { return accepts.test(stack); }

        @Override
        public int getMaxStackSize() {
            return PlayerFurnaceData.UNBOUNDED;
        }

    }

    /** 绑定到 PlayerFurnaceData 进度数组的 ContainerData 视图。 */
    private static class FurnaceData implements ContainerData {
        private final int[] arr;

        FurnaceData(int[] arr) {
            this.arr = arr;
        }

        @Override
        public int get(int index) {
            return arr[index];
        }

        @Override
        public void set(int index, int value) {
            arr[index] = value;
        }

        @Override
        public int getCount() {
            return arr.length;
        }
    }

    // ========== Refined Storage 联动 ==========

    /** 从 RS 网络提取可熔炼矿物补入输入槽。 */
    public void refillOreFromNetwork() {
        if (!(player instanceof ServerPlayer sp)) return;
        List<NetworkHandle> nets = NetworkIO.getNetworks(player);
        if (nets.isEmpty()) {
            sp.sendSystemMessage(Component.literal(NetworkIO.diagnose(player)));
            return;
        }
        List<ItemStack> items = NetworkIO.listItems(nets);
        int idx = PlayerFurnaceData.inputSlot(0);
        ItemStack cur = furnaceData.getStack(idx);
        int total = 0;
        for (ItemStack netStack : items) {
            if (netStack.isEmpty()) continue;
            if (!cur.isEmpty() && !ItemStack.isSameItemSameTags(cur, netStack)) continue;
            if (!hasSmeltingRecipe(netStack, player.level())) continue;
            int request = (int) Math.min(64, PlayerFurnaceData.UNBOUNDED - furnaceData.getAmount(idx));
            if (request <= 0) break;
            ItemStack got = NetworkIO.extract(nets, netStack.copyWithCount(request), request);
            if (got.isEmpty()) continue;
            if (cur.isEmpty()) {
                furnaceData.setSlot(idx, got);
                cur = got;
            } else {
                long space = (long) PlayerFurnaceData.UNBOUNDED - furnaceData.getAmount(idx);
                int put = (int) Math.min(got.getCount(), space);
                if (put <= 0) continue;
                furnaceData.setAmountOnly(idx, furnaceData.getAmount(idx) + put);
            }
            this.slots.get(idx).set(furnaceData.getStack(idx));
            total += got.getCount();
            if (total >= 64 * 16) break;
        }
        if (total == 0) sp.sendSystemMessage(Component.literal("§e网络中没有可熔炼的矿物"));
        else sp.sendSystemMessage(Component.literal("§a已从网络提取 §f" + total + "§a 个矿物到输入槽"));
        broadcastChanges();
    }

    /** 从 RS 网络提取燃料补入燃料槽。 */
    public void refillFuelFromNetwork() {
        if (!(player instanceof ServerPlayer sp)) return;
        List<NetworkHandle> nets = NetworkIO.getNetworks(player);
        if (nets.isEmpty()) {
            sp.sendSystemMessage(Component.literal(NetworkIO.diagnose(player)));
            return;
        }
        List<ItemStack> items = NetworkIO.listItems(nets);
        int idx = PlayerFurnaceData.FUEL_SLOT;
        ItemStack cur = furnaceData.getStack(idx);
        int total = 0;
        for (ItemStack netStack : items) {
            if (netStack.isEmpty()) continue;
            if (ForgeHooks.getBurnTime(netStack, RecipeType.SMELTING) <= 0) continue;
            if (!cur.isEmpty() && !ItemStack.isSameItemSameTags(cur, netStack)) continue;
            int request = (int) Math.min(64, PlayerFurnaceData.UNBOUNDED - furnaceData.getAmount(idx));
            if (request <= 0) break;
            ItemStack got = NetworkIO.extract(nets, netStack.copyWithCount(request), request);
            if (got.isEmpty()) continue;
            if (cur.isEmpty()) {
                furnaceData.setSlot(idx, got);
                cur = got;
            } else {
                long space = (long) PlayerFurnaceData.UNBOUNDED - furnaceData.getAmount(idx);
                int put = (int) Math.min(got.getCount(), space);
                if (put <= 0) continue;
                furnaceData.setAmountOnly(idx, furnaceData.getAmount(idx) + put);
            }
            this.slots.get(idx).set(furnaceData.getStack(idx));
            total += got.getCount();
            if (total >= 1024) break;
        }
        if (total == 0) sp.sendSystemMessage(Component.literal("§e网络中没有可用的燃料"));
        else sp.sendSystemMessage(Component.literal("§a已从网络提取 §f" + total + "§a 个燃料到燃料槽"));
        broadcastChanges();
    }

    /** 将成品储备箱（成品仓）中的成品存入 RS 网络。 */
    public void depositProductsToNetwork() {
        if (!(player instanceof ServerPlayer sp)) return;
        List<NetworkHandle> nets = NetworkIO.getNetworks(player);
        if (nets.isEmpty()) {
            sp.sendSystemMessage(Component.literal(NetworkIO.diagnose(player)));
            return;
        }
        sp.sendSystemMessage(furnaceData.depositOutputToNetwork(nets));
        broadcastChanges();
    }

    private boolean hasSmeltingRecipe(ItemStack stack, Level level) {
        return PlayerFurnaceData.hasSmeltRecipe(level, stack);
    }
}
