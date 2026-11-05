package com.infinitestats;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * 模组配置 - 可自定义的游戏参数
 * 支持服务器管理员调整平衡性
 */
public final class Config {

    public static final ModConfigSpec SPEC;

    // ========== 经验设置 ==========

    public static ModConfigSpec.IntValue XP_PER_KILL_BASE;
    public static ModConfigSpec.DoubleValue XP_PER_KILL_HEALTH_FACTOR;
    public static ModConfigSpec.IntValue PASSIVE_XP_AMOUNT;
    public static ModConfigSpec.IntValue PASSIVE_XP_INTERVAL;

    // ========== 存储网络桥接优先级 ==========
    // 列表顺序即自动选择存储的优先级（靠前的优先）。可选值：
    //   RS       - Refined Storage（需持有已绑定的无线终端）
    //   BD       - Beyond Dimensions（玩家维度网络，跨维度、无需终端）
    //   AE2      - Applied Energistics 2（需持有已链接的无线终端）
    //   BACKPACK - Sophisticated Backpacks（装备在身上 / Curios 背部槽的背包）
    //   TOMS     - Tom's Storage（手持已绑定无线终端，或站在存储终端范围内）
    // 列表中未出现的桥接将不会被自动选中；未知项会被忽略。
    public static ModConfigSpec.ConfigValue<List<? extends String>> NETWORK_PRIORITY;

    // 每提升一级随身工作台物品倍率所消耗的可分配点数（属性点数）
    public static ModConfigSpec.IntValue CRAFTING_MULTIPLIER_COST;

    // ========== 升级设置 ==========

    public static ModConfigSpec.IntValue BASE_XP_PER_LEVEL;
    public static ModConfigSpec.IntValue XP_PER_LEVEL_INCREMENT;
    public static ModConfigSpec.IntValue POINTS_PER_LEVEL;

    // ========== 复活设置 ==========

    public static ModConfigSpec.IntValue AUTO_REVIVE_COOLDOWN;
    public static ModConfigSpec.DoubleValue AUTO_REVIVE_HEALTH_PERCENT;
    public static ModConfigSpec.IntValue AUTO_REVIVE_INVULN_SECONDS;
    public static ModConfigSpec.BooleanValue AUTO_REVIVE_REFILL_FOOD;
    public static ModConfigSpec.BooleanValue AUTO_REVIVE_CLEAR_DEBUFFS_ONLY;

    /** 在伤害结算阶段就拦下致死伤害（而不是等死亡事件）。 */
    public static ModConfigSpec.BooleanValue AUTO_REVIVE_PREVENT_LETHAL;
    /** 掉出世界（虚空 / 世界底部）时立刻拉回，不占用复活冷却。 */
    public static ModConfigSpec.BooleanValue AUTO_REVIVE_VOID_ESCAPE;
    /** 距离世界最低点下方多少格算「已掉出世界」。 */
    public static ModConfigSpec.IntValue AUTO_REVIVE_VOID_ESCAPE_DEPTH;

    // ========== 战斗结算设置 ==========

    /** 暴击基础倍率（其上再叠加 crit_damage）。 */
    public static ModConfigSpec.DoubleValue CRIT_BASE_MULTIPLIER;
    /** 暴击率上限（1.0 = 必定暴击）。 */
    public static ModConfigSpec.DoubleValue CRIT_CHANCE_CAP;
    /** 「满蓄力」判定阈值。 */
    public static ModConfigSpec.DoubleValue FULL_CHARGE_THRESHOLD;
    /** 护甲穿透上限（比例）。 */
    public static ModConfigSpec.DoubleValue ARMOR_PENETRATION_CAP;
    /** 生命偷取上限（比例）。 */
    public static ModConfigSpec.DoubleValue LIFE_STEAL_CAP;
    /** 闪避率上限。 */
    public static ModConfigSpec.DoubleValue DODGE_CHANCE_CAP;
    /** 格挡率上限。 */
    public static ModConfigSpec.DoubleValue BLOCK_CHANCE_CAP;
    /** 伤害减免上限。 */
    public static ModConfigSpec.DoubleValue DAMAGE_REDUCTION_CAP;
    /** 反伤倍率上限。 */
    public static ModConfigSpec.DoubleValue DAMAGE_REFLECTION_CAP;
    /** 范围吸血的搜索半径。 */
    public static ModConfigSpec.DoubleValue AOE_LIFE_STEAL_RANGE;
    /** 范围吸血对周围目标造成的伤害比例。 */
    public static ModConfigSpec.DoubleValue AOE_LIFE_STEAL_DAMAGE_RATIO;
    /** 范围吸血「实际造成伤害」回流为治疗的比例。 */
    public static ModConfigSpec.DoubleValue AOE_LIFE_STEAL_HEAL_RATIO;
    /** 「额外打击」最大次数。 */
    public static ModConfigSpec.IntValue MAX_EXTRA_STRIKES;
    /** 「范围攻击」伤害比例。 */
    public static ModConfigSpec.DoubleValue SCOPE_ATTACK_DAMAGE_RATIO;
    /** 「处决」触发血量比例。 */
    public static ModConfigSpec.DoubleValue EXECUTE_THRESHOLD;
    /** 「无敌」回满血的执行间隔（tick）。 */
    public static ModConfigSpec.IntValue INVINCIBILITY_HEAL_INTERVAL;
    /** 吸收护盾刷新间隔（tick）。 */
    public static ModConfigSpec.IntValue ABSORPTION_REFRESH_INTERVAL;
    /** 双倍掉落概率上限。 */
    public static ModConfigSpec.DoubleValue DOUBLE_LOOT_CAP;
    /** 村民交易折扣上限。 */
    public static ModConfigSpec.DoubleValue VILLAGER_DISCOUNT_CAP;
    /** 物品冷却缩减上限。 */
    public static ModConfigSpec.DoubleValue COOLDOWN_REDUCTION_CAP;
    /** 「自动修复」每级每秒修复的耐久点。 */
    public static ModConfigSpec.DoubleValue AUTO_REPAIR_PER_LEVEL;
    /** 弹射物追踪的视野锥余弦阈值。 */
    public static ModConfigSpec.DoubleValue PROJECTILE_TRACKING_CONE_COS;
    /** 「经验加成」总倍率上限（0 = 不设上限）。 */
    public static ModConfigSpec.DoubleValue XP_GAIN_CAP;
    /** 「经验加成」是否同时放大原版经验。 */
    public static ModConfigSpec.BooleanValue XP_GAIN_AFFECT_VANILLA;

