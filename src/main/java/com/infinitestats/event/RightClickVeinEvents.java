package com.infinitestats.event;

import com.infinitestats.InfiniteStats;
import com.infinitestats.handler.RightClickVeinLogic;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import com.infinitestats.stats.VeinMinerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 「右键整片」的事件接入：斧剥皮 / 铲铺路 / 锄耕地 / 锄收作物。
 * <p>
 * 拦截点选 {@link PlayerInteractEvent.RightClickBlock}：它在原版真正处理这一格之前触发，
 * 可以拒绝掉原版的单块行为，于是整片动作完全由本模组一次性完成，不会出现
 * 「先用原版处理这一格、再把其它方块补一遍」的双重结算。
 * <p>
 * 两端都跑，但职责不同 —— 判定条件（是否归本模组管）两端必须一致，所以都先走
 * {@code RightClickVeinLogic.detect} + 玩家配置；判定通过后两端都拦掉原版单块行为，
 * 真正的批量执行只在服务端做（客户端不预测，避免与服务端结果不一致）。
 */
@Mod.EventBusSubscriber(modid = InfiniteStats.MODID)
public final class RightClickVeinEvents {

    private RightClickVeinEvents() {}

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        Player player = event.getEntity();
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        ItemStack tool = event.getItemStack();

        // 功能开关：配置随属性数据同步到客户端，所以两端判断一致
        PlayerStats stats = player.getCapability(PlayerStatsProvider.PLAYER_STATS).orElse(null);
        if (stats == null || !stats.isToggleActive("vein_miner")) return;

        VeinMinerConfig cfg = stats.getVeinMinerConfig();
        if (!cfg.isRightClickVein()) return;

        RightClickVeinLogic.Action action = RightClickVeinLogic.detect(state, tool);
        if (action == RightClickVeinLogic.Action.NONE) return;

        // 到此确定归本模组处理：两端都拦掉原版单块右键
        event.setUseBlock(Event.Result.DENY);
        event.setUseItem(Event.Result.DENY);

        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)
                || !(level instanceof ServerLevel serverLevel)) {
            return;
        }

        int done = RightClickVeinLogic.apply(serverPlayer, serverLevel, pos, state, tool, cfg, action);
        if (done > 0) {
            serverPlayer.swing(InteractionHand.MAIN_HAND, true);
        }
    }
}
