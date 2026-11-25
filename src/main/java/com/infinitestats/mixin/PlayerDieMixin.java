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
 * 排在最后的人说了算，先注册的一方永远被动 —— 本模组在 1.20.1 分支上被这类处决模组打穿过，
 * 所以在这里改成「不参与排队」。
 * <p>
 * <b>为什么 {@code ServerPlayer} 也要注入</b>：{@code Player} 覆写了 {@code die}（第一行 {@code super.die}），
 * 但 {@code ServerPlayer} <b>又覆写了一次</b>并自己做完收尾（死亡消息、掉落、死亡包），
 * <b>不调用</b> {@code super.die}。也就是说只挂 {@code Player#die} 的话，服务端玩家的死亡
 * 一次都不会走到这里 —— 这是 1.20.1 上踩过的真实坑，1.21.1 的 {@code ServerPlayer#die} 同样如此。
 * <p>
 * <b>通用性</b>：不绑定任何第三方类名。任何模组、任何方式想让玩家死，最终都要走到这里。
 * <p>
 * 判定复用 {@link DefenseHandler#tryLethalAvoid}：与事件层共用同一份按攻击来源去重的缓存，
 * 同一次攻击不会因为多了一层而让闪避率翻倍；未触发时行为与原版完全一致。
 */
@Mixin({Player.class, ServerPlayer.class})
public abstract class PlayerDieMixin {

    /** 排查开关：{@code -Dinfinitestats.debugDeath=true}。 */
    private static final boolean DEBUG_DEATH = Boolean.getBoolean("infinitestats.debugDeath");

    @Inject(
            method = "die(Lnet/minecraft/world/damagesource/DamageSource;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void infinitestats$guardDeath(DamageSource source, CallbackInfo ci) {
        // Mixin 类不继承目标类，`this` 需要显式转回 Player 才能做类型判断。
        Player self = (Player) (Object) this;
        if (!(self instanceof ServerPlayer player)) return;

        PlayerStats stats = PlayerStatsProvider.of(player).orElse(null);
        if (stats == null) return;

        // 判定成功时内部已经把血量恢复到快照值，这里只需要终止死亡流程。
        if (DefenseHandler.tryLethalAvoid(player, stats, source) != DefenseHandler.AVOID_NONE) {
            stats.clearDeathStarted();
            ci.cancel();
            NetworkHandler.syncToClient(player);
            return;
        }

        // 人还活着却被要求 die()：原版只会在血量归零时调 die，这条路径只可能来自外部的「强制死亡」。
        // 不发死亡包、不掉落、不计死亡次数、不进死亡界面，也不消耗闪避 / 复活冷却。
        // 真死（血量已归零）完全不受影响。
        if (player.getHealth() > 0.0F) {
            if (DEBUG_DEATH) {
                System.out.println("[infinitestats] cancel forced die for living player: "
                        + player.getName().getString() + " health=" + player.getHealth());
            }
            ci.cancel();
            return;
        }

        // 死亡流程放行：给零血兜底 / 复活兜底留一个明确标记（Pose.DYING 对玩家不成立）
        stats.markDeathStarted();
    }
}
