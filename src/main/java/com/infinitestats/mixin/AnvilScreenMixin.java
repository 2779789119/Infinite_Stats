package com.infinitestats.mixin;

import com.infinitestats.crafting.PortableAnvilMenu;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * 随身铁砧界面：不再显示「过于昂贵」。
 * <p>
 * 原版 {@code AnvilScreen#renderLabels} 只看 {@code menu.getCost() >= 40} 就显示
 * {@code container.repair.expensive}。随身铁砧既然取消了门槛，界面也必须同步放开，
 * 否则会出现「界面写着过于昂贵、其实能点」的矛盾（消耗数字本身由服务端同步，已经是封顶后的值）。
 */
@Mixin(AnvilScreen.class)
public abstract class AnvilScreenMixin {

    /**
     * {@code require = 0}：这里改的常量同样可能被别的模组抢先
     * （Apotheosis 就在 {@code AnvilMenu} 上做过同一类 {@code @ModifyConstant}）。
     * 界面文案属于锦上添花，撞车时宁可按 WARN 跳过、由原版显示「过于昂贵」，也不要直接崩在启动阶段。
     */
    @ModifyConstant(
            method = "renderLabels",
            constant = @Constant(intValue = 40, ordinal = 0),
            require = 0)
    private int infinitestats$portableCostLabel(int threshold) {
        // 菜单自带的公开访问器（MenuAccess#getMenu），无需 @Shadow
        AnvilScreen self = (AnvilScreen) (Object) this;
        return self.getMenu() instanceof PortableAnvilMenu ? Integer.MAX_VALUE : threshold;
    }
}
