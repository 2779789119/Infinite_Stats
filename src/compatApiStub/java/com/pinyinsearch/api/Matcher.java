package com.pinyinsearch.api;

import java.util.ArrayList;
import java.util.List;

/**
 * 编译期存根：pinyin_search 的匹配器（运行时永不被加载）。
 */
public class Matcher {

    public static Matcher literal(List<String> pool) {
        return new Matcher();
    }

    public List<Integer> searchIndices(String query) {
        return new ArrayList<>();
    }

    public List<String> search(String query) {
        return new ArrayList<>();
    }
}
