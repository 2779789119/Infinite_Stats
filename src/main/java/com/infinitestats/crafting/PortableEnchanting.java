package com.infinitestats.crafting;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

/**
 * 随身「进阶高级附魔台」的打开入口。
 * <p>
 * 与其它随身站点不同，这个台子是<b>自建菜单 + 自建界面</b>（见 {@link PortableInfuserMenu}
 * 与客户端 {@code PortableInfuserScreen}）：原版附魔台是「三档随机附魔」，而进阶高级附魔台要
 * 「自己挑附魔」，模型完全不同，没法复用原版菜单类型。
 */
public final class PortableEnchanting {

    /** 属性 id（与 {@code StatType} / 界面 / 数据包共用同一份字符串）。 */
    public static final String STAT_ID = "portable_enchanting";

    private PortableEnchanting() {}

    /** 为玩家打开随身进阶高级附魔台（调用方需先校验 portable_enchanting 开关）。 */
    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (windowId, inv, p) -> new PortableInfuserMenu(windowId, inv),
                Component.translatable("container.enchant")));
    }
}
