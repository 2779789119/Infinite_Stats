package com.infinitestats.mixin;

import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * 自动修理开关的「不消耗耐久」部分。
 * <p>
 * 只拦 {@code ItemStack#hurtAndBreak(int, ServerLevel, LivingEntity, Consumer)} 这一个重载 ——
 * 1.21.1 里另外两个重载（{@code ServerPlayer} 版与 {@code EquipmentSlot} 版）最终都会走到它，
 * 所以一处拦截就能覆盖挖方块、攻击、盔甲挨打、模组直接调用等全部扣耐久路径。
 * <p>
 * 条件：实体是玩家，且该玩家的 {@code auto_repair} 开关处于激活状态。
 * 补满已磨损的耐久由 {@code UtilityHandler#applyAutoRepair} 每秒做一次。
 */
@Mixin(ItemStack.class)
public abstract class AutoRepairDurabilityMixin {

    @Inject(
            method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;"
                    + "Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private void infinitestats$skipDurabilityLoss(int amount, ServerLevel level,
                                                  LivingEntity entity, Consumer<Item> onBroken,
                                                  CallbackInfo ci) {
        if (entity == null) return;
        PlayerStats stats = PlayerStatsProvider.of(entity).orElse(null);
        if (stats != null && stats.isToggleActive("auto_repair")) {
            // 直接放弃这次扣耐久：耐久值不变，也就不会触发「物品损坏」回调
            ci.cancel();
        }
    }
}
