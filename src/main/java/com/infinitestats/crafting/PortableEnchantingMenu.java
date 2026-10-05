package com.infinitestats.crafting;

import com.infinitestats.emc.ModMenuTypes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.MenuType;

/**
 * 随身附魔台的**客户端**菜单。
 * <p>
 * 服务端用的是 {@link PortableStationMenus.Enchanting}（真实 {@code ContainerLevelAccess}，
 * 附魔算法才能跑）；客户端这份只负责「让界面挂到一个独立菜单类型上」，因此沿用原版做法
 * —— {@link ContainerLevelAccess#NULL}：附加的 ± 加点逻辑写在界面里，通过数据包回服务端，
 * 客户端本身不需要计算附魔消耗（三档消耗与线索本来就会由服务端的数据槽同步过来）。
 * <p>
 * {@link #getType()} 必须覆写：客户端界面是按「菜单类型的注册名」去
 * {@code MenuScreens} 里查构造函数，用原版 {@code MenuType.ENCHANTMENT} 的话
 * 只会拿到原版附魔界面。
 */
public class PortableEnchantingMenu extends EnchantmentMenu {

    public PortableEnchantingMenu(int windowId, Inventory inventory) {
        super(windowId, inventory, ContainerLevelAccess.NULL);
    }

    @Override
    public MenuType<?> getType() {
        return ModMenuTypes.PORTABLE_ENCHANTING_MENU.get();
    }
}
