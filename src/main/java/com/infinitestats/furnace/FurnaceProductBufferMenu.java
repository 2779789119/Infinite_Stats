package com.infinitestats.furnace;

import com.infinitestats.compat.NetworkHandle;
import com.infinitestats.compat.NetworkIO;
import com.infinitestats.emc.ModMenuTypes;

import java.util.List;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 成品储备箱菜单：一个类似箱子的 GUI，用于存放“熔炉输出槽熔炼出的成品”。
 * 熔炉输出槽一旦产出成品，会被自动转入此箱（见 PlayerFurnaceData.pushOutputToBuffer），
 * 玩家可在此统一收集成品。
 *
 * 格子布局 3 行 × 9 列（与外部箱子 UI 一致），每个槽位为普通堆叠上限（64）。
 */
public class FurnaceProductBufferMenu extends AbstractContainerMenu {

    /** 储备箱行 / 列数（与外部箱子一致）。 */
    public static final int ROWS = 3;
    public static final int COLS = 9;
    public static final int BUFFER_SLOTS = ROWS * COLS;

    /**
     * 玩家背包槽位的 y 起点：取“标签下方 + 1 像素间距”。
     * 标签由 FurnaceProductBufferScreen 的 {@code inventoryLabelY = imageHeight - 94 = 74} 绘制，
     * 文字占 ~8 像素，因此玩家背包从 y = 83 开始，正好落在 imageHeight=168 的背景内（快捷栏 141+18=159）。
     */
    private static final int PLAYER_INV_Y = 86;
    private static final int HOTBAR_Y = PLAYER_INV_Y + 3 * 18 + 4; // 141

    private final Player player;
    private final PlayerFurnaceData furnaceData;
    private final ProductBufferContainer buffer;

