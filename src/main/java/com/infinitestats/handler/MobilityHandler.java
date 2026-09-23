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
            updateFlight(player, stats);
            updateStepHeight(player, stats);
        }

        // 每tick更新游泳速度
        updateSwimSpeed(player, stats);
    }

    @Override
    public void onLogin(ServerPlayer player, PlayerStats stats) {
        updateFlight(player, stats);
        updateStepHeight(player, stats);
    }

    @Override
    public void onRespawn(ServerPlayer player, PlayerStats stats) {
        updateFlight(player, stats);
        updateStepHeight(player, stats);
    }

    @Override
    public void onDimensionChange(ServerPlayer player, PlayerStats stats) {
        updateFlight(player, stats);
        updateStepHeight(player, stats);
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    /**
     * 更新飞行能力
     * FLY toggle ON → 始终确保 mayfly=true；OFF → 仅当由本模组提供时才关闭
     */
    private void updateFlight(ServerPlayer player, PlayerStats stats) {
        updateFlightSpeed(player, stats);
        if (player.isCreative() || player.isSpectator()) return;

        boolean wantFly = stats.isToggleActive("fly");
        boolean weProvided = stats.isProviding("fly");

        if (wantFly) {
            player.getAbilities().mayfly = true;
            stats.setProviding("fly", true);
            player.onUpdateAbilities();
        } else if (weProvided) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            stats.setProviding("fly", false);
            player.onUpdateAbilities();
        }


    }

    private void updateFlightSpeed(ServerPlayer player, PlayerStats stats) {
        float bonus = stats.getStatValue("fly_speed");
        String key = "infinitestats.base_fly_speed";
        var data = player.getPersistentData();
        if (bonus != 0) {
            if (!data.contains(key)) data.putFloat(key, player.getAbilities().getFlyingSpeed());
            float speed = Math.max(0, data.getFloat(key) * (1.0f + bonus));
            if (player.getAbilities().getFlyingSpeed() != speed) {
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