package com.infinitestats.crafting;

import com.infinitestats.emc.ModMenuTypes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;

/**
 * 随身铁砧菜单。
 * <p>
 * 行为与 {@link PortableAnvil} 完全一致（原版铁砧算法 + {@link ContainerLevelAccess#NULL}，
 * 因此不会损耗方块、不依赖世界里的铁砧），唯一区别是**拥有自己的 {@link MenuType}**。
 * <p>
 * 为什么需要它：客户端创建菜单时用的是菜单类型工厂，若沿用原版 {@code MenuType.ANVIL}，
 * 客户端拿到的就是一个普通 {@code AnvilMenu}，无从判断「这是随身铁砧还是真铁砧」。
 * 有了独立类型后，Mixin 在两端都能用 {@code instanceof PortableAnvilMenu} 精确识别，
 * 从而只对随身铁砧放宽「过于昂贵」门槛并封顶消耗。
 * <p>
 * {@link #getType()} 必须覆写：{@code NetworkHooks.openScreen} 是拿菜单实例的
 * {@code getType()} 下发到客户端的，而父类构造器里写死的是原版铁砧类型。
 */
public class PortableAnvilMenu extends AnvilMenu {

    public PortableAnvilMenu(int windowId, Inventory inventory) {
        super(windowId, inventory, ContainerLevelAccess.NULL);
    }

    @Override
    public MenuType<?> getType() {
        return ModMenuTypes.PORTABLE_ANVIL_MENU.get();
    }
}
