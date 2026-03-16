package com.baimo.attributebinder.placeholder;

import com.baimo.attributebinder.cache.CacheManager;
import com.baimo.attributebinder.config.LangManager;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.api.stat.StatInstance;
import io.lumine.mythic.lib.api.stat.modifier.StatModifier;
import io.lumine.mythic.lib.player.modifier.ModifierSource;
import io.lumine.mythic.lib.player.modifier.ModifierType;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * PlaceholderFormatter — 用于格式化含展的置位符返回值
 * 主要提供对属性和修饰符的格式转换功能
 */
public final class PlaceholderFormatter {
    private PlaceholderFormatter() {}
    
    /**
     * 格式化AttributeBinder管理的属性，按key分组显示
     */
    public static String formatAttributeBinderAttributes(Player player) {
        return formatAttributeBinderAttributes(player.getUniqueId());
    }

    /**
     * 格式化 AttributeBinder 管理的属性（支持离线玩家，通过 UUID 查询缓存）
     */
    public static String formatAttributeBinderAttributes(UUID uuid) {
        Map<String, Map<String, CacheManager.Entry>> statMap = CacheManager.snapshot(uuid);
        
        if (statMap.isEmpty()) {
            return LangManager.get().get("placeholder-list-empty");
        }
        
        // 按key重新分组
        Map<String, Map<String, CacheManager.Entry>> byKey = new LinkedHashMap<>();
        statMap.forEach((stat, keyMap) -> {
            keyMap.forEach((keyId, entry) -> {
                byKey.computeIfAbsent(keyId, k -> new LinkedHashMap<>()).put(stat, entry);
            });
        });
        
        StringBuilder sb = new StringBuilder();
        String keyHeaderTemplate = LangManager.get().get("placeholder-list-key-header");
        String itemTemplate = LangManager.get().get("placeholder-list-item");
        String separator = LangManager.get().get("placeholder-list-separator");
        
        boolean first = true;
        for (Map.Entry<String, Map<String, CacheManager.Entry>> keyEntry : byKey.entrySet()) {
            if (!first) sb.append(separator);
            first = false;
            
            String keyHeader = keyHeaderTemplate.replace("{key}", keyEntry.getKey());
            sb.append(keyHeader);
            
            for (Map.Entry<String, CacheManager.Entry> attrEntry : keyEntry.getValue().entrySet()) {
                String stat = attrEntry.getKey();
                CacheManager.Entry entry = attrEntry.getValue();
                String valueStr = formatAttributeValue(entry.getValue(), entry.isPercent());
                
                String item = itemTemplate
                    .replace("{attribute}", stat)
                    .replace("{value}", valueStr)
                    .replace("{key}", keyEntry.getKey())
                    .replace("{memoryOnly}", String.valueOf(entry.isMemoryOnly()))
                    .replace("{expireTicks}", String.valueOf(entry.getExpireTicks()));
                sb.append(item);
            }
        }
        
        return sb.toString();
    }
    
    /**
     * 格式化所有属性（包括非AttributeBinder的）
     */
    public static String formatAllAttributes(Player player) {
        StringBuilder sb = new StringBuilder();
        
        // 先显示AttributeBinder管理的属性
        String abAttrs = formatAttributeBinderAttributes(player);
        if (!abAttrs.isEmpty() && !LangManager.get().get("placeholder-list-empty").equals(abAttrs)) {
            String abHeader = LangManager.get().get("placeholder-list-ab-header");
            sb.append(abHeader).append(abAttrs);
        }
        
        // 再显示其他来源的属性
        String otherAttrs = formatOtherAttributes(player);
        if (!otherAttrs.isEmpty()) {
            String separator = LangManager.get().get("placeholder-list-separator");
            if (sb.length() > 0) sb.append(separator);
            String otherHeader = LangManager.get().get("placeholder-list-other-header");
            sb.append(otherHeader).append(otherAttrs);
        }
        
        return sb.length() > 0 ? sb.toString() : LangManager.get().get("placeholder-list-empty");
    }
    
