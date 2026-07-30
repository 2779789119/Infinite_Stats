package com.infinitestats.compat.jei;

import com.infinitestats.crafting.PortableCraftingMenu;
import com.infinitestats.emc.ModMenuTypes;
import com.infinitestats.network.NetworkHandler;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;

import java.util.*;

/**
 * 随身工作台的 JEI 一键转移配方处理器。
 * 支持客户端背包 + 存储网络库存双校验，避免无材料仍可点击。
 */
public class PortableCraftingRecipeTransferHandler implements IRecipeTransferHandler<PortableCraftingMenu, CraftingRecipe> {

    private static final long CACHE_TTL_MS = 5000;
    private static final Set<String> networkItemIds = Collections.synchronizedSet(new HashSet<>());
    private static long cacheExpiresAt;

    private final IRecipeTransferHandlerHelper helper;

    public PortableCraftingRecipeTransferHandler(IRecipeTransferHandlerHelper helper) {
        this.helper = helper;
    }

    // ══════════ 缓存 API（由 SyncNetworkItemsPacket 和 Screen 调用）══════════

    /**
     * 服务端同步的网络物品 ID 列表。
     * 调用时机：接收到 SyncNetworkItemsPacket。
     */
    public static void cacheNetworkItems(List<String> ids) {
        synchronized (networkItemIds) {
            networkItemIds.clear();
            networkItemIds.addAll(ids);
            cacheExpiresAt = System.currentTimeMillis() + CACHE_TTL_MS;
        }
    }

