package com.baimo.attributeBinder.manager;

import io.lumine.mythic.lib.api.player.EquipmentSlot;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.api.stat.modifier.StatModifier;
import io.lumine.mythic.lib.player.modifier.ModifierSource;
import io.lumine.mythic.lib.player.modifier.ModifierType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 兼容新旧 MythicLib 百分比修饰符的属性应用器。
 * 支持 keyId 多实例、百分比与平值两种修饰符。
 */
public class AttributeApplier {

    /** 统一前缀，便于批量移除 */
    private static final String MOD_PREFIX = "AttributeBinder_";

    /** 运行时动态解析百分比修饰符类型 */
    private static final ModifierType PERCENT_TYPE = resolvePercentType();

    /**
     * 动态获取百分比修饰符类型（新版为 ADDITIVE_MULTIPLIER，旧版为 RELATIVE，兜底为 FLAT）
     */
    private static ModifierType resolvePercentType() {
        try {
            return ModifierType.valueOf("ADDITIVE_MULTIPLIER");
        } catch (IllegalArgumentException e1) {
            try {
                return ModifierType.valueOf("RELATIVE");
            } catch (IllegalArgumentException e2) {
                return ModifierType.FLAT;
            }
        }
    }

    private static String modifierId(String stat, String keyId) {
        return MOD_PREFIX + keyId + "_" + stat.toUpperCase();
    }

    /**
     * 为玩家应用属性修饰符（带 keyId，支持百分比/平值，自动兼容新旧 MythicLib）
     */
    public static void apply(UUID uuid, String stat, String keyId, double value, boolean percent) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;

        remove(uuid, stat, keyId);

        MMOPlayerData data = MMOPlayerData.get(uuid);

        // 百分比兼容：新版 ADDITIVE_MULTIPLIER 和旧版 RELATIVE 都直接使用原始百分比值
        // 例如：12% 对应 value=0.12，两种类型都使用 0.12
        double finalValue = value;

        StatModifier modifier = new StatModifier(
                modifierId(stat, keyId),
                stat.toUpperCase(),
                finalValue,
                percent ? PERCENT_TYPE : ModifierType.FLAT,
                EquipmentSlot.OTHER,
                ModifierSource.OTHER
        );

        modifier.register(data);
    }

    /** 兼容旧接口：默认 keyId */
    public static void apply(UUID uuid, String stat, double value) {
        apply(uuid, stat, CacheManager.DEFAULT_KEY, value, false);
    }

    public static void apply(UUID uuid, String stat, double value, boolean percent) {
        apply(uuid, stat, CacheManager.DEFAULT_KEY, value, percent);
    }

    /** 移除单个属性（指定 keyId） */
    public static void remove(UUID uuid, String stat, String keyId) {
        MMOPlayerData data = MMOPlayerData.get(uuid);
        data.getStatMap().getInstance(stat.toUpperCase()).remove(modifierId(stat, keyId));
    }

    /** 兼容旧接口 */
    public static void remove(UUID uuid, String stat) {
        remove(uuid, stat, CacheManager.DEFAULT_KEY);
    }

    /**
     * 移除所有由本插件添加的属性（所有 keyId）
     */
    public static void removeAll(UUID uuid) {
        MMOPlayerData data = MMOPlayerData.get(uuid);

        // 遍历每个 StatInstance，收集所有本插件前缀的修饰符 key
        data.getStatMap().getInstances().forEach(instance -> {
            List<String> toRemove = instance.getModifiers().stream()
                    .map(StatModifier::getKey)
                    .filter(key -> key.startsWith(MOD_PREFIX))
                    .collect(Collectors.toList());

            // 逐条调用官方 API 删除，并触发脏标记与事件
            toRemove.forEach(instance::remove);
        });

        // 统一重算所有属性，触发 StatUpdateEvent
        data.getStatMap().updateAll();
    }

    /**
     * 移除所有由本插件在指定 keyId 下添加的修饰符（所有 stat）
     */
    public static void removeKey(UUID uuid, String keyId) {
        MMOPlayerData data = MMOPlayerData.get(uuid);
        String prefix = MOD_PREFIX + keyId + "_";
        // 遍历每个 StatInstance，收集所有匹配 keyId 的修饰符 key
        data.getStatMap().getInstances().forEach(instance -> {
            List<String> toRemove = instance.getModifiers().stream()
                    .map(StatModifier::getKey)
                    .filter(key -> key.startsWith(prefix))
                    .collect(Collectors.toList());
            toRemove.forEach(instance::remove);
        });
        // 统一重算所有属性，触发 StatUpdateEvent
        data.getStatMap().updateAll();
    }

    /**
     * 移除所有由本插件在指定属性 stat 下添加的修饰符（支持所有 keyId）
     */
    public static void removeStat(UUID uuid, String stat) {
        MMOPlayerData data = MMOPlayerData.get(uuid);
        String statUpper = stat.toUpperCase();
        // 遍历所有 stat 实例，移除本插件该属性下的修饰符
        data.getStatMap().getInstances().forEach(instance -> {
            List<String> toRemove = instance.getModifiers().stream()
                    .map(StatModifier::getKey)
                    .filter(key -> key.startsWith(MOD_PREFIX) && key.endsWith("_" + statUpper))
                    .collect(Collectors.toList());
            toRemove.forEach(instance::remove);
        });
        // 统一重算触发 StatUpdateEvent
        data.getStatMap().updateAll();
    }
}