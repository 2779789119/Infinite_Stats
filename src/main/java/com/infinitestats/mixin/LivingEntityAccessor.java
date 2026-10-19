package com.infinitestats.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 暴露 {@link LivingEntity} 的 {@code dropAllDeathLoot}。
 * <p>
 * 用途：真伤击杀时如果 {@code LivingDeathEvent} 被第三方取消（复活词条 / 死亡拦截），
 * 原版 {@code die()} 会在事件之后立刻 {@code return}，战利品表、装备掉落、经验、
 * {@code LivingDropsEvent} 全部跳过；随后 {@code AttackHandler.forceKill} 会移除实体，
 * 结果就是「无掉落、无经验」。所以需要在移除前手动补一次掉落结算。
 * <p>
 * 这个方法在 1.20.1 里是 {@code protected}，没有公开入口，只能开访问器。
 * 它内部已经包含 {@code dropExperience()} 与 {@code ForgeHooks.onLivingDrops}，
 * 因此只需要调用这一个方法。
 */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {

    /** 等价于原版 {@code die()} 里的 {@code this.dropAllDeathLoot(source)}。 */
    @Invoker("dropAllDeathLoot")
    void infinitestats$dropAllDeathLoot(DamageSource source);
}
