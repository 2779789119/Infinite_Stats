package com.infinitestats.client;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * 「现在有没有开光影」的探测 —— 纯反射，没装光影模组时恒为 {@code false}。
 * <p>
 * 矿透为什么需要知道这件事：光影（Oculus / Iris、以及 OptiFine 的 shaders）会把整个世界先画进
 * 自己的缓冲，<b>等 {@code LevelRenderer#renderLevel} 收尾时再整屏合成一次</b>。也就是说
 * 关卡渲染阶段（{@code RenderLevelStageEvent}）里画的任何东西都会被那次合成整屏覆盖 ——
 * 换阶段也救不回来（{@code AFTER_LEVEL} 同样在合成之前），只能改到合成之后、
 * 也就是 HUD 阶段再画（见 {@link OreVisionOverlay#onRenderGui}）。
 * <p>
 * 这里只问「光影包有没有启用」，不问「光影模组有没有装」：装了但没启用光影包时，
 * Iris 走的是原版渲染路径，世界阶段的画法照旧有效，没必要绕道。
 * <p>
 * 反射而不是编译期依赖：这两个都是「装了才生效」的可选模组，塞进编译类路径只为一次布尔查询
 * 并不划算。任何一步失败都退化为 {@code false}，即维持原来的画法 —— 宁可不切换，也不要在
 * 没有光影的环境里画到 HUD 上去。
 */
final class ShaderCompat {

    /** Iris / Oculus（1.18+ 起的正式包名）。 */
    private static final String IRIS_API = "net.irisshaders.iris.api.v0.IrisApi";
    /** 更早的 Iris 包名（部分第三方分支还在用）。 */
    private static final String IRIS_API_LEGACY = "net.coderbot.iris.api.v0.IrisApi";
    /** OptiFine 的全局开关：它的光影走的是另一套内部实现。 */
    private static final String OPTIFINE_CONFIG = "net.optifine.Config";

    private static boolean resolved;

    private static Method irisGetInstance;
    private static Method irisPackInUse;
    private static Method optifineShaders;

    private ShaderCompat() {}

    /** 当前是否真的在跑光影。每帧最多问一次，取不到一律当作「没开」。 */
    static boolean areShadersActive() {
        resolve();

        Method getInstance = irisGetInstance;
        Method packInUse = irisPackInUse;
        if (getInstance != null && packInUse != null) {
            try {
                Object api = getInstance.invoke(null);
                if (api != null) return Boolean.TRUE.equals(packInUse.invoke(api));
            } catch (Throwable ignored) {
                // 对方改了 API：永久短路这条路，别每帧都抛一次异常
                irisGetInstance = null;
                irisPackInUse = null;
            }
        }

        Method shaders = optifineShaders;
        if (shaders != null) {
            try {
                return Boolean.TRUE.equals(shaders.invoke(null));
            } catch (Throwable ignored) {
                optifineShaders = null;
            }
        }
        return false;
    }

    private static void resolve() {
        if (resolved) return;
        resolved = true;

        Method getInstance = staticNoArg(IRIS_API, "getInstance");
        if (getInstance == null) getInstance = staticNoArg(IRIS_API_LEGACY, "getInstance");
        if (getInstance != null) {
            // 返回类型就是 IrisApi 接口本身，实例方法从它的返回类型上取
            Method packInUse = noArg(getInstance.getReturnType(), "isShaderPackInUse");
            if (packInUse != null && packInUse.getReturnType() == boolean.class) {
                irisGetInstance = getInstance;
                irisPackInUse = packInUse;
            }
        }

        Method shaders = staticNoArg(OPTIFINE_CONFIG, "isShaders");
        if (shaders != null && shaders.getReturnType() == boolean.class) {
            optifineShaders = shaders;
        }
    }

    private static Method staticNoArg(String className, String methodName) {
        try {
            Method method = Class.forName(className).getMethod(methodName);
            return Modifier.isStatic(method.getModifiers()) ? method : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method noArg(Class<?> type, String methodName) {
        try {
            return type.getMethod(methodName);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
