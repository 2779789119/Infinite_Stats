package com.simibubi.create.content.kinetics;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import java.util.Map;

/**
 * <b>Create（机械动力）的编译期存根（stub）—— 运行时永远不会用到这个类。</b>
 * <p>
 * 与 TACZ / Superb Warfare 那几份存根同一用途（见 {@code com.tacz.guns.item.ModernKineticGunScriptAPI}
 * 的长篇说明）：Mixin 注解处理器要求 {@code @Mixin} 的目标类必须在<b>编译期</b>类路径上，
 * 而机械动力对本模组只是「装了才生效」的可选运行时联动。这里只把<b>被注入 / 被读取的成员</b>
 * 按真实签名抄一遍，产物只进编译期类路径、不会进 jar。
 * <p>
 * 本模组的「无限应力」把落点选在 {@link #calculateCapacity()} 的<b>返回值</b>上：它是整个动力网络
 * 产能的唯一汇总点（{@code updateCapacity()} / {@code updateNetwork()} 都取它的结果），
 * 改一处就能让「成员方块 → 应力表 → 同步给客户端的包」全部拿到同一个「无限产能」。
 * 判定归属要用 {@link #members}（{@code add} / {@code addSilently} 都会把方块同时放进
 * {@code members}，动力源另存一份到 {@link #sources}）。
 * <p>
 * 维护约定：<b>签名必须与 Create 1.20.1 的实际实现一致</b>
 * （已对着 {@code create-1.20.1-6.0.8.jar} 用 {@code javap} 逐项核对）。改动这里前先确认真实 jar。
 */
public class KineticNetwork {

    /** 动力源方块 → 它提供的产能（未经转速折算）。 */
    public Map<KineticBlockEntity, Float> sources;

    /** 网络成员方块 → 它消耗的应力（未经转速折算）。动力源也在其中。 */
    public Map<KineticBlockEntity, Float> members;

    /** 汇总产能（网络成员各自的产能 + 未加载区块的产能）。「无限应力」在此返回值上做手脚。 */
    public float calculateCapacity() {
        throw new UnsupportedOperationException("compile-time stub");
    }
}
