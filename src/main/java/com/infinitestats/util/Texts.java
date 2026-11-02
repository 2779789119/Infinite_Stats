package com.infinitestats.util;

import net.minecraft.locale.Language;

/**
 * 语言表小工具。
 *
 * <p>1.19.2 的 {@link Language} 只有 {@code getOrDefault(String)}（未命中返回 key 本身），
 * 1.20 才加的 {@code getOrDefault(String, String)} 在这里没有。
 * 需要「未命中返回空串」的地方统一走 {@link #langOrEmpty}，避免各处自己判断。
 */
public final class Texts {

    private Texts() {
    }

    /** 取翻译；未命中返回空串（而不是 key）。 */
    public static String langOrEmpty(String key) {
        Language language = Language.getInstance();
        return language.has(key) ? language.getOrDefault(key) : "";
    }
}
