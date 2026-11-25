package com.infinitestats.client;

import com.infinitestats.network.NetworkHandler;
import com.infinitestats.stats.PlayerStatsProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * 跨维度传送面板
 * 以可滚动列表展示服务器下发的全部维度，点击目标维度按钮即可一键传送。
 * 列表由服务器动态下发，因此兼容暮色森林 / Alex's Caves / 以太 等模组维度。
 * 需要『跨维度传送』属性已激活（与普通命令一致）。
 */
public class CrossDimScreen extends PanelScreen {

    private static final int GUI_W = 300;
    private static final int GUI_H = 240;

    @Override
    protected int panelW() {
        return GUI_W;
    }

    @Override
    protected int panelH() {
        return GUI_H;
    }

    private static final int BG_PANEL = 0xE81A1A2E;
    private static final int TEXT_GOLD = 0xFFFFD166;
    private static final int TEXT_NEGATIVE = 0xFFF87171;
    private static final int TEXT_ACTIVE = 0xFF4ADE80;

    // 列表区域（搜索框下方到面板底部之间）
    private static final int SEARCH_Y = 54;
    private static final int SEARCH_H = 18;
    private static final int LIST_TOP_OFFSET = 78;
    private static final int LIST_BOTTOM_OFFSET = 10;

    // 服务器下发的维度列表（完整注册名，如 minecraft:overworld / twilightforest:twilight_forest）
    private static final List<String> SERVER_DIMENSIONS = new ArrayList<>(List.of(
            "minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"));

    // leftPos / topPos 由 PanelScreen 提供（恒为 0：面板坐标从 (0,0) 起）
    private boolean toggleActive;
    private String currentDim = "";
    private long lastSoundTick;

    /** 跨维度传送需要二次点击确认（第一次点只把按钮变成「确认？」）。 */
    private final ConfirmAction confirm = new ConfirmAction();

    // 滚动状态
    private final List<Button> dimButtons = new ArrayList<>();
    /** 当前显示的维度（已按搜索词过滤），与 {@link #dimButtons} 一一对应。 */
    private List<String> displayed = new ArrayList<>();
    private net.minecraft.client.gui.components.EditBox searchBox;
    private String searchText = "";
    /** 当前悬停的维度（给原始注册名提示用）。 */
    private String hoveredDim;
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private int contentHeight = 0;
    private static final int BTN_H = 26;
    private static final int GAP = 6;

    public CrossDimScreen() {
        super(Component.translatable("screen.infinitestats.crossdim"));
    }

