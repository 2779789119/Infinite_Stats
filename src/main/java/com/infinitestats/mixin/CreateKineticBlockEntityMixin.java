package com.infinitestats.mixin;

import com.infinitestats.compat.CreateStressCompat;
import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Create（机械动力）联动「无限应力」—— <b>存档不落改写后的产能</b>。
 * <p>
 * 配套 {@link CreateKineticNetworkMixin}：那一半把网络的产能改写成一个「无限」值（10 亿），
 * 这个值会顺着 {@code KineticNetwork.sync()} 写进每个成员方块的 {@code capacity} 字段。
 * 而 {@code KineticBlockEntity.write(compound, clientPacket)} 是<b>同一个字段</b>的两个出口
 * （已用 {@code javap} 核对：{@code writeClient} 传 {@code true} 发包给客户端、
 * {@code SmartBlockEntity.m_183515_}（saveAdditional）传 {@code false} 写存档）：
 * <ul>
 *   <li>{@code clientPacket = true}：<b>照原样发</b>。客户端正是靠这个数字自己重算超载判定
 *       （{@code KineticBlockEntity.read}：{@code overStressed = capacity < stress}），
 *       改了它两边才会一致；</li>
 *   <li>{@code clientPacket = false}：<b>换回真值再写</b>。否则 10 亿会被烤进存档，
 *       重登后 {@code initFromTE} 会把它当成「未加载区块的产能」收进网络（{@code unloadedCapacity}），
 *       从此这张网络永久不过载 —— 属性关掉、甚至卸载本模组都退不回来，等于污染存档。</li>
 * </ul>
 * 整个替换只发生在这一次调用的栈帧里（HEAD 换出去、RETURN 换回来），字段本身始终是真值以外的
 * 那个「运行时值」，游戏逻辑不受影响。真值由 {@link CreateStressCompat#rememberRealCapacity}
 * 在改写产能时记下；没记过（例如方块刚放下、网络还没算过）就原样写，不做任何猜测。
 * <p>
 * 本 Mixin 只在装了机械动力时才会被应用（{@code InfiniteStatsMixinPlugin} 按目标包名判定），
 * 注入点可选（{@code require = 0}）；import 的目标类来自编译期存根 {@code src/compatApiStub}。
 */
@Mixin(value = KineticBlockEntity.class, remap = false)
public abstract class CreateKineticBlockEntityMixin {

    /** 目标类自己的字段（{@code @Shadow} 只认目标类自身声明的成员，所以这里可以影子化）。 */
    @Shadow
    protected float capacity;

    @Unique
    private float infinitestats$capacityBackup;

    @Unique
    private boolean infinitestats$capacitySwapped;

    /**
     * 写存档时把「无限产能」换回真值（发包给客户端时不动）。
     * <p>
     * 先无条件把「本轮已换出」标记归零，再判断：万一上一轮被异常打断，这里也能重新对齐，
     * 不会出现「标记残留 → 后面的存档一直写不了真值」。
     */
    @Inject(method = "write", at = @At("HEAD"), require = 0, remap = false)
    private void infinitestats$writeRealCapacityToDisk(CompoundTag compound, boolean clientPacket, CallbackInfo ci) {
        infinitestats$capacitySwapped = false;

        if (clientPacket) return;                       // 发客户端：保留改写值，客户端靠它算超载

        KineticBlockEntity self = (KineticBlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level == null || level.isClientSide) return; // 只在服务端存档路径上动手

        if (capacity < CreateStressCompat.INFINITE_CAPACITY) return;   // 没被本模组改写过
        if (!self.hasNetwork()) return;

        KineticNetwork network = self.getOrCreateNetwork();
        float real = CreateStressCompat.realCapacityOf(network);
        if (Float.isNaN(real)) return;                  // 没记过真值：不动它，避免凭空写 0

        infinitestats$capacityBackup = capacity;
        capacity = real;
        infinitestats$capacitySwapped = true;
    }

    /** 本次序列化结束后把字段换回来，运行时（HUD / 应力表 / 逻辑判定）继续用改写值。 */
    @Inject(method = "write", at = @At("RETURN"), require = 0, remap = false)
    private void infinitestats$restoreInflatedCapacity(CompoundTag compound, boolean clientPacket, CallbackInfo ci) {
        if (infinitestats$capacitySwapped) {
            capacity = infinitestats$capacityBackup;
            infinitestats$capacitySwapped = false;
        }
    }
}
