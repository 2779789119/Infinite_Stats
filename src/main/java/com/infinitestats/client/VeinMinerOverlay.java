package com.infinitestats.client;

import com.infinitestats.InfiniteStats;
import com.infinitestats.handler.VeinMinerLogic;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import com.infinitestats.stats.VeinMinerConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Objects;

/**
 * 「连锁范围显示」—— 用准星对着方块时，把这一下会连锁掉的方块描出边框。
 * <p>
 * 方块集合在客户端用与服务端同一套 {@link VeinMinerLogic} 现算，所以看到的就是会挖掉的
 * （多人环境下若服务端改过匹配标签而本地没改，可能差一点，属于可接受范围）。
 * 每帧只在「瞄着的方块 / 手上工具 / 配置」发生变化时才重算，其余时候直接用缓存，避免每帧跑 BFS。
 * <p>
 * 显示条件：已解锁且未关闭 {@code vein_miner}、面板里「范围预览」开着；
 * 若开了「需潜行激活」，则只有潜行时才预览（否则平时一直挂着边框太吵）。
 */
@Mod.EventBusSubscriber(modid = InfiniteStats.MODID, value = Dist.CLIENT)
public final class VeinMinerOverlay {

    private VeinMinerOverlay() {}

    // ======================== 缓存（只在瞄着的目标变了才重算） ========================

    private static BlockPos cachedOrigin;
    private static BlockState cachedState;
    private static ItemStack cachedTool = ItemStack.EMPTY;
    private static int cachedHash;
    private static List<BlockPos> cachedTargets = List.of();

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.options.hideGui || mc.screen != null) {
            clearCache();
            return;
        }

        var statsOpt = player.getCapability(PlayerStatsProvider.PLAYER_STATS);
        if (!statsOpt.isPresent()) {
            clearCache();
            return;
        }
        PlayerStats stats = statsOpt.orElse(null);
        if (stats == null || !stats.isToggleActive("vein_miner")) {
            clearCache();
            return;
        }

        VeinMinerConfig cfg = stats.getVeinMinerConfig();
        if (!cfg.isShowPreview()) {
            clearCache();
            return;
        }
        // 开了「需潜行激活」时只有潜行中才预览，免得平时一直挂着边框
        if (cfg.isRequireSneak() && !player.isShiftKeyDown()) {
            clearCache();
            return;
        }

        HitResult hit = player.pick(blockReach(player), event.getPartialTick(), false);
        if (hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult blockHit)) {
            clearCache();
            return;
        }

        BlockPos pos = blockHit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            clearCache();
            return;
        }

        ItemStack tool = player.getMainHandItem();
        int hash = configHash(cfg);
        if (!pos.equals(cachedOrigin) || state != cachedState
                || !ItemStack.matches(tool, cachedTool) || hash != cachedHash) {
            cachedOrigin = pos.immutable();
            cachedState = state;
            cachedTool = tool.copy();
            cachedHash = hash;
            cachedTargets = VeinMinerLogic.collectTargets(level, cfg, pos, state, tool);
        }

        if (cachedTargets.isEmpty()) return;
        render(event, cachedOrigin, cachedTargets);
    }

    /** 方块交互距离（走 Forge 的 block_reach 属性，本模组的 reach 属性也写在这里）。 */
    private static double blockReach(LocalPlayer player) {
        if (player.getAttribute(ForgeMod.BLOCK_REACH.get()) == null) return 4.5D;
        return player.getAttributeValue(ForgeMod.BLOCK_REACH.get());
    }

    private static void render(RenderLevelStageEvent event, BlockPos origin, List<BlockPos> targets) {
        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        // 中心方块用金色，标出「以哪一格为准」；其余会连锁的方块用绿色
        LevelRenderer.renderLineBox(pose, lines, new AABB(origin).inflate(0.003D),
                1.0F, 0.85F, 0.35F, 0.9F);
        for (BlockPos target : targets) {
            LevelRenderer.renderLineBox(pose, lines, new AABB(target).inflate(0.003D),
                    0.30F, 1.0F, 0.55F, 0.7F);
        }
        pose.popPose();

        buffers.endBatch(RenderType.lines());
    }

    private static void clearCache() {
        cachedOrigin = null;
        cachedState = null;
        cachedTool = ItemStack.EMPTY;
        cachedTargets = List.of();
    }

    /** 只把「影响会连锁哪些方块」的项算进缓存键。 */
    private static int configHash(VeinMinerConfig cfg) {
        return Objects.hash(cfg.getMatchMode(), cfg.isUseWhitelist(), cfg.getFilterList(),
                cfg.getMaxBlocks(), cfg.getMaxRadius(), cfg.isDiagonal(), cfg.isRequireCorrectTool());
    }
}