    public FurnaceProductBufferMenu(int windowId, Inventory inv) {
        super(ModMenuTypes.FURNACE_PRODUCT_BUFFER_MENU.get(), windowId);

        this.player = inv.player;
        this.furnaceData = player.getCapability(PlayerStatsProvider.PLAYER_STATS)
                .orElseGet(PlayerStats::new).getFurnaceData();
        this.buffer = new ProductBufferContainer(furnaceData.getOutputBuffer(), furnaceData.getOutputAmounts());

        // 成品仓格子（3 行 × 9 列），接受任何物品，单格堆叠无上限
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                this.addSlot(new ProductUnboundedSlot(buffer, col + row * COLS, 8 + col * 18, 18 + row * 18));
            }
        }
        // 玩家背包
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, PLAYER_INV_Y + row * 18));
            }
        }
        // 玩家快捷栏
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inv, col, 8 + col * 18, HOTBAR_Y));
        }
    }

    public PlayerFurnaceData getFurnaceData() {
        return furnaceData;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;

        if (index < BUFFER_SLOTS) {
            // 成品仓 -> 玩家背包：把该格物品尽量分到背包格子（每格上限 64）
            ItemStack buf = slot.getItem().copy();
            int total = buf.getCount();
            if (total <= 0) return ItemStack.EMPTY;
            for (int i = BUFFER_SLOTS; i < this.slots.size() && !buf.isEmpty(); i++) {
                Slot ps = this.slots.get(i);
                if (!ps.mayPlace(buf)) continue;
                ItemStack cur = ps.getItem();
                int max = Math.min(64, ps.getMaxStackSize());
                if (cur.isEmpty()) {
                    int put = Math.min(max, buf.getCount());
                    ps.set(buf.split(put));
                } else if (ItemStack.isSameItemSameTags(cur, buf)) {
                    int space = max - cur.getCount();
                    if (space > 0) {
                        int put = Math.min(space, buf.getCount());
                        cur.grow(put);
                        buf.shrink(put);
                        ps.set(cur);
                    }
                }
            }
            int taken = total - buf.getCount();
            if (taken > 0) slot.remove(taken);
        } else {
            // 玩家背包 -> 成品仓（接受所有物品，单格堆叠无上限）
            ItemStack src = slot.getItem().copy();
            if (src.isEmpty()) return ItemStack.EMPTY;
            ItemStack left = buffer.deposit(src);
            int taken = src.getCount() - left.getCount();
            if (taken > 0) slot.remove(taken);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    /** 将成品储备箱（成品仓）中的成品存入 RS 网络。 */
    public void depositToNetwork() {
        if (!(player instanceof ServerPlayer sp)) return;
        List<NetworkHandle> nets = NetworkIO.getNetworks(player);
        if (nets.isEmpty()) {
            sp.sendSystemMessage(Component.literal(NetworkIO.diagnose(player)));
            return;
        }
        sp.sendSystemMessage(furnaceData.depositOutputToNetwork(nets));
        this.broadcastChanges();
    }

    /** 绑定到成品储备箱 NonNullList 的容器视图，槽位直接读写持久化数据；单格堆叠无上限。 */
    private static class ProductBufferContainer implements Container {
        private final NonNullList<ItemStack> list;
        private final long[] amounts;

        ProductBufferContainer(NonNullList<ItemStack> list, long[] amounts) {
            this.list = list;
            this.amounts = amounts;
        }

        @Override
        public int getContainerSize() {
            return list.size();
        }

        @Override
        public boolean isEmpty() {
            for (int i = 0; i < list.size(); i++) {
                if (!list.get(i).isEmpty() && amounts[i] > 0) return false;
            }
            return true;
        }

        @Override
        public ItemStack getItem(int i) {
            ItemStack t = list.get(i);
            if (t.isEmpty()) return ItemStack.EMPTY;
            ItemStack s = t.copy();
            PlayerFurnaceData.rawSetCount(s, (int) Math.min(amounts[i], PlayerFurnaceData.UNBOUNDED));
            return s;
        }

        @Override
        public ItemStack removeItem(int i, int count) {
            ItemStack t = list.get(i);
            if (t.isEmpty()) return ItemStack.EMPTY;
            int take = (int) Math.min(count, amounts[i]);
            amounts[i] -= take;
            ItemStack out = t.copy();
            PlayerFurnaceData.rawSetCount(out, take);
            if (amounts[i] <= 0) {
                list.set(i, ItemStack.EMPTY);
                amounts[i] = 0;
            }
            return out;
        }

        @Override
        public ItemStack removeItemNoUpdate(int i) {
            ItemStack t = list.get(i);
            if (t.isEmpty()) return ItemStack.EMPTY;
            ItemStack out = t.copy();
            PlayerFurnaceData.rawSetCount(out, (int) Math.min(amounts[i], PlayerFurnaceData.UNBOUNDED));
            list.set(i, ItemStack.EMPTY);
            amounts[i] = 0;
            return out;
        }

        @Override
        public void setItem(int i, ItemStack stack) {
            if (stack.isEmpty()) {
                list.set(i, ItemStack.EMPTY);
                amounts[i] = 0;
            } else {
                list.set(i, stack.copyWithCount(1));
                amounts[i] = stack.getCount();
            }
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
            for (int i = 0; i < list.size(); i++) {
                list.set(i, ItemStack.EMPTY);
                amounts[i] = 0;
            }
        }

        /** 将物品存入成品仓：先合并同类已有堆叠（无上限），再填入空槽；返回剩余。 */
        public ItemStack deposit(ItemStack stack) {
            if (stack.isEmpty()) return stack;
            ItemStack left = stack.copy();
            long count = left.getCount();
            for (int i = 0; i < list.size() && count > 0; i++) {
                ItemStack s = list.get(i);
                if (!s.isEmpty() && ItemStack.isSameItemSameTags(s, left)) {
                    long space = (long) PlayerFurnaceData.UNBOUNDED - amounts[i];
                    if (space > 0) {
                        long move = Math.min(space, count);
                        amounts[i] += move;
                        count -= move;
                    }
                }
            }
            for (int i = 0; i < list.size() && count > 0; i++) {
                if (!list.get(i).isEmpty()) continue;
                long move = Math.min((long) PlayerFurnaceData.UNBOUNDED, count);
                list.set(i, left.copyWithCount(1));
                amounts[i] = move;
                count -= move;
            }
            if (count <= 0) return ItemStack.EMPTY;
            ItemStack res = left.copy();
            PlayerFurnaceData.rawSetCount(res, (int) count);
            return res;
        }
    }

    /** 成品仓槽位：单格堆叠无上限。 */
    private static class ProductUnboundedSlot extends Slot {
        ProductUnboundedSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public int getMaxStackSize() {
            return PlayerFurnaceData.UNBOUNDED;
        }
    }
}
