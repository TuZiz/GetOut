package ym.getout.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

/**
 * Spigot/Paper 标准调度器实现。
 */
public class BukkitSchedulerAdapter implements SchedulerAdapter {

    private final Plugin plugin;
    private final List<BukkitTask> tasks = new ArrayList<>();

    public BukkitSchedulerAdapter(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runAsync(Runnable task) {
        BukkitTask t = Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        tasks.add(t);
    }

    @Override
    public void runGlobal(Runnable task) {
        BukkitTask t = Bukkit.getScheduler().runTask(plugin, task);
        tasks.add(t);
    }

    @Override
    public void runForPlayer(Player player, Runnable task) {
        BukkitTask t = Bukkit.getScheduler().runTask(plugin, task);
        tasks.add(t);
    }

    @Override
    public void runTimerAsync(Runnable task, long delayTicks, long periodTicks) {
        BukkitTask t = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks);
        tasks.add(t);
    }

    @Override
    public void shutdown() {
        for (BukkitTask task : tasks) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
        tasks.clear();
        Bukkit.getScheduler().cancelTasks(plugin);
    }
}
