package com.infinitestats.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 暴露 {@code LivingEntity#dropAllDeathLoot(ServerLevel, DamageSource)}。
 * <p>
 * 真伤强制收尾时，若 {@code die()} 被第三方复活类模组取消，原版的战利品 / 装备 / 经验结算
 * 一行都不会执行。此时只能手动补一次 —— 而这个方法是 {@code protected}，
 * 1.21.1 还比 1.20.1 多了一个 {@code ServerLevel} 参数，所以通过访问器暴露。
 */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {

    @Invoker("dropAllDeathLoot")
    void infinitestats$dropAllDeathLoot(ServerLevel level, DamageSource source);
}
