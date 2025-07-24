package com.baimo.attributeBinder.placeholder;

import cn.drcomo.corelib.hook.placeholder.PlaceholderAPIUtil;
import com.baimo.attributeBinder.manager.CacheManager;
import com.baimo.attributeBinder.manager.LangManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.api.stat.StatInstance;
import io.lumine.mythic.lib.api.stat.modifier.StatModifier;
import io.lumine.mythic.lib.player.modifier.ModifierSource;
import io.lumine.mythic.lib.player.modifier.ModifierType;

import java.util.*;

/**
 * PlaceholderHandler —— 占位符处理器
 * 负责注册和处理所有 AttributeBinder 相关的占位符
 */
public class PlaceholderHandler {
    
    private final PlaceholderAPIUtil papiUtil;
    
    public PlaceholderHandler(PlaceholderAPIUtil papiUtil) {
        this.papiUtil = papiUtil;
    }
    /**
     * 占位符上下文解析结果，包含目标玩家与剩余参数
     */
    private static class PlaceholderContext {
        final OfflinePlayer target;
        final String args;
        PlaceholderContext(OfflinePlayer target, String args) {
            this.target = target;
            this.args = args;
        }
    }
    /**
     * 解析占位符参数，支持指定其他玩家：<player>_<args> 格式
     * @param player 当前解析请求的玩家
     * @param args 原始参数字符串
     * @return 包含目标玩家和剩余参数的上下文
     */
    @SuppressWarnings("deprecation")
    private PlaceholderContext resolveContext(Player player, String args) {
        if (args == null) {
            return new PlaceholderContext(player, null);
        }
        String[] parts = args.split("_", 2);
        if (parts.length > 1) {
            String name = parts[0];
            OfflinePlayer op = Bukkit.getPlayerExact(name);
            if (op == null) op = Bukkit.getOfflinePlayer(name);
            if (op != null && (op.isOnline() || op.hasPlayedBefore())) {
                return new PlaceholderContext(op, parts[1]);
            }
        }
        return new PlaceholderContext(player, args);
    }
    
