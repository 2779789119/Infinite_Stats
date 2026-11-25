package com.infinitestats.handler;

import com.infinitestats.Config;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.StatCategory;
import com.infinitestats.stats.StatType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 时间加速处理器
 *
 * 参考 Torcherino（加速火把）的设计思路：加速玩家周围的时间流速。
 * Torcherino 通过重复运行 level 的 tick 循环来让附近的方块实体与随机方块刻加速；
 * 这里以「加速」属性点数为倍率，对玩家周围区域额外执行：
 *   1) 随机方块刻（作物生长、树苗、火、树叶腐烂等）
 *   2) 方块实体刻（熔炉冶炼、刷怪笼、酿造台、漏斗等）
 * 方块实体刻使用与原版 level tick 完全相同的 TickingBlockEntity 路径，
 * 避免绕过任何内部逻辑。
 */
public class TimeAccelHandler implements StatEffectHandler {

    /**
     * 单个游戏刻最多执行的额外刻数。
     * <p>
     * 属性没有上限（轮椅模组不砍强度），但「额外刻」是乘在<b>整个半径体积</b>上的：
     * 额外刻数 × 随机刻数 × 方块实体数，点高了就是单 tick 内几十万次方块查询，
     * 结果是服务器直接卡住而不是变快。这里只给「单 tick 的工作量」设天花板，
     * 多点出来的部分直接丢弃（不累加到下一 tick，否则会一直爆）。
     * 64 已经相当于 64 倍速，远超过实际可感知的范围。
     */
    private static final int MAX_EXTRA_TICKS = 64;

    /** 加速半径上限（格）。原配置 + 属性点数相加后可能到几十格，体积是立方的。 */
    private static final int MAX_RADIUS = 16;

    /** 单次额外刻最多做的随机刻次数（默认 randomTickSpeed=3、半径 16 时约 1600 次）。 */
    private static final int MAX_RANDOM_TICKS = 8192;

    @Override
    public String getId() {
        return "time_accel";
    }

    @Override
    public StatType[] getSupportedStats() {
        return new StatType[] { StatType.fromId("time_accel"), StatType.fromId("time_accel_radius") };
    }

    @Override
    public void onTick(ServerPlayer player, PlayerStats stats, long tickCount) {
        float accel = stats.getStatValue("time_accel");
        if (accel <= 0) {
            // 没有加速时清空累加器，避免点数变动后残留导致突然跳变
            stats.setTimeAccelAccum(0);
            return;
        }

        if (player.level().isClientSide()) return;
        ServerLevel level = (ServerLevel) player.level();

        // 累加小数部分，整数部分作为本 tick 的额外加速刻数。
        // 累加器直接钳在 MAX_EXTRA_TICKS：超出部分丢弃，不留给下一 tick（否则永远在爆）。
        double accum = Math.min(stats.getTimeAccelAccum() + accel, MAX_EXTRA_TICKS);
        int extraTicks = (int) accum;
        stats.setTimeAccelAccum(accum - extraTicks);
        if (extraTicks <= 0) return;

        // 影响半径 = 配置基础半径 + 玩家「加速半径」属性点数
        int radius = Config.TIME_ACCEL_RADIUS.get() + (int) stats.getStatValue("time_accel_radius");
        radius = Math.max(1, Math.min(MAX_RADIUS, radius));

        BlockPos center = player.blockPosition();

        // 方块实体的快照每 tick 只取一次，多个额外刻复用同一份：
        // 原来每个额外刻都把半径内所有区块的方块实体表整份复制一遍（额外刻 64 时就是 64 倍复制）。
        List<BlockEntity> blockEntities = snapshotBlockEntities(level, center, radius);

        for (int i = 0; i < extraTicks; i++) {
            accelerateRandomTicks(level, center, radius);
            tickBlockEntities(level, blockEntities, center, radius);
        }
    }

