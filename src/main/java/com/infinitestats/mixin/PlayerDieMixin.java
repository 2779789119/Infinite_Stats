package com.infinitestats.mixin;

import com.infinitestats.handler.DefenseHandler;
import com.infinitestats.network.NetworkHandler;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
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
 * <b>为什么挂在 {@link ServerPlayer} 而不是 {@code Player}</b>：1.20.1 的 {@code ServerPlayer} 覆写了
 * {@code die(DamageSource)} 且<b>不调用 {@code super.die}</b>（原版把死亡收尾整体移进了子类），
 * 所以挂父类 {@code Player#die} 的注入对服务端玩家一次都不会执行 —— 这是一版走空的根因。
 * 服务端玩家死亡必经 {@code ServerPlayer#die}，挂这里才是真正的入口。
 * <p>
 * <b>通用性</b>：不绑定任何第三方类名。任何模组、任何方式想让玩家死，最终都要走到这里；
 * 对方在 {@code die} 之前做的副作用（清背包、锁魂、写名单）管不到，但「死」这一件事能兜住。
 * <p>
 * 判定复用 {@link DefenseHandler#tryLethalAvoid}：与事件层共用同一份按攻击来源去重的缓存，
 * 同一次攻击不会因为多了一层而让闪避率翻倍；未触发时行为与原版完全一致。
 * {@code ci.cancel()} 之后血量已由判定内部恢复到本 tick 开始的快照，不会留下 0 血状态。
 * <p>
 * 没拦截住时打上「死亡流程已放行」标记：{@code ServerPlayer#die} 随后会给客户端发死亡包，
 * 零血兜底不能再把这种 0 血当成「被外部清零」拉回来（否则死亡界面挂着、服务端角色还活着）。
 */
@Mixin(ServerPlayer.class)
public abstract class PlayerDieMixin {

    private static final boolean DEBUG_DEATH = Boolean.getBoolean("infinitestats.debugDeath");

    @Inject(
            method = "die(Lnet/minecraft/world/damagesource/DamageSource;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void infinitestats$guardDeath(DamageSource source, CallbackInfo ci) {
        // Mixin 类不继承目标类，`this` 需要显式转回 ServerPlayer。
        ServerPlayer player = (ServerPlayer) (Object) this;

        // 玩家还活着却被要求 die()：原版只会在血量归零时调用 die，这条路径只可能来自外部
        // 「强制死亡 / 强制播放死亡界面」的实现。直接终止这次调用 —— 不发死亡包、不掉落、
        // 不进死亡界面，也不消耗闪避 / 复活冷却。
        if (player.getHealth() > 0) {
            if (DEBUG_DEATH) {
                System.out.println("[infinitestats] cancel forced die on living player: "
                        + player.getName().getString() + " health=" + player.getHealth()
                        + " source=" + source.getMsgId());
            }
            ci.cancel();
            return;
        }

        PlayerStats stats = player.getCapability(PlayerStatsProvider.PLAYER_STATS).resolve().orElse(null);
        if (stats == null) return;

        // 判定成功时内部已经把血量恢复到快照值，这里只需要终止死亡流程。
        if (DefenseHandler.tryLethalAvoid(player, stats, source) != DefenseHandler.AVOID_NONE) {
            ci.cancel();
            NetworkHandler.syncToClient(player);
            return;
        }

        // 没拦住：接下来就是真正的死亡（发死亡包、掉物品、进死亡界面）。标记给零血兜底看，
        // 免得它在下个 tick 末尾把服务端血量拉回来，变成「客户端已死、服务端还活着」。
        stats.markDeathStarted();
    }
}
