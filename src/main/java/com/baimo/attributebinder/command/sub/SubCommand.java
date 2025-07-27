package com.baimo.attributebinder.command.sub;

import org.bukkit.command.CommandSender;

/**
 * SubCommand — 子命令执行接口
 */
public interface SubCommand {
    /**
     * 执行子命令
     * @param sender 发起者
     * @param args 命令参数
     * @return 是否已处理
     */
    boolean execute(CommandSender sender, String[] args);
}
