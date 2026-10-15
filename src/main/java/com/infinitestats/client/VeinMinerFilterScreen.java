package com.infinitestats.client;

import com.infinitestats.stats.VeinMinerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 连锁挖掘的自定义名单编辑器（{@link VeinMinerConfig.MatchMode#FILTER_LIST} 模式用）。
 * <p>
 * 左边搜索框按「方块 id / 显示名」过滤，下面列表点一下即加入 / 移出当前名单；
 * 右上角可以切换<b>正在编辑哪一份名单</b>（白名单 / 黑名单）—— 两份名单各自独立保存，
 * 切换时会先把当前编辑结果写回对应那一份，再把另一份读出来。
 * <p>
 * 名单的提交交给上层「连锁设置」面板：本界面只改那份<b>本地副本</b>，
 * 点「完成」返回设置面板，由设置面板的「保存」统一发包。因此这里的改动不会即时生效，
 * 也就不会因为乱点而把服务端配置改坏。
 */
public class VeinMinerFilterScreen extends PanelScreen {

    private static final int GUI_W = 320;
    private static final int GUI_H = 300;

    private static final int SEARCH_Y = 26;
    private static final int SEARCH_H = 18;
    private static final int LIST_Y = SEARCH_Y + SEARCH_H + 6;
    private static final int LIST_H = GUI_H - LIST_Y - 34;
    private static final int ROW_H = 16;
    private static final int VISIBLE_ROWS = LIST_H / ROW_H;

    /** 名单里最多能放多少条（与服务端 {@code VeinMinerConfig#sanitize} 的上限一致）。 */
    private static final int MAX_ENTRIES = 256;

    private final VeinMinerConfig draft;
    private final Screen parent;

    private int leftPos;
    private int topPos;
    private int scroll;
    private int maxScroll;
    private int hovered = -1;

    /** 当前正在编辑的名单（白 / 黑由 {@link #useWhitelist} 决定）。 */
    private final Set<String> selected = new LinkedHashSet<>();
    private boolean useWhitelist;
    private String query = "";
    private List<String> filtered = List.of();

    private EditBox searchField;
    private Button sideButton;

    public VeinMinerFilterScreen(VeinMinerConfig draft, Screen parent) {
        super(Component.translatable("screen.infinitestats.vein_filter"));
        this.draft = draft;
        this.parent = parent;
        this.useWhitelist = draft.isUseWhitelist();
        this.selected.addAll(draft.getFilterList());
    }

    @Override
    protected int panelW() {
        return GUI_W;
    }

    @Override
    protected int panelH() {
        return GUI_H;
    }

    @Override
    protected void init() {
        super.init();
        // 面板坐标从 (0,0) 起：居中、缩放与拖动都由 PanelScreen 负责
        leftPos = 0;
        topPos = 0;

        searchField = new EditBox(font, leftPos + EditorUi.GAP, topPos + SEARCH_Y,
                GUI_W - EditorUi.GAP * 2 - 96, SEARCH_H, Component.empty());
        searchField.setMaxLength(48);
        searchField.setHint(Component.translatable("screen.infinitestats.vein_filter.search_hint"));
        searchField.setValue(query);
        searchField.setResponder(text -> {
            query = text;
            rebuildFiltered();
            scroll = 0;
        });
        addRenderableWidget(searchField);

        sideButton = addRenderableWidget(Button.builder(sideLabel(), b -> switchSide())
                .bounds(leftPos + GUI_W - EditorUi.GAP - 92, topPos + SEARCH_Y, 92, SEARCH_H).build());

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.infinitestats.vein_filter.clear"),
                        b -> {
                            selected.clear();
                            updateMaxScroll();
                        })
                .bounds(leftPos + EditorUi.GAP, topPos + GUI_H - 26, 80, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("screen.infinitestats.vein_filter.done"),
                        b -> apply())
                .bounds(leftPos + GUI_W / 2 - 40, topPos + GUI_H - 26, 80, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("screen.infinitestats.vein_filter.back"),
                        b -> onClose())
                .bounds(leftPos + GUI_W - EditorUi.GAP - 80, topPos + GUI_H - 26, 80, 20).build());

        rebuildFiltered();
    }

    private Component sideLabel() {
        return Component.translatable(useWhitelist
                ? "screen.infinitestats.vein_filter.side_white"
                : "screen.infinitestats.vein_filter.side_black");
    }

    /** 切白 / 黑名单：先写回当前那份，再读另一份。 */
    private void switchSide() {
        flush();
        useWhitelist = !useWhitelist;
        draft.setUseWhitelist(useWhitelist);
        selected.clear();
        selected.addAll(draft.getFilterList());
        sideButton.setMessage(sideLabel());
        scroll = 0;
        updateMaxScroll();
    }

    /** 把当前编辑结果写回「正在编辑的那一份名单」。 */
    private void flush() {
        draft.setUseWhitelist(useWhitelist);
        draft.setFilterList(selected);
    }

    /** 完成：写回后返回设置面板（真正的发包在设置面板点「保存」时）。 */
    private void apply() {
        flush();
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    // ======================== 数据 ========================

    /** 按搜索词过滤方块列表（只算一次，避免每帧遍历注册表里的上千个方块）。 */
    private void rebuildFiltered() {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (ResourceLocation id : BuiltInRegistries.BLOCK.keySet()) {
            if (id.getPath().equals("air")) continue;
            Block block = BuiltInRegistries.BLOCK.get(id);
            if (block == null || block == Blocks.AIR) continue;

            String idText = id.toString();
            if (!needle.isEmpty() && !idText.toLowerCase(Locale.ROOT).contains(needle)
                    && !displayName(block).toLowerCase(Locale.ROOT).contains(needle)) {
                continue;
            }
            out.add(idText);
        }
        out.sort(String::compareTo);
        filtered = out;
        updateMaxScroll();
    }

    private static String displayName(Block block) {
        try {
            return new ItemStack(block).getHoverName().getString();
        } catch (Throwable ignored) {
            return block.getName().getString();
        }
    }

    private void updateMaxScroll() {
        maxScroll = Math.max(0, filtered.size() - VISIBLE_ROWS);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
    }

    private void toggle(String id) {
        if (!selected.remove(id)) {
            if (selected.size() >= MAX_ENTRIES) return;
            selected.add(id);
        }
    }

    // ======================== 绘制 ========================

    @Override
    protected void renderPanel(GuiGraphics g, int mouseX, int mouseY, float partialTick) {

        EditorUi.panel(g, leftPos, topPos, GUI_W, GUI_H);
        EditorUi.title(g, font, leftPos, topPos, GUI_W, title.getString());

        String count = Component.translatable("screen.infinitestats.vein_filter.count",
                selected.size(), MAX_ENTRIES).getString();
        g.drawString(font, count, leftPos + GUI_W - EditorUi.GAP - font.width(count),
                topPos + SEARCH_Y + SEARCH_H + 1, EditorUi.SECONDARY);
        EditorUi.dividerH(g, leftPos + 4, topPos + LIST_Y - 4, GUI_W - 8);

        hovered = -1;
        int mx = (int) mouseX - leftPos;
        int my = (int) mouseY - topPos;
        int listX = leftPos + 4;
        int listW = GUI_W - 8 - EditorUi.SCROLL_W;

        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = scroll + row;
            if (index >= filtered.size()) break;
            String id = filtered.get(index);
            int y = topPos + LIST_Y + row * ROW_H;
            boolean hover = mx >= listX && mx < listX + listW && my >= y - topPos && my < y - topPos + ROW_H - 1;
            if (hover) hovered = index;

            boolean picked = selected.contains(id);
            g.fill(listX, y, listX + listW, y + ROW_H - 1,
                    picked ? EditorUi.BG_ROW_DRAG : hover ? EditorUi.BG_ROW_HOVER : EditorUi.BG_ROW);
            if (picked) {
                g.fill(listX, y, listX + 2, y + ROW_H - 1, EditorUi.ACCENT);
            }

            Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.tryParse(id));
            if (block != null && block != Blocks.AIR) {
                g.renderItem(new ItemStack(block), listX + 3, y);
            }
            g.drawString(font, EditorUi.ellipsize(font, id, listW - 24), listX + 22, y + 4,
                    picked ? EditorUi.ACCENT : EditorUi.PRIMARY);
        }

        EditorUi.scrollbar(g, mx, my, leftPos + GUI_W - 4 - EditorUi.SCROLL_W, topPos + LIST_Y,
                LIST_H, scroll, maxScroll, visRows());

        if (hovered >= 0) {
            List<String> lines = new ArrayList<>();
            String id = filtered.get(hovered);
            lines.add("§f" + id);
            Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.tryParse(id));
            if (block != null && block != Blocks.AIR) {
                lines.add("§7" + displayName(block));
            }
            lines.add(selected.contains(id)
                    ? "§b" + Component.translatable("screen.infinitestats.vein_filter.click_remove").getString()
                    : "§b" + Component.translatable("screen.infinitestats.vein_filter.click_add").getString());
            EditorUi.tooltip(g, mouseX, mouseY, lines, EditorUi.ACCENT);
        }

    }

    private int visRows() {
        return VISIBLE_ROWS;
    }

    @Override
    protected boolean onPanelClick(int mouseX, int mouseY, int button) {
        int mx = mouseX - leftPos;
        int my = mouseY - topPos;
        int listX = leftPos + 4;
        int listW = GUI_W - 8 - EditorUi.SCROLL_W;
        if (mx >= listX && mx < listX + listW && my >= LIST_Y && my < LIST_Y + LIST_H) {
            int index = scroll + (my - LIST_Y) / ROW_H;
            if (index >= 0 && index < filtered.size()) {
                toggle(filtered.get(index));
                return true;
            }
        }
        return false;   // 未命中时交回基类，继续派发给控件
    }

    @Override
    protected boolean onPanelScroll(int mouseX, int mouseY, double delta) {
        if (maxScroll > 0) {
            scroll = Math.max(0, Math.min(maxScroll, scroll + (delta > 0 ? -1 : 1)));
            return true;
        }
        return false;
    }

    /** 关闭界面时不要把输入框的焦点留在别处。 */
    @Override
    public void removed() {
        super.removed();
        if (searchField != null) searchField.setFocused(false);
    }
}
