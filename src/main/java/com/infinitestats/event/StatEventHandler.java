package com.infinitestats.event;

import com.infinitestats.Config;
import com.infinitestats.InfiniteStats;
import com.infinitestats.handler.*;
import com.infinitestats.network.NetworkHandler;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import com.infinitestats.stats.StatType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;
import net.minecraftforge.common.TierSortingRegistry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 主事件处理器 - 整合所有属性效果
 * 使用HandlerRegistry统一调度各个专用处理器
 */
@Mod.EventBusSubscriber(modid = InfiniteStats.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StatEventHandler {

    // ========== 死亡事件 ==========

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        // 自动复活
        if (event.getEntity() instanceof ServerPlayer player) {
            player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
                if (DefenseHandler.handleAutoRevive(player, stats)) {
                    event.setCanceled(true);
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component.translatable("message.infinitestats.auto_revive"),
                            true
                    );
                    // 自动复活的 removeAllEffects() 会清除所有效果（包括夜视/隐身等），
                    // 必须立即重新施加被清除的 Utility 效果，否则要等最多 2 秒才会在 tick 中恢复
                    HandlerRegistry.loginAll(player, stats);
                    NetworkHandler.syncToClient(player);
                    return;
                }
            });
            if (event.isCanceled()) return;
        }

        // 击杀获得经验 + 击杀回蓝
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            var target = event.getEntity();
            long baseXp = (long) Config.XP_PER_KILL_BASE.get()
                    + (long) (target.getMaxHealth() * Config.XP_PER_KILL_HEALTH_FACTOR.get());

            player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
                long xp = UtilityHandler.applyXpGainBonus(stats, baseXp);
                addXpAndSync(player, stats, xp);

                // 击杀回蓝
                float manaOnKill = stats.getStatValue(StatType.fromId("mana_on_kill"));
                if (manaOnKill > 0) {
                    float maxMana = stats.getMaxMana();
                    if (maxMana > 0) {
                        stats.setCurrentMana(Math.min(stats.getCurrentMana() + manaOnKill, maxMana));
                    }
                }
            });
        }
    }

    // ========== 跳跃事件 ==========

    /**
     * 使用 LOWEST 优先级的 Tick 阶段检测起跳，比 LivingJumpEvent 更可靠。
     * LivingJumpEvent 虽然理论上有效，但在巨型整合包中容易被其他模组的事件处理器覆盖。
     * 这里改用 ServerPlayer tick 中检测地面→离地转换，在 Phase.START 修改速度，
     * 并通过 setPos 补偿第一个 tick 的运动偏差（因为 travel() 会在 aiStep 中跑完）。
     */
    private static void handleJumpBoost(ServerPlayer player, PlayerStats stats) {
        float multiplier = MobilityHandler.getJumpMultiplier(stats);
        if (multiplier <= 1.0f) return;

        boolean onGround = player.onGround();
        java.util.UUID uuid = player.getUUID();

        Boolean wasOnGround = playerGroundState.get(uuid);
        playerGroundState.put(uuid, onGround);

        // 检测：上一tick在地面、当前tick离地、Y速度为正 → 起跳瞬间
        if (wasOnGround != null && wasOnGround && !onGround && player.getDeltaMovement().y > 0) {
            Vec3 motion = player.getDeltaMovement();
            double boostedY = motion.y * multiplier;
            double extraY = boostedY - motion.y;

            // 补偿第一tick运动（travel()已用原始速度跑完，手动修正位置以匹配倍率）
            if (extraY > 0.001) {
                player.setPos(player.getX(), player.getY() + extraY, player.getZ());
            }

            // 修正速度（影响后续 tick 的运动）
            player.setDeltaMovement(motion.x, boostedY, motion.z);
            player.hasImpulse = true;
        }
    }

    /** 记录各玩家上一tick是否在地面（用于起跳检测） */
    private static final java.util.Map<java.util.UUID, Boolean> playerGroundState = new java.util.HashMap<>();

    /**
     * 保留 LivingJumpEvent 作为第一道防线（在 jumpFromGround 内、travel 前执行，最精确）。
     * 使用 LOWEST 优先级确保覆盖其他模组的修改。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
            float multiplier = MobilityHandler.getJumpMultiplier(stats);
            if (multiplier > 1.0f) {
                Vec3 motion = player.getDeltaMovement();
                player.setDeltaMovement(motion.x, motion.y * multiplier, motion.z);
            }
        });
    }

    // ========== 掉落物事件 ==========

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;

        player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
            // 双倍掉落
            float doubleLootChance = stats.getStatValue(StatType.fromId("double_loot"));
            if (doubleLootChance > 0 && player.getRandom().nextFloat() < doubleLootChance) {
                // 先复制一份再遍历，防止 ConcurrentModificationException
                java.util.List<ItemEntity> copies = new java.util.ArrayList<>();
                for (ItemEntity drop : event.getDrops()) {
                    ItemStack stack = drop.getItem().copy();
                    ItemEntity extraDrop = new ItemEntity(
                            drop.level(), drop.getX(), drop.getY(), drop.getZ(), stack
                    );
                    copies.add(extraDrop);
                }
                event.getDrops().addAll(copies);
            }
        });
    }

    // ========== 伤害事件 ==========

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(LivingHurtEvent event) {
        // 玩家攻击（覆盖近战、弓箭/三叉戟等玩家拥有的抛射物，
        // 避免“伤害来源实体不是玩家”导致范围伤害等攻击效果时灵时不灵）
        ServerPlayer player = getPlayerAttacker(event);
        if (player != null) {
            player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
                float amount = event.getAmount();

                // 暴击
                boolean isFullAttack = player.getAttackStrengthScale(0.5f) > 0.9f;
                amount *= AttackHandler.calculateCritMultiplier(player, stats, isFullAttack);

                // 护甲穿透
                amount *= AttackHandler.calculatePenetrationBonus(stats);

                // 弹射物伤害
                boolean isProjectile = event.getSource().getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile;
                amount *= AttackHandler.calculateProjectileBonus(stats, isProjectile);

                // 魔法伤害
                boolean isIndirect = event.getSource().getEntity() != null
                        && event.getSource().getEntity() != event.getSource().getDirectEntity();
                amount *= AttackHandler.calculateMagicBonus(stats, isIndirect);

                event.setAmount(amount);

                // 生命偷取
                AttackHandler.applyLifeSteal(player, stats, amount);

                // 范围吸血
                AttackHandler.applyAoeLifeSteal(player, stats, amount, event.getEntity());

                // 法力窃取
                AttackHandler.applyManaSteal(player, stats, amount);

                // 处决：对低血量目标额外伤害
                float executeDmg = stats.getStatValue(StatType.fromId("execute"));
                if (executeDmg > 0) {
                    LivingEntity target = event.getEntity();
                    float hpPct = target.getHealth() / target.getMaxHealth();
                    if (hpPct < 0.3f) {
                        float bonus = (1.0f - hpPct) * executeDmg * target.getMaxHealth();
                        event.setAmount(event.getAmount() + bonus);
                    }
                }

                // 真实伤害（无视护甲与减伤）
                AttackHandler.applyTrueDamage(player, stats, event.getAmount(), event.getEntity());

                // 攻击降低目标最大生命值
                AttackHandler.applyReduceMaxHealth(player, stats, event.getEntity());

                // 范围攻击：波及周围敌人
                AttackHandler.applyScopeAttack(player, stats, event.getAmount(), event.getEntity());
            });
        }

        // 玩家受伤
        if (event.getEntity() instanceof ServerPlayer hurtPlayer) {
            hurtPlayer.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
                float amount = event.getAmount();

                // 复活无敌窗口：取消一切伤害（最高优先级，防止复活瞬间原地再死）
                if (DefenseHandler.isReviveInvulnerable(hurtPlayer, stats)) {
                    event.setCanceled(true);
                    return;
                }

                // 无敌：取消所有伤害
                if (DefenseHandler.isInvincible(stats)) {
                    event.setCanceled(true);
                    return;
                }

                // 检查免疫
                if (DefenseHandler.isImmune(stats, event.getSource(), hurtPlayer)) {
                    event.setCanceled(true);
                    return;
                }

                // 闪避
                if (DefenseHandler.handleDodge(hurtPlayer, stats)) {
                    event.setCanceled(true);
                    hurtPlayer.displayClientMessage(
                            net.minecraft.network.chat.Component.translatable("message.infinitestats.dodged"),
                            true
                    );
                    return;
                }

                // 格挡
                if (DefenseHandler.handleBlock(hurtPlayer, stats)) {
                    event.setCanceled(true);
                    hurtPlayer.displayClientMessage(
                            net.minecraft.network.chat.Component.translatable("message.infinitestats.blocked"),
                            true
                    );
                    return;
                }

                // 法力护盾
                amount = MagicHandler.applyManaShield(stats, amount);
                if (amount <= 0) {
                    event.setCanceled(true);
                    return;
                }

                // 伤害减免
                amount = DefenseHandler.applyDamageReduction(stats, amount);

                // 摔落伤害减免
                boolean isFall = event.getSource() == hurtPlayer.level().damageSources().fall();
                amount = DefenseHandler.applyFallDamageReduction(stats, amount, isFall);
                if (amount <= 0) {
                    event.setCanceled(true);
                    return;
                }

                event.setAmount(amount);

                // 伤害反射
                if (event.getSource().getEntity() instanceof LivingEntity attacker) {
                    DefenseHandler.applyDamageReflection(hurtPlayer, stats, event.getAmount(), attacker);
                }
            });
        }
    }

    // ========== 负面效果拦截 ==========

    /**
     * 在药水效果施加前直接拦截（debuff_immunity 开关）
     * 支持玩家自定义过滤列表（黑名单/白名单模式），可选择过滤所有效果（包括正面buff和负面debuff）
     * <p>
     * 注意：绝不拦截本模组自己的 Utility 效果（夜视、隐身等），
     * 否则 UtilityHandler 施加的效果会被这里反向拦截，导致 toggle 功能失效。
     */
    @SubscribeEvent
    public static void onMobEffectApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
            if (!stats.isToggleActive("debuff_immunity")) return;

            String effectId = net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS
                    .getKey(event.getEffectInstance().getEffect()).toString();

            // 绝不拦截本模组自己施加的效果（夜视/隐身/幸运等 Utility toggle）
            if (isOwnUtilityEffect(stats, effectId)) return;

            if (stats.shouldBlockEffect(effectId)) {
                event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
            }
        });
    }

    /**
     * 检查某个药水效果是否来自本模组的 Utility 系统
     * 如果对应的 toggle 已激活，说明该效果由我们自己施加，不应被 debuff_immunity 拦截
     */
    private static boolean isOwnUtilityEffect(PlayerStats stats, String effectId) {
        return switch (effectId) {
            case "minecraft:night_vision" -> stats.isToggleActive("night_vision");
            case "minecraft:invisibility" -> stats.isToggleActive("invisibility");
            case "minecraft:luck" -> stats.getStatValue("loot_luck") > 0;
            default -> false;
        };
    }

    // ========== 玩家Tick事件 ==========

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        long tickCount = player.tickCount;

        // 使用HandlerRegistry处理所有效果（含跳跃检测、被动经验，每玩家独立计数）
        player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
            try {
                HandlerRegistry.tickAll(player, stats, tickCount);
            } catch (Throwable t) {
                t.printStackTrace();
            }
            try {
                // 跳跃加成检测（tick 级兜底，比 LivingJumpEvent 更可靠）
                handleJumpBoost(player, stats);
            } catch (Throwable t) {
                t.printStackTrace();
            }
            try {
                // 被动经验 - 每玩家独立计数器
                stats.incrementPassiveTickCounter();
                if (stats.getPassiveTickCounter() >= Config.PASSIVE_XP_INTERVAL.get()) {
                    stats.resetPassiveTickCounter();
                    addXpAndSync(player, stats, Config.PASSIVE_XP_AMOUNT.get());
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
            // 随身熔炉：每个玩家 tick 都驱动冶炼进度（与界面是否打开无关）
            // 独立 try，确保即使上述逻辑异常也照常冶炼
            try {
                stats.getFurnaceData().tick(player.level());
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });
    }

    // ========== 挖掘速度 ==========

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        event.getEntity().getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
            float bonus = UtilityHandler.getMiningSpeedMultiplier(stats);
            if (bonus > 1) {
                event.setNewSpeed(event.getOriginalSpeed() * bonus);
            }
        });
    }

    // ========== 方块破坏事件 ==========

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (player.isCreative()) return;

        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        ServerLevel level = (ServerLevel) event.getLevel();

        player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
            UtilityHandler.handleVeinMinerAndAutoSmelt(player, stats, pos, state, level);
        });
    }

    // ========== 挖掘等级加成 ==========

    @SubscribeEvent
    public static void onHarvestCheck(PlayerEvent.HarvestCheck event) {
        if (event.canHarvest()) return;

        event.getEntity().getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
            int bonus = UtilityHandler.getMiningLevelBonus(stats);
            if (bonus <= 0) return;

            ItemStack tool = event.getEntity().getMainHandItem();
            int toolIndex = getToolTierIndex(tool);
            if (toolIndex < 0) return;

            java.util.List<net.minecraft.world.item.Tier> sortedTiers =
                    TierSortingRegistry.getSortedTiers();
            int enhancedIndex = Math.min(toolIndex + bonus, sortedTiers.size() - 1);
            if (enhancedIndex <= toolIndex) return;

            net.minecraft.world.item.Tier enhancedTier = sortedTiers.get(enhancedIndex);
            if (TierSortingRegistry.isCorrectTierForDrops(enhancedTier, event.getTargetBlock())) {
                event.setCanHarvest(true);
            }
        });
    }

    /**
     * 获取手持工具在 TierSortingRegistry 排序列表中的索引
     * 空手/非工具 = -1
     */
    private static int getToolTierIndex(ItemStack tool) {
        if (!(tool.getItem() instanceof TieredItem ti)) return -1;
        java.util.List<net.minecraft.world.item.Tier> sorted =
                TierSortingRegistry.getSortedTiers();
        return sorted.indexOf(ti.getTier());
    }

    // ========== 玩家登录 ==========

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
            // 重新计算可用点数，修复旧存档可能为负的问题
            stats.recalculateAvailablePoints();
            NetworkHandler.syncToClient(player);
            HandlerRegistry.loginAll(player, stats);
        });
    }

    // ========== 玩家重生 ==========

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
            NetworkHandler.syncToClient(player);
            HandlerRegistry.respawnAll(player, stats);
        });
    }

    // ========== 玩家克隆 ==========

    /**
     * 玩家实体克隆时保留所有数据（死亡重生、末地传送门返回等场景均会触发 Clone 事件）
     * 不区分死亡/非死亡克隆，与 EMC 系统保持一致的处理方式
     */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        event.getOriginal().reviveCaps();
        event.getOriginal().getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(oldStats -> {
            event.getEntity().getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(newStats -> {
                newStats.copyFrom(oldStats);
            });
        });
        event.getOriginal().invalidateCaps();
    }

    // ========== 维度切换 ==========

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
            NetworkHandler.syncToClient(player);
            HandlerRegistry.dimensionChangeAll(player, stats);
        });
    }

    // ========== 使用速度加速 ==========

    @SubscribeEvent
    public static void onItemUseTick(LivingEntityUseItemEvent.Tick event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getDuration() <= 0) return;

        player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
            // 通用使用速度（吃东西、喝药水、用盾牌等，不对弓/弩/三叉戟生效，那些由 bow_draw_speed 负责）
            boolean isBow = event.getItem().getItem() instanceof BowItem
                    || event.getItem().getItem() instanceof CrossbowItem
                    || event.getItem().getItem() instanceof TridentItem;

            float totalSpeed = 0;
            if (!isBow) {
                totalSpeed = stats.getStatValue(StatType.fromId("use_speed"));
            } else {
                totalSpeed = stats.getStatValue(StatType.fromId("bow_draw_speed"));
            }

            if (totalSpeed > 0) {
                int extraReduction = Math.max(1, (int) (totalSpeed * 100));
                event.setDuration(Math.max(0, event.getDuration() - extraReduction));
            }
        });
    }

    // ========== 合成奖励 ==========

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide) return;

        player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
            float chance = stats.getStatValue(StatType.fromId("crafting_bonus"));
            if (chance > 0 && player.getRandom().nextFloat() < chance) {
                ItemStack bonus = event.getCrafting().copy();
                if (!player.getInventory().add(bonus)) {
                    player.drop(bonus, false);
                }
            }
        });
    }

    // ========== 辅助方法 ==========

    /**
     * 判断本次伤害是否由玩家造成：
     * 1) 伤害来源实体直接就是玩家（近战挥砍、多数模组技能/召唤物以玩家为来源）
     * 2) 直接实体是玩家拥有的抛射物（弓箭、三叉戟等）
     * 用于让范围伤害、吸血等攻击附加效果在更多攻击方式下稳定触发
     */
    private static ServerPlayer getPlayerAttacker(LivingHurtEvent event) {
        var source = event.getSource();
        if (source.getEntity() instanceof ServerPlayer sp) return sp;
        var direct = source.getDirectEntity();
        if (direct instanceof net.minecraft.world.entity.projectile.Projectile p
                && p.getOwner() instanceof ServerPlayer sp) {
            return sp;
        }
        return null;
    }

    private static void addXpAndSync(ServerPlayer player, PlayerStats stats, long xp) {
        boolean leveledUp = stats.addExperience(xp);
        NetworkHandler.syncToClient(player);
        if (leveledUp) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("message.infinitestats.level_up",
                            stats.getLevel(), stats.getAvailablePoints()),
                    true
            );
        }
    }
}