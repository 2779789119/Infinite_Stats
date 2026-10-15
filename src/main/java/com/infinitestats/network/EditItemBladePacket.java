package com.infinitestats.network;

import com.infinitestats.compat.SlashBladeCompat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 拔刀剑编辑（客户端 → 服务端）：把玩家<b>主手</b>那把刀的成长数值改成界面里填的。
 * <p>
 * 只认主手：编辑界面是从「手持那把刀右键打开物品编辑器」这条线进来的，主手就是玩家正在看的那把；
 * 省掉「槽位索引」这一层，也避免客户端传上来一个任意的槽位号去改别人看不见的东西。
 * <p>
 * 数值用 {@code -1} 作「不改动」的哨兵，字符串用 {@code null}（{@code sa}）；{@code se} 为
 * {@code null} 表示不改 SE 列表，空列表则表示「清空 SE」。实际的写入逻辑与兜底都在
 * {@link SlashBladeCompat#apply}。
 * <p>
 * 物品改动本身由原版同步机制推给客户端（玩家背包内容的变化会走 {@code ClientboundContainerSetSlotPacket}），
 * 所以这里不需要额外回包。
 */
public final class EditItemBladePacket {

    /** 「不改动」的数值哨兵。 */
    public static final int UNCHANGED = -1;

    private final int kill;
    private final int soul;
    private final int refine;
    private final String sa;
    private final List<String> se;

    public EditItemBladePacket(int kill, int soul, int refine, String sa, List<String> se) {
        this.kill = kill;
        this.soul = soul;
        this.refine = refine;
        this.sa = sa;
        this.se = se;
    }

    public static void encode(EditItemBladePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.kill);
        buf.writeVarInt(msg.soul);
        buf.writeVarInt(msg.refine);
        buf.writeUtf(msg.sa == null ? "" : msg.sa, 256);

        // se == null（不改）用 -1 区分于「空列表（清空）」
        if (msg.se == null) {
            buf.writeVarInt(-1);
        } else {
            buf.writeVarInt(msg.se.size());
            for (String id : msg.se) buf.writeUtf(id, 256);
        }
    }

    public static EditItemBladePacket decode(FriendlyByteBuf buf) {
        int kill = buf.readVarInt();
        int soul = buf.readVarInt();
        int refine = buf.readVarInt();
        String rawSa = buf.readUtf(256);
        String sa = rawSa.isEmpty() ? null : rawSa;

        int seSize = buf.readVarInt();
        List<String> se = null;
        if (seSize >= 0) {
            se = new ArrayList<>(seSize);
            for (int i = 0; i < seSize; i++) se.add(buf.readUtf(256));
        }
        return new EditItemBladePacket(kill, soul, refine, sa, se);
    }

    public static void handle(EditItemBladePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!SlashBladeCompat.isLoaded()) return;

            ItemStack stack = player.getMainHandItem();
            if (!SlashBladeCompat.isBlade(stack)) return;

            SlashBladeCompat.apply(stack, new SlashBladeCompat.Edit(
                    msg.kill == UNCHANGED ? null : msg.kill,
                    msg.soul == UNCHANGED ? null : msg.soul,
                    msg.refine == UNCHANGED ? null : msg.refine,
                    msg.sa,
                    msg.se));

            // 标记背包变更：没有这一句，改动可能要到玩家下次挪动物品才被推给客户端
            player.getInventory().setChanged();
        });
        ctx.get().setPacketHandled(true);
    }
}
