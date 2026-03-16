package com.baimo.attributebinder.listener;

import com.baimo.attributebinder.DrcomoAttributeBinder;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.Locale;

/**
 * 监听 PlaceholderAPI 重载命令（/papi reload, /placeholderapi reload），并在重载后补注册 ABB 占位符。
 */
public final class PlaceholderAPIReloadListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (isPapiReloadCommand(event.getMessage())) {
            DrcomoAttributeBinder.getInstance().schedulePlaceholderReRegister("/papi reload");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsoleCommand(ServerCommandEvent event) {
        if (isPapiReloadCommand(event.getCommand())) {
            DrcomoAttributeBinder.getInstance().schedulePlaceholderReRegister("控制台 PAPI reload");
        }
    }

    private boolean isPapiReloadCommand(String raw) {
        if (raw == null) {
            return false;
        }

        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1).trim();
        }
        if (normalized.isEmpty()) {
            return false;
        }

        String[] parts = normalized.split("\\s+");
        if (parts.length < 2) {
            return false;
        }

        String root = stripNamespace(parts[0]);
        String sub = parts[1];
        return ("papi".equals(root) || "placeholderapi".equals(root)) && "reload".equals(sub);
    }

    private String stripNamespace(String command) {
        int index = command.indexOf(':');
        if (index < 0 || index == command.length() - 1) {
            return command;
        }
        return command.substring(index + 1);
    }
}
