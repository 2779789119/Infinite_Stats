package com.infinitestats.compat;

import com.infinitestats.stats.PlayerStatsProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 第三方模组联动共用的「属性开关」判定。
 * <p>
 * 三条枪械联动属性（{@code infinite_ammo} 无限子弹 / {@code no_heat} 零热量 /
 * {@code no_recoil} 无后坐力）同时支持 TACZ 与 Superb Warfare 两个模组，
 * 各家的注入点都要问一句「这个射手解锁了吗」。判定逻辑只写一份放这里，
 * 避免两个模组各写一套、将来改口径时漏改一处。
 * <p>
 * 这些方法会被<b>开火路径</b>甚至<b>每帧</b>调用（例如后坐力是渲染帧里施加的），
 * 所以刻意只做一次 Capability 查询 + 走缓存：不反射、不分配对象。
 * 非玩家（生物持枪）一律 false —— 这三条属性都是玩家的。
 * <p>
 * 唯一的例外是<b>载具武器</b>：Superb Warfare 的载具武器不放在物品栏里，开火时传进来的实体是
 * <b>载具本身</b>（{@code VehicleEntity.getAmmoSupplier()} 直接 {@code return this}），
 * 所以另留一个 {@link #isActiveForShooter(Entity, String)} —— 本体不是玩家时再看它的乘员。
 */
public final class CompatToggles {

    private CompatToggles() {}

    /** 无限子弹（弹匣 / 备弹不再被消耗）。 */
    public static boolean infiniteAmmo(LivingEntity entity) {
        return isActive(entity, "infinite_ammo");
    }

    /** 零热量（热量恒为 0，不会过热锁死）。 */
    public static boolean noHeat(LivingEntity entity) {
        return isActive(entity, "no_heat");
    }

    /** 无后坐力（开火时视角不再被往上顶）。 */
    public static boolean noRecoil(LivingEntity entity) {
        return isActive(entity, "no_recoil");
    }

    /** 通用判定：非玩家一律 false。 */
    public static boolean isActive(LivingEntity entity, String statId) {
        if (!(entity instanceof Player player)) return false;
        return player.getCapability(PlayerStatsProvider.PLAYER_STATS)
                .map(stats -> stats.isToggleActive(statId))
                .orElse(false);
    }

    /**
     * 开火方判定（比 {@link #isActive(LivingEntity, String)} 多一层载具兜底）。
     * <p>
     * 载具武器挂在载具实体上，开火时拿到的「射击方」是载具而不是玩家 —— 所以要往乘员里找一遍。
     * 手持枪械走到这里时本体就是玩家，第一句就命中，等于没有额外开销（乘员表为空）。
     * <p>
     * 已知取舍：载具开火的震屏由整台载具统一派发（{@code ShakeClientMessage} 包里没有逐个玩家的身份），
     * 所以「乘员中有解锁者」即视为该载具生效 —— 多人同车时由解锁的那位决定。
     */
    public static boolean isActiveForShooter(Entity shooter, String statId) {
        if (shooter == null) return false;
        if (shooter instanceof LivingEntity living && isActive(living, statId)) return true;
        for (Entity passenger : shooter.getPassengers()) {
            if (passenger instanceof LivingEntity living && isActive(living, statId)) return true;
        }
        return false;
    }
}
