package com.infinitestats.emc;

import com.infinitestats.InfiniteStats;
import com.infinitestats.Config;
import com.infinitestats.network.NetworkHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.event.server.ServerStartedEvent;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;

import java.nio.file.Path;

/**
 * EMC 系统事件处理器 — Capability 注册、实体附加、命令注册、玩家同步
 */
public final class EmcEvents {

    /**
     * MOD 总线 — Capability 注册
     */
    @Mod.EventBusSubscriber(modid = InfiniteStats.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {

        @SubscribeEvent
        public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
            event.register(EmcPlayerData.class);
        }
    }

    /**
     * FORGE 总线 — Capability 附加、数据同步
     */
    @Mod.EventBusSubscriber(modid = InfiniteStats.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ForgeBus {

        @SubscribeEvent
        public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
            if (event.getObject() instanceof Player) {
                event.addCapability(
                        new ResourceLocation(InfiniteStats.MODID, "emc_player_data"),
                        new EmcPlayerDataProvider()
                );
            }
        }

        @SubscribeEvent
        public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                NetworkHandler.syncEmcToClient(player);
            }
        }

        @SubscribeEvent
        public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                // 保留 EMC 数据，同步到客户端
                NetworkHandler.syncEmcToClient(player);
            }
        }

        @SubscribeEvent
        public static void onPlayerClone(PlayerEvent.Clone event) {
            // 死亡时保留 EMC 数据
            event.getOriginal().reviveCaps();
            event.getOriginal().getCapability(EmcPlayerDataProvider.EMC_PLAYER_DATA).ifPresent(oldData -> {
                event.getEntity().getCapability(EmcPlayerDataProvider.EMC_PLAYER_DATA).ifPresent(newData -> {
                    newData.copyFrom(oldData);
                });
            });
            event.getOriginal().invalidateCaps();
        }

        @SubscribeEvent
        public static void onServerStarted(ServerStartedEvent event) {
            // 加载 EMC 数据库（传入 Server 以获取 RecipeManager）
            EmcDatabase.load(event.getServer());
        }

        @SubscribeEvent
        public static void onRegisterCommands(RegisterCommandsEvent event) {
            event.getDispatcher().register(
                Commands.literal("emc")
                    .requires(s -> Config.EMC_ENABLED.get())
                    // /emc — 查看自己的 EMC
                    .executes(ctx -> {
                        if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
                            player.getCapability(EmcPlayerDataProvider.EMC_PLAYER_DATA).ifPresent(data -> {
                                long balance = data.getEmcBalance();
                                int learned = data.getLearnedCount();
                                String msg = "EMC: %s | 已学物品: %d 个".formatted(formatEmc(balance), learned);
                                ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
                            });
                        }
                        return 1;
                    })
                    // /emc learn — 学习手持物品
                    .then(Commands.literal("learn")
                        .executes(ctx -> {
                            if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
                                learnHandItem(player, ctx.getSource());
                            }
                            return 1;
                        })
                    )
                    // /emc learn all — 学习背包中所有有 EMC 的物品
                    .then(Commands.literal("learn")
                        .then(Commands.literal("all")
                            .executes(ctx -> {
                                if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
                                    learnAllInventory(player, ctx.getSource());
                                }
                                return 1;
                            })
                        )
                    )
                    // /emc give <player> <amount> — 给予 EMC（管理员）
                    .then(Commands.literal("give")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                            .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player");
                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                    target.getCapability(EmcPlayerDataProvider.EMC_PLAYER_DATA).ifPresent(data -> {
                                        data.addEmc(amount);
                                        NetworkHandler.syncEmcToClient(target);
                                    });
                                    ctx.getSource().sendSuccess(() ->
                                        Component.literal("给予了 " + target.getName().getString() + " " + formatEmc(amount) + " EMC"), true);
                                    return 1;
                                })
                            )
                        )
                    )
                    // /emc reload — 重载 EMC 数据库
                    .then(Commands.literal("reload")
                        .requires(s -> s.hasPermission(2))
                        .executes(ctx -> {
                            EmcDatabase.reload(ctx.getSource().getServer());
                            ctx.getSource().getServer().getPlayerList().getPlayers().forEach(NetworkHandler::syncEmcToClient);
                            ctx.getSource().sendSuccess(() ->
                                Component.literal("EMC 数据库已重载 (" + EmcDatabase.getTotalItems() + " 个物品)"), true);
                            return 1;
                        })
                    )
            );
        }

        private static void learnHandItem(ServerPlayer player, net.minecraft.commands.CommandSourceStack source) {
            ItemStack held = player.getMainHandItem();
            if (held.isEmpty()) {
                source.sendFailure(Component.literal("你手上没有物品"));
                return;
            }

            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(held.getItem());
            long emcValue = EmcDatabase.getEmc(held);
            if (emcValue <= 0) {
                source.sendFailure(Component.literal("该物品没有 EMC 值"));
                return;
            }

            player.getCapability(EmcPlayerDataProvider.EMC_PLAYER_DATA).ifPresent(data -> {
                if (data.hasLearned(itemId)) {
                    source.sendFailure(Component.literal("你已经学习过该物品了"));
                    return;
                }
                long sale = EmcDatabase.getSellValue(held, 1);
                data.learnAndConvert(itemId, sale, held.getTag());
                Component name = held.getHoverName();
                held.shrink(1);
                NetworkHandler.syncEmcToClient(player);
                source.sendSuccess(() -> Component.literal("已学习: " + name.getString()
                    + " (+" + formatEmc(sale) + " EMC)"), false);
            });
        }

        private static void learnAllInventory(ServerPlayer player, net.minecraft.commands.CommandSourceStack source) {
            player.getCapability(EmcPlayerDataProvider.EMC_PLAYER_DATA).ifPresent(data -> {
                java.util.concurrent.atomic.AtomicInteger learned = new java.util.concurrent.atomic.AtomicInteger(0);
                java.util.concurrent.atomic.AtomicLong totalEmc = new java.util.concurrent.atomic.AtomicLong(0);
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack stack = player.getInventory().getItem(i);
                    if (stack.isEmpty()) continue;

                    ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
                    if (data.hasLearned(itemId)) continue;

                    long emcValue = EmcDatabase.getEmc(stack);
                    if (emcValue <= 0) continue;

                    long sale = EmcDatabase.getSellValue(stack, 1);
                    data.learnAndConvert(itemId, sale, stack.getTag());
                    stack.shrink(1);
                    learned.incrementAndGet();
                    totalEmc.updateAndGet(total -> total + Math.min(sale, Long.MAX_VALUE - total));
                }
                int finalLearned = learned.get();
                long finalTotalEmc = totalEmc.get();
                if (finalLearned > 0) {
                    NetworkHandler.syncEmcToClient(player);
                    source.sendSuccess(() -> Component.literal("学习了 " + finalLearned + " 个新物品 (+"
                        + formatEmc(finalTotalEmc) + " EMC)"), false);
                } else {
                    source.sendFailure(Component.literal("背包中没有可学习的新物品"));
                }
            });
        }

        private static String formatEmc(long emc) {
            if (emc >= 1_000_000_000) return String.format("%.1fB", emc / 1_000_000_000.0);
            if (emc >= 1_000_000) return String.format("%.1fM", emc / 1_000_000.0);
            if (emc >= 1_000) return String.format("%.1fK", emc / 1_000.0);
            return String.valueOf(emc);
        }
    }
}
