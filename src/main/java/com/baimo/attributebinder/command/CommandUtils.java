package com.baimo.attributebinder.command;
import com.baimo.attributebinder.cache.CacheManager;
import com.baimo.attributebinder.config.LangManager;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.stat.type.DoubleStat;
import net.Indyuce.mmoitems.stat.type.ItemStat;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * CommandUtils — 存放自己常用的命令助手函数
 */
public final class CommandUtils {
    private CommandUtils() {}

    // 每次命令调用时记录原始完整指令，便于子命令在用法错误时回显
    private static final ThreadLocal<String> TL_ORIGINAL_COMMAND = new ThreadLocal<>();

    public static void setInvocation(String label, String[] args) {
        StringBuilder sb = new StringBuilder();
        sb.append("/").append(label);
        if (args != null && args.length > 0) {
            sb.append(" ").append(String.join(" ", args));
        }
        TL_ORIGINAL_COMMAND.set(sb.toString());
    }

    public static String getOriginalCommand() {
        String cmd = TL_ORIGINAL_COMMAND.get();
        return cmd == null ? "[未知指令]" : cmd;
    }

    /**
     * 发送错误消息前，统一先回显原始指令，便于用户对照纠正参数。
     */
    public static void sendErrorWithOriginal(LangManager lang, CommandSender sender, String key) {
        sender.sendMessage("[调试] 原始指令: " + getOriginalCommand());
        lang.send(sender, key);
    }

    /**
     * 发送带参数的错误消息前，统一先回显原始指令。
     */
    public static void sendErrorWithOriginal(LangManager lang, CommandSender sender, String key, Map<String, String> params) {
        sender.sendMessage("[调试] 原始指令: " + getOriginalCommand());
        lang.send(sender, key, params);
    }

    /**
     * 仅向玩家发送成功类消息；若为控制台（非玩家），则不发送。
     */
    public static void sendSuccess(LangManager lang, CommandSender sender, String key) {
        if (sender instanceof Player) {
            lang.send(sender, key);
        }
    }

    /**
     * 仅向玩家发送成功类消息（带参数）；若为控制台（非玩家），则不发送。
     */
    public static void sendSuccess(LangManager lang, CommandSender sender, String key, Map<String, String> params) {
        if (sender instanceof Player) {
            lang.send(sender, key, params);
        }
    }

    public static Player getPlayer(String name, CommandSender sender, LangManager lang) {
        Player player = Bukkit.getPlayer(name);
        if (player == null) {
            sendErrorWithOriginal(lang, sender, "cmd.error.player_not_found", Map.of("player", name));
        }
        return player;
    }

    public static ValuePair parseValue(String raw, CommandSender sender, String errorKey, LangManager lang) {
        boolean percent = false;
        if (raw.endsWith("%")) {
            percent = true;
            raw = raw.substring(0, raw.length() - 1);
        }
        try {
            double val = Double.parseDouble(raw);
            return new ValuePair(val, percent);
        } catch (NumberFormatException e) {
            sendErrorWithOriginal(lang, sender, errorKey);
            return null;
        }
    }

    public static String formatValue(double val, boolean percent) {
        if (percent) {
            // 百分比显示：直接显示输入的数值加%号
            return String.format("%s%.1f%%", val >= 0 ? "+" : "", val);
        } else {
            return val >= 0 ? "+" + val : String.valueOf(val);
        }
    }

    public static LinkedHashMap<String, Map<String, CacheManager.Entry>> groupSnapshotByKey(UUID uuid) {
        Map<String, Map<String, CacheManager.Entry>> statMap = CacheManager.snapshot(uuid);
        LinkedHashMap<String, Map<String, CacheManager.Entry>> byKey = new LinkedHashMap<>();
        statMap.forEach((stat, keyMap) ->
                keyMap.forEach((key, entry) ->
                        byKey.computeIfAbsent(key, k -> new LinkedHashMap<>()).put(stat, entry)
                )
        );
        return byKey;
    }