    // ========== 面板入口隐藏 ==========

    /**
     * 要隐藏的面板入口（面板 id 列表）。整合包作者用它把不想给玩家看到的界面藏起来。
     * <p>
     * 可用 id：{@code stats} / {@code emc} / {@code item_editor} / {@code achievements} /
     * {@code waypoint} / {@code ore_vision} / {@code portable_tools} / {@code weather} /
     * {@code chunk_loader} / {@code vein_miner} / {@code entity_panel} / {@code debuff_filter}。
     * 隐藏后对应的快捷键不再生效（已经打开的界面不受影响）。
     */
    public static ModConfigSpec.ConfigValue<List<? extends String>> HIDDEN_PANELS;

    /**
     * 要整组隐藏的属性分类（分类枚举名，如 {@code ATTACK} / {@code EXTERNAL}）。
     * <p>
     * 与 {@link #HIDDEN_PANELS} 不同：这里隐藏的是属性面板内部的**分类页签**，
     * 整合包可以据此砍掉不需要的分类（例如纯生存包去掉 {@code EXTERNAL}），
     * 玩家既看不到页签，也用数字键切不过去。
     */
    public static ModConfigSpec.ConfigValue<List<? extends String>> HIDDEN_CATEGORIES;

    /** 某个分类页签是否被配置隐藏（大小写不敏感）。 */
    public static boolean isCategoryHidden(String categoryName) {
        if (categoryName == null) return false;
        try {
            List<? extends String> list = HIDDEN_CATEGORIES.get();
            if (list == null) return false;
            for (String id : list) {
                if (categoryName.equalsIgnoreCase(id)) return true;
            }
        } catch (Throwable notLoaded) {
            return false;
        }
        return false;
    }

    /** 某个面板入口是否被配置隐藏。 */
    public static boolean isPanelHidden(String panelId) {
        if (panelId == null) return false;
        try {
            List<? extends String> list = HIDDEN_PANELS.get();
            if (list == null) return false;
            for (String id : list) {
                if (panelId.equalsIgnoreCase(id)) return true;
            }
        } catch (Throwable notLoaded) {
            return false;
        }
        return false;
    }

    // ========== 进度补充 ==========

    /** 「被动经验」总开关。 */
    public static ModConfigSpec.BooleanValue PASSIVE_XP_ENABLED;
    /** 击杀经验是否计入召唤物 / 宠物造成的击杀。 */
    public static ModConfigSpec.BooleanValue XP_PER_KILL_COUNT_SUMMONS;

    // ========== 矿透（矿石描边） ==========

    /** 矿石透视的默认扫描半径（格）。 */
    public static ModConfigSpec.IntValue ORE_VISION_DEFAULT_RANGE;
    /** 矿石透视单次描边的矿石数量上限。 */
    public static ModConfigSpec.IntValue ORE_VISION_MAX_BLOCKS;

    // ========== 属性逐项调参（按内置属性表自动生成） ==========

    /** 属性 id → 「每点数值」配置项。 */
    public static final java.util.Map<String, ModConfigSpec.DoubleValue> STAT_PER_POINT =
            new java.util.LinkedHashMap<>();
    /** 属性 id → 「可投入点数上限」配置项（开关型属性即解锁所需点数）。 */
    public static final java.util.Map<String, ModConfigSpec.IntValue> STAT_MAX_POINTS =
            new java.util.LinkedHashMap<>();

