package com.infinitestats.network;

import java.util.UUID;

/**
 * 一条「已激活的传送石碑」记录 —— 服务端从传送石碑（Waystones）读出后下发给客户端，
 * 由 {@code client/WaypointScreen} 显示、点击时按 {@link #uid} 回传请求传送。
 * <p>
 * 刻意只带显示与传送所需的最小字段（名称 / 维度 / 坐标），<b>不引用传送石碑的任何类型</b>：
 * 没装传送石碑时本类照样能加载（列表为空）。
 */
public final class WaystoneEntry {

    /** 石碑 UUID —— 传送请求用它回指服务端的石碑。 */
    public final UUID uid;

    /** 石碑名称（玩家自己起的名字或自动生成的名字）。 */
    public final String name;

    /** 维度 id，如 {@code minecraft:overworld}。 */
    public final String dimension;

    public final double x;
    public final double y;
    public final double z;

    public WaystoneEntry(UUID uid, String name, String dimension, double x, double y, double z) {
        this.uid = uid;
        this.name = name;
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
    }
}
