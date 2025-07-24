package com.baimo.attributeBinder.task;

import com.baimo.attributeBinder.manager.AttributeBinderContext;
import com.baimo.attributeBinder.manager.CacheManager;
import com.baimo.attributeBinder.manager.StorageManager;
import org.bukkit.scheduler.BukkitRunnable;

public class FlushTask extends BukkitRunnable {
    @Override
    public void run() {
        StorageManager storage = AttributeBinderContext.getStorage();
        storage.saveAll(CacheManager.snapshot());
    }
} 