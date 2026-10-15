package com.infinitestats.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 「玩家放置的方块」<b>归属</b>记录（按维度各存一份，随存档持久化）。
 * <p>
 * 存在的意义：本模组有几条属性的语义是「只作用于<b>你自己的</b>机器」——
 * <ul>
 *   <li>{@code infinite_energy}（无限能源）：只补<b>本人放置</b>的能量方块；</li>
 *   <li>{@code create_infinite_stress}（无限应力）：只让<b>本人放置</b>的动力网络
 *       不再因应力不足停机（见 {@code compat/CreateStressCompat}）。</li>
 * </ul>
 * 物品 / 饰品 / 载具天然属于玩家本人，但<b>世界里的方块</b>必须先回答一句「这块是谁放的」，
 * 否则一开扫描就把别人基地、公共机器也一起算进来。所以这里维护一份轻量映射：
 * {@code BlockPos.asLong() -> 放置者 UUID}。
 * <p>
 * 只记录<b>带方块实体</b>的方块（{@code block instanceof EntityBlock}）—— 石头 / 泥土这类纯方块
 * 不可能挂机器 / 能量，收进来只会让映射无谓膨胀。因此真正进表的就是机器 / 储能 / 发电机 / 动力方块。
 * <p>
 * 维度隔离靠 {@link #get(ServerLevel)}：每个 {@code ServerLevel} 各持一份 SavedData，
 * 所以下界里 {@code (0,64,0)} 不会和主世界的同坐标串味。
 * <p>
 * 破坏事件只覆盖玩家破坏；爆炸 / {@code /setblock} / 活塞推动这类移除会留下一条孤儿记录，
 * 但它指向的坐标要么已无方块实体、要么不再被扫描到，影响仅限于一条常量级内存占用，故不主动回收。
 * <p>
 * 存档名沿用 {@code infinitestats_energy_owners}（原名 {@code EnergyOwnershipStore} 只服务
 * 「无限能源」）：改存档名会让已有世界丢掉整张归属表，因此只改类名、<b>不动 DATA_NAME</b>。
 * <p>
 * <b>1.21.1 适配</b>：{@code SavedData#save} 与反序列化都要带上 {@code HolderLookup.Provider}，
 * 且 {@code DimensionDataStorage#computeIfAbsent} 改成收 {@code SavedData.Factory}
 * （1.20.1 是 {@code (loader, factory, name)} 三个参数）。
 */
public final class BlockOwnershipStore extends SavedData {

    private static final String DATA_NAME = "infinitestats_energy_owners";
    private static final String TAG_ENTRIES = "entries";
    private static final String TAG_POS = "p";
    private static final String TAG_OWNER = "u";

    /** {@code BlockPos.asLong()} -> 放置者 UUID。 */
    private final Map<Long, UUID> owners = new HashMap<>();

    /**
     * 反向索引（<b>纯内存，不写存档</b>）：放置者 → 他放置的坐标集合。懒构建，之后随 record / remove 同步维护。
     * <p>
     * 给「某玩家自己放置的全部方块」这类查询用（例如「无限魔力」要补满<b>他放下的</b>魔力池）。
     * 直接遍历 {@link #owners} 也行，但那是整个维度里所有玩家放过的方块；按 owner 反查能一次缩到
     * 「他一个人的那一小撮」，于是可以放开到「不限距离」（只要区块已加载）。
     */
    private final Map<UUID, Set<Long>> byOwner = new HashMap<>();
    private boolean indexBuilt;

    /** 取当前维度的归属表（首次访问时从存档读入 / 新建）。 */
    public static BlockOwnershipStore get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(BlockOwnershipStore::new, BlockOwnershipStore::load),
                DATA_NAME);
    }

    private static BlockOwnershipStore load(CompoundTag tag, HolderLookup.Provider registries) {
        BlockOwnershipStore data = new BlockOwnershipStore();
        ListTag list = tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            try {
                data.owners.put(entry.getLong(TAG_POS), UUID.fromString(entry.getString(TAG_OWNER)));
            } catch (Throwable ignored) {
                // 单条记录损坏就跳过它，不影响其余归属
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<Long, UUID> entry : owners.entrySet()) {
            CompoundTag one = new CompoundTag();
            one.putLong(TAG_POS, entry.getKey());
            one.putString(TAG_OWNER, entry.getValue().toString());
            list.add(one);
        }
        tag.put(TAG_ENTRIES, list);
        return tag;
    }

    /** 记录某坐标由某玩家放置；内容真的变了才标脏，避免无谓写盘。 */
    public void record(BlockPos pos, UUID owner) {
        long packed = pos.asLong();
        UUID old = owners.put(packed, owner);
        if (owner.equals(old)) return;
        if (indexBuilt) {
            if (old != null) removeFromIndex(old, packed);
            byOwner.computeIfAbsent(owner, key -> new HashSet<>()).add(packed);
        }
        setDirty();
    }

    /** 移除某坐标的归属（方块被破坏 / 被替换成非方块实体方块）。 */
    public void remove(BlockPos pos) {
        long packed = pos.asLong();
        UUID old = owners.remove(packed);
        if (old == null) return;
        if (indexBuilt) removeFromIndex(old, packed);
        setDirty();
    }

    /** 该坐标的放置者；没人放过（或已被移除）时返回 {@code null}。 */
    public UUID getOwner(BlockPos pos) {
        return owners.get(pos.asLong());
    }

    /** 该坐标是否由指定玩家放置。 */
    public boolean isOwnedBy(BlockPos pos, UUID player) {
        return player.equals(owners.get(pos.asLong()));
    }

    /**
     * 某玩家在本维度放置的方块坐标（元素是 {@link BlockPos#asLong()} 的打包值）。
     * <p>
     * 返回的是<b>内部集合的只读视图</b>，调用方遍历时不要修改它；坐标为打包值，
     * 用 {@link BlockPos#of(long)} 还原成 {@link BlockPos} 再取方块实体。
     */
    public Collection<Long> ownedPositions(UUID owner) {
        ensureIndex();
        Set<Long> positions = byOwner.get(owner);
        return positions == null ? List.of() : positions;
    }

    /** 首次查询时构建反向索引（之后靠 record / remove 增量维护）。 */
    private void ensureIndex() {
        if (indexBuilt) return;
        indexBuilt = true;
        byOwner.clear();
        for (Map.Entry<Long, UUID> entry : owners.entrySet()) {
            byOwner.computeIfAbsent(entry.getValue(), key -> new HashSet<>()).add(entry.getKey());
        }
    }

    private void removeFromIndex(UUID owner, long packed) {
        Set<Long> positions = byOwner.get(owner);
        if (positions == null) return;
        positions.remove(packed);
        if (positions.isEmpty()) byOwner.remove(owner);
    }
}
