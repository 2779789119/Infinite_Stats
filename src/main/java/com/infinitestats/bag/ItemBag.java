package com.infinitestats.bag;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本模组自带的「无尽背包」数据 —— 玩家随身的一本物品账，不占用原版 36 格。
 *
 * <h2>为什么不是一堆槽位</h2>
 * 拾取时「获得的东西直接进包」要求<b>永远不会因为空间不足而吞物品</b>，
 * 因此这里没有容量上限、也不做 64 个一格的堆叠：每种「物品 + NBT」只记一条
 * {@code (原型, 数量)}，数量用 {@code long}。原版容器那套 36 格 / 每种 64 个的约束
 * 在这里只会造成「背包满了掉一地」。
 *
 * <h2>匹配规则</h2>
 * 同物品同 NBT 才算同一条（{@link ItemStack#isSameItemSameTags}），与存储网络
 * 合并逻辑保持一致 —— 附魔 / 自定义名称不同的同名物品不会混在一起。
 *
 * <h2>并发</h2>
 * 只由服务端主线程读写（拾取事件、菜单动作、数据包处理都在主线程），因此不加锁。
 * 客户端不会拿到这个对象的实例（数据走 {@code SyncItemBagPacket}）。
 */
public final class ItemBag {

    /** 物品 id → 该物品的所有条目（同一物品不同 NBT 会有多条）。 */
    private final Map<ResourceLocation, List<Entry>> buckets = new LinkedHashMap<>();

    /** 条目总数（去重后的「种类数」）。 */
    private int kindCount;
    /** 所有条目的数量总和。 */
    private long totalCount;

    /** 一条记录：{@code proto} 的 count 恒为 1，真实数量在 {@link #count}。 */
    public static final class Entry {
        final ItemStack proto;
        long count;

        Entry(ItemStack proto, long count) {
            this.proto = proto;
            this.count = count;
        }

        public ItemStack prototype() {
            return proto;
        }

        public long count() {
            return count;
        }
    }

    public int kindCount() {
        return kindCount;
    }

    public long totalCount() {
        return totalCount;
    }

    public boolean isEmpty() {
        return kindCount == 0;
    }

    public void clear() {
        buckets.clear();
        kindCount = 0;
        totalCount = 0;
    }

    /**
     * 存入物品。
     *
     * @param stack 要存入的物品（不会被修改）
     * @return 没能存进去的数量（本实现无上限，正常恒为 0）
     */
    public long insert(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        long amount = stack.getCount();
        if (amount <= 0) return 0;

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) return amount;

        List<Entry> list = buckets.get(id);
        if (list == null) {
            list = new ArrayList<>(1);
            buckets.put(id, list);
        }
        for (Entry e : list) {
            if (ItemStack.isSameItemSameTags(e.proto, stack)) {
                e.count += amount;
                totalCount += amount;
                return 0;
            }
        }
        ItemStack proto = stack.copy();
        proto.setCount(1);
        list.add(new Entry(proto, amount));
        kindCount++;
        totalCount += amount;
        // 新条目应出现在「最近获得」的位置：按物品 id 分桶会打乱全局插入顺序，
        // 展示顺序由界面自己排序，这里不做额外处理。
        return 0;
    }

    /**
     * 取出物品。
     *
     * @param template 模板（只看物品与 NBT，不看数量）
     * @param want     想要的数量（调用方负责不要超过 {@link Integer#MAX_VALUE}）
     * @return 实际取出的物品；取不到时为空
     */
    public ItemStack extract(ItemStack template, long want) {
        if (template == null || template.isEmpty() || want <= 0) return ItemStack.EMPTY;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(template.getItem());
        if (id == null) return ItemStack.EMPTY;
        List<Entry> list = buckets.get(id);
        if (list == null) return ItemStack.EMPTY;

        for (int i = 0; i < list.size(); i++) {
            Entry e = list.get(i);
            if (!ItemStack.isSameItemSameTags(e.proto, template)) continue;

            long taken = Math.min(want, e.count);
            if (taken <= 0) return ItemStack.EMPTY;
            e.count -= taken;
            totalCount -= taken;
            if (e.count <= 0) {
                list.remove(i);
                kindCount--;
                if (list.isEmpty()) buckets.remove(id);
            }
            ItemStack out = e.proto.copy();
            out.setCount((int) Math.min(taken, Integer.MAX_VALUE));
            return out;
        }
        return ItemStack.EMPTY;
    }

    /**
     * 取出某个条目的全部。
     *
     * @return 取出的物品；{@link ItemStack#getCount()} 超过 {@link Integer#MAX_VALUE} 时
     *         会被截断（实际不可能达到，仅作保护）
     */
    public ItemStack extractAll(ItemStack template) {
        return extract(template, Integer.MAX_VALUE);
    }

    /** 展示用快照：数量被夹到 int 范围内，保证 {@link ItemStack} 合法。 */
    public List<ItemStack> snapshot() {
        List<ItemStack> out = new ArrayList<>(kindCount);
        for (List<Entry> list : buckets.values()) {
            for (Entry e : list) {
                ItemStack copy = e.proto.copy();
                copy.setCount((int) Math.min(e.count, Integer.MAX_VALUE));
                out.add(copy);
            }
        }
        return out;
    }

    /** 与 {@link #snapshot()} 相同，但带真实 {@code long} 数量，供界面显示大数字。 */
    public List<Entry> entries() {
        List<Entry> out = new ArrayList<>(kindCount);
        for (List<Entry> list : buckets.values()) {
            out.addAll(list);
        }
        return out;
    }

    public void copyFrom(ItemBag other) {
        clear();
        if (other == null) return;
        for (Map.Entry<ResourceLocation, List<Entry>> bucket : other.buckets.entrySet()) {
            List<Entry> copy = new ArrayList<>(bucket.getValue().size());
            for (Entry src : bucket.getValue()) {
                copy.add(new Entry(src.proto.copyWithCount(1), src.count));
            }
            buckets.put(bucket.getKey(), copy);
        }
        kindCount = other.kindCount;
        totalCount = other.totalCount;
    }

    // ======================== 序列化 ========================

    public CompoundTag save() {
        CompoundTag root = new CompoundTag();
        ListTag list = new ListTag();
        for (Entry e : entries()) {
            CompoundTag tag = new CompoundTag();
            tag.put("Item", e.proto.copyWithCount(1).save(new CompoundTag()));
            tag.putLong("Count", e.count);
            list.add(tag);
        }
        root.put("Entries", list);
        return root;
    }

    public void load(CompoundTag root) {
        clear();
        if (root == null) return;
        ListTag list = root.getList("Entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            ItemStack proto = ItemStack.of(tag.getCompound("Item"));
            long count = tag.getLong("Count");
            if (proto.isEmpty() || count <= 0) continue;

            ResourceLocation id = BuiltInRegistries.ITEM.getKey(proto.getItem());
            if (id == null) continue;
            List<Entry> existing = buckets.get(id);
            if (existing == null) {
                existing = new ArrayList<>(1);
                buckets.put(id, existing);
            }
            ItemStack one = proto.copyWithCount(1);
            boolean merged = false;
            for (Entry e : existing) {
                if (ItemStack.isSameItemSameTags(e.proto, one)) {
                    e.count += count;
                    totalCount += count;
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                existing.add(new Entry(one, count));
                kindCount++;
                totalCount += count;
            }
        }
    }
}
