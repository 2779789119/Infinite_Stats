package com.infinitestats.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * 单条成就信息（用于网络传输）。
 * <p>
 * 除了「叫什么、完成没完成」之外，还带上菜单里要用的展示信息：
 * 进度（部分完成态）、框架类型（普通 / 目标 / 挑战）、所属模组命名空间、
 * 父成就 id 与「隐藏成就」标记 —— 这些决定了列表怎么排序、怎么配色、
 * 以及怎么按模组筛选，早期版本一个都没有，只能显示「已完成 / 未完成」两种状态。
 */
public final class AchievementInfo {

    /** 框架类型：普通（原版 TASK）。 */
    public static final int FRAME_TASK = 0;
    /** 框架类型：目标（原版 GOAL）。 */
    public static final int FRAME_GOAL = 1;
    /** 框架类型：挑战（原版 CHALLENGE）。 */
    public static final int FRAME_CHALLENGE = 2;

    public final String id;
    public final String displayName;
    public final String description;
    public final String iconItemId;
    public final boolean completed;
    /** 完成度百分比 0~100（条件做了一半也有值）。 */
    public final int progressPercent;
    /** 已完成的条件数。 */
    public final int criteriaDone;
    /** 条件总数。 */
    public final int criteriaTotal;
    /** 框架类型，见 {@link #FRAME_TASK} 等。 */
    public final int frame;
    /** 所属命名空间（原版是 minecraft，其它模组是 modid）。 */
    public final String namespace;
    /** 父成就 id，根成就为空串。 */
    public final String parentId;
    /** 原版的「隐藏成就」标记（未完成时在原版界面上不显示）。 */
    public final boolean hidden;

    public AchievementInfo(String id, String displayName, String description, String iconItemId,
                           boolean completed, int progressPercent, int criteriaDone, int criteriaTotal,
                           int frame, String namespace, String parentId, boolean hidden) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.iconItemId = iconItemId;
        this.completed = completed;
        this.progressPercent = progressPercent;
        this.criteriaDone = criteriaDone;
        this.criteriaTotal = criteriaTotal;
        this.frame = frame;
        this.namespace = namespace == null ? "minecraft" : namespace;
        this.parentId = parentId == null ? "" : parentId;
        this.hidden = hidden;
    }

    /** 做了一半（至少完成一个条件但还没全完成）。 */
    public boolean isPartial() {
        return !completed && criteriaDone > 0;
    }

    public static void encode(AchievementInfo info, FriendlyByteBuf buf) {
        buf.writeUtf(info.id);
        buf.writeUtf(info.displayName);
        buf.writeUtf(info.description);
        buf.writeUtf(info.iconItemId);
        buf.writeBoolean(info.completed);
        buf.writeVarInt(info.progressPercent);
        buf.writeVarInt(info.criteriaDone);
        buf.writeVarInt(info.criteriaTotal);
        buf.writeByte(info.frame);
        buf.writeUtf(info.namespace);
        buf.writeUtf(info.parentId);
        buf.writeBoolean(info.hidden);
    }

    public static AchievementInfo decode(FriendlyByteBuf buf) {
        return new AchievementInfo(
                buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                buf.readBoolean(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readByte(), buf.readUtf(), buf.readUtf(),
                buf.readBoolean());
    }
}
