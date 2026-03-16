package com.baimo.attributebinder.placeholder;

import org.bukkit.OfflinePlayer;

/**
 * PlaceholderContext — 存储解析得到的目标玩家及剩余参数
 */
class PlaceholderContext {
    final OfflinePlayer target;
    final String args;
    final boolean explicitTarget;

    PlaceholderContext(OfflinePlayer target, String args, boolean explicitTarget) {
        this.target = target;
        this.args = args;
        this.explicitTarget = explicitTarget;
    }
}
