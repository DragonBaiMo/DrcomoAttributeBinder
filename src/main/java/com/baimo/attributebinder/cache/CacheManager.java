package com.baimo.attributebinder.cache;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.baimo.attributebinder.storage.StorageManager;

/**
 * CacheManager —— 统一管理玩家属性缓存（内存）。
 * 
 * 数据结构：
 *   playerUuid → stat → keyId → Entry(value, percent)
 */
public class CacheManager {

    /** 单条属性记录 */
    public static class Entry {
        private double value;
        private boolean percent;
        /** 是否仅内存周期（不跨服） */
        private boolean memoryOnly;
		/** 过期时长（ticks）。-1=永久，0=应移除，>0=剩余ticks */
		private long expireTicks;

        public Entry(double value, boolean percent) {
            this(value, percent, false);
        }
        /**
         * @param memoryOnly 是否仅内存周期，不持久化
         */
        public Entry(double value, boolean percent, boolean memoryOnly) {
        this.value = value;
        this.percent = percent;
        this.memoryOnly = memoryOnly;
        this.expireTicks = 0L;
        }
        public double getValue() { return value; }
        public void setValue(double v) { this.value = v; }
        public boolean isPercent() { return percent; }
        public void setPercent(boolean p) { this.percent = p; }
        /** 是否仅内存周期 */
        public boolean isMemoryOnly() { return memoryOnly; }
        public long getExpireTicks() { return expireTicks; }
        public void setExpireTicks(long expireTicks) { this.expireTicks = expireTicks; }
        public void setMemoryOnly(boolean memoryOnly) { this.memoryOnly = memoryOnly; }
    }

    /** 默认 keyId，用于兼容旧命令 */
    public static final String DEFAULT_KEY = "DEFAULT";

    // 主缓存：UUID -> stat -> keyId -> Entry
    private static final Map<UUID, ConcurrentHashMap<String, ConcurrentHashMap<String, Entry>>> CACHE = new ConcurrentHashMap<>();
    /**
     * 过期清理调用间隔（ticks），每次减少对应ticks数
     */
    public static final int CLEANUP_INTERVAL_TICKS = 5;

    /* ---------------- 私有工具方法 ---------------- */
    private static ConcurrentHashMap<String, ConcurrentHashMap<String, Entry>> getStatMap(UUID uuid) {
        return CACHE.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
    }
    private static ConcurrentHashMap<String, Entry> getKeyMap(UUID uuid, String stat) {
        return getStatMap(uuid).computeIfAbsent(stat.toUpperCase(), k -> new ConcurrentHashMap<>());
    }

    /* ---------------- 公开操作 ---------------- */

    /**
     * 获取指定 stat 全部 keyId 的总和（flat+percent 均直接数值相加）。
     */
    public static double getAttribute(UUID uuid, String stat) {
        return getKeyMap(uuid, stat).values().stream().mapToDouble(Entry::getValue).sum();
    }

    /**
     * 获取指定 keyId 的属性值，不存在返回 0。
     */
    public static double getAttribute(UUID uuid, String stat, String keyId) {
        return getKeyMap(uuid, stat).getOrDefault(keyId, new Entry(0, false)).getValue();
    }

    /**
     * 设置指定 keyId 的属性值及百分比标记（默认跨服持久化）。
     */
    public static void setAttribute(UUID uuid, String stat, String keyId, double value, boolean percent) {
        setAttribute(uuid, stat, keyId, value, percent, false);
    }
    /**
     * 设置指定 keyId 的属性值、百分比标记及周期类型
     * @param memoryOnly true=仅内存周期(不跨服), false=跨服持久化
     */
    public static void setAttribute(UUID uuid, String stat, String keyId, double value, boolean percent, boolean memoryOnly) {
        getKeyMap(uuid, stat).put(keyId, new Entry(value, percent, memoryOnly));
    }
    /**
     * 设置指定 keyId 的属性值、百分比、周期类型及过期时长
     * @param memoryOnly true=仅内存周期(不跨服), false=跨服持久化
     * @param expireTicks 过期时长（ticks）
     */
    public static void setAttribute(UUID uuid, String stat, String keyId, double value, boolean percent, boolean memoryOnly, long expireTicks) {
        Entry entry = new Entry(value, percent, memoryOnly);
        entry.setExpireTicks(expireTicks);
        getKeyMap(uuid, stat).put(keyId, entry);
    }

    /**
     * 移除指定 keyId 的属性；若 keyId 为 null 则移除 stat 的全部 key。
     */
    public static void removeAttribute(UUID uuid, String stat, String keyId) {
        if (keyId == null) {
            getStatMap(uuid).remove(stat.toUpperCase());
        } else {
            getKeyMap(uuid, stat).remove(keyId);
        }
    }

    /**
     * 兼容旧接口：移除整个 stat
     */
    public static void removeAttribute(UUID uuid, String stat) {
        removeAttribute(uuid, stat, null);
    }

    /**
     * 仅用于命令 list 展示，返回〈stat, 总和〉的快照。
     */
    public static Map<String, Double> aggregatedAttributes(UUID uuid) {
        Map<String, Double> map = new LinkedHashMap<>();
        getStatMap(uuid).forEach((stat, keyMap) -> {
            double sum = keyMap.values().stream().mapToDouble(Entry::getValue).sum();
            map.put(stat, sum);
        });
        return map;
    }

