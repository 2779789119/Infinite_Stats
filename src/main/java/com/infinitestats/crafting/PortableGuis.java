package com.infinitestats.crafting;

import com.infinitestats.stats.PlayerStats;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraftforge.network.NetworkHooks;

/**
 * 随身功能台：所有「随身站点」的统一打开入口。
 * <p>
 * 与 {@link PortableAnvil} 同一套思路 —— 直接复用原版菜单类型，客户端会自动套用原版界面，
 * 不需要自建 MenuType / Screen：
 * <ul>
 *   <li><b>末影箱</b>：原版 9×3 箱子菜单（{@code MenuType.GENERIC_9x3}），容器直接接
 *       {@code player.getEnderChestInventory()}，因此与原版末影箱共用同一份库存；</li>
 *   <li><b>锻造台 / 切石机 / 织布机 / 制图台 / 磨石</b>：原版菜单算法 + 解除方块校验，
 *       见 {@link PortableStationMenus}。它们需要一个真实的 {@code ContainerLevelAccess}
 *       才能正常计算成品，因此统一走 {@link PortableStationMenus#contextOf}，
 *       只是把「必须站在对应方块旁」这条校验去掉了；</li>
 *   <li><b>进阶高级附魔台</b>：不是「复用原版菜单」，而是自选附魔的全新菜单 + 界面
 *       （见 {@link PortableInfuserMenu} 与 {@link PortableInfuser}）——
 *       自选附魔、改已有附魔、经验修复、经验回收、制作附魔书，付款可用经验等级或属性点数。</li>
 * </ul>
 */
public final class PortableGuis {

    /** 「随身附魔台」属性 id：1 点解锁的开关（是否能用随身进阶高级附魔台）。 */
    public static final String STAT_ENCHANTING = "portable_enchanting";

    private PortableGuis() {}

    /** 为玩家打开随身末影箱（调用方需先校验属性开关）。 */
    public static void openEnderChest(ServerPlayer player) {
        NetworkHooks.openScreen(player, new SimpleMenuProvider(
                (windowId, inv, p) -> ChestMenu.threeRows(windowId, inv, p.getEnderChestInventory()),
                Component.translatable("container.enderchest")));
    }

    /** 为玩家打开随身锻造台（调用方需先校验属性开关）。 */
    public static void openSmithing(ServerPlayer player) {
        PortableStationMenus.Context context = PortableStationMenus.contextOf(player);
        NetworkHooks.openScreen(player, new SimpleMenuProvider(
                (windowId, inv, p) -> new PortableStationMenus.Smithing(windowId, inv, context),
                Component.translatable("container.upgrade")));
    }

    /**
     * 为玩家打开随身进阶高级附魔台（调用方需先校验属性开关）。
     * <p>
     * 与服务端其它随身站点不同，这里<b>不</b>复用原版附魔菜单：进阶高级附魔台是「自己挑附魔」
     * 的全新界面（自选附魔 + 改已有附魔 + 经验修复 + 经验回收 + 制作附魔书），
     * 与「三档随机附魔」的原版附魔台没有共用逻辑，见 {@link PortableInfuserMenu}。
     */
    public static void openEnchanting(ServerPlayer player) {
        NetworkHooks.openScreen(player, new SimpleMenuProvider(
                (windowId, inv, p) -> new PortableInfuserMenu(windowId, inv),
                Component.translatable("stat.infinitestats.portable_enchanting")));
    }

    /** 为玩家打开随身切石机。 */
    public static void openStonecutter(ServerPlayer player) {
        PortableStationMenus.Context context = PortableStationMenus.contextOf(player);
        NetworkHooks.openScreen(player, new SimpleMenuProvider(
                (windowId, inv, p) -> new PortableStationMenus.Stonecutter(windowId, inv, context),
                Component.translatable("container.stonecutter")));
    }

    /** 为玩家打开随身织布机。 */
    public static void openLoom(ServerPlayer player) {
        PortableStationMenus.Context context = PortableStationMenus.contextOf(player);
        NetworkHooks.openScreen(player, new SimpleMenuProvider(
                (windowId, inv, p) -> new PortableStationMenus.Loom(windowId, inv, context),
                Component.translatable("container.loom")));
    }

    /** 为玩家打开随身制图台。 */
    public static void openCartography(ServerPlayer player) {
        PortableStationMenus.Context context = PortableStationMenus.contextOf(player);
        NetworkHooks.openScreen(player, new SimpleMenuProvider(
                (windowId, inv, p) -> new PortableStationMenus.Cartography(windowId, inv, context),
                Component.translatable("container.cartography_table")));
    }

    /** 为玩家打开随身磨石。 */
    public static void openGrindstone(ServerPlayer player) {
        PortableStationMenus.Context context = PortableStationMenus.contextOf(player);
        NetworkHooks.openScreen(player, new SimpleMenuProvider(
                (windowId, inv, p) -> new PortableStationMenus.Grindstone(windowId, inv, context),
                Component.translatable("container.grindstone_title")));
    }

    /**
     * 为玩家打开随身酿造台（调用方需先校验属性开关）。
     * <p>
     * 与上面几台不同：酿造台没有「读世界数据算成品」这一步，配方与进度全在方块实体里，
     * 因此走 {@link PortableBrewing} 的虚拟酿造台（不占世界坐标），详见该类注释。
     */
    public static void openBrewing(ServerPlayer player) {
        PortableBrewing.open(player);
    }

    /**
     * 按属性 id 打开对应的随身站点（供统一数据包 {@code PortableToolOpenPacket} 调用）。
     *
     * @param stats  玩家属性数据（附魔台需要读点数），可为 null
     * @return 是否认识这个 id（未知 id 返回 false，调用方自行记录日志）
     */
    public static boolean openStation(ServerPlayer player, PlayerStats stats, String toolId) {
        if (toolId == null) return false;
        switch (toolId) {
            case STAT_ENCHANTING -> openEnchanting(player);
            case "portable_stonecutter" -> openStonecutter(player);
            case "portable_loom" -> openLoom(player);
            case "portable_cartography" -> openCartography(player);
            case "portable_grindstone" -> openGrindstone(player);
            case "portable_smithing" -> openSmithing(player);
            case "portable_brewing" -> openBrewing(player);
            default -> {
                return false;
            }
        }
        return true;
    }
}
