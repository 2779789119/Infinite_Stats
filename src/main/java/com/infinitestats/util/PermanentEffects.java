package com.infinitestats.util;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * 「无限时长」药水效果的 1.19.2 写法。
 *
 * <h2>为什么不能直接写 -1</h2>
 * 1.20 起才有 {@code MobEffectInstance.INFINITE_DURATION}（= -1）这个概念，而且是一整套配套的：
 * 格式化函数认得它（显示 {@code **:**}）、{@code hasRemainingDuration()} 认得它、HUD 也会隐藏倒计时。
 * <b>1.19.2 没有这套东西</b>：
 * <ul>
 *   <li>{@code MobEffectInstance#tick} 只在 {@code duration > 0} 时才跑 {@code applyEffectTick} ——
 *       写 -1 等于「效果挂在那儿但永远不生效」，再生 / 饥饿这类需要逐 tick 结算的效果会直接哑掉；</li>
 *   <li>界面上任何按 {@code duration} 算时间的地方（原版背包效果列表、HUD 效果条、其它模组的效果显示）
 *       都会把 -1 算成 {@code 0:00}；</li>
 *   <li>原版 HUD 用的是 {@code StringUtil.formatTickDuration(duration)}，<b>不看</b> {@code noCounter}。</li>
 * </ul>
 *
 * <h2>这里的做法</h2>
 * 给一个「足够长的真实时长」并打上 {@code noCounter} 标记：
 * <ul>
 *   <li>{@code duration > 0} → 逐 tick 结算正常工作；</li>
 *   <li>调用方每次 tick / 定期续期（夜视、隐身、幸运都是每 tick 刷），所以永远不会真的到期；</li>
 *   <li>{@code noCounter} 让用 {@code MobEffectUtil.formatDuration} 的地方显示 {@code **:**}
 *       （原版背包效果列表、提示框），而不是 {@code 0:00}；</li>
 *   <li>{@code noCounter} 同时充当「这个效果是本模组给的」标记 —— 关闭功能时只清自己写的，
 *       不会误删别的模组施加的同类效果。</li>
 * </ul>
 */
public final class PermanentEffects {

    /**
     * 续期用的时长：1 小时。
     * <p>
     * 取大值是为了「即使某条路径漏了续期也不会突然掉效果」；真正的无限由调用方的每 tick 续期保证。
     */
    public static final int DURATION = 20 * 60 * 60;

    private PermanentEffects() {
    }

    /** 构造一个「常驻」效果实例（长时长 + noCounter）。 */
    public static MobEffectInstance of(MobEffect effect, int amplifier,
                                       boolean ambient, boolean visible, boolean showIcon) {
        MobEffectInstance instance = new MobEffectInstance(effect, DURATION, amplifier, ambient, visible, showIcon);
        instance.setNoCounter(true);
        return instance;
    }

    /** 最常用组合：不显示粒子、显示图标。 */
    public static MobEffectInstance of(MobEffect effect, int amplifier) {
        return of(effect, amplifier, false, false, true);
    }

    /**
     * 这个效果是不是本模组给的常驻效果。
     * <p>
     * 1.19.2 的 {@code MobEffectInstance#update} 合并时<b>不会</b>复制 {@code noCounter}，
     * 所以玩家身上已有的普通效果被我们续期后标记可能仍在旧实例上 —— 判断时两种口径都认：
     * {@code noCounter} 或负时长（早期版本写进去的 -1）。
     */
    public static boolean isPermanent(MobEffectInstance instance) {
        return instance != null && (instance.isNoCounter() || instance.getDuration() < 0);
    }

    /**
     * 把玩家身上该效果的实例补上 {@code noCounter} 标记。
     * <p>
     * {@code forceAddEffect} 走的是合并逻辑：若目标身上已有同等级、剩余时间更长的普通效果，
     * 原版只会延长时长而保留旧实例，标记就丢了 → 界面上又出现倒计时。补一下最省事。
     */
    public static void markPermanent(MobEffectInstance instance) {
        if (instance != null) {
            instance.setNoCounter(true);
        }
    }
}
