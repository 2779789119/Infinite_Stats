package com.infinitestats.handler;

import com.infinitestats.stats.VeinMinerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 「右键整片」—— 对标 FTB Ultimine 的右键处理器：同一类方块一次全做掉。
 * <ul>
 *   <li>斧 → 整片剥皮（原木变去皮原木）；</li>
 *   <li>铲 → 整片铺路（草方块 / 土等变土径）；</li>
 *   <li>锄 → 整片耕地（草方块 / 土等变耕地）；</li>
 *   <li>锄对着**成熟作物** → 整片收割并补种（小麦 / 胡萝卜 / 土豆 / 甜菜 / 下界疣）。</li>
 * </ul>
 * 前三种不自己写映射表，而是对每一格走一次原版 {@code tool.useOn}（即「在原版看来玩家右键了这一格」），
 * 这样木种、土类、音效、粒子、工具耐久、统计、以及别的模组注册的扩展全都自动跟随原版行为；
 * 收割因为原版没有「右键收作物」这回事，才自己实现（掉落 + 补种 + 音效）。
 * <p>
 * 收集范围复用连锁那套匹配（{@link VeinMinerLogic#collectMatching}）：受面板里的
 * 「方块匹配方式 / 单次方块数 / 搜索半径 / 斜向」约束，且只收「同一动作也处理得了」的方块，
 * 不会顺着连到已经处理过的方块上。
 */
public final class RightClickVeinLogic {

    private RightClickVeinLogic() {}

    /** 右键整片能做的动作 */
    public enum Action {
        NONE,
        /** 斧：剥皮 */
        STRIP,
        /** 铲：铺路 */
        FLATTEN,
        /** 锄：耕地 */
        TILL,
        /** 锄：收割成熟作物并补种 */
        HARVEST
    }

    /** 原版可铲平的方块（对齐 {@code ShovelItem.FLATTENABLES}） */
    private static final Set<Block> FLATTENABLE = Set.of(
            Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.PODZOL,
            Blocks.COARSE_DIRT, Blocks.MYCELIUM, Blocks.ROOTED_DIRT);

    /** 原版可耕地的方块（对齐 {@code HoeItem.TILLABLES}） */
    private static final Set<Block> TILLABLE = Set.of(
            Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT);

    /**
     * 判定这一下右键属于哪个动作（只读方块状态与手上工具，两端结果一致）。
     * 返回 {@link Action#NONE} 表示不归我们管，原版照常处理。
     */
    public static Action detect(BlockState state, ItemStack tool) {
        if (tool.isEmpty()) return Action.NONE;

        if (tool.getItem() instanceof AxeItem) {
            return AxeItem.getAxeStrippingState(state) != null ? Action.STRIP : Action.NONE;
        }
        if (tool.getItem() instanceof ShovelItem) {
            return FLATTENABLE.contains(state.getBlock()) ? Action.FLATTEN : Action.NONE;
        }
        if (tool.getItem() instanceof HoeItem) {
            if (TILLABLE.contains(state.getBlock())) return Action.TILL;
            return isHarvestable(state) ? Action.HARVEST : Action.NONE;
        }
        return Action.NONE;
    }

    /** 是否为「成熟、可以收割」的作物。 */
    public static boolean isHarvestable(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        if (state.getBlock() instanceof NetherWartBlock) {
            return state.getValue(NetherWartBlock.AGE) >= 3;
        }
        return false;
    }

    /**
     * 对整片同类方块执行同一个动作，返回实际处理的方块数。
     * <p>
     * 只应在服务端调用：客户端的职责是配合拦掉原版的单块右键（见
     * {@code event.RightClickVeinEvents}）。
     */
    public static int apply(ServerPlayer player, ServerLevel level, BlockPos origin, BlockState originState,
            ItemStack tool, VeinMinerConfig cfg, Action action) {
        int limit = VeinMinerLogic.effectiveMaxBlocks(cfg);
        List<BlockPos> targets = new ArrayList<>();
        targets.add(origin);
        if (limit > 1) {
            targets.addAll(VeinMinerLogic.collectMatching(level, cfg, origin, originState,
                    limit - 1, filterFor(action)));
        }

        int done = 0;
        for (BlockPos pos : targets) {
            ItemStack current = player.getMainHandItem();
            if (current.isEmpty()) break;
            // 工具保护：耐久只剩最后 1 点时收手
            if (cfg.isProtectTool() && current.isDamageableItem()
                    && current.getDamageValue() >= current.getMaxDamage() - 1) break;

            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;

            if (action == Action.HARVEST) {
                if (!isHarvestable(state)) continue;
                harvest(player, level, pos, state);
            } else {
                // 原版路径：方向固定朝上（耕地要求不是底面、且上方是空气，由原版自己判断）
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
                InteractionResult result = current.useOn(
                        new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
                if (!result.consumesAction()) continue;
            }
            done++;
        }
        return done;
    }

    /** 该动作「也处理得了」的方块判定（用来筛收集范围，避免连到已处理过的方块）。 */
    private static Predicate<BlockState> filterFor(Action action) {
        return switch (action) {
            case STRIP -> state -> AxeItem.getAxeStrippingState(state) != null;
            case FLATTEN -> state -> FLATTENABLE.contains(state.getBlock());
            case TILL -> state -> TILLABLE.contains(state.getBlock());
            case HARVEST -> RightClickVeinLogic::isHarvestable;
            case NONE -> state -> false;
        };
    }

    /** 收割一格成熟作物：掉落照发，然后把作物重置回第 0 生长阶段（补种）。 */
    private static void harvest(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        List<ItemStack> drops = Block.getDrops(state, level, pos,
                level.getBlockEntity(pos), player, player.getMainHandItem());
        int exp = state.getBlock().getExpDrop(state, level, level.random, pos, 0, 0);

        if (state.getBlock() instanceof CropBlock crop) {
            level.setBlock(pos, crop.defaultBlockState().setValue(CropBlock.AGE, 0), 3);
        } else if (state.getBlock() instanceof NetherWartBlock) {
            level.setBlock(pos, state.setValue(NetherWartBlock.AGE, 0), 3);
        }

        for (ItemStack drop : drops) {
            Block.popResource(level, pos, drop);
        }
        if (exp > 0) player.giveExperiencePoints(exp);
        level.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    /** 供事件层判断「这次右键是否归本模组处理」（两端都要用它，口径必须一致）。 */
    public static boolean handles(Level level, BlockPos pos, ItemStack tool) {
        return detect(level.getBlockState(pos), tool) != Action.NONE;
    }
}
