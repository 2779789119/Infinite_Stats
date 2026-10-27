package com.infinitestats.compat;

import com.infinitestats.stats.PlayerStats;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.Arrays;

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
 * 运行时联动，本模组不声明对它的编译期依赖。没装时 {@code Class.forName} 直接失败并永久短路、零开销。
 * <p>
 * 两套池子<b>分别解析、互不牵连</b>：Goety 不同版本的方法名大小写不一致（例如灵魂图腾的 setter
 * 在 {@code 2.5.36.1} 叫 {@code setSoulsamount}、在 {@code 2.5.58.4} 叫 {@code setSoulsAmount}），
 * 早期版本这里是「一个方法取不到就整条联动短路」，结果图腾 setter 的名字一变，<b>Arca 池一起失效</b>。
 * 现在按「精确名 → 忽略大小写扫同名同参」逐方法查找，取不到的成员留 {@code null}、只在调用处跳过，
 * 另一套池子照常工作；两套都取不到才彻底退化回原版行为（反射失败静默跳过）。
 * <p>
 * 反射到的成员（{@code 2.5.36.1} 与 {@code 2.5.58.4} 均已核对）：
 * {@code SEHelper#getSEActive(Player)} / {@code #increaseSESouls(Player, int)} /
 * {@code #sendSEUpdatePacket(Player)}，{@code TotemFinder#FindTotem(Player)}，
 * {@code ITotem#currentSouls(ItemStack)} / {@code #maximumSouls(ItemStack)} /
 * {@code #setSoulsAmount(ItemStack, int)}（旧版为 {@code setSoulsamount}）。
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

    /** 已尝试过解析（成功与否都置位，避免每 tick 重试反射）。 */
    private static boolean resolved;
    /** 两套池子各自是否可用；某个方法取不到只影响自己这一套。 */
    private static boolean arcaReady;
    private static boolean totemReady;

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
        if (!arcaReady) return;
        if (!(Boolean) getSEActive.invoke(null, player)) return;
        boolean changed = (Boolean) increaseSESouls.invoke(null, player, SAFE_BIG_INCREMENT);
        if (changed && sendSEUpdatePacket != null) {
            sendSEUpdatePacket.invoke(null, player);
        }
    }

    /** 补灵魂图腾池：把 Curios 里的灵魂图腾填到它自己的上限（上限读不到就跳过，等图腾 tick 补上限）。 */
    private static void fillTotem(ServerPlayer player) throws Exception {
        if (!totemReady) return;
        Object found = findTotem.invoke(null, player);
        if (!(found instanceof ItemStack stack) || stack.isEmpty()) return;
        int max = (Integer) totemMaximumSouls.invoke(null, stack);
        if (max <= 0) return;
        int cur = (Integer) totemCurrentSouls.invoke(null, stack);
        if (cur < max) {
            totemSetSoulsAmount.invoke(null, stack, max);
        }
    }

    /** 懒解析反射句柄；只解析一次（成功与否都置位，以后不再重试反射）。 */
    private static boolean resolve() {
        if (resolved) return arcaReady || totemReady;
        synchronized (GoetyCompat.class) {
            if (resolved) return arcaReady || totemReady;

            // Arca 池：核心是「读激活状态 + 增加灵魂」，同步包缺失只影响 HUD 刷新，不算失败。
            try {
                Class<?> seHelper = Class.forName(SE_HELPER);
                getSEActive = findMethod(seHelper, "getSEActive", Player.class);
                increaseSESouls = findMethod(seHelper, "increaseSESouls", Player.class, int.class);
                sendSEUpdatePacket = findMethod(seHelper, "sendSEUpdatePacket", Player.class);
                arcaReady = getSEActive != null && increaseSESouls != null;
            } catch (Throwable ignored) {
                arcaReady = false;
            }

            // 灵魂图腾池：与 Arca 池解耦 —— 这一套解析失败不能把上面那套一起拖死。
            try {
                Class<?> totemFinder = Class.forName(TOTEM_FINDER);
                Class<?> iTotem = Class.forName(ITOTEM);
                findTotem = findMethod(totemFinder, "FindTotem", Player.class);
                totemCurrentSouls = findMethod(iTotem, "currentSouls", ItemStack.class);
                totemMaximumSouls = findMethod(iTotem, "maximumSouls", ItemStack.class);
                // 2.5.58.4 是 setSoulsAmount，2.5.36.1 是 setSoulsamount —— 见 findMethod 的大小写兜底。
                totemSetSoulsAmount = findMethod(iTotem, "setSoulsAmount", ItemStack.class, int.class);
                totemReady = findTotem != null && totemCurrentSouls != null
                        && totemMaximumSouls != null && totemSetSoulsAmount != null;
            } catch (Throwable ignored) {
                totemReady = false;
            }

            resolved = true;
            return arcaReady || totemReady;
        }
    }

    /**
     * 按名字找方法：先精确匹配，再忽略大小写扫一遍同名同参的方法。
     * <p>
     * 后半段是为跨版本兼容 —— Goety 的方法名大小写在不同版本间变过
     * （{@code setSoulsamount} → {@code setSoulsAmount}），精确匹配会直接 {@code NoSuchMethodException}。
     */
    private static Method findMethod(Class<?> owner, String name, Class<?>... params) {
        try {
            return owner.getMethod(name, params);
        } catch (NoSuchMethodException ignored) {
            // 落到忽略大小写的兜底
        }
        for (Method candidate : owner.getMethods()) {
            if (candidate.getName().equalsIgnoreCase(name)
                    && Arrays.equals(candidate.getParameterTypes(), params)) {
                return candidate;
            }
        }
        return null;
    }
}
