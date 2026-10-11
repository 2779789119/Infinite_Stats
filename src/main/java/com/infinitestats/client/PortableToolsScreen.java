package com.infinitestats.client;

import com.infinitestats.network.NetworkHandler;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import com.infinitestats.stats.StatType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 随身工具枢纽 —— 把所有随身站点收进同一个面板，属性面板只保留一个入口。
 * <p>
 * <b>左键 / 右键两种操作：</b>
 * <ul>
 *   <li><b>左键</b>：已解锁 → 打开对应界面；未解锁 → 直接花点数解锁（等价于属性面板里点「+」），
 *       不用再跑去属性面板找那条属性；</li>
 *   <li><b>右键</b>：关闭该工具并退还投入的点数（等价于属性面板里点「-」）。</li>
 * </ul>
 * 未解锁的按钮文字呈灰色，鼠标悬停会说明「左键花 1 点解锁」；解锁后可点，点击即向服务端请求打开对应界面
 * （服务端仍会再校验一次开关，改造过的客户端无法绕过）。解锁 / 关闭都复用属性面板同一个
 * {@link NetworkHandler.ModifyStatPacket}，点数校验与结算全在服务端。
 */
public final class PortableToolsScreen extends Screen {

    private static final int GUI_WIDTH = 440;
    private static final int GUI_HEIGHT = 376;
    private static final int BG_PANEL = 0xE81A1A2E;
    private static final int BORDER_COLOR = 0x403B82F6;
    private static final int TEXT_TITLE = 0xFFFFD166;
    private static final int TEXT_SUBTITLE = 0xFF94A3B8;
    private static final int TEXT_HINT = 0xFF64748B;

    /**
     * 工具清单：顺序即界面顺序（两列、自上而下逐行填充）。
     * 前五个沿用各自既有的打开数据包，其余统一走 {@link NetworkHandler.PortableToolOpenPacket}。
     */
    private static final List<String> TOOL_IDS = List.of(
            "portable_crafting",
            "portable_furnace",
            "portable_anvil",
            "portable_smithing",
            "portable_ender_chest",
            "portable_enchanting",
            "portable_stonecutter",
            "portable_loom",
            "portable_cartography",
            "portable_grindstone",
            "portable_brewing");


    private int leftPos;
    private int topPos;
    private PlayerStats stats;
    /** 与 {@link #TOOL_IDS} 一一对应的按钮，右键关闭时按位置命中。 */
    private final List<Button> toolButtons = new ArrayList<>();

    public PortableToolsScreen() {
        super(Component.translatable("gui.infinitestats.tools.title"));
    }

