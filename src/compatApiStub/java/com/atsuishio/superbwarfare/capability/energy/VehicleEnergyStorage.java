package com.atsuishio.superbwarfare.capability.energy;

/**
 * <b>Superb Warfare 的编译期存根（stub）—— 运行时永远不会用到这个类。</b>
 * <p>
 * 与另外几份存根同一用途（见 {@code com.tacz.guns.item.ModernKineticGunScriptAPI} 的长篇说明）。
 * <p>
 * 这里是<b>载具的电量存储</b>（Kotlin 类，真实实现 {@code extends SyncedEntityEnergyStorage}，
 * 后者 {@code extends net.minecraftforge.energy.EnergyStorage}）—— 它既是载具内部读数 / 扣电的对象，
 * 也是载具 {@code ForgeCapabilities.ENERGY} 能力暴露出去的对象（同一个实例）。
 * <p>
 * 「无限能源」就挂在这个类的 {@code extractEnergy(int, boolean)} 上：已用 {@code javap} 核对，
 * 它（连同父类的实现）是载具电量<b>唯一会变小</b>的地方 ——
 * 载具自己的扣电入口 {@code VehicleEntity.consumeEnergy(int)}（引擎行驶、载具武器）
 * 与按能力来扣电的路径（能量弹药策略 {@code EnergyAmmoStrategy}、外部机器抽电）最终都走到这里，
 * 所以拦一处即全覆盖。
 * <p>
 * 维护约定：包名 / 类名 / 签名必须与 Superb Warfare 0.8.x 实际实现一致
 * （已对着 {@code superbwarfare-0.8.9.2-mc1.20.1} 的 jar 用 {@code javap} 核对）。
 */
public class VehicleEnergyStorage {

    /**
     * 抽取电量（Forge Energy 口径）。
     * <p>
     * 真实实现里有 {@code this.capacity = maxEnergyStored; this.maxExtract = maxEnergyStored;}
     * 那两行「按数据配置放宽上限」的调整，再交给父类扣值并同步进载具的同步数据 ——
     * 本模组只拦「扣值」这一步，因此不影响它的读数口径。
     *
     * @param maxExtract 最多想抽多少
     * @param simulate   true 只试算、不真扣
     * @return 实际抽到的量
     */
    public int extractEnergy(int maxExtract, boolean simulate) {
        throw new UnsupportedOperationException("compile-time stub");
    }
}
