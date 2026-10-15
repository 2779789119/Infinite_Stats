package com.infinitestats.client;

import com.infinitestats.furnace.BulkStorageMenu;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.Slot;

final class BulkCountRenderer {
    private BulkCountRenderer() {}

    static java.util.List<net.minecraft.network.chat.Component> withAmount(
            java.util.List<net.minecraft.network.chat.Component> original, BulkStorageMenu menu, Slot hovered) {
        if (hovered == null || hovered.index < 0 || hovered.index >= menu.getBulkSlotCount()) return original;
        var tooltip = new java.util.ArrayList<>(original);
        tooltip.add(net.minecraft.network.chat.Component.translatable(
                "gui.infinitestats.furnace.amount", menu.getBulkAmount(hovered.index)));
        tooltip.add(net.minecraft.network.chat.Component.translatable(
                "gui.infinitestats.furnace.bulk_tip").withStyle(net.minecraft.ChatFormatting.GRAY));
        return tooltip;
    }

    static void render(GuiGraphics graphics, Font font, BulkStorageMenu menu) {
        for (int i = 0; i < menu.getBulkSlotCount(); i++) {
            long amount = menu.getBulkAmount(i);
            Slot slot = menu.getSlot(i);
            if (amount <= 1 || !slot.hasItem()) continue;
            String text = amount >= 1_000_000_000 ? amount / 1_000_000_000 + "B"
                    : amount >= 1_000_000 ? amount / 1_000_000 + "M"
                    : amount >= 1_000 ? amount / 1_000 + "K" : Long.toString(amount);
            // 物品是以更高 Z 值渲染并写入深度缓冲的 3D 几何，普通 drawString（Z=0）与其位置重叠时
            // 会被物品挡住（表现为"数量数字被物品压住"）。这里把文字抬到物品之上（Z=300），
            // 与原版堆叠数量的观感保持一致。
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, 300.0F);
            graphics.drawString(font, text, slot.x + 17 - font.width(text), slot.y + 9, 0xffffff, true);
            graphics.pose().popPose();
        }
    }
}
