# AttributeBinder `/ab remove` 性能优化方案（批量+异步）

---

## 1. 目标

1. 将数据库 `DELETE` 操作从主线程移出，避免 TPS 抖动。
2. 降低数据库往返次数，提升命令整体吞吐。
3. 与现有业务逻辑保持完全一致，可随时回退。

---

## 2. 总体方案

```
主线程
└─ removeAttributesForPlayer()
    ├─ 立即更新 CacheManager / AttributeApplier  (保持现行逻辑)
    ├─ 收集待删除 (stat,keyId) 列表
    └─ 入队异步任务 AsyncDelete(uuid, 待删列表)

异步线程池 (BukkitScheduler#runTaskAsynchronously)
└─ AsyncDelete.run()
    └─ JdbcStorageManager.deleteAttributesBatch(uuid, 列表)
        └─ 拼接单条批量 DELETE SQL
```

---

## 3. 详细实现

### 3.1 新增批量删除 API

文件：`JdbcStorageManager.java`

```java
public void deleteAttributesBatch(UUID uuid, List<Pair<String,String>> entries) {
    if (entries.isEmpty()) return;
    StringBuilder sb = new StringBuilder("DELETE FROM ")
        .append(tableName).append(" WHERE uuid = ? AND (");
    for (int i = 0; i < entries.size(); i++) {
        sb.append("(stat=? AND key_id=?)");
        if (i < entries.size() - 1) sb.append(" OR ");
    }
    sb.append(")");

    try (Connection conn = getConnection();
         PreparedStatement ps = conn.prepareStatement(sb.toString())) {
        ps.setString(1, uuid.toString());
        int idx = 2;
        for (Pair<String,String> p : entries) {
            ps.setString(idx++, p.getLeft().toUpperCase());
            ps.setString(idx++, p.getRight());
        }
        ps.executeUpdate();
    } catch (SQLException e) {
        debug.error("批量删除失败: " + e.getMessage());
    }
}
```

> MySQL 已启用 `rewriteBatchedStatements=true`；单条合并 OR 查询可进一步减少解析/网络开销。

### 3.2 新增异步任务包装类

文件：`AsyncTasks.java`（新建）

```java
public class AsyncTasks {
    private static final Executor asyncPool =
        Executors.newFixedThreadPool(ConfigManager.get().getAsyncThreads());

    public static void runAsync(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(JavaPlugin.getProvidingPlugin(AsyncTasks.class), task);
    }
}
```

### 3.3 修改 `RemoveCommand.removeAttributesForPlayer`

```java
// 1) 收集待删列表
List<Pair<String,String>> toDelete = new ArrayList<>();
...
toDelete.add(Pair.of(stat, keyId)); // 循环中积累
...

// 2) 主线程立即更新缓存后，异步删除
AsyncTasks.runAsync(() ->
    storage.deleteAttributesBatch(uuid, toDelete));
```

### 3.4 处理 “全服在线玩家” 场景

- 将所有在线玩家的待删任务 **分批(50UUID/批)** 提交到同一异步池。
- 每批构造 `DELETE ... WHERE uuid IN (..)` 形式以进一步合并。

### 3.5 配置项

`config.yml` 新增：

```yaml
async-delete:
  enable: true
  thread-count: 4   # >0 启用异步池
```

可热加载，便于问题排查时一键关闭。

---

## 4. 回退/降级

1. 如果 `async-delete.enable=false` 则回到原同步逻辑。
2. 捕获异步异常，写入日志但不打断主线程；下次保存周期仍会同步保存缓存到库中。

---

## 5. 测试计划

1. **单人删除**
   - 触发 `/ab remove <player> stat key`，验证即时性与 DB 中记录消失。
2. **高并发 20 玩家**
   - 压测命令，观测 TPS 与连接池占用。
3. **关闭开关回退**
   - 设置 `enable=false`，观察逻辑无差异。
4. **故障注入**
   - 暂停 MySQL 服务，确保异步异常被捕获且主线程无崩溃。

---

## 6. 预计收益

| 指标                    | 现状 (P95) | 目标 (P95) |
|-------------------------|------------|------------|
| `/ab remove` 执行时延   | 200–500 ms | ≤50 ms(主线程) |
| 主线程阻塞时长          | 33 %       | <5%        |
| TPS 降速峰值            | >20%       | <5%        |
| MySQL QPS               | baseline   | ↓ ~70%     |

---

## 7. 兼容性

- 只新增 API 与可选异步分支；`StorageManager` 旧实现不受影响。
- 对 SQLite 模式同样适用（连接池大小=1）。