package com.infinitestats.client;

import com.infinitestats.stats.OreVisionConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraftforge.common.Tags;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 「矿透」的本地扫描器 —— 在客户端<b>已加载</b>的区块里找出玩家周围的矿石。
 * <p>
 * 只在客户端做：服务端不会收到任何方块查询，也就不会因为「谁在透视」多出额外负载；
 * 代价是只能看到客户端本来就有的数据（未加载区块不会凭空出现矿石，这与原版视野一致）。
 * <p>
 * 性能上有两道拦截，避免每次重扫都把整片区域逐格读一遍：
 * <ol>
 *   <li>只遍历与玩家距离在半径内的区块 / section；</li>
 *   <li>先用 {@code PalettedContainer#maybeHas} 看该 section 的调色板里<b>有没有</b>矿石，
 *       没有就整块跳过 —— 地下绝大多数 section 都是纯石头，这一条能省掉绝大部分开销。</li>
 * </ol>
 */
final class OreVisionScanner {

    /** 一格矿石：位置 + 分组（决定线框颜色）。 */
    record OreBlock(BlockPos pos, OreVisionConfig.OreGroup group) {}

    private OreVisionScanner() {}

    /**
     * 以 {@code center} 为中心、按 {@code cfg} 的半径扫一遍，返回需要描边的矿石。
     * <p>
     * 结果数量受 {@code cfg.maxBlocks} 限制：成片矿区（尤其是多人服务器里的刷石机废料区）
     * 不设上限会把顶点数撑到几十万，帧率直接崩掉。
     */
    static List<OreBlock> scan(ClientLevel level, BlockPos center, OreVisionConfig cfg) {
        List<OreBlock> found = new ArrayList<>();
        int range = cfg.getRange();
        int maxBlocks = cfg.getMaxBlocks();

        int minCX = (center.getX() - range) >> 4;
        int maxCX = (center.getX() + range) >> 4;
        int minCZ = (center.getZ() - range) >> 4;
        int maxCZ = (center.getZ() + range) >> 4;
        int minSY = Math.max(level.getMinBuildHeight() >> 4, (center.getY() - range) >> 4);
        int maxSY = Math.min((level.getMaxBuildHeight() - 1) >> 4, (center.getY() + range) >> 4);

        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null || chunk.isEmpty()) continue;

                LevelChunkSection[] sections = chunk.getSections();
                for (int sy = minSY; sy <= maxSY; sy++) {
                    int index = level.getSectionIndexFromSectionY(sy);
                    if (index < 0 || index >= sections.length) continue;
                    LevelChunkSection section = sections[index];
                    if (section == null || section.hasOnlyAir()) continue;

                    PalettedContainer<BlockState> states = section.getStates();
                    if (!states.maybeHas(OreVisionScanner::isOre)) continue;

                    if (collectSection(states, cx, sy, cz, center, range, cfg, found, maxBlocks)) {
                        return found;
                    }
                }
            }
        }
        return found;
    }

    /**
     * 遍历一个 16×16×16 的 section。
     *
     * @return true 表示已经攒够 {@code maxBlocks}，外层可以立刻收工
     */
    private static boolean collectSection(PalettedContainer<BlockState> states, int cx, int sy, int cz,
                                          BlockPos center, int range, OreVisionConfig cfg,
                                          List<OreBlock> found, int maxBlocks) {
        int baseX = cx << 4;
        int baseY = sy << 4;
        int baseZ = cz << 4;
        int centerX = center.getX();
        int centerY = center.getY();
        int centerZ = center.getZ();

        for (int y = 0; y < 16; y++) {
            int wy = baseY + y;
            if (Math.abs(wy - centerY) > range) continue;
            for (int z = 0; z < 16; z++) {
                int wz = baseZ + z;
                if (Math.abs(wz - centerZ) > range) continue;
                for (int x = 0; x < 16; x++) {
                    int wx = baseX + x;
                    if (Math.abs(wx - centerX) > range) continue;

                    BlockState state = states.get(x, y, z);
                    if (!isOre(state)) continue;

                    OreVisionConfig.OreGroup group = groupOf(state);
                    if (cfg.isGroupHidden(group)) continue;

                    found.add(new OreBlock(new BlockPos(wx, wy, wz), group));
                    if (found.size() >= maxBlocks) return true;
                }
            }
        }
        return false;
    }

    /**
     * 是不是矿石。
     * <p>
     * 先认 Forge 的 {@code forge:ores} 标签（模组矿石基本都会把它打上），
     * 再用命名兜底：远古残骸没有 {@code _ore} 后缀，而不少模组矿石又没有打标签。
     */
    static boolean isOre(BlockState state) {
        if (state.isAir()) return false;
        if (state.is(Tags.Blocks.ORES)) return true;

        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (id == null) return false;
        String path = id.getPath().toLowerCase(Locale.ROOT);
        return path.equals("ancient_debris") || path.endsWith("_ore") || path.contains("_ore_");
    }

    /** 按方块注册名归类（决定颜色，也决定玩家能不能在面板里把它关掉）。 */
    static OreVisionConfig.OreGroup groupOf(BlockState state) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        String path = id == null ? "" : id.getPath().toLowerCase(Locale.ROOT);

        if (path.contains("debris")) return OreVisionConfig.OreGroup.DEBRIS;
        if (path.contains("quartz")) return OreVisionConfig.OreGroup.QUARTZ;
        if (path.contains("diamond")) return OreVisionConfig.OreGroup.DIAMOND;
        if (path.contains("emerald")) return OreVisionConfig.OreGroup.EMERALD;
        if (path.contains("lapis")) return OreVisionConfig.OreGroup.LAPIS;
        if (path.contains("redstone")) return OreVisionConfig.OreGroup.REDSTONE;
        if (path.contains("copper")) return OreVisionConfig.OreGroup.COPPER;
        if (path.contains("gold")) return OreVisionConfig.OreGroup.GOLD;
        if (path.contains("iron")) return OreVisionConfig.OreGroup.IRON;
        if (path.contains("coal")) return OreVisionConfig.OreGroup.COAL;
        return OreVisionConfig.OreGroup.OTHER;
    }
}