    /**
     * 主动请求网络物品列表。
     * 调用时机：打开随身工作台 / 随身熔炉 GUI 时。
     */
    public static void requestNetworkItems() {
        NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.RequestNetworkItemsPacket());
    }

    /** 检查缓存是否有效 */
    private static boolean isCacheValid() {
        return System.currentTimeMillis() < cacheExpiresAt && !networkItemIds.isEmpty();
    }

    /** 检查缓存的网络物品中是否有匹配 ingredient 的 */
    private static boolean networkCacheHas(Ingredient ing) {
        if (!isCacheValid()) return false;
        synchronized (networkItemIds) {
            for (String id : networkItemIds) {
                ResourceLocation rl = ResourceLocation.tryParse(id);
                if (rl == null) continue;
                net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
                if (item != null && ing.test(new ItemStack(item))) return true;
            }
        }
        return false;
    }

    // ══════════ JEI 转移逻辑 ══════════

    @Override
    public Class<? extends PortableCraftingMenu> getContainerClass() {
        return PortableCraftingMenu.class;
    }

    @Override
    public Optional<MenuType<PortableCraftingMenu>> getMenuType() {
        return Optional.of(ModMenuTypes.PORTABLE_CRAFTING_MENU.get());
    }

    @Override
    public mezz.jei.api.recipe.RecipeType<CraftingRecipe> getRecipeType() {
        return RecipeTypes.CRAFTING;
    }

    @Override
    public IRecipeTransferError transferRecipe(PortableCraftingMenu menu, CraftingRecipe recipe,
                                               IRecipeSlotsView recipeSlots, Player player,
                                               boolean maxTransfer, boolean doTransfer) {
        if (!doTransfer) {
            Ingredient[] grid = buildGrid(recipe);
            boolean needNetwork = false;
            for (int i = 0; i < 9; i++) {
                Ingredient ing = grid[i];
                if (ing == null || ing.isEmpty()) continue;
                if (!menu.getSlot(i + 1).getItem().isEmpty()
                        && ing.test(menu.getSlot(i + 1).getItem())) continue;
                if (hasInClientInventory(player, ing)) continue;
                needNetwork = true;
                break;
            }
            if (needNetwork) {
                // 先查缓存的网络物品列表
                if (isCacheValid() && checkNetworkCache(menu, grid, player)) {
                    // 缓存齐全 → 放行
                    return null;
                }
                // 缓存不满足 → 有终端则发请求刷新 + 乐观放行
                boolean hasTerm = hasStorageTerminal(player);
                if (hasTerm) requestNetworkItems();
                return hasTerm ? null
                        : helper.createUserErrorWithTooltip(
                                Component.translatable("screen.infinitestats.jei.missing_materials"));
            }
            return null;
        }
        Ingredient[] grid = buildGrid(recipe);
        NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.CraftingRecipeFillPacket(grid));
        return null;
    }

    /** 检查缓存中是否覆盖了所有缺失的材料（只检查背包没有的那些格） */
    private boolean checkNetworkCache(PortableCraftingMenu menu, Ingredient[] grid, Player player) {
        for (int i = 0; i < grid.length; i++) {
            Ingredient ing = grid[i];
            if (ing == null || ing.isEmpty()) continue;
            if (!menu.getSlot(i + 1).getItem().isEmpty()
                    && ing.test(menu.getSlot(i + 1).getItem())) continue;
            if (hasInClientInventory(player, ing)) continue;
            if (!networkCacheHas(ing)) return false;
        }
        return true;
    }

    // ══════════ 工具方法 ══════════

    private static boolean hasInClientInventory(Player player, Ingredient ing) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!s.isEmpty() && ing.test(s)) return true;
        }
        return false;
    }

    private static boolean hasStorageTerminal(Player player) {
        // 主物品栏 + 盔甲 + 副手
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!s.isEmpty() && isStorageTerminal(s)) return true;
        }
        // Curios 饰品槽
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            java.lang.reflect.Method getInv;
            try { getInv = api.getMethod("getCuriosInventory", Player.class); }
            catch (NoSuchMethodException e) {
                getInv = api.getMethod("getCuriosInventory",
                        Class.forName("net.minecraft.world.entity.LivingEntity"));
            }
            Object raw = getInv.invoke(null, player);
            if (raw == null) return false;
            Object opt = raw.getClass().getName().contains("LazyOptional")
                    ? raw.getClass().getMethod("resolve").invoke(raw) : raw;
            if (opt == null) return false;
            if (!(Boolean) opt.getClass().getMethod("isPresent").invoke(opt)) return false;
            Object ih = opt.getClass().getMethod("get").invoke(opt);
            if (ih == null) return false;
            Object map = ih.getClass().getMethod("getCurios").invoke(ih);
            if (!(map instanceof java.util.Map)) return false;
            for (Object sh : ((java.util.Map<?, ?>) map).values()) {
                Object stacks = sh.getClass().getMethod("getStacks").invoke(sh);
                if (stacks instanceof Iterable) {
                    for (Object sObj : (Iterable<?>) stacks) {
                        if (sObj instanceof ItemStack s
                                && !s.isEmpty() && isStorageTerminal(s)) return true;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static boolean isStorageTerminal(ItemStack stack) {
        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                stack.getItem()).toString();
        return id.contains(":wireless_")
                || id.contains(":network_card")
                || id.contains(":wireless_terminal")
                || id.contains("sophisticatedbackpacks:")
                || id.contains("toms_storage:")
                || id.contains("bd_network:")
                || id.endsWith("_backpack");
    }

    private static Ingredient[] buildGrid(CraftingRecipe recipe) {
        Ingredient[] grid = new Ingredient[9];
        Arrays.fill(grid, Ingredient.EMPTY);
        List<Ingredient> ings = recipe.getIngredients();
        int w = 3, h = 3;
        if (recipe instanceof ShapedRecipe sr) {
            w = sr.getWidth();
            h = sr.getHeight();
        } else if (!ings.isEmpty()) {
            h = (ings.size() + 2) / 3;
        }
        for (int ry = 0; ry < h; ry++) {
            for (int rx = 0; rx < w; rx++) {
                int idx = ry * w + rx;
                if (idx >= ings.size()) continue;
                int slot = ry * 3 + rx;
                if (slot < 9) grid[slot] = ings.get(idx);
            }
        }
        return grid;
    }
}
