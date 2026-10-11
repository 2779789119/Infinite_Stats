package com.infinitestats.client;

import com.infinitestats.compat.PinyinSearchBridge;
import com.infinitestats.network.NetworkHandler;
import com.infinitestats.network.UpdateVeinMinerConfigPacket;
import com.infinitestats.stats.PlayerStatsProvider;
import com.infinitestats.stats.VeinMinerConfig;
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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 「连锁挖掘 · 自定义名单」界面 —— 用白名单 / 黑名单限定哪些方块能互相连锁。
 * <p>
 * 交互与 {@link AutoDepositFilterScreen} 保持一致（同一个 {@link EditorUi} 规范）：
 * 搜索框支持方块名 / 方块 ID（装了 pinyin_search 时按拼音匹配）、点击行即切换在不在名单里、
 * 底栏可一键「手持加入」与「只看已选」。
 * <p>
 * 只列出「有对应物品形态」的方块：图标与译名都靠物品，纯技术方块（没有 BlockItem）列出来也没法辨认。
 * 语义与自动入库一致：名单为空时不做限制（避免清空后连锁整体失效）。
 */
public class VeinMinerFilterScreen extends Screen {

    // ======================== 布局常量 ========================

    private static final int GUI_W = 420;
    private static final int GUI_H = 272;

    private static final int SEARCH_Y = EditorUi.HEADER_H + 4;
    private static final int SEARCH_H = 18;
    private static final int HINT_Y = SEARCH_Y + SEARCH_H + 2;
    private static final int LIST_Y = HINT_Y + EditorUi.ROW_H - 8;
    private static final int VISIBLE_ROWS = 8;
    private static final int LIST_H = VISIBLE_ROWS * EditorUi.ROW_H;

    private static final int FOOTER_Y = LIST_Y + LIST_H + 8;
    private static final int FOOTER_H = 20;

    private static final int MODE_W = 118;

    // ======================== 状态 ========================

    /** 候选方块（有物品形态的方块，按名称排序） */
    private record Candidate(ResourceLocation id, String display, Item item) {}

    private final List<Candidate> all = new ArrayList<>();
    private List<Candidate> filtered = new ArrayList<>();

    /** 当前名单（方块 ID 或 #标签），本地立即生效，同时提交给服务端 */
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

    private final Screen parent;

    public VeinMinerFilterScreen(Screen parent) {
        super(Component.translatable("screen.infinitestats.vein_filter"));
        this.parent = parent;
        buildCandidates();
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    /** 构建候选方块：只收「有 BlockItem」的方块，名称翻译优先、缺失时回退到可读 ID。 */
    private void buildCandidates() {
        for (Block block : ForgeRegistries.BLOCKS) {
            Item item = block.asItem();
            if (item == Items.AIR) continue;
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
            if (id == null) continue;
            all.add(new Candidate(id, displayName(block, item, id), item));
        }
        all.sort(Comparator.comparing(Candidate::display, String.CASE_INSENSITIVE_ORDER));
    }

    private static String displayName(Block block, Item item, ResourceLocation id) {
        String translated = block.getName().getString();
        if (!translated.isEmpty() && !translated.startsWith("block.")) return translated;
        String key = item.getDescriptionId();
        String itemName = Component.translatable(key).getString();
        if (!itemName.equals(key)) return itemName;
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
            selectedIds.addAll(stats.getVeinMinerConfig().getFilterList());
            useWhitelist = stats.getVeinMinerConfig().isUseWhitelist();
        });
    }

    private void rebuild() {
        clearWidgets();

        searchBox = new EditBox(font, leftPos + EditorUi.GAP, topPos + SEARCH_Y,
                GUI_W - EditorUi.GAP * 2 - MODE_W - 4, SEARCH_H, Component.empty());
        searchBox.setMaxLength(60);
        searchBox.setTextColor(EditorUi.PRIMARY);
        searchBox.setHint(Component.translatable("screen.infinitestats.vein_filter.search_hint"));
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
                        b -> toggleHeldBlock())
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
                    || PinyinSearchBridge.matches(c.display().toLowerCase(Locale.ROOT), q)
                    || c.id().toString().toLowerCase(Locale.ROOT).contains(q)) {
                filtered.add(c);
            }
        }
        maxScroll = Math.max(0, filtered.size() - VISIBLE_ROWS);
        scroll = clamp(scroll, 0, maxScroll);
    }

    private void toggleMode() {
        useWhitelist = !useWhitelist;
        // 黑白名单各自独立：切到新模式后显示该模式自己的名单
        reloadSelection();
        playClick();
        if (modeButton != null) modeButton.setMessage(modeLabel());
        sendUpdate();
    }

    /** 按当前模式重新读取名单（切换模式后调用）。 */
    private void reloadSelection() {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
                VeinMinerConfig cfg = stats.getVeinMinerConfig();
                cfg.setUseWhitelist(useWhitelist);
                selectedIds.clear();
                selectedIds.addAll(cfg.getFilterList());
            });
        }
        applyFilter();
    }

    private void toggle(Candidate c) {
        String id = c.id().toString();
        if (selectedIds.contains(id)) selectedIds.remove(id);
        else selectedIds.add(id);
        playClick();
        if (onlySelected) applyFilter();
        sendUpdate();
    }

    /** 一键把手持方块加入 / 移出名单。 */
    private void toggleHeldBlock() {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty() || !(held.getItem() instanceof BlockItem blockItem)) {
            setStatus(Component.translatable("screen.infinitestats.vein_filter.held_empty").getString());
            return;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(blockItem.getBlock());
        if (id == null) return;
        String idStr = id.toString();
        Candidate target = null;
        for (Candidate c : all) {
            if (c.id().toString().equals(idStr)) {
                target = c;
                break;
            }
        }
        if (target == null) {
            setStatus(Component.translatable("screen.infinitestats.vein_filter.held_empty").getString());
            return;
        }
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

    /** 提交整份配置（名单只是其中两项），并同步本地数据让界面立即反映结果。 */
    private void sendUpdate() {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
            VeinMinerConfig cfg = stats.getVeinMinerConfig().copy();
            cfg.setFilterList(new HashSet<>(selectedIds));
            cfg.setUseWhitelist(useWhitelist);
            NetworkHandler.CHANNEL.sendToServer(new UpdateVeinMinerConfigPacket(cfg));
            stats.setVeinMinerConfig(cfg.copy());
        });
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