    /**
     * 注册所有占位符
     */
    public void registerPlaceholders() {
        // 原有的attr占位符：%attributebinder_attr_<STAT>% - 获取属性总值
        papiUtil.register("attr", (player, args) -> {
            PlaceholderContext ctx = resolveContext(player, args);
            OfflinePlayer target = ctx.target;
            String useArgs = ctx.args;
            if (useArgs == null || useArgs.isEmpty()) return "0";
            String[] parts = useArgs.split("_");
            if (parts.length == 1) {
                String stat = parts[0].toUpperCase();
                double val = CacheManager.getAttribute(target.getUniqueId(), stat);
                return String.valueOf(val);
            } else if (parts.length == 2) {
                String stat = parts[0].toUpperCase();
                String keyId = parts[1];
                double val = CacheManager.getAttribute(target.getUniqueId(), stat, keyId);
                return String.valueOf(val);
            }
            return "0";
        });
        
        // 扩展的list占位符：显示详细的属性列表（支持其他玩家解析）
        papiUtil.register("list", (player, args) -> {
            // 支持解析目标玩家，list只需关注player上下文
            PlaceholderContext ctx = resolveContext(player, args);
            OfflinePlayer target = ctx.target;
            if (target == null) return "";
            String useArgs = ctx.args == null ? "" : ctx.args;
            if ("all".equalsIgnoreCase(useArgs)) {
                return formatAllAttributes((Player) target);
            } else if (useArgs.isEmpty()) {
                return formatAttributeBinderAttributes((Player) target);
            }
            return "";
        });
        
        // 新增：获取指定属性的所有key
        papiUtil.register("keys", (player, args) -> {
            PlaceholderContext ctx = resolveContext(player, args);
            OfflinePlayer target = ctx.target;
            String useArgs = ctx.args;
            if (target == null || useArgs == null || useArgs.isEmpty()) return "";
            String stat = useArgs.toUpperCase();
            UUID uuid = target.getUniqueId();
            Map<String, Map<String, CacheManager.Entry>> statMap = 
                CacheManager.snapshot().getOrDefault(uuid, Collections.emptyMap());
            Map<String, CacheManager.Entry> keyMap = statMap.get(stat);
            if (keyMap == null || keyMap.isEmpty()) return "";
            return String.join(", ", keyMap.keySet());
        });
        
        // 新增：获取指定key下的所有属性
        papiUtil.register("keyattrs", (player, args) -> {
            PlaceholderContext ctx = resolveContext(player, args);
            OfflinePlayer target = ctx.target;
            String keyId = ctx.args;
            if (target == null || keyId == null || keyId.isEmpty()) return "";
            return formatKeyAttributes((Player) target, keyId);
        });
        
        // 新增：详细检查指定属性的所有修饰符信息
        papiUtil.register("inspect", (player, args) -> {
            PlaceholderContext ctx = resolveContext(player, args);
            OfflinePlayer target = ctx.target;
            String stat = ctx.args;
            if (target == null || stat == null || stat.isEmpty()) return "";
            return inspectStatModifiers((Player) target, stat.toUpperCase());
        });
        
        // 新增：按来源过滤显示修饰符
        papiUtil.register("source", (player, args) -> {
            PlaceholderContext ctx = resolveContext(player, args);
            OfflinePlayer target = ctx.target;
            String useArgs = ctx.args;
            if (target == null || useArgs == null) return "";
            String[] parts = useArgs.split("_", 2);
            if (parts.length < 2) return "";
            return inspectStatModifiersBySource((Player) target, parts[0].toUpperCase(), parts[1].toUpperCase());
        });
        
        // 新增：显示指定属性的修饰符统计
        papiUtil.register("stats", (player, args) -> {
            PlaceholderContext ctx = resolveContext(player, args);
            OfflinePlayer target = ctx.target;
            String stat = ctx.args;
            if (target == null || stat == null || stat.isEmpty()) return "";
            return getStatModifierStats((Player) target, stat.toUpperCase());
        });
    }
    
    /**
     * 格式化AttributeBinder管理的属性，按key分组显示
     */
    private static String formatAttributeBinderAttributes(Player player) {
        UUID uuid = player.getUniqueId();
        Map<String, Map<String, CacheManager.Entry>> statMap = 
            CacheManager.snapshot().getOrDefault(uuid, Collections.emptyMap());
        
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
        UUID uuid = player.getUniqueId();
        Map<String, Map<String, CacheManager.Entry>> statMap = 
            CacheManager.snapshot().getOrDefault(uuid, Collections.emptyMap());
        
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
            double p = value * 100;
            // 百分比值乘以100后格式化为一位小数，并加正负号和百分号
            return String.format("%s%.1f%%", p >= 0 ? "+" : "", p);
        } else {
            return value >= 0 ? "+" + value : String.valueOf(value);
        }
    }
    
    /**
     * 详细检查指定属性的所有修饰符信息
     * 基于调研文档中的AttributeInspector实现
     */
    public static String inspectStatModifiers(Player player, String statName) {
        try {
            MMOPlayerData playerData = MMOPlayerData.get(player.getUniqueId());
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
                boolean isPercent = (type == ModifierType.RELATIVE || type == ModifierType.ADDITIVE_MULTIPLIER);
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
        try {
            MMOPlayerData playerData = MMOPlayerData.get(player.getUniqueId());
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
                
                boolean isPercent = (type == ModifierType.RELATIVE || type == ModifierType.ADDITIVE_MULTIPLIER);
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
        try {
            MMOPlayerData playerData = MMOPlayerData.get(player.getUniqueId());
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
            // 百分比类型：将小数转换为百分比显示
            double percentage = value * 100;
            return (percentage >= 0 ? "+" : "") + String.format("%.1f", percentage) + "%";
        } else {
            // 固定值类型
            return (value >= 0 ? "+" : "") + String.valueOf(value);
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