package com.infinitestats.compat;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 「口渴」（Thirst Was Taken，modid {@code thirst}）联动：<b>免口渴</b>。
 * <p>
 * 解锁「免口渴」后，玩家的口渴值（thirst）与解渴度（quenched）恒满、消耗值（exhaustion）恒归零 ——
 * HUD 上的口渴条一直满格，不会因为跑步 / 挖矿 / 回血累积耗尽而扣血。
 * <p>
 * <b>为什么是「每 tick 补满」而不是「拦截消耗」</b>：口渴的消耗散落在
 * {@code PlayerThirst#tick} 的多条分支里（饥饿效果、生物群系修正、火焰保护修正、回血消耗……），
 * 逐个拦截既脆又容易漏；而补满只有一处，且和「无限能源」的做法一致。
 * <p>
 * 上限<b>硬编码 20</b>：Thirst Was Taken 的口渴 / 解渴度上限写死在 {@code PlayerThirst} 里
 * （{@code Math.min(..., 20)}，反编译核对过），没有配置项或属性可读，所以这里照抄同一个数。
 * <p>
 * 全程<b>反射</b>（与 {@code GoetyCompat} 同一套思路）：口渴只是可选运行时联动，本模组不声明编译期依赖。
 * 没装时 {@code Class.forName} 直接失败并永久短路、零开销；版本对不上导致方法取不到时同样整体短路。
 * <p>
 * 反射到的成员（已用 {@code javap} 对着整合包里的 {@code ThirstWasTaken-1.20.1-1.4.0} 核对）：
 * {@code ModCapabilities#PLAYER_THIRST}（{@code Capability<IThirst>}），
 * {@code IThirst#getThirst/setThirst/getQuenched/setQuenched/getExhaustion/setExhaustion/updateThirstData}。
 */
public final class ThirstCompat {

    private static final String MOD_CAPABILITIES = "dev.ghen.thirst.foundation.common.capability.ModCapabilities";
    private static final String ITHIRST = "dev.ghen.thirst.foundation.common.capability.IThirst";

    /** Thirst Was Taken 的口渴 / 解渴度上限（它自己在 {@code PlayerThirst} 里硬编码 20）。 */
    private static final int MAX_THIRST = 20;

    /**
     * 消耗值允许残留的上限。口渴方自己的阈值是 {@code exhaustion > 4} 才扣一格，所以留 1 点余量
     * 完全够用；这样跑步 / 挖矿累积的那点消耗不会被每 tick 立刻抹掉并重发同步包。
     */
    private static final float EXHAUSTION_THRESHOLD = 1.0f;

    /** 已解析成功 / 已判定不可用（分别短路，避免每 tick 重试反射）。 */
    private static boolean resolved;
    private static boolean unavailable;

    private static Object thirstCapability;
    private static Method getCapability;
    private static Method lazyOrElse;
    private static Method getThirst;
    private static Method setThirst;
    private static Method getQuenched;
    private static Method setQuenched;
    private static Method getExhaustion;
    private static Method setExhaustion;
    private static Method updateThirstData;

    private ThirstCompat() {}

    /**
     * 每个玩家 tick 调用一次（由 {@code UtilityHandler} 驱动，只在「免口渴」已解锁时进入）。
     * <p>
     * 已经是满值 + 零消耗时什么都不做（不重复发同步包）；没装口渴模组时在 {@link #resolve()} 里永久短路。
     */
    public static void onTick(ServerPlayer player) {
        if (!resolve()) return;
        try {
            Object lazy = getCapability.invoke(player, new Object[]{thirstCapability, null});
            Object thirst = lazyOrElse.invoke(lazy, new Object[]{null});
            if (thirst == null) return;

            int currentThirst = (Integer) getThirst.invoke(thirst);
            int currentQuenched = (Integer) getQuenched.invoke(thirst);
            float exhaustion = (Float) getExhaustion.invoke(thirst);
            if (currentThirst >= MAX_THIRST && currentQuenched >= MAX_THIRST
                    && exhaustion < EXHAUSTION_THRESHOLD) {
                return;
            }

            setThirst.invoke(thirst, MAX_THIRST);
            setQuenched.invoke(thirst, MAX_THIRST);
            setExhaustion.invoke(thirst, 0f);
            // 补完必须自己发同步包：HUD 读的是客户端那份，不发就会「服务端满、屏幕上空」
            updateThirstData.invoke(thirst, player);
        } catch (Throwable ignored) {
            // 静默跳过这一帧，下次继续
        }
    }

    /** 懒解析反射句柄；失败一次即永久短路（以后不再重试）。 */
    private static boolean resolve() {
        if (resolved || unavailable) return resolved;
        synchronized (ThirstCompat.class) {
            if (resolved || unavailable) return resolved;
            try {
                Field capField = Class.forName(MOD_CAPABILITIES).getField("PLAYER_THIRST");
                thirstCapability = capField.get(null);

                getCapability = Class.forName("net.minecraft.world.entity.Entity")
                        .getMethod("getCapability", Capability.class, Direction.class);
                lazyOrElse = Class.forName("net.minecraftforge.common.util.LazyOptional")
                        .getMethod("orElse", Object.class);

                Class<?> iThirst = Class.forName(ITHIRST);
                getThirst = iThirst.getMethod("getThirst");
                setThirst = iThirst.getMethod("setThirst", int.class);
                getQuenched = iThirst.getMethod("getQuenched");
                setQuenched = iThirst.getMethod("setQuenched", int.class);
                getExhaustion = iThirst.getMethod("getExhaustion");
                setExhaustion = iThirst.getMethod("setExhaustion", float.class);
                updateThirstData = iThirst.getMethod("updateThirstData", Player.class);

                resolved = true;
            } catch (Throwable t) {
                unavailable = true;
            }
            return resolved;
        }
    }
}
