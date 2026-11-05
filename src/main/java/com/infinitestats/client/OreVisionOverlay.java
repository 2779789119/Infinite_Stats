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
import com.mojang.blaze3d.vertex.VertexSorting;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Objects;

/**
 * 「矿透」—— 把周围已加载区块里的矿石描出边框，并且<b>穿透方块</b>显示。
 * <p>
 * 穿透的做法是<b>绕开 RenderType、自己拿 builder 画</b>（{@code Tesselator} + {@code RenderSystem}）：
 * 走 {@code RenderType.lines()} 那条路的话，{@code RenderType.setupRenderState()} 会在真正绘制之前
 * 把该类型自带的 LEQUAL 深度测试重新推给 GL，把外面关掉的深度测试又打开 —— 边框照样被石头挡住。
 * 自己画就没有这一步，{@link RenderSystem#disableDepthTest()} 是最后一句话。
 * <p>
 * 绘制阶段固定在 {@code AFTER_TRANSLUCENT_BLOCKS}：这一档的 {@code event.getPoseStack()} 还带着
 * 相机旋转，{@code translate(-相机位置)} 之后坐标才落在正确方块上（更晚的 {@code AFTER_LEVEL}
 * 已经被原版 {@code popPose()} 弹掉了相机变换，画出来是错位的）。
 * <p>
 * 显示条件：已解锁且未关闭 {@code ore_vision} 属性；若开了「需潜行激活」则只在潜行时显示。
 * <p>
 * <b>光影兼容</b>：上面这套写法在开了光影时会整片消失 —— 光影把世界画进自己的缓冲，
 * {@code LevelRenderer#renderLevel} 收尾时再用 final pass 整屏合成，中途画进主画面的东西全被覆盖。
 * 所以开着光影时改走 {@link #onRenderGui}：把「世界投影 + 相机姿态」临时装回渲染管线，
 * 用同一套线框再画一遍（合成已经结束，谁也覆盖不掉），画完原样还原。
 * <p>
 * 扫描不是每帧做的 —— 那等于每帧遍历十几万个方块位。缓存的重建条件只有四条：
 * 玩家移动超过 {@link #MOVE_RESCAN} 格、距离上次扫描满 {@link #IDLE_RESCAN_TICKS} tick、
 * 配置变了、换了维度。
 */
@EventBusSubscriber(modid = InfiniteStats.MODID, value = Dist.CLIENT)
public final class OreVisionOverlay {

    /** 玩家挪动超过这么多格就重扫（方块）。 */
    private static final int MOVE_RESCAN = 4;
    /** 站着不动时的强制重扫间隔（tick）—— 挖掉矿石、别人放方块都能在这个时间尺度内反映出来。 */
    private static final long IDLE_RESCAN_TICKS = 20L;

    private OreVisionOverlay() {}

    /** 当前缓存里待描边的矿石数量（设置面板会显示它，便于判断是扫描问题还是渲染问题）。 */
    static int cachedCount() {
        return cached.size();
    }

    // ======================== 缓存 ========================

    private static List<OreVisionScanner.OreBlock> cached = List.of();
    private static BlockPos cachedCenter;
    private static ResourceKey<Level> cachedDimension;
    private static int cachedHash;
    private static long lastScanAt = Long.MIN_VALUE;

    // ======================== 光影兜底用的世界变换快照 ========================

