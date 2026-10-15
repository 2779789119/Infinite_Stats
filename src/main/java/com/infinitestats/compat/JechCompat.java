package com.infinitestats.compat;

import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

/**
 * JustEnoughCharacters 兼容层
 * <p>
 * 通过反射调用 JEC 的拼音匹配 API，实现拼音搜索。
 * 若 JEC 未安装，则回退到第二个拼音库 {@code pinyin_search}
 * （见 {@link PinyinSearchBridge}，同样是「装了才有」，没装就是标准字符串包含匹配）。
 * <p>
 * 两个库都能接的意义：整合包用哪个就用哪个 —— 手上有 JEC 的圈子与用 pinyin_search 的圈子
 * 各占一半，只接一个会让另一半玩家的搜索框「拼音打不出来」。
 * <p>
 * JEC API（尝试多个可能的路径，兼容不同版本）：
 * - me.towdium.jecharacters.util.Match.contains(String, String)
 * - me.towdium.jecharacters.Match.contains(String, String)
 */
public final class JechCompat {

    private static final boolean LOADED;
    private static Method containsMethod;

    static {
        boolean loaded = false;
        if (ModList.get().isLoaded("jecharacters")) {
            String[] candidates = {
                    "me.towdium.jecharacters.util.Match",
                    "me.towdium.jecharacters.Match"
            };
            for (String className : candidates) {
                try {
                    Class<?> clz = Class.forName(className);
                    containsMethod = clz.getMethod("contains", String.class, String.class);
                    loaded = true;
                    break;
                } catch (Exception ignored) {
                }
            }
        }
        LOADED = loaded;
    }

    /**
     * 检查输入是否匹配查询（支持拼音）。
     * 输入和查询都应已转为小写。
     *
     * @param input 已转小写的待匹配文本
     * @param query 已转小写的搜索关键词
     * @return 是否匹配
     */
    public static boolean matches(String input, String query) {
        if (query.isEmpty()) return true;
        if (LOADED && containsMethod != null) {
            try {
                return (boolean) containsMethod.invoke(null, input, query);
            } catch (Exception e) {
                // 反射调用失败时继续往下走（pinyin_search / 原文包含）
            }
        }
        // JEC 没装 / 调用失败：交给 pinyin_search（装了走拼音，没装是原文包含）
        return PinyinSearchBridge.matches(input, query);
    }

    /**
     * 检查输入是否匹配查询，自动处理大小写。
     *
     * @param input 原始待匹配文本
     * @param query 原始搜索关键词
     * @return 是否匹配
     */
    public static boolean matchesIgnoreCase(String input, String query) {
        if (query.isEmpty()) return true;
        return matches(input.toLowerCase(), query.toLowerCase());
    }

    /**
     * 是否有可用的拼音搜索后端（JEC 或 pinyin_search 任一）。
     * <p>
     * 界面可以据此决定要不要在搜索框的提示里写「支持拼音」。
     */
    public static boolean isLoaded() {
        return LOADED || PinyinSearchBridge.isAvailable();
    }
}