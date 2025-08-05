package com.baimo.attributebinder.command.sub;

import com.baimo.attributebinder.cache.CacheManager;
import com.baimo.attributebinder.cache.CacheManager.Entry;
import com.baimo.attributebinder.service.AttributeApplier;
import com.baimo.attributebinder.command.CommandUtils;
import com.baimo.attributebinder.command.CommandUtils.ValuePair;
import com.baimo.attributebinder.command.CommandUtils.OptParams;
import com.baimo.attributebinder.config.LangManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * ReplaceCommand — 处理 /ab replace 命令
 */
public class ReplaceCommand implements SubCommand {
    private final LangManager lang = LangManager.get();

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 4 || args.length > 7) {
            lang.send(sender, "command-replace-usage");
            return true;
        }
        Player target = CommandUtils.getPlayer(args[1], sender, lang);
        if (target == null) return true;

        String stat = args[2].toUpperCase();
        ValuePair vp = CommandUtils.parseValue(args[3], sender, "command-give-invalid-number", lang);
        if (vp == null) return true;

        OptParams params = CommandUtils.parseReplaceParams(args, sender, lang);
        if (params == null) return true;

        UUID uuid = target.getUniqueId();
        double oldVal = CacheManager.getAttribute(uuid, stat, params.keyId);

        if (Double.compare(oldVal, vp.value) == 0) {
            long oldTicks = CacheManager.getExpireTicks(uuid, stat, params.keyId);
            // 根据replaceExpire决定是覆盖还是累加过期时间
            long newExpire;
            if (params.expireTicks >= 0) {
                if (!params.replaceExpire && oldTicks > 0) {
                    // 如果指定了:give选项且原有过期时间大于0，则累加过期时间
                    newExpire = oldTicks + params.expireTicks;
                } else {
                    // 默认覆盖模式或原有过期时间为0
                    newExpire = params.expireTicks;
                }
            } else {
                newExpire = oldTicks;
            }
            Entry entry = CacheManager.snapshot(uuid)
                    .getOrDefault(stat, Collections.emptyMap())
                    .get(params.keyId);
            boolean oldPercent = entry != null && entry.isPercent();
            boolean oldMemory = entry != null && entry.isMemoryOnly();
            CacheManager.setAttribute(uuid, stat, params.keyId, oldVal, oldPercent, oldMemory, newExpire);
            String valStr = CommandUtils.formatValue(oldVal, oldPercent);
            lang.send(sender, "command-replace-success", Map.of(
                    "player", target.getName(),
                    "attribute", stat,
                    "value", valStr,
                    "key", params.keyId,
                    "memoryOnly", String.valueOf(params.memoryOnly),
                    "expireTicks", String.valueOf(newExpire)
            ));
        } else {
            long oldExpire = CacheManager.getExpireTicks(uuid, stat, params.keyId);
            // 根据replaceExpire决定是覆盖还是累加过期时间
            long newExpire;
            if (params.expireTicks >= 0) {
                if (!params.replaceExpire && oldExpire > 0) {
                    // 如果指定了:give选项且原有过期时间大于0，则累加过期时间
                    newExpire = oldExpire + params.expireTicks;
                } else {
                    // 默认覆盖模式或原有过期时间为0
                    newExpire = params.expireTicks;
                }
            } else {
                newExpire = oldExpire;
            }
            CacheManager.setAttribute(uuid, stat, params.keyId, vp.value, vp.percent, params.memoryOnly, newExpire);
            AttributeApplier.apply(uuid, stat, params.keyId, vp.value, vp.percent);
            String valStr = CommandUtils.formatValue(vp.value, vp.percent);
            lang.send(sender, "command-replace-success", Map.of(
                    "player", target.getName(),
                    "attribute", stat,
                    "value", valStr,
                    "key", params.keyId,
                    "memoryOnly", String.valueOf(params.memoryOnly),
                    "expireTicks", String.valueOf(newExpire)
            ));
        }
        return true;
    }
}
