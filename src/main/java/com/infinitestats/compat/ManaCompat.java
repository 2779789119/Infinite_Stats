package com.infinitestats.compat;

import com.infinitestats.util.BlockOwnershipStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * <b>无限魔力</b>：三条互相独立的魔力池一起补，装哪个模组哪个生效、没装的那条零开销。
 * <ol>
 *   <li><b>Iron's 法术与魔法书</b>（{@code irons_spellbooks}）：玩家法力（{@code MagicData}）补到
 *       属性 {@code AttributeRegistry.MAX_MANA} 的上限 —— 施法不再受法力限制；</li>
 *   <li><b>Ars Nouveau</b>（{@code ars_nouveau}）：玩家魔源（{@code IManaCap}）补到 {@code getMaxMana()}；</li>
 *   <li><b>Botania 植物魔法</b>（{@code botania}）：背包 / 饰品栏里所有魔力物品（魔力石板、魔力戒指等
 *       实现 {@code ManaItem} 的）补满，外加<b>该玩家自己放下的魔力池</b>（实现 {@code ManaPool} 的
 *       方块实体）补满 —— 判定只看放置归属（{@link BlockOwnershipStore}），<b>不限距离</b>，
 *       区块加载着就补。</li>
 * </ol>
 * <p>
 * 三块都<b>反射</b>实现（与 {@code GoetyCompat} 同一套思路）；各自懒解析、取不到就永久短路，
 * 因此可以「三个模组只装一个」甚至「一个不装」都安全。每个补满动作都是「先读、不满才写」，
 * 避免每 tick 白刷同步包 / 事件。
 * <p>
 * <b>1.21.1 适配</b>：三条版本线在 1.21 上都换过底层机制，本类对<b>易变的几处</b>各做了多套尝试，
 * 命中哪套用哪套：
 * <ul>
 *   <li>能力返回值：Forge 的 {@code LazyOptional}（{@code orElse}）与 NeoForge 的 {@code Optional}
 *       （{@code orElse}）形状不同，两种都试；</li>
 *   <li>属性注册项：1.20.1 的 {@code RegistryObject<Attribute>} 在 1.21 变成
 *       {@code Holder<Attribute>} —— 按 {@code Holder#value()} / {@code Supplier#get()} /
 *       直接是 {@code Attribute} 三种情况分别处理。</li>
 * </ul>
 * 1.20.1 主线已用 {@code javap} 核对过 {@code irons_spellbooks-1.20.1-3.16.2}、
 * {@code ars_nouveau-1.20.1-4.12.7}、{@code Botania-1.20.1-453}；本分支尚未拿到 1.21.1 的对应 jar
 * 逐项复核，取不到的成员一律静默跳过（只让这条属性退化成原版行为，不影响启动）。
 */
public final class ManaCompat {

    /**
     * 自己放下的魔力池的补魔间隔（tick）。要读一遍归属表 + 逐个方块实体取能力，比读玩家能力贵，
     * 所以单独节流；而魔力池的消耗以「每秒几格」计，1 秒（20 tick）补一次完全跟得上。
     */
    private static final int POOL_INTERVAL = 20;

    private ManaCompat() {}

    /** 由 {@code UtilityHandler} 在每个玩家 tick 调用（只在「无限魔力」已解锁时进入）。 */
    public static void onTick(ServerPlayer player, long tickCount) {
        Irons.fill(player);
        Ars.fill(player);
        Botania.fill(player, tickCount);
    }

    /**
     * 把「可能是 Optional / LazyOptional」的返回值解成实体对象。
     * <p>
     * 两种写法的 {@code orElse} 签名恰好都是 {@code orElse(Object)}，所以只要方法名对得上就行 ——
     * 这也正是把它做成一个公共小工具的原因：Ars Nouveau 在两代之间换过一次。
     */
    private static Object unwrap(Object container) {
        if (container == null) return null;
        if (container instanceof Optional<?> optional) return optional.orElse(null);
        try {
            return container.getClass().getMethod("orElse", Object.class).invoke(container, new Object[]{null});
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 从 {@code RegistryObject} / {@code Holder} / 裸 {@code Attribute} 三种形状里取出属性。
     * <p>
     * 返回的是 {@code Holder<Attribute>} 而不是 {@code Attribute}：1.21 起
     * {@code LivingEntity#getAttributeValue} 收的是 {@code Holder<Attribute>}，直接给实例编译不过。
     * 裸实例（第三、四种情况）用 {@link Holder#direct(Object)} 包一层即可。
     */
    private static Holder<Attribute> toAttributeHolder(Object value) {
        if (value instanceof Holder<?> holder && holder.value() instanceof Attribute) {
            @SuppressWarnings("unchecked")
            Holder<Attribute> cast = (Holder<Attribute>) holder;
            return cast;
        }
        if (value instanceof Attribute attribute) return Holder.direct(attribute);
        if (value instanceof Supplier<?> supplier && supplier.get() instanceof Attribute attribute) {
            return Holder.direct(attribute);
        }
        return null;
    }

    // ==================================================================
    // Iron's Spells 'n Spellbooks —— 玩家法力
    // ==================================================================

    /** Iron's 法术书的玩家法力：补到 {@code MAX_MANA} 属性算出的上限。 */
    private static final class Irons {

        private static final String MAGIC_DATA = "io.redspace.ironsspellbooks.api.magic.MagicData";
        private static final String ATTRIBUTE_REGISTRY = "io.redspace.ironsspellbooks.api.registry.AttributeRegistry";

        private static boolean resolved;
        private static boolean unavailable;

        private static Method getPlayerMagicData;
        private static Method getMana;
        private static Method setMana;
        private static Holder<Attribute> maxManaAttribute;

        static void fill(ServerPlayer player) {
            if (!resolve()) return;
            try {
                Object magicData = getPlayerMagicData.invoke(null, player);
                float mana = (Float) getMana.invoke(magicData);
                double max = player.getAttributeValue(maxManaAttribute);
                if (max > 0 && mana < max) {
                    setMana.invoke(magicData, (float) max);
                }
            } catch (Throwable ignored) {
                // 静默跳过这一帧
            }
        }

        private static boolean resolve() {
            if (resolved || unavailable) return resolved;
            synchronized (Irons.class) {
                if (resolved || unavailable) return resolved;
                try {
                    Class<?> magicData = Class.forName(MAGIC_DATA);
                    getPlayerMagicData = magicData.getMethod("getPlayerMagicData", LivingEntity.class);
                    getMana = magicData.getMethod("getMana");
                    setMana = magicData.getMethod("setMana", float.class);

                    Field maxManaField = Class.forName(ATTRIBUTE_REGISTRY).getField("MAX_MANA");
                    maxManaAttribute = toAttributeHolder(maxManaField.get(null));
                    if (maxManaAttribute == null) {
                        unavailable = true;
                        return false;
                    }
                    resolved = true;
                } catch (Throwable t) {
                    unavailable = true;
                }
                return resolved;
            }
        }
    }

    // ==================================================================
    // Ars Nouveau —— 玩家魔源
    // ==================================================================

    /** Ars Nouveau 的玩家魔源：补到 {@code IManaCap#getMaxMana()}。 */
    private static final class Ars {

        private static final String CAPABILITY_REGISTRY = "com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry";
        private static final String IMANA_CAP = "com.hollingsworth.arsnouveau.api.mana.IManaCap";

        private static boolean resolved;
        private static boolean unavailable;

        private static Method getManaCap;
        private static Method getCurrentMana;
        private static Method getMaxMana;
        private static Method setMana;

        static void fill(ServerPlayer player) {
            if (!resolve()) return;
            try {
                Object mana = unwrap(getManaCap.invoke(null, player));
                if (mana == null) return;

                int max = (Integer) getMaxMana.invoke(mana);
                double current = ((Number) getCurrentMana.invoke(mana)).doubleValue();
                if (max > 0 && current < max) {
                    setMana.invoke(mana, (double) max);
                }
            } catch (Throwable ignored) {
                // 静默跳过这一帧
            }
        }

        private static boolean resolve() {
            if (resolved || unavailable) return resolved;
            synchronized (Ars.class) {
                if (resolved || unavailable) return resolved;
                try {
                    Class<?> registry = Class.forName(CAPABILITY_REGISTRY);
                    getManaCap = registry.getMethod("getMana", LivingEntity.class);

                    Class<?> iManaCap = Class.forName(IMANA_CAP);
                    getCurrentMana = iManaCap.getMethod("getCurrentMana");
                    getMaxMana = iManaCap.getMethod("getMaxMana");
                    // 1.20.1 是 setMana(double)，1.21 上仍是 double；取不到就换 float 再试一次
                    try {
                        setMana = iManaCap.getMethod("setMana", double.class);
                    } catch (NoSuchMethodException e) {
                        setMana = iManaCap.getMethod("setMana", float.class);
                    }
                    resolved = true;
                } catch (Throwable t) {
                    unavailable = true;
                }
                return resolved;
            }
        }
    }

    // ==================================================================
    // Botania —— 携带魔力（物品 / 饰品）与自己放下的魔力池
    // ==================================================================

    /** Botania 的魔力：玩家身上的魔力物品 + 自己放下的魔力池。 */
    private static final class Botania {

        private static final String MANA_ITEM_HANDLER = "vazkii.botania.api.mana.ManaItemHandler";
        private static final String MANA_ITEM = "vazkii.botania.api.mana.ManaItem";
        private static final String MANA_POOL = "vazkii.botania.api.mana.ManaPool";
        private static final String MANA_RECEIVER = "vazkii.botania.api.mana.ManaReceiver";

        private static boolean resolved;
        private static boolean unavailable;

        private static Object handlerInstance;
        private static Method getManaItems;
        private static Method getManaAccesories;

        private static Class<?> manaItemClass;
        private static Method itemGetMana;
        private static Method itemGetMaxMana;
        private static Method itemAddMana;

        private static Class<?> manaPoolClass;
        private static Method poolGetMaxMana;
        private static Method receiverGetCurrentMana;
        private static Method receiverReceiveMana;

        static void fill(ServerPlayer player, long tickCount) {
            if (!resolve()) return;
            try {
                fillStacks(player, getManaItems);
                if (getManaAccesories != null) {
                    fillStacks(player, getManaAccesories);
                }
                if (tickCount % POOL_INTERVAL == 0) {
                    fillOwnedPools(player);
                }
            } catch (Throwable ignored) {
                // 静默跳过这一帧
            }
        }

        /** 把一组物品里实现 {@code ManaItem} 的那些补满（非魔力物品直接跳过）。 */
        private static void fillStacks(ServerPlayer player, Method getter) throws Exception {
            if (getter == null) return;
            Object list = getter.invoke(handlerInstance, player);
            if (!(list instanceof List<?> stacks)) return;
            for (Object element : stacks) {
                if (!(element instanceof ItemStack stack) || stack.isEmpty()) continue;
                Object item = stack.getItem();
                if (!manaItemClass.isInstance(item)) continue;

                int max = (Integer) itemGetMaxMana.invoke(item);
                if (max <= 0) continue;
                int current = (Integer) itemGetMana.invoke(item);
                if (current < max) {
                    itemAddMana.invoke(item, max - current);
                }
            }
        }

        /**
         * 补满<b>该玩家自己放下</b>的魔力池。
         * <p>
         * 判定只看放置归属（{@link BlockOwnershipStore}）：相比「扫玩家周围半径」，这么写就是
         * 「放下去就永久生效」—— 人走开、机器在远处自己抽魔力，池子照样是满的。
         * 遍历范围也只有<b>他一个人</b>放下的那一小撮方块（反向索引），不是整个维度的方块实体，
         * 也不会顺手把别人的池子补满。
         * <p>
         * 未加载的区块读不到方块实体，直接跳过；那种情况下魔力池也不会 tick、不会被消耗，
         * 所以「无限」的语义不受影响。
         */
        private static void fillOwnedPools(ServerPlayer player) throws Exception {
            ServerLevel level = player.serverLevel();
            BlockOwnershipStore store = BlockOwnershipStore.get(level);
            for (long packed : store.ownedPositions(player.getUUID())) {
                BlockEntity blockEntity = level.getBlockEntity(BlockPos.of(packed));
                if (blockEntity == null || !manaPoolClass.isInstance(blockEntity)) continue;

                int max = (Integer) poolGetMaxMana.invoke(blockEntity);
                if (max <= 0) continue;
                int current = (Integer) receiverGetCurrentMana.invoke(blockEntity);
                if (current >= max) continue;

                receiverReceiveMana.invoke(blockEntity, max - current);
                blockEntity.setChanged();
            }
        }

        private static boolean resolve() {
            if (resolved || unavailable) return resolved;
            synchronized (Botania.class) {
                if (resolved || unavailable) return resolved;
                try {
                    Class<?> handler = Class.forName(MANA_ITEM_HANDLER);
                    handlerInstance = handler.getField("INSTANCE").get(null);
                    getManaItems = handler.getMethod("getManaItems", Player.class);
                    // 1.21 上这个拼写古怪的方法名可能被改掉（getManaAccesories → getManaAccessories），
                    // 两个都试；都没有就让「饰品栏那一半」静默缺席，背包那一半照常工作
                    getManaAccesories = findMethod(handler, "getManaAccesories", "getManaAccessories", "getManaEquipped");

                    manaItemClass = Class.forName(MANA_ITEM);
                    itemGetMana = manaItemClass.getMethod("getMana");
                    itemGetMaxMana = manaItemClass.getMethod("getMaxMana");
                    itemAddMana = manaItemClass.getMethod("addMana", int.class);

                    manaPoolClass = Class.forName(MANA_POOL);
                    poolGetMaxMana = manaPoolClass.getMethod("getMaxMana");
                    Class<?> receiver = Class.forName(MANA_RECEIVER);
                    receiverGetCurrentMana = receiver.getMethod("getCurrentMana");
                    receiverReceiveMana = receiver.getMethod("receiveMana", int.class);

                    resolved = true;
                } catch (Throwable t) {
                    unavailable = true;
                }
                return resolved;
            }
        }

        /** 按候选名依次找方法；都不存在返回 {@code null}（调用方按「缺席」处理）。 */
        private static Method findMethod(Class<?> type, String... names) {
            for (String name : names) {
                try {
                    return type.getMethod(name, Player.class);
                } catch (Throwable ignored) {
                    // 换下一个候选名
                }
            }
            return null;
        }
    }
}
