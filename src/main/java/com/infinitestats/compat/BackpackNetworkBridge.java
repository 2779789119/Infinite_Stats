package com.infinitestats.compat;

import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sophisticated Backpacks 联动桥接（反射调用，运行时才需 SB 加载，无需编译期依赖）。
 *
 * 识别方式：背包物品通过自定义能力 CapabilityBackpackWrapper 暴露，借此精确区分
 * “是否 SB 背包”（避免误伤其它 IItemHandler 物品）；存取则统一走 Forge 标准
 * ForgeCapabilities.ITEM_HANDLER（即 BackpackWrapper.getInventoryForInputOutput()）。
 *
 * 由于背包是“玩家身上/饰品栏里的实体物品”，这里聚合玩家所有背包的 IItemHandler：
 * 主背包 / 盔甲 / 副手 / Curios 背部槽，作为一组可读写的本地存储，供随身工作台 /
 * 熔炉的联动按钮使用。对背包 handler 的写入会直接回写到物品 NBT，无需额外落盘。
 *
 * SB 未加载或反射失败时会安全降级（返回空列表 / 空栈）。
 */
public final class BackpackNetworkBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(BackpackNetworkBridge.class);

    private static final String SB_MODID = "sophisticatedbackpacks";
    private static final String CAP_CLASS = "net.p3pp3rf1y.sophisticatedbackpacks.api.CapabilityBackpackWrapper";

    private static Boolean sbLoaded;
    private static Capability<?> capInstance;
    private static boolean initialized = false;

    private BackpackNetworkBridge() {
    }

    private static boolean ensureInit() {
        if (initialized) return sbLoaded != null && sbLoaded;
        initialized = true;
        sbLoaded = ModList.get().isLoaded(SB_MODID);
        if (!sbLoaded) return false;
        try {
            Class<?> cap = Class.forName(CAP_CLASS);
            Method m = cap.getMethod("getCapabilityInstance");
            capInstance = (Capability<?>) m.invoke(null);
            return capInstance != null;
        } catch (Throwable t) {
            LOGGER.error("[BackpackNetworkBridge] 初始化 Sophisticated Backpacks 能力反射失败", t);
            sbLoaded = false;
            return false;
        }
    }

    public static boolean isSBLoaded() {
        return ensureInit();
    }

    /** 该物品栈是否为 Sophisticated Backpacks 背包（通过自定义能力识别）。 */
    public static boolean isBackpack(ItemStack stack) {
        if (!ensureInit() || stack.isEmpty()) return false;
        try {
            return stack.getCapability(capInstance).isPresent();
        } catch (Throwable t) {
            return false;
        }
    }

    /** 收集玩家所有背包的 IItemHandler（主背包 / 盔甲 / 副手 / Curios 背部槽）。 */
    public static List<IItemHandler> getHandlers(Player player) {
        List<IItemHandler> handlers = new ArrayList<>();
        if (!ensureInit()) return handlers;
        Inventory inv = player.getInventory();
        collect(inv.armor, handlers);
        collect(inv.offhand, handlers);
        collect(inv.items, handlers);
        collectCurios(player, handlers);
        return handlers;
    }

    private static void collect(Iterable<ItemStack> stacks, List<IItemHandler> handlers) {
        for (ItemStack s : stacks) {
            if (isBackpack(s)) {
                s.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(h -> {
                    if (h.getSlots() > 0) handlers.add(h);
                });
            }
        }
    }

    private static void collectCurios(Player player, List<IItemHandler> handlers) {
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");

            // 兼容多种 Curios API 签名（Player / LivingEntity）
            Method getInv;
            try {
                getInv = api.getMethod("getCuriosInventory", Player.class);
            } catch (NoSuchMethodException e1) {
                getInv = api.getMethod("getCuriosInventory",
                        Class.forName("net.minecraft.world.entity.LivingEntity"));
            }

            Object raw = getInv.invoke(null, player);
            if (raw == null) return;

            // 兼容 LazyOptional 和 Optional 两种返回类型
            Object opt;
            if (raw.getClass().getName().contains("LazyOptional")) {
                opt = raw.getClass().getMethod("resolve").invoke(raw);
            } else {
                opt = raw; // Optional
            }

            if (opt == null) return;
            Method isPresent = opt.getClass().getMethod("isPresent");
            if (!(Boolean) isPresent.invoke(opt)) return;
            Object ih = opt.getClass().getMethod("get").invoke(opt);
            if (ih == null) return;
            Object map = ih.getClass().getMethod("getCurios").invoke(ih);
            if (!(map instanceof Map)) return;
            for (Object sh : ((Map<?, ?>) map).values()) {
                Object stacks = sh.getClass().getMethod("getStacks").invoke(sh);
                // getStacks() 返回 IDynamicStackHandler（IItemHandler），按槽位遍历
                if (stacks instanceof IItemHandler itemHandler) {
                    for (int i = 0; i < itemHandler.getSlots(); i++) {
                        ItemStack s = itemHandler.getStackInSlot(i);
                        if (isBackpack(s)) {
                            s.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(h -> {
                                if (h.getSlots() > 0) handlers.add(h);
                            });
                        }
                    }
                } else {
                    // 兼容旧版返回 List<ItemStack> 的情况
                    for (ItemStack s : asItemStackList(stacks)) {
                        if (isBackpack(s)) {
                            s.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(h -> {
                                if (h.getSlots() > 0) handlers.add(h);
                            });
                        }
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("[BackpackNetworkBridge] 扫描 Curios 饰品栏失败，背包在饰品栏中将无法被检测到", t);
        }
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

    /** 列出所有背包中的物品（按 物品+标签 合并，count 为全部背包内总数）。 */
    @SuppressWarnings("unchecked")
    public static List<ItemStack> listItems(Object handlersObj) {
        List<ItemStack> result = new ArrayList<>();
        if (!(handlersObj instanceof List)) return result;
        List<IItemHandler> handlers = (List<IItemHandler>) handlersObj;
        Map<String, ItemStack> merged = new LinkedHashMap<>();
        for (IItemHandler h : handlers) {
            for (int i = 0; i < h.getSlots(); i++) {
                ItemStack s = h.getStackInSlot(i);
                if (s.isEmpty()) continue;
                String key = String.valueOf(ForgeRegistries.ITEMS.getKey(s.getItem())) + ":" + (s.getTag() == null ? "" : s.getTag().toString());
                ItemStack acc = merged.get(key);
                if (acc == null) {
                    acc = s.copy();
                    merged.put(key, acc);
                } else {
                    acc.grow(s.getCount());
                }
            }
        }
        result.addAll(merged.values());
        return result;
    }

    /** 从所有背包提取最多 count 个与 template 同类同标签的物品，返回实际提取到的。 */
    @SuppressWarnings("unchecked")
    public static ItemStack extract(Object handlersObj, ItemStack template, int count) {
        if (!(handlersObj instanceof List) || template.isEmpty()) return ItemStack.EMPTY;
        List<IItemHandler> handlers = (List<IItemHandler>) handlersObj;
        int remaining = count;
        ItemStack result = ItemStack.EMPTY;
        for (IItemHandler h : handlers) {
            if (remaining <= 0) break;
            for (int i = 0; i < h.getSlots(); i++) {
                ItemStack inSlot = h.getStackInSlot(i);
                if (inSlot.isEmpty() || !ItemStack.isSameItemSameTags(inSlot, template)) continue;
                int toExtract = Math.min(remaining, inSlot.getCount());
                ItemStack got = h.extractItem(i, toExtract, false);
                if (got.isEmpty()) continue;
                if (result.isEmpty()) result = got.copy();
                else result.grow(got.getCount());
                remaining -= got.getCount();
                if (remaining <= 0) break;
            }
        }
        return result;
    }

    /** 将物品存入所有背包（跨背包分配）。返回未能存入的剩余（空表示全部存入）。 */
    @SuppressWarnings("unchecked")
    public static ItemStack insert(Object handlersObj, ItemStack stack) {
        if (!(handlersObj instanceof List) || stack.isEmpty()) return stack;
        List<IItemHandler> handlers = (List<IItemHandler>) handlersObj;
        ItemStack remaining = stack.copy();
        for (IItemHandler h : handlers) {
            if (remaining.isEmpty()) break;
            for (int i = 0; i < h.getSlots(); i++) {
                ItemStack inSlot = h.getStackInSlot(i);
                // 仅向“空槽”或与剩余物品同类同标签的槽位”写入
                if (!inSlot.isEmpty() && !ItemStack.isSameItemSameTags(inSlot, remaining)) continue;
                remaining = h.insertItem(i, remaining, false);
                if (remaining.isEmpty()) break;
            }
        }
        return remaining;
    }
}
