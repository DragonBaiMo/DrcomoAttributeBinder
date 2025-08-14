package com.baimo.attributebinder.command.sub;

import com.baimo.attributebinder.cache.CacheManager;
import com.baimo.attributebinder.service.AttributeApplier;
import com.baimo.attributebinder.config.LangManager;
import com.baimo.attributebinder.command.CommandUtils;
import com.baimo.attributebinder.storage.AttributeBinderContext;
import com.baimo.attributebinder.storage.StorageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * RemoveCommand — 处理 /ab remove 命令
 */
public class RemoveCommand implements SubCommand {
    private final LangManager lang = LangManager.get();

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 3 || args.length > 4) {
            lang.send(sender, "command-remove-usage");
            return true;
        }

        String playerName = args[1];
        String stat = args[2];
        String keyId = args.length == 4 ? args[3] : null;

        if ("*".equals(playerName)) {
            Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
            if (onlinePlayers.isEmpty()) {
                lang.send(sender, "error-no-online-players");
                return true;
            }
            int count = 0;
            for (Player target : onlinePlayers) {
                if (removeAttributesForPlayer(target, stat, keyId)) {
                    count++;
                }
            }
            lang.send(sender, "command-remove-all-players-success", Map.of(
                    "count", String.valueOf(count),
                    "attribute", stat,
                    "key", keyId == null ? "*" : keyId
            ));
            return true;
        }

        Player target = CommandUtils.getPlayer(playerName, sender, lang);
        if (target == null) return true;

        if (removeAttributesForPlayer(target, stat, keyId)) {
            lang.send(sender, "command-remove-success", Map.of(
                    "player", target.getName(),
                    "attribute", stat,
                    "key", keyId == null ? "*" : keyId
            ));
        }
        return true;
    }

    @Override
    public java.util.List<String> optionSuggestions(int argIndex, String partial, CommandSender sender, String[] args) {
        // 当前 remove 无 -- 参数，返回空
        return java.util.Collections.emptyList();
    }

    private boolean removeAttributesForPlayer(Player target, String stat, String keyId) {
        UUID uuid = target.getUniqueId();
        StorageManager storage = AttributeBinderContext.getStorage();

        if ("key".equalsIgnoreCase(stat)) {
            if (keyId == null) return false;
            CacheManager.snapshot(uuid).keySet().forEach(s -> CacheManager.removeAttribute(uuid, s, keyId));
            AttributeApplier.removeKey(uuid, keyId);
            storage.deleteAttributesByKey(uuid, keyId);
            return true;
        }

        if ("all".equalsIgnoreCase(stat)) {
            CacheManager.clear(uuid);
            AttributeApplier.removeAll(uuid);
            storage.deleteAllAttributes(uuid);
            return true;
        }

        if (keyId == null) {
            CacheManager.snapshot(uuid)
                    .getOrDefault(stat, Collections.emptyMap())
                    .keySet()
                    .forEach(k -> CacheManager.removeAttribute(uuid, stat, k));
            AttributeApplier.removeStat(uuid, stat);
            storage.deleteAttribute(uuid, stat, null);
        } else {
            CacheManager.removeAttribute(uuid, stat, keyId);
            AttributeApplier.remove(uuid, stat, keyId);
            storage.deleteAttribute(uuid, stat, keyId);
        }
        return true;
    }
}
