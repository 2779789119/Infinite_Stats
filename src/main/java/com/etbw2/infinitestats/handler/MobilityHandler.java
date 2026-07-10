package com.etbw2.infinitestats.handler;

import com.etbw2.infinitestats.stats.PlayerStats;
import com.etbw2.infinitestats.stats.StatCategory;
import com.etbw2.infinitestats.stats.StatType;
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
        // 每5tick更新飞行状态
        if (tickCount % 5 == 0) {
            updateFlight(player, stats);
            updateStepHeight(player, stats);
        }
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

        // 飞行速度调整
        if (player.getAbilities().flying) {
            float flySpeedBonus = stats.getStatValue(StatType.fromId("fly_speed"));
            if (flySpeedBonus > 0) {
                // 飞行速度通过Attribute处理，这里只是辅助
            }
        }
    }

    /**
     * 更新自动跨越高度
     */
    private void updateStepHeight(ServerPlayer player, PlayerStats stats) {
        boolean autoStep = stats.isToggleActive("auto_step");
        if (autoStep && player.maxUpStep() < 1.0f) {
            player.setMaxUpStep(1.0f);
        } else if (!autoStep && player.maxUpStep() > 0.6f) {
            player.setMaxUpStep(0.6f);
        }
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

    /**
     * 处理冲刺冷却缩减
     */
    public static int getDashCooldownReduction(PlayerStats stats) {
        float reduction = stats.getStatValue(StatType.fromId("dash_cooldown"));
        return (int) Math.abs(reduction * 100);
    }
}