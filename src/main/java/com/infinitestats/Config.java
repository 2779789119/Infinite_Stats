package com.infinitestats;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

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

    // ========== GUI 设置 ==========

    public static ForgeConfigSpec.BooleanValue SHOW_HIDDEN_STATS;

    // ========== 兼容性设置 ==========

    public static ForgeConfigSpec.BooleanValue ENABLE_ATTRIBUTE_DISCOVERY;

    // ========== EMC 等价交换设置 ==========

    public static ForgeConfigSpec.DoubleValue EMC_LOSS_RATE;
    public static ForgeConfigSpec.BooleanValue EMC_ENABLED;

    // ========== 时间加速设置（加速属性） ==========

    // 基础半径：即使玩家未加「加速半径」点数也生效的最小半径
    public static ForgeConfigSpec.IntValue TIME_ACCEL_RADIUS;

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
                .comment("自动复活冷却时间（秒），设为0则无冷却")
                .defineInRange("autoReviveCooldown", 300, 0, 36000);
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

        // GUI 设置
        builder.push("GUI");
        SHOW_HIDDEN_STATS = builder
                .comment("是否在属性面板中显示隐藏属性（如 invincibility 无敌）。",
                         "设为 true 后打开 GUI 即可看到隐藏属性。",
                         "修改后关闭并重新打开属性面板即可生效，无需重启。")
                .define("showHiddenStats", false);
        builder.pop();

        // 兼容性设置
        builder.push("Compatibility");
        ENABLE_ATTRIBUTE_DISCOVERY = builder
                .comment("启用自动发现其他模组注册的属性。",
                         "开启后，所有其他模组的属性将自动出现在 GUI 的「外部属性」分类中。",
                         "如果遇到兼容性问题，可以关闭此选项。",
                         "注意：此选项需要重启游戏才能生效。")
                .define("enableAttributeDiscovery", true);
        builder.pop();

        // EMC 等价交换设置
        builder.push("EMC");
        EMC_ENABLED = builder
                .comment("是否启用内置等价交换 (EMC) 系统。",
                         "关闭后 EMC 转化桌 GUI 和命令将不可用。")
                .define("emcEnabled", true);
        EMC_LOSS_RATE = builder
                .comment("EMC 转换损耗率 (0.0 = 无损耗，1.0 = 100%损耗)。",
                         "学习物品时实际获得的 EMC = 物品EMC值 × (1 - lossRate)。",
                         "例如 lossRate=0.2 时学习一个 100 EMC 的物品获得 80 EMC。")
                .defineInRange("emcLossRate", 0.0, 0.0, 1.0);
        builder.pop();

        // 时间加速设置
        builder.push("TimeAccel");
        TIME_ACCEL_RADIUS = builder
                .comment("「加速」的基础影响半径（方块）。以玩家为中心，水平与垂直方向同半径。",
                         "这是未加「加速半径」点数时的最小半径；玩家可通过「加速半径」属性自行扩大。")
                .defineInRange("timeAccelRadius", 4, 1, 64);
        builder.pop();

        SPEC = builder.build();
    }
}