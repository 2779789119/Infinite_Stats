package com.infinitestats.util;

import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/**
 * 生物面板里可以直接切换的「生物开关」—— 也就是生物身上那些**一开一关**的原版能力。
 *
 * <h2>为什么是这几项</h2>
 * 全部挑的是原版就有、且<b>服务端改了就会同步给所有人</b>的字段：
 * {@code Silent} / {@code Invulnerable} / {@code NoGravity} / {@code Glowing} / {@code Invisible}
 * 走 {@code Entity} 的公开 setter，{@code NoAI} 在 {@code Mob} 上，{@code Baby} 在 {@code AgeableMob} 上。
 * 像「禁止自然消失」这种只有单向 setter（{@code Mob#setPersistenceRequired()} 没有关闭用重载）的字段
 * <b>不做</b>，免得出现「开了关不掉」这种半边功能。
 *
 * <h2>为什么状态要由服务端下发</h2>
 * 这几个开关<b>大多不是</b>同步实体数据（{@code SynchedEntityData}）里的字段 —— 客户端本地那份
 * 可能长期是旧值甚至默认值。所以面板打开时由服务端把「有哪些开关可用 + 当前是开是关」一起发过来
 * （两个位掩码，见 {@link #applicableMask} / {@link #enabledMask}），
 * 每次切换也由服务端执行并回包覆盖，玩家看到的永远是权威状态。
 *
 * <h2>改别人 / 别人的生物是允许的，但有闸</h2>
 * 服务端在 {@code NetworkHandler.EntityPanelActionPacket} 里校验：
 * 属性已激活 + 手里拿着观察杖 + 目标在 8 格内 + 配置 {@code EntityPanel.allowEntityEditing} 打开。
 * 多人服若不想让玩家乱动别人的生物，关掉那个配置即可（面板会变成只读）。
 */
public final class EntityPanelToggles {

    /** 可切换的生物开关。枚举顺序 = 面板里的显示顺序 = 位掩码的位序。 */
    public enum Toggle {
        /** 生物 AI（Mob）：关掉就是「木头人」。 */
        AI("ai", 1 << 0),
        /** 幼年 / 成年（AgeableMob）。 */
        BABY("baby", 1 << 1),
        /** 静音（不再发出任何声音）。 */
        SILENT("silent", 1 << 2),
        /** 不可见（模型隐身，碰撞箱还在）。 */
        INVISIBLE("invisible", 1 << 3),
        /** 发光（描边，隔墙可见）。 */
        GLOWING("glowing", 1 << 4),
        /** 无敌（免疫除虚空 / 指令外的一切伤害）。 */
        INVULNERABLE("invulnerable", 1 << 5),
        /** 无重力（不再下坠）。 */
        NO_GRAVITY("no_gravity", 1 << 6);

        private final String id;
        private final int bit;

        Toggle(String id, int bit) {
            this.id = id;
            this.bit = bit;
        }

        public String getId() {
            return id;
        }

        public int getBit() {
            return bit;
        }

        /** 界面显示名翻译键。 */
        public String getNameKey() {
            return "gui.infinitestats.entity_panel.toggle." + id;
        }

        /** 浮窗说明翻译键。 */
        public String getDescKey() {
            return getNameKey() + ".desc";
        }

        /** 这个开关对目标生物是否成立（面板只列出成立的）。 */
        public boolean appliesTo(Entity entity) {
            if (entity == null) return false;
            return switch (this) {
                case AI -> entity instanceof Mob;
                case BABY -> entity instanceof AgeableMob;
                default -> true;
            };
        }

        /** 当前是否为开。 */
        public boolean isOn(Entity entity) {
            if (entity == null) return false;
            return switch (this) {
                case AI -> entity instanceof Mob mob && mob.isNoAi();
                case BABY -> entity instanceof AgeableMob ageable && ageable.isBaby();
                case SILENT -> entity.isSilent();
                case INVISIBLE -> entity.isInvisible();
                case GLOWING -> entity.hasGlowingTag();
                case INVULNERABLE -> entity.isInvulnerable();
                case NO_GRAVITY -> entity.isNoGravity();
            };
        }

        /** 写入开关（不成立的组合直接忽略）。 */
        public void set(Entity entity, boolean value) {
            if (entity == null || !appliesTo(entity)) return;
            switch (this) {
                case AI -> ((Mob) entity).setNoAi(value);
                case BABY -> ((AgeableMob) entity).setBaby(value);
                case SILENT -> entity.setSilent(value);
                case INVISIBLE -> entity.setInvisible(value);
                case GLOWING -> entity.setGlowingTag(value);
                case INVULNERABLE -> entity.setInvulnerable(value);
                case NO_GRAVITY -> entity.setNoGravity(value);
            }
        }
    }

    private static final Toggle[] VALUES = Toggle.values();

    private EntityPanelToggles() {}

    public static int count() {
        return VALUES.length;
    }

    /** 位序 → 开关，越界返回 null（网络包里带的是客户端传来的下标，必须校验）。 */
    public static Toggle byIndex(int index) {
        return index >= 0 && index < VALUES.length ? VALUES[index] : null;
    }

    /** 「这个生物能用的开关」掩码。 */
    public static int applicableMask(Entity entity) {
        int mask = 0;
        for (Toggle toggle : VALUES) {
            if (toggle.appliesTo(entity)) mask |= toggle.getBit();
        }
        return mask;
    }

    /** 「当前处于开启状态的开关」掩码。 */
    public static int enabledMask(Entity entity) {
        int mask = 0;
        for (Toggle toggle : VALUES) {
            if (toggle.appliesTo(entity) && toggle.isOn(entity)) mask |= toggle.getBit();
        }
        return mask;
    }

    public static boolean isApplicable(int applicableMask, Toggle toggle) {
        return (applicableMask & toggle.getBit()) != 0;
    }

    public static boolean isEnabled(int enabledMask, Toggle toggle) {
        return (enabledMask & toggle.getBit()) != 0;
    }

    /**
     * 执行一次切换（服务端调用）。
     *
     * @return 是否真的发生了改变 —— 下标越界、该开关不适用、或值本来就一样都返回 false
     */
    public static boolean apply(Entity entity, int index, boolean value) {
        Toggle toggle = byIndex(index);
        if (entity == null || toggle == null || !toggle.appliesTo(entity)) return false;
        if (toggle.isOn(entity) == value) return false;
        toggle.set(entity, value);
        return true;
    }
}
