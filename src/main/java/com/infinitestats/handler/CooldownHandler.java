package com.infinitestats.handler;

import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.StatType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 冷却缩减处理器 —— 缩短物品的冷却时间（末影珍珠、三叉戟、盾牌、各类技能物品等）。
 * <p>
 * 两条实现路径：
 * <ol>
 *   <li><b>未满额（&lt; 100%）</b>：物品冷却由 {@link ItemCooldowns} 的公开方法 {@code tick()}
 *       驱动（每次调用推进一次内部计时器并清理到期冷却），因此「每 tick 多调几次 tick()」
 *       就等于等比加速全部冷却 —— 不需要 Mixin，也不需要反射私有字段。</li>
 *   <li><b>满额（100%）</b>：语义是「零冷却」。用加速实现不了：额外流速为
 *       {@code r/(1-r)}，r = 1 时除零；而且无论推多快都只是"接近零"而非真的零。
 *       所以满额时改为直接清除玩家身上的冷却。</li>
 * </ol>
 * 倍率换算（未满额时）：属性语义是「冷却时间减少的百分比」，剩余冷却 = 原时长 × (1 - r)，
 * 所以计时器需要跑到 {@code 1/(1-r)} 倍速，即额外部分为 {@code r/(1-r)}。
 * <p>
 * 注意：客户端也持有自己的一份冷却（{@code LocalPlayer}），服务端加速不会同步过去，
 * 若不一起处理，客户端会认为物品还在冷却而拦住使用。因此 {@code ClientEventHandler}
 * 里调用同一套 {@link #apply} 静态逻辑，保证两端步调一致。
 */
public class CooldownHandler implements StatEffectHandler {

    /** 冷却缩减上限，100% 表示零冷却（此时走直接清除路径）。 */
    public static final float MAX_REDUCTION = 1.0f;

    @Override
    public String getId() {
        return "cooldown";
    }

    @Override
    public StatType[] getSupportedStats() {
        return new StatType[] { StatType.fromId("cooldown_reduction") };
    }

    // ======================== 两端共用的核心逻辑 ========================

    /**
     * 把「冷却时间减少百分比」换算成「计时器额外流速」。
     * <p>
     * 例：0.5 → 1.0（共 2 倍速，冷却减半）；0.8 → 4.0（共 5 倍速，冷却只剩 20%）。
     * 满额（≥ 1.0）不参与换算，由 {@link #apply} 走清除路径。
     */
    public static double extraRate(float reduction) {
        if (reduction <= 0 || reduction >= 1.0f) return 0;
        return reduction / (1.0 - reduction);
    }

    /**
     * 按累加器算出本 tick 需要额外推进的次数，并写回小数部分。
     * 服务端与客户端共用，保证两边推进步调一致、不会出现「服务端已可用、客户端还在转圈」。
     *
     * @return 本 tick 额外调用的 {@code tick()} 次数（0 表示不推进）
     */
    public static int consumeExtraTicks(PlayerStats stats, float reduction) {
        double accum = stats.getCooldownAccum() + extraRate(reduction);
        int extra = (int) accum;
        stats.setCooldownAccum(accum - extra);
        return extra;
    }

    /**
     * 对玩家应用冷却缩减（服务端与客户端都调用这里，避免两套逻辑漂移）。
     *
     * @param reduction 已按 {@link #MAX_REDUCTION} 截断的缩减率
     */
    public static void apply(Player player, PlayerStats stats, float reduction) {
        if (player == null) return;
        ItemCooldowns cooldowns = player.getCooldowns();
        if (cooldowns == null) return;

        if (reduction <= 0) {
            // 没加点时清空累加器，避免点数变动后残留导致突然跳变
            stats.setCooldownAccum(0);
            return;
        }

        if (reduction >= 1.0f) {
            stats.setCooldownAccum(0);
            clearVisibleCooldowns(cooldowns, player.getInventory());
            return;
        }

        int extra = consumeExtraTicks(stats, reduction);
        for (int i = 0; i < extra; i++) {
            cooldowns.tick();
        }
    }

    /**
     * 清除玩家身上的物品冷却。
     * <p>
     * {@link ItemCooldowns} 没有公开的「列出全部冷却」接口，所以这里遍历玩家自己的物品栏
     * （主栏 + 护甲 + 副手，约 41 格）。触发冷却的物品几乎必然在玩家身上，因此覆盖足够；
     * 代价是每 tick 固定几十次 {@code HashMap.remove}，可忽略。
     */
    private static void clearVisibleCooldowns(ItemCooldowns cooldowns, Inventory inventory) {
        removeAll(cooldowns, inventory.items);
        removeAll(cooldowns, inventory.armor);
        removeAll(cooldowns, inventory.offhand);
    }

    private static void removeAll(ItemCooldowns cooldowns, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) cooldowns.removeCooldown(stack.getItem());
        }
    }

    // ======================== Handler 接口 ========================

    @Override
    public void onTick(ServerPlayer player, PlayerStats stats, long tickCount) {
        float reduction = stats.getStatValue("cooldown_reduction");
        if (reduction > MAX_REDUCTION) reduction = MAX_REDUCTION;
        apply(player, stats, reduction);
    }

    @Override
    public void onLogin(ServerPlayer player, PlayerStats stats) {
    }

    @Override
    public void onRespawn(ServerPlayer player, PlayerStats stats) {
    }

    @Override
    public void onDimensionChange(ServerPlayer player, PlayerStats stats) {
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
