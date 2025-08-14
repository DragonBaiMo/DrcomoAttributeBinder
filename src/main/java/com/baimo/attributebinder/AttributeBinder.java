package com.baimo.attributebinder;

import cn.drcomo.corelib.async.AsyncTaskManager;
import cn.drcomo.corelib.hook.placeholder.PlaceholderAPIUtil;
import cn.drcomo.corelib.message.MessageService;
import cn.drcomo.corelib.util.DebugUtil;
import com.baimo.attributebinder.command.AttributeBinderCommand;
import com.baimo.attributebinder.listener.PlayerListener;
import com.baimo.attributebinder.placeholder.PlaceholderHandler;
import com.baimo.attributebinder.task.FlushTask;
import com.baimo.attributebinder.task.ExpireTask;
import com.baimo.attributebinder.config.ConfigManager;
import com.baimo.attributebinder.config.LangManager;
import com.baimo.attributebinder.cache.CacheManager;
import com.baimo.attributebinder.service.AttributeApplier;
import com.baimo.attributebinder.storage.AttributeBinderContext;
import com.baimo.attributebinder.storage.JdbcStorageManager;
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

    // Debug 工具
    private DebugUtil debug;
    // PlaceholderAPI 工具
    private PlaceholderAPIUtil papiUtil;
    // 占位符处理器
    private PlaceholderHandler placeholderHandler;
    // 多语言消息服务
    private MessageService messages;
    // 数据存储管理
    private JdbcStorageManager storage;
    // 定时刷新任务
    private BukkitTask flushTask;
    // 异步任务管理器
    private AsyncTaskManager taskManager;

    @Override
    public void onEnable() {
        INSTANCE = this;

        initResources();
        initConfigAndDebug();
        initPlaceholderAPI();
        initMessages();

        // 初始化异步任务管理器
        taskManager = new AsyncTaskManager(this, debug);

        debug.info("插件正在加载 ...");

        initStorage();
        initCommands();
        initListeners();
        loadAllOnlinePlayers();
        initTasks();

        debug.info("插件已启用，版本 " + getDescription().getVersion());
    }

    @Override
    public void onDisable() {
        // 取消刷新任务
        if (flushTask != null) {
            flushTask.cancel();
        }
        if (taskManager != null) {
            taskManager.shutdown();
        }
        // 保存所有缓存数据并关闭存储
        // 先获取快照，再清除内存缓存，保证保存后清空
        Map<UUID, Map<String, Map<String, CacheManager.Entry>>> cacheSnapshot = CacheManager.snapshot();
        storage.saveAll(cacheSnapshot);
        storage.close();
        // 清除所有玩家内存缓存
        cacheSnapshot.keySet().forEach(CacheManager::clear);
        debug.info("插件已卸载。");
    }

    /** 获取插件实例 */
    public static AttributeBinder getInstance() {
        return INSTANCE;
    }

    /**
     * 获取异步任务管理器。
     *
     * @return AsyncTaskManager 实例
     */
    public static AsyncTaskManager getAsyncTaskManager() {
        return INSTANCE.taskManager;
    }

    /**
     * 重置刷新任务的间隔
     * @param intervalMinutes 同步间隔（分钟），≤0 则取消任务
     */
    public void resetFlushTask(int intervalMinutes) {
        // 复用：按新间隔重新调度
        scheduleFlushTask(intervalMinutes);
    }

    /** 根据配置重新更新日志级别 */
    public void updateDebugLevel() {
        debug.setLevel(ConfigManager.get().getLogLevel());
    }

    // ------------------------- 私有初始化方法 -------------------------

    /** 初始化默认配置文件和资源文件 */
    private void initResources() {
        saveDefaultConfig();
        saveResource("lang.yml", false);
    }

    /** 初始化 ConfigManager 与 DebugUtil */
    private void initConfigAndDebug() {
        debug = new DebugUtil(this, DebugUtil.LogLevel.INFO);
        ConfigManager.init(this, debug);
        debug.setLevel(ConfigManager.get().getLogLevel());
    }

    /** 初始化 PlaceholderAPI 与占位符注册 */
    private void initPlaceholderAPI() {
        papiUtil = new PlaceholderAPIUtil(this, getName().toLowerCase());
        placeholderHandler = new PlaceholderHandler(papiUtil);
        placeholderHandler.registerPlaceholders();
    }

    /** 初始化消息服务与多语言管理 */
    private void initMessages() {
        messages = new MessageService(this, debug, ConfigManager.get().getYaml(), papiUtil, "lang", "");
        LangManager.init(messages);
    }

    /** 初始化存储管理与上下文 */
    private void initStorage() {
        storage = new JdbcStorageManager(this, debug);
        AttributeBinderContext.setStorage(storage);
    }

    /** 注册命令执行器与补全器 */
    private void initCommands() {
        AttributeBinderCommand mainCommand = new AttributeBinderCommand();
        getCommand("attributebinder").setExecutor(mainCommand);
        getCommand("attributebinder").setTabCompleter(mainCommand);
    }

    /** 注册事件监听器 */
    private void initListeners() {
        getServer().getPluginManager().registerEvents(new PlayerListener(), this);
    }

    /** 加载当前在线所有玩家的数据 */
    private void loadAllOnlinePlayers() {
        Bukkit.getOnlinePlayers().forEach(this::loadPlayerData);
    }

    /**
     * 加载单个玩家的属性数据
     * @param player 玩家对象
     */
    private void loadPlayerData(Player player) {
        UUID uuid = player.getUniqueId();
        Map<String, Map<String, CacheManager.Entry>> attrs = storage.loadAttributes(uuid);
        attrs.forEach((stat, keyMap) ->
            keyMap.forEach((keyId, entry) ->
                applyEntry(uuid, stat, keyId, entry)
            )
        );
    }

    /**
     * 处理并应用单条属性缓存
     * @param uuid 玩家 UUID
     * @param stat 属性类型
     * @param keyId 属性键
     * @param entry 缓存条目
     */
    private void applyEntry(UUID uuid, String stat, String keyId, CacheManager.Entry entry) {
        // 从数据库加载的属性都是持久化的，不是memoryOnly
        CacheManager.setAttribute(uuid, stat, keyId, entry.getValue(), entry.isPercent(), false, entry.getExpireTicks());
        AttributeApplier.apply(uuid, stat, keyId, entry.getValue(), entry.isPercent());
    }

    /** 初始化所有定时任务，包括刷新与过期清理 */
    private void initTasks() {
        int interval = ConfigManager.get().getSyncIntervalMinutes();
        scheduleFlushTask(interval);
        scheduleExpireTask();
    }

    /**
     * 调度或取消异步刷新任务
     * @param intervalMinutes 同步间隔（分钟），≤0 则取消任务
     */
    private void scheduleFlushTask(int intervalMinutes) {
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
        if (intervalMinutes > 0) {
            long ticks = intervalMinutes * 60L * 20;
            flushTask = new FlushTask().runTaskTimerAsynchronously(this, ticks, ticks);
        }
    }

    /** 调度过期缓存清理任务 */
    private void scheduleExpireTask() {
        long ticks = CacheManager.CLEANUP_INTERVAL_TICKS;
		new ExpireTask(storage).runTaskTimerAsynchronously(this, ticks, ticks);
    }
}
