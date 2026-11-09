package com.infinitestats.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 死亡界面的最后一道闸门：在客户端拆包的位置直接丢掉「不该显示」的死亡包。
 * <p>
 * <b>为什么要拦这里</b>：{@code DeathScreen} 只有一个创建入口 ——
 * {@code ClientPacketListener#handlePlayerCombatKill}。只要在这里取消，无论死亡包是谁发的
 * （原版 {@code ServerPlayer#die}、某个 boss 模组自己 {@code connection.send(...)} 强制播放死亡、
 * 还是每 tick 重发一次），界面都不会再弹出来，也不会出现「关了又开」的抖动。
 * <p>
 * <b>判定口径</b>：本地玩家血量已经大于 0（服务端把人救活了 / 玩家根本没死）就丢弃。
 * 真死时客户端血量会被 {@code ClientboundSetHealthPacket} 同步成 0，这里不会误拦，
 * 玩家仍然能正常看到死亡界面并重生。
 * <p>
 * 与 {@code ClientEventHandler#onClientTick} 里的「已弹出就收掉」互补：那一处处理
 * 「包先到、人后救活」（收到包的那一刻血量还是 0），这一处处理「之后又被重发」。
 * 排查开关：{@code -Dinfinitestats.debugDeath=true}。
 */
@Mixin(ClientPacketListener.class)
public class CombatKillPacketMixin {

    private static final boolean DEBUG_DEATH = Boolean.getBoolean("infinitestats.debugDeath");

    @Inject(
            method = "handlePlayerCombatKill(Lnet/minecraft/network/protocol/game/ClientboundPlayerCombatKillPacket;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void infinitestats$dropStaleDeathScreen(ClientboundPlayerCombatKillPacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        if (packet.getPlayerId() != player.getId()) return;
        if (player.getHealth() > 0) {
            if (DEBUG_DEATH) {
                System.out.println("[infinitestats] drop combat-kill packet: player=" + player.getName().getString()
                        + " health=" + player.getHealth()
                        + " message=" + packet.getMessage().getString());
            }
            if (mc.screen instanceof DeathScreen) {
                mc.setScreen(null);
            }
            player.deathTime = 0;
            ci.cancel();
        }
    }
}