    /**
     * 额外随机方块刻。
     * 模拟原版在相同体积下每游戏刻应执行的随机刻数量：
     * 每个 16x16x16 区块每游戏刻的随机刻数 = randomTickSpeed（游戏规则，默认 3）。
     */
    private void accelerateRandomTicks(ServerLevel level, BlockPos center, int radius) {
        int randomTickSpeed = level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
        if (randomTickSpeed <= 0) return;

        double volume = Math.pow(2 * radius + 1, 3);
        int randomTickCount = (int) Math.round(volume / 4096.0 * randomTickSpeed);
        // 单次额外刻的随机刻数量也要封顶：半径 16 时体积已是 35³，randomTickSpeed 调高就是几万次方块查询
        if (randomTickCount > MAX_RANDOM_TICKS) randomTickCount = MAX_RANDOM_TICKS;

        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight() - 1;

        for (int i = 0; i < randomTickCount; i++) {
            int x = center.getX() + level.random.nextInt(2 * radius + 1) - radius;
            int y = center.getY() + level.random.nextInt(2 * radius + 1) - radius;
            int z = center.getZ() + level.random.nextInt(2 * radius + 1) - radius;

            // 钳制高度到合法范围，避免越界方块状态
            if (y < minY) y = minY;
            if (y > maxY) y = maxY;

            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);
            // randomTick 内部会判断该方块是否参与随机刻，非随机刻方块为空操作
            state.randomTick(level, pos, level.random);
        }
    }

    /**
     * 额外方块实体刻。
     * 遍历玩家周围已加载区块中的方块实体，使用原版的 TickingBlockEntity 路径再 tick 一次。
     */
    private static List<BlockEntity> snapshotBlockEntities(ServerLevel level, BlockPos center, int radius) {
        int cxMin = (center.getX() - radius) >> 4;
        int cxMax = (center.getX() + radius) >> 4;
        int czMin = (center.getZ() - radius) >> 4;
        int czMax = (center.getZ() + radius) >> 4;

        List<BlockEntity> out = new ArrayList<>();
        for (int cx = cxMin; cx <= cxMax; cx++) {
            for (int cz = czMin; cz <= czMax; cz++) {
                // 只使用已加载的区块，避免强制生成新区块
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                // 复制一份快照：tick 过程中方块实体可能被增删（刷怪笼、漏斗等），
                // 直接遍历原 HashMap 会触发 ConcurrentModificationException。
                // 这份快照在本 tick 的所有额外刻里复用，只复制一次。
                out.addAll(chunk.getBlockEntities().values());
            }
        }
        return out;
    }

    /**
     * 对快照里的方块实体各 tick 一次。
     * <p>
     * 快照是复用的，所以必须跳过已被移除的方块实体 —— 前一个额外刻里
     * 漏斗 / 刷怪笼之类的逻辑可能已经把某个方块实体删掉了。
     */
    private void tickBlockEntities(ServerLevel level, List<BlockEntity> blockEntities,
                                   BlockPos center, int radius) {
        if (blockEntities.isEmpty()) return;

        int yMin = center.getY() - radius;
        int yMax = center.getY() + radius;
        int centerX = center.getX();
        int centerZ = center.getZ();

        for (int i = 0; i < blockEntities.size(); i++) {
            BlockEntity be = blockEntities.get(i);
            if (be.isRemoved()) continue;

            BlockPos bep = be.getBlockPos();
            if (bep.getY() < yMin || bep.getY() > yMax) continue;
            if (Math.abs(bep.getX() - centerX) > radius) continue;
            if (Math.abs(bep.getZ() - centerZ) > radius) continue;

            BlockState state = be.getBlockState();
            if (state.getBlock() instanceof EntityBlock entityBlock) {
                @SuppressWarnings("unchecked")
                BlockEntityTicker ticker = entityBlock.getTicker(level, state, be.getType());
                if (ticker != null) {
                    ticker.tick(level, bep, state, be);
                }
            }
        }
    }

    @Override
    public void onLogin(ServerPlayer player, PlayerStats stats) {
    }

    @Override
    public void onRespawn(ServerPlayer player, PlayerStats stats) {
    }

    @Override
    public void onDimensionChange(ServerPlayer player, PlayerStats stats) {
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
