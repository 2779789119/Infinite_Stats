package com.infinitestats.client;

import com.infinitestats.Config;
import com.infinitestats.network.NetworkHandler;
import com.infinitestats.stats.PlayerStatsProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * 天气控制面板 —— 把原来「点一次切一档」的循环按钮换成一个可选列表。
 * <p>
 * <b>关于「所有已注册的天气」：</b>原版 Minecraft 并<b>没有</b>天气注册表 —— 天气只是
 * {@code Level} 上的两个布尔（{@code raining} / {@code thundering}）加两个计时器，
 * 所以能列的档位就是 <b>晴天 / 下雨 / 雷暴</b> 三档（下雪是「下雨 + 寒冷生物群系」的渲染表现，
 * 不是独立天气）。天气类模组各写各的字段，没有统一注册表可以枚举，
 * 因此这里把档位列成一张表 {@link #ENTRY_IDS}：以后要加档位（含联动某个天气模组）只改这张表。
 * <p>
 * 切换作用于**主世界**：玩家在下界 / 末地时切的仍是主世界天气，所以这个入口在任何维度都有意义。
 */
public class WeatherScreen extends Screen {

    private static final int GUI_W = 300;
    private static final int GUI_H = 212;

    private static final int BG_PANEL = 0xE81A1A2E;
    private static final int TEXT_GOLD = 0xFFFFD166;
    private static final int TEXT_LABEL = 0xFFCBD5E1;
    private static final int TEXT_HINT = 0xFF64748B;
    private static final int TEXT_ACTIVE = 0xFF4ADE80;
    private static final int TEXT_NEGATIVE = 0xFFF87171;
    private static final int TEXT_CURRENT = 0xFF93C5FD;

    /** 可切换的天气档位（顺序即界面顺序）。 */
    private static final String[] ENTRY_IDS = {"clear", "rain", "thunder"};

    private static final int BTN_TOP = 66;
    private static final int BTN_H = 30;
    private static final int BTN_GAP = 8;

    private int leftPos;
    private int topPos;
    private boolean toggleActive;
    private long lastSoundTick;

    private final List<Button> entryButtons = new ArrayList<>();

    public WeatherScreen() {
        super(Component.translatable("gui.infinitestats.weather.title"));
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("gui.infinitestats.weather." + key, args);
    }

    private static ItemStack icon(String id) {
        return switch (id) {
            case "rain" -> new ItemStack(Items.WATER_BUCKET);
            case "thunder" -> new ItemStack(Items.LIGHTNING_ROD);
            default -> new ItemStack(Items.SUNFLOWER);
        };
    }

    @Override
    protected void init() {
        super.init();
        leftPos = (width - GUI_W) / 2;
        topPos = (height - GUI_H) / 2;

        toggleActive = isToggleActive();
        entryButtons.clear();

        int btnW = GUI_W - 48;
        int x = leftPos + 24;
        for (int i = 0; i < ENTRY_IDS.length; i++) {
            final String id = ENTRY_IDS[i];
            int y = topPos + BTN_TOP + i * (BTN_H + BTN_GAP);
            Button btn = Button.builder(text("entry." + id),
                            b -> request(id))
                    .bounds(x, y, btnW, BTN_H)
                    .tooltip(Tooltip.create(text("desc." + id)))
                    .build();
            btn.active = toggleActive;
            addRenderableWidget(btn);
            entryButtons.add(btn);
        }

        addRenderableWidget(Button.builder(text("back"), b -> backToStats())
                .bounds(leftPos + GUI_W / 2 - 50, topPos + GUI_H - 28, 100, 20)
                .build());
    }

    @Override
    public void tick() {
        super.tick();
        // 属性可能刚在属性面板里被开关，打开期间保持一致
        boolean active = isToggleActive();
        if (active != toggleActive) {
            toggleActive = active;
            for (Button btn : entryButtons) btn.active = active;
        }
    }

    private static boolean isToggleActive() {
        Player player = Minecraft.getInstance().player;
        if (player == null) return false;
        return player.getCapability(PlayerStatsProvider.PLAYER_STATS)
                .map(stats -> stats.isToggleActive("weather_control"))
                .orElse(false);
    }

    /** 当前主世界天气档位（客户端本地读，切换后由服务端同步过来）。 */
    private String currentWeather() {
        if (minecraft == null || minecraft.level == null) return "";
        if (minecraft.level.isThundering()) return "thunder";
        if (minecraft.level.isRaining()) return "rain";
        return "clear";
    }

    private void request(String id) {
        playClickSound();
        NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.WeatherControlPacket(id));
    }

    private void backToStats() {
        if (minecraft != null) minecraft.setScreen(new StatsScreen());
    }

    private void playClickSound() {
        if (minecraft == null) return;
        long now = System.currentTimeMillis();
        if (now - lastSoundTick < 50) return;
        lastSoundTick = now;
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        graphics.fill(leftPos - 2, topPos - 2, leftPos + GUI_W + 2, topPos + GUI_H + 2, 0x403B82F6);
        graphics.fill(leftPos - 1, topPos - 1, leftPos + GUI_W + 1, topPos + GUI_H + 1, 0xFF1E293B);
        graphics.fill(leftPos, topPos, leftPos + GUI_W, topPos + GUI_H, BG_PANEL);

        graphics.drawCenteredString(font, title.getString(), leftPos + GUI_W / 2, topPos + 12, TEXT_GOLD);

        // 状态：是否已解锁 + 当前天气
        Component status = toggleActive ? text("on") : text("off");
        graphics.drawCenteredString(font, status.getString(), leftPos + GUI_W / 2, topPos + 30,
                toggleActive ? TEXT_ACTIVE : TEXT_NEGATIVE);

        String current = currentWeather();
        Component currentText = text("current", text("entry." + (current.isEmpty() ? "clear" : current)));
        graphics.drawCenteredString(font, currentText.getString(), leftPos + GUI_W / 2, topPos + 44,
                TEXT_CURRENT);

        // 档位按钮（先画按钮再补图标，图标不会挡住按钮文字）
        super.render(graphics, mouseX, mouseY, partialTick);
        for (int i = 0; i < entryButtons.size(); i++) {
            Button btn = entryButtons.get(i);
            graphics.renderItem(icon(ENTRY_IDS[i]), btn.getX() + 6, btn.getY() + 7);
            // 当前档位画一条高亮边，省得玩家去比对文案
            if (ENTRY_IDS[i].equals(current)) {
                graphics.renderOutline(btn.getX(), btn.getY(), btn.getWidth(), btn.getHeight(), 0xFF4ADE80);
            }
        }

        // 底栏说明：作用范围 + 持续时长
        int seconds = Math.max(1, Config.WEATHER_CYCLE_DURATION.get() / 20);
        graphics.drawCenteredString(font, text("hint", seconds).getString(),
                leftPos + GUI_W / 2, topPos + GUI_H - 46, TEXT_HINT);
        graphics.drawCenteredString(font, text("scope").getString(),
                leftPos + GUI_W / 2, topPos + GUI_H - 58, TEXT_HINT);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
