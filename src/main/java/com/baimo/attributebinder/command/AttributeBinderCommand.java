package com.baimo.attributebinder.command;

import com.baimo.attributebinder.AttributeBinder;
import com.baimo.attributebinder.manager.CacheManager;
import com.baimo.attributebinder.manager.CacheManager.Entry;
import com.baimo.attributebinder.command.sub.*;
import com.baimo.attributebinder.command.CommandUtil;
import com.baimo.attributebinder.manager.ConfigManager;
import com.baimo.attributebinder.manager.LangManager;
import com.baimo.attributebinder.manager.AttributeApplier;
import com.baimo.attributebinder.manager.AggregatedApplier;
import com.baimo.attributebinder.task.FlushTask;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.stat.type.ItemStat;
import net.Indyuce.mmoitems.stat.type.DoubleStat;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Collection;
import java.util.Collections;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 主命令处理类，负责 /attributebinder 子命令的分发与执行
 */
public class AttributeBinderCommand implements CommandExecutor, TabCompleter {

    private final LangManager lang = LangManager.get();

    private final GiveCommand giveCmd = new GiveCommand();
    private final RemoveCommand removeCmd = new RemoveCommand();
    private final ListCommand listCmd = new ListCommand();
    private final ReplaceCommand replaceCmd = new ReplaceCommand();
    private final HelpCommand helpCmd = new HelpCommand();
    private final AdminCommands adminCmd = new AdminCommands();
    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {
        if (args.length < 1) {
            lang.send(sender, "command-main-usage");
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "give":
                if (!CommandUtil.checkPermission(sender, "attributebinder.give", "error-no-permission")) return true;
                giveCmd.execute(sender, args);
                break;
            case "remove":
                if (!CommandUtil.checkPermission(sender, "attributebinder.remove", "error-no-permission")) return true;
                removeCmd.execute(sender, args);
                break;
            case "list":
                if (!CommandUtil.checkPermission(sender, "attributebinder.list", "error-no-permission")) return true;
                listCmd.execute(sender, args);
                break;
            case "replace":
                if (!CommandUtil.checkPermission(sender, "attributebinder.replace", "error-no-permission")) return true;
                replaceCmd.execute(sender, args);
                break;
            case "reload":
                if (!CommandUtil.checkPermission(sender, "attributebinder.admin", "error-no-permission")) return true;
                adminCmd.reload(sender);
                break;
            case "flush":
                if (!CommandUtil.checkPermission(sender, "attributebinder.admin", "error-no-permission")) return true;
                adminCmd.flush(sender);
                break;
            case "help":
                helpCmd.execute(sender);
                break;
            default:
                lang.send(sender, "command-unknown-subcommand");
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
                List<String> suggestions = CommandUtil.getStatSuggestions();
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
                    return StringUtil.copyPartialMatches(args[3], CommandUtil.getStatSuggestions(), new ArrayList<>()).stream()
                                .sorted().collect(Collectors.toList());
                case "keyattrs":
                    // KeyID建议
                    if (sender instanceof Player) {
                        Player player = CommandUtil.getPlayer(args[1], sender);
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
        
        return Collections.emptyList();
    }
}
