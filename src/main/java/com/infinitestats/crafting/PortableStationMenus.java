package com.infinitestats.crafting;

import com.infinitestats.emc.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.ForgeEventFactory;

import java.util.List;

/**
 * 随身站点菜单 — 复用原版菜单算法，但解除「必须站在对应方块旁」的校验。
 * <p>
 * <b>为什么不能直接用 {@link ContainerLevelAccess#NULL}：</b>原版这些菜单把「世界侧」行为
 * 全都写在 {@code access.execute(...)} 回调里，而 NULL 的 {@code evaluate} 返回
 * {@code Optional.empty()}，回调根本不会执行，于是：
 * <ul>
 *   <li>{@code removed(Player)} 里的 {@code clearContainer} 被跳过 —— 关界面时输入槽里的
 *       材料会凭空消失（旧版随身铁砧 / 锻造台就有这个问题）；</li>
 *   <li>附魔台的附魔等级（书架数量）与「点击附魔」整段逻辑都在回调里 —— 用 NULL 时
 *       三档消耗恒为 0，根本附不了魔；</li>
 *   <li>制图台的成品计算也依赖回调里的 {@code level} 读取地图数据。</li>
 * </ul>
 * <p>
 * <b>做法：</b>给菜单传一个真实的 {@link ContainerLevelAccess}（玩家脚下的维度 + 坐标），
 * 于是所有世界侧回调都会正常执行；再继承原版菜单、把 {@code stillValid} 覆写为
 * {@code true}，从而不再要求那个坐标上真的摆着对应方块。
 * <p>
 * <b>客户端：</b>这些子类只存在于服务端 —— 客户端由原版 {@code MenuType} 工厂创建原版菜单
 * （access = NULL），与真正的原版方块场景完全一致，因此无需自建菜单类型与界面。
 * <p>
 * <b>例外（随身铁砧）：</b>见 {@link PortableAnvilMenu} —— 铁砧的 {@code access} 回调里还写着
 * 「消耗时有一定概率砸坏方块」，用真实 access 会真的在世界里留下破坏，所以随身铁砧仍然用
 * NULL + 自行补回物品。
 */
public final class PortableStationMenus {

    private PortableStationMenus() {}

    /**
     * 随身站点执行上下文：真实的 {@link ContainerLevelAccess}，外加它对应的维度 / 坐标
     * （附魔台需要它们来触发 {@code EnchantmentLevelSetEvent} 兼容钩子）。
     */
    public record Context(ContainerLevelAccess access, Level level, BlockPos pos) {}

    /** 以玩家脚下的真实维度 + 坐标构造上下文。 */
    public static Context contextOf(ServerPlayer player) {
        Level level = player.level();
        BlockPos pos = player.blockPosition();
        return new Context(ContainerLevelAccess.create(level, pos), level, pos);
    }

    /** 随身切石机。 */
    public static final class Stonecutter extends StonecutterMenu {
        public Stonecutter(int windowId, Inventory inventory, Context context) {
            super(windowId, inventory, context.access());
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }

    /** 随身织布机。 */
    public static final class Loom extends LoomMenu {
        public Loom(int windowId, Inventory inventory, Context context) {
            super(windowId, inventory, context.access());
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }

    /** 随身制图台。 */
    public static final class Cartography extends CartographyTableMenu {
        public Cartography(int windowId, Inventory inventory, Context context) {
            super(windowId, inventory, context.access());
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }

    /** 随身磨石。 */
    public static final class Grindstone extends GrindstoneMenu {
        public Grindstone(int windowId, Inventory inventory, Context context) {
            super(windowId, inventory, context.access());
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }

    /** 随身锻造台。 */
    public static final class Smithing extends SmithingMenu {
        public Smithing(int windowId, Inventory inventory, Context context) {
            super(windowId, inventory, context.access());
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }

    /**
     * 随身附魔台。
     * <p>
     * 与原版最大的区别：<b>附魔强度由「随身附魔台」属性投入的点数决定，不再看周围书架</b>。
     * 原版的强度计算写在 {@link EnchantmentMenu#slotsChanged} 里、直接读世界里的书架，
     * 所以这里把该方法整段重写，只把「书架强度」换成点数，其余逐行照抄原版：
     * 随机种子取自 {@code getEnchantmentSeed()}、三档消耗写回 {@code costs}（public 数组）、
     * 附魔线索写回 {@code enchantClue}/{@code levelClue}（同样是 public 数组，本来就会随数据槽同步给客户端），
     * 并且照常触发 {@link ForgeEventFactory#onEnchantmentLevelSet} 兼容钩子。
     * <p>
     * 因此「客户端看到的三档消耗与线索」「点击附魔时的判定」以及「实际产出的附魔」都与站在真附魔台前一致
     * —— 点击附魔走的是父类 {@code clickMenuButton}，它用的种子与洗牌算法和这里完全相同。
     */
    public static final class Enchanting extends EnchantmentMenu {

