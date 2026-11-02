package com.infinitestats.event;

import com.infinitestats.InfiniteStats;
import com.infinitestats.util.BlockOwnershipStore;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 维护「玩家放置的方块」的<b>归属表</b>（{@link BlockOwnershipStore}）。
 * <p>
 * 只做两件事：
 * <ul>
 *   <li>玩家放下带方块实体的方块时，记下「这块是谁放的」；</li>
 *   <li>方块被玩家破坏时，把那条归属清掉。</li>
 * </ul>
 * <p>
 * 判定口径刻意与使用方保持一致（{@code EnergyCompat} 的「无限能源」、{@code CreateStressCompat}
 * 的「无限应力」）—— <b>只有玩家本人放置的才算数</b>。
 * 非玩家来源（发射器、其它模组生成、{@code /setblock}）不记录，因而不受这两条属性影响。
 * <p>
 * 记录时只认「带方块实体」的方块：能量能力与动力网络都挂在方块实体上，纯方块永远不会有，
 * 因此石头 / 泥土这类不占表，表里只剩机器 / 储能 / 发电机 / 动力方块。同一坐标改放非方块实体方块时，
 * 顺手清掉可能残留的旧归属，避免坐标复用留下错配。
 */
@Mod.EventBusSubscriber(modid = InfiniteStats.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BlockOwnershipEvents {

    private BlockOwnershipEvents() {}

    /** 玩家放置方块：带方块实体的记入归属，否则清掉该坐标的旧归属。 */
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        BlockPos pos = event.getPos();
        BlockState state = event.getPlacedBlock();
        BlockOwnershipStore store = BlockOwnershipStore.get(level);
        if (state.getBlock() instanceof EntityBlock) {
            store.record(pos, player.getUUID());
        } else {
            store.remove(pos);
        }
    }

    /** 玩家破坏方块：清掉该坐标的归属（谁破坏都清，归属只认放置者）。 */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockOwnershipStore.get(level).remove(event.getPos());
    }
}
