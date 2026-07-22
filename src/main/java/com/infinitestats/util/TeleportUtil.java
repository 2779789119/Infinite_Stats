package com.infinitestats.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.event.ForgeEventFactory;

import java.lang.reflect.Method;

/**
 * 强制跨维度传送工具。
 *
 * <p>原版 {@code ServerPlayer.teleportTo(ServerLevel, ...)} 在进入新维度前会经过
 * {@code ForgeHooks.onTravelToDimension}，该门控会抛出可被外部取消的
 * {@code EntityTravelToDimensionEvent}——整合包里的「维度进入权限」正是靠取消该事件来拦截进入。</p>
 *
 * <p>本工具复制 {@code teleportTo} 的跨维度分支逻辑，但<b>跳过该门控判断</b>，从而强制传送进去，
 * 无需任何外部维度权限。是否允许传送仍由调用方（本模组的 {@code cross_dimension_teleport} 开关属性）负责。</p>
 */
public final class TeleportUtil {

    private TeleportUtil() {}

    private static final Method TRIGGER_DIM_CHANGE_TRIGGERS;

    static {
        Method m = null;
        try {
            m = ServerPlayer.class.getDeclaredMethod("triggerDimensionChangeTriggers", ServerLevel.class);
            m.setAccessible(true);
        } catch (Exception ignored) {
            m = null;
        }
        TRIGGER_DIM_CHANGE_TRIGGERS = m;
    }

    public static void forceTeleportTo(ServerPlayer player, ServerLevel target,
                                       double x, double y, double z, float yRot, float xRot) {
        player.setCamera(player);
        player.stopRiding();

        if (target == player.level()) {
            player.connection.teleport(x, y, z, yRot, xRot);
            return;
        }

        // 复制 ServerPlayer.teleportTo 的跨维度分支，但跳过 ForgeHooks.onTravelToDimension 这道门控
        ServerLevel oldLevel = player.serverLevel();
        var leveldata = target.getLevelData();

        player.connection.send(new ClientboundRespawnPacket(
                target.dimensionTypeId(),
                target.dimension(),
                BiomeManager.obfuscateSeed(target.getSeed()),
                player.gameMode.getGameModeForPlayer(),
                player.gameMode.getPreviousGameModeForPlayer(),
                target.isDebug(),
                target.isFlat(),
                (byte) 3,
                player.getLastDeathLocation(),
                player.getPortalCooldown()));

        player.connection.send(new ClientboundChangeDifficultyPacket(leveldata.getDifficulty(), leveldata.isDifficultyLocked()));
        player.server.getPlayerList().sendPlayerPermissionLevel(player);
        oldLevel.removePlayerImmediately(player, Entity.RemovalReason.CHANGED_DIMENSION);
        player.revive();
        player.moveTo(x, y, z, yRot, xRot);
        player.setServerLevel(target);
        target.addDuringCommandTeleport(player);

        if (TRIGGER_DIM_CHANGE_TRIGGERS != null) {
            try {
                TRIGGER_DIM_CHANGE_TRIGGERS.invoke(player, oldLevel);
            } catch (Exception ignored) {
                // 触发进度类逻辑失败不影响传送本身
            }
        }

        player.connection.teleport(x, y, z, yRot, xRot);
        player.gameMode.setLevel(target);
        player.server.getPlayerList().sendLevelInfo(player, target);
        player.server.getPlayerList().sendAllPlayerInfo(player);
        ForgeEventFactory.firePlayerChangedDimensionEvent(player, oldLevel.dimension(), target.dimension());
    }
}