    /**
     * 读取某个属性的「每点数值」；属性不在配置表中（外部属性）或配置尚未加载时返回兜底值。
     */
    public static float statPerPoint(String statId, float fallback) {
        ModConfigSpec.DoubleValue value = STAT_PER_POINT.get(statId);
        if (value == null) return fallback;
        try {
            Double v = value.get();
            return v == null ? fallback : v.floatValue();
        } catch (Throwable notLoaded) {
            return fallback;
        }
    }

    /** 读取某个属性的「可投入点数上限」；同上。 */
    public static int statMaxPoints(String statId, int fallback) {
        ModConfigSpec.IntValue value = STAT_MAX_POINTS.get(statId);
        if (value == null) return fallback;
        try {
            Integer v = value.get();
            return v == null || v < 1 ? fallback : v;
        } catch (Throwable notLoaded) {
            return fallback;
        }
    }

    // ========== 被动效果设置 ==========

    public static ModConfigSpec.IntValue HEALTH_REGEN_INTERVAL;
    public static ModConfigSpec.IntValue MAGNET_RANGE;
    public static ModConfigSpec.IntValue VEIN_MINER_MAX_BLOCKS;

    // 连锁挖掘「按标签组匹配」时视为同一类的方块标签（支持 * / ? 通配）
    public static ModConfigSpec.ConfigValue<List<? extends String>> VEIN_MINER_MATCH_TAGS;

    // 弹射物追踪扫描半径（方块），以玩家为中心
    public static ModConfigSpec.IntValue PROJECTILE_TRACKING_RANGE;

    // ========== GUI 设置 ==========

    public static ModConfigSpec.BooleanValue SHOW_HIDDEN_STATS;

    /** 「功能开关」把某项关掉时的执行模式（整合包作者的全局设置）。 */
    public static ModConfigSpec.EnumValue<com.infinitestats.stats.FeatureDisableMode> FEATURE_DISABLE_MODE;

    /**
     * 作者预设的「开局默认禁用」属性列表（物品 id 形如 {@code auto_fish}）。
     * 只在玩家数据首次加载时写入一次，之后改这里不影响已有玩家；
     * 后续开放由任务奖励指令 {@code /infstats feature <id> on} 负责。
     */
    public static ModConfigSpec.ConfigValue<List<? extends String>> DISABLED_STATS;

    /** 读取「开局默认禁用」列表（配置未加载时返回空列表）。 */
    public static List<String> defaultDisabledStats() {
        try {
            List<? extends String> list = DISABLED_STATS.get();
            return list == null ? List.of() : List.copyOf(list);
        } catch (Throwable t) {
            return List.of();
        }
    }

    // ========== 兼容性设置 ==========

    public static ModConfigSpec.BooleanValue ENABLE_ATTRIBUTE_DISCOVERY;

    // ========== EMC 等价交换设置 ==========

    public static ModConfigSpec.DoubleValue EMC_LOSS_RATE;
    public static ModConfigSpec.BooleanValue EMC_ENABLED;

    // 等价交换（ProjectE）联动：仅当检测到 projecte 模组时才有实际作用
    public static ModConfigSpec.BooleanValue PE_AUTO_LEARN;

    // 未知物品（无配方 / 锚点 / ProjectE 知识）的兜底 EMC 值；0 = 关闭（保持原行为）
    public static ModConfigSpec.LongValue EMC_FALLBACK_VALUE;

    // ========== 时间加速设置（加速属性） ==========

    // 基础半径：即使玩家未加「加速半径」点数也生效的最小半径
    public static ModConfigSpec.IntValue TIME_ACCEL_RADIUS;

    // ========== 随身熔炉加速设置 ==========

    // 每提升一级熔炉速度所消耗的可分配点数（属性点数）
    public static ModConfigSpec.IntValue FURNACE_SPEED_COST;

    // ========== 天气控制设置 ==========

    /** 「天气」面板切换后，新天气持续的时间（tick）。 */
    public static ModConfigSpec.IntValue WEATHER_CYCLE_DURATION;

    // ========== 区块强加载设置 ==========

    /** 每个维度最多可同时强加载的区块数量。 */
    public static ModConfigSpec.IntValue CHUNK_LOADER_MAX_FORCED_CHUNKS;

    // ========== 自动入库设置 ==========

    /** 自动入库的扫描间隔（tick），值越小越及时、但存储网络调用开销越高。 */
    public static ModConfigSpec.IntValue AUTO_DEPOSIT_INTERVAL;

    /** true = 快捷栏（0-8 号槽）的物品不会被自动存入存储，避免把随身工具吸走。 */
    public static ModConfigSpec.BooleanValue AUTO_DEPOSIT_KEEP_HOTBAR;

