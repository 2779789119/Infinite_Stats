package com.infinitestats.client;

/**
 * 破坏性操作的「二次点击确认」：第一次点击只进入确认态（按钮文字变成「确认？」），
 * {@value #WINDOW_MS} 毫秒内再点一次才真正执行。
 *
 * <h2>为什么不做弹窗</h2>
 * 弹窗要暂停 / 抢焦点，连着删几项时非常烦；超时自动回退的二次点击既能拦住手滑，
 * 又不会拖慢连续操作。成就管理器从一开始就是这么做的，这里抽出来给其它界面共用。
 *
 * <h2>用法</h2>
 * <pre>{@code
 * if (!confirm.confirm("delete:" + id)) return;   // 第一次：进入确认态
 * doDangerousThing();                             // 第二次：真的执行
 * }</pre>
 * 按钮文字用 {@link #isConfirming(String)} 判断要不要换成「确认？」。
 */
final class ConfirmAction {

    /** 确认窗口（毫秒）。 */
    static final long WINDOW_MS = 2500L;

    private String pendingKey;
    private long until;

    /**
     * 请求执行一次「危险操作」。
     *
     * @return {@code true} 表示这是窗口内的第二次点击，可以真正执行；
     *         {@code false} 表示刚进入（或重新进入）确认态，调用方应直接返回
     */
    boolean confirm(String key) {
        long now = System.currentTimeMillis();
        if (key.equals(pendingKey) && now <= until) {
            reset();
            return true;
        }
        pendingKey = key;
        until = now + WINDOW_MS;
        return false;
    }

    /** 当前是否正等着同一个 key 的第二次点击。 */
    boolean isConfirming(String key) {
        return key.equals(pendingKey) && System.currentTimeMillis() <= until;
    }

    /** 是否处于任何确认态（切换界面 / 改筛选时用来清掉）。 */
    boolean isPending() {
        return pendingKey != null && System.currentTimeMillis() <= until;
    }

    void reset() {
        pendingKey = null;
        until = 0;
    }
}
