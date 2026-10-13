package com.infinitestats.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.io.*;
import java.nio.file.Path;

/**
 * 客户端设置 — 持久化保存面板/HUD 的位置和缩放
 * 数据存储为 config/infinite_stats-client.json
 */
@OnlyIn(Dist.CLIENT)
public final class ClientSettings {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ========== GUI 面板 ==========

    /** 缩放下限（再小字就看不清了）。 */
    public static final float MIN_UI_SCALE = 0.5f;
    /** 缩放上限（放太大一样会超出屏幕，留给大屏玩家）。 */
    public static final float MAX_UI_SCALE = 2.0f;
    /** 手动缩放的默认值。 */
    public static final float DEFAULT_UI_SCALE = 1.0f;

    /**
     * 手动 UI 缩放（{@link #MIN_UI_SCALE}~{@link #MAX_UI_SCALE}）。
     * <p>
     * {@link #autoFit} 打开时这个值不生效，只是「下次切回手动」的起点。
     * 面板里 Ctrl+滚轮 会以当前实际缩放为起点改这个值并自动切到手动。
     */
    public static float guiScale = 0.5f;

    /**
     * 自动适配（默认开）：每个面板按自己的尺寸和当前窗口算出「刚好塞得下」的缩放（≤1）。
     * <p>
     * 关掉后用 {@link #guiScale} 手动控制。面板角落的百分比角标可点击切换，
     * Ctrl+滚轮 调手动、Ctrl+0 回到自动。
     */
    public static boolean autoFit = true;

    public static int guiOffsetX = 0;
    public static int guiOffsetY = 0;

    // ========== HUD 叠加层 ==========

    public static float hudScale = 1.0f;
    public static int hudX = 4;
    public static int hudY = 4;
    public static boolean hudVisible = true;

    // ========== 文件路径 ==========

    private static Path getConfigPath() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("infinite_stats-client.json");
    }

    // ========== 加载/保存 ==========

    public static void load() {
        Path path = getConfigPath();
        if (!path.toFile().exists()) return;

        try (Reader reader = new FileReader(path.toFile())) {
            Data data = GSON.fromJson(reader, Data.class);
            if (data == null) return;

            if (data.guiScale > 0) guiScale = data.guiScale;
            autoFit = data.autoFit;
            guiOffsetX = data.guiOffsetX;
            guiOffsetY = data.guiOffsetY;
            if (data.hudScale > 0) hudScale = data.hudScale;
            hudX = data.hudX;
            hudY = data.hudY;
            hudVisible = data.hudVisible;
        } catch (Exception e) {
            // 忽略读取错误，使用默认值
        }
    }

    public static void save() {
        Path path = getConfigPath();
        Data data = new Data();
        data.guiScale = guiScale;
        data.autoFit = autoFit;
        data.guiOffsetX = guiOffsetX;
        data.guiOffsetY = guiOffsetY;
        data.hudScale = hudScale;
        data.hudX = hudX;
        data.hudY = hudY;
        data.hudVisible = hudVisible;

        try {
            path.getParent().toFile().mkdirs();
            try (Writer writer = new FileWriter(path.toFile())) {
                GSON.toJson(data, writer);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * JSON 数据结构
     */
    private static class Data {
        float guiScale = 0.5f;
        boolean autoFit = true;
        int guiOffsetX = 0;
        int guiOffsetY = 0;
        float hudScale = 1.0f;
        int hudX = 4;
        int hudY = 4;
        boolean hudVisible = true;
    }
}
