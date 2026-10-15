package com.infinitestats.mixin;

import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mixin 配置插件：只负责一件事 —— <b>针对第三方模组的 Mixin，目标模组不在场时干脆别应用</b>。
 * <p>
 * 为什么需要它：{@code infinitestats.mixins.json} 是 {@code "required": true} 的，而第三方联动
 * Mixin（{@link CreateKineticNetworkMixin} / {@link CreateKineticBlockEntityMixin}）的目标类在
 * {@code com.simibubi.create.*} 下。对应模组没装时这些类根本不存在，Mixin 会在应用阶段抛
 * {@code ClassMetadataNotFoundException}，在 required 配置里这会直接让游戏起不来 ——
 * 而「本模组无需任何前置」是明确承诺，绝不能让一个可选联动把整包拖崩。
 * <p>
 * 判定按<b>目标类名前缀</b>而不是逐个 Mixin 类名：以后再加注入点（同一个模组或新模组）
 * 只要目标类还在下面这些前缀下，就自动被兜住，不用回来改这里。
 * <p>
 * 用 {@link LoadingModList}（而不是 {@code ModList}）：插件在 Mixin 应用阶段就会被调用，
 * 那会儿模组列表还没建好，只有 loading 层的这份可用。
 * <p>
 * <b>1.21.1 适配</b>：{@code getRefMapperConfig()} 返回 {@code null} —— NeoForge 用 official 映射，
 * 运行时不重映射成员名，不需要也不生成 refmap。
 */
public class InfiniteStatsMixinPlugin implements IMixinConfigPlugin {

    /**
     * 「目标类包前缀 → 该模组的 mod id」。
     * <p>
     * 只列出<b>外部模组</b>的包前缀；原版与 NeoForge 的注入点不在表里，一律照常应用。
     */
    private static final Map<String, String> GATED_PACKAGES = Map.of(
            "com.simibubi.create.", "create",
            "com.atsuishio.superbwarfare.", "superbwarfare"
    );

    @Override
    public void onLoad(String mixinPackage) {
        // 无需初始化
    }

    @Override
    public String getRefMapperConfig() {
        return null; // NeoForge 不需要 refmap
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (targetClassName != null) {
            for (Map.Entry<String, String> entry : GATED_PACKAGES.entrySet()) {
                if (targetClassName.startsWith(entry.getKey())) {
                    return isModLoaded(entry.getValue());
                }
            }
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // 不做额外筛选
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // 无需处理
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // 无需处理
    }

    /** 该模组是否在本次启动的模组列表里（此时 ModList 还没建好，只能查 loading 层）。 */
    private static boolean isModLoaded(String modId) {
        try {
            LoadingModList list = LoadingModList.get();
            return list != null && list.getModFileById(modId) != null;
        } catch (Throwable t) {
            // 判定不出来就当作没装：宁可联动不生效，也不能让启动失败
            return false;
        }
    }
}
