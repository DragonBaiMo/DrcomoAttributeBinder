package com.baimo.attributeBinder.manager;

import java.util.Map;
import java.util.UUID;

public interface StorageManager {

    /**
     * 加载单个玩家属性：返回 stat→keyId→Entry
     */
    Map<String, Map<String, CacheManager.Entry>> loadAttributes(UUID uuid);

    /**
     * 持久化单个玩家属性
     */
    void saveAttributes(UUID uuid, Map<String, Map<String, CacheManager.Entry>> attributes);

    /**
     * 批量保存所有玩家属性
     */
    void saveAll(Map<UUID, Map<String, Map<String, CacheManager.Entry>>> all);

    void close();
} 