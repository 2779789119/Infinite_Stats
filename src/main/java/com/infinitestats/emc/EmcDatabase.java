package com.infinitestats.emc;

import com.google.gson.*;
import com.infinitestats.Config;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * EMC 数据库 — 参照 ProjectE SimplifiedGraphMapper 算法设计
 *
 * 核心原理（与 ProjectE 一致）：
 * 1. 将所有配方视为有向图：原料 → 产物
 * 2. Bellman-Ford 式迭代，每次取最小 EMC
 * 3. 向下取整（floor）防止 "3圆石→6台阶→6 EMC" 复制漏洞
 * 4. 0-EMC 物品不参与配方计算（与 ProjectE 一致）
 * 5. 循环检测：发现 A→B→A 净正值时置 0
 * 6. setValueBefore（MANUAL_EMC）不可被覆盖
 * 7. setValueAfter 在计算后强制覆盖
 *
 * 计算流程：
 * ┌─────────────────────────────────────────────────────┐
 * │ 1. 加载 emc_values.json 手动指定值（基础物品锚点）    │
 * │ 2. 遍历所有配方 N 轮，迭代至收敛                      │
 * │ 3. 循环漏洞检测                                       │
 * │ 4. 应用黑名单（原矿/矿石块 EMC=0）                    │
 * │ 5. 导出调试数据 emc_calculated.json                  │
 * │                                                      │
 * │ ProjectE 安装时：直接调用 IEMCProxy（完全互通）        │
 * └─────────────────────────────────────────────────────┘
 */
public final class EmcDatabase {

    private static final String CONFIG_FILENAME = "emc_values.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 手动指定的 EMC 值（setValueBefore，不可被自动计算覆盖） */
    private static final Map<ResourceLocation, Long> MANUAL_EMC = new LinkedHashMap<>();

    /** 计算后强制覆盖的值（setValueAfter） */
    private static final Map<ResourceLocation, Long> AFTER_OVERRIDE_EMC = new LinkedHashMap<>();

    /** 自动计算出的 EMC 值（含手动值） */
    private static final Map<ResourceLocation, Long> EMC_MAP = new LinkedHashMap<>();

    /** 运行时通过定价器自定义的 EMC 值（优先级最高，覆盖所有其他来源） */
    private static final Map<ResourceLocation, Long> CUSTOM_EMC = new LinkedHashMap<>();

    /** ProjectE 反射 */
    private static Object projecteProxy;
    private static Method projecteGetValueMethod;
    private static boolean projecteLoaded = false;

    /** 是否已完成计算 */
    private static boolean calculated = false;

    // ==================== 加载入口 ====================

    /**
     * 服务端启动时调用
     * @param server MinecraftServer 实例（用于获取 RecipeManager）
     */
    public static void load(MinecraftServer server) {
        CUSTOM_EMC.clear();
        loadCustomPrices();
        EMC_MAP.clear();
        MANUAL_EMC.clear();
        AFTER_OVERRIDE_EMC.clear();

        // 尝试接入 ProjectE
        tryInitProjectE();

        Path configDir = FMLPaths.CONFIGDIR.get().resolve("infinitestats");

        if (projecteLoaded) {
            System.out.println("[InfiniteStats-EMC] Connected to ProjectE API");
            calculated = true;
            return;
        }

        // === 无 ProjectE：自动计算 ===

        // 1. 加载手动值
        loadManualValues(configDir);

        // 2. 自动计算
        RecipeManager rm = server.getRecipeManager();
        autoCalculate(rm, server.registryAccess());

        // 3. 保存计算结果供查看
        saveCalculated(configDir);

        System.out.println("[InfiniteStats-EMC] Auto-calculated " + EMC_MAP.size()
                + " EMC values from " + (MANUAL_EMC.size()) + " base items");
        calculated = true;
    }

    // ==================== ProjectE 反射 ====================

    private static void tryInitProjectE() {
        try {
            if (!ModList.get().isLoaded("projecte")) return;
            Class<?> apiClass = Class.forName("moze_intel.projecte.api.ProjectEAPI");
            projecteProxy = apiClass.getMethod("getEMCProxy").invoke(null);
            projecteGetValueMethod = projecteProxy.getClass().getMethod("getValue", ItemStack.class);
            projecteLoaded = true;
        } catch (Exception ignored) {
            System.out.println("[InfiniteStats-EMC] ProjectE detected but API unavailable, using auto-calc");
        }
    }

    // ==================== 加载手动值 ====================

