package com.infinitestats.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Method;

/**
 * 「冷汗」（Cold Sweat，modid {@code coldsweat}）联动：<b>免体温</b>。
 * <p>
 * 解锁「免体温」后，玩家的<b>体温（BODY 温度）被钉在舒适区</b>：严寒、酷暑、下矿、下界、被火焰 / 雪地烘烤
 * 都不会再让体温偏离中性值，因此不会因为太冷 / 太热而掉血、减速、视线模糊、发抖。
 * <p>
 * <b>为什么改 BODY 而不是 WORLD</b>：Cold Sweat 的温度分几层（{@code Temperature.Trait}）——
 * {@code WORLD} 是「环境有多冷 / 多热」、{@code BASE} 是环境 + 装备修正后的目标值、
 * {@code BODY} 才是<b>玩家实际体温</b>（HUD 上那一条、也是各种冷热效果判定的依据）。
 * 直接钉 {@code BODY} 相当于「体感永远舒适」，且不改变环境读数（护目镜 / Jade 里看环境温度仍是真实的）。
 * <p>
 * 每 tick 只在<b>偏离</b>时才写回（先读后写）：写回会触发 Cold Sweat 的
 * {@code TemperatureChangedEvent} 与同步包，无脑每 tick 写会白白刷事件。
 * <p>
 * 全程<b>反射</b>（与 {@code GoetyCompat} 同一套思路）；没装冷汗时永久短路、零开销。
 * <p>
 * <b>1.21.1 适配</b>：Cold Sweat 在 1.21 线上换过包根（{@code com.momosoftworks} → 新版可能改到
 * {@code dev.momosoftworks} 等），所以 {@link #TEMPERATURE_CANDIDATES} 里按候选名依次尝试。
 * 1.20.1 主线已用 {@code javap} 核对过 {@code ColdSweat-2.4.2}；本分支尚未拿到 1.21.1 的 jar 复核，
 * 全部取不到时整体静默短路 —— 只让这条属性退化成原版行为。
 */
public final class ColdSweatCompat {

    /** {@code Temperature} 类的候选全名（按新版在前、旧版在后依次尝试）。 */
    private static final String[] TEMPERATURE_CANDIDATES = {
            "dev.momosoftworks.coldsweat.api.util.Temperature",
            "com.momosoftworks.coldsweat.api.util.Temperature"
    };

    /** 中性 / 舒适体温（Cold Sweat 的 BODY 温度以 0 为「不冷不热」）。 */
    private static final double COMFORT_TEMPERATURE = 0.0;

    /**
     * 视为「已在舒适区」的容差。体温每 tick 都会向环境目标值靠拢，容差给小了等于每 tick 都写回、
     * 白白刷事件与同步包；给 1 度既远小于冷热效果开始加剧的区间，又能让写回只偶尔发生。
     */
    private static final double COMFORT_EPSILON = 1.0;

    private static boolean resolved;
    private static boolean unavailable;

    private static Method getTemperature;
    private static Method setTemperature;
    private static Object bodyTrait;

    private ColdSweatCompat() {}

    /** 每个玩家 tick 调用一次（由 {@code UtilityHandler} 驱动，只在「免体温」已解锁时进入）。 */
    public static void onTick(ServerPlayer player) {
        if (!resolve()) return;
        try {
            double current = ((Number) getTemperature.invoke(null, player, bodyTrait)).doubleValue();
            if (Math.abs(current - COMFORT_TEMPERATURE) < COMFORT_EPSILON) return;
            setTemperature.invoke(null, player, bodyTrait, COMFORT_TEMPERATURE);
        } catch (Throwable ignored) {
            // 静默跳过这一帧，下次继续
        }
    }

    /** 懒解析反射句柄；失败一次即永久短路（以后不再重试）。 */
    private static boolean resolve() {
        if (resolved || unavailable) return resolved;
        synchronized (ColdSweatCompat.class) {
            if (resolved || unavailable) return resolved;
            try {
                Class<?> temperature = null;
                for (String name : TEMPERATURE_CANDIDATES) {
                    try {
                        temperature = Class.forName(name);
                        break;
                    } catch (Throwable ignored) {
                        // 换下一个候选包名
                    }
                }
                if (temperature == null) throw new ClassNotFoundException("no Temperature class");

                // Trait 是 Temperature 的内部枚举，跟着主类一起定位（换包时不会只换一半）
                Class<?> trait = Class.forName(temperature.getName() + "$Trait");
                getTemperature = temperature.getMethod("get", LivingEntity.class, trait);
                setTemperature = temperature.getMethod("set", LivingEntity.class, trait, double.class);
                bodyTrait = trait.getField("BODY").get(null);

                resolved = true;
            } catch (Throwable t) {
                unavailable = true;
            }
            return resolved;
        }
    }
}
