package com.baimo.attributeBinder.manager;

import com.baimo.attributeBinder.util.ColorUtil;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class LangManager {
    private static LangManager instance;

    private final JavaPlugin plugin;
    private FileConfiguration lang;
    private final Map<String, String> cache = new HashMap<>();

    private LangManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public static void init(JavaPlugin plugin) {
        if (instance == null) {
            instance = new LangManager(plugin);
        }
    }

    public static LangManager get() {
        if (instance == null) {
            throw new IllegalStateException("LangManager 未初始化");
        }
        return instance;
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "lang.yml");
        if (!file.exists()) {
            plugin.saveResource("lang.yml", false);
        }
        lang = new YamlConfiguration();
        try {
            lang.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().severe("无法加载 lang.yml: " + e.getMessage());
        }
        cache.clear();
    }

    public String get(String key) {
        return cache.computeIfAbsent(key, k -> {
            String path = k;
            String value = lang.getString(path);
            if (value == null) {
                value = lang.getString("messages." + k);
            }
            if (value == null) {
                value = "&cMissing lang key: " + k;
            }

            // 替换 %prefix% 占位符
            String prefix = lang.getString("prefix", "");
            value = value.replace("%prefix%", prefix);

            return ColorUtil.translateColors(value);
        });
    }
} 