package com.infinitestats.mixin;

import com.infinitestats.compat.CompatToggles;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TACZ 联动「无限子弹」—— 堵住「卸弹换成一堆真子弹」的口子。
 * <p>
 * {@code AbstractGunItem#dropAllAmmo} 会在**更换弹匣类配件**时被调用：把弹匣里的子弹
 * 变成真的弹药物品塞进背包，再把弹匣清零。装了「无限子弹」之后弹匣会被本模组补满，
 * 于是「换一次配件 → 拿一堆弹药 → 弹匣又被补满」就成了刷弹药的漏洞。
 * 这里在无限子弹生效时直接取消这次卸弹 —— 弹匣保持原样，也不产出任何弹药物品。
 * <p>
 * 这里 import 的 {@code AbstractGunItem} 来自本仓库的编译期存根（{@code src/taczApiStub/java}，
 * 运行时永远不存在）：注解处理器要求目标类在编译期可见，而 TACZ 只是可选联动。
 * mixin JSON 里配了 {@code InfiniteStatsMixinPlugin}，没装 TACZ 时这个 Mixin 不会被应用。
 * 注入点本身可选（{@code require = 0}），用的是 TACZ 自己的成员名，因此 {@code remap = false}。
 */
@Mixin(value = AbstractGunItem.class, remap = false)
public abstract class TaczGunItemMixin {

    /** 卸弹（弹匣 → 背包）；无限子弹时不让它执行。 */
    @Inject(method = "dropAllAmmo", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void infinitestats$blockUnloadWithInfiniteAmmo(Player player, ItemStack gunItem, CallbackInfo ci) {
        if (CompatToggles.infiniteAmmo(player)) {
            ci.cancel();
        }
    }
}
