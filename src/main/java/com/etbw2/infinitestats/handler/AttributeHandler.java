package com.etbw2.infinitestats.handler;

import com.etbw2.infinitestats.stats.PlayerStats;
import com.etbw2.infinitestats.stats.StatType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.UUID;

/**
 * 属性修改处理器
 * 处理所有基于原版Attribute的属性修改
 */
public class AttributeHandler implements StatEffectHandler {

    // 属性修改器UUID缓存
    private static final UUID[] MODIFIER_UUIDS = new UUID[StatType.ALL_STATS.length];

    static {
        for (int i = 0; i < StatType.ALL_STATS.length; i++) {
            MODIFIER_UUIDS[i] = UUID.nameUUIDFromBytes(
                    ("infinitestats:" + StatType.ALL_STATS[i].getId()).getBytes()
            );
        }
    }

    @Override
    public String getId() {
        return "attribute";
    }

    @Override
    public StatType[] getSupportedStats() {
        return StatType.getAttributeStats();
    }

    @Override
    public void onTick(ServerPlayer player, PlayerStats stats, long tickCount) {
        // 每2秒检查属性
        if (tickCount % 40 == 0) {
            applyAllAttributes(player, stats);
        }
    }

    @Override
    public void onLogin(ServerPlayer player, PlayerStats stats) {
        applyAllAttributes(player, stats);
    }

    @Override
    public void onRespawn(ServerPlayer player, PlayerStats stats) {
        applyAllAttributes(player, stats);
    }

    @Override
    public void onDimensionChange(ServerPlayer player, PlayerStats stats) {
        applyAllAttributes(player, stats);
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    /**
     * 应用所有属性修改
     */
    public static void applyAllAttributes(ServerPlayer player, PlayerStats stats) {
        for (StatType stat : StatType.getAttributeStats()) {
            applyAttribute(player, stats, stat);
        }
    }

    /**
     * 应用单个属性修改
     */
    private static void applyAttribute(ServerPlayer player, PlayerStats stats, StatType stat) {
        Attribute attribute = stat.getAttribute();
        if (attribute == null) return;

        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;

        int index = getStatIndex(stat);
        if (index < 0) return;

        UUID modifierId = MODIFIER_UUIDS[index];

        // 移除旧修改器
        instance.removeModifier(modifierId);

        // 计算并添加新修改器
        float value = stats.getStatValue(stat);
        if (value != 0) {
            instance.addPermanentModifier(new AttributeModifier(
                    modifierId,
                    "infinitestats:" + stat.getId(),
                    value,
                    AttributeModifier.Operation.ADDITION
            ));
        }
    }

    /**
     * 获取属性索引
     */
    private static int getStatIndex(StatType stat) {
        for (int i = 0; i < StatType.ALL_STATS.length; i++) {
            if (StatType.ALL_STATS[i] == stat) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 获取修改器UUID
     */
    public static UUID getModifierUUID(StatType stat) {
        int index = getStatIndex(stat);
        return index >= 0 ? MODIFIER_UUIDS[index] : null;
    }
}