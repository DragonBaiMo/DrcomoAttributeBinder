package com.baimo.attributebinder.manager;

import io.lumine.mythic.lib.api.player.EquipmentSlot;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.api.stat.modifier.StatModifier;
import io.lumine.mythic.lib.player.modifier.ModifierSource;
import io.lumine.mythic.lib.player.modifier.ModifierType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.logging.Logger;
import com.baimo.attributebinder.util.PluginConstants;
import com.baimo.attributebinder.util.LoggerProvider;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import cn.drcomo.corelib.util.DebugUtil;
import com.baimo.attributebinder.AttributeBinder;

/**
 * AttributeApplier —— 负责将缓存中的属性写入 MythicLib。
 * <p>百分比值需以百分数形式传入，例如 30% 应写入 30.0。</p>
 */
public class AttributeApplier {

    private static final String MOD_PREFIX = PluginConstants.MOD_PREFIX;

    // 日志工具：优先使用 DrcomoCoreLib，如不可用则退回 Java Logger
    private static final java.util.logging.Logger FALLBACK_LOG = LoggerProvider.getFallback(AttributeApplier.class);
    private static final DebugUtil DEBUG_LOG = LoggerProvider.getDebugLogger();

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

    /** 返回当前解析到的百分比类型 */
    public static ModifierType getPercentType() {
        return PERCENT_TYPE;
    }

    private static String modifierId(String stat, String keyId) {
        return MOD_PREFIX + keyId + "_" + stat.toUpperCase();
    }

    /**
     * 清理指定属性下本插件产生的所有百分比修饰符。
     * @return 移除的条目数量
     */
    public static int clearPercent(UUID uuid, String stat) {
        MMOPlayerData data = MMOPlayerData.get(uuid);
        String statUpper = stat.toUpperCase();
        List<String> toRemove = data.getStatMap().getInstance(statUpper).getModifiers().stream()
                .filter(mod -> mod.getKey().startsWith(MOD_PREFIX) && isPercentType(mod.getType()))
                .map(StatModifier::getKey)
                .collect(Collectors.toList());
        toRemove.forEach(key -> data.getStatMap().getInstance(statUpper).remove(key));
        int removed = toRemove.size();
        data.getStatMap().updateAll();
        return removed;
    }

    /**
     * 应用单条修饰符。
     *
     * @param uuid    目标玩家 UUID
     * @param stat    属性标识
     * @param keyId   属性 Key
     * @param value   数值；当 {@code percent=true} 时需以百分数形式传入（30% → 30.0）
     * @param percent true 表示写入百分比修饰符
     */
    public static void apply(UUID uuid, String stat, String keyId, double value, boolean percent) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;

        MMOPlayerData data = MMOPlayerData.get(uuid);
        String statUpper = stat.toUpperCase();

        // 安全：先清理本插件在该 stat + keyId 下的同类型遗留，避免混合乘区
        cleanupSameChannel(data, uuid, statUpper, keyId, percent);

        if (percent && PERCENT_TYPE == ModifierType.FLAT) {
            String msg = "当前环境不支持百分比类型，已跳过写入";
            if (DEBUG_LOG != null) {
                DEBUG_LOG.warn(msg);
            } else {
                FALLBACK_LOG.warning(msg);
            }
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
    private static void cleanupSameChannel(MMOPlayerData data, UUID uuid, String statUpper, String keyId, boolean percent) {
        String id = modifierId(statUpper, keyId);
        StatModifier existing = data.getStatMap().getInstance(statUpper).getModifier(id);
        if (existing != null) {
            data.getStatMap().getInstance(statUpper).remove(id);
        }

        if (percent) {
            // 额外：清理本插件前缀的百分比修饰符，保持单一乘区
            clearPercent(uuid, statUpper);
        }
    }

    private static boolean isPercentType(ModifierType type) {
        return type == ModifierType.ADDITIVE_MULTIPLIER || type == ModifierType.RELATIVE;
    }

    // === 兼容旧接口 ===

    /**
     * 应用指定属性值（平值），使用默认 KeyID。
     *
     * @param uuid  玩家 UUID
     * @param stat  属性标识
     * @param value 数值
     */
    public static void apply(UUID uuid, String stat, double value) {
        apply(uuid, stat, CacheManager.DEFAULT_KEY, value, false);
    }

    /**
     * 应用指定属性值，使用默认 KeyID。
     *
     * @param uuid   玩家 UUID
     * @param stat   属性标识
     * @param value  数值
     * @param percent 是否百分比
     */
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
