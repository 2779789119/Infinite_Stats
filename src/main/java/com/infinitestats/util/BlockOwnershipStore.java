package com.infinitestats.util;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 「玩家放置的方块」<b>归属</b>记录（按维度各存一份，随存档持久化）。
 * <p>
 * 存在的意义：本模组有几条属性的语义是「只作用于<b>你自己的</b>机器」——
 * <ul>
 *   <li>{@code infinite_energy}（无限能源）：只补<b>本人放置</b>的能量方块
 *       （见 {@code compat/EnergyCompat}）；</li>
 *   <li>{@code create_infinite_stress}（无限应力）：只让<b>本人放置</b>的动力网络
 *       不再因应力不足停机（见 {@code compat/CreateStressCompat}）。</li>
 * </ul>
 * 物品 / 饰品 / 载具天然属于玩家本人，但<b>世界里的方块</b>必须先回答一句「这块是谁放的」——
 * 否则一开扫描就把别人基地、公共机器也一起算进来。所以这里在放置 / 破坏事件里维护一份轻量映射：
 * {@code BlockPos.asLong() -> 放置者 UUID}。
 * <p>
 * 只记录<b>带方块实体</b>的方块（{@code block instanceof net.minecraft.world.level.block.EntityBlock}）——
 * 石头 / 泥土这类纯方块不可能挂机器 / 能量，收进来只会让映射无谓膨胀。因此真正进表的
 * 就是机器 / 储能 / 发电机 / 动力方块这一小撮。
 * <p>
 * 维度隔离靠 {@link #get(ServerLevel)}：每个 {@code ServerLevel} 各持一份 SavedData，
 * 所以下界里 {@code (0,64,0)} 不会和主世界的同坐标串味。
 * <p>
 * 破坏走 {@code BlockEvent.BreakEvent}（仅玩家破坏触发）即时清除；爆炸 / {@code /setblock} /
 * 活塞推动这类不经破坏事件的移除会留下一条孤儿记录，但它指向的坐标要么已无方块实体、
 * 要么根本不再被扫描到，影响仅限于一条常量级的内存占用，故不做主动回收。
 * <p>
 * 历史名与存档名沿用 {@code infinitestats_energy_owners}（原名 {@code EnergyOwnershipStore} 只服务
 * 「无限能源」）：改存档名会让已有世界丢掉整张归属表，玩家得把机器重新拆装一遍才能恢复联动，
 * 因此这里只改类名、<b>不动 DATA_NAME</b>。
 */
public final class BlockOwnershipStore extends SavedData {

    private static final String DATA_NAME = "infinitestats_energy_owners";
    private static final String TAG_ENTRIES = "entries";
    private static final String TAG_POS = "p";
    private static final String TAG_OWNER = "u";

    /** {@code BlockPos.asLong()} -> 放置者 UUID。 */
    private final Map<Long, UUID> owners = new HashMap<>();

    /** 取当前维度的归属表（首次访问时从存档读入 / 新建）。 */
    public static BlockOwnershipStore get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                BlockOwnershipStore::load,
                BlockOwnershipStore::new,
                DATA_NAME);
    }

    private static BlockOwnershipStore load(CompoundTag tag) {
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
    public CompoundTag save(CompoundTag tag) {
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
        UUID old = owners.put(pos.asLong(), owner);
        if (!owner.equals(old)) setDirty();
    }

    /** 移除某坐标的归属（方块被破坏 / 被替换成非方块实体方块）。 */
    public void remove(BlockPos pos) {
        if (owners.remove(pos.asLong()) != null) setDirty();
    }

    /** 该坐标的放置者；没人放过（或已被移除）时返回 {@code null}。 */
    public UUID getOwner(BlockPos pos) {
        return owners.get(pos.asLong());
    }

    /** 该坐标是否由指定玩家放置。 */
    public boolean isOwnedBy(BlockPos pos, UUID player) {
        return player.equals(owners.get(pos.asLong()));
    }
}
