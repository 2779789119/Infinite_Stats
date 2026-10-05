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
import com.infinitestats.handler.MobilityHandler;
import com.infinitestats.network.NetworkHandler;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import com.infinitestats.stats.StatType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
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
            // 飞行速度：飞行时读的是本地 abilities.flyingSpeed，服务端能力包不可靠
            syncFlySpeed(player, stats);
            // 跳跃高度：起跳速度必须本地放大，服务端改的会被客户端运动包覆盖
            applyJumpBoost(player, stats);
            // N 段跳 / 爬梯加速：移动是客户端权威的，必须本地改速度
            applyMovementExtras(player, stats);
        });
    }

    // ========== N 段跳 / 爬梯加速 ==========

    /** 上一次客户端 tick 跳跃键是否按下（用于取"刚按下"的边沿）。 */
    private static boolean jumpKeyWasDown;
    /** 本次离地后已经用掉的空中跳跃次数。 */
    private static int airJumpsUsed;
    /** 最近一次站在地面上的 tick，用于排除"刚起跳那一下"。 */
    private static int lastGroundTick = -1000;

    /**
     * N 段跳与爬梯加速。
     * <p>
     * 必须放在客户端：玩家的移动是客户端权威的（服务端只做校验），
     * 所以"梯子上爬得更快""在空中再跳一次"这类改变速度的效果只有本地改才真的动得了人 ——
     * 与服务端属性类加成（会被同步到两端）不同，这里没有可用的属性通道。
     * <p>
     * 计数口径：站地 / 水中 / 梯子上时清零；离地不足 6 tick 的"起跳那一下"不消耗次数，
     * 因此"从地面起跳 + 空中再跳 N 次"正好等于面板上加出来的 N 段。
     */
    private static void applyMovementExtras(LocalPlayer player, PlayerStats stats) {
        var mc = Minecraft.getInstance();
        boolean onGround = player.onGround();
        boolean climbable = player.onClimbable();
        boolean inFluid = player.isInWater() || player.isInLava();

        // ---- 爬梯加速：原版爬梯的竖直速度写死为 0.2，这里按比例放大 ----
        float climbBonus = stats.getStatValue("climb_speed");
        if (climbable && climbBonus > 0) {
            Vec3 motion = player.getDeltaMovement();
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
        Vec3 motion = player.getDeltaMovement();
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

    // ========== 飞行速度加成（客户端） ==========

    /** 原版飞行速度基准（{@code Abilities#flyingSpeed} 默认 0.05）。 */
    private static final float VANILLA_FLY_SPEED = 0.05f;
    /** 上一次由本模组写入的目标值，小于 0 表示当前没有接管。 */
    private static float lastAppliedFlySpeed = -1f;

    /**
     * 飞行速度加成（客户端本地）。
     * <p>
     * 飞行时移动速度取的是<b>本地</b> {@code abilities.flyingSpeed}
     * （{@code Player#getFlyingSpeed()} → {@code LivingEntity#getFrictionInfluencedSpeed()}），
     * 服务端发送的能力包在整合包环境下可能被其它模组覆盖或丢失，导致加点后完全看不到效果。
     * 与 N 段跳 / 爬梯加速 / 自动跨越同理，这里每 tick 本地校正一次，保证一定生效。
     * <p>
     * 退款（bonus ≤ 0）时只回收「本模组自己写进去的那个值」，不碰其它模组设置的速度。
     */
    private static void syncFlySpeed(LocalPlayer player, PlayerStats stats) {
        var abilities = player.getAbilities();
        float bonus = stats.getStatValue(StatType.fromId("fly_speed"));

        if (bonus <= 0) {
            if (lastAppliedFlySpeed > 0
                    && Math.abs(abilities.getFlyingSpeed() - lastAppliedFlySpeed) < 1e-4f) {
                abilities.setFlyingSpeed(VANILLA_FLY_SPEED);
            }
            lastAppliedFlySpeed = -1f;
            return;
        }

        float target = VANILLA_FLY_SPEED * (1.0f + bonus);
        if (Math.abs(abilities.getFlyingSpeed() - target) > 1e-4f) {
            abilities.setFlyingSpeed(target);
        }
        lastAppliedFlySpeed = target;
    }

    // ========== 跳跃高度加成（客户端） ==========

    /** 上一客户端 tick 是否在地面（用于客户端起跳检测）。 */
    private static boolean clientWasOnGround;

    /**
     * 跳跃高度加成（客户端本地）。
     * <p>
     * 玩家移动是客户端权威的：服务端 {@code StatEventHandler#handleJumpBoost} 改的
     * deltaMovement 会被客户端发来的运动包覆盖，面板上加的点完全看不到效果。
     * 与 N 段跳 / 爬梯加速同理，必须在 LocalPlayer 上本地放大起跳速度。
     * <p>
     * 检测口径与服务端一致：上一tick在地面、本tick离地且 Y 速度为正 → 起跳瞬间；
     * 并按服务端同样的方式用 setPos 补偿本 tick 已按原始速度跑完的位移。
     */
    private static void applyJumpBoost(LocalPlayer player, PlayerStats stats) {
        float multiplier = 1.0f + stats.getStatValue(StatType.fromId("jump_height"));
        if (multiplier <= 1.0f) return;

        boolean onGround = player.onGround();
        boolean was = clientWasOnGround;
        clientWasOnGround = onGround;

        if (was && !onGround && player.getDeltaMovement().y > 0) {
            Vec3 motion = player.getDeltaMovement();
            double boostedY = motion.y * multiplier;
            double extraY = boostedY - motion.y;

            // 补偿本tick已用原始速度跑完的运动（travel() 在 aiStep 中已经执行）
            if (extraY > 0.001) {
                player.setPos(player.getX(), player.getY() + extraY, player.getZ());
            }

            player.setDeltaMovement(motion.x, boostedY, motion.z);
            player.hasImpulse = true;
        }
    }

    // ========== 自动跨越高度（客户端同步） ==========

    /**
     * auto_step / step_height 的客户端同步。
     * <p>
     * 跨越高度参与客户端本地的移动预判（{@code Entity#collide} 里的 {@code getStepHeight()}：
     * 原版字段 + {@code forge:step_height_addition} 属性），只在服务端设置会导致
     * 客户端走不上台阶、被服务端拉回。这里直接复用服务端同一套实现
     * {@link MobilityHandler#applyStepHeight}，两端公式与写入通道完全一致。
     */
    private static void syncStepHeight(LocalPlayer player, PlayerStats stats) {
        MobilityHandler.applyStepHeight(player, stats);
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
                // 随身铁砧沿用原版铁砧界面
                MenuScreens.register(ModMenuTypes.PORTABLE_ANVIL_MENU.get(), AnvilScreen::new);
                // 随身附魔台：原版附魔界面 + 右侧「附魔强度」加点
                MenuScreens.register(ModMenuTypes.PORTABLE_ENCHANTING_MENU.get(), PortableEnchantingScreen::new);
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
