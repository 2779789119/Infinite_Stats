package com.infinitestats.client;

import com.infinitestats.compat.PinyinSearchBridge;
import com.infinitestats.handler.AttributeLockHandler;
import com.infinitestats.network.NetworkHandler;
import com.infinitestats.stats.StatType;
import com.infinitestats.util.AttributeValueMath;
import com.infinitestats.util.EntityPanelActions;
import com.infinitestats.util.EntityPanelToggles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.AgeableMob;
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
import java.util.Locale;
import java.util.Map;

/**
 * 生物面板 —— 一个生物「能看的」和「能改的」都在这儿。
 *
 * <h2>入口</h2>
 * 手持「生物观察杖」（{@code entity_panel} 属性发的木棍）右键生物，
 * 由服务端下发 {@code OpenEntityPanelPacket}，这里再把实体还原成界面（见 {@link #openForEntity}）。
 * 右键空气则打开「附近生物列表」（{@link EntityPickerScreen}），点中哪只就发包请服务端开它的面板。
 *
 * <h2>四页</h2>
 * <ul>
 *   <li><b>属性</b>：这个生物身上所有可显示的属性及其数值（可改、可恢复默认）。</li>
 *   <li><b>开关</b>：AI / 幼年 / 静音 / 不可见 / 发光 / 无敌 / 无重力 / 拾取 / 驯服 / 坐下 / 剃毛。</li>
 *   <li><b>效果</b>：身上的药水效果（等级 / 剩余时间），点两下移除单条，也可一键清空。</li>
 *   <li><b>动作</b>：回满血 / 清效果 / 清仇恨 / 传送 / 复制 / 持久 / 点火 / 杀死。</li>
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
    private static final int GUI_H = 292;

    /** 顶部信息区：类型行 / 生命行 / 血条。 */
    private static final int INFO_Y_TYPE = 26;
    private static final int INFO_Y_HEALTH = 38;
    private static final int BAR_Y = 50;
    private static final int BAR_H = 4;

    /** 搜索框（只在属性页显示）。 */
    private static final int SEARCH_Y = 78;
    private static final int SEARCH_H = 16;

    /** 页签（属性 / 开关 / 效果 / 动作）。 */
    private static final int TAB_Y = 58;
    private static final int TAB_H = 16;
    private static final int TAB_W = 78;
    private static final int TAB_X_ATTR = 6;
    private static final int TAB_X_TOGGLE = 88;
    private static final int TAB_X_EFFECT = 170;
    private static final int TAB_X_ACTION = 252;

    /** 列表区（四页共用，切页时重新算能滚多远）。 */
    private static final int LIST_TOP = 98;
    private static final int LIST_H = 146;
    private static final int ROW_H = 18;
    private static final int VISIBLE_ROWS = LIST_H / ROW_H;

    /** 开关页的「全开 / 全关」按钮（占着属性页搜索框那一行）。 */
    private static final int BULK_W = 60;
    private static final int BULK_ON_X = 200;
    private static final int BULK_OFF_X = 270;

    /** 效果页的「全部清除」按钮（同一行）。 */
    private static final int CLEAR_ALL_W = 90;
    private static final int CLEAR_ALL_X = 240;

    private static final int LIST_X = 4;
    private static final int SCROLL_X = GUI_W - EditorUi.GAP - EditorUi.SCROLL_W;
    /** 行背景的右边界（滚动条左边）。 */
    private static final int ROW_RIGHT = SCROLL_X - 2;
    /** 行尾锁标记的宽度：数值要给它让出位置。 */
    private static final int LOCK_W = 14;
    /** 数值右对齐的位置（锁标记左边）。 */
    private static final int VALUE_RIGHT = ROW_RIGHT - LOCK_W - 2;

    private static final int CLOSE_BTN_Y = 266;

    /** 属性页改数值时，行尾那个输入框的宽度。 */
    private static final int FIELD_W = 78;

    /** 动作页：两列按钮，每行一个动作（宽度按列表可用宽度均分，别超出右边界）。 */
    private static final int ACT_W = 154;
    private static final int ACT_H = 26;
    private static final int ACT_STEP = 30;
    private static final int ACT_X0 = 6;
    private static final int ACT_X1 = 164;
    /** 动作按钮最多摆几行（超出的不画，正常不会超）。 */
    private static final int ACT_ROWS = 4;

    /** 动作页底部的装备行（相对 LIST_TOP 的偏移）。 */
    private static final int EQUIP_Y = 128;
    private static final int EQUIP_SLOT = 18;

    /** 危险动作 / 移除效果需要「2.5 秒内点第二次」才执行。 */
    private static final long CONFIRM_MS = 2500L;

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

    /** 四个页签。 */
    private enum Page { ATTRIBUTES, TOGGLES, EFFECTS, ACTIONS }

    private final LivingEntity entity;
    private final List<Row> rows = new ArrayList<>();
    private final List<EffectRow> effects = new ArrayList<>();
    private final List<EntityPanelActions.Action> actions = new ArrayList<>();
    /** 该生物可用的开关（顺序 = {@link EntityPanelToggles.Toggle} 的枚举顺序）。 */
    private final List<EntityPanelToggles.Toggle> toggles = new ArrayList<>();
    /** 动作页底部的装备（主手 / 副手 / 头盔 / 胸甲 / 护腿 / 靴子）。 */
    private final List<ItemStack> equipment = new ArrayList<>();

    private int applicableMask;
    private int enabledMask;
    private boolean editable;

    private Page page = Page.ATTRIBUTES;

    private int scroll;
    private int maxScroll;

    /** 属性页搜索框与关键词。 */
    private EditBox searchBox;
    private String searchText = "";
    private int hovered = -1;
    private int hoveredToggle = -1;
    private int hoveredEffect = -1;
    private int hoveredAction = -1;
    private int hoveredEquip = -1;
    private boolean hoveredHealthBar;
    private boolean scrollbarDragging;
    private int rebuildCountdown;

    /** 属性页的数值输入框（编辑中才显示，位置每帧跟着所在行走）。 */
    private EditBox valueField;
    /** 正在编辑的属性行下标，-1 = 没有在编辑。 */
    private int editingIndex = -1;

    /** 属性锁定状态：属性注册名 → 模式 / 目标值（服务端下发，行尾据此画「基 / 终」）。 */
    private final Map<String, Integer> lockModes = new HashMap<>();
    private final Map<String, Double> lockValues = new HashMap<>();

    /** 效果页：等待二次确认的那一行，-1 = 没有。 */
    private int pendingEffectIndex = -1;
    private long pendingEffectAt;
    /** 动作页：等待二次确认的动作下标，-1 = 没有（只有危险动作会用到）。 */
    private int pendingAction = -1;
    private long pendingActionAt;

    /**
     * 「暂停放行」的截止时间（{@link System#currentTimeMillis()}）。
     * <p>
     * 面板是暂停界面 —— 单人游戏打开它世界就停了：服务端不 tick，我们发过去的
     * 「改属性 / 切开关 / 执行动作」的包就一直躺在队列里，表现就是「改完必须关掉面板重开才生效」。
     * 所以每次发包后临时放行一百多毫秒，让服务端把包处理掉、属性同步回来，再自动暂停。
     * 用时间而不是帧数：不受帧率影响，服务器 20 tick/s 下至少能跑两三个 tick。
     */
    private long pauseFreeUntil;

    /** 一行属性。 */
    private record Row(String name, String id, double value, double base,
                       List<String> modifiers, boolean vanilla) {}

    /** 一行药水效果。 */
    private record EffectRow(String id, String name, int level, int duration) {}

    public EntityPanelScreen(LivingEntity entity, int applicableMask, int enabledMask, boolean editable) {
        super(entity.getDisplayName());
        this.entity = entity;
        this.applicableMask = applicableMask;
        this.enabledMask = enabledMask;
        this.editable = editable;
        this.toggles.addAll(collectToggles());
        rebuildActions();
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

    /**
     * 由网络包调用：把服务端下发的属性锁表交给对应面板。
     * <p>
     * 界面还没开（比如从附近生物列表点进来、服务器的包先到）就先按实体 id 暂存，
     * 等面板初始化时取走 —— 否则刚打开的那一瞬间锁标记是空的。
     */
    public static void applyLockSnapshot(int entityId, CompoundTag tag) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.screen instanceof EntityPanelScreen panel && panel.entity.getId() == entityId) {
            panel.setLocks(tag);
        } else {
            NetworkHandler.pendingEntityLocks.put(entityId, tag);
        }
    }

    private void setLocks(CompoundTag tag) {
        lockModes.clear();
        lockValues.clear();
        if (tag == null) return;
        for (String key : tag.getAllKeys()) {
            CompoundTag one = tag.getCompound(key);
            int mode = one.getInt("mode");
            if (mode != AttributeLockHandler.MODE_BASE && mode != AttributeLockHandler.MODE_FINAL) continue;
            lockModes.put(key, mode);
            lockValues.put(key, one.getDouble("value"));
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

    private void rebuildActions() {
        actions.clear();
        for (EntityPanelActions.Action action : EntityPanelActions.Action.values()) {
            if (action.appliesTo(entity)) actions.add(action);
        }
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
        rebuildEffects();
        rebuildEquipment();
        // 服务端的锁表可能比界面先到（从附近生物列表点进来的情况）
        CompoundTag pendingLocks = NetworkHandler.pendingEntityLocks.remove(entity.getId());
        if (pendingLocks != null) setLocks(pendingLocks);

        // 属性页搜索：属性一多就不用一路滚到底
        searchBox = new EditBox(font, leftPos + 4, topPos + SEARCH_Y,
                GUI_W - EditorUi.GAP - EditorUi.SCROLL_W - 8, SEARCH_H, Component.empty());
        searchBox.setMaxLength(48);
        searchBox.setHint(Component.translatable("gui.infinitestats.entity_panel.search_hint"));
        searchBox.setValue(searchText);
        searchBox.setResponder(v -> {
            searchText = v;
            scroll = 0;
            rebuildRows();
        });
        searchBox.setVisible(page == Page.ATTRIBUTES);
        addRenderableWidget(searchBox);

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
        // 属性 / 效果会因为药水 / 装备 / 其它模组而变，定时重读（不必每帧）
        if (--rebuildCountdown <= 0) {
            rebuildCountdown = 10;
            rebuildRows();
            rebuildEffects();
            rebuildEquipment();
        }
        // 重读之后行数可能变了，编辑目标要还在
        if (editingIndex >= rows.size()) cancelEditing();
        if (pendingEffectIndex >= effects.size()) pendingEffectIndex = -1;
    }

    // ======================== 数据：属性 ========================

    /**
     * 枚举「这个生物身上存在」的属性。
     * <p>
     * 走属性注册表逐个问 {@code getAttribute(...)} 是否为 null，比直接翻 AttributeMap 稳妥：
     * 注册表里没有的属性不可能有实例，而实例上没有的属性恰好就是 null。
     */
    private void rebuildRows() {
        float keepScrollRatio = page != Page.ATTRIBUTES || maxScroll <= 0 ? 0f : scroll / (float) maxScroll;

        List<AttributeInstance> instances = new ArrayList<>();
        for (Attribute attribute : ForgeRegistries.ATTRIBUTES.getValues()) {
            AttributeInstance instance = entity.getAttribute(attribute);
            if (instance != null) instances.add(instance);
        }
        instances.sort(Comparator.comparingInt(this::rank).thenComparing(this::idOf));

        rows.clear();
        String query = searchText.toLowerCase(Locale.ROOT).trim();
        for (AttributeInstance instance : instances) {
            String id = idOf(instance);
            String name = displayName(instance.getAttribute(), id);
            // 搜索：名称（含拼音）或注册名，命中其一即保留
            if (!query.isEmpty()
                    && !PinyinSearchBridge.matches(name.toLowerCase(Locale.ROOT), query)
                    && !id.toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            List<String> modifiers = new ArrayList<>();
            for (AttributeModifier modifier : instance.getModifiers()) {
                modifiers.add(modifierLine(modifier));
            }
            modifiers.sort(String::compareTo);
            rows.add(new Row(name, id,
                    instance.getValue(), instance.getBaseValue(), modifiers, id.startsWith("minecraft:")));
        }

        updateMaxScroll();
        // 数值刷新后列表长度几乎不变，但万一变了也别让滚动位置跳到别处
        if (page == Page.ATTRIBUTES) scroll = Mth.clamp(Math.round(keepScrollRatio * maxScroll), 0, maxScroll);
    }

    /** 重读生物身上的药水效果（顺序固定：按效果名排序，避免每 10 tick 抖动）。 */
    private void rebuildEffects() {
        float keepScrollRatio = page != Page.EFFECTS || maxScroll <= 0 ? 0f : scroll / (float) maxScroll;
        effects.clear();
        List<MobEffectInstance> list = new ArrayList<>(entity.getActiveEffects());
        list.sort(Comparator.comparing(inst -> inst.getEffect().getDisplayName().getString()));
        for (MobEffectInstance instance : list) {
            effects.add(new EffectRow(
                    EntityPanelActions.effectId(instance),
                    instance.getEffect().getDisplayName().getString(),
                    instance.getAmplifier() + 1,
                    instance.getDuration()));
        }
        updateMaxScroll();
        if (page == Page.EFFECTS) scroll = Mth.clamp(Math.round(keepScrollRatio * maxScroll), 0, maxScroll);
    }

    /** 动作页底部的装备：主手、副手、头盔、胸甲、护腿、靴子。 */
    private void rebuildEquipment() {
        equipment.clear();
        equipment.add(entity.getMainHandItem());
        equipment.add(entity.getOffhandItem());
        for (ItemStack stack : entity.getArmorSlots()) {
            equipment.add(stack);
            if (equipment.size() >= 6) break;
        }
    }

    /** 当前页最多能滚多少行。 */
    private void updateMaxScroll() {
        int size = switch (page) {
            case ATTRIBUTES -> rows.size();
            case TOGGLES -> toggles.size();
            case EFFECTS -> effects.size();
            case ACTIONS -> 0;
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

    /** 玩家到这个生物的距离（格，保留一位）。 */
    private String distanceText() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return "";
        double d = Math.sqrt(entity.distanceToSqr(mc.player));
        return Component.translatable("gui.infinitestats.entity_panel.distance",
                String.format(Locale.ROOT, "%.1f", d)).getString();
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

        renderInfo(g, mouseX, mouseY);
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

        switch (page) {
            case ATTRIBUTES -> renderAttributeRows(g, mouseX, mouseY);
            case TOGGLES -> renderToggleRows(g, mouseX, mouseY);
            case EFFECTS -> renderEffectRows(g, mouseX, mouseY);
            case ACTIONS -> renderActions(g, mouseX, mouseY);
        }

        if (maxScroll > 0) {
            EditorUi.scrollbar(g, mouseX, mouseY, leftPos + SCROLL_X, LIST_TOP, LIST_H,
                    scroll, maxScroll, VISIBLE_ROWS);
        }

        g.drawCenteredString(font, hintText(), leftPos + GUI_W / 2, topPos + CLOSE_BTN_Y - 12,
                !editable && page != Page.ATTRIBUTES ? EditorUi.DANGER : EditorUi.SECONDARY);
    }

    /** 顶部信息：类型行（右对齐距离）、生命行（右对齐护甲）、血条。 */
    private void renderInfo(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, Component.translatable("gui.infinitestats.entity_panel.type", typeName()).getString(),
                leftPos + 10, topPos + INFO_Y_TYPE, EditorUi.SECONDARY);

        String distance = distanceText();
        g.drawString(font, distance, leftPos + GUI_W - 10 - font.width(distance),
                topPos + INFO_Y_TYPE, EditorUi.SECONDARY);

        g.drawString(font, healthText(), leftPos + 10, topPos + INFO_Y_HEALTH, EditorUi.DANGER);

        String armor = Component.translatable("gui.infinitestats.entity_panel.armor",
                EditorUi.amount(entity.getArmorValue()),
                EditorUi.amount(entity.getAttributeValue(
                        net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS))).getString();
        g.drawString(font, armor, leftPos + GUI_W - 10 - font.width(armor),
                topPos + INFO_Y_HEALTH, EditorUi.INFO);

        float max = entity.getMaxHealth();
        float ratio = max <= 0 ? 0f : Mth.clamp(entity.getHealth() / max, 0f, 1f);
        EditorUi.bar(g, leftPos + 10, topPos + BAR_Y, GUI_W - 20, BAR_H, ratio);

        hoveredHealthBar = mouseX >= leftPos + 8 && mouseX < leftPos + GUI_W - 8
                && mouseY >= topPos + INFO_Y_TYPE - 2 && mouseY < topPos + BAR_Y + BAR_H + 3;
    }

    private String hintText() {
        if (page == Page.ATTRIBUTES && editingIndex >= 0) {
            return Component.translatable("gui.infinitestats.entity_panel.hint_edit").getString();
        }
        if (!editable) {
            return Component.translatable("gui.infinitestats.entity_panel.readonly").getString();
        }
        return Component.translatable(switch (page) {
            case ATTRIBUTES -> "gui.infinitestats.entity_panel.hint";
            case TOGGLES -> "gui.infinitestats.entity_panel.hint_toggles";
            case EFFECTS -> "gui.infinitestats.entity_panel.hint_effects";
            case ACTIONS -> "gui.infinitestats.entity_panel.hint_actions";
        }).getString();
    }

    /** 四个页签：选中的那页底色更亮、并有下方一条高亮线。 */
    private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        drawTab(g, mouseX, mouseY, TAB_X_ATTR, Page.ATTRIBUTES,
                Component.translatable("gui.infinitestats.entity_panel.tab.attributes", rows.size()));
        drawTab(g, mouseX, mouseY, TAB_X_TOGGLE, Page.TOGGLES,
                Component.translatable("gui.infinitestats.entity_panel.tab.toggles", toggles.size()));
        drawTab(g, mouseX, mouseY, TAB_X_EFFECT, Page.EFFECTS,
                Component.translatable("gui.infinitestats.entity_panel.tab.effects", effects.size()));
        drawTab(g, mouseX, mouseY, TAB_X_ACTION, Page.ACTIONS,
                Component.translatable("gui.infinitestats.entity_panel.tab.actions", actions.size()));

        renderPageButtons(g, mouseX, mouseY);
    }

    /** 开关页的「全开 / 全关」、效果页的「全部清除」 —— 都占着属性页搜索框那一行。 */
    private void renderPageButtons(GuiGraphics g, int mouseX, int mouseY) {
        if (!editable) return;
        int y = topPos + SEARCH_Y;
        int h = SEARCH_H;

        if (page == Page.TOGGLES) {
            drawFlatButton(g, mouseX, mouseY, leftPos + BULK_ON_X, y, BULK_W, h,
                    Component.translatable("gui.infinitestats.entity_panel.bulk_on").getString(),
                    0xFF1A5334, 0xFF2D8A4E);
            drawFlatButton(g, mouseX, mouseY, leftPos + BULK_OFF_X, y, BULK_W, h,
                    Component.translatable("gui.infinitestats.entity_panel.bulk_off").getString(),
                    0xFF6B2A1A, 0xFF8A3D2D);
        } else if (page == Page.EFFECTS && !effects.isEmpty()) {
            drawFlatButton(g, mouseX, mouseY, leftPos + CLEAR_ALL_X, y, CLEAR_ALL_W, h,
                    Component.translatable("gui.infinitestats.entity_panel.effect.clear_all").getString(),
                    0xFF6B2A1A, 0xFF8A3D2D);
        }
    }

    private void drawFlatButton(GuiGraphics g, int mouseX, int mouseY,
                                int x, int y, int w, int h, String label, int base, int hoverColor) {
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        g.fill(x, y, x + w, y + h, hover ? hoverColor : base);
        g.drawCenteredString(font, label, x + w / 2, y + (h - font.lineHeight) / 2 + 1, 0xFFFFFFFF);
    }

    private void drawTab(GuiGraphics g, int mouseX, int mouseY, int x, Page target, Component label) {
        boolean selected = page == target;
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
        beginPanelScissor(g, leftPos + LIST_X, LIST_TOP, leftPos + ROW_RIGHT, LIST_TOP + LIST_H);

        hovered = -1;
        hoveredToggle = -1;
        hoveredEffect = -1;
        hoveredAction = -1;
        hoveredEquip = -1;
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
                g.fill(leftPos + LIST_X, y, leftPos + ROW_RIGHT, y + ROW_H - 1, EditorUi.BG_ROW_HOVER);
            } else if (index % 2 == 1) {
                g.fill(leftPos + LIST_X, y, leftPos + ROW_RIGHT, y + ROW_H - 1, EditorUi.BG_ROW);
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

            // 行尾的锁标记：未锁时只在悬停那一行显示，锁上后常驻
            renderLockBadge(g, row, y, textY, mouseX, mouseY);
        }

        endPanelScissor(g);
    }

    /** 行尾的锁定标记：空 = 不锁，蓝「基」= 锁基础值，金「终」= 锁最终值。点一下在三种之间循环。 */
    private void renderLockBadge(GuiGraphics g, Row row, int rowY, int textY, int mouseX, int mouseY) {
        int mode = lockModes.getOrDefault(row.id(), AttributeLockHandler.MODE_NONE);
        int x = leftPos + ROW_RIGHT - LOCK_W;
        boolean hover = mouseX >= x && mouseX < leftPos + ROW_RIGHT
                && mouseY >= rowY && mouseY < rowY + ROW_H;
        if (mode == AttributeLockHandler.MODE_NONE && !(hover && editable)) return;

        if (hover) g.fill(x, rowY, leftPos + ROW_RIGHT, rowY + ROW_H - 1, EditorUi.BG_ROW_HOVER);

        String label = Component.translatable(mode == AttributeLockHandler.MODE_BASE
                ? "gui.infinitestats.entity_panel.lock.base"
                : mode == AttributeLockHandler.MODE_FINAL
                ? "gui.infinitestats.entity_panel.lock.final"
                : "gui.infinitestats.entity_panel.lock.none").getString();
        int color = !editable ? EditorUi.SECONDARY
                : mode == AttributeLockHandler.MODE_BASE ? EditorUi.INFO
                : mode == AttributeLockHandler.MODE_FINAL ? EditorUi.GOLD
                : EditorUi.SECONDARY;
        g.drawString(font, EditorUi.ellipsize(font, label, LOCK_W - 2), x + 1, textY, color);
    }

    private void renderToggleRows(GuiGraphics g, int mouseX, int mouseY) {
        beginPanelScissor(g, leftPos + LIST_X, LIST_TOP, leftPos + ROW_RIGHT, LIST_TOP + LIST_H);

        hovered = -1;
        hoveredToggle = -1;
        hoveredEffect = -1;
        hoveredAction = -1;
        hoveredEquip = -1;
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
                g.fill(leftPos + LIST_X, y, leftPos + ROW_RIGHT, y + ROW_H - 1, EditorUi.BG_ROW_HOVER);
            } else if (index % 2 == 1) {
                g.fill(leftPos + LIST_X, y, leftPos + ROW_RIGHT, y + ROW_H - 1, EditorUi.BG_ROW);
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

    /** 效果页：每行一个药水效果，右侧是等级与剩余时间，再点一次移除。 */
    private void renderEffectRows(GuiGraphics g, int mouseX, int mouseY) {
        beginPanelScissor(g, leftPos + LIST_X, LIST_TOP, leftPos + ROW_RIGHT, LIST_TOP + LIST_H);

        hovered = -1;
        hoveredToggle = -1;
        hoveredEffect = -1;
        hoveredAction = -1;
        hoveredEquip = -1;
        if (effects.isEmpty()) {
            g.drawString(font, Component.translatable("gui.infinitestats.entity_panel.effect.none").getString(),
                    leftPos + LIST_X + 6, LIST_TOP + 4, EditorUi.SECONDARY);
        }

        long now = System.currentTimeMillis();
        if (pendingEffectIndex >= 0 && now - pendingEffectAt > CONFIRM_MS) pendingEffectIndex = -1;

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int index = scroll + i;
            if (index >= effects.size()) break;

            EffectRow effect = effects.get(index);
            int y = LIST_TOP + i * ROW_H;
            boolean hover = insideRow(mouseX, mouseY, y);
            if (hover) hoveredEffect = index;
            boolean pending = pendingEffectIndex == index;

            if (pending) {
                g.fill(leftPos + LIST_X, y, leftPos + ROW_RIGHT, y + ROW_H - 1, 0x80AA4040);
            } else if (hover && editable) {
                g.fill(leftPos + LIST_X, y, leftPos + ROW_RIGHT, y + ROW_H - 1, EditorUi.BG_ROW_HOVER);
            } else if (index % 2 == 1) {
                g.fill(leftPos + LIST_X, y, leftPos + ROW_RIGHT, y + ROW_H - 1, EditorUi.BG_ROW);
            }

            int textY = y + (ROW_H - font.lineHeight) / 2;
            String label = effect.level() > 1
                    ? effect.name() + " " + roman(effect.level())
                    : effect.name();
            g.drawString(font, EditorUi.ellipsize(font, label, GUI_W - 150),
                    leftPos + LIST_X + 6, textY, EditorUi.INFO);

            String right = pending
                    ? Component.translatable("gui.infinitestats.entity_panel.effect.confirm").getString()
                    : durationText(effect.duration());
            int color = pending ? 0xFFFFFFFF : EditorUi.GOLD;
            g.drawString(font, right, leftPos + VALUE_RIGHT - font.width(right), textY, color);
        }

        endPanelScissor(g);
    }

    /** 动作页：两列动作按钮 + 底部装备行。 */
    private void renderActions(GuiGraphics g, int mouseX, int mouseY) {
        beginPanelScissor(g, leftPos + LIST_X, LIST_TOP, leftPos + ROW_RIGHT, LIST_TOP + LIST_H);

        hovered = -1;
        hoveredToggle = -1;
        hoveredEffect = -1;
        hoveredAction = -1;
        hoveredEquip = -1;

        long now = System.currentTimeMillis();
        if (pendingAction >= 0 && now - pendingActionAt > CONFIRM_MS) pendingAction = -1;

        for (int i = 0; i < actions.size() && i < ACT_ROWS * 2; i++) {
            EntityPanelActions.Action action = actions.get(i);
            int col = i % 2;
            int row = i / 2;
            int x = leftPos + (col == 0 ? ACT_X0 : ACT_X1);
            int y = LIST_TOP + row * ACT_STEP;
            boolean hover = mouseX >= x && mouseX < x + ACT_W && mouseY >= y && mouseY < y + ACT_H;
            if (hover) hoveredAction = i;

            int base;
            int hoverColor;
            if (action.isDestructive()) {
                base = 0xFF6B2A1A;
                hoverColor = 0xFF8A3D2D;
            } else {
                base = EditorUi.BG_ROW;
                hoverColor = EditorUi.BG_ROW_HOVER;
            }
            if (pendingAction == i) base = 0xFF8A3D2D;
            g.fill(x, y, x + ACT_W, y + ACT_H, hover ? hoverColor : base);
            g.fill(x, y, x + 2, y + ACT_H, action.isDestructive() ? EditorUi.DANGER : EditorUi.ACCENT);

            String label = pendingAction == i
                    ? Component.translatable("gui.infinitestats.entity_panel.action.confirm").getString()
                    : Component.translatable(action.getNameKey()).getString();
            int textColor = !editable ? EditorUi.SECONDARY
                    : action.isDestructive() ? EditorUi.DANGER : EditorUi.PRIMARY;
            g.drawString(font, EditorUi.ellipsize(font, label, ACT_W - 12), x + 8,
                    y + (ACT_H - font.lineHeight) / 2, textColor);
        }

        renderEquipment(g, mouseX, mouseY);
        endPanelScissor(g);
    }

    /** 动作页底部的装备栏（只读，悬停看物品提示）。 */
    private void renderEquipment(GuiGraphics g, int mouseX, int mouseY) {
        int y = LIST_TOP + EQUIP_Y;
        String label = Component.translatable("gui.infinitestats.entity_panel.equipment").getString();
        g.drawString(font, label, leftPos + 6, y + 5, EditorUi.SECONDARY);

        int x = leftPos + 6 + font.width(label) + 6;
        for (int i = 0; i < equipment.size(); i++) {
            ItemStack stack = equipment.get(i);
            int sx = x + i * EQUIP_SLOT;
            boolean hover = mouseX >= sx && mouseX < sx + 16 && mouseY >= y && mouseY < y + 16;
            g.fill(sx, y, sx + 16, y + 16, hover ? EditorUi.BG_ROW_HOVER : EditorUi.BG_ROW);
            if (!stack.isEmpty()) g.renderItem(stack, sx, y);
            if (hover) hoveredEquip = i;
        }
    }

    private boolean insideRow(int mouseX, int mouseY, int rowY) {
        return mouseY >= rowY && mouseY < rowY + ROW_H
                && mouseX >= leftPos + LIST_X && mouseX < leftPos + ROW_RIGHT;
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

    /** 鼠标是否落在属性页某一行的「锁标记」上（行内最右侧那 14px）。 */
    private boolean lockHit(int mouseX, int mouseY, int index) {
        int visible = index - scroll;
        if (visible < 0 || visible >= VISIBLE_ROWS) return false;
        int y = LIST_TOP + visible * ROW_H;
        return mouseY >= y && mouseY < y + ROW_H
                && mouseX >= leftPos + ROW_RIGHT - LOCK_W && mouseX < leftPos + ROW_RIGHT;
    }

    /**
     * 切换某一行的锁定状态：不锁 → 锁基础值 → 锁最终值 → 不锁。
     * <p>
     * 锁基础值时带的是当前基础值，锁最终值时带当前最终值 —— 服务端据此每 tick 压回去。
     */
    private void cycleLock(int index) {
        if (index < 0 || index >= rows.size()) return;
        Row row = rows.get(index);
        int current = lockModes.getOrDefault(row.id(), AttributeLockHandler.MODE_NONE);
        int next = current == AttributeLockHandler.MODE_NONE ? AttributeLockHandler.MODE_BASE
                : current == AttributeLockHandler.MODE_BASE ? AttributeLockHandler.MODE_FINAL
                : AttributeLockHandler.MODE_NONE;
        double value = next == AttributeLockHandler.MODE_BASE ? row.base()
                : next == AttributeLockHandler.MODE_FINAL ? row.value() : 0.0D;

        if (next == AttributeLockHandler.MODE_NONE) {
            lockModes.remove(row.id());
            lockValues.remove(row.id());
        } else {
            lockModes.put(row.id(), next);
            lockValues.put(row.id(), value);
        }
        send(new NetworkHandler.EntityPanelLockPacket(entity.getId(), row.id(), next, value));
    }

    /** 鼠标落在当前页列表的第几行（不区分页面，行高统一）。 */
    private int listIndexAt(int mouseX, int mouseY, int size) {
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int index = scroll + i;
            if (index >= size) break;
            if (insideRow(mouseX, mouseY, LIST_TOP + i * ROW_H)) return index;
        }
        return -1;
    }

    @Override
    protected void renderPanelOverlay(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (hoveredHealthBar) {
            queueTooltip(g, mouseX, mouseY, infoTooltip(), EditorUi.INFO);
            return;
        }
        switch (page) {
            case TOGGLES -> {
                if (hoveredToggle >= 0 && hoveredToggle < toggles.size()) {
                    queueTooltip(g, mouseX, mouseY, toggleTooltip(toggles.get(hoveredToggle)), EditorUi.ACCENT);
                }
            }
            case EFFECTS -> {
                if (hoveredEffect >= 0 && hoveredEffect < effects.size()) {
                    queueTooltip(g, mouseX, mouseY, effectTooltip(effects.get(hoveredEffect)), EditorUi.INFO);
                }
            }
            case ACTIONS -> {
                if (hoveredAction >= 0 && hoveredAction < actions.size()) {
                    queueTooltip(g, mouseX, mouseY, actionTooltip(actions.get(hoveredAction)), EditorUi.INFO);
                } else if (hoveredEquip >= 0 && hoveredEquip < equipment.size()) {
                    queueItemTooltip(g, mouseX, mouseY, equipment.get(hoveredEquip));
                }
            }
            default -> {
                // 正在编辑的那一行不弹浮窗，免得盖住输入框
                if (hovered >= 0 && hovered < rows.size() && hovered != editingIndex) {
                    queueTooltip(g, mouseX, mouseY, tooltipLines(rows.get(hovered)), EditorUi.INFO);
                }
            }
        }
    }

    /** 顶部信息行的浮窗：血量 / 护甲 / 吸收 / 主人 / 成长进度 / 着火状态。 */
    private List<String> infoTooltip() {
        List<String> lines = new ArrayList<>();
        lines.add("§f§l" + typeName());
        lines.add("§c" + healthText());
        if (entity.getAbsorptionAmount() > 0) {
            lines.add("§e" + Component.translatable("gui.infinitestats.entity_panel.absorption",
                    EditorUi.amount(entity.getAbsorptionAmount())).getString());
        }
        lines.add("§9" + Component.translatable("gui.infinitestats.entity_panel.armor_full",
                EditorUi.amount(entity.getArmorValue()),
                EditorUi.amount(entity.getAttributeValue(
                        net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS))).getString());

        if (entity instanceof TamableAnimal animal) {
            String owner = animal.getOwner() == null
                    ? Component.translatable("gui.infinitestats.entity_panel.owner.none").getString()
                    : animal.getOwner().getName().getString();
            lines.add("§d" + Component.translatable("gui.infinitestats.entity_panel.owner", owner).getString());
        }
        if (entity instanceof AgeableMob ageable && ageable.isBaby()) {
            int remain = Math.max(0, -ageable.getAge());
            lines.add("§a" + Component.translatable("gui.infinitestats.entity_panel.growing",
                    durationText(remain)).getString());
        }
        if (entity.isOnFire()) {
            lines.add("§c" + Component.translatable("gui.infinitestats.entity_panel.on_fire",
                    EditorUi.amount(entity.getRemainingFireTicks() / 20.0D)).getString());
        }
        return lines;
    }

    /** 悬停属性行的浮窗：注册名 + 基础值 / 最终值 / 默认值 + 取值范围 + 逐条修饰符。 */
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

        int lockMode = lockModes.getOrDefault(row.id(), AttributeLockHandler.MODE_NONE);
        double lockValue = lockValues.getOrDefault(row.id(), Double.NaN);
        if (lockMode == AttributeLockHandler.MODE_BASE) {
            lines.add("§9" + Component.translatable("gui.infinitestats.entity_panel.lock.tip.base",
                    EditorUi.amount(lockValue)).getString());
        } else if (lockMode == AttributeLockHandler.MODE_FINAL) {
            lines.add("§6" + Component.translatable("gui.infinitestats.entity_panel.lock.tip.final",
                    EditorUi.amount(lockValue)).getString());
        }
        if (editable) {
            lines.add("§8" + Component.translatable("gui.infinitestats.entity_panel.lock.hint").getString());
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

    private List<String> effectTooltip(EffectRow effect) {
        List<String> lines = new ArrayList<>();
        lines.add("§f§l" + effect.name() + (effect.level() > 1 ? " " + roman(effect.level()) : ""));
        lines.add("§7" + Component.translatable("gui.infinitestats.entity_panel.effect.id", effect.id()).getString());
        lines.add("§7" + Component.translatable("gui.infinitestats.entity_panel.effect.duration",
                durationText(effect.duration())).getString());
        lines.add("§8" + Component.translatable(editable
                ? "gui.infinitestats.entity_panel.effect.click"
                : "gui.infinitestats.entity_panel.readonly").getString());
        return lines;
    }

    private List<String> actionTooltip(EntityPanelActions.Action action) {
        List<String> lines = new ArrayList<>();
        lines.add("§f§l" + Component.translatable(action.getNameKey()).getString());
        lines.addAll(EditorUi.wrap(font, "§7" + Component.translatable(action.getDescKey()).getString(), 190));
        if (action.isDestructive()) {
            lines.add("§c" + Component.translatable("gui.infinitestats.entity_panel.action.confirm_hint").getString());
        }
        lines.add("§8" + Component.translatable(editable
                ? "gui.infinitestats.entity_panel.action.click"
                : "gui.infinitestats.entity_panel.readonly").getString());
        return lines;
    }

    // ======================== 输入 ========================

    @Override
    protected boolean onPanelClick(int mouseX, int mouseY, int button) {
        if (button != 0) return false;

        // 页签行右侧的两个整页按钮
        if (editable && mouseY >= topPos + SEARCH_Y && mouseY < topPos + SEARCH_Y + SEARCH_H) {
            if (page == Page.TOGGLES) {
                if (mouseX >= leftPos + BULK_ON_X && mouseX < leftPos + BULK_ON_X + BULK_W) {
                    setAllToggles(true);
                    return true;
                }
                if (mouseX >= leftPos + BULK_OFF_X && mouseX < leftPos + BULK_OFF_X + BULK_W) {
                    setAllToggles(false);
                    return true;
                }
            } else if (page == Page.EFFECTS && !effects.isEmpty()
                    && mouseX >= leftPos + CLEAR_ALL_X && mouseX < leftPos + CLEAR_ALL_X + CLEAR_ALL_W) {
                send(new NetworkHandler.EntityPanelCommandPacket(
                        entity.getId(), EntityPanelActions.Action.CLEAR_EFFECTS.ordinal()));
                return true;
            }
        }

        // 有正在编辑的行：点到自己就交给输入框处理（挪光标），点到别处先提交再继续
        if (editingIndex >= 0 && page == Page.ATTRIBUTES) {
            if (rowIndexAt(mouseX, mouseY) == editingIndex) return false;
            submitEditing();
        }

        // 页签
        if (mouseY >= TAB_Y && mouseY < TAB_Y + TAB_H) {
            if (inTab(mouseX, TAB_X_ATTR) && page != Page.ATTRIBUTES) return setPage(Page.ATTRIBUTES);
            if (inTab(mouseX, TAB_X_TOGGLE) && page != Page.TOGGLES) return setPage(Page.TOGGLES);
            if (inTab(mouseX, TAB_X_EFFECT) && page != Page.EFFECTS) return setPage(Page.EFFECTS);
            if (inTab(mouseX, TAB_X_ACTION) && page != Page.ACTIONS) return setPage(Page.ACTIONS);
        }

        // 滚动条：按下即定位 + 开始拖拽
        if (maxScroll > 0 && EditorUi.onScrollbar(mouseX, mouseY, leftPos + SCROLL_X, LIST_TOP, LIST_H)) {
            scrollbarDragging = true;
            scroll = EditorUi.scrollFromMouseY(mouseY, LIST_TOP, LIST_H, maxScroll, VISIBLE_ROWS);
            return true;
        }

        switch (page) {
            case ATTRIBUTES -> {
                // 点某一行改它的基础值；Shift + 点击恢复默认；行尾点锁在「不锁 / 锁基 / 锁终」之间循环
                if (!editable) break;
                int index = rowIndexAt(mouseX, mouseY);
                if (index >= 0) {
                    if (lockHit(mouseX, mouseY, index)) cycleLock(index);
                    else if (Screen.hasShiftDown()) resetToDefault(index);
                    else startEditing(index);
                    return true;
                }
            }
            case TOGGLES -> {
                if (!editable) break;
                int index = listIndexAt(mouseX, mouseY, toggles.size());
                if (index >= 0) {
                    EntityPanelToggles.Toggle toggle = toggles.get(index);
                    boolean next = !EntityPanelToggles.isEnabled(enabledMask, toggle);
                    // 乐观更新：先本地翻，服务端回包后以权威值覆盖（被拒绝就原地弹回）
                    if (next) enabledMask |= toggle.getBit();
                    else enabledMask &= ~toggle.getBit();
                    send(new NetworkHandler.EntityPanelActionPacket(entity.getId(), toggle.ordinal(), next));
                    return true;
                }
            }
            case EFFECTS -> {
                if (!editable) break;
                int index = listIndexAt(mouseX, mouseY, effects.size());
                if (index >= 0) {
                    // 两段式确认：第一下只是变红，2.5 秒内再点同一行才真的移除
                    long now = System.currentTimeMillis();
                    if (pendingEffectIndex == index && now - pendingEffectAt <= CONFIRM_MS) {
                        pendingEffectIndex = -1;
                        send(new NetworkHandler.EntityPanelEffectPacket(
                                entity.getId(), effects.get(index).id()));
                    } else {
                        pendingEffectIndex = index;
                        pendingEffectAt = now;
                    }
                    return true;
                }
            }
            case ACTIONS -> {
                if (!editable) break;
                int index = actionIndexAt(mouseX, mouseY);
                if (index >= 0) {
                    runAction(index);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean setPage(Page next) {
        page = next;
        scroll = 0;
        cancelEditing();
        pendingEffectIndex = -1;
        pendingAction = -1;
        if (searchBox != null) searchBox.setVisible(next == Page.ATTRIBUTES);
        rebuildActions();
        updateMaxScroll();
        return true;
    }

    private boolean inTab(int mouseX, int x) {
        return mouseX >= leftPos + x && mouseX < leftPos + x + TAB_W;
    }

    /** 动作页鼠标落在第几个按钮，没落到返回 -1。 */
    private int actionIndexAt(int mouseX, int mouseY) {
        for (int i = 0; i < actions.size() && i < ACT_ROWS * 2; i++) {
            int col = i % 2;
            int row = i / 2;
            int x = leftPos + (col == 0 ? ACT_X0 : ACT_X1);
            int y = LIST_TOP + row * ACT_STEP;
            if (mouseX >= x && mouseX < x + ACT_W && mouseY >= y && mouseY < y + ACT_H) return i;
        }
        return -1;
    }

    /** 执行动作；危险动作要 2.5 秒内点第二次。 */
    private void runAction(int index) {
        if (index < 0 || index >= actions.size()) return;
        EntityPanelActions.Action action = actions.get(index);
        long now = System.currentTimeMillis();

        if (action.isDestructive()) {
            if (pendingAction != index || now - pendingActionAt > CONFIRM_MS) {
                pendingAction = index;
                pendingActionAt = now;
                return;
            }
            pendingAction = -1;
        }
        send(new NetworkHandler.EntityPanelCommandPacket(entity.getId(), action.ordinal()));
    }

    /** 一次性把所有适用开关设成同一个值（只发状态真的不同的那些）。 */
    private void setAllToggles(boolean on) {
        if (!editable) return;
        for (EntityPanelToggles.Toggle toggle : toggles) {
            if (EntityPanelToggles.isEnabled(enabledMask, toggle) == on) continue;
            if (on) enabledMask |= toggle.getBit();
            else enabledMask &= ~toggle.getBit();
            send(new NetworkHandler.EntityPanelActionPacket(entity.getId(), toggle.ordinal(), on));
        }
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
        send(new NetworkHandler.EntityPanelAttributePacket(entity.getId(), row.id(), target));
        cancelEditing();
    }

    /** 注册名 → 属性实例（找不到返回 null）。 */
    private static Attribute attributeOf(String attributeId) {
        ResourceLocation id = ResourceLocation.tryParse(attributeId);
        return id == null ? null : ForgeRegistries.ATTRIBUTES.getValue(id);
    }

    /** 罗马数字（药水等级），超过 10 直接给数字。 */
    private static String roman(int level) {
        String[] numerals = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return level >= 1 && level <= numerals.length ? numerals[level - 1] : String.valueOf(level);
    }

    /** tick → {@code mm:ss}；-1（无限时长）显示 ∞。 */
    private static String durationText(int ticks) {
        if (ticks < 0) return "∞";
        int seconds = ticks / 20;
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
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

    /**
     * 平时暂停（面板里翻属性、输数值时世界不会偷袭你）；
     * 刚发过包的那一百多毫秒放行，让服务端真的处理这次改动（见 {@link #pauseFreeUntil}）。
     */
    @Override
    public boolean isPauseScreen() {
        return System.currentTimeMillis() >= pauseFreeUntil;
    }

    /**
     * 统一的发包入口：除了把包丢出去，还顺手放行世界一小会儿。
     * <p>
     * 直接调 {@code CHANNEL.sendToServer} 也能发，但在暂停界面里包会一直不生效 ——
     * 所有「点了要有反应」的操作都必须走这里。
     */
    private void send(Object packet) {
        NetworkHandler.CHANNEL.sendToServer(packet);
        pauseFreeUntil = System.currentTimeMillis() + 150L;
        rebuildCountdown = 1;
    }
}
