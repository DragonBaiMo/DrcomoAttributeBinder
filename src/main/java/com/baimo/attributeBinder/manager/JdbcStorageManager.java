package com.baimo.attributeBinder.manager;

import cn.drcomo.corelib.util.DebugUtil;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class JdbcStorageManager implements StorageManager {

    private final JavaPlugin plugin;
    private final DebugUtil debug;
    private HikariDataSource dataSource;

    public JdbcStorageManager(JavaPlugin plugin, DebugUtil logger) {
        this.plugin = plugin;
        this.debug = logger;
        initPool();
        createTableIfNotExists();
    }

    private void initPool() {
        HikariConfig cfg = new HikariConfig();
        if (ConfigManager.get().useMySql()) {
            String jdbc = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&characterEncoding=utf8",
                    ConfigManager.get().getMySqlHost(),
                    ConfigManager.get().getMySqlPort(),
                    ConfigManager.get().getMySqlDatabase());
            cfg.setJdbcUrl(jdbc);
            cfg.setUsername(ConfigManager.get().getMySqlUser());
            cfg.setPassword(ConfigManager.get().getMySqlPassword());
            cfg.addDataSourceProperty("cachePrepStmts","true");
            cfg.addDataSourceProperty("prepStmtCacheSize","250");
            cfg.addDataSourceProperty("prepStmtCacheSqlLimit","2048");
        } else {
            File dbFile = new File(plugin.getDataFolder(), ConfigManager.get().getSqliteFile());
            cfg.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        }
        cfg.setMaximumPoolSize(10);
        dataSource = new HikariDataSource(cfg);
    }

    private void createTableIfNotExists() {
        String sql = "CREATE TABLE IF NOT EXISTS player_attributes (" +
                "uuid TEXT NOT NULL," +
                "stat TEXT NOT NULL," +
                "key_id TEXT NOT NULL," +
                "value REAL NOT NULL," +
                "percent INTEGER NOT NULL," +
                "PRIMARY KEY(uuid, stat, key_id)" +
                ")";
        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            plugin.getLogger().severe("无法创建表: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Map<String, CacheManager.Entry>> loadAttributes(UUID uuid) {
        Map<String, Map<String, CacheManager.Entry>> map = new HashMap<>();
        String sql = "SELECT stat, key_id, value, percent FROM player_attributes WHERE uuid = ?";
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String stat = rs.getString(1);
                    String keyId = rs.getString(2);
                    double value = rs.getDouble(3);
                    boolean percent = rs.getInt(4) == 1;
                    map.computeIfAbsent(stat, k -> new ConcurrentHashMap<>())
                        .put(keyId, new CacheManager.Entry(value, percent));
                }
            }
        } catch (SQLException e) {
            debug.error("读取玩家属性失败: " + e.getMessage());
        }
        return map;
    }

    @Override
    public void saveAttributes(UUID uuid, Map<String, Map<String, CacheManager.Entry>> attributes) {
        if (attributes == null) return;
        String sql = "REPLACE INTO player_attributes(uuid, stat, key_id, value, percent) VALUES(?, ?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Map.Entry<String, Map<String, CacheManager.Entry>> statEntry : attributes.entrySet()) {
                for (Map.Entry<String, CacheManager.Entry> keyEntry : statEntry.getValue().entrySet()) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, statEntry.getKey());
                    ps.setString(3, keyEntry.getKey());
                    ps.setDouble(4, keyEntry.getValue().getValue());
                    ps.setInt(5, keyEntry.getValue().isPercent()?1:0);
                    ps.addBatch();
                }
            }
            ps.executeBatch();
        } catch (SQLException e) {
            debug.error("保存玩家属性失败: " + e.getMessage());
        }
    }

    @Override
    public void saveAll(Map<UUID, Map<String, Map<String, CacheManager.Entry>>> all) {
        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false);
            String sql = "REPLACE INTO player_attributes(uuid, stat, key_id, value, percent) VALUES(?,?,?,?,?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (Map.Entry<UUID, Map<String, Map<String, CacheManager.Entry>>> player : all.entrySet()) {
                    for (Map.Entry<String, Map<String, CacheManager.Entry>> statEntry : player.getValue().entrySet()) {
                        for (Map.Entry<String, CacheManager.Entry> keyEntry : statEntry.getValue().entrySet()) {
                            ps.setString(1, player.getKey().toString());
                            ps.setString(2, statEntry.getKey());
                            ps.setString(3, keyEntry.getKey());
                            ps.setDouble(4, keyEntry.getValue().getValue());
                            ps.setInt(5, keyEntry.getValue().isPercent()?1:0);
                            ps.addBatch();
                        }
                    }
                }
                ps.executeBatch();
            }
            conn.commit();
        } catch (SQLException e) {
            debug.error("批量保存失败: " + e.getMessage());
            try { if (conn != null) conn.rollback(); } catch (SQLException ignored) {}
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignored) {}
            }
        }
    }

    @Override
    public void close() {
        dataSource.close();
    }
} 