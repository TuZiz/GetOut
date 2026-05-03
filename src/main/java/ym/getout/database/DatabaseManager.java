package ym.getout.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import ym.getout.config.Settings;
import ym.getout.util.LoggerUtil;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * 数据库连接管理器，使用 HikariCP 连接池。
 * 所有数据库操作必须在异步线程中执行。
 */
public class DatabaseManager {

    private HikariDataSource dataSource;
    private final Settings settings;
    private volatile boolean initialized = false;

    public DatabaseManager(Settings settings) {
        this.settings = settings;
    }

    /**
     * 异步初始化数据库连接池。
     * Never call this method from the server main thread.
     */
    public boolean init() {
        checkMainThread();

        SqlDialect dialect = SqlDialect.fromString(settings.getDbType());
        HikariConfig config = new HikariConfig();

        config.setJdbcUrl(dialect.getJdbcUrl(settings.getDbHost(), settings.getDbPort(), settings.getDbDatabase()));
        config.setUsername(settings.getDbUsername());
        config.setPassword(settings.getDbPassword());

        // Only set driver class for non-MySQL (HikariCP auto-detects MySQL)
        if (dialect != SqlDialect.MYSQL && dialect != SqlDialect.MARIADB) {
            try {
                config.setDriverClassName(dialect.getDriverClassName());
            } catch (Exception e) {
                LoggerUtil.error("Failed to load database driver: " + dialect.getDriverClassName(), e);
            }
        }

        config.setMaximumPoolSize(settings.getPoolMaxSize());
        config.setMinimumIdle(settings.getPoolMinIdle());
        config.setConnectionTimeout(settings.getPoolConnectionTimeoutMs());
        config.setMaxLifetime(settings.getPoolMaxLifetimeMs());
        config.setPoolName("Getout-HikariPool");

        // Connection test
        config.setConnectionTestQuery(dialect == SqlDialect.SQLITE ? "SELECT 1" : null);

        try {
            dataSource = new HikariDataSource(config);
            initialized = true;
            LoggerUtil.info("Database connection pool initialized (" + settings.getDbType() + ")");
            return true;
        } catch (Exception e) {
            LoggerUtil.error("Failed to initialize database connection pool", e);
            initialized = false;
            return false;
        }
    }

    /**
     * 获取数据库连接。
     * Never call this method from the server main thread.
     */
    public Connection getConnection() throws SQLException {
        checkMainThread();
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Database connection pool is not initialized or closed");
        }
        return dataSource.getConnection();
    }

    /**
     * 获取 SQL 方言。
     */
    public SqlDialect getDialect() {
        return SqlDialect.fromString(settings.getDbType());
    }

    /**
     * 数据库是否已初始化。
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * 关闭连接池。
     */
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            LoggerUtil.info("Database connection pool closed");
        }
        initialized = false;
    }

    private void checkMainThread() {
        try {
            if (Bukkit.isPrimaryThread()) {
                throw new IllegalStateException("Database IO on main thread is forbidden");
            }
        } catch (NoClassDefFoundError ignored) {
            // Bukkit not available (e.g., during testing)
        }
    }
}
