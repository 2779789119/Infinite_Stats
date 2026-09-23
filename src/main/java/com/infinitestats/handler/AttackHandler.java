package com.infinitestats.handler;

import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.StatCategory;
import com.infinitestats.stats.StatType;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.UUID;

/**
 * 攻击类属性处理器
 * 处理：暴击、护甲穿透、弹射物伤害、生命偷取、范围吸血、法力窃取
 */
public class AttackHandler implements StatEffectHandler {

    /**
     * 本模组“直接伤害”（范围/真实/降上限）使用的自定义伤害类型。
     * bypasses_armor=true 保留“无视护甲”语义；以玩家为来源实体，
     * 使击杀经由 die(playerSource) 正确归属玩家（掉落物 + 成就/进度/FTB任务）。
     */
    public static final ResourceKey<DamageType> DIRECT_DAMAGE =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("infinitestats", "direct_damage"));

    /**
     * 判断伤害来源是否为本模组的“直接伤害”，用于防止范围/真实伤害二次
     * 触发 StatEventHandler 的玩家攻击逻辑造成递归与无限循环。
     */
    public static boolean isDirectDamageSource(DamageSource src) {
        return src.is(DIRECT_DAMAGE);
    }

    public static final TagKey<DamageType> MAGIC_DAMAGE = TagKey.create(
            Registries.DAMAGE_TYPE, new ResourceLocation("infinitestats", "magic"));

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
        // 排斥：持续推开周围的敌对生物
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
        if (critChance > 0 && player.getRandom().nextFloat() < critChance) {
            float critDamage = stats.getStatValue(StatType.fromId("crit_damage"));
            return 1.5f + critDamage;
        }
        return 1.0f;
    }

    /**
     * 计算护甲穿透增伤
     */
    public static float applyArmorPenetration(PlayerStats stats, LivingEntity target,
                                             DamageSource source, float amount) {
        float penetration = Math.min(1, Math.max(0, stats.getStatValue("armor_penetration")));
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
     * 计算魔法伤害增伤
     */
    public static float calculateMagicBonus(PlayerStats stats, boolean isMagic) {
        if (!isMagic) return 1.0f;
        return 1.0f + stats.getStatValue(StatType.fromId("magic_damage"));
    }

    /**
     * 应用生命偷取效果
     */
    public static void applyLifeSteal(ServerPlayer player, PlayerStats stats, float damageAmount) {
        float lifeSteal = stats.getStatValue(StatType.fromId("life_steal"));
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

        double range = 5.0;
        AABB area = new AABB(
                target.getX() - range, target.getY() - range, target.getZ() - range,
                target.getX() + range, target.getY() + range, target.getZ() + range
        );

        List<LivingEntity> nearby = player.level().getEntitiesOfClass(LivingEntity.class, area,
                e -> e.isAlive() && e != player && e != target && !e.isAlliedTo(player));

        float totalAoeDamage = 0;
        for (LivingEntity nearbyMob : nearby) {
            float aoeDmg = damageAmount * aoeSteal * 0.3f;
            // 使用本模组“直接伤害”类型：因其 message_id 命中 isDirectDamageSource，
            // 受击实体再次进入 onLivingHurt 时会被拦截，避免对周围实体造成 playerAttack
            // 伤害后反复重入攻击分支形成同步无限递归（最终 StackOverflow 并崩溃）。
            applyDirectDamage(player, nearbyMob, aoeDmg);
            totalAoeDamage += aoeDmg;
        }

        if (totalAoeDamage > 0) {
            player.heal(totalAoeDamage * 0.5f);
        }
    }

    /**
     * 应用法力窃取效果
     */
    public static void applyManaSteal(ServerPlayer player, PlayerStats stats, float damageAmount) {
        float manaSteal = stats.getStatValue(StatType.fromId("mana_steal"));
        float maxMana = stats.getMaxMana();
        if (manaSteal > 0 && maxMana > 0 && damageAmount > 0) {
            float manaGain = damageAmount * manaSteal;
            stats.setCurrentMana(Math.min(stats.getCurrentMana() + manaGain, maxMana));
        }
    }

    /**
     * 应用真实伤害：基于本次伤害的额外真实伤害，直接削减生命（无视护甲与一切减伤）
     */
    public static void applyTrueDamage(ServerPlayer player, PlayerStats stats, float baseDamage, LivingEntity target) {
        float tdPct = stats.getStatValue(StatType.fromId("true_damage"));
        if (tdPct <= 0 || baseDamage <= 0) return;

        float trueDmg = baseDamage * tdPct;
        if (trueDmg <= 0) return;

        applyDirectDamage(player, target, trueDmg);
    }

    /**
     * 直接削减生命值（无视护甲/减伤），并让击杀正确归属玩家。
     *
     * 注意：必须走正式的 hurt() 流程，而不是直接 setHealth()。
     * 原因：
     *  - setHealth 不会触发 die(玩家伤害来源)，导致死亡来源不是玩家，
     *    成就/进度（FTB kill 任务、L2Hostility 难度判定）不触发；
     *  - setHealth 不会设置 lastHurtByPlayerTime，而掉落物靠该字段判断是否
     *    归玩家所有，所以直接扣血杀死的怪不会掉落物品（表现为“有时不掉”）。
     * 这里使用 bypasses_armor 的自定义伤害类型，既保留“无视护甲”语义，
     * 又让死亡经由 die(playerSource) 正确归属玩家（掉落 + 成就都正常）。
     */
    private static void applyDirectDamage(ServerPlayer player, LivingEntity target, float amount) {
        if (amount <= 0) return;
        var holder = player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DIRECT_DAMAGE);
        DamageSource src = new DamageSource(holder, player, player);
        target.hurt(src, amount);
    }

    /** 本模组“攻击减血上限”使用的累计负修饰符 UUID，直接削减 MAX_HEALTH 总值（含其他模组加成） */
    private static final UUID REDUCE_MAX_HEALTH_UUID =
            UUID.fromString("a1b2c3d4-0000-4e5f-8a9b-0c1d2e3f4a5b");

    /**
     * 应用攻击减血量上限：每次攻击降低目标最大生命“总值”（每点 -1 点，最低保留 1 点）
     * 通过本模组的负 ADDITION 修饰符累计削减，会把其他模组（Apotheosis 词缀/宝石/盔甲/饰品等）
     * 的加成也一同削掉——即真正削减“总值”，而非只削基础值。触底（总值=1）后停止，不再造成额外伤害。
     */
    public static void applyReduceMaxHealth(ServerPlayer player, PlayerStats stats, LivingEntity target) {
        float reduce = stats.getStatValue(StatType.fromId("reduce_max_health"));
        if (reduce <= 0) return;

        var maxHp = target.getAttribute(Attributes.MAX_HEALTH);
        if (maxHp == null) return;

        // 乘算倍率折算：Minecraft 属性公式中 ADDITION 修饰符先加进基数、再被
        // MULTIPLY_BASE / MULTIPLY_TOTAL 乘算放大。L2Hostility（莱特兰）给高等级怪
        // 提血量用的正是乘算修饰符——若不折算，本模组 -1 的 ADDITION 会被放大为
        // -1 × 倍率，表现为“莱特兰等级越高、削减越多，无等级时削减正常”。
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
        // 注意：修饰符量按“未放大”的 ADDITION 值累计，实际削减 = 量 × 倍率，
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
        // 若原血量已低于新上限，则保持不变——本属性只负责“降低上限”，不额外造成一次伤害。
        // 不再调用 applyDirectDamage：否则一次攻击会同时出现“主伤害 + 降上限伤害”两个伤害实例，
        // 玩家观感为“多重伤害”（1.9.10 仅修了满血目标的双倍，未满血目标仍会多挨一次）。
        target.setHealth((float) Math.min(oldHealth, maxHp.getValue()));
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

        float dmg = baseDamage * 0.5f;
        for (LivingEntity e : nearby) {
            applyDirectDamage(player, e, dmg);
        }
    }

    /**
     * 应用排斥：持续将周围敌对生物推开（每 10 tick 触发一次）
     */
    public static void applyRepulsion(ServerPlayer player, PlayerStats stats, long tickCount) {
        float radius = stats.getStatValue(StatType.fromId("repulsion"));
        if (radius <= 0) return;
        if (tickCount % 10 != 0) return;

        AABB area = player.getBoundingBox().inflate(radius, radius + 1, radius);
        List<LivingEntity> nearby = player.level().getEntitiesOfClass(LivingEntity.class, area,
                e -> e.isAlive() && e != player && !e.isAlliedTo(player));

        for (LivingEntity e : nearby) {
            // 手动计算“从玩家指向实体”的水平方向并施加推力，
            // 这样无论 knockback 的 (x,z) 语义如何，都能以玩家为中心向四周推开
            double dx = e.getX() - player.getX();
            double dz = e.getZ() - player.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist < 1e-5) {
                // 实体与玩家几乎重合时，给一个随机水平方向
                double angle = player.getRandom().nextDouble() * Math.PI * 2.0;
                dx = Math.cos(angle);
                dz = Math.sin(angle);
                dist = 1.0;
            }
            double resist = 1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
            if (resist > 0.0) {
                double strength = 0.6 * resist / dist;
                e.setDeltaMovement(
                        e.getDeltaMovement().x + dx * strength,
                        e.getDeltaMovement().y,
                        e.getDeltaMovement().z + dz * strength
                );
                e.hasImpulse = true;
            }
        }
    }
}