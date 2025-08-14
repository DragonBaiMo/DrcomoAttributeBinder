package com.baimo.attributebinder.service;

import com.baimo.attributebinder.AttributeBinder;
import io.lumine.mythic.lib.api.player.EquipmentSlot;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.api.stat.modifier.StatModifier;
import io.lumine.mythic.lib.player.modifier.ModifierSource;
import io.lumine.mythic.lib.player.modifier.ModifierType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.Predicate;

public class AttributeApplier {

    // 插件修饰符前缀
    private static final String MOD_PREFIX = "AttributeBinder_";

    /**
     * 根据 stat 和 keyId 生成唯一修饰符 ID
     */
    private static String modifierId(String stat, String keyId) {
        return MOD_PREFIX + keyId + "_" + stat.toUpperCase();
    }

    /**
     * 应用单条修饰符。
     * @param percent 当为 true 时，value 必须以“百分数”传入：如 12% 传 12.0
     */
    public static void apply(UUID uuid, String stat, String keyId, double value, boolean percent) {
        Runnable task = () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) return;

            MMOPlayerData data = MMOPlayerData.get(uuid);
            String statUpper = stat.toUpperCase();

            // 安全：先清理本插件在该 stat + keyId 下的同类型遗留，避免混合乘区
            cleanupSameChannel(data, statUpper, keyId, percent);

            StatModifier modifier = new StatModifier(
                    modifierId(statUpper, keyId),
                    statUpper,
                    value,
                    percent ? ModifierType.ADDITIVE_MULTIPLIER : ModifierType.FLAT,
                    EquipmentSlot.OTHER,
                    ModifierSource.OTHER
            );
            data.getStatMap().getInstance(statUpper).registerModifier(modifier);
        };
        runOnMain(task);
    }

    /** 清理同一 stat+keyId 下本插件产生的同通道修饰符，防止遗留叠加 */
    private static void cleanupSameChannel(MMOPlayerData data, String statUpper, String keyId, boolean percent) {
        String id = modifierId(statUpper, keyId);
        // 删除相同 ID 的旧修饰符
        data.getStatMap().getInstance(statUpper).removeIf(id::equals);

        // 注意：之前的代码会清理所有与当前stat相关的修饰符，导致不同keyId的属性被错误地清理
        // 修改为只清理当前keyId的修饰符
        String prefix = MOD_PREFIX + keyId + "_";
        data.getStatMap().getInstance(statUpper).removeIf(key -> 
                key.startsWith(prefix) && key.contains("_" + statUpper) && !key.equals(id));
    }

    /**
     * 私有方法：根据给定的 key 过滤条件，删除所有匹配的修饰符，并统一更新属性
     * @param data      玩家数据对象
     * @param keyFilter 判断是否删除的 key 过滤器
     */
    private static void removeModifiers(MMOPlayerData data, Predicate<String> keyFilter) {
        data.getStatMap().getInstances().forEach(instance -> {
            instance.removeIf(keyFilter);
        });
        data.getStatMap().updateAll();
    }

    /**
     * 移除指定 stat + keyId 下的修饰符
     */
    public static void remove(UUID uuid, String stat, String keyId) {
        runOnMain(() -> {
            MMOPlayerData data = MMOPlayerData.get(uuid);
            String statUpper = stat.toUpperCase();
            String id = modifierId(statUpper, keyId);
            data.getStatMap().getInstance(statUpper).removeIf(id::equals);
            data.getStatMap().updateAll();
        });
    }

    /**
     * 移除所有本插件添加的修饰符
     */
    public static void removeAll(UUID uuid) {
        runOnMain(() -> {
            MMOPlayerData data = MMOPlayerData.get(uuid);
            removeModifiers(data, key -> key.startsWith(MOD_PREFIX));
        });
    }

    /**
     * 移除指定 keyId 对应的所有修饰符
     */
    public static void removeKey(UUID uuid, String keyId) {
        runOnMain(() -> {
            MMOPlayerData data = MMOPlayerData.get(uuid);
            String prefix = MOD_PREFIX + keyId + "_";
            removeModifiers(data, key -> key.startsWith(prefix));
        });
    }

    /**
     * 移除指定 stat 对应的所有修饰符
     */
    public static void removeStat(UUID uuid, String stat) {
        runOnMain(() -> {
            MMOPlayerData data = MMOPlayerData.get(uuid);
            String statUpper = stat.toUpperCase();
            removeModifiers(data, key -> key.startsWith(MOD_PREFIX) && key.endsWith("_" + statUpper));
        });
    }

    private static void runOnMain(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(AttributeBinder.getInstance(), task);
        }
    }
}
