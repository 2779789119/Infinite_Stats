package com.infinitestats.crafting;

import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.BrewingStandMenu;

/**
 * 随身酿造台 —— 与「随身锻造台 / 切石机」等站点同一套思路：直接复用原版菜单类型
 * （{@code MenuType.BREWING_STAND}），客户端自动套用原版酿造台界面，不需要自建 MenuType / Screen。
 * <p>
 * 状态归属见 {@link PortableBrewingData}：酿造台不像切石机那样「读世界数据算成品」，
 * 药水配方、进度全在方块实体自己身上，世界里没方块就没这些东西；但也不能「关掉就把物品还回去」——
 * 玩家要的是「关掉也继续酿、东西留在台子里」。因此状态挂在玩家数据上，由
 * {@code StatEventHandler} 每 tick 驱动，与界面是否打开无关。
 */
public final class PortableBrewing {

    private PortableBrewing() {}

    /** 为玩家打开随身酿造台（调用方需先校验 portable_brewing 开关）。 */
    public static void open(ServerPlayer player) {
        PortableBrewingData data = PlayerStatsProvider.of(player)
                .map(PlayerStats::getBrewingData)
                .orElse(null);
        if (data == null) return;

        player.openMenu(new SimpleMenuProvider(
                (windowId, inv, p) -> new BrewingStandMenu(windowId, inv, data, data),
                Component.translatable("container.brewing")));
    }
}
