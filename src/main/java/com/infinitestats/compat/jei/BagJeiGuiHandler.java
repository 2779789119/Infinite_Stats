package com.infinitestats.compat.jei;

import com.infinitestats.client.ItemBagScreen;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.gui.handlers.IScreenHandler;
import net.minecraft.client.gui.screens.Screen;

import javax.annotation.Nullable;

/**
 * 让 JEI 在「无尽背包」界面旁边画物品列表。
 *
 * <p>JEI 默认只认 {@code AbstractContainerScreen}：我们的背包是自绘 Screen（没有容器菜单 / 槽位），
 * 不给它一份 {@link IGuiProperties}，右侧那一列物品就不会出现。
 * 这里把面板当前的位置与尺寸（已含玩家缩放 / 拖动）报给 JEI，它就会把列表摆到面板右边，
 * 玩家换分辨率、Ctrl+滚轮缩放面板时列表也跟着走。
 *
 * <p>注册入口在 {@link InfiniteStatsJeiPlugin#registerGuiHandlers}。
 */
public final class BagJeiGuiHandler implements IScreenHandler<ItemBagScreen> {

    @Nullable
    @Override
    public IGuiProperties apply(ItemBagScreen screen) {
        if (screen == null) return null;
        return new Props(screen);
    }

    private static final class Props implements IGuiProperties {
        private final Class<? extends Screen> screenClass;
        private final int guiLeft;
        private final int guiTop;
        private final int guiWidth;
        private final int guiHeight;
        private final int screenWidth;
        private final int screenHeight;

        private Props(ItemBagScreen screen) {
            this.screenClass = screen.getClass();
            // 面板可以被玩家拖到屏幕边缘外，JEI 那边按「可见区域」算列表位置更稳
            this.guiLeft = Math.max(0, screen.bagGuiLeft());
            this.guiTop = Math.max(0, screen.bagGuiTop());
            this.guiWidth = Math.min(screen.bagGuiWidth(), screen.width);
            this.guiHeight = Math.min(screen.bagGuiHeight(), screen.height);
            this.screenWidth = screen.width;
            this.screenHeight = screen.height;
        }

        @Override
        public Class<? extends Screen> getScreenClass() {
            return screenClass;
        }

        @Override
        public int getGuiLeft() {
            return guiLeft;
        }

        @Override
        public int getGuiTop() {
            return guiTop;
        }

        @Override
        public int getGuiXSize() {
            return guiWidth;
        }

        @Override
        public int getGuiYSize() {
            return guiHeight;
        }

        @Override
        public int getScreenWidth() {
            return screenWidth;
        }

        @Override
        public int getScreenHeight() {
            return screenHeight;
        }
    }
}
