package com.tacz.guns.api.item.gun;

/**
 * <b>TACZ 的编译期存根（stub）—— 运行时永远不会用到这个类。</b>
 * <p>
 * 与 {@code com.tacz.guns.item.ModernKineticGunScriptAPI} 那份存根同一用途（见那里的长篇说明）：
 * 只为让 Mixin 注解处理器在<b>编译期</b>能找到目标类。这里只需要列出被注入的方法。
 * <p>
 * 维护约定：签名必须与 TACZ 1.1.x 实际实现一致（已对着 {@code tacz-1.20.1-1.1.8-hotfix2.jar} 核对）。
 */
public abstract class AbstractGunItem {

    /**
     * 把弹匣里的子弹卸成真的弹药物品塞进玩家背包（更换弹匣类配件时被调用）。
     * <p>
     * 无限子弹生效时本模组会取消它 —— 否则「换配件卸一匣 → 弹匣又被补满」就是刷弹药。
     */
    public void dropAllAmmo(net.minecraft.world.entity.player.Player player, net.minecraft.world.item.ItemStack gunItem) {
        throw new UnsupportedOperationException("compile-time stub");
    }
}
