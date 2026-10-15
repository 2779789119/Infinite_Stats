package com.infinitestats.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

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
 * （{@code Math.min(..., 20)}，1.20.1 主线反编译核对过），没有配置项或属性可读，所以这里照抄同一个数。
 * <p>
 * 全程<b>反射</b>：口渴只是可选运行时联动，本模组不声明编译期依赖。没装时
 * {@code Class.forName} 直接失败并永久短路、零开销；版本对不上导致方法取不到时同样整体短路。
 * <p>
 * <b>1.21.1 适配</b>：Forge 的 Capability 体系（{@code Capability} + {@code LazyOptional} +
 * {@code Entity#getCapability}）在 NeoForge 上被 <b>Data Attachment</b> 取代，而口渴模组的
 * 1.21.1 版本可能整条线都换过机制，因此这里对「怎么从玩家身上拿到 {@code IThirst}」做多套尝试，
 * 命中哪套用哪套（后续的读写方法名不变）：
 * <ol>
 *   <li>{@code ModCapabilities#PLAYER_THIRST} 是 {@code AttachmentType} →
 *       {@code Player#getData(AttachmentType)}（NeoForge 现行写法）；</li>
 *   <li>仍是旧的 {@code Capability} → 依次试 {@code Entity#getCapability(Capability, Direction)}
 *       与 {@code Entity#getCapability(Capability)}，返回值再按 {@code Optional} / {@code LazyOptional} 解包。</li>
 * </ol>
 * 两组都取不到就静默短路，只让这条属性退化成原版行为。
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

    /** 取口渴数据的路径：0 = 还没定，1 = AttachmentType，2 = 带方向的能力，3 = 不带方向的能力。 */
    private static int accessPath;
    private static Object thirstHandle;
    private static Method getData;
    private static Method getCapabilityWithSide;
    private static Method getCapabilityNoSide;

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
            Object thirst = fetch(player);
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

    /** 按 {@link #accessPath} 选定的那条路取出 {@code IThirst} 实现；拿不到返回 {@code null}。 */
    private static Object fetch(ServerPlayer player) throws Exception {
        switch (accessPath) {
            case 1:
                return getData.invoke(player, thirstHandle);
            case 2:
                return unwrap(getCapabilityWithSide.invoke(player, thirstHandle, null));
            case 3:
                return unwrap(getCapabilityNoSide.invoke(player, thirstHandle));
            default:
                return null;
        }
    }

    /** {@code Optional} / {@code LazyOptional} 的解包（两者的 {@code orElse(Object)} 签名相同）。 */
    private static Object unwrap(Object container) {
        if (container == null) return null;
        try {
            return container.getClass().getMethod("orElse", Object.class).invoke(container, new Object[]{null});
        } catch (Throwable t) {
            // 已经是裸对象（新版直接返回实现）——原样交给调用方
            return container;
        }
    }

    /** 懒解析反射句柄；失败一次即永久短路（以后不再重试）。 */
    private static boolean resolve() {
        if (resolved || unavailable) return resolved;
        synchronized (ThirstCompat.class) {
            if (resolved || unavailable) return resolved;
            try {
                Object handle = Class.forName(MOD_CAPABILITIES).getField("PLAYER_THIRST").get(null);

                // 先按 NeoForge 的 Data Attachment 试（1.21.1 的现行写法）
                try {
                    Class<?> attachmentType = Class.forName("net.neoforged.neoforge.attachment.AttachmentType");
                    if (attachmentType.isInstance(handle)) {
                        getData = Class.forName("net.minecraft.world.entity.Entity")
                                .getMethod("getData", attachmentType);
                        accessPath = 1;
                    }
                } catch (Throwable ignored) {
                    // 不是 AttachmentType：继续试旧的 Capability 路线
                }

                if (accessPath == 0) {
                    // 旧的 Forge Capability 路线：带方向与不带方向两种签名都备着
                    try {
                        getCapabilityWithSide = Class.forName("net.minecraft.world.entity.Entity")
                                .getMethod("getCapability",
                                        Class.forName("net.minecraftforge.common.capabilities.Capability"),
                                        net.minecraft.core.Direction.class);
                        accessPath = 2;
                    } catch (Throwable ignored) {
                        try {
                            getCapabilityNoSide = Class.forName("net.minecraft.world.entity.Entity")
                                    .getMethod("getCapability",
                                            Class.forName("net.neoforged.neoforge.capabilities.ItemCapability"));
                            accessPath = 3;
                        } catch (Throwable ignored2) {
                            // 两条路都走不通
                        }
                    }
                }

                if (accessPath == 0) throw new NoSuchMethodException("no thirst access path");
                thirstHandle = handle;

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
