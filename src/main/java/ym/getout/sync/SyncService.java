package ym.getout.sync;

import ym.getout.config.Settings;
import ym.getout.database.DatabaseManager;
import ym.getout.scheduler.SchedulerAdapter;
import ym.getout.storage.BanStore;
import ym.getout.storage.EventStore;
import ym.getout.util.LoggerUtil;

/**
 * 同步服务，负责定期清理过期封禁和过期事件。
 */
public class SyncService {

    private final DatabaseManager db;
    private final BanStore banRepository;
    private final EventStore eventRepository;
    private final Settings settings;
    private final SchedulerAdapter scheduler;
    private final EventProcessor eventProcessor;

    public SyncService(DatabaseManager db, BanStore banRepository, EventStore eventRepository,
                       Settings settings, SchedulerAdapter scheduler, EventProcessor eventProcessor) {
        this.db = db;
        this.banRepository = banRepository;
        this.eventRepository = eventRepository;
        this.settings = settings;
        this.scheduler = scheduler;
        this.eventProcessor = eventProcessor;
    }

    /**
     * 启动同步定时任务。
     */
    public void start() {
        long interval = settings.getSyncPollIntervalTicks();
        if (interval < 1) interval = 20;

        // 定时轮询同步事件
        scheduler.runTimerAsync(this::poll, interval, interval);

        // 定时清理过期封禁和事件（每 5 分钟）
        scheduler.runTimerAsync(this::cleanup, 6000L, 6000L);

        LoggerUtil.info("Sync service started (poll interval: " + interval + " ticks)");
    }

    private void poll() {
        if (settings.isDatabaseEnabled() && (db == null || !db.isInitialized())) return;
        try {
            eventProcessor.processNewEvents();
        } catch (Exception e) {
            LoggerUtil.error("Error during sync event polling", e);
        }
    }

    private void cleanup() {
        if (settings.isDatabaseEnabled() && (db == null || !db.isInitialized())) return;
        try {
            int deactivated = banRepository.deactivateExpiredBans();
            if (deactivated > 0) {
                LoggerUtil.debug("Deactivated " + deactivated + " expired bans");
            }

            int cleaned = eventRepository.cleanExpiredEvents(settings.getSyncEventRetentionDays());
            if (cleaned > 0) {
                LoggerUtil.debug("Cleaned " + cleaned + " expired sync events");
            }
        } catch (Exception e) {
            LoggerUtil.error("Error during sync cleanup", e);
        }
    }
}
