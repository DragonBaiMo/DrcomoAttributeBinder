package com.baimo.attributebinder.command.sub;

import com.baimo.attributebinder.command.CommandUtil;
import com.baimo.attributebinder.manager.AggregatedApplier;
import com.baimo.attributebinder.manager.AttributeApplier;
import com.baimo.attributebinder.manager.CacheManager;
import com.baimo.attributebinder.manager.LangManager;
import com.baimo.attributebinder.manager.AttributeBinderContext;
import com.baimo.attributebinder.manager.StorageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * remove 子命令处理
 */
public class RemoveCommand {
    private final LangManager lang = LangManager.get();

    public void execute(CommandSender sender, String[] args) {
        if (args.length < 3 || args.length > 4) {
            lang.send(sender, "command-remove-usage");
            return;
        }

        String playerName = args[1];
        String stat = args[2];
        String keyId = args.length == 4 ? args[3] : null;

        if ("*".equals(playerName)) {
            Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
            if (onlinePlayers.isEmpty()) {
                lang.send(sender, "error-no-online-players");
                return;
            }

            int processedCount = 0;
            for (Player target : onlinePlayers) {
                if (removeAttributesForPlayer(target, stat, keyId)) {
                    processedCount++;
                }
            }

            lang.send(sender, "command-remove-all-players-success", Map.of(
                    "count", String.valueOf(processedCount),
                    "attribute", stat,
                    "key", keyId == null ? "*" : keyId
            ));
            return;
        }

        Player target = CommandUtil.getPlayer(playerName, sender);
        if (target == null) return;

        if (removeAttributesForPlayer(target, stat, keyId)) {
            lang.send(sender, "command-remove-success", Map.of(
                    "player", target.getName(),
                    "attribute", stat,
                    "key", keyId == null ? "*" : keyId
            ));
        }
    }

    private boolean removeAttributesForPlayer(Player target, String stat, String keyId) {
        UUID uuid = target.getUniqueId();
        StorageManager storage = AttributeBinderContext.getStorage();

        if ("key".equalsIgnoreCase(stat)) {
            if (keyId == null) {
                return false;
            }
            CacheManager.snapshot(uuid)
                    .keySet()
                    .forEach(s -> {
                        CacheManager.removeAttribute(uuid, s, keyId);
                        AggregatedApplier.applyFromCache(uuid, s);
                    });
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
            AggregatedApplier.applyFromCache(uuid, stat);
            storage.deleteAttribute(uuid, stat, null);
        } else {
            CacheManager.removeAttribute(uuid, stat, keyId);
            AggregatedApplier.applyFromCache(uuid, stat);
            storage.deleteAttribute(uuid, stat, keyId);
        }
        return true;
    }
}
