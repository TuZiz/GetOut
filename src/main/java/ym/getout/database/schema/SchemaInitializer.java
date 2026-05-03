package ym.getout.database.schema;

import ym.getout.config.Settings;
import ym.getout.database.DatabaseManager;
import ym.getout.database.SqlDialect;
import ym.getout.util.LoggerUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 数据库表初始化器。
 * Never call this method from the server main thread.
 */
public class SchemaInitializer {

    private final DatabaseManager db;
    private final Settings settings;

    public SchemaInitializer(DatabaseManager db, Settings settings) {
        this.db = db;
        this.settings = settings;
    }

    /**
     * 创建所有必要的数据库表和索引。
     * Never call this method from the server main thread.
     */
    public void createTables() {
        SqlDialect dialect = db.getDialect();
        String prefix = settings.getTablePrefix();

        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(buildPlayersTable(dialect, prefix));
            LoggerUtil.debug("Table " + prefix + "players ensured");

            // Bans table: SQLite doesn't support inline INDEX in CREATE TABLE
            if (dialect == SqlDialect.SQLITE) {
                stmt.execute(buildBansTableSqlite(prefix));
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_bans_uuid_active ON " + prefix + "bans (uuid, active)");
            } else {
                stmt.execute(buildBansTable(dialect, prefix));
            }
            LoggerUtil.debug("Table " + prefix + "bans ensured");

            if (dialect == SqlDialect.SQLITE) {
                stmt.execute(buildEventsTableSqlite(prefix));
            } else {
                stmt.execute(buildEventsTable(dialect, prefix));
            }
            LoggerUtil.debug("Table " + prefix + "events ensured");

            stmt.execute(buildSyncStateTable(dialect, prefix));
            LoggerUtil.debug("Table " + prefix + "sync_state ensured");

            LoggerUtil.info("Database schema initialized");
        } catch (SQLException e) {
            LoggerUtil.error("Failed to create database tables", e);
        }
    }

    private String buildPlayersTable(SqlDialect dialect, String prefix) {
        return "CREATE TABLE IF NOT EXISTS " + prefix + "players (" +
                "uuid " + dialect.uuidType() + " PRIMARY KEY, " +
                "name VARCHAR(16) NOT NULL, " +
                "name_lower VARCHAR(16) NOT NULL, " +
                "last_seen_at " + dialect.longType() + " NOT NULL DEFAULT 0, " +
                "last_server VARCHAR(64) DEFAULT ''" +
                ")";
    }

    private String buildBansTable(SqlDialect dialect, String prefix) {
        String idType = dialect.autoIncrement();
        return "CREATE TABLE IF NOT EXISTS " + prefix + "bans (" +
                "id " + idType + " PRIMARY KEY, " +
                "uuid " + dialect.uuidType() + " NOT NULL, " +
                "name VARCHAR(16) NOT NULL, " +
                "reason " + dialect.textType() + " NOT NULL DEFAULT '', " +
                "operator_uuid " + dialect.uuidType() + " DEFAULT NULL, " +
                "operator_name VARCHAR(16) DEFAULT '', " +
                "created_at " + dialect.longType() + " NOT NULL DEFAULT 0, " +
                "expires_at " + dialect.longType() + " DEFAULT NULL, " +
                "active BOOLEAN NOT NULL DEFAULT TRUE, " +
                "server_id VARCHAR(64) DEFAULT '', " +
                "version " + dialect.longType() + " NOT NULL DEFAULT 1, " +
                "INDEX idx_bans_uuid_active (uuid, active)" +
                ")";
    }

    private String buildBansTableSqlite(String prefix) {
        return "CREATE TABLE IF NOT EXISTS " + prefix + "bans (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "uuid CHAR(36) NOT NULL, " +
                "name VARCHAR(16) NOT NULL, " +
                "reason TEXT NOT NULL DEFAULT '', " +
                "operator_uuid CHAR(36) DEFAULT NULL, " +
                "operator_name VARCHAR(16) DEFAULT '', " +
                "created_at BIGINT NOT NULL DEFAULT 0, " +
                "expires_at BIGINT DEFAULT NULL, " +
                "active BOOLEAN NOT NULL DEFAULT TRUE, " +
                "server_id VARCHAR(64) DEFAULT '', " +
                "version BIGINT NOT NULL DEFAULT 1" +
                ")";
    }

    private String buildEventsTable(SqlDialect dialect, String prefix) {
        String idType = dialect.autoIncrement();
        return "CREATE TABLE IF NOT EXISTS " + prefix + "events (" +
                "id " + idType + " PRIMARY KEY, " +
                "event_type VARCHAR(32) NOT NULL, " +
                "target_uuid " + dialect.uuidType() + " NOT NULL, " +
                "target_name VARCHAR(16) NOT NULL, " +
                "reason " + dialect.textType() + " NOT NULL DEFAULT '', " +
                "operator_name VARCHAR(16) DEFAULT '', " +
                "created_at " + dialect.longType() + " NOT NULL DEFAULT 0, " +
                "server_id VARCHAR(64) DEFAULT '', " +
                "payload " + dialect.textType() + " DEFAULT ''" +
                ")";
    }

    private String buildEventsTableSqlite(String prefix) {
        return "CREATE TABLE IF NOT EXISTS " + prefix + "events (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "event_type VARCHAR(32) NOT NULL, " +
                "target_uuid CHAR(36) NOT NULL, " +
                "target_name VARCHAR(16) NOT NULL, " +
                "reason TEXT NOT NULL DEFAULT '', " +
                "operator_name VARCHAR(16) DEFAULT '', " +
                "created_at BIGINT NOT NULL DEFAULT 0, " +
                "server_id VARCHAR(64) DEFAULT '', " +
                "payload TEXT DEFAULT ''" +
                ")";
    }

    private String buildSyncStateTable(SqlDialect dialect, String prefix) {
        return "CREATE TABLE IF NOT EXISTS " + prefix + "sync_state (" +
                "server_id VARCHAR(64) PRIMARY KEY, " +
                "last_processed_event_id " + dialect.longType() + " NOT NULL DEFAULT 0, " +
                "updated_at " + dialect.longType() + " NOT NULL DEFAULT 0" +
                ")";
    }
}
