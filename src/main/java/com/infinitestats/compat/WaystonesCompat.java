package com.infinitestats.compat;

import com.infinitestats.network.WaystoneEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 传送石碑（Waystones，modid {@code waystones}）联动：把玩家<b>已激活的石碑</b>搬进本模组的传送点面板。
 * <p>
 * 面板复用现有的 {@code client/WaypointScreen}（原本只显示本模组「定点传送」自己保存的点），
 * 现在在同一张列表里追加一行行传送石碑，点「传送」即走 {@code WaystonesAPI#forceTeleportToWaystone}
 * —— <b>绕过经验消耗与冷却</b>。
 * <p>
 * 为什么用 {@code forceTeleportToWaystone}：它正是传送石碑给「传送卷轴 / 传送石」这类自带装置用的入口
 * （不走等级扣费、不查冷却），语义与「免费无冷却」一致；而 {@code tryTeleportToWaystone} 会按配置
 * 扣经验并进入冷却。
 * <p>
 * 全程<b>反射</b>；没装传送石碑时永久短路，面板里也不会多出任何行。
 * <p>
 * <b>1.21.1 兼容做法</b>：传送石碑在 21.x（1.21.1）上把 API 做过一次重构，同一个概念在两条版本线上
 * 是「接口 + getXxx()」与「record + xxx()」两种形状，因此这里对<b>类型名</b>与<b>方法名</b>都做多套尝试
 * （见 {@link #TYPE_NAMES} 与 {@link #findMethod}）：命中哪套用哪套，全不中就永久短路、这条联动不显示。
 */
public final class WaystonesCompat {

    private static final String API = "net.blay09.mods.waystones.api.WaystonesAPI";

    /** 石碑类型的候选名：21.x 起 {@code IWaystone} 被 {@code Waystone}（record）取代。 */
    private static final String[] TYPE_NAMES = {
            "net.blay09.mods.waystones.api.IWaystone",
            "net.blay09.mods.waystones.api.Waystone"
    };

    private static boolean resolved;
    private static boolean unavailable;

    private static Method getActivatedWaystones;
    private static Method forceTeleportToWaystone;
    private static Method waystoneUid;
    private static Method waystoneName;
    private static Method waystonePos;
    private static Method waystoneDimension;

    private WaystonesCompat() {}

    /** 装了传送石碑且 API 可用（面板据此决定要不要发列表请求）。 */
    public static boolean isAvailable() {
        return resolve();
    }

    /**
     * 玩家当前<b>已激活</b>的全部石碑（含别人激活过、你踩过一遍的那种）。
     * 没装传送石碑 / 反射失败时返回空表。
     */
    public static List<WaystoneEntry> getActivated(ServerPlayer player) {
        List<WaystoneEntry> result = new ArrayList<>();
        if (!resolve()) return result;
        try {
            Object collection = getActivatedWaystones.invoke(null, player);
            if (!(collection instanceof Collection<?> waystones)) return result;
            for (Object waystone : waystones) {
                UUID uid = (UUID) waystoneUid.invoke(waystone);
                if (uid == null) continue; // 没 UUID 的石碑既显示不了也传不了，直接跳过
                String name = (String) waystoneName.invoke(waystone);
                if (name == null) name = "";
                BlockPos pos = (BlockPos) waystonePos.invoke(waystone);
                result.add(new WaystoneEntry(uid, name, dimensionOf(waystone),
                        pos.getX(), pos.getY(), pos.getZ()));
            }
        } catch (Throwable ignored) {
            // 取不到就当这张表是空的，不影响面板其余部分
        }
        return result;
    }

    /**
     * 把玩家免费传送到指定 UUID 的石碑（跳过经验与冷却）。
     * <p>
     * 只在<b>该玩家已激活的石碑</b>里查找 —— 客户端递上来的 UUID 不可信，不能直接拿去传送一个
     * 他从未激活过的石碑。
     *
     * @return 是否真的发起了传送（找不到该石碑时为 {@code false}）
     */
    public static boolean teleport(ServerPlayer player, UUID uid) {
        if (!resolve() || uid == null) return false;
        try {
            Object collection = getActivatedWaystones.invoke(null, player);
            if (!(collection instanceof Collection<?> waystones)) return false;
            for (Object waystone : waystones) {
                UUID candidate = (UUID) waystoneUid.invoke(waystone);
                if (uid.equals(candidate)) {
                    forceTeleportToWaystone.invoke(null, player, waystone);
                    return true;
                }
            }
        } catch (Throwable ignored) {
            // 传送失败就当作没发生
        }
        return false;
    }

    /** 石碑所在维度的 id（新版返回 {@code ResourceKey<Level>}）。 */
    private static String dimensionOf(Object waystone) throws Exception {
        Object dimension = waystoneDimension.invoke(waystone);
        if (dimension instanceof ResourceKey<?> key) {
            return key.location().toString();
        }
        return "minecraft:overworld";
    }

    /** 懒解析反射句柄；失败一次即永久短路（以后不再重试）。 */
    private static boolean resolve() {
        if (resolved || unavailable) return resolved;
        synchronized (WaystonesCompat.class) {
            if (resolved || unavailable) return resolved;
            try {
                Class<?> api = Class.forName(API);
                Class<?> waystoneType = null;
                for (String name : TYPE_NAMES) {
                    try {
                        waystoneType = Class.forName(name);
                        break;
                    } catch (Throwable ignored) {
                        // 换下一个候选名
                    }
                }
                if (waystoneType == null) throw new ClassNotFoundException("no waystone type");

                getActivatedWaystones = api.getMethod("getActivatedWaystones", net.minecraft.world.entity.player.Player.class);
                forceTeleportToWaystone = api.getMethod("forceTeleportToWaystone",
                        net.minecraft.world.entity.Entity.class, waystoneType);

                // 新版 record 的访问器不带 get 前缀，两条线各试一遍
                waystoneUid = findMethod(waystoneType, "getWaystoneUid", "waystoneUid");
                waystoneName = findMethod(waystoneType, "getName", "name");
                waystonePos = findMethod(waystoneType, "getPos", "pos");
                waystoneDimension = findMethod(waystoneType, "getDimension", "dimension");

                resolved = waystoneUid != null && waystoneName != null
                        && waystonePos != null && waystoneDimension != null;
                if (!resolved) unavailable = true;
            } catch (Throwable t) {
                unavailable = true;
            }
            return resolved;
        }
    }

    /** 按候选名依次找无参方法；都不存在返回 {@code null}。 */
    private static Method findMethod(Class<?> type, String... names) {
        for (String name : names) {
            try {
                return type.getMethod(name);
            } catch (Throwable ignored) {
                // 换下一个候选名
            }
        }
        return null;
    }
}
