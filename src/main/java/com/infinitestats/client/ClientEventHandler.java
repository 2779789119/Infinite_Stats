package com.infinitestats.client;

import com.infinitestats.InfiniteStats;
import com.infinitestats.Config;
import com.infinitestats.emc.EmcMenu;
import com.infinitestats.emc.ModMenuTypes;
import com.infinitestats.crafting.PortableCraftingMenu;
import com.infinitestats.event.StatEventHandler;
import com.infinitestats.furnace.PortableFurnaceMenu;
import com.infinitestats.furnace.FurnaceFuelBufferMenu;
import com.infinitestats.furnace.FurnaceOrePriorityMenu;
import com.infinitestats.client.PortableFurnaceScreen;
import com.infinitestats.client.FurnaceOrePriorityScreen;
import com.infinitestats.handler.CooldownHandler;
import com.infinitestats.handler.MobilityHandler;
import com.infinitestats.handler.UtilityHandler;
import com.infinitestats.network.NetworkHandler;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import com.infinitestats.stats.StatType;
import com.infinitestats.util.EntityPanelWand;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.BrushItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 客户端事件处理器 — 处理按键输入和 GUI 覆盖层注册
 */
@Mod.EventBusSubscriber(modid = InfiniteStats.MODID, value = Dist.CLIENT)
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
    public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || event.getEntity() != player) return;
        if (mc.screen != null) return;
        if (!EntityPanelWand.isWand(event.getItemStack())) return;

        boolean active = player.getCapability(PlayerStatsProvider.PLAYER_STATS)
                .map(stats -> stats.isToggleActive(EntityPanelWand.STAT_ID))
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

        player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
            boolean isBow = event.getItem().getItem() instanceof BowItem
                    || event.getItem().getItem() instanceof CrossbowItem
                    || event.getItem().getItem() instanceof TridentItem;

            // 刷子不参与加速（理由见服务端 StatEventHandler#onItemUseTick）：它的「刷」只发生在
            // onUseTick 的特定节拍上，跳步会把这些节拍整段跳过，表现为右键刷不动。
            if (event.getItem().getItem() instanceof BrushItem) return;

            float totalSpeed = 0;
            if (!isBow) {
                totalSpeed = stats.getStatValue(StatType.fromId("use_speed"));
            } else {
                totalSpeed = stats.getStatValue(StatType.fromId("bow_draw_speed"));
            }

            if (totalSpeed > 0) {
                int extraReduction = Math.max(1, (int) (totalSpeed * 100));
                // 与服务端保持一致：至少留 1 tick，别把这次使用压到 0
                event.setDuration(Math.max(1, event.getDuration() - extraReduction));
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
            float cap = CooldownHandler.maxReduction();
            float reduction = stats.getStatValue(StatType.fromId("cooldown_reduction"));
            if (reduction > cap) reduction = cap;
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
            if (!panelAvailable(mc, "item_editor")) break;
            mc.setScreen(new ItemEditorScreen());
        }

        // 打开 EMC 转化桌
        while (ClientSetup.OPEN_EMC_KEY.consumeClick()) {
            if (mc.screen != null) break;
            if (!panelAvailable(mc, "emc")) break;
            NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.EmcOpenPacket());
        }

        // 打开成就管理器
        while (ClientSetup.OPEN_ACHIEVEMENTS_KEY.consumeClick()) {
            if (mc.screen != null) break;
            if (!panelAvailable(mc, "achievements")) break;
            mc.setScreen(new AchievementManagerScreen());
        }

        // 打开传送点面板
        while (ClientSetup.OPEN_WAYPOINT_KEY.consumeClick()) {
            if (mc.screen != null) break;
            if (!panelAvailable(mc, "waypoint")) break;
            mc.setScreen(new WaypointScreen());
        }

        // 打开随身工具面板
        while (ClientSetup.OPEN_TOOLS_KEY.consumeClick()) {
            if (mc.screen != null) break;
            if (!panelAvailable(mc, "tools")) break;
            mc.setScreen(new PortableToolsScreen());
        }
    }

    /**
     * 面板被整合包作者在配置里隐藏时提示一句并返回 false。
     * <p>
     * 快捷键按了完全没反应最容易让人以为是模组坏了，所以这里留一条动作栏提示。
     */
    static boolean panelAvailable(Minecraft mc, String panelId) {
        if (!Config.isPanelHidden(panelId)) return true;
        if (mc.player != null) {
            mc.player.displayClientMessage(
                    Component.translatable("message.infinitestats.panel_hidden"), true);
        }
        return false;
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

        // 死亡界面兜底：服务端已经把人救回来了（客户端血量已恢复），但死亡包先一步到、
        // DeathScreen 已经挂在屏幕上 —— 不收掉就是「角色活着、界面卡死、怪物继续围殴」。
        // 真死时客户端血量恒为 0，这里不会误关；正常重生由原版 Respawn 包收尾。
        if (mc.screen instanceof DeathScreen && !player.isDeadOrDying()) {
            mc.setScreen(null);
            // 顺带把死亡动画的计时清掉，否则尸体会在原地保持倒地姿势直到 20 tick
            player.deathTime = 0;
        }

        accelerateCooldowns(player);

        // 清掉客户端的「到期尸体」。
        // Forge 1.20.1 的 LivingEntity#tickEffects 只在 !isClientSide 分支里真正移除到期效果
        // （原版是无条件 iterator.remove），所以本地 duration 归零后客户端自己永远不会删，
        // 只能等服务端发 ClientboundRemoveMobEffectPacket。任何「不发包就删效果」的路径
        // （别的模组直接改效果表、服务端在别处强删）都会在客户端留下一个永远停在 0:00、
        // 永远显示「生效中」的死实例 —— 此时服务端早已没有它，/effect clear 也无从下手。
        // duration 恰好为 0 即已到期；无限时长是 -1，不会被误删。服务端若其实还有，
        // 后续 update / add 包会自动把它补回来。
        player.getActiveEffectsMap().values()
                .removeIf(inst -> inst != null && inst.getDuration() == 0);

        player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
            var map = player.getActiveEffectsMap();

            // 客户端本地清扫「效果过滤」黑名单（与服务端清扫互补，见 sweepBlockedEffects 注释）
            sweepBlockedEffects(player, stats);

            // 白名单（保护）模式的客户端一半：受保护效果被本地删掉时按快照写回
            applyClientProtectedEffects(player, stats);

            if (stats.isToggleActive("night_vision")) {
                // 只有「当前那条不是我们放的无限夜视」时才写：稳态下每 tick 不再新建实例
                if (!UtilityHandler.isOurInfiniteEffect(map.get(MobEffects.NIGHT_VISION))) {
                    map.put(MobEffects.NIGHT_VISION, new MobEffectInstance(
                            MobEffects.NIGHT_VISION,
                            MobEffectInstance.INFINITE_DURATION, 0, false, false, true));
                }
                // 移除黑暗效果，防止其压制夜视视觉。
                // 例外：玩家在「效果过滤」里把黑暗列为保护时让位 —— 保护的意义就是不被外力清除，
                // 否则服务端每 tick 补回、这里每 tick 删掉，界面显示「保护中」却毫无效果。
                if (!stats.isProtectedEffect("minecraft:darkness")) {
                    map.remove(MobEffects.DARKNESS);
                }
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

    // ========== 效果过滤：客户端本地清扫 ==========
    /**
     * 在客户端本地清扫「效果过滤」黑名单效果。
     * <p>
     * 服务端的清扫（{@code DefenseHandler#applyDebuffImmunity}）只能动服务端那张效果表。只要效果
     * 不是由服务端加进来的、或者服务端删掉之后又被补回客户端本地表（别的模组在客户端写效果、
     * 客户端侧的额外施加、丢掉 remove 包留下的鬼影），服务端清多少次客户端都还挂着 ——
     * 界面上「当前生效中：∞」读的正是本地这张表，于是表现为「名单里明明勾了，就是清不掉」。
     * <p>
     * 把清除写在 {@code MobEffect#applyEffectTick} 里的写法（双端执行）之所以看起来更灵，就是因为
     * 客户端也有一份清扫。这里补上这一半：<b>只删本地效果表，不发包、不动服务端数据</b>，
     * 服务端仍由自己的清扫与 {@code hardRemoveEffect} 兜底，两端各管各的，不会互相打架。
     * <p>
     * 前置条件与服务端完全一致（开关 + 黑名单非空 + 条目命中），因此白名单（保护）模式下
     * {@code shouldBlockEffect} 恒为 false，不会误删受保护的效果。
     */
    private static void sweepBlockedEffects(LocalPlayer player, PlayerStats stats) {
        if (!stats.isToggleActive("debuff_immunity") || !stats.hasBlockingEffects()) return;

        var map = player.getActiveEffectsMap();
        if (map.isEmpty()) return;

        // 遍历副本再删：直接边遍历边改会踩 ConcurrentModificationException
        for (MobEffect effect : new java.util.ArrayList<>(map.keySet())) {
            if (effect == null) continue;
            ResourceLocation key = ForgeRegistries.MOB_EFFECTS.getKey(effect);
            if (key == null) continue;
            String id = key.toString();

            // 本模组自己维持的 Utility 效果（夜视 / 隐身 / 幸运）不参与过滤：
            // 名单里写 `*` 时也不该把上面刚写进本地表的无限夜视顺手删掉
            if (StatEventHandler.isOwnUtilityEffect(stats, id)) continue;
            if (!stats.shouldBlockEffect(id)) continue;

            if (player.removeEffect(effect)) continue;
            // 被 MobEffectEvent.Remove 驳回（与服务端 hardRemoveEffect 同理）：直接删本地表。
            // 客户端不涉及发包，但属性修饰符与两个显示标志要手工补回。
            MobEffectInstance removed = player.removeEffectNoUpdate(effect);
            if (removed == null) continue;
            effect.removeAttributeModifiers(player, player.getAttributes(), removed.getAmplifier());
            player.setInvisible(player.hasEffect(MobEffects.INVISIBILITY));
            player.setGlowingTag(player.hasEffect(MobEffects.GLOWING));
        }
    }

    // ========== 效果过滤：白名单（保护）的客户端一半 ==========

    /**
     * 客户端侧的保护快照：效果 ID → 最后一次观察到的实例状态与剩余时长。
     * <p>
     * 与服务端 {@code PlayerStats#protectedSnapshots} 各自独立 —— 服务端那份是权威（时长以服务端实例为准），
     * 这一份只负责「本地被删掉时不让它消失」。两边的剩余时长各自递减，服务端每
     * {@code PROTECTED_RESEND_INTERVAL} tick 会重发一次真实状态把偏差拉回来。
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
     * 这里补上这一半，让「保护」在两端都成立。
     * <p>
     * 安全边界：
     * <ul>
     *   <li>只在白名单模式且名单非空时运行，黑名单模式直接清空快照退出；</li>
     *   <li>客户端实体变了（换维度 / 重生）立即作废快照，避免把旧实体的效果写回新实体；</li>
     *   <li>死亡时不写回；剩余时长递减到 0 视为自然结束，放手不再补；</li>
     *   <li>用 {@code forceAddEffect} 写回，绕过 {@code MobEffectEvent.Applicable}：
     *       保护本来就是「拒绝外力干预」。</li>
     * </ul>
     */
    private static void applyClientProtectedEffects(LocalPlayer player, PlayerStats stats) {
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
            ResourceLocation key = ForgeRegistries.MOB_EFFECTS.getKey(inst.getEffect());
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
            MobEffect effect = effectById(entry.getKey());
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
    private static MobEffect effectById(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? null : ForgeRegistries.MOB_EFFECTS.getValue(rl);
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
                // 随身进阶高级附魔台：自选附魔 / 修复 / 回收的自建界面（不再复用原版附魔界面）
                MenuScreens.register(ModMenuTypes.PORTABLE_ENCHANTING_MENU.get(), PortableInfuserScreen::new);
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