    /** 自动入库黑名单（物品 ID，前缀 {@code #} 表示物品标签）。 */
    public static ModConfigSpec.ConfigValue<List<? extends String>> AUTO_DEPOSIT_BLACKLIST;

    /**
     * 「生物面板」是否允许**从面板上直接改生物** —— 包括开关（AI / 静音 / 无敌 / 无重力等）
     * 与属性数值（把它的生命上限、攻击力、移动速度改成任意值）。
     * <p>
     * true（默认）= 解锁 {@code entity_panel} 属性、手持观察杖右键生物的玩家都能改；
     * false = 面板变成只读，属性与开关都只能看不能点。多人服若不想让玩家乱动别人的生物
     * （把别人的宠物变木头、把 BOSS 的生命上限调成 1 之类），把这里关掉即可；
     * 想彻底不给开面板，则用「功能开关」把整个 {@code entity_panel} 关掉。
     */
    public static ModConfigSpec.BooleanValue ALLOW_ENTITY_EDITING;

    // ========== 额外打击设置 ==========

    /** 「额外打击」每次打击造成的伤害占本次伤害的比例（默认 0.2 = 20%）。 */
    public static ModConfigSpec.DoubleValue EXTRA_STRIKE_DAMAGE_RATIO;

    // ========== 满饱食度进食设置 ==========

    /** 「满饱食度进食」的限制名单（物品 ID 或 {@code #} 标签；留空 = 所有食物都适用）。 */
    public static ModConfigSpec.ConfigValue<List<? extends String>> ALWAYS_EAT_ONLY_LIST;

    /** 「满饱食度进食」的例外名单（优先级高于限制名单）。 */
    public static ModConfigSpec.ConfigValue<List<? extends String>> ALWAYS_EAT_EXCLUDE_LIST;

    // ========== 机械动力「无限应力」设置 ==========

    /**
     * 「无限应力」把本人放置的动力网络产能改写成的值（SU）。
     * <p>
     * 用「够大但仍有限」的数而不是 {@code Float.MAX_VALUE}：真实网络的耗能总量不过几十万 SU，
     * 10 亿永远够用；而 {@code Float.MAX_VALUE} 参与乘法 / 打印 / 第三方附属的数值处理时容易出意外
     * （溢出成 Infinity、界面显示成 3.4E38 之类）。管理员可以把这条线拉低（给「无限应力」设上限，
     * 超出的大网络依旧会超载）或拉高。
     */
    public static ModConfigSpec.LongValue INFINITE_STRESS_CAPACITY;

    // ========== 「无限能源」设置 ==========

    /** 「无限能源」是否也补「玩家自己放置的能量方块」（关掉则只补背包 / 饰品 / 载具）。 */
    public static ModConfigSpec.BooleanValue INFINITE_ENERGY_BLOCKS;

    /** 「无限能源」扫描自己放置的能量方块时的半径（格）。 */
    public static ModConfigSpec.IntValue ENERGY_BLOCK_RADIUS;

    /** 「无限能源」扫描自己放置的能量方块的间隔（tick）。 */
    public static ModConfigSpec.IntValue ENERGY_BLOCK_INTERVAL;

    // ========== 自动钓鱼设置 ==========

    /** 自动钓鱼：收杆后重新抛竿的最短延迟（tick）。 */
    public static ModConfigSpec.IntValue AUTO_FISH_RECAST_DELAY_MIN;

    /** 自动钓鱼：收杆后重新抛竿的最长延迟（tick）。 */
    public static ModConfigSpec.IntValue AUTO_FISH_RECAST_DELAY_MAX;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

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
        AUTO_REVIVE_PREVENT_LETHAL = builder
                .comment("true=在伤害结算阶段就把「这一下会致死」的攻击拦下来并直接复活（连死亡事件都不会触发，",
                         "/kill 与各种绕过无敌的致死伤害同样有效）；false=回到只在死亡事件里复活。")
                .define("autoRevivePreventLethal", true);
        AUTO_REVIVE_VOID_ESCAPE = builder
                .comment("true=掉出世界（虚空 / 世界底部）时立刻拉回重生点，不占用复活冷却。",
                         "这条路径不检查冷却：虚空伤害每 tick 都在跳，靠死亡复活一次人还在虚空里。")
                .define("autoReviveVoidEscape", true);
        AUTO_REVIVE_VOID_ESCAPE_DEPTH = builder
                .comment("距离世界最低点下方多少格算「已掉出世界」。",
                         "默认 8：主世界 y < -72、下界/末地 y < -8 触发（早于原版 -128 的掉出世界伤害）。")
                .defineInRange("autoReviveVoidEscapeDepth", 8, 1, 256);
        builder.pop();

