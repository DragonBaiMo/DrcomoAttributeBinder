package com.baimo.attributeBinder;

import com.baimo.attributeBinder.command.AttributeBinderCommand;
import com.baimo.attributeBinder.listener.PlayerListener;
import com.baimo.attributeBinder.manager.*;
import com.baimo.attributeBinder.task.FlushTask;
import com.baimo.attributeBinder.util.DebugUtil;
import com.baimo.attributeBinder.util.PlaceholderUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Map;
import java.util.UUID;

public final class AttributeBinder extends JavaPlugin {

    private static AttributeBinder INSTANCE;

    private DebugUtil debug;
    private StorageManager storage;
    private BukkitTask flushTask;

    @Override
    public void onEnable() {
        INSTANCE = this;

        // 1. 保存默认文件
        saveDefaultConfig();
        saveResource("lang.yml", false);

        // 2. 初始化 Config & Lang
        ConfigManager.init(this);
        LangManager.init(this);

        // 3. 日志
        debug = new DebugUtil(this, ConfigManager.get().getLogLevel());
        debug.info("插件正在加载 ...");

        // 4. 数据库
        storage = new JdbcStorageManager(this);
        AttributeBinderContext.setStorage(storage);

        // 5. 占位符 & 条件解析器
        PlaceholderUtil.initialize(this);

        // 6. 注册指令（统一一个指令类）
        AttributeBinderCommand mainCommand = new AttributeBinderCommand();
        getCommand("attributebinder").setExecutor(mainCommand);
        getCommand("attributebinder").setTabCompleter(mainCommand);

        // 7. 事件
        getServer().getPluginManager().registerEvents(new PlayerListener(), this);

        // 7.1 加载当前已在线玩家的属性数据（支持热重载场景）
        Bukkit.getOnlinePlayers().forEach(player -> {
            UUID uuid = player.getUniqueId();
            Map<String, Map<String, CacheManager.Entry>> attrs = storage.loadAttributes(uuid);
            attrs.forEach((stat, keyMap) -> keyMap.forEach((keyId, entry) -> {
                CacheManager.setAttribute(uuid, stat, keyId, entry.getValue(), entry.isPercent());
                AttributeApplier.apply(uuid, stat, keyId, entry.getValue(), entry.isPercent());
            }));
        });

        // 8. 定时任务
        int interval = ConfigManager.get().getSyncIntervalMinutes();
        if (interval > 0) {
            flushTask = new FlushTask().runTaskTimerAsynchronously(this, interval * 60L * 20, interval * 60L * 20);
        }

        debug.info("插件已启用，版本 " + getDescription().getVersion());
    }

    @Override
    public void onDisable() {
        if (flushTask != null) flushTask.cancel();
        // 强制写库
        storage.saveAll(CacheManager.snapshot());
        storage.close();
        debug.info("插件已卸载。");
    }

    public static AttributeBinder getInstance() { return INSTANCE; }

    public void resetFlushTask(int intervalMinutes) {
        if (flushTask != null) flushTask.cancel();
        if (intervalMinutes > 0) {
            flushTask = new com.baimo.attributeBinder.task.FlushTask()
                    .runTaskTimerAsynchronously(this, intervalMinutes * 60L * 20, intervalMinutes * 60L * 20);
        } else {
            flushTask = null;
        }
    }

    public void updateDebugLevel() {
        this.debug = new DebugUtil(this, ConfigManager.get().getLogLevel());
    }
}
