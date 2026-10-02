package com.infinitestats.client;

import com.infinitestats.InfiniteStats;
import com.infinitestats.emc.EmcMenu;
import com.infinitestats.emc.ModMenuTypes;
import com.infinitestats.crafting.PortableCraftingMenu;
import com.infinitestats.furnace.PortableFurnaceMenu;
import com.infinitestats.furnace.FurnaceFuelBufferMenu;
import com.infinitestats.furnace.FurnaceOrePriorityMenu;
import com.infinitestats.client.PortableFurnaceScreen;
import com.infinitestats.client.FurnaceOrePriorityScreen;
import com.infinitestats.handler.CooldownHandler;
import com.infinitestats.network.NetworkHandler;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import com.infinitestats.stats.StatType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 客户端事件处理器 — 处理按键输入和 GUI 覆盖层注册
 */
@Mod.EventBusSubscriber(modid = InfiniteStats.MODID, value = Dist.CLIENT)
public final class ClientEventHandler {

    // ========== 使用速度加速（客户端动画同步） ==========

    /**
     * 客户端侧同步，否则 eating/drinking 动画不会加速
     */
    @SubscribeEvent
    public static void onItemUseTick(LivingEntityUseItemEvent.Tick event) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        if (event.getEntity() != player) return;
        if (event.getDuration() <= 0) return;

        player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
            boolean isBow = event.getItem().getItem() instanceof BowItem
                    || event.getItem().getItem() instanceof CrossbowItem
                    || event.getItem().getItem() instanceof TridentItem;

            float totalSpeed = 0;
            if (!isBow) {
                totalSpeed = stats.getStatValue(StatType.fromId("use_speed"));
            } else {
                totalSpeed = stats.getStatValue(StatType.fromId("bow_draw_speed"));
            }

