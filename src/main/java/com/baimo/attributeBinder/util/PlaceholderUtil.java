package com.baimo.attributeBinder.util;

import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * PlaceholderUtil —— 集中式占位符管理器
 *
 * <p>功能：</p>
 * <ul>
 *   <li>统一注册所有占位符处理器</li>
 *   <li>支持多重嵌套递归解析</li>
 *   <li>公开内置占位符工具方法，供其他插件直接调用</li>
 * </ul>
 */
public class PlaceholderUtil {

    private static Plugin plugin;
    private static final Map<String, BiFunction<Player, String, String>> handlers = new HashMap<>();

    private static final PlaceholderExpansion expansion = new PlaceholderExpansion() {
        @Override public boolean canRegister()            { return true; }
        @Override public String getIdentifier()            { return plugin.getName().toLowerCase(); }
        @Override public String getAuthor()                { return String.join(" | ", plugin.getDescription().getAuthors()); }
        @Override public String getVersion()               { return plugin.getDescription().getVersion(); }

        @Override
        public String onPlaceholderRequest(Player player, String params) {
            if (params == null) return "";
            String key = params.contains("_") ? params.substring(0, params.indexOf('_')) : params;
            String args = params.contains("_") ? params.substring(params.indexOf('_') + 1) : "";
            BiFunction<Player, String, String> fn = handlers.get(key.toLowerCase());
            String result = (fn != null ? fn.apply(player, args) : "");
            return parseRecursive(player, result);
        }
    };

    /** 
     * 初始化：在插件 onEnable() 中调用一次。 
     * 自动注册 PAPI 扩展，并加载内置占位符。 
     */
    public static void initialize(Plugin pl) {
        plugin = pl;
        expansion.register();
        registerBuiltins();
    }

    /**
     * 注册一个占位符处理器。
     * @param key      占位符主键（不含 % 和 参数），如 "top"
     * @param resolver 处理逻辑 (player, args) → 返回字符串
     */
    public static void register(String key, BiFunction<Player, String, String> resolver) {
        handlers.put(key.toLowerCase(), resolver);
    }

    /**
     * 立即解析文本中的所有 %plugin_<...>% 占位符，支持多重嵌套。
     * @param player 可为 null，表示控制台
     * @param text   待解析文本
     * @return 完全展开后的文本
     */
    public static String parse(Player player, String text) {
        return parseRecursive(player, text);
    }

    // ================== 公共工具方法，供外部插件调用 ==================

    /**
     * 示例方法获取所有玩家积分并排序后，返回第 n 名的积分。
     * @param n 名次，从 1 开始
     * @return 积分，名次越界则返回 0
    public static int getPointsByRank(int n) {
        return DataManager.getPointsByRank(n);
    }
    **/

    /**
     * 示例方法返回第 n 名玩家的名称，并对名称中的占位符做递归解析。
     * @param player 用于嵌套解析时的上下文，允许为 null
     * @param n      名次，从 1 开始
     * @return 玩家名称或空字符串
    public static String getNameByRank(Player player, int n) {
        String name = DataManager.getNameByRank(n);
        return parseRecursive(player, name);
    }
    **/

    /**
     * 递归解析占位符，直到文本不再变化。
     * @param player 玩家，可为 null 表示控制台
     * @param text   原始包含 %...% 的文本
     * @return 最终解析结果
     */
    private static String parseRecursive(Player player, String text) {
        if (text == null) return "";
        String last, cur = text;
        do {
            last = cur;
            cur = PlaceholderAPI.setPlaceholders(player, last);
        } while (!cur.equals(last) && (cur.contains("%") || cur.contains("{")));
        return cur;
    }

    /**
     * 注册项目内置占位符
     * <p>下面所有示例均已注释，仅作模板说明。</p>
     */
    private static void registerBuiltins() {
        // 注册 %attributebinder_attr_<stat>% 返回玩家对应属性加成值
        register("attr", (player, args) -> {
            if (player == null) return "0";
            String stat = args.toUpperCase();
            double val = com.baimo.attributeBinder.manager.CacheManager.getAttribute(player.getUniqueId(), stat);
            return String.valueOf(val);
        });

        // %attributebinder_list% 返回玩家所有属性列表
        register("list", (player, args) -> {
            if (player == null) return "";
            java.util.Map<String, Double> map = com.baimo.attributeBinder.manager.CacheManager.aggregatedAttributes(player.getUniqueId());
            if (map.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            map.forEach((k, v) -> {
                boolean pc = com.baimo.attributeBinder.manager.CacheManager.isPercent(player.getUniqueId(), k);
                sb.append(k).append("=").append(v).append(pc ? "%" : "").append(", ");
            });
            if (sb.length() >= 2) sb.setLength(sb.length() - 2);
            return sb.toString();
        });
    }

    /**
     * 示例方法：供各功能模块在自己的 onEnable() 中调用，
     * 一次性注册该模块所有占位符处理器。
     *
     * <p>内部代码均以注释说明每一步作用，避免误解。</p>
     */
    public static void registerModulePlaceholders() {
        // 以下示例展示如何注册本模块的占位符，真实逻辑请替换注释中的内容
        /*
        // 注册玩家击杀数占位符 "%<id>_kills_<mobType>%"
        register("kills", (player, mobType) -> {
            // 1. 从你的模块管理类中获取该玩家对指定 mobType 的击杀数量
            //    例如：int count = KillManager.getKills(player.getName(), mobType);
            // 2. 将数字转换为字符串返回给 PAPI
            //    return String.valueOf(count);
            return "0"; // 占位：实际请替换为上面两行实现
        });

        // 注册玩家余额占位符 "%<id>_balance%"
        register("balance", (player, args) -> {
            // 1. 调用你的经济系统 API 获取玩家余额
            //    例如：double bal = EconomyAPI.getBalance(player.getName());
            // 2. 格式化为保留两位小数的字符串
            //    return String.format("%.2f", bal);
            return "0.00"; // 占位：实际请替换为上面两行实现
        });
        */
        // 如果还有其他占位符，如 level、rank 等，可继续用 register("key", resolver) 添加
    }
}
