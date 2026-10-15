package com.infinitestats.handler;

import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.StatType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 属性修改处理器
 * 处理所有基于原版/模组 Attribute 的属性修改
 * 使用动态 UUID 生成以支持任意数量的属性
 */
public class AttributeHandler implements StatEffectHandler {

    // 属性修改器 id 缓存 — 按 statId 动态生成
    private static final Map<String, ResourceLocation> MODIFIER_IDS = new HashMap<>();

    /**
     * 获取或生成 modifier id
     */
    private static ResourceLocation getOrCreateId(String statId) {
        return MODIFIER_IDS.computeIfAbsent(statId,
                id -> ResourceLocation.fromNamespaceAndPath("infinitestats",
                        UUID.nameUUIDFromBytes(("infinitestats:" + id).getBytes()).toString()));
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

        AttributeInstance instance = player.getAttribute(BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attribute));
        if (instance == null) return;

        ResourceLocation modifierId = getOrCreateId(stat.getId());

        // 移除旧修改器
        instance.removeModifier(modifierId);

        // 计算并添加新修改器
        float value = stats.getStatValue(stat);
        if (value != 0) {
            instance.addPermanentModifier(new AttributeModifier(
                    modifierId,
                    value,
                    AttributeModifier.Operation.ADD_VALUE
            ));
        }
    }

    /**
     * 获取修改器 id
     */
    public static ResourceLocation getModifierId(StatType stat) {
        return getOrCreateId(stat.getId());
    }
}