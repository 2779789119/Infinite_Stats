package com.infinitestats.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Refined Storage 联动桥接（反射调用，运行时才需 RS 加载，无需编译期依赖）。
 *
 * 定位逻辑与 RS 自身 {@code NetworkItem.applyNetwork} 完全一致：玩家背包 / 盔甲 /
 * 副手 / Curios 饰品栏中持有 RS 无线终端（refinedstorage:*wireless*，含官方扩展
 * refinedstorageaddons 的无线合成网格，二者均继承自 RS 的 NetworkItem），且终端已
 * 在游戏内潜行右键点击网络方块完成“绑定”时（物品 NBT 写入 NodeX/NodeY/NodeZ/
 * Dimension），本桥复用 RS 的 {@code NetworkItem.applyNetwork(...)} 从绑定节点反查
 * 出对应的 INetwork（无论终端绑定在控制器还是任意线缆节点上都能命中）。反射失败
 * 时回落到 {@code getNetworkManager(绑定维度).getNetwork(绑定坐标)}。
 *
 * 所有 RS 调用均通过反射完成，RS 未加载或反射失败时会安全降级（返回 null / 空）。
 */
public final class RSNetworkBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(RSNetworkBridge.class);

    private static final String RS_MODID = "refinedstorage";
    private static final String API_CLASS = "com.refinedmods.refinedstorage.apiimpl.API";
    private static final String IRSAPI_CLASS = "com.refinedmods.refinedstorage.api.IRSAPI";
    private static final String INET_MANAGER_CLASS = "com.refinedmods.refinedstorage.api.network.INetworkManager";
    private static final String INETWORK_CLASS = "com.refinedmods.refinedstorage.api.network.INetwork";
    private static final String ACTION_CLASS = "com.refinedmods.refinedstorage.api.util.Action";
    private static final String SERVER_LEVEL_CLASS = "net.minecraft.server.level.ServerLevel";
    private static final String BLOCKPOS_CLASS = "net.minecraft.core.BlockPos";
    private static final String NETWORK_ITEM_CLASS = "com.refinedmods.refinedstorage.item.NetworkItem";

    private static Boolean rsLoaded;
    private static Object actionPerform;
    private static Method apiInstance;
    private static Method getNetMgr;
    private static Method getNet;
    private static Method netExtract;
    private static Method netInsert;
    private static Method netGetItemCache;
    private static Method cacheGetList;
    private static Method listGetStacks;
    private static boolean initialized = false;

    // RS 自己的 NetworkItem 反射缓存（用其 applyNetwork 解析网络，最可靠）
    private static Class<?> networkItemClz;
    private static Method niIsValid;
    private static Method niApplyNetwork;
    private static boolean niInit = false;
    private static boolean niAvailable = false;

    private RSNetworkBridge() {
    }

    private static boolean ensureInit() {
        if (initialized) return rsLoaded != null && rsLoaded;
        initialized = true;
        rsLoaded = ModList.get().isLoaded(RS_MODID);
        if (!rsLoaded) return false;
        try {
            Class<?> apiClz = Class.forName(API_CLASS);
            Class<?> irsApiClz = Class.forName(IRSAPI_CLASS);
            Class<?> nmClass = Class.forName(INET_MANAGER_CLASS);
            Class<?> inet = Class.forName(INETWORK_CLASS);
            Class<?> actionClz = Class.forName(ACTION_CLASS);
            Class<?> serverLevelClz = Class.forName(SERVER_LEVEL_CLASS);
            Class<?> blockPosClz = Class.forName(BLOCKPOS_CLASS);
            actionPerform = actionClz.getField("PERFORM").get(null);
            // 通过 RS 公开 API：API.instance().getNetworkManager(ServerLevel).getNetwork(BlockPos)
            apiInstance = apiClz.getMethod("instance");
            getNetMgr = irsApiClz.getMethod("getNetworkManager", serverLevelClz);
            getNet = nmClass.getMethod("getNetwork", blockPosClz);
            netExtract = inet.getMethod("extractItem", ItemStack.class, int.class, actionClz);
            netInsert = inet.getMethod("insertItem", ItemStack.class, int.class, actionClz);
            netGetItemCache = inet.getMethod("getItemStorageCache");
            Class<?> cacheClz = netGetItemCache.getReturnType();
            cacheGetList = cacheClz.getMethod("getList");
            Class<?> listClz = cacheGetList.getReturnType();
            // IStackList 同时有 getStacks()（无参，列出全部）与 getStacks(T)（有参，按物品过滤）
            // 两个重载。必须用无参版本；若用 findMethod 遍历 getMethods() 命中 1 参版本，
            // 无参 invoke(list) 会抛 IllegalArgumentException 被吞，导致 listItems 恒为空，
            // 表现为“熔炉能存（走 insert）不能取（走 listItems）”。故显式取无参签名。
            try {
                listGetStacks = listClz.getMethod("getStacks");
            } catch (Throwable t) {
                listGetStacks = findMethod(listClz, new String[]{"getStacksAsList"});
            }
            return true;
        } catch (Throwable t) {
            LOGGER.error("[RSNetworkBridge] 初始化 RS API 反射失败", t);
            rsLoaded = false;
            return false;
        }
    }

    private static Method findMethod(Class<?> clazz, String[] names) {
        if (clazz == null) return null;
        for (String n : names) {
            for (Method m : clazz.getMethods()) {
                if (m.getName().equals(n)) return m;
            }
        }
        return null;
    }

    // 单独初始化 RS 的 NetworkItem 反射（失败不应拖垮整个 RS 桥接，可回落到手动解析）
    private static boolean ensureNetworkItem() {
        if (niInit) return niAvailable;
        niInit = true;
        niAvailable = false;
        if (!ensureInit()) return false;
        try {
            networkItemClz = Class.forName(NETWORK_ITEM_CLASS);
            niIsValid = networkItemClz.getMethod("isValid", ItemStack.class);
            niApplyNetwork = networkItemClz.getMethod("applyNetwork", MinecraftServer.class, ItemStack.class, Consumer.class, Consumer.class);
            niAvailable = true;
        } catch (Throwable t) {
            niAvailable = false;
        }
        return niAvailable;
    }

    public static boolean isRSLoaded() {
        return ensureInit();
    }

    /** 玩家是否持有 RS 无线终端（背包 / 盔甲 / 副手 / Curios 饰品栏）。 */
    public static boolean hasWirelessTerminal(Player player) {
        return findWirelessTerminal(player) != null;
    }

    /** 返回玩家持有的所有 RS 无线终端物品（背包 / 盔甲 / 副手 / Curios 饰品栏）。 */
    public static List<ItemStack> findWirelessTerminals(Player player) {
        List<ItemStack> list = new ArrayList<>();
        if (!ensureInit()) return list;
        // 预加载 RS 的 NetworkItem 类，供 isWireless 的兜底判断使用
        ensureNetworkItem();
        Inventory inv = player.getInventory();
        for (ItemStack s : inv.armor) {
            if (isWireless(s)) list.add(s);
        }
        for (ItemStack s : inv.offhand) {
            if (isWireless(s)) list.add(s);
        }
        for (ItemStack s : inv.items) {
            if (isWireless(s)) list.add(s);
        }
        // Curios 饰品栏（反射，失败则忽略）
        list.addAll(findInCurios(player));
        return list;
    }

    /** 返回玩家持有的第一个 RS 无线终端物品，没有则返回空。 */
    public static ItemStack findWirelessTerminal(Player player) {
        List<ItemStack> ts = findWirelessTerminals(player);
        return ts.isEmpty() ? ItemStack.EMPTY : ts.get(0);
    }

    /** 玩家持有的 RS 无线终端中，是否存在已绑定（可连网）的。 */
    public static boolean hasBoundWirelessTerminal(Player player) {
        if (!ensureInit()) return false;
        for (ItemStack t : findWirelessTerminals(player)) {
            if (ensureNetworkItem()) {
                try {
                    if ((Boolean) niIsValid.invoke(null, t)) return true;
                } catch (Throwable ignored) {
                }
                continue;
            }
            // 回落：仅按 NBT 绑定字段判断
            var tag = t.getTag();
            if (tag != null && tag.contains("NodeX") && tag.contains("NodeY")
                    && tag.contains("NodeZ") && tag.contains("Dimension")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWireless(ItemStack stack) {
        if (stack.isEmpty()) return false;
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        // 核心 Refined Storage 与官方扩展 refinedstorageaddons 的无线终端（含无线合成网格），
        // 按物品 id 快速判断；二者都继承自 RS 的 NetworkItem，绑定逻辑完全一致。
        if (id.getPath().contains("wireless")
                && ("refinedstorage".equals(id.getNamespace())
                    || "refinedstorageaddons".equals(id.getNamespace()))) {
            return true;
        }
        // 兜底：物品类是否继承自 RS 的 NetworkItem，覆盖任意命名空间下的 RS 无线终端
        //（例如 refinedstorageaddons:wireless_crafting_grid / creative_wireless_crafting_grid）。
        try {
            if (networkItemClz != null && networkItemClz.isInstance(stack.getItem())) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static List<ItemStack> findInCurios(Player player) {
        List<ItemStack> found = new ArrayList<>();
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Method getInv = api.getMethod("getCuriosInventory", Player.class);
            Object lazy = getInv.invoke(null, player);
            if (lazy == null) return found;
            Object opt = lazy.getClass().getMethod("resolve").invoke(lazy);
            if (opt == null || !(boolean) opt.getClass().getMethod("isPresent").invoke(opt)) {
                return found;
            }
            Object handler = opt.getClass().getMethod("get").invoke(opt);
            if (handler == null) return found;
            Object map = handler.getClass().getMethod("getCurios").invoke(handler);
            if (!(map instanceof Map)) return found;
            for (Object sh : ((Map<?, ?>) map).values()) {
                Object stacks = sh.getClass().getMethod("getStacks").invoke(sh);
                for (ItemStack s : asItemStackList(stacks)) {
                    if (isWireless(s)) found.add(s);
                }
            }
        } catch (Throwable ignored) {
            // 未安装 Curios 或 API 不兼容，忽略
        }
        return found;
    }

    @SuppressWarnings("unchecked")
    private static List<ItemStack> asItemStackList(Object o) {
        try {
            if (o instanceof List) return (List<ItemStack>) o;
            Object resolved = o.getClass().getMethod("resolve").invoke(o);
            if (resolved != null && (boolean) resolved.getClass().getMethod("isPresent").invoke(resolved)) {
                Object v = resolved.getClass().getMethod("get").invoke(resolved);
                if (v instanceof List) return (List<ItemStack>) v;
            }
        } catch (Throwable ignored) {
        }
        return new ArrayList<>();
    }

    /**
     * 取得玩家当前可用 RS 网络句柄（Object，运行时为 INetwork 实例）。
     *
     * 优先复用 RS 自己的 {@code NetworkItem.applyNetwork(...)} 解析网络——它内部通过
     * {@code NetworkUtils.getNetworkFromNode(getNodeFromBlockEntity(pos))} 从玩家绑定的
     * 网络节点反查网络，与无线终端在游戏内打开 GUI 用的是同一套逻辑，无论终端绑定在
     * 控制器还是任意线缆节点上都能正确命中。手动用
     * {@code getNetworkManager(维度).getNetwork(绑定坐标)} 仅在绑定坐标是控制器时才命中，
     * 绑定在线缆上会返回 null，因此仅作为反射失败的兜底。
     *
     * 没有任何已绑定的无线终端时返回 null。
     */
    public static Object getNetwork(Player player) {
        if (!ensureInit()) return null;
        if (!(player.level() instanceof ServerLevel)) return null;
        MinecraftServer server = player.getServer();
        if (server == null) return null;
        try {
            if (ensureNetworkItem()) {
                final Object[] found = {null};
                for (ItemStack terminal : findWirelessTerminals(player)) {
                    if (!(Boolean) niIsValid.invoke(null, terminal)) continue;
                    // applyNetwork 只是把网络句柄交给回调，不会打开任何 GUI
                    Object recv = networkItemClz.cast(terminal.getItem());
                    Consumer<Object> onNetwork = net -> found[0] = net;
                    Consumer<Object> onError = err -> {
                    };
                    niApplyNetwork.invoke(recv, server, terminal, onNetwork, onError);
                    if (found[0] != null) return found[0];
                }
                return null;
            }
            // 兜底：手动按 NBT 绑定坐标解析
            Object api = apiInstance.invoke(null);
            for (ItemStack terminal : findWirelessTerminals(player)) {
                var tag = terminal.getTag();
                if (tag == null || !tag.contains("NodeX") || !tag.contains("NodeY")
                        || !tag.contains("NodeZ") || !tag.contains("Dimension")) {
                    continue;
                }
                int x = tag.getInt("NodeX");
                int y = tag.getInt("NodeY");
                int z = tag.getInt("NodeZ");
                ResourceLocation dimName = ResourceLocation.tryParse(tag.getString("Dimension"));
                if (dimName == null) continue;
                ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, dimName);
                ServerLevel boundLevel = server.getLevel(dim);
                if (boundLevel == null) continue;
                Object mgr = getNetMgr.invoke(api, boundLevel);
                if (mgr == null) continue;
                Object net = getNet.invoke(mgr, new BlockPos(x, y, z));
                if (net != null) return net;
            }
            return null;
        } catch (Throwable t) {
            LOGGER.error("[RSNetworkBridge] 获取 RS 网络失败", t);
            return null;
        }
    }

    /**
     * 从网络提取最多 amount 个 template 同类物品，返回实际提取到的（可能为空）。
     *
     * 注意 RS 的 {@code INetwork.extractItem(stack, size, action)} 返回的是【已提取】
     * 的物品（而非未提取的剩余），与 insertItem 语义相反。因此直接返回该结果即可。
     */
    public static ItemStack extract(Object network, ItemStack template, int amount) {
        if (network == null || template.isEmpty()) return ItemStack.EMPTY;
        try {
            Object result = netExtract.invoke(network, template.copyWithCount(amount), amount, actionPerform);
            if (result instanceof ItemStack s && !s.isEmpty()) {
                return s;
            }
            return ItemStack.EMPTY;
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }

    /**
     * 将物品存入网络。返回未能存入的剩余（null / 空表示全部存入）。
     */
    public static ItemStack insert(Object network, ItemStack stack) {
        if (network == null || stack.isEmpty()) return stack;
        try {
            Object remaining = netInsert.invoke(network, stack.copy(), stack.getCount(), actionPerform);
            return remaining == null ? ItemStack.EMPTY : (ItemStack) remaining;
        } catch (Throwable t) {
            return stack;
        }
    }

    /** 列出网络中所有物品（每种一份，count 为网络内总数）。 */
    public static List<ItemStack> listItems(Object network) {
        List<ItemStack> result = new ArrayList<>();
        if (network == null || listGetStacks == null) return result;
        try {
            Object cache = netGetItemCache.invoke(network);
            Object list = cacheGetList.invoke(cache);
            Object stacks = listGetStacks.invoke(list);
            if (stacks instanceof Iterable) {
                for (Object e : (Iterable<?>) stacks) {
                    if (e == null) continue;
                    if (e instanceof ItemStack s && !s.isEmpty()) {
                        result.add(s);
                        continue;
                    }
                    try {
                        Object stack = e.getClass().getMethod("getStack").invoke(e);
                        if (stack instanceof ItemStack s && !s.isEmpty()) result.add(s);
                    } catch (Throwable ignored) {
                        // 单个条目解析失败，跳过
                    }
                }
            }
        } catch (Throwable t) {
            // 忽略
        }
        return result;
    }
}
