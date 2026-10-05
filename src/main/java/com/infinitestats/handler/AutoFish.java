package com.infinitestats.handler;

import com.infinitestats.Config;
import com.infinitestats.mixin.FishingHookAccessor;
import com.infinitestats.stats.PlayerStats;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 钓鱼相关的两个功能：
 * <ul>
 *   <li><b>自动钓鱼</b>（{@code auto_fish}）：主手持钓鱼竿、且视线前方是水面时自动抛竿，
 *       鱼一咬钩就自动收杆，隔一小段随机延迟再抛出；</li>
 *   <li><b>立即咬钩</b>（{@code instant_bite}）：把原版「鱼游过来（100~600 tick）→ 准备咬（20~80 tick）」
 *       两段等待压到最短，手动钓鱼也生效。</li>
 * </ul>
 * 全流程都跑在服务端：浮标、咬钩判定、战利品生成本来就只在服务端进行，
 * 客户端不需要任何改动（所以这里不像跳跃 / 飞行 / 游泳那样需要客户端兜底）。
 * <p>
 * 收杆与抛竿都直接复用原版 {@code FishingRodItem#use}（与玩家右键完全同一条路径）：
 * 收杆那次由原版负责战利品表、经验球、耐久损耗、{@code player.fishing} 清理与音效，
 * 抛竿那次负责生成浮标、消耗统计与音效 —— 不自己复刻任何一份逻辑，避免与其它钓鱼模组跑偏。
 */
public final class AutoFish {

    private AutoFish() {
    }

    /**
     * 「立即咬钩」把两段等待压到的目标值。
     * <p>
     * 不直接设 0：原版是在"递减到 {@code <= 0}"的那一分支里补播放咬钩音效 / 粒子、
     * 并把 {@code nibble} 设成 20~40 的，留 1 tick 让这条正常路径走完。
     */
    private static final int MIN_BITE_WAIT = 1;

    /** 各玩家下一次允许抛竿的 tickCount（收杆后的随机延迟）。 */
    private static final Map<UUID, Long> RECAST_AT = new HashMap<>();

    /** 玩家离开时清理记录，避免长期驻留。 */
    public static void forget(UUID uuid) {
        RECAST_AT.remove(uuid);
    }

    public static void tick(ServerPlayer player, PlayerStats stats) {
        if (player.isSpectator() || player.isDeadOrDying()) return;

        FishingHook hook = player.fishing;

        // 立即咬钩：手动钓鱼也生效
        if (hook != null && stats.isToggleActive("instant_bite")
                && hook.getPlayerOwner() == player) {
            compressBiteTimers(hook);
        }

        // 以下都是「自动钓鱼」的部分
        if (!stats.isToggleActive("auto_fish")) return;

        if (hook != null) {
            // 已有浮标：只在「鱼正咬钩」的窗口内收杆（原版 20~40 tick，错过鱼就跑了）
            if (hook.getPlayerOwner() != player) return;
            if (((FishingHookAccessor) hook).infinitestats$getNibble() <= 0) return;
            InteractionHand hand = findRodHand(player);
            // 手上没竿了：不动，交给原版 FishingHook#shouldStopFishing 收尾
            if (hand == null) return;
            useRod(player, hand);
            scheduleRecast(player);
            return;
        }

        // 没有浮标：主手持竿 + 过了重抛延迟 + 不是在吃喝/拉弓 + 瞄着水面，就自动抛竿
        if (!(player.getMainHandItem().getItem() instanceof FishingRodItem)) return;
        if (player.isUsingItem()) return;
        if (player.tickCount < RECAST_AT.getOrDefault(player.getUUID(), 0L)) return;
        if (!aimingAtWater(player)) return;
        useRod(player, InteractionHand.MAIN_HAND);
    }

    /** 自动抛竿的视线检查距离（格）。原版浮标从眼前 0.6 格处沿视线以约 1 格/tick 抛出，落点最远 10 格上下。 */
    private static final double AIM_CHECK_DISTANCE = 12.0;

    /**
     * 视线前方是不是水面。
     * <p>
     * 用 {@link ClipContext.Block#COLLIDER}（只把<b>有碰撞</b>的方块算作遮挡）+
     * {@link ClipContext.Fluid#ANY}：草、花、火把、藤蔓这类无碰撞方块不会误判成遮挡，
     * 而浮标本来也能穿过它们落进水里 —— 判定口径与浮标的实际飞行一致。
     * <p>
     * 命中流体时 {@link BlockHitResult#getBlockPos()} 就是流体方块本身，据此判断是不是水
     * （岩浆不算，瞄着岩浆不抛竿）。
     */
    private static boolean aimingAtWater(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(AIM_CHECK_DISTANCE));
        BlockHitResult hit = player.level().clip(new ClipContext(eye, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
        if (hit.getType() != HitResult.Type.BLOCK) return false;
        FluidState fluid = player.level().getFluidState(hit.getBlockPos());
        return fluid.is(FluidTags.WATER);
    }

    /**
     * 立即咬钩：把两段等待计时压到 {@link #MIN_BITE_WAIT}，下一两个 tick 内就会进入咬钩。
     * <p>
     * 每 tick 都压一次（原版 {@code timeUntilLured} 归零时会重新给 {@code timeUntilHooked} 赋 20~80，
     * 所以只压一次会被覆盖回来）。
     */
    private static void compressBiteTimers(FishingHook hook) {
        FishingHookAccessor acc = (FishingHookAccessor) hook;
        if (acc.infinitestats$getTimeUntilLured() > MIN_BITE_WAIT) {
            acc.infinitestats$setTimeUntilLured(MIN_BITE_WAIT);
        }
        if (acc.infinitestats$getTimeUntilHooked() > MIN_BITE_WAIT) {
            acc.infinitestats$setTimeUntilHooked(MIN_BITE_WAIT);
        }
    }

    /**
     * 用指定手走一次原版「使用钓鱼竿」：手上已有浮标时原版走收杆分支，否则走抛竿分支。
     */
    private static void useRod(ServerPlayer player, InteractionHand hand) {
        ItemStack rod = player.getItemInHand(hand);
        rod.use(player.level(), player, hand);
        // 服务端主动使用物品不会自动带挥臂动作包，补一下让周围玩家（含自己）看到动作
        player.swing(hand);
    }

    /** 主手优先、其次副手，返回持竿的手；都没竿返回 null。 */
    private static InteractionHand findRodHand(Player player) {
        if (player.getMainHandItem().getItem() instanceof FishingRodItem) return InteractionHand.MAIN_HAND;
        if (player.getOffhandItem().getItem() instanceof FishingRodItem) return InteractionHand.OFF_HAND;
        return null;
    }

    /** 收杆后安排下一次抛竿（配置区间内随机），避免瞬间连抛。 */
    private static void scheduleRecast(ServerPlayer player) {
        int min = Config.AUTO_FISH_RECAST_DELAY_MIN.get();
        int max = Config.AUTO_FISH_RECAST_DELAY_MAX.get();
        if (min > max) {
            int tmp = min;
            min = max;
            max = tmp;
        }
        int delay = max <= min ? min : min + player.getRandom().nextInt(max - min + 1);
        RECAST_AT.put(player.getUUID(), player.tickCount + (long) delay);
    }
}
