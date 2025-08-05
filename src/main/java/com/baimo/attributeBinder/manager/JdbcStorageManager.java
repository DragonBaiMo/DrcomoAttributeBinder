package com.baimo.attributeBinder.manager;

import cn.drcomo.corelib.util.DebugUtil;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class JdbcStorageManager implements StorageManager {

    private final JavaPlugin plugin;
    private final DebugUtil debug;
    private HikariDataSource dataSource;
    private final String tableName;
    
    // SQL语句模板，将在构造函数中替换表名
    private final String CREATE_TABLE_SQL;
    private final String REPLACE_SQL;
    private final String DELETE_SQL;
    private final String DELETE_STAT_SQL;
    private final String DELETE_ATTRIBUTE_SQL;
    private final String DELETE_KEY_SQL;
    private final String SELECT_SQL;
    private final String ALTER_TABLE_SQL;

    public JdbcStorageManager(JavaPlugin plugin, DebugUtil logger) {
        this.plugin = plugin;
        this.debug = logger;
        this.tableName = ConfigManager.get().getTableName();
        
        // 初始化SQL语句，使用配置的表名
        this.CREATE_TABLE_SQL = "CREATE TABLE IF NOT EXISTS " + tableName + " ("
                + "uuid VARCHAR(36) NOT NULL,"
                + "stat VARCHAR(64) NOT NULL,"
                + "key_id VARCHAR(64) NOT NULL,"
                + "value REAL NOT NULL,"
                + "percent INTEGER NOT NULL,"
                + "expire_time BIGINT DEFAULT 0,"
                + "PRIMARY KEY(uuid, stat, key_id)"
                + ")";
        this.REPLACE_SQL = "REPLACE INTO " + tableName + "(uuid, stat, key_id, value, percent, expire_time) VALUES(?, ?, ?, ?, ?, ?)";
        this.DELETE_SQL = "DELETE FROM " + tableName + " WHERE uuid = ?";
        this.DELETE_STAT_SQL = "DELETE FROM " + tableName + " WHERE uuid = ? AND stat = ?";
        this.DELETE_ATTRIBUTE_SQL = "DELETE FROM " + tableName + " WHERE uuid = ? AND stat = ? AND key_id = ?";
        this.DELETE_KEY_SQL = "DELETE FROM " + tableName + " WHERE uuid = ? AND key_id = ?";
        this.SELECT_SQL = "SELECT stat, key_id, value, percent, expire_time FROM " + tableName + " WHERE uuid = ?";
        this.ALTER_TABLE_SQL = "ALTER TABLE " + tableName + " ADD COLUMN expire_time BIGINT DEFAULT 0";
        
        initPool();
        createTableIfNotExists();
    }

    /**
     * 初始化Hikari连接池，根据配置选择MySQL或SQLite
     */
    private void initPool() {
        HikariConfig cfg = new HikariConfig();
        if (ConfigManager.get().useMySql()) {
            String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&characterEncoding=utf8",
                    ConfigManager.get().getMySqlHost(),
                    ConfigManager.get().getMySqlPort(),
                    ConfigManager.get().getMySqlDatabase());
            cfg.setJdbcUrl(jdbcUrl);
            cfg.setUsername(ConfigManager.get().getMySqlUser());
            cfg.setPassword(ConfigManager.get().getMySqlPassword());
            cfg.addDataSourceProperty("cachePrepStmts", "true");
            cfg.addDataSourceProperty("prepStmtCacheSize", "250");
            cfg.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        } else {
            File dbFile = new File(plugin.getDataFolder(), ConfigManager.get().getSqliteFile());
            cfg.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        }
        cfg.setMaximumPoolSize(10);
        dataSource = new HikariDataSource(cfg);
    }

    /**
     * 创建表格，如果不存在则新建，并确保expire_time字段存在
     */
    private void createTableIfNotExists() {
        try (Connection conn = getConnection();
             Statement st = conn.createStatement()) {
            st.execute(CREATE_TABLE_SQL);
            // 为现有表添加expire_time字段（如果不存在）
            try {
                st.execute(ALTER_TABLE_SQL);
            } catch (SQLException e) {
                // 字段已存在，忽略错误
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("无法创建表: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Map<String, CacheManager.Entry>> loadAttributes(UUID uuid) {
        Map<String, Map<String, CacheManager.Entry>> map = new HashMap<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String stat = rs.getString(1);
                    String keyId = rs.getString(2);
                    double value = rs.getDouble(3);
                    boolean percent = rs.getInt(4) == 1;
                    long expireTime = rs.getLong(5);
                    
                    // 将绝对过期时间转换为相对时间（剩余ticks）
                    long expireTicks = 0;
                    if (expireTime > 0) {
                        long currentTime = System.currentTimeMillis();
                        if (expireTime > currentTime) {
                            expireTicks = (expireTime - currentTime) / 50; // 转换为ticks（1tick=50ms）
                        }
                        // 如果已过期，expireTicks保持为0，会在下次清理时被移除
                    }
                    
                    CacheManager.Entry entry = new CacheManager.Entry(value, percent);
                    entry.setExpireTicks(expireTicks);
                    // 从数据库加载的属性都不是memoryOnly的
                    entry.setMemoryOnly(false);
                    map.computeIfAbsent(stat, k -> new ConcurrentHashMap<>())
                       .put(keyId, entry);
                }
            }
        } catch (SQLException e) {
            debug.error("读取玩家属性失败: " + e.getMessage());
        }
        return map;
    }

    @Override
    public void saveAttributes(UUID uuid, Map<String, Map<String, CacheManager.Entry>> attributes) {
        if (attributes == null) {
            return;
        }
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(REPLACE_SQL)) {
            for (Map.Entry<String, Map<String, CacheManager.Entry>> statEntry : attributes.entrySet()) {
                String stat = statEntry.getKey();
                for (Map.Entry<String, CacheManager.Entry> keyEntry : statEntry.getValue().entrySet()) {
                    CacheManager.Entry entry = keyEntry.getValue();
                    if (entry.isMemoryOnly()) {
                        // 仅内存属性不持久化
                        continue;
                    }
                    fillStatement(ps, uuid.toString(), stat, keyEntry.getKey(), entry);
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
            conn = getConnection();
            conn.setAutoCommit(false);

            // 先删除所有缓存快照中的玩家记录
            batchDeleteByUuid(conn, all.keySet());

            // 批量插入当前缓存中非 memoryOnly 的持久化属性
            batchReplaceEntries(conn, all);

            conn.commit();
        } catch (SQLException e) {
            debug.error("批量保存失败: " + e.getMessage());
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                }
            }
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException ignored) {
                }
            }
        }
    }

    @Override
    public void deleteAttribute(UUID uuid, String stat, String keyId) {
        if (keyId == null) {
            // 删除该stat的所有keyId
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(DELETE_STAT_SQL)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, stat.toUpperCase());
                ps.executeUpdate();
            } catch (SQLException e) {
                debug.error("删除玩家属性失败: " + e.getMessage());
            }
        } else {
            // 删除指定的stat和keyId
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(DELETE_ATTRIBUTE_SQL)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, stat.toUpperCase());
                ps.setString(3, keyId);
                ps.executeUpdate();
            } catch (SQLException e) {
                debug.error("删除玩家属性失败: " + e.getMessage());
            }
        }
    }

    @Override
    public void deleteAttributesByKey(UUID uuid, String keyId) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(DELETE_KEY_SQL)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, keyId);
            ps.executeUpdate();
        } catch (SQLException e) {
            debug.error("删除玩家keyId属性失败: " + e.getMessage());
        }
    }

    @Override
    public void deleteAllAttributes(UUID uuid) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(DELETE_SQL)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            debug.error("删除玩家所有属性失败: " + e.getMessage());
        }
    }

    @Override
    public void deleteAttributesBatch(UUID uuid, List<Entry<String, String>> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        StringBuilder sql = new StringBuilder("DELETE FROM ")
            .append(tableName).append(" WHERE uuid = ? AND (");
        for (int i = 0; i < entries.size(); i++) {
            sql.append("(stat=? AND key_id=?)");
            if (i < entries.size() - 1) {
                sql.append(" OR ");
            }
        }
        sql.append(')');

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setString(1, uuid.toString());
            int idx = 2;
            for (Entry<String, String> en : entries) {
                ps.setString(idx++, en.getKey().toUpperCase());
                ps.setString(idx++, en.getValue());
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            debug.error("批量删除失败: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    /**
     * 获取数据库连接
     */
    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * 填充PreparedStatement并添加到批处理中
     */
    private void fillStatement(PreparedStatement ps, String uuidStr, String stat, String keyId, CacheManager.Entry entry) throws SQLException {
        ps.setString(1, uuidStr);
        ps.setString(2, stat);
        ps.setString(3, keyId);
        ps.setDouble(4, entry.getValue());
        ps.setInt(5, entry.isPercent() ? 1 : 0);
        
        // 将相对时间转换为绝对过期时间
        long expireTime = 0;
        if (entry.getExpireTicks() > 0) {
            expireTime = System.currentTimeMillis() + (entry.getExpireTicks() * 50); // 转换为毫秒
        }
        ps.setLong(6, expireTime);
        
        ps.addBatch();
    }

    /**
     * 批量删除指定UUID的所有记录
     */
    private void batchDeleteByUuid(Connection conn, Set<UUID> uuids) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(DELETE_SQL)) {
            for (UUID id : uuids) {
                ps.setString(1, id.toString());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /**
     * 批量替换所有持久化条目，跳过 memoryOnly 的属性
     */
    private void batchReplaceEntries(Connection conn, Map<UUID, Map<String, Map<String, CacheManager.Entry>>> all) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(REPLACE_SQL)) {
            for (Map.Entry<UUID, Map<String, Map<String, CacheManager.Entry>>> player : all.entrySet()) {
                String uuidStr = player.getKey().toString();
                for (Map.Entry<String, Map<String, CacheManager.Entry>> statEntry : player.getValue().entrySet()) {
                    String stat = statEntry.getKey();
                    for (Map.Entry<String, CacheManager.Entry> keyEntry : statEntry.getValue().entrySet()) {
                        CacheManager.Entry entry = keyEntry.getValue();
                        if (entry.isMemoryOnly()) {
                            // 仅内存周期属性不持久化
                            continue;
                        }
                        fillStatement(ps, uuidStr, stat, keyEntry.getKey(), entry);
                    }
                }
            }
            ps.executeBatch();
        }
    }
}
