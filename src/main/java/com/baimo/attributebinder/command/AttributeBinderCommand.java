package com.baimo.attributebinder.command;

import cn.drcomo.corelib.color.ColorUtil;
import com.baimo.attributebinder.AttributeBinder;
import com.baimo.attributebinder.cache.CacheManager;
import com.baimo.attributebinder.cache.CacheManager.Entry;
import com.baimo.attributebinder.config.ConfigManager;
import com.baimo.attributebinder.config.LangManager;
import com.baimo.attributebinder.service.AttributeApplier;
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
                if (!checkPermission(sender, "attributebinder.give", "error-no-permission")) return true;
                handleGive(sender, args);
                break;
            case "remove":
                if (!checkPermission(sender, "attributebinder.remove", "error-no-permission")) return true;
                handleRemove(sender, args);
                break;
            case "list":
                if (!checkPermission(sender, "attributebinder.list", "error-no-permission")) return true;
                handleList(sender, args);
                break;
            case "replace":
                if (!checkPermission(sender, "attributebinder.replace", "error-no-permission")) return true;
                handleReplace(sender, args);
                break;
            case "reload":
                if (!checkPermission(sender, "attributebinder.admin", "error-no-permission")) return true;
                ConfigManager.get().reload();
                LangManager.get().reload();
                AttributeBinder.getInstance().resetFlushTask(ConfigManager.get().getSyncIntervalMinutes());
                AttributeBinder.getInstance().updateDebugLevel();
                lang.send(sender, "command-reload-success");
                break;
            case "flush":
                if (!checkPermission(sender, "attributebinder.admin", "error-no-permission")) return true;
                new FlushTask().runTaskAsynchronously(AttributeBinder.getInstance());
                lang.send(sender, "command-flush-success");
                break;
            case "help":
                handleHelp(sender);
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
                List<String> suggestions = getStatSuggestions();
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
                    return StringUtil.copyPartialMatches(args[3], getStatSuggestions(), new ArrayList<>()).stream()
                                .sorted().collect(Collectors.toList());
                case "keyattrs":
                    // KeyID建议
                    if (sender instanceof Player) {
                        Player player = getPlayer(args[1], sender);
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

    /**
     * 处理 give 子命令
     * 参数格式: give <玩家> <属性> <数值> [KeyID] [memoryOnly] [过期时长ticks]
     */
    private void handleGive(CommandSender sender, String[] args) {
        if (args.length < 4 || args.length > 7) {
            lang.send(sender, "command-give-usage");
            return;
        }
        Player target = getPlayer(args[1], sender);
        if (target == null) return;

        String stat = args[2].toUpperCase();
        // 解析数值及百分比
        ValuePair vp = parseValue(args[3], sender, "command-give-invalid-number");
        if (vp == null) return;

        // 解析可选参数
        OptParams params = parseGiveParams(args, sender);
        if (params == null) return;

        UUID uuid = target.getUniqueId();
        double oldVal = CacheManager.getAttribute(uuid, stat, params.keyId);
        double newVal = oldVal + vp.value;

        if (Double.compare(oldVal, newVal) == 0) {
            // 值未改变，仅更新过期时间
            long oldTicks = CacheManager.getExpireTicks(uuid, stat, params.keyId);
            long newExpire = params.expireTicks >= 0 ? params.expireTicks : oldTicks;
            Entry entry = CacheManager.snapshot(uuid)
                    .getOrDefault(stat, Collections.emptyMap())
                    .get(params.keyId);
            boolean oldPercent = (entry != null && entry.isPercent());
            boolean oldMemory = (entry != null && entry.isMemoryOnly());
            CacheManager.setAttribute(uuid, stat, params.keyId, oldVal, oldPercent, oldMemory, newExpire);
            String valStr = formatValue(oldVal, oldPercent);
            sendGiveSuccess(sender, target.getName(), stat, valStr, params.keyId, oldMemory, newExpire);
        } else {
            // 值改变，设置新值并处理过期时长（累加）
            long oldExpire = CacheManager.getExpireTicks(uuid, stat, params.keyId);
            long newExpire = params.expireTicks >= 0 ? oldExpire + params.expireTicks : oldExpire;
            CacheManager.setAttribute(uuid, stat, params.keyId, newVal, vp.percent, params.memoryOnly, newExpire);
            AttributeApplier.apply(uuid, stat, params.keyId, newVal, vp.percent);
            String valStr = formatValue(newVal, vp.percent);
            sendGiveSuccess(sender, target.getName(), stat, valStr, params.keyId, params.memoryOnly, newExpire);
        }
    }

    /**
     * 处理 remove 子命令
     * 参数格式: remove <玩家|*> <属性|all|key> [KeyID]
     */
    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 3 || args.length > 4) {
            lang.send(sender, "command-remove-usage");
            return;
        }
        
        String playerName = args[1];
        String stat = args[2];
        String keyId = args.length == 4 ? args[3] : null;
        
        // 检查是否为遍历所有在线玩家
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
        
        // 单个玩家处理
        Player target = getPlayer(playerName, sender);
        if (target == null) return;
        
        if (removeAttributesForPlayer(target, stat, keyId)) {
            lang.send(sender, "command-remove-success", Map.of(
                    "player", target.getName(),
                    "attribute", stat,
                    "key", keyId == null ? "*" : keyId
            ));
        }
    }
    
    /**
     * 为指定玩家移除属性的核心逻辑
     * @param target 目标玩家
     * @param stat 属性类型
     * @param keyId 键ID
     * @return 是否成功处理
     */
    private boolean removeAttributesForPlayer(Player target, String stat, String keyId) {
        UUID uuid = target.getUniqueId();
        
        // 获取存储管理器实例
        com.baimo.attributebinder.storage.StorageManager storage = 
            com.baimo.attributebinder.storage.AttributeBinderContext.getStorage();

        // 删除指定 KeyID 下所有属性
        if ("key".equalsIgnoreCase(stat)) {
            if (keyId == null) {
                return false;
            }
            CacheManager.snapshot(uuid)
                    .keySet()
                    .forEach(s -> CacheManager.removeAttribute(uuid, s, keyId));
            AttributeApplier.removeKey(uuid, keyId);
            // 同步删除数据库中的数据
            storage.deleteAttributesByKey(uuid, keyId);
            return true;
        }
        
        // 删除玩家全部属性
        if ("all".equalsIgnoreCase(stat)) {
            CacheManager.clear(uuid);
            AttributeApplier.removeAll(uuid);
            // 同步删除数据库中的数据
            storage.deleteAllAttributes(uuid);
            return true;
        }
        
        // 删除指定属性（所有 KeyID 或 指定 KeyID）
        if (keyId == null) {
            CacheManager.snapshot(uuid)
                    .getOrDefault(stat, Collections.emptyMap())
                    .keySet()
                    .forEach(k -> CacheManager.removeAttribute(uuid, stat, k));
            AttributeApplier.removeStat(uuid, stat);
            // 同步删除数据库中的数据
            storage.deleteAttribute(uuid, stat, null);
        } else {
            CacheManager.removeAttribute(uuid, stat, keyId);
            AttributeApplier.remove(uuid, stat, keyId);
            // 同步删除数据库中的数据
            storage.deleteAttribute(uuid, stat, keyId);
        }
        return true;
    }

    /**
     * 处理 list 子命令
     * 参数格式: list <玩家> [all|keys|keyattrs|inspect|source|stats] [参数]
     */
    private void handleList(CommandSender sender, String[] args) {
        if (args.length < 2 || args.length > 5) {
            lang.send(sender, "command-list-usage");
            return;
        }
        Player target = getPlayer(args[1], sender);
        if (target == null) return;

        String mode = args.length >= 3 ? args[2].toLowerCase() : "default";
        
        switch (mode) {
            case "all":
                handleListAll(sender, target);
                break;
            case "keys":
                if (args.length < 4) {
                    lang.send(sender, "command-list-keys-usage");
                    return;
                }
                handleListKeys(sender, target, args[3]);
                break;
            case "keyattrs":
                if (args.length < 4) {
                    lang.send(sender, "command-list-keyattrs-usage");
                    return;
                }
                handleListKeyAttrs(sender, target, args[3]);
                break;
            case "inspect":
                if (args.length < 4) {
                    lang.send(sender, "command-list-inspect-usage");
                    return;
                }
                handleListInspect(sender, target, args[3]);
                break;
            case "source":
                if (args.length < 5) {
                    lang.send(sender, "command-list-source-usage");
                    return;
                }
                handleListSource(sender, target, args[3], args[4]);
                break;
            case "stats":
                if (args.length < 4) {
                    lang.send(sender, "command-list-stats-usage");
                    return;
                }
                handleListStats(sender, target, args[3]);
                break;
            default:
                handleListDefault(sender, target);
                break;
        }
    }
    
    /**
     * 处理默认的list命令（按KeyID分组显示AttributeBinder属性）
     */
    private void handleListDefault(CommandSender sender, Player target) {
        UUID uuid = target.getUniqueId();
        Map<String, Map<String, Entry>> byKey = groupSnapshotByKey(uuid);
        if (byKey.isEmpty()) {
            lang.send(sender, "command-list-empty", Map.of("player", target.getName()));
            return;
        }
        // 输出头部
        sender.sendMessage(lang.get("command-list-header", Map.of("player", target.getName())));
        // 按 KeyID 分组输出
        byKey.forEach((key, attrs) -> {
            sender.sendMessage(lang.get("command-list-key-header", Map.of("key", key)));
            attrs.forEach((stat, entry) -> {
                sender.sendMessage(lang.get("command-list-item", Map.of(
                        "attribute", stat,
                        "value", formatValue(entry.getValue(), entry.isPercent())
                )));
            });
        });
    }
    
    /**
     * 处理list all命令（显示所有属性，包括非AttributeBinder的）
     */
    private void handleListAll(CommandSender sender, Player target) {
        String result = com.baimo.attributebinder.placeholder.PlaceholderHandler.formatAllAttributes(target);
        sender.sendMessage(ColorUtil.translateColors(result));
    }
    
    /**
     * 处理list keys命令（显示指定属性的所有key）
     */
    private void handleListKeys(CommandSender sender, Player target, String stat) {
        String statUpper = stat.toUpperCase();
        UUID uuid = target.getUniqueId();
        Map<String, Map<String, Entry>> statMap = CacheManager.snapshot(uuid);
        Map<String, Entry> keyMap = statMap.get(statUpper);
        
        if (keyMap == null || keyMap.isEmpty()) {
            lang.send(sender, "command-list-keys-empty", Map.of(
                    "player", target.getName(),
                    "stat", statUpper
            ));
            return;
        }
        
        lang.send(sender, "command-list-keys-header", Map.of(
                "player", target.getName(),
                "stat", statUpper
        ));
        lang.send(sender, "command-list-keys-content", Map.of(
                "keys", String.join(", ", keyMap.keySet())
        ));
    }
    
    /**
     * 处理list keyattrs命令（显示指定key下的所有属性）
     */
    private void handleListKeyAttrs(CommandSender sender, Player target, String keyId) {
        String result = com.baimo.attributebinder.placeholder.PlaceholderHandler.formatKeyAttributes(target, keyId);
        lang.send(sender, "command-list-keyattrs-header", Map.of(
                "player", target.getName(),
                "key", keyId
        ));
        sender.sendMessage(ColorUtil.translateColors(result));
    }
    
    /**
     * 处理list inspect命令（详细检查指定属性的所有修饰符信息）
     */
    private void handleListInspect(CommandSender sender, Player target, String stat) {
        String result = com.baimo.attributebinder.placeholder.PlaceholderHandler.inspectStatModifiers(target, stat.toUpperCase());
        sender.sendMessage(ColorUtil.translateColors(result));
    }
    
    /**
     * 处理list source命令（按来源过滤显示修饰符）
     */
    private void handleListSource(CommandSender sender, Player target, String stat, String source) {
        String result = com.baimo.attributebinder.placeholder.PlaceholderHandler.inspectStatModifiersBySource(target, stat.toUpperCase(), source.toUpperCase());
        sender.sendMessage(ColorUtil.translateColors(result));
    }
    
    /**
     * 处理list stats命令（显示指定属性的修饰符统计）
     */
    private void handleListStats(CommandSender sender, Player target, String stat) {
        String result = com.baimo.attributebinder.placeholder.PlaceholderHandler.getStatModifierStats(target, stat.toUpperCase());
        sender.sendMessage(ColorUtil.translateColors(result));
    }

    /**
     * 处理 replace 子命令
     * 参数格式: replace <玩家> <属性> <数值> [KeyID] [memoryOnly] [过期时长ticks]
     */
    private void handleReplace(CommandSender sender, String[] args) {
        if (args.length < 4 || args.length > 7) {
            lang.send(sender, "command-replace-usage");
            return;
        }
        Player target = getPlayer(args[1], sender);
        if (target == null) return;

        String stat = args[2].toUpperCase();
        // 解析数值及百分比
        ValuePair vp = parseValue(args[3], sender, "command-give-invalid-number");
        if (vp == null) return;

        // 解析可选参数
        OptParams params = parseReplaceParams(args, sender);
        if (params == null) return;

        UUID uuid = target.getUniqueId();
        double oldVal = CacheManager.getAttribute(uuid, stat, params.keyId);

        if (Double.compare(oldVal, vp.value) == 0) {
            // 值未改变，仅更新过期时间（替换语义）
            long oldTicks = CacheManager.getExpireTicks(uuid, stat, params.keyId);
            long newExpire = params.expireTicks >= 0 ? params.expireTicks : oldTicks;
            Entry entry = CacheManager.snapshot(uuid)
                    .getOrDefault(stat, Collections.emptyMap())
                    .get(params.keyId);
            boolean oldPercent = (entry != null && entry.isPercent());
            boolean oldMemory = (entry != null && entry.isMemoryOnly());
            CacheManager.setAttribute(uuid, stat, params.keyId, oldVal, oldPercent, oldMemory, newExpire);
            String valStr = formatValue(oldVal, oldPercent);
            sendReplaceSuccess(sender, target.getName(), stat, valStr, params.keyId);
        } else {
            // 值改变，替换新值并处理过期时长（替换语义）
            long oldExpire = CacheManager.getExpireTicks(uuid, stat, params.keyId);
            long newExpire = params.expireTicks >= 0 ? params.expireTicks : oldExpire;
            CacheManager.setAttribute(uuid, stat, params.keyId, vp.value, vp.percent, params.memoryOnly, newExpire);
            AttributeApplier.apply(uuid, stat, params.keyId, vp.value, vp.percent);
            String valStr = formatValue(vp.value, vp.percent);
            sendReplaceSuccess(sender, target.getName(), stat, valStr, params.keyId);
        }
    }

    /**
     * 处理 help 子命令，输出帮助信息
     */
    private void handleHelp(CommandSender sender) {
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

    /** 获取玩家对象并在不存在时发送错误消息 */
    @Nullable
    private Player getPlayer(String name, CommandSender sender) {
        Player player = Bukkit.getPlayer(name);
        if (player == null) {
            lang.send(sender, "error-player-not-found", Map.of("player", name));
        }
        return player;
    }

    /** 将原始数值字符串解析为数值和百分比标志 */
    private ValuePair parseValue(String raw, CommandSender sender, String errorKey) {
        boolean percent = false;
        if (raw.endsWith("%")) {
            percent = true;
            raw = raw.substring(0, raw.length() - 1);
        }
        try {
            double val = Double.parseDouble(raw);
            return new ValuePair(val, percent);
        } catch (NumberFormatException e) {
            lang.send(sender, errorKey);
            return null;
        }
    }

    /** 给 give 操作发送成功提示 */
    private void sendGiveSuccess(CommandSender sender, String player, String stat, String value,
                                 String key, boolean memoryOnly, long expireTicks) {
        lang.send(sender, "command-give-success", Map.of(
                "player", player,
                "attribute", stat,
                "value", value,
                "key", key,
                "memoryOnly", String.valueOf(memoryOnly),
                "expireTicks", String.valueOf(expireTicks)
        ));
    }

    /** 给 replace 操作发送成功提示 */
    private void sendReplaceSuccess(CommandSender sender, String player, String stat,
                                    String value, String key) {
        lang.send(sender, "command-replace-success", Map.of(
                "player", player,
                "attribute", stat,
                "value", value,
                "key", key
        ));
    }

    /** 格式化数值输出，加上“+”“-”“%”等 */
    private String formatValue(double val, boolean percent) {
        String s = percent ? val + "%" : (val >= 0 ? "+" + val : String.valueOf(val));
        return s;
    }

    /** 将缓存快照按 KeyID 分组 */
    private LinkedHashMap<String, Map<String, Entry>> groupSnapshotByKey(UUID uuid) {
        Map<String, Map<String, Entry>> statMap = CacheManager.snapshot(uuid);
        LinkedHashMap<String, Map<String, Entry>> byKey = new LinkedHashMap<>();
        statMap.forEach((stat, keyMap) ->
                keyMap.forEach((key, entry) ->
                        byKey.computeIfAbsent(key, k -> new LinkedHashMap<>()).put(stat, entry)
                )
        );
        return byKey;
    }

    /** 获取 MMOItems 可用的 DoubleStat ID 列表 */
    private List<String> getStatSuggestions() {
        List<String> list = new ArrayList<>();
        try {
            Collection<ItemStat<?, ?>> stats = MMOItems.plugin.getStats().getAll();
            for (ItemStat<?, ?> stat : stats) {
                if (stat instanceof DoubleStat) {
                    list.add(stat.getId());
                }
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    /** 解析 give 子命令的可选参数 */
    private OptParams parseGiveParams(String[] args, CommandSender sender) {
        String keyId = CacheManager.DEFAULT_KEY;
        boolean memoryOnly = false;
        long expireTicks = -1;
        if (args.length >= 5) {
            keyId = args[4];
        }
        if (args.length >= 6) {
            try {
                memoryOnly = Boolean.parseBoolean(args[5]);
            } catch (Exception e) {
                lang.send(sender, "command-give-usage");
                return null;
            }
        }
        if (args.length == 7) {
            try {
                expireTicks = Long.parseLong(args[6]);
            } catch (NumberFormatException e) {
                lang.send(sender, "command-give-invalid-number");
                return null;
            }
        }
        return new OptParams(keyId, memoryOnly, expireTicks);
    }

    /** 解析 replace 子命令的可选参数 */
    private OptParams parseReplaceParams(String[] args, CommandSender sender) {
        String keyId = CacheManager.DEFAULT_KEY;
        boolean memoryOnly = false;
        long expireTicks = -1;
        if (args.length >= 5) {
            keyId = args[4];
        }
        if (args.length >= 6) {
            try {
                memoryOnly = Boolean.parseBoolean(args[5]);
            } catch (Exception e) {
                lang.send(sender, "command-replace-usage");
                return null;
            }
        }
        if (args.length == 7) {
            try {
                expireTicks = Long.parseLong(args[6]);
            } catch (NumberFormatException e) {
                lang.send(sender, "command-give-invalid-number"); // 保留原逻辑
                return null;
            }
        }
        return new OptParams(keyId, memoryOnly, expireTicks);
    }

    /** 简单数据类：数值 + 百分比标志 */
    private static class ValuePair {
        final double value;
        final boolean percent;
        ValuePair(double value, boolean percent) {
            this.value = value;
            this.percent = percent;
        }
    }

    /** 简单数据类：KeyID + memoryOnly + expireTicks */
    private static class OptParams {
        final String keyId;
        final boolean memoryOnly;
        final long expireTicks;
        OptParams(String keyId, boolean memoryOnly, long expireTicks) {
            this.keyId = keyId;
            this.memoryOnly = memoryOnly;
            this.expireTicks = expireTicks;
        }
    }
}
