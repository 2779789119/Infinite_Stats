package com.infinitestats.client;

import com.infinitestats.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 「附近生物列表」—— 手持生物观察杖<b>右键空气</b>时打开。
 *
 * <h2>为什么需要它</h2>
 * 观察杖原本只能对着生物右键：小体型生物（蝙蝠、鱼、被草挡住的家畜）在密集场景里很难点中，
 * 站远了也点不到。这里把 32 格内的生物按距离列出来，点哪只就看哪只。
 *
 * <h2>为什么点选之后还要跑一趟服务端</h2>
 * 实体追踪数据在客户端就有，所以列表能本地生成；但开关状态（NoAI / 静音 / 无敌……）
 * 与「能不能改」只有服务端知道，所以点选只发一个 {@code EntityPanelRequestPacket}，
 * 由服务端校验后回 {@code OpenEntityPanelPacket} 打开真正的 {@link EntityPanelScreen}。
 */
public class EntityPickerScreen extends PanelScreen {

    private static final int GUI_W = 300;
    private static final int GUI_H = 258;

    private static final int LIST_TOP = 44;
    private static final int LIST_H = 190;
    private static final int ROW_H = 19;
    private static final int VISIBLE_ROWS = LIST_H / ROW_H;

    private static final int LIST_X = 4;
    private static final int SCROLL_X = GUI_W - EditorUi.GAP - EditorUi.SCROLL_W;
    private static final int RIGHT = SCROLL_X - 4;

    private static final int CLOSE_BTN_Y = 232;

    /** 扫描半径（格）：比右键点选远得多，但仍要在实体追踪范围内。 */
    private static final int RADIUS = 32;

    private static final ItemStack HEADER_ICON = new ItemStack(Items.SPYGLASS);

    private record Entry(LivingEntity entity, double distance, String name, float health, float maxHealth) {}

    private final List<Entry> entries = new ArrayList<>();
    private int scroll;
    private int maxScroll;
    private int hovered = -1;
    private boolean scrollbarDragging;
    private int refreshCountdown;

