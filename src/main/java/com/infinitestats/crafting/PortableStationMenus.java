package com.infinitestats.crafting;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.level.Level;

/**
 * 随身站点菜单 — 复用原版菜单算法，但解除「必须站在对应方块旁」的校验。
 * <p>
 * <b>为什么不能直接用 {@link ContainerLevelAccess#NULL}：</b>原版这些菜单把「世界侧」行为
 * 全都写在 {@code access.execute(...)} 回调里，而 NULL 的 {@code evaluate} 返回
 * {@code Optional.empty()}，回调根本不会执行，于是：
 * <ul>
 *   <li>{@code removed(Player)} 里的 {@code clearContainer} 被跳过 —— 关界面时输入槽里的
 *       材料会凭空消失（旧版随身铁砧 / 锻造台就有这个问题）；</li>
 *   <li>制图台的地图操作（复制 / 扩展 / 锁定）整段逻辑都在回调里 —— 用 NULL 时
 *       成品永远算不出来；</li>
 *   <li>锻造台等需要读世界侧数据来判定成品的菜单同理。</li>
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
     * （部分菜单除了 access 还需要自己拿 level / pos 读世界数据）。
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

    /*
     * 注：随身附魔台已由「进阶高级附魔台」取代（见 PortableInfuser / PortableInfuserMenu）——
     * 那一档不是「复用原版菜单」，而是自选附魔的自建菜单与界面，与原版附魔算法不再共用逻辑，
     * 因此这里不再保留对应子类。
     */

    /** 供外部统一判断「这个菜单是不是随身站点菜单」。 */
    public static boolean isPortableStation(AbstractContainerMenu menu) {
        return menu instanceof Stonecutter
                || menu instanceof Loom
                || menu instanceof Cartography
                || menu instanceof Grindstone
                || menu instanceof Smithing;
    }
}
