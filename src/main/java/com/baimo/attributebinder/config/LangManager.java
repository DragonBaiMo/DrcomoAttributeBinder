package com.baimo.attributebinder.config;

import cn.drcomo.corelib.message.MessageService;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.Map;

/**
 * LangManager —— 基于 MessageService 的语言文件管理器。
 */
public class LangManager {
    private static LangManager instance;
    private final MessageService messages;

    private LangManager(MessageService service) {
        this.messages = service;
    }

    public static void init(MessageService service) {
        if (instance == null) {
            instance = new LangManager(service);
        }
    }

    public static LangManager get() {
        if (instance == null) {
            throw new IllegalStateException("LangManager 未初始化");
        }
        return instance;
    }

    public void reload() { messages.reloadLanguages(); }

    public String get(String key) {
        // 自动添加默认前缀变量并替换{prefix}
        String msg = messages.parse(key, null, Collections.singletonMap("prefix", getPrefix()));
        // 替换所有{变量}
        msg = msg.replace("{prefix}", getPrefix());
        return cn.drcomo.corelib.color.ColorUtil.translateColors(msg);
    }

    public String get(String key, Map<String, String> vars) {
        // 合并自定义变量和默认前缀，并替换{变量}
        Map<String, String> m = new java.util.HashMap<>(vars);
        m.put("prefix", getPrefix());
        String msg = messages.parse(key, null, m);
        // 替换所有{变量}
        for (Map.Entry<String, String> e : m.entrySet()) {
            msg = msg.replace("{" + e.getKey() + "}", e.getValue());
        }
        return cn.drcomo.corelib.color.ColorUtil.translateColors(msg);
    }

    public void send(CommandSender target, String key) {
        // 发送单条消息，替换{prefix}
        target.sendMessage(get(key));
    }

    public void send(CommandSender target, String key, Map<String, String> vars) {
        // 发送单条消息，替换所有{变量}
        target.sendMessage(get(key, vars));
    }

    /**
     * 获取前缀消息，用于内部自动注入变量
     */
    private String getPrefix() {
        return messages.parse("prefix", null, Collections.emptyMap());
    }
}
