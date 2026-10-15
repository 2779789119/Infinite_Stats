package com.infinitestats.client;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.infinitestats.Config;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 用 Cloth Config API 把 {@link Config#SPEC} 里的每个配置项摊成一张可编辑的表，
 * 挂到「模组列表 → 本模组 → Config」按钮上。
 * <p>
 * 分类标题取 {@code config.infinitestats.category.<分组>}、条目名取
 * {@code config.infinitestats.<分组>.<键>}，说明文字直接取配置项自己的 comment。
 */
public final class ClothConfigScreen {

    private ClothConfigScreen() {}

    public static Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("screen.infinitestats.config"))
                .setSavingRunnable(ClothConfigScreen::safeSave);
        ConfigEntryBuilder eb = builder.entryBuilder();

        for (Map.Entry<String, Object> group : Config.SPEC.getValues().valueMap().entrySet()) {
            if (!(group.getValue() instanceof UnmodifiableConfig section)) continue;
            ConfigCategory category = builder.getOrCreateCategory(
                    Component.translatable("config.infinitestats.category." + group.getKey()));
            for (Map.Entry<String, Object> leaf : section.valueMap().entrySet()) {
                if (leaf.getValue() instanceof ModConfigSpec.ConfigValue<?> value) {
                    addEntry(eb, category, group.getKey(), leaf.getKey(), value);
                }
            }
        }
        return builder.build();
    }

    private static void safeSave() {
        try {
            Config.SPEC.save();
        } catch (Throwable ignored) {
            // 配置尚未加载等情况：忽略
        }
    }

    private static void addEntry(ConfigEntryBuilder eb, ConfigCategory category,
                                String group, String key, ModConfigSpec.ConfigValue<?> value) {
        Component name = Component.translatable("config.infinitestats." + group + "." + key);
        Component[] tooltip = tooltipOf(value);

        Object current;
        try {
            current = value.get();
        } catch (Throwable t) {
            return; // 配置尚未加载
        }
        Object def = defaultValueOf(value);

        if (value instanceof ModConfigSpec.BooleanValue boolValue) {
            boolean cur = current instanceof Boolean b ? b : false;
            boolean d = def instanceof Boolean b ? b : cur;
            category.addEntry(eb.startBooleanToggle(name, cur)
                    .setDefaultValue(d)
                    .setTooltip(tooltip)
                    .setSaveConsumer(boolValue::set)
                    .build());
        } else if (value instanceof ModConfigSpec.IntValue intValue) {
            int cur = current instanceof Number n ? n.intValue() : 0;
            int d = def instanceof Number n ? n.intValue() : cur;
            var b = eb.startIntField(name, cur)
                    .setDefaultValue(d)
                    .setTooltip(tooltip)
                    .setSaveConsumer(intValue::set);
            applyRange(b, value);
            category.addEntry(b.build());
        } else if (value instanceof ModConfigSpec.LongValue longValue) {
            long cur = current instanceof Number n ? n.longValue() : 0L;
            long d = def instanceof Number n ? n.longValue() : cur;
            var b = eb.startLongField(name, cur)
                    .setDefaultValue(d)
                    .setTooltip(tooltip)
                    .setSaveConsumer(longValue::set);
            applyRange(b, value);
            category.addEntry(b.build());
        } else if (value instanceof ModConfigSpec.DoubleValue doubleValue) {
            double cur = current instanceof Number n ? n.doubleValue() : 0d;
            double d = def instanceof Number n ? n.doubleValue() : cur;
            var b = eb.startDoubleField(name, cur)
                    .setDefaultValue(d)
                    .setTooltip(tooltip)
                    .setSaveConsumer(doubleValue::set);
            applyRange(b, value);
            category.addEntry(b.build());
        } else if (value instanceof ModConfigSpec.EnumValue<?> enumValue) {
            if (current instanceof Enum<?> cur) {
                @SuppressWarnings({"unchecked", "rawtypes"})
                var b = eb.startEnumSelector(name, (Class) cur.getDeclaringClass(), (Enum) cur);
                b.setDefaultValue((Enum) (def instanceof Enum<?> e ? e : cur))
                        .setTooltip(tooltip)
                        .setEnumNameProvider(e -> enumLabel(group, key, (Enum<?>) e))
                        .setSaveConsumer(v -> setRaw(enumValue, v));
                category.addEntry(b.build());
            }
        } else if (current instanceof List<?>) {
            @SuppressWarnings("unchecked")
            List<String> cur = (List<String>) current;
            category.addEntry(eb.startStrList(name, cur)
                    .setTooltip(tooltip)
                    .setSaveConsumer(v -> setRaw(value, v))
                    .build());
        }
    }

    private static void applyRange(Object builder, ModConfigSpec.ConfigValue<?> value) {
        ModConfigSpec.Range<?> range = rangeOf(value);
        if (range == null) return;
        Object lo = range.getMin();
        Object hi = range.getMax();
        if (!(lo instanceof Number ln) || !(hi instanceof Number hn)) return;
        if (builder instanceof me.shedaniel.clothconfig2.impl.builders.IntFieldBuilder ib) {
            ib.setMin(ln.intValue()).setMax(hn.intValue());
        } else if (builder instanceof me.shedaniel.clothconfig2.impl.builders.LongFieldBuilder lb) {
            lb.setMin(ln.longValue()).setMax(hn.longValue());
        } else if (builder instanceof me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder db) {
            db.setMin(ln.doubleValue()).setMax(hn.doubleValue());
        }
    }

    private static ModConfigSpec.Range<?> rangeOf(ModConfigSpec.ConfigValue<?> value) {
        Object spec = Config.SPEC.getSpec().getRaw(value.getPath());
        if (spec instanceof ModConfigSpec.ValueSpec vs) return vs.getRange();
        return null;
    }

    private static Component[] tooltipOf(ModConfigSpec.ConfigValue<?> value) {
        Object spec = Config.SPEC.getSpec().getRaw(value.getPath());
        if (spec instanceof ModConfigSpec.ValueSpec vs) {
            String comment = vs.getComment();
            if (comment != null && !comment.isBlank()) {
                return comment.lines()
                        .map(s -> Component.literal(s.strip()))
                        .toArray(Component[]::new);
            }
        }
        return new Component[0];
    }

    private static Object defaultValueOf(ModConfigSpec.ConfigValue<?> value) {
        Object spec = Config.SPEC.getSpec().getRaw(value.getPath());
        if (spec instanceof ModConfigSpec.ValueSpec vs) return vs.getDefault();
        return null;
    }

    private static Component enumLabel(String group, String key, Enum<?> e) {
        String k = "config.infinitestats." + group + "." + key + "."
                + e.name().toLowerCase(Locale.ROOT);
        return I18n.exists(k) ? Component.translatable(k) : Component.literal(e.name());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setRaw(ModConfigSpec.ConfigValue value, Object v) {
        try {
            value.set(v);
        } catch (Throwable ignored) {
        }
    }
}
