package com.baimo.attributebinder.util;

import cn.drcomo.corelib.util.DebugUtil;
import com.baimo.attributebinder.AttributeBinder;
import com.baimo.attributebinder.manager.ConfigManager;

import java.util.logging.Logger;

/**
 * 日志提供工具：优先返回 DebugUtil 实例，若不可用则使用 Java Logger。
 */
public final class LoggerProvider {
    private LoggerProvider() {}

    /** 获取 DebugUtil，如失败则返回 null */
    public static DebugUtil getDebugLogger() {
        try {
            return new DebugUtil(AttributeBinder.getInstance(), ConfigManager.get().getLogLevel());
        } catch (Throwable t) {
            return null;
        }
    }

    /** 获取 Java Logger 作为回退 */
    public static Logger getFallback(Class<?> clazz) {
        return Logger.getLogger(clazz.getName());
    }
}
