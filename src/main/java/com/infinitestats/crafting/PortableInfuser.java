package com.infinitestats.crafting;

import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import com.infinitestats.util.EnchantLimits;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 进阶高级附魔台的核心规则与换算（自选附魔 / 经验修复 / 经验回收）。
 * <p>
 * 参考 Fuzss 的 Enchanting Infuser（「进阶高级附魔台」档）：<b>不用随机、自己挑附魔</b>，
 * 并且把进阶档的几项额外功能一并做了进来：
 * <ul>
 *   <li><b>自选附魔</b>：列出该物品能附的所有魔，逐条选等级，按价付款（付款方式见 {@link Payment}）；</li>
 *   <li><b>改已有附魔</b>：物品上已有的附魔同样在列表里，升/降/清零即可升级、降级、移除；</li>
 *   <li><b>经验修复</b>：花等级（或点数）直接把耐久修满，不需要修复材料；</li>
 *   <li><b>经验回收</b>：把这件物品上的附魔全部拆下来，按价折算返还（类似砂轮）；</li>
 *   <li><b>制作附魔书</b>：往槽里放书，选好附魔即产出附魔书，用来搭自己的附魔库；</li>
 *   <li><b>铁砧限定组合</b>：原版铁砧不校验「附魔类别是否适配该物品」，这里同样放宽
 *       （斧头加锋利、头盔加深海探索者等），见 {@link #anvilAllowed}。</li>
 * </ul>
 * <b>本类必须双端可用</b>：界面（客户端）要拿 {@link #applicable} 与 {@link #costOf} 画列表，
 * 服务端要拿 {@link #performEnchant} 等做校验与结算。查询类方法纯函数、两端结果一致，
 * 因此客户端只需把「选了哪些附魔、各多少级」发到服务端，无需额外的同步协议。
 */
public final class PortableInfuser {

    /** 付款方式：经验等级 / 属性点数（界面右上角切换）。不消耗青金石。 */
    public enum Payment {
        /** 扣原版经验等级（创造模式免扣）。 */
        LEVELS,
        /** 扣本模组的可用属性点数。 */
        POINTS;

        /** 网络包用：稳定序号，两端必须一致。 */
        public int id() {
            return this == POINTS ? 1 : 0;
        }

        public static Payment byId(int id) {
            return id == 1 ? POINTS : LEVELS;
        }
    }

    /**
     * 修复换算：每 1 级能修回「总耐久 / 该值」点耐久（至少 1 点）。
     * <p>
     * 取 25 是手感折中：一把钻石镐（1561 耐久）从空修满约 25 级，
     * 与「用材料在铁砧上修」相比偏贵（这是买断式修理的代价），但不至于离谱。
     */
    private static final int REPAIR_DURABILITY_PER_LEVEL = 25;

    /** 经验回收的返还比例：按「重新买回来要多少」折算，留出损耗，避免倒卖刷经验。 */
    private static final double RECYCLE_REFUND_RATIO = 0.6D;

    /**
     * 各稀有度的「每级基础价」（单位：经验等级 或 属性点数）。
     * <p>
     * 用原版稀有度（＝附魔权重）定价，而不是照抄某个公式：稀有度本来就代表获取难度，
     * 这样常见附魔（锋利、保护）便宜、稀有附魔（精准采集、经验修补）贵，
     * 并且对模组附魔自动适用 —— 它们也都有自己的稀有度。
     */
    private static final int PRICE_COMMON = 3;
    private static final int PRICE_UNCOMMON = 4;
    private static final int PRICE_RARE = 6;
    private static final int PRICE_VERY_RARE = 8;

    /** 仅限宝藏的附魔（经验修补、冰霜行者、灵魂疾行等）加价倍率。 */
    private static final int TREASURE_MULTIPLIER = 2;

    private PortableInfuser() {}

    // ========== 物品判定 ==========

    /** 书（普通书 / 附魔书）：进阶高级附魔台可以把附魔做成书。 */
    public static boolean isBook(ItemStack stack) {
        return stack.is(Items.BOOK) || stack.is(Items.ENCHANTED_BOOK);
    }

    /**
     * 该物品能不能放进附魔槽：书本可以，可附魔物品（有附魔能力值）可以，
     * <b>已经带附魔的物品也可以</b>（哪怕是不可再附魔的，也要能改 / 能拆）。
     */
    public static boolean isSupported(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (isBook(stack)) return true;
        return stack.getEnchantmentValue() > 0 || stack.isEnchanted();
    }

    // ========== 可选附魔 ==========

    /**
     * 该物品当前可选的附魔清单（顺序固定：价低的在前，同价按注册名排序）。
     * <p>
     * <b>顺序必须固定</b>：列表顺序若随「当前选了什么等级」变化，玩家每点一下 ◀ ▶ 整张表就会跳，
     * 完全没法用。因此不按「是否已附魔」排序 —— 已附魔的靠行内高亮表示。
     */
    public static List<Enchantment> applicable(ItemStack stack) {
        List<Enchantment> list = new ArrayList<>();
        if (!isSupported(stack)) return list;
        for (Enchantment enchantment : BuiltInRegistries.ENCHANTMENT) {
            if (canApply(enchantment, stack)) list.add(enchantment);
        }
        list.sort(Comparator.comparingInt(PortableInfuser::basePrice)
                .thenComparing(PortableInfuser::registryName));
        return list;
    }

    /** 这条附魔能不能附到该物品上。 */
    public static boolean canApply(Enchantment enchantment, ItemStack stack) {
        if (stack.isEmpty() || enchantment == null) return false;
        // 书：什么都能承载（「自己搭附魔库」就是这个功能的意义所在，连诅咒都让玩家自己决定）
        if (isBook(stack)) return true;
        // 原版判定：附魔类别是否适配该物品
        if (enchantment.canEnchant(stack)) return true;
        // 铁砧限定组合
        return anvilAllowed(enchantment, stack);
    }

    /**
     * 铁砧限定组合：原版铁砧组合时不校验附魔类别，所以「斧头 + 锋利」「头盔 + 深海探索者」
     * 这类组合是合法的。这里按<b>大类</b>放宽到同一类物品都能附 —— 比「任意物品随便附」
     * 克制得多（否则给剑加保护、给镐加忠诚也能成，那就不只是「铁砧限定」了）。
     */
    private static boolean anvilAllowed(Enchantment enchantment, ItemStack stack) {
        Item item = stack.getItem();
        return switch (enchantment.category) {
            case WEAPON -> item instanceof SwordItem || item instanceof AxeItem
                    || item instanceof HoeItem || item instanceof TridentItem;
            // 剑在铁砧上也能加效率/耐久类挖掘附魔
            case DIGGER -> item instanceof DiggerItem || item instanceof SwordItem;
            case ARMOR, ARMOR_HEAD, ARMOR_CHEST, ARMOR_LEGS, ARMOR_FEET -> item instanceof ArmorItem;
            case WEARABLE -> item instanceof ArmorItem || item instanceof ElytraItem;
            default -> false;
        };
    }

    // ========== 定价 ==========

    /**
     * 单条附魔的花费：稀有度基础价 × 等级（宝藏附魔 ×2）。
     * <p>
     * 刻意<b>不</b>对「超出原版上限的等级」加价：突破本来就更贵（等级越高线性越贵），
     * 门槛交给「附魔上限突破」那个开关属性。这里必须用 long 相乘再饱和 ——
     * 突破后的等级可以是天文数字，int 溢出成负数会变成「倒贴钱」。
     */
    public static int costOf(Enchantment enchantment, int level) {
        if (enchantment == null || level <= 0) return 0;
        long price = (long) basePrice(enchantment) * level;
        if (enchantment.isTreasureOnly()) price *= TREASURE_MULTIPLIER;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, price));
    }

    /** 整张选择表的花费合计（同样饱和，不溢出）。 */
    public static int totalCost(Map<Enchantment, Integer> selection) {
        long total = 0L;
        for (Map.Entry<Enchantment, Integer> entry : selection.entrySet()) {
            total += costOf(entry.getKey(), entry.getValue());
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    private static int basePrice(Enchantment enchantment) {
        return switch (enchantment.getRarity()) {
            case COMMON -> PRICE_COMMON;
            case UNCOMMON -> PRICE_UNCOMMON;
            case RARE -> PRICE_RARE;
            case VERY_RARE -> PRICE_VERY_RARE;
        };
    }

    private static String registryName(Enchantment enchantment) {
        var key = BuiltInRegistries.ENCHANTMENT.getKey(enchantment);
        return key == null ? "" : key.toString();
    }

    /** 价格文案（界面与提示共用）：经验等级 / 属性点数两种口径。 */
    public static Component priceText(Payment payment, long amount) {
        return payment == Payment.POINTS
                ? Component.translatable("gui.infinitestats.infuser.price_points", amount)
                : Component.translatable("gui.infinitestats.infuser.price_levels", amount);
    }

    // ========== 自选附魔 ==========

    /**
     * 服务端执行附魔：校验 → 按<b>差额</b>结算 → 改写物品（书则产出附魔书）。
     * <p>
     * 结算按差额而不是按整张表：物品自带的附魔不该重复收费，降级还要退回差价
     * （线性定价下「先买 5 级再降到 3 级」的总花费恰好等于直接买 3 级，不存在套利）。
     *
     * @return 给玩家看的反馈（成功与失败都有文案）
     */
    public static Component performEnchant(ServerPlayer player, Container input,
                                           Map<Enchantment, Integer> selection, Payment payment) {
        ItemStack stack = input.getItem(0);
        if (stack.isEmpty()) return message("no_item");
        if (!isSupported(stack)) return message("unsupported");

        Map<Enchantment, Integer> result = new LinkedHashMap<>(EnchantmentHelper.getEnchantments(stack));
        long cost = 0L;
        int changed = 0;

        for (Map.Entry<Enchantment, Integer> entry : selection.entrySet()) {
            Enchantment enchantment = entry.getKey();
            int level = Math.max(0, entry.getValue());
            Integer before = result.get(enchantment);

            if (level <= 0) {
                if (before != null) {
                    result.remove(enchantment);
                    cost -= costOf(enchantment, before);
                    changed++;
                }
                continue;
            }
            // 上限取「该玩家的」：解锁「附魔上限突破」后不再受原版 getMaxLevel 约束
            if (!canApply(enchantment, stack) || level > EnchantLimits.maxLevel(player, enchantment)) {
                return message("bad_selection");
            }
            // 刻意**不做互斥校验**：进阶高级附魔台的定位就是「想怎么配就怎么配」，
            // 锋利 / 亡灵杀手 / 节肢杀手这类原版互斥的组合允许同时附在一件物品上
            // （它们的效果本来就是各自独立结算的，互斥只是原版为了保证随机附魔不出怪组合）。
            cost += costOf(enchantment, level) - (before == null ? 0L : costOf(enchantment, before));
            if (before == null || before != level) changed++;
            result.put(enchantment, level);
        }

        if (changed == 0) return message("nothing_selected");

        PlayerStats stats = statsOf(player);
        if (cost > 0L) {
            if (!canPay(player, stats, payment, cost)) {
                return message(payment == Payment.POINTS ? "not_enough_points" : "not_enough_levels",
                        priceText(payment, cost));
            }
            pay(player, stats, payment, cost);
        } else if (cost < 0L) {
            refund(player, stats, payment, -cost);
        }

        if (isBook(stack)) {
            // 书 → 附魔书：从槽里取走一本，成品优先放回槽（槽空了就放这儿，玩家直接拿走）
            ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
            EnchantmentHelper.setEnchantments(result, book);
            stack.shrink(1);
            if (stack.isEmpty()) {
                input.setItem(0, book);
            } else if (!player.getInventory().add(book)) {
                player.drop(book, false);
            }
            input.setChanged();
        } else {
            EnchantmentHelper.setEnchantments(result, stack);
            input.setChanged();
        }
        // 降级退差价时 cost 为负，文案要跟着变「返还」而不是「消耗」
        Component pricePart = cost >= 0L
                ? message("cost_part", priceText(payment, cost))
                : message("refund_part", priceText(payment, -cost));
        return message("enchant_done", changed, pricePart);
    }

    // ========== 经验修复 ==========

    /** 该物品能不能修（有耐久、且有损耗）。 */
    public static boolean canRepair(ItemStack stack) {
        return !stack.isEmpty() && stack.isDamageableItem() && stack.getDamageValue() > 0;
    }

    /** 修满所需花费。 */
    public static int repairCost(ItemStack stack) {
        if (!canRepair(stack)) return 0;
        int perLevel = Math.max(1, stack.getMaxDamage() / REPAIR_DURABILITY_PER_LEVEL);
        return Math.max(1, (stack.getDamageValue() + perLevel - 1) / perLevel);
    }

    /** 服务端执行修复：花等级（或点数）把耐久直接修满。 */
    public static Component performRepair(ServerPlayer player, Container input, Payment payment) {
        ItemStack stack = input.getItem(0);
        if (stack.isEmpty()) return message("no_item");
        if (!canRepair(stack)) return message("nothing_to_repair");

        int cost = repairCost(stack);
        PlayerStats stats = statsOf(player);
        if (!canPay(player, stats, payment, cost)) {
            return message(payment == Payment.POINTS ? "not_enough_points" : "not_enough_levels",
                    priceText(payment, cost));
        }
        pay(player, stats, payment, cost);
        stack.setDamageValue(0);
        input.setChanged();
        return message("repaired", priceText(payment, cost));
    }

    // ========== 经验回收 ==========

    /** 该物品能不能回收（身上有附魔）。 */
    public static boolean canRecycle(ItemStack stack) {
        return !stack.isEmpty() && !EnchantmentHelper.getEnchantments(stack).isEmpty();
    }

    /** 回收能返还多少（按价折算，向下取整，至少 1）。 */
    public static int recycleRefund(ItemStack stack) {
        Map<Enchantment, Integer> map = EnchantmentHelper.getEnchantments(stack);
        if (map.isEmpty()) return 0;
        long sum = 0L;
        for (Map.Entry<Enchantment, Integer> entry : map.entrySet()) {
            sum += costOf(entry.getKey(), entry.getValue());
        }
        return Math.max(1, (int) Math.floor(sum * RECYCLE_REFUND_RATIO));
    }

    /**
     * 服务端执行回收：拆掉全部附魔并返还。
     * <p>
     * 顺带清掉铁砧的「累积惩罚」（{@code RepairCost}）—— 附魔都没了，惩罚还留着
     * 会让玩家拿去铁砧改的时候莫名其妙贵一大截（原版砂轮同理）。
     */
    public static Component performRecycle(ServerPlayer player, Container input, Payment payment) {
        ItemStack stack = input.getItem(0);
        if (stack.isEmpty()) return message("no_item");
        if (!canRecycle(stack)) return message("nothing_to_recycle");

        int refund = recycleRefund(stack);
        EnchantmentHelper.setEnchantments(Map.of(), stack);
        stack.setRepairCost(0);
        input.setChanged();
        refund(player, statsOf(player), payment, refund);
        return message("recycled", priceText(payment, refund));
    }

    // ========== 付款 ==========

    /** 钱够不够。创造模式免经验（与附魔台一致），但属性点数是本模组的账，照扣。 */
    public static boolean canPay(ServerPlayer player, PlayerStats stats, Payment payment, long cost) {
        if (cost <= 0L) return true;
        if (payment == Payment.POINTS) return stats != null && stats.getAvailablePoints() >= cost;
        return player.isCreative() || player.experienceLevel >= cost;
    }

    private static void pay(ServerPlayer player, PlayerStats stats, Payment payment, long cost) {
        if (cost <= 0L) return;
        if (payment == Payment.POINTS) {
            if (stats != null) stats.setAvailablePoints(stats.getAvailablePoints() - cost);
        } else if (!player.isCreative()) {
            // 等级上限是 int 上限，这里再夹一道，避免天文数字转 int 时符号翻掉变成「白送等级」
            player.giveExperienceLevels((int) -Math.min(cost, Integer.MAX_VALUE));
        }
    }

    /** 返还（回收返还、降级退差价）。 */
    public static void refund(ServerPlayer player, PlayerStats stats, Payment payment, long amount) {
        if (amount <= 0L) return;
        if (payment == Payment.POINTS) {
            if (stats != null) stats.setAvailablePoints(stats.getAvailablePoints() + amount);
        } else {
            player.giveExperienceLevels((int) amount);
        }
    }

    public static PlayerStats statsOf(ServerPlayer player) {
        return player.getCapability(PlayerStatsProvider.PLAYER_STATS).orElse(null);
    }

    private static Component message(String key, Object... args) {
        return Component.translatable("message.infinitestats.infuser." + key, args);
    }
}
