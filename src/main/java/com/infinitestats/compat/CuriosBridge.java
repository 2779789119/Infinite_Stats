package com.infinitestats.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Curios 饰品栏桥接（纯反射，无需编译期依赖；Curios 未加载时所有方法都是空操作）。
 * <p>
 * 存在的意义：「死亡不掉落」属性只能操作原版 {@code Inventory}，而饰品栏是 Curios 自己
 * 挂在实体上的 capability，既不在 {@code Inventory.items/armor/offhand} 里，也不受原版
 * {@code keepInventory} 游戏规则保护。所以死亡瞬间要把饰品也抓走清空，重生后再原样放回，
 * 否则 Curios 会照它自己的规则把饰品掉在地上。
 * <p>
 * 抓取/归还都用可直接持久化的 NBT 表示（{@code {slot: 槽位ID, index: 槽位下标, stack: 物品}}），
 * 这样调用方存入 {@code PlayerStats} 后能跟着玩家存档跨越断线 / 重启。
 * <p>
 * 安全原则：**宁可掉落，绝不凭空消失**。抓取阶段只要出错就整份作废（返回 {@code null}，
 * 调用方保持饰品栏原样让原版掉落）；归还阶段任何槽位放不下 / 找不到，就退回玩家背包或丢在脚下。
 */
public final class CuriosBridge {

    private static final Logger LOGGER = LoggerFactory.getLogger(CuriosBridge.class);

    private static final String API_CLASS = "top.theillusivec4.curios.api.CuriosApi";
    private static final String LIVING_ENTITY_CLASS = "net.minecraft.world.entity.LivingEntity";

    private static final String TAG_SLOT = "slot";
    private static final String TAG_INDEX = "index";
    private static final String TAG_STACK = "stack";

    private static Boolean loaded;
    private static boolean warnOnce;
    /** 已注册的饰品槽位类型 ID（按 Curios 的 order 排序）；空结果不缓存，等同步完成后再取。 */
    private static List<String> slotTypeIds;
    /**
     * Curios 内置的默认槽位类型（对应 jar 里 data/curios/curios/slots/*.json）。
     * 槽位表要等 datapack 同步到客户端才完整，读不到时按这份兜底展开，写多了 Curios 也会忽略。
     */
    private static final List<String> DEFAULT_SLOT_TYPES =
            List.of("curio", "necklace", "ring", "head", "back", "belt", "body", "charm", "hands", "bracelet");

    private CuriosBridge() {
    }

    /**
     * Curios 已注册的饰品槽位类型标识（如 {@code ring} / {@code necklace} / {@code belt}）。
     * <p>
     * 物品编辑器要用它把「饰品」属性展开成每个槽位一条 —— Curios 5.x 只认
     * {@code CurioAttributeModifiers} 里 {@code Slot} 与槽位类型标识**完全相等**的条目，
     * 原版 {@code AttributeModifiers} 的 {@code Slot} 对它无效。
     *
     * @return 槽位类型 ID 列表；Curios 未加载时为 {@link List#of()}（空表），
     *         已加载但槽位表还没同步时退回 Curios 内置的那几个默认类型
     */
    public static List<String> slotTypeIds() {
        if (!isLoaded()) return List.of();
        if (slotTypeIds != null && !slotTypeIds.isEmpty()) return slotTypeIds;

        List<String> ids = new ArrayList<>();
        try {
            Class<?> api = Class.forName(API_CLASS);
            Object helper = api.getMethod("getSlotHelper").invoke(null);
            if (helper != null) {
                Class<?> helperIface = Class.forName("top.theillusivec4.curios.api.type.util.ISlotHelper");
                Class<?> typeIface = Class.forName("top.theillusivec4.curios.api.type.ISlotType");
                Object raw = helperIface.getMethod("getSlotTypes").invoke(helper);
                Method getIdentifier = typeIface.getMethod("getIdentifier");
                Method getOrder = typeIface.getMethod("getOrder");
                List<Object> types = new ArrayList<>();
                if (raw instanceof java.util.Collection<?> c) types.addAll(c);
                // 按 Curios 自己的 order 排，界面上的顺序才和饰品栏一致
                types.sort(Comparator.comparingInt(t -> {
                    try {
                        return (int) getOrder.invoke(t);
                    } catch (Throwable ignored) {
                        return 0;
                    }
                }));
                for (Object type : types) {
                    Object id = getIdentifier.invoke(type);
                    if (id != null && !String.valueOf(id).isBlank()) ids.add(String.valueOf(id));
                }
            }
        } catch (Throwable t) {
            warn("读取 Curios 饰品槽位类型失败，编辑器改用 Curios 内置的默认槽位", t);
        }
        // 槽位表还没同步到客户端时不能就这么算空，否则「饰品」只会写进一个通用槽位
        if (ids.isEmpty()) ids.addAll(DEFAULT_SLOT_TYPES);
        if (!ids.isEmpty()) slotTypeIds = ids;
        return ids;
    }

