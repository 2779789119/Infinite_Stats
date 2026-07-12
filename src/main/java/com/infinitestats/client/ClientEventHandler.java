package com.infinitestats.client;

import com.infinitestats.InfiniteStats;
import com.infinitestats.stats.PlayerStatsProvider;
import com.infinitestats.stats.StatType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.TridentItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
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
        }

        @SubscribeEvent
        public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("infinitestats_hud",
                    (gui, graphics, partialTick, screenWidth, screenHeight) ->
                            StatsHudOverlay.render(graphics, partialTick));
        }
    }
}
