package com.infinitestats.crafting;

import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import net.minecraftforge.event.ForgeEventFactory;

/**
 * 随身酿造台的持久化状态，挂在玩家 PlayerStats 上随存档保存 —— 与
 * {@link com.infinitestats.furnace.PlayerFurnaceData} 同一套思路：
 * <b>关掉界面也继续酿，物品留在台子里</b>，下次打开界面进度与内容都还在。
 *
 * <h3>无限燃料</h3>
 * 随身酿造台不用烈焰粉：起酿只看「原料槽能不能酿」与「本轮是否已经在酿」，
 * 不再检查也不消耗燃料；界面上的燃料条恒为满格（{@link ContainerData} 的第 1 项永远返回满值），
 * 免得看着像「缺燃料」。燃料槽（4 号）仍按原版布局保留，但放进去的东西不会被动用。
 *
 * <h3>为什么不用原版 {@code BrewingStandBlockEntity} 的 serverTick</h3>
 * 复用它的算法看起来省事，但它的内部状态（{@code brewTime} / {@code fuel} / {@code ingredient}）
 * 是包级私有字段，外部既读不到也写不回 —— 也就没法持久化：重新打开界面时「这一轮开始时放的是什么原料」
 * 会丢，原版立刻判定「原料被换掉了」并把进度清零。所以这里把酿造状态收进玩家数据，自己维护这一小段逻辑：
 * 判定能否酿造 / 真正酿造都仍然调用 Forge 的 {@link BrewingRecipeRegistry} 与
 * {@link ForgeEventFactory#onPotionAttemptBrew} / {@link ForgeEventFactory#onPotionBrewed}，
 * 因此<b>其它模组扩展的酿造配方一样生效</b>，只有那 400 tick 的倒计时是自己数的。
 *
 * <h3>界面与同步</h3>
 * 本类同时是容器（0-2 药水 / 3 原料 / 4 燃料，槽位顺序与原版 {@code BrewingStandMenu} 完全一致）
 * 与 {@link ContainerData}（0 = 剩余酿造 tick，1 = 燃料读数，恒满），原版菜单与界面直接套用；
 * 物品与进度由原版菜单的槽位同步自动发给客户端，无需额外网络包。
 *
 * <p>容器部分没有继承 {@code SimpleContainer}：它的物品列表是私有字段、1.20.1 的 {@code Container}
 * 接口也没有取列表的方法，而 {@code BrewingRecipeRegistry} 需要 {@code NonNullList}，
 * 所以这里自己实现容器接口并直接持有列表。
 */
public class PortableBrewingData implements Container, ContainerData {

    /** 与 {@code BrewingStandMenu} 的槽位布局保持一致（药水 ×3 / 原料 / 燃料）。 */
    public static final int SLOT_COUNT = 5;
    public static final int INGREDIENT_SLOT = 3;
    /** 原版的燃料槽 —— 无限燃料后不再需要放烈焰粉，槽位仅为对齐原版布局而保留。 */
    public static final int FUEL_SLOT = 4;

    /** 参与酿造的槽位（原版的 SLOTS_FOR_SIDES）。 */
    private static final int[] POTION_INDEXES = {0, 1, 2};

    /** 原版「一份烈焰粉 = 20 轮」的读数，这里只用于把界面燃料条画满。 */
    private static final int INFINITE_FUEL_READING = 20;

    /** 一轮酿造的时长（tick，与原版一致）。 */
    private static final int BREW_TIME = 400;

    /** 原版的「酿造完成」世界事件（音效 + 粒子）。 */
    private static final int BREW_COMPLETED_EVENT = 1035;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

    /** 剩余酿造 tick，> 0 表示正在酿。 */
    private int brewTime;
    /** 本轮开始时放入的原料物品，用于识别「原料中途被换掉」。 */
    private Item ingredient = Items.AIR;

    // ========== 每 tick 驱动（与界面是否打开无关） ==========

