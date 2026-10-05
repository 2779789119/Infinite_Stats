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
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.EnchantmentMenu;

import java.util.List;

/**
 * 随身附魔台界面 —— 原版附魔界面 + 右侧一列「附魔强度」加点。
 * <p>
 * 为什么需要这个界面：<b>随身附魔台本身只是一条 1 点解锁的开关</b>（属性面板里就是一个开关，
 * 不再有点数），附魔强度改成了隐藏属性 {@code enchant_power}（面板里不显示、无从加点）。
 * 因此强度的加点入口就放在这里：右侧扩展面板显示「附魔强度 Lv.N / 15」与 {@code −} / {@code +}，
 * 点一下即发 {@link NetworkHandler.EnchantPowerPacket}，服务端改完点数后同步回来并重算三档附魔，
 * 于是玩家在界面上就能当场看到三档附魔等级变化。
 * <p>
 * 实现上直接继承原版 {@link EnchantmentScreen}：原版界面（贴图、书页动画、三档附魔文字与点击）
 * 一行都不用改，只在界面右侧**另画一块扩展面板**并摆放两个按钮 ——
 * 因为原版贴图只有 176×166，把 {@code imageWidth} 改宽会让贴图裁切错位，所以保持原尺寸、
 * 把面板画在 {@code leftPos + 176} 之后。
 */
public class PortableEnchantingScreen extends EnchantmentScreen {

    /** 右侧扩展面板几何（相对 leftPos / topPos）。 */
    private static final int EXT_X = 176;
    private static final int EXT_W = 108;
    private static final int PAD = 8;
    private static final int BTN_W = 24;
    private static final int BTN_H = 18;
    private static final int BTN_Y = 22;

    private static final int COLOR_LABEL = 0x404040;
    private static final int COLOR_HINT = 0x6B6B6B;

    private Button powerDown;
    private Button powerUp;

    public PortableEnchantingScreen(EnchantmentMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("gui.infinitestats.enchant." + key, args);
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos + EXT_X + PAD;
        int right = leftPos + EXT_X + EXT_W - PAD - BTN_W;
        powerDown = addRenderableWidget(Button.builder(Component.literal("-"), b -> send(false))
                .bounds(x, topPos + BTN_Y, BTN_W, BTN_H)
                .tooltip(Tooltip.create(text("refund_tip"))).build());
        powerUp = addRenderableWidget(Button.builder(Component.literal("+"), b -> send(true))
                .bounds(right, topPos + BTN_Y, BTN_W, BTN_H)
                .tooltip(Tooltip.create(text("cost_tip"))).build());
    }

    private void send(boolean up) {
        NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.EnchantPowerPacket(up));
    }

    @Override
    public void containerTick() {
        super.containerTick();
        PlayerStats stats = clientStats();
        int power = PortableGuis.enchantPowerOf(stats);
        int max = maxPower();
        long available = stats == null ? 0L : stats.getAvailablePoints();

        if (powerUp != null) {
            boolean canUp = power < max && available > 0L;
            powerUp.active = canUp;
            powerUp.setTooltip(Tooltip.create(canUp
                    ? text("cost_tip")
                    : (power >= max ? text("max") : text("no_points"))));
        }
        if (powerDown != null) {
            powerDown.active = power > 0;
        }
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        // 原版附魔界面（贴图 + 书页动画 + 槽位）原样保留
        super.renderBg(gfx, partialTick, mouseX, mouseY);

        // 右侧扩展面板：与随身工作台 / 随身熔炉同一套扁平面板画法
        int x0 = leftPos + EXT_X;
        int y0 = topPos;
        gfx.fill(x0, y0, x0 + EXT_W, y0 + imageHeight, 0xFF373737);
        gfx.fill(x0 + 1, y0 + 1, x0 + EXT_W - 1, y0 + imageHeight - 1, 0xFFFFFFFF);
        gfx.fill(x0 + 3, y0 + 3, x0 + EXT_W - 3, y0 + imageHeight - 3, 0xFFC6C6C6);
    }

    @Override
    protected void renderLabels(GuiGraphics gfx, int mouseX, int mouseY) {
        super.renderLabels(gfx, mouseX, mouseY);

        int centerX = EXT_X + EXT_W / 2;
        int x = EXT_X + PAD;
        int width = EXT_W - PAD * 2;

        gfx.drawString(font, text("power_label"), x, 8, COLOR_LABEL, false);
        gfx.drawCenteredString(font, text("power", power(), maxPower()), centerX, 46, COLOR_LABEL);
        gfx.drawCenteredString(font, text("points", availablePoints()), centerX, 62, COLOR_LABEL);

        // 说明文字自动折行（面板只有 108 宽，中文一行放不下）
        List<FormattedCharSequence> lines = font.split(text("hint"), width);
        int y = 80;
        for (FormattedCharSequence line : lines) {
            gfx.drawString(font, line, x, y, COLOR_HINT);
            y += 10;
        }
    }

    private static PlayerStats clientStats() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        return mc.player.getCapability(PlayerStatsProvider.PLAYER_STATS).orElse(null);
    }

    private static int power() {
        return PortableGuis.enchantPowerOf(clientStats());
    }

    private static long availablePoints() {
        PlayerStats stats = clientStats();
        return stats == null ? 0L : stats.getAvailablePoints();
    }

    /** 「附魔强度」属性的等级上限（＝满强度 = 原版 15 个书架）。 */
    private static int maxPower() {
        StatType stat = StatType.fromId(PortableGuis.STAT_ENCHANT_POWER);
        return stat == null ? 0 : stat.getMaxLevel();
    }
}
