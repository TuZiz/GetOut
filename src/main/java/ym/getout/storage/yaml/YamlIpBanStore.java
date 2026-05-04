package ym.getout.storage.yaml;

import org.bukkit.configuration.file.YamlConfiguration;
import ym.getout.model.IpBanRecord;
import ym.getout.storage.IpBanStore;
import ym.getout.util.LoggerUtil;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class YamlIpBanStore implements IpBanStore {

    private final File file;
    private final YamlConfiguration data;

    public YamlIpBanStore(File dataFolder) {
        File storageDir = new File(dataFolder, "storage");
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }
        this.file = new File(storageDir, "ip-bans.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    @Override
    public synchronized long createIpBan(String ip, String reason, UUID operatorUuid, String operatorName, String serverId) {
        String key = normalizeKey(ip);
        long id = data.getLong("meta.next-id", 1L);
        String path = "ip-bans." + key;
        data.set(path + ".id", id);
        data.set(path + ".ip", ip);
        data.set(path + ".reason", reason);
        data.set(path + ".operator_uuid", operatorUuid != null ? operatorUuid.toString() : null);
        data.set(path + ".operator_name", operatorName);
        data.set(path + ".created_at", System.currentTimeMillis());
        data.set(path + ".active", true);
        data.set(path + ".server_id", serverId);
        data.set(path + ".version", 1L);
        data.set("meta.next-id", id + 1);
        save();
        return id;
    }

    @Override
    public synchronized IpBanRecord findActiveIpBan(String ip) {
        IpBanRecord record = read("ip-bans." + normalizeKey(ip));
        return record != null && record.isActive() ? record : null;
    }

    @Override
    public synchronized void deactivateIpBan(String ip) {
        String path = "ip-bans." + normalizeKey(ip);
        if (data.isConfigurationSection(path)) {
            data.set(path + ".active", false);
            save();
        }
    }

    private IpBanRecord read(String path) {
        if (!data.isConfigurationSection(path)) return null;
        String operatorUuidString = data.getString(path + ".operator_uuid");
        if (operatorUuidString != null && operatorUuidString.isBlank()) {
            operatorUuidString = null;
        }
        return new IpBanRecord(
                data.getLong(path + ".id", 0L),
                data.getString(path + ".ip", ""),
                data.getString(path + ".reason", ""),
                operatorUuidString != null ? UUID.fromString(operatorUuidString) : null,
                data.getString(path + ".operator_name", ""),
                data.getLong(path + ".created_at", 0L),
                data.getBoolean(path + ".active", false),
                data.getString(path + ".server_id", ""),
                data.getLong(path + ".version", 1L)
        );
    }

    private String normalizeKey(String ip) {
        return ip == null ? "" : ip.replace('.', '_').replace(':', '_');
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            LoggerUtil.error("Failed to save YAML IP ban store", e);
        }
    }
}