        // 战斗结算设置
        builder.push("Combat");
        CRIT_BASE_MULTIPLIER = builder
                .comment("暴击基础倍率（在此之上再叠加 crit_damage 属性的伤害）。默认 1.5 = 暴击造成 150% 伤害。")
                .defineInRange("critBaseMultiplier", 1.5D, 1.0D, 100.0D);
        CRIT_CHANCE_CAP = builder
                .comment("暴击率上限。默认 1.0 = 最多必定暴击；调低可以让暴击率永远到不了 100%。")
                .defineInRange("critChanceCap", 1.0D, 0.0D, 1.0D);
        FULL_CHARGE_THRESHOLD = builder
                .comment("「满蓄力」判定阈值。攻击强度（0~1）高于它才吃暴击与面板真伤估算，默认 0.9。")
                .defineInRange("fullChargeThreshold", 0.9D, 0.0D, 1.0D);
        ARMOR_PENETRATION_CAP = builder
                .comment("护甲穿透上限（比例）。默认 1.0 = 最多完全无视护甲。")
                .defineInRange("armorPenetrationCap", 1.0D, 0.0D, 1.0D);
        LIFE_STEAL_CAP = builder
                .comment("生命偷取上限（比例）。默认 1.0 = 单次最多吸回与伤害等量的血；调低可压制吸血流。")
                .defineInRange("lifeStealCap", 1.0D, 0.0D, 10.0D);
        DODGE_CHANCE_CAP = builder
                .comment("闪避率上限。默认 1.0 = 可以堆到必定闪避。")
                .defineInRange("dodgeChanceCap", 1.0D, 0.0D, 1.0D);
        BLOCK_CHANCE_CAP = builder
                .comment("格挡率上限。默认 1.0 = 可以堆到必定格挡。")
                .defineInRange("blockChanceCap", 1.0D, 0.0D, 1.0D);
        DAMAGE_REDUCTION_CAP = builder
                .comment("伤害减免上限（比例）。默认 1.0 = 允许减到 100%（免疫）；想保留一点压力可设 0.9。")
                .defineInRange("damageReductionCap", 1.0D, 0.0D, 1.0D);
        DAMAGE_REFLECTION_CAP = builder
                .comment("反伤倍率上限（比例）。默认 1.0 = 最多反弹等量伤害。")
                .defineInRange("damageReflectionCap", 1.0D, 0.0D, 10.0D);
        AOE_LIFE_STEAL_RANGE = builder
                .comment("范围吸血的搜索半径（格），以被攻击目标为中心。")
                .defineInRange("aoeLifeStealRange", 5.0D, 0.5D, 32.0D);
        AOE_LIFE_STEAL_DAMAGE_RATIO = builder
                .comment("范围吸血对周围每个敌人造成的伤害 = 本次伤害 × 吸血比例 × 该值。默认 0.3。")
                .defineInRange("aoeLifeStealDamageRatio", 0.3D, 0.0D, 10.0D);
        AOE_LIFE_STEAL_HEAL_RATIO = builder
                .comment("范围吸血把「周围实际造成的总伤害」回流为治疗的比例。默认 0.5。")
                .defineInRange("aoeLifeStealHealRatio", 0.5D, 0.0D, 10.0D);
        MAX_EXTRA_STRIKES = builder
                .comment("「额外打击」的最大次数。默认 8；属性自身的点数上限另由 extra_strike 的点数上限控制。")
                .defineInRange("maxExtraStrikes", 8, 1, 64);
        SCOPE_ATTACK_DAMAGE_RATIO = builder
                .comment("「范围攻击」对周围每个敌人造成的伤害占本次伤害的比例。默认 0.5。")
                .defineInRange("scopeAttackDamageRatio", 0.5D, 0.0D, 10.0D);
        EXECUTE_THRESHOLD = builder
                .comment("「处决」触发血量比例：目标当前血量低于最大血量的该比例时追加处决伤害。默认 0.3。")
                .defineInRange("executeThreshold", 0.3D, 0.0D, 1.0D);
        INVINCIBILITY_HEAL_INTERVAL = builder
                .comment("「无敌」把血量回满的执行间隔（tick）。默认 20 = 每秒一次。")
                .defineInRange("invincibilityHealInterval", 20, 1, 1200);
        ABSORPTION_REFRESH_INTERVAL = builder
                .comment("吸收护盾的刷新间隔（tick）。默认 40 = 每 2 秒一次。")
                .defineInRange("absorptionRefreshInterval", 40, 1, 1200);
        DOUBLE_LOOT_CAP = builder
                .comment("双倍掉落概率上限。默认 1.0 = 可以堆到必定双倍。")
                .defineInRange("doubleLootCap", 1.0D, 0.0D, 1.0D);
        VILLAGER_DISCOUNT_CAP = builder
                .comment("村民交易折扣上限（比例）。默认 0.9 = 最多便宜到原价的 10%。")
                .defineInRange("villagerDiscountCap", 0.9D, 0.0D, 1.0D);
        COOLDOWN_REDUCTION_CAP = builder
                .comment("物品冷却缩减上限（比例）。默认 1.0 = 可以把冷却直接压到 0。")
                .defineInRange("cooldownReductionCap", 1.0D, 0.0D, 1.0D);
        AUTO_REPAIR_PER_LEVEL = builder
                .comment("「自动修复」每一级每秒修复的耐久点（0 级也至少按 1 点计算）。")
                .defineInRange("autoRepairPerLevel", 1.0D, 0.0D, 1000.0D);
        PROJECTILE_TRACKING_CONE_COS = builder
                .comment("弹射物追踪的视野锥余弦阈值：1.0 = 只追正前方，0.0 = 180° 全追，默认 0.5（约 ±60°）。")
                .defineInRange("projectileTrackingConeCos", 0.5D, -1.0D, 1.0D);
        XP_GAIN_CAP = builder
                .comment("「经验加成」属性的总倍率上限（1.0 = 不加成；0 = 不设上限）。默认 0。",
                        "模组经验与原版经验共用这一个上限。")
                .defineInRange("xpGainCap", 0.0D, 0.0D, 1000.0D);
        XP_GAIN_AFFECT_VANILLA = builder
                .comment("「经验加成」是否同时放大原版经验值。",
                        "原版经验的所有入口（经验球拾取、熔炉取出、村民交易、经验瓶等）最终都会走",
                        "Player#giveExperiencePoints，这里在该统一入口处按同一个倍率放大（只增不减）。",
                        "true（默认）= 一并放大；false = 只放大击杀获得的模组经验。")
                .define("xpGainAffectVanilla", true);
        builder.pop();

