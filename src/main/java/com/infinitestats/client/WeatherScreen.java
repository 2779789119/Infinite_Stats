package com.infinitestats.client;

import com.infinitestats.Config;
import com.infinitestats.network.NetworkHandler;
import com.infinitestats.network.WeatherControlPacket;
import com.infinitestats.stats.PlayerStatsProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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
 * 天气控制面板 —— 把天气做成「点哪档切哪档」的列表，而不是循环按钮。
 * <p>
 * <b>关于「所有已注册的天气」：</b>原版 Minecraft 并<b>没有</b>天气注册表 —— 天气只是
 * {@code Level} 上的两个布尔（{@code raining} / {@code thundering}）加两个计时器，
 * 所以能列的档位就是 <b>晴天 / 下雨 / 雷暴</b> 三档（下雪是「下雨 + 寒冷生物群系」的渲染表现，
 * 不是独立天气）。因此这里把档位列成一张表 {@link #ENTRY_IDS}，以后要加档位只改这张表。
 * <p>
 * 切换作用于**主世界**：玩家在下界 / 末地时切的仍是主世界天气，所以这个入口在任何维度都有意义。
 * 需要「天气控制」属性已激活，未激活时按钮全部置灰。
 */
public class WeatherScreen extends PanelScreen {

    private static final int GUI_W = 300;
    private static final int GUI_H = 212;

    @Override
    protected int panelW() {
        return GUI_W;
    }

    @Override
    protected int panelH() {
        return GUI_H;
    }

    private static final int BTN_TOP = 66;
    private static final int BTN_H = 30;
    private static final int BTN_GAP = 8;

    /** 可切换的天气档位（顺序即界面顺序）。 */
    private static final String[] ENTRY_IDS = {
            WeatherControlPacket.CLEAR, WeatherControlPacket.RAIN, WeatherControlPacket.THUNDER
    };

    // leftPos / topPos 由 PanelScreen 提供（恒为 0：面板坐标从 (0,0) 起）
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
            case WeatherControlPacket.RAIN -> new ItemStack(Items.WATER_BUCKET);
            case WeatherControlPacket.THUNDER -> new ItemStack(Items.LIGHTNING_ROD);
            default -> new ItemStack(Items.SUNFLOWER);
        };
    }

    @Override
    protected void init() {
        super.init();
        // 面板坐标从 (0,0) 起：居中、缩放与拖动都由 PanelScreen 负责
        leftPos = 0;
        topPos = 0;

        toggleActive = isToggleActive();
        entryButtons.clear();

        int btnW = GUI_W - 48;
        int x = leftPos + 24;
        for (int i = 0; i < ENTRY_IDS.length; i++) {
            final String id = ENTRY_IDS[i];
            int y = topPos + BTN_TOP + i * (BTN_H + BTN_GAP);
            Button btn = Button.builder(text("entry." + id), b -> request(id))
                    .bounds(x, y, btnW, BTN_H)
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
        return PlayerStatsProvider.of(player)
                .map(stats -> stats.isToggleActive("weather_control"))
                .orElse(false);
    }

    /** 当前主世界天气档位（客户端本地读，切换后由服务端同步过来）。 */
    private String currentWeather() {
        if (minecraft == null || minecraft.level == null) return WeatherControlPacket.CLEAR;
        if (minecraft.level.isThundering()) return WeatherControlPacket.THUNDER;
        if (minecraft.level.isRaining()) return WeatherControlPacket.RAIN;
        return WeatherControlPacket.CLEAR;
    }

    private void request(String id) {
        playClickSound();
        NetworkHandler.CHANNEL.sendToServer(new WeatherControlPacket(id));
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
    protected void renderPanel(GuiGraphics g, int mx, int my, float pt) {
        EditorUi.panel(g, leftPos, topPos, GUI_W, GUI_H);
        EditorUi.title(g, font, leftPos, topPos, GUI_W, title.getString());

        // 状态：是否已解锁 + 当前天气
        Component status = toggleActive ? text("on") : text("off");
        g.drawCenteredString(font, status.getString(), leftPos + GUI_W / 2, topPos + 30,
                toggleActive ? EditorUi.ACCENT : EditorUi.DANGER);

        String current = currentWeather();
        g.drawCenteredString(font, text("current", text("entry." + current)).getString(),
                leftPos + GUI_W / 2, topPos + 44, EditorUi.INFO);

        // 档位图标 + 当前档位高亮（按钮由下面统一遍历绘制，图标画在按钮之上）
        for (int i = 0; i < entryButtons.size(); i++) {
            Button btn = entryButtons.get(i);
            g.renderItem(icon(ENTRY_IDS[i]), btn.getX() + 6, btn.getY() + (btn.getHeight() - 16) / 2);
            if (ENTRY_IDS[i].equals(current)) {
                g.renderOutline(btn.getX(), btn.getY(), btn.getWidth(), btn.getHeight(), EditorUi.ACCENT);
            }
        }

        // 底栏说明：作用范围 + 持续时长
        int seconds = Math.max(1, Config.WEATHER_CYCLE_DURATION.get() / 20);
        g.drawCenteredString(font, text("scope").getString(),
                leftPos + GUI_W / 2, topPos + GUI_H - 58, EditorUi.SECONDARY);
        g.drawCenteredString(font, text("hint", seconds).getString(),
                leftPos + GUI_W / 2, topPos + GUI_H - 46, EditorUi.SECONDARY);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
