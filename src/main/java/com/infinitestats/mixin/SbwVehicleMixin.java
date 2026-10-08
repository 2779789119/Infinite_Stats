package com.infinitestats.mixin;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.infinitestats.compat.SuperbWarfareCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Superb Warfare（卓越前线）联动「无限载具血量」。
 * <p>
 * 落点是载具基类的 {@code setHealth(float)}，用 {@code @ModifyVariable} 把<b>入参</b>改成满血。
 * <p>
 * 为什么选这里（已用 {@code javap} 反汇编核对）：
 * <ul>
 *   <li>它是载具血量的<b>唯一写入口</b> —— 全类只有它把血量写进同步数据 {@code HEALTH}
 *       （子弹 / 炮弹 / 撞击 / 爆炸，无论哪条伤害链路，最后都是
 *       {@code setHealth(当前血量 - 伤害)} 这一句）；</li>
 *   <li>它就在「本次受伤结算」的同一条调用链里，所以只有拦在这里才是真的「打不死」——
 *       放在每 tick 的兜底里回血赶不上同一 tick 内血量归零后的 {@code destroy()}
 *       （一炮打掉满血坦克是常有的事，来不及补）。</li>
 * </ul>
 * <p>
 * 用 {@code @ModifyVariable}（改入参）而不是 {@code @Inject} + 取消：
 * 取消只能「不写」，血量会停在旧值上；改入参则是「无论谁写、要写多少，写进去的都是满血」，
 * 读数、HUD、它的低血提示 / 冒烟特效全都自然跟着满血走。而且也不会递归 ——
 * 兼容层只做反射读 {@code getMaxHealth()}，不再回写。
 * <p>
 * 载具装甲的<b>部件血量</b>（履带 / 引擎 / 炮塔）不需要单独处理：反汇编核对过
 * {@code VehicleEffectUtils.handlePartHealth(...)}，部件只在「主血量低于上限 5%」时被清零，
 * 主血量恒满的情况下这一步根本不会走到。
 * <p>
 * 判定口径与另外三条枪械属性一致（{@code CompatToggles.isActiveForShooter}）：
 * <b>该载具的乘员里有解锁者</b>即生效（载具自己不可能是玩家）。
 * <p>
 * 本 Mixin 只在装了 Superb Warfare 时才会被应用（{@code InfiniteStatsMixinPlugin} 按目标包名判定），
 * 注入点可选（{@code require = 0}）；import 的目标类来自编译期存根 {@code src/compatApiStub}。
 */
@Mixin(value = VehicleEntity.class, remap = false)
public abstract class SbwVehicleMixin {

    /**
     * 血量写入：解锁「无限载具血量」时一律写成满血。
     * <p>
     * {@code this} 是载具实例（本 Mixin 不声明继承任何目标类，所以按 {@code Object} 交给兼容层，
     * 由它做 {@code instanceof Entity} 判断）。
     */
    @ModifyVariable(method = "setHealth", at = @At("HEAD"), argsOnly = true, require = 0, remap = false)
    private float infinitestats$infiniteHealth(float health) {
        return SuperbWarfareCompat.forceFullVehicleHealth(this, health);
    }
}
