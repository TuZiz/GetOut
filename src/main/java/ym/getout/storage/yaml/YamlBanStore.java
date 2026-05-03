package ym.getout.storage.yaml;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ym.getout.model.BanRecord;
import ym.getout.storage.BanStore;
import ym.getout.util.LoggerUtil;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class YamlBanStore implements BanStore {

    private final File file;
    private final YamlConfiguration data;

    public YamlBanStore(File dataFolder) {
        File storageDir = new File(dataFolder, "storage");
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }
        this.file = new File(storageDir, "bans.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    @Override
    public synchronized long createBan(UUID uuid, String name, String reason, UUID operatorUuid, String operatorName, Long expiresAt, String serverId) {
        long id = data.getLong("meta.next-id", 1L);
        String path = "bans." + uuid;
        data.set(path + ".id", id);
        data.set(path + ".uuid", uuid.toString());
        data.set(path + ".name", name);
        data.set(path + ".reason", reason);
        data.set(path + ".operator_uuid", operatorUuid != null ? operatorUuid.toString() : null);
        data.set(path + ".operator_name", operatorName);
        data.set(path + ".created_at", System.currentTimeMillis());
        data.set(path + ".expires_at", expiresAt);
        data.set(path + ".active", true);
        data.set(path + ".server_id", serverId);
        data.set(path + ".version", 1L);
        data.set("meta.next-id", id + 1);
        save();
        return id;
    }

    @Override
    public synchronized BanRecord findActiveBan(UUID uuid) {
        BanRecord record = read("bans." + uuid);
        return record != null && record.isActive() ? record : null;
    }

    @Override
    public synchronized void deactivateBan(UUID uuid) {
        String path = "bans." + uuid;
        if (data.isConfigurationSection(path)) {
            data.set(path + ".active", false);
            save();
        }
    }

    @Override
    public synchronized int deactivateExpiredBans() {
        ConfigurationSection section = data.getConfigurationSection("bans");
        if (section == null) return 0;
        int count = 0;
        for (String key : section.getKeys(false)) {
            String path = "bans." + key;
            BanRecord record = read(path);
            if (record != null && record.isActive() && record.isExpired()) {
                data.set(path + ".active", false);
                count++;
            }
        }
        if (count > 0) {
            save();
        }
        return count;
    }

    private BanRecord read(String path) {
        if (!data.isConfigurationSection(path)) return null;
        String uuidString = data.getString(path + ".uuid", path.substring(path.lastIndexOf('.') + 1));
        String operatorUuidString = data.getString(path + ".operator_uuid");
        if (operatorUuidString != null && operatorUuidString.isBlank()) {
            operatorUuidString = null;
        }
        Long expiresAt = data.isSet(path + ".expires_at") ? data.getLong(path + ".expires_at") : null;
        return new BanRecord(
                data.getLong(path + ".id", 0L),
                UUID.fromString(uuidString),
                data.getString(path + ".name", ""),
                data.getString(path + ".reason", ""),
                operatorUuidString != null ? UUID.fromString(operatorUuidString) : null,
                data.getString(path + ".operator_name", ""),
                data.getLong(path + ".created_at", 0L),
                expiresAt,
                data.getBoolean(path + ".active", false),
                data.getString(path + ".server_id", ""),
                data.getLong(path + ".version", 1L)
        );
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            LoggerUtil.error("Failed to save YAML ban store", e);
        }
    }
}
