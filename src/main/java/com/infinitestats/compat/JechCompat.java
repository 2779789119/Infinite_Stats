package com.infinitestats.compat;

import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

/**
 * JustEnoughCharacters 兼容层
 * <p>
 * 通过反射调用 JEC 的拼音匹配 API，实现拼音搜索。
 * 若 JEC 未安装，则回退为标准的字符串包含匹配。
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
                // 反射调用失败时回退到标准匹配
            }
        }
        return input.contains(query);
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
     * JEC 是否已加载且 API 可用
     */
    public static boolean isLoaded() {
        return LOADED;
    }
}