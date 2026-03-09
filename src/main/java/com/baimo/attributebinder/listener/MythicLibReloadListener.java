package com.baimo.attributebinder.listener;

import com.baimo.attributebinder.DrcomoAttributeBinder;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.Locale;

/**
 * 监听 MythicLib 重载命令（/mythiclib reload, /ml reload），并在重载后重放 ABB 属性。
 * MythicLib 当前未暴露公开 ReloadEvent，因此使用命令侧触发恢复逻辑。
 */
public final class MythicLibReloadListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (isMythicLibReloadCommand(event.getMessage())) {
            DrcomoAttributeBinder.getInstance().scheduleReloadRecovery();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsoleCommand(ServerCommandEvent event) {
        if (isMythicLibReloadCommand(event.getCommand())) {
            DrcomoAttributeBinder.getInstance().scheduleReloadRecovery();
        }
    }

    private boolean isMythicLibReloadCommand(String raw) {
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

        String root = parts[0];
        String sub = parts[1];
        return ("mythiclib".equals(root) || "ml".equals(root)) && "reload".equals(sub);
    }
}
