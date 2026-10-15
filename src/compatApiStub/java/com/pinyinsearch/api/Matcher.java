package com.pinyinsearch.api;

import java.util.List;

/**
 * <b>pinyin_search（通用拼音搜索库）的编译期存根 —— 运行时永远不会用到这个类。</b>
 * <p>
 * 用途与维护约定同 {@code com.pinyinsearch.api.PinyinSearch} 的说明。
 */
public class Matcher {

    /** 「关掉拼音、但仍走同一套归一化规则」的原文包含匹配器。 */
    public static Matcher literal(List<String> pool) {
        throw new UnsupportedOperationException("compile-time stub");
    }

    /** 命中项在 pool 里的下标（升序）。 */
    public List<Integer> searchIndices(String query) {
        throw new UnsupportedOperationException("compile-time stub");
    }

    /** 命中的文本，保留 pool 顺序。 */
    public List<String> search(String query) {
        throw new UnsupportedOperationException("compile-time stub");
    }
}
