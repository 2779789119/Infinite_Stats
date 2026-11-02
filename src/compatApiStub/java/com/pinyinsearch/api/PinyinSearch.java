package com.pinyinsearch.api;

import java.util.List;

/**
 * 编译期存根：pinyin_search 库的入口（仅用于编译 PinyinSearchBridge，运行时永不被加载）。
 * <p>
 * 1.19.2 没有可用的 pinyin_search 发行版（官方 1.1.x 只声明了 1.20.1），
 * 所以这里只保留「被调用到的成员」的空壳，让桥接类照旧编译；
 * 真正的库由玩家自行安装，装了就走拼音，没装就退化为原文包含。
 */
public final class PinyinSearch {

    private PinyinSearch() {
    }

    public static boolean matches(String text, String query) {
        return false;
    }

    public static Matcher matcher(List<String> pool) {
        return new Matcher();
    }
}
