package com.baimo.attributeBinder.listener;

import com.baimo.attributeBinder.manager.*;
import com.baimo.attributeBinder.AttributeBinder;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.Bukkit;

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
        
        // 延迟20ticks加载数据，确保跨服同步
        Bukkit.getScheduler().runTaskLater(AttributeBinder.getInstance(), () -> {
            StorageManager storage = AttributeBinderContext.getStorage();
            // 加载并缓存
            Map<String, Map<String, CacheManager.Entry>> attrs = storage.loadAttributes(uuid);
            attrs.forEach((stat, keyMap) -> keyMap.forEach((keyId, entry) -> {
                CacheManager.setAttribute(uuid, stat, keyId, entry.getValue(), entry.isPercent());
                AttributeApplier.apply(uuid, stat, keyId, entry.getValue(), entry.isPercent());
            }));
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        StorageManager storage = AttributeBinderContext.getStorage();
        // 先复制玩家属性数据，避免异步线程执行时已被清空
        java.util.Map<String, java.util.Map<String, CacheManager.Entry>> dataCopy =
                com.baimo.attributeBinder.manager.CacheManager.snapshot()
                        .getOrDefault(uuid, java.util.Collections.emptyMap());
        // 保存数据（同步，确保在关服时写入），仅持久化周期属性
        Map<String, Map<String, CacheManager.Entry>> filtered = new HashMap<>();
        dataCopy.forEach((stat, keyMap) -> {
            Map<String, CacheManager.Entry> filteredKeyMap = keyMap.entrySet().stream()
                    .filter(e -> !e.getValue().isMemoryOnly())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            if (!filteredKeyMap.isEmpty()) {
                filtered.put(stat, filteredKeyMap);
            }
        });
        storage.saveAttributes(uuid, filtered);
        CacheManager.clear(uuid);
        AttributeApplier.removeAll(uuid);
    }
} 