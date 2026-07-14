package com.infinitestats.emc;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 玩家 EMC 数据 Capability 提供者
 */
public final class EmcPlayerDataProvider implements ICapabilityProvider, INBTSerializable<CompoundTag> {

    public static final Capability<EmcPlayerData> EMC_PLAYER_DATA =
            CapabilityManager.get(new CapabilityToken<EmcPlayerData>() {});

    private EmcPlayerData data;
    private final LazyOptional<EmcPlayerData> optional = LazyOptional.of(this::getOrCreateData);

    @Nonnull
    private EmcPlayerData getOrCreateData() {
        if (data == null) {
            data = new EmcPlayerData();
        }
        return data;
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == EMC_PLAYER_DATA) {
            return optional.cast();
        }
        return LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        return getOrCreateData().serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        getOrCreateData().deserializeNBT(tag);
    }
}
