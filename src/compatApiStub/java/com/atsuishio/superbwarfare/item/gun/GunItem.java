package com.atsuishio.superbwarfare.item.gun;

import com.atsuishio.superbwarfare.data.gun.ShootParameters;

/**
 * <b>Superb Warfare（卓越前线）的编译期存根 —— 运行时永远不会用到这个类。</b>
 * <p>
 * 用途与维护约定同上。这里只用于兜「绕过 {@code GunData.shoot(...)} 直接调 item」的路径 ——
 * 主收口在 {@code GunData} 那边（不挑枪型，连自己覆写了 {@code shoot} 且不回调 {@code super}
 * 的特殊枪也覆盖）。
 */
public class GunItem {

    public void shoot(ShootParameters params) {
        throw new UnsupportedOperationException("compile-time stub");
    }

    public void afterShoot(ShootParameters params) {
        throw new UnsupportedOperationException("compile-time stub");
    }
}
