package com.infinitestats.mixin;

import com.infinitestats.util.EnchantText;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 附魔名显示：让超过 10 级的等级也显示成人话。
 * <p>
 * 原版 {@code Enchantment#getFullname} 直接拼 {@code Component.translatable("enchantment.level." + level)}，
 * 而原版语言文件只提供 <b>1~10</b> 的译文：11 级以上会把键名原样显示成 {@code enchantment.level.11}。
 * 原版有 {@code getMaxLevel()} 挡着所以平时见不到，突破上限后（物品提示、附魔界面、铁砧结果）
 * 到处都会出现，所以由 {@link EnchantText} 接管。
 * <p>
 * 1~10 级刻意**仍然走原版**（跟随客户端语言），只在 11 级以上自己拼。
 * <p>
 * <b>1.21.1 适配</b>：1.20.1 里 {@code getFullname(int)} 是实例方法；1.21 改成了
 * {@code static Component getFullname(Holder<Enchantment>, int)}，所以这里注入静态方法、
 * 并带上完整描述符以区分同名重载。
 */
@Mixin(Enchantment.class)
public abstract class EnchantmentMixin {

    @Inject(
            method = "getFullname(Lnet/minecraft/core/Holder;I)Lnet/minecraft/network/chat/Component;",
            at = @At("HEAD"),
            cancellable = true)
    private static void infinitestats$fullname(Holder<Enchantment> enchantment, int level,
                                              CallbackInfoReturnable<Component> cir) {
        if (level >= 1 && level <= 10) return;
        cir.setReturnValue(EnchantText.name(enchantment, level));
    }
}
