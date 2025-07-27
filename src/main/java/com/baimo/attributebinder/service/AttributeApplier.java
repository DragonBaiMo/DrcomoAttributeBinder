package com.baimo.attributebinder.service;

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

public class AttributeApplier {

    private static final String MOD_PREFIX = "AttributeBinder_";

    /** 仅用于“线性乘区”。若无 ADDITIVE_MULTIPLIER，则回退为 RELATIVE（且必须只保留一个合并后的百分比）。 */
    private static final ModifierType PERCENT_TYPE = resolvePercentType();

    private static ModifierType resolvePercentType() {
        try {
            return ModifierType.valueOf("ADDITIVE_MULTIPLIER");
        } catch (IllegalArgumentException e1) {
            try {
                return ModifierType.valueOf("RELATIVE");
            } catch (IllegalArgumentException e2) {
                // 不应落到 FLAT；只是兜底，避免崩溃。真正遇到应在 apply 时直接返回并告警。
                return ModifierType.FLAT;
            }
        }
    }

    private static String modifierId(String stat, String keyId) {
        return MOD_PREFIX + keyId + "_" + stat.toUpperCase();
    }

    /**
     * 应用单条修饰符。
     * @param percent  当为 true 时，value 必须以“百分数”传入：如 12% 传 12.0
     */
    public static void apply(UUID uuid, String stat, String keyId, double value, boolean percent) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;

        MMOPlayerData data = MMOPlayerData.get(uuid);
        String statUpper = stat.toUpperCase();

        // 安全：先清理本插件在该 stat + keyId 下的同类型遗留，避免混合乘区
        cleanupSameChannel(data, statUpper, keyId, percent);

        if (percent && PERCENT_TYPE == ModifierType.FLAT) {
            // 极端兜底：当前环境不支持百分比类型，直接跳过，避免错误放大
            // 你也可以改成将百分比近似折算为 FLAT，但这通常不符合预期
            return;
        }

        // 重要：百分比以“百分数”传入：12% → 12.0
        double finalValue = value;

        StatModifier modifier = new StatModifier(
                modifierId(statUpper, keyId),
                statUpper,
                finalValue,
                percent ? PERCENT_TYPE : ModifierType.FLAT,
                EquipmentSlot.OTHER,
                ModifierSource.OTHER
        );
        modifier.register(data);
    }

    /** 清理同一 stat+keyId 下本插件产生的同通道修饰符，防止遗留叠加 */
    private static void cleanupSameChannel(MMOPlayerData data, String statUpper, String keyId, boolean percent) {
        String id = modifierId(statUpper, keyId);
        StatModifier existing = data.getStatMap().getInstance(statUpper).getModifier(id);
        if (existing != null) {
            data.getStatMap().getInstance(statUpper).remove(id);
        }

        // 额外：清理本插件前缀、同 stat、且类型属于“百分比通道”的遗留，避免旧版 RELATIVE 与新版 ADDITIVE_MULTIPLIER 混在一起
        data.getStatMap().getInstance(statUpper).getModifiers().stream()
                .filter(mod -> mod.getKey().startsWith(MOD_PREFIX) && mod.getStat().equalsIgnoreCase(statUpper))
                .filter(mod -> isPercentType(mod.getType()))
                .map(StatModifier::getKey)
                .forEach(key -> data.getStatMap().getInstance(statUpper).remove(key));
    }

    private static boolean isPercentType(ModifierType type) {
        return type == ModifierType.ADDITIVE_MULTIPLIER || type == ModifierType.RELATIVE;
    }

    // === 兼容旧接口 ===

    public static void apply(UUID uuid, String stat, double value) {
        apply(uuid, stat, CacheManager.DEFAULT_KEY, value, false);
    }

    public static void apply(UUID uuid, String stat, double value, boolean percent) {
        apply(uuid, stat, CacheManager.DEFAULT_KEY, value, percent);
    }

    public static void remove(UUID uuid, String stat, String keyId) {
        MMOPlayerData data = MMOPlayerData.get(uuid);
        data.getStatMap().getInstance(stat.toUpperCase()).remove(modifierId(stat, keyId));
        data.getStatMap().updateAll();
    }

    public static void remove(UUID uuid, String stat) {
        remove(uuid, stat, CacheManager.DEFAULT_KEY);
    }

    public static void removeAll(UUID uuid) {
        MMOPlayerData data = MMOPlayerData.get(uuid);
        data.getStatMap().getInstances().forEach(instance -> {
            List<String> toRemove = instance.getModifiers().stream()
                    .map(StatModifier::getKey)
                    .filter(key -> key.startsWith(MOD_PREFIX))
                    .collect(Collectors.toList());
            toRemove.forEach(instance::remove);
        });
        data.getStatMap().updateAll();
    }

    public static void removeKey(UUID uuid, String keyId) {
        MMOPlayerData data = MMOPlayerData.get(uuid);
        String prefix = MOD_PREFIX + keyId + "_";
        data.getStatMap().getInstances().forEach(instance -> {
            List<String> toRemove = instance.getModifiers().stream()
                    .map(StatModifier::getKey)
                    .filter(key -> key.startsWith(prefix))
                    .collect(Collectors.toList());
            toRemove.forEach(instance::remove);
        });
        data.getStatMap().updateAll();
    }

    public static void removeStat(UUID uuid, String stat) {
        MMOPlayerData data = MMOPlayerData.get(uuid);
        String statUpper = stat.toUpperCase();
        data.getStatMap().getInstances().forEach(instance -> {
            List<String> toRemove = instance.getModifiers().stream()
                    .map(StatModifier::getKey)
                    .filter(key -> key.startsWith(MOD_PREFIX) && key.endsWith("_" + statUpper))
                    .collect(Collectors.toList());
            toRemove.forEach(instance::remove);
        });
        data.getStatMap().updateAll();
    }
}
