package com.baimo.attributebinder.storage;

/**
 * StorageManager — 存储层程序接口，定义对玩家属性的指定操作
 */

import java.util.Map;
import java.util.UUID;

import com.baimo.attributebinder.cache.CacheManager;

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

    /**
     * 删除指定玩家的单个属性
     * @param uuid 玩家UUID
     * @param stat 属性类型
     * @param keyId 属性键，如果为null则删除该stat的所有keyId
     */
    void deleteAttribute(UUID uuid, String stat, String keyId);

    /**
     * 删除指定玩家在指定keyId下的所有属性
     * @param uuid 玩家UUID
     * @param keyId 属性键
     */
    void deleteAttributesByKey(UUID uuid, String keyId);

    /**
     * 删除指定玩家的所有属性
     * @param uuid 玩家UUID
     */
    void deleteAllAttributes(UUID uuid);

    void close();
}