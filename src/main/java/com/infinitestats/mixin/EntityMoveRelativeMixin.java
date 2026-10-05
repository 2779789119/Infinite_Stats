package com.infinitestats.mixin;

import com.infinitestats.handler.MobilityHandler;
import com.infinitestats.stats.PlayerStatsProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 「游泳速度」加成。
 * <p>
 * 为什么必须走 Mixin：
 * <ol>
 *   <li>玩家的移动是客户端权威的，服务端改 {@code deltaMovement} 客户端收不到 ——
 *       原来的服务端实现（每 tick 把速度乘 (1+bonus)）在玩家身上等于没写；</li>
 *   <li>原版水里的横向速度来自 {@code LivingEntity#travel} 里写死的
 *       {@code moveRelative(0.02F, 输入)}（0.02 是硬编码的输入加速度，Depth Strider 只是在它基础上插值），
 *       没有任何属性通道可以放大它；</li>
 *   <li>如果改成"每 tick 把整条速度乘 (1+bonus)"，会和原版自己的衰减
 *       {@code setDeltaMovement(delta.multiply(0.8, 0.8, 0.8))} 互相累积：
 *       终端速度 = a·d/(1−d·(1+bonus))，倍率 > 1.25 时分母变负 → 指数发散（越游越快 / 抖成鬼畜）。</li>
 * </ol>
 * 这里只放大"本次 {@code moveRelative} 加进去的那一项输入加速度"，于是
 * 终端速度 = a·(1+bonus)·d/(1−d)，与原版成正比、线性、不发散。
 */
@Mixin(Entity.class)
public abstract class EntityMoveRelativeMixin {

    /**
     * 改掉 {@code moveRelative} 里的 {@code setDeltaMovement(delta.add(输入向量))}：
     * 把加进去的那一项按加成放大后再合并，其余速度（已有动量、其它模组加的）原样保留。
     */
    @Redirect(method = "moveRelative(FLnet/minecraft/world/phys/Vec3;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"))
    private void infinitestats$scaleSwimAcceleration(Entity self, Vec3 newDelta) {
        Vec3 current = self.getDeltaMovement();
        float multiplier = infinitestats$swimMultiplier(self);
        if (multiplier <= 1.0f) {
            self.setDeltaMovement(newDelta);
            return;
        }
        // newDelta = current + 输入加速度  →  差值就是本次要放大的那一项
        Vec3 input = newDelta.subtract(current);
        self.setDeltaMovement(current.add(input.scale((double) multiplier)));
    }

    /** 玩家、在水中、非骑乘、非飞行、且加了游泳速度点时返回 1+bonus，否则返回 1。 */
    private static float infinitestats$swimMultiplier(Entity entity) {
        if (!(entity instanceof Player player)) return 1.0f;
        if (!player.isInWater() || player.isPassenger()) return 1.0f;
        if (player.getAbilities().flying) return 1.0f;
        return player.getCapability(PlayerStatsProvider.PLAYER_STATS)
                .map(MobilityHandler::getSwimSpeedMultiplier)
                .orElse(1.0f);
    }
}
