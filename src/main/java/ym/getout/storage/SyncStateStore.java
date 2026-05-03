package ym.getout.storage;

public interface SyncStateStore {
    long getLastProcessedEventId(String serverId);

    void saveLastProcessedEventId(String serverId, long eventId);
}
