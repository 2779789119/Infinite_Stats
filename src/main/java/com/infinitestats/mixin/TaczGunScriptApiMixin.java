package com.infinitestats.mixin;

import com.infinitestats.compat.CompatToggles;
import com.infinitestats.compat.TaczCompat;
import com.tacz.guns.item.ModernKineticGunScriptAPI;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * TACZ 联动 —— 两个属性都注入在 TACZ 的枪械脚本 API 上：
 * <ul>
 *   <li><b>无限子弹</b>（{@code infinite_ammo}）：让弹匣里的子弹永远是满的；</li>
 *   <li><b>零热量</b>（{@code no_heat}）：让热量永远是 0，不会过热锁死。</li>
 * </ul>
 * （第三条 TACZ 属性「无后坐力」在客户端类上，见 {@code TaczCameraRecoilMixin}。）
 * 为什么必须走 Mixin：这两件事的「变化点」都在 TACZ 自己的方法里，而且都发生在
 * 「射击 → 生成子弹」的同一次调用内 —— 事件层面拦不到（{@code GunFireEvent} /
 * {@code GunShootEvent} 只能取消整发，取消就变成打不出子弹），外面每 tick 补一次也赶不上
 * 同 tick 内的读数（HUD 会闪现「满匣 -1」或「热量涨了一下」）。这里直接改这四个方法的返回值：
 * <pre>
 * reduceAmmoOnce()            每次击发都走它。HEAD 处直接返回「消耗成功」，
 *                             于是子弹照常生成、弹匣一发不少，枪膛标记也不会被清掉。
 * removeAmmoFromMagazine(n)   拉栓供弹（defaultTickBolt）与换弹收尾「推一发进枪膛」都走它，
 *                             这里变成空操作，弹匣不会被悄悄扣走。
 * handleShootHeat()           默认的击发加热（满值还会打开「过热锁」），整段跳过。
 * setHeatAmount(float)        枪械 Lua 脚本写热量的包装方法，整段跳过。
 *                             只拦这一个：TACZ 自己的散热走枪械物品上的同名方法，不受影响。
 * </pre>
 * 弹匣「本来就半匣」、枪「本来就热了 / 已被锁」这两种初始状态由 {@code compat/TaczCompat}
 * 每 5 tick 兜底（补满 / 清零），见那里的说明。
 * <p>
 * <b>刻意不使用 {@code @Shadow}</b>：影子成员解析失败属于 Mixin 应用期硬错误（{@code require = 0}
 * 也兜不住），TACZ 一旦改名就会变成启动崩溃。这里取射手改成运行时反射
 * （{@link TaczCompat#shooterOf(Object)}，解析不到就当作「属性没解锁」），
 * 于是 TACZ 换版本最多让这两条联动失效，绝不会把游戏带崩。
 * <p>
 * 注入点也都可选（{@code require = 0}）；并且本 Mixin 只在装了 TACZ 时才会被应用
 * （见 {@code InfiniteStatsMixinPlugin#shouldApplyMixin}）—— 没装 TACZ 时目标类不存在，
 * Mixin 会抛 {@code ClassMetadataNotFoundException}，在 required 配置下那是启动崩溃。
 * <p>
 * 这里 import 的 {@code ModernKineticGunScriptAPI} 来自本仓库的<b>编译期存根</b>
 * （{@code src/taczApiStub/java}，运行时永远不存在）：注解处理器要求目标类在编译期可见，
 * 而 TACZ 只是可选联动。mixin JSON 里配了 {@code InfiniteStatsMixinPlugin}，
 * 没装 TACZ 时这个 Mixin 不会被应用（因此那个类字面量也不会被解析）。
 * 用到的都是 TACZ 自己的成员名，所以整类 {@code remap = false}（不参与 SRG 重映射）。
 */
@Mixin(value = ModernKineticGunScriptAPI.class, remap = false)
public abstract class TaczGunScriptApiMixin {

    /**
     * 击发时的扣弹：无限子弹时直接当作「扣弹成功」，一枚都不扣。
     * <p>
     * 放在击发路径内部（而不是每 tick 事后补）是为了让读数**在同一 tick 内**始终是满的 ——
     * 否则 HUD 会以射速的频率在满匣与满匣 -1 之间闪。
     */
    @Inject(method = "reduceAmmoOnce", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void infinitestats$keepMagazineFull(CallbackInfoReturnable<Boolean> cir) {
        if (!CompatToggles.infiniteAmmo(TaczCompat.shooterOf(this))) return;

        cir.setReturnValue(true);
    }

    /**
     * 从弹匣里取弹：无限子弹时原样「取走」但一枚也不扣。
     * <p>
     * 返回值语义必须与 TACZ 原实现一致（成功取走 {@code amount} 枚）——
     * 调用方（拉栓供弹 / 换弹收尾）据此决定要不要把枪膛标记设为「已上膛」。
     */
    @Inject(method = "removeAmmoFromMagazine", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void infinitestats$keepMagazineFullOnRemove(int amount, CallbackInfoReturnable<Integer> cir) {
        if (!CompatToggles.infiniteAmmo(TaczCompat.shooterOf(this))) return;

        // 负数入参在 TACZ 原实现里本来也返回 0
        cir.setReturnValue(Math.max(amount, 0));
    }

    /**
     * 「零热量」—— 击发时的热量累积：整段跳过。
     * <p>
     * 带热量数据的枪（机枪那类）每次击发都会在 {@code handleShootHeat} 里加热，
     * 满值还会顺手把「过热锁」打开（锁上就打不出子弹）。这里在零热量属性生效时直接取消，
     * 热量既不会涨、也不会锁。
     * <p>
     * 注：枪械自带的 Lua 脚本若定义了 {@code handle_shoot_heat}，走的是脚本那条分支、
     * 不经过这里 —— 但脚本加热只能通过下面的 {@link #infinitestats$ignoreHeatWrite} 或
     * {@code TaczCompat} 每 tick 的兜底清零，两边合起来同样落不到 HUD 上。
     */
    @Inject(method = "handleShootHeat", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void infinitestats$skipShootHeat(CallbackInfo ci) {
        if (CompatToggles.noHeat(TaczCompat.shooterOf(this))) {
            ci.cancel();
        }
    }

    /**
     * 「零热量」—— 任何往枪上写热量的调用：整段跳过。
     * <p>
     * 覆盖默认实现之外的加热路径（主要是枪械脚本直接调用 {@code api:setHeatAmount(...)}）。
     * 只拦 {@code ModernKineticGunScriptAPI} 上的这个包装方法；TACZ 自己的<b>散热</b>
     * （{@code ModernKineticGunItem#tickHeat}）走的是枪械物品上的同名方法，不受影响。
     */
    @Inject(method = "setHeatAmount", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void infinitestats$ignoreHeatWrite(float amount, CallbackInfo ci) {
        if (CompatToggles.noHeat(TaczCompat.shooterOf(this))) {
            ci.cancel();
        }
    }
}
