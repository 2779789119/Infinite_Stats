package com.infinitestats.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.IForgeRegistry;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 拔刀剑（SlashBlade: Resharped）联动 —— 让物品编辑器可以改刀的各项成长数值。
 * <p>
 * 覆盖玩家在编辑器里能改的五项：<b>杀敌数</b>（{@code killCount}）、<b>耀魂数</b>（{@code proudSoul}）、
 * <b>锻造数</b>（{@code RepairCounter}）、<b>SA</b>（{@code SpecialAttackType}，特殊攻击 / 剑技）、
 * <b>SE</b>（{@code SpecialEffects}，特殊效果列表）。
 * <p>
 * <b>数据放在哪</b>（对着 1.20.1 重铸版源码核对）：刀的整套状态由 Forge 能力
 * {@code ItemSlashBlade.BLADESTATE}（{@code ISlashBladeState}）承载，序列化后写在物品 NBT 的
 * <b>{@code "bladeState"}</b> 子标签下 ——
 * 即 {@code stack.getTag().getCompound("bladeState")}，字段名 {@code killCount} / {@code proudSoul} /
 * {@code RepairCounter} / {@code SpecialAttackType} / {@code SpecialEffects}。客户端能读到它，
 * 是因为 {@code ItemSlashBlade#getShareTag} 会把 {@code serializeNBT()} 的结果塞进同步标签。
 * <p>
 * <b>为什么写入必须走能力而不是直接改 NBT</b>：战斗逻辑（放 SA、结算 SE）读的是<b>内存里的能力对象</b>，
 * 而不是每次现读 NBT。只写 NBT 的话数值在重登后才会「生效」，当场改完不会立刻起作用。
 * 所以写入流程是：{@code getCapability(BLADESTATE)} → 调它的 setter → 再 {@code serializeNBT()} 回写 NBT
 * （回写是为了存档与同步；{@code getShareTag} 也依赖这份 NBT）。若能力取不到（版本差异 / 极端环境），
 * 退化为「直接改 NBT」的兜底，至少保证数据落盘。
 * <p>
 * <b>全程反射</b>（与 {@code TaczCompat} / {@code SuperbWarfareCompat} / {@code GoetyCompat} 同一套思路）：
 * 拔刀剑只是可选运行时联动，本模组不声明对它的编译期依赖。没装时所有入口直接短路、零开销；
 * 版本对不上导致某个锚点取不到时只让编辑器这一块退化为不可用，绝不影响其余功能与启动。
 * <p>
 * 唯一的编译期外部类型是 {@code net.minecraftforge.registries.IForgeRegistry}（Forge 自带），
 * 用它把 {@code SlashArtsRegistry.REGISTRY} / {@code SpecialEffectsRegistry.REGISTRY} 这两个
 * 「{@code Supplier<IForgeRegistry<T>>}」枚举成候选列表；{@code T}（{@code SlashArts} / {@code SpecialEffect}）
 * 只在运行时存在，因此不出现任何具体类型引用。
 */
public final class SlashBladeCompat {

    /** 拔刀剑的 mod id（{@code mods.flammpfeil.slashblade.SlashBlade#MODID}）。 */
    public static final String MOD_ID = "slashblade";

    /** 刀状态在物品 NBT 里的根键。 */
    private static final String NBT_ROOT = "bladeState";

    // 状态字段名（1.20.1 重铸版 ISlashBladeState#serializeNBT 的键）
    private static final String K_KILL = "killCount";
    private static final String K_SOUL = "proudSoul";
    private static final String K_REFINE = "RepairCounter";
    private static final String K_SA = "SpecialAttackType";
    private static final String K_SE = "SpecialEffects";

    private static final String ITEM_CLASS = "mods.flammpfeil.slashblade.item.ItemSlashBlade";
    private static final String STATE_IFACE = "mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState";
    private static final String SA_REGISTRY = "mods.flammpfeil.slashblade.registry.SlashArtsRegistry";
    private static final String SE_REGISTRY = "mods.flammpfeil.slashblade.registry.SpecialEffectsRegistry";

    /** 翻译键前缀：{@code Util.makeDescriptionId("slash_art"/"se", key)} 的产物。 */
    private static final String SA_LANG_PREFIX = "slash_art.";
    private static final String SE_LANG_PREFIX = "se.";

    // ======================== 反射句柄（懒解析 + 失败永久短路） ========================

    private static volatile boolean staticResolved;
    private static Class<?> itemClass;
    private static Capability<?> bladeStateCapability;
    private static Method mSetKill, mSetSoul, mSetRefine, mSetSa, mSetSe, mSerialize;

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
                Object cap = itemClass.getField("BLADESTATE").get(null);
                bladeStateCapability = cap instanceof Capability<?> c ? c : null;
            } catch (Throwable t) {
                bladeStateCapability = null;
            }
            try {
                Class<?> iface = Class.forName(STATE_IFACE);
                mSetKill = iface.getMethod("setKillCount", int.class);
                mSetSoul = iface.getMethod("setProudSoulCount", int.class);
                mSetRefine = iface.getMethod("setRefine", int.class);
                mSetSa = iface.getMethod("setSlashArtsKey", ResourceLocation.class);
                mSetSe = iface.getMethod("setSpecialEffects", ListTag.class);
                mSerialize = iface.getMethod("serializeNBT");
            } catch (Throwable t) {
                mSetKill = mSetSoul = mSetRefine = mSetSa = mSetSe = mSerialize = null;
            }
        }
    }

    // ======================== 读取（客户端可安全调用） ========================

    /** 取刀状态 NBT 子标签，不存在返回 {@code null}。 */
    private static CompoundTag stateTag(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(NBT_ROOT, Tag.TAG_COMPOUND)) return null;
        return tag.getCompound(NBT_ROOT);
    }

    public static int killCount(ItemStack stack) {
        CompoundTag t = stateTag(stack);
        return t == null ? 0 : Math.max(0, t.getInt(K_KILL));
    }

    public static int proudSoul(ItemStack stack) {
        CompoundTag t = stateTag(stack);
        return t == null ? 0 : Math.max(0, t.getInt(K_SOUL));
    }

    public static int refine(ItemStack stack) {
        CompoundTag t = stateTag(stack);
        return t == null ? 0 : Math.max(0, t.getInt(K_REFINE));
    }

    /** 当前 SA 的 id 字符串；无 / 无法解析时返回空串。 */
    public static String slashArts(ItemStack stack) {
        CompoundTag t = stateTag(stack);
        if (t == null) return "";
        String s = t.getString(K_SA);
        return s == null ? "" : s;
    }

    /** 当前 SE 列表（id 字符串，按写入顺序）。 */
    public static List<String> specialEffects(ItemStack stack) {
        CompoundTag t = stateTag(stack);
        if (t == null) return new ArrayList<>();
        ListTag list = t.getList(K_SE, Tag.TAG_STRING);
        List<String> out = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) out.add(list.getString(i));
        return out;
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

    private static List<ResourceLocation> registryKeys(String holderClass) {
        if (!isLoaded()) return new ArrayList<>();
        try {
            Class<?> cls = Class.forName(holderClass);
            Object holder = cls.getField("REGISTRY").get(null);
            if (!(holder instanceof java.util.function.Supplier<?> supplier)) return new ArrayList<>();
            Object registry = supplier.get();
            if (!(registry instanceof IForgeRegistry<?> forgeRegistry)) return new ArrayList<>();
            List<ResourceLocation> out = new ArrayList<>(forgeRegistry.getKeys());
            out.sort(Comparator.comparing(rl -> rl.toString()));
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
     * 先更新<b>能力内存态</b>（战斗逻辑读它），再 {@code serializeNBT()} 回写 NBT（存档 / 同步用）；
     * 能力不可用时退化为直接写 NBT。
     */
    public static boolean apply(ItemStack stack, Edit edit) {
        if (edit == null || !isBlade(stack)) return false;
        ensureStatic();

        // 1) 优先走能力：先把内存态改掉，SA / SE 才会「当场」生效
        Object state = stateOf(stack);
        if (state != null && mSerialize != null) {
            try {
                if (edit.kill() != null && mSetKill != null) mSetKill.invoke(state, Math.max(0, edit.kill()));
                if (edit.soul() != null && mSetSoul != null) mSetSoul.invoke(state, Math.max(0, edit.soul()));
                if (edit.refine() != null && mSetRefine != null) mSetRefine.invoke(state, Math.max(0, edit.refine()));
                if (edit.sa() != null && mSetSa != null) {
                    ResourceLocation rl = ResourceLocation.tryParse(edit.sa());
                    if (rl != null) mSetSa.invoke(state, rl);
                }
                if (edit.se() != null && mSetSe != null) mSetSe.invoke(state, toSpecialEffectsTag(edit.se()));

                Object serialized = mSerialize.invoke(state);
                if (serialized instanceof CompoundTag tag) {
                    stack.getOrCreateTag().put(NBT_ROOT, tag);
                    return true;
                }
            } catch (Throwable ignored) {
                // 落到下面的 NBT 兜底
            }
        }

        // 2) 兜底：直接改 NBT（能力缺失 / 反射失败时至少让数据落盘）
        return writeNbt(stack, edit);
    }

    private static boolean writeNbt(ItemStack stack, Edit edit) {
        try {
            CompoundTag root = stack.getOrCreateTagElement(NBT_ROOT);
            if (edit.kill() != null) root.putInt(K_KILL, Math.max(0, edit.kill()));
            if (edit.soul() != null) root.putInt(K_SOUL, Math.max(0, edit.soul()));
            if (edit.refine() != null) root.putInt(K_REFINE, Math.max(0, edit.refine()));
            if (edit.sa() != null) {
                ResourceLocation rl = ResourceLocation.tryParse(edit.sa());
                if (rl != null) root.putString(K_SA, rl.toString());
            }
            if (edit.se() != null) root.put(K_SE, toSpecialEffectsTag(edit.se()));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static ListTag toSpecialEffectsTag(List<String> ids) {
        ListTag list = new ListTag();
        for (String raw : ids) {
            ResourceLocation rl = ResourceLocation.tryParse(raw);
            if (rl != null) list.add(StringTag.valueOf(rl.toString()));
        }
        return list;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object stateOf(ItemStack stack) {
        ensureStatic();
        Capability<?> cap = bladeStateCapability;
        if (cap == null) return null;
        try {
            LazyOptional<?> opt = stack.getCapability((Capability) cap);
            return ((LazyOptional) opt).orElse(null);
        } catch (Throwable t) {
            return null;
        }
    }
}
