package com.infinitestats.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * 所有「固定尺寸面板」界面的基类 —— 统一处理分辨率 / 界面大小适配。
 *
 * <h2>为什么需要它</h2>
 * 原版自动 GUI 缩放只保证逻辑分辨率 ≥ 320×240，16:9 屏幕上逻辑高度常年只有 240~270，
 * 而本模组的面板普遍高 260~376：不做适配就会被切掉底部（连"完成"按钮都点不到）。
 *
 * <h2>坐标系约定（重要）</h2>
 * 子类一律使用<b>面板局部坐标</b>：面板左上角是 (0,0)，右下角是
 * ({@link #panelW()}, {@link #panelH()})，与窗口大小无关。
 * <ul>
 *   <li>{@code leftPos} / {@code topPos} 恒为 <b>0</b> —— 保留这两个字段只是为了让历史代码里
 *       {@code leftPos + X} 之类的写法继续成立，不要再给它们赋值；</li>
 *   <li>屏幕位置由本类在渲染时用 {@code pushPose + translate(origin) + scale(uiScale)} 负责；</li>
 *   <li>鼠标事件在进入子类钩子前已经换算成面板坐标，控件的 bounds 也用面板坐标写即可。</li>
 * </ul>
 *
 * <h2>子类要做什么</h2>
 * <ol>
 *   <li>继承本类，实现 {@link #panelW()} / {@link #panelH()}（面板逻辑尺寸）；</li>
 *   <li>原 {@code render(...)} 改名 {@link #renderPanel}（内容层），
 *       末尾 {@code super.render(...)} 那行删掉 —— 控件由基类在内容层之上绘制；</li>
 *   <li>原本画在 {@code super.render} 之后的浮窗 / 光标层，挪到 {@link #renderPanelOverlay}；</li>
 *   <li>鼠标方法改名成 {@code onPanelXxx}（参数已是面板坐标，不需要也不应该再手动换算）。</li>
 * </ol>
 *
 * <h2>缩放</h2>
 * 默认「自动适配」：取 {@code min(1, 窗口宽/面板宽, 窗口高/面板高)} 并夹在
 * {@link ClientSettings#MIN_UI_SCALE}~{@link ClientSettings#MAX_UI_SCALE}，
 * 保证面板在任何分辨率下都尽量完整可见。面板右下角的百分比角标可点击在
 * 自动 / 手动之间切换，{@code Ctrl+滚轮} 调手动缩放、{@code Ctrl+0} 回到自动，
 * 设置保存在 {@code config/infinite_stats-client.json}。
 */
public abstract class PanelScreen extends Screen {

    /** 面板逻辑宽度（缩放前）。 */
    protected abstract int panelW();

    /** 面板逻辑高度（缩放前）。 */
    protected abstract int panelH();

    /**
     * 面板坐标系原点：<b>恒为 0</b>，见类注释。
     * 只是为了不改动子类里大量 {@code leftPos + X} 的历史写法而保留。
     */
    protected int leftPos;
    /** 面板坐标系原点：<b>恒为 0</b>，见 {@link #leftPos}。 */
    protected int topPos;

    /** 当前生效的缩放。 */
    private float uiScale = 1f;
    /** 面板左上角在屏幕上的实际位置。 */
    private int originX, originY;

    private long lastScaleChange;

    /** 右键拖动面板：记录起点。 */
    private boolean draggingPanel;
    private int dragStartMouseX, dragStartMouseY, dragStartOffsetX, dragStartOffsetY;

    protected PanelScreen(Component title) {
        super(title);
    }

    // ======================== 缩放与坐标 ========================

    /** 每帧 / 每个输入事件前重算一次（窗口大小会变）。 */
    protected final void layout() {
        float fitW = width / (float) Math.max(1, panelW());
        float fitH = height / (float) Math.max(1, panelH());
        float fit = Math.min(fitW, fitH);

        float scale = ClientSettings.autoFit ? Math.min(1f, fit) : ClientSettings.guiScale;
        scale = Math.max(ClientSettings.MIN_UI_SCALE, Math.min(ClientSettings.MAX_UI_SCALE, scale));

        uiScale = scale;
        originX = clampOrigin(Math.round((width - panelW() * scale) / 2f) + ClientSettings.guiOffsetX,
                width, panelW() * scale);
        originY = clampOrigin(Math.round((height - panelH() * scale) / 2f) + ClientSettings.guiOffsetY,
                height, panelH() * scale);
    }

    /**
     * 把面板原点夹进「至少留 40px 可见」的范围。
     * <p>
     * 没拖动时原点是居中的，本来就满足这个条件 —— 这一段只在玩家拖过面板后起作用。
     */
    private static int clampOrigin(int origin, int screenSize, float panelSize) {
        int min = (int) (40 - panelSize);
        int max = screenSize - 40;
        if (min > max) return Math.max(0, (int) ((screenSize - panelSize) / 2f));
        return Math.max(min, Math.min(max, origin));
    }

    /** 当前缩放（供子类画自定义元素时参考）。 */
    protected final float panelScale() {
        return uiScale;
    }

    protected final int panelOriginX() {
        return originX;
    }

    protected final int panelOriginY() {
        return originY;
    }

    /** 屏幕 X → 面板 X。 */
    protected final int toPanelX(double screenX) {
        return Math.round((float) ((screenX - originX) / uiScale));
    }

    /** 屏幕 Y → 面板 Y。 */
    protected final int toPanelY(double screenY) {
        return Math.round((float) ((screenY - originY) / uiScale));
    }

    /** 面板 → 屏幕（需要按原版 API 交屏幕坐标时用，例如 {@code renderTooltip}）。 */
    protected final int toScreenX(double panelX) {
        return Math.round((float) (originX + panelX * uiScale));
    }

    protected final int toScreenY(double panelY) {
        return Math.round((float) (originY + panelY * uiScale));
    }

    /**
     * 按<b>面板坐标</b>开一个裁剪区，坐标空间与缩放无关。
     *
     * <h2>为什么不能直接写 {@code enableScissor(面板坐标)}</h2>
     * 原版 {@code enableScissor} 收的是界面坐标，内部还要换算成物理像素 ——
     * 「当前 pose 的缩放算不算进去」属于实现细节（不同映射 / 版本并不一致）。
     * 一旦不算，面板坐标就会按 1:1 被当成屏幕坐标用：本模组面板又常处于放大状态，
     * 裁剪区就会整体上移、把列表下半截（含诊断信息这类靠近底边的元素）剪掉 ——
     * 表现为"只铺了两三条、下面一片空白"，且看不出来是裁剪导致的。
     * <p>
     * 这里先把姿态临时压成单位矩阵再调用 {@code enableScissor}，并传入真实<b>屏幕坐标</b>：
     * 缩放算不算进去，结果都落在同一块物理区域上。
     *
     * @param x1 面板坐标左上 X
     * @param y1 面板坐标左上 Y
     * @param x2 面板坐标右下 X
     * @param y2 面板坐标右下 Y
     */
    protected final void beginPanelScissor(GuiGraphics g, int x1, int y1, int x2, int y2) {
        g.pose().pushPose();
        g.pose().last().pose().identity();
        g.enableScissor(toScreenX(x1), toScreenY(y1), toScreenX(x2), toScreenY(y2));
        // 弹出后姿态回到面板缩放，内容照常用面板坐标画；裁剪区已在上面算好，不受影响
        g.pose().popPose();
    }

    /** 关闭 {@link #beginPanelScissor} 开的裁剪区（同样在屏幕空间操作，保持栈的坐标口径一致）。 */
    protected final void endPanelScissor(GuiGraphics g) {
        g.pose().pushPose();
        g.pose().last().pose().identity();
        g.disableScissor();
        g.pose().popPose();
    }

    // ======================== 渲染 ========================

    /** 内容层：面板背景、标题、列表等（坐标 = 面板坐标）。 */
    protected void renderPanel(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    /** 覆盖层：需要画在控件之上的浮窗 / 光标提示（同样是面板坐标）。 */
    protected void renderPanelOverlay(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public final void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 1.21 起 renderBackground 需要鼠标坐标与部分 tick
        renderBackground(g, mouseX, mouseY, partialTick);
        layout();
        drawPanel(g, mouseX, mouseY, partialTick);
        renderScaleBadge(g, mouseX, mouseY);
    }

    /**
     * 按当前缩放把面板整体画出来（内容 → 控件 → 覆盖层）。
     * 子类若需要"控件在内容下面"，可以在 {@link #renderPanel} 里调用 {@link #renderWidgets}。
     */
    private void drawPanel(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int panelMouseX = toPanelX(mouseX);
        int panelMouseY = toPanelY(mouseY);

        var pose = g.pose();
        pose.pushPose();
        pose.translate(originX, originY, 0);
        pose.scale(uiScale, uiScale, 1f);
        renderPanel(g, panelMouseX, panelMouseY, partialTick);
        if (baseRendersWidgets()) renderWidgets(g, panelMouseX, panelMouseY, partialTick);
        renderPanelOverlay(g, panelMouseX, panelMouseY, partialTick);
        pose.popPose();
    }

    /** 绘制注册的控件（等价于原版 {@code Screen#render} 里的控件遍历）。 */
    protected final void renderWidgets(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 1.21 起 Screen#render 会自行调用 renderBackground：这里若走 super.render，
        // 背景会被再糊一次、把面板内容盖掉，所以只遍历控件、不调 super.render。
        for (var renderable : this.renderables) {
            renderable.render(g, mouseX, mouseY, partialTick);
        }
    }

    /**
     * 控件是否由基类统一绘制。
     * <p>
     * 需要在裁剪区里自绘控件（例如可滚动列表的按钮）的界面返回 {@code false}，
     * 自己在 {@link #renderPanel} 里遍历 {@code renderables} 画，避免被画两遍。
     */
    protected boolean baseRendersWidgets() {
        return true;
    }

    // ======================== 缩放角标 ========================

    /** 角标矩形（屏幕坐标）。 */
    private int[] badgeRect() {
        String text = badgeText();
        int w = font.width(text) + 6;
        int h = font.lineHeight + 4;
        int x = originX + (int) (panelW() * uiScale) - w - 3;
        int y = originY + (int) (panelH() * uiScale) + 3;
        // 面板贴到底时角标会跑出屏幕 → 挪到面板内右下角
        if (y + h > height) y = originY + (int) (panelH() * uiScale) - h - 3;
        if (x < 0) x = 0;
        return new int[]{x, y, w, h};
    }

    private String badgeText() {
        String pct = Math.round(uiScale * 100) + "%";
        return ClientSettings.autoFit
                ? Component.translatable("screen.infinitestats.ui_scale.auto").getString() + " " + pct
                : pct;
    }

    private void renderScaleBadge(GuiGraphics g, int mouseX, int mouseY) {
        int[] r = badgeRect();
        boolean hover = mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3];
        g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], hover ? 0x80353550 : 0x40151525);
        g.drawString(font, badgeText(), r[0] + 3, r[1] + 2,
                hover ? EditorUi.INFO : EditorUi.SECONDARY);

        if (hover) {
            java.util.List<String> lines = new java.util.ArrayList<>();
            lines.add(Component.translatable("screen.infinitestats.ui_scale.title").getString());
            lines.add("§7" + Component.translatable(ClientSettings.autoFit
                    ? "screen.infinitestats.ui_scale.hint_auto"
                    : "screen.infinitestats.ui_scale.hint_manual").getString());
            EditorUi.tooltip(g, mouseX, mouseY, lines, EditorUi.INFO);
        }
    }

    /** 角标被点击：切自动 / 手动。 */
    private boolean badgeClicked(double mouseX, double mouseY) {
        int[] r = badgeRect();
        return mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3];
    }

    // ======================== 输入：换算 + 钩子 ========================

    @Override
    public final boolean mouseClicked(double mouseX, double mouseY, int button) {
        layout();
        if (button == 0 && badgeClicked(mouseX, mouseY)) {
            ClientSettings.autoFit = !ClientSettings.autoFit;
            if (!ClientSettings.autoFit) ClientSettings.guiScale = uiScale;
            saveScaleLater();
            layout();
            return true;
        }
        int mx = toPanelX(mouseX), my = toPanelY(mouseY);
        // 中键：面板位置归零（缩放用 Ctrl+0 / 角标）
        if (button == 2 && insidePanel(mx, my)) {
            ClientSettings.guiOffsetX = 0;
            ClientSettings.guiOffsetY = 0;
            saveScaleLater();
            layout();
            return true;
        }
        // 右键：拖动面板（位置持久化）
        if (button == 1 && insidePanel(mx, my)) {
            draggingPanel = true;
            dragStartMouseX = (int) mouseX;
            dragStartMouseY = (int) mouseY;
            dragStartOffsetX = ClientSettings.guiOffsetX;
            dragStartOffsetY = ClientSettings.guiOffsetY;
            return true;
        }
        return onPanelClick(mx, my, button) || super.mouseClicked(mx, my, button);
    }

    @Override
    public final boolean mouseReleased(double mouseX, double mouseY, int button) {
        layout();
        if (draggingPanel && button == 1) {
            draggingPanel = false;
            ClientSettings.save();
            return true;
        }
        int mx = toPanelX(mouseX), my = toPanelY(mouseY);
        return onPanelRelease(mx, my, button) || super.mouseReleased(mx, my, button);
    }

    @Override
    public final boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        layout();
        if (draggingPanel) {
            // 拖动用屏幕像素，和 origin 的偏移量在同一坐标系里
            ClientSettings.guiOffsetX = dragStartOffsetX + (int) mouseX - dragStartMouseX;
            ClientSettings.guiOffsetY = dragStartOffsetY + (int) mouseY - dragStartMouseY;
            layout();
            return true;
        }
        int mx = toPanelX(mouseX), my = toPanelY(mouseY);
        int pdx = Math.round((float) (dragX / uiScale));
        int pdy = Math.round((float) (dragY / uiScale));
        return onPanelDrag(mx, my, button, pdx, pdy)
                || super.mouseDragged(mx, my, button, pdx, pdy);
    }

    /** 面板坐标是否落在面板内。 */
    private boolean insidePanel(int panelX, int panelY) {
        return panelX >= 0 && panelX < panelW() && panelY >= 0 && panelY < panelH();
    }

    /** 1.21 起滚轮事件多了水平分量，这里用竖直分量 {@code scrollY}。 */
    @Override
    public final boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        layout();
        // Ctrl + 滚轮 → 全局 UI 缩放（以当前实际值为起点，自动切到手动）
        if (Screen.hasControlDown()) {
            float next = Math.max(ClientSettings.MIN_UI_SCALE,
                    Math.min(ClientSettings.MAX_UI_SCALE, uiScale + (float) (scrollY > 0 ? 0.05 : -0.05)));
            ClientSettings.autoFit = false;
            ClientSettings.guiScale = next;
            saveScaleLater();
            return true;
        }
        int mx = toPanelX(mouseX), my = toPanelY(mouseY);
        return onPanelScroll(mx, my, scrollY) || super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public final void mouseMoved(double mouseX, double mouseY) {
        layout();
        int mx = toPanelX(mouseX), my = toPanelY(mouseY);
        if (!onPanelMove(mx, my)) super.mouseMoved(mx, my);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Ctrl+0：回到自动适配
        if (keyCode == GLFW.GLFW_KEY_0 && Screen.hasControlDown()) {
            ClientSettings.autoFit = true;
            ClientSettings.guiScale = ClientSettings.DEFAULT_UI_SCALE;
            saveScaleLater();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        ClientSettings.save();
        super.onClose();
    }

    /** 缩放改动节流落盘（避免滚轮连滚时每个 tick 都写文件）。 */
    private void saveScaleLater() {
        long now = System.currentTimeMillis();
        if (now - lastScaleChange > 800) {
            lastScaleChange = now;
            ClientSettings.save();
        }
    }

    // ======================== 子类钩子（坐标已是面板坐标） ========================

    protected boolean onPanelClick(int mouseX, int mouseY, int button) {
        return false;
    }

    protected boolean onPanelRelease(int mouseX, int mouseY, int button) {
        return false;
    }

    protected boolean onPanelDrag(int mouseX, int mouseY, int button, int dragX, int dragY) {
        return false;
    }

    protected boolean onPanelScroll(int mouseX, int mouseY, double delta) {
        return false;
    }

    protected boolean onPanelMove(int mouseX, int mouseY) {
        return false;
    }
}
