package com.baimo.attributeBinder;

import cn.drcomo.corelib.hook.placeholder.PlaceholderAPIUtil;
import cn.drcomo.corelib.message.MessageService;
import cn.drcomo.corelib.util.DebugUtil;
import com.baimo.attributeBinder.command.AttributeBinderCommand;
import com.baimo.attributeBinder.listener.PlayerListener;
import com.baimo.attributeBinder.manager.*;
import com.baimo.attributeBinder.task.FlushTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;

/**
 * AttributeBinder 插件主类。
 */
public final class AttributeBinder extends JavaPlugin {

    private static AttributeBinder INSTANCE;

    private DebugUtil debug;
    private PlaceholderAPIUtil papiUtil;
    private MessageService messages;
    private StorageManager storage;
    private BukkitTask flushTask;

    @Override
    public void onEnable() {
        INSTANCE = this;

        saveDefaultConfig();
        saveResource("lang.yml", false);

        debug = new DebugUtil(this, DebugUtil.LogLevel.INFO);
        ConfigManager.init(this, debug);
        debug.setLevel(ConfigManager.get().getLogLevel());

        papiUtil = new PlaceholderAPIUtil(this, getName().toLowerCase());
        registerPlaceholders();

        messages = new MessageService(this, debug, ConfigManager.get().getYaml(), papiUtil, "lang", "");
        LangManager.init(messages);

        debug.info("插件正在加载 ...");

        storage = new JdbcStorageManager(this, debug);
        AttributeBinderContext.setStorage(storage);

        AttributeBinderCommand mainCommand = new AttributeBinderCommand();
        getCommand("attributebinder").setExecutor(mainCommand);
        getCommand("attributebinder").setTabCompleter(mainCommand);

        getServer().getPluginManager().registerEvents(new PlayerListener(), this);

        Bukkit.getOnlinePlayers().forEach(this::loadPlayerData);

        int interval = ConfigManager.get().getSyncIntervalMinutes();
        if (interval > 0) {
            flushTask = new FlushTask().runTaskTimerAsynchronously(this, interval * 60L * 20, interval * 60L * 20);
        }

        debug.info("插件已启用，版本 " + getDescription().getVersion());
    }

    private void registerPlaceholders() {
        papiUtil.register("attr", (player, args) -> {
            if (player == null) return "0";
            String stat = args.toUpperCase();
            double val = CacheManager.getAttribute(player.getUniqueId(), stat);
            return String.valueOf(val);
        });
        papiUtil.register("list", (player, args) -> {
            if (player == null) return "";
            Map<String, Double> map = CacheManager.aggregatedAttributes(player.getUniqueId());
            if (map.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            map.forEach((k, v) -> {
                boolean pc = CacheManager.isPercent(player.getUniqueId(), k);
                sb.append(k).append("=").append(v).append(pc ? "%" : "").append(", ");
            });
            if (sb.length() >= 2) sb.setLength(sb.length() - 2);
            return sb.toString();
        });
    }

    private void loadPlayerData(Player player) {
        UUID uuid = player.getUniqueId();
        Map<String, Map<String, CacheManager.Entry>> attrs = storage.loadAttributes(uuid);
        attrs.forEach((stat, keyMap) -> keyMap.forEach((keyId, entry) -> {
            CacheManager.setAttribute(uuid, stat, keyId, entry.getValue(), entry.isPercent());
            AttributeApplier.apply(uuid, stat, keyId, entry.getValue(), entry.isPercent());
        }));
    }

    @Override
    public void onDisable() {
        if (flushTask != null) flushTask.cancel();
        storage.saveAll(CacheManager.snapshot());
        storage.close();
        debug.info("插件已卸载。");
    }

    public static AttributeBinder getInstance() { return INSTANCE; }

    public void resetFlushTask(int intervalMinutes) {
        if (flushTask != null) flushTask.cancel();
        if (intervalMinutes > 0) {
            flushTask = new FlushTask().runTaskTimerAsynchronously(this, intervalMinutes * 60L * 20, intervalMinutes * 60L * 20);
        } else {
            flushTask = null;
        }
    }

    public void updateDebugLevel() {
        debug.setLevel(ConfigManager.get().getLogLevel());
    }
}
