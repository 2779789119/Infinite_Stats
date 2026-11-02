package com.infinitestats.mixin;

import com.infinitestats.stats.PlayerStatsProvider;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 动物繁殖无冷却。
 * <p>
 * 原版在 {@code Animal#spawnChildFromBreeding} 里把两只亲本的年龄设成 6000（5 分钟），
 * 而 {@code Animal#mobInteract} 要求年龄为 0 才能喂食进入恋爱状态 —— 这就是「繁殖冷却」。
 * <p>
 * 为什么必须走 Mixin：冷却是在 {@code finalizeSpawnChildFromBreeding} 里写死的，Forge 的
 * {@code BabyEntitySpawnEvent} 在这之前触发（拿到时机也没用，事件返回后原版照样把年龄设成 6000），
 * 事件里也没有任何「之后」的回调。
 * <p>
 * 注入点取 {@code TAIL}（方法最后一个 return），因此只覆盖正常繁殖路径：
 * 若事件被其它模组取消（原版走早退分支），我们不会插手，尊重对方的拦截。
 * <p>
 * 谁是「繁殖者」：靠 {@code Animal#getLoveCause()}（喂食时记录、{@code resetLove()} 不会清除），
 * 于是两只亲本各按**喂它的人**是否解锁来判断，混养时也不会互相蹭效果。
 */
@Mixin(Animal.class)
public abstract class AnimalMixin {

    @Inject(method = "spawnChildFromBreeding", at = @At("TAIL"))
    private void infinitestats$clearBreedCooldown(ServerLevel level, Animal partner, CallbackInfo ci) {
        Animal self = (Animal) (Object) this;
        infinitestats$clearBreedCooldownFor(self);
        infinitestats$clearBreedCooldownFor(partner);
    }

    private static void infinitestats$clearBreedCooldownFor(Animal animal) {
        ServerPlayer cause = animal.getLoveCause();
        if (cause == null) return;
        boolean active = cause.getCapability(PlayerStatsProvider.PLAYER_STATS)
                .map(stats -> stats.isToggleActive("breed_no_cooldown"))
                .orElse(false);
        // 年龄归 0 ＝ 成年且可立刻再次喂食（原版此处为 6000）
        if (active) animal.setAge(0);
    }
}