    /**
     * 判断某 stat 在任何 keyId 下是否存在 percent=true。
     * （用于原有占位符兼容）
     */
    public static boolean isPercent(UUID uuid, String stat) {
        return getKeyMap(uuid, stat).values().stream().anyMatch(Entry::isPercent);
    }

    /** 清除玩家缓存 */
    public static void clear(UUID uuid) {
        CACHE.remove(uuid);
    }

    /**
     * 返回深度不可修改快照，供批量持久化。
     * 在嵌套层面上返回新 Map，但内部 Entry 没有做深拷贝（可安全只读）。
     */
    public static Map<UUID, Map<String, Map<String, Entry>>> snapshot() {
        Map<UUID, Map<String, Map<String, Entry>>> copy = new HashMap<>();
        CACHE.forEach((u, statMap) -> {
            Map<String, Map<String, Entry>> statCopy = new HashMap<>();
            statMap.forEach((s, keyMap) -> statCopy.put(s, new HashMap<>(keyMap)));
            copy.put(u, statCopy);
        });
        return Collections.unmodifiableMap(copy);
    }

    /**
     * 返回指定玩家的属性快照。
     *
     * <p>只复制嵌套映射，不深拷贝 {@link Entry}，确保只读安全。</p>
     *
     * @param uuid 目标玩家 UUID
     * @return stat → keyId → Entry 的不可修改视图
     */
    public static Map<String, Map<String, Entry>> snapshot(UUID uuid) {
        ConcurrentHashMap<String, ConcurrentHashMap<String, Entry>> statMap = CACHE.get(uuid);
        if (statMap == null) {
            return Collections.emptyMap();
        }
        Map<String, Map<String, Entry>> copy = new HashMap<>();
        statMap.forEach((stat, keyMap) -> copy.put(stat, new HashMap<>(keyMap)));
        return Collections.unmodifiableMap(copy);
    }
    /**
     * 获取指定 keyId 的剩余过期时长（ticks），不存在或未设置返回 0。
     */
    public static long getExpireTicks(UUID uuid, String stat, String keyId) {
        Entry e = getKeyMap(uuid, stat).get(keyId);
        return (e != null) ? e.getExpireTicks() : 0L;
    }
    /**
     * 清理过期属性（expireTicks 持续递减，<=0 时移除）
     * @param storage 存储管理器，用于同步删除数据库记录
     */
    public static void cleanupExpired(StorageManager storage) {
        // 每次调用递减 CLEANUP_INTERVAL_TICKS
        Iterator<Map.Entry<UUID, ConcurrentHashMap<String, ConcurrentHashMap<String, Entry>>>> playerIt = CACHE.entrySet().iterator();
        while (playerIt.hasNext()) {
            Map.Entry<UUID, ConcurrentHashMap<String, ConcurrentHashMap<String, Entry>>> player = playerIt.next();
            UUID uuid = player.getKey();
            ConcurrentHashMap<String, ConcurrentHashMap<String, Entry>> statMap = player.getValue();

            Iterator<Map.Entry<String, ConcurrentHashMap<String, Entry>>> statIt = statMap.entrySet().iterator();
            while (statIt.hasNext()) {
                Map.Entry<String, ConcurrentHashMap<String, Entry>> statEntry = statIt.next();
                String stat = statEntry.getKey();
                ConcurrentHashMap<String, Entry> keyMap = statEntry.getValue();

                Iterator<Map.Entry<String, Entry>> keyIt = keyMap.entrySet().iterator();
                while (keyIt.hasNext()) {
                    Map.Entry<String, Entry> keyEntry = keyIt.next();
                    String keyId = keyEntry.getKey();
                    Entry entry = keyEntry.getValue();
			if (entry.getExpireTicks() != 0) {
				long current = entry.getExpireTicks();
				if (current < 0) {
					// 永久
					continue;
				}
				long ticks = current - CLEANUP_INTERVAL_TICKS;
				if (ticks <= 0) {
                            keyIt.remove();
                            com.baimo.attributebinder.service.AttributeApplier.remove(uuid, stat, keyId);
                            if (!entry.isMemoryOnly() && storage != null) {
                                storage.deleteAttribute(uuid, stat, keyId);
                            }
                        } else {
                            entry.setExpireTicks(ticks);
                        }
                    }
                }

                if (keyMap.isEmpty()) {
                    statIt.remove();
                }
            }

            if (statMap.isEmpty()) {
                playerIt.remove();
            }
        }
    }

    /**
     * 清理过期属性（兼容旧接口，不同步数据库）
     * @deprecated 建议使用 cleanupExpired(StorageManager) 以确保数据库一致性
     */
    @Deprecated
    public static void cleanupExpired() {
        cleanupExpired(null);
    }

    /**
     * (兼容旧代码) 返回〈stat, 总和〉Map。
     */
    public static Map<String, Double> getAttributes(UUID uuid) {
        return aggregatedAttributes(uuid);
    }
}