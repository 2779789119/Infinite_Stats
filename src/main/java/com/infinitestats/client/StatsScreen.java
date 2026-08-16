package com.infinitestats.client;

import com.infinitestats.Config;
import com.infinitestats.compat.JechCompat;
import com.infinitestats.compat.ProjectEBridge;
import com.infinitestats.network.NetworkHandler;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import net.minecraft.client.gui.components.EditBox;
import com.infinitestats.stats.StatCategory;
import com.infinitestats.stats.StatType;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/**
 * 无限属性系统 GUI — v5.0 全面重构
 * <p>
 * 新增：属性图标系统、拨动开关（Toggle型）、增强提示框、类别点数统计、
 * 点击音效、自动保存设置、法力条优化。
 * 所有 GUI 元素统一跟随缩放，右键拖动，Ctrl+滚轮缩放，中键重置。
 */
public class StatsScreen extends Screen {

    // ======================== 布局常量（面板坐标系，未缩放） ========================

    private static final int GUI_WIDTH = 440;
    private static final int GUI_HEIGHT = 376;
    private static final int HEADER_H = 76;
    private static final int TAB_H = 30;
    private static final int CARD_H = 30;
    private static final int CARD_GAP = 2;
    private static final int MAX_VISIBLE = 6;
    private static final int FOOTER_H = 58;
    private static final int SCROLLBAR_W = 6;
    private static final int BTN_W = 24;
    private static final int BTN_H = 18;
    private static final int ICON_SIZE = 16;
    private static final int TOGGLE_W = 28;
    private static final int TOGGLE_H = 14;

    // 缩放与拖动
    private static final float MIN_SCALE = 0.5f;
    private static final float MAX_SCALE = 3.0f;
    private static final float SCALE_STEP = 0.05f;

    // ======================== 颜色常量 ========================

    private static final int BG_PANEL = 0xE81A1A2E;
    private static final int BG_CARD = 0x50252535;
    private static final int BG_CARD_HOVER = 0x80353550;
    private static final int BG_BTN_PLUS = 0xFF1A5334;
    private static final int BG_BTN_PLUS_HOVER = 0xFF2D8A4E;
    private static final int BG_BTN_MINUS = 0xFF4A1A2E;
    private static final int BG_BTN_MINUS_HOVER = 0xFF8A2D4E;
    private static final int BG_TAB = 0x50252535;
    private static final int BG_TAB_SELECTED = 0x80252540;
    private static final int BG_XP_BAR = 0x50252535;
    private static final int BG_XP_FILL = 0xFF4ADE80;
    private static final int BG_MANA_BAR = 0x50252535;
    private static final int BG_MANA_FILL = 0xFFA78BFA;
    private static final int BG_SCROLLBAR_TRACK = 0x30151520;
    private static final int BG_SCROLLBAR = 0x80555570;
    private static final int BG_SCROLLBAR_HOVER = 0xB08888A0;

    private static final int TEXT_PRIMARY = 0xFFE2E8F0;
    private static final int TEXT_SECONDARY = 0xFF94A3B8;
    private static final int TEXT_GOLD = 0xFFFFD166;
    private static final int TEXT_ACTIVE = 0xFF4ADE80;
    private static final int TEXT_NEGATIVE = 0xFFF87171; // 负属性值红色警示
    private static final int TEXT_LEVEL = 0xFF60A5FA;
    private static final int TEXT_POINTS = 0xFFFFD166;
    private static final int TEXT_POINTS_ZERO = 0xFF64748B;
    private static final int TEXT_POINTS_NEGATIVE = 0xFFF87171; // 可用点数为负也红色
    private static final int TEXT_MANA = 0xFFA78BFA;
    private static final int TEXT_BUTTON = 0xFFFFFFFF;
    private static final int TEXT_HINT = 0xFF64748B;
    private static final int TEXT_TOGGLE_ON = 0xFF4ADE80;
    private static final int TEXT_TOGGLE_OFF = 0xFF64748B;

    // ======================== 状态 ========================

    private int leftPos, topPos;
    private int contentTop, contentHeight;
    private StatCategory selectedCategory = StatCategory.ATTACK;
    private PlayerStats cachedStats;
    private int scrollOffset;
    private int maxScroll;
    private int addAmountIndex; // 0..4 → x1,x10,x100,x1000,x10000
    private boolean maxMode; // true=一次加满
    private boolean scrollbarDragging;
    private int scrollbarDragStartY;
    private int scrollbarDragStartOffset;

    /** 上次播放 click 音效的 tick 数（避免重复） */
    private long lastSoundTick;
    /** 当前鼠标悬停的按钮索引（卡片内），用于 tooltip */
    private int hoveredCardIndex = -1;
    private int hoveredBtnType = 0; // 0=none, 1=minus, 2=plus, 3=toggle

    // 缩放与拖动
    private int dragOffsetX, dragOffsetY;
    private boolean isDragging;
    private int dragStartMouseX, dragStartMouseY;
    private int dragStartPanelX, dragStartPanelY;

    private final List<Button> dynamicButtons = new ArrayList<>();
    private final Map<StatCategory, List<StatType>> categoryStats = new EnumMap<>(StatCategory.class);

    // ═══════ 外部属性折叠分组 ═══════

    /** 显示列表条目 — 可能是分组头或属性卡片 */
    private static class DisplayEntry {
        final boolean isHeader;
        final String namespace;
        final int groupSize;
        final StatType stat;
        private DisplayEntry(boolean isHeader, String namespace, int groupSize, StatType stat) {
            this.isHeader = isHeader;
            this.namespace = namespace;
            this.groupSize = groupSize;
            this.stat = stat;
        }
        static DisplayEntry header(String ns, int size) { return new DisplayEntry(true, ns, size, null); }
        static DisplayEntry statCard(StatType s) { return new DisplayEntry(false, null, 0, s); }
    }

    private final Map<String, List<StatType>> externalGroups = new LinkedHashMap<>();
    private final List<DisplayEntry> externalDisplayList = new ArrayList<>();
    private final Set<String> collapsedExternalGroups = new HashSet<>();

    // ═══════ 搜索与收藏 ═══════
    private EditBox searchBox;
    private Button favoritesBtn;
    private boolean favoritesOnly;

    private static final String[] ADD_LABELS = {"x1", "x10", "x100", "x1000", "x10000"};
    private static final long[] ADD_VALUES = {1, 10, 100, 1000, 10000};

    // ======================== 属性图标映射 ========================

    private static final Map<String, ItemStack> STAT_ICONS = new HashMap<>();

    static {
        // ═══════ 攻击 ═══════
        icon("attack_damage", Items.DIAMOND_SWORD);
        icon("attack_speed", Items.GOLDEN_SWORD);
        icon("crit_chance", Items.IRON_SWORD);
        icon("crit_damage", Items.NETHERITE_SWORD);
        icon("armor_penetration", Items.ARROW);
        icon("knockback_power", Items.PISTON);
        icon("projectile_damage", Items.BOW);
        icon("life_steal", Items.GOLDEN_APPLE);
        icon("life_steal_aoe", Items.ENCHANTED_GOLDEN_APPLE);
        icon("damage_reflection", Items.SHIELD);
        icon("execute", Items.WITHER_SKELETON_SKULL);
        icon("true_damage", Items.NETHERITE_AXE);
        icon("reduce_max_health", Items.WITHER_ROSE);
        icon("scope_attack", Items.TRIDENT);
        icon("repulsion", Items.SLIME_BALL);

        // ═══════ 防御 ═══════
        icon("max_health", Items.RED_BED);
        icon("armor", Items.DIAMOND_CHESTPLATE);
        icon("armor_toughness", Items.NETHERITE_CHESTPLATE);
        icon("health_regen", Items.GLISTERING_MELON_SLICE);
        icon("damage_reduction", Items.IRON_CHESTPLATE);
        icon("knockback_resist", Items.ANVIL);
        icon("fall_resist", Items.FEATHER);
        icon("fire_immunity", Items.BLAZE_POWDER);
        icon("projectile_immunity", Items.SPECTRAL_ARROW);
        icon("explosion_immunity", Items.TNT);
        icon("suffocation_immunity", Items.SAND);
        icon("auto_revive", Items.TOTEM_OF_UNDYING);
        icon("block_chance", Items.SHIELD);
        icon("absorption_shield", Items.GOLDEN_APPLE);
        icon("dodge_chance", Items.RABBIT_FOOT);
        icon("debuff_immunity", Items.MILK_BUCKET);
        icon("invincibility", Items.END_CRYSTAL);

        // ═══════ 机动 ═══════
        icon("movement_speed", Items.LEATHER_BOOTS);
        icon("swim_speed", Items.WATER_BUCKET);
        icon("jump_height", Items.RABBIT_FOOT);
        icon("step_height", Items.LADDER);
        icon("fly_speed", Items.ELYTRA);
        icon("fly", Items.ELYTRA);
        icon("no_fall_damage", Items.FEATHER);
        icon("auto_step", Items.OAK_STAIRS);
        icon("dash_cooldown", Items.CLOCK);
        icon("follow_range", Items.SPYGLASS);

        // ═══════ 功能 ═══════
        icon("luck", Items.RABBIT_FOOT);
        icon("mining_speed", Items.DIAMOND_PICKAXE);
        icon("reach", Items.STICK);
        icon("xp_gain", Items.EXPERIENCE_BOTTLE);
        icon("loot_luck", Items.CHEST);
        icon("night_vision", Items.GOLDEN_CARROT);
        icon("water_breathing", Items.PUFFERFISH);
        icon("no_hunger", Items.COOKED_BEEF);
        icon("item_magnet", Items.HOPPER);
        icon("invisibility", Items.GLASS_BOTTLE);
        icon("vein_miner", Items.IRON_PICKAXE);
        icon("auto_smelt", Items.FURNACE);
        icon("xp_magnet", Items.EXPERIENCE_BOTTLE);
        icon("projectile_tracking", Items.SPECTRAL_ARROW);
        icon("no_invincibility_frames", Items.BLAZE_ROD);
        icon("double_loot", Items.CHEST_MINECART);
        icon("extra_loot_slot", Items.BUNDLE);
        icon("teleport_distance", Items.ENDER_PEARL);
        icon("mining_level", Items.NETHERITE_PICKAXE);
        icon("entity_reach", Items.LEAD);
        icon("crafting_bonus", Items.CRAFTING_TABLE);
        icon("bow_draw_speed", Items.CROSSBOW);
        icon("use_speed", Items.CLOCK);
        icon("auto_repair", Items.ANVIL);
        icon("repair_amount", Items.IRON_INGOT);
        icon("time_accel", Items.CLOCK);
        icon("time_accel_radius", Items.COMPASS);
        icon("cross_dimension_teleport", Items.ENDER_PEARL);
        icon("fixed_point_teleport", Items.LODESTONE);
        icon("portable_crafting", Items.CRAFTING_TABLE);
        icon("portable_furnace", Items.FURNACE);
        icon("pe_auto_learn", Items.BOOK);

        // ═══════ 魔法 ═══════
        icon("max_mana", Items.ENCHANTING_TABLE);
        icon("mana_regen", Items.BOOK);
        icon("magic_damage", Items.BLAZE_ROD);
        icon("cooldown_reduction", Items.CLOCK);
        icon("mana_shield", Items.END_CRYSTAL);
        icon("mana_steal", Items.WITHER_ROSE);
        icon("spell_power", Items.ENCHANTED_BOOK);
        icon("mana_on_kill", Items.EXPERIENCE_BOTTLE);
    }

