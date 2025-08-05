package com.baimo.attributeBinder.task;

import com.baimo.attributeBinder.manager.ConfigManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 异步任务工具，用于在独立线程池中执行耗时操作。
 */
public final class AsyncTasks {

    private static final ExecutorService POOL;

    static {
        if (ConfigManager.get().isAsyncDeleteEnabled() && ConfigManager.get().getAsyncThreads() > 0) {
            POOL = Executors.newFixedThreadPool(ConfigManager.get().getAsyncThreads());
        } else {
            POOL = null;
        }
    }

    private AsyncTasks() {
    }

    /**
     * 提交任务到异步线程池执行；若未启用则在当前线程运行。
     *
     * @param task 待执行任务
     */
    public static void runAsync(Runnable task) {
        if (POOL != null) {
            POOL.execute(task);
        } else {
            task.run();
        }
    }

    /**
     * 关闭线程池，插件停用时调用。
     */
    public static void shutdown() {
        if (POOL != null) {
            POOL.shutdown();
        }
    }
}
