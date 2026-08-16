package com.infinitestats.furnace;

import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeHooks;

import com.infinitestats.compat.NetworkHandle;
import com.infinitestats.compat.NetworkIO;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 随身熔炉的持久化状态，挂在玩家 PlayerStats 上随存档保存。
 * 即使关闭界面、甚至完全不打开界面，也会持续冶炼。
 *
 * 容量模型：每个槽位的“真实数量”存放在 {@code amounts[]}（long）中，
 * ItemStack 仅作为物品模板（count 恒为 1）。这样可突破原版
 * ItemStack 对 count 的 64 / 单字节 NBT 上限，实现槽位堆叠无上限。
 *
 * 单输入/单输出布局：与原版熔炉一致，1 个输入槽 + 1 个输出槽 + 1 个燃料槽。
 */
public class PlayerFurnaceData {

    /** 并行冶炼的输入/输出槽数量（与原版熔炉一致：1 个）。 */
    public static final int INPUT_COUNT = 1;

    public static final int FUEL_SLOT = 0;
    public static int inputSlot(int i) {
        return 1 + i;
    }
    public static int outputSlot(int i) {
        return 1 + INPUT_COUNT + i;
    }
    public static final int TOTAL_SLOTS = 1 + 2 * INPUT_COUNT;

    /** 单槽数量的理论上限（受 int 类型的 ItemStack 展示限制，足够“无上限”体验）。 */
    public static final int UNBOUNDED = Integer.MAX_VALUE;

    /** 矿石储备箱格子数：3 行 × 9 列，与外部箱子 UI 一致。 */
    public static final int INPUT_BUFFER_SLOTS = 27;

    /** 成品储备箱格子数：3 行 × 9 列，与外部箱子 UI 一致。 */
    public static final int OUTPUT_BUFFER_SLOTS = 27;

    // data 数组布局：
    // [0] = 剩余燃烧时间 [1] = 总燃烧时间
    // 每个输入槽 i：[2 + 2*i] = 冶炼进度 [3 + 2*i] = 冶炼总时长
    // 末位 = 加速等级（0 = 普通速度，每级倍率 +1）
    private final int[] data = new int[3 + 2 * INPUT_COUNT];
    private final NonNullList<ItemStack> items = NonNullList.withSize(TOTAL_SLOTS, ItemStack.EMPTY);
    /** 每个槽的真实数量（权威存储）。 */
    private final long[] amounts = new long[TOTAL_SLOTS];

    /**
     * 矿石储备箱模板：仅作为物品类型模板（count 恒为 1），真实数量在 inputAmounts 中。
     * 这样可突破 ItemStack 对 count 的 64 / NBT short 上限，实现单格堆叠无上限。
     */
    private final NonNullList<ItemStack> inputBuffer = NonNullList.withSize(INPUT_BUFFER_SLOTS, ItemStack.EMPTY);
    /** 矿石储备箱每格的真实数量（权威存储，支持无上限堆叠）。 */
    private final long[] inputAmounts = new long[INPUT_BUFFER_SLOTS];

    /** 获取矿石储备箱的底层模板列表（外部箱子 UI 直接读写）。 */
    public NonNullList<ItemStack> getInputBuffer() {
        return inputBuffer;
    }
    /** 获取矿石储备箱每格数量数组（无上限堆叠）。 */
    public long[] getInputAmounts() {
        return inputAmounts;
    }

    /**
     * 矿石优先顺序：按物品注册名（如 "minecraft:iron_ore"）有序排列。
     * 列表靠前的矿石在输入槽为空时会被优先从矿石储备箱取出放入，
     * 列表之外的矿石按缓冲槽顺序排在其后。仅作为排序依据，不影响熔炼逻辑。
     */
    private final List<String> orePriority = new ArrayList<>();

    /** 取某物品栈的物品注册名（用于优先顺序的匹配与持久化）。 */
    public static String idOf(ItemStack s) {
        return BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
    }

