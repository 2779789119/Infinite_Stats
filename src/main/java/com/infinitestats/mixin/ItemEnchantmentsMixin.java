package com.infinitestats.mixin;

import com.mojang.serialization.Codec;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 附魔突破：<b>把物品附魔等级的存档上限从 255 放开</b>。
 * <p>
 * 这是「附魔突破」（{@code enchant_limit}）在 1.21.1 上的必要一环。原版的等级钳制散在好几处，
 * 本模组已经处理掉的两处：<b>上限判定</b>（{@code util/EnchantLimits}，铁砧 / 附魔台看它能加多高）
 * 与<b>等级名显示</b>（{@code EnchantmentMixin#getFullname}，11 级以上不再显示成同一串罗马数字）。
 * 但还漏了一处 —— 也是 1.20.1 主线由 {@code EnchantmentHelperMixin} 解决的那处：<b>存读</b>。
 * <p>
 * 1.21.1 的物品附魔存在数据组件 {@code DataComponents.ENCHANTMENTS}（类型
 * {@link ItemEnchantments}）里，各条路径的钳制情况并不一样（已用 {@code javap} 对
 * {@code neoforge-21.1.234} 核对）：
 * <ul>
 *   <li><b>内存</b>：{@code Object2IntOpenHashMap}，值是 {@code int} —— 不截断；</li>
 *   <li><b>网络同步</b>：{@code STREAM_CODEC} 用 {@code ByteBufCodecs.VAR_INT} —— 不截断。
 *       所以等级超过 255 时<b>当场一切正常</b>：面板能显示、铁砧能加、效果按等级生效；</li>
 *   <li><b>存档 / 复制物品</b>：{@code LEVEL_CODEC = Codec.intRange(0, 255)} —— <b>卡在 255</b>。
 *       超过 255 的物品在序列化时会被这个 codec 拒绝，等级数据存不下来
 *       （旧版对照：1.20.1 卡在 {@code putShort} 的 32767 与 {@code getEnchantmentLevel} 的
 *       {@code clamp(0, 255)} 上，同一个病症、不同的瓶子）。</li>
 * </ul>
 * 落点因此选在 {@code ItemEnchantments} 的<b>静态初始化</b>里那次 {@code Codec.intRange(0, 255)}
 * 调用上：把它的返回值换成一段更宽的范围。之所以是这里而不是直接改 {@code LEVEL_CODEC} 字段 ——
 * {@code LEVEL_CODEC}、{@code LEVELS_CODEC}、{@code FULL_CODEC}、{@code CODEC} 全部在同一个
 * {@code <clinit>} 里按顺序构造，后面的都<b>捕获</b>了前面那个实例；只换字段的值，已经构造好的
 * {@code CODEC} 仍然是旧的那条（而且 {@code DataComponents} 在自己初始化时也捕获了
 * {@code ItemEnchantments.CODEC} 的引用，改字段连它都影响不到）。在构造源头换掉，
 * 整条链路才是真的宽了。
 * <p>
 * 上限取 {@value #MAX_ENCHANT_LEVEL} 而不是 {@code Integer.MAX_VALUE}：与 1.20.1 主线
 * （short 的 32767）对齐，远超任何实际玩法需要，又不会给「等级 × 数量」之类的运算留下溢出隐患。
 * 注入点可选（{@code require = 0}）：万一原版改写这段初始化，这条只退化成原版行为，
 * 不影响其它功能。
 */
@Mixin(ItemEnchantments.class)
public abstract class ItemEnchantmentsMixin {

    /** 放宽后的等级上限（与 1.20.1 主线的 short 上限一致）。 */
    private static final int MAX_ENCHANT_LEVEL = 32767;

    /**
     * 把 {@code Codec.intRange(0, 255)} 换成 {@code Codec.intRange(0, 32767)}。
     * <p>
     * 保留 {@code min} 参数（原本就是 0，非负约束照旧），只改上界 ——
     * 这样即使原版把下界改成别的值，行为也不会跑偏。
     */
    @Redirect(
            method = "<clinit>",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/serialization/Codec;intRange(II)Lcom/mojang/serialization/Codec;"),
            require = 0)
    private static Codec<Integer> infinitestats$widenEnchantLevelRange(int min, int max) {
        return Codec.intRange(min, MAX_ENCHANT_LEVEL);
    }
}