    /**
     * 推进一 tick 酿造。逻辑以原版 {@code BrewingStandBlockEntity.serverTick} 的酿造部分为准，
     * 只有两处不同：状态存在自己身上，而且<b>不需要燃料</b>。
     */
    public void tick(Level level, ServerPlayer player) {
        if (level == null || level.isClientSide()) return;

        boolean brewable = isBrewable();
        ItemStack ingredientStack = items.get(INGREDIENT_SLOT);

        if (brewTime > 0) {
            --brewTime;
            if (brewTime == 0) {
                if (brewable) {
                    doBrew(level, player);
                } else {
                    brewTime = 0;
                }
            } else if (!brewable || !ingredientStack.is(ingredient)) {
                // 药水被拿走 / 原料被换掉 → 本轮作废（与原版同款判定）
                brewTime = 0;
            }
        } else if (brewable) {
            // 无限燃料：直接开酿，不需要烈焰粉、也不扣任何燃料
            brewTime = BREW_TIME;
            ingredient = ingredientStack.getItem();
        }
    }

    /** 原料槽里的东西能不能酿出点什么（交给 Forge 的酿造注册表，含其它模组扩展的配方）。 */
    private boolean isBrewable() {
        ItemStack ingredientStack = items.get(INGREDIENT_SLOT);
        if (ingredientStack.isEmpty()) return false;
        return BrewingRecipeRegistry.canBrew(items, ingredientStack, POTION_INDEXES);
    }

    /** 真正酿一轮：药水槽变成成品、原料消耗一份，与原版 {@code doBrew} 流程一致。 */
    private void doBrew(Level level, ServerPlayer player) {
        // 让其它模组能拦下这次酿造（与原版一样在动手前发事件）
        if (ForgeEventFactory.onPotionAttemptBrew(items)) return;

        ItemStack ingredientStack = items.get(INGREDIENT_SLOT);
        BrewingRecipeRegistry.brewPotions(items, ingredientStack, POTION_INDEXES);
        ForgeEventFactory.onPotionBrewed(items);

        if (ingredientStack.hasCraftingRemainingItem()) {
            ItemStack remaining = ingredientStack.getCraftingRemainingItem();
            ingredientStack.shrink(1);
            if (ingredientStack.isEmpty()) {
                ingredientStack = remaining;
            } else if (player != null) {
                // 原版把容器残留物丢在酿造台旁，这里丢在玩家脚下
                player.drop(remaining, false);
            }
        } else {
            ingredientStack.shrink(1);
        }
        items.set(INGREDIENT_SLOT, ingredientStack);
        if (player != null) {
            level.levelEvent(BREW_COMPLETED_EVENT, player.blockPosition(), 0);
        }
        setChanged();
    }

    // ========== Container ==========

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int index) {
        return items.get(index);
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        ItemStack removed = ContainerHelper.removeItem(items, index, count);
        if (!removed.isEmpty()) setChanged();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        return ContainerHelper.takeItem(items, index);
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        items.set(index, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    @Override
    public void setChanged() {
        // 数据挂在 PlayerStats 上，由 capability 的常规保存落盘，这里无需额外动作
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    // ========== ContainerData（同步给原版界面） ==========

    @Override
    public int getCount() {
        return 2;
    }

    @Override
    public int get(int index) {
        // 0 = 剩余酿造 tick；1 = 燃料条读数（无限燃料 → 恒满）
        return index == 0 ? brewTime : INFINITE_FUEL_READING;
    }

    @Override
    public void set(int index, int value) {
        // 只有酿造进度会被同步回写；燃料是恒定的，忽略
        if (index == 0) {
            brewTime = value;
        }
    }

    // ========== 持久化 ==========

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.put("Items", ContainerHelper.saveAllItems(new CompoundTag(), items));
        tag.putInt("BrewTime", brewTime);
        tag.putString("Ingredient", BuiltInRegistries.ITEM.getKey(ingredient).toString());
        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        if (tag.contains("Items")) {
            ContainerHelper.loadAllItems(tag.getCompound("Items"), items);
        }
        brewTime = tag.getInt("BrewTime");
        ingredient = Items.AIR;
        if (tag.contains("Ingredient")) {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("Ingredient"));
            if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                ingredient = BuiltInRegistries.ITEM.get(id);
            }
        }
    }

    public void copyFrom(PortableBrewingData other) {
        for (int i = 0; i < items.size() && i < other.items.size(); i++) {
            items.set(i, other.items.get(i).copy());
        }
        brewTime = other.brewTime;
        ingredient = other.ingredient;
    }
}
