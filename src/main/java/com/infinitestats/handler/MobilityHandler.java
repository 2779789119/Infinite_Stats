package com.infinitestats.handler;

import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.StatCategory;
import com.infinitestats.stats.StatType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;

import java.util.UUID;

/**
 * 机动类属性处理器
 * 处理：飞行、移动速度、跳跃、游泳、自动跨越
 */
public class MobilityHandler implements StatEffectHandler {

    /** 原版飞行速度基准（{@code Abilities#flyingSpeed} 默认值）。 */
    private static final float VANILLA_FLY_SPEED = 0.05f;

    /** 原版默认跨越高度（台阶 = 0.5 格能上，整格 1.0 上不去）。 */
    public static final float DEFAULT_STEP_HEIGHT = 0.6f;

    /**
     * {@code auto_step} 激活时的跨越高度。
     * <p>
     * 取 1.25 而不是"刚好一格"的 1.0：1.0 只在几何上刚好够，客户端/服务端各自模拟的浮点
     * 误差、以及台阶相邻方块的棱角都会让它差那么一点点，表现就是贴着台阶走不上去
     * （本项目另一个模组同样用 1.25 才稳妥）。
     */
    public static final float AUTO_STEP_HEIGHT = 1.25f;

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

    /**
     * 服务端侧同步飞行速度（真正的幅度由客户端 {@code ClientEventHandler#syncFlySpeed} 保证，
     * 因为飞行时读取的是客户端本地 {@code abilities.flyingSpeed}）。
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
        applyStepHeight(player, stats);
    }

    /**
     * 「跨越高度」修改器用的固定 UUID。
     * <p>
     * 客户端与服务端共用同一个：属性值从服务端同步回客户端时，原版是按 UUID
     * 「替换」同名修改器的，UUID 一致就不会出现本地一份 + 服务端一份的重复叠加。
     */
    private static final UUID STEP_HEIGHT_MODIFIER_UUID =
            UUID.fromString("6d1b7f2a-3c4e-4a91-8f5d-2b0c9e7a4d13");

    /** 该玩家的有效跨越高度（基准 × (1 + 百分比加成)；加成为负时不参与，与原实现一致）。 */
    public static float stepHeightOf(PlayerStats stats) {
        float base = stats.isToggleActive("auto_step") ? AUTO_STEP_HEIGHT : DEFAULT_STEP_HEIGHT;
        float bonus = stats.getStatValue(StatType.fromId("step_height"));
        return bonus > 0 ? base * (1.0f + bonus) : base;
    }

    /**
     * 把跨越高度写到玩家身上（客户端与服务端都调用，公式完全一致）。
     * <p>
     * <b>为什么要分两个通道写：</b>Forge 1.20.1 把 {@code Entity#collide} 里的跨越高度换成了
     * {@code IForgeEntity#getStepHeight()}，它的定义是
     * 「原版 {@code maxUpStep} 字段 <b>加上</b> {@code forge:step_height_addition} 属性的值」
     * （见 Forge 补丁源码 {@code IForgeEntity#getStepHeight()}）。
     * <ul>
     *   <li><b>原版字段</b>只写「基准值」（{@code 0.6}，开了自动跨越则 {@code 1.25}）——
     *       整合包里另有模组读写这个字段是常事，把大数值写在这里容易被它们覆盖；</li>
     *   <li><b>Forge 属性</b>承载「超出基准的那部分」，合计正好等于目标跨越高度，
     *       而且属性本身会随原版属性同步在两端保持一致。</li>
     * </ul>
     * 旧实现只写原版字段：只要别的模组也去设置 {@code maxUpStep}，我们的值就被抹掉，
     * 表现就是「跨越高度加了点却走不上台阶」（客户端本地跳上去了，服务端判定不一致又拉回来）。
     * <p>
     * 只在数值真正变化时才动属性：{@code AttributeInstance} 每次增删修改器都会置脏并触发一次
     * 属性同步包，每 tick 无脑重写会变成刷包。
     */
    public static void applyStepHeight(LivingEntity entity, PlayerStats stats) {
        float base = stats.isToggleActive("auto_step") ? AUTO_STEP_HEIGHT : DEFAULT_STEP_HEIGHT;
        float target = stepHeightOf(stats);

        // 原版通道：字段固定写基准值（不写目标值，避免与下面的属性重复相加）
        if (Math.abs(entity.maxUpStep() - base) > 0.01f) {
            entity.setMaxUpStep(base);
        }

        // Forge 通道：只补「超出基准的差额」
        AttributeInstance instance = entity.getAttribute(ForgeMod.STEP_HEIGHT_ADDITION.get());
        if (instance == null) return;

        double extra = Math.max(0.0D, (double) target - (double) base);
        AttributeModifier existing = instance.getModifier(STEP_HEIGHT_MODIFIER_UUID);
        double current = existing == null ? 0.0D : existing.getAmount();
        if (Math.abs(current - extra) <= 1.0E-4D) return;

        // 必须先摘掉旧修改器：AttributeInstance 对重复 UUID 会直接抛异常
        if (existing != null) instance.removeModifier(STEP_HEIGHT_MODIFIER_UUID);
        if (extra > 1.0E-4D) {
            instance.addTransientModifier(new AttributeModifier(
                    STEP_HEIGHT_MODIFIER_UUID,
                    "infinitestats:step_height",
                    extra,
                    AttributeModifier.Operation.ADDITION));
        }
    }

    /**
     * 计算游泳速度倍率（1 + 加成）。
     * <p>
     * 实际生效点在 {@code mixin/EntityMoveRelativeMixin}：那里的实现放大的是
     * 「输入加速度」这一项，终端速度与原版成正比、线性不发散；
     * 服务端不再直接改速度（玩家移动是客户端权威的，改了客户端也收不到）。
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