package com.infinitestats.compat;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Beyond Dimensions 联动桥接（反射调用，运行时才需 BD 加载，无需编译期依赖）。
 *
 * 维度网络（Dimensional Network）是绑定到玩家的跨维度存储系统：
 * - 通过 DimensionsNet.getNetFromPlayer / getPrimaryNetFromPlayer(Player) 取得玩家网络
 * - 通过 DimensionsNet.getUnifiedStorage() 取得统一存储（实现 IStackHandler）
 * - 物品以 ItemStackKey 包装，存取经由 IStackHandler.insert/extract，返回 KeyAmount
 *
 * 所有 BD 调用均通过反射完成，BD 未加载或反射失败时会安全降级（返回 null / 空）。
 */
public final class BDNetworkBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(BDNetworkBridge.class);

    private static final String BD_MODID = "beyonddimensions";
    private static final String DN_CLASS = "com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet";
    private static final String IKEY_CLASS = "com.wintercogs.beyonddimensions.api.storage.key.IStackKey";
    private static final String ISH_CLASS = "com.wintercogs.beyonddimensions.api.storage.handler.IStackHandler";
    private static final String ISK_CLASS = "com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey";
    private static final String KA_CLASS = "com.wintercogs.beyonddimensions.api.storage.key.KeyAmount";

    private static Boolean bdLoaded;
    private static Class<?> dnClass;
    private static Class<?> iKeyClass;
    private static Class<?> iStackHandlerClass;
    private static Class<?> itemStackKeyClass;
    private static Class<?> keyAmountClass;
    private static Method getPrimaryNet;
    private static Method getNetFromPlayer;
    private static Method getUnifiedStorage;
    private static Method insert;
    private static Method extract;
    private static Method getStorage;
    private static Method toStack;
    private static Method isEmpty;
    private static Method getKey;
    private static boolean initialized = false;

    private BDNetworkBridge() {
    }

    private static boolean ensureInit() {
        if (initialized) return bdLoaded != null && bdLoaded;
        initialized = true;
        bdLoaded = ModList.get().isLoaded(BD_MODID);
        if (!bdLoaded) return false;
        try {
            dnClass = Class.forName(DN_CLASS);
            iKeyClass = Class.forName(IKEY_CLASS);
            iStackHandlerClass = Class.forName(ISH_CLASS);
            itemStackKeyClass = Class.forName(ISK_CLASS);
            keyAmountClass = Class.forName(KA_CLASS);

            getPrimaryNet = findMethod(dnClass, "getPrimaryNetFromPlayer");
            getNetFromPlayer = findMethod(dnClass, "getNetFromPlayer");
            getUnifiedStorage = dnClass.getMethod("getUnifiedStorage");
            insert = iStackHandlerClass.getMethod("insert", iKeyClass, long.class, boolean.class);
            extract = iStackHandlerClass.getMethod("extract", iKeyClass, long.class, boolean.class);
            getStorage = iStackHandlerClass.getMethod("getStorage");
            toStack = keyAmountClass.getMethod("toStack");
            isEmpty = keyAmountClass.getMethod("isEmpty");
            getKey = keyAmountClass.getMethod("key");
            return true;
        } catch (Throwable t) {
            LOGGER.error("[BDNetworkBridge] 初始化 Beyond Dimensions API 反射失败", t);
            bdLoaded = false;
            return false;
        }
    }

    private static Method findMethod(Class<?> clazz, String name) {
        if (clazz == null) return null;
        for (Method m : clazz.getMethods()) {
            if (m.getName().equals(name)) return m;
        }
        return null;
    }

    public static boolean isBDLoaded() {
        return ensureInit();
    }

    /**
     * 取得玩家当前绑定的维度网络句柄（Object，运行时为 DimensionsNet 实例）。
     * 玩家尚未创建任何维度网络时返回 null。
     */
    public static Object getNetwork(Player player) {
        if (!ensureInit()) return null;
        try {
            Object net = null;
            if (getPrimaryNet != null) net = getPrimaryNet.invoke(null, player);
            if (net == null && getNetFromPlayer != null) net = getNetFromPlayer.invoke(null, player);
            return net;
        } catch (Throwable t) {
            LOGGER.error("[BDNetworkBridge] 获取维度网络失败", t);
            return null;
        }
    }

    /** 从网络提取最多 count 个 template 同类物品，返回实际提取到的（可能为空）。 */
    public static ItemStack extract(Object network, ItemStack template, int count) {
        if (network == null || template.isEmpty()) return ItemStack.EMPTY;
        try {
            Object storage = getUnifiedStorage.invoke(network);
            if (storage == null) return ItemStack.EMPTY;
            Object key = itemStackKeyClass.getConstructor(ItemStack.class).newInstance(template);
            Object ka = extract.invoke(storage, key, (long) count, false);
            if (ka == null || (boolean) isEmpty.invoke(ka)) return ItemStack.EMPTY;
            Object stack = toStack.invoke(ka);
            return stack instanceof ItemStack s ? s : ItemStack.EMPTY;
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }

    /** 将物品存入网络。返回未能存入的剩余（null / 空表示全部存入）。 */
    public static ItemStack insert(Object network, ItemStack stack) {
        if (network == null || stack.isEmpty()) return stack;
        try {
            Object storage = getUnifiedStorage.invoke(network);
            if (storage == null) return stack;
            Object key = itemStackKeyClass.getConstructor(ItemStack.class).newInstance(stack);
            // insert 返回“剩余（未被接受）”的 KeyAmount
            Object ka = insert.invoke(storage, key, (long) stack.getCount(), false);
            if (ka == null || (boolean) isEmpty.invoke(ka)) return ItemStack.EMPTY;
            Object remaining = toStack.invoke(ka);
            return remaining instanceof ItemStack s ? s : ItemStack.EMPTY;
        } catch (Throwable t) {
            return stack;
        }
    }

    /** 列出网络中所有物品（每种一份，count 为网络内总数，仅物品类）。 */
    public static List<ItemStack> listItems(Object network) {
        List<ItemStack> result = new ArrayList<>();
        if (network == null) return result;
        try {
            Object storage = getUnifiedStorage.invoke(network);
            if (storage == null) return result;
            Object list = getStorage.invoke(storage);
            if (list instanceof Iterable) {
                for (Object o : (Iterable<?>) list) {
                    if (!keyAmountClass.isInstance(o)) continue;
                    Object key = getKey.invoke(o);
                    // 仅处理物品类键（跳过能量 / 流体等其它资源类型）
                    if (!itemStackKeyClass.isInstance(key)) continue;
                    Object stack = toStack.invoke(o);
                    if (stack instanceof ItemStack s && !s.isEmpty()) result.add(s);
                }
            }
        } catch (Throwable t) {
            // 忽略
        }
        return result;
    }
}
