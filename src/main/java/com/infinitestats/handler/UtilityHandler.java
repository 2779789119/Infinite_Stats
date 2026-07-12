package com.infinitestats.handler;

import com.infinitestats.Config;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.StatCategory;
import com.infinitestats.stats.StatType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * 功能类属性处理器
 * 处理：夜视、水下呼吸、免饥饿、物品磁铁、隐身、连锁挖掘、自动冶炼、经验磁铁等
 */
public class UtilityHandler implements StatEffectHandler {

    @Override
    public String getId() {
        return "utility";
    }

    @Override
    public StatType[] getSupportedStats() {
        return StatType.getByCategory(StatCategory.UTILITY);
    }

    @Override
    public void onTick(ServerPlayer player, PlayerStats stats, long tickCount) {
        // 每5tick处理磁铁和效果
        if (tickCount % 5 == 0) {
            applyItemMagnet(player, stats);
            applyXpMagnet(player, stats);
            applyInvisibility(player, stats);
            applyNoInvincibilityFrames(player, stats);
        }

        // 每2秒处理呼吸、饥饿和幸运
        if (tickCount % 40 == 0) {
            applyNightVision(player, stats);
            applyWaterBreathing(player, stats);
            applyLootLuck(player, stats);
        }

        // 每0.5秒处理饥饿
        if (tickCount % 10 == 0) {
            applyNoHunger(player, stats);
        }

        // 每1秒处理自动修理
        if (tickCount % 20 == 0) {
            applyAutoRepair(player, stats);
        }

        // 火焰免疫时清除火焰
        if (stats.isToggleActive("fire_immunity")) {
            player.clearFire();
        }
    }

    @Override
    public void onLogin(ServerPlayer player, PlayerStats stats) {
        applyNightVision(player, stats);
        applyInvisibility(player, stats);
    }

    @Override
    public void onRespawn(ServerPlayer player, PlayerStats stats) {
        applyNightVision(player, stats);
        applyInvisibility(player, stats);
    }

