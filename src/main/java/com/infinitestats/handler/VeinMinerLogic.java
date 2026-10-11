package com.infinitestats.handler;

import com.infinitestats.Config;
import com.infinitestats.stats.VeinMinerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * 连锁挖掘的「算哪些方块会被连锁」纯逻辑 —— 服务端真正执行时用它，
 * 客户端画高亮预览时也用同一套，保证看到的就是会挖掉的。
 * <p>
 * 只读世界（{@link LevelReader}），不带任何玩家副作用，所以两端都能调用。
 * 客户端的方块标签、服务端配置里的匹配标签都来自本地注册表 / 配置，
 * 多人环境下若服务端改过匹配标签而客户端没改，预览可能与实际差一点（可接受）。
 */
public final class VeinMinerLogic {

    private VeinMinerLogic() {}

    /** 6 邻域（面相邻的 6 个方向） */
    private static final int[][] FACE_OFFSETS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    /** 26 邻域（含斜向） */
    private static final int[][] DIAGONAL_OFFSETS = buildDiagonalOffsets();

    /** 方块 → 它所属的全部方块标签（标签在一次游戏运行内不会变，可以放心缓存） */
    private static final Map<Block, Set<TagKey<Block>>> BLOCK_TAG_CACHE = new HashMap<>();

    /** 名单里 {@code #tag} 项的解析缓存 */
    private static final Map<String, TagKey<Block>> TAG_KEY_CACHE = new HashMap<>();

