package com.infinitestats.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Tom's Storage（tom5454/tomsstorage，mod_id: tomsstorage）联动桥接（反射调用，
 * 运行时才需 TS 加载，无需编译期依赖）。
 *
 * 与 RS/BD 不同，Tom's Storage 没有“玩家级全局网络”，其存储是以「存储终端
 * （Storage Terminal）」方块为访问点、经由库存线缆连接到各个容器的网络。
 * 因此本桥接的模型是：定位玩家当前可用的存储终端，再从其合并后的
 * IItemHandler（线缆网络聚合的容器 handler）读取 / 存入物品。
 *
 * 定位策略（按优先级，全部反射实现、失败即降级）：
 *   1. 玩家手持已绑定的无线终端（NBT 含 BindX/Y/Z/BindDim）时，直接连接所绑定维度
 *      内、对应坐标处的存储终端（真正的“远距离无线访问”，无视范围扫描）；
 *   2. 退而求其次，按手持终端类型取对应配置范围——高级无线终端用 advWirelessRange、
 *      普通无线终端 / 未持终端用 wirelessRange——扫描玩家当前维度内、该范围内最近的
 *      存储终端方块。
 *   （跨维度绑定：仅当绑定维度与玩家当前维度一致时直连生效，否则回退到范围扫描。）
 *
 * 取得 IItemHandler 的尝试顺序（以 TS 1.7.1 实测为准）：
 *   - 私有字段 itemHandler（StorageTerminalBlockEntity 唯一可靠路径，updateServer() 每
 *     tick 聚合理线网络写入，支持 getSlots/extractItem/insertItem，可读写）；
 *   - 公开方法 getMergedHandler() 等（兼容其他构建，TS 1.7.1 上不存在，仅作 fallback）；
 *   - Forge ITEM_HANDLER 能力（TS 1.7.1 未重写 getCapability，仅作 fallback）。
 *
 * 最终统一用标准 IItemHandler 的 list/insert/extract 逻辑（复用
 * BackpackNetworkBridge 的实现），因此对 NetworkIO 而言与背包同构。
 *
 * TS 未加载或反射失败时安全降级（返回空列表 / 空栈）。
 */
