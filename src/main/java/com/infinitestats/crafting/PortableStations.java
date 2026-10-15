package com.infinitestats.crafting;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;

/**
 * 随身功能台：所有「随身站点」的统一打开入口（服务端）。
 * <p>
 * 与 {@link PortableAnvil} 同一套思路 —— 直接复用原版菜单类型，客户端会自动套用原版界面，
 * 不需要自建 MenuType / Screen：
 * <ul>
 *   <li><b>末影箱</b>：原版 9×3 箱子菜单（{@code ChestMenu.threeRows}），容器直接接
 *       {@code player.getEnderChestInventory()}，因此与原版末影箱共用同一份库存；</li>
 *   <li><b>锻造台 / 切石机 / 织布机 / 制图台 / 磨石</b>：原版菜单算法 + 解除方块校验，
 *       见 {@link PortableStationMenus}。</li>
 * </ul>
 * 随身工作台 / 熔炉 / 铁砧各自有自己的菜单与数据，入口在前两者各自的实现里。
 */
public final class PortableStations {

    /** 末影箱（属性 id）。 */
    public static final String ENDER_CHEST = "portable_ender_chest";
    /** 锻造台。 */
    public static final String SMITHING = "portable_smithing";
    /** 切石机。 */
    public static final String STONECUTTER = "portable_stonecutter";
    /** 织布机。 */
    public static final String LOOM = "portable_loom";
    /** 制图台。 */
    public static final String CARTOGRAPHY = "portable_cartography";
    /** 磨石。 */
    public static final String GRINDSTONE = "portable_grindstone";
    /** 酿造台（内容与进度挂在玩家数据上，见 {@link PortableBrewingData}）。 */
    public static final String BREWING = "portable_brewing";
    /** 进阶高级附魔台（自建菜单 + 自建界面，见 {@link PortableEnchanting}）。 */
    public static final String ENCHANTING = "portable_enchanting";

    private PortableStations() {}

    /**
     * 按属性 id 打开对应的随身站点（供统一数据包 {@code PortableStationOpenPacket} 调用）。
     *
     * @return 是否认识这个 id（未知 id 返回 false，调用方自行忽略）
     */
    public static boolean open(ServerPlayer player, String toolId) {
        if (toolId == null) return false;
        switch (toolId) {
            case ENDER_CHEST -> openEnderChest(player);
            case SMITHING -> openSmithing(player);
            case STONECUTTER -> openStonecutter(player);
            case LOOM -> openLoom(player);
            case CARTOGRAPHY -> openCartography(player);
            case GRINDSTONE -> openGrindstone(player);
            case BREWING -> PortableBrewing.open(player);
            case ENCHANTING -> PortableEnchanting.open(player);
            default -> {
                return false;
            }
        }
        return true;
    }

    /** 随身末影箱（与原版末影箱共用同一份库存）。 */
    private static void openEnderChest(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (windowId, inv, p) -> ChestMenu.threeRows(windowId, inv, p.getEnderChestInventory()),
                Component.translatable("container.enderchest")));
    }

    /** 随身锻造台。 */
    private static void openSmithing(ServerPlayer player) {
        PortableStationMenus.Context context = PortableStationMenus.contextOf(player);
        player.openMenu(new SimpleMenuProvider(
                (windowId, inv, p) -> new PortableStationMenus.Smithing(windowId, inv, context),
                Component.translatable("container.upgrade")));
    }

    /** 随身切石机。 */
    private static void openStonecutter(ServerPlayer player) {
        PortableStationMenus.Context context = PortableStationMenus.contextOf(player);
        player.openMenu(new SimpleMenuProvider(
                (windowId, inv, p) -> new PortableStationMenus.Stonecutter(windowId, inv, context),
                Component.translatable("container.stonecutter")));
    }

    /** 随身织布机。 */
    private static void openLoom(ServerPlayer player) {
        PortableStationMenus.Context context = PortableStationMenus.contextOf(player);
        player.openMenu(new SimpleMenuProvider(
                (windowId, inv, p) -> new PortableStationMenus.Loom(windowId, inv, context),
                Component.translatable("container.loom")));
    }

    /** 随身制图台。 */
    private static void openCartography(ServerPlayer player) {
        PortableStationMenus.Context context = PortableStationMenus.contextOf(player);
        player.openMenu(new SimpleMenuProvider(
                (windowId, inv, p) -> new PortableStationMenus.Cartography(windowId, inv, context),
                Component.translatable("container.cartography_table")));
    }

    /** 随身磨石。 */
    private static void openGrindstone(ServerPlayer player) {
        PortableStationMenus.Context context = PortableStationMenus.contextOf(player);
        player.openMenu(new SimpleMenuProvider(
                (windowId, inv, p) -> new PortableStationMenus.Grindstone(windowId, inv, context),
                Component.translatable("container.grindstone_title")));
    }
}
