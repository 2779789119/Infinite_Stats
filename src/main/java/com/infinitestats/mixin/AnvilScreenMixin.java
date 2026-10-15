package com.infinitestats.mixin;

import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.world.entity.player.Abilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 随身铁砧界面：不再显示「过于昂贵」。
 * <p>
 * 原版 {@code AnvilScreen#renderLabels} 只看 {@code menu.getCost() >= 40} 就显示
 * {@code container.repair.expensive}。随身铁砧既然取消了门槛，界面也必须同步放开，
 * 否则会出现「界面写着过于昂贵、其实能点」的矛盾（消耗数字本身由服务端同步，已经是封顶后的值）。
 * <p>
 * 注入点选 {@code Abilities#instabuild} 而不是 {@code AnvilMenu#getCost()}：
 * 这条判断是 {@code i >= 40 && !instabuild}，把 {@code instabuild} 读成 {@code true}
 * 只影响「是否显示过于昂贵」这一处，{@code i} 保持真实值 —— 界面上的消耗数字不会被改花。
 * 该方法里 {@code instabuild} 只出现这一次（已对着 1.21.1 的 {@code -sources.jar} 核对）。
 */
@Mixin(AnvilScreen.class)
public abstract class AnvilScreenMixin {

    @Redirect(
            method = "renderLabels",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/world/entity/player/Abilities;instabuild:Z"))
    private boolean infinitestats$hideTooExpensive(Abilities abilities) {
        return true;
    }
}
