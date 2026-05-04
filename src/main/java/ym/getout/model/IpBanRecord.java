package ym.getout.model;

import java.util.UUID;

public class IpBanRecord {
    private final long id;
    private final String ip;
    private final String reason;
    private final UUID operatorUuid;
    private final String operatorName;
    private final long createdAt;
    private final boolean active;
    private final String serverId;
    private final long version;

    public IpBanRecord(long id, String ip, String reason, UUID operatorUuid, String operatorName,
                       long createdAt, boolean active, String serverId, long version) {
        this.id = id;
        this.ip = ip;
        this.reason = reason;
        this.operatorUuid = operatorUuid;
        this.operatorName = operatorName;
        this.createdAt = createdAt;
        this.active = active;
        this.serverId = serverId;
        this.version = version;
    }

    public long getId() { return id; }
    public String getIp() { return ip; }
    public String getReason() { return reason; }
    public UUID getOperatorUuid() { return operatorUuid; }
    public String getOperatorName() { return operatorName; }
    public long getCreatedAt() { return createdAt; }
    public boolean isActive() { return active; }
    public String getServerId() { return serverId; }
    public long getVersion() { return version; }
}