    /**
     * 格式化其他来源的属性（非AttributeBinder管理的）
     */
    private static String formatOtherAttributes(Player player) {
        try {
            io.lumine.mythic.lib.api.player.MMOPlayerData data = 
                io.lumine.mythic.lib.api.player.MMOPlayerData.get(player.getUniqueId());
            
            StringBuilder sb = new StringBuilder();
            String itemTemplate = LangManager.get().get("placeholder-list-other-item");
            
            // 获取所有属性实例
            data.getStatMap().getInstances().forEach(instance -> {
                String statName = instance.getStat();
                double totalValue = instance.getTotal();
                
                // 计算非AttributeBinder的值（总值减去AttributeBinder的值）
                double abValue = CacheManager.getAttribute(player.getUniqueId(), statName);
                double otherValue = totalValue - abValue;
                
                // 只显示有值的属性
                if (Math.abs(otherValue) > 0.001) {
                    String valueStr = formatAttributeValue(otherValue, false);
                    String item = itemTemplate
                        .replace("{attribute}", statName)
                        .replace("{value}", valueStr)
                        .replace("{total}", formatAttributeValue(totalValue, false));
                    sb.append(item);
                }
            });
            
            return sb.toString();
        } catch (Exception e) {
            // MMOItems/MythicLib未就绪或获取失败
            return "";
        }
    }
    
    /**
     * 格式化指定key下的所有属性
     */
    public static String formatKeyAttributes(Player player, String keyId) {
        return formatKeyAttributes(player.getUniqueId(), keyId);
    }

    /**
     * 格式化指定 key 下的所有属性（支持离线玩家，通过 UUID 查询缓存）
     */
    public static String formatKeyAttributes(UUID uuid, String keyId) {
        Map<String, Map<String, CacheManager.Entry>> statMap = CacheManager.snapshot(uuid);
        
        Map<String, CacheManager.Entry> keyAttrs = new LinkedHashMap<>();
        statMap.forEach((stat, keyMap) -> {
            CacheManager.Entry entry = keyMap.get(keyId);
            if (entry != null) {
                keyAttrs.put(stat, entry);
            }
        });
        
        if (keyAttrs.isEmpty()) {
            return LangManager.get().get("placeholder-key-empty", Map.of("key", keyId));
        }
        
        StringBuilder sb = new StringBuilder();
        String itemTemplate = LangManager.get().get("placeholder-key-item");
        
        keyAttrs.forEach((stat, entry) -> {
            String valueStr = formatAttributeValue(entry.getValue(), entry.isPercent());
            String item = itemTemplate
                .replace("{attribute}", stat)
                .replace("{value}", valueStr)
                .replace("{key}", keyId)
                .replace("{memoryOnly}", String.valueOf(entry.isMemoryOnly()))
                .replace("{expireTicks}", String.valueOf(entry.getExpireTicks()));
            sb.append(item);
        });
        
        return sb.toString();
    }
    
    /**
     * 格式化属性值，处理百分比显示
     */
    private static String formatAttributeValue(double value, boolean isPercent) {
        if (isPercent) {
            // 百分比值直接显示缓存中的原始数值（如12表示12%）
            return String.format("%s%.1f%%", value >= 0 ? "+" : "", value);
        } else {
            return value >= 0 ? "+" + value : String.valueOf(value);
        }
    }
    
    /**
     * 详细检查指定属性的所有修饰符信息
     * 基于调研文档中的AttributeInspector实现
     */
    public static String inspectStatModifiers(Player player, String statName) {
        return inspectStatModifiers(player.getUniqueId(), statName);
    }

