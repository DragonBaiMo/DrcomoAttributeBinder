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
 * GiveCommand — 处理 /ab give 命令
 */
public class GiveCommand implements SubCommand {
    private final LangManager lang = LangManager.get();

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 4 || args.length > 7) {
            lang.send(sender, "command-give-usage");
            return true;
        }
        Player target = CommandUtils.getPlayer(args[1], sender, lang);
        if (target == null) return true;

        String stat = args[2].toUpperCase();
        ValuePair vp = CommandUtils.parseValue(args[3], sender, "command-give-invalid-number", lang);
        if (vp == null) return true;

        OptParams params = CommandUtils.parseGiveParams(args, sender, lang);
        if (params == null) return true;

        UUID uuid = target.getUniqueId();
        double oldVal = CacheManager.getAttribute(uuid, stat, params.keyId);
        double newVal = oldVal + vp.value;

        if (Double.compare(oldVal, newVal) == 0) {
            long oldTicks = CacheManager.getExpireTicks(uuid, stat, params.keyId);
            long newExpire = params.expireTicks >= 0 ? params.expireTicks : oldTicks;
            Entry entry = CacheManager.snapshot(uuid)
                    .getOrDefault(stat, Collections.emptyMap())
                    .get(params.keyId);
            boolean oldPercent = entry != null && entry.isPercent();
            boolean oldMemory = entry != null && entry.isMemoryOnly();
            CacheManager.setAttribute(uuid, stat, params.keyId, oldVal, oldPercent, oldMemory, newExpire);
            String valStr = CommandUtils.formatValue(oldVal, oldPercent);
            lang.send(sender, "command-give-success", Map.of(
                    "player", target.getName(),
                    "attribute", stat,
                    "value", valStr,
                    "key", params.keyId,
                    "memoryOnly", String.valueOf(oldMemory),
                    "expireTicks", String.valueOf(newExpire)
            ));
        } else {
            long oldExpire = CacheManager.getExpireTicks(uuid, stat, params.keyId);
            long newExpire = params.expireTicks >= 0 ? oldExpire + params.expireTicks : oldExpire;
            CacheManager.setAttribute(uuid, stat, params.keyId, newVal, vp.percent, params.memoryOnly, newExpire);
            AttributeApplier.apply(uuid, stat, params.keyId, newVal, vp.percent);
            String valStr = CommandUtils.formatValue(newVal, vp.percent);
            lang.send(sender, "command-give-success", Map.of(
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
