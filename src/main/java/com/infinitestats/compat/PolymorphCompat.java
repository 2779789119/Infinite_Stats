package com.infinitestats.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

/**
 * Polymorph（多态合成）联动桥接（反射调用，运行时才需 Polymorph 加载，无需编译期依赖）。
 *
 * <p>Polymorph 让「多个配方产出同一物品」时由玩家自行选择。它认识三类配方宿主：
 * 方块实体（熔炉等）、物品（背包里的合成升级等）、以及<b>玩家本身</b>——
 * 最后这条正是为「没有方块实体的合成容器」准备的，原版工作台走的就是它。
 * 本模组的随身工作台同样是无方块实体的容器，因此直接复用玩家的配方数据。
 *
 * <p>之所以能用极少代码接上，是因为 Polymorph 的
 * {@code AbstractRecipeData#getRecipe} 会把「收集候选配方 → 按玩家上次的选择挑一个 →
 * 把候选列表同步给客户端控件」全部做完：
 * <ul>
 *   <li>传入空列表时它自己去 {@code RecipeManager#getRecipesFor} 收集全部匹配配方；</li>
 *   <li>客户端那侧的配方选择控件由 Polymorph 自己建立 —— 它的
 *       {@code findCraftingResultSlot} 按「槽位容器是不是 {@code ResultContainer}」
 *       识别合成结果槽，而随身工作台的结果槽正是 {@code ResultSlot}/{@code ResultContainer}，
 *       于是界面打开时会被自动接管，本模组<b>无需任何客户端代码</b>；</li>
 *   <li>列表同步走 {@code PlayerRecipeData#sendRecipesListToListeners}，但它只在
 *       「配方数据的容器菜单 == 玩家当前打开的菜单」时才发送，所以调用前必须先
 *       {@code setContainerMenu(menu)}；该方法读完会自动清空，不必手动还原。</li>
 * </ul>
 *
 * <p>所有调用均通过反射完成；Polymorph 未加载或反射失败时 {@link #getSelectedRecipe}
 * 返回 {@code null}，调用方回退到原版「取第一个匹配配方」的行为。
 */
public final class PolymorphCompat {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolymorphCompat.class);

    private static final String POLYMORPH_MODID = "polymorph";
    private static final String API_CLASS = "com.illusivesoulworks.polymorph.api.PolymorphApi";
    private static final String COMMON_INTERFACE =
            "com.illusivesoulworks.polymorph.api.common.base.IPolymorphCommon";
    private static final String PLAYER_DATA_INTERFACE =
            "com.illusivesoulworks.polymorph.api.common.capability.IPlayerRecipeData";

    private static boolean initialized = false;
    private static boolean available = false;

    /** {@code PolymorphApi.common()} */
    private static Method apiCommon;
    /** {@code IPolymorphCommon.getRecipeData(Player)} */
    private static Method getRecipeData;
    /** {@code IRecipeData.getRecipe(RecipeType, Container, Level, List)} */
    private static Method getRecipe;
    /** {@code IPlayerRecipeData.setContainerMenu(AbstractContainerMenu)} */
    private static Method setContainerMenu;

    private PolymorphCompat() {}

    /** Polymorph 是否已加载且 API 可用。 */
    public static boolean isAvailable() {
        initialize();
        return available;
    }

    private static void initialize() {
        if (initialized) return;
        initialized = true;

        if (!ModList.get().isLoaded(POLYMORPH_MODID)) return;

        try {
            Class<?> apiClass = Class.forName(API_CLASS);
            apiCommon = apiClass.getMethod("common");

            Class<?> commonInterface = Class.forName(COMMON_INTERFACE);
            getRecipeData = commonInterface.getMethod("getRecipeData", Player.class);

            Class<?> playerDataInterface = Class.forName(PLAYER_DATA_INTERFACE);
            getRecipe = playerDataInterface.getMethod("getRecipe",
                    RecipeType.class, Container.class, Level.class, List.class);
            setContainerMenu = playerDataInterface.getMethod("setContainerMenu", AbstractContainerMenu.class);

            available = true;
        } catch (Throwable t) {
            LOGGER.warn("[infinitestats] Polymorph 桥接初始化失败，随身工作台将回退到原版配方选择：{}", t.toString());
        }
    }

    /**
     * 请 Polymorph 决定这次合成该用哪个配方。
     *
     * <p>调用前会把当前菜单挂到玩家的配方数据上，这样 Polymorph 才会把候选列表同步给
     * 客户端的选择控件（读完它自己会清掉）。配方候选列表交给 Polymorph 自行收集。
     *
     * @return 选中的配方；Polymorph 未加载、调用失败或候选为空时返回 {@code null}
     */
    public static CraftingRecipe getSelectedRecipe(ServerPlayer player,
                                                   AbstractContainerMenu menu,
                                                   CraftingContainer craftSlots,
                                                   Level level) {
        if (!isAvailable()) return null;

        try {
            Object common = apiCommon.invoke(null);
            Optional<?> dataOptional = (Optional<?>) getRecipeData.invoke(common, player);
            if (dataOptional.isEmpty()) return null;
            Object recipeData = dataOptional.get();

            setContainerMenu.invoke(recipeData, menu);

            // 传空列表：Polymorph 会自己去 RecipeManager 收集全部匹配配方，
            // 顺带完成候选列表的整理与向客户端控件的同步
            Object raw = getRecipe.invoke(recipeData, RecipeType.CRAFTING, craftSlots, level, List.of());
            if (!(raw instanceof Optional<?> picked) || picked.isEmpty()) return null;

            Object recipe = picked.get();
            return recipe instanceof CraftingRecipe crafting ? crafting : null;
        } catch (Throwable t) {
            // 反射失败一律静默降级，绝不影响正常合成
            LOGGER.debug("[infinitestats] Polymorph 配方选择失败，回退原版逻辑：{}", t.toString());
            return null;
        }
    }
}