    /** 返回当前矿石优先顺序（副本）。 */
    public List<String> getOrePriorityIds() {
        return new ArrayList<>(orePriority);
    }

    /** 设置矿石优先顺序（仅保留真实存在的物品注册名）。 */
    public void setOrePriorityIds(List<String> ids) {
        orePriority.clear();
        if (ids == null) return;
        for (String id : ids) {
            if (id == null) continue;
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) {
                orePriority.add(id);
            }
        }
    }

    /** 清空矿石优先顺序（恢复为按缓冲槽顺序放入）。 */
    public void clearOrePriority() {
        orePriority.clear();
    }

    /**
     * 成品储备箱模板：仅作为物品类型模板（count 恒为 1），真实数量在 outputAmounts 中。
     * 单格堆叠无上限。
     */
    private final NonNullList<ItemStack> outputBuffer = NonNullList.withSize(OUTPUT_BUFFER_SLOTS, ItemStack.EMPTY);
    /** 成品储备箱每格的真实数量（权威存储，支持无上限堆叠）。 */
    private final long[] outputAmounts = new long[OUTPUT_BUFFER_SLOTS];

    /** 获取成品储备箱的底层模板列表（外部箱子 UI 直接读写）。 */
    public NonNullList<ItemStack> getOutputBuffer() {
        return outputBuffer;
    }
    /** 获取成品储备箱每格数量数组（无上限堆叠）。 */
    public long[] getOutputAmounts() {
        return outputAmounts;
    }

    /**
     * 将成品储备箱（成品仓）中的所有成品存入 RS 网络。
     * 成品仓的“真实数量”存放在 {@link #outputAmounts}（long）中，
     * ItemStack 仅为模板（count=1），因此这里按数量构造待存入栈。
     *
     * @return 给玩家的反馈消息
     */
    public Component depositOutputToNetwork(List<NetworkHandle> nets) {
        NonNullList<ItemStack> buffer = getOutputBuffer();
        long[] amounts = getOutputAmounts();
        int total = 0;
        for (int i = 0; i < buffer.size(); i++) {
            ItemStack s = buffer.get(i);
            if (s.isEmpty() || amounts[i] <= 0) continue;
            ItemStack toInsert = s.copy();
            rawSetCount(toInsert, (int) Math.min(amounts[i], UNBOUNDED));
            ItemStack remaining = NetworkIO.insert(nets, toInsert);
            long stored = toInsert.getCount() - remaining.getCount();
            if (stored > 0) {
                long newAmt = amounts[i] - stored;
                if (newAmt <= 0) {
                    buffer.set(i, ItemStack.EMPTY);
                    amounts[i] = 0;
                } else {
                    amounts[i] = newAmt;
                }
                total += stored;
            }
        }
        if (total == 0) return Component.literal("§e成品储备箱中没有可存入网络的成品");
        return Component.literal("§a已将成品储备箱 §f" + total + "§a 个成品存入网络");
    }

    // ========== 绕过 ItemStack count 钳制的反射工具 ==========
    private static final Field STACK_COUNT_FIELD;

    static {
        Field f = null;
        try {
            f = ItemStack.class.getDeclaredField("count");
            f.setAccessible(true);
        } catch (Exception ignored) {
            try {
                f = ItemStack.class.getDeclaredField("field_190927_a");
                f.setAccessible(true);
            } catch (Exception ignored2) {
                f = null;
            }
        }
        STACK_COUNT_FIELD = f;
    }

    /** 读取 ItemStack 的真实 count（不被 maxStackSize 钳制）。 */
    public static int rawGetCount(ItemStack stack) {
        if (STACK_COUNT_FIELD == null) return stack.getCount();
        try {
            return (int) STACK_COUNT_FIELD.get(stack);
        } catch (Exception e) {
            return stack.getCount();
        }
    }

    /** 直接设置 ItemStack 的 count（绕过 maxStackSize 钳制）。 */
    public static void rawSetCount(ItemStack stack, int count) {
        if (STACK_COUNT_FIELD == null) {
            stack.setCount(count);
            return;
        }
        try {
            STACK_COUNT_FIELD.set(stack, count);
        } catch (Exception e) {
            stack.setCount(count);
        }
    }

    // ========== 槽位访问（模板 + 数量） ==========

    public NonNullList<ItemStack> getItems() {
        return items;
    }

    public long[] getAmounts() {
        return amounts;
    }

    public long getAmount(int i) {
        return amounts[i];
    }

    /** 返回用于显示 / 配方匹配 / 网络同步的 ItemStack（count 为真实数量）。 */
    public ItemStack getStack(int i) {
        ItemStack t = items.get(i);
        if (t.isEmpty()) return ItemStack.EMPTY;
        ItemStack s = t.copy();
        rawSetCount(s, (int) Math.min(amounts[i], UNBOUNDED));
        return s;
    }

    /** 设置某槽（写入模板 + 数量）。空栈会清空该槽。 */
    public void setSlot(int i, ItemStack stack) {
        if (stack.isEmpty()) {
            clearSlot(i);
            return;
        }
        items.set(i, stack.copyWithCount(1));
        amounts[i] = stack.getCount();
    }

    /** 仅更新数量（模板保持不变，调用前需确保模板已存在）。 */
    public void setAmountOnly(int i, long amount) {
        if (amount <= 0) {
            clearSlot(i);
        } else {
            amounts[i] = amount;
        }
    }

    /** 取出最多 count 个，返回对应的 ItemStack（count 为真实取出量）。 */
    public ItemStack removeSlot(int i, int count) {
        ItemStack tpl = items.get(i);
        if (tpl.isEmpty()) return ItemStack.EMPTY;
        int take = (int) Math.min(count, amounts[i]);
        amounts[i] -= take;
        ItemStack out = tpl.copy();
        rawSetCount(out, take);
        if (amounts[i] <= 0) {
            items.set(i, ItemStack.EMPTY);
            amounts[i] = 0;
        }
        return out;
    }

    public void clearSlot(int i) {
        items.set(i, ItemStack.EMPTY);
        amounts[i] = 0;
    }

    public int[] getData() {
        return data;
    }

    // ========== 进度访问 ==========

    private int cookIdx(int i) {
        return 2 + 2 * i;
    }
    private int cookTotalIdx(int i) {
        return 3 + 2 * i;
    }
    private int speedIdx() {
        return 2 + 2 * INPUT_COUNT;
    }

    public int getCook(int i) {
        return data[cookIdx(i)];
    }
    public void setCook(int i, int v) {
        data[cookIdx(i)] = v;
    }
    public void addCook(int i, int v) {
        data[cookIdx(i)] += v;
    }
    public int getCookTotal(int i) {
        return data[cookTotalIdx(i)];
    }
    public void setCookTotal(int i, int v) {
        data[cookTotalIdx(i)] = v;
    }

    /** 供界面读取的进度 */
    public boolean isLit() {
        return data[0] > 0;
    }

    public float getLitProgress() {
        int lit = data[0];
        int dur = data[1];
        if (dur <= 0) return 0;
        return lit / (float) dur;
    }

    public float getCookProgress(int i) {
        int prog = getCook(i);
        int total = getCookTotal(i);
        if (total <= 0) return 0;
        return prog / (float) total;
    }

    /** 当前加速等级（0 = 普通速度）。 */
    public int getSpeedLevel() {
        return Math.max(0, data[speedIdx()]);
    }

    /** 当前熔炼速度倍率（加速等级 + 1）。 */
    public int getSpeedMultiplier() {
        return 1 + getSpeedLevel();
    }

    /** 设置加速等级（自动钳制为非负）。 */
    public void setSpeedLevel(int level) {
        data[speedIdx()] = Math.max(0, level);
    }

    /** 每 tick 驱动一次冶炼。level 为 null 或客户端直接跳过。 */
    public void tick(Level level) {
        if (level == null || level.isClientSide()) return;

        // 输入槽为空时，先尝试从矿石储备箱取出矿石放入输入槽。
        // 必须放在 anyWork 判定之前：否则输入槽为空会被直接判为"无活可干"而 return，
        // 导致自动取矿永远触发不到，熔炉无法自动开始工作。
        for (int i = 0; i < INPUT_COUNT; i++) {
            if (amounts[inputSlot(i)] <= 0) {
                pullInputFromBuffer();
            }
        }

        // 输出槽有成品时，自动转入成品储备箱（避免输出槽被单一物品占满，并便于玩家统一收集）
        pushOutputToBuffer();

        // 是否有任意输入可冶炼（有匹配配方且输出槽可接收）
        boolean anyWork = false;
        for (int i = 0; i < INPUT_COUNT; i++) {
            if (canSmeltInput(level, i)) {
                anyWork = true;
                break;
            }
        }

        if (!anyWork) {
            // 无活可干：熄火并清空所有冶炼进度
            if (data[0] > 0) data[0] = 0;
            for (int i = 0; i < INPUT_COUNT; i++) setCook(i, 0);
            return;
        }

        // 燃料计时递减
        if (data[0] > 0) data[0]--;

        // 燃料烧尽时尝试重新点火（需要仍有可冶炼的输入；燃料需玩家手动放入燃料槽）
        if (data[0] <= 0) {
            if (amounts[FUEL_SLOT] > 0) {
                ItemStack fuel = items.get(FUEL_SLOT);
                int burnTime = ForgeHooks.getBurnTime(fuel, RecipeType.SMELTING);
                if (burnTime > 0) {
                    data[0] = burnTime;
                    data[1] = burnTime;
                    consumeFuel();
                }
            }
        }

        if (data[0] > 0) {
            int mult = getSpeedMultiplier();
            for (int i = 0; i < INPUT_COUNT; i++) {
                if (!canSmeltInput(level, i)) {
                    setCook(i, 0);
                    continue;
                }
                AbstractCookingRecipe recipe = findRecipe(level, items.get(inputSlot(i)));
                if (recipe == null) {
                    setCook(i, 0);
                    continue;
                }
                ItemStack result = recipe.getResultItem(level.registryAccess());
                int total = recipe.getCookingTime();
                setCookTotal(i, total);
                addCook(i, mult);
                if (getCook(i) >= total) {
                    ItemStack output = items.get(outputSlot(i));
                    if (output.isEmpty()) {
                        items.set(outputSlot(i), result.copy());
                        amounts[outputSlot(i)] = result.getCount();
                    } else if (ItemStack.isSameItemSameTags(output, result)) {
                        long combined = amounts[outputSlot(i)] + result.getCount();
                        if (combined <= UNBOUNDED) {
                            amounts[outputSlot(i)] = combined;
                        }
                    }
                    // 消耗一份输入
                    if (amounts[inputSlot(i)] > 1) {
                        amounts[inputSlot(i)]--;
                    } else {
                        items.set(inputSlot(i), ItemStack.EMPTY);
                        amounts[inputSlot(i)] = 0;
                    }
                    setCook(i, 0);
                }
            }
        } else {
            for (int i = 0; i < INPUT_COUNT; i++) setCook(i, 0);
        }
    }

    /** 某输入槽当前能否冶炼（有输入、有配方、输出可接收）。 */
    private boolean canSmeltInput(Level level, int i) {
        if (amounts[inputSlot(i)] <= 0) return false;
        ItemStack in = items.get(inputSlot(i));
        if (in.isEmpty()) {
            amounts[inputSlot(i)] = 0;
            return false;
        }
        AbstractCookingRecipe recipe = findRecipe(level, in);
        if (recipe == null) return false;
        ItemStack result = recipe.getResultItem(level.registryAccess());
        return canBurn(in, result, items.get(outputSlot(i)), amounts[outputSlot(i)]);
    }

    /** 优先匹配高炉（Blast Furnace）配方，其次回退到普通熔炉配方。 */
    private AbstractCookingRecipe findRecipe(Level level, ItemStack input) {
        var blast = level.getRecipeManager()
                .getRecipeFor(RecipeType.BLASTING, singleItemView(input), level);
        if (blast.isPresent()) return blast.get();
        var smelt = level.getRecipeManager()
                .getRecipeFor(RecipeType.SMELTING, singleItemView(input), level);
        return smelt.orElse(null);
    }

    /** 消耗一份燃料（处理容器残留物，如空桶）。 */
    private void consumeFuel() {
        ItemStack fuel = items.get(FUEL_SLOT);
        if (amounts[FUEL_SLOT] > 1) {
            amounts[FUEL_SLOT]--;
        } else {
            ItemStack container = fuel.getCraftingRemainingItem();
            if (!container.isEmpty()) {
                items.set(FUEL_SLOT, container.copy());
                amounts[FUEL_SLOT] = 1;
            } else {
                items.set(FUEL_SLOT, ItemStack.EMPTY);
                amounts[FUEL_SLOT] = 0;
            }
        }
    }

    /**
     * 从矿石储备箱（箱子）中自动取出第一种矿石放入输入槽。
     * <p>规则：按 {@code orePriority} 优先顺序挑选，列表内的矿石靠前优先放入；
     * 列表之外的矿石按缓冲槽顺序排在其后。保证“一种熔完了再放下一个”的队列语义。
     *
     * @return 是否成功取出
     */
    public boolean pullInputFromBuffer() {
        // 先按优先顺序列表构造候选顺序，再补充未被列入的缓冲槽
        List<Integer> order = new ArrayList<>();
        Set<String> covered = new HashSet<>();
        for (String id : orePriority) {
            for (int i = 0; i < INPUT_BUFFER_SLOTS; i++) {
                ItemStack s = inputBuffer.get(i);
                if (s.isEmpty() || inputAmounts[i] <= 0) continue;
                if (idOf(s).equals(id)) {
                    order.add(i);
                    covered.add(id);
                    break;
                }
            }
        }
        for (int i = 0; i < INPUT_BUFFER_SLOTS; i++) {
            ItemStack s = inputBuffer.get(i);
            if (s.isEmpty() || inputAmounts[i] <= 0) continue;
            if (!covered.contains(idOf(s))) order.add(i);
        }
        int slot = inputSlot(0);
        for (int idx : order) {
            ItemStack s = inputBuffer.get(idx);
            if (s.isEmpty() || inputAmounts[idx] <= 0) continue;
            items.set(slot, s.copyWithCount(1));
            amounts[slot] = inputAmounts[idx];
            inputBuffer.set(idx, ItemStack.EMPTY);
            inputAmounts[idx] = 0;
            return true;
        }
        return false;
    }

    /**
     * 将输出槽中的成品自动转入成品储备箱。
     * <p>先填满储备箱中已存在的同类堆叠（单格无上限），再填入空槽位；储备箱放不下时，
     * 输出槽保留剩余数量继续冶炼。
     */
    public void pushOutputToBuffer() {
        int slot = outputSlot(0);
        long amt = amounts[slot];
        if (amt <= 0) return;
        ItemStack out = items.get(slot);
        if (out.isEmpty()) {
            amounts[slot] = 0;
            return;
        }
        long remaining = amt;
        // 1) 先填满已存在的同类堆叠（单格无上限）
        for (int i = 0; i < OUTPUT_BUFFER_SLOTS && remaining > 0; i++) {
            ItemStack s = outputBuffer.get(i);
            if (s.isEmpty() || !ItemStack.isSameItemSameTags(s, out)) continue;
            long space = (long) UNBOUNDED - outputAmounts[i];
            if (space > 0) {
                long move = Math.min(space, remaining);
                outputAmounts[i] += move;
                remaining -= move;
            }
        }
        // 2) 再填入空槽位
        for (int i = 0; i < OUTPUT_BUFFER_SLOTS && remaining > 0; i++) {
            if (!outputBuffer.get(i).isEmpty()) continue;
            long move = Math.min((long) UNBOUNDED, remaining);
            outputBuffer.set(i, out.copyWithCount(1));
            outputAmounts[i] = move;
            remaining -= move;
        }
        if (remaining <= 0) {
            items.set(slot, ItemStack.EMPTY);
            amounts[slot] = 0;
        } else {
            amounts[slot] = remaining;
        }
    }

    private boolean canBurn(ItemStack input, ItemStack result, ItemStack output, long outAmount) {
        if (input.isEmpty()) return false;
        if (result.isEmpty()) return false;
        if (output.isEmpty()) return true;
        if (!ItemStack.isSameItemSameTags(output, result)) return false;
        return outAmount + result.getCount() <= UNBOUNDED;
    }

    /** 判断某物品是否能通过高炉或普通熔炉熔炼（用于优先顺序界面的“可加入矿石”筛选）。 */
    public static boolean hasSmeltRecipe(Level level, ItemStack in) {
        if (in.isEmpty()) return false;
        Container c = new SimpleContainer(in);
        return level.getRecipeManager().getRecipeFor(RecipeType.BLASTING, c, level).isPresent()
                || level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, c, level).isPresent();
    }

    /** 仅供配方查询的只读单物品容器视图。 */
    private static Container singleItemView(ItemStack stack) {
        return new Container() {
            @Override public int getContainerSize() { return 1; }
            @Override public boolean isEmpty() { return stack.isEmpty(); }
            @Override public ItemStack getItem(int index) { return index == 0 ? stack : ItemStack.EMPTY; }
            @Override public ItemStack removeItem(int index, int count) { return index == 0 ? stack.copy() : ItemStack.EMPTY; }
            @Override public ItemStack removeItemNoUpdate(int index) { return getItem(index); }
            @Override public void setItem(int index, ItemStack stack) { }
            @Override public void setChanged() { }
            @Override public boolean stillValid(Player player) { return true; }
            @Override public void clearContent() { }
        };
    }

    public void copyFrom(PlayerFurnaceData other) {
        for (int i = 0; i < items.size(); i++) {
            items.set(i, other.items.get(i).copy());
        }
        System.arraycopy(other.data, 0, data, 0, data.length);
        System.arraycopy(other.amounts, 0, amounts, 0, amounts.length);
        for (int i = 0; i < inputBuffer.size(); i++) {
            inputBuffer.set(i, other.inputBuffer.get(i).copy());
        }
        System.arraycopy(other.inputAmounts, 0, inputAmounts, 0, inputAmounts.length);
        for (int i = 0; i < outputBuffer.size(); i++) {
            outputBuffer.set(i, other.outputBuffer.get(i).copy());
        }
        System.arraycopy(other.outputAmounts, 0, outputAmounts, 0, outputAmounts.length);
        orePriority.clear();
        orePriority.addAll(other.orePriority);
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        // 模板物品（count 恒为 1，真实数量在 amounts 中）
        tag.put("Items", ContainerHelper.saveAllItems(new CompoundTag(), items));
        tag.putLongArray("Amounts", amounts);
        tag.putIntArray("Data", data);
        // 矿石储备箱：模板（count=1）用标准容器序列化，真实数量单独以 long 数组保存（突破 NBT short 上限）
        tag.put("InputBuffer", ContainerHelper.saveAllItems(new CompoundTag(), inputBuffer));
        tag.putLongArray("InputAmounts", inputAmounts);
        // 成品储备箱
        tag.put("OutputBuffer", ContainerHelper.saveAllItems(new CompoundTag(), outputBuffer));
        tag.putLongArray("OutputAmounts", outputAmounts);
        // 矿石优先顺序（仅存物品注册名列表）
        ListTag prio = new ListTag();
        for (String id : orePriority) prio.add(StringTag.valueOf(id));
        tag.put("OrePriority", prio);
        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        if (tag.contains("Items")) {
            ContainerHelper.loadAllItems(tag.getCompound("Items"), items);
        }
        if (tag.contains("Amounts", Tag.TAG_LONG_ARRAY)) {
            long[] arr = tag.getLongArray("Amounts");
            System.arraycopy(arr, 0, amounts, 0, Math.min(arr.length, amounts.length));
        }
        if (tag.contains("Data", Tag.TAG_INT_ARRAY)) {
            int[] arr = tag.getIntArray("Data");
            int oldLen = arr.length;
            if (oldLen == data.length) {
                System.arraycopy(arr, 0, data, 0, data.length);
            } else {
                // 旧存档：data 数组较长（多槽位版本），按位置映射关键字段
                // data[0]lit [1]dur [2+2i]cook_i [3+2i]total_i [...]speed
                if (oldLen > 0) data[0] = arr[0];
                if (oldLen > 1) data[1] = arr[1];
                if (oldLen > 2) data[2] = arr[2];
                if (oldLen > 3) data[3] = arr[3];
                int oldSpeedIdx = oldLen - 1; // 旧版本速度永远在最后
                if (oldSpeedIdx >= 0 && oldSpeedIdx < oldLen) {
                    data[2 + 2 * INPUT_COUNT] = arr[oldSpeedIdx];
                }
            }
        }
        if (tag.contains("InputBuffer")) {
            ContainerHelper.loadAllItems(tag.getCompound("InputBuffer"), inputBuffer);
            long[] inAmts = tag.contains("InputAmounts", Tag.TAG_LONG_ARRAY) ? tag.getLongArray("InputAmounts") : null;
            for (int i = 0; i < INPUT_BUFFER_SLOTS; i++) {
                ItemStack s = inputBuffer.get(i);
                if (!s.isEmpty()) {
                    long a = (inAmts != null && inAmts.length > i) ? inAmts[i] : s.getCount();
                    inputAmounts[i] = a;
                    inputBuffer.set(i, s.copyWithCount(1));
                } else {
                    inputAmounts[i] = 0;
                }
            }
        } else if (tag.contains("FuelBuffer")) {
            // 兼容旧版本存档（旧 key 名为 FuelBuffer，count 即数量）
            ContainerHelper.loadAllItems(tag.getCompound("FuelBuffer"), inputBuffer);
            for (int i = 0; i < INPUT_BUFFER_SLOTS; i++) {
                ItemStack s = inputBuffer.get(i);
                if (!s.isEmpty()) {
                    inputAmounts[i] = s.getCount();
                    inputBuffer.set(i, s.copyWithCount(1));
                } else {
                    inputAmounts[i] = 0;
                }
            }
        }
        if (tag.contains("OutputBuffer")) {
            ContainerHelper.loadAllItems(tag.getCompound("OutputBuffer"), outputBuffer);
            long[] outAmts = tag.contains("OutputAmounts", Tag.TAG_LONG_ARRAY) ? tag.getLongArray("OutputAmounts") : null;
            for (int i = 0; i < OUTPUT_BUFFER_SLOTS; i++) {
                ItemStack s = outputBuffer.get(i);
                if (!s.isEmpty()) {
                    long a = (outAmts != null && outAmts.length > i) ? outAmts[i] : s.getCount();
                    outputAmounts[i] = a;
                    outputBuffer.set(i, s.copyWithCount(1));
                } else {
                    outputAmounts[i] = 0;
                }
            }
        }
        if (tag.contains("OrePriority", Tag.TAG_LIST)) {
            ListTag prio = tag.getList("OrePriority", Tag.TAG_STRING);
            orePriority.clear();
            for (int i = 0; i < prio.size(); i++) orePriority.add(prio.getString(i));
        }
    }
}
