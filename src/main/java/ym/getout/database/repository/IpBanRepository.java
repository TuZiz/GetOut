package ym.getout.database.repository;

import org.bukkit.Bukkit;
import ym.getout.config.Settings;
import ym.getout.database.DatabaseManager;
import ym.getout.database.SqlDialect;
import ym.getout.model.IpBanRecord;
import ym.getout.storage.IpBanStore;
import ym.getout.util.LoggerUtil;

import java.sql.*;
import java.util.UUID;

public class IpBanRepository implements IpBanStore {

    private final DatabaseManager db;
    private final Settings settings;

    public IpBanRepository(DatabaseManager db, Settings settings) {
        this.db = db;
        this.settings = settings;
    }

    private String table() {
        return settings.getTablePrefix() + "ip_bans";
    }

    @Override
    public long createIpBan(String ip, String reason, UUID operatorUuid, String operatorName, String serverId) {
        checkMainThread();
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement("UPDATE " + table() + " SET active = FALSE WHERE ip = ? AND active = TRUE")) {
                    ps.setString(1, ip);
                    ps.executeUpdate();
                }

                long now = System.currentTimeMillis();
                if (db.getDialect() == SqlDialect.POSTGRESQL) {
                    String sql = "INSERT INTO " + table() +
                            " (ip, reason, operator_uuid, operator_name, created_at, active, server_id, version) " +
                            "VALUES (?, ?, ?, ?, ?, TRUE, ?, 1) RETURNING id";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        bindInsert(ps, ip, reason, operatorUuid, operatorName, now, serverId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                long id = rs.getLong("id");
                                conn.commit();
                                return id;
                            }
                        }
                    }
                } else {
                    String sql = "INSERT INTO " + table() +
                            " (ip, reason, operator_uuid, operator_name, created_at, active, server_id, version) " +
                            "VALUES (?, ?, ?, ?, ?, TRUE, ?, 1)";
                    try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                        bindInsert(ps, ip, reason, operatorUuid, operatorName, now, serverId);
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
            LoggerUtil.error("Failed to create IP ban for " + ip, e);
            return -1;
        }
    }

    @Override
    public IpBanRecord findActiveIpBan(String ip) {
        checkMainThread();
        String sql = "SELECT id, ip, reason, operator_uuid, operator_name, created_at, active, server_id, version " +
                "FROM " + table() + " WHERE ip = ? AND active = TRUE LIMIT 1";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ip);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return readIpBanRecord(rs);
                }
            }
        } catch (SQLException e) {
            LoggerUtil.error("Failed to find active IP ban for " + ip, e);
        }
        return null;
    }

    @Override
    public void deactivateIpBan(String ip) {
        checkMainThread();
        String sql = "UPDATE " + table() + " SET active = FALSE WHERE ip = ? AND active = TRUE";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ip);
            ps.executeUpdate();
        } catch (SQLException e) {
            LoggerUtil.error("Failed to deactivate IP ban for " + ip, e);
        }
    }

    private void bindInsert(PreparedStatement ps, String ip, String reason, UUID operatorUuid,
                            String operatorName, long now, String serverId) throws SQLException {
        ps.setString(1, ip);
        ps.setString(2, reason);
        ps.setString(3, operatorUuid != null ? operatorUuid.toString() : null);
        ps.setString(4, operatorName);
        ps.setLong(5, now);
        ps.setString(6, serverId);
    }

    private IpBanRecord readIpBanRecord(ResultSet rs) throws SQLException {
        String operatorUuidStr = rs.getString("operator_uuid");
        UUID operatorUuid = operatorUuidStr != null ? UUID.fromString(operatorUuidStr) : null;
        return new IpBanRecord(
                rs.getLong("id"),
                rs.getString("ip"),
                rs.getString("reason"),
                operatorUuid,
                rs.getString("operator_name"),
                rs.getLong("created_at"),
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
