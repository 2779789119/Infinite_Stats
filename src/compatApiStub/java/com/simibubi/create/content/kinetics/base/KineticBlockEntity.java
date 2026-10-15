package com.simibubi.create.content.kinetics.base;

import com.simibubi.create.content.kinetics.KineticNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * <b>Create（机械动力）的编译期存根（stub）—— 运行时永远不会用到这个类。</b>
 * <p>
 * 用途与维护约定同 {@code com.simibubi.create.content.kinetics.KineticNetwork} 的说明。
 * 这里只列「无限应力」用到的成员：
 * <ul>
 *   <li>{@link #capacity}：网络写进来的产能，会被序列化进存档 ——
 *       「无限应力」改写的是内存里的这个值，<b>存档时必须还原成真值</b>
 *       （见 {@code mixin/CreateKineticBlockEntityMixin}）；</li>
 *   <li>{@link #hasNetwork()} / {@link #getOrCreateNetwork()}：拿回自己所属的网络，
 *       以便取出该网络的真实产能；</li>
 *   <li>{@link #write(CompoundTag, HolderLookup.Provider, boolean)}：序列化入口，
 *       {@code clientPacket = true} 是发包给客户端、{@code false} 是写存档。</li>
 * </ul>
 * 注意本类在真实实现里继承 {@code SmartBlockEntity}（→ {@code BlockEntity}），
 * {@code getLevel()} / {@code getBlockPos()} 等成员因此可用；这里如实继承，避免调用侧到处强转。
 * <p>
 * 维护约定：<b>签名必须与目标版本 Create 的实际实现一致</b>；本分支尚未拿到 Create 1.21.1 的 jar
 * 逐项复核，因此注入点全部写成可选（{@code require = 0}），签名不符时静默退化为原版行为。
 */
public class KineticBlockEntity extends BlockEntity {

    /** 网络汇总进来的产能；{@code overStressed} 由它和 stress 比较得出。 */
    protected float capacity;

    public KineticBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** 是否已挂在某个动力网络上。 */
    public boolean hasNetwork() {
        throw new UnsupportedOperationException("compile-time stub");
    }

    /** 取（必要时创建）自己所属的动力网络。 */
    public KineticNetwork getOrCreateNetwork() {
        throw new UnsupportedOperationException("compile-time stub");
    }

    /**
     * 序列化：{@code clientPacket = true} 为发包给客户端，{@code false} 为写存档。
     * <p>
     * 1.21 起 NBT 序列化统一要带注册表（{@code HolderLookup.Provider}），所以比 1.20.1 多一个参数。
     */
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        throw new UnsupportedOperationException("compile-time stub");
    }
}
