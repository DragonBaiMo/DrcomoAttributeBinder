package com.baimo.attributeBinder.command;

import com.baimo.attributeBinder.manager.*;
import com.baimo.attributeBinder.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import com.baimo.attributeBinder.AttributeBinder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import org.bukkit.util.StringUtil;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.stat.type.ItemStat;
import net.Indyuce.mmoitems.stat.type.DoubleStat;

public class AttributeBinderCommand implements CommandExecutor, TabCompleter {

    private final LangManager lang = LangManager.get();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ColorUtil.translateColors(lang.get("command-main-usage")));
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "give":
                if (!sender.hasPermission("attributebinder.give")) {
                    sender.sendMessage(ColorUtil.translateColors(LangManager.get().get("error-no-permission")));
                    return true;
                }
                handleGive(sender, args);
                break;
            case "remove":
                if (!sender.hasPermission("attributebinder.remove")) {
                    sender.sendMessage(ColorUtil.translateColors(LangManager.get().get("error-no-permission")));
                    return true;
                }
                handleremove(sender, args);
                break;
            case "list":
                if (!sender.hasPermission("attributebinder.list")) {
                    sender.sendMessage(ColorUtil.translateColors(LangManager.get().get("error-no-permission")));
                    return true;
                }
                handleList(sender, args);
                break;
            case "replace":
                if (!sender.hasPermission("attributebinder.replace")) {
                    sender.sendMessage(ColorUtil.translateColors(lang.get("error-no-permission")));
                    return true;
                }
                handleReplace(sender, args);
                break;
            case "reload":
                if (!sender.hasPermission("attributebinder.admin")) {
                    sender.sendMessage(ColorUtil.translateColors(lang.get("error-no-permission")));
                    return true;
                }
                ConfigManager.get().reload();
                LangManager.get().reload();
                AttributeBinder.getInstance().resetFlushTask(ConfigManager.get().getSyncIntervalMinutes());
                AttributeBinder.getInstance().updateDebugLevel();
                sender.sendMessage(ColorUtil.translateColors(LangManager.get().get("command-reload-success")));
                break;
            case "flush":
                if (!sender.hasPermission("attributebinder.admin")) {
                    sender.sendMessage(ColorUtil.translateColors(lang.get("error-no-permission")));
                    return true;
                }
                new com.baimo.attributeBinder.task.FlushTask().runTaskAsynchronously(AttributeBinder.getInstance());
                sender.sendMessage(ColorUtil.translateColors(LangManager.get().get("command-flush-success")));
                break;
            case "help":
                handleHelp(sender);
                break;
            default:
                sender.sendMessage(ColorUtil.translateColors("&c未知子命令"));
        }
        return true;
    }

    private void handleGive(CommandSender sender, String[] args) {
        // 参数格式: give <玩家> <属性> <数值> [KeyID] [memoryOnly] <过期时长ticks>
        if (args.length < 5 || args.length > 7) {
            sender.sendMessage(ColorUtil.translateColors(lang.get("command-give-usage")));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ColorUtil.translateColors(lang.get("error-player-not-found").replace("{player}", args[1])));
            return;
        }
        String stat = args[2].toUpperCase();
        boolean percent = false;
        String valueStr = args[3];
        if (valueStr.endsWith("%")) {
            percent = true;
            valueStr = valueStr.substring(0, valueStr.length()-1);
        }
        // 解析可选参数: [KeyID] [memoryOnly] <expireTicks>
        int len = args.length;
        long expireTicks;
        try {
            expireTicks = Long.parseLong(args[len - 1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ColorUtil.translateColors(lang.get("command-give-invalid-number")));
            return;
        }
        String keyId = com.baimo.attributeBinder.manager.CacheManager.DEFAULT_KEY;
        boolean memoryOnly = false;
        if (len == 6) {
            String p = args[4];
            if ("true".equalsIgnoreCase(p) || "false".equalsIgnoreCase(p)) {
                memoryOnly = Boolean.parseBoolean(p);
            } else {
                keyId = p;
            }
        } else if (len == 7) {
            keyId = args[4];
            memoryOnly = Boolean.parseBoolean(args[5]);
        }
        double delta;
        try {
            delta = Double.parseDouble(valueStr);
        } catch (NumberFormatException e) {
            sender.sendMessage(ColorUtil.translateColors(lang.get("command-give-invalid-number")));
            return;
        }
        UUID uuid = target.getUniqueId();
        // 在处理前，检查是否与旧值相同，避免无效操作
        double oldVal = com.baimo.attributeBinder.manager.CacheManager.getAttribute(uuid, stat, keyId);
        double newVal = oldVal + delta;
        if (Double.compare(oldVal, newVal) == 0) {
            sender.sendMessage(ColorUtil.translateColors(lang.get("command-no-change")));
            return;
        }
        com.baimo.attributeBinder.manager.CacheManager.setAttribute(uuid, stat, keyId, newVal, percent, memoryOnly, expireTicks);
        com.baimo.attributeBinder.manager.AttributeApplier.apply(uuid, stat, keyId, newVal, percent);
        String valStr = newVal + (percent ? "%" : "");
        sender.sendMessage(ColorUtil.translateColors(lang.get("command-give-success")
                .replace("{player}", target.getName())
                .replace("{attribute}", stat)
                .replace("{value}", valStr)
                .replace("{key}", keyId)));
    }

    private void handleremove(CommandSender sender, String[] args) {
        if (args.length < 3 || args.length > 4) {
            sender.sendMessage(ColorUtil.translateColors(lang.get("command-remove-usage")));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ColorUtil.translateColors(lang.get("error-player-not-found").replace("{player}", args[1])));
            return;
        }
        String stat = args[2];
        String keyId = args.length == 4 ? args[3] : null; // null 表示全部 key
        // 处理 remove key 子命令，删除指定 keyId 下所有属性
        if ("key".equalsIgnoreCase(stat)) {
            if (keyId == null) {
                sender.sendMessage(ColorUtil.translateColors(lang.get("command-remove-usage")));
                return;
            }
            // 遍历所有属性 statName，移除该 keyId 下对应属性
            UUID uuidKey = target.getUniqueId();
            com.baimo.attributeBinder.manager.CacheManager.snapshot()
                    .getOrDefault(uuidKey, Collections.emptyMap())
                    .keySet()
                    .forEach(statName -> {
                        com.baimo.attributeBinder.manager.CacheManager.removeAttribute(uuidKey, statName, keyId);
                        AttributeApplier.remove(uuidKey, statName, keyId);
                    });
            sender.sendMessage(ColorUtil.translateColors(lang.get("command-remove-key-success")
                    .replace("{player}", target.getName())
                    .replace("{key}", keyId)));
            return;
        }
        UUID uuid = target.getUniqueId();
        if ("all".equalsIgnoreCase(stat)) {
            // 删除该玩家全部属性
            com.baimo.attributeBinder.manager.CacheManager.clear(uuid);
            AttributeApplier.removeAll(uuid);
            sender.sendMessage(ColorUtil.translateColors(lang.get("command-remove-all-success").replace("{player}", target.getName())));
        } else {
            com.baimo.attributeBinder.manager.CacheManager.removeAttribute(uuid, stat, keyId);
            if (keyId == null) {
                AttributeApplier.remove(uuid, stat);
            } else {
                AttributeApplier.remove(uuid, stat, keyId);
            }
            sender.sendMessage(ColorUtil.translateColors(lang.get("command-remove-success")
                    .replace("{player}", target.getName())
                    .replace("{attribute}", stat)
                    .replace("{key}", keyId == null ? "*" : keyId)));
        }
    }

    private void handleList(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(ColorUtil.translateColors(lang.get("command-list-usage")));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ColorUtil.translateColors(lang.get("error-player-not-found").replace("{player}", args[1])));
            return;
        }
        UUID uuid = target.getUniqueId();

        // 获取玩家完整缓存快照，并按 keyId 重新分组
        Map<String, Map<String, com.baimo.attributeBinder.manager.CacheManager.Entry>> statMap =
                com.baimo.attributeBinder.manager.CacheManager.snapshot().getOrDefault(uuid, Collections.emptyMap());
        Map<String, Map<String, com.baimo.attributeBinder.manager.CacheManager.Entry>> byKey = new LinkedHashMap<>();
        statMap.forEach((stat, keyMap) -> keyMap.forEach((keyId, entry) -> {
            byKey.computeIfAbsent(keyId, k -> new LinkedHashMap<>()).put(stat, entry);
        }));

        if (byKey.isEmpty()) {
            sender.sendMessage(ColorUtil.translateColors(lang.get("command-list-empty").replace("{player}", target.getName())));
            return;
        }

        // 头部
        sender.sendMessage(ColorUtil.translateColors(lang.get("command-list-header").replace("{player}", target.getName())));

        // 逐 keyId 输出明细
        byKey.forEach((keyId, attrMap) -> {
            sender.sendMessage(ColorUtil.translateColors(lang.get("command-list-key-header").replace("{key}", keyId)));
            attrMap.forEach((stat, entry) -> {
                double value = entry.getValue();
                boolean percent = entry.isPercent();
                String valStr;
                if (percent) {
                    valStr = value + "%";
                } else {
                    valStr = (value >= 0 ? "+" + value : String.valueOf(value));
                }
                sender.sendMessage(ColorUtil.translateColors(lang.get("command-list-item")
                        .replace("{attribute}", stat)
                        .replace("{value}", valStr)));
            });
        });
    }

    private void handleReplace(CommandSender sender, String[] args) {
        // 参数格式: replace <玩家> <属性> <数值> [KeyID] [memoryOnly] <过期时长ticks>
        if (args.length < 5 || args.length > 7) {
            sender.sendMessage(ColorUtil.translateColors(lang.get("command-replace-usage")));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ColorUtil.translateColors(lang.get("error-player-not-found").replace("{player}", args[1])));
            return;
        }
        String stat = args[2].toUpperCase();
        boolean percent = false;
        String valueStr = args[3];
        if (valueStr.endsWith("%")) {
            percent = true;
            valueStr = valueStr.substring(0, valueStr.length()-1);
        }
        // 解析可选参数: [KeyID] [memoryOnly] <expireTicks>
        int lenR = args.length;
        long expireTicksR;
        try {
            expireTicksR = Long.parseLong(args[lenR - 1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ColorUtil.translateColors(lang.get("command-give-invalid-number")));
            return;
        }
        String keyIdR = com.baimo.attributeBinder.manager.CacheManager.DEFAULT_KEY;
        boolean memoryOnlyR = false;
        if (lenR == 6) {
            String p = args[4];
            if ("true".equalsIgnoreCase(p) || "false".equalsIgnoreCase(p)) {
                memoryOnlyR = Boolean.parseBoolean(p);
            } else {
                keyIdR = p;
            }
        } else if (lenR == 7) {
            keyIdR = args[4];
            memoryOnlyR = Boolean.parseBoolean(args[5]);
        }
        double value;
        try {
            value = Double.parseDouble(valueStr);
        } catch (NumberFormatException e) {
            sender.sendMessage(ColorUtil.translateColors(lang.get("command-give-invalid-number")));
            return;
        }
        UUID uuid = target.getUniqueId();
        // 检查旧值 与 新值 相同，则跳过
        double oldVal = com.baimo.attributeBinder.manager.CacheManager.getAttribute(uuid, stat, keyIdR);
        if (Double.compare(oldVal, value) == 0) {
            sender.sendMessage(ColorUtil.translateColors("&e属性未改变，无需操作"));
            return;
        }
        com.baimo.attributeBinder.manager.CacheManager.setAttribute(uuid, stat, keyIdR, value, percent, memoryOnlyR, expireTicksR);
        com.baimo.attributeBinder.manager.AttributeApplier.apply(uuid, stat, keyIdR, value, percent);
        String valStr = value + (percent ? "%" : "");
        sender.sendMessage(ColorUtil.translateColors(lang.get("command-replace-success")
                .replace("{player}", target.getName())
                .replace("{attribute}", stat)
                .replace("{value}", valStr)
                .replace("{key}", keyIdR)));
    }

    private void handleHelp(CommandSender sender) {
        // 统一帮助信息，根据 lang.yml 中的各条 usage 组装
        sender.sendMessage(ColorUtil.translateColors("&e========== AttributeBinder 帮助 =========="));
        sender.sendMessage(ColorUtil.translateColors(lang.get("command-give-usage")));
        sender.sendMessage(ColorUtil.translateColors(lang.get("command-replace-usage")));
        sender.sendMessage(ColorUtil.translateColors(lang.get("command-remove-usage")));
        sender.sendMessage(ColorUtil.translateColors(lang.get("command-list-usage")));
        sender.sendMessage(ColorUtil.translateColors("%prefix% &e/attributebinder reload &7- 重载配置/语言".replace("%prefix%", lang.get("prefix"))));
        sender.sendMessage(ColorUtil.translateColors("%prefix% &e/attributebinder flush &7- 立即保存数据".replace("%prefix%", lang.get("prefix"))));
        sender.sendMessage(ColorUtil.translateColors("&e======================================"));
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.asList("give", "replace", "remove", "list", "reload", "flush", "help").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        String sub = args[0].toLowerCase();

        // 第二个参数：玩家名
        if (args.length == 2 && Arrays.asList("give", "replace", "remove", "list").contains(sub)) {
            List<String> players = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList());
            return StringUtil.copyPartialMatches(args[1], players, new ArrayList<>()).stream()
                    .sorted()
                    .collect(Collectors.toList());
        }

        // 第三个参数：属性 ID（仅 give / remove / replace）
        if (args.length == 3 && Arrays.asList("give", "replace", "remove").contains(sub)) {
            List<String> suggestions = new ArrayList<>();
            try {
                Collection<ItemStat<?, ?>> stats = MMOItems.plugin.getStats().getAll();
                for (ItemStat<?, ?> stat : stats) {
                    if (stat instanceof DoubleStat) {
                        suggestions.add(stat.getId());
                    }
                }
            } catch (Exception ignored) {
                // MMOItems 未就绪或获取失败
            }

            // remove 额外支持 all 和 key
            if ("remove".equals(sub)) {
                suggestions.add("all");
                suggestions.add("key");
            }
            return StringUtil.copyPartialMatches(args[2], suggestions, new ArrayList<>()).stream()
                    .sorted()
                    .collect(Collectors.toList());
        }

        // 第四个参数：keyId（可选，不做提示）
        return java.util.Collections.emptyList();
    }
}