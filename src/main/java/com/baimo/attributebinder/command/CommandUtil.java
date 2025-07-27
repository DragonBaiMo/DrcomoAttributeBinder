package com.baimo.attributebinder.command;

import com.baimo.attributebinder.manager.CacheManager;
import com.baimo.attributebinder.manager.CacheManager.Entry;
import com.baimo.attributebinder.manager.LangManager;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.stat.type.DoubleStat;
import net.Indyuce.mmoitems.stat.type.ItemStat;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 命令工具类，提供常用解析与提示方法。
 */
public final class CommandUtil {
    private static final LangManager LANG = LangManager.get();

    private CommandUtil() {}

    static boolean checkPermission(CommandSender sender, String perm, String langKey) {
        if (!sender.hasPermission(perm)) {
            LANG.send(sender, langKey);
            return false;
        }
        return true;
    }

    @Nullable
    static Player getPlayer(String name, CommandSender sender) {
        Player player = Bukkit.getPlayer(name);
        if (player == null) {
            LANG.send(sender, "error-player-not-found", Map.of("player", name));
        }
        return player;
    }

    static ValuePair parseValue(String raw, CommandSender sender, String errorKey) {
        boolean percent = false;
        if (raw.endsWith("%")) {
            percent = true;
            raw = raw.substring(0, raw.length() - 1);
        }
        try {
            double val = Double.parseDouble(raw);
            return new ValuePair(val, percent);
        } catch (NumberFormatException e) {
            LANG.send(sender, errorKey);
            return null;
        }
    }

    static void sendGiveSuccess(CommandSender sender, String player, String stat, String value,
                                String key, boolean memoryOnly, long expireTicks) {
        LANG.send(sender, "command-give-success", Map.of(
                "player", player,
                "attribute", stat,
                "value", value,
                "key", key,
                "memoryOnly", String.valueOf(memoryOnly),
                "expireTicks", String.valueOf(expireTicks)
        ));
    }

    static void sendReplaceSuccess(CommandSender sender, String player, String stat,
                                   String value, String key) {
        LANG.send(sender, "command-replace-success", Map.of(
                "player", player,
                "attribute", stat,
                "value", value,
                "key", key
        ));
    }

    static String formatValue(double val, boolean percent) {
        return percent ? val + "%" : (val >= 0 ? "+" + val : String.valueOf(val));
    }

    static LinkedHashMap<String, Map<String, Entry>> groupSnapshotByKey(UUID uuid) {
        Map<String, Map<String, Entry>> statMap = CacheManager.snapshot(uuid);
        LinkedHashMap<String, Map<String, Entry>> byKey = new LinkedHashMap<>();
        statMap.forEach((stat, keyMap) ->
                keyMap.forEach((key, entry) ->
                        byKey.computeIfAbsent(key, k -> new LinkedHashMap<>()).put(stat, entry)
                )
        );
        return byKey;
    }

    static List<String> getStatSuggestions() {
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

    static OptParams parseGiveParams(String[] args, CommandSender sender) {
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
                LANG.send(sender, "command-give-usage");
                return null;
            }
        }
        if (args.length == 7) {
            try {
                expireTicks = Long.parseLong(args[6]);
            } catch (NumberFormatException e) {
                LANG.send(sender, "command-give-invalid-number");
                return null;
            }
        }
        return new OptParams(keyId, memoryOnly, expireTicks);
    }

    static OptParams parseReplaceParams(String[] args, CommandSender sender) {
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
                LANG.send(sender, "command-replace-usage");
                return null;
            }
        }
        if (args.length == 7) {
            try {
                expireTicks = Long.parseLong(args[6]);
            } catch (NumberFormatException e) {
                LANG.send(sender, "command-give-invalid-number");
                return null;
            }
        }
        return new OptParams(keyId, memoryOnly, expireTicks);
    }

    static class ValuePair {
        final double value;
        final boolean percent;
        ValuePair(double value, boolean percent) {
            this.value = value;
            this.percent = percent;
        }
    }

    static class OptParams {
        final String keyId;
        final boolean memoryOnly;
        final long expireTicks;
        OptParams(String keyId, boolean memoryOnly, long expireTicks) {
            this.keyId = keyId;
            this.memoryOnly = memoryOnly;
            this.expireTicks = expireTicks;
        }
    }
}
