package ym.getout.storage.yaml;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ym.getout.model.PlayerIndex;
import ym.getout.storage.PlayerStore;
import ym.getout.util.LoggerUtil;
import ym.getout.util.UuidUtil;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class YamlPlayerStore implements PlayerStore {

    private final File file;
    private final YamlConfiguration data;

    public YamlPlayerStore(File dataFolder) {
        File storageDir = new File(dataFolder, "storage");
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }
        this.file = new File(storageDir, "players.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    @Override
    public synchronized void upsert(UUID uuid, String name, String serverId) {
        upsert(uuid, name, serverId, "");
    }

    @Override
    public synchronized void upsert(UUID uuid, String name, String serverId, String lastIp) {
        String path = "players." + uuid;
        data.set(path + ".name", name);
        data.set(path + ".name_lower", name.toLowerCase());
        data.set(path + ".last_seen_at", System.currentTimeMillis());
        data.set(path + ".last_server", serverId);
        data.set(path + ".last_ip", lastIp != null ? lastIp : "");
        save();
    }

    @Override
    public synchronized PlayerIndex findByUuid(UUID uuid) {
        return read("players." + uuid);
    }

    @Override
    public synchronized PlayerIndex findByName(String name) {
        ConfigurationSection section = data.getConfigurationSection("players");
        if (section == null) return null;
        String lower = name.toLowerCase();
        for (String key : section.getKeys(false)) {
            String path = "players." + key;
            if (lower.equals(data.getString(path + ".name_lower", ""))) {
                return read(path);
            }
        }
        return null;
    }

    @Override
    public PlayerIndex findByNameOrUuid(String input) {
        UUID uuid = UuidUtil.parse(input);
        if (uuid != null) {
            return findByUuid(uuid);
        }
        return findByName(input);
    }

    private PlayerIndex read(String path) {
        if (!data.isConfigurationSection(path)) return null;
        UUID uuid = UUID.fromString(path.substring(path.lastIndexOf('.') + 1));
        return new PlayerIndex(
                uuid,
                data.getString(path + ".name", ""),
                data.getString(path + ".name_lower", ""),
                data.getLong(path + ".last_seen_at", 0L),
                data.getString(path + ".last_server", ""),
                data.getString(path + ".last_ip", "")
        );
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            LoggerUtil.error("Failed to save YAML player store", e);
        }
    }
}
