package com.baimo.attributebinder.command.sub;

import com.baimo.attributebinder.manager.LangManager;
import org.bukkit.command.CommandSender;

/**
 * help 子命令处理
 */
public class HelpCommand {
    private final LangManager lang = LangManager.get();

    public void execute(CommandSender sender) {
        lang.send(sender, "command-help-header");
        lang.send(sender, "command-give-usage");
        lang.send(sender, "command-remove-usage");
        lang.send(sender, "command-replace-usage");
        lang.send(sender, "command-list-usage");
        lang.send(sender, "command-help-list-modes");
        lang.send(sender, "command-help-reload");
        lang.send(sender, "command-help-flush");
        lang.send(sender, "command-help-help");
        lang.send(sender, "command-help-footer");
    }
}
