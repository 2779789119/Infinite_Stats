package com.tacz.guns.item;

/**
 * <b>TACZ 的编译期存根（stub）—— 运行时永远不会用到这个类。</b>
 * <p>
 * 为什么需要它：Mixin 注解处理器要求 {@code @Mixin} 的目标类必须在<b>编译期</b>类路径上，
 * 否则直接报 {@code Mixin target ... could not be found} 让构建失败；而 TACZ 对本模组来说只是
 * 「装了才生效」的可选运行时联动 —— 把它的 jar（几十 MB）塞进仓库既不合适、又会随对方升级变味。
 * 于是这里只留一个空壳：把本模组<b>注入 / 反射用到的成员</b>按真实签名列出来，产物只进编译期
 * 类路径（{@code main.compileClasspath}），既让注解处理器能校验注入点名字，又不会被打进 jar。
 * <p>
 * 维护约定：<b>签名必须与 TACZ 1.1.x 的实际实现一致</b>（已对着
 * {@code tacz-1.20.1-1.1.8-hotfix2.jar} 逐字节核对过成员名）。改动这里的任何签名前，
 * 先确认真实 jar 里的签名，否则注解处理器会给出错误的「通过」。
 */
public class ModernKineticGunScriptAPI {

    /** 当前射手（注入点靠它拿到玩家，从而判断属性开关）。 */
    public net.minecraft.world.entity.LivingEntity getShooter() {
        throw new UnsupportedOperationException("compile-time stub");
    }

    /** 击发时扣弹；无限子弹注入点把它改成「直接返回成功」。 */
    public boolean reduceAmmoOnce() {
        throw new UnsupportedOperationException("compile-time stub");
    }

    /** 从弹匣取弹（拉栓供弹 / 换弹收尾推弹进枪膛）；无限子弹注入点把它变成空操作。 */
    public int removeAmmoFromMagazine(int amount) {
        throw new UnsupportedOperationException("compile-time stub");
    }

    /** 击发时的热量累积（默认实现）；「零热量」注入点把它整段跳过。 */
    public void handleShootHeat() {
        throw new UnsupportedOperationException("compile-time stub");
    }

    /** 往枪上写热量（枪械脚本走这个包装方法）；「零热量」注入点把它整段跳过。 */
    public void setHeatAmount(float amount) {
        throw new UnsupportedOperationException("compile-time stub");
    }

    /** 弹匣还差多少发才满（本模组暂未注入，列出以便对照签名）。 */
    public int getNeededAmmoAmount() {
        throw new UnsupportedOperationException("compile-time stub");
    }

    /** 往弹匣压弹（本模组暂未注入，列出以便对照签名）。 */
    public int putAmmoInMagazine(int amount) {
        throw new UnsupportedOperationException("compile-time stub");
    }

    /** 弹匣上限（含扩容弹匣等配件影响）；{@code TaczCompat} 反射调用它来补满弹匣。 */
    public int getMaxAmmoCount() {
        throw new UnsupportedOperationException("compile-time stub");
    }

    /** 设置当前枪械物品；{@code TaczCompat} 反射调用它来初始化上面的上限查询。 */
    public void setItemStack(net.minecraft.world.item.ItemStack itemStack) {
        throw new UnsupportedOperationException("compile-time stub");
    }
}
