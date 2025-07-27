package com.baimo.attributebinder.task;

/**
 * FlushTask — 将内存缓存全量写入库的安排任务
 */

import com.baimo.attributebinder.storage.AttributeBinderContext;
import com.baimo.attributebinder.cache.CacheManager;
import com.baimo.attributebinder.storage.StorageManager;
import org.bukkit.scheduler.BukkitRunnable;

public class FlushTask extends BukkitRunnable {
    @Override
    public void run() {
        StorageManager storage = AttributeBinderContext.getStorage();
        storage.saveAll(CacheManager.snapshot());
    }
} 