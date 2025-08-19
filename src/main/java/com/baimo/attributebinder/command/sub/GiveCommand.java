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
        if (args.length < 4) {
            CommandUtils.sendErrorWithOriginal(lang, sender, "cmd.usage.give");
            return true;
        }
        Player target = CommandUtils.getPlayer(args[1], sender, lang);
        if (target == null) return true;

        String stat = args[2].toUpperCase();
        ValuePair vp = CommandUtils.parseValue(args[3], sender, "cmd.error.invalid_number", lang);
        if (vp == null) return true;

        OptParams params = CommandUtils.parseGiveParams(args, sender, lang);
        if (params == null) return true;

        UUID uuid = target.getUniqueId();
        double oldVal = CacheManager.getAttribute(uuid, stat, params.keyId);
        // 获取旧记录以判断类型（百分比/数值）是否一致
        Entry entry = CacheManager.snapshot(uuid)
                .getOrDefault(stat, Collections.emptyMap())
                .get(params.keyId);
        boolean oldPercent = entry != null && entry.isPercent();

        // 计算候选新值：
        // - 若旧条目存在且类型与本次一致（均为百分比或均为数值），则做叠加
        // - 若类型不一致或不存在旧条目，则视为切换类型，直接采用本次值
        double candidate = (entry != null && oldPercent == vp.percent)
                ? (oldVal + vp.value)
                : vp.value;
        // 上限裁剪：百分比仅按数值位裁剪，但生效仍按百分比
        double newVal = params.maxValue >= 0 ? Math.min(candidate, params.maxValue) : candidate;

		if (Double.compare(oldVal, newVal) == 0) {
			long oldTicks = CacheManager.getExpireTicks(uuid, stat, params.keyId);
			// 过期时间新语义：-1=永久，0=移除，>0 按模式处理；未提供时解析层已默认 -1
			long newExpire;
			if (params.expireTicks == -1L) {
				newExpire = -1L;
			} else if (params.expireTicks == 0L) {
				newExpire = 0L;
			} else if (params.expireTicks > 0L) {
				newExpire = params.replaceExpire ? params.expireTicks : (oldTicks > 0L ? oldTicks + params.expireTicks : params.expireTicks);
			} else {
				newExpire = oldTicks;
			}
            boolean oldMemory = entry != null && entry.isMemoryOnly();
            CacheManager.setAttribute(uuid, stat, params.keyId, oldVal, oldPercent, oldMemory, newExpire);
            String valStr = CommandUtils.formatValue(oldVal, oldPercent);
			CommandUtils.sendSuccess(lang, sender, "cmd.give.success", Map.of(
                    "player", target.getName(),
                    "attribute", stat,
                    "value", valStr,
                    "key", params.keyId,
                    "memoryOnly", String.valueOf(oldMemory),
					"expireTicks", String.valueOf(newExpire)
			));
		} else {
			long oldExpire = CacheManager.getExpireTicks(uuid, stat, params.keyId);
			// 过期时间新语义：-1=永久，0=移除，>0 按模式处理；未提供时解析层已默认 -1
			long newExpire;
			if (params.expireTicks == -1L) {
				newExpire = -1L;
			} else if (params.expireTicks == 0L) {
				newExpire = 0L;
			} else if (params.expireTicks > 0L) {
				newExpire = params.replaceExpire ? params.expireTicks : (oldExpire > 0L ? oldExpire + params.expireTicks : params.expireTicks);
			} else {
				newExpire = oldExpire;
			}
            CacheManager.setAttribute(uuid, stat, params.keyId, newVal, vp.percent, params.memoryOnly, newExpire);
            AttributeApplier.apply(uuid, stat, params.keyId, newVal, vp.percent);
            String valStr = CommandUtils.formatValue(newVal, vp.percent);
			CommandUtils.sendSuccess(lang, sender, "cmd.give.success", Map.of(
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

    @Override
    public java.util.List<String> optionSuggestions(int argIndex, String partial, CommandSender sender, String[] args) {
        java.util.List<String> options = new java.util.ArrayList<>();
        // 最简选项集合：仅提供短参数
        options.add("--k=");
        options.add("--mem=");
        options.add("--exp=");
        options.add("--md=");
        options.add("--mx=");

        if (partial == null || partial.isEmpty()) return options;
        java.util.List<String> result = new java.util.ArrayList<>();
        for (String opt : options) {
            if (opt.regionMatches(true, 0, partial, 0, partial.length())) {
                result.add(opt);
            }
        }
        return result;
    }
}
