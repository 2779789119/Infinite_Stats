package com.infinitestats.mixin;

import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * 「无限耐久」开关开启时，玩家的物品不再掉耐久。
 * <p>
 * 原版所有耐久损耗（挖掘 / 攻击 / 射箭 / 耕地 / 剪羊毛 / 钓鱼 / 盔甲受击……）最终都走
 * {@code ItemStack#hurtAndBreak(int, LivingEntity, Consumer)}，在 HEAD 取消即可从源头掐断：
 * 既不扣 damage，也不会触发 {@code onBroken}（工具不会真正损坏）。
 * <p>
 * 只处理服务端玩家：耐久是服务端权威数据，客户端本地不参与结算。
 * 绕过该方法直接写 damage 的第三方模组由 {@code UtilityHandler#applyAutoRepair} 的定时补满兜底。
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    @Inject(method = "hurtAndBreak", at = @At("HEAD"), cancellable = true, require = 0)
    private void infinitestats$skipDurabilityLoss(int amount, LivingEntity entity,
                                                  Consumer<LivingEntity> onBroken, CallbackInfo ci) {
        if (!(entity instanceof ServerPlayer player)) return;

        PlayerStats stats = player.getCapability(PlayerStatsProvider.PLAYER_STATS).resolve().orElse(null);
        if (stats == null || !stats.isToggleActive("auto_repair")) return;

        ItemStack self = (ItemStack) (Object) this;
        if (self.getDamageValue() > 0) self.setDamageValue(0);
        ci.cancel();
    }
}
