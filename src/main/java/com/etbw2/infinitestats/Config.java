package com.etbw2.infinitestats;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 模组配置 - 可自定义的游戏参数
 * 支持服务器管理员调整平衡性
 */
public final class Config {

    public static final ForgeConfigSpec SPEC;

    // ========== 经验设置 ==========

    public static ForgeConfigSpec.IntValue XP_PER_KILL_BASE;
    public static ForgeConfigSpec.DoubleValue XP_PER_KILL_HEALTH_FACTOR;
    public static ForgeConfigSpec.IntValue PASSIVE_XP_AMOUNT;
    public static ForgeConfigSpec.IntValue PASSIVE_XP_INTERVAL;

    // ========== 升级设置 ==========

    public static ForgeConfigSpec.IntValue BASE_XP_PER_LEVEL;
    public static ForgeConfigSpec.IntValue XP_PER_LEVEL_INCREMENT;
    public static ForgeConfigSpec.IntValue POINTS_PER_LEVEL;

    // ========== 复活设置 ==========

    public static ForgeConfigSpec.IntValue AUTO_REVIVE_COOLDOWN;
    public static ForgeConfigSpec.DoubleValue AUTO_REVIVE_HEALTH_PERCENT;

    // ========== 被动效果设置 ==========

    public static ForgeConfigSpec.IntValue HEALTH_REGEN_INTERVAL;
    public static ForgeConfigSpec.IntValue MANA_REGEN_INTERVAL;
    public static ForgeConfigSpec.IntValue MAGNET_RANGE;
    public static ForgeConfigSpec.IntValue VEIN_MINER_MAX_BLOCKS;

    // ========== 平衡设置 ==========

    public static ForgeConfigSpec.DoubleValue CRIT_DAMAGE_BASE;
    public static ForgeConfigSpec.DoubleValue LIFE_STEAL_CAP;
    public static ForgeConfigSpec.DoubleValue DAMAGE_REDUCTION_CAP;
    public static ForgeConfigSpec.DoubleValue COOLDOWN_REDUCTION_CAP;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        // 经验设置
        builder.push("Experience");
        XP_PER_KILL_BASE = builder
                .comment("击杀怪物基础经验值")
                .defineInRange("xpPerKillBase", 20, 1, 10000);
        XP_PER_KILL_HEALTH_FACTOR = builder
                .comment("怪物最大生命值每点增加的经验值系数")
                .defineInRange("xpPerKillHealthFactor", 3.0, 0.0, 100.0);
        PASSIVE_XP_AMOUNT = builder
                .comment("被动获取的经验值数量")
                .defineInRange("passiveXpAmount", 2, 0, 100);
        PASSIVE_XP_INTERVAL = builder
                .comment("被动经验获取间隔（tick，20tick=1秒）")
                .defineInRange("passiveXpInterval", 80, 20, 12000);
        builder.pop();

        // 升级设置
        builder.push("Leveling");
        BASE_XP_PER_LEVEL = builder
                .comment("升到2级所需的基础经验值")
                .defineInRange("baseXpPerLevel", 60, 10, 100000);
        XP_PER_LEVEL_INCREMENT = builder
                .comment("每级增加的经验值需求")
                .defineInRange("xpPerLevelIncrement", 30, 0, 10000);
        POINTS_PER_LEVEL = builder
                .comment("每次升级获得的属性点数")
                .defineInRange("pointsPerLevel", 3, 1, 100);
        builder.pop();

        // 复活设置
        builder.push("AutoRevive");
        AUTO_REVIVE_COOLDOWN = builder
                .comment("自动复活冷却时间（秒）")
                .defineInRange("autoReviveCooldown", 300, 10, 36000);
        AUTO_REVIVE_HEALTH_PERCENT = builder
                .comment("复活后恢复的生命值百分比（0.0-1.0）")
                .defineInRange("autoReviveHealthPercent", 0.3, 0.1, 1.0);
        builder.pop();

        // 被动效果设置
        builder.push("PassiveEffects");
        HEALTH_REGEN_INTERVAL = builder
                .comment("生命恢复间隔（tick）")
                .defineInRange("healthRegenInterval", 100, 20, 400);
        MANA_REGEN_INTERVAL = builder
                .comment("法力恢复间隔（tick）")
                .defineInRange("manaRegenInterval", 40, 20, 400);
        MAGNET_RANGE = builder
                .comment("物品/经验磁铁吸引范围")
                .defineInRange("magnetRange", 10, 3, 50);
        VEIN_MINER_MAX_BLOCKS = builder
                .comment("连锁挖掘最大方块数")
                .defineInRange("veinMinerMaxBlocks", 64, 8, 256);
        builder.pop();

        // 平衡设置
        builder.push("Balance");
        CRIT_DAMAGE_BASE = builder
                .comment("暴击基础伤害倍率")
                .defineInRange("critDamageBase", 1.5, 1.0, 5.0);
        LIFE_STEAL_CAP = builder
                .comment("生命偷取上限")
                .defineInRange("lifeStealCap", 0.5, 0.1, 1.0);
        DAMAGE_REDUCTION_CAP = builder
                .comment("伤害减免上限")
                .defineInRange("damageReductionCap", 0.9, 0.5, 0.99);
        COOLDOWN_REDUCTION_CAP = builder
                .comment("冷却缩减上限")
                .defineInRange("cooldownReductionCap", 0.7, 0.3, 0.95);
        builder.pop();

        SPEC = builder.build();
    }
}