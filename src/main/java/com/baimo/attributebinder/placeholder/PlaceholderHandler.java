package com.baimo.attributebinder.placeholder;

import cn.drcomo.corelib.hook.placeholder.PlaceholderAPIUtil;
import com.baimo.attributebinder.cache.CacheManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * PlaceholderHandler —— 占位符处理器
 * 负责注册和处理所有 DrcomoAttributeBinder 相关的占位符
 */
public class PlaceholderHandler {
    
    private final PlaceholderAPIUtil papiUtil;
    
    public PlaceholderHandler(PlaceholderAPIUtil papiUtil) {
        this.papiUtil = papiUtil;
    }
    /**
     * 占位符上下文解析结果，包含目标玩家与剩余参数
     *
     * 解析占位符参数，支持指定其他玩家：<player>_<args> 格式
     * @param player 当前解析请求的玩家
     * @param args 原始参数字符串
     * @return 包含目标玩家和剩余参数的上下文
     */
    private PlaceholderContext resolveContext(Player player, String args) {
        if (args == null || args.isEmpty()) {
            return new PlaceholderContext(player, args, false);
        }

        final String prefixPlayer = "player:";
        final String prefixUuid = "uuid:";

        if (args.regionMatches(true, 0, prefixPlayer, 0, prefixPlayer.length())) {
            String rest = args.substring(prefixPlayer.length());
            int idx = rest.indexOf('_');
            if (idx > 0) {
                String playerName = rest.substring(0, idx);
                String remained = rest.substring(idx + 1);
                OfflinePlayer target = Bukkit.getPlayerExact(playerName);
                if (target == null) {
                    target = Bukkit.getOfflinePlayer(playerName);
                }
                if (target != null && (target.isOnline() || target.hasPlayedBefore())) {
                    return new PlaceholderContext(target, remained, true);
                }
            }
            return new PlaceholderContext(player, args, false);
        }

        if (args.regionMatches(true, 0, prefixUuid, 0, prefixUuid.length())) {
            String rest = args.substring(prefixUuid.length());
            int idx = rest.indexOf('_');
            if (idx > 0) {
                String uuidRaw = rest.substring(0, idx);
                String remained = rest.substring(idx + 1);
                UUID uuid = tryParseUuid(uuidRaw);
                if (uuid != null) {
                    OfflinePlayer target = Bukkit.getOfflinePlayer(uuid);
                    if (target != null && (target.isOnline() || target.hasPlayedBefore())) {
                        return new PlaceholderContext(target, remained, true);
                    }
                }
            }
            return new PlaceholderContext(player, args, false);
        }

        return new PlaceholderContext(player, args, false);
    }

