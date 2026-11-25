package com.infinitestats.handler;

import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.StatCategory;
import com.infinitestats.stats.StatType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * 机动类属性处理器
 * 处理：飞行、移动速度、跳跃、游泳、自动跨越
 */
public class MobilityHandler implements StatEffectHandler {

    /** 原版飞行速度基准（{@code Abilities#flyingSpeed} 默认值）。 */
    private static final float VANILLA_FLY_SPEED = 0.05f;

    /**
     * 能力包兜底补发周期（tick）。
     * <p>
     * 客户端 {@code LocalPlayer} 在若干路径上会被重建，{@code Abilities} 回到默认值
     * （{@code mayfly=false}）：{@code teleportTo} 类跨维度（命令、多数模组传送器）、
     * 部分模组的重生 / 换角色流程。这些路径<b>不补发</b> {@code ClientboundPlayerAbilitiesPacket}，
     * 而授权方只在「服务端状态变化」时发包 —— 服务端仍是 {@code mayfly=true}，永远觉得自己不用发，
     * 客户端却一直是 {@code false}，表现就是「能飞的角色怎么按都不起来」，且所有依赖
     * {@code mayfly} 的模组飞行一起失效。
     * <p>
     * 这里按周期把服务端真实能力补发一次，让客户端自愈。周期取 100 tick（5 秒）；
     * 发包本身不改服务端任何字段，不会和其它模组的授权抢状态。
     */
    private static final int FLIGHT_RESYNC_INTERVAL = 100;

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
        // 每 5 tick 更新飞行状态与跨越高度
        int interval = 5;
        if (tickCount % interval == 0) {
            // 兜底补发窗口：一个补发周期内必然命中且只命中一次
            long period = Math.max(FLIGHT_RESYNC_INTERVAL, interval);
            boolean resyncDue = tickCount % period < interval;
            updateFlight(player, stats, resyncDue);
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

        if (player.isCreative() || player.isSpectator()) {
            // 创造 / 旁观的能力由原版维护，绝不改字段；只在需要时把当前真实能力补发下去，
            // 覆盖 teleportTo 类跨维度不带能力包的路径
            if (forceSync) player.onUpdateAbilities();
            return;
        }

        boolean wantFly = stats.isToggleActive("fly");
        boolean weProvided = stats.isProviding("fly");

        if (wantFly) {
            // 平时只在能力状态真正变化时同步，避免周期性发包干扰客户端主导的 flying 状态；
            // forceSync（关键时刻 + 周期兜底）用于覆盖客户端 LocalPlayer 被重建后的能力重置
            boolean needSync = forceSync || !player.getAbilities().mayfly || !weProvided;
            player.getAbilities().mayfly = true;
            stats.setProviding("fly", true);
            if (needSync) player.onUpdateAbilities();
        } else if (weProvided) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            stats.setProviding("fly", false);
            player.onUpdateAbilities();
        } else if (forceSync && player.getAbilities().mayfly) {
            // 飞行能力来自其它模组：它们同样只在「状态变化」时发包，客户端被重建后不会自愈。
            // 这里按服务端真实状态补发一次 —— 只读不改，不抢任何模组的授权
            player.onUpdateAbilities();
        }
    }

    /**
     * 服务端侧同步飞行速度（真正的幅度由客户端 {@code ClientEventHandler#syncFlySpeed} 保证）。
     * <p>
     * 基准固定为原版默认值 0.05，<b>不再</b>从当前 abilities 读取后缓存：
     * {@code Abilities} 的 flySpeed 会被写进玩家 NBT，缓存"当前值"作为基准会导致
     * 每次重新登录都按倍率复利放大（0.05 → 0.25 → 1.25 …）。
     */
    private void updateFlightSpeed(ServerPlayer player, PlayerStats stats, boolean forceSync) {
        float bonus = stats.getStatValue("fly_speed");
        if (bonus != 0) {
            float speed = Math.max(0, VANILLA_FLY_SPEED * (1.0f + bonus));
            // forceSync：客户端飞行速度同样会在 LocalPlayer 重建时被重置回 0.05
            if (forceSync || player.getAbilities().getFlyingSpeed() != speed) {
                player.getAbilities().setFlyingSpeed(speed);
                player.onUpdateAbilities();
            }
            stats.setProviding("fly_speed", true);
        } else if (stats.isProviding("fly_speed")) {
            player.getAbilities().setFlyingSpeed(VANILLA_FLY_SPEED);
            // 清理旧版本缓存的基准值，避免老存档残留
            player.getPersistentData().remove("infinitestats.base_fly_speed");
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
            AttributeInstance stepAttr = player.getAttribute(Attributes.STEP_HEIGHT);
            if (stepAttr != null) {
                stepAttr.setBaseValue(targetStep);
            }
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