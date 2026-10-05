package com.infinitestats.client;

import com.infinitestats.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;

/**
 * 区块强加载面板。
 * <p>
 * 输入方块坐标（或点「填入当前位置」）后点「加载」，服务端把对应区块设为强加载；
 * 下方列出**当前维度**已强加载的区块，每行可单独卸载。列表由服务端在每次操作后回推，
 * 因此界面不会与真实状态脱节（换维度后重新打开会重新拉取）。
 * <p>
 * 每维度数量上限由配置 {@code ChunkLoader.maxForcedChunks} 决定，超限时服务端会拒绝并提示。
 */
public final class ChunkLoaderScreen extends Screen {

    private static final int GUI_WIDTH = 320;
    private static final int GUI_HEIGHT = 252;
    private static final int BG_PANEL = 0xE81A1A2E;
    private static final int BORDER_COLOR = 0x403B82F6;
    private static final int TEXT_TITLE = 0xFFFFD166;
    private static final int TEXT_LABEL = 0xFF94A3B8;
    private static final int TEXT_HINT = 0xFF64748B;
    private static final int TEXT_ROW = 0xFFE2E8F0;

    private static final int ROWS_PER_PAGE = 5;
    private static final int ROW_H = 16;

    private int leftPos;
    private int topPos;
    private EditBox xField;
    private EditBox zField;
    private Button prevPageBtn;
    private Button nextPageBtn;

    private int listTop;
    private int page;
    private long lastVersion = -1;
    private final List<Button> rowButtons = new ArrayList<>();

    public ChunkLoaderScreen() {
        super(Component.translatable("gui.infinitestats.chunk.title"));
    }

