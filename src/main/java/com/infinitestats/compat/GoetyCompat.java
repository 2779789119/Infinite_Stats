package com.infinitestats.compat;

import com.infinitestats.stats.PlayerStats;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;

/**
 * Goety（诡厄巫法）联动：无限灵魂能量。
 * <p>
 * 解锁「无限灵魂能量」后，玩家的灵魂能量**恒满**，施法 / 仪式 / 灵魂修补器 / 巫妖回血等
 * 所有消耗灵魂的路径都不会再把灵魂用光，HUD 上的灵魂能量条一直满格。
 * <p>
 * Goety 的灵魂有两套独立的池子，这里各补一份（缺一不可，覆盖它自己的 {@code getSoulsAmount}
 * 门槛判定与 HUD 读数）：
 * <ol>
 *   <li><b>Arca / 灵魂能量池</b>：玩家拿到 Arca（{@code SEActive = true}）后用能力值
 *       {@code ISoulEnergy#soulEnergy} 存灵魂，上限是配置 {@code maxArcaSouls}（默认 100000）。
 *       走 {@code SEHelper#increaseSESouls(Player, int)} 补 —— 它内部用
 *       {@code Math.min(当前 + 增量, MaxArcaSouls)} 自己夹到上限，补满返回 {@code true}、
 *       已满返回 {@code false}（据此决定要不要发同步包）；</li>
 *   <li><b>灵魂图腾池</b>：没有 Arca 的玩家靠 Curios 里的「灵魂图腾（Totem of Souls）」存灵魂，
 *       灵魂数写在物品 NBT 的 {@code "Souls"} 上、上限在 {@code "Max Souls"}。走
 *       {@code ITotem} 的静态读写补到上限 —— 只在上限可读（图腾已 tick 过、写下了 Max Souls）时才补。</li>
 * </ol>
 * <p>
 * 全程<b>反射</b>（与 {@code SuperbWarfareCompat} / {@code TaczCompat} 同一套思路）：Goety 只是可选
 * 运行时联动，本模组不声明对它的编译期依赖。没装时 {@code Class.forName} 直接失败并永久短路、零开销；
 * 版本对不上导致某个方法取不到时同样整体短路，只让这条属性退化成原版行为（反射失败静默跳过）。
 * <p>
 * 反射到的成员（已用 {@code javap} 对着整合包里的 {@code goety-2.5.58.4} 核对）：
 * {@code SEHelper#getSEActive(Player)} / {@code #increaseSESouls(Player, int)} /
 * {@code #sendSEUpdatePacket(Player)}，{@code TotemFinder#FindTotem(Player)}，
 * {@code ITotem#currentSouls(ItemStack)} / {@code #maximumSouls(ItemStack)} / {@code #setSoulsAmount(ItemStack, int)}。
 */
public final class GoetyCompat {

    private static final String SE_HELPER = "com.Polarice3.Goety.utils.SEHelper";
    private static final String TOTEM_FINDER = "com.Polarice3.Goety.utils.TotemFinder";
    private static final String ITOTEM = "com.Polarice3.Goety.api.items.magic.ITotem";

    /** 这条联动属性。 */
    private static final String STAT_SOUL = "goety_infinite_soul";

    /**
     * 交给 {@code SEHelper#increaseSESouls} 的增量：足够大、保证一次补满，又不会让
     * {@code 当前 + 增量} 溢出 —— 当前值最多是 {@code maxArcaSouls}（百万级），加 10 亿仍在 int 内。
     */
    private static final int SAFE_BIG_INCREMENT = 1_000_000_000;

    /** 已解析成功 / 已判定不可用（分别短路，避免每 tick 重试反射）。 */
    private static boolean resolved;
    private static boolean unavailable;

    private static Method getSEActive;
    private static Method increaseSESouls;
    private static Method sendSEUpdatePacket;
    private static Method findTotem;
    private static Method totemCurrentSouls;
    private static Method totemMaximumSouls;
    private static Method totemSetSoulsAmount;

    private GoetyCompat() {}

    /**
     * 每个玩家 tick 调用一次（由 {@code UtilityHandler} 驱动）。
     * <p>
     * 没解锁、或没装 Goety 时在第一句 / 第二句就短路，零开销。运行时异常静默吞掉 ——
     * 单个玩家一帧出错不该打断整条玩家 tick（外层 {@code UtilityHandler} 也有兜底 try/catch）。
     */
    public static void onTick(ServerPlayer player, PlayerStats stats) {
        if (!stats.isToggleActive(STAT_SOUL)) return;
        if (!resolve()) return;
        try {
            fillArcaSouls(player);
            fillTotem(player);
        } catch (Throwable ignored) {
            // 静默跳过这一帧，下次继续
        }
    }

    /** 补 Arca / 灵魂能量池：只在玩家已激活灵魂能量（SEActive）时补，补满才发同步包。 */
    private static void fillArcaSouls(ServerPlayer player) throws Exception {
        if (!(Boolean) getSEActive.invoke(null, player)) return;
        boolean changed = (Boolean) increaseSESouls.invoke(null, player, SAFE_BIG_INCREMENT);
        if (changed) {
            sendSEUpdatePacket.invoke(null, player);
        }
    }

    /** 补灵魂图腾池：把 Curios 里的灵魂图腾填到它自己的上限（上限读不到就跳过，等图腾 tick 补上限）。 */
    private static void fillTotem(ServerPlayer player) throws Exception {
        Object found = findTotem.invoke(null, player);
        if (!(found instanceof ItemStack stack) || stack.isEmpty()) return;
        int max = (Integer) totemMaximumSouls.invoke(null, stack);
        if (max <= 0) return;
        int cur = (Integer) totemCurrentSouls.invoke(null, stack);
        if (cur < max) {
            totemSetSoulsAmount.invoke(null, stack, max);
        }
    }

    /** 懒解析反射句柄；失败一次即永久短路（以后不再重试）。 */
    private static boolean resolve() {
        if (resolved || unavailable) return resolved;
        synchronized (GoetyCompat.class) {
            if (resolved || unavailable) return resolved;
            try {
                Class<?> seHelper = Class.forName(SE_HELPER);
                Class<?> totemFinder = Class.forName(TOTEM_FINDER);
                Class<?> iTotem = Class.forName(ITOTEM);

                getSEActive = seHelper.getMethod("getSEActive", Player.class);
                increaseSESouls = seHelper.getMethod("increaseSESouls", Player.class, int.class);
                sendSEUpdatePacket = seHelper.getMethod("sendSEUpdatePacket", Player.class);
                findTotem = totemFinder.getMethod("FindTotem", Player.class);
                totemCurrentSouls = iTotem.getMethod("currentSouls", ItemStack.class);
                totemMaximumSouls = iTotem.getMethod("maximumSouls", ItemStack.class);
                totemSetSoulsAmount = iTotem.getMethod("setSoulsAmount", ItemStack.class, int.class);

                resolved = true;
            } catch (Throwable t) {
                unavailable = true;
            }
            return resolved;
        }
    }
}