    private static void icon(String statId, net.minecraft.world.item.Item item) {
        STAT_ICONS.put(statId, new ItemStack(item));
    }

    private static ItemStack getIcon(StatType stat) {
        ItemStack icon = STAT_ICONS.getOrDefault(stat.getId(), ItemStack.EMPTY);
        if (icon.isEmpty() && stat.getCategory() == StatCategory.EXTERNAL) {
            return DEFAULT_EXTERNAL_ICON;
        }
        return icon;
    }

    /** 外部属性的默认图标 */
    private static final ItemStack DEFAULT_EXTERNAL_ICON = new ItemStack(Items.KNOWLEDGE_BOOK);

    // ======================== 构造与初始化 ========================

    public StatsScreen() {
        super(Component.translatable("screen.infinitestats.title"));
    }

    @Override
    protected void init() {
        super.init();
        dragOffsetX = ClientSettings.guiOffsetX;
        dragOffsetY = ClientSettings.guiOffsetY;
        recalcPanelPosition();
        cachedStats = getPlayerStats();
        buildCategoryMap();
        updateMaxScroll();
        rebuildAllWidgets();

        // 搜索框
        searchBox = new EditBox(font, 0, 0, 0, 0,
                Component.translatable("screen.infinitestats.search"));
        searchBox.setMaxLength(30);
        searchBox.setResponder(s -> {
            scrollOffset = 0;
            buildCategoryMap();
            updateMaxScroll();
            rebuildAllWidgets();
        });
        addRenderableWidget(searchBox);

        // 收藏过滤按钮
        favoritesBtn = addRenderableWidget(Button.builder(
                Component.literal("\u2606"), b -> {
                    favoritesOnly = !favoritesOnly;
                    b.setMessage(Component.literal(favoritesOnly ? "\u2605" : "\u2606"));
                    scrollOffset = 0;
                    buildCategoryMap();
                    updateMaxScroll();
                    rebuildAllWidgets();
                })
                .pos(0, 0).size(14, 14).build());

        // 把搜索框和按钮放到正确位置（必须在 addRenderableWidget 之后）
        repositionSearch();
    }

    private static final int SEARCH_BAR_H = 16;
    private static final int STAR_BTN_SIZE = 12;

    private void recalcPanelPosition() {
        int scaledW = (int) (GUI_WIDTH * ClientSettings.guiScale);
        int scaledH = (int) (GUI_HEIGHT * ClientSettings.guiScale);
        leftPos = (width - scaledW) / 2 + dragOffsetX;
        topPos = (height - scaledH) / 2 + dragOffsetY;
        contentTop = HEADER_H + TAB_H + SEARCH_BAR_H + 4;
        contentHeight = MAX_VISIBLE * (CARD_H + CARD_GAP) - CARD_GAP;
        repositionSearch();
    }

    private void repositionSearch() {
        if (searchBox != null) {
            // widgets 在 pose.transform 内渲染，坐标须为面板内坐标
            searchBox.setX(8);
            searchBox.setY(HEADER_H + TAB_H + 1);
            searchBox.setWidth(120);
            searchBox.setHeight(14);
        }
        if (favoritesBtn != null) {
            favoritesBtn.setX(132);
            favoritesBtn.setY(HEADER_H + TAB_H + 1);
        }
    }

    @Override
    public void tick() {
        super.tick();
        cachedStats = getPlayerStats();
    }

