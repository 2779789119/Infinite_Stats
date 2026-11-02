package com.infinitestats.mixin;

import com.infinitestats.util.EnchantLevelStore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 附魔等级的读写接管 —— 「附魔上限突破」真正的地基。
 * <p>
 * 只放开上限判断是不够的：原版读写本身就是两道硬瓶颈（对着 1.20.1 的源码核过）——
 * <ul>
 *   <li>{@code storeEnchantment} / {@code setEnchantmentLevel}：{@code putShort("lvl", (short) level)}，
 *       超过 32767 截断；</li>
 *   <li>{@code getEnchantmentLevel(CompoundTag)}：{@code Mth.clamp(tag.getInt("lvl"), 0, 255)}，
 *       超过 255 一律读成 255 —— 也就是说，就算物品上真写了等级 1000，读回来还是 255。</li>
 * </ul>
 * 于是这里把三个方法全部改成走 {@link EnchantLevelStore}：<b>能塞进 short 的照旧写 short</b>
 * （不突破的存档 NBT 与其它模组看到的完全一样），超出才写 int；读取则不再夹到 255。
 * <p>
 * 这三处是静态方法、拿不到玩家上下文，所以是<b>无条件</b>生效的（等级本来就存在的值不会被改写，
 * 只是不再被截断）——与参考实现（EnchantmentLevelBreak 的 Helper）同一思路。
 * <p>
 * 注入点都在 HEAD：原逻辑只有那两行读写，没有别的副作用，直接整体接管比改参数更干净。
 */
@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperMixin {

    /** 读等级：不再夹到 255。 */
    @Inject(
            method = "getEnchantmentLevel(Lnet/minecraft/nbt/CompoundTag;)I",
            at = @At("HEAD"),
            cancellable = true)
    private static void infinitestats$readLevel(CompoundTag tag, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(EnchantLevelStore.read(tag));
    }

    /** 写等级：不再截断到 short。 */
    @Inject(method = "setEnchantmentLevel", at = @At("HEAD"), cancellable = true)
    private static void infinitestats$setLevel(CompoundTag tag, int level, CallbackInfo ci) {
        EnchantLevelStore.write(tag, level);
        ci.cancel();
    }

    /** 生成附魔条目：与 {@code setEnchantmentLevel} 同样的写法（原版这里也写 short）。 */
    @Inject(method = "storeEnchantment", at = @At("HEAD"), cancellable = true)
    private static void infinitestats$storeEnchantment(ResourceLocation id, int level,
                                                       CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag tag = new CompoundTag();
        // 与原版一致：id 为空时写成字符串 "null"
        tag.putString("id", String.valueOf(id));
        EnchantLevelStore.write(tag, level);
        cir.setReturnValue(tag);
    }
}
