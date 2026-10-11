package com.infinitestats.command;

import com.infinitestats.Config;
import com.infinitestats.InfiniteStats;
import com.infinitestats.crafting.PortableAnvil;
import com.infinitestats.crafting.PortableCraftingMenu;
import com.infinitestats.crafting.PortableGuis;
import com.infinitestats.furnace.PortableFurnaceMenu;
import com.infinitestats.handler.AttributeHandler;
import com.infinitestats.network.NetworkHandler;
import com.infinitestats.stats.FeatureDisableMode;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import com.infinitestats.stats.StatType;
import com.infinitestats.stats.Waypoint;
import com.infinitestats.util.EnchantText;
import com.infinitestats.util.TeleportUtil;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
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
                        .then(Commands.literal("anvil")
                                .executes(ModServerCommands::openAnvil))
                        .then(Commands.literal("enderchest")
                                .executes(ModServerCommands::openEnderChest))
                        .then(Commands.literal("smithing")
                                .executes(ModServerCommands::openSmithing))
                        .then(Commands.literal("brewing")
                                .executes(ModServerCommands::openBrewing))
                        // 附魔上限突破的配套 OP 工具：/infstats enchant <附魔> <等级>
                        // 直接给主手物品写入任意等级（写入走 EnchantmentHelper，落库时由
                        // EnchantmentHelperMixin 写成 int，因此不受原版 short / 255 的截断限制）
                        .then(Commands.literal("enchant")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("enchantment", ResourceArgument.resource(
                                                event.getBuildContext(), Registries.ENCHANTMENT))
                                        .then(Commands.argument("level", IntegerArgumentType.integer(1))
                                                .executes(ModServerCommands::enchantItem))))
                        // 功能开关（按玩家）：/infstats feature [id] [on|off]
                        // 给整合包作者用的：开局默认禁用由配置 disabledStats 决定，做任务后用指令逐步开放；
                        // 玩家没有界面可自行开关，指令一律要求权限等级 2（OP），普通玩家连子命令都看不到。
                        .then(Commands.literal("feature")
                                .requires(source -> source.hasPermission(2))
                                .executes(ModServerCommands::featureList)
                                .then(Commands.literal("list")
                                        .executes(ModServerCommands::featureList))
                                .then(Commands.literal("all")
                                        .then(Commands.literal("on")
                                                .executes(ctx -> featureAll(ctx, false)))
                                        .then(Commands.literal("off")
                                                .executes(ctx -> featureAll(ctx, true))))
                                .then(Commands.literal("mode")
                                        .executes(ModServerCommands::featureModeShow)
                                        .then(Commands.argument("mode", StringArgumentType.word())
                                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                                        List.of("keep", "refund", "hide"), b))
                                                .executes(ModServerCommands::featureModeSet)))
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests(ModServerCommands::suggestFeatureIds)
                                        .executes(ModServerCommands::featureToggle)
                                        .then(Commands.literal("on")
                                                .executes(ctx -> featureSet(ctx, false)))
                                        .then(Commands.literal("off")
                                                .executes(ctx -> featureSet(ctx, true)))))
        );
    }

    /**
     * /infstats enchant &lt;附魔&gt; &lt;等级&gt; —— 给主手物品直接写入任意等级的附魔（OP 工具）。
     * <p>
     * 与「进阶高级附魔台」不同，这里刻意<b>不</b>校验「附魔上限突破」属性：它是给整合包作者 / 管理员的
     * 发物品与调试工具（对应参考实现的 {@code /cenchant}），等级上限由 int 本身兜底。
     * 写入走 {@link EnchantmentHelper#setEnchantments}，因此同样受 Mixin 的 int 存储支持，
     * 写出来的等级不会在保存 / 读取时被截断。
     */
    private static int enchantItem(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("只能在玩家身上使用此命令"));
            return 0;
        }
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            source.sendFailure(Component.literal("请先把要附魔的物品拿在主手"));
            return 0;
        }

        Enchantment enchantment = ResourceArgument.getEnchantment(ctx, "enchantment").value();
        int level = IntegerArgumentType.getInteger(ctx, "level");

        Map<Enchantment, Integer> enchantments = new LinkedHashMap<>(EnchantmentHelper.getEnchantments(stack));
        enchantments.put(enchantment, level);
        EnchantmentHelper.setEnchantments(enchantments, stack);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();

        source.sendSuccess(() -> Component.translatable("message.infinitestats.enchant.given",
                EnchantText.name(enchantment, level), level), true);
        return 1;
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
            source.sendFailure(featureDisabledReason(stats, "cross_dimension_teleport",
                    "跨维度传送", "未激活『跨维度传送』属性，无法跨维度传送"));
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
            source.sendFailure(featureDisabledReason(stats, "fixed_point_teleport",
                    "定点传送", "未激活『定点传送』属性，无法保存传送点"));
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
            source.sendFailure(featureDisabledReason(stats, "fixed_point_teleport",
                    "定点传送", "未激活『定点传送』属性，无法使用传送点"));
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
            source.sendFailure(featureDisabledReason(stats, "fixed_point_teleport",
                    "定点传送", "未激活『定点传送』属性"));
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

    /**
     * 统一的「功能不可用」提示：被玩家在「功能开关」里关闭时给出明确原因，
     * 否则沿用原来的「未激活 / 未解锁」提示。
     */
    private static Component featureDisabledReason(PlayerStats stats, String statId,
                                                   String feature, String fallback) {
        if (stats != null && stats.isStatInactive(statId)) {
            return Component.literal("『" + feature + "』尚未开放"
                    + "（完成对应任务 / 由整合包作者开启）");
        }
        return Component.literal(fallback);
    }

    // ========== 功能开关（按玩家，整合包作者通过指令控制开放进度） ==========

    /** 列出当前已关闭的功能。 */
    private static int featureList(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;

        PlayerStats stats = player.getCapability(PlayerStatsProvider.PLAYER_STATS).orElse(null);
        if (stats == null) return 0;

        Set<String> disabled = stats.getDisabledStats();
        source.sendSuccess(() -> Component.literal("§6功能开关：§7共 " + StatType.getBuiltinCount()
                + " 条内置属性，当前已关闭 §c" + disabled.size() + " §7条"), false);
        if (disabled.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    "§7全部功能均为开启状态。用法：§f/infstats feature <属性ID> [on|off]"), false);
            return 1;
        }
        StringBuilder sb = new StringBuilder();
        for (String id : disabled) {
            if (sb.length() > 0) sb.append("§7, §c");
            sb.append(id);
        }
        source.sendSuccess(() -> Component.literal("§7已关闭：§c" + sb), false);
        source.sendSuccess(() -> Component.literal(
                "§7开启：§f/infstats feature <属性ID> on§7；关闭：§f/infstats feature <属性ID> off"
                        + "§7；不带参数则切换"), false);
        return 1;
    }

    /** /infstats feature <id>：切换单个功能。 */
    private static int featureToggle(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;

        PlayerStats stats = player.getCapability(PlayerStatsProvider.PLAYER_STATS).orElse(null);
        if (stats == null) return 0;

        String id = StringArgumentType.getString(ctx, "id");
        StatType stat = StatType.fromId(id);
        if (stat == null) {
            source.sendFailure(Component.literal("未知属性 ID：" + id + "（可用 Tab 补全）"));
            return 0;
        }
        return applyFeature(source, player, stats, List.of(stat.getId()),
                !stats.isStatDisabled(stat.getId()));
    }

    /** /infstats feature <id> <on|off>：明确设定单个功能。 */
    private static int featureSet(CommandContext<CommandSourceStack> ctx, boolean disabled) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;

        PlayerStats stats = player.getCapability(PlayerStatsProvider.PLAYER_STATS).orElse(null);
        if (stats == null) return 0;

        String id = StringArgumentType.getString(ctx, "id");
        StatType stat = StatType.fromId(id);
        if (stat == null) {
            source.sendFailure(Component.literal("未知属性 ID：" + id + "（可用 Tab 补全）"));
            return 0;
        }
        return applyFeature(source, player, stats, List.of(stat.getId()), disabled);
    }

    /** /infstats feature all <on|off>：一次开关全部内置属性。 */
    private static int featureAll(CommandContext<CommandSourceStack> ctx, boolean disabled) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;

        PlayerStats stats = player.getCapability(PlayerStatsProvider.PLAYER_STATS).orElse(null);
        if (stats == null) return 0;

        List<String> ids = new ArrayList<>();
        for (StatType stat : StatType.ALL_STATS) {
            if (stat.isBuiltin()) ids.add(stat.getId());
        }
        return applyFeature(source, player, stats, ids, disabled);
    }

    /** 应用功能开关：写入玩家数据、立刻重算属性加成，并同步给客户端。 */
    private static int applyFeature(CommandSourceStack source, ServerPlayer player, PlayerStats stats,
                                    List<String> ids, boolean disabled) {
        if (ids == null || ids.isEmpty()) return 0;

        stats.setStatsDisabled(ids, disabled);
        AttributeHandler.applyAllAttributes(player, stats);
        NetworkHandler.syncToClient(player);

        String what = ids.size() == 1 ? "『" + ids.get(0) + "』" : ids.size() + " 项功能";
        source.sendSuccess(() -> Component.literal(disabled
                ? "§c已关闭 " + what + "§7：面板隐藏、整条失效（已投入的点数保留）"
                : "§a已开启 " + what + "§7：面板恢复显示，效果与指令恢复可用"), true);
        return ids.size();
    }

    /** /infstats feature mode：显示当前执行模式（全局，整合包设置）。 */
    private static int featureModeShow(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        FeatureDisableMode mode = FeatureDisableMode.current();

        source.sendSuccess(() -> Component.literal(
                "§6功能开关执行模式（全局）：§f" + describeMode(mode)), false);
        source.sendSuccess(() -> Component.literal(
                "§7keep = 整条失效·保留点数；refund = 整条失效·返还点数；hide = 只隐藏（效果照常）"), false);
        source.sendSuccess(() -> Component.literal(
                "§7切换：§f/infstats feature mode <keep|refund|hide>§7；或直接改配置文件 "
                        + "§fconfig/infinitestats-common.toml §7→ §fGUI.featureDisableMode"), false);
        return 1;
    }

    /**
     * /infstats feature mode <keep|refund|hide>：改写**全局**配置（影响所有玩家，包括新玩家）。
     * 这是整合包作者的设置项，所以也放在要求权限等级 2 的 {@code feature} 子树里。
     */
    private static int featureModeSet(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        FeatureDisableMode mode = FeatureDisableMode.byId(StringArgumentType.getString(ctx, "mode"));
        if (mode == null) {
            source.sendFailure(Component.literal("可用值：keep / refund / hide"));
            return 0;
        }
        if (mode == FeatureDisableMode.current()) {
            source.sendSuccess(() -> Component.literal(
                    "§7执行模式已经是：" + describeMode(mode)), false);
            return 1;
        }

        Config.FEATURE_DISABLE_MODE.set(mode);
        try {
            Config.SPEC.save();
        } catch (Throwable ignored) {
            // 配置文件只读或尚未生成：内存里的值立刻生效，重启后会回到文件里的值
        }

        // 模式决定「数值是否归零」，当场给所有在线玩家重算属性并同步
        int affected = 0;
        if (source.getServer() != null) {
            for (ServerPlayer online : source.getServer().getPlayerList().getPlayers()) {
                online.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
                    stats.refreshFeatureMode();
                    AttributeHandler.applyAllAttributes(online, stats);
                    NetworkHandler.syncToClient(online);
                });
                affected++;
            }
        }

        final int count = affected;
        source.sendSuccess(() -> Component.literal("§a执行模式已设为（全局）：" + describeMode(mode)
                + "§7，已为 " + count + " 名在线玩家重算"), true);
        return 1;
    }

    private static String describeMode(FeatureDisableMode mode) {
        return switch (mode) {
            case INACTIVE_KEEP_POINTS -> "整条失效（保留点数）";
            case INACTIVE_REFUND_POINTS -> "整条失效（返还点数）";
            case HIDDEN_ONLY -> "只隐藏（不返还点数，效果与指令照常）";
        };
    }

    /** Tab 补全：所有属性 ID（内置 + 外部发现）。 */
    private static CompletableFuture<Suggestions> suggestFeatureIds(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        List<String> ids = new ArrayList<>();
        for (StatType stat : StatType.ALL_STATS) ids.add(stat.getId());
        return SharedSuggestionProvider.suggest(ids, builder);
    }

    private static int openCrafting(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;

        PlayerStats stats = player.getCapability(PlayerStatsProvider.PLAYER_STATS).orElse(null);
        if (stats == null || !stats.isToggleActive("portable_crafting")) {
            source.sendFailure(featureDisabledReason(stats, "portable_crafting",
                    "随身工作台", "未激活『内置工作台』属性，无法打开随身工作台"));
            return 0;
        }

        MenuProvider provider = new SimpleMenuProvider(
                (id, inv, p) -> new PortableCraftingMenu(id, inv),
                Component.translatable("container.crafting"));
        NetworkHooks.openScreen(player, provider);
        return 1;
    }

    private static int openAnvil(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;

        PlayerStats stats = player.getCapability(PlayerStatsProvider.PLAYER_STATS).orElse(null);
        if (stats == null || !stats.isToggleActive("portable_anvil")) {
            source.sendFailure(featureDisabledReason(stats, "portable_anvil",
                    "随身铁砧", "未激活『随身铁砧』属性，无法打开随身铁砧"));
            return 0;
        }

        PortableAnvil.open(player);
        return 1;
    }

    private static int openEnderChest(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;

        PlayerStats stats = player.getCapability(PlayerStatsProvider.PLAYER_STATS).orElse(null);
        if (stats == null || !stats.isToggleActive("portable_ender_chest")) {
            source.sendFailure(featureDisabledReason(stats, "portable_ender_chest",
                    "随身末影箱", "未激活『随身末影箱』属性，无法打开随身末影箱"));
            return 0;
        }

        PortableGuis.openEnderChest(player);
        return 1;
    }

    private static int openSmithing(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;

        PlayerStats stats = player.getCapability(PlayerStatsProvider.PLAYER_STATS).orElse(null);
        if (stats == null || !stats.isToggleActive("portable_smithing")) {
            source.sendFailure(featureDisabledReason(stats, "portable_smithing",
                    "随身锻造台", "未激活『随身锻造台』属性，无法打开随身锻造台"));
            return 0;
        }

        PortableGuis.openSmithing(player);
        return 1;
    }

    private static int openBrewing(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;

        PlayerStats stats = player.getCapability(PlayerStatsProvider.PLAYER_STATS).orElse(null);
        if (stats == null || !stats.isToggleActive("portable_brewing")) {
            source.sendFailure(featureDisabledReason(stats, "portable_brewing",
                    "随身酿造台", "未激活『随身酿造台』属性，无法打开随身酿造台"));
            return 0;
        }

        PortableGuis.openBrewing(player);
        return 1;
    }

    private static int openFurnace(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;

        PlayerStats stats = player.getCapability(PlayerStatsProvider.PLAYER_STATS).orElse(null);
        if (stats == null || !stats.isToggleActive("portable_furnace")) {
            source.sendFailure(featureDisabledReason(stats, "portable_furnace",
                    "随身熔炉", "未激活『内置熔炉』属性，无法打开随身熔炉"));
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
            source.sendFailure(featureDisabledReason(stats, "fixed_point_teleport",
                    "定点传送", "未激活『定点传送』属性"));
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
