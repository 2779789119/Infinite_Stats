package com.infinitestats.client;

import com.infinitestats.network.EntityPanelActionPacket;
import com.infinitestats.network.EntityPanelAttributePacket;
import com.infinitestats.network.NetworkHandler;
import com.infinitestats.stats.StatType;
import com.infinitestats.util.EntityPanelToggles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
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
 * 手持「生物观察杖」（{@code entity_panel} 属性发的木棍）右键生物，由服务端下发
 * {@code OpenEntityPanelPacket}，这里再把实体 id 还原成界面（见 {@link #openForEntity}）。
 *
 * <h2>两页</h2>
 * <ul>
 *   <li><b>属性</b>：这个生物身上所有可显示的属性及其数值；点某个数值可以<b>直接改基础值</b>
 *       （回车提交、Esc 取消），服务端会夹进该属性自身的取值范围。</li>
 *   <li><b>开关</b>：AI / 幼年 / 静音 / 不可见 / 发光 / 无敌 / 无重力，点一下就能切
 *       （服务端执行，回包刷新成权威状态）。</li>
 * </ul>
 *
 * <h2>数据从哪来</h2>
 * 属性数值直接读本地实体 —— 生物的属性表（含每一条修饰符）本来就随实体追踪同步在客户端，
 * 天然和世界里一致；代价是被标记为「不同步」的属性拿不到，面板里也就不会列出它。
 * <p>
 * 开关状态则<b>只认服务端下发的两个掩码</b>：{@code NoAI} / {@code Silent} 这些字段大多不在同步实体数据里，
 * 客户端本地读到的可能是旧值，所以打开面板时、以及每次切换后都由服务端覆盖一次。
 *
 * <h2>1.21.1 适配要点（与 1.20.1 主线的差异）</h2>
 * <ul>
 *   <li><b>属性 id 去掉了 {@code generic.} 前缀</b>：主线的置顶清单写 {@code minecraft:generic.max_health}，
 *       1.21 起是 {@code minecraft:max_health}（见 {@link #PINNED}），不改会一个都匹配不上、置顶失效；</li>
 *   <li><b>属性注册表</b>：{@code ForgeRegistries.ATTRIBUTES} → 1.21 的 {@code BuiltInRegistries.ATTRIBUTE}，
 *       且属性以 {@link Holder} 参与判定（{@code LivingEntity#getAttribute(Holder)}）；</li>
 *   <li><b>属性修饰符改名了</b>：1.20.1 的 {@code getName()}/{@code getAmount()}/{@code getOperation()}
 *       在 1.21 变成 record 访问器 {@code id()}/{@code amount()}/{@code operation()}，
 *       而且修饰符不再带「名字」，只有一个 {@link ResourceLocation} id —— 显示时按
 *       {@code attribute.modifier.<命名空间>.<路径>} 试译文，取不到就直接显示 id；</li>
 *   <li>界面本身按本分支的 {@link Screen} + {@link EditorUi} 风格重写（主线那版依赖自适应的
 *       {@code PanelScreen} 缩放基类，本分支尚未移植，见后续「界面自适应」批次）。</li>
 * </ul>
 */
public class EntityPanelScreen extends PanelScreen {

    private static final int GUI_W = 336;
    private static final int GUI_H = 292;

    /** 页签（属性 / 开关 / 效果 / 动作）。 */
    private static final int TAB_Y = 56;
    private static final int TAB_H = 16;
    private static final int TAB_W = 78;
    private static final int[] TAB_X = {6, 88, 170, 252};

    /** 列表区。两页共用，切页时重新算能滚多远。 */
    private static final int LIST_TOP = 98;
    private static final int LIST_H = 146;
    private static final int ROW_H = 18;
    private static final int VISIBLE_ROWS = LIST_H / ROW_H;

    private static final int LIST_X = 4;
    private static final int SCROLL_X = GUI_W - EditorUi.GAP - EditorUi.SCROLL_W;
    private static final int VALUE_RIGHT = SCROLL_X - 4;

    private static final int CLOSE_BTN_Y = 266;

    /** 属性页改数值时，行尾那个输入框的宽度。 */
    private static final int FIELD_W = 78;

    /** 标题行左侧的小图标（观察镜）。 */
    private static final ItemStack HEADER_ICON = new ItemStack(Items.SPYGLASS);

    /**
     * 最常看的属性排在列表最前面（不在表里的按下面的规则往后排）。
     * 键是属性注册名，1.21 起没有 {@code generic.} 前缀。
     */
    private static final List<String> PINNED = List.of(
            "minecraft:max_health",
            "minecraft:armor",
            "minecraft:armor_toughness",
            "minecraft:attack_damage",
            "minecraft:attack_speed",
            "minecraft:movement_speed",
            "minecraft:knockback_resistance",
            "minecraft:follow_range");

    /** 属性注册名 → 本模组为它准备的中文键（用来兜底「模组自己没提供译文」的属性）。 */
    private static Map<String, StatType> attributeTranslationCache;

    private final LivingEntity entity;
    private final List<Row> rows = new ArrayList<>();
    /** 该生物可用的开关（顺序 = {@link EntityPanelToggles.Toggle} 的枚举顺序）。 */
    private final List<EntityPanelToggles.Toggle> toggles = new ArrayList<>();

    private int applicableMask;
    private int enabledMask;
    private boolean editable;

    /** 0 = 属性页，1 = 开关页，2 = 效果页，3 = 动作页。 */
    private int page;
    /** 兼容旧分支判断：只有开关页为 true。 */
    private boolean showToggles;

    /** 效果页的一行。 */
    private record EffectRow(String id, String name, int level, int duration) {}

    private final List<EffectRow> effects = new ArrayList<>();
    private int hoveredEffect = -1;
    /** 效果行「连点两下移除」的待确认目标与时间戳。 */
    private int pendingEffectIndex = -1;
    private long pendingEffectAt;

    /** 动作页：等待二次确认的动作下标（只有危险动作会用到）。 */
    private int pendingAction = -1;
    private long pendingActionAt;

    /** 属性锁：属性 id → 锁模式（0 无 / 1 基 / 2 终），以及锁的目标值。 */
    private final Map<String, Integer> lockModes = new HashMap<>();
    private final Map<String, Double> lockValues = new HashMap<>();

    /**
     * 「暂停放行」的截止时间（{@link System#currentTimeMillis()}）。
     * <p>
     * 面板是暂停界面 —— 单人游戏打开它世界就停了：服务端不 tick，我们发过去的
     * 「改属性 / 切开关 / 执行动作」的包就一直躺在队列里，表现就是「改完必须关掉面板重开才生效」。
     * 所以每次发包后临时放行 80ms，让服务端把包处理掉、属性同步回来，再自动暂停。
     * 用时间而不是帧数：不受帧率影响，服务器 20 tick/s 下至少能跑到一两个 tick。
     * 时间刻意压得比一个 tick 稍长而不是更长 —— 放行期间世界是真在跑的，怪物会动、会打人。
     */
    private long pauseFreeUntil;

    /** 发包后放行世界的时长（毫秒）。一个服务端 tick 是 50ms，80ms 保证至少跑一次。 */
    private static final long PAUSE_FREE_MS = 80L;

    private int scroll;
    private int maxScroll;
    private int hovered = -1;
    private int hoveredToggle = -1;
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
     * 否则每次点开关都会把滚动位置和页签重置掉。找不到实体（已经死了 / 已被卸载 / 还没同步过来）
     * 就什么都不做，不会开一个空面板。
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

    /**
     * 由 {@code EntityPanelLockSyncPacket} 调用：把服务端的锁表套到当前面板上。
     * <p>
     * 面板没开着（或已切到别的生物）时直接丢弃 —— 行尾的「基 / 终」只服务于正在看的那只。
     */
    public static void applyLockSnapshot(int entityId, net.minecraft.nbt.CompoundTag locks) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof EntityPanelScreen panel)) return;
        if (panel.entity.getId() != entityId) return;

        panel.lockModes.clear();
        panel.lockValues.clear();
        if (locks != null) {
            for (String key : locks.getAllKeys()) {
                var one = locks.getCompound(key);
                int mode = one.getInt("mode");
                if (mode == 0) continue;
                panel.lockModes.put(key, mode);
                panel.lockValues.put(key, one.getDouble("value"));
            }
        }
    }

    /** 服务端下发了新效果 / 动作结果时，重建非属性页的数据。 */
    public void refreshDynamicContent() {
        rebuildEffects();
    }

    /** 服务端把权威开关状态推回来（也可能把乐观更新纠正掉）。 */
    private void applyToggleState(int applicableMask, int enabledMask, boolean editable) {
        this.applicableMask = applicableMask;
        this.enabledMask = enabledMask;
        this.editable = editable;
        this.toggles.clear();
        this.toggles.addAll(collectToggles());
        rebuildEffects();
        updateMaxScroll();
    }

    /** 读本地实体的药水效果（效果页）。客户端持有的效果表就是服务端同步下来的，够用。 */
    private void rebuildEffects() {
        effects.clear();
        for (var inst : entity.getActiveEffects()) {
            if (inst.getEffect() == null) continue;
            String id = com.infinitestats.util.EntityPanelActions.effectId(inst);
            if (id.isEmpty()) continue;
            effects.add(new EffectRow(id,
                    inst.getEffect().value().getDisplayName().getString(),
                    inst.getAmplifier() + 1,
                    inst.getDuration()));
        }
        effects.sort(Comparator.comparing(EffectRow::name, String.CASE_INSENSITIVE_ORDER));
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
        // 面板坐标从 (0,0) 起：居中、缩放与拖动都由 PanelScreen 负责
        this.leftPos = 0;
        this.topPos = 0;
        this.scroll = 0;
        this.editingIndex = -1;
        rebuildRows();

        // 改数值用的输入框：常驻但默认隐藏，编辑某一行时才挪到那行、显示出来
        this.valueField = new EditBox(font, leftPos, topPos, FIELD_W, 14, Component.empty());
        this.valueField.setMaxLength(24);
        this.valueField.setFilter(text -> text.matches("-?\\d*\\.?\\d*")); // 数字 / 负号 / 小数点
        this.valueField.setVisible(false);
        addRenderableWidget(this.valueField);

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.infinitestats.entity_panel.close"),
                        b -> onClose())
                .bounds(leftPos + GUI_W / 2 - 50, topPos + CLOSE_BTN_Y, 100, 20)
                .build());
    }

    // ======================== 面板坐标（本分支 Screens 的惯例） ========================

    // leftPos / topPos 与 toPanelX / toPanelY 现在由 PanelScreen 提供
    // （面板坐标恒以 (0,0) 为原点，且换算已把界面缩放算进去）

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
        // 放行期间每帧重读：服务端写入 + 属性同步包回来的那点延迟里，界面能第一时间显示新值
        if (System.currentTimeMillis() < pauseFreeUntil) {
            rebuildCountdown = 1;
        }
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
            rebuildEffects();
        }
        // 重读之后行数可能变了，编辑目标要还在
        if (editingIndex >= rows.size()) cancelEditing();
    }

    /**
     * 平时暂停（面板里翻属性、输数值时世界不会偷袭你）；
     * 刚发过包的那几十毫秒放行，让服务端真的处理这次改动（见 {@link #pauseFreeUntil}）。
     */
    @Override
    public boolean isPauseScreen() {
        return System.currentTimeMillis() >= pauseFreeUntil;
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(null);
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

        List<Holder.Reference<Attribute>> holders = BuiltInRegistries.ATTRIBUTE.holders().toList();
        List<AttributeInstance> instances = new ArrayList<>();
        for (Holder<Attribute> holder : holders) {
            AttributeInstance instance = entity.getAttribute(holder);
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
            rows.add(new Row(displayName(instance.getAttribute().value(), id), id,
                    instance.getValue(), instance.getBaseValue(), modifiers, id.startsWith("minecraft:")));
        }

        updateMaxScroll();
        // 数值刷新后列表长度几乎不变，但万一变了也别让滚动位置跳到别处
        if (page == 0) scroll = Mth.clamp(Math.round(keepScrollRatio * maxScroll), 0, maxScroll);
    }

    /** 动作页里「对这只生物成立」的动作（顺序 = 枚举顺序）。 */
    private List<com.infinitestats.util.EntityPanelActions.Action> actionRows() {
        List<com.infinitestats.util.EntityPanelActions.Action> out = new ArrayList<>();
        for (com.infinitestats.util.EntityPanelActions.Action action
                : com.infinitestats.util.EntityPanelActions.Action.values()) {
            if (action.appliesTo(entity)) out.add(action);
        }
        return out;
    }

    /** 当前页最多能滚多少行。 */
    private void updateMaxScroll() {
        int size = switch (page) {
            case 3 -> actionRows().size();
            case 2 -> effects.size();
            case 1 -> toggles.size();
            default -> rows.size();
        };
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
        ResourceLocation id = BuiltInRegistries.ATTRIBUTE.getKey(instance.getAttribute().value());
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

    /**
     * 一条修饰符的说明行（浮窗里用）。
     * <p>
     * 1.21 的修饰符不再有「名字」，只剩一个 id，所以这里按
     * {@code attribute.modifier.<命名空间>.<路径>} 试一次译文，取不到就直接显示 id。
     */
    private static String modifierLine(AttributeModifier modifier) {
        ResourceLocation id = modifier.id();
        String key = "attribute.modifier." + id.getNamespace() + "." + id.getPath();
        String name = Component.translatable(key).getString();
        if (name.equals(key)) name = id.toString();
        return "§b  " + signed(modifier.amount()) + " §7" + name
                + " §8(" + operationName(modifier.operation()) + ")";
    }

    private static String operationName(AttributeModifier.Operation operation) {
        String key = switch (operation) {
            case ADD_VALUE -> "gui.infinitestats.entity_panel.op.addition";
            case ADD_MULTIPLIED_BASE -> "gui.infinitestats.entity_panel.op.multiply_base";
            case ADD_MULTIPLIED_TOTAL -> "gui.infinitestats.entity_panel.op.multiply_total";
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
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
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
        // 基类传入的鼠标坐标已经是面板坐标，不需要再换算
        int mx = mouseX;
        int my = mouseY;
        hovered = -1;
        hoveredToggle = -1;

        EditorUi.panel(g, leftPos, topPos, GUI_W, GUI_H);
        EditorUi.title(g, font, leftPos, topPos, GUI_W, title.getString());

        renderHeader(g);
        renderTabs(g, mx, my);

        switch (page) {
            case 3 -> renderActionRows(g, mx, my);
            case 2 -> renderEffectRows(g, mx, my);
            case 1 -> renderToggleRows(g, mx, my);
            default -> renderAttributeRows(g, mx, my);
        }

        EditorUi.dividerH(g, leftPos + LIST_X, topPos + LIST_TOP - 2, GUI_W - LIST_X * 2 - EditorUi.SCROLL_W);
        renderScrollbar(g, mx, my);

        // 页脚提示：能不能改 / 怎么改
        String hint = hintText();
        g.drawString(font, hint, leftPos + LIST_X + 2, topPos + CLOSE_BTN_Y - 12, EditorUi.SECONDARY);

    }

    /** 行内浮窗画在控件之上。 */
    @Override
    protected void renderPanelOverlay(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderRowTooltip(g, mouseX, mouseY);
    }

    private void renderHeader(GuiGraphics g) {
        g.renderItem(HEADER_ICON, leftPos + LIST_X + 2, topPos + 20);
        g.drawString(font, EditorUi.ellipsize(font, entity.getDisplayName().getString(), 140),
                leftPos + LIST_X + 22, topPos + 21, EditorUi.PRIMARY);
        g.drawString(font, EditorUi.ellipsize(font, typeName(), 90),
                leftPos + LIST_X + 22, topPos + 31, EditorUi.SECONDARY);
        String health = healthText();
        g.drawString(font, health, leftPos + GUI_W - EditorUi.GAP - font.width(health),
                topPos + 27, healthColor());

        // 页签与列表之间的信息条（与主版本的顶部信息区对齐）：坐标 / 距离 / 当前页条目数
        double distance = (minecraft == null || minecraft.player == null)
                ? 0.0 : Math.sqrt(entity.distanceToSqr(minecraft.player));
        String info = String.format(java.util.Locale.ROOT,
                "XYZ  %.0f / %.0f / %.0f    ·    %.1f m",
                entity.getX(), entity.getY(), entity.getZ(), distance);
        g.drawString(font, EditorUi.ellipsize(font, info, GUI_W - LIST_X * 2 - 100),
                leftPos + LIST_X + 2, topPos + 78, EditorUi.SECONDARY);

        int count = switch (page) {
            case 3 -> actionRows().size();
            case 2 -> effects.size();
            case 1 -> toggles.size();
            default -> rows.size();
        };
        String total = count + " " + Component.translatable(
                "gui.infinitestats.entity_panel.entries").getString();
        g.drawString(font, total, leftPos + GUI_W - EditorUi.GAP - font.width(total),
                topPos + 78, EditorUi.GOLD);
        g.drawString(font, healthText()
                        + "  ·  " + typeName().replaceAll("§.", ""),
                leftPos + LIST_X + 2, topPos + 89, EditorUi.INFO);
    }

    private int healthColor() {
        float ratio = entity.getMaxHealth() <= 0 ? 0f : entity.getHealth() / entity.getMaxHealth();
        if (ratio > 0.5f) return EditorUi.ACCENT;
        if (ratio > 0.25f) return EditorUi.GOLD;
        return EditorUi.DANGER;
    }

    private String hintText() {
        if (!editable) {
            return Component.translatable("gui.infinitestats.entity_panel.hint.readonly").getString();
        }
        return switch (page) {
            case 3 -> Component.translatable("gui.infinitestats.entity_panel.hint.actions").getString();
            case 2 -> Component.translatable("gui.infinitestats.entity_panel.hint.effects").getString();
            case 1 -> Component.translatable("gui.infinitestats.entity_panel.hint.toggles").getString();
            default -> Component.translatable("gui.infinitestats.entity_panel.hint.attributes").getString();
        };
    }

    private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        Component[] labels = {
                Component.translatable("gui.infinitestats.entity_panel.tab.attributes"),
                Component.translatable("gui.infinitestats.entity_panel.tab.toggles"),
                Component.translatable("gui.infinitestats.entity_panel.tab.effects",
                        String.valueOf(effects.size())),
                Component.translatable("gui.infinitestats.entity_panel.tab.actions",
                        String.valueOf(actionRows().size()))
        };
        for (int i = 0; i < TAB_X.length; i++) {
            drawTab(g, mouseX, mouseY, TAB_X[i], i, labels[i]);
        }
    }

    private void drawTab(GuiGraphics g, int mouseX, int mouseY, int x, int tabIndex, Component label) {
        boolean active = page == tabIndex;
        boolean hover = mouseX >= x && mouseX < x + TAB_W && mouseY >= TAB_Y && mouseY < TAB_Y + TAB_H;
        int color = active ? EditorUi.ACCENT : hover ? EditorUi.PRIMARY : EditorUi.SECONDARY;
        g.fill(leftPos + x, topPos + TAB_Y, leftPos + x + TAB_W, topPos + TAB_Y + TAB_H,
                active ? EditorUi.BG_ROW_HOVER : EditorUi.BG_ROW);
        if (active) {
            g.fill(leftPos + x, topPos + TAB_Y + TAB_H - 2, leftPos + x + TAB_W, topPos + TAB_Y + TAB_H, EditorUi.ACCENT);
        }
        g.drawCenteredString(font, label, leftPos + x + TAB_W / 2,
                topPos + TAB_Y + (TAB_H - font.lineHeight) / 2 + 1, color);
    }

    /** 属性页：名称 | 当前值 | （付）基础值 | 修饰符条数。 */
    private void renderAttributeRows(GuiGraphics g, int mouseX, int mouseY) {
        if (rows.isEmpty()) {
            g.drawString(font, Component.translatable("gui.infinitestats.entity_panel.no_attributes").getString(),
                    leftPos + LIST_X + 4, topPos + LIST_TOP + 6, EditorUi.SECONDARY);
            return;
        }
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = scroll + row;
            if (index >= rows.size()) break;
            Row entry = rows.get(index);
            int y = topPos + LIST_TOP + row * ROW_H;
            boolean hover = mouseX >= leftPos + LIST_X && mouseX < leftPos + VALUE_RIGHT
                    && mouseY >= y && mouseY < y + ROW_H - 1;
            if (hover) hovered = index;

            g.fill(leftPos + LIST_X, y, leftPos + VALUE_RIGHT, y + ROW_H - 1,
                    index == editingIndex ? EditorUi.BG_ROW_DRAG
                            : hover ? EditorUi.BG_ROW_HOVER : EditorUi.BG_ROW);

            int nameColor = entry.vanilla() ? EditorUi.PRIMARY : EditorUi.INFO;
            g.drawString(font, EditorUi.ellipsize(font, entry.name(), 150), leftPos + LIST_X + 4, y + 5, nameColor);

            // 当前值：与基础值不同说明被修饰符改过，用强调色标出来
            String value = EditorUi.amount(entry.value());
            int valueColor = entry.value() != entry.base() ? EditorUi.ACCENT : EditorUi.PRIMARY;
            g.drawString(font, value, leftPos + LIST_X + 158, y + 5, valueColor);

            String base = "(" + EditorUi.amount(entry.base()) + ")";
            g.drawString(font, base, leftPos + LIST_X + 214, y + 5,
                    index == editingIndex ? EditorUi.ACCENT : EditorUi.SECONDARY);

            if (!entry.modifiers().isEmpty()) {
                String count = "×" + entry.modifiers().size();
                g.drawString(font, count, leftPos + VALUE_RIGHT - 26 - font.width(count), y + 5, EditorUi.SECONDARY);
            }

            // 行尾锁标记：不锁（暗）→ 锁基础值（蓝「基」）→ 锁最终值（金「终」）
            int lockMode = lockModes.getOrDefault(entry.id(), 0);
            if (lockMode > 0 || (editable && hover && mouseX >= leftPos + VALUE_RIGHT - 16)) {
                String glyph = lockMode == 2 ? "终" : lockMode == 1 ? "基" : "○";
                int lockColor = lockMode == 2 ? EditorUi.GOLD : lockMode == 1 ? EditorUi.INFO : EditorUi.SECONDARY;
                g.drawString(font, glyph, leftPos + VALUE_RIGHT - 14, y + 5, lockColor);
            }
        }
    }

    /** 开关页：名称 + 开 / 关。 */
    private void renderToggleRows(GuiGraphics g, int mouseX, int mouseY) {
        if (toggles.isEmpty()) {
            g.drawString(font, Component.translatable("gui.infinitestats.entity_panel.no_toggles").getString(),
                    leftPos + LIST_X + 4, topPos + LIST_TOP + 6, EditorUi.SECONDARY);
            return;
        }
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = scroll + row;
            if (index >= toggles.size()) break;
            EntityPanelToggles.Toggle toggle = toggles.get(index);
            int y = topPos + LIST_TOP + row * ROW_H;
            boolean hover = mouseX >= leftPos + LIST_X && mouseX < leftPos + VALUE_RIGHT
                    && mouseY >= y && mouseY < y + ROW_H - 1;
            if (hover) hoveredToggle = index;

            boolean on = EntityPanelToggles.isEnabled(enabledMask, toggle);
            g.fill(leftPos + LIST_X, y, leftPos + VALUE_RIGHT, y + ROW_H - 1,
                    hover && editable ? EditorUi.BG_ROW_HOVER : EditorUi.BG_ROW);
            if (on) {
                g.fill(leftPos + LIST_X, y, leftPos + LIST_X + 2, y + ROW_H - 1, EditorUi.ACCENT);
            }
            g.drawString(font, Component.translatable(toggle.getNameKey()).getString(),
                    leftPos + LIST_X + 6, y + 5, on ? EditorUi.ACCENT : EditorUi.PRIMARY);

            String state = Component.translatable(on
                    ? "gui.infinitestats.entity_panel.on"
                    : "gui.infinitestats.entity_panel.off").getString();
            g.drawString(font, state, leftPos + VALUE_RIGHT - 4 - font.width(state), y + 5,
                    on ? EditorUi.ACCENT : EditorUi.SECONDARY);
        }
    }

    /** 效果页：名称 + 等级 + 剩余时间；连点两下移除。 */
    private void renderEffectRows(GuiGraphics g, int mouseX, int mouseY) {
        hoveredEffect = -1;
        if (effects.isEmpty()) {
            g.drawString(font, Component.translatable("gui.infinitestats.entity_panel.no_effects").getString(),
                    leftPos + LIST_X + 4, topPos + LIST_TOP + 6, EditorUi.SECONDARY);
            return;
        }
        long now = System.currentTimeMillis();
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = scroll + row;
            if (index >= effects.size()) break;
            EffectRow effect = effects.get(index);
            int y = topPos + LIST_TOP + row * ROW_H;
            boolean hover = mouseX >= leftPos + LIST_X && mouseX < leftPos + VALUE_RIGHT
                    && mouseY >= y && mouseY < y + ROW_H - 1;
            if (hover) hoveredEffect = index;

            boolean confirming = pendingEffectIndex == index && now - pendingEffectAt < 2500L;
            g.fill(leftPos + LIST_X, y, leftPos + VALUE_RIGHT, y + ROW_H - 1,
                    confirming ? EditorUi.BG_ROW_DRAG : hover && editable ? EditorUi.BG_ROW_HOVER : EditorUi.BG_ROW);
            g.drawString(font, EditorUi.ellipsize(font, effect.name(), 190),
                    leftPos + LIST_X + 4, y + 5, EditorUi.PRIMARY);

            String lvl = "Lv." + effect.level();
            g.drawString(font, lvl, leftPos + LIST_X + 200, y + 5, EditorUi.GOLD);

            String time = confirming
                    ? Component.translatable("screen.infinitestats.ui.confirm").getString()
                    : effect.duration() < 0 ? "∞" : formatTicks(effect.duration());
            g.drawString(font, time, leftPos + VALUE_RIGHT - 4 - font.width(time), y + 5,
                    confirming ? EditorUi.DANGER : EditorUi.SECONDARY);
        }
    }

    /** 动作页：每个动作一行；危险动作需要二次确认。 */
    private void renderActionRows(GuiGraphics g, int mouseX, int mouseY) {
        List<com.infinitestats.util.EntityPanelActions.Action> actions = actionRows();
        long now = System.currentTimeMillis();
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = scroll + row;
            if (index >= actions.size()) break;
            var action = actions.get(index);
            int y = topPos + LIST_TOP + row * ROW_H;
            boolean hover = mouseX >= leftPos + LIST_X && mouseX < leftPos + VALUE_RIGHT
                    && mouseY >= y && mouseY < y + ROW_H - 1;
            boolean confirming = pendingAction == index && now - pendingActionAt < 2500L;

            g.fill(leftPos + LIST_X, y, leftPos + VALUE_RIGHT, y + ROW_H - 1,
                    confirming ? EditorUi.BG_ROW_DRAG : hover && editable ? EditorUi.BG_ROW_HOVER : EditorUi.BG_ROW);
            if (action.isDestructive()) {
                g.fill(leftPos + LIST_X, y, leftPos + LIST_X + 2, y + ROW_H - 1, EditorUi.DANGER);
            }
            g.drawString(font, Component.translatable(action.getNameKey()).getString(),
                    leftPos + LIST_X + 6, y + 5,
                    action.isDestructive() ? EditorUi.DANGER : EditorUi.PRIMARY);

            if (confirming) {
                String sure = Component.translatable("screen.infinitestats.ui.confirm").getString();
                g.drawString(font, sure, leftPos + VALUE_RIGHT - 4 - font.width(sure), y + 5, EditorUi.DANGER);
            }
        }

        renderEquipment(g, mouseX, mouseY);
    }

    /** 动作页底部：目标身上的装备（悬停是原版物品提示）。 */
    private void renderEquipment(GuiGraphics g, int mouseX, int mouseY) {
        var slots = new net.minecraft.world.entity.EquipmentSlot[]{
                net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                net.minecraft.world.entity.EquipmentSlot.OFFHAND,
                net.minecraft.world.entity.EquipmentSlot.HEAD,
                net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.EquipmentSlot.LEGS,
                net.minecraft.world.entity.EquipmentSlot.FEET
        };
        int y = topPos + LIST_TOP + LIST_H + 2;
        for (int i = 0; i < slots.length; i++) {
            int x = leftPos + LIST_X + 2 + i * 20;
            ItemStack stack = entity.getItemBySlot(slots[i]);
            g.fill(x, y, x + 18, y + 18, EditorUi.BG_ROW);
            if (!stack.isEmpty()) {
                g.renderItem(stack, x + 1, y + 1);
                boolean hover = mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18;
                if (hover) queueItemTooltip(g, mouseX, mouseY, stack);
            }
        }
    }

    /** {@code 1234} tick → {@code 1:01}（负数是无限时长，由调用方显示 ∞）。 */
    private static String formatTicks(int ticks) {
        int total = Math.max(0, ticks) / 20;
        return String.format(java.util.Locale.ROOT, "%d:%02d", total / 60, total % 60);
    }

    private void renderScrollbar(GuiGraphics g, int mouseX, int mouseY) {
        // 交给 EditorUi：滑块几何与拖动命中用同一套算法，避免「画的位置」和「能拖的位置」错开
        EditorUi.scrollbar(g, mouseX, mouseY, leftPos + SCROLL_X, topPos + LIST_TOP, LIST_H,
                scroll, maxScroll, VISIBLE_ROWS);
    }

    /** 悬停某一行的浮窗：属性显示修饰符明细与上限，开关显示说明。 */
    private void renderRowTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (showToggles) {
            if (hoveredToggle < 0 || hoveredToggle >= toggles.size()) return;
            EntityPanelToggles.Toggle toggle = toggles.get(hoveredToggle);
            List<String> lines = new ArrayList<>();
            lines.add("§f" + Component.translatable(toggle.getNameKey()).getString());
            for (String line : EditorUi.wrap(font, "§7" + Component.translatable(toggle.getDescKey()).getString(), 180)) {
                lines.add(line);
            }
            if (editable) lines.add("§b" + Component.translatable("gui.infinitestats.entity_panel.click_to_toggle").getString());
            queueTooltip(g, mouseX, mouseY, lines, EditorUi.ACCENT);
            return;
        }

        if (hovered < 0 || hovered >= rows.size()) return;
        Row entry = rows.get(hovered);
        List<String> lines = new ArrayList<>();
        lines.add("§f" + entry.name());
        lines.add("§7" + entry.id());
        lines.add(Component.translatable("gui.infinitestats.entity_panel.tooltip.value",
                EditorUi.amount(entry.value()), EditorUi.amount(entry.base())).getString());
        for (String modifier : entry.modifiers()) {
            lines.add(modifier);
        }
        if (editable) {
            lines.add("§b" + Component.translatable("gui.infinitestats.entity_panel.click_to_edit").getString());
        }
        lines.add("§8" + Component.translatable("gui.infinitestats.entity_panel.tooltip.range",
                rangeText(entry)).getString());
        queueTooltip(g, mouseX, mouseY, lines, EditorUi.INFO);
    }

    /** 属性取值范围（没范围的属性显示「不限」）。 */
    private String rangeText(Row entry) {
        Holder<Attribute> holder = BuiltInRegistries.ATTRIBUTE.getHolder(
                ResourceLocation.tryParse(entry.id())).orElse(null);
        if (holder == null) return "-";
        if (holder.value() instanceof RangedAttribute ranged) {
            return EditorUi.amount(ranged.getMinValue()) + " ~ " + EditorUi.amount(ranged.getMaxValue());
        }
        return Component.translatable("gui.infinitestats.entity_panel.tooltip.unlimited").getString();
    }

    // ======================== 交互 ========================

    @Override
    protected boolean onPanelClick(int mouseX, int mouseY, int button) {
        // 滚动条拖动：排在最前，右缘那一列不让行 / 页签的命中区抢走
        if (button == 0 && beginScrollbarDrag(mouseX, mouseY, leftPos + SCROLL_X,
                topPos + LIST_TOP, LIST_H, scroll, maxScroll, VISIBLE_ROWS,
                v -> scroll = Mth.clamp(v, 0, maxScroll))) {
            return true;
        }

        // 页签
        if (mouseY >= TAB_Y && mouseY < TAB_Y + TAB_H) {
            for (int i = 0; i < TAB_X.length; i++) {
                if (mouseX >= TAB_X[i] && mouseX < TAB_X[i] + TAB_W) {
                    switchPage(i);
                    return true;
                }
            }
        }

        // 列表行
        if (mouseX >= LIST_X && mouseX < VALUE_RIGHT
                && mouseY >= LIST_TOP && mouseY < LIST_TOP + LIST_H) {
            int index = scroll + (mouseY - LIST_TOP) / ROW_H;
            if (!editable) return true;

            if (page == 3) {
                List<com.infinitestats.util.EntityPanelActions.Action> actions = actionRows();
                if (index >= 0 && index < actions.size()) {
                    var action = actions.get(index);
                    long now = System.currentTimeMillis();
                    // 危险动作（杀死）需要 2.5 秒内再点一次
                    if (action.isDestructive() && !(pendingAction == index && now - pendingActionAt < 2500L)) {
                        pendingAction = index;
                        pendingActionAt = now;
                        return true;
                    }
                    pendingAction = -1;
                    NetworkHandler.CHANNEL.sendToServer(
                            new NetworkHandler.EntityPanelCommandPacket(entity.getId(), action.ordinal()));
                    pauseFreeUntil = System.currentTimeMillis() + PAUSE_FREE_MS;
                    rebuildCountdown = 1;
                    return true;
                }
                return true;
            }

            if (page == 2) {
                if (index >= 0 && index < effects.size()) {
                    long now = System.currentTimeMillis();
                    if (!(pendingEffectIndex == index && now - pendingEffectAt < 2500L)) {
                        pendingEffectIndex = index;
                        pendingEffectAt = now;
                        return true;
                    }
                    pendingEffectIndex = -1;
                    NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.EntityPanelEffectPacket(
                            entity.getId(), effects.get(index).id()));
                    pauseFreeUntil = System.currentTimeMillis() + PAUSE_FREE_MS;
                    rebuildCountdown = 1;
                    return true;
                }
                return true;
            }

            if (page == 1) {
                if (index >= 0 && index < toggles.size()) {
                    EntityPanelToggles.Toggle toggle = toggles.get(index);
                    boolean target = !EntityPanelToggles.isEnabled(enabledMask, toggle);
                    // 乐观更新：服务端回包会把权威状态盖回来（改不动就原地弹回）
                    enabledMask = target
                            ? enabledMask | toggle.getBit()
                            : enabledMask & ~toggle.getBit();
                    NetworkHandler.CHANNEL.sendToServer(
                            new EntityPanelActionPacket(entity.getId(), toggle.ordinal(), target));
                    pauseFreeUntil = System.currentTimeMillis() + PAUSE_FREE_MS;
                    return true;
                }
                return true;
            }

            // 属性页：行尾 16px 是锁标记，其余区域点开数值编辑
            if (index >= 0 && index < rows.size()) {
                if (mouseX >= LIST_X + VALUE_RIGHT - 16) {
                    cycleLock(rows.get(index));
                    return true;
                }
                beginEditing(index);
                return true;
            }
            return true;
        }

        cancelEditing();
        return false;   // 未命中时交回基类，继续派发给控件
    }

    @Override
    protected boolean onPanelRelease(int mouseX, int mouseY, int button) {
        return button == 0 && endScrollbarDrag();
    }

    @Override
    protected boolean onPanelDrag(int mouseX, int mouseY, int button, int dragX, int dragY) {
        return button == 0 && updateScrollbarDrag(mouseY);
    }

    @Override
    protected boolean onPanelScroll(int mouseX, int mouseY, double delta) {
        if (mouseX >= LIST_X && mouseX < VALUE_RIGHT + EditorUi.SCROLL_W
                && mouseY >= LIST_TOP && mouseY < LIST_TOP + LIST_H) {
            scroll = Mth.clamp(scroll + (delta > 0 ? -1 : 1), 0, maxScroll);
            return true;
        }
        return false;
    }

    private void switchPage(int next) {
        if (page == next) return;
        cancelEditing();
        page = next;
        showToggles = next == 1;
        scroll = 0;
        pendingAction = -1;
        pendingEffectIndex = -1;
        if (next == 2) rebuildEffects();
        updateMaxScroll();
    }

    /**
     * 行尾锁标记：不锁 → 锁基础值 → 锁最终值 → 不锁。
     * <p>
     * 锁基础值时目标就是基础值；锁最终值时目标是玩家看到的那个最终值，
     * 服务端每 tick 按当前修饰符反算基础值来抵消。
     */
    private void cycleLock(Row row) {
        if (!editable) return;
        int mode = lockModes.getOrDefault(row.id(), 0);
        int next = mode == 0 ? 1 : mode == 1 ? 2 : 0;
        double target = next == 1 ? row.base() : row.value();
        if (next == 0) {
            lockModes.remove(row.id());
            lockValues.remove(row.id());
        } else {
            lockModes.put(row.id(), next);
            lockValues.put(row.id(), target);
        }
        NetworkHandler.CHANNEL.sendToServer(
                new NetworkHandler.EntityPanelLockPacket(entity.getId(), row.id(), next, target));
        pauseFreeUntil = System.currentTimeMillis() + PAUSE_FREE_MS;
        rebuildCountdown = 1;
    }

    // ======================== 改数值 ========================

    /** 开始编辑某一行：输入框挪到那一行、填入当前基础值并聚焦。 */
    private void beginEditing(int index) {
        Row entry = rows.get(index);
        editingIndex = index;
        valueField.setX(leftPos + LIST_X + 214 - 2);
        valueField.setY(topPos + LIST_TOP + (index - scroll) * ROW_H + 1);
        valueField.setValue(EditorUi.amount(entry.base()));
        valueField.setVisible(true);
        valueField.setFocused(true);
        setFocused(valueField);
    }

    /** 回车：提交基础值（非法输入或没改动就当作取消）。 */
    private void submitEditing() {
        if (editingIndex < 0 || editingIndex >= rows.size()) {
            cancelEditing();
            return;
        }
        Row entry = rows.get(editingIndex);
        String text = valueField.getValue().trim();
        double value;
        try {
            value = Double.parseDouble(text);
        } catch (NumberFormatException e) {
            cancelEditing();
            return;
        }
        if (!Double.isFinite(value) || value == entry.base()) {
            cancelEditing();
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(entry.id());
        if (id == null) {
            cancelEditing();
            return;
        }
        NetworkHandler.CHANNEL.sendToServer(
                new EntityPanelAttributePacket(entity.getId(), id, value));
        // 暂停界面里包会一直躺在队列里：放行 150ms 让服务端处理并同步回来
        pauseFreeUntil = System.currentTimeMillis() + PAUSE_FREE_MS;
        rebuildCountdown = 1;
        // 乐观更新：本地先改基础值，权威值由原版属性同步包盖回来
        Holder<Attribute> holder = BuiltInRegistries.ATTRIBUTE.getHolder(id).orElse(null);
        if (holder != null && entity.getAttribute(holder) instanceof AttributeInstance instance) {
            instance.setBaseValue(value);
        }
        cancelEditing();
    }

    private void cancelEditing() {
        editingIndex = -1;
        if (valueField != null) {
            valueField.setVisible(false);
            valueField.setFocused(false);
        }
        if (getFocused() == valueField) setFocused(null);
    }
}
