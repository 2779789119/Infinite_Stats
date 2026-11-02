package com.infinitestats.compat;

/**
 * 网络句柄：封装具体存储实现（RS 或 Beyond Dimensions）与其运行期网络对象。
 * 仅由 {@link NetworkIO} 构造与解释，UI/菜单侧无需关心底层是哪种存储。
 */
public final class NetworkHandle {
    /** 0 = Refined Storage；1 = Beyond Dimensions；2 = Applied Energistics 2（{MEStorage, IActionSource}）；3 = Sophisticated Backpacks（聚合的所有背包 IItemHandler）；4 = Tom's Storage（存储终端合并 IItemHandler）。 */
    public final int kind;
    public final Object net;

    public NetworkHandle(int kind, Object net) {
        this.kind = kind;
        this.net = net;
    }
}
