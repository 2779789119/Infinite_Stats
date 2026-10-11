package com.infinitestats.compat;

import com.infinitestats.stats.PlayerStats;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * TACZ（永恒枪械工坊：零）联动 —— 无限子弹（弹匣永远是满的）。
 * <p>
 * 分两半，各管一件事：
 * <ol>
 *   <li><b>不再扣弹</b>：由 {@code mixin/TaczGunScriptApiMixin} 注入 TACZ 的
 *       {@code ModernKineticGunScriptAPI#reduceAmmoOnce} / {@code #removeAmmoFromMagazine} ——
 *       击发与拉栓都拿不到子弹、弹匣计数自始至终不变，因此也永远不需要换弹。
 *       之所以必须在 Mixin 里做：扣弹与生成子弹在同一次调用内完成，任何「事后补回来」的方案
 *       都赶不上同一 tick 的读数，HUD 会按射速闪 max-1。</li>
 *   <li><b>补满已有的弹匣</b>：由本类每几 tick 把<b>手持</b>枪械的弹匣补到上限。Mixin 只保证
 *       「不少」，保证不了「本来就不满的枪也是满的」—— 捡到 / 换到一把半匣的枪、
 *       或其它模组改过数值之后，靠这一步把读数拉满。</li>
 *   <li><b>背包直读枪的「虚拟备弹」</b>：同一趟里判断，若这把枪是 {@code useInventoryAmmo}
 *       （弹容显示「使用背包弹容」，如 M134 转管机枪），它没有弹匣、只认「背包里有没有这种弹」，
 *       于是给它塞 {@code DummyAmmo}（TACZ 自己的虚拟备弹通道）—— 背包一发实弹都没有也能打，
 *       而且不扣背包（见 {@link #fillDummyAmmo(Object, ItemStack)}）。</li>
 * </ol>
 * 判定「玩家是否解锁了这条属性」的入口 {@link #isInfiniteAmmoActive(LivingEntity)} 给 Mixin 用；
 * 它是纯 Capability 查询，不涉及任何反射，所以即使 TACZ 的类改名导致反射链失效，
 * 「不扣弹」这半边依然照常工作。
 * <p>
 * 全程<b>反射</b>（和 {@code compat/ApotheosisEnchantCompat} 同一套思路）：TACZ 只是可选联动，
 * 本模组不声明对它的编译期依赖。没装时 {@code Class.forName} 直接失败并永久短路、零开销；
 * 装了但版本对不上时只关掉「补满」这半边，不影响别的功能。
 */
public final class TaczCompat {

    /** TACZ 枪械物品接口。 */
    private static final String GUN_CLASS = "com.tacz.guns.api.item.IGun";
    /** TACZ 的枪械脚本 API（服务端侧），用来读「弹匣上限」。 */
    private static final String SCRIPT_API_CLASS = "com.tacz.guns.item.ModernKineticGunScriptAPI";

    /**
     * 背包直读枪的「虚拟备弹」目标量。
     * <p>
     * 与 TACZ 自带命令 {@code /tacz dummy <目标> <数量>} 用的是同一条通道
     * （{@code IGun#setDummyAmmoAmount}，官方命令也只调它、不碰 {@code MaxDummyAmmo}）。
     * 9999 足够任何射速打到下一次补齐。
     */
    private static final int DUMMY_AMMO = 9999;

    /** TACZ 是否已确认不可用（解析失败后不再重复尝试）。 */
    private static volatile boolean unavailable;

    // —— 反射句柄（解析成功后一直复用）——
    private static Class<?> gunClass;
    private static Constructor<?> scriptApiCtor;
    private static Method scriptApiSetItem;
    private static Method scriptApiGetMaxAmmo;
    private static Method getCurrentAmmo;
    private static Method setCurrentAmmo;
    private static Method useInventoryAmmo;
    private static Method getDummyAmmoAmount;
    private static Method setDummyAmmoAmount;
    private static Method hasHeatData;
    private static Method getHeatAmount;
    private static Method setHeatAmount;
    private static Method isOverheatLocked;
    private static Method setOverheatLocked;

    /** {@code ModernKineticGunScriptAPI#getShooter()}，给注入点用；解析失败后为 null 且不再重试。 */
    private static volatile Method scriptApiGetShooter;
    private static volatile boolean shooterUnavailable;

    private TaczCompat() {}

    // ========== 供 Mixin 调用 ==========

    /**
     * 从 {@code ModernKineticGunScriptAPI} 实例上取「当前射手」。
     * <p>
     * 注入点用不了 Mixin 的 {@code @Shadow}（影子解析失败是应用期硬错误，{@code require = 0}
     * 兜不住，TACZ 一改名就变成启动崩溃），所以改成运行时反射：解析不到、或调用抛异常时
     * 统一返回 {@code null}（调用方据此当作「没解锁」），代价只是这条联动失效。
     *
     * @param scriptApi TACZ 的 {@code ModernKineticGunScriptAPI} 实例（注入点里的 {@code this}）
     * @return 射手；取不到时 {@code null}
     */
    public static LivingEntity shooterOf(Object scriptApi) {
        if (scriptApi == null || shooterUnavailable) return null;

        Method getter = scriptApiGetShooter;
        if (getter == null) {
            synchronized (TaczCompat.class) {
                if (shooterUnavailable) return null;
                getter = scriptApiGetShooter;
                if (getter == null) {
                    try {
                        getter = scriptApi.getClass().getMethod("getShooter");
                    } catch (Throwable t) {
                        shooterUnavailable = true;
                        return null;
                    }
                    scriptApiGetShooter = getter;
                }
            }
        }

        try {
            Object shooter = getter.invoke(scriptApi);
            return shooter instanceof LivingEntity living ? living : null;
        } catch (Throwable t) {
            return null;
        }
    }

    // ========== 每 tick 的「补满弹匣 / 清空热量」 ==========

    /**
     * 每个玩家 tick 调用一次（由 {@code UtilityHandler} 按固定间隔驱动）。
     * <p>
     * 两条 TACZ 联动都从这里驱动（各自独立判定）。两条都没解锁、或未安装 TACZ 时直接返回：
     * 不产生任何背包扫描开销，也不写任何 NBT。
     */
    public static void onTick(ServerPlayer player, PlayerStats stats) {
        boolean infiniteAmmo = stats.isToggleActive("infinite_ammo");
        boolean noHeat = stats.isToggleActive("no_heat");
        if (!infiniteAmmo && !noHeat) return;
        if (!resolve()) return;

        Inventory inventory = player.getInventory();
        handleHeld(inventory.getSelected(), infiniteAmmo, noHeat);
        for (ItemStack stack : inventory.offhand) {
            handleHeld(stack, infiniteAmmo, noHeat);
        }
    }

    private static void handleHeld(ItemStack stack, boolean infiniteAmmo, boolean noHeat) {
        if (infiniteAmmo) {
            fillMagazine(stack);
        }
        if (noHeat) {
            zeroHeat(stack);
        }
    }

    /** 把一把枪的弹匣补到上限；不是枪、已是满的、或数据读不出来时什么都不做。 */
    private static void fillMagazine(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !gunClass.isInstance(stack.getItem())) return;
        Object gun = stack.getItem();
        try {
            if ((Boolean) useInventoryAmmo.invoke(gun, stack)) {
                // 背包直读（FeedType.INVENTORY，如 M134 那种「使用背包弹容」）的枪没有弹匣概念，
                // 它问的是「背包里有没有对应弹药」—— 走模组自己的「虚拟备弹」通道喂它，见 fillDummyAmmo
                fillDummyAmmo(gun, stack);
                return;
            }

            // 弹匣上限要带上扩容弹匣等配件的影响，只能问 TACZ 自己
            Object scriptApi = scriptApiCtor.newInstance();
            scriptApiSetItem.invoke(scriptApi, stack);
            int max = (Integer) scriptApiGetMaxAmmo.invoke(scriptApi);
            if (max <= 0) return;

            int current = (Integer) getCurrentAmmo.invoke(gun, stack);
            if (current >= max) return;                    // 已经是满的：不重复写 NBT，避免每 tick 标脏同步

            setCurrentAmmo.invoke(gun, stack, max);
        } catch (Throwable ignored) {
            // 枪械数据缺失 / TACZ 版本 API 有出入：跳过这把枪，不影响其它功能
        }
    }

    /**
     * 给<b>背包直读</b>的枪塞「虚拟备弹」（TACZ 自己的机制，NBT 键 {@code DummyAmmo}）。
     * <p>
     * 不加这一步的话，这种枪在背包里没有对应弹药时<b>直接打不响</b>：它不走弹匣，
     * 只问「背包里有没有这种弹」。而 TACZ 的三个判定点 ——
     * {@code ModernKineticGunScriptAPI#hasAmmoToConsume}（有没有弹可打）、
     * {@code #consumeAmmoFromPlayer}（扣弹）、
     * {@code AbstractGunItem#hasInventoryAmmo}（背包供弹检查，客户端也会问）——
     * 都会在枪上带着虚拟备弹时改走虚拟池，于是「一发实弹都没有」也能一直打，且不扣背包。
     * <p>
     * 值与用法对齐 TACZ 自带命令 {@code /tacz dummy <目标> <数量>}：只调 {@code setDummyAmmoAmount}，
     * 不碰 {@code MaxDummyAmmo}（官方命令也没碰）。
     */
    private static void fillDummyAmmo(Object gun, ItemStack stack) throws ReflectiveOperationException {
        if ((Integer) getDummyAmmoAmount.invoke(gun, stack) >= DUMMY_AMMO) return;   // 够了就不写
        setDummyAmmoAmount.invoke(gun, stack, DUMMY_AMMO);
    }

    /**
     * 把一把枪的热量清零、并解除「过热锁」。
     * <p>
     * 只有带热量数据的枪（机枪那类）才会写：没有热量数据的枪连 NBT 都不碰。
     * 已经是 0 且未锁定时不重复写 —— 否则每 5 tick 都会把物品标脏、反复同步给客户端。
     * <p>
     * 这里负责的是「本来就热了 / 被锁了」的状态（装上属性、或枪是捡来的），
     * 击发时新产生的热量由注入点直接掐掉（见 {@code mixin/TaczGunScriptApiMixin}），
     * 所以 HUD 上的热量条不会有「先涨后掉」的闪动。
     */
    private static void zeroHeat(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !gunClass.isInstance(stack.getItem())) return;
        try {
            if (!(Boolean) hasHeatData.invoke(stack.getItem(), stack)) return;

            Object gun = stack.getItem();
            if ((Float) getHeatAmount.invoke(gun, stack) > 0f) {
                setHeatAmount.invoke(gun, stack, 0f);
            }
            if ((Boolean) isOverheatLocked.invoke(gun, stack)) {
                setOverheatLocked.invoke(gun, stack, false);
            }
        } catch (Throwable ignored) {
            // 同上：读不出来就跳过这把枪
        }
    }

    // ========== 反射解析 ==========

    private static boolean resolve() {
        if (unavailable) return false;
        if (gunClass != null) return true;

        synchronized (TaczCompat.class) {
            if (unavailable) return false;
            if (gunClass != null) return true;
            try {
                Class<?> gun = Class.forName(GUN_CLASS);
                Class<?> scriptApi = Class.forName(SCRIPT_API_CLASS);

                getCurrentAmmo = gun.getMethod("getCurrentAmmoCount", ItemStack.class);
                setCurrentAmmo = gun.getMethod("setCurrentAmmoCount", ItemStack.class, int.class);
                useInventoryAmmo = gun.getMethod("useInventoryAmmo", ItemStack.class);
                getDummyAmmoAmount = gun.getMethod("getDummyAmmoAmount", ItemStack.class);
                setDummyAmmoAmount = gun.getMethod("setDummyAmmoAmount", ItemStack.class, int.class);
                hasHeatData = gun.getMethod("hasHeatData", ItemStack.class);
                getHeatAmount = gun.getMethod("getHeatAmount", ItemStack.class);
                setHeatAmount = gun.getMethod("setHeatAmount", ItemStack.class, float.class);
                isOverheatLocked = gun.getMethod("isOverheatLocked", ItemStack.class);
                setOverheatLocked = gun.getMethod("setOverheatLocked", ItemStack.class, boolean.class);
                scriptApiCtor = scriptApi.getDeclaredConstructor();
                scriptApiSetItem = scriptApi.getMethod("setItemStack", ItemStack.class);
                scriptApiGetMaxAmmo = scriptApi.getMethod("getMaxAmmoCount");

                gunClass = gun;
                return true;
            } catch (Throwable t) {
                unavailable = true;
                return false;
            }
        }
    }
}
