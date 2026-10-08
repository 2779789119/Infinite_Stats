package com.infinitestats.compat;

import com.infinitestats.stats.PlayerStats;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.energy.IEnergyStorage;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Superb Warfare（卓越前线）联动支撑：弹匣补满 + 热量清零 + 载具电量 / 血量 + 每 tick 兜底。
 * <p>
 * 三条枪械属性在该模组上的分工：
 * <ul>
 *   <li><b>无限子弹（弹匣恒满）</b>：
 *       <ul>
 *         <li>备弹侧：{@code mixin/SbwGunDataMixin} 把 {@code GunData.hasInfiniteBackupAmmo(Entity)}
 *             置为 true —— 这是模组<b>自带</b>的无限弹药通道（创造模式、创造弹药盒、各类弹药策略最终都问它），
 *             保证「射击永远不被弹药卡住、换弹也不消耗任何真实弹药」；</li>
 *         <li>弹匣侧：本类把弹匣直接写回容量上限（见 {@link #fillMagazine(Object)}）——
 *             只看备弹的话会出现「能一直打，但弹匣照常掉空、游戏仍要求你换弹」。</li>
 *         <li>补弹的三层收口（越靠前越及时，全部幂等、可叠加）：
 *             <ol>
 *               <li><b>收口 A（全枪型）</b>：{@code SbwGunDataMixin} 挂在 {@code GunData.shoot(...)}
 *                   各入口的 RETURN 上 —— 它内部就是 {@code item.shoot(...)}，所以连 Igla / Javelin /
 *                   Bocek / Ql1031 / Sentinel / Taser 这几把<b>自己覆写了 {@code shoot} / {@code afterShoot}
 *                   且覆写里不回 {@code super}</b> 的特殊枪也覆盖；</li>
 *               <li><b>收口 B</b>：{@code SbwGunItemMixin} 挂在 {@code GunItem.shoot(ShootParameters)} 与
 *                   {@code GunItem.afterShoot(ShootParameters)} 的 RETURN 上，兜「直接调 item、
 *                   不经过 {@code GunData.shoot(...)}」的路径；</li>
 *               <li><b>每 5 tick 兜底</b>：{@link #onTick(ServerPlayer, PlayerStats)} 扫手持两格，
 *                   管「捡来的 / 属性解锁前就已经半匣」的枪；同时扫玩家所在<b>载具</b>的每个武器
 *                   （载具武器不在物品栏里，而空弹匣连 {@code canShoot} 都过不了、进不了开火链路 ——
 *                   只有这一趟能兜住「必须先手动塞一发」的问题），并把该车的<b>干扰弹</b>也顶满。</li>
 *             </ol>
 *         </li>
 *       </ul>
 *   </li>
 *   <li><b>零热量</b>：击发产生的热量由 {@code mixin/SbwGunItemMixin} 在开火流程返回时清掉
 *       （同一 tick 内，HUD 看不到升温）；本类同时负责兜底 —— 把「属性解锁之前 / 捡来时就已经热了、
 *       甚至已经被过热锁死」的枪拉回 0。</li>
 *   <li><b>无后坐力</b>：客户端那一半由 {@code mixin/SbwClientEventHandlerMixin} 负责 —— 它<b>只吞掉</b>
 *       {@code handleGunRecoil}（后坐力）与 {@code handleWeaponBreathSway}（呼吸 / 瞄准随机晃动）对
 *       {@code LocalPlayer} 朝向的写入，方法本身照常跑（后坐力状态量该衰减还是要衰减）；
 *       这里只提供 {@link #zeroFireCameraOffset()}，给开火时的相机偏移用。</li>
 *   <li><b>无限能源</b>：载具扣电由 {@code mixin/SbwVehicleEnergyStorageMixin} 拦载具电量存储的
 *       {@code extractEnergy(...)}（载具引擎耗电与能量弹药武器都走它，拦一处即全覆盖）；
 *       通用 FE 充能（背包 / 饰品 / 骑乘实体，覆盖 RF / Mekanism / 热力 / EIO / 沉浸工程等）在
 *       {@link EnergyCompat} 里，这里不参与。</li>
 *   <li><b>无限载具血量</b>：{@code mixin/SbwVehicleMixin} 用 {@code @ModifyVariable} 把载具
 *       {@code setHealth(float)} 的入参改成满血（该方法是载具血量的唯一写入口）。</li>
 * </ul>
 * <p>
 * 载具那两条的判定口径与上面三条一致（{@link CompatToggles#isActiveForShooter(Entity, String)}）：
 * <b>该载具的乘员里有解锁者</b>即生效 —— 载具自己不可能是玩家，而且这两条也只在
 * 「玩家正坐着这台载具」时才有意义。载具句柄与枪械句柄<b>各自独立解析</b>
 * （见 {@link #resolveVehicle()} 与 {@link #vehicleOfEnergyStorage(Object)}）：
 * 一边的版本对不上只让那一边退化成原版行为，不会互相拖累。
 * <p>
 * 为什么直接写弹匣、而不调用模组的 {@code GunData.reloadAmmo(...)}：后者是<b>换弹收尾</b>函数，
 * 顺手还会改换弹状态机（{@code reload.setState(NOT_RELOADING)}、{@code bolt}、{@code fireIndex.reset()}、
 * {@code nbtVersion.invalidateStructural()}）—— 拿它当补弹工具会打乱换弹动画 / 拉栓状态。
 * 而容量属性 {@code GunData.get(GunProp.MAGAZINE)} 是模组自己的口径（扩容弹匣、Perk 修正都已算进去，
 * 它的 {@code reloadAmmo} 用的也是同一个属性），直接写这个数既准确又无副作用。
 * <p>
 * 全程<b>反射</b>（和 {@code TaczCompat} / {@code ApotheosisEnchantCompat} 同一套思路）：Superb Warfare
 * 只是可选运行时联动，本模组不声明对它的编译期依赖（编译期只借用 {@code src/compatApiStub} 里的空壳）。
 * 没装时 {@code Class.forName} 直接失败并永久短路、零开销；装了但版本对不上时只关掉对应的一半，
 * 其余功能照常（弹匣句柄单独解析，见 {@link #resolve()}）。
 * <p>
 * 反射到的成员（已用 {@code javap} 对着 {@code superbwarfare-0.8.9.2} 核对）：
 * {@code GunData.DATA_CACHE}（{@code LoadingCache<ItemStack, GunData>}）、
 * {@code GunProp.MAGAZINE}（public static 属性对象）、{@code GunData.get(GunProp)}、
 * {@code GunData.ammo}（{@code IntValue}）、{@code GunData.heat}（{@code DoubleValue}）、
 * {@code GunData.overHeat}（{@code BooleanValue}），{@code ShootParameters} 的
 * {@code data} / {@code shooter} / {@code ammoSupplier} 字段，
 * {@code VehicleEntity} 的 {@code getHealth()} / {@code setHealth(float)} / {@code getMaxHealth()}，
 * 以及 {@code VehicleEnergyStorage#getVehicle()}。
 */
public final class SuperbWarfareCompat {

    /** 枪械运行时数据类。 */
    private static final String GUN_DATA_CLASS = "com.atsuishio.superbwarfare.data.gun.GunData";
    /** 枪械属性表（容量等属性都是它的 public static 字段）。 */
    private static final String GUN_PROP_CLASS = "com.atsuishio.superbwarfare.data.gun.GunProp";
    /** 开火参数（Kotlin data class，编译产物是 Record）。 */
    private static final String SHOOT_PARAMS_CLASS = "com.atsuishio.superbwarfare.data.gun.ShootParameters";
    /** 枪械物品基类（用来判断一个 ItemStack 是不是枪）。 */
    private static final String GUN_ITEM_CLASS = "com.atsuishio.superbwarfare.item.gun.GunItem";
    /** 客户端相机 / 视角处理器（纯客户端类，只在客户端解析）。 */
    private static final String CLIENT_EVENT_HANDLER_CLASS = "com.atsuishio.superbwarfare.event.ClientEventHandler";
    /** 载具基类（Kotlin；真实实现 {@code extends Entity}，所以载具不是 LivingEntity）。 */
    private static final String VEHICLE_CLASS_NAME = "com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity";

    /** 「无限能源」（通用 FE，见 {@link EnergyCompat}；这里只用来判载具扣电那一半）。 */
    private static final String STAT_INFINITE_ENERGY = EnergyCompat.STAT_ID;
    /** 「无限载具血量」。 */
    private static final String STAT_VEHICLE_HEALTH = "sbw_infinite_health";

    private static final String FIELD_DATA_CACHE = "DATA_CACHE";
    private static final String FIELD_SHOOT_PARAMS_DATA = "data";
    private static final String FIELD_SHOOT_PARAMS_SHOOTER = "shooter";
    /** 供弹者：手持枪械时就是射手本人；载具武器时是炮手（而 {@code shooter} 是载具本体）。 */
    private static final String FIELD_SHOOT_PARAMS_AMMO_SUPPLIER = "ammoSupplier";
    private static final String FIELD_AMMO = "ammo";
    private static final String FIELD_HEAT = "heat";
    private static final String FIELD_OVER_HEAT = "overHeat";
    /** 弹匣容量属性：{@code GunProp.MAGAZINE}。 */
    private static final String FIELD_MAGAZINE_PROP = "MAGAZINE";
    /** 客户端的相机角度偏移数组（开火后坐会写它）。 */
    private static final String FIELD_CAMERA_ROT = "cameraRot";
    /** {@code GunData.get(GunProp)} —— 取某个属性「算完修正之后」的最终值。 */
    private static final String METHOD_GET_PROP = "get";
    /** {@code VehicleEnergyStorage.getVehicle()} —— 从电量存储反查它属于哪台载具。 */
    private static final String METHOD_GET_VEHICLE = "getVehicle";
    /** {@code VehicleEntity.getGunDataMap()} —— 载具每个武器一份的 {@code GunData} 表。 */
    private static final String METHOD_GET_GUN_DATA_MAP = "getGunDataMap";
    /** {@code VehicleEntity#computed()} —— 载具「算完修正」的数据，干扰弹弹匣容量在里面。 */
    private static final String METHOD_COMPUTED = "computed";
    /** {@code DefaultVehicleData#getDecoyMagazineSize()} —— 干扰弹弹匣容量。 */
    private static final String METHOD_GET_DECOY_MAGAZINE_SIZE = "getDecoyMagazineSize";
    /** {@code VehicleEntity#getDecoyCount()} —— 已经装填好的干扰弹数。 */
    private static final String METHOD_GET_DECOY_COUNT = "getDecoyCount";
    /** {@code VehicleEntity#setDecoyCount(int)}。 */
    private static final String METHOD_SET_DECOY_COUNT = "setDecoyCount";

    /** Superb Warfare 是否已确认不可用（解析失败后不再重复尝试）。 */
    private static volatile boolean unavailable;

    /** 载具句柄是否已确认不可用（与枪械那套各自独立，互不拖累）。 */
    private static volatile boolean vehicleUnavailable;
    /** 「电量存储 → 载具」是否已确认取不到（版本对不上时只让电量那一半失效）。 */
    private static volatile boolean energyStorageVehicleUnavailable;

    /** 「开火时的相机偏移」静态数组字段（纯客户端类，懒解析；失败后不再尝试）。 */
    private static volatile Field cameraRot;
    private static volatile boolean cameraRotUnavailable;

    // —— 反射句柄（解析成功后一直复用）——
    private static Class<?> gunItemClass;
    private static Object dataCache;
    private static Method cacheGetUnchecked;
    private static Field shootParamsData;
    private static Field shootParamsShooter;
    private static Field shootParamsAmmoSupplier;
    private static Method gunDataGetByProp;
    private static Object magazineProp;
    private static Field gunDataAmmo;
    private static Method intValueGet;
    private static Method intValueSet;
    private static Field gunDataHeat;
    private static Field gunDataOverHeat;
    private static Method doubleValueGet;
    private static Method doubleValueSet;
    private static Method booleanValueGet;
    private static Method booleanValueSet;

    // —— 载具的反射句柄（与枪械那套分开解析，见 resolveVehicle()）——
    private static Class<?> vehicleClass;
    private static Method vehicleGetHealth;
    private static Method vehicleSetHealth;
    private static Method vehicleGetMaxHealth;
    /** {@code VehicleEntity#getGunDataMap()}：载具武器表（拿不到只让「载具补弹」失效）。 */
    private static volatile Method vehicleGetGunDataMap;
    private static volatile boolean gunDataMapUnavailable;
    /** 干扰弹（随「无限子弹」一起无限）：容量 + 已装填数（拿不到只让干扰弹那一半失效）。 */
    private static volatile Method vehicleComputed;
    private static volatile Method decoyMagazineSizeGet;
    private static volatile Method decoyCountGet;
    private static volatile Method decoyCountSet;
    private static volatile boolean decoyUnavailable;
    /** {@code VehicleEnergyStorage#getVehicle()}（protected final，要 setAccessible）。 */
    private static volatile Method energyStorageGetVehicle;

    private SuperbWarfareCompat() {}

    // ========== 供 Mixin 调用 ==========

    /**
     * 开火收尾：把这一发扣掉的弹匣子弹补回去，并把这一发攒下的热量清零。
     * <p>
     * 直接接 {@code GunData} 与射手（收口 A，见类注释）：{@code SbwGunDataMixin} 挂在
     * {@code GunData.shoot(...)} 的几个入口的 RETURN 上 —— 那几个入口内部就是 {@code item.shoot(...)}，
     * 所以这里返回时整条开火链路已经跑完，<b>不挑枪型</b>（连自己覆写了 {@code shoot} / {@code afterShoot}、
     * 覆写里又不回 {@code super} 的特殊枪也覆盖）。
     * <p>
     * 放在开火链路内部（而不是每 tick 事后补）是为了让 HUD 与射击逻辑<b>在同一 tick 内</b>始终看到满匣 /
     * 零热量 —— 否则弹匣会按射速往下掉、还可能触发模组自己的「该换弹了」判断。
     *
     * @param shooter 射击方：手持枪械时是玩家；<b>载具武器时是载具实体</b>（炮手在
     *                {@code ShootParameters.ammoSupplier} 里，见 {@link #onShootParams(Object)}），
     *                所以这里走 {@link CompatToggles#isActiveForShooter(Entity, String)} ——
     *                本体不是玩家时再看乘员；AI 用枪（怪物）两条属性都不生效，它们是玩家属性
     * @param gunData 该枪的运行时数据（由 Mixin 把目标实例直接传进来，不再走反射取字段）
     */
    public static void onGunFired(Entity shooter, Object gunData) {
        if (gunData == null || shooter == null) return;

        boolean infiniteAmmo = CompatToggles.isActiveForShooter(shooter, "infinite_ammo");
        boolean noHeat = CompatToggles.isActiveForShooter(shooter, "no_heat");
        if (!infiniteAmmo && !noHeat) return;
        if (!resolve()) return;

        try {
            if (infiniteAmmo) {
                fillMagazine(gunData);
            }
            if (noHeat) {
                writeZero(gunData);
            }
        } catch (Throwable ignored) {
            // 版本对不上 / 字段消失：跳过，不影响射击本身
        }
    }

    /**
     * 开火收尾（收口 B）：参数包在 {@code ShootParameters} 里时用它 ——
     * {@code SbwGunDataMixin} 挂在 {@code GunData.shoot(ShootParameters)} 的 RETURN 上
     * （<b>载具开火也走这个重载</b>），{@code SbwGunItemMixin} 挂在
     * {@code GunItem.shoot(ShootParameters)} 与 {@code GunItem.afterShoot(ShootParameters)} 的 RETURN 上，
     * 兜「绕过 {@code GunData.shoot(...)} 直接调 item」的路径（两个都挂是为了不依赖它们的先后顺序）。
     * <p>
     * 已核对 {@code ShootParameters} 有 {@code shooter} / {@code ammoSupplier} / {@code data} 三个
     * public final 字段，所以这里反射取一次再交给 {@link #onGunFired(Entity, Object)}。
     * <p>
     * <b>载具的坑</b>：载具开火时 {@code shooter} 是<b>载具本体</b>（已核对
     * {@code VehicleEntity.getAmmoSupplier()} 就是 {@code return this}），真炮手在 {@code ammoSupplier} 里 ——
     * 所以优先把「活体那一个」交出去，按玩家判定最准，拿不到活体时才退回载具（由乘员兜底判定）。
     */
    public static void onShootParams(Object shootParameters) {
        if (shootParameters == null) return;
        if (!resolve()) return;
        try {
            Object gunData = shootParamsData.get(shootParameters);
            Object shooterField = shootParamsShooter.get(shootParameters);
            Object ammoField = shootParamsAmmoSupplier.get(shootParameters);

            Entity shooter = shooterField instanceof Entity entity ? entity : null;
            Entity ammoSupplier = ammoField instanceof Entity entity ? entity : null;

            Entity responsible = shooter instanceof LivingEntity ? shooter
                    : ammoSupplier instanceof LivingEntity ? ammoSupplier
                    : shooter != null ? shooter : ammoSupplier;
            if (responsible == null) return;

            onGunFired(responsible, gunData);
        } catch (Throwable ignored) {
            // 版本对不上 / 字段消失：跳过，不影响射击本身
        }
    }

    // ========== 载具：电量与血量 ==========

    /**
     * 载具血量注入点（{@code VehicleEntity#setHealth(float)} 的 {@code @ModifyVariable}）：
     * 解锁「无限载具血量」时，无论谁要把血量写成多少，一律改成<b>满血</b>。
     * <p>
     * 为什么改入参而不是取消这次写入：取消只能「不写」，血量会停在旧值上；改入参则读出来的、
     * HUD 显示的、它自己的低血提示 / 冒烟特效自然全都是满血。也没必要担心递归 ——
     * 这里只反射读 {@code getMaxHealth()}，<b>不回写</b>血量。
     *
     * @param vehicle 载具实例（由 Mixin 把 {@code this} 直接传进来）
     * @param health  这次本来要写入的血量
     * @return 实际写入的血量（未解锁 / 取不到上限时原样返回）
     */
    public static float forceFullVehicleHealth(Object vehicle, float health) {
        if (!(vehicle instanceof Entity entity)) return health;
        if (!resolveVehicle() || !vehicleClass.isInstance(entity)) return health;
        if (!CompatToggles.isActiveForShooter(entity, STAT_VEHICLE_HEALTH)) return health;

        float max = maxHealthOf(vehicle);
        return max > 0f ? max : health;
    }

    /**
     * 载具电量注入点（{@code VehicleEnergyStorage#extractEnergy(int, boolean)}）：
     * 解锁「无限能源」时不让载具扣电。
     * <p>
     * 返回「本该抽到的量」而不是 0：调用方据此认为这次抽电成功，
     * 载具自己的「够不够电」判断（引擎 / 能量弹药武器）继续放行，电量却一点没少。
     * 上限按承载量取 {@code min}，好让「抽电」这一步在读数上依旧是自洽的。
     *
     * @param energyStorage 载具的电量存储（由 Mixin 把 {@code this} 直接传进来）
     * @param maxExtract    这次最多想抽多少
     * @return 应当返回给调用方的抽取量；{@code null} 表示不拦截（照常执行原逻辑）
     */
    public static Integer interceptVehicleExtract(Object energyStorage, int maxExtract) {
        Entity vehicle = vehicleOfEnergyStorage(energyStorage);
        if (vehicle == null) return null;
        if (!CompatToggles.isActiveForShooter(vehicle, STAT_INFINITE_ENERGY)) return null;

        int wanted = Math.max(maxExtract, 0);
        int stored = energyStorage instanceof IEnergyStorage storage
                ? Math.max(storage.getEnergyStored(), 0)
                : wanted;
        return Math.min(wanted, stored);
    }

    // ========== 每 tick 的兜底 ==========

    /**
     * 每个玩家 tick 调用一次（由 {@code UtilityHandler} 按固定间隔驱动）。
     * <p>
     * 兜底几件事，各自独立判定、按需短路：
     * <ul>
     *   <li>手持枪械：捡来的 / 属性解锁前就已经半匣的枪补满，已经热了 / 被过热锁住的枪清零；</li>
     *   <li><b>载具武器</b>：正坐着的那台车的每个武器同样补满 / 清热量 ——
     *       载具武器不在物品栏里，只有这里能管到「弹匣本来就是空的」那种情况（见 {@link #handleVehicleWeapons}）；
     *       顺带把该车的<b>干扰弹</b>也顶满（见 {@link #topUpVehicleDecoys}，跟随「无限子弹」）；</li>
     *   <li>载具血量：补满血 —— 注入点只保证「不再变少」，
     *       保证不了「属性解锁之前就已经掉了血」的那台。载具的<b>电量</b>不在这里管：
     *       由 {@link EnergyCompat} 统一给「骑乘实体 + 背包 + 饰品」的 FE 能量源补满。</li>
     * </ul>
     * 全部没解锁、或没装 Superb Warfare 时直接返回，不产生任何开销。
     */
    public static void onTick(ServerPlayer player, PlayerStats stats) {
        boolean infiniteAmmo = stats.isToggleActive("infinite_ammo");
        boolean noHeat = stats.isToggleActive("no_heat");
        boolean vehicleHealth = stats.isToggleActive(STAT_VEHICLE_HEALTH);

        if (infiniteAmmo || noHeat) {
            if (resolve()) {
                Inventory inventory = player.getInventory();
                handleHeld(inventory.getSelected(), infiniteAmmo, noHeat);
                for (ItemStack stack : inventory.offhand) {
                    handleHeld(stack, infiniteAmmo, noHeat);
                }
            }
            if (resolveVehicle()) {
                Entity ridden = riddenVehicle(player);
                handleVehicleWeapons(ridden, infiniteAmmo, noHeat);
                if (infiniteAmmo) {
                    topUpVehicleDecoys(ridden);
                }
            }
        }

        if (!vehicleHealth) return;
        if (!resolveVehicle()) return;

        Entity vehicle = riddenVehicle(player);
        if (vehicle == null) return;
        topUpVehicleHealth(vehicle);
    }

    /** 玩家正乘坐的载具；没坐 / 坐的不是载具时返回 null。 */
    private static Entity riddenVehicle(ServerPlayer player) {
        Entity vehicle = player.getVehicle();
        return vehicle != null && vehicleClass.isInstance(vehicle) ? vehicle : null;
    }

    /**
     * 把玩家所在载具的<b>每个武器</b>都补满（并顺带清热量）。
     * <p>
     * 为什么非得单独走这一趟：载具武器不在物品栏里 —— 每个武器一份 {@code GunData} 挂在载具的
     * （同步）{@code GUN_DATA_MAP} 上，而开火收尾那几个注入点只有「已经打过一发」之后才会被触发；
     * <b>空弹匣连 {@code canShoot} 都过不了</b>，压根进不了开火链路 —— 表现就是
     * 「必须先手动塞一发弹药才能打」。这里在每 5 tick 的兜底里先把弹匣顶满，没有这个启动问题。
     */
    private static void handleVehicleWeapons(Entity vehicle, boolean infiniteAmmo, boolean noHeat) {
        if (vehicle == null) return;
        resolveVehicleGunDataMap();
        if (vehicleGetGunDataMap == null) return;
        try {
            if (!(vehicleGetGunDataMap.invoke(vehicle) instanceof Map<?, ?> gunDataMap)) return;
            for (Object gunData : gunDataMap.values()) {
                if (gunData == null) continue;
                if (infiniteAmmo) {
                    fillMagazine(gunData);
                }
                if (noHeat) {
                    clearHeatIfNeeded(gunData);
                }
            }
        } catch (Throwable ignored) {
            // 版本对不上 / 这台车没有武器表：这次跳过
        }
    }

    private static void handleHeld(ItemStack stack, boolean infiniteAmmo, boolean noHeat) {
        if (stack == null || stack.isEmpty() || !gunItemClass.isInstance(stack.getItem())) return;
        try {
            Object gunData = cacheGetUnchecked.invoke(dataCache, stack);
            if (gunData == null) return;
            if (infiniteAmmo) {
                fillMagazine(gunData);
            }
            if (noHeat) {
                clearHeatIfNeeded(gunData);
            }
        } catch (Throwable ignored) {
            // 非枪物品 / 数据缺失：跳过这把枪
        }
    }

    /**
     * 把玩家所在载具的<b>干扰弹</b>顶到弹匣容量（随「无限子弹」一起无限）。
     * <p>
     * 载具干扰弹是「装填数 + 补充冷却」两段：{@code VehicleWeaponUtils#reloadDecoy} 会拿车上的
     * {@code FLYING_FLARE_AMMO} 物品去装填，装填量 = {@code min(携带量, computed().decoyMagazineSize)}，
     * 发射时扣的是 {@code DECOY_COUNT}。所以只要每 5 tick 把 {@code DECOY_COUNT} 顶满，
     * 就既不用补弹、也不会真吃玩家的干扰弹物品（发射永远不解渴）。
     * <p>
     * <b>不动</b>携带量 {@code DECOY_ITEM_COUNT}：它由载具自己的 {@code tick()} 每 tick 从乘员背包重算，
     * 写了也会被覆盖 —— HUD 上那个「携带量」读数照旧，只有「已装填」恒满。
     * 补充冷却（{@code DECOY_RELOAD_COOLDOWN}）同样不碰：那是射速限制，不是弹药。
     */
    private static void topUpVehicleDecoys(Entity vehicle) {
        if (vehicle == null) return;
        if (!resolveVehicleDecoy()) return;
        try {
            Object data = vehicleComputed.invoke(vehicle);
            if (data == null) return;
            int capacity = (Integer) decoyMagazineSizeGet.invoke(data);
            if (capacity <= 0) return;
            if ((Integer) decoyCountGet.invoke(vehicle) < capacity) {
                decoyCountSet.invoke(vehicle, capacity);
            }
        } catch (Throwable ignored) {
            // 版本对不上 / 这台车没有干扰弹：这次跳过
        }
    }

    // ========== 弹匣 ==========

    /**
     * 把弹匣写回容量上限；已经是满的就不写（避免每发都标脏物品、反复同步给客户端）。
     * <p>
     * 容量走 {@code GunData.get(GunProp.MAGAZINE)} —— 模组自己的算法，扩容弹匣 / Perk 等修正都已经算进去。
     * 若该属性在这次运行的版本里取不到（{@code magazineUnavailable}），这里静默跳过：
     * 「无限子弹」会退化成「备弹无限但弹匣照常掉」（即接管 {@code hasInfiniteBackupAmmo} 那一半的效果），
     * 其余功能不受影响。
     */
    private static void fillMagazine(Object gunData) throws ReflectiveOperationException {
        if (gunData == null || magazineProp == null) return;

        Object capacityValue = gunDataGetByProp.invoke(gunData, magazineProp);
        if (!(capacityValue instanceof Number)) return;
        int capacity = ((Number) capacityValue).intValue();
        if (capacity <= 0) return;

        Object ammo = gunDataAmmo.get(gunData);
        if (ammo == null) return;
        int current = (Integer) intValueGet.invoke(ammo);
        if (current >= capacity) return;

        intValueSet.invoke(ammo, capacity);
    }

    // ========== 热量 ==========

    /** 枪已经热了 / 被锁了才写 NBT；已经是 0 且未锁定则什么都不做。 */
    private static void clearHeatIfNeeded(Object gunData) {
        if (gunData == null) return;
        try {
            Object heat = gunDataHeat.get(gunData);
            Object overHeat = gunDataOverHeat.get(gunData);
            if (heat != null && (Double) doubleValueGet.invoke(heat) > 0d) {
                doubleValueSet.invoke(heat, 0d);
            }
            if (overHeat != null && (Boolean) booleanValueGet.invoke(overHeat)) {
                booleanValueSet.invoke(overHeat, false);
            }
        } catch (Throwable ignored) {
            // 数据缺失：跳过
        }
    }

    /** 无条件把热量与过热标记写成 0（开火后调用，此时必然是刚加过热的）。 */
    private static void writeZero(Object gunData) throws ReflectiveOperationException {
        if (gunData == null) return;
        doubleValueSet.invoke(gunDataHeat.get(gunData), 0d);
        booleanValueSet.invoke(gunDataOverHeat.get(gunData), false);
    }

    // ========== 载具的兜底补满 ==========

    /**
     * 把已经掉过血的载具补满。
     * <p>
     * 注入点拦住的是「以后不再掉」，这里管的是「解锁之前就已经掉了」的那台。
     * 已经是满血就不写（避免每 tick 标脏同步数据、反复推给客户端）。
     */
    private static void topUpVehicleHealth(Entity vehicle) {
        float max = maxHealthOf(vehicle);
        if (max <= 0f) return;
        try {
            if ((Float) vehicleGetHealth.invoke(vehicle) < max) {
                vehicleSetHealth.invoke(vehicle, max);
            }
        } catch (Throwable ignored) {
            // 版本对不上：这次跳过
        }
    }

    /** 反射读载具血量上限；取不到（或非正数）返回 0，调用方据此跳过。 */
    private static float maxHealthOf(Object vehicle) {
        try {
            float max = (Float) vehicleGetMaxHealth.invoke(vehicle);
            return max > 0f ? max : 0f;
        } catch (Throwable t) {
            return 0f;
        }
    }

    /**
     * 从载具的电量存储反查它属于哪台载具。
     * <p>
     * 存储本身没有实体引用（只有载具的同步数据），但它是 Kotlin 的 {@code protected var vehicle}
     * —— 编译产物带了 {@code protected final VehicleEntity getVehicle()}，反射取它即可。
     * 这个句柄<b>单独懒解析</b>：拿不到就只让「无限能源」的载具扣电那半边变成空操作，
     * 载具血量与其余三条枪械属性都不受影响。
     */
    private static Entity vehicleOfEnergyStorage(Object energyStorage) {
        if (energyStorage == null) return null;

        Method getter = energyStorageGetVehicle;
        if (getter == null) {
            if (energyStorageVehicleUnavailable) return null;
            synchronized (SuperbWarfareCompat.class) {
                getter = energyStorageGetVehicle;
                if (getter == null) {
                    if (energyStorageVehicleUnavailable) return null;
                    try {
                        getter = energyStorage.getClass().getDeclaredMethod(METHOD_GET_VEHICLE);
                        // Kotlin 的 protected var 编译成 protected 方法，反射要显式放开
                        getter.setAccessible(true);
                    } catch (Throwable t) {
                        energyStorageVehicleUnavailable = true;
                        return null;
                    }
                    energyStorageGetVehicle = getter;
                }
            }
        }

        try {
            return getter.invoke(energyStorage) instanceof Entity entity ? entity : null;
        } catch (Throwable t) {
            return null;
        }
    }

    // ========== 开火时的相机偏移 ==========

    /**
     * 把开火造成的相机偏移（{@code ClientEventHandler.cameraRot[2]}）清零。
     * <p>
     * 由客户端 Mixin 在 {@code handlePlayerCamera} 的 HEAD 每帧调一次（只在无后坐力解锁时）。
     * 那个字段在模组里是 {@code public static double[]}：
     * {@code handleWeaponFire} 每帧把第 3 个分量往开火目标值 lerp，{@code handlePlayerCamera}
     * 再把它加到相机角度上 —— 清零即「开火时相机不动」。索引 2 之外的分量模组自己不写（始终为 0），
     * 所以只清这一个、不去动整条数组，免得误伤别的相机逻辑。
     * <p>
     * 这个字段属于<b>纯客户端类</b>（专用服务器上根本加载不了），所以它<b>单独懒解析</b>、
     * 失败也不影响其它功能（与 {@link #resolveMagazine(Class)} 同一套容错思路）。
     */
    public static void zeroFireCameraOffset() {
        if (cameraRot == null) {
            if (cameraRotUnavailable) return;
            synchronized (SuperbWarfareCompat.class) {
                if (cameraRot == null) {
                    if (cameraRotUnavailable) return;
                    try {
                        cameraRot = Class.forName(CLIENT_EVENT_HANDLER_CLASS).getField(FIELD_CAMERA_ROT);
                    } catch (Throwable t) {
                        cameraRotUnavailable = true;
                        return;
                    }
                }
            }
        }
        try {
            Object value = cameraRot.get(null);
            if (value instanceof double[] offsets && offsets.length > 2) {
                offsets[2] = 0d;
            }
        } catch (Throwable ignored) {
            // 版本对不上：这次跳过
        }
    }

    // ========== 反射解析 ==========

    private static boolean resolve() {
        if (unavailable) return false;
        if (dataCache != null) return true;

        synchronized (SuperbWarfareCompat.class) {
            if (unavailable) return false;
            if (dataCache != null) return true;
            try {
                Class<?> gunData = Class.forName(GUN_DATA_CLASS);
                Class<?> shootParams = Class.forName(SHOOT_PARAMS_CLASS);
                gunItemClass = Class.forName(GUN_ITEM_CLASS);

                // GunData 自己维护的「ItemStack → GunData」缓存：拿它取出某把枪的运行时数据
                dataCache = gunData.getField(FIELD_DATA_CACHE).get(null);
                cacheGetUnchecked = findGetUnchecked(dataCache.getClass());

                shootParamsData = shootParams.getField(FIELD_SHOOT_PARAMS_DATA);
                shootParamsShooter = shootParams.getField(FIELD_SHOOT_PARAMS_SHOOTER);
                shootParamsAmmoSupplier = shootParams.getField(FIELD_SHOOT_PARAMS_AMMO_SUPPLIER);

                gunDataHeat = gunData.getField(FIELD_HEAT);
                gunDataOverHeat = gunData.getField(FIELD_OVER_HEAT);
                doubleValueGet = gunDataHeat.getType().getMethod("get");
                doubleValueSet = gunDataHeat.getType().getMethod("set", double.class);
                booleanValueGet = gunDataOverHeat.getType().getMethod("get");
                booleanValueSet = gunDataOverHeat.getType().getMethod("set", boolean.class);

                resolveMagazine(gunData);
                return true;
            } catch (Throwable t) {
                unavailable = true;
                return false;
            }
        }
    }

    /**
     * 载具句柄<b>单独解析</b>：与枪械那套完全独立 —— 枪械的类名 / 字段变了不会让载具血量失效，
     * 反之亦然。四个成员都在载具基类上，且都是 {@code public}（已核对：
     * {@code getHealth()} / {@code setHealth(float)} / {@code getMaxHealth()} / {@code getGunDataMap()}）。
     * <p>
     * 失败时 {@code vehicleClass} 保持 null：载具血量那两条会退化成「不生效」，
     * 但载具电量那条只依赖 {@code VehicleEnergyStorage#getVehicle()}（另一套懒解析），
     * 不受影响。武器表（{@code getGunDataMap}）再单独一层 try/catch：它取不到只让「载具补弹」失效。
     */
    private static boolean resolveVehicle() {
        if (vehicleUnavailable) return false;
        if (vehicleClass != null) return true;

        synchronized (SuperbWarfareCompat.class) {
            if (vehicleUnavailable) return false;
            if (vehicleClass != null) return true;
            try {
                Class<?> vehicle = Class.forName(VEHICLE_CLASS_NAME);
                vehicleGetHealth = vehicle.getMethod("getHealth");
                vehicleSetHealth = vehicle.getMethod("setHealth", float.class);
                vehicleGetMaxHealth = vehicle.getMethod("getMaxHealth");
                vehicleClass = vehicle;
            } catch (Throwable t) {
                vehicleUnavailable = true;
                return false;
            }
            resolveVehicleGunDataMap();
            return true;
        }
    }

    /**
     * 干扰弹句柄：独立解析，失败只让「干扰弹无限」这一半失效。
     * <p>
     * 容量在载具数据上（{@code VehicleEntity#computed()} → {@code getDecoyMagazineSize()}），
     * 所以先从 {@code computed()} 的返回类型上取容量 getter，再取载具自己的读写方法。
     */
    private static boolean resolveVehicleDecoy() {
        if (decoyCountSet != null) return true;
        if (decoyUnavailable) return false;
        synchronized (SuperbWarfareCompat.class) {
            if (decoyCountSet != null) return true;
            if (decoyUnavailable) return false;
            if (vehicleClass == null) return false;
            try {
                vehicleComputed = vehicleClass.getMethod(METHOD_COMPUTED);
                decoyMagazineSizeGet = vehicleComputed.getReturnType().getMethod(METHOD_GET_DECOY_MAGAZINE_SIZE);
                decoyCountGet = vehicleClass.getMethod(METHOD_GET_DECOY_COUNT);
                decoyCountSet = vehicleClass.getMethod(METHOD_SET_DECOY_COUNT, int.class);
                return true;
            } catch (Throwable t) {
                decoyUnavailable = true;
                return false;
            }
        }
    }

    /** 载具武器表句柄：独立解析，失败只让「每 5 tick 给载具武器补弹」这一半失效。 */
    private static void resolveVehicleGunDataMap() {
        if (vehicleGetGunDataMap != null) return;
        if (gunDataMapUnavailable) return;
        try {
            vehicleGetGunDataMap = vehicleClass.getMethod(METHOD_GET_GUN_DATA_MAP);
        } catch (Throwable t) {
            gunDataMapUnavailable = true;
        }
    }

    /**
     * 弹匣相关的句柄<b>单独解析</b>：这几步失败（比如以后某个版本把 {@code MAGAZINE} 属性收成私有、
     * 或挪了位置）只让「补满弹匣」这一半失效，不能把整条联动拖下水。
     * <p>
     * 失败时 {@code magazineProp} 保持 null，{@link #fillMagazine(Object)} 会直接返回。
     */
    private static void resolveMagazine(Class<?> gunData) {
        try {
            Class<?> gunProp = Class.forName(GUN_PROP_CLASS);
            magazineProp = gunProp.getField(FIELD_MAGAZINE_PROP).get(null);
            gunDataGetByProp = gunData.getMethod(METHOD_GET_PROP, gunProp);

            gunDataAmmo = gunData.getField(FIELD_AMMO);
            Class<?> ammoType = gunDataAmmo.getType();
            intValueGet = ammoType.getMethod("get");
            try {
                intValueSet = ammoType.getMethod("set", int.class);
            } catch (NoSuchMethodException ignored) {
                // Kotlin 里写成 set(Integer) 之类的装箱重载时的兜底
                intValueSet = ammoType.getMethod("set", Integer.class);
            }
        } catch (Throwable t) {
            magazineProp = null;
        }
    }

    /**
     * 找 {@code getUnchecked(Object)}。
     * <p>
     * {@code DATA_CACHE} 的声明类型是 Guava 的 {@code LoadingCache}，但它的运行时实现类是 Guava 内部类，
     * 方法不一定在实现类上直接可见 —— 先在实现类上找，找不到再遍历它实现的接口。
     */
    private static Method findGetUnchecked(Class<?> cacheClass) throws NoSuchMethodException {
        try {
            return cacheClass.getMethod("getUnchecked", Object.class);
        } catch (NoSuchMethodException ignored) {
            // 继续往接口上找
        }
        for (Class<?> iface : cacheClass.getInterfaces()) {
            try {
                return iface.getMethod("getUnchecked", Object.class);
            } catch (NoSuchMethodException ignored) {
                // 看下一个接口
            }
        }
        throw new NoSuchMethodException("getUnchecked not found on " + cacheClass.getName());
    }
}
