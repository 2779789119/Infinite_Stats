package com.infinitestats.mixin;

import com.infinitestats.handler.DefenseHandler;
import com.infinitestats.network.NetworkHandler;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 玩家死亡的总闸门：把「致死闪避 / 格挡」的判定提到死亡流程之前。
 * <p>
 * <b>为什么不用事件</b>：{@code LivingDeathEvent} 是原版在 {@code die()} 内部派发的，任何模组都能用
 * {@code EventPriority.LOWEST + setCanceled(false)} 把别人的取消重新放行。事件是「排队」的，
 * 排在最后的人说了算，先注册的一方永远被动。
 * <p>
 * <b>为什么选 {@code Player#die} 而不是 {@code LivingEntity#die}</b>：{@code Player} 覆写了 {@code die}，
 * 第一行就是 {@code super.die(...)}，之后还有困难模式掉落、死亡动画广播等收尾。若只拦 {@code LivingEntity#die}，
 * {@code Player.die} 在 super 返回后仍会继续执行。拦在 {@code Player#die} 的 HEAD，整个死亡流程
 * （含 {@code super.die} 里的掉落、{@code dead} 标记、死亡界面）一行都不会执行。
 * <p>
 * <b>通用性</b>：不绑定任何第三方类名。任何模组、任何方式想让玩家死，最终都要走到这里；
 * 对方在 {@code die} 之前做的副作用（清背包、锁魂、写名单）管不到，但「死」这一件事能兜住。
 * <p>
 * 判定复用 {@link DefenseHandler#tryLethalAvoid}：与事件层共用同一份按攻击来源去重的缓存，
 * 同一次攻击不会因为多了一层而让闪避率翻倍；未触发时行为与原版完全一致。
 * {@code ci.cancel()} 之后血量已由判定内部恢复到本 tick 开始的快照，不会留下 0 血状态。
 */
@Mixin(Player.class)
public abstract class PlayerDieMixin {

    @Inject(
            method = "die(Lnet/minecraft/world/damagesource/DamageSource;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void infinitestats$guardDeath(DamageSource source, CallbackInfo ci) {
        // Mixin 类不继承目标类，`this` 需要显式转回 Player 才能做类型判断。
        Player self = (Player) (Object) this;
        if (!(self instanceof ServerPlayer player)) return;

        PlayerStats stats = player.getCapability(PlayerStatsProvider.PLAYER_STATS).resolve().orElse(null);
        if (stats == null) return;

        // 判定成功时内部已经把血量恢复到快照值，这里只需要终止死亡流程。
        if (DefenseHandler.tryLethalAvoid(player, stats, source) != DefenseHandler.AVOID_NONE) {
            ci.cancel();
            NetworkHandler.syncToClient(player);
        }
    }
}
