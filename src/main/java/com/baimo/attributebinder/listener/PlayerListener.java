package com.baimo.attributebinder.listener;

/**
 * PlayerListener — 监听玩家上下线事件并处理属性加成与缓存保存
 */

import com.baimo.attributebinder.AttributeBinder;
import com.baimo.attributebinder.cache.CacheManager;
import com.baimo.attributebinder.service.AttributeApplier;
import com.baimo.attributebinder.storage.AttributeBinderContext;
import com.baimo.attributebinder.storage.StorageManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.Bukkit;

import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.stream.Collectors;

public class PlayerListener implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        
        // 先清空该玩家的缓存，防止有旧数据
        CacheManager.clear(uuid);
        AttributeApplier.removeAll(uuid);
        
        // 延迟一秒异步加载数据，完成后在主线程应用
        AttributeBinder.getAsyncTaskManager().scheduleAsync(() -> {
            StorageManager storage = AttributeBinderContext.getStorage();
            Map<String, Map<String, CacheManager.Entry>> attrs = storage.loadAttributes(uuid);
				Bukkit.getScheduler().runTask(AttributeBinder.getInstance(), () ->
					attrs.forEach((stat, keyMap) -> keyMap.forEach((keyId, entry) -> {
						CacheManager.setAttribute(uuid, stat, keyId, entry.getValue(), entry.isPercent(), false, entry.getExpireTicks());
						AttributeApplier.apply(uuid, stat, keyId, entry.getValue(), entry.isPercent());
					})));
        }, 1, TimeUnit.SECONDS);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        StorageManager storage = AttributeBinderContext.getStorage();
        // 复制玩家属性数据，避免异步线程执行时数据被清空
        Map<String, Map<String, CacheManager.Entry>> dataCopy = CacheManager.snapshot(uuid);

        Map<String, Map<String, CacheManager.Entry>> filtered = new HashMap<>();
        dataCopy.forEach((stat, keyMap) -> {
            Map<String, CacheManager.Entry> filteredKeyMap = keyMap.entrySet().stream()
                    .filter(e -> !e.getValue().isMemoryOnly())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            if (!filteredKeyMap.isEmpty()) {
                filtered.put(stat, filteredKeyMap);
            }
        });

        // 异步保存，减少主线程阻塞
        AttributeBinder.getAsyncTaskManager().submitAsync(() -> storage.saveAttributes(uuid, filtered));
        CacheManager.clear(uuid);
        AttributeApplier.removeAll(uuid);
    }
}