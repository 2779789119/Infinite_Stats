package com.infinitestats.emc;

import com.infinitestats.Config;
import com.infinitestats.network.NetworkHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * EMC 转化石板容器 — 服务端菜单（奥术转化石板布局）。
 *
 * 槽位索引（顺序即索引，界面绘制由 {@code EmcScreen} 自行布局）：
 * <pre>
 *   0            学习槽（放入物品→自动消耗→学习+获得EMC）
 *   1  ~ 9       玩家快捷栏
 *   10 ~ 36      玩家主物品栏 (3×9)
 *   37 ~ 45      EMC 合成网格 3×3（配方示意槽，合成时不消耗，材料以 EMC 支付）
 *   46           合成结果槽（只能取出；取出时按材料 EMC 总价扣款）
 * </pre>
 *
 * 合成网格的语义对齐 Project Expansion 的「奥术转化石板（Arcane Transmutation Tablet）」：
 * 摆进网格的材料不会被消耗，**改为按配方材料的 EMC 总额扣款**，取出结果后才结算。
 */
public class EmcMenu extends AbstractContainerMenu {

    private final Player player;
    private final Container learnContainer = new SimpleContainer(1);
    private final GridContainer craftGrid = new GridContainer(this, 9);
    private final SimpleContainer craftResult = new SimpleContainer(1);
    /** 用于向配方系统暴露 3×3 网格内容的视图容器。 */
    private final TransientCraftingContainer craftingView;

    // === 槽位索引 ===
    public static final int SLOT_LEARN = 0;
    public static final int SLOT_HOTBAR_START = 1;
    public static final int SLOT_HOTBAR_END = 9;
    public static final int SLOT_INV_START = 10;
    public static final int SLOT_INV_END = 36;
    public static final int SLOT_GRID_START = 37;
    public static final int SLOT_GRID_END = 45;
    public static final int SLOT_RESULT = 46;
    public static final int GRID_SIZE = 9;

    // === 界面尺寸（EmcScreen 使用）===
    public static final int GUI_WIDTH = 342;
    public static final int GUI_HEIGHT = 256;

