package com.infinitestats.stats;

import com.infinitestats.Config;

/**
 * 「功能开关」把某项关掉时的执行模式。
 * <p>
 * 这是**整合包作者**的全局设置：改配置文件 {@code config/infinitestats-common.toml} 里的
 * {@code GUI.featureDisableMode}，需要时也可由 OP 用 {@code /infstats feature mode} 切换。
 * 不按玩家存 —— 所有玩家共用同一套规则。
 */
public enum FeatureDisableMode {

    /** 整条失效，保留已投入的点数（默认） */
    INACTIVE_KEEP_POINTS("keep"),

    /** 整条失效，并返还该属性已投入的点数 */
    INACTIVE_REFUND_POINTS("refund"),

    /** 只从属性面板隐藏：效果与指令照常，点数不返还 */
    HIDDEN_ONLY("hide");

    private final String id;

    FeatureDisableMode(String id) {
        this.id = id;
    }

    /** 指令与配置里使用的短 id。 */
    public String getId() {
        return id;
    }

    /** 界面显示名翻译键。 */
    public String getTranslationKey() {
        return "screen.infinitestats.feature_toggle.mode_" + id;
    }

    /** 浮窗说明翻译键。 */
    public String getDescriptionKey() {
        return getTranslationKey() + ".desc";
    }

    /** 关闭该项时是否需要整条失效（效果归零 / 指令停用）。 */
    public boolean isInactive() {
        return this != HIDDEN_ONLY;
    }

    /** 关闭该项时是否返还已投入的点数。 */
    public boolean isRefund() {
        return this == INACTIVE_REFUND_POINTS;
    }

    /** 当前生效的模式（来自整合包配置，未加载时回退到默认值）。 */
    public static FeatureDisableMode current() {
        try {
            FeatureDisableMode configured = Config.FEATURE_DISABLE_MODE.get();
            if (configured != null) return configured;
        } catch (Throwable ignored) {
            // 配置尚未加载：使用默认值
        }
        return INACTIVE_KEEP_POINTS;
    }

    /** 按短 id 查找，未知返回 null。 */
    public static FeatureDisableMode byId(String id) {
        if (id == null) return null;
        for (FeatureDisableMode mode : values()) {
            if (mode.id.equalsIgnoreCase(id)) return mode;
        }
        return null;
    }
}
