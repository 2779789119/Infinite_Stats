package com.infinitestats.client;

import com.infinitestats.compat.JechCompat;
import com.infinitestats.network.NetworkHandler;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/**
 * 效果过滤器 GUI（原 Debuff 免疫过滤器）
 * <p>
 * 玩家可以选择哪些效果被拦截、哪些放行，支持所有正面和负面效果。
 * 支持两种模式：
 * - 黑名单模式（默认）：列表中的效果被拦截，其他全部放行
 * - 白名单模式：列表中的效果绝对不拦截，其他不管
 */
public class DebuffFilterScreen extends Screen {

    private static final int GUI_WIDTH = 360;
    private static final int GUI_HEIGHT = 280;
    private static final int HEADER_H = 24;
    private static final int LIST_TOP = 30;
    private static final int ENTRY_H = 20;
    private static final int MAX_VISIBLE = 7;
    private static final int FOOTER_H = 28;
    private static final int SCROLLBAR_W = 6;
    private static final int CUSTOM_H = 24;

    private static final int BG_PANEL = 0xE81A1A2E;
    private static final int BG_ENTRY = 0x50252535;
    private static final int BG_ENTRY_HOVER = 0x80353550;
    private static final int BG_ENTRY_FILTERED = 0x504A2A1A;
    private static final int BG_ENTRY_FILTERED_HOVER = 0x808A4A2E;
    private static final int BG_ENTRY_FILTERED_GOOD = 0x502A4A1A;
    private static final int BG_ENTRY_FILTERED_GOOD_HOVER = 0x804E8A2E;
    private static final int BG_HEADER = 0xFF1E293B;
    private static final int BG_INPUT = 0xFF0F172A;
    private static final int BG_SCROLLBAR_TRACK = 0x30151520;
    private static final int BG_SCROLLBAR = 0x80555570;

    private static final int TEXT_PRIMARY = 0xFFE2E8F0;
    private static final int TEXT_SECONDARY = 0xFF94A3B8;
    private static final int TEXT_HIGHLIGHT = 0xFF4ADE80;
    private static final int TEXT_WARNING = 0xFFF87171;
    private static final int TEXT_MODE_BLACKLIST = 0xFFF87171;
    private static final int TEXT_MODE_WHITELIST = 0xFF4ADE80;

    /** 统一的列表条目 */
    private record FilterEntry(String name, String id, boolean isCustom) {}

    // 状态
    private int leftPos, topPos;
    private int scrollOffset;
    private int maxScroll;
    private boolean scrollbarDragging;
    private int scrollbarDragStartY;
    private int scrollbarDragStartOffset;

    private EditBox searchBox;
    private EditBox customInputBox;
    private String searchText = "";
    private boolean useBlacklist; // true=黑名单, false=白名单
    private final Set<String> filteredEffects = new HashSet<>();
    /** 记录用户手动输入的 ID（不在注册表中的自定义效果） */
    private final Set<String> customIds = new HashSet<>();

    /** 所有可用的效果列表（缓存） */
    private List<MobEffect> allEffects = new ArrayList<>();
    /** 过滤后的统一列表 */
    private List<FilterEntry> displayedEntries = new ArrayList<>();
    /** 上次播放音效的 tick */
    private long lastSoundTick;
    /** 悬停条目索引 */
    private int hoveredIndex = -1;

    public DebuffFilterScreen() {
        super(Component.translatable("screen.infinitestats.debuff_filter"));
    }

