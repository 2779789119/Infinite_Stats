package com.infinitestats.client;

import com.infinitestats.InfiniteStats;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端命令注册 - 用于调整 HUD 等客户端设置
 */
@Mod.EventBusSubscriber(modid = InfiniteStats.MODID, value = Dist.CLIENT)
public final class ClientCommands {

    private ClientCommands() {}

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("infstats")
                .then(Commands.literal("hud")
                    // 显示当前位置
                    .executes(ctx -> showHudInfo(ctx.getSource()))

                    // 预设位置
                    .then(Commands.literal("preset")
                        .then(Commands.argument("position", StringArgumentType.word())
                            .suggests((ctx, builder) -> {
                                String[] presets = {"topleft", "topright", "bottomleft", "bottomright"};
                                for (String p : presets) builder.suggest(p);
                                return builder.buildFuture();
                            })
                            .executes(ctx -> setHudPreset(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "position")))
                        )
                    )

                    // 设置 X
                    .then(Commands.literal("x")
                        .then(Commands.argument("value", IntegerArgumentType.integer(0))
                            .executes(ctx -> setHudX(ctx.getSource(),
                                    IntegerArgumentType.getInteger(ctx, "value")))
                        )
                    )

                    // 设置 Y
                    .then(Commands.literal("y")
                        .then(Commands.argument("value", IntegerArgumentType.integer(0))
                            .executes(ctx -> setHudY(ctx.getSource(),
                                    IntegerArgumentType.getInteger(ctx, "value")))
                        )
                    )

                    // 重置
                    .then(Commands.literal("reset")
                        .executes(ctx -> resetHud(ctx.getSource()))
                    )

                    // 编辑模式（拖拽移动）
                    .then(Commands.literal("edit")
                        .executes(ctx -> toggleEditHud(ctx.getSource()))
                    )
                )
                .then(Commands.literal("achievements")
                    .executes(ctx -> openAchievementManager(ctx.getSource()))
                )
        );
    }

    private static int showHudInfo(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable("message.infinitestats.hud_info",
                ClientSettings.hudX, ClientSettings.hudY), false);
        return 1;
    }

    private static int setHudX(CommandSourceStack source, int x) {
        ClientSettings.hudX = clampX(x);
        ClientSettings.save();
        source.sendSuccess(() -> Component.translatable("message.infinitestats.hud_moved",
                ClientSettings.hudX, ClientSettings.hudY), false);
        return 1;
    }

    private static int setHudY(CommandSourceStack source, int y) {
        ClientSettings.hudY = clampY(y);
        ClientSettings.save();
        source.sendSuccess(() -> Component.translatable("message.infinitestats.hud_moved",
                ClientSettings.hudX, ClientSettings.hudY), false);
        return 1;
    }

    private static int resetHud(CommandSourceStack source) {
        ClientSettings.hudX = 4;
        ClientSettings.hudY = 4;
        ClientSettings.save();
        source.sendSuccess(() -> Component.translatable("message.infinitestats.hud_reset"), false);
        return 1;
    }

    private static int setHudPreset(CommandSourceStack source, String preset) {
        Minecraft mc = Minecraft.getInstance();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        switch (preset.toLowerCase()) {
            case "topleft" -> {
                ClientSettings.hudX = 4;
                ClientSettings.hudY = 4;
            }
            case "topright" -> {
                ClientSettings.hudX = screenW - StatsHudOverlay.PANEL_WIDTH;
                ClientSettings.hudY = 4;
            }
            case "bottomleft" -> {
                ClientSettings.hudX = 4;
                ClientSettings.hudY = screenH - 60;
            }
            case "bottomright" -> {
                ClientSettings.hudX = screenW - StatsHudOverlay.PANEL_WIDTH;
                ClientSettings.hudY = screenH - 60;
            }
            default -> {
                source.sendFailure(Component.translatable("message.infinitestats.hud_preset_invalid",
                        "topleft/topright/bottomleft/bottomright"));
                return 0;
            }
        }

        ClientSettings.save();
        source.sendSuccess(() -> Component.translatable("message.infinitestats.hud_preset_set",
                preset.toLowerCase(), ClientSettings.hudX, ClientSettings.hudY), false);
        return 1;
    }

    private static int clampX(int x) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return Math.max(0, x);
        int maxW = mc.getWindow().getGuiScaledWidth() - StatsHudOverlay.PANEL_WIDTH;
        return Math.max(0, Math.min(x, maxW));
    }

    private static int clampY(int y) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return Math.max(0, y);
        int maxH = mc.getWindow().getGuiScaledHeight() - 60;
        return Math.max(0, Math.min(y, maxH));
    }

    private static int toggleEditHud(CommandSourceStack source) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof HudEditScreen) {
            // 已经在编辑界面 → 关闭
            mc.setScreen(null);
            source.sendSuccess(() -> Component.translatable("message.infinitestats.hud_edit_exit"), false);
        } else {
            // 打开编辑界面
            mc.setScreen(new HudEditScreen());
        }
        return 1;
    }

    private static int openAchievementManager(CommandSourceStack source) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new AchievementManagerScreen());
        return 1;
    }
}
