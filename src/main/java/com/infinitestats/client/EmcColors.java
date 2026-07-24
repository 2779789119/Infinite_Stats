package com.infinitestats.client;

/**
 * EMC 转化桌 GUI 颜色常量
 * 集中管理所有颜色值，便于统一调整主题
 */
public final class EmcColors {

    // ========== 背景 ==========

    /** 主面板背景 */
    public static final int BG_PANEL = 0xC01A1A2E;
    /** 顶部标题栏背景 */
    public static final int BG_HEADER = 0xC0151536;
    /** 左侧列表区域背景 */
    public static final int BG_LIST = 0x80101020;

    // ========== 列表条目 ==========

    /** 普通条目 */
    public static final int BG_ENTRY = 0x50252535;
    /** 悬停条目 */
    public static final int BG_ENTRY_HOVER = 0x80353550;
    /** 选中条目 */
    public static final int BG_ENTRY_SELECTED = 0x80404080;

    // ========== 按钮 ==========

    /** 提取按钮 */
    public static final int BG_BTN = 0xFF1A5334;
    /** 提取按钮（悬停/选中） */
    public static final int BG_BTN_HOVER = 0xFF2D8A4E;

    // ========== 学习槽 ==========

    /** 学习槽背景 */
    public static final int BG_LEARN_SLOT = 0x80303050;
    /** 学习槽悬停 */
    public static final int BG_LEARN_SLOT_HOVER = 0x80404070;
    /** 学习槽边框（金色） */
    public static final int BORDER_LEARN_SLOT = 0xFFB8860B;

    // ========== 文字 ==========

    /** 主要文字 */
    public static final int TEXT_PRIMARY = 0xFFE2E8F0;
    /** 次要文字 */
    public static final int TEXT_SECONDARY = 0xFF94A3B8;
    /** 金色文字（EMC / 稀有） */
    public static final int TEXT_GOLD = 0xFFFFD166;
    /** EMC 余额专用 */
    public static final int TEXT_EMC = 0xFF60A5FA;
    /** 提示文字 */
    public static final int TEXT_HINT = 0xFF64748B;

    // ========== 滚动条 ==========

    /** 滚动条轨道 */
    public static final int SCROLLBAR_TRACK = 0x30151520;
    /** 滚动条滑块 */
    public static final int SCROLLBAR_BAR = 0x80555570;
    /** 滚动条滑块（悬停） */
    public static final int SCROLLBAR_BAR_HOVER = 0xB08888A0;

    private EmcColors() {}
}