    @Override
    protected void init() {
        super.init();
        toolButtons.clear();
        leftPos = (width - GUI_WIDTH) / 2;
        topPos = (height - GUI_HEIGHT) / 2;
        stats = getClientStats();

        int margin = 16;
        int gap = 8;
        int btnW = (GUI_WIDTH - margin * 2 - gap) / 2;
        int btnH = 24;
        int startY = 62;
        int rowGap = 6;

        for (int i = 0; i < TOOL_IDS.size(); i++) {
            String id = TOOL_IDS.get(i);
            int col = i % 2;
            int row = i / 2;
            int x = leftPos + margin + col * (btnW + gap);
            int y = topPos + startY + row * (btnH + rowGap);

            boolean unlocked = isUnlocked(id);
            Component name = Component.translatable("stat.infinitestats." + id);
            // 未解锁用灰字标明状态（按钮仍可点：左键即解锁）
            Component label = unlocked ? name : name.copy().withStyle(ChatFormatting.DARK_GRAY);
            Component tip = unlocked
                    ? Component.translatable("gui.infinitestats.tools.tip_unlocked")
                    : Component.translatable("gui.infinitestats.tools.tip_locked", name);

            Button btn = Button.builder(label, b -> onLeftClick(id))
                    .bounds(x, y, btnW, btnH)
                    .tooltip(Tooltip.create(tip))
                    .build();
            addRenderableWidget(btn);
            toolButtons.add(btn);
        }

        int footerY = topPos + GUI_HEIGHT - 32;
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.infinitestats.tools.back"),
                        b -> backToStats())
                .bounds(leftPos + GUI_WIDTH / 2 - 60, footerY, 120, 20)
                .build());
    }

    /** 左键：已解锁就打开界面，未解锁就花点数解锁。 */
    private void onLeftClick(String id) {
        if (isUnlocked(id)) {
            openTool(id);
        } else {
            activateTool(id);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 右键 = 关闭该工具（退还点数）；未解锁的按钮右键无操作，直接吞掉
        if (button == 1) {
            for (int i = 0; i < toolButtons.size(); i++) {
                if (toolButtons.get(i).isMouseOver(mouseX, mouseY)) {
                    deactivateTool(TOOL_IDS.get(i));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * 解锁某个随身工具：按开关型属性一次到位（1 点），与属性面板里点「+」完全等价 ——
     * 本地先做一次校验（避免发无效请求），成功才发包，服务端会再结算一次。
     */
    private void activateTool(String id) {
        if (stats == null) return;
        StatType stat = StatType.fromId(id);
        if (stat == null) return;

        // 点数已经投过却仍不可用 → 多半是「功能开关」把整条关掉了，这里不给解锁
        if (stats.getStatLevel(stat) >= stat.getMaxLevel()) {
            showMessage(Component.translatable("message.infinitestats.tools_feature_disabled"));
            return;
        }

        long needed = stat.getMaxLevel() - stats.getStatLevel(stat);
        if (needed <= 0) needed = 1;
        if (stats.getAvailablePoints() < needed) {
            showMessage(Component.translatable("message.infinitestats.not_enough_points"));
            return;
        }
        if (!stats.addPoints(stat, needed)) {
            showMessage(Component.translatable("message.infinitestats.not_enough_points"));
            return;
        }
        NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.ModifyStatPacket(id, needed));
        rebuildWidgets();
    }

    /** 关闭某个随身工具：退还投入的点数（等价于属性面板里点「-」）。 */
    private void deactivateTool(String id) {
        if (stats == null) return;
        StatType stat = StatType.fromId(id);
        if (stat == null) return;
        if (stats.getStatLevel(stat) <= 0) return;
        if (!stats.removePoints(stat, 1)) return;
        NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.ModifyStatPacket(id, -1));
        rebuildWidgets();
    }

    private void showMessage(Component message) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.displayClientMessage(message, true);
        }
    }

    /** 点击某个已解锁的工具：按 id 发送对应的打开数据包。 */
    private void openTool(String id) {
        switch (id) {
            case "portable_crafting" ->
                    NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.CraftingOpenPacket());
            case "portable_furnace" ->
                    NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.FurnaceOpenPacket());
            case "portable_anvil" ->
                    NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.AnvilOpenPacket());
            case "portable_ender_chest" ->
                    NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.EnderChestOpenPacket());
            default ->
                    NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.PortableToolOpenPacket(id));
        }
    }

    private void backToStats() {
        if (minecraft != null) {
            minecraft.setScreen(new StatsScreen());
        }
    }

    private static PlayerStats getClientStats() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        return mc.player.getCapability(PlayerStatsProvider.PLAYER_STATS).orElse(null);
    }

    private int unlockedCount() {
        int n = 0;
        for (String id : TOOL_IDS) {
            if (isUnlocked(id)) n++;
        }
        return n;
    }

    /**
     * 该工具是否可用：用「有效等级 &gt; 0」判断，开关型属性激活时等级为 1，
     * 且天然尊重「功能开关」把整条关掉的情况。
     */
    private boolean isUnlocked(String id) {
        return stats != null && stats.getEffectiveStatLevel(id) > 0;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        graphics.fill(leftPos - 2, topPos - 2, leftPos + GUI_WIDTH + 2, topPos + GUI_HEIGHT + 2, BORDER_COLOR);
        graphics.fill(leftPos, topPos, leftPos + GUI_WIDTH, topPos + GUI_HEIGHT, BG_PANEL);

        graphics.drawCenteredString(font, title, leftPos + GUI_WIDTH / 2, topPos + 14, TEXT_TITLE);
        graphics.drawCenteredString(font,
                Component.translatable("gui.infinitestats.tools.subtitle", unlockedCount(), TOOL_IDS.size()),
                leftPos + GUI_WIDTH / 2, topPos + 28, TEXT_SUBTITLE);
        graphics.drawCenteredString(font, Component.translatable("gui.infinitestats.tools.hint"),
                leftPos + GUI_WIDTH / 2, topPos + GUI_HEIGHT - 44, TEXT_HINT);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
