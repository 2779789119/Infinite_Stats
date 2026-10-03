package com.infinitestats.handler;

import com.infinitestats.stats.PlayerStats;
import com.infinitestats.stats.StatType;
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

    // 属性修改器UUID缓存 — 按 statId 动态生成
    private static final Map<String, UUID> MODIFIER_UUIDS = new HashMap<>();

    /**
     * 获取或生成 modifier UUID
     */
    private static UUID getOrCreateUUID(String statId) {
        return MODIFIER_UUIDS.computeIfAbsent(statId,
                id -> UUID.nameUUIDFromBytes(("infinitestats:" + id).getBytes()));
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
     * 一个属性可能同时写入多份等价的注册名（主属性 + 镜像属性），
     * 未安装对应模组时该注册名解析为 null，自动跳过。
     */
    private static void applyAttribute(ServerPlayer player, PlayerStats stats, StatType stat) {
        Map<String, Attribute> targets = stat.resolveAttributes();
        if (targets.isEmpty()) return;

        float value = stats.getStatValue(stat);
        String primaryName = stat.getAttributeName();

        for (Map.Entry<String, Attribute> entry : targets.entrySet()) {
            AttributeInstance instance = player.getAttribute(entry.getValue());
            if (instance == null) continue;

            // 主属性沿用旧 UUID，避免升级后旧存档里的修改器残留
            boolean primary = entry.getKey().equals(primaryName);
            UUID modifierId = getOrCreateUUID(primary
                    ? stat.getId()
                    : stat.getId() + "@" + entry.getKey());

            // 移除旧修改器
            instance.removeModifier(modifierId);

            // 计算并添加新修改器
            if (value != 0) {
                instance.addPermanentModifier(new AttributeModifier(
                        modifierId,
                        "infinitestats:" + stat.getId(),
                        value,
                        AttributeModifier.Operation.ADDITION
                ));
            }
        }
    }

    /**
     * 获取修改器UUID
     */
    public static UUID getModifierUUID(StatType stat) {
        return getOrCreateUUID(stat.getId());
    }
}