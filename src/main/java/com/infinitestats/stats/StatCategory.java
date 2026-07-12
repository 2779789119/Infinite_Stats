package com.infinitestats.stats;

/**
 * 属性类别 - 分类系统
 * 每个类别有唯一名称、翻译键和主题颜色
 */
public enum StatCategory {

    ATTACK("attack", "category.infinitestats.attack", 0xFF5555),
    DEFENSE("defense", "category.infinitestats.defense", 0x5555FF),
    MOBILITY("mobility", "category.infinitestats.mobility", 0x55FF55),
    UTILITY("utility", "category.infinitestats.utility", 0xFFAA00),
    MAGIC("magic", "category.infinitestats.magic", 0xAA55FF),
    /** 其他模组注册的属性 - 自动从 ForgeRegistries.ATTRIBUTES 发现 */
    EXTERNAL("external", "category.infinitestats.external", 0xFF66B2);

    private final String name;
    private final String translationKey;
    private final int color;

    StatCategory(String name, String translationKey, int color) {
        this.name = name;
        this.translationKey = translationKey;
        this.color = color;
    }

    /**
     * 获取类别名称（用于数据包传输）
     */
    public String getName() {
        return name;
    }

    /**
     * 获取翻译键
     */
    public String getTranslationKey() {
        return translationKey;
    }

    /**
     * 获取主题颜色（ARGB格式）
     */
    public int getColor() {
        return color;
    }

    /**
     * 根据名称查找类别
     */
    public static StatCategory fromName(String name) {
        for (StatCategory cat : values()) {
            if (cat.name.equals(name)) {
                return cat;
            }
        }
        return null;
    }
}