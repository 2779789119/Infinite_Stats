package com.infinitestats.util;

import com.infinitestats.compat.PinyinSearchBridge;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

/**
 * 存储终端风格的物品搜索语法（参考精致存储 / 汤姆存储的搜索框）：
 * <ul>
 *   <li>普通词 → 物品显示名（支持拼音）或注册名包含；</li>
 *   <li>{@code @模组} → 按命名空间或模组显示名过滤（如 {@code @create}、{@code @机械动力}）；</li>
 *   <li>{@code #标签} → 按物品标签过滤（如 {@code #minecraft:coals}、{@code #forge:ingots/iron}）；</li>
 *   <li>多个词用空格分隔 → 全部命中才算匹配（AND）。</li>
 * </ul>
 *
 * <p>客户端与服务端共用同一套判定：网络标签页的过滤在服务端做（只回传匹配结果），
 * 如果两边规则不一致，就会出现「客户端筛掉了、服务端又发回来」的闪烁。
 */
public final class ItemSearch {

    private ItemSearch() {
    }

    public static boolean matches(ItemStack stack, String query) {
        if (stack == null || stack.isEmpty()) return false;
        if (query == null) return true;
        String trimmed = query.trim();
        if (trimmed.isEmpty()) return true;

        for (String token : trimmed.split("\\s+")) {
            if (token.isEmpty()) continue;
            if (!matchesToken(stack, token)) return false;
        }
        return true;
    }

    private static boolean matchesToken(ItemStack stack, String token) {
        if (token.startsWith("@") && token.length() > 1) {
            return matchesMod(stack, token.substring(1).toLowerCase(java.util.Locale.ROOT));
        }
        if (token.startsWith("#") && token.length() > 1) {
            return matchesTag(stack, token.substring(1));
        }
        return matchesText(stack, token);
    }

    private static boolean matchesMod(ItemStack stack, String needle) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) return false;
        String namespace = id.getNamespace();
        if (namespace.toLowerCase(java.util.Locale.ROOT).contains(needle)) return true;
        // 模组显示名（中文名也能搜）
        return ModList.get().getModContainerById(namespace)
                .map(container -> container.getModInfo().getDisplayName().toLowerCase(java.util.Locale.ROOT))
                .map(name -> name.contains(needle))
                .orElse(false);
    }

    private static boolean matchesTag(ItemStack stack, String raw) {
        ResourceLocation tagId = ResourceLocation.tryParse(raw);
        if (tagId == null) return false;
        return stack.is(TagKey.create(Registries.ITEM, tagId));
    }

    private static boolean matchesText(ItemStack stack, String needle) {
        String name = stack.getHoverName().getString();
        if (PinyinSearchBridge.matches(name, needle)) return true;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && id.toString().toLowerCase(java.util.Locale.ROOT)
                .contains(needle.toLowerCase(java.util.Locale.ROOT));
    }

    /** 物品所属模组的命名空间（tooltip 里显示来源用）。 */
    public static String namespaceOf(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "" : id.getNamespace();
    }

    /** 模组显示名；查不到时退回命名空间。 */
    public static String modNameOf(ItemStack stack) {
        String namespace = namespaceOf(stack);
        return ModList.get().getModContainerById(namespace)
                .map(container -> container.getModInfo().getDisplayName())
                .orElse(namespace);
    }
}
