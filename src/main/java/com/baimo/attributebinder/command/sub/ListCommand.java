package com.baimo.attributebinder.command.sub;

import com.baimo.attributebinder.command.CommandUtil;
import com.baimo.attributebinder.manager.CacheManager;
import com.baimo.attributebinder.manager.CacheManager.Entry;
import com.baimo.attributebinder.manager.LangManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import cn.drcomo.corelib.color.ColorUtil;

import java.util.Map;
import java.util.UUID;

/**
 * list 子命令处理
 */
public class ListCommand {
    private final LangManager lang = LangManager.get();

    public void execute(CommandSender sender, String[] args) {
        if (args.length < 2 || args.length > 5) {
            lang.send(sender, "command-list-usage");
            return;
        }
        Player target = CommandUtil.getPlayer(args[1], sender);
        if (target == null) return;

        String mode = args.length >= 3 ? args[2].toLowerCase() : "default";
        switch (mode) {
            case "all" -> handleListAll(sender, target);
            case "keys" -> {
                if (args.length < 4) {
                    lang.send(sender, "command-list-keys-usage");
                    return;
                }
                handleListKeys(sender, target, args[3]);
            }
            case "keyattrs" -> {
                if (args.length < 4) {
                    lang.send(sender, "command-list-keyattrs-usage");
                    return;
                }
                handleListKeyAttrs(sender, target, args[3]);
            }
            case "inspect" -> {
                if (args.length < 4) {
                    lang.send(sender, "command-list-inspect-usage");
                    return;
                }
                handleListInspect(sender, target, args[3]);
            }
            case "source" -> {
                if (args.length < 5) {
                    lang.send(sender, "command-list-source-usage");
                    return;
                }
                handleListSource(sender, target, args[3], args[4]);
            }
            case "stats" -> {
                if (args.length < 4) {
                    lang.send(sender, "command-list-stats-usage");
                    return;
                }
                handleListStats(sender, target, args[3]);
            }
            default -> handleListDefault(sender, target);
        }
    }

    private void handleListDefault(CommandSender sender, Player target) {
        UUID uuid = target.getUniqueId();
        Map<String, Map<String, Entry>> byKey = CommandUtil.groupSnapshotByKey(uuid);
        if (byKey.isEmpty()) {
            lang.send(sender, "command-list-empty", Map.of("player", target.getName()));
            return;
        }
        sender.sendMessage(lang.get("command-list-header", Map.of("player", target.getName())));
        byKey.forEach((key, attrs) -> {
            sender.sendMessage(lang.get("command-list-key-header", Map.of("key", key)));
            attrs.forEach((stat, entry) -> sender.sendMessage(lang.get("command-list-item", Map.of(
                    "attribute", stat,
                    "value", CommandUtil.formatValue(entry.getValue(), entry.isPercent())
            )));
        });
    }

    private void handleListAll(CommandSender sender, Player target) {
        String result = com.baimo.attributebinder.placeholder.PlaceholderHandler.formatAllAttributes(target);
        sender.sendMessage(ColorUtil.translateColors(result));
    }

    private void handleListKeys(CommandSender sender, Player target, String stat) {
        String statUpper = stat.toUpperCase();
        UUID uuid = target.getUniqueId();
        Map<String, Map<String, Entry>> statMap = CacheManager.snapshot(uuid);
        Map<String, Entry> keyMap = statMap.get(statUpper);
        if (keyMap == null || keyMap.isEmpty()) {
            lang.send(sender, "command-list-keys-empty", Map.of(
                    "player", target.getName(),
                    "stat", statUpper
            ));
            return;
        }
        lang.send(sender, "command-list-keys-header", Map.of(
                "player", target.getName(),
                "stat", statUpper
        ));
        lang.send(sender, "command-list-keys-content", Map.of(
                "keys", String.join(", ", keyMap.keySet())
        ));
    }

    private void handleListKeyAttrs(CommandSender sender, Player target, String keyId) {
        String result = com.baimo.attributebinder.placeholder.PlaceholderHandler.formatKeyAttributes(target, keyId);
        lang.send(sender, "command-list-keyattrs-header", Map.of(
                "player", target.getName(),
                "key", keyId
        ));
        sender.sendMessage(ColorUtil.translateColors(result));
    }

    private void handleListInspect(CommandSender sender, Player target, String stat) {
        String result = com.baimo.attributebinder.placeholder.PlaceholderHandler.inspectStatModifiers(target, stat.toUpperCase());
        sender.sendMessage(ColorUtil.translateColors(result));
    }

    private void handleListSource(CommandSender sender, Player target, String stat, String source) {
        String result = com.baimo.attributebinder.placeholder.PlaceholderHandler.inspectStatModifiersBySource(target, stat.toUpperCase(), source.toUpperCase());
        sender.sendMessage(ColorUtil.translateColors(result));
    }

    private void handleListStats(CommandSender sender, Player target, String stat) {
        String result = com.baimo.attributebinder.placeholder.PlaceholderHandler.getStatModifierStats(target, stat.toUpperCase());
        sender.sendMessage(ColorUtil.translateColors(result));
    }
}
