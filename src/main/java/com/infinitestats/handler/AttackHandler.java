package com.infinitestats.handler;

import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.StatCategory;
import com.infinitestats.stats.StatType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 攻击类属性处理器
 * 处理：暴击、护甲穿透、弹射物伤害、生命偷取、范围吸血、法力窃取
 */
public class AttackHandler implements StatEffectHandler {

    @Override
    public String getId() {
        return "attack";
    }

    @Override
    public StatType[] getSupportedStats() {
        return StatType.getByCategory(StatCategory.ATTACK);
    }

    @Override
    public void onTick(ServerPlayer player, PlayerStats stats, long tickCount) {
        // 攻击类属性不需要持续tick处理
    }

    @Override
    public void onLogin(ServerPlayer player, PlayerStats stats) {
        // 无需初始化
    }

    @Override
    public void onRespawn(ServerPlayer player, PlayerStats stats) {
        // 无需处理
    }

    @Override
    public void onDimensionChange(ServerPlayer player, PlayerStats stats) {
        // 无需处理
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    /**
     * 计算暴击伤害倍率
     */
    public static float calculateCritMultiplier(ServerPlayer player, PlayerStats stats, boolean isFullAttack) {
        if (!isFullAttack) return 1.0f;

        float critChance = stats.getStatValue(StatType.fromId("crit_chance"));
        if (critChance > 0 && player.getRandom().nextFloat() < critChance) {
            float critDamage = stats.getStatValue(StatType.fromId("crit_damage"));
            return 1.5f + critDamage;
        }
        return 1.0f;
    }

    /**
     * 计算护甲穿透增伤
     */
    public static float calculatePenetrationBonus(PlayerStats stats) {
        float penetration = stats.getStatValue(StatType.fromId("armor_penetration"));
        return penetration > 0 ? 1.0f + penetration * 0.5f : 1.0f;
    }

    /**
     * 计算弹射物伤害增伤
     */
    public static float calculateProjectileBonus(PlayerStats stats, boolean isProjectile) {
        if (!isProjectile) return 1.0f;
        return 1.0f + stats.getStatValue(StatType.fromId("projectile_damage"));
    }

    /**
     * 计算魔法伤害增伤
     */
    public static float calculateMagicBonus(PlayerStats stats, boolean isIndirect) {
        if (!isIndirect) return 1.0f;
        return 1.0f + stats.getStatValue(StatType.fromId("magic_damage"));
    }

    /**
     * 应用生命偷取效果
     */
    public static void applyLifeSteal(ServerPlayer player, PlayerStats stats, float damageAmount) {
        float lifeSteal = stats.getStatValue(StatType.fromId("life_steal"));
        if (lifeSteal > 0 && damageAmount > 0) {
            player.heal(damageAmount * lifeSteal);
        }
    }

    /**
     * 应用范围吸血效果
     */
    public static void applyAoeLifeSteal(ServerPlayer player, PlayerStats stats, float damageAmount, LivingEntity target) {
        float aoeSteal = stats.getStatValue(StatType.fromId("life_steal_aoe"));
        if (aoeSteal <= 0 || damageAmount <= 0) return;

        double range = 5.0;
        AABB area = new AABB(
                target.getX() - range, target.getY() - range, target.getZ() - range,
                target.getX() + range, target.getY() + range, target.getZ() + range
        );

        List<LivingEntity> nearby = player.level().getEntitiesOfClass(LivingEntity.class, area,
                e -> e.isAlive() && e != player && e != target && !e.isAlliedTo(player));

        float totalAoeDamage = 0;
        for (LivingEntity nearbyMob : nearby) {
            float aoeDmg = damageAmount * aoeSteal * 0.3f;
            nearbyMob.hurt(player.level().damageSources().playerAttack(player), aoeDmg);
            totalAoeDamage += aoeDmg;
        }

        if (totalAoeDamage > 0) {
            player.heal(totalAoeDamage * 0.5f);
        }
    }

    /**
     * 应用法力窃取效果
     */
    public static void applyManaSteal(ServerPlayer player, PlayerStats stats, float damageAmount) {
        float manaSteal = stats.getStatValue(StatType.fromId("mana_steal"));
        float maxMana = stats.getMaxMana();
        if (manaSteal > 0 && maxMana > 0 && damageAmount > 0) {
            float manaGain = damageAmount * manaSteal;
            stats.setCurrentMana(Math.min(stats.getCurrentMana() + manaGain, maxMana));
        }
    }
}