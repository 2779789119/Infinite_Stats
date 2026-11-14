package com.infinitestats.mixin;

import com.infinitestats.compat.AutoDeposit;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 给「自动入库」提供<b>背包变更</b>信号。
 * <p>
 * 原先是每 tick 取模固定间隔无条件扫一遍背包，背包里什么都没发生也照样每秒问一次存储网络
 * （RS / AE2 / 汤姆存储都要反射进去，是这块最贵的部分）。Forge 没有「玩家背包内容变了」这种通用事件
 * （{@code ItemPickupEvent} / {@code ItemCraftedEvent} 之类只覆盖原版的少数入口，模组往背包里塞东西
 * 一条都不走），所以这里挂在原版 {@link Inventory} 的三个写入口上：
 * <ul>
 *   <li>{@code setChanged()} —— 绝大多数「背包被动过」的收尾都会调它（拾取、合成产出、指令等）；</li>
 *   <li>{@code setItem(int, ItemStack)} —— 容器界面点格子、{@code Slot#set} 走的正是这里，
 *       而它**不会**调 {@code setChanged()}，不补这条会漏掉整理背包 / 从箱子里取物；</li>
 *   <li>{@code add(int, ItemStack)} —— 拾取与各类「塞进背包」的入口，它直接写 {@code items} 列表。</li>
 * </ul>
 * 注入只做一件事：给玩家记一个标记（{@link AutoDeposit#markInventoryChanged}），不做任何查询；
 * 真正的入库由 {@link AutoDeposit#tick} 在下一个 tick 合并执行，所以一次批量拾取只会查一次存储网络。
 * <p>
 * 三个注入点全部 {@code require = 0}（配置层 {@code injectors.defaultRequire = 0}）：签名变了只让这条信号失效，
 * 不能把启动拖崩。
 */
@Mixin(Inventory.class)
public abstract class InventoryChangeMixin {

    @Inject(method = "setChanged()V", at = @At("RETURN"))
    private void infinitestats$markChanged(CallbackInfo ci) {
        AutoDeposit.markInventoryChanged(((Inventory) (Object) this).player);
    }

    @Inject(method = "setItem(ILnet/minecraft/world/item/ItemStack;)V", at = @At("RETURN"))
    private void infinitestats$markSetItem(int slot, ItemStack stack, CallbackInfo ci) {
        AutoDeposit.markInventoryChanged(((Inventory) (Object) this).player);
    }

    @Inject(method = "add(ILnet/minecraft/world/item/ItemStack;)Z", at = @At("RETURN"))
    private void infinitestats$markAdd(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        AutoDeposit.markInventoryChanged(((Inventory) (Object) this).player);
    }
}
