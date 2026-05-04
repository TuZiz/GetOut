package ym.getout.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class Settings {

    private String serverId = "server-1";
    private boolean debug = false;
    private boolean failOpenOnDatabaseError = true;

    // Storage
    private String storageType = "yaml";
    private String databaseFailureStrategy = "fail-fast";

    // Database
    private String dbType = "mysql";
    private String dbHost = "localhost";
    private int dbPort = 3306;
    private String dbDatabase = "getout";
    private String dbUsername = "root";
    private String dbPassword = "password";
    private String tablePrefix = "getout_";
    private int poolMaxSize = 10;
    private int poolMinIdle = 2;
    private long poolConnectionTimeoutMs = 30000;
    private long poolMaxLifetimeMs = 1800000;

    // Sync
    private long syncPollIntervalTicks = 20;
    private int syncEventRetentionDays = 7;
    private boolean syncKickOnlineAfterBan = true;
    private boolean kickCrossServer = true;
    private boolean syncProcessOwnEvents = false;
    private long expiredBanCleanupIntervalTicks = 6000L;

    // Admin notifications
    private boolean adminNotifyEnabled = true;
    private String adminNotifyPermission = "getout.notify";
    private boolean adminNotifyConsole = true;

    // Cache
    private int papiCacheSeconds = 30;
    private int playerIndexCacheSeconds = 300;

    // Time
    private String zoneId = "Asia/Shanghai";
    private String timeFormat = "yyyy-MM-dd HH:mm:ss";

    // Messages
    private String lang = "zh_cn";

    // Redis (optional, not implemented in base)
    private boolean redisEnabled = false;
    private String redisHost = "localhost";
    private int redisPort = 6379;
    private String redisPassword = "";

    public void load(FileConfiguration config) {
        serverId = config.getString("server-id", "server-1");
        debug = config.getBoolean("debug", false);
        failOpenOnDatabaseError = config.getBoolean("fail-open-on-database-error", true);

        if (config.isSet("storage.type")) {
            storageType = config.getString("storage.type", "yaml").toLowerCase();
        } else {
            // Backward compatibility for older configs that only had the database block.
            storageType = config.isConfigurationSection("database") ? "database" : "yaml";
        }
        databaseFailureStrategy = config.getString("storage.database-failure-strategy", "fail-fast").toLowerCase();

        dbType = config.getString("database.type", "mysql");
        dbHost = config.getString("database.host", "localhost");
        dbPort = config.getInt("database.port", 3306);
        dbDatabase = config.getString("database.database", "getout");
        dbUsername = config.getString("database.username", "root");
        dbPassword = config.getString("database.password", "password");
        tablePrefix = config.getString("database.table-prefix", "getout_");
        poolMaxSize = config.getInt("database.pool.maximum-pool-size", 10);
        poolMinIdle = config.getInt("database.pool.minimum-idle", 2);
        poolConnectionTimeoutMs = config.getLong("database.pool.connection-timeout-ms", 30000);
        poolMaxLifetimeMs = config.getLong("database.pool.max-lifetime-ms", 1800000);

        syncPollIntervalTicks = config.getLong("sync.poll-interval-ticks", 20);
        syncEventRetentionDays = config.getInt("sync.event-retention-days", 7);
        syncKickOnlineAfterBan = config.getBoolean("sync.kick-online-after-ban", true);
        kickCrossServer = config.getBoolean("kick.cross-server", true);
        syncProcessOwnEvents = config.getBoolean("sync.process-own-events", false);
        expiredBanCleanupIntervalTicks = config.getLong("cleanup.expired-ban-interval-ticks", 6000L);

        adminNotifyEnabled = config.getBoolean("admin-notify.enabled", true);
        adminNotifyPermission = config.getString("admin-notify.permission", "getout.notify");
        adminNotifyConsole = config.getBoolean("admin-notify.console", true);

        papiCacheSeconds = config.getInt("cache.papi-cache-seconds", 30);
        playerIndexCacheSeconds = config.getInt("cache.player-index-cache-seconds", 300);

        zoneId = config.getString("time.zone-id", "Asia/Shanghai");
        timeFormat = config.getString("time.format", "yyyy-MM-dd HH:mm:ss");

        lang = config.getString("messages.lang", "zh_cn");
    }

    // --- Getters ---

    public String getServerId() { return serverId; }
    public boolean isDebug() { return debug; }
    public boolean isFailOpenOnDatabaseError() { return failOpenOnDatabaseError; }
    public String getStorageType() { return storageType; }
    public boolean isDatabaseEnabled() { return "database".equals(storageType); }
    public void setStorageType(String storageType) { this.storageType = storageType == null ? "yaml" : storageType.toLowerCase(); }
    public String getDatabaseFailureStrategy() { return databaseFailureStrategy; }
    public boolean isDatabaseFallbackYamlEnabled() { return "fallback-yaml".equals(databaseFailureStrategy); }

    public String getDbType() { return dbType; }
    public String getDbHost() { return dbHost; }
    public int getDbPort() { return dbPort; }
    public String getDbDatabase() { return dbDatabase; }
    public String getDbUsername() { return dbUsername; }
    public String getDbPassword() { return dbPassword; }
    public String getTablePrefix() { return tablePrefix; }
    public int getPoolMaxSize() { return poolMaxSize; }
    public int getPoolMinIdle() { return poolMinIdle; }
    public long getPoolConnectionTimeoutMs() { return poolConnectionTimeoutMs; }
    public long getPoolMaxLifetimeMs() { return poolMaxLifetimeMs; }

    public long getSyncPollIntervalTicks() { return syncPollIntervalTicks; }
    public int getSyncEventRetentionDays() { return syncEventRetentionDays; }
    public boolean isSyncKickOnlineAfterBan() { return syncKickOnlineAfterBan; }
    public boolean isKickCrossServer() { return kickCrossServer; }
    public boolean isSyncProcessOwnEvents() { return syncProcessOwnEvents; }
    public long getExpiredBanCleanupIntervalTicks() { return expiredBanCleanupIntervalTicks; }

    public boolean isAdminNotifyEnabled() { return adminNotifyEnabled; }
    public String getAdminNotifyPermission() { return adminNotifyPermission; }
    public boolean isAdminNotifyConsole() { return adminNotifyConsole; }

    public int getPapiCacheSeconds() { return papiCacheSeconds; }
    public int getPlayerIndexCacheSeconds() { return playerIndexCacheSeconds; }

    public String getZoneId() { return zoneId; }
    public String getTimeFormat() { return timeFormat; }

    public String getLang() { return lang; }

    public boolean isRedisEnabled() { return redisEnabled; }
    public String getRedisHost() { return redisHost; }
    public int getRedisPort() { return redisPort; }
    public String getRedisPassword() { return redisPassword; }
}
