package ym.getout.storage.yaml;

import org.bukkit.configuration.file.YamlConfiguration;
import ym.getout.storage.SyncStateStore;
import ym.getout.util.LoggerUtil;

import java.io.File;
import java.io.IOException;

public class YamlSyncStateStore implements SyncStateStore {

    private final File file;
    private final YamlConfiguration data;

    public YamlSyncStateStore(File dataFolder) {
        File storageDir = new File(dataFolder, "storage");
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }
        this.file = new File(storageDir, "sync-state.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    @Override
    public synchronized long getLastProcessedEventId(String serverId) {
        return data.getLong("servers." + serverId + ".last_processed_event_id", 0L);
    }

    @Override
    public synchronized void saveLastProcessedEventId(String serverId, long eventId) {
        data.set("servers." + serverId + ".last_processed_event_id", eventId);
        data.set("servers." + serverId + ".updated_at", System.currentTimeMillis());
        save();
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            LoggerUtil.error("Failed to save YAML sync state store", e);
        }
    }
}
