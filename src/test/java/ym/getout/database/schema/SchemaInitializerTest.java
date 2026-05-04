package ym.getout.database.schema;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ym.getout.config.Settings;
import ym.getout.database.DatabaseManager;
import ym.getout.database.SqlDialect;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaInitializerTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldRejectInvalidTablePrefix() {
        Settings settings = new Settings();
        YamlConfiguration config = new YamlConfiguration();
        config.set("database.table-prefix", "bad-prefix;");

        assertThrows(IllegalArgumentException.class, () -> settings.load(config));
    }

    @Test
    void shouldCreatePrefixedIndexesForSqlite() throws Exception {
        Settings settings = new Settings();
        YamlConfiguration config = new YamlConfiguration();
        config.set("database.type", "sqlite");
        config.set("database.database", tempDir.resolve("schema-test").toString());
        config.set("database.table-prefix", "unit_");
        settings.load(config);

        String jdbcUrl = "jdbc:sqlite:" + tempDir.resolve("schema-test.db");
        SchemaInitializer initializer = new SchemaInitializer(new TestDatabaseManager(settings, jdbcUrl), settings);

        assertTrue(initializer.createTables());
        assertTrue(indexExists(jdbcUrl, "unit_idx_bans_uuid_active"));
        assertTrue(indexExists(jdbcUrl, "unit_idx_ip_bans_ip_active"));
    }

    private boolean indexExists(String jdbcUrl, String indexName) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             ResultSet rs = connection.createStatement().executeQuery(
                     "SELECT name FROM sqlite_master WHERE type = 'index' AND name = '" + indexName + "'")) {
            return rs.next();
        }
    }

    private static final class TestDatabaseManager extends DatabaseManager {

        private final String jdbcUrl;

        private TestDatabaseManager(Settings settings, String jdbcUrl) {
            super(settings);
            this.jdbcUrl = jdbcUrl;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(jdbcUrl);
        }

        @Override
        public SqlDialect getDialect() {
            return SqlDialect.SQLITE;
        }
    }
}
