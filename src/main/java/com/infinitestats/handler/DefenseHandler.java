package com.infinitestats.handler;

import com.infinitestats.Config;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.StatCategory;
import com.infinitestats.stats.StatType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 防御类属性处理器
 * 处理：免疫、格挡、伤害减免、护盾、闪避、复活、反射
 */
public class DefenseHandler implements StatEffectHandler {

    @Override
    public String getId() {
        return "defense";
    }

    @Override
    public StatType[] getSupportedStats() {
        return StatType.getByCategory(StatCategory.DEFENSE);
    }

    @Override
    public void onTick(ServerPlayer player, PlayerStats stats, long tickCount) {
        // 无敌：每秒恢复满生命值
        if (tickCount % 20 == 0) {
            applyInvincibilityTick(player, stats);
        }

        // 每5秒处理生命恢复
        if (tickCount % 100 == 0) {
            applyHealthRegen(player, stats);
        }

        // 每2秒处理吸收护盾和负面效果驱散
        if (tickCount % 40 == 0) {
            applyAbsorptionShield(player, stats);
            applyDebuffImmunity(player, stats);
        }
    }

    @Override
    public void onLogin(ServerPlayer player, PlayerStats stats) {
        applyAbsorptionShield(player, stats);
    }

    @Override
    public void onRespawn(ServerPlayer player, PlayerStats stats) {
        applyAbsorptionShield(player, stats);
    }

