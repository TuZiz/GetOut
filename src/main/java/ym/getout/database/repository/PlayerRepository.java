package ym.getout.database.repository;

import org.bukkit.Bukkit;
import ym.getout.config.Settings;
import ym.getout.database.DatabaseManager;
import ym.getout.model.PlayerIndex;
import ym.getout.storage.PlayerStore;
import ym.getout.util.LoggerUtil;

import java.sql.*;
import java.util.UUID;

/**
 * 玩家索引仓库。
 * Never call any method from the server main thread.
 */
public class PlayerRepository implements PlayerStore {

    private final DatabaseManager db;
    private final Settings settings;

    public PlayerRepository(DatabaseManager db, Settings settings) {
        this.db = db;
        this.settings = settings;
    }

    private String table() {
        return settings.getTablePrefix() + "players";
    }

    /**
     * 插入或更新玩家索引。
     */
    public void upsert(UUID uuid, String name, String serverId) {
        upsert(uuid, name, serverId, "");
    }

    @Override
    public void upsert(UUID uuid, String name, String serverId, String lastIp) {
        checkMainThread();
        String sql;
        switch (db.getDialect()) {
            case POSTGRESQL -> sql = "INSERT INTO " + table() + " (uuid, name, name_lower, last_seen_at, last_server, last_ip) " +
                    "VALUES (?, ?, ?, ?, ?, ?) " +
                    "ON CONFLICT (uuid) DO UPDATE SET name = EXCLUDED.name, name_lower = EXCLUDED.name_lower, " +
                    "last_seen_at = EXCLUDED.last_seen_at, last_server = EXCLUDED.last_server, last_ip = EXCLUDED.last_ip";
            case SQLITE -> sql = "INSERT OR REPLACE INTO " + table() + " (uuid, name, name_lower, last_seen_at, last_server, last_ip) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";
            default -> sql = "INSERT INTO " + table() + " (uuid, name, name_lower, last_seen_at, last_server, last_ip) " +
                    "VALUES (?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE name = VALUES(name), name_lower = VALUES(name_lower), " +
                    "last_seen_at = VALUES(last_seen_at), last_server = VALUES(last_server), last_ip = VALUES(last_ip)";
        }

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setString(3, name.toLowerCase());
            ps.setLong(4, System.currentTimeMillis());
            ps.setString(5, serverId);
            ps.setString(6, lastIp != null ? lastIp : "");
            ps.executeUpdate();
        } catch (SQLException e) {
            LoggerUtil.error("Failed to upsert player index for " + name, e);
        }
    }

    /**
     * 通过 UUID 查询玩家索引。
     */
    public PlayerIndex findByUuid(UUID uuid) {
        checkMainThread();
        String sql = "SELECT uuid, name, name_lower, last_seen_at, last_server, last_ip FROM " + table() + " WHERE uuid = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new PlayerIndex(
                            UUID.fromString(rs.getString("uuid")),
                            rs.getString("name"),
                            rs.getString("name_lower"),
                            rs.getLong("last_seen_at"),
                            rs.getString("last_server"),
                            rs.getString("last_ip")
                    );
                }
            }
        } catch (SQLException e) {
            LoggerUtil.error("Failed to find player by UUID: " + uuid, e);
        }
        return null;
    }

    /**
     * 通过玩家名（不区分大小写）查询玩家索引。
     */
    public PlayerIndex findByName(String name) {
        checkMainThread();
        String sql = "SELECT uuid, name, name_lower, last_seen_at, last_server, last_ip FROM " + table() + " WHERE name_lower = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new PlayerIndex(
                            UUID.fromString(rs.getString("uuid")),
                            rs.getString("name"),
                            rs.getString("name_lower"),
                            rs.getLong("last_seen_at"),
                            rs.getString("last_server"),
                            rs.getString("last_ip")
                    );
                }
            }
        } catch (SQLException e) {
            LoggerUtil.error("Failed to find player by name: " + name, e);
        }
        return null;
    }

    /**
     * 尝试通过名字或 UUID 字符串查找玩家。
     */
    public PlayerIndex findByNameOrUuid(String input) {
        UUID uuid = ym.getout.util.UuidUtil.parse(input);
        if (uuid != null) {
            return findByUuid(uuid);
        }
        return findByName(input);
    }

    private void checkMainThread() {
        try {
            if (Bukkit.isPrimaryThread()) {
                throw new IllegalStateException("Database IO on main thread is forbidden");
            }
        } catch (NoClassDefFoundError ignored) {}
    }
}
