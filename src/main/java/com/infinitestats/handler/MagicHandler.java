package com.infinitestats.handler;

import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.StatCategory;
import com.infinitestats.stats.StatType;
import net.minecraft.server.level.ServerPlayer;

/**
 * 魔法类属性处理器
 * 处理：法力值、法力恢复、法力护盾、冷却缩减
 */
public class MagicHandler implements StatEffectHandler {

    @Override
    public String getId() {
        return "magic";
    }

    @Override
    public StatType[] getSupportedStats() {
        return StatType.getByCategory(StatCategory.MAGIC);
    }

    @Override
    public void onTick(ServerPlayer player, PlayerStats stats, long tickCount) {
        // 每2秒处理法力恢复
        if (tickCount % 40 == 0) {
            regenerateMana(stats);
        }
    }

    @Override
    public void onLogin(ServerPlayer player, PlayerStats stats) {
        // 初始化法力值
        float maxMana = stats.getMaxMana();
        if (stats.getCurrentMana() <= 0 && maxMana > 0) {
            stats.setCurrentMana(maxMana);
        }
    }

    @Override
    public void onRespawn(ServerPlayer player, PlayerStats stats) {
        // 重生后恢复法力
        float maxMana = stats.getMaxMana();
        if (maxMana > 0) {
            stats.setCurrentMana(maxMana);
        }
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
     * 法力恢复
     */
    private void regenerateMana(PlayerStats stats) {
        float maxMana = stats.getMaxMana();
        if (maxMana <= 0) return;

        float currentMana = stats.getCurrentMana();
        if (currentMana >= maxMana) return;

        float regen = stats.getStatValue(StatType.fromId("mana_regen"));
        if (regen > 0) {
            stats.setCurrentMana(Math.min(currentMana + regen, maxMana));
        }
    }

    /**
     * 使用法力护盾吸收伤害
     * @return 吸收后的剩余伤害值
     */
    public static float applyManaShield(PlayerStats stats, float damageAmount) {
        float manaShield = stats.getStatValue(StatType.fromId("mana_shield"));
        float maxMana = stats.getMaxMana();
        float currentMana = stats.getCurrentMana();

        if (manaShield <= 0 || maxMana <= 0 || currentMana <= 0) {
            return damageAmount;
        }

        float manaAbsorb = Math.min(damageAmount * manaShield, currentMana);
        stats.setCurrentMana(currentMana - manaAbsorb);

        float remaining = damageAmount - manaAbsorb;
        return Math.max(0, remaining);
    }

    /**
     * 计算冷却缩减后的冷却时间
     */
    public static int applyCooldownReduction(PlayerStats stats, int baseCooldown) {
        float reduction = stats.getStatValue(StatType.fromId("cooldown_reduction"));
        if (reduction <= 0) return baseCooldown;
        return Math.max(1, (int) (baseCooldown * (1.0f - reduction)));
    }

    /**
     * 计算魔法伤害增伤
     */
    public static float getMagicDamageMultiplier(PlayerStats stats) {
        return 1.0f + stats.getStatValue(StatType.fromId("magic_damage"));
    }

    /**
     * 计算法术强度
     */
    public static float getSpellPowerMultiplier(PlayerStats stats) {
        return 1.0f + stats.getStatValue(StatType.fromId("spell_power"));
    }
}