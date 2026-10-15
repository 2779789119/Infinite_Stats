package com.pinyinsearch.api;

import java.util.List;

/**
 * <b>pinyin_search（通用拼音搜索库）的编译期存根 —— 运行时永远不会用到这个类。</b>
 * <p>
 * 用途与维护约定同 {@code com.simibubi.create.content.kinetics.KineticNetwork} 的说明：
 * 该库对本模组只是「装了才生效」的可选运行时联动，但 {@code PinyinSearchBridge} 里有几处
 * 直接调用（类型只出现在方法体里，因此类加载是安全的），编译期需要这些符号存在。
 * 1.20.1 主线是从 jitpack 拉 compileOnly 依赖拿到它们，本分支改用存根，省掉一个外部仓库依赖。
 * <p>
 * 只列被调用到的成员；签名必须与库的实际实现一致（1.20.1 主线已核对）。
 */
public final class PinyinSearch {

    /** 单对单匹配：给定文本是否命中查询串（支持拼音、首字母、原文包含）。 */
    public static boolean matches(String text, String query) {
        throw new UnsupportedOperationException("compile-time stub");
    }

    /** 为一批文本建索引，之后可用不同 query 反复搜索。 */
    public static Matcher matcher(List<String> pool) {
        throw new UnsupportedOperationException("compile-time stub");
    }
}
