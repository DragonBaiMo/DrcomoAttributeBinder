package com.baimo.attributebinder.task;

import com.baimo.attributebinder.cache.CacheManager;
import com.baimo.attributebinder.storage.StorageManager;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 定时清理过期属性任务，每个服务器 tick 调用一次
 */
public class ExpireTask extends BukkitRunnable {
    private final StorageManager storage;

    public ExpireTask(StorageManager storage) {
        this.storage = storage;
    }

    @Override
    public void run() {
        // 异步线程中仅做内存与数据库删除，不触发 Bukkit API；AttributeApplier.remove 仅操作 MythicLib 数据结构，保持线程安全
        CacheManager.cleanupExpired(storage);
    }
}
