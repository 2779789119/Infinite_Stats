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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 「自动入库」过滤界面 —— 用白名单 / 黑名单控制哪些物品会被自动存入存储网络。
 * <p>
 * 视觉与交互沿用 {@link EditorUi} 规范（与物品编辑器、效果过滤一致）：
 * <ul>
 *   <li>搜索框支持物品名与物品 ID（装了 JustEnoughCharacters 时按拼音匹配）；</li>
 *   <li>列表列出全部物品，<b>点击行即切换「在名单内 / 不在名单内」</b>，在名单内的行有绿色指示条；</li>
 *   <li>底栏「手持加入」可一键把当前主手物品加入名单，省去搜索；</li>
 *   <li>「只看已选」把列表收窄到已选条目，方便核对与移除。</li>
 * </ul>
 * 语义：<b>白名单</b> = 只入库名单内的物品；<b>黑名单</b> = 名单内的物品不入库。
 * 名单为空时不做任何限制（避免清空列表后自动入库整体失效）。
 */
public class AutoDepositFilterScreen extends Screen {

    // ======================== 布局常量 ========================

    private static final int GUI_W = 420;
    private static final int GUI_H = 272;

    private static final int SEARCH_Y = EditorUi.HEADER_H + 4;   // 28
    private static final int SEARCH_H = 18;
    private static final int HINT_Y = SEARCH_Y + SEARCH_H + 2;    // 48
    private static final int LIST_Y = HINT_Y + EditorUi.ROW_H - 8; // 60
    private static final int VISIBLE_ROWS = 8;
    private static final int LIST_H = VISIBLE_ROWS * EditorUi.ROW_H;

    private static final int FOOTER_Y = LIST_Y + LIST_H + 8;      // 244
    private static final int FOOTER_H = 20;

    private static final int MODE_W = 118;

    // ======================== 状态 ========================

    /** 候选物品（全部注册物品，按名称排序；item 直接缓存，避免每帧查注册表） */
    private record Candidate(ResourceLocation id, String display, Item item) {}

    private final List<Candidate> all = new ArrayList<>();
    private List<Candidate> filtered = new ArrayList<>();

    /** 当前名单（物品 ID），本地立即生效，同时提交给服务端 */
    private final Set<String> selectedIds = new HashSet<>();
    private boolean useWhitelist;
    private boolean onlySelected;

    private String searchText = "";
    private int scroll, maxScroll;
    private Candidate hovered;
    private final List<int[]> rowRects = new ArrayList<>();

    private EditBox searchBox;
    private Button modeButton;

    private long lastSoundTick;
    private String statusMsg = "";
    private long statusUntil;

    private int leftPos, topPos;

    /** 关闭本界面时返回的属性面板（沿用物品编辑器的「返回父界面」模式） */
    private final Screen parent;

