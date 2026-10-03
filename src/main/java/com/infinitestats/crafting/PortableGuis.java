package com.infinitestats.crafting;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraftforge.network.NetworkHooks;

/**
 * 随身功能台：末影箱 / 锻造台。
 * <p>
 * 与 {@link PortableAnvil} 同一套思路 —— 直接复用原版菜单类型，客户端会自动套用原版界面，
 * 不需要自建 MenuType / Screen：
 * <ul>
 *   <li><b>末影箱</b>：原版 9×3 箱子菜单（{@code MenuType.GENERIC_9x3}），容器直接接
 *       {@code player.getEnderChestInventory()}，因此与原版末影箱共用同一份库存；</li>
 *   <li><b>锻造台</b>：原版 {@code SmithingMenu} + {@link ContainerLevelAccess#NULL}，
 *       方块校验（{@code isValidBlock}）写在 access 回调里，NULL 实现是空操作，
 *       于是不依赖世界里的锻造台方块。</li>
 * </ul>
 */
public final class PortableGuis {

    private PortableGuis() {}

    /** 为玩家打开随身末影箱（调用方需先校验属性开关）。 */
    public static void openEnderChest(ServerPlayer player) {
        NetworkHooks.openScreen(player, new SimpleMenuProvider(
                (windowId, inv, p) -> ChestMenu.threeRows(windowId, inv, p.getEnderChestInventory()),
                Component.translatable("container.enderchest")));
    }

    /** 为玩家打开随身锻造台（调用方需先校验属性开关）。 */
    public static void openSmithing(ServerPlayer player) {
        NetworkHooks.openScreen(player, new SimpleMenuProvider(
                (windowId, inv, p) -> new SmithingMenu(windowId, inv, ContainerLevelAccess.NULL),
                Component.translatable("container.upgrade")));
    }
}
