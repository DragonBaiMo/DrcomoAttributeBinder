package com.baimo.attributeBinder.task;

import com.baimo.attributeBinder.manager.CacheManager;
import com.baimo.attributeBinder.manager.StorageManager;
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
        CacheManager.cleanupExpired(storage);
    }
}
