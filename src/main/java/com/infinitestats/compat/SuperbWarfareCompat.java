package com.infinitestats.compat;

import com.infinitestats.stats.PlayerStats;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Superb Warfare（卓越前线）联动支撑：弹匣补满 + 热量清零 + 载具电量 / 血量 + 每 tick 兜底。
 * <p>
 * 四条属性在该模组上的分工：
 * <ul>
 *   <li><b>无限子弹（{@code infinite_ammo}）</b>：备弹侧由 {@code SbwGunDataMixin} 把
 *       {@code countBackupAmmo(Entity)} 报成无限、把 {@code consumeBackupAmmo(...)} 整个取消
 *       （前者是所有弹药策略的口径，后者是唯一的实扣入口）——
 *       注意 {@code hasInfiniteBackupAmmo} 只是 HUD / 脚本语义，{@code GunData} 自己从不调用它，
 *       光挂那一条会出现「界面显示 ∞，弹药照样被扣」；弹匣侧由本类把弹匣写回容量上限
 *       （只看备弹会出现「能一直打但弹匣照常掉空、游戏仍要求换弹」）。
 *       补弹有三层收口（越靠前越及时，全部幂等）：{@code GunData.shoot(...)} 各入口的 RETURN
 *       → {@code GunItem.shoot / afterShoot} 的 RETURN → 每 5 tick 的兜底；
 *   <li><b>零热量（{@code no_heat}）</b>：开火流程返回时把 {@code heat} / {@code overHeat} 清零
 *       （同一 tick 内，HUD 看不到升温）；本类的每 tick 兜底负责「解锁之前就已经热了、甚至已被
 *       过热锁死」的枪；
 *   <li><b>无后坐力（{@code no_recoil}）</b>：客户端那半由 {@code SbwClientEventHandlerMixin} 吞掉
 *       对 {@code LocalPlayer} 朝向的写入（后坐力与呼吸晃动的四处理），方法本身照常跑（状态量该衰减
 *       还是要衰减）；载具开火的镜头抖动则拦 {@code GunData.shakePlayers(Entity)}。
 *       本类只提供 {@link #zeroFireCameraOffset()}（开火时的相机偏移清零）；
 *   <li><b>无限能源（{@code infinite_energy}）</b>：载具扣电由
 *       {@code SbwVehicleEnergyStorageMixin} 拦载具电量存储的 {@code extractEnergy(...)}
 *       （引擎耗电与能量弹药武器都走它，拦一处即全覆盖）；通用 FE 充能在 {@link EnergyCompat}；
 *   <li><b>无限载具血量（{@code sbw_infinite_health}）</b>：{@code SbwVehicleMixin} 用
 *       {@code @ModifyVariable} 把 {@code setHealth(float)} 的入参改成满血（该方法是载具血量的
 *       唯一写入口）。改入参而不是取消：取消只能「不写」，血量会停在旧值上；改入参则读数、HUD、
 *       低血提示 / 冒烟特效全都自然跟着满血走。
 * </ul>
 * 载具那两条的判定口径与枪械一致（{@link CompatToggles#isActiveForShooter(Entity, String)}）：
 * <b>该载具的乘员里有解锁者</b>即生效 —— 载具自己不可能是玩家，而且这两条也只在「玩家正坐着这台
 * 载具」时才有意义。载具句柄与枪械句柄、干扰弹句柄、电量句柄<b>各自独立解析</b>：一边版本对不上
 * 只让那一边退化成原版行为，不会互相拖累。
 * <p>
 * 为什么直接写弹匣、而不调用模组的 {@code reloadAmmo(...)}：后者是<b>换弹收尾</b>函数，顺手还会改
 * 换弹状态机（{@code reload.setState(NOT_RELOADING)}、{@code bolt}、{@code fireIndex.reset()}）——
 * 拿它当补弹工具会打乱换弹动画 / 拉栓状态。而容量属性 {@code GunProp.MAGAZINE} 是模组自己的口径
 * （扩容弹匣、Perk 修正都已算进去），直接写这个数既准确又无副作用。
 * <p>
 * 全程<b>反射</b>：卓越前线只是可选运行时联动，本模组不声明对它的编译期依赖（编译期只借用
 * {@code src/compatApiStub} 里的空壳）。没装时 {@code Class.forName} 直接失败并永久短路、零开销。
 * <p>
 * <b>1.21.1 适配</b>：{@code IEnergyStorage} 换到 NeoForge 包
 * （{@code net.neoforged.neoforge.energy}）；客户端 Mixin 里对 {@code LocalPlayer} 的注入目标
 * 从 SRG 名（{@code m_146922_} / {@code m_146926_}）改成 official 名（{@code setYRot} / {@code setXRot}）
 * —— NeoForge 运行在 official 映射上，不再需要 SRG。
 * <p>
 * 反射到的成员（1.20.1 主线已用 {@code javap} 对 {@code superbwarfare-0.8.9.2} 核对）：
 * {@code GunData.DATA_CACHE}（{@code LoadingCache<ItemStack, GunData>}）、{@code GunProp.MAGAZINE}、
 * {@code GunData.get(GunProp)}、{@code GunData.ammo}（{@code IntValue}）、{@code GunData.heat}
 * （{@code DoubleValue}）、{@code GunData.overHeat}（{@code BooleanValue}）、
 * {@code ShootParameters} 的 {@code data} / {@code shooter} / {@code ammoSupplier} 字段、
 * {@code VehicleEntity} 的 {@code getHealth/setHealth/getMaxHealth/getGunDataMap/computed}
 * 与干扰弹的 {@code getDecoyCount/setDecoyCount}、{@code VehicleEnergyStorage#getVehicle()}。
 * <b>本分支尚未拿到卓越前线 1.21.1 的 jar 复核</b>，取不到的成员一律单独短路（只让对应的一半失效）。
 */
public final class SuperbWarfareCompat {

    private static final String GUN_DATA_CLASS = "com.atsuishio.superbwarfare.data.gun.GunData";
    private static final String GUN_PROP_CLASS = "com.atsuishio.superbwarfare.data.gun.GunProp";
    private static final String SHOOT_PARAMS_CLASS = "com.atsuishio.superbwarfare.data.gun.ShootParameters";
    private static final String GUN_ITEM_CLASS = "com.atsuishio.superbwarfare.item.gun.GunItem";
    private static final String CLIENT_EVENT_HANDLER_CLASS = "com.atsuishio.superbwarfare.event.ClientEventHandler";
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
    private static final String FIELD_MAGAZINE_PROP = "MAGAZINE";
    private static final String FIELD_CAMERA_ROT = "cameraRot";
    private static final String METHOD_GET_PROP = "get";
    private static final String METHOD_GET_VEHICLE = "getVehicle";
    private static final String METHOD_GET_GUN_DATA_MAP = "getGunDataMap";
    private static final String METHOD_COMPUTED = "computed";
    private static final String METHOD_GET_DECOY_MAGAZINE_SIZE = "getDecoyMagazineSize";
    /** 干扰弹装填数的同步实体数据 accessor 字段名（1.21.1）。 */
    private static final String FIELD_DECOY_COUNT = "DECOY_COUNT";

    /** Superb Warfare 是否已确认不可用（解析失败后不再重复尝试）。 */
    private static volatile boolean unavailable;

    private static volatile boolean vehicleUnavailable;
    private static volatile boolean energyStorageVehicleUnavailable;

    private static volatile Field cameraRot;
    private static volatile boolean cameraRotUnavailable;

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

    private static Class<?> vehicleClass;
    private static Method vehicleGetHealth;
    private static Method vehicleSetHealth;
    private static Method vehicleGetMaxHealth;
    private static volatile Method vehicleGetGunDataMap;
    private static volatile boolean gunDataMapUnavailable;
    private static volatile Method vehicleComputed;
    private static volatile Method decoyMagazineSizeGet;
    /**
     * 载具的「已装填干扰弹数」是<b>同步实体数据</b>（{@code VehicleEntity.DECOY_COUNT} 这个
     * {@code EntityDataAccessor<Integer>}），1.21.1 上没有 {@code getDecoyCount()} / {@code setDecoyCount()}
     * 这类普通读写方法（1.20.1 有）—— 所以只能拿 accessor 走
     * {@code Entity#getEntityData()} 的 {@code get} / {@code set}。
     */
    private static volatile Object decoyCountAccessor;
    private static volatile Method entityDataOf;
    private static volatile Method synchedGet;
    private static volatile Method synchedSet;
    private static volatile boolean decoyUnavailable;
    private static volatile Method energyStorageGetVehicle;

    private SuperbWarfareCompat() {}

    // ========== 供 Mixin 调用 ==========

    /**
     * 开火收尾：把这一发扣掉的弹匣子弹补回去，并把这一发攒下的热量清零。
     * <p>
     * 放在开火链路内部（而不是每 tick 事后补）是为了让 HUD 与射击逻辑<b>在同一 tick 内</b>始终看到满匣 /
     * 零热量 —— 否则弹匣会按射速往下掉、还可能触发模组自己的「该换弹了」判断。
     *
     * @param shooter 射击方：手持枪械时是玩家；<b>载具武器时是载具实体</b>（炮手在
     *                {@code ShootParameters.ammoSupplier} 里），所以走
     *                {@link CompatToggles#isActiveForShooter(Entity, String)} —— 本体不是玩家时再看乘员
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
     * 开火收尾（参数包版本）：{@code GunData.shoot(ShootParameters)} 与
     * {@code GunItem.shoot / afterShoot(ShootParameters)} 的 RETURN 都调它，
     * 兜「绕过 {@code GunData.shoot(...)} 直接调 item」的路径与<b>载具开火</b>。
     * <p>
     * <b>载具的坑</b>：载具开火时 {@code shooter} 是<b>载具本体</b>（{@code VehicleEntity.getAmmoSupplier()}
     * 就是 {@code return this}），真炮手在 {@code ammoSupplier} 里 —— 所以优先把「活体那一个」交出去，
     * 按玩家判定最准，拿不到活体时才退回载具（由乘员兜底判定）。
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
     * 返回「本该抽到的量」而不是 0：调用方据此认为这次抽电成功，载具自己的「够不够电」判断
     * （引擎 / 能量弹药武器）继续放行，电量却一点没少。上限按当前电量取 {@code min}，
     * 好让「抽电」这一步在读数上依旧自洽。
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
     *   <li><b>载具武器</b>：正坐着的那台车的每个武器同样补满 / 清热量 —— 载具武器不在物品栏里，
     *       而<b>空弹匣连 {@code canShoot} 都过不了</b>、压根进不了开火链路，只有这一趟能兜住
     *       「必须先手动塞一发」；顺带把该车的<b>干扰弹</b>也顶满；</li>
     *   <li>载具血量：补满血 —— 注入点只保证「不再变少」，保证不了「解锁之前就已经掉了血」的那台。
     *       载具的电量不在这里管：由 {@link EnergyCompat} 统一给「骑乘实体 + 背包 + 饰品」补满。</li>
     * </ul>
     */
    public static void onTick(ServerPlayer player, PlayerStats stats) {
        boolean infiniteAmmo = stats.isToggleActive("infinite_ammo");
        boolean noHeat = stats.isToggleActive("no_heat");
        boolean vehicleHealth = stats.isToggleActive(STAT_VEHICLE_HEALTH);
        boolean infiniteEnergy = stats.isToggleActive(STAT_INFINITE_ENERGY);
        if (!infiniteAmmo && !noHeat && !vehicleHealth && !infiniteEnergy) return;

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
        return vehicle != null && vehicleClass != null && vehicleClass.isInstance(vehicle) ? vehicle : null;
    }

    /**
     * 把玩家所在载具的<b>每个武器</b>都补满（并顺带清热量）。
     * <p>
     * 载具武器不在物品栏里 —— 每个武器一份 {@code GunData} 挂在载具的 {@code GUN_DATA_MAP} 上，
     * 而开火收尾那几个注入点只有「已经打过一发」之后才会触发；这里每 5 tick 先把弹匣顶满，
     * 没有那个启动问题。
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
        if (stack == null || stack.isEmpty() || gunItemClass == null || !gunItemClass.isInstance(stack.getItem())) {
            return;
        }
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
     * 装填量 = {@code min(携带量, computed().decoyMagazineSize)}，发射时扣 {@code DECOY_COUNT}；
     * 只要每 5 tick 把 {@code DECOY_COUNT} 顶满，就既不用补弹、也不会真吃玩家的干扰弹物品。
     * <b>不动</b>携带量：它由载具自己的 {@code tick()} 每 tick 从乘员背包重算，写了也会被覆盖。
     */
    private static void topUpVehicleDecoys(Entity vehicle) {
        if (vehicle == null) return;
        if (!resolveVehicleDecoy()) return;
        try {
            Object data = vehicleComputed.invoke(vehicle);
            if (data == null) return;
            int capacity = (Integer) decoyMagazineSizeGet.invoke(data);
            if (capacity <= 0) return;

            Object synched = entityDataOf.invoke(vehicle);
            if (synched == null) return;
            Object currentRaw = synchedGet.invoke(synched, decoyCountAccessor);
            int current = currentRaw instanceof Integer value ? value : 0;
            if (current < capacity) {
                synchedSet.invoke(synched, decoyCountAccessor, capacity);
            }
        } catch (Throwable ignored) {
            // 版本对不上 / 这台车没有干扰弹：这次跳过
        }
    }

    // ========== 弹匣 ==========

    /**
     * 把弹匣写回容量上限；已经是满的就不写（避免每发都标脏物品、反复同步给客户端）。
     * <p>
     * 容量走 {@code GunData.get(GunProp.MAGAZINE)} —— 模组自己的算法，扩容弹匣 / Perk 等修正
     * 都已经算进去。该属性取不到时静默跳过：「无限子弹」退化成「备弹无限但弹匣照常掉」。
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
            if (heat != null && (Double) doubleValueGet.invoke(heat) > 0d) {
                doubleValueSet.invoke(heat, 0d);
            }
            Object overHeat = gunDataOverHeat.get(gunData);
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

    /** 把已经掉过血的载具补满（注入点只管「以后不再掉」）。已经是满血就不写，避免每 tick 标脏同步数据。 */
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
     * 存储本身没有实体引用（只有载具的同步数据），但上游是 Kotlin 的 {@code protected var vehicle},
     * 编译产物带了 {@code protected final VehicleEntity getVehicle()}，反射取它即可。
     * 这个句柄<b>单独懒解析</b>：拿不到就只让「无限能源」的载具扣电那半边变成空操作。
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
     * 那个字段是 {@code public static double[]}：{@code handleWeaponFire} 每帧把第 3 个分量往开火
     * 目标值 lerp，{@code handlePlayerCamera} 再把它加到相机角度上 —— 清零即「开火时相机不动」。
     * 索引 2 之外的分量模组自己不写（始终为 0），所以只清这一个，免得误伤别的相机逻辑。
     * <p>
     * 这个字段属于<b>纯客户端类</b>，所以它单独懒解析、失败也不影响其它功能。
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
     * 反之亦然。武器表（{@code getGunDataMap}）再单独一层 try/catch：它取不到只让「载具补弹」失效。
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

    /** 干扰弹句柄：独立解析，失败只让「干扰弹无限」这一半失效。 */
    private static boolean resolveVehicleDecoy() {
        if (decoyCountAccessor != null && synchedSet != null) return true;
        if (decoyUnavailable) return false;
        synchronized (SuperbWarfareCompat.class) {
            if (decoyCountAccessor != null && synchedSet != null) return true;
            if (decoyUnavailable) return false;
            if (vehicleClass == null) return false;
            try {
                vehicleComputed = vehicleClass.getMethod(METHOD_COMPUTED);
                decoyMagazineSizeGet = vehicleComputed.getReturnType().getMethod(METHOD_GET_DECOY_MAGAZINE_SIZE);

                // 装填数是同步实体数据：拿 accessor + SynchedEntityData 的 get/set
                decoyCountAccessor = vehicleClass.getField(FIELD_DECOY_COUNT).get(null);
                Class<?> accessorClass = Class.forName("net.minecraft.network.syncher.EntityDataAccessor");
                entityDataOf = Class.forName("net.minecraft.world.entity.Entity").getMethod("getEntityData");
                Class<?> synchedData = Class.forName("net.minecraft.network.syncher.SynchedEntityData");
                synchedGet = synchedData.getMethod("get", accessorClass);
                synchedSet = synchedData.getMethod("set", accessorClass, Object.class);
                return true;
            } catch (Throwable t) {
                decoyUnavailable = true;
                return false;
            }
        }
    }

    /** 载具武器表句柄：独立解析，失败只让「每 tick 给载具武器补弹」这一半失效。 */
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
     * 弹匣相关的句柄<b>单独解析</b>：这几步失败（比如某个版本把 {@code MAGAZINE} 属性收成私有、
     * 或挪了位置）只让「补满弹匣」这一半失效，不能把整条联动拖下水。
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
     * {@code DATA_CACHE} 的声明类型是 Guava 的 {@code LoadingCache}，但它的运行时实现类是 Guava
     * 内部类，方法不一定在实现类上直接可见 —— 先在实现类上找，找不到再遍历它实现的接口。
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
