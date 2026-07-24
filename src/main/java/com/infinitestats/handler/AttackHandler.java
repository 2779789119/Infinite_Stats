package com.infinitestats.handler;

import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.StatCategory;
import com.infinitestats.stats.StatType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;

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
        // 排斥：持续推开周围的敌对生物
        applyRepulsion(player, stats, tickCount);
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

    /**
     * 应用真实伤害：基于本次伤害的额外真实伤害，直接削减生命（无视护甲与一切减伤）
     */
    public static void applyTrueDamage(ServerPlayer player, PlayerStats stats, float baseDamage, LivingEntity target) {
        float tdPct = stats.getStatValue(StatType.fromId("true_damage"));
        if (tdPct <= 0 || baseDamage <= 0) return;

        float trueDmg = baseDamage * tdPct;
        if (trueDmg <= 0) return;

        applyDirectDamage(player, target, trueDmg);
    }

    /**
     * 直接削减生命值（无视护甲/减伤），并处理击杀归属与死亡触发
     */
    private static void applyDirectDamage(ServerPlayer player, LivingEntity target, float amount) {
        target.setLastHurtByMob(player);
        // 同时设置玩家击杀归属，使经验、掉落归属、击杀统计、FTB kill 任务、
        // L2Hostility 难度判定等都将此击杀算作玩家（否则仅 lastHurtByMob 不触发 player 击杀路径）
        target.setLastHurtByPlayer(player);
        // 直接削减血量：无视护盾/护甲吸收，health<=0 时由 setHealth 触发死亡流程
        target.setHealth(target.getHealth() - amount);
    }

    /**
     * 应用攻击减血量上限：每次攻击降低目标最大生命值（最低保留 1 点）
     * 兼容其他模组的生命加成：读取总值和基础值的差值作为修饰符，计算新基础值时扣除修饰符部分
     */
    public static void applyReduceMaxHealth(ServerPlayer player, PlayerStats stats, LivingEntity target) {
        float reduce = stats.getStatValue(StatType.fromId("reduce_max_health"));
        if (reduce <= 0) return;

        var maxHp = target.getAttribute(Attributes.MAX_HEALTH);
        if (maxHp == null) return;

        double curMax = maxHp.getValue();
        double curBase = maxHp.getBaseValue();
        double modifiers = curMax - curBase; // 其他模组加成部分
        double newMax = Math.max(1.0, curMax - Math.min(reduce, curMax - 1.0));
        if (newMax < curMax - 0.001) {
            double lose = curMax - newMax;
            double newBase = Math.max(1.0, newMax - modifiers);
            maxHp.setBaseValue(newBase);
            applyDirectDamage(player, target, (float) lose);
        }
    }

    /**
     * 应用范围攻击：对目标周围敌人造成 50% 伤害（直接削减，无递归重入）
     */
    public static void applyScopeAttack(ServerPlayer player, PlayerStats stats, float baseDamage, LivingEntity origin) {
        float radius = stats.getStatValue(StatType.fromId("scope_attack"));
        if (radius <= 0 || baseDamage <= 0) return;

        // 以被攻击生物为中心做范围判定
        AABB area = origin.getBoundingBox().inflate(radius, radius, radius);
        List<LivingEntity> nearby = player.level().getEntitiesOfClass(LivingEntity.class, area,
                e -> e.isAlive() && e != origin && e != player && !e.isAlliedTo(player));

        float dmg = baseDamage * 0.5f;
        for (LivingEntity e : nearby) {
            applyDirectDamage(player, e, dmg);
        }
    }

    /**
     * 应用排斥：持续将周围敌对生物推开（每 10 tick 触发一次）
     */
    public static void applyRepulsion(ServerPlayer player, PlayerStats stats, long tickCount) {
        float radius = stats.getStatValue(StatType.fromId("repulsion"));
        if (radius <= 0) return;
        if (tickCount % 10 != 0) return;

        AABB area = player.getBoundingBox().inflate(radius, radius + 1, radius);
        List<LivingEntity> nearby = player.level().getEntitiesOfClass(LivingEntity.class, area,
                e -> e.isAlive() && e != player && !e.isAlliedTo(player));

        for (LivingEntity e : nearby) {
            // 手动计算“从玩家指向实体”的水平方向并施加推力，
            // 这样无论 knockback 的 (x,z) 语义如何，都能以玩家为中心向四周推开
            double dx = e.getX() - player.getX();
            double dz = e.getZ() - player.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist < 1e-5) {
                // 实体与玩家几乎重合时，给一个随机水平方向
                double angle = player.getRandom().nextDouble() * Math.PI * 2.0;
                dx = Math.cos(angle);
                dz = Math.sin(angle);
                dist = 1.0;
            }
            double resist = 1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
            if (resist > 0.0) {
                double strength = 0.6 * resist / dist;
                e.setDeltaMovement(
                        e.getDeltaMovement().x + dx * strength,
                        e.getDeltaMovement().y,
                        e.getDeltaMovement().z + dz * strength
                );
                e.hasImpulse = true;
            }
        }
    }
}