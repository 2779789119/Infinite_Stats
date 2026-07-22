package com.infinitestats.stats;

import net.minecraft.nbt.CompoundTag;

/**
 * 定点传送的传送点数据
 */
public class Waypoint {
    public final String dimension;
    public final double x, y, z;
    public final float yaw, pitch;

    public Waypoint(String dimension, double x, double y, double z, float yaw, float pitch) {
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("dim", dimension);
        tag.putDouble("x", x);
        tag.putDouble("y", y);
        tag.putDouble("z", z);
        tag.putFloat("yaw", yaw);
        tag.putFloat("pitch", pitch);
        return tag;
    }

    public static Waypoint fromTag(CompoundTag tag) {
        return new Waypoint(
                tag.getString("dim"),
                tag.getDouble("x"),
                tag.getDouble("y"),
                tag.getDouble("z"),
                tag.getFloat("yaw"),
                tag.getFloat("pitch")
        );
    }
}
