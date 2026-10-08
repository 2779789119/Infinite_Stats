package com.infinitestats.client;

import com.infinitestats.crafting.PortableInfuser;
import com.infinitestats.crafting.PortableInfuserMenu;
import com.infinitestats.network.NetworkHandler;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import com.infinitestats.util.EnchantLimits;
import com.infinitestats.util.EnchantText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 随身「进阶高级附魔台」界面 —— 自己挑附魔，不用随机（参考 Enchanting Infuser 的进阶档）。
 * <p>
 * 界面构成：
 * <ul>
 *   <li>左上角一个物品槽 + 一条可滚动的附魔清单：每行「附魔名 · 等级 · 价格」，
 *       点 {@code <} / {@code >} 调等级（0 = 不附 / 拆掉），按住 Shift 点击直接清零或拉到满级；</li>
 *   <li>价格列显示每条自己的价，底部一行显示<b>本次实付</b>（按差额算：物品已有的附魔不重复收费、
 *       降级退差价）与当前钱包；</li>
 *   <li>底部三个按钮：附魔 / 修复（花等级把耐久修满）/ 回收（拆掉全部附魔换回等级）；</li>
 *   <li>右上角切换付款方式：经验等级 ⇄ 可用属性点数（两种都不消耗青金石）。</li>
 * </ul>
 * <b>清单与价格全部在客户端现算</b>（{@link PortableInfuser} 的双端同源纯函数），
 * 所以点一下箭头界面立刻就变；只有点「附魔 / 修复 / 回收」才发包，服务端会整体重算一遍
 * —— 界面上的数字只是展示，不是凭据，改过的客户端既拿不到附魔也少不了钱。
 * <p>
 * <b>为什么整张清单自己画，而不是摆一堆按钮控件：</b>清单要能滚动、每行还带两个小箭头，
 * 用 {@code Button} 控件既无法随滚动整体移动，也很难只画在清单底板范围内。
 * 这里是纯 {@code fill} + 文本绘制，与原版槽位渲染共存。
 */
public class PortableInfuserScreen extends AbstractContainerScreen<PortableInfuserMenu> {

    // ===== 总尺寸与配色（沿用随身工具面板那套深色扁平风） =====
    private static final int GUI_W = 248;
    private static final int GUI_H = 266;
    private static final int BG_PANEL = 0xE81A1A2E;
    private static final int BORDER_COLOR = 0x403B82F6;
    private static final int PANEL_INSET = 0x66000000;
    private static final int SLOT_BORDER = 0x50FFFFFF;
    private static final int TEXT_TITLE = 0xFFFFD166;
    private static final int TEXT_NORMAL = 0xFFE2E8F0;
    private static final int TEXT_DIM = 0xFF64748B;
    private static final int TEXT_PRICE = 0xFF94A3B8;
    private static final int TEXT_CHANGED = 0xFF55FF55;
    private static final int TEXT_LACK = 0xFFFF6B6B;
    private static final int ROW_PICKED = 0x30FFFFFF;

    // ===== 附魔清单区域 =====
    private static final int LIST_X = 46;
    private static final int LIST_Y = 24;
    private static final int LIST_W = 194;
    private static final int LIST_H = 112;
    private static final int ROW_H = 18;
    private static final int VISIBLE_ROWS = LIST_H / ROW_H;

    /** 行内：名称列 / 等级列 / ⟨ ⟩ 箭头 / 价格列（右对齐）。 */
    private static final int NAME_X = LIST_X + 4;
    private static final int NAME_W = 74;
    private static final int MINUS_X = LIST_X + 86;
    private static final int PLUS_X = LIST_X + 128;
    private static final int ARROW_W = 14;
    private static final int ARROW_H = 16;
    private static final int LEVEL_CX = LIST_X + 114;
    private static final int PRICE_RIGHT = LIST_X + LIST_W - 6;

    // ===== 按钮行 / 结算行 =====
    private static final int BTN_Y = 140;
    private static final int BTN_H = 20;
    private static final int INFO_Y = 165;

