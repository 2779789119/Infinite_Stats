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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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

    public static float calculateTrueDamage(PlayerStats stats, float baseDamage) {
        float percent = stats.getStatValue("true_damage");
        if (baseDamage <= 0 || percent <= 0 || !Float.isFinite(baseDamage) || !Float.isFinite(percent)) return 0;
        return (float) Math.min(Float.MAX_VALUE, (double) baseDamage * percent);
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
        // 排斥：持续推开周围的生物与飞行中的弹射物
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
     * 应用生命偷取效果
     *
     * @param damageAmount 结算后的实际伤害（护甲 / 减伤已扣除）。传入面板伤害会导致
     *                     打高护甲或带限伤的目标时严重虚高，调用点见
     *                     {@code StatEventHandler#onLivingDamage}。
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
            player.heal(totalAoeDamage * 0.5f);
        }
    }

    /**
     * 应用真实伤害：基于本次伤害的额外真实伤害，直接削减生命（无视护甲与一切减伤）
     *
     * 注意：这是「独立结算」入口，内部仍走正式 hurt() 流程（为了有原版结算收尾），
     * 因此在该流程之外触发的限伤仍可能削减它。事件链上的真伤不走这里，
     * 而是由 {@link #applyUnavoidableTrueDamage(LivingEntity, float, boolean)} 直接扣血。
     */
    public static void applyTrueDamage(ServerPlayer player, PlayerStats stats, float baseDamage, LivingEntity target) {
        float trueDmg = calculateTrueDamage(stats, baseDamage);
        if (trueDmg <= 0) return;

        applyDirectDamage(player, target, trueDmg);
    }

    /**
     * 真伤专用结算：直接扣血，完全脱离伤害管线。
     *
     * 为什么不能叠加进 LivingDamageEvent.getAmount()：那样真伤仍在管线内，会被后续环节
     * 连带削减——同优先级（LOWEST）注册顺序靠后的监听器、原版在 LivingDamageEvent 之后
     * 才执行的 getDamageAfterMagicAbsorb（抗性提升 / 保护附魔）与吸收（absorption）、
     * 以及任何 Mixin 到 actuallyHurt 的「单次伤害上限（限伤）」实现。
     *
     * 死亡归属不受影响：原版 actuallyHurt 结尾是 setHealth(getHealth() - f3)，读取的是
     * 当前血量，提前扣掉的部分不会丢失；紧随其后的 getHealth() <= 0 判定照常触发
     * 图腾检查与 die(playerSource)，掉落 / 成就 / 击杀进度归属正常。
     *
     * @param vanillaWillResolveDeath 主伤害是否有效（进入事件时 amount > 0）。
     *        为 false 时原版会在 LivingHurtEvent 后提前 return，不走死亡判定，
     *        此时最多扣到 0.01，避免出现「0 血存活」的实体。
     */
    public static void applyUnavoidableTrueDamage(LivingEntity target, float amount, boolean vanillaWillResolveDeath) {
        if (amount <= 0 || !Float.isFinite(amount) || target.isDeadOrDying()) return;
        double hp = target.getHealth();
        if (hp <= 0) return;

        double after = Math.max(0.0, hp - amount);
        if (after <= 0 && !vanillaWillResolveDeath) after = 0.01;
        target.setHealth((float) after);
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

    /** 「额外打击」的次数上限，与属性面板上的 maxLevel 保持一致。 */
    public static final int MAX_EXTRA_STRIKES = 8;

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
        return Math.min(MAX_EXTRA_STRIKES, (int) value);
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

        float dmg = baseDamage * 0.5f;
        for (LivingEntity e : nearby) {
            applyDirectDamage(player, e, dmg);
        }
    }

    /**
     * 应用排斥：每 tick 把周围的生物与飞行中的弹射物推开。
     * <p>
     * 行为对齐 ProjectE 的 SWRG 护盾（{@code WorldHelper#repelEntitiesSWRG}）：
     * 作用范围是以玩家为中心的立方体，推力沿"玩家 → 目标"方向，大小为
     * {@code 距离 / (1.5 * (距离 + 0.1))} —— 距离越近推得越"实"，但整体近似一个
     * 与距离无关的恒定推力（约 0.65 单位/tick），所以生物一进入范围就会被弹飞出去，
     * 而不是被稳稳地挡在边缘。
     * <p>
     * 两处与 ProjectE 的差异：
     * 1. 半径由属性点数缩放（每点 +0.5 格），ProjectE 是固定 5 格；
     * 2. 额外乘以目标的击退抗性系数（与 {@code Entity#push(Entity)} 一致），
     *    否则百抗生物（铁傀儡等）也会被推飞。
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
     * 排斥的目标筛选，规则同 ProjectE：只推生物（不推玩家）与"未落地"的弹射物，
     * 且不推自己扔出去的弹射物；旁观者一律不推。
     */
    private static boolean isRepellable(Entity entity, ServerPlayer player) {
        if (entity.isSpectator() || entity == player) return false;
        if (entity instanceof Projectile projectile) {
            // 已落地的弹射物（插在地上的箭等）不动
            if (projectile.onGround()) return false;
            Entity owner = projectile.getOwner();
            return owner == null || !player.getUUID().equals(owner.getUUID());
        }
        return entity instanceof Mob;
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
