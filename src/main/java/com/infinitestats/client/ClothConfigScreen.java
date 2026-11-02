package com.infinitestats.client;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.infinitestats.Config;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 用 Cloth Config API 把 {@link Config#SPEC} 里的每个配置项摊成一张可编辑的表，
 * 挂到「模组列表 → 本模组 → Config」按钮上。
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
                if (leaf.getValue() instanceof ForgeConfigSpec.ConfigValue<?> value) {
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
                                String group, String key, ForgeConfigSpec.ConfigValue<?> value) {
        Component name = Component.translatable("config.infinitestats." + group + "." + key);
        Component[] tooltip = tooltipOf(value);

        Object current;
        try {
            current = value.get();
        } catch (Throwable t) {
            return; // 配置尚未加载
        }
        Object def = defaultValueOf(value);

        if (value instanceof ForgeConfigSpec.BooleanValue boolValue) {
            boolean cur = current instanceof Boolean b ? b : false;
            boolean d = def instanceof Boolean b ? b : cur;
            category.addEntry(eb.startBooleanToggle(name, cur)
                    .setDefaultValue(d)
                    .setTooltip(tooltip)
                    .setSaveConsumer(boolValue::set)
                    .build());
        } else if (value instanceof ForgeConfigSpec.IntValue intValue) {
            int cur = current instanceof Number n ? n.intValue() : 0;
            int d = def instanceof Number n ? n.intValue() : cur;
            var b = eb.startIntField(name, cur)
                    .setDefaultValue(d)
                    .setTooltip(tooltip)
                    .setSaveConsumer(intValue::set);
            applyRange(b, value);
            category.addEntry(b.build());
        } else if (value instanceof ForgeConfigSpec.LongValue longValue) {
            long cur = current instanceof Number n ? n.longValue() : 0L;
            long d = def instanceof Number n ? n.longValue() : cur;
            var b = eb.startLongField(name, cur)
                    .setDefaultValue(d)
                    .setTooltip(tooltip)
                    .setSaveConsumer(longValue::set);
            applyRange(b, value);
            category.addEntry(b.build());
        } else if (value instanceof ForgeConfigSpec.DoubleValue doubleValue) {
            double cur = current instanceof Number n ? n.doubleValue() : 0d;
            double d = def instanceof Number n ? n.doubleValue() : cur;
            var b = eb.startDoubleField(name, cur)
                    .setDefaultValue(d)
                    .setTooltip(tooltip)
                    .setSaveConsumer(doubleValue::set);
            applyRange(b, value);
            category.addEntry(b.build());
        } else if (value instanceof ForgeConfigSpec.EnumValue<?> enumValue) {
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

    private static void applyRange(Object builder, ForgeConfigSpec.ConfigValue<?> value) {
        Number[] range = rangeOf(value);
        if (range == null) return;
        Number ln = range[0];
        Number hn = range[1];
        if (builder instanceof me.shedaniel.clothconfig2.impl.builders.IntFieldBuilder ib) {
            ib.setMin(ln.intValue()).setMax(hn.intValue());
        } else if (builder instanceof me.shedaniel.clothconfig2.impl.builders.LongFieldBuilder lb) {
            lb.setMin(ln.longValue()).setMax(hn.longValue());
        } else if (builder instanceof me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder db) {
            db.setMin(ln.doubleValue()).setMax(hn.doubleValue());
        }
    }

    /**
     * 取配置项的数值区间 [下限, 上限]。
     * <p>
     * <b>不能把返回类型写成 {@code ForgeConfigSpec.Range<?>}</b>：那个内部类在 Forge 43.2.0 及更早
     * 是 private（43.5.x 才改成 public），写进方法签名会让低版本直接编译失败
     * （"Range has private access in ForgeConfigSpec"）。这里用反射读 getMin / getMax，
     * 新旧版本都能跑；取不到就返回 null，界面退化成不限范围的输入框。
     */
    private static Number[] rangeOf(ForgeConfigSpec.ConfigValue<?> value) {
        Object spec = Config.SPEC.getSpec().getRaw(value.getPath());
        if (!(spec instanceof ForgeConfigSpec.ValueSpec vs)) return null;
        Object range = vs.getRange();
        if (range == null) return null;
        try {
            java.lang.reflect.Method getMin = range.getClass().getMethod("getMin");
            java.lang.reflect.Method getMax = range.getClass().getMethod("getMax");
            getMin.setAccessible(true);
            getMax.setAccessible(true);
            if (getMin.invoke(range) instanceof Number lo && getMax.invoke(range) instanceof Number hi) {
                return new Number[]{lo, hi};
            }
        } catch (Throwable ignored) {
            // 版本差异 / 反射受限：当作没有范围，别让配置界面炸掉
        }
        return null;
    }

    private static Component[] tooltipOf(ForgeConfigSpec.ConfigValue<?> value) {
        Object spec = Config.SPEC.getSpec().getRaw(value.getPath());
        if (spec instanceof ForgeConfigSpec.ValueSpec vs) {
            String comment = vs.getComment();
            if (comment != null && !comment.isBlank()) {
                return comment.lines()
                        .map(s -> Component.literal(s.strip()))
                        .toArray(Component[]::new);
            }
        }
        return new Component[0];
    }

    private static Object defaultValueOf(ForgeConfigSpec.ConfigValue<?> value) {
        Object spec = Config.SPEC.getSpec().getRaw(value.getPath());
        if (spec instanceof ForgeConfigSpec.ValueSpec vs) return vs.getDefault();
        return null;
    }

    private static Component enumLabel(String group, String key, Enum<?> e) {
        String k = "config.infinitestats." + group + "." + key + "."
                + e.name().toLowerCase(Locale.ROOT);
        return I18n.exists(k) ? Component.translatable(k) : Component.literal(e.name());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setRaw(ForgeConfigSpec.ConfigValue value, Object v) {
        try {
            value.set(v);
        } catch (Throwable ignored) {
        }
    }
}