    /** 当前选择：目标等级（0 = 不附 / 拆掉）。缺省即 0。 */
    private final Map<Enchantment, Integer> selection = new LinkedHashMap<>();
    /** 当前物品可选的附魔（顺序固定，不随选择变化，见 PortableInfuser#applicable）。 */
    private List<Enchantment> enchantments = new ArrayList<>();
    /** 上次见到的物品：靠它发现「物品换了 / 附魔生效回传了」并整表重算。 */
    private ItemStack cachedItem = ItemStack.EMPTY;
    private int scroll;
    private PortableInfuser.Payment payment = PortableInfuser.Payment.LEVELS;

    private Button applyButton;
    private Button repairButton;
    private Button recycleButton;
    private Button paymentButton;

    public PortableInfuserScreen(PortableInfuserMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = GUI_W;
        this.imageHeight = GUI_H;
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("gui.infinitestats.infuser." + key, args);
    }

    private static PlayerStats clientStats() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null
                : mc.player.getCapability(PlayerStatsProvider.PLAYER_STATS).orElse(null);
    }

    private static long availablePoints() {
        PlayerStats stats = clientStats();
        return stats == null ? 0L : stats.getAvailablePoints();
    }

    private static int experienceLevels() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? 0 : mc.player.experienceLevel;
    }

    private ItemStack inputItem() {
        return this.menu.getSlot(PortableInfuserMenu.SLOT_ITEM).getItem();
    }

    private Map<Enchantment, Integer> currentEnchantments() {
        ItemStack item = inputItem();
        return item.isEmpty() ? Map.of() : EnchantmentHelper.getEnchantments(item);
    }

    @Override
    protected void init() {
        super.init();
        refresh();

        int y = topPos + BTN_Y;
        applyButton = addRenderableWidget(Button.builder(text("do_enchant"), b -> sendEnchant())
                .bounds(leftPos + LIST_X, y, 70, BTN_H).build());
        repairButton = addRenderableWidget(Button.builder(text("do_repair"),
                        b -> sendSimple(NetworkHandler.InfuserActionPacket.ACTION_REPAIR))
                .bounds(leftPos + 120, y, 56, BTN_H).build());
        recycleButton = addRenderableWidget(Button.builder(text("do_recycle"),
                        b -> sendSimple(NetworkHandler.InfuserActionPacket.ACTION_RECYCLE))
                .bounds(leftPos + 180, y, 56, BTN_H).build());
        paymentButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            payment = payment == PortableInfuser.Payment.LEVELS
                    ? PortableInfuser.Payment.POINTS
                    : PortableInfuser.Payment.LEVELS;
            refreshWidgets();
        }).bounds(leftPos + GUI_W - 96, topPos + 4, 92, 18).build());
    }

    // ===== 状态维护 =====

    /** 物品变了就整表重算：清单换一套、选择回到「物品当前的样子」。 */
    private void refresh() {
        ItemStack item = inputItem();
        this.cachedItem = item.copy();
        this.enchantments = PortableInfuser.applicable(item);
        this.selection.clear();
        Map<Enchantment, Integer> current = currentEnchantments();
        for (Enchantment enchantment : this.enchantments) {
            this.selection.put(enchantment, current.getOrDefault(enchantment, 0));
        }
        this.scroll = 0;
    }

    @Override
    public void containerTick() {
        super.containerTick();
        // 物品被换掉 / 附魔生效回传 / 数量变化 —— 都靠这一处比对触发重算
        if (!ItemStack.matches(this.cachedItem, inputItem())) {
            refresh();
        }
        refreshWidgets();
    }

    /** 按钮文案、可用状态与提示（每 tick 刷新，随选择与钱包实时变化）。 */
    private void refreshWidgets() {
        if (paymentButton != null) {
            paymentButton.setMessage(payment == PortableInfuser.Payment.POINTS
                    ? text("pay_points", availablePoints())
                    : text("pay_levels", experienceLevels()));
            paymentButton.setTooltip(Tooltip.create(text("pay_tip")));
        }

        long delta = costDelta();
        if (applyButton != null) {
            applyButton.active = delta != 0L && canPay(Math.max(delta, 0L));
            applyButton.setTooltip(Tooltip.create(text("apply_tip", deltaText(delta))));
        }

        ItemStack item = inputItem();
        if (repairButton != null) {
            int cost = PortableInfuser.repairCost(item);
            repairButton.active = cost > 0 && canPay(cost);
            repairButton.setTooltip(Tooltip.create(cost > 0
                    ? text("repair_tip", PortableInfuser.priceText(payment, cost))
                    : text("repair_none")));
        }
        if (recycleButton != null) {
            int refund = PortableInfuser.recycleRefund(item);
            recycleButton.active = refund > 0;
            recycleButton.setTooltip(Tooltip.create(refund > 0
                    ? text("recycle_tip", PortableInfuser.priceText(payment, refund))
                    : text("recycle_none")));
        }
    }

    /** 钱包够不够（界面只做按钮灰显；真正结算在服务端）。突破后金额可以很大，故用 long。 */
    private boolean canPay(long cost) {
        if (cost <= 0L) return true;
        if (payment == PortableInfuser.Payment.POINTS) return availablePoints() >= cost;
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && (mc.player.isCreative() || mc.player.experienceLevel >= cost);
    }

    /**
     * 当前玩家对该附魔的等级上限。
     * <p>
     * 未解锁「附魔上限突破」时 = <b>整合包实际生效的上限</b>（装了 Apotheosis 时是它配置的等级上限，
     * 例如锋利 9 级而不是原版的 5 级）；解锁后 = int 上限。
     */
    private int capOf(Enchantment enchantment) {
        return EnchantLimits.maxLevel(Minecraft.getInstance().player, enchantment);
    }

    /** 是否已解锁「附魔上限突破」（用于提示文案）。 */
    private boolean unlimited() {
        return EnchantLimits.isUnlocked(Minecraft.getInstance().player);
    }

    /**
     * 本次「附魔」的实付差额（正 = 花，负 = 退）。
     * <p>
     * 与服务端 {@link PortableInfuser#performEnchant} 同一口径：逐条比对「目标等级」与
     * 「物品现在的等级」，只算差额 —— 界面上显示多少钱，就扣多少钱。
     */
    private long costDelta() {
        Map<Enchantment, Integer> current = currentEnchantments();
        long delta = 0L;
        for (Map.Entry<Enchantment, Integer> entry : selection.entrySet()) {
            int target = Math.max(0, entry.getValue());
            int now = current.getOrDefault(entry.getKey(), 0);
            if (target == now) continue;
            delta += (long) PortableInfuser.costOf(entry.getKey(), target)
                    - PortableInfuser.costOf(entry.getKey(), now);
        }
        return delta;
    }

    private String deltaText(long delta) {
        if (delta > 0) return text("info_cost", PortableInfuser.priceText(payment, delta)).getString();
        if (delta < 0) return text("info_refund", PortableInfuser.priceText(payment, -delta)).getString();
        return text("info_none").getString();
    }

    /** 该条是否被别的已选附魔挡住（原版互斥，例如锋利 / 亡灵杀手）。 */
    private boolean blocked(Enchantment enchantment) {
        return blockerOf(enchantment) != null;
    }

    /** 挡住它的那条附魔（没有则返回 null）。 */
    private Enchantment blockerOf(Enchantment enchantment) {
        // 自己已经选着的话，它本身就是冲突源，不能算「被挡」
        if (selection.getOrDefault(enchantment, 0) > 0) return null;
        for (Map.Entry<Enchantment, Integer> entry : selection.entrySet()) {
            if (entry.getValue() > 0 && entry.getKey() != enchantment
                    && !enchantment.isCompatibleWith(entry.getKey())) {
                return entry.getKey();
            }
        }
        return null;
    }

    // ===== 发包 =====

    private void sendEnchant() {
        Map<Enchantment, Integer> payload = new LinkedHashMap<>();
        for (Map.Entry<Enchantment, Integer> entry : selection.entrySet()) {
            payload.put(entry.getKey(), Math.max(0, entry.getValue()));
        }
        boolean points = payment == PortableInfuser.Payment.POINTS;
        NetworkHandler.CHANNEL.sendToServer(NetworkHandler.InfuserActionPacket.enchant(points, payload));
    }

    private void sendSimple(int action) {
        boolean points = payment == PortableInfuser.Payment.POINTS;
        NetworkHandler.CHANNEL.sendToServer(NetworkHandler.InfuserActionPacket.simple(action, points));
    }

    // ===== 交互 =====

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (handleListClick(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleListClick(double mouseX, double mouseY, int button) {
        // 注意：鼠标事件给的是**屏幕绝对坐标**（原版 isHovering 内部也是先减 leftPos/topPos），
        // 所以这里统一换算到界面相对坐标，别拿布局常量直接比
        double x = mouseX - leftPos;
        double y = mouseY - topPos;
        if (x < LIST_X || x > LIST_X + LIST_W) return false;
        if (y < LIST_Y || y > LIST_Y + LIST_H) return false;
        int visibleRow = (int) ((y - LIST_Y) / ROW_H);
        int index = visibleRow + scroll;
        if (index < 0 || index >= enchantments.size()) return true;

        Enchantment enchantment = enchantments.get(index);
        double rowY = LIST_Y + visibleRow * ROW_H;
        boolean onArrow = y >= rowY && y <= rowY + ARROW_H;
        boolean extreme = hasShiftDown();

        if (onArrow && x >= MINUS_X && x < MINUS_X + ARROW_W) {
            adjust(enchantment, false, extreme);
            return true;
        }
        if (onArrow && x >= PLUS_X && x < PLUS_X + ARROW_W) {
            adjust(enchantment, true, extreme);
            return true;
        }
        // 行内其它位置也能调：左键 +1、右键 -1（连点比每次都对准小箭头省事）
        adjust(enchantment, button != 1, extreme);
        return true;
    }

    /**
     * 调等级：0 = 不选（已附魔的即拆掉）。
     * <p>
     * 上限取「该玩家的」上限 —— 未解锁时是整合包实际生效的上限，解锁「附魔上限突破」后不再受限。
     * 按住 Shift 是「跳到两端」：未解锁时那一端就是整合包满级（例如锋利 9）；
     * 解锁后上限是 int 上限（21 亿），真跳过去既付不起也没意义，所以仍然只跳到<b>原版满级</b>，
     * 想再往上就继续点 {@code >}。
     */
    private void adjust(Enchantment enchantment, boolean increase, boolean extreme) {
        if (blocked(enchantment)) return;
        int now = selection.getOrDefault(enchantment, 0);
        int max = capOf(enchantment);
        int next;
        if (extreme) {
            int full = unlimited() ? enchantment.getMaxLevel() : max;
            next = increase ? Math.max(now, full) : 0;
        } else if (increase) {
            // 已经在上限时不再 +1：int 上限下 now + 1 会翻成负数
            next = now >= max ? max : now + 1;
        } else {
            next = Math.max(0, now - 1);
        }
        if (next == now) return;
        selection.put(enchantment, next);
        refreshWidgets();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isOverList(mouseX, mouseY)) {
            int max = Math.max(0, enchantments.size() - VISIBLE_ROWS);
            this.scroll = Math.max(0, Math.min(max, this.scroll + (delta > 0 ? -1 : 1)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    /** 鼠标（屏幕绝对坐标）是否落在附魔清单上。 */
    private boolean isOverList(double mouseX, double mouseY) {
        return mouseX >= leftPos + LIST_X && mouseX <= leftPos + LIST_X + LIST_W
                && mouseY >= topPos + LIST_Y && mouseY <= topPos + LIST_Y + LIST_H;
    }

    // ===== 绘制 =====

    @Override
    protected void renderBg(GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        int x0 = leftPos;
        int y0 = topPos;
        gfx.fill(x0 - 2, y0 - 2, x0 + GUI_W + 2, y0 + GUI_H + 2, BORDER_COLOR);
        gfx.fill(x0, y0, x0 + GUI_W, y0 + GUI_H, BG_PANEL);

        // 清单底板
        gfx.fill(x0 + LIST_X, y0 + LIST_Y, x0 + LIST_X + LIST_W, y0 + LIST_Y + LIST_H, PANEL_INSET);

        // 行：已选中的整行浅色高亮；箭头可点性随状态变化
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = scroll + row;
            if (index >= enchantments.size()) break;
            Enchantment enchantment = enchantments.get(index);
            int chosen = selection.getOrDefault(enchantment, 0);
            boolean blocked = blocked(enchantment);
            int rowY = y0 + LIST_Y + row * ROW_H;
            if (chosen > 0) {
                gfx.fill(x0 + LIST_X + 1, rowY, x0 + LIST_X + LIST_W - 1, rowY + ROW_H, ROW_PICKED);
            }
            drawArrow(gfx, x0 + MINUS_X, rowY + 1, "<", mouseX, mouseY, !blocked && chosen > 0);
            drawArrow(gfx, x0 + PLUS_X, rowY + 1, ">", mouseX, mouseY,
                    !blocked && chosen < capOf(enchantment));
        }

        drawScrollbar(gfx, x0, y0);

        // 槽位描边（物品槽 + 背包 + 快捷栏）：自绘背景，所以原版槽位贴图没有了，边框得自己补
        drawSlotFrame(gfx, x0 + PortableInfuserMenu.ITEM_SLOT_X, y0 + PortableInfuserMenu.ITEM_SLOT_Y);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlotFrame(gfx, x0 + PortableInfuserMenu.INV_X + col * 18,
                        y0 + PortableInfuserMenu.INV_Y + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlotFrame(gfx, x0 + PortableInfuserMenu.INV_X + col * 18, y0 + PortableInfuserMenu.HOTBAR_Y);
        }
    }

    private void drawScrollbar(GuiGraphics gfx, int x0, int y0) {
        int total = enchantments.size();
        if (total <= VISIBLE_ROWS) return;
        int trackX = x0 + LIST_X + LIST_W - 4;
        gfx.fill(trackX, y0 + LIST_Y + 1, trackX + 3, y0 + LIST_Y + LIST_H - 1, 0x40FFFFFF);
        int thumbH = Math.max(12, (LIST_H - 2) * VISIBLE_ROWS / total);
        int maxScroll = total - VISIBLE_ROWS;
        int thumbY = y0 + LIST_Y + 1
                + (maxScroll == 0 ? 0 : (LIST_H - 2 - thumbH) * scroll / maxScroll);
        gfx.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, 0xA0FFFFFF);
    }

    private static void drawSlotFrame(GuiGraphics gfx, int x, int y) {
        gfx.fill(x - 1, y - 1, x + 17, y + 17, SLOT_BORDER);
        gfx.fill(x, y, x + 16, y + 16, 0x80000000);
    }

    private void drawArrow(GuiGraphics gfx, int x, int y, String glyph,
                           int mouseX, int mouseY, boolean active) {
        boolean hover = active && mouseX >= x && mouseX < x + ARROW_W && mouseY >= y && mouseY < y + ARROW_H;
        gfx.fill(x, y, x + ARROW_W, y + ARROW_H, hover ? 0x60FFFFFF : 0x30FFFFFF);
        gfx.drawString(font, glyph, x + 4, y + 4, active ? TEXT_NORMAL : TEXT_DIM, false);
    }

    @Override
    protected void renderLabels(GuiGraphics gfx, int mouseX, int mouseY) {
        gfx.drawString(font, title, 8, 10, TEXT_TITLE, false);

        ItemStack item = inputItem();
        if (item.isEmpty() || enchantments.isEmpty()) {
            String hint = item.isEmpty() ? text("put_item").getString() : text("no_enchant").getString();
            gfx.drawString(font, hint, LIST_X + 4, LIST_Y + 6, TEXT_DIM, false);
        } else {
            for (int row = 0; row < VISIBLE_ROWS; row++) {
                int index = scroll + row;
                if (index >= enchantments.size()) break;
                drawRow(gfx, index, row);
            }
        }

        // 结算行：左边本次实付，右边钱包（解锁突破时额外标注「上限不限」）
        long delta = costDelta();
        gfx.drawString(font, deltaText(delta), LIST_X, INFO_Y,
                delta > 0 && !canPay(delta) ? TEXT_LACK : TEXT_NORMAL, false);
        Component wallet = unlimited()
                ? text("wallet_unlimited", experienceLevels(), availablePoints())
                : text("wallet", experienceLevels(), availablePoints());
        gfx.drawString(font, wallet, PRICE_RIGHT - font.width(wallet), INFO_Y, TEXT_DIM, false);
    }

    private void drawRow(GuiGraphics gfx, int index, int visibleRow) {
        Enchantment enchantment = this.enchantments.get(index);
        int rowY = LIST_Y + visibleRow * ROW_H;
        Enchantment blocker = blockerOf(enchantment);
        int target = selection.getOrDefault(enchantment, 0);
        int current = currentEnchantments().getOrDefault(enchantment, 0);
        int nameColor = blocker != null ? TEXT_DIM : (enchantment.isCurse() ? TEXT_LACK : TEXT_NORMAL);

        gfx.drawString(font, trim(enchantment.getFullname(1).getString(), NAME_W),
                NAME_X, rowY + 5, nameColor, false);

        // 等级：与物品当前等级不同就用高亮色标出来（一眼看出哪些改动会被提交）
        // 超过 10 级原版没有译文（会显示成 enchantment.level.11 这种键名），交给 EnchantText
        String level = target <= 0 ? "-" : EnchantText.levelSuffix(target);
        int levelColor = blocker != null ? TEXT_DIM : (target == current ? TEXT_PRICE : TEXT_CHANGED);
        gfx.drawString(font, level, LEVEL_CX - font.width(level) / 2, rowY + 5, levelColor, false);

        // 该条自己的价（按目标等级）；被挡住的直接标「冲突」
        if (blocker != null) {
            String mark = text("conflict_mark").getString();
            gfx.drawString(font, mark, PRICE_RIGHT - font.width(mark), rowY + 5, TEXT_LACK, false);
        } else if (target > 0) {
            String price = String.valueOf(PortableInfuser.costOf(enchantment, target));
            gfx.drawString(font, price, PRICE_RIGHT - font.width(price), rowY + 5, TEXT_PRICE, false);
        }
    }

    private String trim(String value, int maxWidth) {
        if (font.width(value) <= maxWidth) return value;
        String cut = value;
        while (!cut.isEmpty() && font.width(cut + "...") > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "...";
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        super.render(gfx, mouseX, mouseY, partialTick);
        renderRowTooltip(gfx, mouseX, mouseY);
    }

    /** 悬停某一行时补全信息（行内空间有限，长名字与价格口径都在这里说清楚）。 */
    private void renderRowTooltip(GuiGraphics gfx, int mouseX, int mouseY) {
        if (!isOverList(mouseX, mouseY)) return;
        int index = (mouseY - topPos - LIST_Y) / ROW_H + scroll;
        if (index < 0 || index >= enchantments.size()) return;

        Enchantment enchantment = enchantments.get(index);
        Enchantment blocker = blockerOf(enchantment);
        int target = selection.getOrDefault(enchantment, 0);
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(enchantment.getFullname(Math.max(1, target)));
        if (blocker != null) {
            tooltip.add(text("conflict_tip", blocker.getFullname(1)));
        } else {
            tooltip.add(target <= 0
                    ? text("row_none")
                    : text("row_price", PortableInfuser.priceText(payment,
                    PortableInfuser.costOf(enchantment, target))));
            tooltip.add(unlimited()
                    ? text("row_hint_unlimited")
                    : text("row_hint", capOf(enchantment)));
        }
        // GuiGraphics 没有「Component 列表」重载，且长句要自己折行，所以走 FormattedCharSequence
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (Component line : tooltip) {
            lines.addAll(font.split(line, 220));
        }
        gfx.renderTooltip(font, lines, mouseX, mouseY);
    }
}
