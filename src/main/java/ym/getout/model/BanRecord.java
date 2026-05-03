package ym.getout.model;

import java.util.UUID;

public class BanRecord {
    private final long id;
    private final UUID uuid;
    private final String name;
    private final String reason;
    private final UUID operatorUuid;
    private final String operatorName;
    private final long createdAt;
    private final Long expiresAt;
    private final boolean active;
    private final String serverId;
    private final long version;

    public BanRecord(long id, UUID uuid, String name, String reason,
                     UUID operatorUuid, String operatorName,
                     long createdAt, Long expiresAt, boolean active,
                     String serverId, long version) {
        this.id = id;
        this.uuid = uuid;
        this.name = name;
        this.reason = reason;
        this.operatorUuid = operatorUuid;
        this.operatorName = operatorName;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.active = active;
        this.serverId = serverId;
        this.version = version;
    }

    public long getId() { return id; }
    public UUID getUuid() { return uuid; }
    public String getName() { return name; }
    public String getReason() { return reason; }
    public UUID getOperatorUuid() { return operatorUuid; }
    public String getOperatorName() { return operatorName; }
    public long getCreatedAt() { return createdAt; }
    public Long getExpiresAt() { return expiresAt; }
    public boolean isActive() { return active; }
    public String getServerId() { return serverId; }
    public long getVersion() { return version; }

    public boolean isPermanent() {
        return expiresAt == null;
    }

    public boolean isExpired() {
        if (expiresAt == null) return false;
        return System.currentTimeMillis() >= expiresAt;
    }

    public boolean isEffective() {
        return active && !isExpired();
    }
}
