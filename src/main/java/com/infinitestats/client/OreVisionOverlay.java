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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
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
 * 顶点也直接落在主画面上（不经过实体专用的离屏 target），不存在「画完却等不到合成」的时序问题 ——
 * 前提是没人在这之后再把整屏重画一遍，而光影恰好会（见下方「光影兼容」）。
 * <p>
 * 绘制阶段固定在 {@code AFTER_TRANSLUCENT_BLOCKS}：这一档的 {@code event.getPoseStack()} 还带着
 * 相机旋转，{@code translate(-相机位置)} 之后坐标才落在正确方块上（更晚的 {@code AFTER_LEVEL}
 * 已经被原版 {@code popPose()} 弹掉了相机变换，画出来是错位的）。穿透与阶段无关 ——
 * 那是 {@code disableDepthTest()} 的功劳。
 * <p>
 * 显示条件：已解锁且未关闭 {@code ore_vision} 属性；若开了「需潜行激活」则只在潜行时显示。
 * 颜色按矿物分组走（煤灰 / 铁棕 / 金 / 红石红 / 钻石青 …），一眼能分辨是什么矿。
 * <p>
 * <b>光影兼容</b>：上面这套「在世界阶段画、把深度测试关掉」的写法，在开了光影时会整片消失 ——
 * 光影把世界画进自己的缓冲，{@code LevelRenderer#renderLevel} 收尾时再用 final pass 整屏合成，
 * 中途画进主画面的东西全被覆盖（{@code AFTER_LEVEL} 也一样被覆盖，换阶段没用）。
 * 所以开着光影时改走 {@link #onRenderGui}：在同一帧的 HUD 阶段把「世界投影 + 相机姿态」
 * 临时装回渲染管线，用同一套线框再画一遍（合成已经结束，谁也覆盖不掉），画完原样还原。
 * 判断光影是否真的启用交给 {@link ShaderCompat}；没开光影时仍走原来那条路，行为零变化。
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

    // ======================== 光影兜底用的世界变换快照 ========================

    /**
     * 世界渲染用的透视投影矩阵（每帧从 {@link RenderLevelStageEvent#getProjectionMatrix()} 抄一份）。
     * <p>
     * HUD 阶段管线里挂的是 GUI 的正交投影，要画世界坐标就得把这一份换回去；
     * 现算的话得拿到 FOV（{@code GameRenderer#getFov} 是私有的，而且冲刺 / 受伤 / 水下都会改它），
     * 抄现成的既准确又便宜。
     */
    private static final Matrix4f worldProjection = new Matrix4f();
    /** 世界渲染那一份 pose（相机旋转等，不含平移）—— 照抄下来，眼位抖动、翻滚才能对得上。 */
    private static final Matrix4f worldPose = new Matrix4f();
    /** 相机世界坐标。 */
    private static Vec3 worldCamPos = Vec3.ZERO;
    /** 快照取自哪一帧；HUD 阶段靠它判断「这一帧世界到底画过没有」。 */
    private static long worldSnapshotNs = Long.MIN_VALUE;
    /**
     * 这一帧世界阶段是否真的「该显示」（属性开着、且潜行要求满足）。
     * <p>
     * 潜行要求在世界阶段只是「不画、缓存留着」，HUD 阶段若只看缓存非空，没潜行时也照样描边，
     * 所以单独记一笔，让两条渲染路径用同一个可见性判断。
     */
    private static boolean worldPassVisible;
    /**
     * 这一帧是否在跑光影 —— 每帧只问一次（世界阶段问），两条渲染路径共用同一个答案，
     * 免得出现「世界阶段判定有光影、HUD 阶段判定没有」而两边都没画的情况。
     */
    private static boolean shadersActive;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        // AFTER_LEVEL 这一档的 pose stack 已经被原版 popPose() 弹掉了相机变换，抄下来是错的；
        // 其余档都还带着相机变换。快照不限定在某一档，是为了让 HUD 阶段总有一份当帧的矩阵 ——
        // 光影环境下万一某个档位被跳过，也不至于整片不显示。
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) return;

        shadersActive = ShaderCompat.areShadersActive();
        // 每帧都抄一份世界变换：即便这一帧不画（潜行要求没满足 / 光影接管了绘制），
        // HUD 阶段也要用同一帧的矩阵，抄的时机必须早于任何提前返回
        snapshotWorldTransform(event);

        // 绘制仍然固定在 AFTER_TRANSLUCENT_BLOCKS：
        // 更晚的 AFTER_LEVEL 已经没有相机变换，那时再 translate(-相机位置) 坐标会整体错位。
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        worldPassVisible = false;

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
     * 时序上是「世界画完 → 光影 final pass 合成 → 清深度 → 画 GUI」，所以在这里画出来的线框
     * 才是真正留在屏幕上的那一份。坐标不用重算：把世界渲染那一份投影矩阵与相机 pose 装回去，
     * 顶点走的还是同一个 {@code renderLineBox}，位置与不开光影时逐像素一致。
     * <p>
     * 注意两点，否则线会跑到别处去或者被裁掉：
     * <ol>
     *   <li>HUD 阶段管线里的 modelview 是 {@code translate(0, 0, -11000)}（配合 GUI 的正交投影），
     *       这里必须复位成单位阵 —— 世界渲染同样是「顶点在 CPU 侧变换、GPU 侧 modelview 为单位阵」，
     *       这样 GPU 只会再乘一次投影；</li>
     *   <li>GUI 渲染前原版清过一次深度缓冲，所以深度测试对我们没有意义，直接关掉（穿透效果的来源）。</li>
     * </ol>
     */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Pre event) {
        if (!worldPassVisible || cached.isEmpty()) return;
        if (worldSnapshotNs == Long.MIN_VALUE) return;
        // 这一帧世界根本没画（暂停 / 只重画 GUI）时快照是陈的，此时按旧视角画会错位。
        // 用时间差而不是严格相等：帧内顺序由原版保证，这里只是宽容一点，避免因时序细节整片不显示
        if (Minecraft.getInstance().getFrameTimeNs() - worldSnapshotNs > 50_000_000L) return;
        if (!shadersActive) return;

        renderThroughGui(cached);
    }

    /** 抄下这一帧世界渲染用的投影矩阵 / 相机 pose / 相机坐标。 */
    private static void snapshotWorldTransform(RenderLevelStageEvent event) {
        worldProjection.set(event.getProjectionMatrix());
        worldPose.set(event.getPoseStack().last().pose());
        worldCamPos = event.getCamera().getPosition();
        worldSnapshotNs = Minecraft.getInstance().getFrameTimeNs();
    }

    private static boolean needsRescan(ClientLevel level, BlockPos center, OreVisionConfig cfg) {
        if (cachedCenter == null) return true;
        if (!level.dimension().equals(cachedDimension)) return true;
        if (configHash(cfg) != cachedHash) return true;
        if (center.distSqr(cachedCenter) > (double) (MOVE_RESCAN * MOVE_RESCAN)) return true;
        return level.getGameTime() - lastScanAt >= IDLE_RESCAN_TICKS;
    }

    /**
     * 世界阶段的绘制（不开光影时走这里）。
     * <p>
     * 关键在深度状态是<b>我们自己</b>设的：{@code disableDepthTest()} 之后没有任何
     * {@code RenderType.setupRenderState()} 会把它覆盖回去，所以边框一定画在最上层。
     */
    private static void render(RenderLevelStageEvent event, List<OreVisionScanner.OreBlock> blocks) {
        // 先把共享批次刷干净：下面要借 Tesselator 那个 BufferBuilder 手动画，
        // 若 BufferSource 里还挂着没提交的顶点，两边会抢同一个 builder
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        buffers.endBatch();

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();

        beginLineBatch(builder);
        appendBoxes(builder, event.getPoseStack(), event.getCamera().getPosition(), blocks);
        BufferUploader.drawWithShader(builder.end());
        endLineBatch();
    }

    /** 光影下的绘制（在 HUD 阶段调用，理由见 {@link #onRenderGui}）。 */
    private static void renderThroughGui(List<OreVisionScanner.OreBlock> blocks) {
        // 先把共享批次刷干净：Tesselator 的 builder 就是 GuiGraphics 在用的那一个，
        // 此时它可能还挂着屏幕（背包 / 聊天）没提交的顶点，直接 begin() 会把它们丢掉
        Minecraft.getInstance().renderBuffers().bufferSource().endBatch();

        PoseStack modelView = RenderSystem.getModelViewStack();
        // setShaderColor 是全局状态，HUD 阶段可能挂着别的颜色，用完要还
        float[] shaderColor = RenderSystem.getShaderColor().clone();

        modelView.pushPose();
        modelView.setIdentity();
        RenderSystem.applyModelViewMatrix();
        // 投影换成世界那一份，画完原样还回去（连 VertexSorting 一起，GUI 的元素排序不能被打乱）
        RenderSystem.backupProjectionMatrix();
        try {
            RenderSystem.setProjectionMatrix(worldProjection, VertexSorting.DISTANCE_TO_ORIGIN);

            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder builder = tesselator.getBuilder();

            beginLineBatch(builder);
            PoseStack pose = new PoseStack();
            // 从世界渲染那一份 pose 出发（相机旋转 / 翻滚 / 眼位抖动都在里面），再平移到相机原点
            pose.mulPoseMatrix(worldPose);
            appendBoxes(builder, pose, worldCamPos, blocks);
            BufferUploader.drawWithShader(builder.end());
            endLineBatch();
        } finally {
            // 投影 / modelview / 着色器颜色都是全局状态：漏还原会让整个 GUI 错位或变色，
            // 所以哪怕画到一半出问题也要收尾
            RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
            RenderSystem.restoreProjectionMatrix();
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
        }
    }

    /** 线框批次共用的状态：穿透（不做深度测试）+ 混合 + 不剔除背面。 */
    private static void beginLineBatch(BufferBuilder builder) {
        RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        // 穿透的来源：不做深度测试。线框渲染在最后，这里也不会被谁覆盖
        RenderSystem.disableDepthTest();
        // 只写颜色不写深度：不该让线框改动深度缓冲，影响排在它后面的手部 / GUI 渲染
        RenderSystem.depthMask(false);

        builder.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
    }

    /** 还原 {@link #beginLineBatch} 改过的状态，别影响排在后面的渲染。 */
    private static void endLineBatch() {
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /**
     * 往当前批次里塞线框。
     * <p>
     * {@code pose} 只带相机旋转，这里再平移 -相机坐标，顶点就落在相机空间 —— 与不开光影时
     * 世界阶段那一份变换完全一致。每个方块用「六个坐标」的重载而不是 {@code new AABB}：
     * 一帧几千个方块，省下的是实打实的 GC 压力。
     */
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
