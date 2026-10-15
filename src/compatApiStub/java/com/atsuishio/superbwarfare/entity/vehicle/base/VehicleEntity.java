package com.atsuishio.superbwarfare.entity.vehicle.base;

/**
 * <b>Superb Warfare（卓越前线）的编译期存根 —— 运行时永远不会用到这个类。</b>
 * <p>
 * 用途与维护约定同上。上游是 Kotlin 类、真实实现 {@code extends Entity}（所以载具不是 LivingEntity），
 * 但这里只声明被注入的那几个方法 —— Mixin 不继承目标类，不需要完整的继承链。
 */
public class VehicleEntity {

    public float getHealth() {
        throw new UnsupportedOperationException("compile-time stub");
    }

    /** 载具血量的<b>唯一写入口</b>：{@code SbwVehicleMixin} 在这里改入参。 */
    public void setHealth(float health) {
        throw new UnsupportedOperationException("compile-time stub");
    }

    public float getMaxHealth() {
        throw new UnsupportedOperationException("compile-time stub");
    }
}
