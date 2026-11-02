package com.infinitestats.stats;

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
 * 玩家属性数据Provider - Capability提供者
 * 使用新版的CapabilityToken方式注册
 */
public final class PlayerStatsProvider implements ICapabilityProvider, INBTSerializable<CompoundTag> {

    /**
     * Capability Token
     */
    public static final Capability<PlayerStats> PLAYER_STATS = CapabilityManager.get(new CapabilityToken<PlayerStats>() {});

    private PlayerStats stats = null;
    private final LazyOptional<PlayerStats> optional = LazyOptional.of(this::getOrCreateStats);

    /**
     * 获取或创建PlayerStats实例
     */
    @Nonnull
    private PlayerStats getOrCreateStats() {
        if (stats == null) {
            stats = new PlayerStats();
        }
        return stats;
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == PLAYER_STATS) {
            return optional.cast();
        }
        return LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        return getOrCreateStats().serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        getOrCreateStats().deserializeNBT(tag);
    }
}