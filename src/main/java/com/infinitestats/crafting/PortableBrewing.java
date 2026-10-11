package com.infinitestats.crafting;

import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraftforge.network.NetworkHooks;

/**
 * 随身酿造台 —— 与「随身锻造台 / 切石机」等站点同一套思路：直接复用原版菜单类型
 * （{@code MenuType.BREWING_STAND}），客户端自动套用原版酿造台界面，不需要自建 MenuType / Screen。
 *
 * <h3>状态归属：台子跟着玩家走</h3>
 * 酿造台不像切石机那样「读世界数据算成品」—— 药水配方、进度、燃料全在方块实体自己身上，
 * 世界里没方块就没这些东西。<b>但也不能像切石机那样「关掉就把物品还回去」</b>：
 * 玩家要的是「关掉也继续酿、东西留在台子里」。
 * <p>
 * 因此酿造台的状态是 {@link PortableBrewingData}（挂在玩家 PlayerStats 上、随存档保存）：
 * <ul>
 *   <li>容器（5 格）与进度数据都由它提供 —— 本类只负责把原版菜单挂上去；</li>
 *   <li>酿造由 {@code StatEventHandler#onPlayerTick} 每 tick 驱动，<b>与界面是否打开无关</b>，
 *       关掉界面、跑去别的维度都照常酿；</li>
 *   <li>该数据同时也是 {@code ContainerData}，所以原版界面的进度条 / 燃料条照常显示，
 *       物品与进度通过原版菜单的槽位同步发给客户端，不新增网络包。</li>
 * </ul>
 * 与随身熔炉的区别只有一个：熔炉「没燃料就停」是持续生产设备，酿造台一轮 400 tick（20 秒），
 * 摆进去就得等完这一轮 —— 但只要不把原料 / 药水拿走，中途关界面不会打断它。
 */
public final class PortableBrewing {

    private PortableBrewing() {}

    /** 为玩家打开随身酿造台（调用方需先校验 portable_brewing 开关）。 */
    public static void open(ServerPlayer player) {
        PortableBrewingData data = player.getCapability(PlayerStatsProvider.PLAYER_STATS)
                .map(PlayerStats::getBrewingData)
                .orElse(null);
        if (data == null) return;

        NetworkHooks.openScreen(player, new SimpleMenuProvider(
                (windowId, inv, p) -> new BrewingStandMenu(windowId, inv, data, data),
                Component.translatable("container.brewing")));
    }
}
