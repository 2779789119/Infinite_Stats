package com.infinitestats.emc;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * 玩家 EMC 数据 — 存储 EMC 余额和已学物品列表
 * 通过 Capability 附加到每个玩家
 */
public class EmcPlayerData {

    /** 当前 EMC 余额 */
    private long emcBalance;

    /** 已学习的物品 ID 集合 */
    private final Set<ResourceLocation> learnedItems = new LinkedHashSet<>();

    /** 已学物品的 NBT 数据（仅存储有 NBT 的物品，用于提取时还原） */
    private final Map<ResourceLocation, CompoundTag> itemNbt = new HashMap<>();

    // ==================== EMC 余额 ====================

    public long getEmcBalance() {
        return emcBalance;
    }

    public void setEmcBalance(long balance) {
        this.emcBalance = Math.max(0, balance);
    }

    /**
     * 增加 EMC
     */
    public void addEmc(long amount) {
        if (amount > 0) {
            this.emcBalance += amount;
        }
    }

    /**
     * 消耗 EMC
     * @return 是否成功消耗
     */
    public boolean consumeEmc(long amount) {
        if (amount <= 0) return true;
        if (emcBalance < amount) return false;
        emcBalance -= amount;
        return true;
    }

    // ==================== 已学物品 ====================

    /**
     * 检查物品是否已学习
     */
    public boolean hasLearned(ResourceLocation itemId) {
        return learnedItems.contains(itemId);
    }

    /**
     * 学习一个物品
     */
    public void learnItem(ResourceLocation itemId) {
        learnedItems.add(itemId);
    }

    /**
     * 学习一个物品（带 NBT 数据，用于手册等需保留标签的物品）
     */
    public void learnItem(ResourceLocation itemId, CompoundTag nbt) {
        learnedItems.add(itemId);
        if (nbt != null && !nbt.isEmpty()) {
            itemNbt.put(itemId, nbt);
        }
    }

    /**
     * 学习物品并返还其 EMC 值
     * @return 添加的 EMC 数量
     */
    public long learnAndConvert(ResourceLocation itemId, long emcValue) {
        learnedItems.add(itemId);
        addEmc(emcValue);
        return emcValue;
    }

    /**
     * 学习物品并返还其 EMC 值（带 NBT）
     */
    public long learnAndConvert(ResourceLocation itemId, long emcValue, CompoundTag nbt) {
        learnedItems.add(itemId);
        if (nbt != null && !nbt.isEmpty()) {
            itemNbt.put(itemId, nbt);
        }
        addEmc(emcValue);
        return emcValue;
    }

    /**
     * 获取已学物品的 NBT 数据
     */
    public CompoundTag getItemNbt(ResourceLocation itemId) {
        return itemNbt.get(itemId);
    }

    /**
     * 获取所有已学物品 ID
     */
    public Set<ResourceLocation> getLearnedItems() {
        return Collections.unmodifiableSet(learnedItems);
    }

    /**
     * 获取已学物品数量
     */
    public int getLearnedCount() {
        return learnedItems.size();
    }

    // ==================== NBT 序列化 ====================

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("emcBalance", emcBalance);

        ListTag learnedList = new ListTag();
        for (ResourceLocation id : learnedItems) {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", id.toString());
            CompoundTag nbt = itemNbt.get(id);
            if (nbt != null && !nbt.isEmpty()) {
                entry.put("nbt", nbt);
            }
            learnedList.add(entry);
        }
        tag.put("learnedItems", learnedList);

        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        emcBalance = tag.getLong("emcBalance");
        learnedItems.clear();
        itemNbt.clear();

        ListTag learnedList = tag.getList("learnedItems", Tag.TAG_COMPOUND);
        for (int i = 0; i < learnedList.size(); i++) {
            CompoundTag entry = learnedList.getCompound(i);
            ResourceLocation rl = ResourceLocation.tryParse(entry.getString("id"));
            if (rl != null) {
                learnedItems.add(rl);
                if (entry.contains("nbt")) {
                    itemNbt.put(rl, entry.getCompound("nbt"));
                }
            }
        }
    }

    /**
     * 从另一个 EmcPlayerData 复制数据（用于同步）
     */
    public void copyFrom(EmcPlayerData other) {
        this.emcBalance = other.emcBalance;
        this.learnedItems.clear();
        this.learnedItems.addAll(other.learnedItems);
        this.itemNbt.clear();
        this.itemNbt.putAll(other.itemNbt);
    }

    /**
     * 创建快照用于网络同步
     */
    public EmcSnapshot createSnapshot() {
        return new EmcSnapshot(emcBalance, new ArrayList<>(learnedItems), new HashMap<>(itemNbt));
    }

    /**
     * 从快照恢复
     */
    public void restoreFromSnapshot(EmcSnapshot snapshot) {
        this.emcBalance = snapshot.emcBalance;
        this.learnedItems.clear();
        this.learnedItems.addAll(snapshot.learnedItems);
        this.itemNbt.clear();
        this.itemNbt.putAll(snapshot.itemNbt);
    }

    // ==================== 快照类 ====================

    public static class EmcSnapshot {
        public final long emcBalance;
        public final List<ResourceLocation> learnedItems;
        public final Map<ResourceLocation, CompoundTag> itemNbt;

        public EmcSnapshot(long emcBalance, List<ResourceLocation> learnedItems, Map<ResourceLocation, CompoundTag> itemNbt) {
            this.emcBalance = emcBalance;
            this.learnedItems = learnedItems;
            this.itemNbt = itemNbt;
        }
    }
}