    private PlayerStats getPlayerStats() {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            var cap = player.getCapability(PlayerStatsProvider.PLAYER_STATS);
            if (cap.isPresent()) return cap.orElse(null);
        }
        return null;
    }

    private void buildCategoryMap() {
        categoryStats.clear();
        String search = searchBox != null ? searchBox.getValue().toLowerCase().trim() : "";

        for (StatCategory cat : StatCategory.values()) {
            List<StatType> list = new ArrayList<>();
            for (StatType stat : StatType.ALL_STATS) {
                if (stat.getCategory() == cat) {
                    if (!stat.isHidden() || Config.SHOW_HIDDEN_STATS.get()) {
                        if (stat.getId().equals("pe_auto_learn") && !ProjectEBridge.isProjectELoaded()) {
                            continue;
                        }
                        if (matchesSearch(stat, search)) {
                            list.add(stat);
                        }
                    }
                }
            }
            // 仅收藏模式：当前分类只保留已收藏的属性
            if (favoritesOnly && cachedStats != null) {
                list.removeIf(s -> !cachedStats.isFavorite(s.getId()));
            }
            categoryStats.put(cat, list);
        }
        // 为 EXTERNAL 分类构建命名空间分组
        List<StatType> externalStats = categoryStats.get(StatCategory.EXTERNAL);
        if (externalStats != null && !externalStats.isEmpty()) {
            externalGroups.clear();
            for (StatType stat : externalStats) {
                String ns = extractModNamespace(stat);
                externalGroups.computeIfAbsent(ns, k -> new ArrayList<>()).add(stat);
            }
            buildExternalDisplayList();
        }
    }

    private boolean matchesSearch(StatType stat, String search) {
        if (search.isEmpty()) return true;
        String name = Component.translatable(stat.getTranslationKey()).getString().toLowerCase();
        String desc = stat.getDescription() != null ? stat.getDescription().toLowerCase() : "";
        return JechCompat.matches(name, search) || JechCompat.matches(desc, search)
                || JechCompat.matches(stat.getId().toLowerCase(), search);
    }

    /**
     * 从外部属性 ID（attr.modid.path）提取模组命名空间
     */
    private static String extractModNamespace(StatType stat) {
        String id = stat.getId();
        if (id.startsWith("attr.")) {
            int secondDot = id.indexOf('.', 5);
            if (secondDot > 0) {
                return id.substring(5, secondDot);
            }
        }
        return "other";
    }

    /**
     * 根据折叠状态重建外部属性显示列表（分组头 + 展开的属性卡片）
     */
    private void buildExternalDisplayList() {
        externalDisplayList.clear();
        for (Map.Entry<String, List<StatType>> group : externalGroups.entrySet()) {
            String ns = group.getKey();
            List<StatType> stats = group.getValue();
            externalDisplayList.add(DisplayEntry.header(ns, stats.size()));
            if (!collapsedExternalGroups.contains(ns)) {
                for (StatType stat : stats) {
                    externalDisplayList.add(DisplayEntry.statCard(stat));
                }
            }
        }
    }

    /**
     * 切换外部属性组的折叠/展开状态
     */
    private void toggleExternalGroup(String namespace) {
        if (collapsedExternalGroups.contains(namespace)) {
            collapsedExternalGroups.remove(namespace);
        } else {
            collapsedExternalGroups.add(namespace);
        }
        buildExternalDisplayList();
        // 保持当前滚动位置，不重置到顶部
        updateMaxScroll();
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }
        rebuildAllWidgets();
    }

    @Override
    public void onClose() {
        // 自动保存客户端设置
        ClientSettings.save();
        super.onClose();
    }

    // ======================== 音效 ========================

    private void playClickSound() {
        if (minecraft == null) return;
        long now = System.currentTimeMillis();
        if (now - lastSoundTick < 50) return; // 防抖
        lastSoundTick = now;
        minecraft.getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    // ======================== Widget 管理（面板坐标系） ========================

    private void rebuildAllWidgets() {
        clearWidgets();
        dynamicButtons.clear();

        addCategoryTabs();
        addStatCardButtons();
        addBatchModeButtons();
        addResetButtons();
        addPanelNavButtons();

        // 恢复常驻组件（clearWidgets 会清掉）
        if (searchBox != null) addRenderableWidget(searchBox);
        if (favoritesBtn != null) addRenderableWidget(favoritesBtn);
    }

    private void addCategoryTabs() {
        int tabWidth = GUI_WIDTH / StatCategory.values().length;
        StatCategory[] cats = StatCategory.values();
        for (int i = 0; i < cats.length; i++) {
            final StatCategory cat = cats[i];
            int tx = i * tabWidth;
            long pts = getCategoryAllocatedPoints(cat);
            boolean selected = cat == selectedCategory;
            Button btn = new TabButton(tx + 3, HEADER_H + 2, tabWidth - 6, TAB_H - 4,
                    Component.translatable(cat.getTranslationKey()),
                    pts, cat.getColor(), selected, b -> selectCategory(cat));
            addRenderableWidget(btn);
        }
    }

    private long getCategoryAllocatedPoints(StatCategory cat) {
        return cachedStats != null ? cachedStats.getCategoryPoints(cat) : 0L;
    }

    private void addStatCardButtons() {
        if (selectedCategory == StatCategory.EXTERNAL) {
            addExternalStatCardButtons();
            return;
        }

        List<StatType> stats = categoryStats.get(selectedCategory);
        if (stats == null || stats.isEmpty()) return;

        for (int i = 0; i < MAX_VISIBLE; i++) {
            int cardIndex = i + scrollOffset;
            if (cardIndex >= stats.size()) break;

            StatType stat = stats.get(cardIndex);
            int cardY = contentTop + i * (CARD_H + CARD_GAP);
            int btnY = cardY + (CARD_H - BTN_H) / 2;
            int rightEdge = GUI_WIDTH - SCROLLBAR_W - 8;

            if (stat.isToggle()) {
                // 拨动开关区域（在右侧）
                int toggleX = rightEdge - TOGGLE_W - 2;
                int toggleY = cardY + (CARD_H - TOGGLE_H) / 2;
                Button toggleBtn = new ToggleSwitchButton(toggleX, toggleY, TOGGLE_W, TOGGLE_H, stat,
                        b -> toggleStat(stat));
                addRenderableWidget(toggleBtn);
                dynamicButtons.add(toggleBtn);
            } else {
                int minusX = rightEdge - BTN_W * 2 - 4;
                int plusX = minusX + BTN_W + 4;

                Button plusBtn = new PixelButton(plusX, btnY, BTN_W, BTN_H,
                        Component.literal("+"),
                        BG_BTN_PLUS, BG_BTN_PLUS_HOVER, TEXT_BUTTON,
                        b -> { playClickSound(); modifyPoints(stat, getBatchAmount(stat, true)); });
                addRenderableWidget(plusBtn);
                dynamicButtons.add(plusBtn);

                Button minusBtn = new PixelButton(minusX, btnY, BTN_W, BTN_H,
                        Component.literal("−"),
                        BG_BTN_MINUS, BG_BTN_MINUS_HOVER, TEXT_BUTTON,
                        b -> { playClickSound(); modifyPoints(stat, -getBatchAmount(stat, false)); });
                addRenderableWidget(minusBtn);
                dynamicButtons.add(minusBtn);
            }
        }
    }

    private void addExternalStatCardButtons() {
        if (externalDisplayList.isEmpty()) return;

        for (int i = 0; i < MAX_VISIBLE; i++) {
            int displayIndex = i + scrollOffset;
            if (displayIndex >= externalDisplayList.size()) break;

            DisplayEntry entry = externalDisplayList.get(displayIndex);
            if (entry.isHeader) continue; // 分组头不添加按钮

            StatType stat = entry.stat;
            int cardY = contentTop + i * (CARD_H + CARD_GAP);
            int btnY = cardY + (CARD_H - BTN_H) / 2;
            int rightEdge = GUI_WIDTH - SCROLLBAR_W - 8;

            if (stat.isToggle()) {
                int toggleX = rightEdge - TOGGLE_W - 2;
                int toggleY = cardY + (CARD_H - TOGGLE_H) / 2;
                Button toggleBtn = new ToggleSwitchButton(toggleX, toggleY, TOGGLE_W, TOGGLE_H, stat,
                        b -> toggleStat(stat));
                addRenderableWidget(toggleBtn);
                dynamicButtons.add(toggleBtn);
            } else {
                int minusX = rightEdge - BTN_W * 2 - 4;
                int plusX = minusX + BTN_W + 4;

                Button plusBtn = new PixelButton(plusX, btnY, BTN_W, BTN_H,
                        Component.literal("+"),
                        BG_BTN_PLUS, BG_BTN_PLUS_HOVER, TEXT_BUTTON,
                        b -> { playClickSound(); modifyPoints(stat, getBatchAmount(stat, true)); });
                addRenderableWidget(plusBtn);
                dynamicButtons.add(plusBtn);

                Button minusBtn = new PixelButton(minusX, btnY, BTN_W, BTN_H,
                        Component.literal("−"),
                        BG_BTN_MINUS, BG_BTN_MINUS_HOVER, TEXT_BUTTON,
                        b -> { playClickSound(); modifyPoints(stat, -getBatchAmount(stat, false)); });
                addRenderableWidget(minusBtn);
                dynamicButtons.add(minusBtn);
            }
        }
    }

    private void addBatchModeButtons() {
        int footerY = GUI_HEIGHT - FOOTER_H + 26;
        int btnW = 64, gap = 4;

        // 倍率切换按钮：点击循环 x1 → x10 → x100 → x1000 → x10000（同时关闭 MAX）
        Button multBtn = new PixelButton(8, footerY, btnW, 14,
                Component.literal(ADD_LABELS[addAmountIndex]),
                0x50252535, 0x80353550, TEXT_SECONDARY,
                b -> {
                    playClickSound();
                    addAmountIndex = (addAmountIndex + 1) % ADD_LABELS.length;
                    maxMode = false;
                    rebuildAllWidgets();
                });
        addRenderableWidget(multBtn);

        // MAX 按钮：与倍率互斥，开启后一次加满
        Button maxBtn = new PixelButton(8 + btnW + gap, footerY, 48, 14,
                Component.literal("MAX"),
                maxMode ? 0xFFB45309 : 0x50252535,
                maxMode ? 0xFFD97706 : 0x80353550,
                maxMode ? TEXT_PRIMARY : TEXT_SECONDARY,
                b -> {
                    playClickSound();
                    maxMode = !maxMode;
                    rebuildAllWidgets();
                });
        addRenderableWidget(maxBtn);
    }

    private void addResetButtons() {
        int footerY = GUI_HEIGHT - FOOTER_H + 26;
        int btnWidth = 48;

        Button resetCatBtn = new PixelButton(
                GUI_WIDTH - SCROLLBAR_W - btnWidth * 2 - 24, footerY,
                btnWidth, 14,
                Component.translatable("screen.infinitestats.reset_category"),
                0x504A2A1A, 0x808A4A2E, TEXT_SECONDARY,
                b -> { playClickSound(); resetCategory(); });
        addRenderableWidget(resetCatBtn);

        Button resetAllBtn = new PixelButton(
                GUI_WIDTH - SCROLLBAR_W - btnWidth - 12, footerY,
                btnWidth, 14,
                Component.translatable("screen.infinitestats.reset_all"),
                0x504A1A1A, 0x808A2E2E, TEXT_SECONDARY,
                b -> { playClickSound(); resetAll(); });
        addRenderableWidget(resetAllBtn);

        // 打开传送点面板
        Button wpBtn = new PixelButton(
                8 + 4 * 42 + 8, footerY,
                70, 14,
                Component.translatable("screen.infinitestats.waypoint"),
                0x503B5E8A, 0x805080B0, TEXT_SECONDARY,
                b -> {
                    playClickSound();
                    if (minecraft != null) minecraft.setScreen(new WaypointScreen());
                });
        addRenderableWidget(wpBtn);
    }

    /** 页脚导航行：从主面板一键打开其它独立面板。 */
    private void addPanelNavButtons() {
        int navY = GUI_HEIGHT - FOOTER_H + 4;
        int btnW = 45, gap = 4;
        String[] labels = {"传送", "跨维度", "过滤", "EMC", "成就", "物品", "HUD", "工作台", "熔炉"};
        Runnable[] actions = {
                () -> { if (minecraft != null) minecraft.setScreen(new WaypointScreen()); },
                () -> { if (minecraft != null) minecraft.setScreen(new CrossDimScreen()); },
                this::openDebuffFilter,
                () -> NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.EmcOpenPacket()),
                () -> { if (minecraft != null) minecraft.setScreen(new AchievementManagerScreen()); },
                () -> { if (minecraft != null) minecraft.setScreen(new ItemEditorScreen()); },
                () -> { if (minecraft != null) minecraft.setScreen(new HudEditScreen()); },
                () -> NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.CraftingOpenPacket()),
                () -> NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.FurnaceOpenPacket())
        };
        int totalW = labels.length * btnW + (labels.length - 1) * gap;
        int startX = (GUI_WIDTH - totalW) / 2;
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            Button btn = new PixelButton(startX + i * (btnW + gap), navY, btnW, 16,
                    Component.literal(labels[i]), 0x503B5E8A, 0x805080B0, TEXT_SECONDARY,
                    b -> { playClickSound(); actions[idx].run(); });
            addRenderableWidget(btn);
        }
    }

    private void openDebuffFilter() {
        if (minecraft != null) {
            minecraft.setScreen(new DebuffFilterScreen());
        }
    }

    // ======================== 交互逻辑 ========================

    private void selectCategory(StatCategory cat) {
        if (cat == selectedCategory) return;
        playClickSound();
        selectedCategory = cat;
        scrollOffset = 0;
        updateMaxScroll();
        rebuildAllWidgets();
    }

    private void toggleStat(StatType stat) {
        if (cachedStats == null) return;
        long current = cachedStats.getStatLevel(stat);
        if (current >= stat.getMaxLevel()) {
            // 已激活 → 移除
            playClickSound();
            modifyPoints(stat, -stat.getMaxLevel());
        } else {
            // 未激活 → 激活：开关型不允许透支点数
            long needed = stat.getMaxLevel() - current;
            if (cachedStats.getAvailablePoints() < needed) {
                if (minecraft.player != null) {
                    minecraft.player.displayClientMessage(
                            net.minecraft.network.chat.Component.translatable("message.infinitestats.not_enough_points"),
                            true);
                }
                return;
            }
            playClickSound();
            modifyPoints(stat, needed);
        }
    }

    private void resetCategory() {
        NetworkHandler.CHANNEL.sendToServer(
                new NetworkHandler.ResetCategoryPacket(selectedCategory.getName()));
        if (cachedStats != null) {
            for (StatType stat : StatType.ALL_STATS) {
                if (stat.getCategory() == selectedCategory) {
                    cachedStats.resetStat(stat);
                }
            }
        }
    }

    private void resetAll() {
        NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.ResetAllPacket());
        if (cachedStats != null) {
            cachedStats.resetAllPoints();
        }
    }

    private void modifyPoints(StatType stat, long amount) {
        if (cachedStats == null || amount == 0) return;

        // 先用本地副本校验：成功才发送网络包，避免服务器收到无效请求
        boolean success = amount > 0
                ? cachedStats.addPoints(stat, amount)
                : cachedStats.removePoints(stat, -amount);

        if (success) {
            NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.ModifyStatPacket(stat.getId(), amount));
        } else {
            showNotEnoughPoints();
        }
    }

    private long getBatchAmount(StatType stat, boolean isAdd) {
        if (maxMode) { // MAX
            if (cachedStats == null) return 0L;
            long level = cachedStats.getStatLevel(stat);
            long available = cachedStats.getAvailablePoints();
            if (isAdd) {
                if (level >= 0) {
                    // 正值加正：消耗点数，受可用点数和上限约束
                    long space = (long) stat.getMaxLevel() - level;
                    return Math.max(0, Math.min(available, space));
                } else {
                    // 负值加正：往 0 靠近，返还点数，只需 -level 即可归零
                    return -level;
                }
            } else {
                if (level > 0) {
                    // 正值减：往 0 靠近，返还点数，最多减到 0
                    return level;
                } else {
                    // 负值减：远离零点，消耗点数，受可用点数和下限约束
                    long minLevel = -(long) stat.getMaxLevel();
                    long space = level - minLevel; // 还能降多少
                    return Math.max(0, Math.min(available, space));
                }
            }
        }
        // 非 MAX 模式：如果是消耗操作，受可用点数限制
        long base = ADD_VALUES[addAmountIndex];
        if (cachedStats != null) {
            long level = cachedStats.getStatLevel(stat);
            long available = cachedStats.getAvailablePoints();
            if (isAdd && level >= 0) {
                // 正值加正：消耗点数，不得超过可用点数和上限
                long space = (long) stat.getMaxLevel() - level;
                return Math.min(base, Math.min(available, space));
            } else if (!isAdd && level <= 0) {
                // 负值减：消耗点数，不得超过可用点数和下限
                long minLevel = -(long) stat.getMaxLevel();
                long space = level - minLevel;
                return Math.min(base, Math.min(available, space));
            }
        }
        return base;
    }

    private void showNotEnoughPoints() {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.displayClientMessage(
                    Component.translatable("message.infinitestats.not_enough_points"), true);
        }
    }

    /**
     * 快捷加点逻辑（= 键调用，共用）
     * current >= 0 时消耗点数，current < 0 时返还点数
     */
    private void handleQuickAdd(StatType stat) {
        if (cachedStats == null) return;
        long current = cachedStats.getStatLevel(stat);
        long available = cachedStats.getAvailablePoints();
        if (current >= 0 && available <= 0) {
            showNotEnoughPoints();
            return;
        }
        long count;
        if (maxMode) {
            if (current < 0) {
                // 负值加正：往 0 靠近，返还点数，最多加 -current
                count = -current;
            } else {
                // 正值加正：消耗点数，受可用点数和上限约束
                long space = (long) stat.getMaxLevel() - current;
                count = Math.min(available, space);
            }
        } else {
            count = ADD_VALUES[addAmountIndex];
            if (current >= 0) {
                // 消耗操作：不得超过可用点数和上限
                long space = (long) stat.getMaxLevel() - current;
                count = Math.min(count, Math.min(available, space));
            }
        }
        count = Math.max(count, 0L);
        if (count > 0) {
            modifyPoints(stat, count);
            playClickSound();
        }
    }

    private void updateMaxScroll() {
        int size;
        if (selectedCategory == StatCategory.EXTERNAL) {
            size = externalDisplayList.size();
        } else {
            List<StatType> stats = categoryStats.get(selectedCategory);
            size = stats != null ? stats.size() : 0;
        }
        maxScroll = Math.max(0, size - MAX_VISIBLE);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
    }

    private void doScroll(int delta) {
        if (maxScroll <= 0) return;
        scrollOffset = Mth.clamp(scrollOffset + delta, 0, maxScroll);
        rebuildAllWidgets();
    }

    // ======================== 渲染主循环 ========================

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        int panelMouseX = screenToPanelX(mouseX);
        int panelMouseY = screenToPanelY(mouseY);

        // 更新悬停状态
        updateHoverState(panelMouseX, panelMouseY);

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(leftPos, topPos, 0);
        pose.scale(ClientSettings.guiScale, ClientSettings.guiScale, 1.0f);

        renderPanel(graphics, 0, 0);
        renderHeader(graphics, 0, 0);
        renderStatCards(graphics, 0, 0, panelMouseX, panelMouseY);
        renderScrollbar(graphics, 0, 0, panelMouseX, panelMouseY);
        renderFooter(graphics, 0, 0);
        renderDividers(graphics, 0, 0);

        for (var renderable : this.renderables) {
            renderable.render(graphics, panelMouseX, panelMouseY, partialTick);
        }

        pose.popPose();

        renderScaleIndicator(graphics, mouseX, mouseY);
        renderTooltipOverlay(graphics, panelMouseX, panelMouseY, mouseX, mouseY);
    }

    /**
     * 更新鼠标悬停在哪个卡片/按钮上
     */
    private void updateHoverState(int mouseX, int mouseY) {
        hoveredCardIndex = -1;
        hoveredBtnType = 0;

        if (selectedCategory == StatCategory.EXTERNAL) {
            updateExternalHoverState(mouseX, mouseY);
            return;
        }

        List<StatType> stats = categoryStats.get(selectedCategory);
        if (stats == null) return;

        int cardWidth = GUI_WIDTH - SCROLLBAR_W - 16;

        for (int i = 0; i < MAX_VISIBLE; i++) {
            int cardIndex = i + scrollOffset;
            if (cardIndex >= stats.size()) break;
            int cardY = contentTop + i * (CARD_H + CARD_GAP);
            int cardX = 4;

            if (!isMouseInCard(mouseX, mouseY, cardX, cardY, cardWidth)) continue;

            hoveredCardIndex = i;
            StatType stat = stats.get(cardIndex);
            int rightEdge = GUI_WIDTH - SCROLLBAR_W - 8;

            if (stat.isToggle()) {
                int toggleX = rightEdge - TOGGLE_W - 2;
                int toggleY = cardY + (CARD_H - TOGGLE_H) / 2;
                if (mouseX >= toggleX && mouseX < toggleX + TOGGLE_W &&
                        mouseY >= toggleY && mouseY < toggleY + TOGGLE_H) {
                    hoveredBtnType = 3;
                }
            } else {
                int btnY = cardY + (CARD_H - BTN_H) / 2;
                int minusX = rightEdge - BTN_W * 2 - 4;
                int plusX = minusX + BTN_W + 4;
                if (mouseX >= plusX && mouseX < plusX + BTN_W &&
                        mouseY >= btnY && mouseY < btnY + BTN_H) {
                    hoveredBtnType = 2; // plus
                } else if (mouseX >= minusX && mouseX < minusX + BTN_W &&
                        mouseY >= btnY && mouseY < btnY + BTN_H) {
                    hoveredBtnType = 1; // minus
                }
            }
            return;
        }
    }

    private void updateExternalHoverState(int mouseX, int mouseY) {
        int cardWidth = GUI_WIDTH - SCROLLBAR_W - 16;

        for (int i = 0; i < MAX_VISIBLE; i++) {
            int displayIndex = i + scrollOffset;
            if (displayIndex >= externalDisplayList.size()) break;
            int cardY = contentTop + i * (CARD_H + CARD_GAP);
            int cardX = 4;

            if (!isMouseInCard(mouseX, mouseY, cardX, cardY, cardWidth)) continue;

            DisplayEntry entry = externalDisplayList.get(displayIndex);
            hoveredCardIndex = i;

            if (entry.isHeader) {
                hoveredBtnType = 0; // 分组头无按钮
                return;
            }

            // 属性卡片 — 检测按钮区域
            StatType stat = entry.stat;
            int rightEdge = GUI_WIDTH - SCROLLBAR_W - 8;

            if (stat.isToggle()) {
                int toggleX = rightEdge - TOGGLE_W - 2;
                int toggleY = cardY + (CARD_H - TOGGLE_H) / 2;
                if (mouseX >= toggleX && mouseX < toggleX + TOGGLE_W &&
                        mouseY >= toggleY && mouseY < toggleY + TOGGLE_H) {
                    hoveredBtnType = 3;
                }
            } else {
                int btnY = cardY + (CARD_H - BTN_H) / 2;
                int minusX = rightEdge - BTN_W * 2 - 4;
                int plusX = minusX + BTN_W + 4;
                if (mouseX >= plusX && mouseX < plusX + BTN_W &&
                        mouseY >= btnY && mouseY < btnY + BTN_H) {
                    hoveredBtnType = 2;
                } else if (mouseX >= minusX && mouseX < minusX + BTN_W &&
                        mouseY >= btnY && mouseY < btnY + BTN_H) {
                    hoveredBtnType = 1;
                }
            }
            return;
        }
    }

    private int screenToPanelX(int screenX) {
        return (int) ((screenX - leftPos) / ClientSettings.guiScale);
    }

    private int screenToPanelY(int screenY) {
        return (int) ((screenY - topPos) / ClientSettings.guiScale);
    }

    private void renderScaleIndicator(GuiGraphics g, int mouseX, int mouseY) {
        String text = String.format("%.0f%%", ClientSettings.guiScale * 100);
        int tx = leftPos + (int) (GUI_WIDTH * ClientSettings.guiScale) - font.width(text) - 6;
        int ty = topPos + (int) (GUI_HEIGHT * ClientSettings.guiScale) + 4;
        boolean hover = mouseX >= tx - 2 && mouseX <= tx + font.width(text) + 2
                && mouseY >= ty - 2 && mouseY <= ty + font.lineHeight + 2;

        g.fill(tx - 2, ty - 2, tx + font.width(text) + 2, ty + font.lineHeight + 2,
                hover ? 0x80353550 : 0x40151525);
        g.drawString(font, text, tx, ty, hover ? TEXT_PRIMARY : TEXT_HINT);
    }

    // ======================== 面板绘制（面板坐标系） ========================

    private void renderPanel(GuiGraphics g, int l, int t) {
        // 外发光边框
        g.fill(l - 2, t - 2, l + GUI_WIDTH + 2, t + GUI_HEIGHT + 2, 0x403B82F6);
        // 深色边框
        g.fill(l - 1, t - 1, l + GUI_WIDTH + 1, t + GUI_HEIGHT + 1, 0xFF1E293B);
        // 主体面板
        g.fill(l, t, l + GUI_WIDTH, t + GUI_HEIGHT, BG_PANEL);
    }

    private void renderDividers(GuiGraphics g, int l, int t) {
        int y1 = t + HEADER_H;
        g.fill(l + 8, y1, l + GUI_WIDTH - 8, y1 + 1, 0x30334860);
        int y2 = t + HEADER_H + TAB_H;
        g.fill(l + 8, y2, l + GUI_WIDTH - 8, y2 + 1, 0x40334860);
        int y3 = contentTop + contentHeight + 4;
        g.fill(l + 8, y3, l + GUI_WIDTH - 8, y3 + 1, 0x30334860);
    }

    // ======================== 头部绘制 ========================

    private void renderHeader(GuiGraphics g, int l, int t) {
        if (cachedStats == null) {
            String title = Component.translatable("screen.infinitestats.title").getString();
            g.drawCenteredString(font, title, l + GUI_WIDTH / 2, t + 30, TEXT_GOLD);
            return;
        }

        long level = cachedStats.getLevel();
        long xp = cachedStats.getExperience();
        long xpNeeded = cachedStats.getXpForNextLevel();
        long points = cachedStats.getAvailablePoints();
        float maxMana = cachedStats.getMaxMana();

        // 标题
        String title = Component.translatable("screen.infinitestats.title").getString();
        g.drawCenteredString(font, title, l + GUI_WIDTH / 2, t + 8, TEXT_GOLD);

        // 等级
        String levelStr = "Lv." + level;
        g.drawString(font, levelStr, l + 14, t + 28, TEXT_LEVEL);

        // XP 进度条
        int barX = l + 60;
        int barY = t + 26;
        int barW = 200;
        int barH = 12;

        g.fill(barX, barY, barX + barW, barY + barH, BG_XP_BAR);
        if (xpNeeded > 0) {
            float progress = (float) xp / xpNeeded;
            int filled = Math.max(1, (int) (progress * barW));
            g.fill(barX, barY, barX + filled, barY + barH, BG_XP_FILL);
        }
        String xpText = xp + " / " + xpNeeded + " XP";
        g.drawCenteredString(font, xpText, barX + barW / 2, barY + 2, TEXT_PRIMARY);

        // 可用点数（右对齐，正=金色，零=灰色，负=红色）
        String pointsStr = Component.translatable("screen.infinitestats.points_available", points).getString();
        int pointsColor = points > 0 ? TEXT_POINTS : points < 0 ? TEXT_POINTS_NEGATIVE : TEXT_POINTS_ZERO;
        g.drawString(font, pointsStr, l + GUI_WIDTH - 14 - font.width(pointsStr), t + 28, pointsColor);

        if (maxMana > 0) {
            // 法力条
            int manaBarX = l + 60;
            int manaBarY = t + 44;
            int manaBarW = 140;
            int manaBarH = 8;
            float manaPercent = Mth.clamp(cachedStats.getCurrentMana() / maxMana, 0, 1);

            g.fill(manaBarX, manaBarY, manaBarX + manaBarW, manaBarY + manaBarH, BG_MANA_BAR);
            int manaFilled = Math.max(1, (int) (manaPercent * manaBarW));
            g.fill(manaBarX, manaBarY, manaBarX + manaFilled, manaBarY + manaBarH, BG_MANA_FILL);

            String manaStr = String.format("\u2727 %.0f / %.0f", cachedStats.getCurrentMana(), maxMana);
            g.drawString(font, manaStr, manaBarX + manaBarW + 6, manaBarY - 1, TEXT_MANA);
        } else {
            String hint = Component.translatable("screen.infinitestats.hint_controls").getString();
            g.drawString(font, hint, l + 60, t + 44, TEXT_HINT);
        }
    }

    // ======================== 属性卡片绘制 ========================

    private void renderStatCards(GuiGraphics g, int l, int t, int mouseX, int mouseY) {
        if (selectedCategory == StatCategory.EXTERNAL) {
            renderExternalStatCards(g, l, t, mouseX, mouseY);
            return;
        }

        List<StatType> stats = categoryStats.get(selectedCategory);
        if (stats == null || stats.isEmpty()) return;

        int cardWidth = GUI_WIDTH - SCROLLBAR_W - 16;

        for (int i = 0; i < MAX_VISIBLE; i++) {
            int cardIndex = i + scrollOffset;
            if (cardIndex >= stats.size()) break;

            StatType stat = stats.get(cardIndex);
            int cardY = contentTop + i * (CARD_H + CARD_GAP);
            int cardX = l + 4;

            boolean hovered = isMouseInCard(mouseX, mouseY, cardX, cardY, cardWidth);
            renderOneCard(g, cardX, cardY, cardWidth, stat, hovered);
        }
    }

    private void renderExternalStatCards(GuiGraphics g, int l, int t, int mouseX, int mouseY) {
        if (externalDisplayList.isEmpty()) return;

        int cardWidth = GUI_WIDTH - SCROLLBAR_W - 16;

        for (int i = 0; i < MAX_VISIBLE; i++) {
            int displayIndex = i + scrollOffset;
            if (displayIndex >= externalDisplayList.size()) break;

            DisplayEntry entry = externalDisplayList.get(displayIndex);
            int cardY = contentTop + i * (CARD_H + CARD_GAP);
            int cardX = l + 4;
            boolean hovered = isMouseInCard(mouseX, mouseY, cardX, cardY, cardWidth);

            if (entry.isHeader) {
                renderGroupHeaderCard(g, cardX, cardY, cardWidth, entry, hovered);
            } else {
                renderOneCard(g, cardX, cardY, cardWidth, entry.stat, hovered);
            }
        }
    }

    private void renderGroupHeaderCard(GuiGraphics g, int x, int y, int width,
                                        DisplayEntry header, boolean hovered) {
        int bgColor = hovered ? 0x80404060 : 0x40383850;
        g.fill(x, y, x + width, y + CARD_H, bgColor);

        // 左侧灰条
        g.fill(x, y, x + 3, y + CARD_H, 0xFF7777AA);

        // 折叠指示符 + 命名空间 + 数量
        boolean collapsed = collapsedExternalGroups.contains(header.namespace);
        String arrow = collapsed ? "▶" : "▼";
        int iconSize = 12;
        int iconX = x + 18;
        int iconY = y + (CARD_H - iconSize) / 2;
        g.renderItem(GROUP_HEADER_ICON, iconX, iconY);
        String label = arrow + " " + header.namespace + "  §7(" + header.groupSize + ")";
        g.drawString(font, label, x + 34, y + (CARD_H - font.lineHeight) / 2 + 1,
                hovered ? TEXT_PRIMARY : TEXT_SECONDARY);
    }

    /** 分组头默认图标 */
    private static final ItemStack GROUP_HEADER_ICON = new ItemStack(Items.BOOKSHELF);

    private void renderOneCard(GuiGraphics g, int x, int y, int width, StatType stat, boolean hovered) {
        int bgColor = hovered ? BG_CARD_HOVER : BG_CARD;
        g.fill(x, y, x + width, y + CARD_H, bgColor);

        // 左侧类别色条
        int catColor = stat.getCategory().getColor();
        g.fill(x, y, x + 3, y + CARD_H, catColor | 0xFF000000);

        // 收藏星标
        boolean isFav = cachedStats != null && cachedStats.isFavorite(stat.getId());
        int starX = x + 5;
        int starY = y + (CARD_H - font.lineHeight) / 2 + 1;
        int starColor = isFav ? 0xFFFFD700 : 0xFF555555;
        g.drawString(font, isFav ? "\u2605" : "\u2606", starX, starY, starColor);

        // 图标（向右移 10px 给星标留空间）
        ItemStack icon = getIcon(stat);
        if (!icon.isEmpty()) {
            int iconX = x + 18;
            int iconY = y + (CARD_H - ICON_SIZE) / 2;
            g.renderItem(icon, iconX, iconY);
            if (stat.isToggle()) {
                long lvl = cachedStats != null ? cachedStats.getStatLevel(stat) : 0L;
                boolean activeIcon = lvl >= stat.getMaxLevel();
                if (!activeIcon) {
                    g.fill(iconX, iconY, iconX + ICON_SIZE, iconY + ICON_SIZE, 0x60000000);
                }
            }
        }

        int nameX = x + 36;
        int rightEdge = x + width - 8;
        long level = cachedStats != null ? cachedStats.getStatLevel(stat) : 0L;
        float value = cachedStats != null ? cachedStats.getStatValue(stat) : 0;
        int nameColor = hovered ? TEXT_PRIMARY : (stat.getCategory().getColor() | 0xFF000000);

        if (stat.isToggle()) {
            // 拨动开关在按钮区域（由 ToggleSwitchButton 渲染）
            boolean active = level >= stat.getMaxLevel();
            int toggleX = rightEdge - TOGGLE_W - 2;

            // 开关状态文字
            String toggleText = active
                    ? Component.translatable("screen.infinitestats.toggle_on").getString()
                    : Component.translatable("screen.infinitestats.toggle_off").getString();
            int toggleColor = active ? TEXT_TOGGLE_ON : TEXT_TOGGLE_OFF;
            int toggleW = font.width(toggleText);

            // 所需点数标签
            long needed = stat.getMaxLevel();
            String costText = Component.translatable("screen.infinitestats.toggle_cost", needed).getString();
            int costW = font.width(costText);

            // 名称截断，避免与右侧内容重叠
            int maxNameRight = toggleX - toggleW - 6 - costW - 6 - 4;
            int maxNameWidth = Math.max(20, maxNameRight - nameX);
            String name = truncate(getStatDisplayName(stat), maxNameWidth);
            g.drawString(font, name, nameX, y + (CARD_H - font.lineHeight) / 2 + 1, nameColor);

            g.drawString(font, toggleText, toggleX - toggleW - 6,
                    y + (CARD_H - font.lineHeight) / 2 + 1, toggleColor);
            g.drawString(font, costText, toggleX - toggleW - 6 - costW - 6,
                    y + (CARD_H - font.lineHeight) / 2 + 1, TEXT_HINT);
        } else {
            // 等级和数值
            String valueStr = stat.formatValue(value);
            int valueW = font.width(valueStr);
            String levelTag = "Lv." + level;
            int levelW = font.width(levelTag);

            // 名称截断，避免与右侧内容重叠
            int maxNameRight = rightEdge - BTN_W * 2 - 8 - valueW - levelW - 8 - 4;
            int maxNameWidth = Math.max(20, maxNameRight - nameX);
            String name = truncate(getStatDisplayName(stat), maxNameWidth);
            g.drawString(font, name, nameX, y + (CARD_H - font.lineHeight) / 2 + 1, nameColor);

            g.drawString(font, levelTag, rightEdge - BTN_W * 2 - 8 - valueW - levelW - 8,
                    y + (CARD_H - font.lineHeight) / 2 + 1, TEXT_SECONDARY);
            int valColor = value > 0 ? TEXT_ACTIVE : value < 0 ? TEXT_NEGATIVE : TEXT_SECONDARY;
            g.drawString(font, valueStr, rightEdge - BTN_W * 2 - 8 - valueW,
                    y + (CARD_H - font.lineHeight) / 2 + 1, valColor);
        }
    }

    /** 按最大像素宽度截断文本并追加省略号，避免名称过长与右侧元素重叠 */
    private String truncate(String text, int maxWidth) {
        if (text == null || maxWidth <= 0) return "";
        if (font.width(text) <= maxWidth) return text;
        int ellipsisW = font.width("…");
        int limit = maxWidth - ellipsisW;
        if (limit <= 0) return "…";
        StringBuilder sb = new StringBuilder();
        int w = 0;
        for (int i = 0; i < text.length(); i++) {
            int cw = font.width(text.substring(i, i + 1));
            if (w + cw > limit) break;
            w += cw;
            sb.append(text.charAt(i));
        }
        return sb.toString() + "…";
    }

    // ======================== 滚动条绘制 ========================

    private void renderScrollbar(GuiGraphics g, int l, int t, int mouseX, int mouseY) {
        if (maxScroll <= 0) return;

        int sx = l + GUI_WIDTH - SCROLLBAR_W - 4;
        int sy = contentTop;
        int sh = contentHeight;

        g.fill(sx, sy, sx + SCROLLBAR_W, sy + sh, BG_SCROLLBAR_TRACK);

        int sliderH = Math.max(20, (int) ((float) MAX_VISIBLE / (maxScroll + MAX_VISIBLE) * sh));
        int sliderY = sy + (sh - sliderH) * scrollOffset / maxScroll;

        boolean hovering = mouseX >= sx && mouseX < sx + SCROLLBAR_W &&
                mouseY >= sliderY && mouseY < sliderY + sliderH;
        int sliderColor = hovering || scrollbarDragging ? BG_SCROLLBAR_HOVER : BG_SCROLLBAR;

        g.fill(sx, sliderY, sx + SCROLLBAR_W, sliderY + sliderH, sliderColor);
    }

    // ======================== 底栏绘制 ========================

    private void renderFooter(GuiGraphics g, int l, int t) {
        int footerY = t + GUI_HEIGHT - FOOTER_H + 4;
        // 绘制实心底栏背景，避免与卡片重叠
        g.fill(l, footerY, l + GUI_WIDTH, footerY + FOOTER_H - 4, BG_PANEL);
        g.fill(l + 1, footerY, l + GUI_WIDTH - 1, footerY + 1, 0x30334860);

        int hintY = footerY + 38;
        String hint = "P " + Component.translatable("screen.infinitestats.hint_close").getString()
                + "  1-6 " + Component.translatable("screen.infinitestats.hint_switch").getString()
                + "  B " + Component.translatable("screen.infinitestats.hint_batch").getString()
                + "  = " + Component.translatable("screen.infinitestats.hint_quick_add").getString()
                + "  Ctrl+滚轮 " + Component.translatable("screen.infinitestats.hint_zoom").getString()
                + "  右键 " + Component.translatable("screen.infinitestats.hint_drag").getString()
                + "  中键 " + Component.translatable("screen.infinitestats.hint_reset_view").getString();
        g.drawCenteredString(font, hint, l + GUI_WIDTH / 2, hintY, TEXT_HINT);
    }

    // ======================== 悬停提示 ========================

    private void renderTooltipOverlay(GuiGraphics g, int panelMouseX, int panelMouseY,
                                      int screenMouseX, int screenMouseY) {
        if (hoveredCardIndex < 0) return;

        StatType stat;
        long level;
        float value;
        String groupNamespace = null;
        int groupSize = 0;

        if (selectedCategory == StatCategory.EXTERNAL) {
            int displayIndex = hoveredCardIndex + scrollOffset;
            if (displayIndex >= externalDisplayList.size()) return;

            DisplayEntry entry = externalDisplayList.get(displayIndex);
            if (entry.isHeader) {
                // 分组头提示
                groupNamespace = entry.namespace;
                groupSize = entry.groupSize;
                List<String> lines = new ArrayList<>();
                boolean collapsed = collapsedExternalGroups.contains(groupNamespace);
                lines.add("§e" + groupNamespace);
                lines.add("§7" + groupSize + " 个属性 · " + (collapsed ? "点击展开" : "点击折叠"));
                List<net.minecraft.util.FormattedCharSequence> visualLines = new ArrayList<>();
                for (String line : lines) {
                    visualLines.add(Component.literal(line).getVisualOrderText());
                }
                g.renderTooltip(font, visualLines, screenMouseX, screenMouseY);
                return;
            }
            stat = entry.stat;
            level = cachedStats != null ? cachedStats.getStatLevel(stat) : 0L;
            value = cachedStats != null ? cachedStats.getStatValue(stat) : 0;
        } else {
            List<StatType> stats = categoryStats.get(selectedCategory);
            if (stats == null) return;

            int cardIndex = hoveredCardIndex + scrollOffset;
            if (cardIndex >= stats.size()) return;

            stat = stats.get(cardIndex);
            level = cachedStats != null ? cachedStats.getStatLevel(stat) : 0L;
            value = cachedStats != null ? cachedStats.getStatValue(stat) : 0;
        }

        List<String> tooltipLines = new ArrayList<>();

        // 悬停在 +/- 按钮上
        if (!stat.isToggle() && (hoveredBtnType == 1 || hoveredBtnType == 2)) {
            long amount = hoveredBtnType == 2
                    ? getBatchAmount(stat, true)
                    : getBatchAmount(stat, false);
            String label = maxMode ? "MAX" : String.valueOf(amount);
            if (hoveredBtnType == 2) {
                tooltipLines.add(Component.translatable("screen.infinitestats.tooltip_add", label).getString());
                // "+" 统一语义：值往正向走，负值区返还点数
                if (cachedStats != null && !stat.isToggle()) {
                    long nextLevel = level + amount;
                    float nextValue = stat.calculateValue(nextLevel);
                    String preview = Component.translatable("screen.infinitestats.tooltip_preview",
                            stat.formatValue(value), stat.formatValue(nextValue)).getString();
                    tooltipLines.add(preview);
                }
            } else {
                tooltipLines.add(Component.translatable("screen.infinitestats.tooltip_remove", label).getString());
                // "-" 统一语义：值往负向走，负值区消耗点数
                if (!stat.isToggle()) {
                    long prevLevel = level - amount;
                    float prevValue = stat.calculateValue(prevLevel);
                    String preview = Component.translatable("screen.infinitestats.tooltip_preview",
                            stat.formatValue(value), stat.formatValue(prevValue)).getString();
                    tooltipLines.add(preview);
                }
            }
        } else if (stat.isToggle() && hoveredBtnType == 3) {
            // 悬停在拨动开关上
            tooltipLines.add(Component.translatable("screen.infinitestats.tooltip_toggle").getString());
        } else {
            // 悬停在卡片本体上 — 显示详细描述
            String desc = getStatDescription(stat);
            tooltipLines.add(desc);
            if (!stat.isToggle()) {
                tooltipLines.add(Component.translatable("screen.infinitestats.tooltip_current",
                        level, stat.formatValue(value)).getString());
                tooltipLines.add(Component.translatable("screen.infinitestats.per_point",
                        stat.formatValue(stat.getPerPointValue())).getString());
            }
        }

        if (!tooltipLines.isEmpty()) {
            List<net.minecraft.util.FormattedCharSequence> visualLines = new ArrayList<>();
            for (String line : tooltipLines) {
                visualLines.add(Component.literal(line).getVisualOrderText());
            }
            g.renderTooltip(font, visualLines, screenMouseX, screenMouseY);
        }
    }

    // ======================== 鼠标输入 ========================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean onPanel = mouseX >= leftPos && mouseX < leftPos + GUI_WIDTH * ClientSettings.guiScale
                && mouseY >= topPos && mouseY < topPos + GUI_HEIGHT * ClientSettings.guiScale;

        // 中键 — 重置缩放和位置
        if (button == 2) {
            ClientSettings.guiScale = 0.5f;
            dragOffsetX = 0;
            dragOffsetY = 0;
            ClientSettings.guiOffsetX = 0;
            ClientSettings.guiOffsetY = 0;
            recalcPanelPosition();
            rebuildAllWidgets();
            return true;
        }

        // 右键 — 拖动面板
        if (button == 1) {
            if (onPanel) {
                isDragging = true;
                dragStartMouseX = (int) mouseX;
                dragStartMouseY = (int) mouseY;
                dragStartPanelX = dragOffsetX;
                dragStartPanelY = dragOffsetY;
                return true;
            }
        }

        if (onPanel) {
            int px = screenToPanelX((int) mouseX);
            int py = screenToPanelY((int) mouseY);

            // 滚动条拖拽
            if (button == 0 && maxScroll > 0 && isMouseOnScrollbar(px, py)) {
                scrollbarDragging = true;
                scrollbarDragStartY = py;
                // 点击轨道时先把滚动位置对齐到点击处（居中到滑块）
                int sh = contentHeight;
                int sliderH = Math.max(20, (int) ((float) MAX_VISIBLE / (maxScroll + MAX_VISIBLE) * sh));
                int dragRange = sh - sliderH;
                if (dragRange > 0) {
                    int sliderY0 = py - sliderH / 2;
                    float progress = (float) (sliderY0 - contentTop) / dragRange;
                    scrollOffset = Mth.clamp((int) (progress * maxScroll + 0.5f), 0, maxScroll);
                    scrollbarDragStartOffset = scrollOffset;
                } else {
                    scrollbarDragStartOffset = 0;
                }
                rebuildAllWidgets();
                return true;
            }

            // 检测外部属性分组头点击（在 Widget 按钮之前拦截）
            if (button == 0 && selectedCategory == StatCategory.EXTERNAL) {
                if (handleExternalHeaderClick(px, py)) return true;
            }

            // 检测收藏星标点击
            if (button == 0 && handleStarClick(px, py)) return true;

            return super.mouseClicked(px, py, button);
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (scrollbarDragging && maxScroll > 0) {
            int py = screenToPanelY((int) mouseY);
            int sh = contentHeight;
            int sliderH = Math.max(20, (int) ((float) MAX_VISIBLE / (maxScroll + MAX_VISIBLE) * sh));
            int dragRange = sh - sliderH;
            if (dragRange > 0) {
                // 鼠标位移量（像素）→ 滚动位移量，再加上点击时的基准偏移
                float delta = (float)(py - scrollbarDragStartY) / dragRange;
                float progress = delta + (float) scrollbarDragStartOffset / maxScroll;
                scrollOffset = Mth.clamp((int) (progress * maxScroll + 0.5), 0, maxScroll);
                rebuildAllWidgets();
            }
            return true;
        }

        if (isDragging) {
            dragOffsetX = dragStartPanelX + (int) mouseX - dragStartMouseX;
            dragOffsetY = dragStartPanelY + (int) mouseY - dragStartMouseY;
            ClientSettings.guiOffsetX = dragOffsetX;
            ClientSettings.guiOffsetY = dragOffsetY;
            recalcPanelPosition();
            rebuildAllWidgets();
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (scrollbarDragging) {
            scrollbarDragging = false;
            return true;
        }
        if (isDragging && button == 1) {
            isDragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        boolean onPanel = mouseX >= leftPos && mouseX < leftPos + GUI_WIDTH * ClientSettings.guiScale
                && mouseY >= topPos && mouseY < topPos + GUI_HEIGHT * ClientSettings.guiScale;

        if (onPanel) {
            // Ctrl + 滚轮 → 缩放
            if (Screen.hasControlDown()) {
                float newScale = Mth.clamp(ClientSettings.guiScale + (delta > 0 ? SCALE_STEP : -SCALE_STEP),
                        MIN_SCALE, MAX_SCALE);
                if (newScale != ClientSettings.guiScale) {
                    ClientSettings.guiScale = newScale;
                    recalcPanelPosition();
                    rebuildAllWidgets();
                }
                return true;
            }

            // 普通滚轮 → 滚动属性列表
            if (maxScroll > 0) {
                doScroll(delta > 0 ? -1 : 1);
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    // ======================== 键盘输入 ========================

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE ||
                ClientSetup.OPEN_STATS_KEY.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }

        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
            StatCategory[] cats = StatCategory.values();
            int index = keyCode - GLFW.GLFW_KEY_1;
            if (index < cats.length) {
                selectCategory(cats[index]);
                return true;
            }
        }

        if (keyCode == GLFW.GLFW_KEY_EQUAL) {
            // 快速加点：对当前悬停的卡片添加一批点数
            if (selectedCategory == StatCategory.EXTERNAL) {
                if (hoveredCardIndex >= 0) {
                    int displayIndex = hoveredCardIndex + scrollOffset;
                    if (displayIndex >= 0 && displayIndex < externalDisplayList.size()) {
                        DisplayEntry entry = externalDisplayList.get(displayIndex);
                        if (!entry.isHeader) {
                            StatType stat = entry.stat;
                            if (!stat.isToggle()) {
                                handleQuickAdd(stat);
                            }
                        }
                    }
                }
                return true;
            }
            List<StatType> stats = categoryStats.get(selectedCategory);
            if (stats != null && hoveredCardIndex >= 0 && cachedStats != null) {
                int cardIndex = hoveredCardIndex + scrollOffset;
                if (cardIndex >= 0 && cardIndex < stats.size()) {
                    StatType stat = stats.get(cardIndex);
                    if (!stat.isToggle()) {
                        handleQuickAdd(stat);
                    }
                }
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_B) {
            addAmountIndex = (addAmountIndex + 1) % ADD_LABELS.length;
            maxMode = false;
            playClickSound();
            rebuildAllWidgets();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_R && hasShiftDown()) {
            resetCategory();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_R && hasControlDown()) {
            resetAll();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_0 && hasControlDown()) {
            ClientSettings.guiScale = 0.5f;
            dragOffsetX = 0;
            dragOffsetY = 0;
            ClientSettings.guiOffsetX = 0;
            ClientSettings.guiOffsetY = 0;
            recalcPanelPosition();
            rebuildAllWidgets();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_W) {
            doScroll(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_S) {
            doScroll(1);
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ======================== 辅助方法 ========================

    /**
     * 获取属性的显示名称（直接使用翻译键，lang 文件中已包含所有外部属性翻译）
     */
    private String getStatDisplayName(StatType stat) {
        return Component.translatable(stat.getTranslationKey()).getString();
    }

    /**
     * 获取属性的描述文本（优先翻译键，回退到属性注册名）
     */
    private String getStatDescription(StatType stat) {
        String descKey = stat.getTranslationKey() + ".desc";
        String translated = Component.translatable(descKey).getString();
        // 如果翻译就是键本身（没找到翻译），回退到注册名
        if (translated.equals(descKey)) {
            if (stat.getAttributeName() != null) {
                return "[" + stat.getAttributeName() + "]";
            }
            return "";
        }
        return translated;
    }

    private boolean isMouseInCard(int mouseX, int mouseY, int cardX, int cardY, int cardWidth) {
        return mouseX >= cardX && mouseX < cardX + cardWidth &&
                mouseY >= cardY && mouseY < cardY + CARD_H;
    }

    private boolean isMouseOnScrollbar(int mouseX, int mouseY) {
        int sx = GUI_WIDTH - SCROLLBAR_W - 4;
        return mouseX >= sx && mouseX < sx + SCROLLBAR_W + 4 &&
                mouseY >= contentTop && mouseY < contentTop + contentHeight;
    }

    /**
     * 检测是否点击了属性卡片的收藏星标
     */
    private boolean handleStarClick(int px, int py) {
        int cardWidth = GUI_WIDTH - SCROLLBAR_W - 16;
        int starRight = 4 + font.width("\u2605");

        for (int i = 0; i < MAX_VISIBLE; i++) {
            int cardY = contentTop + i * (CARD_H + CARD_GAP);
            if (py >= cardY && py < cardY + CARD_H && px >= 4 && px <= 4 + starRight + 4) {
                // 获取当前卡片对应的 StatType
                StatType stat = null;
                if (selectedCategory == StatCategory.EXTERNAL) {
                    int di = i + scrollOffset;
                    if (di < externalDisplayList.size() && !externalDisplayList.get(di).isHeader) {
                        stat = externalDisplayList.get(di).stat;
                    }
                } else {
                    List<StatType> list = categoryStats.get(selectedCategory);
                    int idx = i + scrollOffset;
                    if (list != null && idx < list.size()) stat = list.get(idx);
                }
                if (stat != null) {
                    playClickSound();
                    NetworkHandler.CHANNEL.sendToServer(
                            new NetworkHandler.ToggleFavoritePacket(stat.getId()));
                    // 立即更新客户端缓存
                    if (cachedStats != null) cachedStats.toggleFavorite(stat.getId());
                    // 如果开启了仅收藏模式，刷新列表
                    if (favoritesOnly) {
                        buildCategoryMap();
                        updateMaxScroll();
                        rebuildAllWidgets();
                    }
                    return true;
                }
                break;
            }
        }
        return false;
    }

    /**
     * 检测是否点击了 EXTERNAL 标签下的分组头
     */
    private boolean handleExternalHeaderClick(int px, int py) {
        int cardWidth = GUI_WIDTH - SCROLLBAR_W - 16;
        for (int i = 0; i < MAX_VISIBLE; i++) {
            int displayIndex = i + scrollOffset;
            if (displayIndex >= externalDisplayList.size()) break;
            int cardY = contentTop + i * (CARD_H + CARD_GAP);
            if (isMouseInCard(px, py, 4, cardY, cardWidth)) {
                DisplayEntry entry = externalDisplayList.get(displayIndex);
                if (entry.isHeader) {
                    playClickSound();
                    toggleExternalGroup(entry.namespace);
                    return true;
                }
                break; // 点到了属性卡片，让 Widget 按钮处理
            }
        }
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    // ======================== 自定义按钮控件 ========================

    /** 像素风格按钮 — 纯色背景 + 居中文字 */
    private static class PixelButton extends Button {
        private final int bgColor, hoverColor, textColor;

        PixelButton(int x, int y, int w, int h, Component message,
                    int bg, int hover, int text, OnPress onPress) {
            super(x, y, w, h, message, onPress, DEFAULT_NARRATION);
            this.bgColor = bg;
            this.hoverColor = hover;
            this.textColor = text;
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            Minecraft mc = Minecraft.getInstance();
            int color = isHoveredOrFocused() ? hoverColor : bgColor;
            g.fill(getX(), getY(), getX() + width, getY() + height, color);
            g.drawCenteredString(mc.font, getMessage(), getX() + width / 2,
                    getY() + (height - mc.font.lineHeight) / 2 + 1, textColor);
        }

        @Override
        public void playDownSound(net.minecraft.client.sounds.SoundManager handler) {
            // 音效已在外层 onPress 中处理，这里静默
        }
    }

    /** 分类标签按钮 — 带颜色指示条 + 点数统计 */
    private static class TabButton extends Button {
        private final int accentColor;
        private final long allocatedPoints;
        private boolean selected;

        TabButton(int x, int y, int w, int h, Component message,
                  long allocatedPoints, int accent, boolean selected, OnPress onPress) {
            super(x, y, w, h, message, onPress, DEFAULT_NARRATION);
            this.allocatedPoints = allocatedPoints;
            this.accentColor = accent;
            this.selected = selected;
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            Minecraft mc = Minecraft.getInstance();
            int bg = selected ? BG_TAB_SELECTED :
                    (isHoveredOrFocused() ? 0x60353550 : BG_TAB);
            g.fill(getX(), getY(), getX() + width, getY() + height, bg);

            // 选中指示条
            if (selected) {
                g.fill(getX() + 4, getY() + height - 3, getX() + width - 4, getY() + height - 1,
                        accentColor | 0xFF000000);
            }

            // 类别名称
            int textColor = selected ? (accentColor | 0xFF000000) :
                    (isHoveredOrFocused() ? TEXT_PRIMARY : TEXT_SECONDARY);
            String label = getMessage().getString();
            int labelW = mc.font.width(label);

            // 如果有点数统计（含负数），紧凑排布
            if (allocatedPoints != 0) {
                String ptsStr = "(" + allocatedPoints + ")";
                int ptsW = mc.font.width(ptsStr);
                int totalW = labelW + ptsW + 4;
                int startX = getX() + (width - totalW) / 2;
                int textY = getY() + (height - mc.font.lineHeight) / 2 + 1;
                int ptsColor = allocatedPoints > 0 ? TEXT_POINTS : TEXT_POINTS_NEGATIVE;

                g.drawString(mc.font, label, startX, textY, textColor);
                g.drawString(mc.font, ptsStr, startX + labelW + 4, textY, ptsColor);
            } else {
                g.drawCenteredString(mc.font, label, getX() + width / 2,
                        getY() + (height - mc.font.lineHeight) / 2 + 1, textColor);
            }
        }

        @Override
        public void playDownSound(net.minecraft.client.sounds.SoundManager handler) {
            // 静默 — 由父级处理
        }
    }

    /** 拨动开关按钮 — 用于 Toggle 型属性 */
    private class ToggleSwitchButton extends Button {
        private final StatType stat;

        ToggleSwitchButton(int x, int y, int w, int h, StatType stat, OnPress onPress) {
            super(x, y, w, h, Component.empty(), onPress, DEFAULT_NARRATION);
            this.stat = stat;
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            long level = cachedStats != null ? cachedStats.getStatLevel(stat) : 0L;
            boolean active = level >= stat.getMaxLevel();
            int tx = getX();
            int ty = getY();

            // 轨道背景
            int trackColor = active ? 0xFF22C55E : 0xFF374151;
            g.fill(tx, ty + 2, tx + TOGGLE_W, ty + TOGGLE_H - 2, trackColor);

            // 阴影
            int knobOffset = active ? TOGGLE_W - 12 : 0;
            g.fill(tx + knobOffset, ty, tx + knobOffset + 12, ty + TOGGLE_H, 0x40000000);

            // 滑块
            int knobColor = isHoveredOrFocused() ? 0xFFFFFFFF : 0xFFDDDDDD;
            g.fill(tx + knobOffset + 1, ty + 1, tx + knobOffset + 11, ty + TOGGLE_H - 1, knobColor);
        }

        @Override
        public void playDownSound(net.minecraft.client.sounds.SoundManager handler) {
            // 静默 — 由 toggleStat 处理
        }
    }
}
