package ym.getout.sync;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ym.getout.config.Settings;
import ym.getout.lang.MessageService;
import ym.getout.notify.AdminNotifier;
import ym.getout.model.SyncEvent;
import ym.getout.scheduler.SchedulerAdapter;
import ym.getout.storage.BanStore;
import ym.getout.storage.EventStore;
import ym.getout.storage.IpBanStore;
import ym.getout.storage.SyncStateStore;
import ym.getout.util.LoggerUtil;
import ym.getout.util.TimeFormatter;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.HashMap;
import java.util.Map;
import java.util.Date;

public class EventProcessor {

    private final EventStore eventRepository;
    private final BanStore banRepository;
    private final IpBanStore ipBanRepository;
    private final SyncStateStore syncStateRepository;
    private final Settings settings;
    private final SchedulerAdapter scheduler;
    private final MessageService messages;
    private final AdminNotifier adminNotifier;
    private final AtomicLong lastProcessedId = new AtomicLong(0);

    public EventProcessor(EventStore eventRepository, BanStore banRepository, IpBanStore ipBanRepository,
                          SyncStateStore syncStateRepository, Settings settings, SchedulerAdapter scheduler,
                          MessageService messages, AdminNotifier adminNotifier) {
        this.eventRepository = eventRepository;
        this.banRepository = banRepository;
        this.ipBanRepository = ipBanRepository;
        this.syncStateRepository = syncStateRepository;
        this.settings = settings;
        this.scheduler = scheduler;
        this.messages = messages;
        this.adminNotifier = adminNotifier;
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
            case "IP_BAN" -> handleIpBanEvent(event);
            case "TEMPBAN" -> handleTempBanEvent(event);
            case "UNBAN" -> handleUnbanEvent(event);
            case "KICK" -> handleKickEvent(event);
            default -> LoggerUtil.debug("Unknown event type: " + event.getEventType());
        }
    }

    private void handleBanEvent(SyncEvent event) {
        Long banId = parseLongPayload(event.getPayload(), "ban_id");
        if (settings.isSyncKickOnlineAfterBan()) {
            kickOnlinePlayer(event.getTargetUuid(), event.getTargetName(), event.getReason(), event.getOperatorName(), "BAN", null, banId);
        }
        adminNotifier.notifyPunishment("BAN", event.getTargetName(), event.getReason(), event.getOperatorName(), event.getServerId(), true);
    }

    private void handleTempBanEvent(SyncEvent event) {
        Long expiresAt = parseLongPayload(event.getPayload(), "expires_at");
        Long banId = parseLongPayload(event.getPayload(), "ban_id");
        if (settings.isSyncKickOnlineAfterBan()) {
            kickOnlinePlayer(event.getTargetUuid(), event.getTargetName(), event.getReason(), event.getOperatorName(), "TEMPBAN", expiresAt, banId);
        }
        adminNotifier.notifyPunishment("TEMPBAN", event.getTargetName(), event.getReason(), event.getOperatorName(), event.getServerId(), true);
    }

    private void handleIpBanEvent(SyncEvent event) {
        adminNotifier.notifyPunishment("IP_BAN", event.getTargetName(), event.getReason(), event.getOperatorName(), event.getServerId(), true);
    }

    private void handleKickEvent(SyncEvent event) {
        kickOnlinePlayer(event.getTargetUuid(), event.getTargetName(), event.getReason(), event.getOperatorName(), "KICK", null, null);
        adminNotifier.notifyPunishment("KICK", event.getTargetName(), event.getReason(), event.getOperatorName(), event.getServerId(), true);
    }

    private void handleUnbanEvent(SyncEvent event) {
        banRepository.deactivateBan(event.getTargetUuid());
        String ip = parseStringPayload(event.getPayload(), "ip");
        if (ip != null && !ip.isBlank()) {
            ipBanRepository.deactivateIpBan(ip);
        }
        adminNotifier.notifyPunishment("UNBAN", event.getTargetName(), event.getReason(), event.getOperatorName(), event.getServerId(), true);
    }

    private void kickOnlinePlayer(UUID uuid, String name, String reason, String operator, String eventType, Long expiresAt, Long banId) {
        scheduler.runGlobal(() -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                Component kickMessage = buildKickMessage(eventType, name, reason, operator, expiresAt, banId);
                player.kick(kickMessage);
                LoggerUtil.info("Kicked synced player: " + name);
            }
        });
    }

    private Component buildKickMessage(String eventType, String name, String reason, String operator, Long expiresAt, Long banId) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", name != null ? name : "");
        placeholders.put("ban_id", banId != null ? String.valueOf(banId) : "未知");
        placeholders.put("reason", reason != null ? reason : "");
        placeholders.put("operator", operator != null ? operator : "Console");
        placeholders.put("time", new SimpleDateFormat(settings.getTimeFormat()).format(new Date()));
        placeholders.put("left", expiresAt != null ? TimeFormatter.formatRemaining(expiresAt) : messages.getString("placeholder.never", "永久"));
        placeholders.put("expire", expiresAt != null ? new SimpleDateFormat(settings.getTimeFormat()).format(new Date(expiresAt)) : messages.getString("placeholder.never", "永久"));

        String path = switch (eventType.toUpperCase()) {
            case "TEMPBAN" -> "tempban.kick-message";
            case "KICK" -> "kick.message";
            default -> "ban.kick-message";
        };
        return messages.getComponentList(path, placeholders);
    }

    private Long parseLongPayload(String payload, String key) {
        if (payload == null || payload.isBlank()) return null;
        for (String part : payload.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && key.equalsIgnoreCase(kv[0])) {
                try {
                    return Long.parseLong(kv[1]);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private String parseStringPayload(String payload, String key) {
        if (payload == null || payload.isBlank()) return null;
        for (String part : payload.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && key.equalsIgnoreCase(kv[0])) {
                return kv[1];
            }
        }
        return null;
    }

    public void setLastProcessedId(long id) {
        lastProcessedId.set(id);
    }
}
