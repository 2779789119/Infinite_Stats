package com.infinitestats.client;

import com.infinitestats.compat.JechCompat;
import com.infinitestats.network.NetworkHandler;
import com.infinitestats.stats.PlayerStatsProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/**
 * 效果过滤器界面（原 Debuff 免疫过滤器）。
 * <p>
 * 玩家勾选需要过滤的效果，支持两种模式：
 * <ul>
 *   <li><b>黑名单</b>（默认）：勾选的效果被拦截，其余放行；</li>
 *   <li><b>白名单</b>：勾选的效果绝对不拦截，其余不处理。</li>
 * </ul>
 * 本次重写要点：
 * <ul>
 *   <li>按原版 {@link MobEffectCategory} 把效果分为<b>增益 / 中性 / 负面</b>三类，
 *       用「左侧色条 + 名称着色 + 类别标签」三重标识；</li>
 *   <li>新增<b>类别筛选</b>（全部 / 增益 / 中性 / 负面），配合搜索可快速定位；</li>
 *   <li>修复旧版搜索框与标题栏同高互相压盖的问题，布局改用 {@link EditorUi} 统一规范；</li>
 *   <li>新增「全选」（作用于当前筛选结果）与底栏统计；移除与风格不符的自绘按钮类。</li>
 * </ul>
 */
public class DebuffFilterScreen extends Screen {

    // ======================== 三分类配色 ========================

    /** 增益（BENEFICIAL） */
    static final int COLOR_BENEFICIAL = 0xFF4ADE80;
    /** 负面（HARMFUL） */
    static final int COLOR_HARMFUL = 0xFFF87171;
    /** 中性（NEUTRAL） */
    static final int COLOR_NEUTRAL = 0xFFFBBF24;
    /** 未注册 / 自定义（类别未知） */
    static final int COLOR_UNKNOWN = 0xFF94A3B8;

    /** 黑名单模式下「已勾选 = 将被拦截」的行底色 */
    private static final int BG_PICKED_BLOCK = 0x504A2A1A;
    private static final int BG_PICKED_BLOCK_HOVER = 0x808A4A2E;
    /** 白名单模式下「已勾选 = 受到保护」的行底色 */
    private static final int BG_PICKED_KEEP = 0x502A4A1A;
    private static final int BG_PICKED_KEEP_HOVER = 0x804E8A2E;

    private static final int CAT_DIM = 0xFF64748B;

    // ======================== 布局常量 ========================

    private static final int GUI_W = 420;
    private static final int GUI_H = 312;

    private static final int SEARCH_Y = EditorUi.HEADER_H + 6;              // 30
    private static final int SEARCH_H = 18;
    private static final int LIST_Y = SEARCH_Y + SEARCH_H + 4;              // 52
    private static final int VISIBLE_ROWS = 9;
    private static final int LIST_H = VISIBLE_ROWS * EditorUi.ROW_H;        // 198

    private static final int CUSTOM_Y = LIST_Y + LIST_H + 6;                // 256
    private static final int CUSTOM_H = 20;
    private static final int FOOTER_Y = CUSTOM_Y + CUSTOM_H + 10;           // 286
    private static final int FOOTER_H = 20;

    private static final int CAT_BTN_W = 40;
    private static final int CAT_BTN_H = 16;
    private static final int CAT_BTN_GAP = 3;
    private static final int CAT_TOTAL_W =
            CAT_BTN_W * 4 + CAT_BTN_GAP * 3;                                // 169

    private static final int MODE_BTN_W = 96;
    private static final int CUSTOM_LABEL_W = 54;
    private static final int CUSTOM_ADD_W = 66;

    // ======================== 类别筛选 ========================

    private enum CatFilter {
        ALL("all", EditorUi.INFO),
        BENEFICIAL("beneficial", COLOR_BENEFICIAL),
        NEUTRAL("neutral", COLOR_NEUTRAL),
        HARMFUL("harmful", COLOR_HARMFUL);

        final String key;
        final int color;

        CatFilter(String key, int color) {
            this.key = key;
            this.color = color;
        }

        String labelKey() {
            return "screen.infinitestats.debuff_filter.cat_" + key;
        }

        /** 自定义条目（类别未知）只在「全部」下出现。 */
        boolean accept(MobEffectCategory category) {
            if (this == ALL) return true;
            if (category == null) return false;
            return switch (this) {
                case BENEFICIAL -> category == MobEffectCategory.BENEFICIAL;
                case NEUTRAL -> category == MobEffectCategory.NEUTRAL;
                case HARMFUL -> category == MobEffectCategory.HARMFUL;
                case ALL -> true;
            };
        }
    }

