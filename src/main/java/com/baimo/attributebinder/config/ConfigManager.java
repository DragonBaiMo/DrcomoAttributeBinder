package com.baimo.attributebinder.config;

import cn.drcomo.corelib.config.YamlUtil;
import cn.drcomo.corelib.util.DebugUtil;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * ConfigManager —— 使用 YamlUtil 管理配置文件。
 */
public class ConfigManager {
    private static ConfigManager instance;
    private final YamlUtil yaml;

    private ConfigManager(JavaPlugin plugin, DebugUtil logger) {
        this.yaml = new YamlUtil(plugin, logger);
        this.yaml.loadConfig("config");
    }

    public static void init(JavaPlugin plugin, DebugUtil logger) {
        if (instance == null) {
            instance = new ConfigManager(plugin, logger);
        }
    }

    public static ConfigManager get() {
        if (instance == null) {
            throw new IllegalStateException("ConfigManager 未初始化");
        }
        return instance;
    }

    public void reload() { yaml.reloadConfig("config"); }

    public YamlUtil getYaml() { return yaml; }

    public DebugUtil.LogLevel getLogLevel() {
        String level = yaml.getString("config", "debug-level", "INFO");
        return DebugUtil.LogLevel.fromString(level, DebugUtil.LogLevel.INFO);
    }

    public boolean useMySql() {
        // 判断存储类型，仅以 storage.type 字段决定是否使用 MySQL
        return "mysql".equalsIgnoreCase(
            yaml.getString("config", "storage.type", "sqlite")
        );
    }

    public String getSqliteFile() {
        // SQLite 数据库文件名，默认为示例配置中的 drcomoattributebinder.db
        return yaml.getString("config", "storage.sqlite-file", "drcomoattributebinder.db");
    }

    /** MySQL 主机 */
    public String getMySqlHost() {
        return yaml.getString("config", "storage.mysql.host", "localhost");
    }
    /** MySQL 端口 */
    public int getMySqlPort() {
        return yaml.getInt("config", "storage.mysql.port", 3306);
    }
    /** MySQL 数据库名 */
    public String getMySqlDatabase() {
        return yaml.getString("config", "storage.mysql.database", "drcomoattributebinder");
    }
    /** MySQL 用户名 */
    public String getMySqlUser() {
        return yaml.getString("config", "storage.mysql.user", "root");
    }
    /** MySQL 密码 */
    public String getMySqlPassword() {
        return yaml.getString("config", "storage.mysql.password", "");
    }

    /** 同步间隔（分钟），对应 config.yml 中 sync-interval-minutes */
    public int getSyncIntervalMinutes() {
        return yaml.getInt("config", "sync-interval-minutes", 10);
    }
    
    /** 获取自定义表名，默认为插件名 */
    public String getTableName() {
        return yaml.getString("config", "storage.table-name", "drcomoattributebinder");
    }
}
