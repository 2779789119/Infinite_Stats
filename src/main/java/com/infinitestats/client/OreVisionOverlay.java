package com.infinitestats.client;

import com.infinitestats.InfiniteStats;
import com.infinitestats.stats.OreVisionConfig;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Objects;

/**
 * 「矿透」—— 把周围已加载区块里的矿石描出边框，并且<b>穿透方块</b>显示。
 * <p>
 * 穿透的做法是<b>绕开 RenderType、自己拿 builder 画</b>（{@code Tesselator} + {@code RenderSystem}）：
 * 走 {@code RenderType.lines()} 那条路的话，{@code RenderType.setupRenderState()} 会在真正绘制之前
 * 把该类型自带的 LEQUAL 深度测试重新推给 GL，把外面关掉的深度测试又打开 —— 边框照样被石头挡住。
 * 自己画就没有这一步，{@link RenderSystem#disableDepthTest()} 是最后一句话。
 * 顶点也直接落在主画面上（不经过实体专用的离屏 target），不存在「画完却等不到合成」的时序问题。
 * <p>
 * 绘制阶段固定在 {@code AFTER_TRANSLUCENT_BLOCKS}：这一档的 {@code event.getPoseStack()} 还带着
 * 相机旋转，{@code translate(-相机位置)} 之后坐标才落在正确方块上（更晚的 {@code AFTER_LEVEL}
 * 已经被原版 {@code popPose()} 弹掉了相机变换，画出来是错位的）。穿透与阶段无关 ——
 * 那是 {@code disableDepthTest()} 的功劳。
 * <p>
 * 显示条件：已解锁且未关闭 {@code ore_vision} 属性；若开了「需潜行激活」则只在潜行时显示。
 * 颜色按矿物分组走（煤灰 / 铁棕 / 金 / 红石红 / 钻石青 …），一眼能分辨是什么矿。
 * <p>
 * 扫描不是每帧做的 —— 那等于每帧遍历十几万个方块位。缓存的重建条件只有四条：
 * 玩家移动超过 {@link #MOVE_RESCAN} 格、距离上次扫描满 {@link #IDLE_RESCAN_TICKS} tick、
 * 配置变了、换了维度。其余时候每帧只负责把缓存里的方块画出来。
 */
@Mod.EventBusSubscriber(modid = InfiniteStats.MODID, value = Dist.CLIENT)
public final class OreVisionOverlay {

    /** 玩家挪动超过这么多格就重扫（方块）。 */
    private static final int MOVE_RESCAN = 4;
    /** 站着不动时的强制重扫间隔（tick）—— 挖掉矿石、别人放方块都能在这个时间尺度内反映出来。 */
    private static final long IDLE_RESCAN_TICKS = 20L;

    private OreVisionOverlay() {}

    /**
     * 当前缓存里待描边的矿石数量。
     * <p>
     * 设置面板会把它显示出来：一旦出现「属性开着却看不到边框」，这个数字能立刻说明问题在哪一侧 ——
     * 是 0（扫描没命中：没解锁 / 区块没加载 / 种类被过滤光了）还是几十上百（扫描没问题，问题在渲染）。
     */
    static int cachedCount() {
        return cached.size();
    }

    // ======================== 缓存 ========================

    private static List<OreVisionScanner.OreBlock> cached = List.of();
    private static BlockPos cachedCenter;
    private static ResourceKey<Level> cachedDimension;
    private static int cachedHash;
    private static long lastScanAt = Long.MIN_VALUE;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        // 必须用带相机变换的那一档 pose stack。
        // 不能用更晚的 AFTER_LEVEL：原版在 renderLevel 结尾已经 popPose() 弹掉了相机变换，
        // 那时再 translate(-相机位置) 得到的是一套错乱的坐标，线框会整体不在方块上。
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            clearCache();
            return;
        }

        var statsOpt = player.getCapability(PlayerStatsProvider.PLAYER_STATS);
        if (!statsOpt.isPresent()) {
            clearCache();
            return;
        }
        PlayerStats stats = statsOpt.orElse(null);
        if (stats == null || !stats.isToggleActive("ore_vision")) {
            clearCache();
            return;
        }

        OreVisionConfig cfg = stats.getOreVisionConfig();
        // 需潜行时只是「不画」，缓存留着 —— 松开潜行立刻能看到，不必再等一次扫描
        if (cfg.isRequireSneak() && !player.isShiftKeyDown()) return;

        BlockPos center = player.blockPosition();
        if (needsRescan(level, center, cfg)) {
            cached = OreVisionScanner.scan(level, center, cfg);
            cachedCenter = center.immutable();
            cachedDimension = level.dimension();
            cachedHash = configHash(cfg);
            lastScanAt = level.getGameTime();
        }

        if (!cached.isEmpty()) render(event, cached);
    }

    private static boolean needsRescan(ClientLevel level, BlockPos center, OreVisionConfig cfg) {
        if (cachedCenter == null) return true;
        if (!level.dimension().equals(cachedDimension)) return true;
        if (configHash(cfg) != cachedHash) return true;
        if (center.distSqr(cachedCenter) > (double) (MOVE_RESCAN * MOVE_RESCAN)) return true;
        return level.getGameTime() - lastScanAt >= IDLE_RESCAN_TICKS;
    }

    /**
     * 把一个方块的 12 条棱画成线框。
     * <p>
     * 关键在深度状态是<b>我们自己</b>设的：{@code disableDepthTest()} 之后没有任何
     * {@code RenderType.setupRenderState()} 会把它覆盖回去，所以边框一定画在最上层。
     * 每个方块用「六个坐标」的重载而不是 {@code new AABB}：一帧几千个方块，省下的是实打实的 GC 压力。
     */
    private static void render(RenderLevelStageEvent event, List<OreVisionScanner.OreBlock> blocks) {
        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();

        // 先把共享批次刷干净：下面要借 Tesselator 那个 BufferBuilder 手动画，
        // 若 BufferSource 里还挂着没提交的顶点，两边会抢同一个 builder
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        buffers.endBatch();

        RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        // 穿透的来源：不做深度测试。线框渲染在最后，这里也不会被谁覆盖
        RenderSystem.disableDepthTest();
        // 只写颜色不写深度：不该让线框改动深度缓冲，影响排在它后面的手部 / GUI 渲染
        RenderSystem.depthMask(false);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        builder.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);

        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        for (OreVisionScanner.OreBlock ore : blocks) {
            float[] color = ore.group().color();
            BlockPos pos = ore.pos();
            LevelRenderer.renderLineBox(pose, builder,
                    pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1.0D, pos.getY() + 1.0D, pos.getZ() + 1.0D,
                    color[0], color[1], color[2], 1.0F);
        }
        pose.popPose();

        BufferUploader.drawWithShader(builder.end());

        // 还原状态，别影响排在后面的渲染
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void clearCache() {
        cached = List.of();
        cachedCenter = null;
        cachedDimension = null;
        lastScanAt = Long.MIN_VALUE;
    }

    /** 只把「影响扫描结果」的项算进缓存键。 */
    private static int configHash(OreVisionConfig cfg) {
        return Objects.hash(cfg.getRange(), cfg.getMaxBlocks(), cfg.getHiddenGroups());
    }
}