    public static List<String> getStatSuggestions() {
        List<String> list = new ArrayList<>();
        try {
            Collection<ItemStat<?, ?>> stats = MMOItems.plugin.getStats().getAll();
            for (ItemStat<?, ?> stat : stats) {
                if (stat instanceof DoubleStat) {
                    list.add(stat.getId());
                }
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public static OptParams parseGiveParams(String[] args, CommandSender sender, LangManager lang) {
        // 新结构：-- 开头的参数优先解析；旧结构：保留兼容，转交给弃用方法解析
        String keyId = null;                 // 若未通过 -- 指定，则走旧结构
        Boolean memoryOnly = null;           // 三态：null=未指定
        Long expireTicks = null;             // null=未指定；最终默认-1永久
        Boolean replaceExpire = null;        // null=未指定
        double maxValue = -1D;               // 默认无限制

        // 解析 -- 参数
        for (int i = 4; i < args.length; i++) {
            String token = args[i];
            if (!token.startsWith("--")) continue;
            String opt = token.substring(2);

            // --max | --mx
            String v;
            v = extractOptionValue(opt, "max", "mx");
            if (v != null) {
                try {
                    String mx = v.endsWith("%") ? v.substring(0, v.length() - 1) : v;
                    maxValue = Double.parseDouble(mx);
                } catch (NumberFormatException e) {
                    sendErrorWithOriginal(lang, sender, "cmd.error.invalid_number");
                    return null;
                }
                continue;
            }

            // --mode | --md
            v = extractOptionValue(opt, "mode", "md");
            if (v != null) {
                if ("replace".equalsIgnoreCase(v)) {
                    replaceExpire = true;
                } else if ("give".equalsIgnoreCase(v)) {
                    replaceExpire = false;
                } else {
                    sendErrorWithOriginal(lang, sender, "cmd.usage.give");
                    return null;
                }
                continue;
            }

            // --key | --k
            v = extractOptionValue(opt, "key", "k");
            if (v != null) {
                keyId = v;
                continue;
            }

            // --memoryOnly | --memory-only | --memory | --mem | --m
            v = extractOptionValue(opt, "memoryOnly", "memory-only", "memory", "mem", "m");
            if (v != null) {
                if (v.isEmpty()) {
                    memoryOnly = true; // 无显式值时，视为 true
                } else {
                    memoryOnly = Boolean.parseBoolean(v);
                }
                continue;
            }

            // --expire | --exp | --e
            v = extractOptionValue(opt, "expire", "exp", "e");
            if (v != null) {
                try {
                    expireTicks = Long.parseLong(v);
                } catch (NumberFormatException e) {
                    sendErrorWithOriginal(lang, sender, "cmd.error.invalid_number");
                    return null;
                }
                continue;
            }
        }

        // 收集旧结构剩余参数（非 --），交给弃用方法解析
        List<String> legacy = new ArrayList<>();
        for (int i = 4; i < args.length; i++) {
            if (!args[i].startsWith("--")) legacy.add(args[i]);
        }
        OptParams legacyParams = parseGiveParamsLegacy(legacy, sender, lang);
        if (legacyParams == null) {
            // 旧结构解析失败时，已在旧方法内发送了对应错误提示，这里直接中断
            return null;
        }

        // 合并优先级：新结构 -- 覆盖旧结构
        String finalKey = (keyId != null ? keyId : legacyParams.keyId);
        boolean finalMemory = (memoryOnly != null ? memoryOnly : legacyParams.memoryOnly);
        long finalExpire = (expireTicks != null ? expireTicks : (legacyParams.expireTicks >= 0 ? legacyParams.expireTicks : -1L));
        boolean finalReplace = (replaceExpire != null ? replaceExpire : legacyParams.replaceExpire);

        return new OptParams(finalKey, finalMemory, finalExpire, finalReplace, maxValue);
    }

    public static OptParams parseReplaceParams(String[] args, CommandSender sender, LangManager lang) {
        String keyId = null;                 // 三态：null 表示未通过 -- 指定
        Boolean memoryOnly = null;           // 三态：null 表示未指定
        Long expireTicks = null;             // 三态：null 表示未指定；最终默认-1永久
        Boolean replaceExpire = null;        // 三态：null 表示未指定；replace 命令默认 true（覆盖）
        double maxValue = -1D;               // 默认无限制

        // 新结构 -- 参数优先
        for (int i = 4; i < args.length; i++) {
            String token = args[i];
            if (!token.startsWith("--")) continue;
            String opt = token.substring(2);

            // --max | --mx
            String v;
            v = extractOptionValue(opt, "max", "mx");
            if (v != null) {
                try {
                    String mx = v.endsWith("%") ? v.substring(0, v.length() - 1) : v;
                    maxValue = Double.parseDouble(mx);
                } catch (NumberFormatException e) {
                    sendErrorWithOriginal(lang, sender, "cmd.error.invalid_number");
                    return null;
                }
                continue;
            }

            // --mode | --md
            v = extractOptionValue(opt, "mode", "md");
            if (v != null) {
                if ("replace".equalsIgnoreCase(v)) {
                    replaceExpire = true;
                } else if ("give".equalsIgnoreCase(v)) {
                    replaceExpire = false;
                } else {
                    sendErrorWithOriginal(lang, sender, "cmd.usage.replace");
                    return null;
                }
                continue;
            }
            // --key | --k
            v = extractOptionValue(opt, "key", "k");
            if (v != null) {
                keyId = v;
                continue;
            }
            // --memoryOnly | --memory-only | --memory | --mem | --m
            v = extractOptionValue(opt, "memoryOnly", "memory-only", "memory", "mem", "m");
            if (v != null) {
                if (v.isEmpty()) {
                    memoryOnly = true;
                } else {
                    memoryOnly = Boolean.parseBoolean(v);
                }
                continue;
            }
            // --expire | --exp | --e
            v = extractOptionValue(opt, "expire", "exp", "e");
            if (v != null) {
                try {
                    expireTicks = Long.parseLong(v);
                } catch (NumberFormatException e) {
                    sendErrorWithOriginal(lang, sender, "cmd.error.invalid_number");
                    return null;
                }
                continue;
            }
        }

        // 旧结构（位置参数）
        OptParams legacy = parseReplaceParamsLegacy(collectLegacy(args, 4), sender, lang);
        if (legacy == null) {
            // 旧结构解析失败时，已在旧方法内发送了对应错误提示，这里直接中断
            return null;
        }

        String finalKey = (keyId != null ? keyId : legacy.keyId);
        boolean finalMemoryOnly = (memoryOnly != null ? memoryOnly : legacy.memoryOnly);
        long finalExpire = (expireTicks != null ? expireTicks : (legacy.expireTicks >= 0 ? legacy.expireTicks : -1L));
        boolean finalReplace = (replaceExpire != null ? replaceExpire : legacy.replaceExpire);

        return new OptParams(finalKey, finalMemoryOnly, finalExpire, finalReplace, maxValue);
    }

    /**
     * 从 opt 字符串中匹配并提取选项值，支持 "name=value"、"name:value"、以及 "name"（无值）形式。
     * 若匹配成功：
     *  - 返回值字符串；
     *  - 当无显式值（仅有名称）时返回空字符串 ""；
     * 若均未匹配，返回 null。
     */
    private static String extractOptionValue(String opt, String... names) {
        for (String name : names) {
            String prefixEq = name + "=";
            String prefixColon = name + ":";
            if (opt.startsWith(prefixEq)) {
                return opt.substring(prefixEq.length());
            }
            if (opt.startsWith(prefixColon)) {
                return opt.substring(prefixColon.length());
            }
            if (opt.equals(name)) {
                return ""; // 仅名称，无值
            }
        }
        return null;
    }

    /** 数值和百分比 */
    public static class ValuePair {
        public final double value;
        public final boolean percent;
        public ValuePair(double value, boolean percent) {
            this.value = value;
            this.percent = percent;
        }
    }

    /** 选参 */
    public static class OptParams {
        public final String keyId;
        public final boolean memoryOnly;
        public final long expireTicks;
        public final boolean replaceExpire;
        public final double maxValue; // 叠加总值上限
        public OptParams(String keyId, boolean memoryOnly, long expireTicks) {
            this(keyId, memoryOnly, expireTicks, false, -1D);
        }
        public OptParams(String keyId, boolean memoryOnly, long expireTicks, boolean replaceExpire) {
            this(keyId, memoryOnly, expireTicks, replaceExpire, -1D);
        }
        public OptParams(String keyId, boolean memoryOnly, long expireTicks, boolean replaceExpire, double maxValue) {
            this.keyId = keyId;
            this.memoryOnly = memoryOnly;
            this.expireTicks = expireTicks;
            this.replaceExpire = replaceExpire;
            this.maxValue = maxValue;
        }
    }

    /**
     * 仅解析旧结构位置参数：[KeyID] [memoryOnly] [ticks[:replace|give]]
     * 已弃用：仅供向后兼容，建议使用 -- 参数。
     */
    @Deprecated
    private static OptParams parseGiveParamsLegacy(List<String> legacy, CommandSender sender, LangManager lang) {
        String keyId = CacheManager.DEFAULT_KEY;
        boolean memoryOnly = false;
        long expireTicks = -1L; // 默认永久
        boolean replaceExpire = false;

        int idx = 0;
        if (idx < legacy.size()) {
            keyId = legacy.get(idx++);
        }
        if (idx < legacy.size()) {
            String t = legacy.get(idx);
            if ("true".equalsIgnoreCase(t) || "false".equalsIgnoreCase(t)) {
                memoryOnly = Boolean.parseBoolean(t);
                idx++;
            }
        }
        if (idx < legacy.size()) {
            String t = legacy.get(idx);
            if (t.contains(":")) {
                String[] parts = t.split(":", 2);
                try {
                    // 兼容小数：先按 Double 解析，再四舍五入为 tick
                    expireTicks = Math.round(Double.parseDouble(parts[0]));
                } catch (NumberFormatException e) {
                    sendErrorWithOriginal(lang, sender, "cmd.error.invalid_number");
                    return null;
                }
                if (parts.length > 1) {
                    if ("replace".equalsIgnoreCase(parts[1])) replaceExpire = true;
                    else if ("give".equalsIgnoreCase(parts[1])) replaceExpire = false;
                    else {
                        sendErrorWithOriginal(lang, sender, "cmd.usage.give");
                        return null;
                    }
                }
            } else {
                try {
                    // 兼容小数：先按 Double 解析，再四舍五入为 tick
                    expireTicks = Math.round(Double.parseDouble(t));
                } catch (NumberFormatException e) {
                    sendErrorWithOriginal(lang, sender, "cmd.error.invalid_number");
                    return null;
                }
            }
        }

        if (legacy.size() >= 3) {
            // 第7位旧形式，提示弃用
            lang.send(sender, "cmd.deprecated.expire_arg");
        }
        return new OptParams(keyId, memoryOnly, expireTicks, replaceExpire);
    }

    @Deprecated
    private static OptParams parseReplaceParamsLegacy(List<String> legacy, CommandSender sender, LangManager lang) {
        String keyId = CacheManager.DEFAULT_KEY;
        boolean memoryOnly = false;
        long expireTicks = -1L; // 默认永久
        boolean replaceExpire = true; // replace 默认覆盖

        int idx = 0;
        if (idx < legacy.size()) keyId = legacy.get(idx++);
        if (idx < legacy.size()) {
            String t = legacy.get(idx);
            if ("true".equalsIgnoreCase(t) || "false".equalsIgnoreCase(t)) {
                memoryOnly = Boolean.parseBoolean(t);
                idx++;
            }
        }
        if (idx < legacy.size()) {
            String t = legacy.get(idx);
            if (t.contains(":")) {
                String[] parts = t.split(":", 2);
                try {
                    // 兼容小数：先按 Double 解析，再四舍五入为 tick
                    expireTicks = Math.round(Double.parseDouble(parts[0]));
                } catch (NumberFormatException e) {
                    sendErrorWithOriginal(lang, sender, "cmd.error.invalid_number");
                    return null;
                }
                if (parts.length > 1) {
                    if ("replace".equalsIgnoreCase(parts[1])) replaceExpire = true;
                    else if ("give".equalsIgnoreCase(parts[1])) replaceExpire = false;
                    else {
                        sendErrorWithOriginal(lang, sender, "cmd.usage.replace");
                        return null;
                    }
                }
            } else {
                try {
                    // 兼容小数：先按 Double 解析，再四舍五入为 tick
                    expireTicks = Math.round(Double.parseDouble(t));
                } catch (NumberFormatException e) {
                    sendErrorWithOriginal(lang, sender, "cmd.error.invalid_number");
                    return null;
                }
            }
        }
        return new OptParams(keyId, memoryOnly, expireTicks, replaceExpire);
    }

    private static List<String> collectLegacy(String[] args, int startIndex) {
        List<String> legacy = new ArrayList<>();
        for (int i = startIndex; i < args.length; i++) {
            if (!args[i].startsWith("--")) legacy.add(args[i]);
        }
        return legacy;
    }
}
