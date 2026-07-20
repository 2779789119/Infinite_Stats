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

        // 累加小数部分，整数部分作为本 tick 的额外加速刻数（不再设上限）
        double accum = stats.getTimeAccelAccum() + accel;
        int extraTicks = (int) accum;
        stats.setTimeAccelAccum(accum - extraTicks);
        if (extraTicks <= 0) return;

        // 影响半径 = 配置基础半径 + 玩家「加速半径」属性点数
        int radius = Config.TIME_ACCEL_RADIUS.get() + (int) stats.getStatValue("time_accel_radius");
        if (radius < 1) radius = 1;

        for (int i = 0; i < extraTicks; i++) {
            accelerateOnce(player, level, radius);
        }
    }

    /**
     * 对玩家周围区域执行一次"额外游戏刻"的时间加速。
     */
    private void accelerateOnce(ServerPlayer player, ServerLevel level, int radius) {
        BlockPos center = player.blockPosition();

        accelerateRandomTicks(player, level, center, radius);
        accelerateBlockEntities(player, level, center, radius);
    }

    /**
     * 额外随机方块刻。
     * 模拟原版在相同体积下每游戏刻应执行的随机刻数量：
     * 每个 16x16x16 区块每游戏刻的随机刻数 = randomTickSpeed（游戏规则，默认 3）。
     */
    private void accelerateRandomTicks(ServerPlayer player, ServerLevel level, BlockPos center, int radius) {
        int randomTickSpeed = level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
        if (randomTickSpeed <= 0) return;

        double volume = Math.pow(2 * radius + 1, 3);
        int randomTickCount = (int) Math.round(volume / 4096.0 * randomTickSpeed);

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
    private void accelerateBlockEntities(ServerPlayer player, ServerLevel level, BlockPos center, int radius) {
        int cxMin = (center.getX() - radius) >> 4;
        int cxMax = (center.getX() + radius) >> 4;
        int czMin = (center.getZ() - radius) >> 4;
        int czMax = (center.getZ() + radius) >> 4;
        int yMin = center.getY() - radius;
        int yMax = center.getY() + radius;

        for (int cx = cxMin; cx <= cxMax; cx++) {
            for (int cz = czMin; cz <= czMax; cz++) {
                // 只使用已加载的区块，避免强制生成新区块
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;

                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos bep = be.getBlockPos();
                    if (bep.getY() < yMin || bep.getY() > yMax) continue;
                    if (Math.abs(bep.getX() - center.getX()) > radius) continue;
                    if (Math.abs(bep.getZ() - center.getZ()) > radius) continue;

                    Block block = be.getBlockState().getBlock();
                    if (block instanceof EntityBlock entityBlock) {
                        @SuppressWarnings("unchecked")
                        BlockEntityTicker ticker = entityBlock.getTicker(level, be.getBlockState(), be.getType());
                        if (ticker != null) {
                            ticker.tick(level, bep, be.getBlockState(), be);
                        }
                    }
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