    /**
     * 物品是否被 Curios 认作饰品（有 curio capability）。
     * <p>
     * 只有这类物品才会被 Curios 在饰品栏里处理：属性生效、tooltip 里出现「佩戴X时：」都以此为前提。
     * 往非饰品物品上写 {@code CurioAttributeModifiers} 不会有任何效果。
     */
    public static boolean isCurioItem(ItemStack stack) {
        if (!isLoaded() || stack.isEmpty()) return false;
        try {
            Class<?> api = Class.forName(API_CLASS);
            Object raw = api.getMethod("getCurio", ItemStack.class).invoke(null, stack);
            if (raw == null) return false;
            Object optional = raw.getClass().getName().contains("LazyOptional")
                    ? raw.getClass().getMethod("resolve").invoke(raw) : raw;
            return optional != null && (boolean) optional.getClass().getMethod("isPresent").invoke(optional);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Curios 是否已加载。 */
    public static boolean isLoaded() {
        if (loaded == null) {
            try {
                loaded = ModList.get().isLoaded("curios");
            } catch (Throwable t) {
                loaded = false;
            }
        }
        return loaded;
    }

    /**
     * 抓取饰品栏里的全部物品，并把它们从饰品栏里清空。
     *
     * @return 可直接持久化的 NBT 列表；Curios 未加载时返回空列表；
     *         反射失败时返回 {@code null} —— 此时调用方**必须放弃清空饰品栏**。
     */
    public static List<CompoundTag> takeAll(Player player) {
        if (!isLoaded()) return new ArrayList<>();
        try {
            Map<?, ?> curios = curiosMap(player);
            if (curios == null) return new ArrayList<>();

            // 第一遍只读不写：先把所有物品记下来，避免"清了一半才发现读不了"导致物品凭空消失
            List<CompoundTag> taken = new ArrayList<>();
            for (Object entryObj : curios.entrySet()) {
                Map.Entry<?, ?> entry = (Map.Entry<?, ?>) entryObj;
                String slotId = String.valueOf(entry.getKey());
                IItemHandler handler = stacksOf(entry.getValue());
                if (handler == null) continue;
                for (int i = 0; i < handler.getSlots(); i++) {
                    ItemStack stack = handler.getStackInSlot(i);
                    if (stack.isEmpty()) continue;
                    CompoundTag tag = new CompoundTag();
                    tag.putString(TAG_SLOT, slotId);
                    tag.putInt(TAG_INDEX, i);
                    tag.put(TAG_STACK, stack.copy().save(new CompoundTag()));
                    taken.add(tag);
                }
            }

            // 第二遍才真正清空；某个槽位清不掉就把对应记录删掉（那件饰品留给原版掉落，不重复归还）
            for (int i = taken.size() - 1; i >= 0; i--) {
                if (!clearSlot(curios, taken.get(i))) taken.remove(i);
            }
            return taken;
        } catch (Throwable t) {
            warn("抓取 Curios 饰品栏失败，本次死亡饰品按原版掉落", t);
            return null;
        }
    }

    /**
     * 把 {@link #takeAll} 抓到的饰品放回原来的槽位。
     * 槽位不存在、类型不再匹配或放不下时，退到玩家背包，再不行就丢在脚下。
     */
    public static void restore(Player player, List<CompoundTag> entries) {
        if (entries == null || entries.isEmpty()) return;

        Map<?, ?> curios = null;
        if (isLoaded()) {
            try {
                curios = curiosMap(player);
            } catch (Throwable t) {
                warn("读取 Curios 饰品栏失败，本次饰品改为放回背包", t);
            }
        }

        for (CompoundTag tag : entries) {
            ItemStack stack = ItemStack.of(tag.getCompound(TAG_STACK));
            if (stack.isEmpty()) continue;

            ItemStack remainder = stack;
            if (curios != null) {
                try {
                    IItemHandler handler = stacksOf(curios.get(tag.getString(TAG_SLOT)));
                    if (handler != null) {
                        int index = Math.max(0, Math.min(tag.getInt(TAG_INDEX), handler.getSlots() - 1));
                        remainder = handler.insertItem(index, stack, false);
                    }
                } catch (Throwable t) {
                    warn("归还 Curios 饰品失败，剩余饰品改为放回背包", t);
                    remainder = stack;
                }
            }
            if (!remainder.isEmpty() && !player.getInventory().add(remainder)) {
                player.drop(remainder, false);
            }
        }
    }

    /**
     * 遍历玩家所有饰品槽位里的物品（Curios 未加载 / 反射失败时为空操作）。
     * <p>
     * 供「无限能源」这类需要扫一遍饰品栏的功能复用 —— 与 {@link #takeAll} 不同，
     * 这里只读不写、不清空。
     */
    public static void forEachStack(Player player, java.util.function.Consumer<ItemStack> consumer) {
        if (!isLoaded()) return;
        try {
            Map<?, ?> curios = curiosMap(player);
            if (curios == null) return;
            for (Object value : curios.values()) {
                IItemHandler handler = stacksOf(value);
                if (handler == null) continue;
                for (int i = 0; i < handler.getSlots(); i++) {
                    ItemStack stack = handler.getStackInSlot(i);
                    if (!stack.isEmpty()) consumer.accept(stack);
                }
            }
        } catch (Throwable ignored) {
            // 版本对不上：这次跳过
        }
    }

    /** 取玩家的「槽位 ID → ICuriosStackHandler」映射；取不到返回 null。 */
    private static Map<?, ?> curiosMap(Player player) throws Exception {
        Class<?> api = Class.forName(API_CLASS);
        // Curios 5.x 的签名是 getCuriosInventory(LivingEntity)，旧版是 Player，两种都要兼容
        Method getInventory;
        try {
            getInventory = api.getMethod("getCuriosInventory", Player.class);
        } catch (NoSuchMethodException e) {
            getInventory = api.getMethod("getCuriosInventory", Class.forName(LIVING_ENTITY_CLASS));
        }
        Object raw = getInventory.invoke(null, player);
        if (raw == null) return null;

        // 兼容 LazyOptional（Forge）与 Optional 两种返回类型
        Object optional = raw.getClass().getName().contains("LazyOptional")
                ? raw.getClass().getMethod("resolve").invoke(raw) : raw;
        if (optional == null || !(boolean) optional.getClass().getMethod("isPresent").invoke(optional)) {
            return null;
        }
        Object handler = optional.getClass().getMethod("get").invoke(optional);
        if (handler == null) return null;
        Object map = handler.getClass().getMethod("getCurios").invoke(handler);
        return map instanceof Map ? (Map<?, ?>) map : null;
    }

    /** 取某个槽位的物品容器；Curios 5.x 的 {@code getStacks()} 返回实现 IItemHandler 的 IDynamicStackHandler。 */
    private static IItemHandler stacksOf(Object slotHandler) {
        if (slotHandler == null) return null;
        try {
            Object stacks = slotHandler.getClass().getMethod("getStacks").invoke(slotHandler);
            return stacks instanceof IItemHandler handler ? handler : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 清空一条记录对应的槽位；槽位不存在或本来就空了则返回 false（该记录作废）。 */
    private static boolean clearSlot(Map<?, ?> curios, CompoundTag tag) {
        try {
            IItemHandler handler = stacksOf(curios.get(tag.getString(TAG_SLOT)));
            if (handler == null) return false;
            int index = tag.getInt(TAG_INDEX);
            if (index < 0 || index >= handler.getSlots()) return false;
            ItemStack current = handler.getStackInSlot(index);
            if (current.isEmpty()) return false;

            // IItemHandler 只有 extractItem（setStackInSlot 在 IItemHandlerModifiable 上），
            // 因此按数量抽取；抽不干净（极少数自定义 handler）时只把真正抽走的那部分留在记录里，
            // 残留在槽位里的那点照原版掉落，既不凭空消失也不复制
            int before = current.getCount();
            handler.extractItem(index, before, false);
            int after = handler.getStackInSlot(index).getCount();
            if (after <= 0) return true;
            int taken = before - after;
            if (taken <= 0) return false;
            tag.put(TAG_STACK, current.copyWithCount(taken).save(new CompoundTag()));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 反射失败只提示一次，避免每次死亡刷屏。 */
    private static void warn(String message, Throwable t) {
        if (warnOnce) return;
        warnOnce = true;
        LOGGER.warn("[InfiniteStats] " + message, t);
    }
}
