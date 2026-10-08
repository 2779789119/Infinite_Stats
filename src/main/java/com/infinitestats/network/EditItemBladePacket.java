package com.infinitestats.network;

import com.infinitestats.compat.SlashBladeCompat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 拔刀剑编辑数据包（客户端 → 服务器）。
 * <p>
 * 编辑主手拔刀剑的 <b>杀敌数 / 耀魂数 / 锻造数 / SA / SE</b>。
 * 字段为 {@code null} 表示「不改动该项」；SA / SE 在服务端会按注册表校验，
 * 非法项被丢弃（SA 非法则整包拒绝，因为 SA 是单值且非法值会让战斗逻辑取空）。
 * <p>
 * 刻意不做「原始值快照」式的并发校验（与 {@link EditItemMetaPacket} 一致）：杀敌数会随战斗实时增长，
 * 快照校验会导致频繁误判；这里只校验「槽位一致 + 物品 id 一致 + 确实是拔刀剑」。
 */
public final class EditItemBladePacket {

    private final int selectedSlot;
    private final ResourceLocation itemId;
    private final Integer kill;
    private final Integer soul;
    private final Integer refine;
    private final String sa;            // null = 不改
    private final List<String> se;      // null = 不改

    public EditItemBladePacket(int selectedSlot, ResourceLocation itemId,
                               Integer kill, Integer soul, Integer refine,
                               String sa, List<String> se) {
        this.selectedSlot = selectedSlot;
        this.itemId = itemId;
        this.kill = kill;
        this.soul = soul;
        this.refine = refine;
        this.sa = sa;
        this.se = se == null ? null : new ArrayList<>(se);
    }

    public static void encode(EditItemBladePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.selectedSlot);
        buf.writeResourceLocation(msg.itemId);

        writeNullableInt(buf, msg.kill);
        writeNullableInt(buf, msg.soul);
        writeNullableInt(buf, msg.refine);

        buf.writeBoolean(msg.sa != null);
        if (msg.sa != null) buf.writeUtf(msg.sa);

        buf.writeBoolean(msg.se != null);
        if (msg.se != null) {
            buf.writeVarInt(msg.se.size());
            for (String s : msg.se) buf.writeUtf(s);
        }
    }

    public static EditItemBladePacket decode(FriendlyByteBuf buf) {
        int slot = buf.readVarInt();
        ResourceLocation item = buf.readResourceLocation();
        Integer kill = readNullableInt(buf);
        Integer soul = readNullableInt(buf);
        Integer refine = readNullableInt(buf);
        String sa = buf.readBoolean() ? buf.readUtf() : null;
        List<String> se = null;
        if (buf.readBoolean()) {
            int n = buf.readVarInt();
            se = new ArrayList<>(n);
            for (int i = 0; i < n; i++) se.add(buf.readUtf());
        }
        return new EditItemBladePacket(slot, item, kill, soul, refine, sa, se);
    }

    private static void writeNullableInt(FriendlyByteBuf buf, Integer v) {
        buf.writeBoolean(v != null);
        if (v != null) buf.writeVarInt(v);
    }

    private static Integer readNullableInt(FriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readVarInt() : null;
    }

    public static void handle(EditItemBladePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            if (msg.selectedSlot < 0 || msg.selectedSlot > 8
                    || player.getInventory().selected != msg.selectedSlot) {
                player.displayClientMessage(translatable("screen.infinitestats.item_editor.changed"), true);
                return;
            }

            ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
            if (stack.isEmpty()
                    || !msg.itemId.equals(ForgeRegistries.ITEMS.getKey(stack.getItem()))
                    || !SlashBladeCompat.isBlade(stack)) {
                player.displayClientMessage(translatable("screen.infinitestats.slashblade.not_blade"), true);
                return;
            }

            // SA：单值，非法即拒绝（否则战斗时取到空剑技）
            String sa = msg.sa;
            if (sa != null) {
                ResourceLocation rl = ResourceLocation.tryParse(sa);
                if (rl == null || !SlashBladeCompat.slashArtsOptions().contains(rl)) {
                    player.displayClientMessage(translatable("screen.infinitestats.slashblade.invalid_sa"), true);
                    return;
                }
                sa = rl.toString();
            }

            // SE：多值，逐项过滤，非法项直接丢弃
            List<String> se = null;
            if (msg.se != null) {
                List<ResourceLocation> valid = SlashBladeCompat.specialEffectsOptions();
                se = new ArrayList<>();
                for (String raw : msg.se) {
                    ResourceLocation rl = ResourceLocation.tryParse(raw);
                    if (rl != null && valid.contains(rl)) se.add(rl.toString());
                }
            }

            boolean ok = SlashBladeCompat.apply(stack,
                    new SlashBladeCompat.Edit(msg.kill, msg.soul, msg.refine, sa, se));
            if (!ok) {
                player.displayClientMessage(translatable("screen.infinitestats.slashblade.failed"), true);
                return;
            }

            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
            player.displayClientMessage(translatable("screen.infinitestats.item_editor.applied"), true);
        });
        ctx.get().setPacketHandled(true);
    }

    private static Component translatable(String key) {
        return Component.translatable(key);
    }
}