    /** 统一的列表条目。category 为 null 表示未注册 / 自定义效果。 */
    private record FilterEntry(String name, String id, boolean isCustom, MobEffectCategory category) {

        int color() {
            if (category == null) return COLOR_UNKNOWN;
            return switch (category) {
                case BENEFICIAL -> COLOR_BENEFICIAL;
                case HARMFUL -> COLOR_HARMFUL;
                case NEUTRAL -> COLOR_NEUTRAL;
            };
        }

        String categoryKey() {
            if (category == null) return "screen.infinitestats.debuff_filter.cat_custom";
            return switch (category) {
                case BENEFICIAL -> "screen.infinitestats.debuff_filter.cat_beneficial";
                case HARMFUL -> "screen.infinitestats.debuff_filter.cat_harmful";
                case NEUTRAL -> "screen.infinitestats.debuff_filter.cat_neutral";
            };
        }
    }

    // ======================== 状态 ========================

    private int leftPos, topPos;
    private int scrollOffset, maxScroll;

    private EditBox searchBox;
    private EditBox customInputBox;
    private Button modeButton;

    private String searchText = "";
    private CatFilter categoryFilter = CatFilter.ALL;
    private boolean useBlacklist = true;

    private final Set<String> filteredEffects = new HashSet<>();
    private final Set<String> customIds = new HashSet<>();

    private List<MobEffect> allEffects = new ArrayList<>();
    private List<FilterEntry> displayedEntries = new ArrayList<>();

    private int hoveredIndex = -1;
    private long lastSoundTick;
    private String statusMsg = "";
    private long statusUntil;

    private final List<int[]> rowRects = new ArrayList<>();
    private final List<int[]> catBtnRects = new ArrayList<>();

    private boolean scrollbarDragging;
    private int scrollbarDragStartY;
    private int scrollbarDragStartOffset;

    public DebuffFilterScreen() {
        super(Component.translatable("screen.infinitestats.debuff_filter"));
    }

    // ======================== 初始化 ========================

