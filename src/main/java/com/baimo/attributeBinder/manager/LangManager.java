package com.baimo.attributeBinder.manager;

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
        return messages.parse(key, null, Collections.emptyMap());
    }

    public String get(String key, Map<String, String> vars) {
        return messages.parse(key, null, vars);
    }

    public void send(CommandSender target, String key) {
        messages.send(target, key, Collections.emptyMap());
    }

    public void send(CommandSender target, String key, Map<String, String> vars) {
        messages.send(target, key, vars);
    }
}
