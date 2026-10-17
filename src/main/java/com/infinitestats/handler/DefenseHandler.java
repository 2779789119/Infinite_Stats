package com.infinitestats.handler;

import com.infinitestats.Config;
import com.infinitestats.event.StatEventHandler;
import com.infinitestats.network.NetworkHandler;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.StatCategory;
import com.infinitestats.stats.StatType;
import com.infinitestats.util.TeleportUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Iterator;
import java.util.Map;

/**
 * 防御类属性处理器
 * 处理：免疫、格挡、伤害减免、护盾、闪避、复活、反射、效果过滤（黑名单拦截 / 白名单保护）
 */
public class DefenseHandler implements StatEffectHandler {

    /** 虚空脱困防抖用的持久化 NBT 键（存在 player 实体上，随实体走）。 */
    private static final String VOID_ESCAPE_TICK = "infinitestats_void_escape_tick";

    @Override
    public String getId() {
        return "defense";
    }

    @Override
    public StatType[] getSupportedStats() {
        return StatType.getByCategory(StatCategory.DEFENSE);
    }

    @Override
    public void onTick(ServerPlayer player, PlayerStats stats, long tickCount) {
        // 掉出世界兜底：放在最前。等人受伤时往往已经在虚空底部，甚至已被原版移除，
        // 所以这里按坐标提前拉回（早于原版 -128 的掉出世界伤害）。
        if (handleVoidEscape(player, stats)) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("message.infinitestats.auto_revive_void"), true);
            NetworkHandler.syncToClient(player);
        }

        // 白名单（保护）模式：每 tick 采样受保护效果，并把被外力清除的补回。
        // 未处于保护模式时零开销，放在最前面是为了与外部的清除发生在同一 tick 内（不成帧闪烁）。
        applyProtectedEffects(player, stats);

        // 黑名单拦截：每 tick 清扫。
        // 原先挂在 tickCount % 40 上，而 forceAddEffect / 直写效果表等路径绕过了
        // MobEffectEvent.Applicable，只能等清扫时才被移除 —— 毒 / 凋零 / 燃烧会在
        // 这段最长 2 秒的空窗里白跳好几跳伤害。名单为空或开关未开时零开销。
        applyDebuffImmunity(player, stats);

        // 无敌：每秒恢复满生命值
        if (tickCount % 20 == 0) {
            applyInvincibilityTick(player, stats);
        }

        // 按配置间隔处理生命恢复
        if (tickCount % Config.HEALTH_REGEN_INTERVAL.get() == 0) {
            applyHealthRegen(player, stats);
        }

        // 每2秒处理吸收护盾（setAbsorptionAmount 会同步实体数据，不宜每 tick 调用）
        if (tickCount % 40 == 0) {
            applyAbsorptionShield(player, stats);
        }
    }

    @Override
    public void onLogin(ServerPlayer player, PlayerStats stats) {
        applyAbsorptionShield(player, stats);
    }

    @Override
    public void onRespawn(ServerPlayer player, PlayerStats stats) {
        applyAbsorptionShield(player, stats);
    }

    @Override
    public void onDimensionChange(ServerPlayer player, PlayerStats stats) {
        applyAbsorptionShield(player, stats);
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    /**
     * 应用生命恢复（正=回血，负=扣血）
     */
    private void applyHealthRegen(ServerPlayer player, PlayerStats stats) {
        float regen = stats.getStatValue(StatType.fromId("health_regen"));
        if (regen != 0 && player.getHealth() > 0) {
            if (regen > 0 && player.getHealth() < player.getMaxHealth()) {
                player.heal(regen);
            } else if (regen < 0) {
                // 负回血 = 生命流失（不能通过回血类型伤害触发，直接扣血）
                float damage = Math.min(-regen, player.getHealth() - 0.5f);
                if (damage > 0) {
                    player.hurt(player.level().damageSources().magic(), damage);
                }
            }
        }
    }

    /**
     * 应用吸收护盾
     * shield > 0 → 始终确保吸收生效；shield == 0 → 仅当由本模组提供时才移除
     */
    public static void applyAbsorptionShield(ServerPlayer player, PlayerStats stats) {
        float shield = Math.max(0, stats.getStatValue("absorption_shield"));
        float owned = stats.getShieldAbsorption();
        // 迁移旧版由本模组施加的短时吸收药水，避免其到期扣除新护盾。
        if (owned < 0) {
            var effect = player.getEffect(MobEffects.ABSORPTION);
            if (stats.isProviding("absorption_shield") && effect != null
                    && effect.getDuration() <= 100 && !effect.isVisible()
                    && !isProtected(stats, MobEffects.ABSORPTION)) {
                player.removeEffect(MobEffects.ABSORPTION);
            }
            owned = 0;
        }
        float remaining = Math.max(0, player.getAbsorptionAmount() - owned);
        player.setAbsorptionAmount(remaining + shield);
        stats.setShieldAbsorption(shield);
        stats.setProviding("absorption_shield", shield > 0);
    }

    public static void trackAbsorptionUse(ServerPlayer player, PlayerStats stats) {
        stats.setShieldAbsorption(Math.min(stats.getShieldAbsorption(), player.getAbsorptionAmount()));
    }

    /**
     * 驱散负面药水效果（debuff_immunity 开关 + 黑名单模式），每 tick 调用。
     * <p>
     * 支持玩家自定义过滤列表（ID / {@code ns:*} 通配 / {@code #标签}），
     * 可选择过滤所有效果（包括正面buff和负面debuff）。
     */
    private void applyDebuffImmunity(ServerPlayer player, PlayerStats stats) {
        if (!stats.isToggleActive("debuff_immunity") || !stats.hasBlockingEffects()) return;

        var effects = player.getActiveEffects();
        if (effects.isEmpty()) return;
        // 遍历副本，安全移除
        for (MobEffectInstance inst : new java.util.ArrayList<>(effects)) {
            if (inst.getEffect() == null) continue;

            String effectId = effectId(inst.getEffect());
            if (effectId == null) continue;
            // 本模组自己施加的 Utility 效果（夜视 / 隐身 / 幸运）不受黑名单影响：
            // 否则名单里写 minecraft:* 或 * 会把自己刚加上的效果每 2 秒清一次（闪烁）
            if (StatEventHandler.isOwnUtilityEffect(stats, effectId)) continue;
            if (stats.shouldBlockEffect(effectId)) {
                player.removeEffect(inst.getEffect());
            }
        }
    }

    // ======================== 白名单「保护」 ========================

    /**
     * 白名单（保护）模式的每 tick 维护：
     * <ol>
     *   <li><b>采样</b>：名单里的效果还在身上 → 用当前实例刷新快照（等级 / 粒子 / 剩余时长）；</li>
     *   <li><b>补回</b>：快照里有、身上却没了 → 说明被外力清掉了，只要还没到自然结束时间，
     *       就按剩余时长重新施加（{@code forceAddEffect}，绕过其它模组在
     *       {@code MobEffectEvent.Applicable} 上的拦截 —— 保护就是拒绝外力干预）。</li>
     * </ol>
     * 快照里的剩余时长逐 tick 递减，耗尽即视为「自然结束」，撤销保护不再补回。
     */
    private void applyProtectedEffects(ServerPlayer player, PlayerStats stats) {
        if (!stats.hasProtectedEffects() || player.isDeadOrDying()) return;

        Map<String, PlayerStats.ProtectedEffect> snapshots = stats.getProtectedSnapshots();

        for (MobEffectInstance inst : player.getActiveEffects()) {
            if (inst.getEffect() == null) continue;
            String id = effectId(inst.getEffect());
            if (id == null || !stats.isProtectedEffect(id)) continue;
            snapshots.computeIfAbsent(id, k -> new PlayerStats.ProtectedEffect()).capture(inst);
        }

        if (snapshots.isEmpty()) return;

        Iterator<Map.Entry<String, PlayerStats.ProtectedEffect>> it = snapshots.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, PlayerStats.ProtectedEffect> entry = it.next();
            if (!stats.isProtectedEffect(entry.getKey())) {
                // 玩家已取消保护
                it.remove();
                continue;
            }
            MobEffect effect = effectById(entry.getKey());
            if (effect == null) {
                // 效果已随模组卸载而消失，补不回来
                it.remove();
                continue;
            }
            if (player.hasEffect(effect)) continue;
            if (!entry.getValue().tickDown()) {
                // 剩余时长耗尽 = 自然结束，放手
                it.remove();
                continue;
            }
            player.forceAddEffect(entry.getValue().toInstance(effect), player);
        }
    }

    /**
     * 一次性把保护中的效果补回。
     * <p>
     * 给 {@code player.removeAllEffects()} 这类「一把全清」的路径用：本模组自己清的时候
     * 直接跳过保护项即可，但像自动复活的「全部清除」是原版 API，只能清完立刻补回来。
     */
    public static void restoreProtectedEffects(ServerPlayer player, PlayerStats stats) {
        if (!stats.hasProtectedEffects() || player.isDeadOrDying()) return;

        for (Map.Entry<String, PlayerStats.ProtectedEffect> entry
                : stats.getProtectedSnapshots().entrySet()) {
            if (!stats.isProtectedEffect(entry.getKey())) continue;
            MobEffect effect = effectById(entry.getKey());
            if (effect == null || player.hasEffect(effect)) continue;
            player.forceAddEffect(entry.getValue().toInstance(effect), player);
        }
    }

    /** 该效果是否受白名单保护（保护模式未开启时恒为 false）。 */
    public static boolean isProtected(PlayerStats stats, MobEffect effect) {
        if (stats == null || effect == null || !stats.isProtectionMode()) return false;
        String id = effectId(effect);
        return id != null && stats.isProtectedEffect(id);
    }

    /** 效果 ID；未注册的效果返回 {@code null}（避免 {@code getKey} 返回 null 时 NPE）。 */
    private static String effectId(MobEffect effect) {
        ResourceLocation key = ForgeRegistries.MOB_EFFECTS.getKey(effect);
        return key == null ? null : key.toString();
    }

    /** 由 ID 反查效果；ID 非法或未注册时返回 {@code null}。 */
    private static MobEffect effectById(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? null : ForgeRegistries.MOB_EFFECTS.getValue(rl);
    }

    /**
     * 处理复活逻辑
     * @return true表示复活成功，取消死亡
     */
    public static boolean handleAutoRevive(ServerPlayer player, PlayerStats stats) {
        if (!stats.isToggleActive("auto_revive")) return false;

        long gameTick = player.level().getGameTime();
        long cooldownTicks = Config.AUTO_REVIVE_COOLDOWN.get() * 20L;

        if (gameTick - stats.getLastReviveTime() >= cooldownTicks) {
            // 取 1 点保底：最大生命被其它模组压到 1~3 点时，按百分比算出来可能是 0 血、
            // 复活后立刻再死（还会白白吃掉冷却）。
            float reviveHealth = Math.min(player.getMaxHealth(),
                    Math.max(1.0f, player.getMaxHealth() * Config.AUTO_REVIVE_HEALTH_PERCENT.get().floatValue()));
            player.setHealth(reviveHealth);
            player.setAirSupply(player.getMaxAirSupply());

            // 清除效果：默认只清负面效果以保留其他模组的增益Buff；关闭则该回全部清除（旧行为）
            // 白名单保护中的效果两者都跳过（保护语义：自然结束前不接受外力清除，复活清效果也算外力）
            if (Config.AUTO_REVIVE_CLEAR_DEBUFFS_ONLY.get()) {
                for (MobEffectInstance inst : new java.util.ArrayList<>(player.getActiveEffects())) {
                    if (inst.getEffect() == null || inst.getEffect().isBeneficial()) continue;
                    if (isProtected(stats, inst.getEffect())) continue;
                    player.removeEffect(inst.getEffect());
                }
            } else {
                player.removeAllEffects();
                // 原版 API 没法「选择性清除」，清完立刻把保护项补回来
                restoreProtectedEffects(player, stats);
            }

            // 补满饥饿与饱食度（默认开启），避免复活后立刻饿死
            if (Config.AUTO_REVIVE_REFILL_FOOD.get()) {
                player.getFoodData().setFoodLevel(20);
                player.getFoodData().setSaturation(20.0f);
            }

            // 复活后短暂伤害免疫，防止在危险地点（岩浆/敌群）立刻再次死亡
            int invulnSeconds = Config.AUTO_REVIVE_INVULN_SECONDS.get();
            stats.setReviveInvulnUntilTick(invulnSeconds > 0 ? gameTick + invulnSeconds * 20L : 0);

            stats.setLastReviveTime(gameTick);
            return true;
        }
        return false;
    }

    /**
     * 致死伤害预拦截：在 {@code LivingDamageEvent} 里判断「这一下会打死玩家」，
     * 命中就复活并把伤害清零 —— 玩家完全不会进入死亡流程。
     * <p>
     * 为什么还要这一层：死亡事件虽然能拦住 {@code /kill}（它也走 {@code hurt}），但那时原版已经
     * 走完扣血、图腾判定、死亡掉落与统计，任何挂在 {@code die} 上的模组逻辑都插过队了；
     * 而且冷却期内挨一下 {@code Float.MAX_VALUE} 的伤害，人就没了。在伤害结算阶段就截断，
     * 可以保证「没有任何一次致死扣血真正落地」。
     * <p>
     * {@code incoming} 是护甲 / 减伤 / 其它模组限伤结算后的最终扣血量。
     */
    public static boolean handleLethalDamage(ServerPlayer player, PlayerStats stats, float incoming) {
        if (!Config.AUTO_REVIVE_PREVENT_LETHAL.get()) return false;
        if (incoming <= 0 || !Float.isFinite(incoming)) return false;
        // LivingDamageEvent 拿到的是吸收护盾之前的值：先扣掉护盾再判致死，
        // 否则「有盾挡得住」的伤害也会被当成致死伤害，白白消耗一次复活冷却。
        float afterAbsorption = Math.max(0f, incoming - player.getAbsorptionAmount());
        // 只有「这一下确实会致死」才接管，普通受伤仍走原版结算（吸血 / 反弹等逻辑不受影响）
        if (player.getHealth() - afterAbsorption > 0) return false;
        return handleAutoRevive(player, stats);
    }

    /**
     * 掉出世界（虚空 / 世界底部）脱困。
     * <p>
     * <b>不检查、不消耗复活冷却</b>：虚空伤害是每 tick 的持续伤害，靠死亡事件复活一次后
     * 人还在虚空里，下一 tick 继续掉 —— 冷却没走完就彻底没救。所以这条路径只负责「把人拉回来」。
     * <p>
     * 触发点比原版早：主世界默认 y &lt; -72 就拉回，而原版要到 -128 才开始扣掉出世界伤害，
     * 因此多数情况下玩家连伤害都吃不到；下界 / 末地的世界底部是 0，默认 y &lt; -8 触发。
     */
    public static boolean handleVoidEscape(ServerPlayer player, PlayerStats stats) {
        return handleVoidEscape(player, stats, false);
    }

    /**
     * @param allowDying 死亡事件里调用时传 {@code true}：此时血量已经归零，但死亡还没真正落地
     *                   （事件被取消即可），可以放心拉回来。tick / 受伤路径必须传 {@code false}，
     *                   否则会把已经进入死亡界面的玩家强行救活，客户端状态对不上。
     */
    public static boolean handleVoidEscape(ServerPlayer player, PlayerStats stats, boolean allowDying) {
        if (!Config.AUTO_REVIVE_VOID_ESCAPE.get()) return false;
        if (!stats.isToggleActive("auto_revive")) return false;
        if (player.isRemoved()) return false;
        if (!allowDying && player.isDeadOrDying()) return false;
        if (!isOutOfWorld(player)) return false;

        // 防抖：传送包要等客户端确认后服务端位置才稳定，这期间坐标还在世界底部，
        // 不设闸门就会每 tick 传一次（跨维度分支尤其重）。
        long now = player.level().getGameTime();
        if (now - player.getPersistentData().getLong(VOID_ESCAPE_TICK) < 20L) return false;
        player.getPersistentData().putLong(VOID_ESCAPE_TICK, now);

        // 在虚空里往往已经掉到个位数血，拉回后不回血的话落地很容易被怪补刀
        float target = player.getMaxHealth() * Config.AUTO_REVIVE_HEALTH_PERCENT.get().floatValue();
        player.setHealth(Math.min(player.getMaxHealth(),
                Math.max(player.getHealth(), Math.max(1.0f, target))));
        player.setAirSupply(player.getMaxAirSupply());

        int invulnSeconds = Config.AUTO_REVIVE_INVULN_SECONDS.get();
        if (invulnSeconds > 0) {
            stats.setReviveInvulnUntilTick(player.level().getGameTime() + invulnSeconds * 20L);
        }

        teleportToSafety(player);
        return true;
    }

    /** 玩家是否已掉到世界底部之外（比原版掉出世界的伤害判定更早，留出救援余量）。 */
    public static boolean isOutOfWorld(ServerPlayer player) {
        return player.getY() < player.level().getMinBuildHeight() - Config.AUTO_REVIVE_VOID_ESCAPE_DEPTH.get();
    }

    /**
     * 送回重生点（没有重生点则回当前维度的世界出生点），并挑一个不会卡在方块里的落点。
     * <p>
     * 跨维度走 {@link TeleportUtil#forceTeleportTo}，它复制了原版传送流程但跳过
     * {@code onTravelToDimension} 门控，避免被其它模组的维度黑名单拦下、人卡在虚空里。
     */
    private static void teleportToSafety(ServerPlayer player) {
        MinecraftServer server = player.server;
        ServerLevel target = player.serverLevel();
        BlockPos origin = null;

        BlockPos respawn = player.getRespawnPosition();
        if (respawn != null) {
            ServerLevel respawnLevel = server.getLevel(player.getRespawnDimension());
            if (respawnLevel != null) {
                target = respawnLevel;
                origin = respawn;
            }
        }
        if (origin == null) origin = target.getSharedSpawnPos();

        BlockPos safe = findSafeSpot(target, origin);
        TeleportUtil.forceTeleportTo(player, target, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5,
                player.getYRot(), player.getXRot());
        player.setDeltaMovement(0, 0, 0);
        player.fallDistance = 0;
    }

    /** 以 origin 所在列为准，从地表往上找一处「脚下不是岩浆、头顶两格空」的落脚点。 */
    private static BlockPos findSafeSpot(ServerLevel level, BlockPos origin) {
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, origin);
        BlockPos.MutableBlockPos pos = surface.mutable();
        int maxY = level.getMaxBuildHeight() - 3;
        if (pos.getY() > maxY) pos.setY(maxY);

        for (int i = 0; i < 16 && pos.getY() <= maxY; i++, pos.move(0, 1, 0)) {
            if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
                    && !level.getFluidState(pos.below()).is(FluidTags.LAVA)) {
                return pos.immutable();
            }
        }
        return pos.immutable();
    }

    /**
     * 复活无敌窗口内：玩家免疫一切伤害，避免复活瞬间在原地再次致死却因冷却无法再复活。
     */
    public static boolean isReviveInvulnerable(ServerPlayer player, PlayerStats stats) {
        return player.level().getGameTime() < stats.getReviveInvulnUntilTick();
    }

    /**
     * 判定是否免疫伤害（invincibility 开关开启时取消所有伤害）
     */
    public static boolean isInvincible(PlayerStats stats) {
        if (!stats.isToggleActive("invincibility")) return false;

        // 恢复满生命值
        return true;
    }

    /**
     * 为无敌玩家补满生命值
     */
    public static void applyInvincibilityTick(ServerPlayer player, PlayerStats stats) {
        if (stats.isToggleActive("invincibility")) {
            if (player.getHealth() < player.getMaxHealth()) {
                player.setHealth(player.getMaxHealth());
            }
        }
    }

    /**
     * 处理格挡逻辑
     * @return true表示格挡成功，取消伤害
     */
    public static boolean handleBlock(ServerPlayer player, PlayerStats stats) {
        float blockChance = stats.getStatValue(StatType.fromId("block_chance"));
        if (blockChance > 0 && player.getRandom().nextFloat() < blockChance) {
            return true;
        }
        return false;
    }

    /**
     * 处理闪避逻辑
     * @return true表示闪避成功，取消伤害
     */
    public static boolean handleDodge(ServerPlayer player, PlayerStats stats) {
        float dodgeChance = stats.getStatValue(StatType.fromId("dodge_chance"));
        if (dodgeChance > 0 && player.getRandom().nextFloat() < dodgeChance) {
            return true;
        }
        return false;
    }

    /**
     * 计算伤害减免后的伤害值（正=减伤，负=增伤）
     */
    public static float applyDamageReduction(PlayerStats stats, float amount) {
        float reduction = stats.getStatValue(StatType.fromId("damage_reduction"));
        if (reduction != 0) {
            float factor = 1.0f - reduction;
            if (factor <= 0) return 0; // 减免≥100% → 免疫
            return amount * factor;
        }
        return amount;
    }

    /**
     * 处理摔落伤害减免（正=减免，负=增伤）
     */
    public static float applyFallDamageReduction(PlayerStats stats, float amount, boolean isFall) {
        if (!isFall) return amount;

        float fallResist = stats.getStatValue(StatType.fromId("fall_resist"));
        if (fallResist != 0) {
            float factor = 1.0f - fallResist;
            // 减免≥100% → 仍有至少10%伤害残留，避免完全免疫（no_fall_damage开关才是完全免疫）
            amount *= Math.max(0.1f, factor);
        }

        // 免疫摔落开关（优先于任何计算）
        if (stats.isToggleActive("no_fall_damage")) {
            return 0;
        }
        return amount;
    }

    /**
     * 检查免疫类型
     */
    public static boolean isImmune(PlayerStats stats, net.minecraft.world.damagesource.DamageSource source, ServerPlayer player) {
        // 火焰免疫
        if (stats.isToggleActive("fire_immunity") && source.is(DamageTypeTags.IS_FIRE)) {
            return true;
        }
        // 弹射物免疫
        if (stats.isToggleActive("projectile_immunity") && source.is(DamageTypeTags.IS_PROJECTILE)) {
            return true;
        }
        // 爆炸免疫
        if (stats.isToggleActive("explosion_immunity") && source.is(DamageTypeTags.IS_EXPLOSION)) {
            return true;
        }
        // 窒息免疫 - inWall伤害没有实体来源，需通过player获取damageSources
        if (stats.isToggleActive("suffocation_immunity") &&
                source == player.level().damageSources().inWall()) {
            return true;
        }
        return false;
    }

    /**
     * 处理伤害反射
     */
    public static void applyDamageReflection(ServerPlayer player, PlayerStats stats, 
            float amount, LivingEntity attacker) {
        float reflection = stats.getStatValue(StatType.fromId("damage_reflection"));
        if (reflection > 0 && attacker != null && attacker != player) {
            float reflectDmg = amount * reflection;
            if (reflectDmg > 0) {
                attacker.hurt(player.level().damageSources().thorns(player), reflectDmg);
            }
        }
    }
}
