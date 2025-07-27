package com.baimo.attributebinder.command.sub;

import com.baimo.attributebinder.AttributeBinder;
import com.baimo.attributebinder.manager.ConfigManager;
import com.baimo.attributebinder.manager.LangManager;
import com.baimo.attributebinder.task.FlushTask;
import org.bukkit.command.CommandSender;

/**
 * 简单的管理类子命令：reload 与 flush
 */
public class AdminCommands {
    private final LangManager lang = LangManager.get();

    public void reload(CommandSender sender) {
        ConfigManager.get().reload();
        LangManager.get().reload();
        AttributeBinder.getInstance().resetFlushTask(ConfigManager.get().getSyncIntervalMinutes());
        AttributeBinder.getInstance().updateDebugLevel();
        lang.send(sender, "command-reload-success");
    }

    public void flush(CommandSender sender) {
        new FlushTask().runTaskAsynchronously(AttributeBinder.getInstance());
        lang.send(sender, "command-flush-success");
    }
}
