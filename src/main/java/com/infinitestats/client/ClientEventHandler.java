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
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * 客户端事件处理器 — 处理按键输入和 GUI 覆盖层注册
 */
@EventBusSubscriber(modid = InfiniteStats.MODID, value = Dist.CLIENT)
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

        PlayerStatsProvider.of(player).ifPresent(stats -> {
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
        PlayerStatsProvider.of(player).ifPresent(stats -> {
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
    public static void onClientTick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null) return;

        accelerateCooldowns(player);

        PlayerStatsProvider.of(player).ifPresent(stats -> {
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

            // 爬梯加速 + N 段跳（客户端本地）
            applyMovementExtras(player, stats);
        });
    }

    // ========== 爬梯加速 / N 段跳（客户端） ==========

    /** 当前已用掉的空中跳跃次数。 */
    private static int airJumpsUsed = 0;
    /** 最近一次站在地面上的 tick。 */
    private static int lastGroundTick = 0;
    /** 上一 tick 跳跃键是否按下（用于边沿检测）。 */
    private static boolean jumpKeyWasDown = false;

    /**
     * 爬梯加速（{@code climb_speed}）与 N 段跳（{@code multi_jump}）。
     * <p>
     * 这两条都与「速度」有关，而玩家的移动是<b>客户端权威</b>的：服务端改
     * {@code deltaMovement} 客户端收不到，等于没写，所以只能在本地每 tick 校正。
     * <p>
     * 计数口径：站地 / 水中 / 梯子上时清零；离地不足 6 tick 的「起跳那一下」不消耗次数，
     * 因此「从地面起跳 + 空中再跳 N 次」正好等于面板上加出来的 N 段。
     */
    private static void applyMovementExtras(LocalPlayer player, PlayerStats stats) {
        var mc = Minecraft.getInstance();
        boolean onGround = player.onGround();
        boolean climbable = player.onClimbable();
        boolean inFluid = player.isInWater() || player.isInLava();

        // ---- 爬梯加速：原版爬梯的竖直速度写死为 0.2，这里按比例放大 ----
        float climbBonus = stats.getStatValue("climb_speed");
        if (climbable && climbBonus > 0) {
            var motion = player.getDeltaMovement();
            if (motion.y > 0.02 && motion.y <= 0.21) {
                player.setDeltaMovement(motion.x, motion.y * (1.0 + climbBonus), motion.z);
                player.hasImpulse = true;
            }
        }

        // ---- N 段跳 ----
        int maxAirJumps = (int) Math.round(stats.getStatValue("multi_jump"));
        boolean jumpDown = mc.options.keyJump.isDown() && mc.screen == null;
        boolean pressed = jumpDown && !jumpKeyWasDown;
        jumpKeyWasDown = jumpDown;

        if (onGround || climbable || inFluid) airJumpsUsed = 0;
        if (onGround) lastGroundTick = player.tickCount;

        if (maxAirJumps <= 0 || !pressed) return;
        if (onGround || climbable || inFluid) return;              // 第一段交给原版
        if (player.isPassenger()) return;
        if (player.getAbilities().flying || player.isFallFlying() || player.isSpectator()) return;
        if (player.tickCount - lastGroundTick < 6) return;         // 刚起跳那一下不算
        if (airJumpsUsed >= maxAirJumps) return;

        airJumpsUsed++;
        var motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x, 0.42, motion.z);
        player.hasImpulse = true;
        player.fallDistance = 0;
        player.playSound(SoundEvents.SLIME_JUMP_SMALL, 0.6F, 1.2F);
        for (int i = 0; i < 6; i++) {
            player.level().addParticle(ParticleTypes.CLOUD,
                    player.getX() + (player.getRandom().nextDouble() - 0.5D) * 0.6D,
                    player.getY() + 0.05D,
                    player.getZ() + (player.getRandom().nextDouble() - 0.5D) * 0.6D,
                    0.0D, 0.02D, 0.0D);
        }
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
            var stepAttr = player.getAttribute(Attributes.STEP_HEIGHT);
            if (stepAttr != null) stepAttr.setBaseValue(targetStep);
        }
    }

    /**
     * 注册 HUD 覆盖层（在 MOD 事件总线上）
     */
    @EventBusSubscriber(modid = InfiniteStats.MODID, value = Dist.CLIENT)
    public static final class ModBus {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            // 游戏启动时加载保存的 HUD 位置
            ClientSettings.load();
        }

        @SubscribeEvent
        public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
            // 注册 EMC 转化桌等屏幕（客户端侧 MenuType → Screen 映射）
            event.register(ModMenuTypes.EMC_MENU.get(), EmcScreen::new);
            event.register(ModMenuTypes.PORTABLE_FURNACE_MENU.get(), PortableFurnaceScreen::new);
            event.register(ModMenuTypes.FURNACE_FUEL_BUFFER_MENU.get(), FurnaceFuelBufferScreen::new);
            event.register(ModMenuTypes.FURNACE_PRODUCT_BUFFER_MENU.get(), FurnaceProductBufferScreen::new);
            event.register(ModMenuTypes.FURNACE_ORE_PRIORITY_MENU.get(), FurnaceOrePriorityScreen::new);
            event.register(ModMenuTypes.PORTABLE_CRAFTING_MENU.get(), PortableCraftingScreen::new);
            event.register(ModMenuTypes.PORTABLE_ENCHANTING_MENU.get(), PortableInfuserScreen::new);
        }

        @SubscribeEvent
        public static void onRegisterOverlays(RegisterGuiLayersEvent event) {
            event.registerAboveAll(
                    ResourceLocation.fromNamespaceAndPath("infinitestats", "infinitestats_hud"),
                    (guiGraphics, deltaTracker) -> StatsHudOverlay.render(
                            guiGraphics, deltaTracker.getGameTimeDeltaPartialTick(false)));
        }
    }
}
