package ym.getout.storage;

import ym.getout.model.SyncEvent;

import java.util.List;
import java.util.UUID;

public interface EventStore {
    long insertEvent(String eventType, UUID targetUuid, String targetName, String reason, String operatorName, String serverId, String payload);

    List<SyncEvent> findEventsAfter(long lastId, boolean includeOwn, String serverId);

    int cleanExpiredEvents(int retentionDays);
}
