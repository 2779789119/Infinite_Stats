package com.infinitestats.command;

import com.infinitestats.Config;
import com.infinitestats.InfiniteStats;
import com.infinitestats.crafting.PortableAnvil;
import com.infinitestats.network.NetworkHandler;
import com.infinitestats.stats.FeatureDisableMode;
import com.infinitestats.stats.StatType;
import com.infinitestats.crafting.PortableCraftingMenu;
import com.infinitestats.furnace.PortableFurnaceMenu;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import com.infinitestats.stats.Waypoint;
import com.infinitestats.util.TeleportUtil;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.EntityArgument;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.RegisterCommandsEvent;


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
                        .then(Commands.literal("enchant")
                                .then(Commands.argument("enchantment", StringArgumentType.string())
                                        .then(Commands.argument("level", IntegerArgumentType.integer(0, 255))
                                                .executes(ModServerCommands::enchantSet))))
                        .then(Commands.literal("feature")
                                .requires(s -> s.hasPermission(2))
                                .then(Commands.literal("list")
                                        .executes(ModServerCommands::featureList))
                                .then(Commands.literal("all")
                                        .then(Commands.literal("on")
                                                .executes(ctx -> featureAll(ctx, true)))
                                        .then(Commands.literal("off")
                                                .executes(ctx -> featureAll(ctx, false))))
                                .then(Commands.literal("mode")
                                        .then(Commands.argument("mode", StringArgumentType.word())
                                                .executes(ModServerCommands::featureMode)))
                                .then(Commands.argument("stat", StringArgumentType.word())
                                        .then(Commands.literal("on")
                                                .executes(ctx -> featureSet(ctx, true)))
                                        .then(Commands.literal("off")
                                                .executes(ctx -> featureSet(ctx, false)))))
                        // 外部属性热重扫：/infstats reload external（OP）
                        // 改完 config/infinitestats/external_attributes.json 后执行，无需重启
                        .then(Commands.literal("reload")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.literal("external")
                                        .executes(ctx -> reloadExternal(ctx.getSource()))))
                        // 属性点数：/infstats points [get|add|set]
                        // 查询对所有人开放（只能查自己，OP 可查别人）；add / set 是 OP 工具，
                        // 整合包的任务奖励也走这里（例如 /infstats points add @p 50）。
                        .then(Commands.literal("points")
                                .executes(ModServerCommands::pointsShowSelf)
                                .then(Commands.literal("get")
                                        .executes(ModServerCommands::pointsShowSelf)
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .executes(ModServerCommands::pointsShowTargets)))
                                .then(Commands.literal("add")
                                        .requires(source -> source.hasPermission(2))
                                        // 先给「只填数量 = 给自己」的简写，再给「目标 + 数量」的完整形式。
                                        // 两个子节点参数类型不同（long / 玩家），Brigadier 会按输入自动分流。
                                        .then(Commands.argument("amount", LongArgumentType.longArg(
                                                        -1_000_000_000L, 1_000_000_000L))
                                                .executes(ModServerCommands::pointsAddSelf))
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .then(Commands.argument("amount", LongArgumentType.longArg(
                                                                -1_000_000_000L, 1_000_000_000L))
                                                        .executes(ModServerCommands::pointsAddTargets))))
                                .then(Commands.literal("set")
                                        .requires(source -> source.hasPermission(2))
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .then(Commands.argument("amount", LongArgumentType.longArg(
                                                                0L, 1_000_000_000_000L))
                                                        .executes(ModServerCommands::pointsSetTargets)))))
        );
    }

    // ========== 附魔等级指定（/infstats enchant） ==========

    /**
     * {@code /infstats enchant <附魔> <等级>}：把手持物品上的这条附魔直接设成指定等级
     * （0 = 移除该附魔）。
     * <p>
     * 需要「附魔上限突破」已解锁：这是突破上限之后的配套工具，未解锁时只允许设到整合包上限，
     * 与附魔台 / 铁砧的口径一致（见 {@link com.infinitestats.util.EnchantLimits}）。
     * <p>
     * 1.21 起附魔等级存在 {@code ItemEnchantments} 组件里（底层 int，不再有 short/255 的截断），
     * 所以这里直接改组件、不走 NBT 手工拼写。
     */
    private static int enchantSet(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("只能在玩家身上使用此命令"));
            return 0;
        }
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            source.sendFailure(Component.literal("请手持一件物品"));
            return 0;
        }

        ResourceLocation enchantId = ResourceLocation.tryParse(StringArgumentType.getString(ctx, "enchantment"));
        if (enchantId == null) {
            source.sendFailure(Component.literal("附魔 id 格式不对（应形如 minecraft:sharpness）"));
            return 0;
        }
        int level = IntegerArgumentType.getInteger(ctx, "level");

        // 1.21 的注册表查找按 ResourceKey 取（HolderGetter 没有按 ResourceLocation 的重载）
        var registry = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        ResourceKey<net.minecraft.world.item.enchantment.Enchantment> enchantKey =
                ResourceKey.create(Registries.ENCHANTMENT, enchantId);
        var holder = registry.get(enchantKey).orElse(null);
        if (holder == null) {
            source.sendFailure(Component.literal("未知附魔: " + enchantId));
            return 0;
        }

        if (level > 0 && !com.infinitestats.util.EnchantLimits.isUnlocked(player)) {
            int cap = com.infinitestats.util.EnchantLimits.packCap(holder.value());
            if (level > cap) {
                source.sendFailure(Component.literal("未解锁『附魔上限突破』，该附魔最高只能到 " + cap + " 级"));
                return 0;
            }
        }

        var current = held.getOrDefault(net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        var mutable = new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(current);
        if (level <= 0) {
            mutable.set(holder, 0);
        } else {
            mutable.set(holder, level);
        }
        held.set(net.minecraft.core.component.DataComponents.ENCHANTMENTS, mutable.toImmutable());

        int finalLevel = level;
        source.sendSuccess(() -> Component.literal(level <= 0
                ? "§c已移除附魔 §f" + enchantId
                : "§a已把 §f" + enchantId + "§a 设为 §f" + finalLevel + " §a级"), true);
        return 1;
    }

    // ========== 功能开关（/infstats feature） ==========

    /**
     * 「功能开关」：逐条关闭 / 开启属性，或切换「关掉时」的执行模式。
     * <p>
     * 整条指令需要 OP（权限等级 2）—— 这是「作者预设 + 任务指令开放」的工具，
     * 普通玩家不应自行绕过整合包设定。
     */
    private static int featureSet(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("只能在玩家身上使用此命令"));
            return 0;
        }
        String statId = StringArgumentType.getString(ctx, "stat");
        if (StatType.fromId(statId) == null) {
            source.sendFailure(Component.literal("未知属性: " + statId));
            return 0;
        }
        PlayerStatsProvider.of(player).ifPresent(stats -> {
            stats.setStatDisabled(statId, !enabled);
            NetworkHandler.syncToClient(player);
            FeatureDisableMode mode = FeatureDisableMode.current();
            String note = !enabled && mode.isInactive() ? "§7（整条失效）" : "";
            source.sendSuccess(() -> Component.literal(
                    (enabled ? "§a已开启 " : "§c已关闭 ") + statId + note), true);
        });
        return 1;
    }

    private static int featureAll(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("只能在玩家身上使用此命令"));
            return 0;
        }
        List<String> ids = new ArrayList<>();
        for (StatType stat : StatType.ALL_STATS) ids.add(stat.getId());
        PlayerStatsProvider.of(player).ifPresent(stats -> {
            stats.setStatsDisabled(ids, !enabled);
            NetworkHandler.syncToClient(player);
            source.sendSuccess(() -> Component.literal(
                    "§6已" + (enabled ? "开启" : "关闭") + "全部 " + ids.size() + " 条属性"), true);
        });
        return 1;
    }

    private static int featureList(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("只能在玩家身上使用此命令"));
            return 0;
        }
        PlayerStatsProvider.of(player).ifPresent(stats -> {
            FeatureDisableMode mode = FeatureDisableMode.current();
            source.sendSuccess(() -> Component.literal("§6当前模式: §f" + mode.getId()
                    + "§7（" + (mode.isInactive() ? "整条失效" : "仅隐藏") + "）"), false);
            Set<String> disabled = stats.getDisabledStats();
            if (disabled.isEmpty()) {
                source.sendSuccess(() -> Component.literal("§7没有被关闭的属性"), false);
                return;
            }
            source.sendSuccess(() -> Component.literal("§6已关闭的属性（" + disabled.size() + "）:"), false);
            for (String id : disabled) {
                source.sendSuccess(() -> Component.literal("§f- " + id), false);
            }
        });
        return 1;
    }

    private static int featureMode(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String id = StringArgumentType.getString(ctx, "mode");
        FeatureDisableMode mode = FeatureDisableMode.byId(id);
        if (mode == null) {
            source.sendFailure(Component.literal("未知模式: " + id + "（可选 keep / refund / hide）"));
            return 0;
        }
        try {
            Config.FEATURE_DISABLE_MODE.set(mode);
            Config.SPEC.save();
        } catch (Throwable t) {
            source.sendFailure(Component.literal("写入配置失败: " + t.getMessage()));
            return 0;
        }
        MinecraftServer server = source.getServer();
        if (server != null) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                PlayerStatsProvider.of(p).ifPresent(PlayerStats::refreshFeatureMode);
            }
        }
        source.sendSuccess(() -> Component.literal("§a「功能开关」模式已切换为 §f" + mode.getId()), true);
        return 1;
    }

    // ========== 外部属性热重扫 ==========

    /**
     * {@code /infstats reload external}：重新读配置 + 重扫属性注册表 + 刷新在线玩家。
     * <p>
     * 改完 {@code config/infinitestats/external_attributes.json} 不必重启：内置属性、等级与已投入的点数
     * 都不受影响（玩家数据按 statId 存，重扫前后 id 不变），只是外部属性列表按新配置重建，
     * 然后给每个在线玩家重写一遍属性修饰符并把新列表同步给客户端。
     */
    private static int reloadExternal(CommandSourceStack source) {
        String error = com.infinitestats.stats.ExternalAttributeConfig.getLastError();
        int total = com.infinitestats.stats.StatType.reloadExternalAttributes();

        int players = 0;
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            PlayerStats stats = PlayerStatsProvider.of(player).orElse(null);
            if (stats == null) continue;
            com.infinitestats.handler.AttributeHandler.applyAllAttributes(player, stats);
            NetworkHandler.syncToClient(player);
            // 外部属性清单变了：客户端必须用新的那份覆盖自己扫的结果
            NetworkHandler.syncExternalStats(player);
            players++;
        }

        String message = "外部属性已重扫：" + total + " 条，已刷新 " + players + " 名在线玩家";
        if (error != null && !error.isEmpty()) {
            message += "（配置文件解析失败，已沿用上一次配置：" + error + "）";
        }
        String finalMessage = message;
        source.sendSuccess(() -> Component.literal(finalMessage), true);
        return total;
    }

    // ========== 属性点数（查询 / 增加 / 设置） ==========

    /** /infstats points：查看自己的等级与可用点数。 */
    private static int pointsShowSelf(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal(
                    "控制台请用 /infstats points get <玩家> 查询指定玩家"));
            return 0;
        }
        PlayerStats stats = PlayerStatsProvider.of(player).orElse(null);
        if (stats == null) return 0;
        sendPointsLine(source, player, stats);
        return 1;
    }

    /** /infstats points get <玩家>：查看指定玩家的等级与可用点数。 */
    private static int pointsShowTargets(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Collection<? extends ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        int shown = 0;
        for (ServerPlayer target : targets) {
            PlayerStats stats = PlayerStatsProvider.of(target).orElse(null);
            if (stats == null) continue;
            sendPointsLine(source, target, stats);
            shown++;
        }
        return shown;
    }

    /** /infstats points add <数量>：给自己增加（负数为扣除）。 */
    private static int pointsAddSelf(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal(
                    "控制台请用 /infstats points add <玩家> <数量>"));
            return 0;
        }
        long amount = LongArgumentType.getLong(ctx, "amount");
        return applyPoints(source, List.of(player), amount, false);
    }

    /** /infstats points add <玩家> <数量>：给指定玩家增加（负数为扣除）。 */
    private static int pointsAddTargets(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Collection<? extends ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        long amount = LongArgumentType.getLong(ctx, "amount");
        return applyPoints(source, targets, amount, false);
    }

    /** /infstats points set <玩家> <数量>：把可用点数设成指定值。 */
    private static int pointsSetTargets(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Collection<? extends ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        long amount = LongArgumentType.getLong(ctx, "amount");
        return applyPoints(source, targets, amount, true);
    }

    /**
     * 写入可用点数并同步客户端。
     *
     * @param amount   增量（absolute=false）或目标值（absolute=true）
     * @param absolute true = 直接设为该值，false = 在当前值上增减
     */
    private static int applyPoints(CommandSourceStack source, Collection<? extends ServerPlayer> targets,
                                   long amount, boolean absolute) {
        int changed = 0;
        for (ServerPlayer target : targets) {
            PlayerStats stats = PlayerStatsProvider.of(target).orElse(null);
            if (stats == null) continue;

            // 写入走 PlayerStats 的「额外授予」记账：差额会落进 NBT，
            // 因此退出存档再进来（登录重算）或之后升级都不会被等级公式覆盖。
            if (absolute) {
                stats.setAvailablePoints(amount);
            } else {
                stats.addAvailablePoints(amount);
            }
            NetworkHandler.syncToClient(target);

            final long newValue = stats.getAvailablePoints();
            final String shown = absolute
                    ? String.valueOf(newValue)
                    : (amount >= 0 ? "+" + amount : String.valueOf(amount));
            source.sendSuccess(() -> Component.literal("§a已为 §f" + target.getScoreboardName()
                    + (absolute ? " §a设置可用点数为 §f" : " §a添加 §f")
                    + shown + " §a点§7（当前 §f" + newValue + "§7）"), false);
            changed++;
        }
        if (changed == 0) {
            source.sendFailure(Component.literal("没有可修改的在线玩家"));
        }
        return changed;
    }

    private static void sendPointsLine(CommandSourceStack source, ServerPlayer player, PlayerStats stats) {
        final String name = player.getScoreboardName();
        final long level = stats.getLevel();
        final long available = stats.getAvailablePoints();
        final long allocated = stats.getTotalAllocatedPoints();
        source.sendSuccess(() -> Component.literal("§6" + name
                + " §7等级 §f" + level
                + " §7· 可用点数 §a" + available
                + " §7· 已分配 §f" + allocated), false);
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
                    ? ResourceLocation.fromNamespaceAndPath("minecraft", dimArg)
                    : ResourceLocation.parse(dimArg);
        };
        ResourceKey<Level> targetKey = ResourceKey.create(Registries.DIMENSION, rl);

        PlayerStats stats = PlayerStatsProvider.of(player).orElse(null);
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
        PlayerStats stats = PlayerStatsProvider.of(player).orElse(null);
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
        PlayerStats stats = PlayerStatsProvider.of(player).orElse(null);
        if (stats == null || !stats.isToggleActive("fixed_point_teleport")) {
            source.sendFailure(Component.literal("未激活『定点传送』属性，无法使用传送点"));
            return 0;
        }

        Waypoint wp = stats.getWaypoint(name);
        if (wp == null) {
            source.sendFailure(Component.literal("传送点不存在: " + name));
            return 0;
        }

        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(wp.dimension));
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
        PlayerStats stats = PlayerStatsProvider.of(player).orElse(null);
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

        PlayerStats stats = PlayerStatsProvider.of(player).orElse(null);
        if (stats == null || !stats.isToggleActive("portable_crafting")) {
            source.sendFailure(Component.literal("未激活『内置工作台』属性，无法打开随身工作台"));
            return 0;
        }

        MenuProvider provider = new SimpleMenuProvider(
                (id, inv, p) -> new PortableCraftingMenu(id, inv),
                Component.translatable("container.crafting"));
        player.openMenu(provider);
        return 1;
    }

    private static int openAnvil(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;

        PlayerStats stats = PlayerStatsProvider.of(player).orElse(null);
        if (stats == null || !stats.isToggleActive("portable_anvil")) {
            source.sendFailure(Component.literal("未激活『随身铁砧』属性，无法打开随身铁砧"));
            return 0;
        }

        PortableAnvil.open(player);
        return 1;
    }

    private static int openFurnace(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;

        PlayerStats stats = PlayerStatsProvider.of(player).orElse(null);
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
        player.openMenu(provider, buf -> {});
        return 1;
    }

    private static int wpList(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;

        PlayerStats stats = PlayerStatsProvider.of(player).orElse(null);
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
