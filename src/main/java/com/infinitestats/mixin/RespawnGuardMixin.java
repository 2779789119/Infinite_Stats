package com.infinitestats.mixin;

import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 复活请求放行：原版 {@code handleClientCommand} 在 {@code PERFORM_RESPAWN} 分支上有一道
 * {@code if (this.player.getHealth() > 0.0F) return;} —— 服务端血量只要不是 0，点复活就完全没反应。
 * <p>
 * 这个判断的本意是防止「活着的人用复活包瞬移回出生点」。但一旦死亡界面已经弹给客户端、
 * 服务端又被任何一方（其它模组 / 无敌补血 / 延迟复活）把血量拉回正数，玩家就会永久卡在
 * 死亡界面上：界面关不掉，点复活也无效，只能退出重连。
 * <p>
 * 这里只对「确实走过一次死亡流程」的玩家放行（{@link PlayerStats#isDeathStarted()}），
 * 每局死亡最多换一次复活，不影响原本的防瞬移语义；标记不会过继给重生后的新实体。
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class RespawnGuardMixin {

    private static final boolean DEBUG_DEATH = Boolean.getBoolean("infinitestats.debugDeath");

    @Redirect(
            method = "handleClientCommand(Lnet/minecraft/network/protocol/game/ServerboundClientCommandPacket;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;getHealth()F")
    )
    private float infinitestats$allowRespawnAfterDeath(ServerPlayer player) {
        float health = player.getHealth();
        if (health > 0.0F) {
            boolean died = PlayerStatsProvider.of(player)
                    .map(PlayerStats::isDeathStarted).orElse(false);
            if (died) {
                if (DEBUG_DEATH) {
                    System.out.println("[infinitestats] allow respawn for revived player: "
                            + player.getName().getString() + " health=" + health);
                }
                // 假装血量是 0，让原版走正常复活流程（新建实体 / 传送重生点 / 下发 Respawn 包）
                return 0.0F;
            }
        }
        return health;
    }
}
