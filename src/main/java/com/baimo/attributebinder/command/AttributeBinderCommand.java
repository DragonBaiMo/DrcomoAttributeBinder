package com.baimo.attributebinder.command;

import com.baimo.attributebinder.DrcomoAttributeBinder;
import com.baimo.attributebinder.config.ConfigManager;
import com.baimo.attributebinder.config.LangManager;
import com.baimo.attributebinder.task.FlushTask;
import com.baimo.attributebinder.cache.CacheManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 主命令处理类，负责 /drcomoattributebinder 子命令的分发与执行
 */
public class AttributeBinderCommand implements CommandExecutor, TabCompleter {

    private final LangManager lang = LangManager.get();

    private final com.baimo.attributebinder.command.sub.SubCommand giveCmd = new com.baimo.attributebinder.command.sub.GiveCommand();
    private final com.baimo.attributebinder.command.sub.SubCommand removeCmd = new com.baimo.attributebinder.command.sub.RemoveCommand();
    private final com.baimo.attributebinder.command.sub.SubCommand listCmd = new com.baimo.attributebinder.command.sub.ListCommand();
    private final com.baimo.attributebinder.command.sub.SubCommand replaceCmd = new com.baimo.attributebinder.command.sub.ReplaceCommand();

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {
        com.baimo.attributebinder.command.CommandUtils.setInvocation(label, args);
        if (args.length < 1) {
            lang.send(sender, "cmd.usage.main");
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "give":
                if (!checkPermission(sender, "drcomoattributebinder.give", "cmd.error.no_permission")) return true;
                giveCmd.execute(sender, args);
                break;
            case "remove":
                if (!checkPermission(sender, "drcomoattributebinder.remove", "cmd.error.no_permission")) return true;
                removeCmd.execute(sender, args);
                break;
            case "list":
                if (!checkPermission(sender, "drcomoattributebinder.list", "cmd.error.no_permission")) return true;
                listCmd.execute(sender, args);
                break;
            case "replace":
                if (!checkPermission(sender, "drcomoattributebinder.replace", "cmd.error.no_permission")) return true;
                replaceCmd.execute(sender, args);
                break;
            case "reload":
                if (!checkPermission(sender, "drcomoattributebinder.admin", "cmd.error.no_permission")) return true;
                ConfigManager.get().reload();
                LangManager.get().reload();
                DrcomoAttributeBinder.getInstance().resetFlushTask(ConfigManager.get().getSyncIntervalMinutes());
                DrcomoAttributeBinder.getInstance().updateDebugLevel();
                CommandUtils.sendSuccess(lang, sender, "cmd.reload.success");
                break;
            case "flush":
                if (!checkPermission(sender, "drcomoattributebinder.admin", "cmd.error.no_permission")) return true;
                new FlushTask().runTaskAsynchronously(DrcomoAttributeBinder.getInstance());
                CommandUtils.sendSuccess(lang, sender, "cmd.flush.success");
                break;
            case "help":
                handleHelp(sender);
                break;
            default:
                lang.send(sender, "cmd.error.unknown_subcommand");
        }
        return true;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command command,
                                      @NotNull String alias,
                                      @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.asList("give", "replace", "remove", "list", "reload", "flush", "help").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .sorted()
                    .collect(Collectors.toList());
        }

