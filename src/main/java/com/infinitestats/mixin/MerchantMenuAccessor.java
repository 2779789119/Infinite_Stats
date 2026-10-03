package com.infinitestats.mixin;

import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.Merchant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 {@code MerchantMenu#trader}（private final）。
 * <p>
 * 服务端需要拿到商人实体本身，才能取到**真实**的职业等级 / 经验 / 进度条开关 ——
 * 菜单上的 {@code getTraderLevel()} / {@code showProgressBar()} / {@code canRestock()}
 * 都是客户端收包才写入的字段，服务端恒为 0 / false。
 */
@Mixin(MerchantMenu.class)
public interface MerchantMenuAccessor {

    @Accessor("trader")
    Merchant infinitestats$getTrader();
}
