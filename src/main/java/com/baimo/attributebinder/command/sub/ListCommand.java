package com.baimo.attributebinder.command.sub;

import cn.drcomo.corelib.color.ColorUtil;
import com.baimo.attributebinder.cache.CacheManager;
import com.baimo.attributebinder.cache.CacheManager.Entry;
import com.baimo.attributebinder.command.CommandUtils;
import com.baimo.attributebinder.config.LangManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

/**
 * ListCommand — 处理 /ab list 命令
 */
public class ListCommand implements SubCommand {
    private final LangManager lang = LangManager.get();

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2 || args.length > 5) {
            lang.send(sender, "cmd.usage.list");
            return true;
        }
        Player target = CommandUtils.getPlayer(args[1], sender, lang);
        if (target == null) return true;

        String mode = args.length >= 3 ? args[2].toLowerCase() : "default";
        switch (mode) {
            case "all" -> handleListAll(sender, target);
            case "keys" -> {
                if (args.length < 4) {
                    lang.send(sender, "cmd.usage.list_keys");
                    return true;
                }
                handleListKeys(sender, target, args[3]);
            }
            case "keyattrs" -> {
                if (args.length < 4) {
                    lang.send(sender, "cmd.usage.list_keyattrs");
                    return true;
                }
                handleListKeyAttrs(sender, target, args[3]);
            }
            case "inspect" -> {
                if (args.length < 4) {
                    lang.send(sender, "cmd.usage.list_inspect");
                    return true;
                }
                handleListInspect(sender, target, args[3]);
            }
            case "source" -> {
                if (args.length < 5) {
                    lang.send(sender, "cmd.usage.list_source");
                    return true;
                }
                handleListSource(sender, target, args[3], args[4]);
            }
            case "stats" -> {
                if (args.length < 4) {
                    lang.send(sender, "cmd.usage.list_stats");
                    return true;
                }
                handleListStats(sender, target, args[3]);
            }
            default -> handleListDefault(sender, target);
        }
        return true;
    }

    @Override
    public java.util.List<String> optionSuggestions(int argIndex, String partial, CommandSender sender, String[] args) {
        // 当前 list 无 -- 参数，返回空
        return java.util.Collections.emptyList();
    }

    private void handleListDefault(CommandSender sender, Player target) {
        UUID uuid = target.getUniqueId();
        Map<String, Map<String, Entry>> byKey = CommandUtils.groupSnapshotByKey(uuid);
        if (byKey.isEmpty()) {
            CommandUtils.sendSuccess(lang, sender, "cmd.list.empty", Map.of("player", target.getName()));
            return;
        }
        CommandUtils.sendSuccess(lang, sender, "cmd.list.header", Map.of("player", target.getName()));
        byKey.forEach((key, attrs) -> {
            CommandUtils.sendSuccess(lang, sender, "cmd.list.key_header", Map.of("key", key));
            attrs.forEach((stat, entry) -> CommandUtils.sendSuccess(lang, sender, "cmd.list.item", Map.of(
                    "attribute", stat,
                    "value", CommandUtils.formatValue(entry.getValue(), entry.isPercent())
            )));
        });
    }

    private void handleListAll(CommandSender sender, Player target) {
        String result = com.baimo.attributebinder.placeholder.PlaceholderFormatter.formatAllAttributes(target);
        if (sender instanceof Player) {
            sender.sendMessage(ColorUtil.translateColors(result));
        }
    }

    private void handleListKeys(CommandSender sender, Player target, String stat) {
        String statUpper = stat.toUpperCase();
        UUID uuid = target.getUniqueId();
        Map<String, Map<String, Entry>> statMap = CacheManager.snapshot(uuid);
        Map<String, Entry> keyMap = statMap.get(statUpper);
        if (keyMap == null || keyMap.isEmpty()) {
            CommandUtils.sendSuccess(lang, sender, "cmd.list.keys_empty", Map.of(
                    "player", target.getName(),
                    "stat", statUpper
            ));
            return;
        }
        CommandUtils.sendSuccess(lang, sender, "cmd.list.keys_header", Map.of(
                "player", target.getName(),
                "stat", statUpper
        ));
        CommandUtils.sendSuccess(lang, sender, "cmd.list.keys_content", Map.of(
                "keys", String.join(", ", keyMap.keySet())
        ));
    }

    private void handleListKeyAttrs(CommandSender sender, Player target, String keyId) {
        String result = com.baimo.attributebinder.placeholder.PlaceholderFormatter.formatKeyAttributes(target, keyId);
        CommandUtils.sendSuccess(lang, sender, "cmd.list.keyattrs_header", Map.of(
                "player", target.getName(),
                "key", keyId
        ));
        if (sender instanceof Player) {
            sender.sendMessage(ColorUtil.translateColors(result));
        }
    }

    private void handleListInspect(CommandSender sender, Player target, String stat) {
        String result = com.baimo.attributebinder.placeholder.PlaceholderFormatter.inspectStatModifiers(target, stat.toUpperCase());
        if (sender instanceof Player) {
            sender.sendMessage(ColorUtil.translateColors(result));
        }
    }

    private void handleListSource(CommandSender sender, Player target, String stat, String source) {
        String result = com.baimo.attributebinder.placeholder.PlaceholderFormatter.inspectStatModifiersBySource(target, stat.toUpperCase(), source.toUpperCase());
        if (sender instanceof Player) {
            sender.sendMessage(ColorUtil.translateColors(result));
        }
    }

    private void handleListStats(CommandSender sender, Player target, String stat) {
        String result = com.baimo.attributebinder.placeholder.PlaceholderFormatter.getStatModifierStats(target, stat.toUpperCase());
        if (sender instanceof Player) {
            sender.sendMessage(ColorUtil.translateColors(result));
        }
    }
}
