package com.baimo.attributebinder.listener;

import com.baimo.attributebinder.DrcomoAttributeBinder;
import net.Indyuce.mmoitems.api.event.MMOItemsReloadEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * 监听 MMOItems 热重载，并在其重建内部统计链路后重新应用 ABB 属性修饰符。
 */
public final class MMOItemsReloadListener implements Listener {

    @EventHandler
    public void onMMOItemsReload(MMOItemsReloadEvent event) {
        DrcomoAttributeBinder.getInstance().scheduleReloadRecovery();
    }
}
