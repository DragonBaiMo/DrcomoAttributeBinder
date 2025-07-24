package com.baimo.attributeBinder.manager;

public class AttributeBinderContext {
    private static StorageManager storage;

    public static void setStorage(StorageManager sm) {
        storage = sm;
    }

    public static StorageManager getStorage() {
        return storage;
    }
} 