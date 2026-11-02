package com.atsuishio.superbwarfare.entity.vehicle.base;

/**
 * <b>Superb Warfare 的编译期存根（stub）—— 运行时永远不会用到这个类。</b>
 * <p>
 * 与另外几份存根同一用途（见 {@code com.tacz.guns.item.ModernKineticGunScriptAPI} 的长篇说明）：
 * Mixin 注解处理器要求 {@code @Mixin} 的目标类在<b>编译期</b>可解析，而 Superb Warfare
 * 对本模组只是「装了才生效」的可选运行时联动。
 * <p>
 * 这里是<b>载具基类</b>（Kotlin 抽象类；真实实现 {@code extends net.minecraft.world.entity.Entity}，
 * 所以它<b>不是</b> {@code LivingEntity}，血量自带一套同步数据 {@code HEALTH}）。
 * 本模组「无限载具血量」的落点是 {@code setHealth(float)} ——
 * 已用 {@code javap} 核对：全类只有它把血量写进 {@code HEALTH}（写入时钳到
 * {@code [-最大血量-10, 最大血量]}），把它按 {@code @ModifyVariable} 改成「写满血」即
 * 「载具永远满血、也永远打不死」。
 * <p>
 * 电量那一半不在这里（载具电量是 Forge Energy 能力，走
 * {@code capability/energy/VehicleEnergyStorage}，见那份存根）。
 * <p>
 * 维护约定：包名 / 类名 / 签名必须与 Superb Warfare 0.8.x 实际实现一致
 * （已对着 {@code superbwarfare-0.8.9.2-mc1.20.1} 的 jar 用 {@code javap} 核对）。
 */
public class VehicleEntity {

    /** 当前血量（读的就是同步数据 {@code HEALTH}，客户端也拿得到）。 */
    public float getHealth() {
        throw new UnsupportedOperationException("compile-time stub");
    }

    /** 写入血量：真实实现会钳到 {@code [-最大血量-10, 最大血量]} 再同步给客户端。 */
    public void setHealth(float health) {
        throw new UnsupportedOperationException("compile-time stub");
    }

    /** 血量上限（由该载具的数据配置算出）。 */
    public float getMaxHealth() {
        throw new UnsupportedOperationException("compile-time stub");
    }
}
