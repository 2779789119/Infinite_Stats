package com.infinitestats.client;

import com.infinitestats.network.NetworkHandler;
import com.infinitestats.stats.StatType;
import com.infinitestats.util.AttributeValueMath;
import com.infinitestats.util.EntityPanelToggles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 生物面板 —— 一个生物「能看的」和「能改的」都在这儿。
 *
 * <h2>入口</h2>
 * 手持「生物观察杖」（{@code entity_panel} 属性发的木棍）右键生物，
 * 由服务端下发 {@code OpenEntityPanelPacket}，这里再把实体还原成界面（见 {@link #openForEntity}）。
 *
 * <h2>两页</h2>
 * <ul>
 *   <li><b>属性</b>：这个生物身上所有可显示的属性及其数值。</li>
 *   <li><b>开关</b>：AI / 幼年 / 静音 / 不可见 / 发光 / 无敌 / 无重力，点一下就能切
 *       （服务端执行，回包刷新）。</li>
 * </ul>
 *
 * <h2>数据从哪来</h2>
 * 属性数值直接读本地实体 —— 生物的属性表（含每一条修饰符）本来就随实体追踪同步在客户端，
 * 天然和世界里一致；代价是被标记为「不同步」的属性拿不到，面板里也就不会列出它。
 * <p>
 * 开关状态则<b>只认服务端下发的两个掩码</b>：{@code NoAI} / {@code Silent} 这些字段大多不在同步实体数据里，
 * 客户端本地读到的可能是旧值，所以打开面板时、以及每次切换后都由服务端覆盖一次。
 */
public class EntityPanelScreen extends PanelScreen {

    private static final int GUI_W = 336;
    private static final int GUI_H = 258;

    /** 页签（属性 / 开关）。 */
    private static final int TAB_Y = 56;
    private static final int TAB_H = 16;
    private static final int TAB1_X = 8;
    private static final int TAB2_X = 124;
    private static final int TAB_W = 110;

    /** 列表区（面板坐标）。两页共用，切页时重新算能滚多远。 */
    private static final int LIST_TOP = 74;
    private static final int LIST_H = 148;
    private static final int ROW_H = 18;
    private static final int VISIBLE_ROWS = LIST_H / ROW_H;

    private static final int LIST_X = 4;
    private static final int SCROLL_X = GUI_W - EditorUi.GAP - EditorUi.SCROLL_W;
    private static final int VALUE_RIGHT = SCROLL_X - 4;

    private static final int CLOSE_BTN_Y = 232;

    /** 属性页改数值时，行尾那个输入框的宽度。 */
    private static final int FIELD_W = 78;

    /** 标题行左侧的小图标（观察镜）。 */
    private static final ItemStack HEADER_ICON = new ItemStack(Items.SPYGLASS);

    /**
     * 最常看的属性排在列表最前面（不在表里的按下面的规则往后排）。
     * 键是属性注册名，与 {@code Attributes} 注册表里的写法一致。
     */
    private static final List<String> PINNED = List.of(
            "minecraft:generic.max_health",
            "minecraft:generic.armor",
            "minecraft:generic.armor_toughness",
            "minecraft:generic.attack_damage",
            "minecraft:generic.attack_speed",
            "minecraft:generic.movement_speed",
            "minecraft:generic.knockback_resistance",
            "minecraft:generic.follow_range");

    /** 属性注册名 → 本模组为它准备的中文键（用来兜底「模组自己没提供译文」的属性）。 */
    private static Map<String, StatType> attributeTranslationCache;

    private final LivingEntity entity;
    private final List<Row> rows = new ArrayList<>();
    /** 该生物可用的开关（顺序 = {@link EntityPanelToggles.Toggle} 的枚举顺序）。 */
    private final List<EntityPanelToggles.Toggle> toggles = new ArrayList<>();

    private int applicableMask;
    private int enabledMask;
    private boolean editable;

    /** false = 属性页，true = 开关页。 */
    private boolean showToggles;

    private int scroll;
    private int maxScroll;
    private int hovered = -1;
    private int hoveredToggle = -1;
    private boolean scrollbarDragging;
    private int rebuildCountdown;

    /** 属性页的数值输入框（编辑中才显示，位置每帧跟着所在行走）。 */
    private EditBox valueField;
    /** 正在编辑的属性行下标，-1 = 没有在编辑。 */
    private int editingIndex = -1;

    /** 一行属性。 */
    private record Row(String name, String id, double value, double base,
                       List<String> modifiers, boolean vanilla) {}

    public EntityPanelScreen(LivingEntity entity, int applicableMask, int enabledMask, boolean editable) {
        super(entity.getDisplayName());
        this.entity = entity;
        this.applicableMask = applicableMask;
        this.enabledMask = enabledMask;
        this.editable = editable;
        this.toggles.addAll(collectToggles());
    }

    /**
     * 由网络包调用：把实体 id 还原成界面。
     * <p>
     * 同一个生物的面板已经开着时只刷新开关状态（切开关后服务端会回一发同样的包），不重建界面 ——
     * 否则每次点开关都会把滚动位置和页签重置掉。
     * 找不到实体（已经死了 / 已被卸载 / 还没同步过来）就什么都不做，不会开一个空面板。
     */
    public static void openForEntity(int entityId, int applicableMask, int enabledMask, boolean editable) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) return;

        if (mc.screen instanceof EntityPanelScreen panel && panel.entity.getId() == entityId) {
            panel.applyToggleState(applicableMask, enabledMask, editable);
            return;
        }
        if (mc.level.getEntity(entityId) instanceof LivingEntity living) {
            mc.setScreen(new EntityPanelScreen(living, applicableMask, enabledMask, editable));
        }
    }

    /** 服务端把权威开关状态推回来（也可能把乐观更新纠正掉）。 */
    private void applyToggleState(int applicableMask, int enabledMask, boolean editable) {
        this.applicableMask = applicableMask;
        this.enabledMask = enabledMask;
        this.editable = editable;
        this.toggles.clear();
        this.toggles.addAll(collectToggles());
        updateMaxScroll();
    }

    private List<EntityPanelToggles.Toggle> collectToggles() {
        List<EntityPanelToggles.Toggle> list = new ArrayList<>();
        for (EntityPanelToggles.Toggle toggle : EntityPanelToggles.Toggle.values()) {
            if (EntityPanelToggles.isApplicable(applicableMask, toggle)) list.add(toggle);
        }
        return list;
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
        scroll = 0;
        editingIndex = -1;
        rebuildRows();

        // 改数值用的输入框：常驻但默认隐藏，编辑某一行时才挪到那行、显示出来
        valueField = new EditBox(font, 0, 0, FIELD_W, 14, Component.empty());
        valueField.setMaxLength(24);
        valueField.setFilter(text -> text.matches("-?\\d*\\.?\\d*")); // 数字 / 负号 / 小数点
        valueField.setVisible(false);
        addRenderableWidget(valueField);

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.infinitestats.entity_panel.close"),
                        b -> onClose())
                .bounds(leftPos + GUI_W / 2 - 50, topPos + CLOSE_BTN_Y, 100, 20)
                .build());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (editingIndex >= 0) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                submitEditing();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                cancelEditing();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void tick() {
        super.tick();
        // 生物没了（被打死 / 区块卸载）就自动关掉，避免留着一个悬空面板
        if (minecraft == null || minecraft.level == null || entity.isRemoved()
                || minecraft.level.getEntity(entity.getId()) == null) {
            onClose();
            return;
        }
        // 属性会因为药水 / 装备 / 其它模组而变，定时重读（不必每帧）
        if (--rebuildCountdown <= 0) {
            rebuildCountdown = 10;
            rebuildRows();
        }
        // 重读之后行数可能变了，编辑目标要还在
        if (editingIndex >= rows.size()) cancelEditing();
    }

    // ======================== 数据：属性 ========================

    /**
     * 枚举「这个生物身上存在」的属性。
     * <p>
     * 走属性注册表逐个问 {@code getAttribute(...)} 是否为 null，比直接翻 AttributeMap 稳妥：
     * 注册表里没有的属性不可能有实例，而实例上没有的属性恰好就是 null。
     */
    private void rebuildRows() {
        float keepScrollRatio = showToggles || maxScroll <= 0 ? 0f : scroll / (float) maxScroll;

        List<AttributeInstance> instances = new ArrayList<>();
        for (Attribute attribute : ForgeRegistries.ATTRIBUTES.getValues()) {
            AttributeInstance instance = entity.getAttribute(attribute);
            if (instance != null) instances.add(instance);
        }
        instances.sort(Comparator.comparingInt(this::rank).thenComparing(this::idOf));

        rows.clear();
        for (AttributeInstance instance : instances) {
            String id = idOf(instance);
            List<String> modifiers = new ArrayList<>();
            for (AttributeModifier modifier : instance.getModifiers()) {
                modifiers.add(modifierLine(modifier));
            }
            modifiers.sort(String::compareTo);
            rows.add(new Row(displayName(instance.getAttribute(), id), id,
                    instance.getValue(), instance.getBaseValue(), modifiers, id.startsWith("minecraft:")));
        }

        updateMaxScroll();
        // 数值刷新后列表长度几乎不变，但万一变了也别让滚动位置跳到别处
        if (!showToggles) scroll = Mth.clamp(Math.round(keepScrollRatio * maxScroll), 0, maxScroll);
    }

    /** 当前页最多能滚多少行。 */
    private void updateMaxScroll() {
        int size = showToggles ? toggles.size() : rows.size();
        maxScroll = Math.max(0, size - VISIBLE_ROWS);
        scroll = Mth.clamp(scroll, 0, maxScroll);
    }

    /** 排序权重：置顶清单里的按清单顺序，其余原版在前、模组在后。 */
    private int rank(AttributeInstance instance) {
        int pinned = PINNED.indexOf(idOf(instance));
        if (pinned >= 0) return pinned;
        return 100 + (idOf(instance).startsWith("minecraft:") ? 0 : 1);
    }

    private String idOf(AttributeInstance instance) {
        ResourceLocation id = ForgeRegistries.ATTRIBUTES.getKey(instance.getAttribute());
        return id == null ? "?" : id.toString();
    }

    /**
     * 属性显示名：优先用属性自带的译名（{@code getDescriptionId()}）。
     * 模组没提供译文时，退回本模组给这个属性准备的译名（外部属性的内置翻译就是靠这一步），
     * 两者都没有就显示注册名 —— 至少能看出是哪个模组的属性。
     */
    private static String displayName(Attribute attribute, String id) {
        String key = attribute.getDescriptionId();
        String translated = Component.translatable(key).getString();
        if (!translated.equals(key)) return translated;

        StatType stat = attributeTranslations().get(id);
        if (stat != null) {
            String statKey = stat.getTranslationKey();
            String statName = Component.translatable(statKey).getString();
            if (!statName.equals(statKey)) return statName;
        }
        return id;
    }

    private static Map<String, StatType> attributeTranslations() {
        if (attributeTranslationCache == null) {
            Map<String, StatType> map = new HashMap<>();
            for (StatType stat : StatType.ALL_STATS) {
                if (stat.getAttributeName() != null) map.putIfAbsent(stat.getAttributeName(), stat);
            }
            attributeTranslationCache = map;
        }
        return attributeTranslationCache;
    }

    /** 一条修饰符的说明行（浮窗里用）。 */
    private static String modifierLine(AttributeModifier modifier) {
        String raw = modifier.getName();
        String name = Component.translatable(raw).getString();
        if (name.equals(raw)) name = raw;
        return "§b  " + signed(modifier.getAmount()) + " §7" + name
                + " §8(" + operationName(modifier.getOperation()) + ")";
    }

    private static String operationName(AttributeModifier.Operation operation) {
        String key = switch (operation) {
            case ADDITION -> "gui.infinitestats.entity_panel.op.addition";
            case MULTIPLY_BASE -> "gui.infinitestats.entity_panel.op.multiply_base";
            case MULTIPLY_TOTAL -> "gui.infinitestats.entity_panel.op.multiply_total";
        };
        return Component.translatable(key).getString();
    }

    private static String signed(double value) {
        return (value >= 0 ? "+" : "") + EditorUi.amount(value);
    }

    /** 生物类型名（没译文就退回注册名，避免显示成 {@code entity.xxx.yyy} 这种键名）。 */
    private String typeName() {
        EntityType<?> type = entity.getType();
        String text = type.getDescription().getString();
        // 缺译文时 getDescription() 返回的就是语言键本身，这时显示注册名更可读
        if (text.equals(type.getDescriptionId())) {
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
            return id == null ? text : id.toString();
        }
        return text;
    }

    private String healthText() {
        String current = EditorUi.amount(entity.getHealth());
        String max = EditorUi.amount(entity.getMaxHealth());
        if (entity.getAbsorptionAmount() > 0) {
            return Component.translatable("gui.infinitestats.entity_panel.health_absorption",
                    current, max, EditorUi.amount(entity.getAbsorptionAmount())).getString();
        }
        return Component.translatable("gui.infinitestats.entity_panel.health", current, max).getString();
    }

    // ======================== 渲染 ========================

    @Override
    protected void renderPanel(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(leftPos - 2, topPos - 2, leftPos + GUI_W + 2, topPos + GUI_H + 2, 0x403B82F6);
        g.fill(leftPos, topPos, leftPos + GUI_W, topPos + GUI_H, EditorUi.BG_PANEL);
        g.fill(leftPos, topPos, leftPos + GUI_W, topPos + EditorUi.HEADER_H, EditorUi.BG_HEADER);

        // 标题行：左侧放个「观察镜」图标，点题这是「拿杖观察生物」
        g.renderItem(HEADER_ICON, leftPos + 6, topPos + 4);
        String name = EditorUi.ellipsize(font, title.getString(), GUI_W - 56);
        EditorUi.title(g, font, leftPos + 10, topPos, GUI_W - 20, name);

        g.drawString(font, Component.translatable("gui.infinitestats.entity_panel.type", typeName()).getString(),
                leftPos + 10, topPos + 30, EditorUi.SECONDARY);
        g.drawString(font, healthText(), leftPos + 10, topPos + 44, EditorUi.DANGER);

        renderTabs(g, mouseX, mouseY);

        // 输入框跟着「正在编辑的那一行」走；滚出可见范围就当作放弃编辑
        if (editingIndex >= 0) {
            int visible = editingIndex - scroll;
            if (visible < 0 || visible >= VISIBLE_ROWS) {
                cancelEditing();
            } else {
                valueField.setX(leftPos + VALUE_RIGHT - FIELD_W);
                valueField.setY(LIST_TOP + visible * ROW_H + 2);
            }
        }

        if (showToggles) {
            renderToggleRows(g, mouseX, mouseY);
        } else {
            renderAttributeRows(g, mouseX, mouseY);
        }

        if (maxScroll > 0) {
            EditorUi.scrollbar(g, mouseX, mouseY, leftPos + SCROLL_X, LIST_TOP, LIST_H,
                    scroll, maxScroll, VISIBLE_ROWS);
        }

        g.drawCenteredString(font, hintText(), leftPos + GUI_W / 2, topPos + CLOSE_BTN_Y - 12,
                showToggles && !editable ? EditorUi.DANGER : EditorUi.SECONDARY);
    }

    private String hintText() {
        if (editingIndex >= 0) {
            return Component.translatable("gui.infinitestats.entity_panel.hint_edit").getString();
        }
        if (!editable) {
            return Component.translatable("gui.infinitestats.entity_panel.readonly").getString();
        }
        return Component.translatable(showToggles
                ? "gui.infinitestats.entity_panel.hint_toggles"
                : "gui.infinitestats.entity_panel.hint").getString();
    }

    /** 两个页签：选中的那页底色更亮、并有下方一条高亮线。 */
    private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        drawTab(g, mouseX, mouseY, TAB1_X, false,
                Component.translatable("gui.infinitestats.entity_panel.tab.attributes", rows.size()));
        drawTab(g, mouseX, mouseY, TAB2_X, true,
                Component.translatable("gui.infinitestats.entity_panel.tab.toggles", toggles.size()));
    }

    private void drawTab(GuiGraphics g, int mouseX, int mouseY, int x, boolean page, Component label) {
        boolean selected = showToggles == page;
        boolean hover = mouseX >= leftPos + x && mouseX < leftPos + x + TAB_W
                && mouseY >= TAB_Y && mouseY < TAB_Y + TAB_H;
        g.fill(leftPos + x, TAB_Y, leftPos + x + TAB_W, TAB_Y + TAB_H,
                selected ? EditorUi.BG_ROW_HOVER : hover ? EditorUi.BG_ROW : EditorUi.BG_HEADER);
        g.drawCenteredString(font, EditorUi.ellipsize(font, label.getString(), TAB_W - 6),
                leftPos + x + TAB_W / 2, TAB_Y + (TAB_H - font.lineHeight) / 2,
                selected ? EditorUi.ACCENT : EditorUi.SECONDARY);
        if (selected) g.fill(leftPos + x, TAB_Y + TAB_H - 1, leftPos + x + TAB_W, TAB_Y + TAB_H, EditorUi.ACCENT);
    }

    private void renderAttributeRows(GuiGraphics g, int mouseX, int mouseY) {
        beginPanelScissor(g, leftPos + LIST_X, LIST_TOP, leftPos + VALUE_RIGHT + 2, LIST_TOP + LIST_H);

        hovered = -1;
        hoveredToggle = -1;
        if (rows.isEmpty()) {
            g.drawString(font, Component.translatable("gui.infinitestats.entity_panel.empty").getString(),
                    leftPos + LIST_X + 6, LIST_TOP + 4, EditorUi.SECONDARY);
        }

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int index = scroll + i;
            if (index >= rows.size()) break;

            Row row = rows.get(index);
            int y = LIST_TOP + i * ROW_H;
            if (insideRow(mouseX, mouseY, y)) hovered = index;

            if (hovered == index) {
                g.fill(leftPos + LIST_X, y, leftPos + VALUE_RIGHT + 2, y + ROW_H - 1, EditorUi.BG_ROW_HOVER);
            } else if (index % 2 == 1) {
                g.fill(leftPos + LIST_X, y, leftPos + VALUE_RIGHT + 2, y + ROW_H - 1, EditorUi.BG_ROW);
            }

            int textY = y + (ROW_H - font.lineHeight) / 2;

            // 可编辑且悬停 / 正在编辑：左侧竖条标出「这条能点」/「正在改这条」
            if (editable && (hovered == index || editingIndex == index)) {
                g.fill(leftPos + LIST_X + 1, y + 3, leftPos + LIST_X + 3, y + ROW_H - 4, EditorUi.ACCENT);
            }

            g.drawString(font, EditorUi.ellipsize(font, row.name(), GUI_W - 150),
                    leftPos + LIST_X + 6, textY, row.vanilla() ? EditorUi.PRIMARY : EditorUi.INFO);

            // 正在编辑的那一行数值被输入框盖住，就不重复画了
            if (editingIndex != index) {
                String value = EditorUi.amount(row.value());
                int valueX = leftPos + VALUE_RIGHT - font.width(value);
                g.drawString(font, value, valueX, textY, EditorUi.GOLD);

                // 有修饰符的行在数值左边标个「+N」，提示这里还有细节可看
                if (!row.modifiers().isEmpty()) {
                    String count = "+" + row.modifiers().size();
                    g.drawString(font, count, valueX - 6 - font.width(count), textY, EditorUi.INFO);
                }
            }
        }

        endPanelScissor(g);
    }

    private void renderToggleRows(GuiGraphics g, int mouseX, int mouseY) {
        beginPanelScissor(g, leftPos + LIST_X, LIST_TOP, leftPos + VALUE_RIGHT + 2, LIST_TOP + LIST_H);

        hovered = -1;
        hoveredToggle = -1;
        if (toggles.isEmpty()) {
            g.drawString(font, Component.translatable("gui.infinitestats.entity_panel.toggle.none").getString(),
                    leftPos + LIST_X + 6, LIST_TOP + 4, EditorUi.SECONDARY);
        }

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int index = scroll + i;
            if (index >= toggles.size()) break;

            EntityPanelToggles.Toggle toggle = toggles.get(index);
            boolean on = EntityPanelToggles.isEnabled(enabledMask, toggle);
            int y = LIST_TOP + i * ROW_H;
            boolean hover = insideRow(mouseX, mouseY, y);
            if (hover) hoveredToggle = index;

            if (hover && editable) {
                g.fill(leftPos + LIST_X, y, leftPos + VALUE_RIGHT + 2, y + ROW_H - 1, EditorUi.BG_ROW_HOVER);
            } else if (index % 2 == 1) {
                g.fill(leftPos + LIST_X, y, leftPos + VALUE_RIGHT + 2, y + ROW_H - 1, EditorUi.BG_ROW);
            }

            int textY = y + (ROW_H - font.lineHeight) / 2;
            g.drawString(font, EditorUi.ellipsize(font, Component.translatable(toggle.getNameKey()).getString(),
                            GUI_W - 120), leftPos + LIST_X + 6, textY,
                    editable ? EditorUi.PRIMARY : EditorUi.SECONDARY);

            String state = Component.translatable(on
                    ? "gui.infinitestats.entity_panel.toggle.on"
                    : "gui.infinitestats.entity_panel.toggle.off").getString();
            int stateColor = on ? EditorUi.ACCENT : EditorUi.DANGER;
            if (!editable) stateColor = on ? 0xFF4ADE80 : EditorUi.SECONDARY;
            g.drawString(font, state, leftPos + VALUE_RIGHT - font.width(state), textY, stateColor);

            // 可点又悬停着，就在行尾画一个「可点」的小箭头，省得玩家以为这行只是文字
            if (hover && editable) {
                g.drawString(font, "»", leftPos + VALUE_RIGHT - font.width(state) - 10, textY, EditorUi.ACCENT);
            }
        }

        endPanelScissor(g);
    }

    private boolean insideRow(int mouseX, int mouseY, int rowY) {
        return mouseY >= rowY && mouseY < rowY + ROW_H
                && mouseX >= leftPos + LIST_X && mouseX < leftPos + VALUE_RIGHT + 2;
    }

    /** 鼠标（面板坐标）落在属性页第几行，没落在任何行返回 -1。 */
    private int rowIndexAt(int mouseX, int mouseY) {
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int index = scroll + i;
            if (index >= rows.size()) break;
            if (insideRow(mouseX, mouseY, LIST_TOP + i * ROW_H)) return index;
        }
        return -1;
    }

    @Override
    protected void renderPanelOverlay(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (showToggles) {
            if (hoveredToggle >= 0 && hoveredToggle < toggles.size()) {
                EditorUi.tooltip(g, mouseX, mouseY, toggleTooltip(toggles.get(hoveredToggle)), EditorUi.ACCENT);
            }
            return;
        }
        // 正在编辑的那一行不弹浮窗，免得盖住输入框
        if (hovered >= 0 && hovered < rows.size() && hovered != editingIndex) {
            EditorUi.tooltip(g, mouseX, mouseY, tooltipLines(rows.get(hovered)), EditorUi.INFO);
        }
    }

    /** 悬停行的浮窗：注册名 + 基础值 / 最终值 / 默认值 + 取值范围 + 逐条修饰符。 */
    private List<String> tooltipLines(Row row) {
        List<String> lines = new ArrayList<>();
        lines.add("§f§l" + row.name());
        lines.add("§7" + Component.translatable("gui.infinitestats.entity_panel.id", row.id()).getString());
        lines.add("§7" + Component.translatable("gui.infinitestats.entity_panel.base",
                EditorUi.amount(row.base())).getString());
        lines.add("§7" + Component.translatable("gui.infinitestats.entity_panel.final",
                EditorUi.amount(row.value())).getString());

        Attribute attribute = attributeOf(row.id());
        if (attribute != null) {
            double def = entity.getAttributes().getBaseValue(attribute);
            lines.add("§7" + Component.translatable("gui.infinitestats.entity_panel.default",
                    EditorUi.amount(def)).getString());
            if (attribute instanceof RangedAttribute ranged) {
                lines.add("§7" + Component.translatable("gui.infinitestats.entity_panel.range",
                        EditorUi.amount(ranged.getMinValue()),
                        EditorUi.amount(ranged.getMaxValue())).getString());
            }
        }

        if (!row.modifiers().isEmpty()) {
            lines.add("§6" + Component.translatable("gui.infinitestats.entity_panel.modifiers",
                    row.modifiers().size()).getString());
            lines.addAll(row.modifiers());
        }
        if (editable) {
            lines.add("§8" + Component.translatable("gui.infinitestats.entity_panel.edit_hint").getString());
        } else {
            lines.add("§8" + Component.translatable("gui.infinitestats.entity_panel.readonly_hint").getString());
        }
        return lines;
    }

    /** 悬停开关行的浮窗：这个开关是干什么的 + 怎么用。 */
    private List<String> toggleTooltip(EntityPanelToggles.Toggle toggle) {
        List<String> lines = new ArrayList<>();
        lines.add("§f§l" + Component.translatable(toggle.getNameKey()).getString());
        lines.addAll(EditorUi.wrap(font, "§7" + Component.translatable(toggle.getDescKey()).getString(), 190));
        lines.add("§8" + Component.translatable(editable
                ? "gui.infinitestats.entity_panel.toggle.click"
                : "gui.infinitestats.entity_panel.readonly").getString());
        return lines;
    }

    // ======================== 输入 ========================

    @Override
    protected boolean onPanelClick(int mouseX, int mouseY, int button) {
        if (button != 0) return false;

        // 有正在编辑的行：点到自己就交给输入框处理（挪光标），点到别处先提交再继续
        if (editingIndex >= 0 && !showToggles) {
            if (rowIndexAt(mouseX, mouseY) == editingIndex) return false;
            submitEditing();
        }

        // 页签
        if (inTab(mouseX, mouseY, TAB1_X) && showToggles) {
            showToggles = false;
            scroll = 0;
            updateMaxScroll();
            return true;
        }
        if (inTab(mouseX, mouseY, TAB2_X) && !showToggles) {
            showToggles = true;
            scroll = 0;
            updateMaxScroll();
            return true;
        }

        // 滚动条：按下即定位 + 开始拖拽
        if (maxScroll > 0 && EditorUi.onScrollbar(mouseX, mouseY, leftPos + SCROLL_X, LIST_TOP, LIST_H)) {
            scrollbarDragging = true;
            scroll = EditorUi.scrollFromMouseY(mouseY, LIST_TOP, LIST_H, maxScroll, VISIBLE_ROWS);
            return true;
        }

        // 属性页：点某一行改它的基础值；Shift + 点击恢复该生物类型的默认值
        if (!showToggles && editable) {
            int index = rowIndexAt(mouseX, mouseY);
            if (index >= 0) {
                if (Screen.hasShiftDown()) {
                    resetToDefault(index);
                } else {
                    startEditing(index);
                }
                return true;
            }
        }

        // 开关页：点某一行就切那个开关
        if (showToggles && editable) {
            for (int i = 0; i < VISIBLE_ROWS; i++) {
                int index = scroll + i;
                if (index >= toggles.size()) break;
                if (!insideRow(mouseX, mouseY, LIST_TOP + i * ROW_H)) continue;

                EntityPanelToggles.Toggle toggle = toggles.get(index);
                boolean next = !EntityPanelToggles.isEnabled(enabledMask, toggle);
                // 乐观更新：先本地翻，服务端回包后以权威值覆盖（被拒绝就原地弹回）
                if (next) enabledMask |= toggle.getBit();
                else enabledMask &= ~toggle.getBit();
                NetworkHandler.CHANNEL.sendToServer(
                        new NetworkHandler.EntityPanelActionPacket(entity.getId(), toggle.ordinal(), next));
                return true;
            }
        }
        return false;
    }

    private boolean inTab(int mouseX, int mouseY, int x) {
        return mouseX >= leftPos + x && mouseX < leftPos + x + TAB_W
                && mouseY >= TAB_Y && mouseY < TAB_Y + TAB_H;
    }

    // ======================== 属性基础值编辑 ========================

    /** 把输入框挪到这一行、填上当前**最终值**并聚焦。 */
    private void startEditing(int index) {
        if (index < 0 || index >= rows.size()) return;
        editingIndex = index;
        valueField.setValue(EditorUi.amount(rows.get(index).value()));
        valueField.setVisible(true);
        setFocused(valueField);
    }

    private void cancelEditing() {
        editingIndex = -1;
        if (valueField != null) {
            valueField.setValue("");
            valueField.setVisible(false);
        }
        setFocused(null);
    }

    /** 回车：把输入的数字提交给服务端（非法输入直接放弃，不报错也不发包）。 */
    private void submitEditing() {
        if (editingIndex < 0 || editingIndex >= rows.size()) {
            cancelEditing();
            return;
        }
        double value;
        try {
            value = Double.parseDouble(valueField.getValue().trim());
        } catch (NumberFormatException e) {
            cancelEditing();
            return;
        }
        if (!Double.isFinite(value)) {
            cancelEditing();
            return;
        }
        applyFinalValue(editingIndex, value);
    }

    /**
     * Shift + 点击：恢复成「该生物类型自带的默认基础值」。
     * <p>
     * 包里的语义统一是「目标最终值」，所以这里先把默认基础值换算成它会产生的那份最终值再提交 ——
     * 「恢复默认」因此也只是一次普通的最终值写入，不必给网络包再加一个模式开关。
     */
    private void resetToDefault(int index) {
        if (index < 0 || index >= rows.size()) return;
        Row row = rows.get(index);
        Attribute attribute = attributeOf(row.id());
        if (attribute == null) return;
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) return;
        double target = AttributeValueMath.finalForBase(instance,
                entity.getAttributes().getBaseValue(attribute));
        if (Double.isNaN(target)) return;
        applyFinalValue(index, target);
    }

    /**
     * 提交一个**目标最终值**：先本地乐观更新（只改这一行的显示数据），再发包让服务端执行。
     * <p>
     * 基础值用同一套公式在本地反算一份，纯粹为了这一秒内浮窗能自洽；
     * 权威值走原版属性同步回来，面板每 10 tick 也会重读本地实体，
     * 所以被服务端夹过范围 / 拒绝的写入会在一秒内自动显示成真值。
     */
    private void applyFinalValue(int index, double target) {
        Row row = rows.get(index);
        Attribute attribute = attributeOf(row.id());
        AttributeInstance instance = attribute == null ? null : entity.getAttribute(attribute);
        double base = instance == null ? Double.NaN : AttributeValueMath.baseForFinal(instance, target);
        if (Double.isNaN(base)) base = row.base();
        rows.set(index, new Row(row.name(), row.id(), target, base, row.modifiers(), row.vanilla()));
        NetworkHandler.CHANNEL.sendToServer(
                new NetworkHandler.EntityPanelAttributePacket(entity.getId(), row.id(), target));
        cancelEditing();
    }

    /** 注册名 → 属性实例（找不到返回 null）。 */
    private static Attribute attributeOf(String attributeId) {
        ResourceLocation id = ResourceLocation.tryParse(attributeId);
        return id == null ? null : ForgeRegistries.ATTRIBUTES.getValue(id);
    }

    @Override
    protected boolean onPanelDrag(int mouseX, int mouseY, int button, int dragX, int dragY) {
        if (scrollbarDragging && maxScroll > 0) {
            scroll = EditorUi.scrollFromMouseY(mouseY, LIST_TOP, LIST_H, maxScroll, VISIBLE_ROWS);
            return true;
        }
        return false;
    }

    @Override
    protected boolean onPanelRelease(int mouseX, int mouseY, int button) {
        if (scrollbarDragging) {
            scrollbarDragging = false;
            return true;
        }
        return false;
    }

    @Override
    protected boolean onPanelScroll(int mouseX, int mouseY, double delta) {
        if (delta == 0 || maxScroll <= 0) return false;
        scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, maxScroll);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
