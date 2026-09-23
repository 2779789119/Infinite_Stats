package com.infinitestats.client;

import com.infinitestats.compat.JechCompat;
import com.infinitestats.emc.EmcMenu;
import com.infinitestats.emc.EmcPlayerDataProvider;
import com.infinitestats.emc.EmcDatabase;
import com.infinitestats.network.NetworkHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.BiFunction;
import java.util.function.BiConsumer;

/**
 * EMC 定价器 GUI — 由 GUI Crafter 生成，完整替换旧的转化台界面。
 *
 * 交互元素与接入点（覆盖同名方法即可介入逻辑）：
 *   button   "que_ren_ding_jia" -> onButtonClick("que_ren_ding_jia")                  确认定价按钮
 *   slot     "ding_jia_wu_pin"  -> getSlotItem("ding_jia_wu_pin", 0) / onSlotClick(...)  定价物品槽
 *   slot     "mai_ru"           -> 学习槽（对应 EmcMenu.learnContainer / Slot 0 的 LearnSlot，
 *                                  放入即自动消耗并 learnAndConvert 学习/返还 EMC）
 *                                  getSlotItem("mai_ru", 0) 取学习槽当前物品；
 *                                  onSlotClick("mai_ru", 0) 处理把物品放入学习槽
 *   textField "ding_jia"        -> onTextFieldChanged("ding_jia", value)             定价数值输入
 *   textField "sou_suo"         -> onTextFieldChanged("sou_suo", value)             搜索过滤
 *   slots    "wan_jia_bei_bao" / "wan_jia_wu_pin_lan" -> 玩家背包/物品栏 getSlotItem/onSlotClick
 *
 * 装饰元素：
 *   tooltip "bei_jing" -> 已学习列表的背景面板（渲染 EmcPlayerData.getLearnedItems() 列表的区域，
 *                         当前仅画背景框；如需展示列表，在 render 中自定义绘制该区域即可）
 *   label "EMC"  -> getText("EMC", ...)   EMC 余额标题
 *   label "su_zi" -> getText("su_zi", ...) EMC 余额数值（接 EmcPlayerData.getEmcBalance()）
 *   label "label" -> getText("label", "定价器")
 *
 * 注意：当前为占位实现，所有交互回调均为空，数据接入待后续补充。
 *       语义已标注：mai_ru = 学习槽，bei_jing = 已学习列表背景。
 */
public class EmcScreen extends Screen implements MenuAccess<EmcMenu> {

    private static final int CANVAS_W = 256;
    private static final int CANVAS_H = 256;

    private static final ResourceLocation ICONS = new ResourceLocation("textures/gui/icons.png");
    private static final ResourceLocation FURNACE = new ResourceLocation("textures/gui/container/furnace.png");
    private static final ResourceLocation GENERIC_54 = new ResourceLocation("textures/gui/container/generic_54.png");

    private static final class E {
        final String type;
        final int x, y, w, h;
        final String text;
        final String name;
        final String texture;
        final int u, v, tw, th, texW, texH;
        E(String type, int x, int y, int w, int h, String text, String name, String texture, int u, int v, int tw, int th, int texW, int texH) {
            this.type = type; this.x = x; this.y = y; this.w = w; this.h = h; this.text = text; this.name = name;
            this.texture = texture; this.u = u; this.v = v; this.tw = tw; this.th = th; this.texW = texW; this.texH = texH;
        }
    }

    private static final List<E> ELEMENTS = new ArrayList<>();
    static {
        ELEMENTS.add(new E("panel", 0, 0, 256, 256, "", "zheng_ti_bei_jing", "", 0, 0, 0, 0, 0, 0));
        ELEMENTS.add(new E("tooltip", 3, 19, 250, 150, "", "bei_jing", "", 0, 0, 0, 0, 0, 0));
        ELEMENTS.add(new E("button", 192, 240, 58, 11, "确认", "que_ren_ding_jia", "", 0, 0, 0, 0, 0, 0));
        ELEMENTS.add(new E("slot_grid", 5, 176, 162, 54, "", "wan_jia_bei_bao", "", 0, 0, 0, 0, 0, 0));
        ELEMENTS.add(new E("slot_grid", 5, 233, 162, 18, "", "wan_jia_wu_pin_lan", "", 0, 0, 0, 0, 0, 0));
        ELEMENTS.add(new E("separator", 3, 171, 249, 4, "", "separator", "", 0, 0, 0, 0, 0, 0));
        ELEMENTS.add(new E("slot", 209, 208, 18, 18, "", "ding_jia_wu_pin", "", 0, 0, 0, 0, 0, 0));
        ELEMENTS.add(new E("label", 209, 175, 26, 8, "定价器", "label", "", 0, 0, 0, 0, 0, 0));
        ELEMENTS.add(new E("text_field", 187, 187, 65, 12, "", "ding_jia", "", 0, 0, 0, 0, 0, 0));
        ELEMENTS.add(new E("slot", 169, 175, 18, 18, "", "mai_ru", "", 0, 0, 0, 0, 0, 0));
        ELEMENTS.add(new E("separator", 3, 15, 250, 4, "", "separator_1", "", 0, 0, 0, 0, 0, 0));
        ELEMENTS.add(new E("label", 4, 5, 18, 8, "EMC", "EMC", "", 0, 0, 0, 0, 0, 0));
        ELEMENTS.add(new E("label", 24, 4, 33, 9, "", "su_zi", "", 0, 0, 0, 0, 0, 0));
        ELEMENTS.add(new E("text_field", 165, 2, 86, 12, "", "sou_suo", "", 0, 0, 0, 0, 0, 0));
        }

