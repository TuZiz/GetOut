package ym.getout.database.repository;

import org.bukkit.Bukkit;
import ym.getout.config.Settings;
import ym.getout.database.DatabaseManager;
import ym.getout.database.SqlDialect;
import ym.getout.storage.SyncStateStore;
import ym.getout.util.LoggerUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class SyncStateRepository implements SyncStateStore {

    private final DatabaseManager db;
    private final Settings settings;

    public SyncStateRepository(DatabaseManager db, Settings settings) {
        this.db = db;
        this.settings = settings;
    }

    private String table() {
        return settings.getTablePrefix() + "sync_state";
    }

    public long getLastProcessedEventId(String serverId) {
        checkMainThread();
        String sql = "SELECT last_processed_event_id FROM " + table() + " WHERE server_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, serverId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("last_processed_event_id");
                }
            }
        } catch (SQLException e) {
            LoggerUtil.error("Failed to load sync cursor for server " + serverId, e);
        }
        return 0L;
    }

    public void saveLastProcessedEventId(String serverId, long eventId) {
        checkMainThread();
        SqlDialect dialect = db.getDialect();
        String sql;
        switch (dialect) {
            case POSTGRESQL -> sql = "INSERT INTO " + table() +
                    " (server_id, last_processed_event_id, updated_at) VALUES (?, ?, ?) " +
                    "ON CONFLICT (server_id) DO UPDATE SET last_processed_event_id = EXCLUDED.last_processed_event_id, updated_at = EXCLUDED.updated_at";
            case SQLITE -> sql = "INSERT OR REPLACE INTO " + table() +
                    " (server_id, last_processed_event_id, updated_at) VALUES (?, ?, ?)";
            default -> sql = "INSERT INTO " + table() +
                    " (server_id, last_processed_event_id, updated_at) VALUES (?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE last_processed_event_id = VALUES(last_processed_event_id), updated_at = VALUES(updated_at)";
        }

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, serverId);
            ps.setLong(2, eventId);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            LoggerUtil.error("Failed to save sync cursor for server " + serverId, e);
        }
    }

    private void checkMainThread() {
        try {
            if (Bukkit.isPrimaryThread()) {
                throw new IllegalStateException("Database IO on main thread is forbidden");
            }
        } catch (NoClassDefFoundError ignored) {
        }
    }
}
