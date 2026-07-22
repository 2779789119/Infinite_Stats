package com.infinitestats.client;

import com.infinitestats.network.NetworkHandler;
import com.infinitestats.stats.PlayerStatsProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * 跨维度传送面板
 * 以可滚动列表展示服务器下发的全部维度，点击目标维度按钮即可一键传送。
 * 列表由服务器动态下发，因此兼容暮色森林 / Alex's Caves / 以太 等模组维度。
 * 需要『跨维度传送』属性已激活（与普通命令一致）。
 */
public class CrossDimScreen extends Screen {

    private static final int GUI_W = 300;
    private static final int GUI_H = 240;

    private static final int BG_PANEL = 0xE81A1A2E;
    private static final int TEXT_GOLD = 0xFFFFD166;
    private static final int TEXT_NEGATIVE = 0xFFF87171;
    private static final int TEXT_ACTIVE = 0xFF4ADE80;

    // 列表区域（标题/状态栏下方到面板底部之间）
    private static final int LIST_TOP_OFFSET = 66;
    private static final int LIST_BOTTOM_OFFSET = 10;

    // 服务器下发的维度列表（完整注册名，如 minecraft:overworld / twilightforest:twilight_forest）
    private static final List<String> SERVER_DIMENSIONS = new ArrayList<>(List.of(
            "minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"));

    private int leftPos, topPos;
    private boolean toggleActive;
    private String currentDim = "";
    private long lastSoundTick;

    // 滚动状态
    private final List<Button> dimButtons = new ArrayList<>();
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private int contentHeight = 0;
    private static final int BTN_H = 26;
    private static final int GAP = 6;

    public CrossDimScreen() {
        super(Component.translatable("screen.infinitestats.crossdim"));
    }

    /** 由服务器列表数据包回填，并刷新当前打开的面板。 */
    public static void setServerDimensions(List<String> dims) {
        SERVER_DIMENSIONS.clear();
        SERVER_DIMENSIONS.addAll(dims);
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof CrossDimScreen cs) {
            cs.rebuildList();
        }
    }

    private static String displayName(String rl) {
        return switch (rl) {
            case "minecraft:overworld" -> "主世界";
            case "minecraft:the_nether" -> "下界";
            case "minecraft:the_end" -> "末地";
            default -> rl;
        };
    }

    @Override
    protected void init() {
        super.init();
        leftPos = (width - GUI_W) / 2;
        topPos = (height - GUI_H) / 2;

        Player player = Minecraft.getInstance().player;
        toggleActive = false;
        currentDim = "";
        if (player != null) {
            currentDim = player.level().dimension().location().toString();
            player.getCapability(PlayerStatsProvider.PLAYER_STATS).ifPresent(stats -> {
                toggleActive = stats.isToggleActive("cross_dimension_teleport");
            });
        }

        // 向服务器请求完整的维度列表（含模组维度）
        NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.CrossDimListRequestPacket());

        rebuildList();
    }

    /** 重建维度按钮列表，并重新计算滚动范围。 */
    private void rebuildList() {
        this.clearWidgets();
        dimButtons.clear();

        int btnW = GUI_W - 40;
        int listHeight = GUI_H - LIST_TOP_OFFSET - LIST_BOTTOM_OFFSET;
        int startY = topPos + LIST_TOP_OFFSET;

        int i = 0;
        for (String dimKey : SERVER_DIMENSIONS) {
            final String key = dimKey;
            boolean isCurrent = currentDim.equals(key);
            int by = startY + i * (BTN_H + GAP);
            Button btn = Button.builder(
                            Component.literal(displayName(key) + (isCurrent ? "（当前）" : "")),
                            b -> requestTeleport(key))
                    .bounds(leftPos + 20, by, btnW, BTN_H)
                    .build();
            btn.active = toggleActive && !isCurrent;
            addRenderableWidget(btn);
            dimButtons.add(btn);
            i++;
        }

        contentHeight = SERVER_DIMENSIONS.size() * (BTN_H + GAP);
        maxScroll = Math.max(0, contentHeight - listHeight);
        clampScroll();
    }

    private void clampScroll() {
        if (scrollOffset < 0) scrollOffset = 0;
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
    }

    private void requestTeleport(String dimKey) {
        playClickSound();
        NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.CrossDimRequestPacket(dimKey));
        onClose();
    }

    private void playClickSound() {
        if (minecraft == null) return;
        long now = System.currentTimeMillis();
        if (now - lastSoundTick < 50) return;
        lastSoundTick = now;
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (maxScroll > 0) {
            scrollOffset += (int) (-delta * (BTN_H + GAP));
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        // 面板
        graphics.fill(leftPos - 2, topPos - 2, leftPos + GUI_W + 2, topPos + GUI_H + 2, 0x403B82F6);
        graphics.fill(leftPos - 1, topPos - 1, leftPos + GUI_W + 1, topPos + GUI_H + 1, 0xFF1E293B);
        graphics.fill(leftPos, topPos, leftPos + GUI_W, topPos + GUI_H, BG_PANEL);

        // 标题
        graphics.drawCenteredString(font, Component.translatable("screen.infinitestats.crossdim").getString(),
                leftPos + GUI_W / 2, topPos + 12, TEXT_GOLD);

        // 状态提示
        int toggleColor = toggleActive ? TEXT_ACTIVE : TEXT_NEGATIVE;
        Component toggleText = toggleActive
                ? Component.translatable("gui.infinitestats.crossdim_on")
                : Component.translatable("gui.infinitestats.crossdim_off");
        graphics.drawCenteredString(font, toggleText.getString(),
                leftPos + GUI_W / 2, topPos + 40, toggleColor);

        // 裁剪列表区域后绘制按钮
        int listTop = topPos + LIST_TOP_OFFSET;
        int listBottom = topPos + GUI_H - LIST_BOTTOM_OFFSET;
        graphics.enableScissor(leftPos, listTop, leftPos + GUI_W, listBottom);
        for (int i = 0; i < dimButtons.size(); i++) {
            Button btn = dimButtons.get(i);
            int by = listTop + i * (BTN_H + GAP) - scrollOffset;
            btn.setY(by);
            btn.visible = by + BTN_H > listTop && by < listBottom;
        }
        for (var renderable : this.renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
        graphics.disableScissor();

        // 底部提示 / 滚动条
        if (!toggleActive) {
            graphics.drawCenteredString(font,
                    Component.translatable("gui.infinitestats.crossdim_hint").getString(),
                    leftPos + GUI_W / 2, topPos + GUI_H - 14, TEXT_NEGATIVE);
        } else if (maxScroll > 0) {
            drawScrollbar(graphics, listTop, listBottom);
        }
    }

    private void drawScrollbar(GuiGraphics graphics, int listTop, int listBottom) {
        int barX = leftPos + GUI_W - 6;
        int trackH = listBottom - listTop;
        int thumbH = Math.max(16, (int) ((double) trackH * (trackH) / contentHeight));
        int thumbY = listTop + (int) ((double) scrollOffset / maxScroll * (trackH - thumbH));
        graphics.fill(barX, listTop, barX + 3, listBottom, 0x803B5E8A);
        graphics.fill(barX, thumbY, barX + 3, thumbY + thumbH, 0xFF8080B0);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
