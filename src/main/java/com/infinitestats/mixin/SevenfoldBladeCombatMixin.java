package com.infinitestats.mixin;

import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * sevenfold_blade（太一剑）联动的处决判定豁免。
 * <p>
 * <b>为什么不挂在事件上</b>：对方的处决链路完全绕开原版伤害管线 —— {@code execute()} 里直接
 * {@code setHealth(0)} + {@code die()}，不经过 {@code hurt()}，所以 {@code LivingAttackEvent} /
 * {@code LivingHurtEvent} / {@code LivingDamageEvent} 的传统防御（闪避、格挡、免疫）全部接不到。
 * 而 {@code LivingDeathEvent} 上又安排了 {@code EventPriority.LOWEST} 的
 * {@code event.setCanceled(false)}，会把任何模组（包括它自己）的「死亡取消」重新放行。
 * 事件层因此没有剩余空间，只能回到判定源头。
 * <p>
 * <b>注入点选 {@code canBeExecuted}</b>：它是 {@code execute()} 的第一行守卫，也在
 * {@code aoeAllows()} 里被调用。只要它返回 {@code false}，后续的一整套副作用都不会发生 ——
 * 终局裁决标记、吸收护盾清除、复活冷却锁死、永久死亡名单写入、秒杀、缴械、锁魂。
 * 比拦 {@code instantKill} 更靠前：那些 {@code condemn} / {@code suppress} 在秒杀之前就已经执行了。
 * <p>
 * <b>豁免范围</b>：投入了「原地复活」并解锁、或投入了闪避 / 格挡点数的玩家。语义是「免死」，
 * 不是「死了能活」—— 因为对方的压制会先把复活冷却锁死，那条路本来就无效。
 * 怪物不受影响：本注入只对 {@link ServerPlayer} 生效，其余目标照常被处决。
 * <p>
 * 目标类不在编译期依赖里（属于可选联动），所以用字符串指定；{@code remap = false} 是必须的 ——
 * 该类不属于 Minecraft，走映射转换会找不到目标。注入点可选（{@code require = 0}），
 * 对方改版后注入静默失效，但不会影响启动。
 * <p>
 * 未安装 sevenfold_blade 时本 Mixin 由 {@link InfiniteStatsMixinPlugin} 直接跳过。
 */
@Pseudo
@Mixin(targets = "cn.blockforge.generated.sevenfoldblade.combat.ArtifactCombat", remap = false)
public abstract class SevenfoldBladeCombatMixin {

    /**
     * 处决判定入口：命中豁免条件时直接判为「不可处决」。
     * <p>
     * 用完整描述符匹配：该方法有明确的 {@code (LivingEntity, LivingEntity)Z} 形态，
     * 带上描述符可以避免后续版本新增同名重载时误命中。
     */
    @Inject(
            method = "canBeExecuted(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;)Z",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private static void infinitestats$exemptFromExecution(LivingEntity entity, LivingEntity killer,
                                                          CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof ServerPlayer player)) return;
        player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
            if (isExecutionImmune(stats)) {
                // false = 不可处决，调用方（execute / aoeAllows）会直接跳过这个目标。
                cir.setReturnValue(false);
            }
        });
    }

    /**
     * 是否享受处决豁免。
     * <p>
     * 自动复活是开关型属性（投入 8 点解锁），只能用 {@code isToggleActive} 判断；
     * 闪避 / 格挡是百分比型，投入点数即生效。
     */
    private static boolean isExecutionImmune(PlayerStats stats) {
        return stats.isToggleActive("auto_revive")
                || stats.getStatValue("dodge_chance") > 0
                || stats.getStatValue("block_chance") > 0;
    }
}
