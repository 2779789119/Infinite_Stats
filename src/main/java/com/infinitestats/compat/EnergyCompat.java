package com.infinitestats.compat;

import com.infinitestats.Config;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.util.BlockOwnershipStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;

import java.util.ArrayList;
import java.util.UUID;

/**
 * 「无限能源」—— 让玩家「拥有 / 正在使用」的所有能量源保持满电。
 * <p>
 * 统一走 <b>Forge Energy（FE）</b> 能力（{@code ForgeCapabilities.ENERGY}）。在 1.20.1 里
 * RF（红石通量）就是 FE 的别名（CoFH 的 RF API 只是薄壳），Mekanism（对外暴露 FE）、
 * 热力系列（Thermal，RF=FE）、EnderIO（对外暴露 FE）、沉浸工程（Immersive Engineering）、
 * 机械动力附属（Create Crafts &amp; Additions 等）的<b>物品 / 方块实体 / 实体</b>最终都通过
 * {@code IEnergyStorage} 暴露能量 —— 所以「把 FE 补满」这一件事就能覆盖所有这些「能源类型」。
 * <p>
 * 覆盖两类来源：
 * <ul>
 *   <li><b>玩家自身携带 / 骑乘</b>（每 tick）：正在骑乘的实体、背包 36 格 + 盔甲 4 格 + 副手 1 格、
 *       Curios 饰品槽（装了 Curios 才扫，见 {@link CuriosBridge#forEachStack}）；</li>
 *   <li><b>玩家自己放置的能量方块</b>（按配置间隔扫描，默认 1 秒一次）：
 *       玩家周围「已加载区块」里、归属为本人（见 {@link BlockOwnershipStore}）且暴露
 *       {@code ForgeCapabilities.ENERGY} 的方块实体。</li>
 * </ul>
 * 方块那一半刻意限定为<b>本人放置</b>，与「只补你自己的能源」语义一致 —— 不会顺手把别人基地、
 * 公共机器也补满；也未解锁时第一步短路，零开销。扫描方块实体比遍历背包贵得多，所以单独走
 * {@link Config#ENERGY_BLOCK_INTERVAL} 节流。
 * <p>
 * 与「不再变少」那一半的关系：卓越前线载具由 {@code mixin/SbwVehicleEnergyStorageMixin} 直接拦掉
 * 扣电（引擎 / 能量弹药武器 / 外部抽电，拦一处全覆盖，HUD 不会闪）；本类只做<b>兜底</b> ——
 * 把「属性解锁之前就已经掉了电」的能量源、以及<b>其它所有模组</b>挂在物品 / 坐骑 / 方块上的能量源补满。
 * 其它模组的物品与方块没有统一的「扣电注入点」可拦，所以它们靠本类按 tick 补满（能量被用到才会掉，
 * 补一次对读数与 HUD 来说就是「恒满」）。
 */
public final class EnergyCompat {

    private EnergyCompat() {}

    /** 「无限能源」的属性 id。 */
    public static final String STAT_ID = "infinite_energy";

    /**
     * 每个玩家 tick 调用一次（由 {@code UtilityHandler} 直接驱动，不走 5 tick 节流）。
     * <p>
     * 未解锁时第一步就返回，零开销。背包 / 饰品 / 载具每 tick 补；能量方块按
     * {@link Config#ENERGY_BLOCK_INTERVAL} 节流扫描（默认 1 秒一次）。
     */
    public static void onTick(ServerPlayer player, PlayerStats stats, long tickCount) {
        if (!stats.isToggleActive(STAT_ID)) return;

        Entity vehicle = player.getVehicle();
        if (vehicle != null) {
            topUpEntity(vehicle);
        }

        Inventory inventory = player.getInventory();
        for (ItemStack stack : inventory.items) {
            topUpStack(stack);
        }
        for (ItemStack stack : inventory.armor) {
            topUpStack(stack);
        }
        for (ItemStack stack : inventory.offhand) {
            topUpStack(stack);
        }

        CuriosBridge.forEachStack(player, EnergyCompat::topUpStack);

        // 玩家自己放置的能量方块：扫描方块实体代价高，按配置间隔节流
        if (Config.INFINITE_ENERGY_BLOCKS.get()) {
            int interval = Math.max(1, Config.ENERGY_BLOCK_INTERVAL.get());
            if (tickCount % interval == 0) {
                topUpOwnedBlocks(player);
            }
        }
    }

