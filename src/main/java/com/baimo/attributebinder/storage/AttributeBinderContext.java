package com.baimo.attributebinder.storage;

/**
 * AttributeBinderContext — 处理数据存储管理的单一上下文
 */

public class AttributeBinderContext {
    private static StorageManager storage;

    public static void setStorage(StorageManager sm) {
        storage = sm;
    }

    public static StorageManager getStorage() {
        return storage;
    }
} 