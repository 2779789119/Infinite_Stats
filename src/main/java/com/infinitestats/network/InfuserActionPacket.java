package com.infinitestats.network;

import com.infinitestats.crafting.PortableEnchanting;
import com.infinitestats.crafting.PortableInfuser;
import com.infinitestats.crafting.PortableInfuserMenu;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 随身「进阶高级附魔台」的动作包（客户端 → 服务器）：附魔 / 修复 / 回收。
 * <p>
 * 客户端只会把「选了哪些附魔、各多少级、用哪种货币付款」发上来，<b>价格与合法性一律由服务端
 * 现算</b>（{@link PortableInfuser} 与服务端同源）：客户端的列表是提示，不是凭据，
 * 改过的客户端既不能白拿附魔，也不能少付钱。
 * <p>
 * 三道闸门：
 * <ol>
 *   <li>必须正开着附魔台菜单（{@link PortableInfuserMenu}）—— 否则连物品槽都不存在；</li>
 *   <li>「随身附魔台」开关必须激活；</li>
 *   <li>附魔条目数封顶，避免构造超长包刷内存。</li>
 * </ol>
 * 动作结果（成功或失败原因）统一用动作栏消息回给玩家，点数变化则整包同步一次属性数据。
 */
public final class InfuserActionPacket {

    public static final int ACTION_ENCHANT = 0;
    public static final int ACTION_REPAIR = 1;
    public static final int ACTION_RECYCLE = 2;

    /** 一次最多提交多少条附魔（远超任何物品的可选上限，纯粹防构造包）。 */
    private static final int MAX_SELECTION = 512;

    private final int action;
    /** true = 用可用属性点数付款，false = 用经验等级付款。 */
    private final boolean usePoints;
    private final List<ResourceLocation> enchantmentIds;
    private final List<Integer> levels;

    public InfuserActionPacket(int action, boolean usePoints,
                               List<ResourceLocation> enchantmentIds, List<Integer> levels) {
        this.action = action;
        this.usePoints = usePoints;
        this.enchantmentIds = enchantmentIds == null ? List.of() : enchantmentIds;
        this.levels = levels == null ? List.of() : levels;
    }

    /** 附魔提交：选择表用 {@code Map<附魔, 目标等级>}（等级 0 表示拆掉）。 */
    public static InfuserActionPacket enchant(boolean usePoints, Map<Holder<Enchantment>, Integer> selection) {
        List<ResourceLocation> ids = new ArrayList<>(selection.size());
        List<Integer> values = new ArrayList<>(selection.size());
        for (Map.Entry<Holder<Enchantment>, Integer> entry : selection.entrySet()) {
            ResourceLocation id = entry.getKey() == null
                    ? null
                    : entry.getKey().unwrapKey().map(key -> key.location()).orElse(null);
            if (id == null) continue;
            ids.add(id);
            values.add(Math.max(0, entry.getValue()));
        }
        return new InfuserActionPacket(ACTION_ENCHANT, usePoints, ids, values);
    }

    public static InfuserActionPacket simple(int action, boolean usePoints) {
        return new InfuserActionPacket(action, usePoints, List.of(), List.of());
    }

    public static void encode(InfuserActionPacket msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.action);
        buf.writeBoolean(msg.usePoints);
        int size = Math.min(msg.enchantmentIds.size(), MAX_SELECTION);
        buf.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            buf.writeResourceLocation(msg.enchantmentIds.get(i));
            buf.writeVarInt(msg.levels.size() > i ? msg.levels.get(i) : 0);
        }
    }

    public static InfuserActionPacket decode(FriendlyByteBuf buf) {
        int action = buf.readByte();
        boolean usePoints = buf.readBoolean();
        // 先夹住条数再分配：恶意包可以写一个很大的 VarInt，不夹会直接按它建列表
        int size = Math.min(Math.max(buf.readVarInt(), 0), MAX_SELECTION);
        List<ResourceLocation> ids = new ArrayList<>(size);
        List<Integer> levels = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ids.add(buf.readResourceLocation());
            levels.add(Math.max(0, buf.readVarInt()));
        }
        return new InfuserActionPacket(action, usePoints, ids, levels);
    }

    public static void handle(InfuserActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            // 闸门一：必须是随身附魔台菜单
            if (!(player.containerMenu instanceof PortableInfuserMenu menu)) return;

            PlayerStats stats = PlayerStatsProvider.of(player).orElse(null);
            // 闸门二：开关属性必须激活
            if (stats == null || !stats.isToggleActive(PortableEnchanting.STAT_ID)) {
                player.displayClientMessage(Component.translatable(
                        "message.infinitestats.portable_station_locked",
                        Component.translatable("stat.infinitestats." + PortableEnchanting.STAT_ID)), true);
                return;
            }

            PortableInfuser.Payment payment = msg.usePoints
                    ? PortableInfuser.Payment.POINTS
                    : PortableInfuser.Payment.LEVELS;

            Component feedback = switch (msg.action) {
                case ACTION_ENCHANT -> PortableInfuser.performEnchant(player, menu.getInput(),
                        selectionOf(msg, player), payment);
                case ACTION_REPAIR -> PortableInfuser.performRepair(player, menu.getInput(), payment);
                case ACTION_RECYCLE -> PortableInfuser.performRecycle(player, menu.getInput(), payment);
                default -> null;
            };

            if (feedback != null) {
                player.displayClientMessage(feedback, true);
            }
            // 用点数付款时数值变了，整包同步一次（经验等级原版自己会同步）
            if (msg.usePoints) {
                NetworkHandler.syncToClient(player);
            }
            menu.broadcastChanges();
        });
        ctx.get().setPacketHandled(true);
    }

    /** 把「附魔 id + 等级」还原成选择表；认不出的 id（模组被移除等）静默丢弃。 */
    private static Map<Holder<Enchantment>, Integer> selectionOf(InfuserActionPacket msg, ServerPlayer player) {
        Map<Holder<Enchantment>, Integer> selection = new LinkedHashMap<>();
        // 附魔是数据驱动注册表，从玩家所在世界的 RegistryAccess 取（客户端与服务端同一份定义）
        var registry = player.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        for (int i = 0; i < msg.enchantmentIds.size() && i < msg.levels.size(); i++) {
            Holder<Enchantment> enchantment = registry.getHolder(msg.enchantmentIds.get(i))
                    .map(holder -> (Holder<Enchantment>) holder)
                    .orElse(null);
            if (enchantment != null) {
                selection.put(enchantment, msg.levels.get(i));
            }
        }
        return selection;
    }
}
