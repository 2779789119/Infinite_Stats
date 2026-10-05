package com.infinitestats;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
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

    // ========== 存储网络桥接优先级 ==========
    // 列表顺序即自动选择存储的优先级（靠前的优先）。可选值：
    //   RS       - Refined Storage（需持有已绑定的无线终端）
    //   BD       - Beyond Dimensions（玩家维度网络，跨维度、无需终端）
    //   AE2      - Applied Energistics 2（需持有已链接的无线终端）
    //   BACKPACK - Sophisticated Backpacks（装备在身上 / Curios 背部槽的背包）
    //   TOMS     - Tom's Storage（手持已绑定无线终端，或站在存储终端范围内）
    // 列表中未出现的桥接将不会被自动选中；未知项会被忽略。
    public static ForgeConfigSpec.ConfigValue<List<? extends String>> NETWORK_PRIORITY;

    // 每提升一级随身工作台物品倍率所消耗的可分配点数（属性点数）
    public static ForgeConfigSpec.IntValue CRAFTING_MULTIPLIER_COST;

    // ========== 升级设置 ==========

    public static ForgeConfigSpec.IntValue BASE_XP_PER_LEVEL;
    public static ForgeConfigSpec.IntValue XP_PER_LEVEL_INCREMENT;
    public static ForgeConfigSpec.IntValue POINTS_PER_LEVEL;

    // ========== 复活设置 ==========

    public static ForgeConfigSpec.IntValue AUTO_REVIVE_COOLDOWN;
    public static ForgeConfigSpec.DoubleValue AUTO_REVIVE_HEALTH_PERCENT;
    public static ForgeConfigSpec.IntValue AUTO_REVIVE_INVULN_SECONDS;
    public static ForgeConfigSpec.BooleanValue AUTO_REVIVE_REFILL_FOOD;
    public static ForgeConfigSpec.BooleanValue AUTO_REVIVE_CLEAR_DEBUFFS_ONLY;

    // ========== 被动效果设置 ==========

    public static ForgeConfigSpec.IntValue HEALTH_REGEN_INTERVAL;
    public static ForgeConfigSpec.IntValue MAGNET_RANGE;
    public static ForgeConfigSpec.IntValue VEIN_MINER_MAX_BLOCKS;

    // 弹射物追踪扫描半径（方块），以玩家为中心
    public static ForgeConfigSpec.IntValue PROJECTILE_TRACKING_RANGE;

    // ========== GUI 设置 ==========

    public static ForgeConfigSpec.BooleanValue SHOW_HIDDEN_STATS;

    /**
     * 「功能开关」把某项关掉时的执行模式（**全局设置，由整合包作者决定**，不按玩家存）。
     * <p>
     * OP 也可用 {@code /infstats feature mode <keep|refund|hide>} 在运行时改写。
     * <ul>
     *   <li>{@code keep}：整条失效，保留已投入的点数（默认）；</li>
     *   <li>{@code refund}：整条失效，并返还该属性已投入的点数；</li>
     *   <li>{@code hide}：只从属性面板隐藏，效果与指令照常，点数不返还。</li>
     * </ul>
     */
    public static ForgeConfigSpec.EnumValue<com.infinitestats.stats.FeatureDisableMode> FEATURE_DISABLE_MODE;

    /**
     * 整合包作者预设的「开局默认禁用」属性列表（属性 id，如 {@code auto_deposit}）。
     * <p>
     * 列表中的属性在玩家<b>首次进入</b>时默认被禁用（按玩家写入，之后不随配置变回）；
     * 作者可配合 {@link #FEATURE_DISABLE_MODE} 决定禁用后是整条失效还是仅隐藏，
     * 再通过 FTB 任务奖励的指令（{@code /infstats feature <id> on}）逐步开放。
     * 玩家自己<b>不能</b>编辑这份列表 —— 这纯粹是给整合包作者的开局设计入口。
     */
    public static ForgeConfigSpec.ConfigValue<List<? extends String>> DISABLED_STATS;

    /** 作者预设的开局默认禁用列表（可能为 null，未加载时）。 */
    public static List<String> defaultDisabledStats() {
        try {
            List<? extends String> list = DISABLED_STATS.get();
            return list == null ? List.of() : new ArrayList<>(list);
        } catch (Throwable ignored) {
            return List.of(); // 配置尚未加载
        }
    }

    // ========== 兼容性设置 ==========

    public static ForgeConfigSpec.BooleanValue ENABLE_ATTRIBUTE_DISCOVERY;

    // ========== EMC 等价交换设置 ==========

    public static ForgeConfigSpec.DoubleValue EMC_LOSS_RATE;
    public static ForgeConfigSpec.BooleanValue EMC_ENABLED;

    // 等价交换（ProjectE）联动：仅当检测到 projecte 模组时才有实际作用
    public static ForgeConfigSpec.BooleanValue PE_AUTO_LEARN;

    // 未知物品（无配方 / 锚点 / ProjectE 知识）的兜底 EMC 值；0 = 关闭（保持原行为）
    public static ForgeConfigSpec.LongValue EMC_FALLBACK_VALUE;

    // ========== 时间加速设置（加速属性） ==========

    // 基础半径：即使玩家未加「加速半径」点数也生效的最小半径
    public static ForgeConfigSpec.IntValue TIME_ACCEL_RADIUS;

    // ========== 随身熔炉加速设置 ==========

    // 每提升一级熔炉速度所消耗的可分配点数（属性点数）
    public static ForgeConfigSpec.IntValue FURNACE_SPEED_COST;

    // ========== 自动入库设置 ==========

    /** 自动入库的扫描间隔（tick），值越小越及时、但存储网络调用开销越高。 */
    public static ForgeConfigSpec.IntValue AUTO_DEPOSIT_INTERVAL;

    /** true = 快捷栏（0-8 号槽）的物品不会被自动存入存储，避免把随身工具吸走。 */
    public static ForgeConfigSpec.BooleanValue AUTO_DEPOSIT_KEEP_HOTBAR;

    /** 自动入库黑名单（物品 ID），列表中的物品永不自动存入存储。 */
    public static ForgeConfigSpec.ConfigValue<List<? extends String>> AUTO_DEPOSIT_BLACKLIST;

    // ========== 自动钓鱼设置 ==========

    /** 自动钓鱼：收杆后重新抛竿的最短延迟（tick）。 */
    public static ForgeConfigSpec.IntValue AUTO_FISH_RECAST_DELAY_MIN;

    /** 自动钓鱼：收杆后重新抛竿的最长延迟（tick）。 */
    public static ForgeConfigSpec.IntValue AUTO_FISH_RECAST_DELAY_MAX;

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
                .comment("被动经验获取间隔（tick，20tick=1秒），默认 20tick=1秒")
                .defineInRange("passiveXpInterval", 20, 20, 12000);
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
                .defineInRange("pointsPerLevel", 10, 1, 100);
        builder.pop();

        // 复活设置
        builder.push("AutoRevive");
        AUTO_REVIVE_COOLDOWN = builder
                .comment("自动复活冷却时间（秒），设为0则无冷却")
                .defineInRange("autoReviveCooldown", 0, 0, 36000);
        AUTO_REVIVE_HEALTH_PERCENT = builder
                .comment("复活后恢复的生命值百分比（0.0-1.0）")
                .defineInRange("autoReviveHealthPercent", 0.3, 0.1, 1.0);
        AUTO_REVIVE_INVULN_SECONDS = builder
                .comment("复活后获得的伤害免疫时间（秒），防止在原地（岩浆/敌群）立刻再次死亡。设为0则无免疫。")
                .defineInRange("autoReviveInvulnSeconds", 3, 0, 60);
        AUTO_REVIVE_REFILL_FOOD = builder
                .comment("复活时是否把饥饿值与饱食度补满，避免复活后因饥饿立刻再次陷入险境。")
                .define("autoReviveRefillFood", true);
        AUTO_REVIVE_CLEAR_DEBUFFS_ONLY = builder
                .comment("true=复活时仅清除负面效果并保留增益Buff；false=清除全部效果（旧行为）。")
                .define("autoReviveClearDebuffsOnly", true);
        builder.pop();

        // 被动效果设置
        builder.push("PassiveEffects");
        HEALTH_REGEN_INTERVAL = builder
                .comment("生命恢复间隔（tick），默认 20tick=1秒")
                .defineInRange("healthRegenInterval", 20, 20, 400);
        MAGNET_RANGE = builder
                .comment("物品/经验磁铁吸引范围")
                .defineInRange("magnetRange", 10, 3, 50);
        VEIN_MINER_MAX_BLOCKS = builder
                .comment("连锁挖掘最大方块数")
                .defineInRange("veinMinerMaxBlocks", 64, 8, 256);
        PROJECTILE_TRACKING_RANGE = builder
                .comment("弹射物追踪的扫描半径（方块）。以玩家为中心，水平与垂直方向同半径。",
                         "追踪会在该范围内寻找玩家发射的弹射物与最近的敌人，",
                         "避免对全维度实体做遍历扫描以优化性能。数值越大追踪范围越广但开销越高。")
                .defineInRange("projectileTrackingRange", 64, 8, 256);
        builder.pop();

        // GUI 设置
        builder.push("GUI");
        SHOW_HIDDEN_STATS = builder
                .comment("是否在属性面板中显示隐藏属性（如 invincibility 无敌）。",
                         "设为 true 后打开 GUI 即可看到隐藏属性。",
                         "修改后关闭并重新打开属性面板即可生效，无需重启。")
                .define("showHiddenStats", false);
        FEATURE_DISABLE_MODE = builder
                .comment("某个属性被「禁用」时的执行模式 —— 全局设置，对所有人一致。",
                         "keep   = 整条失效，保留已投入的点数（默认）；",
                         "refund = 整条失效，并返还该属性已投入的点数；",
                         "hide   = 只从属性面板隐藏，效果与指令照常，点数不返还。",
                         "改这里即时生效；OP 也可用 /infstats feature mode <keep|refund|hide> 切换。")
                .defineEnum("featureDisableMode", com.infinitestats.stats.FeatureDisableMode.INACTIVE_KEEP_POINTS);
        DISABLED_STATS = builder
                .comment("整合包作者预设的「开局默认禁用」属性列表（属性 id，如 auto_deposit）。",
                         "新玩家首次进入世界时，这些属性按玩家写入为禁用状态；之后作者通过任务奖励指令",
                         "/infstats feature <id> on 逐步开放。玩家自己不能编辑这份列表。",
                         "属性 id 可用 /infstats feature 命令的 Tab 补全查看。")
                .defineList("disabledStats", List.<String>of(), o -> o instanceof String);
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
        PE_AUTO_LEARN = builder
                .comment("等价交换（ProjectE）联动：自动学习【全局主开关】。",
                         "仅在检测到 projecte 模组时生效；未安装 ProjectE 时无任何作用。",
                         "开启后，玩家还需要在属性面板投入 5 点解锁「pe_auto_learn」属性，",
                         "解锁后拾取 / 合成的物品会自动加入 ProjectE 转化知识库。",
                         "关闭此开关则所有玩家（无论是否加点）的自动学习全部禁用。")
                .define("autoLearnProjectE", true);
        EMC_FALLBACK_VALUE = builder
                .comment("未知物品（无任何配方 / 锚点 / ProjectE 知识）的兜底 EMC 值。",
                         "设为 0（默认）表示保持原行为：无来源的物品不获得 EMC，无法被学习 / 转化。",
                         "设为大于 0 的值（如 1）后，所有物品至少拥有该 EMC，从而可在 EMC 屏中统一被学习 / 转化。",
                         "注意：此值会覆盖原矿等本应 0 EMC 的黑名单物品，可能改变平衡，请谨慎设置。")
                .defineInRange("emcFallbackValue", 0L, 0L, 1_000_000_000_000L);
        builder.pop();

        // 时间加速设置
        builder.push("TimeAccel");
        TIME_ACCEL_RADIUS = builder
                .comment("「加速」的基础影响半径（方块）。以玩家为中心，水平与垂直方向同半径。",
                         "这是未加「加速半径」点数时的最小半径；玩家可通过「加速半径」属性自行扩大。")
                .defineInRange("timeAccelRadius", 4, 1, 64);
        builder.pop();

        // 随身熔炉加速设置
        builder.push("Furnace");
        FURNACE_SPEED_COST = builder
                .comment("每提升一级随身熔炉速度所消耗的可分配点数（属性点数）。",
                         "降低速度等级时会返还相同点数。")
                .defineInRange("furnaceSpeedCost", 5, 1, 100000);
        builder.pop();

        // 存储网络桥接优先级
        builder.push("NetworkPriority");
        NETWORK_PRIORITY = builder
                .comment("自动选择存储网络的优先级（靠前的优先）。",
                         "可选值：RS（Refined Storage）、BD（Beyond Dimensions）、AE2（Applied Energistics 2）、BACKPACK（Sophisticated Backpacks）、TOMS（Tom's Storage）。",
                         "列表中未出现的桥接不会被自动选中；未知项会被忽略。",
                         "修改此列表后重启游戏生效。",
                         "默认顺序把 BD 放在最后作为兜底，使其在手持其他无线终端时不再抢占。")
                .define("networkPriority", List.of("RS", "AE2", "TOMS", "BACKPACK", "BD"));
        builder.pop();

        // 随身工作台倍率设置
        builder.push("Crafting");
        CRAFTING_MULTIPLIER_COST = builder
                .comment("每提升一级随身工作台物品倍率所消耗的可分配点数（属性点数）。",
                         "降低倍率等级时会返还相同点数。")
                .defineInRange("craftingMultiplierCost", 5, 1, 100000);
        builder.pop();

        // 自动入库设置
        builder.push("AutoDeposit");
        AUTO_DEPOSIT_INTERVAL = builder
                .comment("「自动入库」的扫描间隔（tick），默认 20tick = 1 秒。",
                         "间隔越短越及时，但每秒都会查询一次存储网络，间隔过小会带来额外开销。")
                .defineInRange("autoDepositInterval", 20, 5, 1200);
        AUTO_DEPOSIT_KEEP_HOTBAR = builder
                .comment("是否保留快捷栏（0-8 号槽）中的物品不被自动入库。",
                         "true（默认）= 只把主背包 27 格里的物品自动存入存储，随身工具/武器不会被吸走；",
                         "false = 除当前手持的那一格之外，快捷栏也会一并存入存储。")
                .define("autoDepositKeepHotbar", true);
        AUTO_DEPOSIT_BLACKLIST = builder
                .comment("自动入库黑名单（物品 ID，如 minecraft:diamond）。",
                         "列表中的物品永远不会被自动存入存储网络。",
                         "注：存储终端（RS / AE2 / 汤姆存储的无线终端等）与背包会被自动保护，无需在此重复配置。")
                .defineList("autoDepositBlacklist", List.<String>of(), o -> o instanceof String);
        builder.pop();

        // 自动钓鱼设置
        builder.push("AutoFish");
        AUTO_FISH_RECAST_DELAY_MIN = builder
                .comment("「自动钓鱼」收杆后重新抛竿的最短延迟（tick，20tick = 1 秒）。",
                         "留一点延迟可以避免机器式瞬间连抛，也更接近手动钓鱼的节奏。")
                .defineInRange("autoFishRecastDelayMin", 5, 0, 200);
        AUTO_FISH_RECAST_DELAY_MAX = builder
                .comment("「自动钓鱼」收杆后重新抛竿的最长延迟（tick）。",
                         "每次实际延迟在「最短 ~ 最长」之间随机；两者相同则为固定延迟。")
                .defineInRange("autoFishRecastDelayMax", 15, 0, 200);
        builder.pop();

        SPEC = builder.build();
    }
}