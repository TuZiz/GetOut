package ym.getout.model;

import java.util.UUID;

public class PlayerIndex {
    private final UUID uuid;
    private final String name;
    private final String nameLower;
    private final long lastSeenAt;
    private final String lastServer;

    public PlayerIndex(UUID uuid, String name, String nameLower, long lastSeenAt, String lastServer) {
        this.uuid = uuid;
        this.name = name;
        this.nameLower = nameLower;
        this.lastSeenAt = lastSeenAt;
        this.lastServer = lastServer;
    }

    public UUID getUuid() { return uuid; }
    public String getName() { return name; }
    public String getNameLower() { return nameLower; }
    public long getLastSeenAt() { return lastSeenAt; }
    public String getLastServer() { return lastServer; }
}
