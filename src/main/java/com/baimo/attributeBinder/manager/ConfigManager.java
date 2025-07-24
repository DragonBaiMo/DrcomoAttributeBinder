package com.baimo.attributeBinder.manager;

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
        if (yaml.contains("config", "database.use-mysql")) {
            return yaml.getBoolean("config", "database.use-mysql", false);
        }
        return "mysql".equalsIgnoreCase(yaml.getString("config", "storage.type", "sqlite"));
    }

    public String getSqliteFile() { return yaml.getString("config", "storage.sqlite-file", "attributes.db"); }

    public String getMySqlHost() { return yaml.getString("config", "storage.mysql.host", yaml.getString("config", "database.host", "localhost")); }
    public int getMySqlPort() { return yaml.getInt("config", "storage.mysql.port", yaml.getInt("config", "database.port", 3306)); }
    public String getMySqlDatabase() { return yaml.getString("config", "storage.mysql.database", yaml.getString("config", "database.database", "minecraft")); }
    public String getMySqlUser() { return yaml.getString("config", "storage.mysql.user", yaml.getString("config", "database.user", "root")); }
    public String getMySqlPassword() { return yaml.getString("config", "storage.mysql.password", yaml.getString("config", "database.password", "")); }

    public int getSyncIntervalMinutes() { return yaml.getInt("config", "sync-interval-minutes", 5); }
}