    private static void loadManualValues(Path configDir) {
        Path configFile = configDir.resolve(CONFIG_FILENAME);

        if (!Files.exists(configFile)) {
            // 首次运行：写入内置基础锚点
            writeBuiltinBootstrap(configFile);
        }

        // 读取 JSON
        try (Reader reader = Files.newBufferedReader(configFile)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) return;
            JsonArray items = root.getAsJsonArray("items");
            if (items == null) return;

            for (JsonElement elem : items) {
                JsonObject obj = elem.getAsJsonObject();
                String id = obj.get("id").getAsString();
                long value = obj.get("emc").getAsLong();
                ResourceLocation rl = ResourceLocation.tryParse(id);
                if (rl != null && value > 0) {
                    // 支持 "after" 字段：true = setValueAfter（计算后强制覆盖）
                    boolean after = obj.has("after") && obj.get("after").getAsBoolean();
                    if (after) {
                        AFTER_OVERRIDE_EMC.put(rl, value);
                    } else {
                        MANUAL_EMC.put(rl, value);
                        EMC_MAP.put(rl, value);
                    }
                }
            }
            System.out.println("[InfiniteStats-EMC] Loaded " + MANUAL_EMC.size()
                    + " base EMC values + " + AFTER_OVERRIDE_EMC.size() + " after-overrides");
        } catch (Exception e) {
            System.err.println("[InfiniteStats-EMC] Failed to load emc_values.json: " + e.getMessage());
        }
    }

    /**
     * 首次运行时生成基础锚点文件（只有从世界直接获取的物品需要手动指定 EMC）
     * 设计原则：只给"无任何合成/烧炼配方、只能从世界采集/击杀/宝箱获取的物品"设锚点。
     * 所有能通过配方推导的物品交给自动计算引擎。
     */
    private static void writeBuiltinBootstrap(Path configFile) {
        Map<String, Long> bootstrap = new LinkedHashMap<>();

        // ================================================================
        // === 世界自然生成 — 原木（10 种） ===
        // ================================================================
        bootstrap.put("minecraft:oak_log", 32L);
        bootstrap.put("minecraft:spruce_log", 32L);
        bootstrap.put("minecraft:birch_log", 32L);
        bootstrap.put("minecraft:jungle_log", 32L);
        bootstrap.put("minecraft:acacia_log", 32L);
        bootstrap.put("minecraft:dark_oak_log", 32L);
        bootstrap.put("minecraft:mangrove_log", 32L);
        bootstrap.put("minecraft:cherry_log", 32L);
        bootstrap.put("minecraft:crimson_stem", 32L);
        bootstrap.put("minecraft:warped_stem", 32L);

        // ================================================================
        // === 世界自然生成 — 基础方块（7 种） ===
        // ================================================================
        bootstrap.put("minecraft:cobblestone", 1L);       // ⭐ 最关键的缺失锚点——解锁所有石制物品
        bootstrap.put("minecraft:dirt", 1L);
        bootstrap.put("minecraft:gravel", 4L);
        bootstrap.put("minecraft:sand", 1L);
        bootstrap.put("minecraft:red_sand", 1L);
        bootstrap.put("minecraft:sandstone", 4L);          // 自然生成在沙漠，也可合成
        bootstrap.put("minecraft:clay_ball", 16L);
        bootstrap.put("minecraft:obsidian", 64L);
        bootstrap.put("minecraft:crying_obsidian", 128L);  // 废弃传送门/猪灵交易
        bootstrap.put("minecraft:netherrack", 1L);
        bootstrap.put("minecraft:end_stone", 2L);

        // ================================================================
        // === 寒冷/水域方块（5 种） ===
        // ================================================================
        bootstrap.put("minecraft:ice", 1L);
        bootstrap.put("minecraft:snowball", 1L);
        bootstrap.put("minecraft:prismarine_shard", 64L);
        bootstrap.put("minecraft:prismarine_crystals", 128L);
        bootstrap.put("minecraft:sponge", 256L);

        // ================================================================
        // === 种植/养殖 — 作物（12 种） ===
        // ================================================================
        bootstrap.put("minecraft:wheat", 24L);
        bootstrap.put("minecraft:wheat_seeds", 4L);
        bootstrap.put("minecraft:carrot", 64L);
        bootstrap.put("minecraft:potato", 64L);
        bootstrap.put("minecraft:beetroot", 64L);
        bootstrap.put("minecraft:beetroot_seeds", 4L);
        bootstrap.put("minecraft:sugar_cane", 32L);
        bootstrap.put("minecraft:cactus", 8L);
        bootstrap.put("minecraft:bamboo", 4L);
        bootstrap.put("minecraft:kelp", 1L);
        bootstrap.put("minecraft:pumpkin", 72L);           // 自然生成/可种植，用于傀儡/南瓜派
        bootstrap.put("minecraft:melon_slice", 16L);       // 自然生成/可种植
        bootstrap.put("minecraft:cocoa_beans", 32L);       // 丛林专属
        bootstrap.put("minecraft:sweet_berries", 16L);
        bootstrap.put("minecraft:glow_berries", 32L);

        // ================================================================
        // === 种植/养殖 — 树木/花朵（20 种） ===
        // ================================================================
        bootstrap.put("minecraft:apple", 32L);             // 橡树树叶掉落
        bootstrap.put("minecraft:brown_mushroom", 32L);
        bootstrap.put("minecraft:red_mushroom", 32L);
        bootstrap.put("minecraft:dandelion", 8L);
        bootstrap.put("minecraft:poppy", 8L);
        bootstrap.put("minecraft:blue_orchid", 8L);
        bootstrap.put("minecraft:allium", 8L);
        bootstrap.put("minecraft:azure_bluet", 8L);
        bootstrap.put("minecraft:red_tulip", 8L);
        bootstrap.put("minecraft:orange_tulip", 8L);
        bootstrap.put("minecraft:white_tulip", 8L);
        bootstrap.put("minecraft:pink_tulip", 8L);
        bootstrap.put("minecraft:oxeye_daisy", 8L);
        bootstrap.put("minecraft:cornflower", 8L);
        bootstrap.put("minecraft:lily_of_the_valley", 8L);
        bootstrap.put("minecraft:sunflower", 8L);
        bootstrap.put("minecraft:lilac", 8L);
        bootstrap.put("minecraft:rose_bush", 8L);
        bootstrap.put("minecraft:peony", 8L);
        bootstrap.put("minecraft:wither_rose", 256L);      // 凋灵击杀掉落，稀有
        bootstrap.put("minecraft:torchflower", 8L);
        bootstrap.put("minecraft:pitcher_plant", 8L);
        bootstrap.put("minecraft:pitcher_pod", 8L);
        bootstrap.put("minecraft:torchflower_seeds", 8L);
        bootstrap.put("minecraft:pumpkin_seeds", 8L);
        bootstrap.put("minecraft:melon_seeds", 8L);
        bootstrap.put("minecraft:vine", 8L);
        bootstrap.put("minecraft:lily_pad", 16L);
        bootstrap.put("minecraft:sea_pickle", 16L);

        // ================================================================
        // === 动物掉落（8 种） ===
        // ================================================================
        bootstrap.put("minecraft:egg", 32L);
        bootstrap.put("minecraft:leather", 64L);
        bootstrap.put("minecraft:feather", 48L);
        bootstrap.put("minecraft:rabbit_foot", 1024L);
        bootstrap.put("minecraft:rabbit_hide", 16L);
        bootstrap.put("minecraft:ink_sac", 64L);
        bootstrap.put("minecraft:glow_ink_sac", 256L);
        bootstrap.put("minecraft:honeycomb", 64L);
        bootstrap.put("minecraft:scute", 64L);
        bootstrap.put("minecraft:turtle_egg", 64L);
        bootstrap.put("minecraft:goat_horn", 256L);

        // ================================================================
        // === 怪物掉落（19 种） ===
        // ================================================================
        bootstrap.put("minecraft:rotten_flesh", 32L);
        bootstrap.put("minecraft:bone", 48L);
        bootstrap.put("minecraft:string", 12L);
        bootstrap.put("minecraft:spider_eye", 128L);
        bootstrap.put("minecraft:gunpowder", 192L);
        bootstrap.put("minecraft:slime_ball", 64L);
        bootstrap.put("minecraft:ender_pearl", 1024L);
        bootstrap.put("minecraft:blaze_rod", 1536L);
        bootstrap.put("minecraft:ghast_tear", 4096L);
        bootstrap.put("minecraft:phantom_membrane", 256L);
        bootstrap.put("minecraft:shulker_shell", 4096L);
        bootstrap.put("minecraft:wither_skeleton_skull", 8192L);
        bootstrap.put("minecraft:nether_star", 139264L);
        bootstrap.put("minecraft:dragon_egg", 262144L);
        bootstrap.put("minecraft:flint", 32L);
        bootstrap.put("minecraft:magma_cream", 832L);      // 岩浆怪掉落 / 合成
        bootstrap.put("minecraft:glowstone_dust", 128L);
        bootstrap.put("minecraft:nether_wart", 64L);
        bootstrap.put("minecraft:chorus_fruit", 64L);

        // ================================================================
        // === 矿石烧炼产物（9 种锚点） ===
        // ================================================================
        bootstrap.put("minecraft:coal", 128L);
        bootstrap.put("minecraft:iron_ingot", 256L);
        bootstrap.put("minecraft:gold_ingot", 2048L);
        bootstrap.put("minecraft:copper_ingot", 128L);
        bootstrap.put("minecraft:diamond", 8192L);
        bootstrap.put("minecraft:emerald", 16384L);
        bootstrap.put("minecraft:lapis_lazuli", 768L);
        bootstrap.put("minecraft:redstone", 64L);
        bootstrap.put("minecraft:quartz", 256L);
        bootstrap.put("minecraft:netherite_scrap", 12288L);
        bootstrap.put("minecraft:amethyst_shard", 64L);

        // ================================================================
        // === 遗迹/宝箱/钓鱼专属物品（无任何配方，18 种） ===
        // ================================================================
        bootstrap.put("minecraft:experience_bottle", 384L);
        bootstrap.put("minecraft:saddle", 4096L);
        bootstrap.put("minecraft:name_tag", 2048L);
        bootstrap.put("minecraft:iron_horse_armor", 2048L);
        bootstrap.put("minecraft:golden_horse_armor", 8192L);
        bootstrap.put("minecraft:diamond_horse_armor", 32768L);
        bootstrap.put("minecraft:totem_of_undying", 65536L);
        bootstrap.put("minecraft:trident", 8192L);
        bootstrap.put("minecraft:elytra", 131072L);
        bootstrap.put("minecraft:heart_of_the_sea", 32768L);
        bootstrap.put("minecraft:nautilus_shell", 1024L);
        bootstrap.put("minecraft:echo_shard", 4096L);
        bootstrap.put("minecraft:music_disc_13", 2048L);
        bootstrap.put("minecraft:music_disc_cat", 2048L);
        bootstrap.put("minecraft:music_disc_pigstep", 4096L);
        bootstrap.put("minecraft:music_disc_otherside", 4096L);
        bootstrap.put("minecraft:music_disc_5", 4096L);
        bootstrap.put("minecraft:disc_fragment_5", 1024L);
        // 注意：netherite_upgrade_smithing_template 不设 EMC——它在锻造台可复用，
        // calcSmithingEmc() 会跳过无 EMC 原料，下界合金装备 = 钻石装备 + 下界合金锭

        try {
            Files.createDirectories(configFile.getParent());
            JsonObject root = new JsonObject();
            JsonArray items = new JsonArray();
            for (var entry : bootstrap.entrySet()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("id", entry.getKey());
                obj.addProperty("emc", entry.getValue());
                items.add(obj);
            }
            root.add("items", items);
            try (Writer w = Files.newBufferedWriter(configFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                GSON.toJson(root, w);
            }
            System.out.println("[InfiniteStats-EMC] Generated bootstrap config with " + bootstrap.size() + " base items");
        } catch (IOException e) {
            System.err.println("[InfiniteStats-EMC] Failed to write bootstrap: " + e.getMessage());
        }

        // 写入内存
        for (var entry : bootstrap.entrySet()) {
            ResourceLocation rl = ResourceLocation.tryParse(entry.getKey());
            if (rl != null) {
                MANUAL_EMC.put(rl, entry.getValue());
                EMC_MAP.put(rl, entry.getValue());
            }
        }
    }

    // ==================== 自动计算核心 ====================

    /**
     * 参照 ProjectE SimpleGraphMapper.generateValues() 的 Bellman-Ford 迭代算法
     *
     * 关键设计决策（与 ProjectE 保持一致）：
     * - 向下取整（floor）：防止 "3圆石→6台阶→6EMC>3EMC输入" 漏洞
     * - 0-EMC 物品不参与配方计算（getIngredientEmc 返回 0 → calcCraftingEmc 返回 0 → 本轮跳过）
     * - 锻造台模板无 EMC → calcSmithingEmc 跳过模板，只计算消耗品
     * - 收敛后检查循环漏洞
     */
    private static void autoCalculate(RecipeManager rm, net.minecraft.core.RegistryAccess registryAccess) {
        int maxIterations = 30;
        boolean changed = true;
        int iter = 0;

        // 阶段一：Bellman-Ford 迭代——每轮对所有配方重新计算，取最小 EMC
        // 直到没有任何物品的 EMC 值发生变化
        while (changed && iter < maxIterations) {
            changed = false;
            iter++;

            for (Recipe<?> recipe : rm.getRecipes()) {
                try {
                    if (recipe.isSpecial()) continue;

                    ItemStack result = recipe.getResultItem(registryAccess);
                    if (result.isEmpty()) continue;

                    ResourceLocation outId = BuiltInRegistries.ITEM.getKey(result.getItem());
                    long candidateEmc = 0;

                    if (recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe) {
                        candidateEmc = calcCraftingEmc(recipe.getIngredients(), result.getCount());
                    } else if (recipe instanceof SmeltingRecipe || recipe instanceof BlastingRecipe
                            || recipe instanceof SmokingRecipe || recipe instanceof CampfireCookingRecipe) {
                        candidateEmc = calcSingleInputEmc(recipe.getIngredients(), result.getCount());
                    } else if (recipe instanceof SmithingTransformRecipe) {
                        // 锻造台：跳过无 EMC 的模板（可复用），只计算消耗品
                        candidateEmc = calcSmithingEmc(recipe.getIngredients());
                    } else if (recipe instanceof SmithingTrimRecipe) {
                        continue; // 纯装饰
                    } else if (recipe instanceof StonecutterRecipe) {
                        candidateEmc = calcSingleInputEmc(recipe.getIngredients(), result.getCount());
                    } else {
                        candidateEmc = calcCraftingEmc(recipe.getIngredients(), result.getCount());
                    }

                    if (candidateEmc > 0) {
                        // 不受 setValueBefore（MANUAL_EMC）覆盖
                        if (!MANUAL_EMC.containsKey(outId)) {
                            Long current = EMC_MAP.get(outId);
                            if (current == null || candidateEmc < current) {
                                EMC_MAP.put(outId, candidateEmc);
                                changed = true;
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            int calcCount = EMC_MAP.size() - MANUAL_EMC.size();
            System.out.println("[InfiniteStats-EMC] Iteration " + iter
                    + ": " + EMC_MAP.size() + " items total ("
                    + calcCount + " calculated, " + MANUAL_EMC.size() + " manual)");
        }

        System.out.println("[InfiniteStats-EMC] Converged after " + iter + " iterations");

        // 阶段二：循环漏洞检测（与 ProjectE 一致）
        // 检测输入EMC < 输出EMC 的配方（净正值 = 复制漏洞），将产物置0
        int zeroed = detectAndZeroExploitLoops(rm, registryAccess);

        // 阶段三：应用 setValueAfter 强制覆盖
        for (var entry : AFTER_OVERRIDE_EMC.entrySet()) {
            if (entry.getValue() > 0) {
                EMC_MAP.put(entry.getKey(), entry.getValue());
            }
        }

        // 阶段四：黑名单
        applyBlacklists();

        System.out.println("[InfiniteStats-EMC] Total: " + EMC_MAP.size() + " items, "
                + zeroed + " exploit-zeroed, "
                + AFTER_OVERRIDE_EMC.size() + " after-overrides, "
                + MANUAL_EMC.size() + " manual anchors");
    }

    /**
     * 工作台配方：所有非空原料 EMC 之和 ÷ 输出数量（向下取整）
     *
     * ProjectE 使用 floor（而非 ceil）的关键原因：防止 EMC 复制漏洞。
     * 例：3 圆石(EMC=3) → 6 台阶 → floor(3/6)=0，台阶 EMC=0，不参与后续计算
     * 如果用 ceil：ceil(3/6)=1 → 6×1=6 EMC → 凭空多 3 EMC（漏洞！）
     *
     * 任一必需原料无 EMC → 返回 0（本轮不可计算，等待该原料在后续迭代中获得 EMC）
     */
    private static long calcCraftingEmc(List<Ingredient> ingredients, int outputCount) {
        if (outputCount <= 0) return 0;
        long total = 0;
        int usedCount = 0;
        for (Ingredient ing : ingredients) {
            if (ing.isEmpty()) continue;
            long ingEmc = getIngredientEmc(ing);
            if (ingEmc <= 0) return 0; // 缺失 EMC 的原料 → 本轮跳过
            total += ingEmc;
            usedCount++;
        }
        // 向下取整：total / outputCount（与 ProjectE 一致）
        return usedCount > 0 ? total / outputCount : 0;
    }

    /**
     * 单输入配方（熔炉/切石机）：第一个原料 EMC ÷ 输出数量（向下取整）
     */
    private static long calcSingleInputEmc(List<Ingredient> ingredients, int outputCount) {
        if (outputCount <= 0 || ingredients.isEmpty()) return 0;
        long ingEmc = getIngredientEmc(ingredients.get(0));
        return ingEmc > 0 ? ingEmc / outputCount : 0;
    }

    /**
     * 锻造台配方：所有有 EMC 的原料之和（跳过模板等无 EMC 可复用材料）
     * 与 ProjectE 一致：零数量/无 EMC 的催化剂不参与计算
     */
    private static long calcSmithingEmc(List<Ingredient> ingredients) {
        long total = 0;
        boolean hasNonZero = false;
        for (Ingredient ing : ingredients) {
            if (ing.isEmpty()) continue;
            long ingEmc = getIngredientEmc(ing);
            if (ingEmc <= 0) continue; // 模板类跳过
            total += ingEmc;
            hasNonZero = true;
        }
        return hasNonZero ? total : 0;
    }

    /**
     * 获取 Ingredient 中任意一个匹配物品的 EMC（取最小）
     * 返回 0 表示原料缺失 EMC → 调用方应跳过该配方
     */
    private static long getIngredientEmc(Ingredient ing) {
        if (ing.isEmpty()) return 0;
        ItemStack[] stacks = ing.getItems();
        if (stacks.length == 0) return 0;

        long minEmc = Long.MAX_VALUE;
        boolean found = false;
        for (ItemStack s : stacks) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(s.getItem());
            Long emc = EMC_MAP.get(id);
            if (emc != null) {
                found = true;
                if (emc > 0 && emc < minEmc) {
                    minEmc = emc;
                }
            }
        }
        // 如果 Ingredient 的所有选项在 EMC_MAP 中都存在但值都是 0，
        // 说明这是 0-EMC 物品（如台阶），返回 0 让配方跳过（与 ProjectE 一致）
        if (!found) return 0; // 完全不认识的物品
        return minEmc == Long.MAX_VALUE ? 0 : minEmc;
    }

    /**
     * 循环漏洞检测：参照 ProjectE 的循环检测逻辑
     *
     * 扫描所有配方，如果某个配方的输出总 EMC > 输入原料 EMC 之和，
     * 说明存在净正值漏洞（如 A→B 且 B 可合成回 A 且产出 > 消耗）。
     * 将漏洞产物 EMC 置 0。
     *
     * 例：1 圆石(1) → 1 石头(1) → 1 切石砖(1)，无漏洞
     * 例：3 圆石(3) → 6 台阶 → floor(3/6)=0 → 已有保护，不会触发
     *
     * 此函数主要防范多步循环（模组配方更可能出现）
     */
    private static int detectAndZeroExploitLoops(RecipeManager rm, net.minecraft.core.RegistryAccess registryAccess) {
        int zeroed = 0;
        int maxPasses = 5; // 多轮检查，因为清零一个物品后可能暴露新的漏洞

        for (int pass = 0; pass < maxPasses; pass++) {
            boolean anyZeroed = false;

            for (Recipe<?> recipe : rm.getRecipes()) {
                try {
                    if (recipe.isSpecial()) continue;

                    ItemStack result = recipe.getResultItem(registryAccess);
                    if (result.isEmpty()) continue;

                    ResourceLocation outId = BuiltInRegistries.ITEM.getKey(result.getItem());
                    Long existingEmc = EMC_MAP.get(outId);
                    if (existingEmc == null || existingEmc <= 0) continue; // 无 EMC 或已清零，跳过
                    if (MANUAL_EMC.containsKey(outId)) continue; // 手动值受保护

                    // 计算配方输入 EMC
                    long realInputEmc = 0;
                    boolean allKnown = true;

                    for (Ingredient ing : recipe.getIngredients()) {
                        if (ing.isEmpty()) continue;
                        // 这里不同于 calcCraftingEmc：我们使用宽松匹配（允许 0-EMC）
                        // 因为此时所有 EMC 值都已收敛，我们只需要检测漏洞
                        long ingEmc = getIngredientEmcForExploitCheck(ing);
                        if (ingEmc < 0) { allKnown = false; break; } // 完全未知
                        realInputEmc += ingEmc;
                    }

                    if (!allKnown || realInputEmc <= 0) continue;

                    // 计算输出总 EMC
                    long outputTotalEmc = existingEmc * result.getCount();

                    // 漏洞：输出总 EMC > 输入总 EMC
                    if (outputTotalEmc > realInputEmc) {
                        EMC_MAP.put(outId, 0L);
                        zeroed++;
                        anyZeroed = true;
                    }
                } catch (Exception ignored) {}
            }

            if (!anyZeroed) break; // 本轮无新增清零，结束
        }

        if (zeroed > 0) {
            System.out.println("[InfiniteStats-EMC] Exploit detection: zeroed " + zeroed + " items");
        }
        return zeroed;
    }

    /**
     * 漏洞检测专用 EMC 查询（允许 0-EMC 物品）
     * 返回 -1 表示该原料完全不在 EMC_MAP 中（未知）
     */
    private static long getIngredientEmcForExploitCheck(Ingredient ing) {
        if (ing.isEmpty()) return 0;
        ItemStack[] stacks = ing.getItems();
        if (stacks.length == 0) return -1;

        long minEmc = Long.MAX_VALUE;
        boolean found = false;
        for (ItemStack s : stacks) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(s.getItem());
            Long emc = EMC_MAP.get(id);
            if (emc != null) {
                found = true;
                if (emc < minEmc) minEmc = emc;
            }
        }
        if (!found) return -1;
        return minEmc == Long.MAX_VALUE ? 0 : minEmc;
    }

    /**
     * 黑名单：原矿（forge:raw_materials/*）、矿石块（forge:ores/*）EMC=0
     * 防止"矿石→烧炼→合成块→拆解→更多矿石"的 EMC 漏洞
     */
    private static void applyBlacklists() {
        Set<String> blacklisted = new HashSet<>();

        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            String idStr = id.toString();

            // #forge:raw_materials 标签
            // #forge:ores 标签
            // #minecraft:coals, #minecraft:iron_ores 等
            if (idStr.contains("_ore") || idStr.endsWith("_ores")
                    || idStr.contains("raw_") && !idStr.contains("raw_fish")) {
                Long emc = EMC_MAP.get(id);
                if (emc != null && emc > 0) {
                    EMC_MAP.put(id, 0L);
                    blacklisted.add(idStr);
                }
            }
        }

        // 明确黑名单的原矿物品
        String[] explicitBlacklist = {
            "minecraft:raw_iron", "minecraft:raw_copper", "minecraft:raw_gold",
            "minecraft:iron_ore", "minecraft:copper_ore", "minecraft:gold_ore",
            "minecraft:diamond_ore", "minecraft:emerald_ore", "minecraft:lapis_ore",
            "minecraft:redstone_ore", "minecraft:nether_quartz_ore", "minecraft:coal_ore",
            "minecraft:deepslate_iron_ore", "minecraft:deepslate_copper_ore",
            "minecraft:deepslate_gold_ore", "minecraft:deepslate_diamond_ore",
            "minecraft:deepslate_emerald_ore", "minecraft:deepslate_lapis_ore",
            "minecraft:deepslate_redstone_ore", "minecraft:nether_gold_ore",
            "minecraft:ancient_debris"
        };
        for (String bl : explicitBlacklist) {
            ResourceLocation rl = ResourceLocation.tryParse(bl);
            if (rl != null && EMC_MAP.containsKey(rl)) {
                EMC_MAP.put(rl, 0L);
                blacklisted.add(bl);
            }
        }

        if (!blacklisted.isEmpty()) {
            System.out.println("[InfiniteStats-EMC] Blacklisted " + blacklisted.size() + " raw ore/ore block items");
        }
    }

    // ==================== 保存计算结果 ====================

    private static void saveCalculated(Path configDir) {
        try {
            Path outputFile = configDir.resolve("emc_calculated.json");
            Files.createDirectories(outputFile.getParent());

            JsonObject root = new JsonObject();

            // 元信息
            JsonObject meta = new JsonObject();
            meta.addProperty("algorithm", "SimpleGraphMapper (Bellman-Ford + floor division)");
            meta.addProperty("rounding", "floor");
            meta.addProperty("total_items", EMC_MAP.size());
            meta.addProperty("manual_anchors", MANUAL_EMC.size());
            meta.addProperty("after_overrides", AFTER_OVERRIDE_EMC.size());
            meta.addProperty("projecte_compatible", true);
            root.add("_meta", meta);

            // 物品列表（按 EMC 降序）
            JsonArray items = new JsonArray();
            EMC_MAP.entrySet().stream()
                    .sorted(Map.Entry.<ResourceLocation, Long>comparingByValue().reversed())
                    .forEach(entry -> {
                        JsonObject obj = new JsonObject();
                        obj.addProperty("id", entry.getKey().toString());
                        obj.addProperty("emc", entry.getValue());
                        obj.addProperty("source", MANUAL_EMC.containsKey(entry.getKey()) ? "manual"
                                : AFTER_OVERRIDE_EMC.containsKey(entry.getKey()) ? "after_override"
                                : "calculated");
                        items.add(obj);
                    });

            root.add("items", items);
            try (Writer w = Files.newBufferedWriter(outputFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                GSON.toJson(root, w);
            }
        } catch (IOException e) {
            System.err.println("[InfiniteStats-EMC] Failed to save calculated: " + e.getMessage());
        }
    }

    // ==================== 查询接口 ====================

    public static long getEmc(ItemStack stack) {
        if (!Config.EMC_ENABLED.get() || stack.isEmpty()) return 0;

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        // 自定义定价优先级最高（覆盖 ProjectE 和自动计算）
        Long custom = CUSTOM_EMC.get(id);
        if (custom != null) return custom;

        long emc;
        if (projecteLoaded && projecteGetValueMethod != null) {
            try {
                Object result = projecteGetValueMethod.invoke(projecteProxy, stack);
                emc = (result instanceof Number) ? ((Number) result).longValue() : 0L;
            } catch (Exception ignored) {
                emc = 0L;
            }
        } else {
            emc = EMC_MAP.getOrDefault(id, 0L);
        }

        // 兜底：本应 0 EMC 的未知物品，按配置赋予最小值，使其可被学习 / 转化
        if (emc <= 0) {
            long fallback = Config.EMC_FALLBACK_VALUE.get();
            if (fallback > 0) emc = fallback;
        }
        return emc;
    }

    public static long getSellValue(ItemStack stack, int count) {
        if (count <= 0) return 0;
        return java.math.BigDecimal.valueOf(getEmc(stack))
                .multiply(java.math.BigDecimal.valueOf(count))
                .multiply(java.math.BigDecimal.ONE.subtract(java.math.BigDecimal.valueOf(Config.EMC_LOSS_RATE.get())))
                .setScale(0, java.math.RoundingMode.FLOOR)
                .min(java.math.BigDecimal.valueOf(Long.MAX_VALUE)).longValue();
    }

    public static boolean hasEmc(ItemStack stack) {
        return getEmc(stack) > 0;
    }

    public static List<ItemStack> getItemsForDisplay(String filter) {
        List<ItemStack> result = new ArrayList<>();
        String lower = filter.toLowerCase();

        if (projecteLoaded) {
            for (Item item : BuiltInRegistries.ITEM) {
                if (item == Items.AIR) continue;
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                if (!lower.isEmpty() && !id.toString().contains(lower)) continue;
                ItemStack s = new ItemStack(item);
                if (getEmc(s) > 0) result.add(s);
            }
        } else {
            for (var entry : EMC_MAP.entrySet()) {
                if (entry.getValue() <= 0) continue;
                if (!lower.isEmpty() && !entry.getKey().toString().contains(lower)) continue;
                Item item = BuiltInRegistries.ITEM.get(entry.getKey());
                if (item != Items.AIR) result.add(new ItemStack(item));
            }
        }
        return result;
    }

    public static Map<ResourceLocation, Long> getAllEmcValues() {
        return Collections.unmodifiableMap(EMC_MAP);
    }

    public static long getTotalItems() {
        return projecteLoaded ? -1 : EMC_MAP.size();
    }

    public static boolean isProjectELoaded() {
        return projecteLoaded;
    }

    public static boolean isCalculated() {
        return calculated || projecteLoaded;
    }

    // ==================== 重载 ====================

    public static void reload(MinecraftServer server) {
        calculated = false;
        load(server);
    }

    // ==================== 运行时定价 ====================

    /** 设置自定义 EMC 值（0 表示删除） */
    private static Path customPricePath() {
        return FMLPaths.CONFIGDIR.get().resolve("infinitestats").resolve("custom_emc_values.json");
    }

    private static void loadCustomPrices() {
        Path path = customPricePath();
        if (!Files.isRegularFile(path)) return;
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject values = GSON.fromJson(reader, JsonObject.class);
            if (values == null) return;
            for (var entry : values.entrySet()) {
                ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
                long value = entry.getValue().getAsLong();
                if (id != null && value > 0) CUSTOM_EMC.put(id, value);
            }
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Cannot load custom EMC prices: " + path, e);
        }
    }

    public static boolean setCustomEmc(ResourceLocation id, long value) {
        Map<ResourceLocation, Long> updated = new LinkedHashMap<>(CUSTOM_EMC);
        if (value <= 0) updated.remove(id);
        else updated.put(id, value);
        JsonObject json = new JsonObject();
        updated.forEach((key, price) -> json.addProperty(key.toString(), price));
        Path path = customPricePath();
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(temp, GSON.toJson(json), java.nio.charset.StandardCharsets.UTF_8);
            try {
                Files.move(temp, path, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            CUSTOM_EMC.clear();
            CUSTOM_EMC.putAll(updated);
            return true;
        } catch (IOException e) {
            System.err.println("[InfiniteStats-EMC] Cannot save custom prices: " + e.getMessage());
            return false;
        }
    }

    public static long getCustomEmc(ResourceLocation id) {
        return CUSTOM_EMC.getOrDefault(id, 0L);
    }
}
