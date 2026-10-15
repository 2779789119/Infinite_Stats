package com.atsuishio.superbwarfare.capability.energy;

/**
 * <b>Superb Warfare（卓越前线）的编译期存根 —— 运行时永远不会用到这个类。</b>
 * <p>
 * 这类既是载具内部扣电 / 读数的对象，也是载具对外的能量能力载体：
 * 引擎行驶、能量弹药、外部机器抽电最终都走 {@link #extractEnergy(int, boolean)}，
 * 所以 {@code SbwVehicleEnergyStorageMixin} 只拦这一处即全覆盖。
 * <p>
 * <b>注意</b>：卓越前线在 1.21.1 上换用了 NeoForge 的能力体系，这个类名 / 包路径可能已经变了。
 * 拿不到 1.21.1 的 jar 复核，所以这条注入点写成可选 —— 对不上时「无限能源」的载具那半会退化成
 * 空操作，其余功能与 {@code EnergyCompat} 的通用补满都不受影响。
 */
public class VehicleEnergyStorage {

    /** 抽取电量；返回实际抽到的量。 */
    public int extractEnergy(int maxExtract, boolean simulate) {
        throw new UnsupportedOperationException("compile-time stub");
    }
}
