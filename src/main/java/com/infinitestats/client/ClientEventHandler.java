package com.infinitestats.client;

import com.infinitestats.InfiniteStats;
import com.infinitestats.menu.ModMenuTypes;
import com.infinitestats.crafting.PortableCraftingMenu;
import com.infinitestats.furnace.PortableFurnaceMenu;
import com.infinitestats.furnace.FurnaceFuelBufferMenu;
import com.infinitestats.furnace.FurnaceOrePriorityMenu;
import com.infinitestats.client.PortableFurnaceScreen;
import com.infinitestats.client.FurnaceOrePriorityScreen;
import com.infinitestats.handler.CooldownHandler;
import com.infinitestats.handler.UtilityHandler;
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
import net.minecraft.world.entity.player.Player;
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

    // ========== 生物观察杖：右键空气打开「附近生物列表」 ==========

    /**
     * 手持生物观察杖右键空气 → 打开附近生物列表。
     * <p>
     * 右键空气只有客户端能感知（原版不发服务端包），所以界面上直接本地开；
     * 列表里的实体也是客户端已有的追踪数据。点选之后才由
     * {@code EntityPanelRequestPacket} 走服务端校验并下发权威的开关状态。
     */
    @SubscribeEvent
    public static void onRightClickEmpty(
            net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickEmpty event) {
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || event.getEntity() != player) return;
        if (mc.screen != null) return;
        if (!com.infinitestats.util.EntityPanelWand.isWand(event.getItemStack())) return;

        boolean active = PlayerStatsProvider.of(player)
                .map(stats -> stats.isToggleActive(com.infinitestats.util.EntityPanelWand.STAT_ID))
                .orElse(false);
        if (!active) return;
        mc.setScreen(new EntityPickerScreen());
    }

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

    /**
     * 客户端效果表兜底：把「时长已经归零、却还挂在身上」的本地实例删掉。
     * <p>
     * 1.20.1 起（含 1.21.1）原版把 {@code LivingEntity#tickEffects} 的移除分支改成了
     * 只在服务端执行 —— 客户端本地 duration 归零后<b>自己永远不会删</b>，只认服务端发来的
     * {@code ClientboundRemoveMobEffectPacket}。只要有一条移除路径没发包（别的模组直接改效果表、
     * 服务端在别处强删），客户端就会留下一个永远停在 0:00、永远显示「生效中」的死实例：
     * 服务端其实早就没有它了，所以 {@code /effect clear}、牛奶桶、清空黑名单全都无从下手。
     * <p>
     * 无限时长是 {@code -1}，不会被这里误删；服务端若其实还在，后续 update / add 包会自动补回来。
     */
    @SubscribeEvent
    public static void onClientPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (!event.getEntity().level().isClientSide) return;
        var player = event.getEntity();

        // 死亡界面兜底：服务端已经把人救回来了（客户端血量已恢复），但死亡包先一步到、
        // DeathScreen 已经挂在屏幕上 —— 不收掉就是「角色活着、界面卡死、怪物继续围殴」。
        // 真死时客户端血量恒为 0，这里不会误关；正常重生由原版 Respawn 包收尾。
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof net.minecraft.client.gui.screens.DeathScreen && !player.isDeadOrDying()) {
            mc.setScreen(null);
            // 顺带把死亡动画的计时清掉，否则尸体会在原地保持倒地姿势直到 20 tick
            player.deathTime = 0;
        }

        // 效果过滤的双端清扫：服务端那份（DefenseHandler）只能动服务端的效果表，
        // 客户端这张表被别的模组写过 / 丢过 remove 包时，只有本地也扫一遍才清得掉。
        PlayerStatsProvider.of(player).ifPresent(stats -> {
            sweepBlockedEffects(player, stats);
            // 白名单（保护）模式的客户端一半：被本地删掉时按快照写回
            applyClientProtectedEffects(player, stats);
        });

        var effects = player.getActiveEffects();
        if (effects.isEmpty()) return;
        for (MobEffectInstance inst : new java.util.ArrayList<>(effects)) {
            if (inst.getEffect() == null) continue;
            if (inst.getDuration() == 0) {
                player.removeEffect(inst.getEffect());
            }
        }
    }

    // ========== 效果过滤：客户端本地清扫 ==========

    /**
     * 在客户端本地清扫「效果过滤」黑名单效果。
     * <p>
     * 服务端的清扫（{@code DefenseHandler#applyDebuffImmunity}）只能动服务端那张效果表。只要效果
     * 不是由服务端加进来的、或者服务端删掉之后又被补回客户端本地表（别的模组在客户端写效果、
     * 客户端侧的额外施加、丢掉 remove 包留下的鬼影），服务端清多少次客户端都还挂着 ——
     * 界面上「当前生效中：∞」读的正是本地这张表，于是表现为「名单里明明勾了，就是清不掉」。
     * <p>
     * 这里补上另一半：<b>只删本地效果表，不发包、不动服务端数据</b>，服务端仍由自己的清扫与
     * {@code hardRemoveEffect} 兜底，两端各管各的，不会互相打架。
     * <p>
     * 前置条件与服务端完全一致（开关 + 黑名单非空 + 条目命中），因此白名单（保护）模式下
     * {@code shouldBlockEffect} 恒为 false，不会误删受保护的效果。
     */
    private static void sweepBlockedEffects(Player player, PlayerStats stats) {
        if (!stats.isToggleActive("debuff_immunity") || !stats.hasBlockingEffects()) return;

        var map = player.getActiveEffectsMap();
        if (map.isEmpty()) return;

        // 遍历副本再删：直接边遍历边改会踩 ConcurrentModificationException
        for (var effect : new java.util.ArrayList<>(map.keySet())) {
            if (effect == null) continue;
            ResourceLocation key = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT
                    .getKey(effect.value());
            if (key == null) continue;
            String id = key.toString();

            // 本模组自己维持的 Utility 效果（夜视 / 隐身 / 幸运）不参与过滤
            if (com.infinitestats.event.StatEventHandler.isOwnUtilityEffect(stats, id)) continue;
            if (!stats.shouldBlockEffect(id)) continue;

            if (player.removeEffect(effect)) continue;
            // 被 MobEffectEvent.Remove 驳回（与服务端 hardRemoveEffect 同理）：直接删本地表。
            // 客户端不涉及发包，但属性修饰符与两个显示标志要手工补回。
            MobEffectInstance removed = player.removeEffectNoUpdate(effect);
            if (removed == null) continue;
            effect.value().removeAttributeModifiers(player.getAttributes());
            player.setInvisible(player.hasEffect(MobEffects.INVISIBILITY));
            player.setGlowingTag(player.hasEffect(MobEffects.GLOWING));
        }
    }

    // ========== 效果过滤：白名单（保护）的客户端一半 ==========

    /**
     * 客户端侧的保护快照：效果 ID → 最后一次观察到的实例状态与剩余时长。
     * <p>
     * 与服务端 {@code PlayerStats#protectedSnapshots} 各自独立 —— 服务端那份是权威（时长以服务端实例为准），
     * 这一份只负责「本地被删掉时不让它消失」。
     */
    private static final java.util.Map<String, PlayerStats.ProtectedEffect> CLIENT_PROTECTED =
            new java.util.HashMap<>();

    /** 上面这份快照属于哪个客户端实体；换维度 / 重生会换实体，此时必须整体作废。 */
    private static int protectedSnapshotOwner = -1;

    /**
     * 白名单（保护）模式的客户端清扫：受保护效果在本地被删掉时，按快照写回本地效果表。
     * <p>
     * 服务端 {@code DefenseHandler#applyProtectedEffects} 的补回只看服务端那张表：
     * 服务端还在、客户端没了（别的模组在客户端本地删表、remove / update 包丢失）时
     * {@code player.hasEffect(...)} 仍为 true，服务端不会补也不会发包，客户端会一直缺着。
     * <p>
     * 安全边界：只在白名单模式且名单非空时运行；换实体立即作废快照；死亡时不写回；
     * 剩余时长递减到 0 视为自然结束；写回用 {@code forceAddEffect} 绕过事件拦截。
     */
    private static void applyClientProtectedEffects(Player player, PlayerStats stats) {
        if (!stats.hasProtectedEffects()) {
            CLIENT_PROTECTED.clear();
            protectedSnapshotOwner = -1;
            return;
        }

        // 换维度 / 重生 → 本地实体换了，旧快照不能搬过去（服务端会重新下发这些效果）
        if (player.getId() != protectedSnapshotOwner) {
            CLIENT_PROTECTED.clear();
            protectedSnapshotOwner = player.getId();
        }
        if (player.isDeadOrDying()) return;

        // 1) 采样：还在身上的刷新快照（等级 / 粒子 / 剩余时长）
        for (MobEffectInstance inst : player.getActiveEffects()) {
            if (inst.getEffect() == null) continue;
            ResourceLocation key = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT
                    .getKey(inst.getEffect().value());
            if (key == null) continue;
            String id = key.toString();
            if (!stats.isProtectedEffect(id)) continue;
            CLIENT_PROTECTED.computeIfAbsent(id, k -> new PlayerStats.ProtectedEffect()).capture(inst);
        }

        // 2) 补回：快照里有、本地却没了 = 被外力从客户端表里删掉，按剩余时长写回
        java.util.Iterator<java.util.Map.Entry<String, PlayerStats.ProtectedEffect>> it =
                CLIENT_PROTECTED.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<String, PlayerStats.ProtectedEffect> entry = it.next();
            if (!stats.isProtectedEffect(entry.getKey())) {
                // 玩家已取消保护
                it.remove();
                continue;
            }
            net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect =
                    effectById(entry.getKey());
            if (effect == null) {
                // 效果随模组卸载消失，补不回来
                it.remove();
                continue;
            }
            if (player.hasEffect(effect)) continue;
            if (!entry.getValue().tickDown()) {
                // 剩余时长耗尽 = 自然结束，放手
                it.remove();
                continue;
            }
            player.forceAddEffect(entry.getValue().toInstance(effect), player);
        }
    }

    /** 由效果 ID 反查效果；ID 非法或未注册时返回 {@code null}。 */
    private static net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effectById(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return null;
        var effect = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.get(rl);
        return effect == null ? null
                : net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect);
    }

    // ========== 按键处理 ==========

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 打开属性面板
        while (ClientSetup.OPEN_STATS_KEY.consumeClick()) {
            if (com.infinitestats.Config.isPanelHidden("stats")) break;
            mc.setScreen(new StatsScreen());
        }

        // 快速加点 — 直接打开属性面板（与 P 键相同效果）
        while (ClientSetup.QUICK_ADD_KEY.consumeClick()) {
            if (com.infinitestats.Config.isPanelHidden("stats")) break;
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
            if (com.infinitestats.Config.isPanelHidden("item_editor")) break;
            mc.setScreen(new ItemEditorScreen());
        }


        // 打开成就管理器
        while (ClientSetup.OPEN_ACHIEVEMENTS_KEY.consumeClick()) {
            if (mc.screen != null) break;
            if (com.infinitestats.Config.isPanelHidden("achievements")) break;
            mc.setScreen(new AchievementManagerScreen());
        }

        // 打开传送点面板
        while (ClientSetup.OPEN_WAYPOINT_KEY.consumeClick()) {
            if (mc.screen != null) break;
            if (com.infinitestats.Config.isPanelHidden("waypoint")) break;
            mc.setScreen(new WaypointScreen());
        }

        // 打开随身工作台：已解锁直接开面板，未解锁时打开「随身工具」页去解锁
        while (ClientSetup.OPEN_PORTABLE_CRAFTING_KEY.consumeClick()) {
            if (mc.screen != null) break;
            if (com.infinitestats.Config.isPanelHidden("portable_tools")) break;
            boolean unlocked = PlayerStatsProvider.of(mc.player)
                    .map(stats -> stats.isToggleActive("portable_crafting"))
                    .orElse(false);
            if (unlocked) {
                NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.CraftingOpenPacket());
            } else {
                mc.setScreen(new PortableToolsScreen());
            }
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
                // 只有「当前那条不是我们放的无限夜视」时才写：稳态下每 tick 不再新建实例
                if (!UtilityHandler.isOurInfiniteEffect(map.get(MobEffects.NIGHT_VISION))) {
                    map.put(MobEffects.NIGHT_VISION, new MobEffectInstance(
                            MobEffects.NIGHT_VISION,
                            MobEffectInstance.INFINITE_DURATION, 0, false, false, true));
                }
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
            // 注册自建菜单的屏幕（客户端侧 MenuType → Screen 映射）
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