    private UUID tryParseUuid(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            if (raw.length() == 32 && raw.matches("[0-9a-fA-F]{32}")) {
                String dashed = raw.substring(0, 8) + "-" + raw.substring(8, 12) + "-" + raw.substring(12, 16)
                        + "-" + raw.substring(16, 20) + "-" + raw.substring(20);
                try {
                    return UUID.fromString(dashed);
                } catch (IllegalArgumentException ignored2) {
                    return null;
                }
            }
            return null;
        }
    }

    private String unresolvedTargetFallback(String originalArgs) {
        if (originalArgs != null && (originalArgs.regionMatches(true, 0, "player:", 0, 7)
                || originalArgs.regionMatches(true, 0, "uuid:", 0, 5))) {
            return "";
        }
        return "0";
    }

    private boolean hasExactKey(UUID targetUuid, String stat, String keyId) {
        if (stat == null || keyId == null || stat.isEmpty() || keyId.isEmpty()) {
            return false;
        }
        Map<String, Map<String, CacheManager.Entry>> statMap = CacheManager.snapshot(targetUuid);
        Map<String, CacheManager.Entry> keyMap = statMap.get(stat.toUpperCase(Locale.ROOT));
        return keyMap != null && keyMap.containsKey(keyId);
    }

    private String parseAttrCurrentPlayer(UUID targetUuid, String useArgs) {
        if (useArgs == null || useArgs.isEmpty()) {
            return "0";
        }

        int statKeySplit = useArgs.lastIndexOf('_');
        if (statKeySplit > 0) {
            String left = useArgs.substring(0, statKeySplit);
            String right = useArgs.substring(statKeySplit + 1);

            if (!right.isEmpty() && hasExactKey(targetUuid, left, right)) {
                double val = CacheManager.getAttribute(targetUuid, left.toUpperCase(Locale.ROOT), right);
                return String.valueOf(val);
            }
        }

        double total = CacheManager.getAttribute(targetUuid, useArgs.toUpperCase(Locale.ROOT));
        return String.valueOf(total);
    }

    private String parseAttrExplicitTarget(UUID targetUuid, String useArgs) {
        if (useArgs == null || useArgs.isEmpty()) {
            return "0";
        }

        int statKeySplit = useArgs.lastIndexOf('_');
        if (statKeySplit > 0) {
            String stat = useArgs.substring(0, statKeySplit).toUpperCase(Locale.ROOT);
            String key = useArgs.substring(statKeySplit + 1);
            if (!key.isEmpty() && hasExactKey(targetUuid, stat, key)) {
                double val = CacheManager.getAttribute(targetUuid, stat, key);
                return String.valueOf(val);
            }
        }

        double total = CacheManager.getAttribute(targetUuid, useArgs.toUpperCase(Locale.ROOT));
        return String.valueOf(total);
    }

    private String[] splitSourceArgs(String useArgs) {
        if (useArgs == null) {
            return null;
        }

        String normalized = useArgs.toUpperCase(Locale.ROOT);
        for (String sourceName : SOURCE_NAMES) {
            String suffix = "_" + sourceName;
            if (normalized.endsWith(suffix) && normalized.length() > suffix.length()) {
                String stat = normalized.substring(0, normalized.length() - suffix.length());
                return new String[]{stat, sourceName};
            }
        }
        return null;
    }

    private static final List<String> SOURCE_NAMES = List.of(
            "ACCESSORY",
            "ARMOR",
            "HAND_ITEM",
            "MAINHAND_ITEM",
            "MELEE_WEAPON",
            "OFFHAND_ITEM",
            "ORNAMENT",
            "OTHER",
            "RANGED_WEAPON",
            "VOID"
    );
    
    /**
     * 注册所有占位符
     */
    public void registerPlaceholders() {
        // 原有的attr占位符：%attributebinder_attr_<STAT>% - 获取属性总值
        papiUtil.register("attr", (player, args) -> {
            PlaceholderContext ctx = resolveContext(player, args);
            OfflinePlayer target = ctx.target;
            String useArgs = ctx.args;
            if (target == null) return unresolvedTargetFallback(args);

            UUID targetUuid = target.getUniqueId();
            if (ctx.explicitTarget) {
                return parseAttrExplicitTarget(targetUuid, useArgs);
            }
            return parseAttrCurrentPlayer(targetUuid, useArgs);
        });
        
        // 扩展的list占位符：显示详细的属性列表（支持其他玩家解析）
        papiUtil.register("list", (player, args) -> {
            // 支持解析目标玩家，list只需关注player上下文
            PlaceholderContext ctx = resolveContext(player, args);
            OfflinePlayer target = ctx.target;
            if (target == null) return "";
            String useArgs = ctx.args == null ? "" : ctx.args;
            if ("all".equalsIgnoreCase(useArgs)) {
                if (!(target instanceof Player onlinePlayer)) return "";
                return PlaceholderFormatter.formatAllAttributes(onlinePlayer);
            } else if (useArgs.isEmpty()) {
                return PlaceholderFormatter.formatAttributeBinderAttributes(target.getUniqueId());
            }
            return "";
        });
        
        // 新增：获取指定属性的所有key
        papiUtil.register("keys", (player, args) -> {
            PlaceholderContext ctx = resolveContext(player, args);
            OfflinePlayer target = ctx.target;
            String useArgs = ctx.args;
            if (target == null || useArgs == null || useArgs.isEmpty()) return "";
            String stat = useArgs.toUpperCase(Locale.ROOT);
            UUID uuid = target.getUniqueId();
            Map<String, Map<String, CacheManager.Entry>> statMap = CacheManager.snapshot(uuid);
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
            return PlaceholderFormatter.formatKeyAttributes(target.getUniqueId(), keyId);
        });
        
        // 新增：详细检查指定属性的所有修饰符信息
        papiUtil.register("inspect", (player, args) -> {
            PlaceholderContext ctx = resolveContext(player, args);
            OfflinePlayer target = ctx.target;
            String stat = ctx.args;
            if (target == null || stat == null || stat.isEmpty()) return "";
            return PlaceholderFormatter.inspectStatModifiers(target.getUniqueId(), stat.toUpperCase(Locale.ROOT));
        });
        
        // 新增：按来源过滤显示修饰符
        papiUtil.register("source", (player, args) -> {
            PlaceholderContext ctx = resolveContext(player, args);
            OfflinePlayer target = ctx.target;
            String useArgs = ctx.args;
            if (target == null || useArgs == null) return "";
            String[] parts = splitSourceArgs(useArgs);
            if (parts == null) return "";
            return PlaceholderFormatter.inspectStatModifiersBySource(target.getUniqueId(), parts[0], parts[1]);
        });
        
        // 新增：显示指定属性的修饰符统计
        papiUtil.register("stats", (player, args) -> {
            PlaceholderContext ctx = resolveContext(player, args);
            OfflinePlayer target = ctx.target;
            String stat = ctx.args;
            if (target == null || stat == null || stat.isEmpty()) return "";
            return PlaceholderFormatter.getStatModifierStats(target.getUniqueId(), stat.toUpperCase(Locale.ROOT));
        });
    }
    
}
