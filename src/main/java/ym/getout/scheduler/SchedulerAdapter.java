package ym.getout.scheduler;

import org.bukkit.entity.Player;

/**
 * 调度器适配层接口，兼容 Spigot / Paper / Folia。
 */
public interface SchedulerAdapter {

    /**
     * 异步执行任务。
     */
    void runAsync(Runnable task);

    /**
     * 在全局区域调度器执行（Folia 兼容）。
     * Spigot/Paper 下退化为同步执行。
     */
    void runGlobal(Runnable task);

    /**
     * 为指定玩家执行任务（Folia 兼容）。
     * Spigot/Paper 下退化为同步执行。
     */
    void runForPlayer(Player player, Runnable task);

    /**
     * 异步定时任务。
     */
    void runTimerAsync(Runnable task, long delayTicks, long periodTicks);

    /**
     * 关闭调度器，取消所有任务。
     */
    void shutdown();
}
