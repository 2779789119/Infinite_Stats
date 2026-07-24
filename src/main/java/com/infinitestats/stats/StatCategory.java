package com.infinitestats.stats;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 属性类别 - 分类系统
 * 每个类别有唯一名称、翻译键、主题颜色和图标
 */
public enum StatCategory {

    ATTACK("attack", "category.infinitestats.attack", 0xFF5555, Items.DIAMOND_SWORD),
    DEFENSE("defense", "category.infinitestats.defense", 0x5555FF, Items.SHIELD),
    MOBILITY("mobility", "category.infinitestats.mobility", 0x55FF55, Items.ELYTRA),
    UTILITY("utility", "category.infinitestats.utility", 0xFFAA00, Items.DIAMOND_PICKAXE),
    MAGIC("magic", "category.infinitestats.magic", 0xAA55FF, Items.ENCHANTED_BOOK),
    /** 其他模组注册的属性 - 自动从 ForgeRegistries.ATTRIBUTES 发现 */
    EXTERNAL("external", "category.infinitestats.external", 0xFF66B2, Items.KNOWLEDGE_BOOK);

    private final String name;
    private final String translationKey;
    private final int color;
    private final ItemStack icon;

    StatCategory(String name, String translationKey, int color, Item iconItem) {
        this.name = name;
        this.translationKey = translationKey;
        this.color = color;
        this.icon = new ItemStack(iconItem);
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
     * 获取类别图标
     */
    public ItemStack getIcon() {
        return icon;
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