    @Override
    protected void init() {
        super.init();
        leftPos = Math.max(0, (width - GUI_W) / 2);
        topPos = Math.max(0, (height - GUI_H) / 2);

        loadFromPlayer();
        buildAllEffects();
        applyFilters();

        searchBox = new EditBox(font, leftPos + EditorUi.GAP, topPos + SEARCH_Y,
                searchWidth(), SEARCH_H, Component.empty());
        searchBox.setMaxLength(50);
        searchBox.setTextColor(EditorUi.PRIMARY);
        searchBox.setHint(Component.translatable("screen.infinitestats.debuff_filter.search_hint"));
        searchBox.setValue(searchText);
        searchBox.setResponder(v -> { searchText = v; applyFilters(); });
        searchBox.setFocused(true);
        addRenderableWidget(searchBox);

        modeButton = Button.builder(modeLabel(), b -> toggleMode())
                .bounds(leftPos + GUI_W - EditorUi.GAP - MODE_BTN_W, topPos + 4, MODE_BTN_W, 16)
                .build();
        addRenderableWidget(modeButton);

        customInputBox = new EditBox(font, leftPos + EditorUi.GAP + CUSTOM_LABEL_W,
                topPos + CUSTOM_Y, GUI_W - CUSTOM_LABEL_W - EditorUi.GAP * 2 - CUSTOM_ADD_W - 4,
                CUSTOM_H, Component.empty());
        customInputBox.setMaxLength(80);
        customInputBox.setTextColor(EditorUi.PRIMARY);
        customInputBox.setHint(Component.translatable("screen.infinitestats.debuff_filter.custom_hint"));
        addRenderableWidget(customInputBox);

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.infinitestats.debuff_filter.custom_add"),
                        b -> addCustomId())
                .bounds(leftPos + GUI_W - EditorUi.GAP - CUSTOM_ADD_W, topPos + CUSTOM_Y,
                        CUSTOM_ADD_W, CUSTOM_H).build());

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.infinitestats.debuff_filter.clear"),
                        b -> clearAll())
                .bounds(leftPos + EditorUi.GAP, topPos + FOOTER_Y, 70, FOOTER_H).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("screen.infinitestats.debuff_filter.select_all"),
                        b -> selectAllShown())
                .bounds(leftPos + EditorUi.GAP + 74, topPos + FOOTER_Y, 70, FOOTER_H).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("screen.infinitestats.debuff_filter.done"),
                        b -> onClose())
                .bounds(leftPos + GUI_W - EditorUi.GAP - 100, topPos + FOOTER_Y, 100, FOOTER_H).build());
    }

    private int searchWidth() {
        return GUI_W - EditorUi.GAP * 2 - CAT_TOTAL_W - 4;
    }

    /** 从客户端缓存的属性数据读取当前过滤状态。 */
    private void loadFromPlayer() {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
            useBlacklist = stats.isBuffUseBlacklist();
            filteredEffects.clear();
            filteredEffects.addAll(stats.getBuffFilterList());
            customIds.clear();
            for (String id : filteredEffects) {
                ResourceLocation rl = ResourceLocation.tryParse(id);
                if (rl == null || ForgeRegistries.MOB_EFFECTS.getValue(rl) == null) customIds.add(id);
            }
        });
    }

    private void buildAllEffects() {
        allEffects.clear();
        for (MobEffect effect : ForgeRegistries.MOB_EFFECTS) allEffects.add(effect);
        allEffects.sort(Comparator.comparing(e ->
                e.getDisplayName().getString().toLowerCase(Locale.ROOT)));
    }

    /**
     * 查询某个效果的说明文本。
     * <p>
     * 取值顺序：
     * <ol>
     *   <li><code>effect.&lt;命名空间&gt;.&lt;路径&gt;.desc</code> —— 通用约定键，
     *       任何模组或资源包按此格式提供即可自动生效；</li>
     *   <li><code>infinitestats.effect.&lt;命名空间&gt;.&lt;路径&gt;</code> —— 本模组内置说明
     *       （独立命名空间，避免与其他模组抢占同名键）。</li>
     * </ol>
     *
     * @return 说明文本；两处都没有时返回 {@code null}
     */
    static String effectDescription(ResourceLocation rl) {
        if (rl == null) return null;
        String[] keys = {
                "effect." + rl.getNamespace() + "." + rl.getPath() + ".desc",
                "infinitestats.effect." + rl.getNamespace() + "." + rl.getPath()
        };
        for (String key : keys) {
            String val = Language.getInstance().getOrDefault(key, "");
            if (val != null && !val.isEmpty() && !val.equals(key)) return val;
        }
        return null;
    }

    /** 依据「类别筛选 + 搜索关键字」重建显示列表（搜索同时匹配效果说明）。 */
    private void applyFilters() {
        displayedEntries.clear();
        String q = searchText.toLowerCase(Locale.ROOT).trim();

        for (MobEffect effect : allEffects) {
            ResourceLocation rl = ForgeRegistries.MOB_EFFECTS.getKey(effect);
            if (rl == null) continue;
            String id = rl.toString();
            MobEffectCategory category = effect.getCategory();
            if (!categoryFilter.accept(category)) continue;

            String name = effect.getDisplayName().getString();
            if (!q.isEmpty()) {
                String desc = effectDescription(rl);
                if (!JechCompat.matches(name.toLowerCase(Locale.ROOT), q)
                        && !JechCompat.matches(id.toLowerCase(Locale.ROOT), q)
                        && (desc == null || !JechCompat.matches(desc.toLowerCase(Locale.ROOT), q))) {
                    continue;
                }
            }
            displayedEntries.add(new FilterEntry(name, id, false, category));
        }

        for (String customId : customIds) {
            if (!categoryFilter.accept(null)) continue;
            if (!q.isEmpty() && !JechCompat.matches(customId.toLowerCase(Locale.ROOT), q)) continue;
            boolean listed = false;
            for (MobEffect e : allEffects) {
                ResourceLocation key = ForgeRegistries.MOB_EFFECTS.getKey(e);
                if (key != null && key.toString().equals(customId)) { listed = true; break; }
            }
            if (!listed) displayedEntries.add(new FilterEntry(customId, customId, true, null));
        }

        maxScroll = Math.max(0, displayedEntries.size() - VISIBLE_ROWS);
        scrollOffset = clamp(scrollOffset, 0, maxScroll);
    }

    // ======================== 数据操作 ========================

    private void toggleMode() {
        useBlacklist = !useBlacklist;
        if (modeButton != null) modeButton.setMessage(modeLabel());
        playClick();
        sendFilterUpdate();
    }

    private Component modeLabel() {
        return Component.translatable("screen.infinitestats.debuff_filter.mode_label",
                Component.translatable(useBlacklist
                        ? "screen.infinitestats.debuff_filter.mode_blacklist"
                        : "screen.infinitestats.debuff_filter.mode_whitelist"));
    }

    private void toggleEffect(FilterEntry entry) {
        if (!filteredEffects.remove(entry.id())) {
            filteredEffects.add(entry.id());
            if (entry.isCustom()) customIds.add(entry.id());
        } else if (entry.isCustom()) {
            customIds.remove(entry.id());
        }
        playClick();
        sendFilterUpdate();
    }

    private void clearAll() {
        filteredEffects.clear();
        customIds.clear();
        applyFilters();
        playClick();
        sendFilterUpdate();
    }

    /** 把当前筛选结果全部勾选（例如先筛「负面」再点全选，即可一键拦截所有负面效果）。 */
    private void selectAllShown() {
        for (FilterEntry entry : displayedEntries) filteredEffects.add(entry.id());
        playClick();
        sendFilterUpdate();
    }

    private void addCustomId() {
        String input = customInputBox.getValue().trim().toLowerCase(Locale.ROOT);
        if (input.isEmpty()) return;
        if (!input.contains(":")) input = "minecraft:" + input;

        ResourceLocation rl = ResourceLocation.tryParse(input);
        if (rl == null) {
            setStatus(Component.translatable("screen.infinitestats.debuff_filter.invalid_id").getString());
            return;
        }
        filteredEffects.add(input);
        if (ForgeRegistries.MOB_EFFECTS.getValue(rl) == null) customIds.add(input);

        customInputBox.setValue("");
        applyFilters();
        playClick();
        sendFilterUpdate();
    }

    private void sendFilterUpdate() {
        NetworkHandler.CHANNEL.sendToServer(
                new NetworkHandler.UpdateBuffFilterPacket(useBlacklist, new HashSet<>(filteredEffects)));

        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats ->
                    stats.setBuffFilterList(new HashSet<>(filteredEffects), useBlacklist));
        }
    }

    private void setStatus(String msg) {
        statusMsg = msg;
        statusUntil = System.currentTimeMillis() + 2500;
    }

    private void playClick() {
        if (minecraft == null) return;
        long now = System.currentTimeMillis();
        if (now - lastSoundTick < 50) return;
        lastSoundTick = now;
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    // ======================== 绘制 ========================

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);

        EditorUi.panel(g, leftPos, topPos, GUI_W, GUI_H);
        EditorUi.title(g, font, leftPos, topPos, GUI_W, title.getString());

        drawCategoryButtons(g, mx, my);
        drawList(g, mx, my);

        EditorUi.dividerH(g, leftPos + 4, topPos + LIST_Y + LIST_H - 2, GUI_W - 8);
        g.drawString(font, Component.translatable("screen.infinitestats.debuff_filter.custom_input").getString(),
                leftPos + EditorUi.GAP, topPos + CUSTOM_Y + 6, EditorUi.GOLD);
        EditorUi.dividerH(g, leftPos + 4, topPos + FOOTER_Y - 6, GUI_W - 8);

        // 底栏统计（说明当前模式的含义）
        String stat = Component.translatable(useBlacklist
                        ? "screen.infinitestats.debuff_filter.stat_blacklist"
                        : "screen.infinitestats.debuff_filter.stat_whitelist",
                filteredEffects.size()).getString();
        g.drawString(font, stat, leftPos + 156, topPos + FOOTER_Y + 6,
                useBlacklist ? COLOR_HARMFUL : COLOR_BENEFICIAL);

        super.render(g, mx, my, pt);

        drawTooltip(g, mx, my);

        if (System.currentTimeMillis() < statusUntil) {
            g.drawCenteredString(font, statusMsg, leftPos + GUI_W / 2, topPos + GUI_H - 8, EditorUi.DANGER);
        }
    }

    private void drawCategoryButtons(GuiGraphics g, int mx, int my) {
        catBtnRects.clear();
        int by = topPos + SEARCH_Y + (SEARCH_H - CAT_BTN_H) / 2;
        int bx = leftPos + GUI_W - EditorUi.GAP - CAT_TOTAL_W;

        for (CatFilter f : CatFilter.values()) {
            boolean active = categoryFilter == f;
            boolean hover = mx >= bx && mx < bx + CAT_BTN_W && my >= by && my < by + CAT_BTN_H;

            int bg = active ? ((f.color & 0x00FFFFFF) | 0x50000000)
                    : hover ? EditorUi.BG_ROW_HOVER : EditorUi.BG_ROW;
            g.fill(bx, by, bx + CAT_BTN_W, by + CAT_BTN_H, bg);
            // 激活时用类别色画下边框，非激活时画一条暗边框
            g.fill(bx, by + CAT_BTN_H - 1, bx + CAT_BTN_W, by + CAT_BTN_H,
                    active ? f.color : EditorUi.BG_DIVIDER);

            g.drawCenteredString(font, Component.translatable(f.labelKey()).getString(),
                    bx + CAT_BTN_W / 2, by + (CAT_BTN_H - font.lineHeight) / 2 + 1,
                    active ? f.color : EditorUi.SECONDARY);

            catBtnRects.add(new int[]{bx, by, CAT_BTN_W, CAT_BTN_H, f.ordinal()});
            bx += CAT_BTN_W + CAT_BTN_GAP;
        }
    }

    private void drawList(GuiGraphics g, int mx, int my) {
        int x = leftPos + EditorUi.GAP;
        int w = GUI_W - EditorUi.GAP * 2 - EditorUi.SCROLL_W - 2;
        int ly = topPos + LIST_Y;

        rowRects.clear();
        hoveredIndex = -1;

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int idx = i + scrollOffset;
            if (idx >= displayedEntries.size()) break;

            FilterEntry entry = displayedEntries.get(idx);
            boolean picked = filteredEffects.contains(entry.id());
            int y = ly + i * EditorUi.ROW_H;
            boolean hover = mx >= x && mx < x + w && my >= y && my < y + EditorUi.ROW_H - 2;
            if (hover) hoveredIndex = idx;

            int bg;
            if (picked) {
                bg = useBlacklist
                        ? (hover ? BG_PICKED_BLOCK_HOVER : BG_PICKED_BLOCK)
                        : (hover ? BG_PICKED_KEEP_HOVER : BG_PICKED_KEEP);
            } else {
                bg = hover ? EditorUi.BG_ROW_HOVER : EditorUi.BG_ROW;
            }
            g.fill(x, y, x + w, y + EditorUi.ROW_H - 2, bg);

            // 类别色条：整行最直观的分类标识
            g.fill(x, y, x + 2, y + EditorUi.ROW_H - 2, entry.color());

            int ty = y + (EditorUi.ROW_H - 2 - font.lineHeight) / 2 + 1;
            g.drawString(font, picked ? "◉" : "○", x + 6, ty, picked ? entry.color() : CAT_DIM);

            // 名称直接占满「勾选图标 → ID」之间的空间。
            // 行内不再绘制「增益 / 中性 / 负面」文字标签：左侧色条 + 名称着色已经足够区分，
            // 而文字标签会占掉约 40px，把长效果名挤到几乎看不见。
            // 需要确认精确类别时看悬停浮窗里的「类别: xxx」。
            String idText = EditorUi.ellipsize(font, entry.id(), 140);
            int idX = x + w - font.width(idText) - 6;

            String name = entry.isCustom() ? "⚙ " + entry.name() : entry.name();
            int nameMax = Math.max(20, idX - (x + 18) - 6);
            g.drawString(font, EditorUi.ellipsize(font, name, nameMax), x + 18, ty, entry.color());
            g.drawString(font, idText, idX, ty, EditorUi.SECONDARY);

            rowRects.add(new int[]{x, y, w, EditorUi.ROW_H - 2, idx});
        }

        if (displayedEntries.isEmpty()) {
            g.drawString(font,
                    Component.translatable("screen.infinitestats.debuff_filter.no_match").getString(),
                    x + 6, ly + 6, EditorUi.SECONDARY);
        }

        EditorUi.scrollbar(g, mx, my, leftPos + GUI_W - EditorUi.GAP - EditorUi.SCROLL_W,
                ly, LIST_H - 2, scrollOffset, maxScroll, VISIBLE_ROWS);
    }

    private void drawTooltip(GuiGraphics g, int mx, int my) {
        if (hoveredIndex < 0 || hoveredIndex >= displayedEntries.size()) return;
        FilterEntry entry = displayedEntries.get(hoveredIndex);
        boolean picked = filteredEffects.contains(entry.id());

        List<String> lines = new ArrayList<>();
        lines.add("§f§l" + entry.name());
        lines.add("§7类别: " + chatColor(entry.color())
                + Component.translatable(entry.categoryKey()).getString());

        // 效果说明（可由其他模组 / 资源包通过 effect.<ns>.<path>.desc 覆盖）
        String desc = effectDescription(ResourceLocation.tryParse(entry.id()));
        if (desc != null) {
            lines.add("");
            for (String line : EditorUi.wrap(font, desc, 190)) {
                lines.add(line.isEmpty() ? "§8§m        " : "§7" + line);
            }
        }

        if (useBlacklist) {
            lines.add(picked
                    ? Component.translatable("screen.infinitestats.debuff_filter.tooltip_blacklist_blocked").getString()
                    : Component.translatable("screen.infinitestats.debuff_filter.tooltip_blacklist_allowed").getString());
        } else {
            lines.add(picked
                    ? Component.translatable("screen.infinitestats.debuff_filter.tooltip_whitelist_protected").getString()
                    : Component.translatable("screen.infinitestats.debuff_filter.tooltip_whitelist_ignored").getString());
        }
        lines.add("§8" + entry.id());
        lines.add("§8" + Component.translatable("screen.infinitestats.debuff_filter.tooltip_click").getString());

        EditorUi.tooltip(g, mx, my, lines, entry.color());
    }

    /** 把 ARGB 映射到最接近的原版格式码，用于 tooltip 内的彩色片段。 */
    private static String chatColor(int argb) {
        return switch (argb) {
            case COLOR_BENEFICIAL -> "§a";
            case COLOR_HARMFUL -> "§c";
            case COLOR_NEUTRAL -> "§e";
            default -> "§7";
        };
    }

    // ======================== 鼠标输入 ========================

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            // 类别筛选
            for (int[] r : catBtnRects) {
                if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                    categoryFilter = CatFilter.values()[r[4]];
                    scrollOffset = 0;
                    applyFilters();
                    playClick();
                    return true;
                }
            }

            // 列表条目
            if (hoveredIndex >= 0 && hoveredIndex < displayedEntries.size()) {
                toggleEffect(displayedEntries.get(hoveredIndex));
                return true;
            }

            // 滚动条
            if (maxScroll > 0 && isOnScrollbar((int) mx, (int) my)) {
                scrollbarDragging = true;
                scrollbarDragStartY = (int) my;
                scrollbarDragStartOffset = scrollOffset;
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (scrollbarDragging && maxScroll > 0) {
            int[] slider = sliderRect();
            int trackY = topPos + LIST_Y;
            int trackH = LIST_H - 2;
            int range = trackH - slider[3];
            if (range > 0) {
                int newY = (int) (my - (slider[1] - scrollbarDragStartY) - trackY);
                float progress = (float) newY / range;
                scrollOffset = clamp((int) (progress * maxScroll + 0.5f), 0, maxScroll);
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (scrollbarDragging) {
            scrollbarDragging = false;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (maxScroll > 0) {
            scrollOffset = clamp(scrollOffset + (delta > 0 ? -1 : 1), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    /** 与 {@link EditorUi#scrollbar} 使用同一套几何计算，保证拖动与绘制一致。 */
    private int[] sliderRect() {
        int sx = leftPos + GUI_W - EditorUi.GAP - EditorUi.SCROLL_W;
        int sy = topPos + LIST_Y;
        int h = LIST_H - 2;
        int sliderH = Math.min(h, Math.max(16,
                (int) ((float) VISIBLE_ROWS / (maxScroll + VISIBLE_ROWS) * h)));
        int sliderY = maxScroll > 0 ? sy + (h - sliderH) * scrollOffset / maxScroll : sy;
        return new int[]{sx, sliderY, EditorUi.SCROLL_W, sliderH};
    }

    private boolean isOnScrollbar(int mx, int my) {
        int[] slider = sliderRect();
        int trackX = leftPos + GUI_W - EditorUi.GAP - EditorUi.SCROLL_W;
        return mx >= trackX - 2 && mx < trackX + EditorUi.SCROLL_W + 2
                && my >= topPos + LIST_Y && my < topPos + LIST_Y + LIST_H - 2;
    }

    // ======================== 键盘输入 ========================

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && customInputBox != null && customInputBox.isFocused()) {
            addCustomId();
            return true;
        }
        // 方向键翻列表（不占用 W/S，避免与搜索框输入冲突）
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
            if (maxScroll > 0) {
                scrollOffset = clamp(scrollOffset + (keyCode == GLFW.GLFW_KEY_UP ? -1 : 1), 0, maxScroll);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