    public AutoDepositFilterScreen(Screen parent) {
        super(Component.translatable("screen.infinitestats.deposit_filter"));
        this.parent = parent;
        buildCandidates();
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    /** 构建全部物品候选（名称翻译优先，缺失时回退到可读的物品 ID）。 */
    private void buildCandidates() {
        for (Item item : ForgeRegistries.ITEMS) {
            if (item == Items.AIR) continue;
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
            if (id == null) continue;
            all.add(new Candidate(id, displayName(item, id), item));
        }
        all.sort(Comparator.comparing(Candidate::display, String.CASE_INSENSITIVE_ORDER));
    }

    private static String displayName(Item item, ResourceLocation id) {
        String key = item.getDescriptionId();
        String translated = Component.translatable(key).getString();
        if (!translated.equals(key)) return translated;
        // 模组没提供译文：用「命名空间: 可读路径」兜底，避免直接摆出 item.modid.foo 这种键名
        return id.getNamespace() + ": " + id.getPath().replace('_', ' ');
    }

    // ======================== 初始化 ========================

    @Override
    protected void init() {
        super.init();
        leftPos = Math.max(0, (width - GUI_W) / 2);
        topPos = Math.max(0, (height - GUI_H) / 2);

        loadFromPlayer();
        applyFilter();
        rebuild();
    }

    private void loadFromPlayer() {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
            selectedIds.clear();
            selectedIds.addAll(stats.getAutoDepositFilterList());
            useWhitelist = stats.isAutoDepositUseWhitelist();
        });
    }

    private void rebuild() {
        clearWidgets();

        searchBox = new EditBox(font, leftPos + EditorUi.GAP, topPos + SEARCH_Y,
                GUI_W - EditorUi.GAP * 2 - MODE_W - 4, SEARCH_H, Component.empty());
        searchBox.setMaxLength(60);
        searchBox.setTextColor(EditorUi.PRIMARY);
        searchBox.setHint(Component.translatable("screen.infinitestats.deposit_filter.search_hint"));
        searchBox.setValue(searchText);
        searchBox.setResponder(v -> {
            searchText = v;
            scroll = 0;
            applyFilter();
        });
        addRenderableWidget(searchBox);

        modeButton = addRenderableWidget(Button.builder(modeLabel(), b -> toggleMode())
                .bounds(leftPos + GUI_W - EditorUi.GAP - MODE_W, topPos + SEARCH_Y, MODE_W, SEARCH_H)
                .build());

        addRenderableWidget(Button.builder(onlySelectedLabel(), b -> {
            onlySelected = !onlySelected;
            playClick();
            scroll = 0;
            applyFilter();
            b.setMessage(onlySelectedLabel());
        }).bounds(leftPos + EditorUi.GAP + 64, topPos + FOOTER_Y, 76, FOOTER_H).build());

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.infinitestats.deposit_filter.clear"),
                        b -> clearAll())
                .bounds(leftPos + EditorUi.GAP, topPos + FOOTER_Y, 60, FOOTER_H).build());

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.infinitestats.deposit_filter.add_held"),
                        b -> toggleHeldItem())
                .bounds(leftPos + GUI_W - EditorUi.GAP - 80 - 4 - 84, topPos + FOOTER_Y, 84, FOOTER_H).build());

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.infinitestats.deposit_filter.done"),
                        b -> onClose())
                .bounds(leftPos + GUI_W - EditorUi.GAP - 80, topPos + FOOTER_Y, 80, FOOTER_H).build());
    }

    private Component modeLabel() {
        return Component.translatable(useWhitelist
                ? "screen.infinitestats.deposit_filter.mode_whitelist"
                : "screen.infinitestats.deposit_filter.mode_blacklist");
    }

    private Component onlySelectedLabel() {
        return Component.translatable(onlySelected
                ? "screen.infinitestats.deposit_filter.only_selected_on"
                : "screen.infinitestats.deposit_filter.only_selected");
    }

    // ======================== 过滤与列表 ========================

    private void applyFilter() {
        String q = searchText.toLowerCase(Locale.ROOT).trim();
        filtered = new ArrayList<>();
        for (Candidate c : all) {
            if (onlySelected && !selectedIds.contains(c.id().toString())) continue;
            if (q.isEmpty()
                    || JechCompat.matches(c.display().toLowerCase(Locale.ROOT), q)
                    || c.id().toString().toLowerCase(Locale.ROOT).contains(q)) {
                filtered.add(c);
            }
        }
        maxScroll = Math.max(0, filtered.size() - VISIBLE_ROWS);
        scroll = clamp(scroll, 0, maxScroll);
    }

    private void toggleMode() {
        useWhitelist = !useWhitelist;
        playClick();
        if (modeButton != null) modeButton.setMessage(modeLabel());
        sendUpdate();
    }

    private void toggle(Candidate c) {
        String id = c.id().toString();
        if (selectedIds.contains(id)) selectedIds.remove(id);
        else selectedIds.add(id);
        playClick();
        if (onlySelected) applyFilter();
        sendUpdate();
    }

    /** 一键把主手物品加入 / 移出名单。 */
    private void toggleHeldItem() {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            setStatus(Component.translatable("screen.infinitestats.deposit_filter.held_empty").getString());
            return;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(held.getItem());
        if (id == null) return;
        String idStr = id.toString();
        Candidate target = null;
        for (Candidate c : all) {
            if (c.id().toString().equals(idStr)) {
                target = c;
                break;
            }
        }
        if (target == null) return;
        toggle(target);
        setStatus(Component.translatable(
                selectedIds.contains(idStr)
                        ? "screen.infinitestats.deposit_filter.held_added"
                        : "screen.infinitestats.deposit_filter.held_removed").getString());
    }

    private void clearAll() {
        if (selectedIds.isEmpty()) return;
        selectedIds.clear();
        playClick();
        applyFilter();
        sendUpdate();
    }

    /** 提交到服务端，并同步更新本地数据，让界面立即反映结果而不用等服务端回包。 */
    private void sendUpdate() {
        NetworkHandler.CHANNEL.sendToServer(
                new NetworkHandler.UpdateDepositFilterPacket(useWhitelist, new HashSet<>(selectedIds)));

        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats ->
                    stats.setAutoDepositFilterList(new HashSet<>(selectedIds), useWhitelist));
        }
    }

    private void setStatus(String msg) {
        statusMsg = msg;
        statusUntil = System.currentTimeMillis() + 2000;
    }

    // ======================== 绘制 ========================

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        EditorUi.panel(g, leftPos, topPos, GUI_W, GUI_H);
        EditorUi.title(g, font, leftPos, topPos, GUI_W, title.getString());

        // 说明行：左＝模式语义，右＝已选数量
        String hint = Component.translatable(useWhitelist
                ? "screen.infinitestats.deposit_filter.hint_whitelist"
                : "screen.infinitestats.deposit_filter.hint_blacklist").getString();
        g.drawString(font, hint, leftPos + EditorUi.GAP, topPos + HINT_Y, EditorUi.SECONDARY);
        String count = Component.translatable("screen.infinitestats.deposit_filter.selected_count",
                selectedIds.size()).getString();
        g.drawString(font, count, leftPos + GUI_W - EditorUi.GAP - font.width(count),
                topPos + HINT_Y, selectedIds.isEmpty() ? EditorUi.SECONDARY : EditorUi.GOLD);

        int listX = leftPos + EditorUi.GAP;
        int listW = GUI_W - EditorUi.GAP * 2 - EditorUi.SCROLL_W - 2;
        int ly = topPos + LIST_Y;
        EditorUi.dividerH(g, leftPos + 4, ly - 3, GUI_W - 8);

        rowRects.clear();
        hovered = null;
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int idx = i + scroll;
            if (idx >= filtered.size()) break;
            Candidate c = filtered.get(idx);
            int y = ly + i * EditorUi.ROW_H;
            int rowH = EditorUi.ROW_H - 2;
            boolean hover = mx >= listX && mx < listX + listW && my >= y && my < y + rowH;
            if (hover) hovered = c;

            boolean selected = selectedIds.contains(c.id().toString());
            int bg = selected ? EditorUi.BG_ROW_DRAG : hover ? EditorUi.BG_ROW_HOVER : EditorUi.BG_ROW;
            g.fill(listX, y, listX + listW, y + rowH, bg);
            if (selected) g.fill(listX, y, listX + 2, y + rowH, EditorUi.ACCENT);

            g.renderItem(new ItemStack(c.item()), listX + 4, y + 2);

            String idStr = c.id().toString();
            int idW = font.width(idStr);
            int nameMax = Math.max(40, listW - 26 - idW - 8);
            g.drawString(font, EditorUi.ellipsize(font, c.display(), nameMax),
                    listX + 24, y + 6, selected ? EditorUi.ACCENT : EditorUi.PRIMARY);
            g.drawString(font, idStr, listX + listW - idW - 6, y + 6, EditorUi.SECONDARY);

            rowRects.add(new int[]{listX, y, idx});
        }

        if (filtered.isEmpty()) {
            g.drawString(font,
                    Component.translatable("screen.infinitestats.deposit_filter.no_match").getString(),
                    listX + 6, ly + 6, EditorUi.SECONDARY);
        }

        EditorUi.scrollbar(g, mx, my, leftPos + GUI_W - EditorUi.GAP - EditorUi.SCROLL_W,
                ly, LIST_H - 2, scroll, maxScroll, VISIBLE_ROWS);

        EditorUi.dividerH(g, leftPos + 4, ly + LIST_H - 2, GUI_W - 8);

        super.render(g, mx, my, pt);

        if (System.currentTimeMillis() < statusUntil) {
            g.drawCenteredString(font, statusMsg, leftPos + GUI_W / 2, topPos + GUI_H - 10, EditorUi.GOLD);
        }
    }

    // ======================== 输入 ========================

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (int[] r : rowRects) {
                int listX = leftPos + EditorUi.GAP;
                int listW = GUI_W - EditorUi.GAP * 2 - EditorUi.SCROLL_W - 2;
                if (mx >= listX && mx < listX + listW
                        && my >= r[1] && my < r[1] + EditorUi.ROW_H - 2) {
                    toggle(filtered.get(r[2]));
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (maxScroll > 0) {
            scroll = clamp(scroll + (delta > 0 ? -1 : 1), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void playClick() {
        long now = System.currentTimeMillis();
        if (now - lastSoundTick < 50) return;
        lastSoundTick = now;
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
