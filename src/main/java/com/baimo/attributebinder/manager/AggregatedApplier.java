package com.baimo.attributebinder.manager;

import cn.drcomo.corelib.util.DebugUtil;
import com.baimo.attributebinder.AttributeBinder;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import com.baimo.attributebinder.util.PluginConstants;
import com.baimo.attributebinder.util.LoggerProvider;
import io.lumine.mythic.lib.api.stat.modifier.StatModifier;
import io.lumine.mythic.lib.player.modifier.ModifierType;
import com.baimo.attributebinder.manager.CacheManager;
import com.baimo.attributebinder.manager.AttributeApplier;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * AggregatedApplier —— 负责多来源属性的聚合写入。
 */
public final class AggregatedApplier {

    private static final String MOD_PREFIX = PluginConstants.MOD_PREFIX;
    /** 聚合写入时默认使用的 KeyID */
    public static final String AGG_KEY = "AGGREGATED";
    private static final Logger FALLBACK_LOG = LoggerProvider.getFallback(AggregatedApplier.class);
    private static final DebugUtil DEBUG_LOG = LoggerProvider.getDebugLogger();
    private AggregatedApplier() {}

    /**
     * 根据缓存中的数值重新计算并写入指定属性。
     * 若缓存为空，将移除该属性下所有本插件修饰符。
     *
     * @param uuid 玩家 UUID
     * @param stat 属性标识
     */
    public static void applyFromCache(UUID uuid, String stat) {
        Map<String, Map<String, CacheManager.Entry>> snapshot = CacheManager.snapshot(uuid);
        Map<String, CacheManager.Entry> keyMap = snapshot.get(stat.toUpperCase());
        if (keyMap == null || keyMap.isEmpty()) {
            AttributeApplier.removeStat(uuid, stat);
            return;
        }
        double flat = 0d;
        double percent = 0d;
        for (CacheManager.Entry e : keyMap.values()) {
            if (e.isPercent()) {
                percent += e.getValue();
            } else {
                flat += e.getValue();
            }
        }
        applyAggregated(uuid, stat, AGG_KEY, flat, percent);
    }

    /**
     * 将玩家所有属性按缓存重写入。
     */
    public static void applyAllFromCache(UUID uuid) {
        Map<String, Map<String, CacheManager.Entry>> snapshot = CacheManager.snapshot(uuid);
        snapshot.keySet().forEach(stat -> applyFromCache(uuid, stat));
    }

    /**
     * 将多个来源的平值和百分比汇总后统一写入。
     *
     * @param uuid       玩家 UUID
     * @param stat       属性标识
     * @param keyId      属性 Key
     * @param flatSum    平值合计
     * @param percentSum 百分比合计（以百分数传入）
     */
    public static void applyAggregated(UUID uuid, String stat, String keyId, double flatSum, double percentSum) {
        MMOPlayerData data = MMOPlayerData.get(uuid);
        String statUpper = stat.toUpperCase();

        int cleared = clearPercentModifiers(data, statUpper);

        if (flatSum != 0d) {
            AttributeApplier.apply(uuid, stat, keyId, flatSum, false);
        }

        if (percentSum != 0d) {
            AttributeApplier.apply(uuid, stat, keyId, percentSum, true);
        }

        String msg = String.format("写入 %s flat=%.2f percent=%.2f 类型=%s 清理=%d", statUpper, flatSum, percentSum,
                AttributeApplier.getPercentType(), cleared);
        if (DEBUG_LOG != null) {
            DEBUG_LOG.info(msg);
        } else {
            FALLBACK_LOG.info(msg);
        }
    }

    /** 返回指定属性下已注册的插件百分比修饰符数并清理 */
    private static int clearPercentModifiers(MMOPlayerData data, String statUpper) {
        List<String> keys = data.getStatMap().getInstance(statUpper).getModifiers().stream()
                .filter(m -> isPercentType(m.getType()))
                .map(StatModifier::getKey)
                .collect(Collectors.toList());
        keys.forEach(k -> data.getStatMap().getInstance(statUpper).remove(k));
        data.getStatMap().updateAll();
        return keys.size();
    }

    private static boolean isPercentType(ModifierType type) {
        return type == ModifierType.ADDITIVE_MULTIPLIER || type == ModifierType.RELATIVE;
    }

    /**
     * 仅查看当前数值，便于调试。
     */
    public static StatView debugDump(UUID uuid, String stat) {
        MMOPlayerData data = MMOPlayerData.get(uuid);
        String statUpper = stat.toUpperCase();

        double base = data.getStatMap().getInstance(statUpper).getBase();
        double flat = data.getStatMap().getInstance(statUpper).getModifiers().stream()
                .mapToDouble(StatModifier::getValue).sum();
        double percent = data.getStatMap().getInstance(statUpper).getModifiers().stream()
                .mapToDouble(StatModifier::getValue).sum();
        double finalVal = (base + flat) * (1 + percent / 100d);
        return new StatView(base, flat, percent, AttributeApplier.getPercentType(), finalVal);
    }

    /** 调试视图记录 */
    public record StatView(double base, double flatSum, double percentSum, ModifierType type, double finalValue) {
    }
}