    public EntityPickerScreen() {
        super(Component.translatable("gui.infinitestats.entity_panel.picker.title"));
        rebuild();
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
        rebuild();
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.infinitestats.entity_panel.close"),
                        b -> onClose())
                .bounds(GUI_W / 2 - 50, CLOSE_BTN_Y, 100, 20)
                .build());
    }

    @Override
    public void tick() {
        super.tick();
        // 生物会走动 / 死掉，一秒重扫一次列表
        if (--refreshCountdown <= 0) {
            refreshCountdown = 20;
            rebuild();
        }
    }

    /** 扫一遍附近的生物，按距离从近到远排。 */
    private void rebuild() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc == null ? null : mc.player;
        float keepRatio = maxScroll <= 0 ? 0f : scroll / (float) maxScroll;

        entries.clear();
        if (player != null && mc.level != null) {
            List<Entity> nearby = mc.level.getEntities(player, player.getBoundingBox().inflate(RADIUS));
            List<Entry> found = new ArrayList<>();
            for (Entity entity : nearby) {
                if (!(entity instanceof LivingEntity living)) continue;
                if (living instanceof Player || living.isRemoved() || !living.isAlive()) continue;

                String name = living.getDisplayName().getString();
                found.add(new Entry(living,
                        Math.sqrt(living.distanceToSqr(player)),
                        name,
                        living.getHealth(),
                        living.getMaxHealth()));
            }
            found.sort(Comparator.comparingDouble(Entry::distance).thenComparing(Entry::name));
            entries.addAll(found);
        }

        maxScroll = Math.max(0, entries.size() - VISIBLE_ROWS);
        scroll = Mth.clamp(Math.round(keepRatio * maxScroll), 0, maxScroll);
    }

    @Override
    protected void renderPanel(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, GUI_W, GUI_H, EditorUi.BG_PANEL);
        g.fill(0, 0, GUI_W, EditorUi.HEADER_H, EditorUi.BG_HEADER);

        g.renderItem(HEADER_ICON, 6, 4);
        EditorUi.title(g, font, 10, 0, GUI_W - 20, title.getString());

        g.drawString(font, Component.translatable("gui.infinitestats.entity_panel.picker.hint", RADIUS).getString(),
                6, 30, EditorUi.SECONDARY);

        beginPanelScissor(g, LIST_X, LIST_TOP, RIGHT + 2, LIST_TOP + LIST_H);
        hovered = -1;
        if (entries.isEmpty()) {
            g.drawString(font, Component.translatable("gui.infinitestats.entity_panel.picker.empty", RADIUS).getString(),
                    LIST_X + 6, LIST_TOP + 4, EditorUi.SECONDARY);
        }
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int index = scroll + i;
            if (index >= entries.size()) break;

            Entry entry = entries.get(index);
            int y = LIST_TOP + i * ROW_H;
            boolean hover = mouseY >= y && mouseY < y + ROW_H
                    && mouseX >= LIST_X && mouseX < RIGHT + 2;
            if (hover) hovered = index;

            if (hover) {
                g.fill(LIST_X, y, RIGHT + 2, y + ROW_H - 1, EditorUi.BG_ROW_HOVER);
            } else if (index % 2 == 1) {
                g.fill(LIST_X, y, RIGHT + 2, y + ROW_H - 1, EditorUi.BG_ROW);
            }

            int textY = y + (ROW_H - font.lineHeight) / 2;
            if (hover) g.fill(LIST_X + 1, y + 3, LIST_X + 3, y + ROW_H - 4, EditorUi.ACCENT);

            // 右对齐：距离 + 血量
            String distance = Component.translatable("gui.infinitestats.entity_panel.picker.distance",
                    String.format(Locale.ROOT, "%.1f", entry.distance())).getString();
            String health = EditorUi.amount(entry.health()) + " / " + EditorUi.amount(entry.maxHealth());
            g.drawString(font, health, RIGHT - font.width(health), textY, EditorUi.DANGER);
            g.drawString(font, distance, RIGHT - font.width(health) - 8 - font.width(distance), textY,
                    EditorUi.SECONDARY);

            int nameWidth = RIGHT - font.width(health) - 8 - font.width(distance) - 12 - LIST_X;
            g.drawString(font, EditorUi.ellipsize(font, entry.name(), Math.max(20, nameWidth)),
                    LIST_X + 6, textY, EditorUi.PRIMARY);
        }
        endPanelScissor(g);

        if (maxScroll > 0) {
            EditorUi.scrollbar(g, mouseX, mouseY, SCROLL_X, LIST_TOP, LIST_H, scroll, maxScroll, VISIBLE_ROWS);
        }
    }

    @Override
    protected void renderPanelOverlay(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (hovered < 0 || hovered >= entries.size()) return;
        Entry entry = entries.get(hovered);
        List<String> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.infinitestats.entity_panel.picker.click").getString());
        lines.add("§7" + Component.translatable("gui.infinitestats.entity_panel.type",
                entry.entity().getType().getDescription().getString()).getString());
        queueTooltip(g, mouseX, mouseY, lines, EditorUi.ACCENT);
    }

    @Override
    protected boolean onPanelClick(int mouseX, int mouseY, int button) {
        if (button != 0) return false;

        if (maxScroll > 0 && EditorUi.onScrollbar(mouseX, mouseY, SCROLL_X, LIST_TOP, LIST_H)) {
            scrollbarDragging = true;
            scroll = EditorUi.scrollFromMouseY(mouseY, LIST_TOP, LIST_H, maxScroll, VISIBLE_ROWS);
            return true;
        }

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int index = scroll + i;
            if (index >= entries.size()) break;
            int y = LIST_TOP + i * ROW_H;
            if (mouseY >= y && mouseY < y + ROW_H && mouseX >= LIST_X && mouseX < RIGHT + 2) {
                NetworkHandler.CHANNEL.sendToServer(
                        new NetworkHandler.EntityPanelRequestPacket(entries.get(index).entity().getId()));
                onClose();
                return true;
            }
        }
        return false;
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
