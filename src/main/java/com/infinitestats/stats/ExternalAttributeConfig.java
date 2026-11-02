package com.infinitestats.stats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 外部属性覆盖配置：{@code config/infinitestats/external_attributes.json}。
 *
 * <h2>为什么是独立 JSON，而不是塞进 Forge 配置</h2>
 * 外部属性的数量取决于整合包装了哪些模组 —— 同一个实例在不同整合包里可能是 80 条，
 * 也可能是 600 条。按 Forge 配置「每个属性两个键」的方式生成，配置文件会变成几千行，
 * 且每次换整合包都要重写。这里只记录<b>需要人工干预的那几条</b>，其余继续走自动推导。
 *
 * <h2>匹配规则</h2>
 * <ul>
 *   <li>完整注册名：{@code mod:attr}</li>
 *   <li>整个命名空间：{@code mod:*}</li>
 *   <li>{@code *}：全部外部属性</li>
 * </ul>
 * 大小写不敏感（注册名本身总是小写，这里只是容错）。
 *
 * <h2>刷新时机</h2>
 * {@link StatType#reloadExternalAttributes()} 会重新读一次本文件；对局内用
 * {@code /infstats reload external} 触发，无需重启游戏。
 */
public final class ExternalAttributeConfig {

    /** 单条属性的覆盖项；字段为 {@code null} 表示「不覆盖，沿用自动推导」。 */
    public static final class Override {
        private String displayName;
        private String description;
        private Double perPoint;
        private Integer maxPoints;
        private boolean hidden;
        private boolean enabled = true;

        @Nullable
        public String getDisplayName() {
            return displayName;
        }

        @Nullable
        public String getDescription() {
            return description;
        }

        @Nullable
        public Double getPerPoint() {
            return perPoint;
        }

        @Nullable
        public Integer getMaxPoints() {
            return maxPoints;
        }

        public boolean isHidden() {
            return hidden;
        }

        public boolean isEnabled() {
            return enabled;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** 运行目录下的相对路径（与 {@link StatType} 生成的翻译模板同目录）。 */
    private static final Path FILE = Path.of("config", "infinitestats", "external_attributes.json");

    private static final List<String> BLACKLIST = new ArrayList<>();
    private static final List<String> WHITELIST = new ArrayList<>();
    private static final Map<String, Override> OVERRIDES = new LinkedHashMap<>();

    /** 是否已经读过一次文件（没读过时一切都按「全部收录」处理）。 */
    private static boolean loaded = false;
    /** 上次解析失败的提示，空串表示没有问题。 */
    private static String lastError = "";

    private ExternalAttributeConfig() {
    }

    /**
     * 重新读取配置文件。
     * <p>
     * 文件不存在时写一份带注释的模板；解析失败时<b>保留旧配置</b>并记下错误，
     * 不让一条手滑的 JSON 把整个外部属性分类清空。
     */
    public static synchronized void reload() {
        List<String> newBlacklist = new ArrayList<>();
        List<String> newWhitelist = new ArrayList<>();
        Map<String, Override> newOverrides = new LinkedHashMap<>();
        String error = "";

        try {
            Path dir = FILE.getParent();
            if (dir != null) Files.createDirectories(dir);

            if (!Files.exists(FILE)) {
                Files.writeString(FILE, template(), StandardCharsets.UTF_8);
                System.out.println("[InfiniteStats] Wrote external attribute config template: " + FILE);
            } else {
                String text = Files.readString(FILE, StandardCharsets.UTF_8);
                JsonElement parsed = text.isBlank() ? null : JsonParser.parseString(text);
                if (parsed != null && parsed.isJsonObject()) {
                    JsonObject root = parsed.getAsJsonObject();
                    readStringList(root, "blacklist", newBlacklist);
                    readStringList(root, "whitelist", newWhitelist);
                    readOverrides(root, newOverrides);
                } else if (parsed != null) {
                    error = "root is not a JSON object";
                }
            }
        } catch (Exception e) {
            error = e.getClass().getSimpleName() + ": " + e.getMessage();
        }

        if (!error.isEmpty()) {
            lastError = error;
            System.err.println("[InfiniteStats] external_attributes.json 解析失败，沿用上一次配置：" + error);
            // 首次读取就失败时至少标记为已加载，避免每次查询都去碰磁盘
            loaded = true;
            return;
        }

        BLACKLIST.clear();
        BLACKLIST.addAll(newBlacklist);
        WHITELIST.clear();
        WHITELIST.addAll(newWhitelist);
        OVERRIDES.clear();
        OVERRIDES.putAll(newOverrides);
        lastError = "";
        loaded = true;

        System.out.println("[InfiniteStats] External attribute config: "
                + BLACKLIST.size() + " blacklist, " + WHITELIST.size() + " whitelist, "
                + OVERRIDES.size() + " override(s)");
    }

    private static void readStringList(JsonObject root, String key, List<String> out) {
        JsonElement element = root.get(key);
        if (element == null || !element.isJsonArray()) return;
        JsonArray array = element.getAsJsonArray();
        for (JsonElement item : array) {
            if (item != null && item.isJsonPrimitive()) {
                String value = item.getAsString().trim();
                if (!value.isEmpty()) out.add(value);
            }
        }
    }

    private static void readOverrides(JsonObject root, Map<String, Override> out) {
        JsonElement element = root.get("overrides");
        if (element == null || !element.isJsonObject()) return;

        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            String attrName = entry.getKey() == null ? "" : entry.getKey().trim();
            if (attrName.isEmpty() || !entry.getValue().isJsonObject()) continue;

            JsonObject obj = entry.getValue().getAsJsonObject();
            Override override = new Override();
            override.displayName = readString(obj, "displayName");
            override.description = readString(obj, "description");
            override.perPoint = readDouble(obj, "perPoint");
            override.maxPoints = readInt(obj, "maxPoints");
            override.hidden = readBoolean(obj, "hidden", false);
            override.enabled = readBoolean(obj, "enabled", true);
            out.put(attrName.toLowerCase(Locale.ROOT), override);
        }
    }

    @Nullable
    private static String readString(JsonObject obj, String key) {
        JsonElement element = obj.get(key);
        if (element == null || !element.isJsonPrimitive()) return null;
        String value = element.getAsString().trim();
        return value.isEmpty() ? null : value;
    }

    @Nullable
    private static Double readDouble(JsonObject obj, String key) {
        JsonElement element = obj.get(key);
        if (element == null || !element.isJsonPrimitive()) return null;
        try {
            double value = element.getAsDouble();
            return Double.isFinite(value) ? value : null;
        } catch (RuntimeException notANumber) {
            return null;
        }
    }

    @Nullable
    private static Integer readInt(JsonObject obj, String key) {
        Double value = readDouble(obj, key);
        if (value == null) return null;
        int rounded = (int) Math.round(value);
        return rounded < 1 ? null : rounded;
    }

    private static boolean readBoolean(JsonObject obj, String key, boolean fallback) {
        JsonElement element = obj.get(key);
        if (element == null || !element.isJsonPrimitive()) return fallback;
        try {
            return element.getAsBoolean();
        } catch (RuntimeException notABoolean) {
            return fallback;
        }
    }

    /**
     * 该注册名的属性是否应该被收录为「外部属性」。
     * <p>
     * 优先级：{@code overrides.enabled=false} → 黑名单 → 白名单（为空表示不限制）。
     * 被排除的属性不会出现在面板里，也不会生成任何 StatType。
     */
    public static boolean isAllowed(String attributeName) {
        if (attributeName == null || attributeName.isEmpty()) return false;
        if (!loaded) reload();

        Override override = get(attributeName);
        if (override != null && !override.isEnabled()) return false;
        if (matchesAny(BLACKLIST, attributeName)) return false;
        if (!WHITELIST.isEmpty() && !matchesAny(WHITELIST, attributeName)) return false;
        return true;
    }

    @Nullable
    public static Override get(String attributeName) {
        if (attributeName == null) return null;
        return OVERRIDES.get(attributeName.toLowerCase(Locale.ROOT));
    }

    public static String getLastError() {
        return lastError;
    }

    public static int blacklistSize() {
        return BLACKLIST.size();
    }

    private static boolean matchesAny(List<String> patterns, String attributeName) {
        for (String pattern : patterns) {
            if (matches(pattern, attributeName)) return true;
        }
        return false;
    }

    private static boolean matches(String pattern, String attributeName) {
        if (pattern == null) return false;
        String p = pattern.trim().toLowerCase(Locale.ROOT);
        String name = attributeName.toLowerCase(Locale.ROOT);
        if (p.isEmpty()) return false;
        if (p.equals("*") || p.equals(name)) return true;
        // "mod:*" —— 末尾保留冒号，避免把 "mod:" 误当成 "mode:" 之类的前缀
        return p.endsWith(":*") && name.startsWith(p.substring(0, p.length() - 1));
    }

    private static String template() {
        JsonObject root = new JsonObject();
        root.addProperty("_comment", "外部属性（其他模组注册的属性）高级配置。改完执行 /infstats reload external 立即生效（需要 OP 权限）。");
        root.addProperty("_comment_blacklist", "不收录的属性注册名。支持完整名 mod:attr、整个命名空间 mod:*、以及 * 表示全部。");
        root.add("blacklist", new JsonArray());
        root.addProperty("_comment_whitelist", "非空时只收录列出的属性（同样支持 mod:*）。留空表示收录全部。");
        root.add("whitelist", new JsonArray());
        root.addProperty("_comment_overrides", "逐条覆盖自动推导的数值。perPoint 每点数值，maxPoints 可投入点数上限，"
                + "displayName 自定义显示名，description 面板浮窗说明，hidden 只对开启「显示隐藏属性」的玩家可见，"
                + "enabled=false 等价于加进黑名单。");
        JsonObject example = new JsonObject();
        example.addProperty("displayName", "示例属性（这条不会匹配任何真实属性，可整段删掉）");
        example.addProperty("perPoint", 0.05);
        example.addProperty("maxPoints", 2000);
        example.addProperty("hidden", false);
        example.addProperty("enabled", true);
        example.addProperty("description", "面板浮窗里显示的一行说明");
        JsonObject overrides = new JsonObject();
        overrides.add("examplemod:example_attribute", example);
        root.add("overrides", overrides);
        return GSON.toJson(root) + System.lineSeparator();
    }
}
