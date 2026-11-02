package com.infinitestats.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * 单条成就信息（用于网络传输）
 */
public final class AchievementInfo {
    public final String id;
    public final String displayName;
    public final String description;
    public final String iconItemId;
    public final boolean completed;

    public AchievementInfo(String id, String displayName, String description,
                            String iconItemId, boolean completed) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.iconItemId = iconItemId;
        this.completed = completed;
    }

    public static void encode(AchievementInfo info, FriendlyByteBuf buf) {
        buf.writeUtf(info.id);
        buf.writeUtf(info.displayName);
        buf.writeUtf(info.description);
        buf.writeUtf(info.iconItemId);
        buf.writeBoolean(info.completed);
    }

    public static AchievementInfo decode(FriendlyByteBuf buf) {
        return new AchievementInfo(
                buf.readUtf(), buf.readUtf(), buf.readUtf(),
                buf.readUtf(), buf.readBoolean());
    }
}