    private static final Matrix4f worldProjection = new Matrix4f();
    private static final Matrix4f worldPose = new Matrix4f();
    private static Vec3 worldCamPos = Vec3.ZERO;
    /** 快照取自哪一帧（nanoTime）；HUD 阶段靠它判断「这一帧世界到底画过没有」。 */
    private static long worldSnapshotNs = Long.MIN_VALUE;
    /** 这一帧世界阶段是否真的「该显示」（属性开着、且潜行要求满足）。 */
    private static boolean worldPassVisible;
    /** 这一帧是否在跑光影：每帧只问一次，两条渲染路径共用同一个答案。 */
    private static boolean shadersActive;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        // AFTER_LEVEL 这一档的 pose stack 已经被原版 popPose() 弹掉了相机变换，抄下来是错的
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) return;

        shadersActive = ShaderCompat.areShadersActive();
        snapshotWorldTransform(event);

        // 绘制固定在 AFTER_TRANSLUCENT_BLOCKS：更晚的阶段已经没有相机变换
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        worldPassVisible = false;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            clearCache();
            return;
        }

        PlayerStats stats = PlayerStatsProvider.of(player).orElse(null);
        // 客户端靠 isToggleActive 判断解锁：客户端同样持有已分配点数与开关状态
        if (stats == null || !stats.isToggleActive("ore_vision")) {
            clearCache();
            return;
        }

        OreVisionConfig cfg = stats.getOreVisionConfig();
        // 需潜行时只是「不画」，缓存留着 —— 松开潜行立刻能看到，不必再等一次扫描
        if (cfg.isRequireSneak() && !player.isShiftKeyDown()) return;

        worldPassVisible = true;

        BlockPos center = player.blockPosition();
        if (needsRescan(level, center, cfg)) {
            cached = OreVisionScanner.scan(level, center, cfg);
            cachedCenter = center.immutable();
            cachedDimension = level.dimension();
            cachedHash = configHash(cfg);
            lastScanAt = level.getGameTime();
        }

        // 开着光影时这里不画：画了也会被 final pass 覆盖，交给 HUD 阶段（见 onRenderGui）
        if (!cached.isEmpty() && !shadersActive) render(event, cached);
    }

    /**
     * 光影下的绘制入口：HUD 阶段（合成之后）。
     * <p>
     * 坐标不用重算：把世界渲染那一份投影矩阵与相机 pose 装回去，顶点走的还是同一个
     * {@code renderLineBox}，位置与不开光影时一致。
     */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Pre event) {
        if (!worldPassVisible || cached.isEmpty()) return;
        if (worldSnapshotNs == Long.MIN_VALUE) return;
        // 这一帧世界根本没画（暂停 / 只重画 GUI）时快照是陈的，此时按旧视角画会错位
        if (System.nanoTime() - worldSnapshotNs > 50_000_000L) return;
        if (!shadersActive) return;

        renderThroughGui(cached);
    }

    /** 抄下这一帧世界渲染用的投影矩阵 / 相机 pose / 相机坐标。 */
    private static void snapshotWorldTransform(RenderLevelStageEvent event) {
        worldProjection.set(event.getProjectionMatrix());
        worldPose.set(event.getPoseStack().last().pose());
        worldCamPos = event.getCamera().getPosition();
        worldSnapshotNs = System.nanoTime();
    }

    private static boolean needsRescan(ClientLevel level, BlockPos center, OreVisionConfig cfg) {
        if (cachedCenter == null) return true;
        if (!level.dimension().equals(cachedDimension)) return true;
        if (configHash(cfg) != cachedHash) return true;
        if (center.distSqr(cachedCenter) > (double) (MOVE_RESCAN * MOVE_RESCAN)) return true;
        return level.getGameTime() - lastScanAt >= IDLE_RESCAN_TICKS;
    }

    /** 世界阶段的绘制（不开光影时走这里）。 */
    private static void render(RenderLevelStageEvent event, List<OreVisionScanner.OreBlock> blocks) {
        // 先把共享批次刷干净：下面要借 Tesselator 那个 BufferBuilder 手动画
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        buffers.endBatch();

        BufferBuilder builder = beginLineBatch();
        appendBoxes(builder, event.getPoseStack(), event.getCamera().getPosition(), blocks);
        BufferUploader.drawWithShader(builder.buildOrThrow());
        endLineBatch();
    }

    /** 光影下的绘制（在 HUD 阶段调用，理由见 {@link #onRenderGui}）。 */
    private static void renderThroughGui(List<OreVisionScanner.OreBlock> blocks) {
        Minecraft.getInstance().renderBuffers().bufferSource().endBatch();

        var modelView = RenderSystem.getModelViewStack();
        float[] shaderColor = RenderSystem.getShaderColor().clone();

        modelView.pushMatrix();
        modelView.identity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.backupProjectionMatrix();
        try {
            RenderSystem.setProjectionMatrix(worldProjection, VertexSorting.DISTANCE_TO_ORIGIN);

            BufferBuilder builder = beginLineBatch();
            PoseStack pose = new PoseStack();
            pose.mulPose(worldPose);
            appendBoxes(builder, pose, worldCamPos, blocks);
            BufferUploader.drawWithShader(builder.buildOrThrow());
            endLineBatch();
        } finally {
            // 投影 / modelview / 着色器颜色都是全局状态：漏还原会让整个 GUI 错位或变色
            RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
            RenderSystem.restoreProjectionMatrix();
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    /** 线框批次共用的状态：穿透（不做深度测试）+ 混合 + 不剔除背面。 */
    private static BufferBuilder beginLineBatch() {
        RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        // 穿透的来源：不做深度测试。线框渲染在最后，这里也不会被谁覆盖
        RenderSystem.disableDepthTest();
        // 只写颜色不写深度：不该让线框改动深度缓冲，影响排在它后面的手部 / GUI 渲染
        RenderSystem.depthMask(false);

        // 1.21 起 Tesselator 不再长期持有 Builder，改为每次 begin 现给一个
        return Tesselator.getInstance().begin(VertexFormat.Mode.LINES,
                DefaultVertexFormat.POSITION_COLOR_NORMAL);
    }

    /** 还原 {@link #beginLineBatch} 改过的状态。 */
    private static void endLineBatch() {
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /** 往当前批次里塞线框；{@code pose} 只带相机旋转，这里再平移 -相机坐标。 */
    private static void appendBoxes(BufferBuilder builder, PoseStack pose, Vec3 cam,
                                    List<OreVisionScanner.OreBlock> blocks) {
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
