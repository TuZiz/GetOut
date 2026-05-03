package ym.getout.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import ym.getout.util.LoggerUtil;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

public class FoliaSchedulerAdapter implements SchedulerAdapter {

    private final Plugin plugin;
    private final Object asyncScheduler;
    private final Object globalRegionScheduler;
    private final boolean foliaAvailable;

    public FoliaSchedulerAdapter(Plugin plugin) {
        this.plugin = plugin;

        Object async = null;
        Object global = null;
        boolean available = false;

        try {
            Method getAsyncScheduler = Bukkit.class.getMethod("getAsyncScheduler");
            async = getAsyncScheduler.invoke(null);
            available = async != null;
        } catch (Exception e) {
            LoggerUtil.debug("Folia AsyncScheduler not available, falling back to Bukkit scheduler");
        }

        try {
            Method getGlobalRegionScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler");
            global = getGlobalRegionScheduler.invoke(null);
        } catch (Exception e) {
            LoggerUtil.debug("Folia GlobalRegionScheduler not available, falling back to Bukkit scheduler");
        }

        this.asyncScheduler = async;
        this.globalRegionScheduler = global;
        this.foliaAvailable = available;
    }

    @Override
    public void runAsync(Runnable task) {
        if (foliaAvailable && asyncScheduler != null) {
            try {
                Method runNow = asyncScheduler.getClass().getMethod("runNow", Plugin.class, java.util.function.Consumer.class);
                runNow.invoke(asyncScheduler, plugin, (java.util.function.Consumer<?>) ignored -> task.run());
                return;
            } catch (Exception e) {
                LoggerUtil.debug("Folia runAsync fallback: " + e.getMessage());
            }
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    @Override
    public void runGlobal(Runnable task) {
        if (globalRegionScheduler != null) {
            try {
                Method runMethod = globalRegionScheduler.getClass().getMethod("run", Plugin.class, java.util.function.Consumer.class);
                runMethod.invoke(globalRegionScheduler, plugin, (java.util.function.Consumer<?>) ignored -> task.run());
                return;
            } catch (Exception e) {
                LoggerUtil.debug("Folia runGlobal fallback: " + e.getMessage());
            }
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    @Override
    public void runForPlayer(Player player, Runnable task) {
        try {
            Method getScheduler = player.getClass().getMethod("getScheduler");
            Object entityScheduler = getScheduler.invoke(player);
            Method runMethod = entityScheduler.getClass().getMethod("run", Plugin.class, java.util.function.Consumer.class, Runnable.class);
            runMethod.invoke(entityScheduler, plugin, (java.util.function.Consumer<?>) ignored -> task.run(), null);
            return;
        } catch (Exception e) {
            LoggerUtil.debug("Folia runForPlayer fallback: " + e.getMessage());
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    @Override
    public void runTimerAsync(Runnable task, long delayTicks, long periodTicks) {
        if (foliaAvailable && asyncScheduler != null) {
            try {
                Method runAtFixedRate = asyncScheduler.getClass().getMethod(
                        "runAtFixedRate", Plugin.class, java.util.function.Consumer.class, long.class, long.class, TimeUnit.class);
                long delayMs = delayTicks * 50L;
                long periodMs = periodTicks * 50L;
                runAtFixedRate.invoke(asyncScheduler, plugin, (java.util.function.Consumer<?>) ignored -> task.run(),
                        delayMs, periodMs, TimeUnit.MILLISECONDS);
                return;
            } catch (Exception e) {
                LoggerUtil.debug("Folia runTimerAsync fallback: " + e.getMessage());
            }
        }
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks);
    }

    @Override
    public void shutdown() {
        Bukkit.getScheduler().cancelTasks(plugin);
    }
}
