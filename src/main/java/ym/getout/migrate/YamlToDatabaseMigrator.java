package ym.getout.migrate;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ym.getout.config.Settings;
import ym.getout.database.DatabaseManager;
import ym.getout.database.schema.SchemaInitializer;
import ym.getout.database.repository.BanRepository;
import ym.getout.database.repository.EventRepository;
import ym.getout.database.repository.PlayerRepository;
import ym.getout.database.repository.SyncStateRepository;
import ym.getout.util.LoggerUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class YamlToDatabaseMigrator {

    public record MigrationResult(int players, int bans, int events, int syncStates) {}

    private final File dataFolder;
    private final Settings settings;

    public YamlToDatabaseMigrator(File dataFolder, Settings settings) {
        this.dataFolder = dataFolder;
        this.settings = settings;
    }

    public MigrationResult migrate(DatabaseManager databaseManager) {
        if (!databaseManager.isInitialized()) {
            throw new IllegalStateException("Database is not initialized");
        }

        if (!new SchemaInitializer(databaseManager, settings).createTables()) {
            throw new IllegalStateException("Database schema initialization failed");
        }

        PlayerRepository playerRepository = new PlayerRepository(databaseManager, settings);
        BanRepository banRepository = new BanRepository(databaseManager, settings);
        EventRepository eventRepository = new EventRepository(databaseManager, settings);
        SyncStateRepository syncStateRepository = new SyncStateRepository(databaseManager, settings);

        int players = migratePlayers(playerRepository);
        int bans = migrateBans(banRepository);
        int events = migrateEvents(eventRepository);
        int syncStates = migrateSyncStates(syncStateRepository);

        return new MigrationResult(players, bans, events, syncStates);
    }

    private int migratePlayers(PlayerRepository repository) {
        File file = storageFile("players.yml");
        if (!file.exists()) return 0;
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = data.getConfigurationSection("players");
        if (section == null) return 0;

        int count = 0;
        for (String key : new ArrayList<>(section.getKeys(false))) {
            String path = "players." + key;
            try {
                repository.upsert(
                        UUID.fromString(data.getString(path + ".uuid", key)),
                        data.getString(path + ".name", ""),
                        data.getString(path + ".last_server", "")
                );
                count++;
            } catch (Exception e) {
                LoggerUtil.error("Failed to migrate player entry " + key, e);
            }
        }
        return count;
    }

    private int migrateBans(BanRepository repository) {
        File file = storageFile("bans.yml");
        if (!file.exists()) return 0;
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = data.getConfigurationSection("bans");
        if (section == null) return 0;

        int count = 0;
        for (String key : new ArrayList<>(section.getKeys(false))) {
            String path = "bans." + key;
            if (!data.getBoolean(path + ".active", false)) continue;
            try {
                UUID uuid = UUID.fromString(data.getString(path + ".uuid", key));
                String reason = data.getString(path + ".reason", "");
                String name = data.getString(path + ".name", "");
                String operatorName = data.getString(path + ".operator_name", "Console");
                String operatorUuidStr = data.getString(path + ".operator_uuid");
                UUID operatorUuid = operatorUuidStr == null || operatorUuidStr.isBlank() ? null : UUID.fromString(operatorUuidStr);
                Long expiresAt = data.isSet(path + ".expires_at") ? data.getLong(path + ".expires_at") : null;
                long id = repository.createBan(uuid, name, reason, operatorUuid, operatorName, expiresAt, data.getString(path + ".server_id", settings.getServerId()));
                if (id > 0) {
                    count++;
                }
            } catch (Exception e) {
                LoggerUtil.error("Failed to migrate ban entry " + key, e);
            }
        }
        return count;
    }

    private int migrateEvents(EventRepository repository) {
        File file = storageFile("events.yml");
        if (!file.exists()) return 0;
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = data.getConfigurationSection("events");
        if (section == null) return 0;

        int count = 0;
        for (String key : new ArrayList<>(section.getKeys(false))) {
            String path = "events." + key;
            try {
                long inserted = repository.insertEvent(
                        data.getString(path + ".event_type", ""),
                        UUID.fromString(data.getString(path + ".target_uuid", key)),
                        data.getString(path + ".target_name", ""),
                        data.getString(path + ".reason", ""),
                        data.getString(path + ".operator_name", "Console"),
                        data.getString(path + ".server_id", settings.getServerId()),
                        data.getString(path + ".payload", "")
                );
                if (inserted > 0) {
                    count++;
                }
            } catch (Exception e) {
                LoggerUtil.error("Failed to migrate event entry " + key, e);
            }
        }
        return count;
    }

    private int migrateSyncStates(SyncStateRepository repository) {
        File file = storageFile("sync-state.yml");
        if (!file.exists()) return 0;
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = data.getConfigurationSection("servers");
        if (section == null) return 0;

        int count = 0;
        for (String serverId : new ArrayList<>(section.getKeys(false))) {
            try {
                long eventId = data.getLong("servers." + serverId + ".last_processed_event_id", 0L);
                repository.saveLastProcessedEventId(serverId, eventId);
                count++;
            } catch (Exception e) {
                LoggerUtil.error("Failed to migrate sync state for server " + serverId, e);
            }
        }
        return count;
    }

    private File storageFile(String name) {
        File storageDir = new File(dataFolder, "storage");
        return new File(storageDir, name);
    }
}
