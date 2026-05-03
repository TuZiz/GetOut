package ym.getout.database.repository;

import org.bukkit.Bukkit;
import ym.getout.config.Settings;
import ym.getout.database.DatabaseManager;
import ym.getout.model.SyncEvent;
import ym.getout.storage.EventStore;
import ym.getout.util.LoggerUtil;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 同步事件仓库（Outbox 模式）。
 * Never call any method from the server main thread.
 */
public class EventRepository implements EventStore {

    private final DatabaseManager db;
    private final Settings settings;

    public EventRepository(DatabaseManager db, Settings settings) {
        this.db = db;
        this.settings = settings;
    }

    private String table() {
        return settings.getTablePrefix() + "events";
    }

    /**
     * 写入一条同步事件。
     */
    public long insertEvent(String eventType, UUID targetUuid, String targetName,
                            String reason, String operatorName, String serverId, String payload) {
        checkMainThread();
        long now = System.currentTimeMillis();

        if (db.getDialect() == ym.getout.database.SqlDialect.POSTGRESQL) {
            String sql = "INSERT INTO " + table() +
                    " (event_type, target_uuid, target_name, reason, operator_name, created_at, server_id, payload) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id";
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, eventType);
                ps.setString(2, targetUuid.toString());
                ps.setString(3, targetName);
                ps.setString(4, reason);
                ps.setString(5, operatorName);
                ps.setLong(6, now);
                ps.setString(7, serverId);
                ps.setString(8, payload);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getLong("id");
                }
            } catch (SQLException e) {
                LoggerUtil.error("Failed to insert sync event", e);
            }
        } else {
            String sql = "INSERT INTO " + table() +
                    " (event_type, target_uuid, target_name, reason, operator_name, created_at, server_id, payload) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, eventType);
                ps.setString(2, targetUuid.toString());
                ps.setString(3, targetName);
                ps.setString(4, reason);
                ps.setString(5, operatorName);
                ps.setLong(6, now);
                ps.setString(7, serverId);
                ps.setString(8, payload);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) return keys.getLong(1);
                }
            } catch (SQLException e) {
                LoggerUtil.error("Failed to insert sync event", e);
            }
        }
        return -1;
    }

    /**
     * 查询指定 ID 之后的事件（用于增量同步）。
     * 排除本服务器产生的事件（如果配置了 process-own-events = false）。
     */
    public List<SyncEvent> findEventsAfter(long lastId, boolean includeOwn, String serverId) {
        checkMainThread();
        List<SyncEvent> events = new ArrayList<>();

        String sql;
        if (includeOwn) {
            sql = "SELECT id, event_type, target_uuid, target_name, reason, operator_name, created_at, server_id, payload " +
                    "FROM " + table() + " WHERE id > ? ORDER BY id ASC";
        } else {
            sql = "SELECT id, event_type, target_uuid, target_name, reason, operator_name, created_at, server_id, payload " +
                    "FROM " + table() + " WHERE id > ? AND server_id != ? ORDER BY id ASC";
        }

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, lastId);
            if (!includeOwn) {
                ps.setString(2, serverId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    events.add(readSyncEvent(rs));
                }
            }
        } catch (SQLException e) {
            LoggerUtil.error("Failed to find events after id " + lastId, e);
        }
        return events;
    }

    /**
     * 清理过期的同步事件。
     */
    public int cleanExpiredEvents(int retentionDays) {
        checkMainThread();
        long cutoff = System.currentTimeMillis() - (retentionDays * 86400L * 1000L);
        String sql = "DELETE FROM " + table() + " WHERE created_at < ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, cutoff);
            return ps.executeUpdate();
        } catch (SQLException e) {
            LoggerUtil.error("Failed to clean expired events", e);
        }
        return 0;
    }

    private SyncEvent readSyncEvent(ResultSet rs) throws SQLException {
        return new SyncEvent(
                rs.getLong("id"),
                rs.getString("event_type"),
                UUID.fromString(rs.getString("target_uuid")),
                rs.getString("target_name"),
                rs.getString("reason"),
                rs.getString("operator_name"),
                rs.getLong("created_at"),
                rs.getString("server_id"),
                rs.getString("payload")
        );
    }

    private void checkMainThread() {
        try {
            if (Bukkit.isPrimaryThread()) {
                throw new IllegalStateException("Database IO on main thread is forbidden");
            }
        } catch (NoClassDefFoundError ignored) {}
    }
}
