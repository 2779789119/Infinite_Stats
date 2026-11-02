package com.infinitestats.mixin;

import com.infinitestats.crafting.PortableAnvilMenu;
import com.infinitestats.stats.PlayerStatsProvider;
import com.infinitestats.util.EnchantLimits;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 铁砧消耗接管。
 * <p>
 * 为什么必须走 Mixin：Forge 的 {@code AnvilUpdateEvent#setCost} 只在**同时设置了 output** 时
 * 才被 {@code ForgeHooks.onAnvilChange} 采用（内部调 {@code setMaximumCost}），否则原版会继续用
 * 自己算出来的 {@code this.cost}；而且该事件在附魔消耗计算**之前**触发，参数只是基础修理费，
 * 连最终值都看不到。原版把「过于昂贵」门槛硬编码为 40，也没有任何事件可以拦截，
 * 所以只能直接改字节码。
 * <p>
 * 三处注入的锚点（均已用 {@code javap} 对字节码核对）：
 * <ul>
 *   <li>最终写入消耗：{@code createResult} 内第 6 次 {@code DataSlot.set(I)}（{@code this.cost.set(j + i)}，序号 5）；</li>
 *   <li>「过于昂贵」门槛：{@code createResult} 内第 2 次 {@code DataSlot.get()}（序号 1）；</li>
 *   <li>附魔等级钳制：{@code createResult} 内第 1 次 {@code Enchantment#getMaxLevel()}。</li>
 * </ul>
 * <p>
 * 门槛这里**刻意不用 {@code @ModifyConstant} 改那个常量 40**：Apotheosis 的
 * {@code apotheosis.mixins.json:AnvilMenuMixin#apoth_removeLevelCap} 也在同一常量上做
 * {@code @ModifyConstant}，同优先级下 Mixin 会判为冲突并跳过我们的注入 —— 实测就是这样崩在启动阶段的。
 * 改成 {@code @Redirect} 掉门槛比较里的取数指令后，注入目标与任何 {@code @ModifyConstant} 都不同。
 * <p>
 * <b>全部注入点都是「可选」的（{@code require = 0}）</b>：AnvilMenu 是大包里的热门改写目标
 * （Apotheosis 的 coremod 会把附魔相关调用整段换掉、其它铁砧模组也在同几个位置插桩），
 * 任何一处锚点被别人挪走都不该让整个游戏起不来 —— 注入失败时 Mixin 只会打印一条警告，
 * 对应功能退化为原版行为，其余部分照常工作。配置层同样把 {@code injectors.defaultRequire}
 * 设成了 0，避免「一处对不上、整个模组加载失败」。
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {

    /** 随身铁砧的经验收费封顶 */
    private static final int PORTABLE_MAX_COST = 50;

    /** 只读访问父类 {@code ItemCombinerMenu} 的玩家字段（见 {@link ItemCombinerMenuAccessor}） */
    private Player infinitestats$player() {
        return ((ItemCombinerMenuAccessor) (Object) this).infinitestats$getPlayer();
    }

    /**
     * 改写「最终消耗」写入数据槽的值。
     * <p>
     * 此处仍在「过于昂贵」判定之前，因此 {@code anvil_cost} 的减免能真正把高消耗操作压到门槛以下，
     * 界面显示的数值与实际扣费也必然一致（同一个值）。
     * 只在随身铁砧上额外封顶 —— 原版铁砧保持自由的减免行为。
     */
    @ModifyArg(
            method = "createResult",
            require = 0,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/inventory/DataSlot;set(I)V",
                    ordinal = 5),
            index = 0)
    private int infinitestats$adjustFinalCost(int cost) {
        if (cost <= 0) return cost;

        int adjusted = cost;
        float rate = infinitestats$anvilCostRate();
        if (rate > 0.0F) {
            adjusted = Math.max(1, (int) Math.ceil(cost * (1.0F - rate)));
        }
        if (infinitestats$isPortable()) {
            adjusted = Math.min(adjusted, PORTABLE_MAX_COST);
        }
        return adjusted;
    }

    /**
     * 拔掉「过于昂贵」门槛：原版在 {@code cost >= 40} 且非创造时会清空结果槽，
     * 随身铁砧改为永不触发（收费由上面封顶兜底）。
     * <p>
     * 这里拦的是门槛比较里的取数指令（{@code createResult} 内第 2 次 {@code DataSlot.get()}），
     * 随身铁砧直接返回 {@code Integer.MIN_VALUE} —— 于是无论门槛被原版还是被别的模组
     * （例如 Apotheosis 的 {@code apoth_removeLevelCap}）改成多少，比较恒为 false。
     */
    @Redirect(
            method = "createResult",
            require = 0,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/inventory/DataSlot;get()I",
                    ordinal = 1))
    private int infinitestats$liftPortableGate(DataSlot slot) {
        int cost = slot.get();
        return infinitestats$isPortable() ? Integer.MIN_VALUE : cost;
    }

    /**
     * 附魔上限突破：铁砧里「同等级 +1」后那句 {@code if (j2 > enchantment1.getMaxLevel()) j2 = enchantment1.getMaxLevel();}
     * 是**整段 {@code createResult} 里唯一的钳制点**（全文件 {@code getMaxLevel} 只出现这两次：一次比较、一次赋值）。
     * <p>
     * 这里替换的是<b>比较</b>里的取数（{@code ordinal = 0}）：解锁「附魔上限突破」的玩家返回
     * {@link Integer#MAX_VALUE}，比较恒为 false，等级于是原样保留 —— 两本锋利 V 就能叠出 VI、VII…，
     * 并且紧随其后的消耗计算（{@code i += k3 * j2}）用的是真实等级，越突破越贵；未解锁时原样返回
     * {@code getMaxLevel()}，行为与原版逐字节一致。
     * <p>
     * 只替换比较、不动赋值那一处是刻意的：赋值那条分支在解锁后永远进不去，
     * 少碰一个注入点就少一分与其它铁砧模组抢同一处字节码的风险。
     * <p>
     * 这个注入点在<b>装了 Apotheosis 的包里有 0 个匹配</b>（它的 coremod 已经把这处调用换成了
     * {@link #infinitestats$raiseApotheosisMergeCap} 处理的那个静态钩子），所以标了 {@code require = 0}，
     * 由下面那个兼容注入点接手。
     */
    @Redirect(
            method = "createResult",
            require = 0,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/enchantment/Enchantment;getMaxLevel()I",
                    ordinal = 0))
    private int infinitestats$raiseMergeCap(Enchantment enchantment) {
        return EnchantLimits.maxLevel(infinitestats$player(), enchantment);
    }

    /**
     * 与 Apotheosis 共存的上限突破：它的 coremod（{@code coremods/ench/ench_info_redirector.js}）
     * 会把 AnvilMenu 里那两处 {@code Enchantment#getMaxLevel()} 整段换成静态钩子
     * {@code dev.shadowsoffire.apotheosis.ench.asm.EnchHooks.getMaxLevel(Enchantment)}，
     * 于是上面那个注入点找不到目标。这里对同一位置做等价替换（{@code ordinal = 0} 同样是「比较」那一处）。
     * <p>
     * 上限值统一走 {@link EnchantLimits#maxLevel(Player, Enchantment)}：<b>未解锁时返回的是整合包
     * 实际生效的上限</b>（装了 Apotheosis 就是它配置的等级上限，例如锋利 9 级）而不是原版的
     * {@code getMaxLevel()}（5 级）—— 直接返回原版值等于把人家放宽的上限又收紧了；
     * 解锁后才返回不限。这样进阶附魔台 / 随身铁砧 / 指令三处口径完全一致。
     * <p>
     * 读 Apotheosis 的值走 {@code compat/ApotheosisEnchantCompat} 的<b>反射</b>，
     * 刻意不在字节码里直接引用它的类：没装该模组时这个处理器根本不会被调用（注入点不存在），
     * 但直接引用会让 AnvilMenu 的类定义多出一条指向不存在类的常量池引用，徒增类加载风险。
     * <p>
     * <b>处理器必须是实例方法（不能写 static）</b>：Mixin 校验的是处理器与<b>宿主方法</b>
     * （{@code createResult}，实例方法）的 static 修饰符是否一致，<b>与被重定向的那个调用是不是静态无关</b>
     * —— 被调用方是静态只影响处理器的参数表。写成 {@code static} 会抛
     * {@code InvalidInjectionException: 'static' modifier of handler method does not match target}，
     * 这类错误<b>不受 {@code require = 0} 兜底</b>（require 只管「找不到目标点是否报错」），
     * 会让整个 Mixin 应用失败、游戏启动即崩（1.40.0 就是这么崩的）。
     * 顺带的好处：实例方法里 {@code this} 就是那个 AnvilMenu，玩家上下文直接问 {@code infinitestats$player()} 即可，
     * 不必像之前那样为了「静态处理器拿不到实例」而绕 ThreadLocal + HEAD/RETURN 捕获。
     */
    @Redirect(
            method = "createResult",
            require = 0,
            at = @At(value = "INVOKE",
                    target = "Ldev/shadowsoffire/apotheosis/ench/asm/EnchHooks;getMaxLevel(Lnet/minecraft/world/item/enchantment/Enchantment;)I",
                    ordinal = 0))
    private int infinitestats$raiseApotheosisMergeCap(Enchantment enchantment) {
        return EnchantLimits.maxLevel(infinitestats$player(), enchantment);
    }

    private boolean infinitestats$isPortable() {
        return ((Object) this) instanceof PortableAnvilMenu;
    }

    private float infinitestats$anvilCostRate() {
        Player player = infinitestats$player();
        if (player == null) return 0.0F;
        return player.getCapability(PlayerStatsProvider.PLAYER_STATS)
                .map(stats -> Math.min(0.9F, Math.max(0.0F, stats.getStatValue("anvil_cost"))))
                .orElse(0.0F);
    }
}
