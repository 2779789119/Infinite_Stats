package com.infinitestats.compat;

import com.infinitestats.util.BlockOwnershipStore;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.registries.RegistryObject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * <b>无限魔力</b>：三条互相独立的魔力池一起补，装哪个模组哪个生效、没装的那条零开销。
 * <ol>
 *   <li><b>Iron's 法术与魔法书</b>（{@code irons_spellbooks}）：玩家法力（{@code MagicData}）补到
 *       属性 {@code AttributeRegistry.MAX_MANA} 的上限 —— 施法不再受法力限制；</li>
 *   <li><b>Ars Nouveau</b>（{@code ars_nouveau}）：玩家魔源（{@code IManaCap}）补到 {@code getMaxMana()}；</li>
 *   <li><b>Botania 植物魔法</b>（{@code botania}）：背包 / 饰品栏里所有魔力物品（魔力石板、魔力戒指等
 *       实现 {@code ManaItem} 的）补满，外加<b>该玩家自己放下的魔力池</b>（实现 {@code ManaPool} 的
 *       方块实体）补满 —— 判定只看放置归属（{@code BlockOwnershipStore}），<b>不限距离</b>，
 *       区块加载着就补。</li>
 * </ol>
 * <p>
 * 三块都<b>反射</b>实现（与 {@code GoetyCompat} 同一套思路）；各自懒解析、取不到就永久短路，
 * 因此可以「三个模组只装一个」甚至「一个不装」都安全。每个补满动作都是「先读、不满才写」，
 * 避免每 tick 白刷同步包 / 事件。
 * <p>
 * 反射到的成员（已用 {@code javap} 对着整合包里的 {@code irons_spellbooks-1.20.1-3.16.2}、
 * {@code ars_nouveau-1.20.1-4.12.7}、{@code Botania-1.20.1-453} 核对）：
 * <ul>
 *   <li>{@code MagicData#getPlayerMagicData(LivingEntity)} / {@code #getMana()} / {@code #setMana(float)}，
 *       {@code AttributeRegistry#MAX_MANA}（{@code RegistryObject<Attribute>}）；</li>
 *   <li>{@code CapabilityRegistry#getMana(LivingEntity)}，{@code IManaCap#getCurrentMana/getMaxMana/setMana}；</li>
 *   <li>{@code ManaItemHandler#INSTANCE} / {@code #getManaItems(Player)} / {@code #getManaAccesories(Player)}，
 *       {@code ManaItem#getMana/getMaxMana/addMana(int)}，
 *       {@code ManaPool#getMaxMana()}，{@code ManaReceiver#getCurrentMana()/receiveMana(int)}。</li>
 * </ul>
 */
public final class ManaCompat {

    /**
     * 自己放下的魔力池的补魔间隔（tick）。要读一遍归属表 + 逐个方块实体取能力，比读玩家能力贵，
     * 所以单独节流；而魔力池的消耗以「每秒几格」计，1 秒（20 tick）补一次完全跟得上。
     */
    private static final int POOL_INTERVAL = 20;

    /** 当前生效的魔力池补魔间隔（配置项 Modules.manaPoolRefillInterval）。 */
    private static int poolInterval() {
        try {
            int value = com.infinitestats.Config.MANA_POOL_REFILL_INTERVAL.get();
            return Math.max(1, value);
        } catch (Throwable ignored) {
            return POOL_INTERVAL;
        }
    }

    private ManaCompat() {}

    /** 由 {@code UtilityHandler} 在每个玩家 tick 调用（只在「无限魔力」已解锁时进入）。 */
    public static void onTick(ServerPlayer player, long tickCount) {
        Irons.fill(player);
        Ars.fill(player);
        Botania.fill(player, tickCount);
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
        private static Attribute maxManaAttribute;

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
                    Object registryObject = maxManaField.get(null);
                    if (registryObject instanceof RegistryObject<?> ro && ro.get() instanceof Attribute attribute) {
                        maxManaAttribute = attribute;
                    } else {
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
        private static Method lazyOrElse;
        private static Method getCurrentMana;
        private static Method getMaxMana;
        private static Method setMana;

        static void fill(ServerPlayer player) {
            if (!resolve()) return;
            try {
                Object lazy = getManaCap.invoke(null, player);
                Object mana = lazyOrElse.invoke(lazy, new Object[]{null});
                if (mana == null) return;

                int max = (Integer) getMaxMana.invoke(mana);
                double current = (Double) getCurrentMana.invoke(mana);
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
                    lazyOrElse = Class.forName("net.minecraftforge.common.util.LazyOptional")
                            .getMethod("orElse", Object.class);

                    Class<?> iManaCap = Class.forName(IMANA_CAP);
                    getCurrentMana = iManaCap.getMethod("getCurrentMana");
                    getMaxMana = iManaCap.getMethod("getMaxMana");
                    setMana = iManaCap.getMethod("setMana", double.class);

                    resolved = true;
                } catch (Throwable t) {
                    unavailable = true;
                }
                return resolved;
            }
        }
    }

    // ==================================================================
    // Botania —— 携带魔力（物品 / 饰品）与附近魔力池
    // ==================================================================

    /** Botania 的魔力：玩家身上的魔力物品 + 附近魔力池。 */
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
                fillStacks(player, getManaAccesories);
                if (tickCount % poolInterval() == 0) {
                    fillOwnedPools(player);
                }
            } catch (Throwable ignored) {
                // 静默跳过这一帧
            }
        }

        /** 把一组物品里实现 {@code ManaItem} 的那些补满（非魔力物品直接跳过）。 */
        private static void fillStacks(ServerPlayer player, Method getter) throws Exception {
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
                    getManaAccesories = handler.getMethod("getManaAccesories", Player.class);

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
    }
}
