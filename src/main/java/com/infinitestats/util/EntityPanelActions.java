package com.infinitestats.util;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 生物面板「动作」页里那些一次性操作 —— 与 {@link EntityPanelToggles} 的开关相对：
 * 开关是持续状态，动作是点一下执行一次。
 *
 * <h2>为什么单独一个类</h2>
 * 面板本身只管画界面与发包，真正碰实体的逻辑放这里，服务端包处理只做权限校验后调用，
 * 这样「面板能做什么」和「谁能做」是两层，加动作不用动网络层。
 *
 * <h2>对玩家目标一律拒绝</h2>
 * 观察杖对玩家也能开面板（属性照看），但治疗以外的动作（复制 / 传送 / 点火 / 杀死）
 * 用在玩家身上会变成「一键干掉别人」，所以 {@link #appliesTo} 里直接把玩家排除掉。
 *
 * <h2>1.21.1 适配</h2>
 * 药水效果以 {@code Holder<MobEffect>} 参与判定（效果表键、移除、反查注册名都是 holder）。
 */
public final class EntityPanelActions {

    /** 动作页里的按钮，枚举顺序 = 显示顺序。 */
    public enum Action {
        /** 回满血。 */
        HEAL("heal"),
        /** 清除身上所有药水效果。 */
        CLEAR_EFFECTS("clear_effects"),
        /** 清除仇恨目标（不再追着谁打）。 */
        CLEAR_TARGET("clear_target"),
        /** 把生物传送到玩家身前。 */
        TELEPORT("teleport"),
        /** 复制一只同样的生物。 */
        DUPLICATE("duplicate"),
        /** 设为持久（不会被自然清除）。 */
        PERSISTENT("persistent"),
        /** 点燃 10 秒。 */
        IGNITE("ignite"),
        /** 直接杀死（会正常掉落）。 */
        KILL("kill");

        private final String id;

        Action(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public String getNameKey() {
            return "gui.infinitestats.entity_panel.action." + id;
        }

        public String getDescKey() {
            return getNameKey() + ".desc";
        }

        /** 危险动作用红色按钮 + 需要二次确认。 */
        public boolean isDestructive() {
            return this == KILL;
        }

        /** 这个动作对目标生物是否成立。 */
        public boolean appliesTo(Entity entity) {
            if (!(entity instanceof LivingEntity living)) return false;
            // 玩家只允许看属性和回血，别的都不给（复制 / 传送 / 点火 / 杀死用在玩家身上就是恶意）
            if (living instanceof Player) return this == HEAL;
            return switch (this) {
                case CLEAR_TARGET, PERSISTENT -> entity instanceof Mob;
                default -> true;
            };
        }
    }

    private static final Action[] VALUES = Action.values();

    private EntityPanelActions() {}

    public static Action byIndex(int index) {
        return index >= 0 && index < VALUES.length ? VALUES[index] : null;
    }

    /**
     * 执行一个动作。
     *
     * @return 是否真的做了什么（目标不适用 / 无可清除项都返回 false）
     */
    public static boolean apply(ServerPlayer player, LivingEntity target, Action action) {
        if (player == null || target == null || action == null) return false;
        if (!action.appliesTo(target)) return false;

        return switch (action) {
            case HEAL -> heal(target);
            case CLEAR_EFFECTS -> clearEffects(target);
            case CLEAR_TARGET -> clearTarget(target);
            case TELEPORT -> teleportInFront(player, target);
            case DUPLICATE -> duplicate(target);
            case PERSISTENT -> setPersistent(target);
            case IGNITE -> ignite(target);
            case KILL -> kill(target);
        };
    }

    /** 回满血（只补到上限）。 */
    public static boolean heal(LivingEntity target) {
        if (target.getHealth() >= target.getMaxHealth()) return false;
        target.setHealth(target.getMaxHealth());
        return true;
    }

    /** 清除全部药水效果。 */
    public static boolean clearEffects(LivingEntity target) {
        List<Holder<MobEffect>> active = new ArrayList<>(target.getActiveEffectsMap().keySet());
        if (active.isEmpty()) return false;
        for (Holder<MobEffect> effect : active) {
            target.removeEffect(effect);
        }
        return true;
    }

    /** 移除单个药水效果（面板效果页里点某一行）。 */
    public static boolean removeEffect(LivingEntity target, String effectId) {
        if (target == null || effectId == null) return false;
        ResourceLocation id = ResourceLocation.tryParse(effectId);
        if (id == null) return false;
        MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(id);
        return effect != null && target.removeEffect(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect));
    }

    /** 当前效果的注册名（面板里用它回传给服务端）。 */
    public static String effectId(MobEffectInstance instance) {
        if (instance == null || instance.getEffect() == null) return "";
        ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(instance.getEffect().value());
        return id == null ? "" : id.toString();
    }

    private static boolean clearTarget(LivingEntity target) {
        if (!(target instanceof Mob mob)) return false;
        boolean had = mob.getTarget() != null || mob.getLastHurtByMob() != null;
        mob.setTarget(null);
        mob.setLastHurtByMob(null);
        mob.getNavigation().stop();
        return had;
    }

    /** 传送到玩家身前 2.5 格（脚下）。 */
    private static boolean teleportInFront(ServerPlayer player, LivingEntity target) {
        Vec3 look = player.getLookAngle();
        double x = player.getX() + look.x * 2.5D;
        double z = player.getZ() + look.z * 2.5D;
        double y = player.getY();
        if (target instanceof Mob mob) mob.getNavigation().stop();
        target.teleportTo(x, y, z);
        target.setDeltaMovement(Vec3.ZERO);
        target.fallDistance = 0;
        return true;
    }

    /**
     * 复制一只同样的生物。
     * <p>
     * 直接 {@code saveWithoutId → load} 复制全部状态（血量、装备、驯服、村民职业……），
     * 但要先抹掉三样东西：UUID（不然两只共用身份）、Passengers（乘客不能复制两份）、
     * Leash（拴绳引用的是另一头实体）。复制出来的那只补上「持久」标记，
     * 免得刷出来就自然消失让人以为没生效。
     */
    private static boolean duplicate(LivingEntity target) {
        EntityType<?> type = target.getType();
        Entity copy = type.create(target.level());
        if (copy == null) return false;

        CompoundTag tag = new CompoundTag();
        target.saveWithoutId(tag);
        tag.remove("UUID");
        tag.remove("Passengers");
        tag.remove("Leash");
        copy.load(tag);
        copy.setPos(target.getX() + 1.0D, target.getY(), target.getZ() + 1.0D);
        if (copy instanceof Mob mob) mob.setPersistenceRequired();
        return target.level().addFreshEntity(copy);
    }

    private static boolean setPersistent(LivingEntity target) {
        if (!(target instanceof Mob mob)) return false;
        if (mob.isPersistenceRequired()) return false;
        mob.setPersistenceRequired();
        return true;
    }

    private static boolean ignite(LivingEntity target) {
        // 1.20.5+ 改名为 setRemainingFireTicks（单位 tick，10 秒 = 200）
        target.setRemainingFireTicks(200);
        return true;
    }

    private static boolean kill(LivingEntity target) {
        if (!target.isAlive()) return false;
        target.kill();
        return true;
    }
}
