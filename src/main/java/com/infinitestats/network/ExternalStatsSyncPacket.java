package com.infinitestats.network;

import com.infinitestats.stats.StatType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 外部属性列表同步（服务器 → 客户端）。
 *
 * <h2>为什么必须下发，而不是让客户端自己扫</h2>
 * 客户端的 {@code FMLCommonSetup} 确实也会扫一遍属性注册表，但它读不到<b>服务端的判定</b>：
 * 黑名单 / 白名单 / 每条属性的 perPoint 与点数上限 / displayName / hidden 都来自服务端配置文件，
 * 热重扫（{@code /infstats reload external}）更是只发生在服务端。各自扫各自的，就会出现
 * 「面板上有这条、点下去服务端不认识」或者数值对不上的情况。
 *
 * <p>整份列表只在<b>登录</b>与<b>热重扫</b>时发一次，不进高频的 {@link SyncStatsPacket}，
 * 避免每次加点都重复几百条文本。
 */
public final class ExternalStatsSyncPacket {

    private final List<StatType.ExternalSpec> specs;

    public ExternalStatsSyncPacket(List<StatType.ExternalSpec> specs) {
        this.specs = specs != null ? specs : List.of();
    }

    public static void encode(ExternalStatsSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.specs.size());
        for (StatType.ExternalSpec spec : msg.specs) {
            buf.writeUtf(spec.getAttributeName());
            buf.writeUtf(spec.getTranslationKey() != null ? spec.getTranslationKey() : "");
            buf.writeBoolean(spec.getDisplayName() != null);
            if (spec.getDisplayName() != null) buf.writeUtf(spec.getDisplayName());
            buf.writeBoolean(spec.getDescription() != null);
            if (spec.getDescription() != null) buf.writeUtf(spec.getDescription());
            buf.writeFloat(spec.getPerPoint());
            buf.writeVarInt(spec.getMaxPoints());
            buf.writeBoolean(spec.isHidden());
        }
    }

    public static ExternalStatsSyncPacket decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<StatType.ExternalSpec> specs = new ArrayList<>(Math.min(count, 1024));
        for (int i = 0; i < count; i++) {
            String attributeName = buf.readUtf();
            String translationKey = buf.readUtf();
            String displayName = buf.readBoolean() ? buf.readUtf() : null;
            String description = buf.readBoolean() ? buf.readUtf() : null;
            float perPoint = buf.readFloat();
            int maxPoints = buf.readVarInt();
            boolean hidden = buf.readBoolean();
            specs.add(new StatType.ExternalSpec(attributeName,
                    translationKey.isBlank() ? null : translationKey,
                    displayName, description, perPoint, maxPoints, hidden));
        }
        return new ExternalStatsSyncPacket(specs);
    }

    public static void handle(ExternalStatsSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            StatType.applyExternalSnapshot(msg.specs);

            // 面板正开着（多人游戏里管理员热重扫）时立刻重建列表，否则要重开才看得到
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen instanceof com.infinitestats.client.StatsScreen screen) {
                screen.reloadExternalStats();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