        // 经验 / 升级曲线补充项
        builder.push("Progression");
        PASSIVE_XP_ENABLED = builder
                .comment("「被动经验」总开关。false = 在线玩家不再每秒自动获得经验，只能靠击杀 / 其他途径。")
                .define("passiveXpEnabled", true);
        XP_PER_KILL_COUNT_SUMMONS = builder
                .comment("击杀经验是否也计入「你的召唤物 / 宠物造成的击杀」（含铁傀儡、召唤物、驯服宠物等）。",
                        "false（默认）= 只有玩家本人直接造成的击杀才给经验，与原行为一致。")
                .define("xpPerKillCountSummons", false);
        builder.pop();

        // 矿石透视
        builder.push("OreVision");
        ORE_VISION_DEFAULT_RANGE = builder
                .comment("矿石透视的默认扫描半径（格）。玩家可在面板里自行调整，这里只是初始值 / 上限基准。")
                .defineInRange("oreVisionDefaultRange", 24, 8, 64);
        ORE_VISION_MAX_BLOCKS = builder
                .comment("矿石透视单次描边的矿石数量上限。数量越大越吃帧，默认 1500。")
                .defineInRange("oreVisionMaxBlocks", 1500, 50, 8000);
        builder.pop();

        // 属性逐项调参（按内置属性表自动生成）
        builder.comment(
                "每个内置属性的「每点数值」与「可投入点数上限」。键名格式：<属性id>_perPoint / <属性id>_maxPoints。",
                "例：attack_damage_perPoint = 0.05 表示每点攻击力 +0.05；infinite_arrows_maxPoints = 5 表示投入 5 点解锁。",
                "开关型属性没有 _perPoint（改了没有效果），它的 _maxPoints 就是解锁所需点数。",
                "属性允许负向加点，因此 _perPoint 也可以填负数（效果会反向）；_maxPoints 同时是正负两个方向的绝对上限。",
                "改完重启游戏（或在主菜单重载配置）生效。外部模组属性（attr.*）不在本表内，沿用内置的自动估算值。");
        builder.push("Stats");
        for (com.infinitestats.stats.StatType.TuningDefault def
                : com.infinitestats.stats.StatType.tuningDefaults()) {
            String id = def.getId();
            if (!def.isToggle()) {
                // 用 6 位小数收敛 float 的二进制尾巴（0.005f → 0.005，而不是 0.004999999888241291）
                double defaultPerPoint = Math.round(def.getPerPoint() * 1_000_000.0D) / 1_000_000.0D;
                STAT_PER_POINT.put(id, builder.defineInRange(id + "_perPoint",
                        defaultPerPoint, -1000.0D, 1000.0D));
            }
            STAT_MAX_POINTS.put(id, builder.defineInRange(id + "_maxPoints",
                    def.getMaxPoints(), 1, Integer.MAX_VALUE));
        }
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
                .comment("连锁挖掘单次方块数的默认值。",
                         "玩家可以在「连锁设置」面板里单独调整（8~256），",
                         "面板里选择「跟随服务端」时用的就是这里的数值。")
                .defineInRange("veinMinerMaxBlocks", 64, 8, 256);
        VEIN_MINER_MATCH_TAGS = builder
                .comment("连锁挖掘「按标签组匹配」时，把哪些方块标签视为同一类（支持 * 与 ? 通配）。",
                         "默认只认矿物与原木：铁矿与深板岩铁矿共享 forge:ores/iron 所以能一起连锁，",
                         "而石头/泥土不共享这些标签，因此不会被误连锁。",
                         "加入 *:storage_blocks/* 会让铁块等存储方块也互相连锁，谨慎开启。")
                .defineList("veinMinerMatchTags",
                        List.of("*:ores", "*:ores/*", "minecraft:logs", "minecraft:logs_that_burn"),
                        o -> o instanceof String);
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
                .comment("「功能开关」把某个属性关掉（/infstats feature <属性> off）后的执行方式：",
                         "keep   = 整条失效，已投入的点数保留（默认）；",
                         "refund = 整条失效，并把该属性已投入的点数退回可用点数；",
                         "hide   = 只从属性面板隐藏，效果与指令照常生效，点数不返还。",
                         "这是整合包作者的全局设置，不按玩家存；OP 也可用 /infstats feature mode 切换。")
                .defineEnum("featureDisableMode",
                        com.infinitestats.stats.FeatureDisableMode.INACTIVE_KEEP_POINTS);
        DISABLED_STATS = builder
                .comment("作者预设的「开局默认禁用」属性列表（形如 auto_fish、keep_inventory）。",
                         "只在玩家数据首次加载时写入一次：之后改这里不影响已有玩家，",
                         "后续开放由任务奖励指令 /infstats feature <属性> on 负责。",
                         "留空（默认）= 所有属性开局都开着。")
                .defineList("disabledStats", List.<String>of(), o -> o instanceof String);
        HIDDEN_PANELS = builder
                .comment("要隐藏的面板入口（面板 id 列表）。整合包作者用它把不想给玩家看到的界面藏起来。",
                        "可用 id：stats / emc / item_editor / achievements / waypoint / ore_vision /",
                        "portable_tools / weather / chunk_loader / vein_miner / entity_panel / debuff_filter。",
                        "隐藏后对应的快捷键不再生效；已经打开的界面不受影响。",
                        "留空（默认）= 全部显示。")
                .defineList("hiddenPanels", List.<String>of(), o -> o instanceof String);
        HIDDEN_CATEGORIES = builder
                .comment("要整组隐藏的属性分类页签（分类枚举名，如 ATTACK / DEFENSE / UTILITY / EXTERNAL）。",
                        "隐藏后玩家看不到该页签，数字键也切不过去；已投入的点数仍然保留、效果照常生效。",
                        "留空（默认）= 显示全部分类。")
                .defineList("hiddenCategories", List.<String>of(), o -> o instanceof String);
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

