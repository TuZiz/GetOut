package ym.getout.model;

import java.util.UUID;

public class SyncEvent {
    private final long id;
    private final String eventType;
    private final UUID targetUuid;
    private final String targetName;
    private final String reason;
    private final String operatorName;
    private final long createdAt;
    private final String serverId;
    private final String payload;

    public SyncEvent(long id, String eventType, UUID targetUuid, String targetName,
                     String reason, String operatorName, long createdAt,
                     String serverId, String payload) {
        this.id = id;
        this.eventType = eventType;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.reason = reason;
        this.operatorName = operatorName;
        this.createdAt = createdAt;
        this.serverId = serverId;
        this.payload = payload;
    }

    public long getId() { return id; }
    public String getEventType() { return eventType; }
    public UUID getTargetUuid() { return targetUuid; }
    public String getTargetName() { return targetName; }
    public String getReason() { return reason; }
    public String getOperatorName() { return operatorName; }
    public long getCreatedAt() { return createdAt; }
    public String getServerId() { return serverId; }
    public String getPayload() { return payload; }
}
