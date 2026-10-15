package com.infinitestats.emc;

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
 * 玩家 EMC 数据 - 使用 NeoForge Data Attachment 挂载到玩家实体。
 */
public final class EmcPlayerDataProvider {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, InfiniteStats.MODID);

    private static final IAttachmentSerializer<CompoundTag, EmcPlayerData> SERIALIZER =
            new IAttachmentSerializer<>() {
                @Override
                public EmcPlayerData read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider) {
                    EmcPlayerData data = new EmcPlayerData();
                    data.deserializeNBT(tag);
                    return data;
                }

                @Override
                public CompoundTag write(EmcPlayerData attachment, HolderLookup.Provider provider) {
                    return attachment.serializeNBT();
                }
            };

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<EmcPlayerData>> EMC_PLAYER_DATA =
            ATTACHMENT_TYPES.register("emc_player_data",
                    () -> AttachmentType.builder(EmcPlayerData::new).serialize(SERIALIZER).build());

    private EmcPlayerDataProvider() {}

    /**
     * 兼容旧 Capability 调用点，返回 {@link Optional}。
     */
    public static Optional<EmcPlayerData> of(Entity entity) {
        if (entity == null) return Optional.empty();
        return Optional.of(entity.getData(EMC_PLAYER_DATA));
    }
}