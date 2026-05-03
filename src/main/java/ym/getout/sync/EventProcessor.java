package ym.getout.sync;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ym.getout.config.Settings;
import ym.getout.model.SyncEvent;
import ym.getout.scheduler.SchedulerAdapter;
import ym.getout.storage.BanStore;
import ym.getout.storage.EventStore;
import ym.getout.storage.SyncStateStore;
import ym.getout.util.LoggerUtil;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class EventProcessor {

    private final EventStore eventRepository;
    private final BanStore banRepository;
    private final SyncStateStore syncStateRepository;
    private final Settings settings;
    private final SchedulerAdapter scheduler;
    private final AtomicLong lastProcessedId = new AtomicLong(0);

    public EventProcessor(EventStore eventRepository, BanStore banRepository,
                          SyncStateStore syncStateRepository, Settings settings, SchedulerAdapter scheduler) {
        this.eventRepository = eventRepository;
        this.banRepository = banRepository;
        this.syncStateRepository = syncStateRepository;
        this.settings = settings;
        this.scheduler = scheduler;
        this.lastProcessedId.set(syncStateRepository.getLastProcessedEventId(settings.getServerId()));
    }

    /**
     * 处理新的同步事件。
     * Never call this method from the server main thread.
     */
    public void processNewEvents() {
        boolean includeOwn = settings.isSyncProcessOwnEvents();
        String serverId = settings.getServerId();

        List<SyncEvent> events = eventRepository.findEventsAfter(
                lastProcessedId.get(), includeOwn, serverId);

        for (SyncEvent event : events) {
            try {
                processEvent(event);
                lastProcessedId.set(event.getId());
                syncStateRepository.saveLastProcessedEventId(serverId, event.getId());
            } catch (Exception e) {
                LoggerUtil.error("Failed to process sync event #" + event.getId() + " (" + event.getEventType() + ")", e);
            }
        }
    }

    private void processEvent(SyncEvent event) {
        switch (event.getEventType()) {
            case "BAN" -> handleBanEvent(event);
            case "TEMPBAN" -> handleTempBanEvent(event);
            case "KICK" -> handleKickEvent(event);
            default -> LoggerUtil.debug("Unknown event type: " + event.getEventType());
        }
    }

    private void handleBanEvent(SyncEvent event) {
        if (!settings.isSyncKickOnlineAfterBan()) return;
        kickOnlinePlayer(event.getTargetUuid(), event.getTargetName(), event.getReason(), event.getOperatorName());
    }

    private void handleTempBanEvent(SyncEvent event) {
        if (!settings.isSyncKickOnlineAfterBan()) return;
        kickOnlinePlayer(event.getTargetUuid(), event.getTargetName(), event.getReason(), event.getOperatorName());
    }

    private void handleKickEvent(SyncEvent event) {
        kickOnlinePlayer(event.getTargetUuid(), event.getTargetName(), event.getReason(), event.getOperatorName());
    }

    private void kickOnlinePlayer(UUID uuid, String name, String reason, String operator) {
        scheduler.runGlobal(() -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                String kickMsg = "你已被封禁/踢出\n原因: " + (reason != null ? reason : "无");
                player.kickPlayer(kickMsg);
                LoggerUtil.info("Kicked synced player: " + name);
            }
        });
    }

    public void setLastProcessedId(long id) {
        lastProcessedId.set(id);
    }
}
