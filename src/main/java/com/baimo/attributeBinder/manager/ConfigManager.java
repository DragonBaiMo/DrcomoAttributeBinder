package com.baimo.attributeBinder.manager;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import com.baimo.attributeBinder.util.DebugUtil.LogLevel;

import java.io.File;
import java.io.IOException;

public class ConfigManager {
    private static ConfigManager instance;
    private final JavaPlugin plugin;

    private FileConfiguration config;

    private ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public static void init(JavaPlugin plugin) {
        if (instance == null) {
            instance = new ConfigManager(plugin);
        }
    }

    public static ConfigManager get() {
        if (instance == null) {
            throw new IllegalStateException("ConfigManager 未初始化");
        }
        return instance;
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.exists()) {
            plugin.saveResource("config.yml", false);
        }
        config = new YamlConfiguration();
        try {
            config.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().severe("无法加载 config.yml: " + e.getMessage());
        }
    }

    public FileConfiguration getRaw() {
        return config;
    }

    /* ===================== 对外快捷访问 ===================== */

    public LogLevel getLogLevel() {
        return LogLevel.fromString(config.getString("debug-level", "INFO"), LogLevel.INFO);
    }

    /* ---------- 数据库 ---------- */
    public boolean useMySql() {
        // 兼容旧 key 和新 storage.type
        if (config.contains("database.use-mysql")) {
            return config.getBoolean("database.use-mysql");
        }
        return "mysql".equalsIgnoreCase(config.getString("storage.type", "sqlite"));
    }

    public String getSqliteFile() { return config.getString("storage.sqlite-file", "attributes.db"); }

    public String getMySqlHost() { return config.getString("storage.mysql.host", config.getString("database.host", "localhost")); }
    public int getMySqlPort() { return config.getInt("storage.mysql.port", config.getInt("database.port", 3306)); }
    public String getMySqlDatabase() { return config.getString("storage.mysql.database", config.getString("database.database", "minecraft")); }
    public String getMySqlUser() { return config.getString("storage.mysql.user", config.getString("database.user", "root")); }
    public String getMySqlPassword() { return config.getString("storage.mysql.password", config.getString("database.password", "")); }

    public int getSyncIntervalMinutes() { return config.getInt("sync-interval-minutes", 5); }
} 