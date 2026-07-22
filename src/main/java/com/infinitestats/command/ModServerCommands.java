package com.infinitestats.command;

import com.infinitestats.InfiniteStats;
import com.infinitestats.crafting.PortableCraftingMenu;
import com.infinitestats.furnace.PortableFurnaceMenu;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import com.infinitestats.stats.Waypoint;
import com.infinitestats.util.TeleportUtil;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.network.NetworkHooks;

/**
 * 服务端命令：跨维度传送与定点传送
 * 通过 /infstats 子命令提供，需在对应属性开关激活后使用
 */
public final class ModServerCommands {

    private ModServerCommands() {}

    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("infstats")
                        .then(Commands.literal("crossdim")
                                .then(Commands.argument("dimension", StringArgumentType.string())
                                        .executes(ModServerCommands::crossDim))
                                .executes(ModServerCommands::crossDimList))
                        .then(Commands.literal("wp")
                                .then(Commands.literal("set")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(ModServerCommands::wpSet)))
                                .then(Commands.literal("del")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(ModServerCommands::wpDel)))
                                .then(Commands.literal("list")
                                        .executes(ModServerCommands::wpList))
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .executes(ModServerCommands::wpTeleport)))
                        .then(Commands.literal("craft")
                                .executes(ModServerCommands::openCrafting))
                        .then(Commands.literal("furnace")
                                .executes(ModServerCommands::openFurnace))
        );
    }

    private static int crossDim(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("只能在玩家身上使用此命令"));
            return 0;
        }

        String dimArg = StringArgumentType.getString(ctx, "dimension").trim().toLowerCase();
        ResourceLocation rl = switch (dimArg) {
            case "overworld" -> Level.OVERWORLD.location();
            case "nether" -> Level.NETHER.location();
            case "end" -> Level.END.location();
            default -> dimArg.indexOf(':') < 0
                    ? new ResourceLocation("minecraft", dimArg)
                    : new ResourceLocation(dimArg);
        };
        ResourceKey<Level> targetKey = ResourceKey.create(Registries.DIMENSION, rl);

        PlayerStats stats = player.getCapability(PlayerStatsProvider.PLAYER_STATS).orElse(null);
        if (stats == null || !stats.isToggleActive("cross_dimension_teleport")) {
            source.sendFailure(Component.literal("未激活『跨维度传送』属性，无法跨维度传送"));
            return 0;
        }

        if (player.level().dimension() == targetKey) {
            source.sendFailure(Component.literal("你已经在该维度"));
            return 0;
        }

        ServerLevel target = getOrLoadLevel(player.getServer(), targetKey);
        if (target == null) {
            source.sendFailure(Component.literal("未知或无法加载的维度: " + rl + "（用 /infstats crossdim 查看可用维度）"));
            return 0;
        }

        // 强制跨维度传送：绕过外部「维度进入权限」（不触发可取消的 EntityTravelToDimensionEvent）
        TeleportUtil.forceTeleportTo(player, target, player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        source.sendSuccess(() -> Component.literal("§a已跨维度传送到 §f" + rl), false);
        return 1;
    }

    /** /infstats crossdim —— 列出当前世界已加载的全部维度。 */
    private static int crossDimList(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("只能在玩家身上使用此命令"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("§6当前世界已知的维度（含未加载）:"), false);
        List<String> dimNames = new ArrayList<>();
        for (ResourceKey<Level> key : player.getServer().levelKeys()) {
            dimNames.add(key.location().toString());
        }
        dimNames.sort((a, b) -> {
            boolean am = a.startsWith("minecraft:");
            boolean bm = b.startsWith("minecraft:");
            if (am != bm) return am ? -1 : 1;
            return a.compareTo(b);
        });
        String currentName = player.level().dimension().location().toString();
        for (String name : dimNames) {
            source.sendSuccess(() -> Component.literal("§f- " + name + (name.equals(currentName) ? " §a(当前)" : "")), false);
        }
        source.sendSuccess(() -> Component.literal("§7用法: /infstats crossdim <维度名>"), false);
        return 1;
    }

    /**
     * 获取指定维度；若维度已注册（存在于 levelKeys）但当前尚未加载到内存，
     * 则通过反射调用 MinecraftServer#loadLevel() 按需加载（仅补充缺失维度，不重复加载已加载维度）。
     */
    private static ServerLevel getOrLoadLevel(MinecraftServer server, ResourceKey<Level> key) {
        ServerLevel level = server.getLevel(key);
        if (level != null) return level;
        if (server.levelKeys().contains(key)) {
            try {
                Method loadLevel = MinecraftServer.class.getDeclaredMethod("loadLevel");
                loadLevel.setAccessible(true);
                loadLevel.invoke(server);
            } catch (Throwable ignored) {
                // 加载失败时下方再次获取，仍可能为 null，由调用方提示
            }
            level = server.getLevel(key);
        }
        return level;
    }

    private static int wpSet(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;

        String name = StringArgumentType.getString(ctx, "name");
        PlayerStats stats = player.getCapability(PlayerStatsProvider.PLAYER_STATS).orElse(null);
        if (stats == null || !stats.isToggleActive("fixed_point_teleport")) {
            source.sendFailure(Component.literal("未激活『定点传送』属性，无法保存传送点"));
            return 0;
        }

        ResourceKey<Level> dim = player.level().dimension();
        Waypoint wp = new Waypoint(dim.location().toString(), player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot());
        stats.setWaypoint(name, wp);
        source.sendSuccess(() -> Component.literal("§a已保存传送点 §f" + name), false);
        return 1;
    }

    private static int wpTeleport(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;

        String name = StringArgumentType.getString(ctx, "name");
        PlayerStats stats = player.getCapability(PlayerStatsProvider.PLAYER_STATS).orElse(null);
        if (stats == null || !stats.isToggleActive("fixed_point_teleport")) {
            source.sendFailure(Component.literal("未激活『定点传送』属性，无法使用传送点"));
            return 0;
        }

        Waypoint wp = stats.getWaypoint(name);
        if (wp == null) {
            source.sendFailure(Component.literal("传送点不存在: " + name));
            return 0;
        }

        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(wp.dimension));
        ServerLevel target = player.getServer().getLevel(dimKey);
        if (target == null) {
            source.sendFailure(Component.literal("传送点所在维度未加载: " + wp.dimension));
            return 0;
        }

        player.teleportTo(target, wp.x, wp.y, wp.z, wp.yaw, wp.pitch);
        source.sendSuccess(() -> Component.literal("§a已传送到传送点 §f" + name), false);
        return 1;
    }

    private static int wpDel(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;

        String name = StringArgumentType.getString(ctx, "name");
        PlayerStats stats = player.getCapability(PlayerStatsProvider.PLAYER_STATS).orElse(null);
        if (stats == null || !stats.isToggleActive("fixed_point_teleport")) {
            source.sendFailure(Component.literal("未激活『定点传送』属性"));
            return 0;
        }
        if (!stats.hasWaypoint(name)) {
            source.sendFailure(Component.literal("传送点不存在: " + name));
            return 0;
        }

        stats.removeWaypoint(name);
        source.sendSuccess(() -> Component.literal("§a已删除传送点 §f" + name), false);
        return 1;
    }

    private static int openCrafting(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;

        PlayerStats stats = player.getCapability(PlayerStatsProvider.PLAYER_STATS).orElse(null);
        if (stats == null || !stats.isToggleActive("portable_crafting")) {
            source.sendFailure(Component.literal("未激活『内置工作台』属性，无法打开随身工作台"));
            return 0;
        }

        MenuProvider provider = new SimpleMenuProvider(
                (id, inv, p) -> new PortableCraftingMenu(id, inv),
                Component.translatable("container.crafting"));
        NetworkHooks.openScreen(player, provider);
        return 1;
    }

    private static int openFurnace(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;

        PlayerStats stats = player.getCapability(PlayerStatsProvider.PLAYER_STATS).orElse(null);
        if (stats == null || !stats.isToggleActive("portable_furnace")) {
            source.sendFailure(Component.literal("未激活『内置熔炉』属性，无法打开随身熔炉"));
            return 0;
        }

        MenuProvider provider = new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.translatable("container.furnace");
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new PortableFurnaceMenu(id, inv);
            }
        };
        NetworkHooks.openScreen(player, provider, buf -> {});
        return 1;
    }

    private static int wpList(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;

        PlayerStats stats = player.getCapability(PlayerStatsProvider.PLAYER_STATS).orElse(null);
        if (stats == null || !stats.isToggleActive("fixed_point_teleport")) {
            source.sendFailure(Component.literal("未激活『定点传送』属性"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("§6已保存的传送点:"), false);
        for (var entry : stats.getWaypoints().entrySet()) {
            Waypoint wp = entry.getValue();
            source.sendSuccess(() -> Component.literal("§f- " + entry.getKey() + " §7[" + wp.dimension + " "
                    + String.format("%.1f, %.1f, %.1f", wp.x, wp.y, wp.z) + "]"), false);
        }
        return 1;
    }
}
