package com.infinitestats.handler;

import com.infinitestats.InfiniteStats;
import com.infinitestats.util.AttributeValueMath;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 生物面板的「属性锁定」—— 让面板改过的数值不被其它模组改回去。
 *
 * <h2>要解决什么</h2>
 * 面板写的是<b>基础值</b>（见 {@link AttributeValueMath}），但在一个几百模组的整合包里，
 * 基础值从来不只属于你：Boss 血量控制器、等级成长、Configured 一类的接管型模组、
 * 甚至别的模组自己写的「每 tick 把某属性设回默认」逻辑，都会在我们写完之后再覆盖一次。
 * 玩家看到的就是「改完过一会儿又变回去了」，面板因此显得不可信。
 *
 * <h2>怎么拦</h2>
 * 锁定 = 服务端替这个实体记一份「目标值」，并在 {@link LivingEvent.LivingTickEvent}
 * 的 <b>LOWEST + 每个 tick</b> 检查一次：只要发现当前值与目标不符，就重新写回去。
 * 这样别的模组即使改了，改动最多存活 1 tick（在玩家眼里就是不生效），不需要去反编译对方、
 * 也不需要 Mixin 到对方的写入点上 —— 我们只做「最后一道覆盖」。
 *
 * <h2>两种锁</h2>
 * <ul>
 *   <li>{@link #MODE_BASE} <b>锁基础值</b>：目标就是基础值本身。装备 / 药水等修饰符照常叠加，
 *       只是别的模组对基础值的改写会被压回去。日常最常用。</li>
 *   <li>{@link #MODE_FINAL} <b>锁最终值</b>：目标是你看到的那个最终值，每 tick 按当前修饰符
 *       反算基础值来抵消。连其它模组加的修饰符一起压住 —— 但代价是装备加成也会被抵消，
 *       所以只在「必须钉死在某个数值」时用。</li>
 * </ul>
 * 目标值超出属性自身范围时按范围上限处理（{@code RangedAttribute} 的 min / max），
 * 不会出现「锁了 2000 但只能到 1024、于是每 tick 反复写入」的抖动。
 *
 * <h2>存在哪</h2>
 * 锁写在实体自己的 {@code ForgeData} 里（{@code infinitestats:attribute_locks}），
 * 随实体存档走：重进世界、切维度、被复制出来的那一只都会带着锁。
 * 运行时另外有一份 UUID → 锁表 的缓存，避免每 tick 解析 NBT。
 */
@Mod.EventBusSubscriber(modid = InfiniteStats.MODID)
public final class AttributeLockHandler {

    /** 不锁。 */
    public static final int MODE_NONE = 0;
    /** 锁基础值（修饰符照常叠加）。 */
    public static final int MODE_BASE = 1;
    /** 锁最终值（连修饰符一起抵消）。 */
    public static final int MODE_FINAL = 2;

    /** 存在实体 ForgeData 里的键。 */
    private static final String NBT_KEY = "infinitestats:attribute_locks";

    /** 比这个差值小就当作「已经是目标值」：避免浮点抖动导致每 tick 都写一次（进而每 tick 发属性同步包）。 */
    private static final double EPS = 1.0E-4D;

    /** UUID → (属性注册名 → 锁)。只放真正有锁的实体。 */
    private static final Map<UUID, Map<String, Lock>> CACHE = new ConcurrentHashMap<>();

    /** 一条锁。 */
    public record Lock(int mode, double value) {}

    private AttributeLockHandler() {}

    // ======================== 查询 ========================

    /** 实体的全部锁（没有就返回空表，不要往里写）。 */
    public static Map<String, Lock> locksOf(Entity entity) {
        if (entity == null) return Collections.emptyMap();
        Map<String, Lock> cached = CACHE.get(entity.getUUID());
        if (cached != null) return cached;
        return Collections.emptyMap();
    }

    public static int modeOf(Entity entity, String attributeId) {
        Lock lock = locksOf(entity).get(attributeId);
        return lock == null ? MODE_NONE : lock.mode();
    }

    public static double valueOf(Entity entity, String attributeId) {
        Lock lock = locksOf(entity).get(attributeId);
        return lock == null ? Double.NaN : lock.value();
    }

    /** 给客户端用的快照：{@code 属性注册名 → {mode, value}}。 */
    public static CompoundTag snapshot(Entity entity) {
        if (entity instanceof LivingEntity living) hydrate(living);
        CompoundTag out = new CompoundTag();
        for (Map.Entry<String, Lock> entry : locksOf(entity).entrySet()) {
            CompoundTag one = new CompoundTag();
            one.putInt("mode", entry.getValue().mode());
            one.putDouble("value", entry.getValue().value());
            out.put(entry.getKey(), one);
        }
        return out;
    }

    // ======================== 写入 ========================

    /**
     * 设置 / 清除一条锁。
     *
     * @param mode  {@link #MODE_NONE} 表示解锁
     * @param value 目标值（锁基础值 = 基础值；锁最终值 = 最终值）
     */
    public static void set(LivingEntity entity, String attributeId, int mode, double value) {
        if (entity == null || attributeId == null) return;
        Map<String, Lock> locks = mutableLocks(entity);

        if (mode != MODE_BASE && mode != MODE_FINAL) {
            if (locks.remove(attributeId) != null) commit(entity, locks);
            return;
        }
        locks.put(attributeId, new Lock(mode, value));
        commit(entity, locks);
        // 立刻压一次，玩家点完锁当场就能看到效果（不必等下一个 tick）
        applyLocks(entity, locks);
    }

    /**
     * 面板改了某个属性的数值：如果它正被锁着，把锁的目标一起更新成新值。
     * <p>
     * 不这么做就会出现「锁着 100，输 200 回车，下一 tick 又被压回 100」这种怪事 ——
     * 玩家会以为输入框坏了。
     */
    public static void onAttributeEdited(LivingEntity entity, String attributeId,
                                         double finalValue, double baseValue) {
        hydrate(entity);
        Lock lock = locksOf(entity).get(attributeId);
        if (lock == null) return;
        double target = lock.mode() == MODE_BASE ? baseValue : finalValue;
        if (!Double.isFinite(target)) return;
        set(entity, attributeId, lock.mode(), target);
    }

    /** 实体被移除时把缓存放掉（NBT 上的锁随实体一起没了）。 */
    @SubscribeEvent
    public static void onLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        CACHE.remove(event.getEntity().getUUID());
    }

    // ======================== 每 tick 压回去 ========================

    /**
     * 优先级 LOWEST：保证在所有其它监听器都改完之后再覆盖，
     * 否则我们的写入会被排在后面的模组再改一次 —— 那就白锁了。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;

        // 缓存里没有：可能是刚加载 / 刚复制出来的实体，从它自己的 NBT 里补一次
        // （每 20 tick 才碰一次 NBT，避免几千实体时每 tick 都读）
        if (entity.tickCount % 20 == 0) hydrate(entity);

        Map<String, Lock> locks = CACHE.get(entity.getUUID());
        if (locks == null || locks.isEmpty()) return;
        applyLocks(entity, locks);
    }

    /** 把当前值强制对齐到锁目标。 */
    private static void applyLocks(LivingEntity entity, Map<String, Lock> locks) {
        boolean touchedMaxHealth = false;
        for (Map.Entry<String, Lock> entry : locks.entrySet()) {
            AttributeInstance instance = instanceOf(entity, entry.getKey());
            if (instance == null) continue;
            Lock lock = entry.getValue();

            if (lock.mode() == MODE_BASE) {
                if (Math.abs(instance.getBaseValue() - lock.value()) > EPS) {
                    instance.setBaseValue(lock.value());
                    if (isMaxHealth(instance)) touchedMaxHealth = true;
                }
            } else {
                // 锁最终值：先把目标夹进属性范围，否则超范围时会永远对不上、每 tick 反复写
                double target = sanitize(instance, lock.value());
                if (Math.abs(instance.getValue() - target) > EPS) {
                    double base = AttributeValueMath.baseForFinal(instance, target);
                    if (Double.isFinite(base) && Math.abs(instance.getBaseValue() - base) > EPS) {
                        instance.setBaseValue(base);
                        if (isMaxHealth(instance)) touchedMaxHealth = true;
                    }
                }
            }
        }
        if (touchedMaxHealth && entity.getHealth() > entity.getMaxHealth()) {
            entity.setHealth(entity.getMaxHealth());
        }
    }

    private static void hydrate(LivingEntity entity) {
        if (CACHE.containsKey(entity.getUUID())) return;
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(NBT_KEY, Tag.TAG_COMPOUND)) return;
        Map<String, Lock> locks = new HashMap<>();
        readInto(data.getCompound(NBT_KEY), locks);
        if (!locks.isEmpty()) CACHE.put(entity.getUUID(), new ConcurrentHashMap<>(locks));
    }

    // ======================== 内部工具 ========================

    /** 拿一份可写的锁表（没有就从 NBT 读一份空表挂上去）。 */
    private static Map<String, Lock> mutableLocks(LivingEntity entity) {
        Map<String, Lock> locks = CACHE.get(entity.getUUID());
        if (locks != null) return locks;

        Map<String, Lock> loaded = new HashMap<>();
        readInto(entity.getPersistentData().getCompound(NBT_KEY), loaded);
        Map<String, Lock> stored = new ConcurrentHashMap<>(loaded);
        CACHE.put(entity.getUUID(), stored);
        return stored;
    }

    /** 写回缓存 + 实体 NBT（空表就把 NBT 键删掉，别在存档里留空壳）。 */
    private static void commit(LivingEntity entity, Map<String, Lock> locks) {
        CompoundTag data = entity.getPersistentData();
        if (locks.isEmpty()) {
            data.remove(NBT_KEY);
            CACHE.remove(entity.getUUID());
            return;
        }
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<String, Lock> entry : locks.entrySet()) {
            CompoundTag one = new CompoundTag();
            one.putInt("mode", entry.getValue().mode());
            one.putDouble("value", entry.getValue().value());
            tag.put(entry.getKey(), one);
        }
        data.put(NBT_KEY, tag);
    }

    private static void readInto(CompoundTag tag, Map<String, Lock> out) {
        for (String key : tag.getAllKeys()) {
            CompoundTag one = tag.getCompound(key);
            int mode = one.getInt("mode");
            if (mode != MODE_BASE && mode != MODE_FINAL) continue;
            double value = one.getDouble("value");
            if (!Double.isFinite(value)) continue;
            out.put(key, new Lock(mode, value));
        }
    }

    private static AttributeInstance instanceOf(Entity entity, String attributeId) {
        if (!(entity instanceof LivingEntity living)) return null;
        ResourceLocation id = ResourceLocation.tryParse(attributeId);
        if (id == null) return null;
        Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(id);
        return attribute == null ? null : living.getAttribute(attribute);
    }

    private static boolean isMaxHealth(AttributeInstance instance) {
        return instance.getAttribute() == Attributes.MAX_HEALTH;
    }

    /** 按属性自身的取值范围夹一次（只有 RangedAttribute 有范围）。 */
    private static double sanitize(AttributeInstance instance, double value) {
        Attribute attribute = instance.getAttribute();
        if (attribute instanceof RangedAttribute ranged) {
            return Math.max(ranged.getMinValue(), Math.min(ranged.getMaxValue(), value));
        }
        return value;
    }
}
