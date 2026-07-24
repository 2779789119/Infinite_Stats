package com.infinitestats.compat;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * ProjectE（等价交换）联动桥接（反射调用，运行时才需 ProjectE 加载，无需编译期依赖）。
 *
 * 提供「自动学习」能力：玩家获得物品（拾取 / 合成等）时，自动把物品加入 ProjectE 的
 * 转化知识库（等价于在转化桌 / 知识板中学习过），从而「不需要把物品卖入转化桌也会被学习」。
 *
 * 所有 ProjectE 调用均通过反射完成；ProjectE 未加载或反射失败时安全降级（什么都不做）。
 *
 * 依赖的 ProjectE API（1.20.1）：
 *   - moze_intel.projecte.api.ProjectEAPI.getTransmutationProxy() -> ITransmutationProxy
 *   - ITransmutationProxy.getKnowledgeProviderFor(UUID) -> IKnowledgeProvider
 *   - IKnowledgeProvider.addKnowledge(ItemStack) : boolean
 *   - IKnowledgeProvider.sync(Player) : void
 */
public final class ProjectEBridge {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectEBridge.class);
    private static final String PE_MODID = "projecte";

    private static Boolean peLoaded;
    private static boolean initialized = false;
    private static Object transProxyInstance;
    private static Method getKnowledgeProviderFor;
    private static Method addKnowledge;
    private static Method sync;

    private ProjectEBridge() {
    }

    /** 等价交换（ProjectE）是否已加载并成功初始化反射桥接。 */
    public static boolean isProjectELoaded() {
        return ensureInit();
    }

    private static boolean ensureInit() {
        if (initialized) return peLoaded != null && peLoaded;
        initialized = true;
        peLoaded = ModList.get().isLoaded(PE_MODID);
        if (!peLoaded) return false;
        try {
            Class<?> apiClass = Class.forName("moze_intel.projecte.api.ProjectEAPI");
            Method getTransProxy = apiClass.getMethod("getTransmutationProxy");
            transProxyInstance = getTransProxy.invoke(null);
            if (transProxyInstance == null) {
                peLoaded = false;
                return false;
            }
            Class<?> transProxyClz = transProxyInstance.getClass();
            getKnowledgeProviderFor = transProxyClz.getMethod("getKnowledgeProviderFor", UUID.class);
            Class<?> kpClz = getKnowledgeProviderFor.getReturnType();
            addKnowledge = kpClz.getMethod("addKnowledge", ItemStack.class);
            sync = findSync(kpClz);
            LOGGER.info("[ProjectEBridge] 检测到等价交换（ProjectE），自动学习功能已可用。");
            return true;
        } catch (Throwable t) {
            LOGGER.error("[ProjectEBridge] 初始化 ProjectE API 反射失败，自动学习功能不可用。", t);
            peLoaded = false;
            return false;
        }
    }

    private static Method findSync(Class<?> kpClz) {
        // 1.20.1：sync(Player)；为兼容不同版本做兜底遍历查找
        for (Method m : kpClz.getMethods()) {
            if (!"sync".equals(m.getName())) continue;
            Class<?>[] params = m.getParameterTypes();
            if (params.length == 1 && Player.class.isAssignableFrom(params[0])) {
                return m;
            }
        }
        return null;
    }

    /**
     * 把单个物品加入玩家的 ProjectE 知识库（自动学习）。
     * 返回是否真正新增了知识（false 表示本来已学会、调用失败或未加载 ProjectE）。
     */
    public static boolean learn(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) return false;
        if (player.level().isClientSide) return false;
        if (!ensureInit()) return false;
        try {
            Object provider = getKnowledgeProviderFor.invoke(transProxyInstance, player.getUUID());
            if (provider == null) return false;
            boolean added = (Boolean) addKnowledge.invoke(provider, stack.copy());
            if (added && sync != null) {
                try {
                    sync.invoke(provider, player);
                } catch (Throwable ignored) {
                    // 同步失败不影响本地知识写入
                }
            }
            return added;
        } catch (Throwable t) {
            return false;
        }
    }
}