    @Override
    public void onDimensionChange(ServerPlayer player, PlayerStats stats) {
        applyAbsorptionShield(player, stats);
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    /**
     * 应用生命恢复（正=回血，负=扣血）
     */
    private void applyHealthRegen(ServerPlayer player, PlayerStats stats) {
        float regen = stats.getStatValue(StatType.fromId("health_regen"));
        if (regen != 0 && player.getHealth() > 0) {
            if (regen > 0 && player.getHealth() < player.getMaxHealth()) {
                player.heal(regen);
            } else if (regen < 0) {
                // 负回血 = 生命流失（不能通过回血类型伤害触发，直接扣血）
                float damage = Math.min(-regen, player.getHealth() - 0.5f);
                if (damage > 0) {
                    player.hurt(player.level().damageSources().magic(), damage);
                }
            }
        }
    }

    /**
     * 应用吸收护盾
     * shield > 0 → 始终确保吸收生效；shield == 0 → 仅当由本模组提供时才移除
     */
    private void applyAbsorptionShield(ServerPlayer player, PlayerStats stats) {
        float shield = stats.getStatValue(StatType.fromId("absorption_shield"));
        boolean weProvided = stats.isProviding("absorption_shield");

        if (shield > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,
                    100, (int) Math.min(shield / 4, 4), false, false, true));
            stats.setProviding("absorption_shield", true);
        } else if (weProvided) {
            player.removeEffect(MobEffects.ABSORPTION);
            stats.setProviding("absorption_shield", false);
        }
    }

    /**
     * 驱散负面药水效果（debuff_immunity 开关）
     * 支持玩家自定义过滤列表，可选择过滤所有效果（包括正面buff和负面debuff）
     */
    private void applyDebuffImmunity(ServerPlayer player, PlayerStats stats) {
        if (!stats.isToggleActive("debuff_immunity")) return;

        var effects = player.getActiveEffects();
        // 遍历副本，安全移除
        for (MobEffectInstance inst : new java.util.ArrayList<>(effects)) {
            if (inst.getEffect() == null) continue;

            String effectId = ForgeRegistries.MOB_EFFECTS.getKey(inst.getEffect()).toString();
            if (stats.shouldBlockEffect(effectId)) {
                player.removeEffect(inst.getEffect());
            }
        }
    }

    /**
     * 处理复活逻辑
     * @return true表示复活成功，取消死亡
     */
    public static boolean handleAutoRevive(ServerPlayer player, PlayerStats stats) {
        if (!stats.isToggleActive("auto_revive")) return false;

        long gameTick = player.level().getGameTime();
        long cooldownTicks = Config.AUTO_REVIVE_COOLDOWN.get() * 20L;

        if (gameTick - stats.getLastReviveTime() >= cooldownTicks) {
            player.setHealth(player.getMaxHealth() * Config.AUTO_REVIVE_HEALTH_PERCENT.get().floatValue());
            player.setAirSupply(player.getMaxAirSupply());

            // 清除效果：默认只清负面效果以保留其他模组的增益Buff；关闭则该回全部清除（旧行为）
            if (Config.AUTO_REVIVE_CLEAR_DEBUFFS_ONLY.get()) {
                for (MobEffectInstance inst : new java.util.ArrayList<>(player.getActiveEffects())) {
                    if (inst.getEffect() != null && !inst.getEffect().isBeneficial()) {
                        player.removeEffect(inst.getEffect());
                    }
                }
            } else {
                player.removeAllEffects();
            }

            // 补满饥饿与饱食度（默认开启），避免复活后立刻饿死
            if (Config.AUTO_REVIVE_REFILL_FOOD.get()) {
                player.getFoodData().setFoodLevel(20);
                player.getFoodData().setSaturation(20.0f);
            }

            // 复活后短暂伤害免疫，防止在危险地点（岩浆/敌群）立刻再次死亡
            int invulnSeconds = Config.AUTO_REVIVE_INVULN_SECONDS.get();
            stats.setReviveInvulnUntilTick(invulnSeconds > 0 ? gameTick + invulnSeconds * 20L : 0);

            stats.setLastReviveTime(gameTick);
            return true;
        }
        return false;
    }

    /**
     * 复活无敌窗口内：玩家免疫一切伤害，避免复活瞬间在原地再次致死却因冷却无法再复活。
     */
    public static boolean isReviveInvulnerable(ServerPlayer player, PlayerStats stats) {
        return player.level().getGameTime() < stats.getReviveInvulnUntilTick();
    }

    /**
     * 判定是否免疫伤害（invincibility 开关开启时取消所有伤害）
     */
    public static boolean isInvincible(PlayerStats stats) {
        if (!stats.isToggleActive("invincibility")) return false;

        // 恢复满生命值
        return true;
    }

    /**
     * 为无敌玩家补满生命值
     */
    public static void applyInvincibilityTick(ServerPlayer player, PlayerStats stats) {
        if (stats.isToggleActive("invincibility")) {
            if (player.getHealth() < player.getMaxHealth()) {
                player.setHealth(player.getMaxHealth());
            }
        }
    }

    /**
     * 处理格挡逻辑
     * @return true表示格挡成功，取消伤害
     */
    public static boolean handleBlock(ServerPlayer player, PlayerStats stats) {
        float blockChance = stats.getStatValue(StatType.fromId("block_chance"));
        if (blockChance > 0 && player.getRandom().nextFloat() < blockChance) {
            return true;
        }
        return false;
    }

    /**
     * 处理闪避逻辑
     * @return true表示闪避成功，取消伤害
     */
    public static boolean handleDodge(ServerPlayer player, PlayerStats stats) {
        float dodgeChance = stats.getStatValue(StatType.fromId("dodge_chance"));
        if (dodgeChance > 0 && player.getRandom().nextFloat() < dodgeChance) {
            return true;
        }
        return false;
    }

    /**
     * 计算伤害减免后的伤害值（正=减伤，负=增伤）
     */
    public static float applyDamageReduction(PlayerStats stats, float amount) {
        float reduction = stats.getStatValue(StatType.fromId("damage_reduction"));
        if (reduction != 0) {
            float factor = 1.0f - reduction;
            if (factor <= 0) return 0; // 减免≥100% → 免疫
            return amount * factor;
        }
        return amount;
    }

    /**
     * 处理摔落伤害减免（正=减免，负=增伤）
     */
    public static float applyFallDamageReduction(PlayerStats stats, float amount, boolean isFall) {
        if (!isFall) return amount;

        float fallResist = stats.getStatValue(StatType.fromId("fall_resist"));
        if (fallResist != 0) {
            float factor = 1.0f - fallResist;
            // 减免≥100% → 仍有至少10%伤害残留，避免完全免疫（no_fall_damage开关才是完全免疫）
            amount *= Math.max(0.1f, factor);
        }

        // 免疫摔落开关（优先于任何计算）
        if (stats.isToggleActive("no_fall_damage")) {
            return 0;
        }
        return amount;
    }

    /**
     * 检查免疫类型
     */
    public static boolean isImmune(PlayerStats stats, net.minecraft.world.damagesource.DamageSource source, ServerPlayer player) {
        // 火焰免疫
        if (stats.isToggleActive("fire_immunity") && source.is(DamageTypeTags.IS_FIRE)) {
            return true;
        }
        // 弹射物免疫
        if (stats.isToggleActive("projectile_immunity") && source.is(DamageTypeTags.IS_PROJECTILE)) {
            return true;
        }
        // 爆炸免疫
        if (stats.isToggleActive("explosion_immunity") && source.is(DamageTypeTags.IS_EXPLOSION)) {
            return true;
        }
        // 窒息免疫 - inWall伤害没有实体来源，需通过player获取damageSources
        if (stats.isToggleActive("suffocation_immunity") &&
                source == player.level().damageSources().inWall()) {
            return true;
        }
        return false;
    }

    /**
     * 处理伤害反射
     */
    public static void applyDamageReflection(ServerPlayer player, PlayerStats stats, 
            float amount, LivingEntity attacker) {
        float reflection = stats.getStatValue(StatType.fromId("damage_reflection"));
        if (reflection > 0 && attacker != null && attacker != player) {
            float reflectDmg = amount * reflection;
            if (reflectDmg > 0) {
                attacker.hurt(player.level().damageSources().thorns(player), reflectDmg);
            }
        }
    }
}