package com.infinitestats.compat;

import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 拔刀剑（SlashBlade: Resharped）联动 —— 让物品编辑器可以改刀的各项成长数值。
 * <p>
 * 覆盖玩家在编辑器里能改的五项：<b>杀敌数</b>（killCount）、<b>耀魂数</b>（proudSoul）、
 * <b>锻造数</b>（refine）、<b>SA</b>（特殊攻击 / 剑技）、<b>SE</b>（特殊效果列表）。
 * <p>
 * <b>数据怎么读写的（1.21.1 实况，已用 {@code javap} 对 {@code SlashBladeResharped-2.1.9-1.21.1}
 * 逐项核对）</b>：1.21 的重铸版把整套状态搬进了<b>数据组件</b>，并且提供了一份干净的访问器：
 * <pre>
 * BladeStateAccess.of(ItemStack)          → Optional&lt;ISlashBladeState&gt;   ← 读写都用这个
 * BladeStateAccess.getData(ItemStack)     → Optional&lt;BladeStateData&gt;      （只读的不可变快照）
 * BladeStateAccess.setData(ItemStack, ...) / updateData(..., UnaryOperator)
 * </pre>
 * {@code of()} 返回的实例实际类型是 {@code BladeStateAccess$ComponentBackedState}
 * （{@code implements ISlashBladeState}），它的 <b>setter 直接写回数据组件</b> —— 所以
 * 「改完当场生效 + 自动同步 + 自动存档」三件事一次到位，不需要像 1.20.1 那样
 * 「改内存态 → {@code serializeNBT()} → 再把 NBT 塞回物品」。
 * <p>
 * <b>注意与 1.20.1 的差别</b>：1.20.1 是通过能力 {@code ItemSlashBlade.BLADESTATE} +
 * 物品 NBT 的 {@code "bladeState"} 子标签读写；这两个东西在 1.21.1 上<b>都不存在了</b>
 * （{@code BLADESTATE} 字段已移除，状态也不再放在自定义数据里）。所以本类不再碰 NBT，
 * 一律走 {@code BladeStateAccess.of()}。
 * <p>
 * 全程<b>反射</b>：拔刀剑只是可选运行时联动，本模组不声明对它的编译期依赖。没装时所有入口直接
 * 短路、零开销；版本对不上导致某个锚点取不到时只让编辑器这一块退化为不可用。
 */
public final class SlashBladeCompat {

    /** 拔刀剑的 mod id（{@code mods.flammpfeil.slashblade.SlashBlade#MODID}）。 */
    public static final String MOD_ID = "slashblade";

    private static final String ITEM_CLASS = "mods.flammpfeil.slashblade.item.ItemSlashBlade";
    private static final String STATE_ACCESS = "mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess";
    private static final String STATE_IFACE = "mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState";
    private static final String SA_REGISTRY = "mods.flammpfeil.slashblade.registry.SlashArtsRegistry";
    private static final String SE_REGISTRY = "mods.flammpfeil.slashblade.registry.SpecialEffectsRegistry";

    /** 翻译键前缀：{@code Util.makeDescriptionId("slash_art"/"se", key)} 的产物。 */
    private static final String SA_LANG_PREFIX = "slash_art.";
    private static final String SE_LANG_PREFIX = "se.";

    // ======================== 反射句柄（懒解析 + 失败永久短路） ========================

    private static volatile boolean staticResolved;
    private static Class<?> itemClass;

    /** {@code BladeStateAccess.of(ItemStack)} → {@code Optional<ISlashBladeState>}。 */
    private static Method mStateOf;

    private static Method mGetKill, mGetSoul, mGetRefine, mGetSa, mGetSe;
    private static Method mSetKill, mSetSoul, mSetRefine, mSetSa, mSetSe;

    /** SA / SE 候选列表（注册表加载完成后内容固定，可安全记忆化）。 */
    private static volatile List<ResourceLocation> saCache;
    private static volatile List<ResourceLocation> seCache;

    private SlashBladeCompat() {}

    // ======================== 判定 ========================

