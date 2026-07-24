package com.infinitestats.client;

/**
 * EMC 转化桌 GUI 布局常量
 * 集中管理所有位置、尺寸、间距，消除魔法数字
 */
public final class EmcLayout {

    // ========== GUI 整体尺寸 ==========

    public static final int GUI_WIDTH = 300;
    public static final int GUI_HEIGHT = 216;

    // ========== 顶部栏 ==========

    public static final int HEADER_H = 24;
    public static final int HEADER_EMC_LABEL_X = 126;
    public static final int HEADER_EMC_LABEL_Y = 7;

    // ========== 搜索框 ==========

    public static final int SEARCH_X = 6;
    public static final int SEARCH_Y = 6;
    public static final int SEARCH_WIDTH = 108;
    public static final int SEARCH_HEIGHT = 14;

    // ========== 左侧物品列表 ==========

    public static final int LIST_X = 0;
    public static final int LIST_Y = HEADER_H;
    public static final int LIST_WIDTH = 120;
    public static final int LIST_PADDING_H = 2;

    /** 列表条目高度 */
    public static final int ENTRY_H = 22;
    /** 图标在条目内的 X 偏移 */
    public static final int ENTRY_ICON_X = 5;
    /** 图标在条目内的 Y 偏移 */
    public static final int ENTRY_ICON_Y = 2;
    /** 文字在条目内的 X 偏移 */
    public static final int ENTRY_TEXT_X = 23;
    /** 文字在条目内的 Y 偏移 */
    public static final int ENTRY_TEXT_Y = 5;

    // ========== 滚动条 ==========

    public static final int SCROLLBAR_W = 4;
    public static final int SCROLLBAR_MIN_H = 20;
    public static final int SCROLLBAR_PADDING = 2;

    // ========== 右侧详情面板 ==========

    public static final int DETAIL_X = 135;
    public static final int DETAIL_WIDTH = 85;
    public static final int DETAIL_TOP_OFFSET = 10;

    /** 大图标 X 偏移（相对 detailLeft） */
    public static final int DETAIL_ICON_X = 28;
    /** 大图标 Y 偏移 */
    public static final int DETAIL_ICON_Y = 0;
    /** 物品名称 Y 偏移 */
    public static final int DETAIL_NAME_Y = 20;
    /** EMC 值 Y 偏移 */
    public static final int DETAIL_EMC_Y = 34;

    // ========== 提取按钮 ==========

    public static final int EXTRACT_BTN_Y = 52;
    public static final int EXTRACT_BTN_W = 24;
    public static final int EXTRACT_BTN_H = 14;
    public static final int EXTRACT_BTN_GAP = 3;
    public static final int EXTRACT_BTN_TEXT_Y = 3;

    // ========== 提示文字 ==========

    public static final int HINT_Y = 0;
    public static final int HINT2_Y = 16;
    public static final int HINT3_Y = 30;

    // ========== 学习槽 ==========

    public static final int LEARN_X = 230;
    public static final int LEARN_Y = 4;
    public static final int LEARN_SIZE = 18;
    public static final int LEARN_ICON_X = 4;
    public static final int LEARN_ICON_Y = 3;

    // ========== 玩家物品栏 ==========

    public static final int PLAYER_INV_X = 8;
    public static final int PLAYER_INV_Y = 132;
    public static final int PLAYER_HOTBAR_Y = 190;
    public static final int PLAYER_INV_LABEL_X = 8;
    public static final int PLAYER_INV_LABEL_Y = 122;

    // ========== 计算辅助方法 ==========

    public static int listTop(int topPos) {
        return topPos + LIST_Y;
    }

    public static int listBottom(int topPos) {
        return topPos + PLAYER_INV_Y - 4;
    }

    public static int listVisibleCount(int topPos) {
        return (listBottom(topPos) - listTop(topPos)) / ENTRY_H;
    }

    public static int listLeft(int leftPos) {
        return leftPos + LIST_X;
    }

    public static int detailLeft(int leftPos) {
        return leftPos + DETAIL_X;
    }

    public static int detailTop(int topPos) {
        return listTop(topPos) + DETAIL_TOP_OFFSET;
    }

    public static int learnX(int leftPos) {
        return leftPos + LEARN_X;
    }

    public static int learnY(int topPos) {
        return topPos + LEARN_Y;
    }

    private EmcLayout() {}
}