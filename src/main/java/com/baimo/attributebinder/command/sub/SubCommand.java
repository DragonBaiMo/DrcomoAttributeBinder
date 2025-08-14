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

    /**
     * 提供 -- 可选参数的联想建议。默认无建议，由具体子命令实现。
     * @param argIndex 当前参数位置（从1开始计数同命令显示，第一个子命令为 args[0]）
     * @param partial 当前正在输入的片段（如 "--m" 或 "--mode=")
     * @param sender 发起者
     * @param args 整体参数
     * @return 建议列表
     */
    default java.util.List<String> optionSuggestions(int argIndex, String partial, CommandSender sender, String[] args) {
        return java.util.Collections.emptyList();
    }
}