    @Override
    public void onDimensionChange(ServerPlayer player, PlayerStats stats) {
        applyNightVision(player, stats);
        applyInvisibility(player, stats);
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    /**
     * 应用夜视效果
     * toggle ON → 始终确保夜视生效；toggle OFF → 仅当由本模组提供时才移除
     */
    private void applyNightVision(ServerPlayer player, PlayerStats stats) {
        boolean nightVision = stats.isToggleActive("night_vision");
        boolean weProvided = stats.isProviding("night_vision");

        if (nightVision) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,
                    MobEffectInstance.INFINITE_DURATION, 0, false, false, true));
            stats.setProviding("night_vision", true);
        } else if (weProvided) {
            player.removeEffect(MobEffects.NIGHT_VISION);
            stats.setProviding("night_vision", false);
        }
    }

    /**
     * 应用水下呼吸
     */
    private void applyWaterBreathing(ServerPlayer player, PlayerStats stats) {
        boolean waterBreathing = stats.isToggleActive("water_breathing");
        if (waterBreathing) {
            player.setAirSupply(player.getMaxAirSupply());
            player.removeEffect(MobEffects.WATER_BREATHING);
        }
    }

    /**
     * 应用免饥饿
     */
    private void applyNoHunger(ServerPlayer player, PlayerStats stats) {
        boolean noHunger = stats.isToggleActive("no_hunger");
        if (noHunger) {
            FoodData food = player.getFoodData();
            food.setFoodLevel(20);
            food.setSaturation(20.0f);
            food.setExhaustion(0.0f);
        }
    }

    /**
     * 应用隐身
     * 使用实体标志 setInvisible 而非药水效果，避免与 removeEffect 冲突导致闪烁
     * toggle ON → 始终确保隐身生效；toggle OFF → 仅当由本模组提供时才取消
     */
    private void applyInvisibility(ServerPlayer player, PlayerStats stats) {
        boolean invis = stats.isToggleActive("invisibility");
        boolean weProvided = stats.isProviding("invisibility");

        if (invis) {
            player.setInvisible(true);
            stats.setProviding("invisibility", true);
        } else if (weProvided) {
            player.setInvisible(false);
            stats.setProviding("invisibility", false);
        }
    }

    /**
     * 应用物品磁铁
     */
    private void applyItemMagnet(ServerPlayer player, PlayerStats stats) {
        if (!stats.isToggleActive("item_magnet")) return;

        double range = 8.0;
        AABB area = new AABB(
                player.getX() - range, player.getY() - range, player.getZ() - range,
                player.getX() + range, player.getY() + range, player.getZ() + range
        );

        List<ItemEntity> items = player.level().getEntitiesOfClass(ItemEntity.class, area,
                e -> e.isAlive() && !e.hasPickUpDelay());

        Vec3 playerPos = player.position();
        for (ItemEntity item : items) {
            Vec3 diff = playerPos.subtract(item.position());
            double dist = diff.length();
            if (dist > 0.5) {
                Vec3 speed = diff.normalize().scale(0.3);
                item.setDeltaMovement(item.getDeltaMovement().add(speed));
            }
        }
    }

    /**
     * 应用经验磁铁
     */
    private void applyXpMagnet(ServerPlayer player, PlayerStats stats) {
        if (!stats.isToggleActive("xp_magnet")) return;

        double range = 10.0;
        AABB area = new AABB(
                player.getX() - range, player.getY() - range, player.getZ() - range,
                player.getX() + range, player.getY() + range, player.getZ() + range
        );

        List<ExperienceOrb> orbs = player.level().getEntitiesOfClass(ExperienceOrb.class, area);
        Vec3 playerPos = player.position();

        for (ExperienceOrb orb : orbs) {
            Vec3 diff = playerPos.subtract(orb.position());
            double dist = diff.length();
            if (dist > 1.0) {
                orb.setDeltaMovement(diff.normalize().scale(0.5));
            }
        }
    }

    /**
     * 取消无敌帧
     */
    private void applyNoInvincibilityFrames(ServerPlayer player, PlayerStats stats) {
        if (stats.isToggleActive("no_invincibility_frames")) {
            player.invulnerableTime = 0;
        }
    }

    /**
     * 自动修理装备（auto_repair 开关 + repair_amount 控制修理量）
     * 每秒修理玩家背包和装备栏中所有可损坏物品
     */
    private void applyAutoRepair(ServerPlayer player, PlayerStats stats) {
        if (!stats.isToggleActive("auto_repair")) return;

        long repairAmount = stats.getStatLevel(StatType.fromId("repair_amount"));
        if (repairAmount <= 0) repairAmount = 1;

        // 修理所有物品栏（主物品栏 + 盔甲 + 副手 + Curios饰品）
        List<ItemStack> allItems = new ArrayList<>();
        allItems.addAll(player.getInventory().items);
        allItems.addAll(player.getInventory().armor);
        allItems.addAll(player.getInventory().offhand);

        for (ItemStack stack : allItems) {
            if (!stack.isEmpty() && stack.isDamaged()) {
                stack.setDamageValue(Math.max(0, stack.getDamageValue() - (int) repairAmount));
            }
        }
    }

    // ========== 连锁挖掘 & 自动冶炼 ==========

    private static final Map<Item, Item> SMELT_MAP = new HashMap<>();

    static {
        SMELT_MAP.put(Items.RAW_IRON, Items.IRON_INGOT);
        SMELT_MAP.put(Items.RAW_GOLD, Items.GOLD_INGOT);
        SMELT_MAP.put(Items.RAW_COPPER, Items.COPPER_INGOT);
        SMELT_MAP.put(Items.IRON_ORE, Items.IRON_INGOT);
        SMELT_MAP.put(Items.DEEPSLATE_IRON_ORE, Items.IRON_INGOT);
        SMELT_MAP.put(Items.GOLD_ORE, Items.GOLD_INGOT);
        SMELT_MAP.put(Items.DEEPSLATE_GOLD_ORE, Items.GOLD_INGOT);
        SMELT_MAP.put(Items.COPPER_ORE, Items.COPPER_INGOT);
        SMELT_MAP.put(Items.DEEPSLATE_COPPER_ORE, Items.COPPER_INGOT);
        SMELT_MAP.put(Items.ANCIENT_DEBRIS, Items.NETHERITE_SCRAP);
        SMELT_MAP.put(Items.NETHER_GOLD_ORE, Items.GOLD_INGOT);
        SMELT_MAP.put(Items.DIAMOND_ORE, Items.DIAMOND);
        SMELT_MAP.put(Items.DEEPSLATE_DIAMOND_ORE, Items.DIAMOND);
        SMELT_MAP.put(Items.EMERALD_ORE, Items.EMERALD);
        SMELT_MAP.put(Items.DEEPSLATE_EMERALD_ORE, Items.EMERALD);
        SMELT_MAP.put(Items.LAPIS_ORE, Items.LAPIS_LAZULI);
        SMELT_MAP.put(Items.DEEPSLATE_LAPIS_ORE, Items.LAPIS_LAZULI);
        SMELT_MAP.put(Items.REDSTONE_ORE, Items.REDSTONE);
        SMELT_MAP.put(Items.DEEPSLATE_REDSTONE_ORE, Items.REDSTONE);
        SMELT_MAP.put(Items.COAL_ORE, Items.COAL);
        SMELT_MAP.put(Items.DEEPSLATE_COAL_ORE, Items.COAL);
        SMELT_MAP.put(Items.NETHER_QUARTZ_ORE, Items.QUARTZ);
    }

    /**
     * 处理连锁挖掘和自动冶炼
     */
    public static void handleVeinMinerAndAutoSmelt(ServerPlayer player, PlayerStats stats,
            BlockPos pos, BlockState state, ServerLevel level) {
        boolean veinMiner = stats.isToggleActive("vein_miner");
        boolean autoSmelt = stats.isToggleActive("auto_smelt");

        if (!veinMiner && !autoSmelt) return;

        ItemStack tool = player.getMainHandItem();
        Block block = state.getBlock();

        // 主方块掉落物
        List<ItemStack> drops = Block.getDrops(state, level, pos,
                level.getBlockEntity(pos), player, tool);

        // 自动冶炼主方块掉落物
        if (autoSmelt) {
            smeltDrops(drops);
        }

        // 连锁挖掘
        if (veinMiner) {
            int maxBlocks = Config.VEIN_MINER_MAX_BLOCKS.get();
            int mined = 1;
            Set<BlockPos> visited = new HashSet<>();
            Deque<BlockPos> queue = new ArrayDeque<>();
            queue.add(pos);
            visited.add(pos);

            while (!queue.isEmpty() && mined < maxBlocks) {
                BlockPos current = queue.poll();
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            BlockPos neighbor = current.offset(dx, dy, dz);
                            if (visited.contains(neighbor)) continue;
                            visited.add(neighbor);
                            BlockState nState = level.getBlockState(neighbor);
                            if (nState.getBlock() == block && level.getBlockEntity(neighbor) == null) {
                                if (tool.isCorrectToolForDrops(nState)) {
                                    List<ItemStack> nDrops = Block.getDrops(nState, level, neighbor,
                                            level.getBlockEntity(neighbor), player, tool);
                                    level.destroyBlock(neighbor, false, player);
                                    if (autoSmelt) {
                                        smeltDrops(nDrops);
                                    }
                                    for (ItemStack d : nDrops) {
                                        Block.popResource(level, neighbor, d);
                                    }
                                    mined++;
                                    if (mined >= maxBlocks) break;
                                    queue.add(neighbor);
                                }
                            }
                        }
                        if (mined >= maxBlocks) break;
                    }
                }
            }
        }

        // 生成主方块掉落物
        for (ItemStack drop : drops) {
            Block.popResource(level, pos, drop);
        }
    }

    private static void smeltDrops(List<ItemStack> drops) {
        for (int i = 0; i < drops.size(); i++) {
            Item smeltResult = SMELT_MAP.get(drops.get(i).getItem());
            if (smeltResult != null) {
                drops.set(i, new ItemStack(smeltResult, drops.get(i).getCount()));
            }
        }
    }

    /**
     * 计算挖掘速度加成
     */
    public static float getMiningSpeedMultiplier(PlayerStats stats) {
        return 1.0f + stats.getStatValue(StatType.fromId("mining_speed"));
    }

    /**
     * 获取挖掘等级加成（每级 +1 挖掘等级）
     */
    public static int getMiningLevelBonus(PlayerStats stats) {
        return (int) stats.getStatValue(StatType.fromId("mining_level"));
    }

    /**
     * 计算经验获取加成
     */
    public static long applyXpGainBonus(PlayerStats stats, long baseXp) {
        float multiplier = 1.0f + stats.getStatValue(StatType.fromId("xp_gain"));
        return (long) (baseXp * multiplier);
    }

    /**
     * 应用掉落物幸运加成
     * 根据 loot_luck 属性值给予对应等级的幸运效果
     */
    private void applyLootLuck(ServerPlayer player, PlayerStats stats) {
        float luckValue = stats.getStatValue(StatType.fromId("loot_luck"));
        int luckLevel = (int) (luckValue * 100); // 转换为整数等级

        boolean weProvided = stats.isProviding("loot_luck");

        if (luckLevel > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.LUCK,
                    MobEffectInstance.INFINITE_DURATION, luckLevel - 1, false, false, true));
            stats.setProviding("loot_luck", true);
        } else if (weProvided) {
            player.removeEffect(MobEffects.LUCK);
            stats.setProviding("loot_luck", false);
        }
    }

    /**
     * 获取掉落物幸运等级（供外部查询）
     */
    public static int getLootLuckLevel(PlayerStats stats) {
        float luckValue = stats.getStatValue(StatType.fromId("loot_luck"));
        return (int) (luckValue * 100);
    }
}