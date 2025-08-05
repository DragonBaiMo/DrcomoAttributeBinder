package com.baimo.attributebinder.placeholder;

import cn.drcomo.corelib.hook.placeholder.PlaceholderAPIUtil;
import com.baimo.attributebinder.cache.CacheManager;
import com.baimo.attributebinder.config.LangManager;
import com.baimo.attributebinder.placeholder.PlaceholderFormatter;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

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
     *
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
                return PlaceholderFormatter.formatAllAttributes((Player) target);
            } else if (useArgs.isEmpty()) {
                return PlaceholderFormatter.formatAttributeBinderAttributes((Player) target);
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
            return PlaceholderFormatter.formatKeyAttributes((Player) target, keyId);
        });
        
        // 新增：详细检查指定属性的所有修饰符信息
        papiUtil.register("inspect", (player, args) -> {
            PlaceholderContext ctx = resolveContext(player, args);
            OfflinePlayer target = ctx.target;
            String stat = ctx.args;
            if (target == null || stat == null || stat.isEmpty()) return "";
            return PlaceholderFormatter.inspectStatModifiers((Player) target, stat.toUpperCase());
        });
        
        // 新增：按来源过滤显示修饰符
        papiUtil.register("source", (player, args) -> {
            PlaceholderContext ctx = resolveContext(player, args);
            OfflinePlayer target = ctx.target;
            String useArgs = ctx.args;
            if (target == null || useArgs == null) return "";
            String[] parts = useArgs.split("_", 2);
            if (parts.length < 2) return "";
            return PlaceholderFormatter.inspectStatModifiersBySource((Player) target, parts[0].toUpperCase(), parts[1].toUpperCase());
        });
        
        // 新增：显示指定属性的修饰符统计
        papiUtil.register("stats", (player, args) -> {
            PlaceholderContext ctx = resolveContext(player, args);
            OfflinePlayer target = ctx.target;
            String stat = ctx.args;
            if (target == null || stat == null || stat.isEmpty()) return "";
            return PlaceholderFormatter.getStatModifierStats((Player) target, stat.toUpperCase());
        });
    }
    
}