    /** 把实体上的 FE 能量源补满（载具 / 坐骑）。 */
    private static void topUpEntity(Entity entity) {
        try {
            entity.getCapability(ForgeCapabilities.ENERGY).ifPresent(EnergyCompat::fill);
        } catch (Throwable ignored) {
            // 该实体没有能量能力 / 版本对不上：跳过
        }
    }

    /** 把物品上的 FE 能量源补满（电池、能量背包、能量工具等）。 */
    private static void topUpStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        try {
            stack.getCapability(ForgeCapabilities.ENERGY).ifPresent(EnergyCompat::fill);
        } catch (Throwable ignored) {
            // 该物品没有能量能力：跳过
        }
    }

    /**
     * 扫描玩家周围「已加载区块」里的方块实体，把<b>玩家自己放置的</b> FE 能源方块补满。
     * <p>
     * 只扫已加载区块（{@code getChunkNow}）—— 绝不为了补电去强制生成 / 加载新区块。
     * 归属判定（{@link BlockOwnershipStore#isOwnedBy}）先于能力查询：表里没有的坐标
     * 直接跳过，不做任何能力探测。
     */
    private static void topUpOwnedBlocks(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;

        int radius = Math.max(1, Config.ENERGY_BLOCK_RADIUS.get());
        BlockPos center = player.blockPosition();
        UUID owner = player.getUUID();
        BlockOwnershipStore store = BlockOwnershipStore.get(level);

        int cxMin = (center.getX() - radius) >> 4;
        int cxMax = (center.getX() + radius) >> 4;
        int czMin = (center.getZ() - radius) >> 4;
        int czMax = (center.getZ() + radius) >> 4;
        int yMin = center.getY() - radius;
        int yMax = center.getY() + radius;

        for (int cx = cxMin; cx <= cxMax; cx++) {
            for (int cz = czMin; cz <= czMax; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;

                // 遍历副本快照：补电过程中方块实体可能被增删（机器被拆掉等）
                for (BlockEntity be : new ArrayList<>(chunk.getBlockEntities().values())) {
                    BlockPos bep = be.getBlockPos();
                    if (bep.getY() < yMin || bep.getY() > yMax) continue;
                    if (Math.abs(bep.getX() - center.getX()) > radius) continue;
                    if (Math.abs(bep.getZ() - center.getZ()) > radius) continue;
                    if (!store.isOwnedBy(bep, owner)) continue;
                    topUpBlockEntity(be);
                }
            }
        }
    }

    /**
     * 补满方块实体上的 FE 能量源。
     * <p>
     * 先试「不带方向」的能力，再逐个面试 —— 不少机器只在特定面暴露能量能力（输出面 / 输入面），
     * 只问 {@code null} 面会漏；反过来，找到的第一个面若是「只出不进」，其 {@code receiveEnergy}
     * 会返回 0，于是继续试下一个面，直到真正补进电或已满为止。
     */
    private static void topUpBlockEntity(BlockEntity be) {
        if (tryFillSide(be, null)) return;
        for (Direction side : Direction.values()) {
            if (tryFillSide(be, side)) return;
        }
    }

    /**
     * 尝试从某一面补电。
     *
     * @return {@code true} 表示「这一面已处理完毕」（补进了电，或本来就已经满）；
     *         {@code false} 表示这一面没有能量能力 / 拒绝输入，调用方应继续试下一面。
     */
    private static boolean tryFillSide(BlockEntity be, Direction side) {
        try {
            LazyOptional<IEnergyStorage> cap = be.getCapability(ForgeCapabilities.ENERGY, side);
            if (!cap.isPresent()) return false;

            IEnergyStorage storage = cap.orElse(null);
            if (storage == null) return false;

            int max = storage.getMaxEnergyStored();
            if (max <= 0) return false;
            int current = storage.getEnergyStored();
            if (current >= max) return true; // 已经是满的：这一半无需再动
            return storage.receiveEnergy(max - current, false) > 0;
        } catch (Throwable ignored) {
            // 该面能力版本对不上 / 抛异常：当作没有，继续试下一面
            return false;
        }
    }

    /** 已经是满的就不写（避免每 tick 都标脏物品、反复同步给客户端）。 */
    private static void fill(IEnergyStorage storage) {
        int max = storage.getMaxEnergyStored();
        int current = storage.getEnergyStored();
        if (max > 0 && current < max) {
            storage.receiveEnergy(max - current, false);
        }
    }
}
