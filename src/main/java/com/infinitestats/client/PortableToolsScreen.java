package com.infinitestats.client;

import com.infinitestats.crafting.PortableGuis;
import com.infinitestats.network.NetworkHandler;
import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.PlayerStatsProvider;
import com.infinitestats.stats.StatType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 随身工具枢纽 —— 把所有随身站点收进同一个面板，属性面板只保留一个入口。
 * <p>
 * 未解锁（对应开关属性未激活）的工具按钮呈灰色不可点，鼠标悬停会说明需要解锁哪一条属性；
 * 解锁后可点，点击即向服务端请求打开对应界面（服务端仍会再校验一次开关，改造过的客户端无法绕过）。
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
            "portable_grindstone");

    /** 「随身附魔台」开关属性 id：点击后打开的附魔界面里可以调「附魔强度」。 */
    private static final String ENCHANTING_ID = PortableGuis.STAT_ENCHANTING;

    private int leftPos;
    private int topPos;
    private PlayerStats stats;

    public PortableToolsScreen() {
        super(Component.translatable("gui.infinitestats.tools.title"));
    }

    @Override
    protected void init() {
        super.init();
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
            Component label = Component.translatable("stat.infinitestats." + id);
            Component tip;
            if (!unlocked) {
                tip = Component.translatable("gui.infinitestats.tools.tip_locked", label);
            } else if (ENCHANTING_ID.equals(id)) {
                // 附魔强度是隐藏属性，面板里看不到 —— 在这里把当前强度告诉玩家
                tip = Component.translatable("gui.infinitestats.tools.tip_enchanting",
                        Math.round(stats.getStatValue(PortableGuis.STAT_ENCHANT_POWER)),
                        maxEnchantPower());
            } else {
                tip = Component.translatable("gui.infinitestats.tools.tip_unlocked");
            }

            Button btn = Button.builder(label, b -> openTool(id))
                    .bounds(x, y, btnW, btnH)
                    .tooltip(Tooltip.create(tip))
                    .build();
            // 未解锁：灰色不可点，避免玩家以为是"点了没反应"
            btn.active = unlocked;
            addRenderableWidget(btn);
        }

        int footerY = topPos + GUI_HEIGHT - 32;
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.infinitestats.tools.back"),
                        b -> backToStats())
                .bounds(leftPos + GUI_WIDTH / 2 - 60, footerY, 120, 20)
                .build());
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

    /** 「附魔强度」属性的等级上限（＝满强度），用于提示文案。 */
    private static int maxEnchantPower() {
        StatType stat = StatType.fromId(PortableGuis.STAT_ENCHANT_POWER);
        return stat == null ? 0 : stat.getMaxLevel();
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
