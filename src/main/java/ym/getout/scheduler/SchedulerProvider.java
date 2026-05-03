package ym.getout.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * 调度器工厂，自动检测 Folia 环境并返回对应的调度器实现。
 */
public final class SchedulerProvider {

    private SchedulerProvider() {}

    public static SchedulerAdapter create(Plugin plugin) {
        if (isFolia()) {
            try {
                return new FoliaSchedulerAdapter(plugin);
            } catch (Exception e) {
                // Folia detection failed, fallback to Bukkit
            }
        }
        return new BukkitSchedulerAdapter(plugin);
    }

    private static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