        // 天气控制设置
        builder.push("WeatherControl");
        WEATHER_CYCLE_DURATION = builder
                .comment("「天气」面板切换一次后，新天气持续的时间（tick，20tick = 1 秒）。",
                         "默认 6000 tick = 5 分钟；到时间后天气会按原版规则自行演变。")
                .defineInRange("weatherCycleDuration", 6000, 200, 72000);
        builder.pop();

        // 区块强加载设置
        builder.push("ChunkLoader");
        CHUNK_LOADER_MAX_FORCED_CHUNKS = builder
                .comment("「强加载」每个维度最多可同时保持加载的区块数量。",
                         "强加载的区块即使附近没有玩家也会被服务端持续加载，数量越多服务器负担越重，",
                         "因此这里做硬性上限，防止误操作拖垮服务器。")
                .defineInRange("maxForcedChunks", 16, 1, 256);
        builder.pop();

        // 自动入库设置
        builder.push("AutoDeposit");
        AUTO_DEPOSIT_INTERVAL = builder
                .comment("「自动入库」的扫描间隔（tick），默认 20tick = 1 秒。",
                         "间隔越短越及时，但每次都会查询一遍存储网络，间隔过小会带来额外开销。")
                .defineInRange("autoDepositInterval", 20, 5, 1200);
        AUTO_DEPOSIT_KEEP_HOTBAR = builder
                .comment("是否保留快捷栏（0-8 号槽）中的物品不被自动入库。",
                         "true（默认）= 只把主背包 27 格里的物品自动存入存储，随身工具 / 武器不会被吸走；",
                         "false = 除当前手持的那一格之外，快捷栏也会一并存入存储。")
                .define("autoDepositKeepHotbar", true);
        AUTO_DEPOSIT_BLACKLIST = builder
                .comment("自动入库黑名单（物品 ID，如 minecraft:diamond）。",
                         "列表中的物品永远不会被自动存入存储网络；以 # 开头表示物品标签（如 #minecraft:logs）。",
                         "注：存储终端（RS / AE2 / 汤姆存储的无线终端等）与背包会被自动保护，无需在此重复配置。")
                .defineList("autoDepositBlacklist", List.<String>of(), o -> o instanceof String);
        builder.pop();

