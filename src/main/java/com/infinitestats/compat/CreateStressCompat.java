package com.infinitestats.compat;

import com.infinitestats.util.BlockOwnershipStore;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Create（机械动力）联动：<b>无限应力</b>。
 * <p>
 * 解锁「无限应力」后，<b>你自己放置的</b>动力网络不再因为应力（Stress, SU）不足而超载停机 ——
 * 机器照常转、应力表指针回到低位、护目镜读数正常。判定口径与「无限能源」一致：
 * 归属表（{@link BlockOwnershipStore}）里认到的方块才算数，别人基地与公共机器不受影响。
 * <p>
 * <b>机械动力的应力模型</b>（已用 {@code javap} 对整合包里的 {@code create-1.20.1-6.0.8} 核对）：
 * <ul>
 *   <li>{@code KineticNetwork.calculateCapacity()} 汇总全部动力源的产能 + 未加载区块的产能，
 *       {@code calculateStress()} 汇总全部成员的耗能；</li>
 *   <li>网络把这两个总数交给每个成员的
 *       {@code KineticBlockEntity.updateFromNetwork(maxStress, currentStress, size)}，
 *       由后者算出 {@code overStressed = maxStress < currentStress && StressImpact.isEnabled()}；
 *       {@code getSpeed()} 在 {@code overStressed} 时直接返回 0 —— 这就是「超载停机」；</li>
 *   <li>客户端不跑网络，而是从同步包的 {@code Capacity} / {@code Stress} <b>自行重算</b>同一个判据
 *       （{@code KineticBlockEntity.read}）。</li>
 * </ul>
 * 因此只要让网络的<b>产能</b>足够大，服务端与客户端、应力表与护目镜会同时得到「不过载」的一致结论。
 * 落点与两个必须一起做的事见 {@code mixin/CreateKineticNetworkMixin}（改容量）与
 * {@code mixin/CreateKineticBlockEntityMixin}（存档时还原真实容量）。
 * <p>
 * 本类只做与机械动力无关的两件事，保持「不引用 Create 任何类型」——目标类不在场时本模组根本
 * 不会加载它（Mixin 被 {@code InfiniteStatsMixinPlugin} 拦掉），但保持这一点可以让
 * 「没装机械动力」时连类加载都不发生：
 * <ol>
 *   <li><b>判定归属者里有没有解锁者</b>：读归属表 + 在线玩家缓存；</li>
 *   <li><b>记住每个网络的真实产能</b>：改写容量时会把「改写前的真值」存下来，供存档时还原
 *       （见 {@link #realCapacityOf}）——否则「无限产能」会被烤进存档，属性关掉或卸载模组后仍然生效。</li>
 * </ol>
 */
public final class CreateStressCompat {

    /** 「无限应力」的属性 id。 */
    public static final String STAT_ID = "create_infinite_stress";

    /**
     * 改写后的「无限产能」。
     * <p>
     * 用 10<sup>9</sup> 这个<b>够大但仍有限</b>的数，而不是 {@code Float.MAX_VALUE}：真实网络里的
     * 耗能总量不过几十万 SU，10 亿已经永远够用；而 {@code Float.MAX_VALUE} 参与乘法 / 打印 /
     * 第三方附属的数值处理时容易出意外（溢出成 Infinity、界面显示成 3.4E38 之类）。
     */
    public static final float INFINITE_CAPACITY = 1_000_000_000f;

    /** 在线解锁者集合的缓存时长（tick）。加点的瞬间不必立刻生效，1 秒足够。 */
    private static final int UNLOCKED_CACHE_TICKS = 20;

    /** 在线解锁者 UUID（按服务器 tick 缓存，避免每个方块实体都遍历一次玩家列表）。 */
    private static Set<UUID> unlockedOwners = Collections.emptySet();
    private static int unlockedCacheTick = -1_000_000;

    /**
     * 每个网络的「真实产能」（改写前的值）。
     * <p>
     * 键是 {@code KineticNetwork} 实例本身（它没重写 {@code equals}/{@code hashCode}，天然是身份语义），
     * 用 {@link WeakHashMap} 弱引用持有 —— 网络对象在存档 / 维度切换时会被整个换掉，
     * 弱引用保证旧条目自然消失，不会随游玩时长无限增长。
     */
    private static final Map<Object, Float> REAL_CAPACITY = new WeakHashMap<>();

    private CreateStressCompat() {}

    /**
     * 这批方块所在的动力网络是否「由解锁者所有」——只要其中<b>任意一块</b>是当前在线、
     * 且已解锁「无限应力」的玩家放置的，整张网络就视为生效。
     * <p>
     * 之所以按整张网络而不是按单个方块：动力网络是一整套连在一起的传动结构，
     * 一部分转、一部分停会直接穿帮；整张网络一起生效才与其他机器表现一致。
     * <p>
     * 没装机械动力、没解锁者在线、或在客户端调用时都会很快返回 {@code false}（前两步零扫描）。
     */
    public static boolean isEmpowered(Level level, List<BlockPos> positions) {
        if (!(level instanceof ServerLevel serverLevel) || positions.isEmpty()) return false;

        Set<UUID> unlocked = unlockedOwners(serverLevel);
        if (unlocked.isEmpty()) return false;

        BlockOwnershipStore store = BlockOwnershipStore.get(serverLevel);
        for (int i = 0; i < positions.size(); i++) {
            UUID owner = store.getOwner(positions.get(i));
            // owner 可能是 null（方块是别人放的 / 由非玩家来源产生）：HashSet 查 null 安全返回 false
            if (owner != null && unlocked.contains(owner)) return true;
        }
        return false;
    }

    /** 当前服务器上是否有「已解锁无限应力」的在线玩家；给热路径做零分配快速短路用。 */
    public static boolean hasAnyUnlocked(ServerLevel level) {
        return !unlockedOwners(level).isEmpty();
    }

    /** 当前服务器上「已解锁无限应力」的在线玩家 UUID（按 {@value #UNLOCKED_CACHE_TICKS} tick 缓存）。 */
    private static Set<UUID> unlockedOwners(ServerLevel level) {
        MinecraftServer server = level.getServer();
        if (server == null) return Collections.emptySet();

        int tick = server.getTickCount();
        int age = tick - unlockedCacheTick;
        if (age >= 0 && age < UNLOCKED_CACHE_TICKS) return unlockedOwners;

        Set<UUID> next = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (CompatToggles.isActive(player, STAT_ID)) {
                next.add(player.getUUID());
            }
        }
        unlockedOwners = next.isEmpty() ? Collections.emptySet() : next;
        unlockedCacheTick = tick;
        return unlockedOwners;
    }

    /**
     * 记下某动力网络「没被改写时」的产能真值。
     * <p>
     * 由 {@code CreateKineticNetworkMixin} 在改写返回值之前调用：改完这一笔，网络上所有成员的
     * {@code capacity} 字段都会写成 10 亿，而存档必须落<b>真值</b>，否则会把「无限产能」永久写进
     * 存档（属性关掉后机器仍然不过载、卸载本模组后依旧如此）。存档时的取用见 {@link #realCapacityOf}。
     */
    public static void rememberRealCapacity(Object network, float capacity) {
        if (network == null) return;
        REAL_CAPACITY.put(network, capacity);
    }

    /**
     * 某动力网络最近一次算出的真实产能；从未算过时返回 {@code NaN}（调用方据此跳过还原，
     * 例如方块实体刚放下的第一次存档）。
     */
    public static float realCapacityOf(Object network) {
        if (network == null) return Float.NaN;
        Float value = REAL_CAPACITY.get(network);
        return value == null ? Float.NaN : value;
    }
}