        /** 待附魔物品槽（父类第一个加入的槽）。 */
        private static final int SLOT_ITEM = 0;

        /** 附魔强度：等价于原版的「书架数量」（原版内部会把它截断到 15 以内参与计算）。 */
        private int enchantPower;
        private final Level level;
        private final BlockPos pos;

        public Enchanting(int windowId, Inventory inventory, Context context, int enchantPower) {
            super(windowId, inventory, context.access());
            this.enchantPower = Math.max(0, enchantPower);
            this.level = context.level();
            this.pos = context.pos();
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        /**
         * 独立菜单类型：客户端要凭它创建 {@code PortableEnchantingScreen}（附魔强度 ± 的界面）。
         * 若沿用原版 {@code MenuType.ENCHANTMENT}，客户端只会套用原版附魔界面，无从加载我们的界面。
         */
        @Override
        public MenuType<?> getType() {
            return ModMenuTypes.PORTABLE_ENCHANTING_MENU.get();
        }

        /**
         * 改附魔强度并立刻重算三档附魔（供「附魔强度 ±」加点后调用）。
         * <p>
         * 重算走 {@link #slotsChanged(Container)}：那里面写死了「用点数当书架强度」的全部算法，
         * 并且会 {@code broadcastChanges()} 把新的消耗与线索同步给客户端 —— 于是加点后
         * 界面上三档附魔会当场变化，不需要关掉重开。
         */
        public void applyEnchantPower(int power) {
            this.enchantPower = Math.max(0, power);
            this.slotsChanged(this.slots.get(SLOT_ITEM).container);
        }

        @Override
        public void slotsChanged(Container container) {
            ItemStack item = this.slots.get(SLOT_ITEM).getItem();
            if (item.isEmpty() || !item.isEnchantable()) {
                for (int i = 0; i < 3; i++) {
                    this.costs[i] = 0;
                    this.enchantClue[i] = -1;
                    this.levelClue[i] = -1;
                }
                this.broadcastChanges();
                return;
            }

            RandomSource random = RandomSource.create();
            random.setSeed(this.getEnchantmentSeed());

            for (int slot = 0; slot < 3; slot++) {
                this.costs[slot] = EnchantmentHelper.getEnchantmentCost(random, slot, this.enchantPower, item);
                this.enchantClue[slot] = -1;
                this.levelClue[slot] = -1;
                if (this.costs[slot] < slot + 1) {
                    this.costs[slot] = 0;
                }
                this.costs[slot] = ForgeEventFactory.onEnchantmentLevelSet(
                        this.level, this.pos, slot, this.enchantPower, item, this.costs[slot]);
            }

            for (int slot = 0; slot < 3; slot++) {
                if (this.costs[slot] > 0) {
                    List<EnchantmentInstance> list = rollEnchantments(random, item, slot, this.costs[slot]);
                    if (!list.isEmpty()) {
                        EnchantmentInstance picked = list.get(random.nextInt(list.size()));
                        this.enchantClue[slot] = BuiltInRegistries.ENCHANTMENT.getId(picked.enchantment);
                        this.levelClue[slot] = picked.level;
                    }
                }
            }

            this.broadcastChanges();
        }

        /**
         * 与原版私有方法 {@code EnchantmentMenu#getEnchantmentList} 完全同算法：
         * 先按 {@code 种子 + 槽位} 重设随机源，再抽附魔；成书且抽出多条时随机去掉一条。
         * <p>
         * 之所以自己实现：父类那个方法是 private，而点击附魔时父类会用它复算同一份列表，
         * 只要种子与调用顺序一致，两边结果必然相同（这里显示的线索也就不会与实际产出对不上）。
         */
        private List<EnchantmentInstance> rollEnchantments(RandomSource random, ItemStack item, int slot, int cost) {
            random.setSeed((long) (this.getEnchantmentSeed() + slot));
            List<EnchantmentInstance> list = EnchantmentHelper.selectEnchantment(random, item, cost, false);
            if (item.is(Items.BOOK) && list.size() > 1) {
                list.remove(random.nextInt(list.size()));
            }
            return list;
        }
    }

    /** 供外部统一判断「这个菜单是不是随身站点菜单」。 */
    public static boolean isPortableStation(AbstractContainerMenu menu) {
        return menu instanceof Stonecutter
                || menu instanceof Loom
                || menu instanceof Cartography
                || menu instanceof Grindstone
                || menu instanceof Smithing
                || menu instanceof Enchanting;
    }
}