    private static int[][] buildDiagonalOffsets() {
        List<int[]> list = new ArrayList<>(26);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dy != 0 || dz != 0) list.add(new int[]{dx, dy, dz});
                }
            }
        }
        return list.toArray(new int[0][]);
    }

    /** BFS 收集连锁目标（不含主方块本身），由近及远，最多 {@code effectiveMaxBlocks} 个。 */
    public static List<BlockPos> collectTargets(LevelReader level, VeinMinerConfig cfg,
            BlockPos origin, BlockState origState, ItemStack tool) {
        List<BlockPos> found = new ArrayList<>();
        int limit = effectiveMaxBlocks(cfg);
        if (limit <= 1) return found;

        List<Pattern> tagPatterns = compileTagPatterns();
        int radius = cfg.getMaxRadius();
        int[][] offsets = cfg.isDiagonal() ? DIAGONAL_OFFSETS : FACE_OFFSETS;
        float origHardness = origState.getDestroySpeed(level, origin);

        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin);
        visited.add(origin);

        while (!queue.isEmpty() && found.size() < limit) {
            BlockPos current = queue.poll();
            for (int[] off : offsets) {
                BlockPos next = current.offset(off[0], off[1], off[2]);
                if (!visited.add(next)) continue;
                if (radius > 0 && chebyshev(origin, next) > radius) continue;

                BlockState nextState = level.getBlockState(next);
                if (nextState.isAir()) continue;
                // 带方块实体的方块一律不碰（箱子 / 刷怪笼 / 机器等）
                if (level.getBlockEntity(next) != null) continue;
                if (!matches(cfg, origState, nextState, tagPatterns)) continue;
                // 与原版「能否掉落」同口径：不需要工具的方块（泥土 / 沙子 / 草方块…）空手也放行，
                // 只有「需要工具」的方块（石头 / 矿石）才看手上工具 ——
                // 直接问 ItemStack.EMPTY 会一律答 false，导致空手整片都连锁不了
                if (cfg.isRequireCorrectTool() && nextState.requiresCorrectToolForDrops()
                        && !tool.isCorrectToolForDrops(nextState)) continue;

                float hardness = nextState.getDestroySpeed(level, next);
                // 挖不动的、以及比原方块更硬的（如贴在矿脉上的黑曜石）跳过
                if (hardness < 0.0F || hardness > origHardness) continue;

                found.add(next);
                if (found.size() >= limit) break;
                queue.add(next);
            }
        }
        return found;
    }

    /**
     * 右键整片用：按同一套匹配策略收集「同类、且都能被同一个右键动作处理」的方块（不含原方块本身）。
     * <p>
     * 与 {@link #collectTargets} 的区别：不校验工具与硬度 —— 作物、耕地这类根本谈不上「正确工具」，
     * 改由调用方传 {@code extraFilter} 判断候选能不能被同一动作处理（比如「可剥皮」「可耕地」），
     * 这样也不会顺着矿脉连到已经处理过的方块上。
     */
    public static List<BlockPos> collectMatching(LevelReader level, VeinMinerConfig cfg,
            BlockPos origin, BlockState origState, int limit, Predicate<BlockState> extraFilter) {
        List<BlockPos> found = new ArrayList<>();
        if (limit <= 1) return found;

        List<Pattern> tagPatterns = compileTagPatterns();
        int radius = cfg.getMaxRadius();
        int[][] offsets = cfg.isDiagonal() ? DIAGONAL_OFFSETS : FACE_OFFSETS;

        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin);
        visited.add(origin);

        while (!queue.isEmpty() && found.size() < limit) {
            BlockPos current = queue.poll();
            for (int[] off : offsets) {
                BlockPos next = current.offset(off[0], off[1], off[2]);
                if (!visited.add(next)) continue;
                if (radius > 0 && chebyshev(origin, next) > radius) continue;

                BlockState nextState = level.getBlockState(next);
                if (nextState.isAir()) continue;
                if (level.getBlockEntity(next) != null) continue;
                if (!extraFilter.test(nextState)) continue;
                if (!matches(cfg, origState, nextState, tagPatterns)) continue;

                found.add(next);
                if (found.size() >= limit) break;
                queue.add(next);
            }
        }
        return found;
    }

    /** 本次连锁实际生效的方块数上限：个人设置优先，0 / 负数表示跟随服务端默认值。 */
    public static int effectiveMaxBlocks(VeinMinerConfig cfg) {
        int value = cfg.getMaxBlocks();
        if (value <= 0) value = Config.VEIN_MINER_MAX_BLOCKS.get();
        return Math.max(1, Math.min(value, VeinMinerConfig.MAX_BLOCKS_LIMIT));
    }

    /** 判断候选方块是否算「同类」。 */
    private static boolean matches(VeinMinerConfig cfg, BlockState orig, BlockState candidate,
            List<Pattern> tagPatterns) {
        if (orig.getBlock() == candidate.getBlock()) return true;
        return switch (cfg.getMatchMode()) {
            case SAME_BLOCK -> false;
            case TAG_GROUP -> sharesMatchTag(orig, candidate, tagPatterns);
            case FILTER_LIST -> matchesFilterList(cfg, candidate);
            case AUTO -> autoMatch(orig, candidate, tagPatterns);
        };
    }

    /**
     * 「自动」模式（对齐 FTB Ultimine 的三段判定）：
     * 原方块自身命中「算同一类」的标签 → 用标签组；否则若是作物 → 只认同一种作物；
     * 再否则只认同方块（方块不同已在外层拦掉，这里直接 false）。
     */
    private static boolean autoMatch(BlockState orig, BlockState candidate, List<Pattern> tagPatterns) {
        if (sharesMatchTag(orig, orig, tagPatterns)) {
            return sharesMatchTag(orig, candidate, tagPatterns);
        }
        if (looksLikeCrop(orig)) {
            return sameCrop(orig, candidate);
        }
        return false;
    }

    /**
     * 是否属于「作物类」方块。原版与多数模组作物都继承 {@link CropBlock}，
     * 另外把瓜藤、下界疣、甜浆果、可可、甘蔗、竹子一并算进来 —— 它们的共同点是「种下去会长」。
     */
    public static boolean looksLikeCrop(BlockState state) {
        Block block = state.getBlock();
        return block instanceof CropBlock
                || block instanceof StemBlock
                || block instanceof AttachedStemBlock
                || block instanceof NetherWartBlock
                || block instanceof SweetBerryBushBlock
                || block instanceof CocoaBlock
                || block instanceof SugarCaneBlock
                || block instanceof BambooStalkBlock;
    }

    /**
     * 两种方块是否属于「同一种作物」。原版里同一作物的不同生长阶段是同一个 Block
     * （生长程度落在 state 上），所以这里比方块本身即可；不同作物（小麦 vs 胡萝卜）不会被混为一谈。
     */
    public static boolean sameCrop(BlockState a, BlockState b) {
        return a.getBlock() == b.getBlock();
    }

    /** 两个方块是否共享任一「算同一类」的标签（如铁矿与深板岩铁矿共享 forge:ores/iron）。 */
    private static boolean sharesMatchTag(BlockState orig, BlockState candidate, List<Pattern> patterns) {
        Set<TagKey<Block>> origTags = tagsOf(orig);
        if (origTags.isEmpty()) return false;
        for (TagKey<Block> tag : tagsOf(candidate)) {
            if (origTags.contains(tag) && matchesAny(patterns, tag.location().toString())) return true;
        }
        return false;
    }

    /** 方块所属标签（按方块缓存，避免每个候选方块都遍历一遍标签注册表）。 */
    private static Set<TagKey<Block>> tagsOf(BlockState state) {
        return BLOCK_TAG_CACHE.computeIfAbsent(state.getBlock(), block -> {
            Set<TagKey<Block>> tags = new HashSet<>();
            for (TagKey<Block> tag : BuiltInRegistries.BLOCK.getTagNames().toList()) {
                if (state.is(tag)) tags.add(tag);
            }
            return tags;
        });
    }

    /**
     * 按自定义名单判断。名单为空时不做限制（白名单为空若理解为「什么都不连锁」，
     * 玩家清空列表就直接失去连锁能力，容易误操作）。
     */
    private static boolean matchesFilterList(VeinMinerConfig cfg, BlockState candidate) {
        Set<String> list = cfg.getFilterList();
        if (list.isEmpty()) return true;

        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(candidate.getBlock());
        String idStr = id == null ? "" : id.toString();

        boolean hit = false;
        for (String entry : list) {
            if (entry.startsWith("#")) {
                TagKey<Block> tag = TAG_KEY_CACHE.computeIfAbsent(entry, key -> {
                    ResourceLocation tagId = ResourceLocation.tryParse(key.substring(1));
                    return tagId == null ? null : TagKey.create(Registries.BLOCK, tagId);
                });
                if (tag != null && candidate.is(tag)) {
                    hit = true;
                    break;
                }
            } else if (entry.equals(idStr)) {
                hit = true;
                break;
            }
        }
        return cfg.isUseWhitelist() == hit;
    }

    /** 把配置里的标签通配串编译成正则（每次连锁编译一次，开销可忽略）。 */
    private static List<Pattern> compileTagPatterns() {
        List<Pattern> patterns = new ArrayList<>();
        List<? extends String> raw = Config.VEIN_MINER_MATCH_TAGS.get();
        if (raw == null) return patterns;
        for (String glob : raw) {
            if (glob == null || glob.isBlank()) continue;
            patterns.add(globToPattern(glob.trim()));
        }
        return patterns;
    }

    private static Pattern globToPattern(String glob) {
        StringBuilder sb = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> sb.append(".*");
                case '?' -> sb.append('.');
                case '.', '\\', '+', '(', ')', '[', ']', '{', '}', '^', '$', '|' -> sb.append('\\').append(c);
                default -> sb.append(c);
            }
        }
        sb.append('$');
        return Pattern.compile(sb.toString());
    }

    private static boolean matchesAny(List<Pattern> patterns, String value) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(value).matches()) return true;
        }
        return false;
    }

    /** 切比雪夫距离：26 邻域下「走了几层」，用来当搜索半径。 */
    private static int chebyshev(BlockPos a, BlockPos b) {
        return Math.max(Math.abs(a.getX() - b.getX()),
                Math.max(Math.abs(a.getY() - b.getY()), Math.abs(a.getZ() - b.getZ())));
    }
}