    private final Map<String, Button> buttons = new HashMap<>();
    private final Map<String, EditBox> textFields = new HashMap<>();
    private final Map<String, CheckboxWidget> checkboxes = new HashMap<>();
    private final Map<String, SliderWidget> sliders = new HashMap<>();
    private final Map<String, ScrollbarWidget> scrollbars = new HashMap<>();
    private final Map<String, SlotWidget> slots = new HashMap<>();

    private final EmcMenu menu;

    public EmcScreen(EmcMenu menu, Inventory inv, Component title) {
        super(title);
        this.menu = menu;
    }

    @Override
    public EmcMenu getMenu() {
        return this.menu;
    }

    // 已学列表状态
    private String searchText = "";
    private int learnedPage = 0;
    private ItemStack hoveredLearned = ItemStack.EMPTY;

    // 定价器状态
    private ItemStack pricingItem = ItemStack.EMPTY;
    private String pricingText = "";

    @Override
    protected void init() {
        super.init();
        int left = (this.width - CANVAS_W) / 2;
        int top = (this.height - CANVAS_H) / 2;
        for (E e : ELEMENTS) {
            int x = left + e.x, y = top + e.y, w = Math.max(1, e.w), h = Math.max(1, e.h);
            switch (e.type) {
                case "button" -> {
                    Button btn = new Button.Builder(Component.literal(e.text == null ? "" : e.text), b -> onButtonClick(e.name)).pos(x, y).size(w, h).build();
                    buttons.put(e.name, btn);
                    addRenderableWidget(btn);
                }
                case "text_field" -> {
                    EditBox eb = new EditBox(this.font, x, y, w, h, Component.empty());
                    eb.setMaxLength(256);
                    if ("sou_suo".equals(e.name)) {
                        eb.setHint(Component.literal("搜索 @模组 #标签"));
                    }
                    eb.setValue(e.text == null ? "" : e.text);
                    eb.setResponder(v -> onTextFieldChanged(e.name, v));
                    textFields.put(e.name, eb);
                    addRenderableWidget(eb);
                }
                case "checkbox" -> {
                    CheckboxWidget cb = new CheckboxWidget(x, y, w, h, e.name, v -> onCheckboxToggle(e.name, v));
                    checkboxes.put(e.name, cb);
                    addRenderableWidget(cb);
                }
                case "slider" -> {
                    SliderWidget sl = new SliderWidget(x, y, w, h, e.name, v -> onSliderChanged(e.name, v));
                    sliders.put(e.name, sl);
                    addRenderableWidget(sl);
                }
                case "scrollbar" -> {
                    ScrollbarWidget sbw = new ScrollbarWidget(x, y, w, h, e.name, v -> onScrollChanged(e.name, v));
                    scrollbars.put(e.name, sbw);
                    addRenderableWidget(sbw);
                }
                case "slot" -> {
                    SlotWidget sw = new SlotWidget(x, y, 18, 18, e.name, 0, this::getSlotItem, this::onSlotClick);
                    slots.put(e.name, sw);
                    addRenderableWidget(sw);
                }
                case "slot_grid" -> {
                    int cols = Math.max(1, e.w / 18);
                    int rows = Math.max(1, e.h / 18);
                    for (int r = 0; r < rows; r++) {
                        for (int c = 0; c < cols; c++) {
                            int idx = r * cols + c;
                            addRenderableWidget(new SlotWidget(x + c * 18, y + r * 18, 18, 18, e.name, idx, this::getSlotItem, this::onSlotClick));
                        }
                    }
                }
                default -> { }
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        int left = (this.width - CANVAS_W) / 2;
        int top = (this.height - CANVAS_H) / 2;
        for (E e : ELEMENTS) {
            int x = left + e.x, y = top + e.y, w = Math.max(1, e.w), h = Math.max(1, e.h);
            switch (e.type) {
                // 可交互组件：自身绘制（由 super.render 处理）
                case "button", "text_field", "checkbox", "slider", "scrollbar", "slot", "slot_grid" -> { }
                // 动态数值装饰：每帧读取 getElementValue 实时绘制
                case "bar", "exp_bar", "heart", "food", "armor" ->
                    renderDynamicValue(g, this.font, e, x, y, w, h, getElementValue(e.name, defaultDynamic(e.type)));
                // 动态文本装饰：每帧读取 getText 实时绘制
                case "label", "tooltip" ->
                    renderTextElement(g, this.font, e, x, y, w, h, getText(e.name, e.text));
                // 其余静态装饰
                default -> renderElement(g, this.font, e, x, y, w, h);
            }
        }
        // 交互组件（按钮/文本框/槽位）由 super.render 渲染
        super.render(g, mx, my, pt);

        // 学习槽燃烧图标：仅在槽位为空时显示在槽位之上，避免被 SlotWidget 背景覆盖
        if (getSlotItem("mai_ru", 0).isEmpty()) {
            g.blit(FURNACE, left + 170, top + 176, 16, 16, 176.0F, 0.0F, 14, 14, 256, 256);
        }

        // 在 bei_jing 背景框之上绘制已学习列表
        renderLearnedList(g, mx, my);
        // 统一物品悬浮提示（已学列表 + 背包槽 + 学习槽），显示标准物品信息框
        ItemStack hovered = getHoveredItem((int) mx, (int) my);
        if (!hovered.isEmpty()) {
            g.renderTooltip(this.font, hovered, (int) mx, (int) my);
        }
        // 跟随鼠标绘制手持（光标）物品（置于最上层）
        renderCursor(g, mx, my);
    }

    private static void renderElement(GuiGraphics g, Font font, E e, int x, int y, int w, int h) {
        String type = e.type;
        if (e.texture != null && !e.texture.isEmpty()) {
            ResourceLocation tex = new ResourceLocation(e.texture);
            int texW = e.texW > 0 ? e.texW : (e.tw > 0 ? e.tw : w);
            int texH = e.texH > 0 ? e.texH : (e.th > 0 ? e.th : h);
            int sw = e.tw > 0 ? e.tw : texW;
            int sh = e.th > 0 ? e.th : texH;
            g.blit(tex, x, y, w, h, e.u, e.v, sw, sh, texW, texH);
            return;
        }
        switch (type) {
            case "panel" -> {
                g.fill(x, y, x + w, y + h, 0xFF000000);
                g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFC6C6C6);
                g.fill(x + 1, y + 1, x + w - 2, y + 3, 0xFFFFFFFF);
                g.fill(x + 1, y + 1, x + 3, y + h - 2, 0xFFFFFFFF);
                g.fill(x + 2, y + h - 3, x + w - 1, y + h - 1, 0xFF555555);
                g.fill(x + w - 3, y + 2, x + w - 1, y + h - 1, 0xFF555555);
            }
            case "arrow" -> g.blit(FURNACE, x, y, w, h, 176.0F, 14.0F, 24, 16, 256, 256);
            case "flame" -> g.blit(FURNACE, x, y, w, h, 176.0F, 0.0F, 14, 14, 256, 256);
            case "tab" -> {
                g.fill(x, y + 2, x + w, y + h, 0xFF000000);
                g.fill(x + 1, y + 3, x + w - 1, y + h - 1, 0xFFC6C6C6);
                g.fill(x + 1, y + 3, x + w - 2, y + 5, 0xFFFFFFFF);
                g.fill(x + 1, y + 3, x + 3, y + h - 2, 0xFFFFFFFF);
                g.fill(x + 2, y + h - 3, x + w - 1, y + h - 1, 0xFF555555);
                g.fill(x + w - 3, y + 4, x + w - 1, y + h - 1, 0xFF555555);
            }
            case "separator" -> {
                g.fill(x, y, x + w, y + 1, 0xFF000000);
                if (h > 1) g.fill(x, y + 1, x + w, y + 2, 0xFF555555);
            }
            case "image" -> {
                g.fill(x, y, x + w, y + h, 0xFF000000);
                g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF7A7A7A);
                g.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFF2E2E2E);
                int n = Math.min(w, h) - 4;
                for (int i = 0; i < n; i++) {
                    g.fill(x + 2 + i, y + 2 + i, x + 3 + i, y + 3 + i, 0xFF5A5A5A);
                    g.fill(x + w - 3 - i, y + 2 + i, x + w - 2 - i, y + 3 + i, 0xFF5A5A5A);
                }
            }
            case "lock" -> {
                g.fill(x + 1, y + 3, x + w - 1, y + h - 1, 0xFF000000);
                g.fill(x + 2, y + 4, x + w - 2, y + h - 2, 0xFFC6C6C6);
                g.fill(x + 3, y, x + 4, y + 4, 0xFFC6C6C6);
                g.fill(x + w - 4, y, x + w - 3, y + 4, 0xFFC6C6C6);
                g.fill(x + 3, y + 1, x + w - 3, y + 2, 0xFFC6C6C6);
                int kx = x + w / 2;
                g.fill(kx - 1, y + 5, kx + 1, y + 6, 0xFF000000);
                g.fill(kx, y + 6, kx + 1, y + h - 3, 0xFF000000);
            }
            default -> { }
        }
    }

    private static void renderDynamicValue(GuiGraphics g, Font font, E e, int x, int y, int w, int h, double value) {
        double v = Math.max(0.0, Math.min(1.0, value));
        switch (e.type) {
            case "bar" -> {
                g.fill(x, y, x + w, y + h, 0xFF000000);
                g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF373737);
                int fill = (int) ((w - 2) * v);
                if (fill > 0) g.fill(x + 1, y + 1, x + 1 + fill, y + h - 1, 0xFF46B846);
                String bt = e.text == null ? "" : e.text;
                if (!bt.isEmpty() && h >= 9) g.drawCenteredString(font, bt, x + w / 2, y + (h - 8) / 2, 0xFFFFFFFF);
            }
            case "exp_bar" -> {
                g.blit(ICONS, x, y, w, h, 0.0F, 64.0F, 182, 5, 256, 256);
                int ew = (int) (w * v);
                if (ew > 0) g.blit(ICONS, x, y, ew, h, 0.0F, 69.0F, (int) (182.0 * v), 5, 256, 256);
            }
            case "heart" -> {
                if (v >= 0.75) g.blit(ICONS, x, y, w, h, 16.0F, 0.0F, 9, 9, 256, 256);
                else if (v >= 0.25) g.blit(ICONS, x, y, w, h, 70.0F, 0.0F, 9, 9, 256, 256);
                else g.blit(ICONS, x, y, w, h, 52.0F, 0.0F, 9, 9, 256, 256);
            }
            case "food" -> {
                if (v >= 0.75) g.blit(ICONS, x, y, w, h, 16.0F, 27.0F, 9, 9, 256, 256);
                else if (v >= 0.25) g.blit(ICONS, x, y, w, h, 70.0F, 27.0F, 9, 9, 256, 256);
                else g.blit(ICONS, x, y, w, h, 52.0F, 27.0F, 9, 9, 256, 256);
            }
            case "armor" -> g.blit(ICONS, x, y, w, h, v >= 0.5 ? 34.0F : 52.0F, 9.0F, 9, 9, 256, 256);
            default -> { }
        }
    }

    private static void renderTextElement(GuiGraphics g, Font font, E e, int x, int y, int w, int h, String text) {
        String t = text == null ? "" : text;
        switch (e.type) {
            case "label" -> g.drawString(font, t, x, y, 0x404040, false);
            case "tooltip" -> {
                g.fill(x, y, x + w, y + h, 0xFF100010);
                g.fill(x, y, x + w, y + 1, 0xFFF8F8F8);
                g.fill(x, y + h - 1, x + w, y + h, 0xFFF8F8F8);
                g.fill(x, y, x + 1, y + h, 0xFFF8F8F8);
                g.fill(x + w - 1, y, x + w, y + h, 0xFFF8F8F8);
                if (!t.isEmpty()) g.drawString(font, t, x + 3, y + 3, 0xFFF8F8F8, false);
            }
            default -> { }
        }
    }

    private static double defaultDynamic(String type) {
        return "exp_bar".equals(type) ? 0.0 : 1.0;
    }

    // ===== 接入点：将 GUI Crafter 槽位接到真实 EmcMenu（学习槽 + 玩家背包） =====

    /** 将界面槽位名映射到 EmcMenu 的 slot 索引；返回 -1 表示无对应菜单槽位（如定价物品槽占位） */
    private int menuSlotFor(String name, int index) {
        return switch (name) {
            case "mai_ru" -> 0;                        // 学习槽（LearnSlot）
            case "wan_jia_wu_pin_lan" -> 1 + index;     // 快捷栏 9 格
            case "wan_jia_bei_bao" -> 10 + index;       // 主背包 27 格
            default -> -1;                              // ding_jia_wu_pin 等占位
        };
    }

    protected ItemStack getSlotItem(String name, int index) {
        if ("ding_jia_wu_pin".equals(name)) return pricingItem;
        int m = menuSlotFor(name, index);
        if (m >= 0 && m < menu.slots.size()) return menu.slots.get(m).getItem();
        return ItemStack.EMPTY;
    }

    protected void onSlotClick(String name, int index) {
        // 定价物品槽：放入/取出物品
        if ("ding_jia_wu_pin".equals(name)) {
            ItemStack carried = menu.getCarried();
            if (!carried.isEmpty()) {
                pricingItem = carried.copy();
                pricingItem.setCount(1);
            } else {
                pricingItem = ItemStack.EMPTY;
            }
            return;
        }
        int m = menuSlotFor(name, index);
        if (m < 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.containerMenu != menu) return;

        if ("mai_ru".equals(name)) {
            // 学习槽：把当前手持（光标）物品学会并返还 EMC
            ItemStack carried = menu.getCarried();
            if (carried.isEmpty()) return;
            String id = BuiltInRegistries.ITEM.getKey(carried.getItem()).toString();
            NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.EmcLearnPacket(id));
            return;
        }
        // Shift+左键：一键卖出背包/物品栏中的物品
        if (hasShiftDown()) {
            ItemStack stack = getSlotItem(name, index);
            if (!stack.isEmpty()) {
                NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.EmcSellSlotPacket(m));
                return;
            }
        }
        // 背包/物品栏：转发给真实菜单，实现拾取/放下/交换
        if (mc.gameMode != null) {
            mc.gameMode.handleInventoryMouseClick(menu.containerId, m, 0, ClickType.PICKUP, mc.player);
        }
    }

    protected void onTextFieldChanged(String name, String value) {
        if ("sou_suo".equals(name)) {
            searchText = value == null ? "" : value;
            learnedPage = 0;
        } else if ("ding_jia".equals(name)) {
            pricingText = value == null ? "" : value;
        }
    }

    protected String getText(String name, String fallback) {
        if ("su_zi".equals(name)) {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                return player.getCapability(EmcPlayerDataProvider.EMC_PLAYER_DATA)
                        .map(d -> formatEmc(d.getEmcBalance()))
                        .orElse("0");
            }
            return "0";
        }
        return fallback;
    }

    // 其余装饰接入点保持默认
    protected void onButtonClick(String name) {
        if ("que_ren_ding_jia".equals(name)) {
            if (pricingItem.isEmpty()) return;
            String id = BuiltInRegistries.ITEM.getKey(pricingItem.getItem()).toString();
            long emc;
            try {
                emc = pricingText.isEmpty() ? 0 : Long.parseLong(pricingText);
            } catch (NumberFormatException e) {
                emc = 0;
            }
            NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.EmcSetPricePacket(id, emc));
        }
    }
    protected void onCheckboxToggle(String name, boolean value) { }
    protected void onSliderChanged(String name, double value) { }
    protected void onScrollChanged(String name, double value) { }
    protected double getElementValue(String name, double fallback) { return fallback; }

    // 超大 EMC 数值缩写，避免溢出 su_zi 标签宽度（33px）
    private static String formatEmc(long value) {
        if (value < 1000L) return Long.toString(value);
        String[] units = {"", "K", "M", "B", "T", "Qa", "Qi"};
        int tier = (int) (Math.log10(value) / 3);
        if (tier >= units.length) tier = units.length - 1;
        double scaled = value / Math.pow(1000.0, tier);
        String num = scaled >= 100 ? String.format("%.0f", scaled)
                : scaled >= 10 ? String.format("%.1f", scaled)
                : String.format("%.2f", scaled);
        return num + units[tier];
    }

    // 小框内紧凑格式（无小数），保证窄格内不溢出
    private static String formatEmcShort(long value) {
        if (value < 1000L) return Long.toString(value);
        String[] units = {"", "K", "M", "B", "T", "Qa", "Qi"};
        int tier = (int) (Math.log10(value) / 3);
        if (tier >= units.length) tier = units.length - 1;
        long scaled = Math.round(value / Math.pow(1000.0, tier));
        return scaled + units[tier];
    }

    // ===== 已学习列表（bei_jing）渲染与交互 =====

    private List<ResourceLocation> getLearnedItems() {
        var player = Minecraft.getInstance().player;
        if (player == null) return new ArrayList<>();
        return player.getCapability(EmcPlayerDataProvider.EMC_PLAYER_DATA)
                .map(d -> new ArrayList<>(d.getLearnedItems()))
                .orElseGet(ArrayList::new);
    }

    private List<ItemStack> buildDisplay(List<ResourceLocation> learned) {
        List<ItemStack> out = new ArrayList<>();
        String f = searchText.toLowerCase();
        var player = Minecraft.getInstance().player;
        var data = player != null ? player.getCapability(EmcPlayerDataProvider.EMC_PLAYER_DATA).orElse(null) : null;
        for (ResourceLocation id : learned) {
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item == Items.AIR) continue;
            if (!f.isEmpty()) {
                ItemStack s = new ItemStack(item);
                String name = s.getHoverName().getString().toLowerCase();
                if (!matchesSearch(id, s, name, f)) continue;
            }
            ItemStack s = new ItemStack(item);
            // 还原已存储的 NBT（手册等物品需要标签才能正确渲染）
            if (data != null) {
                CompoundTag nbt = data.getItemNbt(id);
                if (nbt != null && !nbt.isEmpty()) {
                    s.setTag(nbt.copy());
                }
            }
            out.add(s);
        }
        return out;
    }

    /**
     * 搜索匹配，支持 JEI 风格的前缀：
     * - 以 @ 开头：按来源模组匹配（@modid 前缀匹配 namespace）
     * - 以 # 开头：按物品标签匹配（#modid:tag 或 #tag 子串匹配）
     * - 否则：物品 ID / 显示名的子串匹配（含 JEC 拼音）
     *
     * @param id   物品注册 ID（小写）
     * @param s    用于读取显示名/标签的 ItemStack
     * @param name 已转小写的显示名
     * @param f    已转小写的搜索关键词
     */
    private boolean matchesSearch(ResourceLocation id, ItemStack s, String name, String f) {
        if (f.startsWith("@")) {
            String mod = f.substring(1);
            return mod.isEmpty() || id.getNamespace().contains(mod);
        }
        if (f.startsWith("#")) {
            String tagQuery = f.substring(1);
            return !tagQuery.isEmpty() && itemHasTag(id, tagQuery);
        }
        return JechCompat.matches(id.toString(), f) || JechCompat.matches(name, f);
    }

    /**
     * 判断物品是否拥有匹配查询的标签。
     * tagQuery 为空时由调用方保证不会进入；此处做包含匹配，
     * 支持 #modid:tag 全名或 #tag 局部名。
     */
    private boolean itemHasTag(ResourceLocation id, String tagQuery) {
        var holder = BuiltInRegistries.ITEM.getHolder(ResourceKey.create(Registries.ITEM, id));
        if (holder.isEmpty()) return false;
        return holder.get().tags().anyMatch(tk -> {
            String tag = tk.location().toString(); // 形如 modid:path
            return tag.contains(tagQuery);
        });
    }

    private void renderLearnedList(GuiGraphics g, int mx, int my) {
        int left = (this.width - CANVAS_W) / 2;
        int top = (this.height - CANVAS_H) / 2;
        g.drawString(this.font, "已学习", left + 8, top + 21, 0xFFF8F8F8, false);

        LearnedLayout L = computeLearnedLayout();
        hoveredLearned = ItemStack.EMPTY;
        int start = L.page * L.visible;
        for (int i = 0; i < L.visible; i++) {
            int di = start + i;
            if (di >= L.display.size()) break;
            int col = i % L.cols, row = i / L.cols;
            int x = L.rx + col * L.cellW, y = L.ry + row * L.cellH;
            ItemStack s = L.display.get(di);

            // 物品框
            g.fill(x, y, x + L.cellW, y + L.cellH, 0xFF262626);
            g.fill(x, y, x + L.cellW, y + 1, 0xFF4A4A4A);
            g.fill(x, y + L.cellH - 1, x + L.cellW, y + L.cellH, 0xFF4A4A4A);
            g.fill(x, y, x + 1, y + L.cellH, 0xFF4A4A4A);
            g.fill(x + L.cellW - 1, y, x + L.cellW, y + L.cellH, 0xFF4A4A4A);

            // 图标（左侧）
            g.renderItem(s, x + 1, y + 2);

            // 悬停命中判定
            boolean hover = mx >= x && mx < x + L.cellW && my >= y && my < y + L.cellH;
            if (hover) hoveredLearned = s;

            // EMC（右侧，缩放显示）
            long emc = NetworkHandler.getClientEmc(BuiltInRegistries.ITEM.getKey(s.getItem()));
            String emcStr = formatEmcShort(emc);
            int ew = this.font.width(emcStr);
            int actualX = Math.max(x + 18, x + L.cellW - 2 - ew / 2);
            int actualY = y + 6;
            g.pose().pushPose();
            g.pose().scale(0.5f, 0.5f, 1f);
            g.drawString(this.font, emcStr, actualX * 2, actualY * 2, 0xFF9FE6FF, false);
            g.pose().popPose();

            if (hover) g.fill(x, y, x + L.cellW, y + L.cellH, 0x80FFFFFF); // 悬停高亮
        }

        if (L.display.isEmpty()) {
            g.drawString(this.font, "（暂无已学习物品）", L.rx + 2, L.ry + 4, 0xFF9A9A9A, false);
        }

        // 翻页导航（底部）
        int navY = top + 155;
        int prevX = left + 6, nextX = left + 20;
        g.fill(prevX, navY, prevX + 12, navY + 9, 0xFF000000);
        g.fill(prevX + 1, navY + 1, prevX + 11, navY + 8, 0xFF3A3A3A);
        g.fill(nextX, navY, nextX + 12, navY + 9, 0xFF000000);
        g.fill(nextX + 1, navY + 1, nextX + 11, navY + 8, 0xFF3A3A3A);
        g.drawCenteredString(this.font, "<", prevX + 6, navY + 1, 0xFFFFFFFF);
        g.drawCenteredString(this.font, ">", nextX + 6, navY + 1, 0xFFFFFFFF);
        String pageStr = (L.page + 1) + "/" + L.pages;
        g.drawString(this.font, pageStr, left + 38, navY + 1, 0xFFF8F8F8, false);
    }

    private void renderCursor(GuiGraphics g, int mx, int my) {
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty()) return;
        int x = mx - 8, y = my - 8;
        g.renderItem(carried, x, y);
        g.renderItemDecorations(this.font, carried, x, y);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int left = (this.width - CANVAS_W) / 2;
        int top = (this.height - CANVAS_H) / 2;
        int rx = left + 5, ry = top + 34, rw = 240, rh = 120;
        if (mx >= rx && mx <= rx + rw && my >= ry && my <= ry + rh) {
            learnedPage += (delta > 0 ? -1 : 1); // 滚轮上翻上一页、下翻下一页
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public void onClose() {
        super.onClose();
        // 关闭界面时同步关闭服务端容器菜单，避免菜单残留
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.closeContainer();
        }
    }

    // ===== 已学列表布局（渲染与点击命中共享） =====
    private static final class LearnedLayout {
        List<ItemStack> display;
        int page, pages, rx, ry, rw, rh, cellW, cellH, cols, rows, visible;
    }

    private LearnedLayout computeLearnedLayout() {
        LearnedLayout L = new LearnedLayout();
        L.display = buildDisplay(getLearnedItems());
        L.cellW = 60;                // 每格宽度（图标 + EMC 并排）
        L.cellH = 20;                // 每格高度
        L.cols = 4;                  // 一行 4 个物品
        L.rows = 6;                  // 一页 6 行
        L.visible = L.cols * L.rows; // 每页 24 个
        L.pages = Math.max(1, (L.display.size() + L.visible - 1) / L.visible);
        if (learnedPage >= L.pages) learnedPage = L.pages - 1;
        if (learnedPage < 0) learnedPage = 0;
        L.page = learnedPage;
        int left = (this.width - CANVAS_W) / 2;
        int top = (this.height - CANVAS_H) / 2;
        L.rx = left + 5;
        L.ry = top + 34;
        L.rw = L.cols * L.cellW;     // 网格实际宽度 116
        L.rh = L.rows * L.cellH;     // 网格实际高度 80
        return L;
    }

    /** 命中已学列表中某物品（用于点击提取），未命中返回 EMPTY。 */
    private ItemStack getLearnedItemAt(int mx, int my) {
        LearnedLayout L = computeLearnedLayout();
        int ix = mx - L.rx, iy = my - L.ry;
        if (ix < 0 || iy < 0 || ix >= L.rw || iy >= L.rh) return ItemStack.EMPTY;
        int col = ix / L.cellW, row = iy / L.cellH;
        if (col >= L.cols || row >= L.rows) return ItemStack.EMPTY;
        int idx = row * L.cols + col + L.page * L.visible;
        if (idx < 0 || idx >= L.display.size()) return ItemStack.EMPTY;
        return L.display.get(idx);
    }

    /** 当前鼠标悬停的物品：优先已学列表，其次各背包/学习槽位。 */
    private ItemStack getHoveredItem(int mx, int my) {
        ItemStack learned = getLearnedItemAt(mx, my);
        if (!learned.isEmpty()) return learned;
        int left = (this.width - CANVAS_W) / 2;
        int top = (this.height - CANVAS_H) / 2;
        for (E e : ELEMENTS) {
            if (!e.type.equals("slot") && !e.type.equals("slot_grid")) continue;
            int x = left + e.x, y = top + e.y;
            int cols = e.type.equals("slot_grid") ? Math.max(1, e.w / 18) : 1;
            int rows = e.type.equals("slot_grid") ? Math.max(1, e.h / 18) : 1;
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    int sx = x + c * 18, sy = y + r * 18;
                    if (mx >= sx && mx < sx + 18 && my >= sy && my < sy + 18) {
                        ItemStack s = getSlotItem(e.name, r * cols + c);
                        if (!s.isEmpty()) return s;
                    }
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // 翻页按钮（背景框底部）
        int left = (this.width - CANVAS_W) / 2;
        int top = (this.height - CANVAS_H) / 2;
        int navY = top + 155;
        int prevX = left + 6, nextX = left + 20;
        if (mx >= prevX && mx <= prevX + 12 && my >= navY && my <= navY + 9) {
            learnedPage--;
            return true;
        }
        if (mx >= nextX && mx <= nextX + 12 && my >= navY && my <= navY + 9) {
            learnedPage++;
            return true;
        }
        boolean handled = super.mouseClicked(mx, my, button);
        // 点击已学列表中的物品 → 消耗 EMC 提取（Shift+左键取一组，否则取 1 个）
        ItemStack target = getLearnedItemAt((int) mx, (int) my);
        if (!target.isEmpty()) {
            int amount;
            if (button == 1) amount = -1;                                   // 右键：快捷买入「买满」（用尽 EMC）
            else if (button == 0 && hasShiftDown()) amount = 64;            // Shift+左键：买一组
            else amount = 1;                                                // 左键：买 1 个
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(target.getItem());
            NetworkHandler.CHANNEL.sendToServer(
                    new NetworkHandler.EmcExtractPacket(id.toString(), amount));
            return true;
        }
        return handled;
    }

    private static final class CheckboxWidget extends AbstractWidget {
        private boolean checked;
        private final Consumer<Boolean> onToggle;
        CheckboxWidget(int x, int y, int w, int h, String name, Consumer<Boolean> onToggle) {
            super(x, y, Math.max(8, w), Math.max(8, h), Component.literal(name));
            this.onToggle = onToggle;
        }
        @Override
        public void onClick(double mx, double my) {
            checked = !checked;
            onToggle.accept(checked);
        }
        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            int x = getX(), y = getY(), s = Math.min(getWidth(), getHeight());
            g.fill(x, y, x + s, y + s, 0xFF000000);
            g.fill(x + 1, y + 1, x + s - 1, y + s - 1, checked ? 0xFF3C8520 : 0xFFC0C0C0);
            g.fill(x + 1, y + 1, x + s - 2, y + 3, 0xFFFFFFFF);
            if (checked) {
                g.fill(x + 3, y + s / 2, x + s / 2, y + s - 3, 0xFFFFFFFF);
                g.fill(x + s / 2, y + 3, x + s - 3, y + s / 2, 0xFFFFFFFF);
            }
        }
        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            out.add(NarratedElementType.TITLE, Component.literal(getMessage().getString()));
        }
    }

    private static final class SliderWidget extends AbstractWidget {
        private double value;
        private final Consumer<Double> onChanged;
        SliderWidget(int x, int y, int w, int h, String name, Consumer<Double> onChanged) {
            super(x, y, Math.max(8, w), Math.max(6, h), Component.literal(name));
            this.onChanged = onChanged;
        }
        public double getValue() { return value; }
        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            int midY = y + h / 2;
            g.fill(x, midY - 1, x + w, midY + 1, 0xFF000000);
            g.fill(x, midY, x + w, midY + 1, 0xFF555555);
            int hw = Math.min(8, Math.max(4, w / 6));
            int hx = x + (int) (value * (w - hw));
            g.fill(hx, y, hx + hw, y + h, 0xFF000000);
            g.fill(hx + 1, y + 1, hx + hw - 1, y + h - 1, 0xFFC6C6C6);
        }
        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            if (!this.isMouseOver(mx, my)) return false;
            updateValue(mx);
            return true;
        }
        @Override
        public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
            if (!this.isMouseOver(mx, my)) return false;
            updateValue(mx);
            return true;
        }
        private void updateValue(double mx) {
            int hw = Math.min(8, Math.max(4, getWidth() / 6));
            double v = (mx - getX() - hw / 2.0) / (getWidth() - hw);
            v = Math.max(0.0, Math.min(1.0, v));
            if (Math.abs(v - value) > 1e-4) { value = v; onChanged.accept(value); }
        }
        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            out.add(NarratedElementType.TITLE, Component.literal(getMessage().getString()));
        }
    }

    private static final class ScrollbarWidget extends AbstractWidget {
        private double value;
        private final Consumer<Double> onChanged;
        ScrollbarWidget(int x, int y, int w, int h, String name, Consumer<Double> onChanged) {
            super(x, y, Math.max(6, w), Math.max(8, h), Component.literal(name));
            this.onChanged = onChanged;
        }
        public double getValue() { return value; }
        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            g.fill(x, y, x + w, y + h, 0xFF000000);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF494949);
            int hh = Math.max(6, h / 3);
            int hy = y + (int) (value * (h - hh));
            g.fill(x, hy, x + w, hy + hh, 0xFF000000);
            g.fill(x + 1, hy + 1, x + w - 1, hy + hh - 1, 0xFFC6C6C6);
        }
        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            if (!this.isMouseOver(mx, my)) return false;
            updateValue(my);
            return true;
        }
        @Override
        public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
            if (!this.isMouseOver(mx, my)) return false;
            updateValue(my);
            return true;
        }
        private void updateValue(double my) {
            int hh = Math.max(6, getHeight() / 3);
            double v = (my - getY() - hh / 2.0) / (getHeight() - hh);
            v = Math.max(0.0, Math.min(1.0, v));
            if (Math.abs(v - value) > 1e-4) { value = v; onChanged.accept(value); }
        }
        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            out.add(NarratedElementType.TITLE, Component.literal(getMessage().getString()));
        }
    }

    private static final class SlotWidget extends AbstractWidget {
        private final String slotName;
        private final int index;
        private final BiFunction<String, Integer, ItemStack> supplier;
        private final BiConsumer<String, Integer> clicker;
        SlotWidget(int x, int y, int w, int h, String name, int index,
                     BiFunction<String, Integer, ItemStack> supplier, BiConsumer<String, Integer> clicker) {
            super(x, y, w, h, Component.literal(name));
            this.slotName = name; this.index = index; this.supplier = supplier; this.clicker = clicker;
        }
        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            int x = getX(), y = getY();
            g.blit(GENERIC_54, x, y, getWidth(), getHeight(), 7.0F, 17.0F, 18, 18, 256, 256);
            ItemStack s = supplier.apply(slotName, index);
            if (s != null && !s.isEmpty()) {
                g.renderItem(s, x + 1, y + 1);
                g.renderItemDecorations(Minecraft.getInstance().font, s, x + 1, y + 1);
            }
            if (isMouseOver(mx, my)) {
                RenderSystem.disableDepthTest();
                RenderSystem.depthMask(false);
                RenderSystem.colorMask(true, true, true, false);
                g.fill(x, y, x + getWidth(), y + getHeight(), 0x80FFFFFF);
                RenderSystem.colorMask(true, true, true, true);
                RenderSystem.depthMask(true);
                RenderSystem.enableDepthTest();
            }
        }
        @Override
        public void onClick(double mx, double my) {
            clicker.accept(slotName, index);
        }
        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            out.add(NarratedElementType.TITLE, Component.literal(slotName));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
