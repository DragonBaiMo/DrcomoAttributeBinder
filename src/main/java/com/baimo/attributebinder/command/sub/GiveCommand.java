package com.baimo.attributebinder.command.sub;

import com.baimo.attributebinder.command.CommandUtil;
import com.baimo.attributebinder.manager.CacheManager;
import com.baimo.attributebinder.manager.CacheManager.Entry;
import com.baimo.attributebinder.manager.AggregatedApplier;
import com.baimo.attributebinder.manager.LangManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * give 子命令处理
 */
public class GiveCommand {
    private final LangManager lang = LangManager.get();

    public void execute(CommandSender sender, String[] args) {
        if (args.length < 4 || args.length > 7) {
            lang.send(sender, "command-give-usage");
            return;
        }
        Player target = CommandUtil.getPlayer(args[1], sender);
        if (target == null) return;

        String stat = args[2].toUpperCase();
        CommandUtil.ValuePair vp = CommandUtil.parseValue(args[3], sender, "command-give-invalid-number");
        if (vp == null) return;

        CommandUtil.OptParams params = CommandUtil.parseGiveParams(args, sender);
        if (params == null) return;

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
            String valStr = CommandUtil.formatValue(oldVal, oldPercent);
            CommandUtil.sendGiveSuccess(sender, target.getName(), stat, valStr, params.keyId, oldMemory, newExpire);
        } else {
            long oldExpire = CacheManager.getExpireTicks(uuid, stat, params.keyId);
            long newExpire = params.expireTicks >= 0 ? oldExpire + params.expireTicks : oldExpire;
            CacheManager.setAttribute(uuid, stat, params.keyId, newVal, vp.percent, params.memoryOnly, newExpire);
            AggregatedApplier.applyFromCache(uuid, stat);
            String valStr = CommandUtil.formatValue(newVal, vp.percent);
            CommandUtil.sendGiveSuccess(sender, target.getName(), stat, valStr, params.keyId, params.memoryOnly, newExpire);
        }
    }
}
