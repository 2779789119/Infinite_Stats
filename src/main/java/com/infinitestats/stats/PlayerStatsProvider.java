package com.infinitestats.stats;

import com.infinitestats.InfiniteStats;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.Optional;

/**
 * 玩家属性数据 - 使用 NeoForge Data Attachment 挂载到玩家实体。
 */
public final class PlayerStatsProvider {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, InfiniteStats.MODID);

    private static final IAttachmentSerializer<CompoundTag, PlayerStats> SERIALIZER =
            new IAttachmentSerializer<>() {
                @Override
                public PlayerStats read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider) {
                    PlayerStats stats = new PlayerStats();
                    stats.deserializeNBT(tag);
                    return stats;
                }

                @Override
                public CompoundTag write(PlayerStats attachment, HolderLookup.Provider provider) {
                    return attachment.serializeNBT();
                }
            };

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlayerStats>> PLAYER_STATS =
            ATTACHMENT_TYPES.register("player_stats",
                    () -> AttachmentType.builder(PlayerStats::new).serialize(SERIALIZER).build());

    private PlayerStatsProvider() {}

    /**
     * 兼容旧 Capability 调用点，返回 {@link Optional}。
     */
    public static Optional<PlayerStats> of(Entity entity) {
        if (entity == null) return Optional.empty();
        return Optional.of(entity.getData(PLAYER_STATS));
    }
}