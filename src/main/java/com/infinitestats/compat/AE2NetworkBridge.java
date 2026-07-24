package com.infinitestats.compat;

import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Applied Energistics 2 联动桥接（反射调用，运行时才需 AE2 加载，无需编译期依赖）。
 *
 * 定位逻辑：玩家背包 / 盔甲 / 副手 / Curios 饰品栏中持有 AE2 无线终端
 * （ae2:wireless_terminal 等，继承自 appeng.items.tools.powered.WirelessTerminalItem）
 * 时，视为“已连接 ME 网络”。再通过无线终端的 getLinkedGrid 取得玩家所在的 ME 网格
 * （要求终端已链接到无线接入点且有电，与 AE2 无线终端自身连网逻辑一致）。
 *
 * 取得 MEStorage（物品网络库存）后，存取统一走 AE2 的 MEStorage 接口：
 *   - 提取：MEStorage.extract(AEItemKey, count, MODULATE, source)
 *   - 存入：MEStorage.insert(AEItemKey, count, MODULATE, source)
 *   - 列举：MEStorage.getAvailableStacks() → KeyCounter
 *
 * 所有 AE2 调用均通过反射完成，AE2 未加载或反射失败时会安全降级（返回 null / 空）。
 */
public final class AE2NetworkBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(AE2NetworkBridge.class);

    private static final String AE2_MODID = "ae2";
    private static final String WIRELESS_CLASS = "appeng.items.tools.powered.WirelessTerminalItem";
    private static final String IGRID_CLASS = "appeng.api.networking.IGrid";
    private static final String ISTORAGESERVICE_CLASS = "appeng.api.networking.storage.IStorageService";
    private static final String MESTORAGE_CLASS = "appeng.api.storage.MEStorage";
    private static final String AEKEY_CLASS = "appeng.api.stacks.AEKey";
    private static final String AEITEMKEY_CLASS = "appeng.api.stacks.AEItemKey";
    private static final String ACTIONABLE_CLASS = "appeng.api.config.Actionable";
    private static final String IACTIONSOURCE_CLASS = "appeng.api.networking.security.IActionSource";
    private static final String KEYCOUNTER_CLASS = "appeng.api.stacks.KeyCounter";

    private static Boolean ae2Loaded;
    private static Class<?> wirelessClz;
    private static Class<?> igridClz;
    private static Class<?> istorageClz;
    private static Class<?> mestorageClz;
    private static Class<?> aekeyClz;
    private static Class<?> aeitemkeyClz;
    private static Class<?> actionableClz;
    private static Class<?> iactionClz;
    private static Class<?> keycounterClz;

    private static Method mGetLinkedGrid;
    private static Method mGetStorageService;
    private static Method mGetInventory;
    private static Method mExtract;
    private static Method mInsert;
    private static Method mGetAvailableStacks;
    private static Method mAEItemKeyOf;
    private static Method mAEItemKeyToStack;
    private static Method mKeyCounterKeySet;
    private static Method mKeyCounterGet;
    private static Method mActionSourceOfPlayer;
    private static Object actionableModulate;

    private static boolean initialized = false;

    private AE2NetworkBridge() {
    }

    private static boolean ensureInit() {
        if (initialized) return ae2Loaded != null && ae2Loaded;
        initialized = true;
        ae2Loaded = ModList.get().isLoaded(AE2_MODID);
        if (!ae2Loaded) return false;
        try {
            wirelessClz = Class.forName(WIRELESS_CLASS);
            igridClz = Class.forName(IGRID_CLASS);
            istorageClz = Class.forName(ISTORAGESERVICE_CLASS);
            mestorageClz = Class.forName(MESTORAGE_CLASS);
            aekeyClz = Class.forName(AEKEY_CLASS);
            aeitemkeyClz = Class.forName(AEITEMKEY_CLASS);
            actionableClz = Class.forName(ACTIONABLE_CLASS);
            iactionClz = Class.forName(IACTIONSOURCE_CLASS);
            keycounterClz = Class.forName(KEYCOUNTER_CLASS);

            mGetLinkedGrid = findMethodByArity(wirelessClz, "getLinkedGrid", 3);
            if (mGetLinkedGrid == null) throw new NoSuchMethodException("WirelessTerminalItem.getLinkedGrid");
            mGetStorageService = igridClz.getMethod("getStorageService");
            mGetInventory = istorageClz.getMethod("getInventory");
            mExtract = mestorageClz.getMethod("extract", aekeyClz, long.class, actionableClz, iactionClz);
            mInsert = mestorageClz.getMethod("insert", aekeyClz, long.class, actionableClz, iactionClz);
            mGetAvailableStacks = mestorageClz.getMethod("getAvailableStacks");
            mAEItemKeyOf = aeitemkeyClz.getMethod("of", ItemStack.class);
            mAEItemKeyToStack = aeitemkeyClz.getMethod("toStack", int.class);
            mKeyCounterKeySet = keycounterClz.getMethod("keySet");
            mKeyCounterGet = keycounterClz.getMethod("get", aekeyClz);
            mActionSourceOfPlayer = iactionClz.getMethod("ofPlayer", Player.class);
            actionableModulate = actionableClz.getField("MODULATE").get(null);
            return true;
        } catch (Throwable t) {
            LOGGER.error("[AE2NetworkBridge] 初始化 AE2 API 反射失败", t);
            ae2Loaded = false;
            return false;
        }
    }

    private static Method findMethodByArity(Class<?> clazz, String name, int arity) {
        if (clazz == null) return null;
        for (Method m : clazz.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == arity) return m;
        }
        return null;
    }

    public static boolean isAE2Loaded() {
        return ensureInit();
    }

    /** 玩家是否持有 AE2 无线终端（背包 / 盔甲 / 副手 / Curios 饰品栏）。 */
    public static boolean hasWirelessTerminal(Player player) {
        return !findWirelessTerminal(player).isEmpty();
    }

    /** 返回玩家持有的第一个 AE2 无线终端物品，没有则返回空栈。 */
    public static ItemStack findWirelessTerminal(Player player) {
        if (!ensureInit()) return ItemStack.EMPTY;
        Inventory inv = player.getInventory();
        for (ItemStack s : inv.armor) {
            if (isWireless(s)) return s;
        }
        for (ItemStack s : inv.offhand) {
            if (isWireless(s)) return s;
        }
        for (ItemStack s : inv.items) {
            if (isWireless(s)) return s;
        }
        ItemStack curios = findInCurios(player);
        if (!curios.isEmpty()) return curios;
        return ItemStack.EMPTY;
    }

    private static boolean isWireless(ItemStack stack) {
        if (stack.isEmpty()) return false;
        // 通过类继承精确识别 AE2 无线终端（含无线合成终端、无线流体终端等）
        if (wirelessClz.isInstance(stack.getItem())) return true;
        // 兜底：按物品注册名前缀 ae2:wireless* 识别
        var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return "ae2".equals(id.getNamespace()) && id.getPath().startsWith("wireless");
    }

    private static ItemStack findInCurios(Player player) {
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Method getInv = api.getMethod("getCuriosInventory", Player.class);
            Object lazy = getInv.invoke(null, player);
            if (lazy == null) return ItemStack.EMPTY;
            Object opt = lazy.getClass().getMethod("resolve").invoke(lazy);
            if (opt == null || !(Boolean) opt.getClass().getMethod("isPresent").invoke(opt)) {
                return ItemStack.EMPTY;
            }
            Object handler = opt.getClass().getMethod("get").invoke(opt);
            if (handler == null) return ItemStack.EMPTY;
            Object map = handler.getClass().getMethod("getCurios").invoke(handler);
            if (!(map instanceof Map)) return ItemStack.EMPTY;
            for (Object sh : ((Map<?, ?>) map).values()) {
                Object stacks = sh.getClass().getMethod("getStacks").invoke(sh);
                for (ItemStack s : asItemStackList(stacks)) {
                    if (isWireless(s)) return s;
                }
            }
        } catch (Throwable ignored) {
            // 未安装 Curios 或 API 不兼容，忽略
        }
        return ItemStack.EMPTY;
    }

    @SuppressWarnings("unchecked")
    private static List<ItemStack> asItemStackList(Object o) {
        try {
            if (o instanceof List) return (List<ItemStack>) o;
            Object resolved = o.getClass().getMethod("resolve").invoke(o);
            if (resolved != null && (Boolean) resolved.getClass().getMethod("isPresent").invoke(resolved)) {
                Object v = resolved.getClass().getMethod("get").invoke(resolved);
                if (v instanceof List) return (List<ItemStack>) v;
            }
        } catch (Throwable ignored) {
        }
        return new ArrayList<>();
    }

    /**
     * 取得玩家当前可达的 ME 网络句柄（Object[] { MEStorage, IActionSource }）。
     * 未持有无线终端 / 未链接 / 无电时返回 null。
     */
    public static Object getNetwork(Player player) {
        if (!ensureInit()) return null;
        ItemStack terminal = findWirelessTerminal(player);
        if (terminal.isEmpty()) return null;
        try {
            Object grid = mGetLinkedGrid.invoke(terminal.getItem(), terminal, player.level(), player);
            if (grid == null) return null;
            Object storageService = mGetStorageService.invoke(grid);
            if (storageService == null) return null;
            Object me = mGetInventory.invoke(storageService);
            if (me == null) return null;
            Object source = mActionSourceOfPlayer.invoke(null, player);
            return new Object[]{me, source};
        } catch (Throwable t) {
            LOGGER.error("[AE2NetworkBridge] 获取 ME 网络失败", t);
            return null;
        }
    }

    /** 从网络提取最多 amount 个 template 同类物品，返回实际提取到的（可能为空）。 */
    public static ItemStack extract(Object network, ItemStack template, int amount) {
        if (network == null || template.isEmpty()) return ItemStack.EMPTY;
        try {
            Object[] arr = (Object[]) network;
            Object me = arr[0];
            Object source = arr[1];
            Object key = mAEItemKeyOf.invoke(null, template);
            if (key == null) return ItemStack.EMPTY;
            long got = (Long) mExtract.invoke(me, key, (long) amount, actionableModulate, source);
            if (got <= 0) return ItemStack.EMPTY;
            return (ItemStack) mAEItemKeyToStack.invoke(key, (int) got);
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }

    /**
     * 将物品存入网络。返回未能存入的剩余（空表示全部存入）。
     */
    public static ItemStack insert(Object network, ItemStack stack) {
        if (network == null || stack.isEmpty()) return stack;
        try {
            Object[] arr = (Object[]) network;
            Object me = arr[0];
            Object source = arr[1];
            Object key = mAEItemKeyOf.invoke(null, stack);
            if (key == null) return stack;
            long remaining = (Long) mInsert.invoke(me, key, (long) stack.getCount(), actionableModulate, source);
            if (remaining <= 0) return ItemStack.EMPTY;
            return stack.copyWithCount((int) remaining);
        } catch (Throwable t) {
            return stack;
        }
    }

    /** 列出网络中所有物品（每种一份，count 为网络内总数）。 */
    @SuppressWarnings("unchecked")
    public static List<ItemStack> listItems(Object network) {
        List<ItemStack> result = new ArrayList<>();
        if (network == null) return result;
        try {
            Object[] arr = (Object[]) network;
            Object me = arr[0];
            Object kc = mGetAvailableStacks.invoke(me);
            if (kc == null) return result;
            Set<Object> keys = (Set<Object>) mKeyCounterKeySet.invoke(kc);
            for (Object key : keys) {
                if (!aeitemkeyClz.isInstance(key)) continue;
                long count = (Long) mKeyCounterGet.invoke(kc, key);
                if (count <= 0) continue;
                ItemStack is = (ItemStack) mAEItemKeyToStack.invoke(key, (int) Math.min(count, Integer.MAX_VALUE));
                if (!is.isEmpty()) result.add(is);
            }
        } catch (Throwable t) {
            // 忽略
        }
        return result;
    }
}
