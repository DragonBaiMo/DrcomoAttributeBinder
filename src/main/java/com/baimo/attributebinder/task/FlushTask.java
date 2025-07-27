package com.baimo.attributebinder.task;

import com.baimo.attributebinder.manager.AttributeBinderContext;
import com.baimo.attributebinder.manager.CacheManager;
import com.baimo.attributebinder.manager.StorageManager;
import org.bukkit.scheduler.BukkitRunnable;

public class FlushTask extends BukkitRunnable {
    @Override
    public void run() {
        StorageManager storage = AttributeBinderContext.getStorage();
        storage.saveAll(CacheManager.snapshot());
    }
} 