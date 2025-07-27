package com.baimo.attributebinder.placeholder;

import org.bukkit.OfflinePlayer;

/**
 * PlaceholderContext — 存储解析得到的目标玩家及剩余参数
 */
class PlaceholderContext {
    final OfflinePlayer target;
    final String args;

    PlaceholderContext(OfflinePlayer target, String args) {
        this.target = target;
        this.args = args;
    }
}