public final class TomsStorageNetworkBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(TomsStorageNetworkBridge.class);

    private static final String TS_MODID = "tomsstorage";
    private static final String STORAGE_TERMINAL_ID = "tomsstorage:storage_terminal";
    private static final String CRAFTING_TERMINAL_ID = "tomsstorage:crafting_terminal";

    private static Boolean tsLoaded;

    private TomsStorageNetworkBridge() {
    }

    private static boolean ensureInit() {
        if (tsLoaded != null) return tsLoaded;
        tsLoaded = ModList.get().isLoaded(TS_MODID);
        return tsLoaded;
    }

    public static boolean isTSLoaded() {
        return ensureInit();
    }

    /** 取得玩家可用的 Tom's Storage 存储终端 handler 列表（0 或 1 个元素）。 */
    public static List<IItemHandler> getHandlers(Player player) {
        List<IItemHandler> handlers = new ArrayList<>();
        if (!ensureInit()) return handlers;
        try {
            // 1) 优先直连已绑定的无线终端坐标（真正的远距离访问）
            BlockEntity bound = boundTerminal(player);
            BlockEntity be = bound != null ? bound : nearestTerminal(player);
            if (be == null) {
                LOGGER.warn("[TomsStorageNetworkBridge] 未在范围内找到存储终端：需在存储终端 wirelessRange 范围内，"
                        + "手持无线终端可扩大范围；手持已绑定终端可无视范围直连");
                return handlers;
            }
            IItemHandler h = getHandler(be);
            if (h != null && h.getSlots() > 0) handlers.add(h);
        } catch (Throwable t) {
            LOGGER.error("[TomsStorageNetworkBridge] 定位存储终端失败", t);
        }
        return handlers;
    }

    /** 玩家主手/副手是否持有 Tom's Storage 无线终端，返回该物品栈或 null。 */
    private static ItemStack heldWireless(Player player) {
        for (ItemStack s : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.getItem());
            if (id != null && id.getNamespace().equals(TS_MODID)
                    && (id.getPath().equals("wireless_terminal") || id.getPath().equals("adv_wireless_terminal"))) {
                return s;
            }
        }
        return null;
    }

    /**
     * 解析手持无线终端的绑定坐标，若绑定维度与玩家当前维度一致则返回对应存储终端。
     * 绑定 NBT 键（TS 1.7.1 实测）：BindX/BindY/BindZ（int）、BindDim（维度 ResourceLocation 字符串）。
     */
    private static BlockEntity boundTerminal(Player player) {
        ItemStack held = heldWireless(player);
        if (held == null) return null;
        CompoundTag tag = held.getTag();
        if (tag == null || !tag.contains("BindX", CompoundTag.TAG_INT)
                || !tag.contains("BindY", CompoundTag.TAG_INT) || !tag.contains("BindZ", CompoundTag.TAG_INT)) {
            return null;
        }
        ResourceLocation dim = ResourceLocation.tryParse(tag.getString("BindDim"));
        if (dim == null || !player.level().dimension().location().equals(dim)) {
            return null; // 跨维度绑定不支持，回退到范围扫描
        }
        BlockPos bp = new BlockPos(tag.getInt("BindX"), tag.getInt("BindY"), tag.getInt("BindZ"));
        if (!player.level().isLoaded(bp)) return null;
        BlockEntity be = player.level().getBlockEntity(bp);
        return isStorageTerminal(be) ? be : null;
    }

    /** 扫描玩家当前维度内最近（且范围内）的存储终端方块。 */
    private static BlockEntity nearestTerminal(Player player) {
        int r = getConnectRange(player);
        BlockPos p = player.blockPosition();
        BlockEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos bp = new BlockPos(p.getX() + dx, p.getY() + dy, p.getZ() + dz);
                    if (!player.level().isLoaded(bp)) continue;
                    BlockEntity be = player.level().getBlockEntity(bp);
                    if (!isStorageTerminal(be)) continue;
                    double d = bp.distSqr(p);
                    if (d < bestD) {
                        bestD = d;
                        best = be;
                    }
                }
            }
        }
        return best;
    }

    /** 按手持终端类型取连接范围：高级无线终端用 advWirelessRange，其余用 wirelessRange。 */
    private static int getConnectRange(Player player) {
        ItemStack held = heldWireless(player);
        if (held != null) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(held.getItem());
            if (id != null && id.getPath().equals("adv_wireless_terminal")) {
                return getAdvWirelessRange();
            }
        }
        return getWirelessRange();
    }

    private static boolean isStorageTerminal(BlockEntity be) {
        if (be == null) return false;
        try {
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(be.getBlockState().getBlock());
            if (id != null && (STORAGE_TERMINAL_ID.equals(id.toString())
                    || CRAFTING_TERMINAL_ID.equals(id.toString()))) return true;
        } catch (Throwable ignored) {
        }
        // 兜底：类名包含 StorageTerminal（兼容不同构建的注册名差异）
        return be.getClass().getSimpleName().contains("StorageTerminal");
    }

    /** 从终端方块实体取得聚合后的 IItemHandler（多种尝试，失败返回 null）。 */
    private static IItemHandler getHandler(BlockEntity be) {
        // 1) 私有字段（TS 1.7.1 确认可靠路径）：StorageTerminalBlockEntity 不暴露公开
        //    handler 方法、也未重写 getCapability，其聚合理线网络的 handler 仅存于私有
        //    字段 itemHandler，由 updateServer() 每 tick 填充，支持读写。
        for (String fn : new String[]{"itemHandler", "mergedHandler", "handler"}) {
            try {
                Field f = be.getClass().getDeclaredField(fn);
                f.setAccessible(true);
                Object v = f.get(be);
                if (v instanceof IItemHandler) return (IItemHandler) v;
            } catch (Throwable ignored) {
            }
        }
        // 2) 公开方法（兼容其他构建：部分构建可能暴露 getMergedHandler 等）
        for (String name : new String[]{"getMergedHandler", "getContainer", "getItemHandler", "getInventoryHandler"}) {
            try {
                Method m = be.getClass().getMethod(name);
                Object r = m.invoke(be);
                if (r instanceof IItemHandler) return (IItemHandler) r;
            } catch (Throwable ignored) {
            }
        }
        // 3) 标准 ITEM_HANDLER 能力（个别构建会把合并 handler 暴露为能力）
        try {
            var cap = be.getCapability(ForgeCapabilities.ITEM_HANDLER, null);
            if (cap.isPresent()) {
                IItemHandler h = cap.resolve().orElse(null);
                if (h != null) return h;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** 读取 Tom's Storage 配置的整数字段（默认无线范围 16 / 高级无线范围 24）。 */
    private static int getConfigInt(String field, int def) {
        try {
            Class<?> cfg = Class.forName("com.tom.storagemod.Config");
            Object inst = cfg.getMethod("get").invoke(null);
            return inst.getClass().getField(field).getInt(inst);
        } catch (Throwable t) {
            return def;
        }
    }

    private static int getWirelessRange() {
        return getConfigInt("wirelessRange", 16);
    }

    private static int getAdvWirelessRange() {
        return getConfigInt("advWirelessRange", 24);
    }
}