    /**
     * 详细检查指定属性的所有修饰符信息（支持离线玩家，通过 UUID 访问）
     */
    public static String inspectStatModifiers(UUID uuid, String statName) {
        try {
            MMOPlayerData playerData = MMOPlayerData.get(uuid);
            StatInstance statInstance = playerData.getStatMap().getInstance(statName);
            
            StringBuilder sb = new StringBuilder();
            String headerTemplate = LangManager.get().get("placeholder-inspect-header");
            String itemTemplate = LangManager.get().get("placeholder-inspect-item");
            
            // 添加标题
            String header = headerTemplate
                .replace("{stat}", statName)
                .replace("{total}", formatAttributeValue(statInstance.getTotal(), false));
            sb.append(header);
            
            // 获取所有修饰符
            Collection<StatModifier> modifiers = statInstance.getModifiers();
            
            if (modifiers.isEmpty()) {
                sb.append(LangManager.get().get("placeholder-inspect-empty"));
                return sb.toString();
            }
            
            // 遍历并格式化每个修饰符
            for (StatModifier modifier : modifiers) {
                String key = modifier.getKey();
                double value = modifier.getValue();
                ModifierType type = modifier.getType();
                ModifierSource source = modifier.getSource();
                
                // 判断是否为百分比类型
                boolean isPercent = (type == ModifierType.ADDITIVE_MULTIPLIER || type == ModifierType.RELATIVE);
                String valueStr = formatModifierValue(value, isPercent);
                
                String item = itemTemplate
                    .replace("{key}", key)
                    .replace("{value}", valueStr)
                    .replace("{type}", getModifierTypeDisplay(type))
                    .replace("{source}", getModifierSourceDisplay(source))
                    .replace("{slot}", modifier.getSlot().name())
                    .replace("{uuid}", modifier.getUniqueId().toString());
                sb.append(item);
            }
            
            return sb.toString();
        } catch (Exception e) {
            return LangManager.get().get("placeholder-inspect-error", Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * 按来源过滤显示修饰符
     */
    public static String inspectStatModifiersBySource(Player player, String statName, String sourceName) {
        return inspectStatModifiersBySource(player.getUniqueId(), statName, sourceName);
    }

    /**
     * 按来源过滤显示修饰符（支持离线玩家，通过 UUID 访问）
     */
    public static String inspectStatModifiersBySource(UUID uuid, String statName, String sourceName) {
        try {
            MMOPlayerData playerData = MMOPlayerData.get(uuid);
            StatInstance statInstance = playerData.getStatMap().getInstance(statName);
            
            ModifierSource targetSource;
            try {
                targetSource = ModifierSource.valueOf(sourceName);
            } catch (IllegalArgumentException e) {
                return LangManager.get().get("placeholder-source-invalid", Map.of("source", sourceName));
            }
            
            StringBuilder sb = new StringBuilder();
            String headerTemplate = LangManager.get().get("placeholder-source-header");
            String itemTemplate = LangManager.get().get("placeholder-source-item");
            
            // 添加标题
            String header = headerTemplate
                .replace("{stat}", statName)
                .replace("{source}", getModifierSourceDisplay(targetSource));
            sb.append(header);
            
            // 过滤指定来源的修饰符
            List<StatModifier> filteredModifiers = statInstance.getModifiers().stream()
                .filter(modifier -> modifier.getSource() == targetSource)
                .collect(java.util.stream.Collectors.toList());
            
            if (filteredModifiers.isEmpty()) {
                sb.append(LangManager.get().get("placeholder-source-empty"));
                return sb.toString();
            }
            
            // 遍历并格式化过滤后的修饰符
            for (StatModifier modifier : filteredModifiers) {
                String key = modifier.getKey();
                double value = modifier.getValue();
                ModifierType type = modifier.getType();
                
                boolean isPercent = (type == ModifierType.ADDITIVE_MULTIPLIER || type == ModifierType.RELATIVE);
                String valueStr = formatModifierValue(value, isPercent);
                
                String item = itemTemplate
                    .replace("{key}", key)
                    .replace("{value}", valueStr)
                    .replace("{type}", getModifierTypeDisplay(type));
                sb.append(item);
            }
            
            return sb.toString();
        } catch (Exception e) {
            return LangManager.get().get("placeholder-source-error", Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * 获取指定属性的修饰符统计信息
     */
    public static String getStatModifierStats(Player player, String statName) {
        return getStatModifierStats(player.getUniqueId(), statName);
    }

    /**
     * 获取指定属性的修饰符统计信息（支持离线玩家，通过 UUID 访问）
     */
    public static String getStatModifierStats(UUID uuid, String statName) {
        try {
            MMOPlayerData playerData = MMOPlayerData.get(uuid);
            StatInstance statInstance = playerData.getStatMap().getInstance(statName);
            
            Collection<StatModifier> modifiers = statInstance.getModifiers();
            
            // 统计各种类型的修饰符
            Map<ModifierSource, Integer> sourceCount = new HashMap<>();
            Map<ModifierType, Integer> typeCount = new HashMap<>();
            double totalFlat = 0;
            double totalRelative = 0;
            double totalMultiplicative = 0;
            
            for (StatModifier modifier : modifiers) {
                ModifierSource source = modifier.getSource();
                ModifierType type = modifier.getType();
                double value = modifier.getValue();
                
                sourceCount.put(source, sourceCount.getOrDefault(source, 0) + 1);
                typeCount.put(type, typeCount.getOrDefault(type, 0) + 1);
                
                switch (type) {
                    case FLAT:
                        totalFlat += value;
                        break;
                    case RELATIVE:
                        totalRelative += value;
                        break;
                    case ADDITIVE_MULTIPLIER:
                        totalMultiplicative += value;
                        break;
                }
            }
            
            String template = LangManager.get().get("placeholder-stats-template");
            
            String result = template
                .replace("{stat}", statName)
                .replace("{total}", formatAttributeValue(statInstance.getTotal(), false))
                .replace("{count}", String.valueOf(modifiers.size()))
                .replace("{flat_total}", formatAttributeValue(totalFlat, false))
                .replace("{relative_total}", formatModifierValue(totalRelative, true))
                .replace("{multiplicative_total}", formatModifierValue(totalMultiplicative, true))
                .replace("{source_breakdown}", formatSourceBreakdown(sourceCount))
                .replace("{type_breakdown}", formatTypeBreakdown(typeCount));
            
            return result;
        } catch (Exception e) {
            return LangManager.get().get("placeholder-stats-error", Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * 格式化修饰符数值
     */
    private static String formatModifierValue(double value, boolean isPercent) {
        if (isPercent) {
            // 百分比类型：MMO系统中存储的是数字形式（12），直接显示为12%
            return (value >= 0 ? "+" : "") + String.format("%.1f", value) + "%";
        } else {
            // 固定值类型：显示为整数
            return (value >= 0 ? "+" : "") + String.format("%.0f", value);
        }
    }
    
    /**
     * 获取修饰符类型的显示名称
     */
    private static String getModifierTypeDisplay(ModifierType type) {
        switch (type) {
            case FLAT:
                return LangManager.get().get("modifier-type-flat");
            case RELATIVE:
                return LangManager.get().get("modifier-type-relative");
            case ADDITIVE_MULTIPLIER:
                return LangManager.get().get("modifier-type-multiplicative");
            default:
                return type.name();
        }
    }
    
    /**
     * 获取修饰符来源的显示名称
     */
    private static String getModifierSourceDisplay(ModifierSource source) {
        switch (source) {
            case ACCESSORY:
                return LangManager.get().get("modifier-source-accessory");
            case ARMOR:
                return LangManager.get().get("modifier-source-armor");
            case HAND_ITEM:
                return LangManager.get().get("modifier-source-hand-item");
            case MAINHAND_ITEM:
                return LangManager.get().get("modifier-source-mainhand-item");
            case MELEE_WEAPON:
                return LangManager.get().get("modifier-source-melee-weapon");
            case OFFHAND_ITEM:
                return LangManager.get().get("modifier-source-offhand-item");
            case ORNAMENT:
                return LangManager.get().get("modifier-source-ornament");
            case OTHER:
                return LangManager.get().get("modifier-source-other");
            case RANGED_WEAPON:
                return LangManager.get().get("modifier-source-ranged-weapon");
            case VOID:
                return LangManager.get().get("modifier-source-void");
            default:
                return source.name();
        }
    }
    
    /**
     * 格式化来源统计细分
     */
    private static String formatSourceBreakdown(Map<ModifierSource, Integer> sourceCount) {
        StringBuilder sb = new StringBuilder();
        String itemTemplate = LangManager.get().get("placeholder-stats-source-item");
        
        sourceCount.entrySet().stream()
            .sorted(Map.Entry.<ModifierSource, Integer>comparingByValue().reversed())
            .forEach(entry -> {
                String item = itemTemplate
                    .replace("{source}", getModifierSourceDisplay(entry.getKey()))
                    .replace("{count}", String.valueOf(entry.getValue()));
                sb.append(item);
            });
        
        return sb.toString();
    }
    
    /**
     * 格式化类型统计细分
     */
    private static String formatTypeBreakdown(Map<ModifierType, Integer> typeCount) {
        StringBuilder sb = new StringBuilder();
        String itemTemplate = LangManager.get().get("placeholder-stats-type-item");
        
        typeCount.entrySet().stream()
            .sorted(Map.Entry.<ModifierType, Integer>comparingByValue().reversed())
            .forEach(entry -> {
                String item = itemTemplate
                    .replace("{type}", getModifierTypeDisplay(entry.getKey()))
                    .replace("{count}", String.valueOf(entry.getValue()));
                sb.append(item);
            });
        
        return sb.toString();
    }
}
