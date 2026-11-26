package com.infinitestats.bag;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 「无尽背包」的 Capability 提供者。
 * <p>
 * 数据只挂在玩家身上并由 Forge 随 playerdata 一起落盘；客户端也有一份（恒为空），
 * 因为它从不参与同步 —— 界面内容走 {@code SyncItemBagPacket}，客户端拿到的是只读列表。
 */
public final class ItemBagProvider implements ICapabilityProvider, INBTSerializable<CompoundTag> {

    public static final Capability<ItemBag> ITEM_BAG =
            CapabilityManager.get(new CapabilityToken<ItemBag>() {});

    /** 同步包来源：背包本身。 */
    public static final byte SOURCE_BAG = 0;
    /** 同步包来源：已连接的其它存储（RS / AE2 / BD / 汤姆 / 背包模组）。 */
    public static final byte SOURCE_NETWORK = 1;

    private ItemBag bag;
    private final LazyOptional<ItemBag> optional = LazyOptional.of(this::getOrCreate);

    @Nonnull
    private ItemBag getOrCreate() {
        if (bag == null) {
            bag = new ItemBag();
        }
        return bag;
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ITEM_BAG) return optional.cast();
        return LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        return getOrCreate().save();
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        getOrCreate().load(tag);
    }

    /**
     * 死亡重生 / 换维度时把背包数据搬到新玩家实体上。
     * <p>
     * 必须在调用方 {@code reviveCaps()} 之后执行；调用方之后会 {@code invalidateCaps()}，
     * 所以这里只读不持有引用。
     */
    public static void copy(Player from, Player to) {
        if (from == null || to == null) return;
        from.getCapability(ITEM_BAG).ifPresent(oldBag ->
                to.getCapability(ITEM_BAG).ifPresent(newBag -> newBag.copyFrom(oldBag)));
    }
}
