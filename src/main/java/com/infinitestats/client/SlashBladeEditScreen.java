package com.infinitestats.client;

import com.infinitestats.compat.PinyinSearchBridge;
import com.infinitestats.compat.SlashBladeCompat;
import com.infinitestats.network.EditItemBladePacket;
import com.infinitestats.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/**
 * 拔刀剑编辑界面 — 在物品编辑器里改刀的成长数值与技能。
 * <p>
 * 从 {@link ItemEditorScreen} 的「拔刀剑」按钮进入（仅主手是拔刀剑时可点）。编辑项：
 * <ul>
 *   <li><b>杀敌数 / 耀魂数 / 锻造数</b>：顶部一行三个整数输入框；</li>
 *   <li><b>SA（特殊攻击）</b>：左栏单选列表，列出 {@code SlashArtsRegistry} 全部剑技，点击即选中；</li>
 *   <li><b>SE（特殊效果）</b>：右栏多选列表，列出 {@code SpecialEffectsRegistry} 全部效果，点击切换勾选。</li>
 * </ul>
 * 两栏各有独立搜索框（支持拼音）。当前值直接从物品 NBT（{@code bladeState}）读取，
 * 提交后由服务端走能力写回（见 {@link com.infinitestats.compat.SlashBladeCompat}）。
 * 视觉规范复用 {@link EditorUi}。
 */
public class SlashBladeEditScreen extends Screen {

    // ======================== 布局常量 ========================

    private static final int GUI_W = 420;
    private static final int ITEM_BAR_H = 34;
    private static final int NUM_Y = EditorUi.HEADER_H + ITEM_BAR_H + 4;   // 62
    private static final int NUM_H = 18;
    private static final int SECTION_Y = NUM_Y + NUM_H + 8;               // 88
    private static final int SEARCH_Y = SECTION_Y + 16;                   // 104
    private static final int SEARCH_H = 18;
    private static final int LIST_Y = SEARCH_Y + SEARCH_H + 4;            // 126

    private static final int VISIBLE_ROWS = 7;
    private static final int LIST_H = VISIBLE_ROWS * EditorUi.ROW_H;      // 154

    private static final int FOOTER_Y = LIST_Y + LIST_H + 6;              // 286
    private static final int FOOTER_H = 20;
    private static final int GUI_H = FOOTER_Y + FOOTER_H + EditorUi.GAP;  // 314

    private static final int COL_W = 198;
    private static final int COL_L = EditorUi.GAP;
    private static final int COL_R = COL_L + COL_W + EditorUi.GAP;        // 214
    private static final int LIST_W = COL_W - EditorUi.SCROLL_W - 2;      // 191

    // 顶部三个数值输入框的分组宽度
    private static final int GROUP_W = (GUI_W - EditorUi.GAP * 2) / 3;    // 134
    private static final int LABEL_W = 36;
    private static final int BOX_W = GROUP_W - LABEL_W - 4;               // 94

    // ======================== 状态 ========================

    private final ItemEditorScreen parent;
    private int leftPos, topPos;

    /** 候选（注册表全集）与过滤后的视图。 */
    private final List<ResourceLocation> saAll = new ArrayList<>();
    private final List<ResourceLocation> seAll = new ArrayList<>();
    private final List<ResourceLocation> saView = new ArrayList<>();
    private final List<ResourceLocation> seView = new ArrayList<>();
    private String saQuery = "", seQuery = "";
    private int saScroll, seScroll, saMaxScroll, seMaxScroll;

    /** 编辑中的值。 */
    private String saSel = "";
    private final Set<String> seSel = new LinkedHashSet<>();

    /** 打开界面时的基准值（用于判断哪些项真的被改过）。 */
    private int origKill, origSoul, origRefine;
    private String origSa = "";
    private final List<String> origSe = new ArrayList<>();

    private EditBox killBox, soulBox, refineBox, saFilter, seFilter;
    private final List<int[]> saRects = new ArrayList<>();
    private final List<int[]> seRects = new ArrayList<>();
    private ResourceLocation hoveredSa, hoveredSe;

    private String statusMsg = "";
    private long statusUntil;
    private long lastSoundTick;

