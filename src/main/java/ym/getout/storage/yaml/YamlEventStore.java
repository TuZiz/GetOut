package ym.getout.storage.yaml;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ym.getout.model.SyncEvent;
import ym.getout.storage.EventStore;
import ym.getout.util.LoggerUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class YamlEventStore implements EventStore {

    private final File file;
    private final YamlConfiguration data;

    public YamlEventStore(File dataFolder) {
        File storageDir = new File(dataFolder, "storage");
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }
        this.file = new File(storageDir, "events.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    @Override
    public synchronized long insertEvent(String eventType, UUID targetUuid, String targetName, String reason, String operatorName, String serverId, String payload) {
        long id = data.getLong("meta.next-id", 1L);
        String path = "events." + id;
        data.set(path + ".id", id);
        data.set(path + ".event_type", eventType);
        data.set(path + ".target_uuid", targetUuid.toString());
        data.set(path + ".target_name", targetName);
        data.set(path + ".reason", reason);
        data.set(path + ".operator_name", operatorName);
        data.set(path + ".created_at", System.currentTimeMillis());
        data.set(path + ".server_id", serverId);
        data.set(path + ".payload", payload);
        data.set("meta.next-id", id + 1);
        save();
        return id;
    }

    @Override
    public synchronized List<SyncEvent> findEventsAfter(long lastId, boolean includeOwn, String serverId) {
        List<SyncEvent> events = new ArrayList<>();
        ConfigurationSection section = data.getConfigurationSection("events");
        if (section == null) return events;
        for (String key : section.getKeys(false)) {
            SyncEvent event = read("events." + key);
            if (event == null || event.getId() <= lastId) continue;
            if (!includeOwn && serverId.equals(event.getServerId())) continue;
            events.add(event);
        }
        events.sort(Comparator.comparingLong(SyncEvent::getId));
        return events;
    }

    @Override
    public synchronized int cleanExpiredEvents(int retentionDays) {
        ConfigurationSection section = data.getConfigurationSection("events");
        if (section == null) return 0;
        long cutoff = System.currentTimeMillis() - (retentionDays * 86400L * 1000L);
        int count = 0;
        for (String key : new ArrayList<>(section.getKeys(false))) {
            String path = "events." + key;
            if (data.getLong(path + ".created_at", 0L) < cutoff) {
                data.set(path, null);
                count++;
            }
        }
        if (count > 0) {
            save();
        }
        return count;
    }

    private SyncEvent read(String path) {
        if (!data.isConfigurationSection(path)) return null;
        return new SyncEvent(
                data.getLong(path + ".id", 0L),
                data.getString(path + ".event_type", ""),
                UUID.fromString(data.getString(path + ".target_uuid", "00000000-0000-0000-0000-000000000000")),
                data.getString(path + ".target_name", ""),
                data.getString(path + ".reason", ""),
                data.getString(path + ".operator_name", ""),
                data.getLong(path + ".created_at", 0L),
                data.getString(path + ".server_id", ""),
                data.getString(path + ".payload", "")
        );
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            LoggerUtil.error("Failed to save YAML event store", e);
        }
    }
}
