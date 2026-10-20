package com.infinitestats.handler;

import com.infinitestats.mixin.LivingEntityAccessor;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.StatCategory;
import com.infinitestats.stats.StatType;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * 攻击类属性处理器
 * 处理：暴击、护甲穿透、弹射物伤害、生命偷取、范围吸血、法力窃取
 */
public class AttackHandler implements StatEffectHandler {

    /**
     * 本模组"直接伤害"（范围/真实/降上限）使用的自定义伤害类型。
     * bypasses_armor=true 保留"无视护甲"语义；以玩家为来源实体，
     * 使击杀经由 die(playerSource) 正确归属玩家（掉落物 + 成就/进度/FTB任务）。
     */
    public static final ResourceKey<DamageType> DIRECT_DAMAGE =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("infinitestats", "direct_damage"));

    /** 与 StatEventHandler 共用同一个排查开关：-Dinfinitestats.debugTrueDamage=true */
    private static final boolean DEBUG_TRUE_DAMAGE =
            Boolean.getBoolean("infinitestats.debugTrueDamage");

    /**
     * 判断伤害来源是否为本模组的"直接伤害"，用于防止范围/真实伤害二次
     * 触发 StatEventHandler 的玩家攻击逻辑造成递归与无限循环。
     */
    public static boolean isDirectDamageSource(DamageSource src) {
        return src.is(DIRECT_DAMAGE);
    }

    // 一次 hurt 的两个 Forge 阶段用目标与同一个 DamageSource 配对；次级攻击各自独立。
    private static final ThreadLocal<Map<LivingEntity, Map<DamageSource, Float>>> PENDING_TRUE_DAMAGE =
            ThreadLocal.withInitial(IdentityHashMap::new);

    /**
     * 一次 hurt 的「原始伤害」快照，由 {@code LivingHurtEvent} 的 HIGHEST 阶段写入。
     * <p>
     * 真伤基数必须取「攻击方本来该打出的伤害」：在 HIGHEST 这个时间点，{@code getAmount()}
     * 还没有被任何模组的限伤 / 减伤 / 易伤动过。若等到 LivingDamageEvent 再读，拿到的已经是
     * 受击方防御的结算结果，真伤就会跟着被削 —— 这正是要避免的。
     */
    private static final ThreadLocal<Map<LivingEntity, Map<DamageSource, Float>>> RAW_HURT =
            ThreadLocal.withInitial(IdentityHashMap::new);

    public static float calculateTrueDamage(PlayerStats stats, float baseDamage) {
        float percent = stats.getStatValue("true_damage");
        if (baseDamage <= 0 || percent <= 0 || !Float.isFinite(baseDamage) || !Float.isFinite(percent)) return 0;
        return (float) Math.min(Float.MAX_VALUE, (double) baseDamage * percent);
    }

    /** 记录本次 hurt 的原始伤害（HIGHEST 阶段）。 */
    public static void rememberRawHurt(LivingEntity target, DamageSource source, float amount) {
        if (!Float.isFinite(amount) || amount < 0) return;
        RAW_HURT.get().computeIfAbsent(target, ignored -> new IdentityHashMap<>()).put(source, amount);
    }

    /** 取走本次 hurt 的原始伤害快照；没有快照时返回 0，调用方回退到事件里的数值。 */
    public static float takeRawHurt(LivingEntity target, DamageSource source) {
        Map<LivingEntity, Map<DamageSource, Float>> byTarget = RAW_HURT.get();
        Map<DamageSource, Float> hits = byTarget.get(target);
        if (hits == null) return 0;
        Float amount = hits.remove(source);
        if (hits.isEmpty()) byTarget.remove(target);
        return amount == null ? 0 : amount;
    }

    /**
     * 攻击者侧的近战面板伤害估算：面板攻击力 × 蓄力系数 + 附魔伤害加成。
     * <p>
     * 为什么需要它：有些模组（例如 LegendaryMonsters）的「单次伤害上限 / 减伤」是在实体自己的
     * {@code hurt()} 覆盖里改 amount 的，发生在 Forge 的 {@code LivingHurtEvent} <b>之前</b>，
     * 任何事件里读到的数值都已经被钳过。真伤要无视这类限伤，基数就只能自己按攻击者面板算。
     * <p>
     * 与 vanilla {@code Player#attack} 的口径保持一致，只是不含原版暴击（暴击由本模组的
     * 暴击倍率另行处理，且事件链上会用 {@code max(快照, 面板估算)} 兜住）。
     */
    public static float estimateMeleeDamage(ServerPlayer player, LivingEntity target) {
        float base = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        base *= player.getAttackStrengthScale(0.5f);
        base += net.minecraft.world.item.enchantment.EnchantmentHelper
                .getDamageBonus(player.getMainHandItem(), target.getMobType());
        return Float.isFinite(base) && base > 0 ? base : 0;
    }

    /**
     * 估算这次攻击「本来该打出的伤害」，供真伤取基数。
     * <p>
     * 取 HIGHEST 阶段的原始伤害快照（可能已被目标在 {@code hurt()} 里钳过）与攻击者面板估算的
     * <b>较大值</b>：面板估算不会被任何限伤影响，快照则能带上原版暴击 / 附魔之外的加成。
     * 两者取大，既不会被限伤压小，也不会因为面板估算偏低而缩水。
     */
    public static float calculateProjectedTrueDamage(ServerPlayer player, PlayerStats stats,
                                                     net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        float raw = takeRawHurt(event.getEntity(), event.getSource());
        if (!(raw > 0)) raw = event.getAmount();
        boolean melee = event.getSource().getDirectEntity() instanceof ServerPlayer;
        if (melee) raw = Math.max(raw, estimateMeleeDamage(player, event.getEntity()));
        boolean isProjectile = event.getSource().getDirectEntity() instanceof Projectile;
        boolean isFullAttack = player.getAttackStrengthScale(0.5f)
                > com.infinitestats.Config.FULL_CHARGE_THRESHOLD.get().floatValue();
        float projected = raw
                * calculateCritMultiplier(player, stats, isFullAttack)
                * calculateProjectileBonus(stats, isProjectile);
        return calculateTrueDamage(stats, projected);
    }

    public static void rememberTrueDamage(LivingEntity target, DamageSource source, float amount) {
        if (amount <= 0 || !Float.isFinite(amount)) return;
        PENDING_TRUE_DAMAGE.get().computeIfAbsent(target, ignored -> new IdentityHashMap<>()).put(source, amount);
    }

    public static float takeTrueDamage(LivingEntity target, DamageSource source) {
        var byTarget = PENDING_TRUE_DAMAGE.get();
        var hits = byTarget.get(target);
        if (hits == null) return 0;
        Float amount = hits.remove(source);
        if (hits.isEmpty()) byTarget.remove(target);
        return amount == null ? 0 : amount;
    }

    public static void clearPendingTrueDamage() {
        PENDING_TRUE_DAMAGE.remove();
        RAW_HURT.remove();
    }

    @Override
    public String getId() {
        return "attack";
    }

    @Override
    public StatType[] getSupportedStats() {
        return StatType.getByCategory(StatCategory.ATTACK);
    }

    @Override
    public void onTick(ServerPlayer player, PlayerStats stats, long tickCount) {
        // 排斥：持续推开周围的敌对生物与它们射出的弹射物
        applyRepulsion(player, stats, tickCount);
    }

    @Override
    public void onLogin(ServerPlayer player, PlayerStats stats) {
        // 无需初始化
    }

    @Override
    public void onRespawn(ServerPlayer player, PlayerStats stats) {
        // 无需处理
    }

    @Override
    public void onDimensionChange(ServerPlayer player, PlayerStats stats) {
        // 无需处理
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    /**
     * 计算暴击伤害倍率
     */
    public static float calculateCritMultiplier(ServerPlayer player, PlayerStats stats, boolean isFullAttack) {
        if (!isFullAttack) return 1.0f;

        float critChance = stats.getStatValue(StatType.fromId("crit_chance"));
        critChance = Math.min(critChance, com.infinitestats.Config.CRIT_CHANCE_CAP.get().floatValue());
        if (critChance > 0 && player.getRandom().nextFloat() < critChance) {
            float critDamage = stats.getStatValue(StatType.fromId("crit_damage"));
            return com.infinitestats.Config.CRIT_BASE_MULTIPLIER.get().floatValue() + critDamage;
        }
        return 1.0f;
    }

    /**
     * 计算护甲穿透增伤
     */
    public static float applyArmorPenetration(PlayerStats stats, LivingEntity target,
                                             DamageSource source, float amount) {
        float penCap = com.infinitestats.Config.ARMOR_PENETRATION_CAP.get().floatValue();
        float penetration = Math.min(penCap, Math.max(0, stats.getStatValue("armor_penetration")));
        if (penetration == 0 || amount <= 0 || source.is(DamageTypeTags.BYPASSES_ARMOR)) return amount;
        return compensateArmor(amount, target.getArmorValue(),
                (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS), penetration);
    }

    public static float compensateArmor(float amount, float armor, float toughness, float penetration) {
        if (armor <= 0 || penetration <= 0 || !Float.isFinite(amount)) return amount;
        float desired = CombatRules.getDamageAfterAbsorb(amount,
                armor * (1 - Math.min(1, penetration)), toughness);
        // LivingHurt 在原版护甲结算之前发生，反求输入以得到穿透后的实际伤害。
        float low = amount;
        float high = Math.max(amount, desired * 5);
        for (int i = 0; i < 32; i++) {
            float mid = low + (high - low) * 0.5f;
            if (CombatRules.getDamageAfterAbsorb(mid, armor, toughness) < desired) low = mid;
            else high = mid;
        }
        return high;
    }

    /**
     * 计算弹射物伤害增伤
     */
    public static float calculateProjectileBonus(PlayerStats stats, boolean isProjectile) {
        if (!isProjectile) return 1.0f;
        return 1.0f + stats.getStatValue(StatType.fromId("projectile_damage"));
    }

    /**
     * 应用生命偷取效果
     *
     * @param damageAmount 结算后的实际伤害（护甲 / 减伤已扣除）。传入面板伤害会导致
     *                     打高护甲或带限伤的目标时严重虚高，调用点见
     *                     {@code StatEventHandler#onLivingDamage}。
     */
    public static void applyLifeSteal(ServerPlayer player, PlayerStats stats, float damageAmount) {
        float lifeSteal = Math.min(stats.getStatValue(StatType.fromId("life_steal")),
                com.infinitestats.Config.LIFE_STEAL_CAP.get().floatValue());
        if (lifeSteal > 0 && damageAmount > 0) {
            player.heal(damageAmount * lifeSteal);
        }
    }

    /**
     * 应用范围吸血效果
     */
    public static void applyAoeLifeSteal(ServerPlayer player, PlayerStats stats, float damageAmount, LivingEntity target) {
        float aoeSteal = stats.getStatValue(StatType.fromId("life_steal_aoe"));
        if (aoeSteal <= 0 || damageAmount <= 0) return;

        double range = com.infinitestats.Config.AOE_LIFE_STEAL_RANGE.get();
        AABB area = new AABB(
                target.getX() - range, target.getY() - range, target.getZ() - range,
                target.getX() + range, target.getY() + range, target.getZ() + range
        );

        List<LivingEntity> nearby = player.level().getEntitiesOfClass(LivingEntity.class, area,
                e -> e.isAlive() && e != player && e != target && !e.isAlliedTo(player));

        float totalAoeDamage = 0;
        for (LivingEntity nearbyMob : nearby) {
            float aoeDmg = (float) (damageAmount * aoeSteal
                    * com.infinitestats.Config.AOE_LIFE_STEAL_DAMAGE_RATIO.get());
            // 使用本模组"直接伤害"类型：因其 message_id 命中 isDirectDamageSource，
            // 受击实体再次进入 onLivingHurt 时会被拦截，避免对周围实体造成 playerAttack
            // 伤害后反复重入攻击分支形成同步无限递归（最终 StackOverflow 并崩溃）。
            float before = nearbyMob.getHealth() + nearbyMob.getAbsorptionAmount();
            applyDirectDamage(player, nearbyMob, aoeDmg);
            // 只按真正被承受的伤害回血：被护甲 / 抗性 / 无敌削减或完全免疫的部分不再计入，
            // 避免出现"打不动的敌人也照常吸血"。
            totalAoeDamage += Math.max(0f,
                    before - nearbyMob.getHealth() - nearbyMob.getAbsorptionAmount());
        }

        if (totalAoeDamage > 0) {
            player.heal((float) (totalAoeDamage * com.infinitestats.Config.AOE_LIFE_STEAL_HEAL_RATIO.get()));
        }
    }

    /**
     * 应用真实伤害：基于本次伤害的额外真实伤害，直接削减生命（无视护甲与一切减伤）
     *
     * 注意：这是「独立结算」入口，内部仍走正式 hurt() 流程（为了有原版结算收尾），
     * 因此在该流程之外触发的限伤仍可能削减它。事件链上的真伤不走这里，
     * 而是由 {@link #calculateProjectedTrueDamage} + {@link #applyUnavoidableTrueDamage} 直接扣血。
     */
    public static void applyTrueDamage(ServerPlayer player, PlayerStats stats, float baseDamage, LivingEntity target) {
        float trueDmg = calculateTrueDamage(stats, baseDamage);
        if (trueDmg <= 0) return;

        applyDirectDamage(player, target, trueDmg);
    }

    /**
     * 真伤落地：直接扣血，完全脱离伤害管线。
     * <p>
     * 为什么不能叠加进 LivingDamageEvent.getAmount()：那样真伤仍在管线内，会被后续环节
     * 连带削减——同优先级（LOWEST）注册顺序靠后的监听器、原版在 LivingDamageEvent 之后
     * 才执行的 getDamageAfterMagicAbsorb（抗性提升 / 保护附魔）与吸收（absorption）、
     * 以及任何 Mixin 到 actuallyHurt 的「单次伤害上限（限伤）」实现。
     * <p>
     * 死亡处理按目标类型分开：
     * <ul>
     *   <li><b>非玩家目标</b>：血量扣到 0 时由本模组强制收尾死亡（{@link #forceKill}）——
     *       掉落 / 经验 / 击杀归属走 {@code die} 的正常结算；若死亡被第三方复活类模组取消，
     *       直接移除实体，保证「真伤击杀不可被别家取消」。</li>
     *   <li><b>玩家目标</b>：先过本模组的致死预拦截（自动复活），没拦住才扣血；扣到 0 走原版
     *       {@code die}，玩家死亡与重生流程保持标准（玩家实体无法像生物那样强删）。</li>
     * </ul>
     *
     * @param attacker 伤害归属玩家，不可为 null（真伤只在玩家攻击分支里产生）
     */
    public static void applyUnavoidableTrueDamage(LivingEntity target, float amount, ServerPlayer attacker) {
        if (attacker == null || amount <= 0 || !Float.isFinite(amount)) return;
        if (target.isRemoved() || target.isDeadOrDying()) return;
        double hp = target.getHealth();
        if (hp <= 0) return;

        DamageSource source = attacker.damageSources().playerAttack(attacker);

        if (target instanceof ServerPlayer victim) {
            // 真伤穿盾：按「不扣吸收护盾」的口径判致死，否则有盾挡着也会白耗一次复活冷却。
            boolean revived = victim.getCapability(com.infinitestats.stats.PlayerStatsProvider.PLAYER_STATS)
                    .map(stats -> DefenseHandler.handleLethalDamage(
                            victim, stats, (float) Math.min(hp, amount), false))
                    .orElse(false);
            if (revived) return;

            victim.setHealth((float) Math.max(0.0, hp - amount));
            if (victim.getHealth() <= 0.0F) {
                victim.setLastHurtByPlayer(attacker);
                victim.die(source);
            }
            return;
        }

        target.setHealth((float) Math.max(0.0, hp - amount));
        // 校验直接扣血是否真的生效：个别模组的 Boss 会锁血 / 在 setHealth 上做手脚
        // （实测 LegendaryMonsters 的坠落圣骑：setHealth 之后血量纹丝不动，而 hurt() 照常结算）。
        // 这时退回正规伤害流程 —— 本模组的 direct_damage 带 bypasses_invulnerability，
        // 能跳过那类「在 hurt() 里钳上限」的实现。
        if (target.getHealth() > hp - amount + 1.0E-4) {
            hurtAsFallback(target, amount, attacker);
        }
        if (target.getHealth() <= 0.0F) {
            forceKill(target, attacker);
        }
    }

    /**
     * 直接扣血无效时的兜底：用本模组的 {@code direct_damage} 走一次 {@code hurt()}。
     * <p>
     * 注意两点：
     * <ul>
     *   <li>这个调用发生在原版本次主伤害<b>之前</b>（近战真伤挂在 AttackEntityEvent 上），
     *       {@code hurt()} 会把目标的 {@code invulnerableTime} 拉满，若不清零，紧随其后的主伤害
     *       就会被无敌帧吞掉 —— 所以调用后立刻清零，让主伤害照常结算；</li>
     *   <li>某些目标会直接拒绝 {@code hurt()}（卡位 / 睡眠阶段），那就只能放弃这次真伤，
     *       与它们对主伤害的态度保持一致。</li>
     * </ul>
     */
    private static void hurtAsFallback(LivingEntity target, float amount, ServerPlayer attacker) {
        if (amount <= 0 || !Float.isFinite(amount)) return;
        var holder = attacker.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DIRECT_DAMAGE);
        DamageSource source = new DamageSource(holder, attacker, attacker);
        float before = target.getHealth();
        target.hurt(source, amount);
        target.invulnerableTime = 0;
        if (DEBUG_TRUE_DAMAGE) {
            System.out.println("[InfiniteStats][TrueDamage] fallback-hurt target="
                    + target.getName().getString()
                    + " amount=" + amount + " hp=" + before + "->" + target.getHealth()
                    + " max=" + target.getMaxHealth()
                    + " class=" + target.getClass().getSimpleName());
        }
    }

    /**
     * 强制收尾真伤击杀：走 {@code die} 的正常结算（掉落 / 经验 / 击杀归属 / 成就）。
     * <p>
     * {@code die} 在 1.20.1 里自带 {@code !isRemoved() && !dead} 幂等守卫，外层原版收尾再调一次安全。
     * 正常走完会把姿态置为 {@code DYING}；若被第三方复活类模组取消（事件在 {@code die} 开头就被
     * 拦下），则停在原姿态 —— 真伤击杀不接受别家拦截，仍然移除实体，但移除前必须手动补一次
     * 死亡结算（见 {@link #settleDeathRewardsManually}），否则玩家拿不到掉落与经验。
     */
    private static void forceKill(LivingEntity target, ServerPlayer attacker) {
        if (target.isRemoved() || target.getPose() == Pose.DYING) return;
        DamageSource source = attacker.damageSources().playerAttack(attacker);
        target.setLastHurtByPlayer(attacker);
        target.setHealth(0.0F);
        target.die(source);
        // 正常死亡收尾：die 已经把掉落 / 经验 / 击杀归属都结算完，姿态也会被置为 DYING。
        if (target.isRemoved() || target.getPose() == Pose.DYING) return;
        // die 被第三方取消（给生物挂「复活」词条的模组、死亡拦截插件等）：原版在
        // LivingDeathEvent 之后立刻 return，掉落 / 经验 / 击杀统计全都没结算。真伤击杀
        // 仍然不接受别家拦截（继续移除实体），但必须先手动补一次死亡结算 ——
        // 否则这里 remove(KILLED) 抹掉实体后，玩家什么都拿不到。
        settleDeathRewardsManually(target, attacker, source);
        target.remove(Entity.RemovalReason.KILLED);
    }

    /**
     * 死亡被第三方取消时，手动补一次「原版 {@code die()} 里被跳过的那部分结算」。
     * <p>
     * 触发场景：带复活词条 / 死亡拦截的模组在 {@code LivingDeathEvent} 里
     * {@code setCanceled(true)}，原版 {@code LivingEntity#die} 的第一行判断失败后直接
     * {@code return}，于是 {@code killedEntity}（击杀统计 / 成就 / 进度）与
     * {@code dropAllDeathLoot}（战利品表 / 装备 / 经验 / {@code LivingDropsEvent}）都没执行。
     * <p>
     * {@code dropAllDeathLoot} 在 1.20.1 里是 {@code protected}，通过
     * {@link LivingEntityAccessor} 暴露；它内部已经包含 {@code dropExperience()} 与
     * {@code ForgeHooks.onLivingDrops}，所以只调这一个就够。掉落是否按玩家击杀 roll
     * 取决于 {@code lastHurtByPlayerTime}，调用前已由 {@link #forceKill} 设好。
     */
    private static void settleDeathRewardsManually(LivingEntity target, ServerPlayer attacker,
                                                   DamageSource source) {
        try {
            if (target.level() instanceof ServerLevel serverLevel) {
                // 与 die() 的顺序保持一致：先记击杀统计 / 成就，再结算掉落
                attacker.killedEntity(serverLevel, target);
            }
            ((LivingEntityAccessor) target).infinitestats$dropAllDeathLoot(source);
        } catch (Throwable t) {
            // 异常绝不能冒泡到 Forge 事件总线（详见 StatEventHandler.onLivingHurt 的同类处理）
            System.err.println("[infinitestats] 真伤击杀补掉落失败（已抑制）：target="
                    + target.getName().getString() + " class=" + target.getClass().getName());
            t.printStackTrace();
        }
        if (DEBUG_TRUE_DAMAGE) {
            System.out.println("[InfiniteStats][TrueDamage] manual-drops target="
                    + target.getName().getString()
                    + " class=" + target.getClass().getSimpleName()
                    + " hp=" + target.getHealth());
        }
    }

    /**
     * 直接削减生命值（无视护甲/减伤），并让击杀正确归属玩家。
     *
     * 注意：必须走正式的 hurt() 流程，而不是直接 setHealth()。
     * 原因：
     *  - setHealth 不会触发 die(玩家伤害来源)，导致死亡来源不是玩家，
     *    成就/进度（FTB kill 任务、L2Hostility 难度判定）不触发；
     *  - setHealth 不会设置 lastHurtByPlayerTime，而掉落物靠该字段判断是否
     *    归玩家所有，所以直接扣血杀死的怪不会掉落物品（表现为"有时不掉"）。
     * 这里使用 bypasses_armor 的自定义伤害类型，既保留"无视护甲"语义，
     * 又让死亡经由 die(playerSource) 正确归属玩家（掉落 + 成就都正常）。
     */
    private static void applyDirectDamage(ServerPlayer player, LivingEntity target, float amount) {
        if (amount <= 0) return;
        var holder = player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DIRECT_DAMAGE);
        DamageSource src = new DamageSource(holder, player, player);
        target.hurt(src, amount);
    }

    /** 本模组"攻击减血上限"使用的累计负修饰符 UUID，直接削减 MAX_HEALTH 总值（含其他模组加成） */
    private static final UUID REDUCE_MAX_HEALTH_UUID =
            UUID.fromString("a1b2c3d4-0000-4e5f-8a9b-0c1d2e3f4a5b");

    /**
     * 应用攻击减血量上限：每次攻击降低目标最大生命"总值"（每点 -1 点，最低保留 1 点）
     * 通过本模组的负 ADDITION 修饰符累计削减，会把其他模组（Apotheosis 词缀/宝石/盔甲/饰品等）
     * 的加成也一同削掉——即真正削减"总值"，而非只削基础值。触底（总值=1）后停止，不再造成额外伤害。
     */
    public static void applyReduceMaxHealth(ServerPlayer player, PlayerStats stats, LivingEntity target) {
        float reduce = stats.getStatValue(StatType.fromId("reduce_max_health"));
        if (reduce <= 0) return;

        // 兜底：绝不给攻击者自己扣上限。自伤已在 getPlayerAttacker 里被排除，
        // 这里再挡一层，确保无论如何都不会让玩家把自己永久削成 1 点血。
        if (target == player) return;

        var maxHp = target.getAttribute(Attributes.MAX_HEALTH);
        if (maxHp == null) return;

        // 乘算倍率折算：Minecraft 属性公式中 ADDITION 修饰符先加进基数、再被
        // MULTIPLY_BASE / MULTIPLY_TOTAL 乘算放大。L2Hostility（莱特兰）给高等级怪
        // 提血量用的正是乘算修饰符——若不折算，本模组 -1 的 ADDITION 会被放大为
        // -1 × 倍率，表现为"莱特兰等级越高、削减越多，无等级时削减正常"。
        double multBase = 1.0, multTotal = 1.0;
        for (AttributeModifier m : maxHp.getModifiers()) {
            AttributeModifier.Operation op = m.getOperation();
            if (op == AttributeModifier.Operation.MULTIPLY_BASE) multBase += m.getAmount();
            else if (op == AttributeModifier.Operation.MULTIPLY_TOTAL) multTotal *= 1.0 + m.getAmount();
        }
        double scale = Math.max(multBase * multTotal, 1e-6);     // 防御非法倍率（0/负值）

        double curTotal = maxHp.getValue();                       // 当前总值（含所有加成）
        double targetTotal = Math.max(1.0, curTotal - reduce);   // 目标总值
        double actualReduce = curTotal - targetTotal;            // 本次实际削减量，触底时为 0
        if (actualReduce <= 0.001) return;

        double oldHealth = target.getHealth();                   // 削减上限前的当前血量

        // 累计本模组已削减量，用单个负修饰符体现，避免每次攻击新增一个 modifier
        // 注意：修饰符量按"未放大"的 ADDITION 值累计，实际削减 = 量 × 倍率，
        // 因此每刀把 actualReduce 除以倍率折算，保证任何等级下每刀恰好减 reduce 点。
        double alreadyReduced = 0.0;
        AttributeModifier existing = maxHp.getModifier(REDUCE_MAX_HEALTH_UUID);
        if (existing != null) alreadyReduced = -existing.getAmount();
        double newReduced = alreadyReduced + actualReduce / scale;

        maxHp.removeModifier(REDUCE_MAX_HEALTH_UUID);
        maxHp.addTransientModifier(new AttributeModifier(
                REDUCE_MAX_HEALTH_UUID,
                "infinitestats.reduce_max_health",
                -newReduced,
                AttributeModifier.Operation.ADDITION
        ));

        // 仅下调生命上限：把当前血量夹取到新上限之下。
        // 若原血量高于新上限，则自然跟随下降（不高于新上限）；
        // 若原血量已低于新上限，则保持不变——本属性只负责"降低上限"，不额外造成一次伤害。
        // 不再调用 applyDirectDamage：否则一次攻击会同时出现"主伤害 + 降上限伤害"两个伤害实例，
        // 玩家观感为"多重伤害"（1.9.10 仅修了满血目标的双倍，未满血目标仍会多挨一次）。
        target.setHealth((float) Math.min(oldHealth, maxHp.getValue()));
    }

    /** 「额外打击」的次数兜底上限（配置缺失时使用）。 */
    public static final int MAX_EXTRA_STRIKES = 8;

    /** 实际生效的额外打击次数上限：优先读配置，配置尚未加载时用常量兜底。 */
    public static int maxExtraStrikes() {
        try {
            int value = com.infinitestats.Config.MAX_EXTRA_STRIKES.get();
            return value >= 1 ? value : MAX_EXTRA_STRIKES;
        } catch (Throwable ignored) {
            return MAX_EXTRA_STRIKES;
        }
    }

    /** 「额外打击」每次打击的伤害比例（默认 0.2 = 20%），配置尚未加载时回退到默认值。 */
    public static double extraStrikeDamageRatio() {
        try {
            double ratio = com.infinitestats.Config.EXTRA_STRIKE_DAMAGE_RATIO.get();
            return Double.isFinite(ratio) && ratio > 0 ? ratio : 0.0;
        } catch (Throwable ignored) {
            return 0.0; // 配置尚未加载
        }
    }

    /**
     * 「额外打击」的次数：等级即次数（被「功能开关」关闭时 getStatValue 返回 0，自动失效）。
     * 属性点可以为负（界面允许），因此这里把负数一律按 0 处理。
     */
    public static int extraStrikeCount(PlayerStats stats) {
        float value = stats.getStatValue("extra_strike");
        if (!(value > 0) || !Float.isFinite(value)) return 0;
        return Math.min(maxExtraStrikes(), (int) value);
    }

    /**
     * 额外打击：在本次伤害之外，对同一目标追加 N 次独立结算的额外属性伤害。
     * <p>
     * 为什么用 {@code setHealth} 直接扣血，而不是循环调用 {@code target.hurt(...)}：
     * 本方法是从 {@code LivingDamageEvent} 里调用的（此刻正处在目标本次受伤的
     * {@code actuallyHurt} 内部），嵌套一次 {@code hurt()} 会让「同一次受击」走两遍死亡收尾
     * —— 掉落物、成就、击杀进度都会重复触发。直接扣血后，死亡依旧由外层本次受伤的
     * 原版收尾负责（{@code setHealth(getHealth() - f)} → {@code getHealth() <= 0} → {@code die(玩家伤害来源)}），
     * 击杀归属与掉落保持正常，这与本模组「真实伤害」的做法一致。
     * <p>
     * 伤害按「次」结算，但总量固定 = 本次伤害 × 比例 × 次数，目标倒下即停止，不会因为循环而漂移。
     *
     * @param baseDamage 本次实际造成的伤害（已扣除护甲与减伤，取 {@code LivingDamageEvent#getAmount()}）
     */
    public static void applyExtraStrikes(PlayerStats stats, float baseDamage, LivingEntity target) {
        int strikes = extraStrikeCount(stats);
        if (strikes <= 0 || !(baseDamage > 0) || !Float.isFinite(baseDamage)) return;

        double ratio = extraStrikeDamageRatio();
        if (!(ratio > 0)) return;

        double perStrike = (double) baseDamage * ratio;
        if (!(perStrike > 0) || !Double.isFinite(perStrike)) return;

        for (int i = 0; i < strikes; i++) {
            if (target.isRemoved() || target.isDeadOrDying()) return;
            double hp = target.getHealth();
            if (hp <= 0) return;
            target.setHealth((float) Math.max(0.0, hp - perStrike));
        }
    }

    /**
     * 应用范围攻击：对目标周围敌人造成 50% 伤害（直接削减，无递归重入）
     */
    public static void applyScopeAttack(ServerPlayer player, PlayerStats stats, float baseDamage, LivingEntity origin) {
        float radius = stats.getStatValue(StatType.fromId("scope_attack"));
        if (radius <= 0 || baseDamage <= 0) return;

        // 以被攻击生物为中心做范围判定
        AABB area = origin.getBoundingBox().inflate(radius, radius, radius);
        List<LivingEntity> nearby = player.level().getEntitiesOfClass(LivingEntity.class, area,
                e -> e.isAlive() && e != origin && e != player && !e.isAlliedTo(player));

        float dmg = (float) (baseDamage * com.infinitestats.Config.SCOPE_ATTACK_DAMAGE_RATIO.get());
        for (LivingEntity e : nearby) {
            applyDirectDamage(player, e, dmg);
        }
    }

    /**
     * 应用排斥：每 tick 把周围的敌对生物（及其射出的弹射物）推开。
     * <p>
     * 行为对齐 ProjectE 的 SWRG 护盾（{@code WorldHelper#repelEntitiesSWRG}）：
     * 作用范围是以玩家为中心的立方体，推力沿"玩家 → 目标"方向，大小为
     * {@code 距离 / (1.5 * (距离 + 0.1))} —— 距离越近推得越"实"，但整体近似一个
     * 与距离无关的恒定推力（约 0.65 单位/tick），所以生物一进入范围就会被弹飞出去，
     * 而不是被稳稳地挡在边缘。
     * <p>
     * 三处与 ProjectE 的差异：
     * 1. 半径由属性点数缩放（每点 +0.5 格），ProjectE 是固定 5 格；
     * 2. 额外乘以目标的击退抗性系数（与 {@code Entity#push(Entity)} 一致），
     *    否则百抗生物（铁傀儡等）也会被推飞；
     * 3. 只推<b>敌对生物</b>（{@link Enemy} 或注册为 {@link MobCategory#MONSTER} 的生物），
     *    动物 / 村民 / NPC / 其他玩家不再被弹开；飞行中的弹射物只处理敌对生物射出的
     *    （含无主的发射器陷阱弹射物），玩家自己的弹射物不受影响。
     */
    public static void applyRepulsion(ServerPlayer player, PlayerStats stats, long tickCount) {
        float radius = stats.getStatValue(StatType.fromId("repulsion"));
        if (radius <= 0) return;

        AABB area = player.getBoundingBox().inflate(radius);
        List<Entity> nearby = player.level().getEntitiesOfClass(Entity.class, area,
                e -> isRepellable(e, player));

        Vec3 center = player.position();
        for (Entity e : nearby) {
            repelEntity(center, e);
        }
    }

    /**
     * 排斥的目标筛选：只推敌对生物与敌对生物射出的、尚未落地的弹射物；
     * 旁观者、玩家自己、自己扔出去的弹射物一律不推。
     */
    private static boolean isRepellable(Entity entity, ServerPlayer player) {
        if (entity.isSpectator() || entity == player) return false;
        if (entity instanceof Projectile projectile) {
            // 已落地的弹射物（插在地上的箭等）不动
            if (projectile.onGround()) return false;
            Entity owner = projectile.getOwner();
            // 无主弹射物（发射器 / 陷阱箭等）视为敌对；玩家自己射出的不推
            if (owner == null) return true;
            if (player.getUUID().equals(owner.getUUID())) return false;
            return isHostile(owner);
        }
        return isHostile(entity);
    }

    /**
     * 是否为敌对生物：原版 {@link Enemy}（怪物 / 史莱姆 / 恶魂 / 幻翼等）
     * 或注册在 {@link MobCategory#MONSTER} 分类下的模组生物。
     * 中立生物（末影人、僵尸猪灵等）原版即实现 {@link Enemy}，因此同样会被推开。
     */
    private static boolean isHostile(Entity entity) {
        if (!(entity instanceof Mob mob)) return false;
        if (mob instanceof Enemy) return true;
        return mob.getType().getCategory() == MobCategory.MONSTER;
    }

    /**
     * 沿"中心 → 实体"方向施加一次推力，并保留实体原有的速度分量。
     */
    private static void repelEntity(Vec3 center, Entity entity) {
        Vec3 offset = entity.position().subtract(center);
        double distance = offset.length() + 0.1;
        Vec3 direction;
        if (offset.lengthSqr() < 1.0E-10) {
            // 与玩家几乎重合时给一个随机水平方向，避免推力为零、卡在玩家身上
            double angle = entity.level().getRandom().nextDouble() * Math.PI * 2.0;
            direction = new Vec3(Math.cos(angle), 0.0, Math.sin(angle))
                    .scale(1.0 / (1.5 * distance));
        } else {
            direction = offset.scale(1.0 / (1.5 * distance));
        }

        // 击退抗性越高被推得越少（100% 抗性 → 完全推不动）
        double susceptibility = entity instanceof LivingEntity living
                ? 1.0 - living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)
                : 1.0;
        if (susceptibility <= 0.0) return;

        Vec3 delta = entity.getDeltaMovement();
        entity.setDeltaMovement(
                delta.x + direction.x * susceptibility,
                delta.y + direction.y * susceptibility,
                delta.z + direction.z * susceptibility);
        entity.hasImpulse = true;
    }
}