        // 生物面板设置
        builder.push("EntityPanel");
        ALLOW_ENTITY_EDITING = builder
                .comment("「生物面板」是否允许从面板上直接改生物（开关：AI / 静音 / 隐身 / 发光 / 无敌 / 无重力 / 幼年；",
                         "以及属性数值：把它的生命上限 / 攻击力 / 移动速度改成任意值 —— 改的是基础值，",
                         "会被夹进该属性自身的取值范围）。",
                         "true（默认）= 解锁「生物面板」属性、手持生物观察杖右键生物的玩家都能改；",
                         "false = 面板只读：属性照常显示，输入框与开关都不响应。",
                         "服务端还会另行校验：属性已激活 + 手里拿着观察杖 + 目标在 8 格内。",
                         "多人服若不想让玩家乱动别人的生物（把别人的宠物变木头、给 BOSS 上无敌），把这里关掉即可。")
                .define("allowEntityEditing", true);
        builder.pop();

        // 额外打击设置
        builder.push("ExtraStrike");
        EXTRA_STRIKE_DAMAGE_RATIO = builder
                .comment("「额外打击」每次打击造成的伤害占本次伤害的比例（默认 0.2 = 20%）。",
                         "追加伤害总量 = 本次伤害 × 该比例 × 额外打击等级（最多 8 级，即最高 160%）。",
                         "额外打击独立结算并忽略护甲与减伤；设为 0 可只保留属性条目、不造成伤害。")
                .defineInRange("extraStrikeDamageRatio", 0.2, 0.0, 10.0);
        builder.pop();

        // 满饱食度进食设置
        builder.push("AlwaysEat");
        ALWAYS_EAT_ONLY_LIST = builder
                .comment("「满饱食度进食」【限制名单】：只有名单里的食物能在饱食度已满时继续吃。",
                         "物品 ID（如 minecraft:bread），或 # 开头的物品标签（如 #c:foods）。",
                         "留空（默认）= 解锁该属性的玩家对【所有】食物都能满饱食度进食。")
                .defineList("alwaysEatOnlyList", List.<String>of(), o -> o instanceof String);
        ALWAYS_EAT_EXCLUDE_LIST = builder
                .comment("「满饱食度进食」【例外名单】：名单里的食物不适用本功能，一律交回原版判定。",
                         "写法同上（物品 ID 或 # 标签）；优先级高于上面的限制名单 ——",
                         "同时出现在两份名单里时按【例外】处理。")
                .defineList("alwaysEatExcludeList", List.<String>of(), o -> o instanceof String);
        builder.pop();

        // 无限能源：能量方块设置
        builder.push("InfiniteEnergy");
        INFINITE_ENERGY_BLOCKS = builder
                .comment("「无限能源」是否覆盖世界里的方块。",
                         "true（默认）= 在玩家自身携带 / 骑乘的能源之外，再把玩家【自己放置】的",
                         "FE 能量方块（储能方块、耗电机器、发电机等，方块实体上挂能量能力的）也补满；",
                         "false = 只补玩家自身携带 / 骑乘的能源，不碰世界里的任何方块。",
                         "归属按「谁放的」记录，只补玩家本人放置的方块，不会影响到别人或公共机器。")
                .define("infiniteEnergyBlocks", true);
        ENERGY_BLOCK_RADIUS = builder
                .comment("能量方块的扫描半径（方块）。以玩家为中心，水平与垂直方向同半径。",
                         "只补该范围内、玩家本人放置的能量方块。数值越大覆盖越广，但每次扫描的方块实体越多、开销越高。")
                .defineInRange("energyBlockRadius", 16, 1, 64);
        ENERGY_BLOCK_INTERVAL = builder
                .comment("能量方块的扫描 / 补电间隔（tick，20tick = 1 秒），默认 20tick = 1 秒。",
                         "方块实体扫描比遍历背包贵，所以单独用这个间隔节流；间隔越短补得越勤、开销越高。",
                         "玩家自身携带 / 骑乘的能源不受此项影响，仍是每 tick 补满。")
                .defineInRange("energyBlockInterval", 20, 1, 200);
        builder.pop();

        // 机械动力「无限应力」设置
        builder.push("CreateStress");
        INFINITE_STRESS_CAPACITY = builder
                .comment("「无限应力」把本人放置的动力网络产能改写成的值（SU）。",
                         "用「够大但仍有限」的数而不是 Float.MAX_VALUE：真实网络的耗能总量不过几十万 SU，",
                         "10 亿永远够用；而 MAX_VALUE 参与乘法 / 打印 / 界面显示时容易出意外（Infinity、3.4E38）。",
                         "只有装了机械动力时这条配置才有效。")
                .defineInRange("infiniteStressCapacity", 1_000_000_000L, 1_000L, Long.MAX_VALUE);
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