            if (totalSpeed > 0) {
                int extraReduction = Math.max(1, (int) (totalSpeed * 100));
                event.setDuration(Math.max(0, event.getDuration() - extraReduction));
            }
        });
    }

    // ========== 冷却缩减（客户端同步） ==========

    /**
     * 客户端也持有自己的一份物品冷却（{@link LocalPlayer}），服务端那侧的加速不会同步过来。
     * 这里若不同步推进，客户端会认为物品仍在冷却而拦住使用，表现为"右键点了没反应"。
     * 算法与服务端 {@link CooldownHandler} 完全一致，保证两端步调相同、不会错位。
     */
    private static void accelerateCooldowns(LocalPlayer player) {
        player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
            float reduction = stats.getStatValue(StatType.fromId("cooldown_reduction"));
            if (reduction > CooldownHandler.MAX_REDUCTION) reduction = CooldownHandler.MAX_REDUCTION;
            // 与服务端共用同一套逻辑（含满额时的清除路径），避免两边算法漂移
            CooldownHandler.apply(player, stats, reduction);
        });
    }

    // ========== 按键处理 ==========

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 打开属性面板
        while (ClientSetup.OPEN_STATS_KEY.consumeClick()) {
            mc.setScreen(new StatsScreen());
        }

        // 快速加点 — 直接打开属性面板（与 P 键相同效果）
        while (ClientSetup.QUICK_ADD_KEY.consumeClick()) {
            mc.setScreen(new StatsScreen());
        }

        // 开关 HUD 显示（不在 GUI 界面中触发，避免与 StatsScreen 的 Ctrl+H 冲突）
        while (ClientSetup.HUD_TOGGLE_KEY.consumeClick()) {
            if (mc.screen != null) break;
            ClientSettings.hudVisible = !ClientSettings.hudVisible;
            ClientSettings.save();
            if (mc.player != null) {
                String msg = ClientSettings.hudVisible
                        ? Component.translatable("message.infinitestats.hud_on").getString()
                        : Component.translatable("message.infinitestats.hud_off").getString();
                mc.player.displayClientMessage(Component.literal(msg), true);
            }
        }

        // 打开物品编辑器
        while (ClientSetup.OPEN_ITEM_EDITOR_KEY.consumeClick()) {
            if (mc.screen != null) break;
            mc.setScreen(new ItemEditorScreen());
        }

        // 打开 EMC 转化桌
        while (ClientSetup.OPEN_EMC_KEY.consumeClick()) {
            if (mc.screen != null) break;
            NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.EmcOpenPacket());
        }

        // 打开成就管理器
        while (ClientSetup.OPEN_ACHIEVEMENTS_KEY.consumeClick()) {
            if (mc.screen != null) break;
            mc.setScreen(new AchievementManagerScreen());
        }

        // 打开传送点面板
        while (ClientSetup.OPEN_WAYPOINT_KEY.consumeClick()) {
            if (mc.screen != null) break;
            mc.setScreen(new WaypointScreen());
        }
    }

    // ========== 客户端强制维持夜视（彻底绕过任何模组拦截） ==========

    /**
     * 在客户端本地（渲染前最后一刻）把夜视效果直接写回本地玩家效果表。
     * 直接操作 activeEffects 地图，绕过：
     *   1) 其他模组在 MobEffectEvent.Applicable 中对夜视的 DENY 拦截；
     *   2) 其他模组在各自 tick 中直接 removeEffect(NIGHT_VISION) 导致服务端最终无夜视、
     *      客户端又收到 remove 包而丢失的问题。
     * 由于渲染读取的是本地 activeEffects，服务端来回的 add/remove 包不再影响最终画面。
     * 同时清除本地的 DARKNESS（黑暗）效果，避免它对夜视视觉的压制，使夜视真正"强力"。
     * toggle 关闭时仅移除我们写入的无限夜视，避免误删其他模组的有限夜视。
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null) return;

        accelerateCooldowns(player);

        player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
            var map = player.getActiveEffectsMap();
            if (stats.isToggleActive("night_vision")) {
                map.put(MobEffects.NIGHT_VISION, new MobEffectInstance(
                        MobEffects.NIGHT_VISION,
                        MobEffectInstance.INFINITE_DURATION, 0, false, false, true));
                // 移除黑暗效果，防止其压制夜视视觉
                map.remove(MobEffects.DARKNESS);
            } else {
                // 关闭时只移除我们写入的无限夜视，避免误删其他模组的有限夜视
                MobEffectInstance cur = player.getEffect(MobEffects.NIGHT_VISION);
                if (cur != null && cur.getDuration() == MobEffectInstance.INFINITE_DURATION) {
                    map.remove(MobEffects.NIGHT_VISION);
                }
            }

            // 自动跨越：客户端本地同步 maxUpStep
            syncStepHeight(player, stats);
        });
    }

    // ========== 自动跨越高度（客户端同步） ==========

    /**
     * auto_step / step_height 的客户端同步。
     *
     * 台阶高度由客户端本地碰撞（Entity#collide 读取 maxUpStep）参与移动预判，
     * 只在服务端设置会导致客户端走不上台阶、被服务端拉回。这里按与服务端
     * MobilityHandler#updateStepHeight 完全一致的公式在本地同步。
     */
    private static void syncStepHeight(LocalPlayer player, PlayerStats stats) {
        float targetStep = 0.6f;
        if (stats.isToggleActive("auto_step")) targetStep = 1.0f;
        float stepBonus = stats.getStatValue(StatType.fromId("step_height"));
        if (stepBonus > 0) targetStep *= (1.0f + stepBonus);

        if (Math.abs(player.maxUpStep() - targetStep) > 0.01f) {
            player.setMaxUpStep(targetStep);
        }
    }

    /**
     * 注册 HUD 覆盖层（在 MOD 事件总线上）
     */
    @Mod.EventBusSubscriber(modid = InfiniteStats.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            // 游戏启动时加载保存的 HUD 位置
            ClientSettings.load();

            // 注册 EMC 转化桌屏幕（客户端侧 MenuType → Screen 映射）
            event.enqueueWork(() -> {
                MenuScreens.register(ModMenuTypes.EMC_MENU.get(), EmcScreen::new);
                MenuScreens.register(ModMenuTypes.PORTABLE_FURNACE_MENU.get(), PortableFurnaceScreen::new);
                MenuScreens.register(ModMenuTypes.FURNACE_FUEL_BUFFER_MENU.get(), FurnaceFuelBufferScreen::new);
                MenuScreens.register(ModMenuTypes.FURNACE_PRODUCT_BUFFER_MENU.get(), FurnaceProductBufferScreen::new);
                MenuScreens.register(ModMenuTypes.FURNACE_ORE_PRIORITY_MENU.get(), FurnaceOrePriorityScreen::new);
                MenuScreens.register(ModMenuTypes.PORTABLE_CRAFTING_MENU.get(), PortableCraftingScreen::new);
            });
        }

        @SubscribeEvent
        public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("infinitestats_hud",
                    (gui, graphics, partialTick, screenWidth, screenHeight) ->
                            StatsHudOverlay.render(graphics, partialTick));
        }
    }
}