    public EmcMenu(int windowId, Inventory playerInv) {
        super(ModMenuTypes.EMC_MENU.get(), windowId);
        this.player = playerInv.player;
        this.craftingView = new TransientCraftingContainer(this, 3, 3);

        // 学习槽 (index 0)
        this.addSlot(new LearnSlot(learnContainer, 0, 0, 0));

        // 玩家快捷栏 (indices 1-9)
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, 0, 0));
        }

        // 玩家主物品栏 (indices 10-36)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 0, 0));
            }
        }

        // EMC 合成网格 3×3 (indices 37-45)
        for (int i = 0; i < GRID_SIZE; i++) {
            this.addSlot(new Slot(craftGrid, i, 0, 0));
        }

        // 合成结果槽 (index 46)
        this.addSlot(new Slot(craftResult, 0, 0, 0) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        if (index == SLOT_RESULT) return ItemStack.EMPTY; // 结果槽只走点击结算

        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();

        if (index == SLOT_LEARN) {
            // 学习槽 → 玩家背包
            if (!this.moveItemStackTo(stack, SLOT_HOTBAR_START, SLOT_INV_END + 1, true)) {
                return ItemStack.EMPTY;
            }
        } else if (index >= SLOT_GRID_START && index <= SLOT_GRID_END) {
            // 合成网格 → 玩家背包
            if (!this.moveItemStackTo(stack, SLOT_HOTBAR_START, SLOT_INV_END + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            // 玩家背包 → 学习槽（可学则学），否则 → 合成网格
            if (!this.moveItemStackTo(stack, SLOT_LEARN, SLOT_LEARN + 1, false)
                    && !this.moveItemStackTo(stack, SLOT_GRID_START, SLOT_GRID_END + 1, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        slot.onTake(player, stack);
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return Config.EMC_ENABLED.get();
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide()) {
            clearContainer(player, learnContainer);
            // 网格里的物品是玩家自己摆进去的，退出界面时归还，避免凭空蒸发
            clearContainer(player, craftGrid);
            craftResult.setItem(0, ItemStack.EMPTY);
        }
    }

    // ==================== 合成结果 ====================

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (container == craftGrid && player instanceof ServerPlayer) {
            updateCraftResult();
        }
    }

    /** 用当前 3×3 网格内容匹配工作台配方并刷新结果槽（仅服务端计算，结果经槽位同步给客户端）。 */
    private void updateCraftResult() {
        Level level = player.level();
        for (int i = 0; i < GRID_SIZE; i++) {
            craftingView.setItem(i, craftGrid.getItem(i));
        }
        ItemStack result = level.getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, craftingView, level)
                .map(recipe -> recipe.assemble(craftingView, level.registryAccess()))
                .orElse(ItemStack.EMPTY);
        if (!ItemStack.matches(result, craftResult.getItem(0))) {
            craftResult.setItem(0, result);
        }
    }

    /**
     * 计算当前网格配方的 EMC 总价。
     *
     * @return 材料 EMC 总额；返回 -1 表示存在「没有 EMC 值」的材料（禁止以 EMC 合成）
     */
    private long computeCraftCost() {
        long total = 0;
        boolean any = false;
        for (int i = 0; i < GRID_SIZE; i++) {
            ItemStack s = craftGrid.getItem(i);
            if (s.isEmpty()) continue;
            long emc = EmcPricing.buyPrice(player, s);
            if (emc <= 0) return -1;
            total += emc * s.getCount();
            any = true;
        }
        return any ? total : -1;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId == SLOT_RESULT) {
            // 客户端不做本地预测，等服务端广播结果（避免两端余额判断不一致）
            if (player.level().isClientSide) return;
            if (clickType != ClickType.PICKUP || button != 0) return;
            if (!getCarried().isEmpty()) return;

            ItemStack result = craftResult.getItem(0);
            if (result.isEmpty()) return;

            long cost = computeCraftCost();
            if (cost < 0) {
                player.displayClientMessage(
                        Component.translatable("message.infinitestats.emc.craft_no_value"), true);
                return;
            }
            EmcPlayerData data = player.getCapability(EmcPlayerDataProvider.EMC_PLAYER_DATA).orElse(null);
            if (data == null) return;
            if (!data.consumeEmc(cost)) {
                player.displayClientMessage(
                        Component.translatable("message.infinitestats.emc.craft_insufficient",
                                formatEmc(cost), formatEmc(data.getEmcBalance())), true);
                return;
            }

            setCarried(result.copy());
            broadcastChanges();
            if (player instanceof ServerPlayer sp) {
                NetworkHandler.syncEmcToClient(sp);
            }
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    // ==================== 网格容器（变化时通知菜单重算配方） ====================

    private static final class GridContainer extends SimpleContainer {
        private final EmcMenu menu;

        GridContainer(EmcMenu menu, int size) {
            super(size);
            this.menu = menu;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            menu.slotsChanged(this);
        }
    }

    // ==================== 学习槽 ====================

    /**
     * 放入物品时自动消耗、学习并返还 EMC
     */
    private class LearnSlot extends Slot {

        public LearnSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (stack.isEmpty()) return false;
            long emc = EmcDatabase.getEmc(stack);
            return emc > 0;
        }

        @Override
        public void setByPlayer(ItemStack stack) {
            // 玩家手动放入（非 quickMoveStack）
            if (!stack.isEmpty() && player instanceof ServerPlayer sp) {
                consumeAndLearn(sp, stack);
                return;
            }
            super.setByPlayer(stack);
        }

        @Override
        public void set(ItemStack stack) {
            // quickMoveStack 或代码调用
            if (!stack.isEmpty() && player instanceof ServerPlayer sp) {
                consumeAndLearn(sp, stack);
                return;
            }
            super.set(stack);
        }

        private void consumeAndLearn(ServerPlayer sp, ItemStack stack) {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (!Config.EMC_ENABLED.get() || EmcDatabase.getEmc(stack) <= 0) {
                super.set(stack);
                return;
            }
            long emcValue = EmcPricing.sellValue(sp, stack, stack.getCount());
            CompoundTag nbt = stack.getTag();

            sp.getCapability(EmcPlayerDataProvider.EMC_PLAYER_DATA).ifPresent(data -> {
                if (!data.hasLearned(itemId)) {
                    data.learnAndConvert(itemId, emcValue, nbt);
                    NetworkHandler.syncEmcToClient(sp);
                    sp.displayClientMessage(
                            Component.translatable("message.infinitestats.emc.learned",
                                    stack.getHoverName(), formatEmc(emcValue)), false);
                } else {
                    // 已学过：仅返还 EMC（不重复标记），更新 NBT（可能放入了不同版本的手册）
                    if (nbt != null && !nbt.isEmpty()) {
                        data.learnItem(itemId, nbt);
                    }
                    data.addEmc(emcValue);
                    NetworkHandler.syncEmcToClient(sp);
                    sp.displayClientMessage(
                            Component.translatable("message.infinitestats.emc.converted",
                                    stack.getHoverName(), formatEmc(emcValue)), false);
                }
            });

            // 消耗物品（不真正放入槽位）
            this.container.setItem(0, ItemStack.EMPTY);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    // ==================== 工具方法 ====================

    private static String formatEmc(long emc) {
        if (emc >= 1_000_000_000) return String.format("%.1fB", emc / 1_000_000_000.0);
        if (emc >= 1_000_000) return String.format("%.1fM", emc / 1_000_000.0);
        if (emc >= 1_000) return String.format("%.1fK", emc / 1_000.0);
        return String.valueOf(emc);
    }
}