    /** 该物品是不是拔刀剑。 */
    public static boolean isBlade(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!ModList.get().isLoaded(MOD_ID)) return false;
        Class<?> cls = itemClass();
        return cls != null && cls.isInstance(stack.getItem());
    }

    /** 当前环境是否装了拔刀剑。 */
    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    private static Class<?> itemClass() {
        ensureStatic();
        return itemClass;
    }

    private static void ensureStatic() {
        if (staticResolved) return;
        synchronized (SlashBladeCompat.class) {
            if (staticResolved) return;
            staticResolved = true;
            try {
                itemClass = Class.forName(ITEM_CLASS);
            } catch (Throwable t) {
                itemClass = null;
                return;
            }
            try {
                mStateOf = Class.forName(STATE_ACCESS).getMethod("of", ItemStack.class);
            } catch (Throwable t) {
                mStateOf = null;
            }
            try {
                Class<?> iface = Class.forName(STATE_IFACE);
                mGetKill = iface.getMethod("getKillCount");
                mGetSoul = iface.getMethod("getProudSoulCount");
                mGetRefine = iface.getMethod("getRefine");
                mGetSa = iface.getMethod("getSlashArtsKey");
                mGetSe = iface.getMethod("getSpecialEffects");

                mSetKill = iface.getMethod("setKillCount", int.class);
                mSetSoul = iface.getMethod("setProudSoulCount", int.class);
                mSetRefine = iface.getMethod("setRefine", int.class);
                mSetSa = iface.getMethod("setSlashArtsKey", ResourceLocation.class);
                mSetSe = iface.getMethod("setSpecialEffects", ListTag.class);
            } catch (Throwable t) {
                mGetKill = mGetSoul = mGetRefine = mGetSa = mGetSe = null;
                mSetKill = mSetSoul = mSetRefine = mSetSa = mSetSe = null;
            }
        }
    }

    /**
     * 取刀的状态对象（{@code BladeStateAccess.of(stack)}），拿不到返回 {@code null}。
     * <p>
     * 读和写用的是同一个对象：它的 setter 直接写回数据组件，所以改完当场生效、也会同步与存档。
     */
    private static Object stateOf(ItemStack stack) {
        ensureStatic();
        Method of = mStateOf;
        if (of == null || stack == null || stack.isEmpty()) return null;
        try {
            Object optional = of.invoke(null, stack);
            return optional instanceof Optional<?> opt ? opt.orElse(null) : null;
        } catch (Throwable t) {
            return null;
        }
    }

    // ======================== 读取（客户端可安全调用） ========================

    public static int killCount(ItemStack stack) {
        return readInt(stack, mGetKill);
    }

    public static int proudSoul(ItemStack stack) {
        return readInt(stack, mGetSoul);
    }

    public static int refine(ItemStack stack) {
        return readInt(stack, mGetRefine);
    }

    private static int readInt(ItemStack stack, Method getter) {
        Object state = stateOf(stack);
        if (state == null || getter == null) return 0;
        try {
            Object value = getter.invoke(state);
            return value instanceof Integer i ? Math.max(0, i) : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    /** 当前 SA 的 id 字符串；无 / 无法解析时返回空串。 */
    public static String slashArts(ItemStack stack) {
        Object state = stateOf(stack);
        if (state == null || mGetSa == null) return "";
        try {
            Object value = mGetSa.invoke(state);
            return value instanceof ResourceLocation rl ? rl.toString() : "";
        } catch (Throwable t) {
            return "";
        }
    }

    /** 当前 SE 列表（id 字符串）。 */
    public static List<String> specialEffects(ItemStack stack) {
        Object state = stateOf(stack);
        if (state == null || mGetSe == null) return new ArrayList<>();
        try {
            Object value = mGetSe.invoke(state);
            // 1.21.1 的 getSpecialEffects() 返回 Collection<ResourceLocation>（1.20.1 是 ListTag）
            if (value instanceof Collection<?> collection) {
                List<String> out = new ArrayList<>(collection.size());
                for (Object element : collection) {
                    if (element instanceof ResourceLocation rl) out.add(rl.toString());
                }
                return out;
            }
            return new ArrayList<>();
        } catch (Throwable t) {
            return new ArrayList<>();
        }
    }

    // ======================== 候选枚举（SA / SE 注册表） ========================

    /** 全部已注册的 SA（特殊攻击）id，按 id 排序。未装 / 取不到时返回空列表。 */
    public static List<ResourceLocation> slashArtsOptions() {
        List<ResourceLocation> cached = saCache;
        if (cached != null) return cached;
        List<ResourceLocation> keys = registryKeys(SA_REGISTRY);
        // 只在拿到非空结果时缓存：避免万一在注册表尚未冻结时被调用而把空表固化下来
        if (!keys.isEmpty()) saCache = keys;
        return keys;
    }

    /** 全部已注册的 SE（特殊效果）id，按 id 排序。未装 / 取不到时返回空列表。 */
    public static List<ResourceLocation> specialEffectsOptions() {
        List<ResourceLocation> cached = seCache;
        if (cached != null) return cached;
        List<ResourceLocation> keys = registryKeys(SE_REGISTRY);
        if (!keys.isEmpty()) seCache = keys;
        return keys;
    }

    /**
     * 从某个注册表持有类里枚举全部键。
     * <p>
     * 1.21.1 上这两个字段的类型都是原生 {@code Registry<T>}（直接用 {@code keySet()}）；
     * 若某些版本改成 {@code Supplier<Registry<T>>} 也能解一层，都不认就返回空表。
     */
    private static List<ResourceLocation> registryKeys(String holderClass) {
        if (!isLoaded()) return new ArrayList<>();
        try {
            Class<?> cls = Class.forName(holderClass);
            Object holder = cls.getField("REGISTRY").get(null);
            Object registry = holder instanceof Supplier<?> supplier ? supplier.get() : holder;
            if (registry == null) return new ArrayList<>();

            List<ResourceLocation> out = new ArrayList<>();
            Object keys = registry.getClass().getMethod("keySet").invoke(registry);
            if (keys instanceof java.util.Set<?> set) {
                for (Object key : set) {
                    if (key instanceof ResourceLocation rl) out.add(rl);
                }
            }
            out.sort(Comparator.comparing(ResourceLocation::toString));
            return out;
        } catch (Throwable t) {
            return new ArrayList<>();
        }
    }

    // ======================== 显示名 ========================

    /** SA 的本地化显示名（键 {@code slash_art.<ns>.<path>}，缺失时回退到可读化的 path）。 */
    public static String slashArtsName(ResourceLocation id) {
        return localized(SA_LANG_PREFIX, id);
    }

    /** SE 的本地化显示名（键 {@code se.<ns>.<path>}，缺失时回退到可读化的 path）。 */
    public static String specialEffectsName(ResourceLocation id) {
        return localized(SE_LANG_PREFIX, id);
    }

    private static String localized(String prefix, ResourceLocation id) {
        if (id == null) return "";
        String key = prefix + id.getNamespace() + "." + id.getPath();
        String translated = Component.translatable(key).getString();
        if (translated != null && !translated.isEmpty() && !translated.equals(key)) return translated;
        return id.getNamespace() + ": " + id.getPath().replace('_', ' ');
    }

    // ======================== 写入（仅服务端调用） ========================

    /**
     * 一次编辑请求。字段为 {@code null} 表示「不改动」；数值会被夹到 {@code >= 0}。
     *
     * @param kill   杀敌数
     * @param soul   耀魂数
     * @param refine 锻造数
     * @param sa     SA 的 id 字符串（{@code null} = 不改）
     * @param se     SE 的 id 字符串列表（{@code null} = 不改）
     */
    public record Edit(Integer kill, Integer soul, Integer refine, String sa, List<String> se) {}

    /**
     * 把编辑应用到刀上。成功返回 {@code true}。
     * <p>
     * 直接改 {@code BladeStateAccess.of()} 返回的状态对象 —— 它的 setter 会写回数据组件，
     * 所以改完<b>当场生效</b>（战斗逻辑读的就是这份数据），也会随物品同步与存档。
     */
    public static boolean apply(ItemStack stack, Edit edit) {
        if (edit == null || !isBlade(stack)) return false;
        ensureStatic();

        Object state = stateOf(stack);
        if (state == null) return false;

        try {
            boolean changed = false;
            if (edit.kill() != null && mSetKill != null) {
                mSetKill.invoke(state, Math.max(0, edit.kill()));
                changed = true;
            }
            if (edit.soul() != null && mSetSoul != null) {
                mSetSoul.invoke(state, Math.max(0, edit.soul()));
                changed = true;
            }
            if (edit.refine() != null && mSetRefine != null) {
                mSetRefine.invoke(state, Math.max(0, edit.refine()));
                changed = true;
            }
            if (edit.sa() != null && mSetSa != null) {
                ResourceLocation rl = ResourceLocation.tryParse(edit.sa());
                if (rl != null) {
                    mSetSa.invoke(state, rl);
                    changed = true;
                }
            }
            if (edit.se() != null && mSetSe != null) {
                mSetSe.invoke(state, toSpecialEffectsTag(edit.se()));
                changed = true;
            }
            return changed;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * SE 列表转成 {@code ListTag}（接口的 setter 收的是这个，元素是 id 字符串）。
     * <p>
     * 注意 1.21.1 的 getter 返回的是 {@code Collection<ResourceLocation>}，读写两侧的类型不对称 ——
     * 这是上游接口本身的样子，照它来即可。
     */
    private static ListTag toSpecialEffectsTag(List<String> ids) {
        ListTag list = new ListTag();
        for (String raw : ids) {
            ResourceLocation rl = ResourceLocation.tryParse(raw);
            if (rl != null) list.add(StringTag.valueOf(rl.toString()));
        }
        return list;
    }
}
