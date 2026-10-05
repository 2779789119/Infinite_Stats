package com.infinitestats.compat;

import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 拼音搜索桥接：装了 pinyin_search 就走拼音匹配，没装则完全退化为纯原文包含。
 *
 * ★ 关键约定：库的类型（PinyinSearch / Matcher / Profile）只出现在方法体里，
 *   绝不写进字段类型、方法签名、继承或实现 —— 否则类加载阶段就会 NoClassDefFoundError。
 */
public final class PinyinSearchBridge {

    public static final String MOD_ID = "pinyin_search";

    /** 字段类型是 boolean，不是库里的类型，加载安全。 */
    private static final boolean AVAILABLE = ModList.get().isLoaded(MOD_ID);

    private PinyinSearchBridge() {
    }

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    /** 单对单匹配：小列表（几十~几百条）逐条判断用这个。 */
    public static boolean matches(String text, String query) {
        if (AVAILABLE) {
            return com.pinyinsearch.api.PinyinSearch.matches(text, query);
        }
        return rawContains(text, query);
    }

    /**
     * 大数据集：建一次索引，之后用不同 query 反复搜。
     *
     * @param pool 文本池，必须是稳定有序的 List（index 返回的下标语义依赖迭代顺序）
     */
    public static Index index(List<String> pool) {
        return new Index(pool, createImpl(pool, true));
    }

    /**
     * 建索引，并显式指定是否启用拼音（装了库但玩家关掉拼音开关时传 false）。
     *
     * @param pool          文本池
     * @param pinyinEnabled 是否启用拼音
     */
    public static Index index(List<String> pool, boolean pinyinEnabled) {
        return new Index(pool, createImpl(pool, pinyinEnabled));
    }

    /**
     * 实现对象。返回类型故意写成 Object：
     * 库没装时返回 null，调用方走纯原文分支，绝不触碰库的类型。
     */
    private static Object createImpl(List<String> pool, boolean pinyinEnabled) {
        if (!AVAILABLE) {
            return null;
        }
        // 装了库：Matcher.literal 是「关掉拼音但仍按同一套归一化规则做原文包含」
        return pinyinEnabled
                ? com.pinyinsearch.api.PinyinSearch.matcher(pool)
                : com.pinyinsearch.api.Matcher.literal(pool);
    }

    private static boolean rawContains(String text, String query) {
        if (text == null || query == null) {
            return true;
        }
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return true;
        }
        return text.toLowerCase(Locale.ROOT).contains(q);
    }

    /**
     * 索引句柄：对外只暴露 List<Integer> / List<String>，库的类型被封装在内部。
     * 本类可安全加载（库没装时 impl == null）。
     */
    public static final class Index {
        private final List<String> pool;
        private final Object impl;   // 实际类型是 Matcher；库没装时为 null

        private Index(List<String> pool, Object impl) {
            this.pool = pool == null ? List.of() : new ArrayList<>(pool);
            this.impl = impl;
        }

        /** 命中下标，升序；query 为 null / 空 / 全空白 → 返回全部下标。 */
        public List<Integer> indices(String query) {
            if (impl != null) {
                return ((com.pinyinsearch.api.Matcher) impl).searchIndices(query);
            }
            List<Integer> ret = new ArrayList<>();
            for (int i = 0; i < pool.size(); i++) {
                if (rawContains(pool.get(i), query)) {
                    ret.add(i);
                }
            }
            return ret;
        }

        /** 命中的文本，保留 pool 顺序与重复项。 */
        public List<String> search(String query) {
            if (impl != null) {
                return ((com.pinyinsearch.api.Matcher) impl).search(query);
            }
            List<String> ret = new ArrayList<>();
            for (Integer i : indices(query)) {
                ret.add(pool.get(i));
            }
            return ret;
        }
    }
}