    @Override
    protected void init() {
        super.init();
        leftPos = (width - GUI_WIDTH) / 2;
        topPos = (height - GUI_HEIGHT) / 2;

        // 坐标输入（方块坐标，允许负号）
        xField = new EditBox(font, leftPos + 66, topPos + 34, 74, 16,
                Component.translatable("gui.infinitestats.chunk.x"));
        xField.setMaxLength(9);
        xField.setFilter(s -> s.matches("-?\\d{0,9}"));
        addRenderableWidget(xField);

        zField = new EditBox(font, leftPos + 206, topPos + 34, 74, 16,
                Component.translatable("gui.infinitestats.chunk.z"));
        zField.setMaxLength(9);
        zField.setFilter(s -> s.matches("-?\\d{0,9}"));
        addRenderableWidget(zField);

        // 默认填入玩家当前位置，省得每次都手输
        fillCurrentPosition();

        // 操作按钮
        int actionY = topPos + 58;
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.infinitestats.chunk.here"),
                        b -> fillCurrentPosition())
                .bounds(leftPos + 16, actionY, 96, 16).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.infinitestats.chunk.load"),
                        b -> submit(true))
                .bounds(leftPos + 118, actionY, 52, 16).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.infinitestats.chunk.unload"),
                        b -> submit(false))
                .bounds(leftPos + 176, actionY, 52, 16).build());

        // 列表分页
        listTop = topPos + 118;
        int pageY = listTop + ROWS_PER_PAGE * ROW_H + 6;
        prevPageBtn = addRenderableWidget(Button.builder(
                        Component.literal("<"),
                        b -> changePage(-1))
                .bounds(leftPos + 16, pageY, 20, 14).build());
        nextPageBtn = addRenderableWidget(Button.builder(
                        Component.literal(">"),
                        b -> changePage(1))
                .bounds(leftPos + 40, pageY, 20, 14).build());

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.infinitestats.chunk.back"),
                        b -> backToStats())
                .bounds(leftPos + GUI_WIDTH / 2 - 60, topPos + GUI_HEIGHT - 26, 120, 20).build());

        // 首次打开：向服务端要一份当前维度的强加载列表
        lastVersion = -1;
        page = 0;
        NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.ChunkLoaderActionPacket(
                NetworkHandler.ChunkLoaderActionPacket.ACTION_REQUEST, 0, 0));
        rebuildList();
    }

    @Override
    public void tick() {
        super.tick();
        // 服务端每次操作后都会回推列表；版本号变化即重建行按钮
        if (lastVersion != NetworkHandler.forcedChunksVersion) {
            lastVersion = NetworkHandler.forcedChunksVersion;
            rebuildList();
        }
    }

    private void fillCurrentPosition() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (xField != null) xField.setValue(String.valueOf(mc.player.blockPosition().getX()));
        if (zField != null) zField.setValue(String.valueOf(mc.player.blockPosition().getZ()));
    }

    /** 提交加载 / 卸载请求：方块坐标 -> 区块坐标（算术右移 4 位等于向下取整 ÷16）。 */
    private void submit(boolean add) {
        int blockX = parseCoord(xField);
        int blockZ = parseCoord(zField);
        if (blockX == Integer.MIN_VALUE || blockZ == Integer.MIN_VALUE) {
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        Component.translatable("gui.infinitestats.chunk.invalid"), true);
            }
            return;
        }
        NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.ChunkLoaderActionPacket(
                add ? NetworkHandler.ChunkLoaderActionPacket.ACTION_ADD
                    : NetworkHandler.ChunkLoaderActionPacket.ACTION_REMOVE,
                blockX >> 4, blockZ >> 4));
    }

    private static int parseCoord(EditBox field) {
        if (field == null) return Integer.MIN_VALUE;
        String raw = field.getValue().trim();
        if (raw.isEmpty() || raw.equals("-")) return Integer.MIN_VALUE;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return Integer.MIN_VALUE;
        }
    }

    private List<Long> chunks() {
        List<Long> list = NetworkHandler.pendingForcedChunks;
        return list == null ? List.of() : list;
    }

    private int pageCount() {
        int total = chunks().size();
        return Math.max(1, (total + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
    }

    private void changePage(int delta) {
        page = Math.max(0, Math.min(page + delta, pageCount() - 1));
        rebuildList();
    }

    /** 重建列表行按钮（每行一个「卸载」）。 */
    private void rebuildList() {
        for (Button b : rowButtons) {
            removeWidget(b);
        }
        rowButtons.clear();

        List<Long> list = chunks();
        int pages = pageCount();
        if (page >= pages) page = pages - 1;
        if (page < 0) page = 0;

        if (prevPageBtn != null) prevPageBtn.active = page > 0;
        if (nextPageBtn != null) nextPageBtn.active = page < pages - 1;

        for (int i = 0; i < ROWS_PER_PAGE; i++) {
            int idx = page * ROWS_PER_PAGE + i;
            if (idx >= list.size()) break;
            long packed = list.get(idx);
            final int cx = ChunkPos.getX(packed);
            final int cz = ChunkPos.getZ(packed);
            Button unload = Button.builder(
                            Component.translatable("gui.infinitestats.chunk.unload"),
                            b -> NetworkHandler.CHANNEL.sendToServer(
                                    new NetworkHandler.ChunkLoaderActionPacket(
                                            NetworkHandler.ChunkLoaderActionPacket.ACTION_REMOVE, cx, cz)))
                    .bounds(leftPos + GUI_WIDTH - 68, listTop + i * ROW_H, 52, 14)
                    .build();
            addRenderableWidget(unload);
            rowButtons.add(unload);
        }
    }

    private void backToStats() {
        if (minecraft != null) {
            minecraft.setScreen(new StatsScreen());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        graphics.fill(leftPos - 2, topPos - 2, leftPos + GUI_WIDTH + 2, topPos + GUI_HEIGHT + 2, BORDER_COLOR);
        graphics.fill(leftPos, topPos, leftPos + GUI_WIDTH, topPos + GUI_HEIGHT, BG_PANEL);

        graphics.drawCenteredString(font, title, leftPos + GUI_WIDTH / 2, topPos + 12, TEXT_TITLE);

        graphics.drawString(font, Component.translatable("gui.infinitestats.chunk.x"), leftPos + 16, topPos + 38, TEXT_LABEL);
        graphics.drawString(font, Component.translatable("gui.infinitestats.chunk.z"), leftPos + 156, topPos + 38, TEXT_LABEL);

        // 换算提示：方块坐标 -> 区块坐标
        int blockX = parseCoord(xField);
        int blockZ = parseCoord(zField);
        if (blockX != Integer.MIN_VALUE && blockZ != Integer.MIN_VALUE) {
            graphics.drawString(font,
                    Component.translatable("gui.infinitestats.chunk.mapped", blockX >> 4, blockZ >> 4),
                    leftPos + 16, topPos + 80, TEXT_HINT);
        }

        List<Long> list = chunks();
        String dim = NetworkHandler.pendingForcedChunkDimension;
        graphics.drawString(font,
                Component.translatable("gui.infinitestats.chunk.list_title",
                        list.size(), dim == null ? "-" : dim),
                leftPos + 16, topPos + 102, TEXT_LABEL);

        if (list.isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.infinitestats.chunk.empty"),
                    leftPos + 16, listTop + 2, TEXT_HINT);
        } else {
            for (int i = 0; i < ROWS_PER_PAGE; i++) {
                int idx = page * ROWS_PER_PAGE + i;
                if (idx >= list.size()) break;
                long packed = list.get(idx);
                int cx = ChunkPos.getX(packed);
                int cz = ChunkPos.getZ(packed);
                int y = listTop + i * ROW_H + 3;
                graphics.drawString(font,
                        Component.translatable("gui.infinitestats.chunk.row",
                                cx, cz, cx << 4, cz << 4),
                        leftPos + 16, y, TEXT_ROW);
            }
            Component pageText = Component.translatable("gui.infinitestats.chunk.page", page + 1, pageCount());
            graphics.drawString(font, pageText, leftPos + 68, listTop + ROWS_PER_PAGE * ROW_H + 9, TEXT_HINT);
        }

        graphics.drawString(font, Component.translatable("gui.infinitestats.chunk.hint"),
                leftPos + 16, topPos + GUI_HEIGHT - 40, TEXT_HINT);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