    @Override
    protected void init() {
        super.init();
        leftPos = (width - GUI_WIDTH) / 2;
        topPos = (height - GUI_HEIGHT) / 2;

        // 从客户端缓存读取当前过滤状态
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
                useBlacklist = stats.isBuffUseBlacklist();
                filteredEffects.clear();
                filteredEffects.addAll(stats.getBuffFilterList());
                // 重建 customIds：找出 filteredEffects 中不在注册表里的 ID
                for (String id : filteredEffects) {
                    if (ForgeRegistries.MOB_EFFECTS.getValue(new net.minecraft.resources.ResourceLocation(id)) == null) {
                        customIds.add(id);
                    }
                }
            });
        }

        // 收集所有效果
        buildAllEffectsList();
        applySearchFilter();

        // 搜索框
        int searchX = leftPos + 8;
        int searchY = topPos + 6;
        int searchW = GUI_WIDTH - 16;
        searchBox = new EditBox(font, searchX, searchY, searchW, 16,
                Component.translatable("screen.infinitestats.debuff_filter.search"));
        searchBox.setMaxLength(50);
        searchBox.setValue(searchText);
        searchBox.setResponder(this::onSearchChanged);
        searchBox.setBordered(true);
        searchBox.setFocused(true);
        searchBox.setTextColor(TEXT_PRIMARY);
        searchBox.setHint(Component.translatable("screen.infinitestats.debuff_filter.search_hint"));
        addRenderableWidget(searchBox);

        // 自定义 ID 输入框
        int customInputY = topPos + LIST_TOP + 26 + MAX_VISIBLE * (ENTRY_H + 2) + 4;
        customInputBox = new EditBox(font, leftPos + 8, customInputY, GUI_WIDTH - 120, 16,
                Component.translatable("screen.infinitestats.debuff_filter.custom_input"));
        customInputBox.setMaxLength(80);
        customInputBox.setBordered(true);
        customInputBox.setTextColor(TEXT_PRIMARY);
        customInputBox.setHint(Component.translatable("screen.infinitestats.debuff_filter.custom_hint"));
        addRenderableWidget(customInputBox);

        updateMaxScroll();
        rebuildFilterWidgets();
    }

    private void buildAllEffectsList() {
        allEffects.clear();
        for (MobEffect effect : ForgeRegistries.MOB_EFFECTS) {
            allEffects.add(effect);
        }
        // 按显示名称排序
        allEffects.sort(Comparator.comparing(e ->
                e.getDisplayName().getString().toLowerCase()));
    }

    private void applySearchFilter() {
        displayedEntries.clear();
        String query = searchText.toLowerCase().trim();

        // 先添加注册表中的效果
        for (MobEffect effect : allEffects) {
            String id = ForgeRegistries.MOB_EFFECTS.getKey(effect).toString();
            if (query.isEmpty() ||
                    JechCompat.matches(effect.getDisplayName().getString().toLowerCase(), query) ||
                    JechCompat.matches(id.toLowerCase(), query)) {
                displayedEntries.add(new FilterEntry(effect.getDisplayName().getString(), id, false));
            }
        }

        // 再添加自定义 ID（不重复添加已在注册表中的）
        for (String customId : customIds) {
            if (query.isEmpty() || JechCompat.matches(customId.toLowerCase(), query)) {
                // 检查是否已在注册表条目中
                boolean alreadyListed = allEffects.stream().anyMatch(e ->
                        ForgeRegistries.MOB_EFFECTS.getKey(e).toString().equals(customId));
                if (!alreadyListed) {
                    displayedEntries.add(new FilterEntry(customId, customId, true));
                }
            }
        }
    }

    private void onSearchChanged(String text) {
        searchText = text;
        applySearchFilter();
        scrollOffset = 0;
        updateMaxScroll();
        rebuildFilterWidgets();
    }

    private void updateMaxScroll() {
        maxScroll = Math.max(0, displayedEntries.size() - MAX_VISIBLE);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
    }

    private void rebuildFilterWidgets() {
        clearWidgets();
        // 重新添加搜索框和自定义输入框
        if (searchBox != null) {
            addRenderableWidget(searchBox);
        }
        if (customInputBox != null) {
            addRenderableWidget(customInputBox);
        }

        // 自定义 ID 添加按钮
        int customInputY = topPos + LIST_TOP + 26 + MAX_VISIBLE * (ENTRY_H + 2) + 4;
        addRenderableWidget(new PixelButton(
                leftPos + GUI_WIDTH - 106, customInputY, 42, 16,
                Component.translatable("screen.infinitestats.debuff_filter.custom_add"),
                0x503B5E8A, 0x805080B0, TEXT_HIGHLIGHT,
                b -> addCustomId()));

        // 模式切换按钮
        int modeBtnX = leftPos + 8;
        int modeBtnY = topPos + GUI_HEIGHT - FOOTER_H + 2;
        int modeBtnW = 100;
        addRenderableWidget(new PixelButton(modeBtnX, modeBtnY, modeBtnW, 14,
                Component.translatable("screen.infinitestats.debuff_filter.mode"),
                useBlacklist ? 0x508A2E2E : 0x502E8A4E,
                useBlacklist ? 0x80AA4040 : 0x8040AA40,
                useBlacklist ? TEXT_MODE_BLACKLIST : TEXT_MODE_WHITELIST,
                b -> toggleMode()));

        // 清空按钮
        int clearBtnX = modeBtnX + modeBtnW + 6;
        addRenderableWidget(new PixelButton(clearBtnX, modeBtnY, 48, 14,
                Component.translatable("screen.infinitestats.debuff_filter.clear"),
                0x50353550, 0x80555570, TEXT_SECONDARY,
                b -> clearAllFilters()));

        // 底部提示
        int hintX = leftPos + GUI_WIDTH - 8;
        String hint = useBlacklist
                ? Component.translatable("screen.infinitestats.debuff_filter.hint_blacklist").getString()
                : Component.translatable("screen.infinitestats.debuff_filter.hint_whitelist").getString();
        addRenderableWidget(new HintLabel(hintX - font.width(hint), modeBtnY + 1, hint));
    }

    /**
     * 添加用户自定义的效果 ID
     */
    private void addCustomId() {
        String input = customInputBox.getValue().trim();
        if (input.isEmpty()) return;

        // 验证格式：必须包含冒号 (namespace:id)
        if (!input.contains(":")) {
            // 自动补全 minecraft: 前缀
            input = "minecraft:" + input;
        }

        String id = input.toLowerCase();
        if (filteredEffects.add(id)) {
            playClickSound();
            customIds.add(id);
            applySearchFilter();
            updateMaxScroll();
            rebuildFilterWidgets();
            sendFilterUpdate();
        }
        customInputBox.setValue("");
    }

    private void toggleMode() {
        playClickSound();
        useBlacklist = !useBlacklist;
        sendFilterUpdate();
        rebuildFilterWidgets();
    }

    private void clearAllFilters() {
        playClickSound();
        filteredEffects.clear();
        customIds.clear();
        applySearchFilter();
        updateMaxScroll();
        sendFilterUpdate();
        rebuildFilterWidgets();
    }

    private void toggleEffect(FilterEntry entry) {
        playClickSound();
        if (filteredEffects.contains(entry.id())) {
            filteredEffects.remove(entry.id());
            customIds.remove(entry.id());
        } else {
            filteredEffects.add(entry.id());
            if (entry.isCustom()) {
                customIds.add(entry.id());
            }
        }
        applySearchFilter();
        updateMaxScroll();
        sendFilterUpdate();
        rebuildFilterWidgets();
    }

    private void sendFilterUpdate() {
        NetworkHandler.CHANNEL.sendToServer(
                new NetworkHandler.UpdateBuffFilterPacket(useBlacklist, new HashSet<>(filteredEffects)));

        // 同步更新客户端缓存
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
                stats.setBuffFilterList(new HashSet<>(filteredEffects), useBlacklist);
            });
        }
    }

    private void playClickSound() {
        if (minecraft == null) return;
        long now = System.currentTimeMillis();
        if (now - lastSoundTick < 50) return;
        lastSoundTick = now;
        minecraft.getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    // ======================== 渲染 ========================

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        PoseStack pose = graphics.pose();
        pose.pushPose();

        // 面板背景
        graphics.fill(leftPos, topPos, leftPos + GUI_WIDTH, topPos + GUI_HEIGHT, BG_PANEL);

        // 标题栏
        graphics.fill(leftPos, topPos, leftPos + GUI_WIDTH, topPos + HEADER_H, BG_HEADER);
        String title = Component.translatable("screen.infinitestats.debuff_filter").getString();
        graphics.drawCenteredString(font, title, leftPos + GUI_WIDTH / 2, topPos + 8, TEXT_HIGHLIGHT);

        // 模式标签
        String modeText = useBlacklist
                ? Component.translatable("screen.infinitestats.debuff_filter.mode_blacklist").getString()
                : Component.translatable("screen.infinitestats.debuff_filter.mode_whitelist").getString();
        int modeColor = useBlacklist ? TEXT_MODE_BLACKLIST : TEXT_MODE_WHITELIST;
        graphics.drawString(font, modeText, leftPos + GUI_WIDTH - font.width(modeText) - 8,
                topPos + 8, modeColor);

        // 渲染效果列表
        renderEffectList(graphics, mouseX, mouseY);
        renderScrollbar(graphics, mouseX, mouseY);

        // 渲染所有 widget
        for (var renderable : this.renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }

        // 渲染悬停 tooltip
        renderTooltip(graphics, mouseX, mouseY);

        pose.popPose();
    }

    private void renderEffectList(GuiGraphics g, int mouseX, int mouseY) {
        hoveredIndex = -1;

        for (int i = 0; i < MAX_VISIBLE; i++) {
            int idx = i + scrollOffset;
            if (idx >= displayedEntries.size()) break;

            FilterEntry entry = displayedEntries.get(idx);
            boolean isInFilter = filteredEffects.contains(entry.id());

            int y = topPos + LIST_TOP + 24 + i * (ENTRY_H + 2);
            int x = leftPos + 6;
            int entryW = GUI_WIDTH - SCROLLBAR_W - 18;

            boolean hovered = mouseX >= x && mouseX < x + entryW &&
                    mouseY >= y && mouseY < y + ENTRY_H;
            if (hovered) hoveredIndex = idx;

            int bgColor;
            if (isInFilter) {
                if (useBlacklist) {
                    bgColor = hovered ? BG_ENTRY_FILTERED_HOVER : BG_ENTRY_FILTERED;
                } else {
                    bgColor = hovered ? BG_ENTRY_FILTERED_GOOD_HOVER : BG_ENTRY_FILTERED_GOOD;
                }
            } else {
                bgColor = hovered ? BG_ENTRY_HOVER : BG_ENTRY;
            }

            g.fill(x, y, x + entryW, y + ENTRY_H, bgColor);

            // 状态图标
            String icon;
            int iconColor;
            if (entry.isCustom()) {
                // 自定义条目：特殊图标
                icon = isInFilter ? "✦" : "✧";
                iconColor = isInFilter ? TEXT_HIGHLIGHT : 0xFF64748B;
            } else {
                icon = isInFilter ? "◉" : "○";
                if (useBlacklist) {
                    iconColor = isInFilter ? TEXT_WARNING : TEXT_HIGHLIGHT;
                } else {
                    iconColor = isInFilter ? TEXT_HIGHLIGHT : TEXT_SECONDARY;
                }
            }
            g.drawString(font, icon, x + 6, y + (ENTRY_H - font.lineHeight) / 2 + 1, iconColor);

            // 条目名称
            String name = entry.isCustom()
                    ? "⚙ " + entry.name()
                    : entry.name();
            int textColor = hovered ? TEXT_PRIMARY : TEXT_SECONDARY;
            g.drawString(font, name, x + 20, y + (ENTRY_H - font.lineHeight) / 2 + 1, textColor);

            // 效果 ID
            int idColor = hovered ? TEXT_HIGHLIGHT : 0xFF64748B;
            g.drawString(font, entry.id(), x + entryW - font.width(entry.id()) - 4,
                    y + (ENTRY_H - font.lineHeight) / 2 + 1, idColor);
        }

        // 自定义输入分隔线
        int lineY = topPos + LIST_TOP + 24 + MAX_VISIBLE * (ENTRY_H + 2) + 2;
        g.fill(leftPos + 8, lineY, leftPos + GUI_WIDTH - 8, lineY + 1, 0x403B5E8A);
    }

    private void renderScrollbar(GuiGraphics g, int mouseX, int mouseY) {
        if (maxScroll <= 0) return;

        int sx = leftPos + GUI_WIDTH - SCROLLBAR_W - 4;
        int sy = topPos + LIST_TOP + 24;
        int sh = MAX_VISIBLE * (ENTRY_H + 2) - 2;

        g.fill(sx, sy, sx + SCROLLBAR_W, sy + sh, BG_SCROLLBAR_TRACK);

        int sliderH = Math.max(20, (int) ((float) MAX_VISIBLE / (maxScroll + MAX_VISIBLE) * sh));
        int sliderY = sy + (sh - sliderH) * scrollOffset / maxScroll;

        boolean hovering = mouseX >= sx && mouseX < sx + SCROLLBAR_W &&
                mouseY >= sliderY && mouseY < sliderY + sliderH;
        g.fill(sx, sliderY, sx + SCROLLBAR_W, sliderY + sliderH,
                hovering || scrollbarDragging ? 0xB08888A0 : BG_SCROLLBAR);
    }

    private void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (hoveredIndex < 0 || hoveredIndex >= displayedEntries.size()) return;

        FilterEntry entry = displayedEntries.get(hoveredIndex);
        boolean isInFilter = filteredEffects.contains(entry.id());

        List<String> lines = new ArrayList<>();
        if (entry.isCustom()) {
            lines.add(Component.translatable("screen.infinitestats.debuff_filter.custom_label").getString() + " " + entry.name());
        } else {
            lines.add(entry.name());
        }

        if (useBlacklist) {
            if (isInFilter) {
                lines.add(Component.translatable("screen.infinitestats.debuff_filter.tooltip_blacklist_blocked").getString());
            } else {
                lines.add(Component.translatable("screen.infinitestats.debuff_filter.tooltip_blacklist_allowed").getString());
            }
        } else {
            if (isInFilter) {
                lines.add(Component.translatable("screen.infinitestats.debuff_filter.tooltip_whitelist_protected").getString());
            } else {
                lines.add(Component.translatable("screen.infinitestats.debuff_filter.tooltip_whitelist_ignored").getString());
            }
        }
        lines.add(entry.id());
        lines.add(Component.translatable("screen.infinitestats.debuff_filter.tooltip_click").getString());

        List<net.minecraft.util.FormattedCharSequence> visualLines = new ArrayList<>();
        for (String line : lines) {
            visualLines.add(Component.literal(line).getVisualOrderText());
        }
        g.renderTooltip(font, visualLines, mouseX, mouseY);
    }

    // ======================== 鼠标输入 ========================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // 点击条目
            if (hoveredIndex >= 0 && hoveredIndex < displayedEntries.size()) {
                toggleEffect(displayedEntries.get(hoveredIndex));
                return true;
            }

            // 滚动条
            if (maxScroll > 0 && isMouseOnScrollbar((int) mouseX, (int) mouseY)) {
                scrollbarDragging = true;
                scrollbarDragStartY = (int) mouseY;
                scrollbarDragStartOffset = scrollOffset;
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (scrollbarDragging && maxScroll > 0) {
            int sy = topPos + LIST_TOP + 24;
            int sh = MAX_VISIBLE * (ENTRY_H + 2) - 2;
            int sliderH = Math.max(20, (int) ((float) MAX_VISIBLE / (maxScroll + MAX_VISIBLE) * sh));
            int dragRange = sh - sliderH;
            if (dragRange > 0) {
                float progress = (float) ((mouseY - sy - sliderH / 2.0 - scrollbarDragStartY) / dragRange
                        + (float) scrollbarDragStartOffset / maxScroll);
                scrollOffset = Math.max(0, Math.min(maxScroll,
                        (int) (progress * maxScroll + 0.5)));
            }
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
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (maxScroll > 0) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset + (delta > 0 ? -1 : 1)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private boolean isMouseOnScrollbar(int mouseX, int mouseY) {
        int sx = leftPos + GUI_WIDTH - SCROLLBAR_W - 4;
        int sy = topPos + LIST_TOP + 24;
        int sh = MAX_VISIBLE * (ENTRY_H + 2) - 2;
        return mouseX >= sx && mouseX < sx + SCROLLBAR_W + 4 &&
                mouseY >= sy && mouseY < sy + sh;
    }

    // ======================== 键盘输入 ========================

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        // 在自定义输入框按回车 → 添加
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (customInputBox != null && customInputBox.isFocused()) {
                addCustomId();
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_W) {
            if (maxScroll > 0) {
                scrollOffset = Math.max(0, scrollOffset - 1);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_S) {
            if (maxScroll > 0) {
                scrollOffset = Math.min(maxScroll, scrollOffset + 1);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    // ======================== 自定义控件 ========================

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
            int color = isHoveredOrFocused() ? hoverColor : bgColor;
            g.fill(getX(), getY(), getX() + width, getY() + height, color);
            g.drawCenteredString(Minecraft.getInstance().font, getMessage(),
                    getX() + width / 2,
                    getY() + (height - Minecraft.getInstance().font.lineHeight) / 2 + 1,
                    textColor);
        }

        @Override
        public void playDownSound(net.minecraft.client.sounds.SoundManager handler) {}
    }

    private static class HintLabel extends Button {
        private final String text;
        private final int color;

        HintLabel(int x, int y, String text) {
            super(x, y, 0, 12, Component.literal(text), b -> {}, DEFAULT_NARRATION);
            this.text = text;
            this.color = TEXT_SECONDARY;
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.drawString(Minecraft.getInstance().font, text, getX(), getY(), color);
        }

        @Override
        public void playDownSound(net.minecraft.client.sounds.SoundManager handler) {}
    }
}
