package com.infinitestats.mixin;

import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 {@code FishingHook} 的三个私有计时字段。
 * <p>
 * 原版咬钩流程（{@code FishingHook#catchingFish}）：
 * {@code timeUntilLured}（100~600 tick，鱼游过来）→ 递减到 0 后赋 {@code timeUntilHooked}
 * （20~80 tick，鱼准备咬）→ 递减到 0 时咬钩，把 {@code nibble} 设为 20~40
 * （同时同步 {@code DATA_BITING} 让浮标下沉、放咬钩音效），之后就等玩家右键收杆，
 * 窗口内没收到就归零、鱼跑掉。
 * <p>
 * 三个字段都是私有的且没有 getter，所以开一个访问器：
 * 「自动钓鱼」用 {@code nibble} 抓收杆窗口，「立即咬钩」压前两段等待。
 */
@Mixin(FishingHook.class)
public interface FishingHookAccessor {

    /** 咬钩窗口剩余 tick，> 0 表示鱼正咬钩、可以收杆。 */
    @Accessor("nibble")
    int infinitestats$getNibble();

    /** 鱼游过来的剩余 tick（原版 100~600，受诱饵附魔缩短）。 */
    @Accessor("timeUntilLured")
    int infinitestats$getTimeUntilLured();

    @Accessor("timeUntilLured")
    void infinitestats$setTimeUntilLured(int value);

    /** 鱼准备咬钩的剩余 tick（原版 20~80）。 */
    @Accessor("timeUntilHooked")
    int infinitestats$getTimeUntilHooked();

    @Accessor("timeUntilHooked")
    void infinitestats$setTimeUntilHooked(int value);
}