    public SlashBladeEditScreen(ItemEditorScreen parent) {
        super(Component.translatable("screen.infinitestats.slashblade.title"));
        this.parent = parent;
        loadFromItem();
    }

    /** 打开时读取一次主手刀的当前值（与所选槽位一起固定下来）。 */
    private void loadFromItem() {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        ItemStack stack = player.getMainHandItem();

        origKill = SlashBladeCompat.killCount(stack);
        origSoul = SlashBladeCompat.proudSoul(stack);
        origRefine = SlashBladeCompat.refine(stack);
        origSa = nullToEmpty(SlashBladeCompat.slashArts(stack));
        origSe.clear();
        origSe.addAll(SlashBladeCompat.specialEffects(stack));

        saSel = origSa;
        seSel.clear();
        seSel.addAll(origSe);

        saAll.addAll(SlashBladeCompat.slashArtsOptions());
        seAll.addAll(SlashBladeCompat.specialEffectsOptions());
    }

    // ======================== 初始化 ========================

    @Override
    protected void init() {
        super.init();
        leftPos = Math.max(0, (width - GUI_W) / 2);
        topPos = Math.max(0, (height - GUI_H) / 2);
        rebuildSaView();
        rebuildSeView();

        killBox = numericBox(0, Integer.toString(origKill), v -> {});
        soulBox = numericBox(1, Integer.toString(origSoul), v -> {});
        refineBox = numericBox(2, Integer.toString(origRefine), v -> {});

        saFilter = filterBox(colX(0), saQuery, v -> { saQuery = v; rebuildSaView(); });
        seFilter = filterBox(colX(1), seQuery, v -> { seQuery = v; rebuildSeView(); });

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.infinitestats.item_editor.back"), b -> onClose())
                .bounds(leftPos + EditorUi.GAP, topPos + FOOTER_Y, 100, FOOTER_H).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("screen.infinitestats.item_editor.apply"), b -> apply())
                .bounds(leftPos + GUI_W - EditorUi.GAP - 100, topPos + FOOTER_Y, 100, FOOTER_H).build());
    }

    private EditBox numericBox(int index, String value, java.util.function.Consumer<String> responder) {
        EditBox box = new EditBox(font, numX(index) + LABEL_W, topPos + NUM_Y, BOX_W, NUM_H, Component.empty());
        box.setMaxLength(10);
        box.setTextColor(EditorUi.PRIMARY);
        box.setValue(value);
        box.setResponder(responder);
        addRenderableWidget(box);
        return box;
    }

    private EditBox filterBox(int x, String value, java.util.function.Consumer<String> responder) {
        EditBox box = new EditBox(font, x, topPos + SEARCH_Y, COL_W, SEARCH_H, Component.empty());
        box.setMaxLength(48);
        box.setTextColor(EditorUi.PRIMARY);
        box.setHint(Component.translatable("screen.infinitestats.item_editor.filter_hint"));
        box.setValue(value);
        box.setResponder(responder);
        addRenderableWidget(box);
        return box;
    }

    // ======================== 过滤 / 滚动 ========================

    private void rebuildSaView() {
        saView.clear();
        String q = saQuery.toLowerCase(Locale.ROOT).trim();
        for (ResourceLocation rl : saAll) {
            if (q.isEmpty() || PinyinSearchBridge.matches(matchText(rl, true), q)) saView.add(rl);
        }
        saMaxScroll = Math.max(0, saView.size() - VISIBLE_ROWS);
        saScroll = clamp(saScroll, 0, saMaxScroll);
    }

    private void rebuildSeView() {
        seView.clear();
        String q = seQuery.toLowerCase(Locale.ROOT).trim();
        for (ResourceLocation rl : seAll) {
            if (q.isEmpty() || PinyinSearchBridge.matches(matchText(rl, false), q)) seView.add(rl);
        }
        seMaxScroll = Math.max(0, seView.size() - VISIBLE_ROWS);
        seScroll = clamp(seScroll, 0, seMaxScroll);
    }

    private String matchText(ResourceLocation rl, boolean sa) {
        String name = sa ? SlashBladeCompat.slashArtsName(rl) : SlashBladeCompat.specialEffectsName(rl);
        return (name + " " + rl).toLowerCase(Locale.ROOT);
    }

    // ======================== 绘制 ========================

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        EditorUi.panel(g, leftPos, topPos, GUI_W, GUI_H);
        EditorUi.title(g, font, leftPos, topPos, GUI_W, title.getString());
        drawItemBar(g);

        EditorUi.dividerH(g, leftPos + 4, topPos + NUM_Y - 4, GUI_W - 8);

        // 顶部三个数值标签（输入框是控件，由 super.render 绘制）
        drawNumLabel(g, 0, "screen.infinitestats.slashblade.kill");
        drawNumLabel(g, 1, "screen.infinitestats.slashblade.soul");
        drawNumLabel(g, 2, "screen.infinitestats.slashblade.refine");

        // 中缝
        EditorUi.dividerV(g, leftPos + COL_L + COL_W + 4, topPos + SECTION_Y - 2,
                GUI_H - SECTION_Y);

        EditorUi.section(g, font, colX(0), topPos + SECTION_Y,
                Component.translatable("screen.infinitestats.slashblade.sa").getString());
        EditorUi.section(g, font, colX(1), topPos + SECTION_Y,
                Component.translatable("screen.infinitestats.slashblade.se").getString());
        drawCount(g, 0, saView.size(), saAll.size());
        drawCount(g, 1, seView.size(), seAll.size());

        saRects.clear();
        seRects.clear();
        hoveredSa = null;
        hoveredSe = null;
        drawSaColumn(g, mx, my);
        drawSeColumn(g, mx, my);

        super.render(g, mx, my, pt);

        if (hoveredSa != null) {
            EditorUi.tooltip(g, mx, my, List.of(
                    "§e" + SlashBladeCompat.slashArtsName(hoveredSa),
                    "§7" + hoveredSa), EditorUi.INFO);
        } else if (hoveredSe != null) {
            EditorUi.tooltip(g, mx, my, List.of(
                    "§e" + SlashBladeCompat.specialEffectsName(hoveredSe),
                    "§7" + hoveredSe), EditorUi.INFO);
        }

        if (System.currentTimeMillis() < statusUntil) {
            g.drawCenteredString(font, statusMsg, leftPos + GUI_W / 2, topPos + GUI_H - 8, EditorUi.DANGER);
        }
    }

    private void drawNumLabel(GuiGraphics g, int index, String key) {
        g.drawString(font, Component.translatable(key).getString(),
                numX(index), topPos + NUM_Y + 5, EditorUi.GOLD);
    }

    private void drawCount(GuiGraphics g, int col, int shown, int total) {
        String count = shown == total ? "(" + total + ")" : shown + "/" + total;
        g.drawString(font, count, colX(col) + COL_W - font.width(count), topPos + SECTION_Y,
                shown == total ? EditorUi.SECONDARY : EditorUi.INFO);
    }

    /** SA 单选列表。 */
    private void drawSaColumn(GuiGraphics g, int mx, int my) {
        int cx = colX(0);
        int ly = topPos + LIST_Y;
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int vi = row + saScroll;
            if (vi >= saView.size()) break;
            ResourceLocation rl = saView.get(vi);
            int ry = ly + row * EditorUi.ROW_H;
            boolean hover = mx >= cx && mx < cx + LIST_W
                    && my >= ry && my < ry + EditorUi.ROW_H - 2;
            if (hover) hoveredSa = rl;
            boolean selected = rl.toString().equals(saSel);

            int bg = selected ? EditorUi.BG_ROW_DRAG : hover ? EditorUi.BG_ROW_HOVER : EditorUi.BG_ROW;
            g.fill(cx, ry, cx + LIST_W, ry + EditorUi.ROW_H - 2, bg);
            if (selected) g.fill(cx, ry, cx + 2, ry + EditorUi.ROW_H - 2, EditorUi.ACCENT);

            String id = rl.toString();
            int idW = font.width(id);
            int nameMax = Math.max(40, LIST_W - 12 - idW - 6);
            g.drawString(font, EditorUi.ellipsize(font, SlashBladeCompat.slashArtsName(rl), nameMax),
                    cx + 5, ry + 6, selected ? EditorUi.ACCENT : EditorUi.PRIMARY);
            g.drawString(font, id, cx + LIST_W - idW - 4, ry + 6, EditorUi.SECONDARY);

            saRects.add(new int[]{cx, ry, vi});
        }
        if (saView.isEmpty()) {
            g.drawString(font, emptyHint(saQuery), cx + 5, ly + 6, EditorUi.SECONDARY);
        }
        EditorUi.scrollbar(g, mx, my, cx + COL_W - EditorUi.SCROLL_W,
                ly, LIST_H - 2, saScroll, saMaxScroll, VISIBLE_ROWS);
    }

    /** SE 多选列表。 */
    private void drawSeColumn(GuiGraphics g, int mx, int my) {
        int cx = colX(1);
        int ly = topPos + LIST_Y;
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int vi = row + seScroll;
            if (vi >= seView.size()) break;
            ResourceLocation rl = seView.get(vi);
            int ry = ly + row * EditorUi.ROW_H;
            boolean hover = mx >= cx && mx < cx + LIST_W
                    && my >= ry && my < ry + EditorUi.ROW_H - 2;
            if (hover) hoveredSe = rl;
            boolean selected = seSel.contains(rl.toString());

            int bg = selected ? EditorUi.BG_ROW_DRAG : hover ? EditorUi.BG_ROW_HOVER : EditorUi.BG_ROW;
            g.fill(cx, ry, cx + LIST_W, ry + EditorUi.ROW_H - 2, bg);

            // 勾选框
            int bx = cx + 4;
            int by = ry + (EditorUi.ROW_H - 2 - 10) / 2;
            g.fill(bx, by, bx + 10, by + 10, selected ? EditorUi.ACCENT : EditorUi.BG_SB_TRACK);
            if (selected) g.drawString(font, "✓", bx + 1, by + 1, 0xFF0F1A12);

            String id = rl.toString();
            int idW = font.width(id);
            int nameMax = Math.max(40, LIST_W - 30 - idW - 6);
            g.drawString(font, EditorUi.ellipsize(font, SlashBladeCompat.specialEffectsName(rl), nameMax),
                    cx + 18, ry + 6, selected ? EditorUi.ACCENT : EditorUi.PRIMARY);
            g.drawString(font, id, cx + LIST_W - idW - 4, ry + 6, EditorUi.SECONDARY);

            seRects.add(new int[]{cx, ry, vi});
        }
        if (seView.isEmpty()) {
            g.drawString(font, emptyHint(seQuery), cx + 5, ly + 6, EditorUi.SECONDARY);
        }
        EditorUi.scrollbar(g, mx, my, cx + COL_W - EditorUi.SCROLL_W,
                ly, LIST_H - 2, seScroll, seMaxScroll, VISIBLE_ROWS);
    }

    private String emptyHint(String query) {
        return Component.translatable(query.isBlank()
                ? "screen.infinitestats.item_editor.none"
                : "screen.infinitestats.item_editor.no_match").getString();
    }

    /** 顶部物品预览条。 */
    private void drawItemBar(GuiGraphics g) {
        int x = leftPos, y = topPos + EditorUi.HEADER_H;
        g.fill(x, y, x + GUI_W, y + ITEM_BAR_H, EditorUi.BG_ITEM_BAR);

        var player = Minecraft.getInstance().player;
        ItemStack stack = player == null ? ItemStack.EMPTY : player.getMainHandItem();
        if (stack.isEmpty()) {
            g.drawString(font, Component.translatable("screen.infinitestats.item_editor.empty").getString(),
                    x + EditorUi.GAP, y + (ITEM_BAR_H - font.lineHeight) / 2, EditorUi.DANGER);
            return;
        }

        int ix = x + EditorUi.GAP;
        int iy = y + (ITEM_BAR_H - 16) / 2;
        g.renderItem(stack, ix, iy);
        if (stack.getCount() > 1) {
            g.drawString(font, "x" + stack.getCount(), ix + 17, iy + 9, EditorUi.GOLD);
        }

        int tx = ix + 24;
        g.drawString(font, EditorUi.ellipsize(font, stack.getHoverName().getString(), 150),
                tx, y + 5, EditorUi.PRIMARY);

        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        String idStr = id == null ? "" : id.toString();
        g.drawString(font, EditorUi.ellipsize(font, idStr, 150),
                x + GUI_W - EditorUi.GAP - Math.min(150, font.width(idStr)), y + 5, EditorUi.SECONDARY);
    }

    // ======================== 输入 ========================

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            int saIdx = rowIndexAt(saRects, mx, my);
            if (saIdx >= 0) {
                saSel = saView.get(saIdx).toString();
                playClick();
                return true;
            }
            int seIdx = rowIndexAt(seRects, mx, my);
            if (seIdx >= 0) {
                String id = seView.get(seIdx).toString();
                if (!seSel.remove(id)) seSel.add(id);
                playClick();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    /** 命中列表行则返回视图下标，否则 -1。 */
    private int rowIndexAt(List<int[]> rects, double mx, double my) {
        for (int[] r : rects) {
            if (mx >= r[0] && mx < r[0] + LIST_W && my >= r[1] && my < r[1] + EditorUi.ROW_H - 2) {
                return r[2];
            }
        }
        return -1;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int col = colAt(mx);
        if (col == 0) {
            saScroll = clamp(saScroll + (delta > 0 ? -1 : 1), 0, saMaxScroll);
            return true;
        }
        if (col == 1) {
            seScroll = clamp(seScroll + (delta > 0 ? -1 : 1), 0, seMaxScroll);
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

    private int colAt(double mx) {
        for (int col = 0; col < 2; col++) {
            int x = colX(col);
            if (mx >= x && mx < x + COL_W) return col;
        }
        return -1;
    }

    // ======================== 提交 ========================

    private void apply() {
        var player = Minecraft.getInstance().player;
        if (player == null || player.getMainHandItem().isEmpty()) {
            setStatus(Component.translatable("screen.infinitestats.item_editor.empty").getString());
            return;
        }
        if (!SlashBladeCompat.isBlade(player.getMainHandItem())) {
            setStatus(Component.translatable("screen.infinitestats.slashblade.not_blade").getString());
            return;
        }

        int k, s, r;
        try {
            k = Math.max(0, Integer.parseInt(killBox.getValue().trim()));
            s = Math.max(0, Integer.parseInt(soulBox.getValue().trim()));
            r = Math.max(0, Integer.parseInt(refineBox.getValue().trim()));
        } catch (NumberFormatException e) {
            setStatus(Component.translatable("screen.infinitestats.item_editor.invalid_value").getString());
            return;
        }

        Integer kill = k == origKill ? null : k;
        Integer soul = s == origSoul ? null : s;
        Integer refine = r == origRefine ? null : r;
        String sa = (!saSel.isEmpty() && !saSel.equals(origSa)) ? saSel : null;
        List<String> se = seSel.equals(new HashSet<>(origSe)) ? null : new ArrayList<>(seSel);

        if (kill == null && soul == null && refine == null && sa == null && se == null) {
            setStatus(Component.translatable("screen.infinitestats.item_editor.no_changes").getString());
            return;
        }

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(player.getMainHandItem().getItem());
        NetworkHandler.CHANNEL.sendToServer(new EditItemBladePacket(
                player.getInventory().selected, itemId, kill, soul, refine, sa, se));

        // 更新基准，便于连续微调而不会每次都重发全部字段
        origKill = k;
        origSoul = s;
        origRefine = r;
        if (sa != null) origSa = sa;
        if (se != null) {
            origSe.clear();
            origSe.addAll(se);
        }
        setStatus(Component.translatable("screen.infinitestats.item_editor.applied").getString());
    }

    // ======================== 工具 ========================

    private int numX(int index) {
        return leftPos + EditorUi.GAP + index * GROUP_W;
    }

    private int colX(int col) {
        return leftPos + (col == 0 ? COL_L : COL_R);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private void setStatus(String msg) {
        statusMsg = msg;
        statusUntil = System.currentTimeMillis() + 2500;
    }

    private void playClick() {
        long now = System.currentTimeMillis();
        if (now - lastSoundTick < 50) return;
        lastSoundTick = now;
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
