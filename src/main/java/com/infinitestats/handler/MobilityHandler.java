package com.infinitestats.handler;

import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.StatCategory;
import com.infinitestats.stats.StatType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * 机动类属性处理器
 * 处理：飞行、移动速度、跳跃、游泳、自动跨越
 */
public class MobilityHandler implements StatEffectHandler {

    @Override
    public String getId() {
        return "mobility";
    }

    @Override
    public StatType[] getSupportedStats() {
        return StatType.getByCategory(StatCategory.MOBILITY);
    }

    @Override
    public void onTick(ServerPlayer player, PlayerStats stats, long tickCount) {
        // 每5tick更新飞行状态和跨越高度
        if (tickCount % 5 == 0) {
            updateFlight(player, stats, false);
            updateStepHeight(player, stats);
        }

        // 每tick更新游泳速度
        updateSwimSpeed(player, stats);
    }

    @Override
    public void onLogin(ServerPlayer player, PlayerStats stats) {
        updateFlight(player, stats, true);
        updateStepHeight(player, stats);
    }

    @Override
    public void onRespawn(ServerPlayer player, PlayerStats stats) {
        updateFlight(player, stats, true);
        updateStepHeight(player, stats);
    }

    @Override
    public void onDimensionChange(ServerPlayer player, PlayerStats stats) {
        // 跨维度后客户端 LocalPlayer 会被 Respawn 包重建、能力被重置为默认值，
        // 必须无条件重发能力包（详见 updateFlight 的 forceSync 说明）
        updateFlight(player, stats, true);
        updateStepHeight(player, stats);
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    /**
     * 更新飞行能力（与原版创造/旁观飞行一致）
     *
     * FLY toggle ON → 始终确保 mayfly=true，flying 状态完全交给原版客户端
     * （双击跳跃切换，落地自动退出），服务端不主动接管；OFF → 仅当由本模组提供时才关闭。
     *
     * @param forceSync 客户端能力可能已被重置时（登录 / 重生 / 跨维度）传 true，无条件重发能力包。
     *                  <p>原因：客户端收到 {@code ClientboundRespawnPacket} 时会丢弃 LocalPlayer、
     *                  新建一个能力全为默认值（mayfly=false、flyingSpeed=0.05）的实例。原版
     *                  {@code ServerPlayer#changeDimension}（传送门）路径在之后补发了
     *                  {@code ClientboundPlayerAbilitiesPacket}，但 {@code ServerPlayer#teleportTo}
     *                  路径（命令跨维度、多数模组传送器、本模组 {@code TeleportUtil}）<b>没有</b>补发，
     *                  于是客户端 mayfly 永久丢失、怎么按都飞不起来。
     *                  <p>服务端记的 mayfly 仍是 true，所以只靠"状态变化才发包"无法自愈，必须在这些
     *                  时机强制重发。
     */
    private void updateFlight(ServerPlayer player, PlayerStats stats, boolean forceSync) {
        updateFlightSpeed(player, stats, forceSync);
        if (player.isCreative() || player.isSpectator()) return;

        boolean wantFly = stats.isToggleActive("fly");
        boolean weProvided = stats.isProviding("fly");

        if (wantFly) {
            // 平时只在能力状态真正变化时同步，避免周期性发包干扰客户端主导的 flying 状态；
            // forceSync 用于覆盖客户端 LocalPlayer 被重建后的能力重置
            boolean needSync = forceSync || !player.getAbilities().mayfly || !weProvided;
            player.getAbilities().mayfly = true;
            stats.setProviding("fly", true);
            if (needSync) player.onUpdateAbilities();
        } else if (weProvided) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            stats.setProviding("fly", false);
            player.onUpdateAbilities();
        }
    }

    private void updateFlightSpeed(ServerPlayer player, PlayerStats stats, boolean forceSync) {
        float bonus = stats.getStatValue("fly_speed");
        String key = "infinitestats.base_fly_speed";
        var data = player.getPersistentData();
        if (bonus != 0) {
            if (!data.contains(key)) data.putFloat(key, player.getAbilities().getFlyingSpeed());
            float speed = Math.max(0, data.getFloat(key) * (1.0f + bonus));
            // forceSync：客户端飞行速度同样会在 LocalPlayer 重建时被重置回 0.05
            if (forceSync || player.getAbilities().getFlyingSpeed() != speed) {
                player.getAbilities().setFlyingSpeed(speed);
                player.onUpdateAbilities();
            }
            stats.setProviding("fly_speed", true);
        } else if (stats.isProviding("fly_speed")) {
            player.getAbilities().setFlyingSpeed(data.contains(key) ? data.getFloat(key) : 0.05f);
            data.remove(key);
            stats.setProviding("fly_speed", false);
            player.onUpdateAbilities();
        }
    }

    /**
     * 更新自动跨越高度（含 step_height 加成）
     */
    private void updateStepHeight(ServerPlayer player, PlayerStats stats) {
        float targetStep = 0.6f;

        // auto_step: 开关型，激活后基础跨越=1.0
        if (stats.isToggleActive("auto_step")) {
            targetStep = 1.0f;
        }

        // step_height: 百分比加成
        float stepBonus = stats.getStatValue(StatType.fromId("step_height"));
        if (stepBonus > 0) {
            targetStep *= (1.0f + stepBonus);
        }

        if (Math.abs(player.maxUpStep() - targetStep) > 0.01f) {
            player.setMaxUpStep(targetStep);
        }
    }

    /**
     * 更新游泳速度
     */
    private void updateSwimSpeed(ServerPlayer player, PlayerStats stats) {
        float swimSpeed = stats.getStatValue(StatType.fromId("swim_speed"));
        if (swimSpeed <= 0) return;
        if (!player.isInWater()) return;

        Vec3 motion = player.getDeltaMovement();
        double hLen = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        if (hLen < 0.01) return;

        float boost = 1.0f + swimSpeed;
        player.setDeltaMovement(motion.x * boost, motion.y, motion.z * boost);
    }

    /**
     * 计算游泳速度加成
     */
    public static float getSwimSpeedMultiplier(PlayerStats stats) {
        return 1.0f + stats.getStatValue(StatType.fromId("swim_speed"));
    }

    /**
     * 计算跳跃高度加成
     */
    public static float getJumpMultiplier(PlayerStats stats) {
        return 1.0f + stats.getStatValue(StatType.fromId("jump_height"));
    }

}