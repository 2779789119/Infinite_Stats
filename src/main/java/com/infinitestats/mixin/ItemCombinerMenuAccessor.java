package com.infinitestats.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ItemCombinerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 {@code ItemCombinerMenu#player}。
 * <p>
 * {@code player} 字段声明在父类 {@code ItemCombinerMenu} 上，而 {@code @Shadow} 只认目标类
 * 自身声明的成员（Mixin 注解处理器与运行期预处理器都会直接报
 * "Cannot find target for @Shadow field"），所以只能在字段所属的类上开一个访问器，
 * 再让 AnvilMenu 侧强转接口调用。
 */
@Mixin(ItemCombinerMenu.class)
public interface ItemCombinerMenuAccessor {

    @Accessor("player")
    Player infinitestats$getPlayer();
}
