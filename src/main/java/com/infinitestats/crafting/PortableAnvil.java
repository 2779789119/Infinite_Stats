package com.infinitestats.crafting;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraftforge.network.NetworkHooks;

/**
 * 随身铁砧：把原版 {@link AnvilMenu} 直接摆到玩家面前，不依赖世界里的铁砧方块。
 * <p>
 * 关键在于把 {@link ContainerLevelAccess} 传成 {@link ContainerLevelAccess#NULL}：
 * 铁砧的方块损伤、砸落铁砧伤害、音效与统计这些"世界侧"行为全都写在
 * {@code access.execute(...)} 回调里，而 NULL 实现是空操作 —— 于是随身铁砧天然
 * 不会损耗、不在世界里留痕。经验扣减在 {@code AnvilMenu#onTake} 里位于该回调之外，
 * 因此照常生效，与站在真铁砧前一致。
 * <p>
 * 菜单类型沿用原版 {@code MenuType.ANVIL}，客户端会因此自动套用原版 {@code AnvilScreen}，
 * 不需要自建菜单类型与界面。
 */
public final class PortableAnvil {

    private PortableAnvil() {}

    /** 为玩家打开随身铁砧界面（调用方需先校验属性开关）。 */
    public static void open(ServerPlayer player) {
        NetworkHooks.openScreen(player, new SimpleMenuProvider(
                (windowId, inv, p) -> new PortableAnvilMenu(windowId, inv),
                Component.translatable("container.repair")));
    }
}