        String sub = args[0].toLowerCase();
        // 第二个参数：玩家名
        if (args.length == 2 && Arrays.asList("give", "replace", "remove", "list").contains(sub)) {
            List<String> players = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList());
            // 为 remove 命令添加 * 选项
            if ("remove".equals(sub)) {
                players.add("*");
            }
            return StringUtil.copyPartialMatches(args[1], players, new ArrayList<>()).stream()
                    .sorted()
                    .collect(Collectors.toList());
        }
        // 第三个参数：属性 ID（give/replace/remove）或模式（list）
        if (args.length == 3) {
            if (Arrays.asList("give", "replace", "remove").contains(sub)) {
                List<String> suggestions = com.baimo.attributebinder.command.CommandUtils.getStatSuggestions();
                if ("remove".equals(sub)) {
                    suggestions.add("all");
                    suggestions.add("key");
                }
                return StringUtil.copyPartialMatches(args[2], suggestions, new ArrayList<>()).stream()
                            .sorted().collect(Collectors.toList());
            } else if ("list".equals(sub)) {
                // list 第三个参数建议: 检测模式
                List<String> modes = Arrays.asList("all", "keys", "keyattrs", "inspect", "source", "stats");
                return StringUtil.copyPartialMatches(args[2], modes, new ArrayList<>()).stream()
                            .sorted().collect(Collectors.toList());
            }
        }
        
        // 第四个参数：根据list模式提供不同建议
        if (args.length == 4 && "list".equals(sub)) {
            String mode = args[2].toLowerCase();
            switch (mode) {
                case "keys":
                case "inspect":
                case "source":
                case "stats":
                    // 属性名建议
                    return StringUtil.copyPartialMatches(args[3], com.baimo.attributebinder.command.CommandUtils.getStatSuggestions(), new ArrayList<>()).stream()
                                .sorted().collect(Collectors.toList());
                case "keyattrs":
                    // KeyID建议
                    if (sender instanceof Player) {
                        Player player = com.baimo.attributebinder.command.CommandUtils.getPlayer(args[1], sender, lang);
                        if (player != null) {
                            UUID uuid = player.getUniqueId();
                            List<String> keys = CacheManager.snapshot(uuid)
                                    .values().stream()
                                    .flatMap(statMap -> statMap.keySet().stream())
                                    .distinct()
                                    .collect(Collectors.toList());
                            return StringUtil.copyPartialMatches(args[3], keys, new ArrayList<>()).stream()
                                        .sorted().collect(Collectors.toList());
                        }
                    }
                    break;
            }
        }
        
        // 第五个参数：source模式的来源建议
        if (args.length == 5 && "list".equals(sub) && "source".equals(args[2].toLowerCase())) {
            List<String> sources = Arrays.asList("ACCESSORY", "ARMOR", "HAND_ITEM", "MAINHAND_ITEM", 
                    "MELEE_WEAPON", "OFFHAND_ITEM", "ORNAMENT", "OTHER", "RANGED_WEAPON", "VOID");
            return StringUtil.copyPartialMatches(args[4], sources, new ArrayList<>()).stream()
                        .sorted().collect(Collectors.toList());
        }
        
        // 其余参数位置：为 give/replace 提供 -- 参数补全
        if (Arrays.asList("give", "replace", "remove", "list").contains(sub) && args.length >= 4) {
            com.baimo.attributebinder.command.sub.SubCommand impl = switch (sub) {
                case "give" -> giveCmd;
                case "replace" -> replaceCmd;
                case "remove" -> removeCmd;
                case "list" -> listCmd;
                default -> null;
            };
            if (impl != null) {
                String partial = args[args.length - 1];
                List<String> opts = impl.optionSuggestions(args.length, partial, sender, args);
                if (opts != null && !opts.isEmpty()) {
                    return StringUtil.copyPartialMatches(partial, opts, new ArrayList<>()).stream()
                            .sorted().collect(Collectors.toList());
                }
            }
        }

        return Collections.emptyList();
    }

    /**
     * 处理 give 子命令
     * 参数格式: give <玩家> <属性> <数值> [KeyID] [memoryOnly] [过期时长ticks]
     */
    private void handleHelp(CommandSender sender) {
        CommandUtils.sendSuccess(lang, sender, "cmd.help.header");
        CommandUtils.sendSuccess(lang, sender, "cmd.usage.give");
        CommandUtils.sendSuccess(lang, sender, "cmd.usage.remove");
        CommandUtils.sendSuccess(lang, sender, "cmd.usage.replace");
        CommandUtils.sendSuccess(lang, sender, "cmd.usage.list");
        CommandUtils.sendSuccess(lang, sender, "cmd.help.list_modes");
        CommandUtils.sendSuccess(lang, sender, "cmd.help.options");
        CommandUtils.sendSuccess(lang, sender, "cmd.help.reload");
        CommandUtils.sendSuccess(lang, sender, "cmd.help.flush");
        CommandUtils.sendSuccess(lang, sender, "cmd.help.help");
        CommandUtils.sendSuccess(lang, sender, "cmd.help.footer");
    }

    // --------------------------------------
    // 私有工具方法 & 内部数据结构
    // --------------------------------------

    /** 简化权限检查，返回 false 时已发送提示 */
    private boolean checkPermission(CommandSender sender, String perm, String langKey) {
        if (!sender.hasPermission(perm)) {
            lang.send(sender, langKey);
            return false;
        }
        return true;
    }
}
