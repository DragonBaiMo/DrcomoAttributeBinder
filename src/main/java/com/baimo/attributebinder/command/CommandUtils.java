package com.baimo.attributebinder.command;

import cn.drcomo.corelib.color.ColorUtil;
import com.baimo.attributebinder.cache.CacheManager;
import com.baimo.attributebinder.command.CommandUtils.ValuePair;
import com.baimo.attributebinder.config.LangManager;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.stat.type.DoubleStat;
import net.Indyuce.mmoitems.stat.type.ItemStat;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.*;
import java.util.stream.Collectors;

/**
 * CommandUtils — 存放自己常用的命令助手函数
 */
public final class CommandUtils {
    private CommandUtils() {}

    public static Player getPlayer(String name, CommandSender sender, LangManager lang) {
        Player player = Bukkit.getPlayer(name);
        if (player == null) {
            lang.send(sender, "error-player-not-found", Map.of("player", name));
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
            lang.send(sender, errorKey);
            return null;
        }
    }

    public static String formatValue(double val, boolean percent) {
        return percent ? val + "%" : (val >= 0 ? "+" + val : String.valueOf(val));
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
        String keyId = CacheManager.DEFAULT_KEY;
        boolean memoryOnly = false;
        long expireTicks = -1;
        if (args.length >= 5) {
            keyId = args[4];
        }
        if (args.length >= 6) {
            try {
                memoryOnly = Boolean.parseBoolean(args[5]);
            } catch (Exception e) {
                lang.send(sender, "command-give-usage");
                return null;
            }
        }
        if (args.length == 7) {
            try {
                expireTicks = Long.parseLong(args[6]);
            } catch (NumberFormatException e) {
                lang.send(sender, "command-give-invalid-number");
                return null;
            }
        }
        return new OptParams(keyId, memoryOnly, expireTicks);
    }

    public static OptParams parseReplaceParams(String[] args, CommandSender sender, LangManager lang) {
        String keyId = CacheManager.DEFAULT_KEY;
        boolean memoryOnly = false;
        long expireTicks = -1;
        if (args.length >= 5) {
            keyId = args[4];
        }
        if (args.length >= 6) {
            try {
                memoryOnly = Boolean.parseBoolean(args[5]);
            } catch (Exception e) {
                lang.send(sender, "command-replace-usage");
                return null;
            }
        }
        if (args.length == 7) {
            try {
                expireTicks = Long.parseLong(args[6]);
            } catch (NumberFormatException e) {
                lang.send(sender, "command-give-invalid-number");
                return null;
            }
        }
        return new OptParams(keyId, memoryOnly, expireTicks);
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
        public OptParams(String keyId, boolean memoryOnly, long expireTicks) {
            this.keyId = keyId;
            this.memoryOnly = memoryOnly;
            this.expireTicks = expireTicks;
        }
    }
}
