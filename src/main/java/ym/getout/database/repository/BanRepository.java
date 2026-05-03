package ym.getout.database.repository;

import org.bukkit.Bukkit;
import ym.getout.config.Settings;
import ym.getout.database.DatabaseManager;
import ym.getout.model.BanRecord;
import ym.getout.storage.BanStore;
import ym.getout.util.LoggerUtil;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 封禁记录仓库。
 * Never call any method from the server main thread.
 */
public class BanRepository implements BanStore {

    private final DatabaseManager db;
    private final Settings settings;

    public BanRepository(DatabaseManager db, Settings settings) {
        this.db = db;
        this.settings = settings;
    }

    private String table() {
        return settings.getTablePrefix() + "bans";
    }

    /**
     * 创建一个新的封禁记录。
     * 如果该玩家已有 active 记录，先将其标记为 inactive。
     * 返回新封禁记录的 ID。
     * 使用事务保证原子性。
     */
    public long createBan(UUID uuid, String name, String reason,
                          UUID operatorUuid, String operatorName,
                          Long expiresAt, String serverId) {
        checkMainThread();
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. 将旧的 active ban 标记为 inactive
                String deactivateSql = "UPDATE " + table() + " SET active = FALSE WHERE uuid = ? AND active = TRUE";
                try (PreparedStatement ps = conn.prepareStatement(deactivateSql)) {
                    ps.setString(1, uuid.toString());
                    ps.executeUpdate();
                }

                // 2. 插入新封禁记录
                long now = System.currentTimeMillis();
                String insertSql;
                if (db.getDialect() == ym.getout.database.SqlDialect.POSTGRESQL) {
                    insertSql = "INSERT INTO " + table() +
                            " (uuid, name, reason, operator_uuid, operator_name, created_at, expires_at, active, server_id, version) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, TRUE, ?, 1) RETURNING id";
                    try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                        ps.setString(1, uuid.toString());
                        ps.setString(2, name);
                        ps.setString(3, reason);
                        ps.setString(4, operatorUuid != null ? operatorUuid.toString() : null);
                        ps.setString(5, operatorName);
                        ps.setLong(6, now);
                        if (expiresAt != null) {
                            ps.setLong(7, expiresAt);
                        } else {
                            ps.setNull(7, Types.BIGINT);
                        }
                        ps.setString(8, serverId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                long id = rs.getLong("id");
                                conn.commit();
                                return id;
                            }
                        }
                    }
                } else {
                    insertSql = "INSERT INTO " + table() +
                            " (uuid, name, reason, operator_uuid, operator_name, created_at, expires_at, active, server_id, version) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, TRUE, ?, 1)";
                    try (PreparedStatement ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                        ps.setString(1, uuid.toString());
                        ps.setString(2, name);
                        ps.setString(3, reason);
                        ps.setString(4, operatorUuid != null ? operatorUuid.toString() : null);
                        ps.setString(5, operatorName);
                        ps.setLong(6, now);
                        if (expiresAt != null) {
                            ps.setLong(7, expiresAt);
                        } else {
                            ps.setNull(7, Types.BIGINT);
                        }
                        ps.setString(8, serverId);
                        ps.executeUpdate();
                        try (ResultSet keys = ps.getGeneratedKeys()) {
                            if (keys.next()) {
                                long id = keys.getLong(1);
                                conn.commit();
                                return id;
                            }
                        }
                    }
                }
                conn.commit();
                return -1;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            LoggerUtil.error("Failed to create ban for " + name, e);
            return -1;
        }
    }

    /**
     * 查询玩家当前有效的封禁记录。
     */
    public BanRecord findActiveBan(UUID uuid) {
        checkMainThread();
        String sql = "SELECT id, uuid, name, reason, operator_uuid, operator_name, created_at, expires_at, active, server_id, version " +
                "FROM " + table() + " WHERE uuid = ? AND active = TRUE LIMIT 1";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return readBanRecord(rs);
                }
            }
        } catch (SQLException e) {
            LoggerUtil.error("Failed to find active ban for " + uuid, e);
        }
        return null;
    }

    /**
     * 将封禁记录标记为 inactive（解封）。
     */
    public void deactivateBan(UUID uuid) {
        checkMainThread();
        String sql = "UPDATE " + table() + " SET active = FALSE WHERE uuid = ? AND active = TRUE";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            LoggerUtil.error("Failed to deactivate ban for " + uuid, e);
        }
    }

    /**
     * 通过 ID 查询封禁记录。
     */
    public BanRecord findById(long id) {
        checkMainThread();
        String sql = "SELECT id, uuid, name, reason, operator_uuid, operator_name, created_at, expires_at, active, server_id, version " +
                "FROM " + table() + " WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return readBanRecord(rs);
                }
            }
        } catch (SQLException e) {
            LoggerUtil.error("Failed to find ban by id: " + id, e);
        }
        return null;
    }

    /**
     * 标记过期的临时封禁为 inactive。
     */
    public int deactivateExpiredBans() {
        checkMainThread();
        String sql = "UPDATE " + table() + " SET active = FALSE WHERE active = TRUE AND expires_at IS NOT NULL AND expires_at <= ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis());
            return ps.executeUpdate();
        } catch (SQLException e) {
            LoggerUtil.error("Failed to deactivate expired bans", e);
        }
        return 0;
    }

    /**
     * 通过 UUID 和 version 更新封禁记录的版本号（用于跨服同步时避免冲突）。
     */
    public boolean updateBanVersion(long banId, long newVersion) {
        checkMainThread();
        String sql = "UPDATE " + table() + " SET version = ? WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, newVersion);
            ps.setLong(2, banId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LoggerUtil.error("Failed to update ban version for id " + banId, e);
        }
        return false;
    }

    private BanRecord readBanRecord(ResultSet rs) throws SQLException {
        String expiresAtStr = rs.getString("expires_at");
        Long expiresAt = rs.getObject("expires_at") != null ? rs.getLong("expires_at") : null;

        String operatorUuidStr = rs.getString("operator_uuid");
        UUID operatorUuid = operatorUuidStr != null ? UUID.fromString(operatorUuidStr) : null;

        return new BanRecord(
                rs.getLong("id"),
                UUID.fromString(rs.getString("uuid")),
                rs.getString("name"),
                rs.getString("reason"),
                operatorUuid,
                rs.getString("operator_name"),
                rs.getLong("created_at"),
                expiresAt,
                rs.getBoolean("active"),
                rs.getString("server_id"),
                rs.getLong("version")
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