    /** 由服务器列表数据包回填，并刷新当前打开的面板。 */
    public static void setServerDimensions(List<String> dims) {
        SERVER_DIMENSIONS.clear();
        SERVER_DIMENSIONS.addAll(dims);
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof CrossDimScreen cs) {
            cs.rebuildList();
        }
    }

    /** 原版与少数有固定译名的维度的中文名。 */
    private static final java.util.Map<String, String> KNOWN_DIMENSION_NAMES = java.util.Map.of(
            "minecraft:overworld", "主世界",
            "minecraft:the_nether", "下界",
            "minecraft:the_end", "末地",
            "twilightforest:twilight_forest", "暮色森林",
            "aether:the_aether", "天境");

    /**
     * 维度显示名。
     * <p>
     * 原版<b>没有</b>「维度名」这一说：{@code Dimension} 是代码注册的，既没有 display 字段，
     * 也不像生物群系那样带本地化键，所以拿不到「官方中文名」，只能三层兜底：
     * <ol>
     *   <li>内置的常见维度表；</li>
     *   <li>语言文件里的 {@code gui.infinitestats.dimension.&lt;命名空间&gt;.&lt;路径&gt;}
     *       —— 整合包作者想给某个模组维度起中文名，在资源包里加这一条就行；</li>
     *   <li>把路径段转成可读形式（{@code twilight_forest} → {@code Twilight Forest}）。</li>
     * </ol>
     * 原始注册名始终保留在悬停提示与搜索匹配里，不会认错。
     */
    private static String displayName(String rl) {
        String known = KNOWN_DIMENSION_NAMES.get(rl);
        if (known != null) return known;

        int colon = rl.indexOf(':');
        String ns = colon >= 0 ? rl.substring(0, colon) : "minecraft";
        String path = colon >= 0 ? rl.substring(colon + 1) : rl;

        String key = "gui.infinitestats.dimension." + ns + "." + path;
        String localized = net.minecraft.locale.Language.getInstance().getOrDefault(key, "");
        if (localized != null && !localized.isEmpty() && !localized.equals(key)) {
            return localized;
        }

        return readable(path);
    }

    /** {@code twilight_forest} → {@code Twilight Forest}。 */
    private static String readable(String path) {
        String text = path.replace('_', ' ').replace('/', ' ');
        StringBuilder sb = new StringBuilder(text.length());
        boolean upper = true;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ') {
                sb.append(c);
                upper = true;
            } else if (upper) {
                sb.append(Character.toUpperCase(c));
                upper = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    @Override
    protected void init() {
        super.init();
        // 面板坐标从 (0,0) 起：居中、缩放与拖动都由 PanelScreen 负责
        leftPos = 0;
        topPos = 0;

        Player player = Minecraft.getInstance().player;
        toggleActive = false;
        currentDim = "";
        if (player != null) {
            currentDim = player.level().dimension().location().toString();
            PlayerStatsProvider.of(player).ifPresent(stats -> {
                toggleActive = stats.isToggleActive("cross_dimension_teleport");
            });
        }

        // 搜索框：模组维度一多就不用一路滚到底
        searchText = "";
        searchBox = new net.minecraft.client.gui.components.EditBox(font,
                leftPos + 20, topPos + SEARCH_Y, GUI_W - 40, SEARCH_H, Component.empty());
        searchBox.setMaxLength(48);
        searchBox.setHint(Component.translatable("gui.infinitestats.crossdim_search_hint"));
        searchBox.setResponder(v -> {
            searchText = v;
            scrollOffset = 0;
            rebuildList();
        });
        addRenderableWidget(searchBox);

        // 向服务器请求完整的维度列表（含模组维度）
        NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.CrossDimListRequestPacket());

        rebuildList();
    }

    /** 按搜索词过滤维度（匹配显示名、注册名）。 */
    private List<String> filteredDimensions() {
        String q = searchText.toLowerCase(java.util.Locale.ROOT).trim();
        if (q.isEmpty()) return SERVER_DIMENSIONS;

        List<String> out = new ArrayList<>();
        for (String key : SERVER_DIMENSIONS) {
            if (key.toLowerCase(java.util.Locale.ROOT).contains(q)
                    || displayName(key).toLowerCase(java.util.Locale.ROOT).contains(q)) {
                out.add(key);
            }
        }
        return out;
    }

    /** 重建维度按钮列表，并重新计算滚动范围。 */
    private void rebuildList() {
        // 只清维度按钮：搜索框要留着，否则每敲一个字就丢焦点
        for (Button b : dimButtons) removeWidget(b);
        dimButtons.clear();
        confirm.reset();

        displayed = filteredDimensions();

        int btnW = GUI_W - 40;
        int listHeight = GUI_H - LIST_TOP_OFFSET - LIST_BOTTOM_OFFSET;
        int startY = topPos + LIST_TOP_OFFSET;

        int i = 0;
        for (String dimKey : displayed) {
            final String key = dimKey;
            boolean isCurrent = currentDim.equals(key);
            int by = startY + i * (BTN_H + GAP);
            Component label = isCurrent
                    ? Component.translatable("gui.infinitestats.crossdim_current", displayName(key))
                    : Component.literal(displayName(key));
            Button btn = Button.builder(label, b -> requestTeleport(key))
                    .bounds(leftPos + 20, by, btnW, BTN_H)
                    .build();
            btn.active = toggleActive && !isCurrent;
            addRenderableWidget(btn);
            dimButtons.add(btn);
            i++;
        }

        contentHeight = displayed.size() * (BTN_H + GAP);
        maxScroll = Math.max(0, contentHeight - listHeight);
        clampScroll();
    }

    private void clampScroll() {
        if (scrollOffset < 0) scrollOffset = 0;
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
    }

    /** 传送前要再点一次：第一下只把按钮变成「确认？」，避免手滑直接换维度。 */
    private void requestTeleport(String dimKey) {
        playClickSound();
        if (!confirm.confirm("dim:" + dimKey)) {
            return;
        }
        NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.CrossDimRequestPacket(dimKey));
        onClose();
    }

    private void playClickSound() {
        if (minecraft == null) return;
        long now = System.currentTimeMillis();
        if (now - lastSoundTick < 50) return;
        lastSoundTick = now;
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    /** 滚轮：坐标为面板坐标，{@code delta} 是垂直滚轮量（PanelScreen 已取出第 4 个参数）。 */
    @Override
    protected boolean onPanelScroll(int mouseX, int mouseY, double delta) {
        if (maxScroll > 0) {
            scrollOffset += (int) (-delta * (BTN_H + GAP));
            clampScroll();
            return true;
        }
        return false;
    }

    @Override
    protected boolean baseRendersWidgets() {
        // 控件在下面的裁剪区里自己遍历绘制，不能交给基类在裁剪区外再画一遍
        return false;
    }

    // ======================== 滚动条拖动 ========================

    @Override
    protected boolean onPanelClick(int mouseX, int mouseY, int button) {
        if (button != 0) return false;
        return beginScrollbarDrag(mouseX, mouseY, scrollbarX(), scrollTrackTop(), scrollTrackH(),
                scrollOffset, maxScroll, visibleRows(scrollTrackH()),
                v -> scrollOffset = Math.max(0, Math.min(maxScroll, v)));
    }

    @Override
    protected boolean onPanelDrag(int mouseX, int mouseY, int button, int dragX, int dragY) {
        return button == 0 && updateScrollbarDrag(mouseY);
    }

    @Override
    protected boolean onPanelRelease(int mouseX, int mouseY, int button) {
        return button == 0 && endScrollbarDrag();
    }

    @Override
    protected void renderPanel(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 面板
        graphics.fill(leftPos - 2, topPos - 2, leftPos + GUI_W + 2, topPos + GUI_H + 2, 0x403B82F6);
        graphics.fill(leftPos - 1, topPos - 1, leftPos + GUI_W + 1, topPos + GUI_H + 1, 0xFF1E293B);
        graphics.fill(leftPos, topPos, leftPos + GUI_W, topPos + GUI_H, BG_PANEL);

        // 标题
        graphics.drawCenteredString(font, Component.translatable("screen.infinitestats.crossdim").getString(),
                leftPos + GUI_W / 2, topPos + 12, TEXT_GOLD);

        // 状态提示
        int toggleColor = toggleActive ? TEXT_ACTIVE : TEXT_NEGATIVE;
        Component toggleText = toggleActive
                ? Component.translatable("gui.infinitestats.crossdim_on")
                : Component.translatable("gui.infinitestats.crossdim_off");
        graphics.drawCenteredString(font, toggleText.getString(),
                leftPos + GUI_W / 2, topPos + 40, toggleColor);

        // 裁剪列表区域后绘制按钮
        int listTop = topPos + LIST_TOP_OFFSET;
        int listBottom = topPos + GUI_H - LIST_BOTTOM_OFFSET;
        // 裁剪区按「面板坐标」开：基类负责换算成屏幕坐标（缩放后依然裁对位置）
        beginPanelScissor(graphics, leftPos, listTop, leftPos + GUI_W, listBottom);
        hoveredDim = null;
        for (int i = 0; i < dimButtons.size(); i++) {
            Button btn = dimButtons.get(i);
            int by = listTop + i * (BTN_H + GAP) - scrollOffset;
            btn.setY(by);
            btn.visible = by + BTN_H > listTop && by < listBottom;

            // 二次确认态：被点过一次的那个按钮显示「确认？」
            if (i < displayed.size()) {
                String key = displayed.get(i);
                boolean isCurrent = currentDim.equals(key);
                boolean confirming = confirm.isConfirming("dim:" + key);
                btn.setMessage(confirming
                        ? Component.translatable("screen.infinitestats.ui.confirm")
                        : isCurrent
                                ? Component.translatable("gui.infinitestats.crossdim_current", displayName(key))
                                : Component.literal(displayName(key)));

                if (btn.visible && mouseX >= btn.getX() && mouseX < btn.getX() + btn.getWidth()
                        && mouseY >= btn.getY() && mouseY < btn.getY() + btn.getHeight()) {
                    hoveredDim = key;
                }
            }
            btn.render(graphics, mouseX, mouseY, partialTick);
        }
        endPanelScissor(graphics);

        // 搜索框在裁剪区之上，必须单独画（否则会被列表的裁剪裁掉）
        if (searchBox != null) searchBox.render(graphics, mouseX, mouseY, partialTick);

        // 空状态：没有维度，或者被搜索条件筛没了
        if (dimButtons.isEmpty()) {
            graphics.drawCenteredString(font,
                    Component.translatable(searchText.isBlank()
                            ? "gui.infinitestats.crossdim_empty"
                            : "gui.infinitestats.crossdim_no_match").getString(),
                    leftPos + GUI_W / 2, listTop + 24, TEXT_NEGATIVE);
        }

        // 底部提示 / 滚动条
        if (!toggleActive) {
            graphics.drawCenteredString(font,
                    Component.translatable("gui.infinitestats.crossdim_hint").getString(),
                    leftPos + GUI_W / 2, topPos + GUI_H - 14, TEXT_NEGATIVE);
        } else if (maxScroll > 0) {
            drawScrollbar(graphics, mouseX, mouseY, listTop, listBottom);
        }

        // 显示名可能是兜底翻译出来的，悬停给原始注册名，避免认错维度
        if (hoveredDim != null && !hoveredDim.equals(displayName(hoveredDim))) {
            queueTooltip(graphics, mouseX, mouseY, List.of("§7" + hoveredDim), TEXT_ACTIVE);
        }
    }

    /**
     * 滚动条。
     * <p>
     * 这里的 {@code scrollOffset} 是<b>像素</b>而不是行号，因此「可见量」直接取轨道高度 ——
     * 内容高 = 轨道高 + 最大偏移，滑块比例 {@code visible/(maxScroll+visible)} 正好等于
     * {@code trackH/contentHeight}，与 {@link EditorUi} 的通用算法一致。
     */
    private void drawScrollbar(GuiGraphics graphics, int mouseX, int mouseY,
                               int listTop, int listBottom) {
        int trackH = listBottom - listTop;
        EditorUi.scrollbar(graphics, mouseX, mouseY, scrollbarX(), listTop, trackH,
                scrollOffset, maxScroll, visibleRows(trackH));
    }

    /**
     * 列表可见行数 —— 滚动条滑块高度比例必须用「可见行数」，
     * 直接拿轨道像素高当 visible 会让滑块几乎占满整条轨道（比例公式
     * {@code visible/(maxScroll+visible)} 里 visible 变成像素数），结果就是「拖不动」。
     */
    private int visibleRows(int trackH) {
        return Math.max(1, trackH / (BTN_H + GAP));
    }

    /** 滚动条轨道左缘（面板坐标）。 */
    private int scrollbarX() {
        return leftPos + GUI_W - 6;
    }

    private int scrollTrackTop() {
        return topPos + LIST_TOP_OFFSET;
    }

    private int scrollTrackH() {
        return GUI_H - LIST_TOP_OFFSET - LIST_BOTTOM_OFFSET;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
