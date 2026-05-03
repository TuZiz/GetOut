package ym.getout.database;

public enum SqlDialect {
    MYSQL,
    MARIADB,
    POSTGRESQL,
    SQLITE;

    public static SqlDialect fromString(String type) {
        if (type == null) return MYSQL;
        return switch (type.toLowerCase()) {
            case "mysql" -> MYSQL;
            case "mariadb" -> MARIADB;
            case "postgresql", "postgres" -> POSTGRESQL;
            case "sqlite" -> SQLITE;
            default -> MYSQL;
        };
    }

    public String getJdbcUrl(String host, int port, String database) {
        return switch (this) {
            case MYSQL -> "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf8";
            case MARIADB -> "jdbc:mariadb://" + host + ":" + port + "/" + database + "?useSSL=false&serverTimezone=UTC&characterEncoding=utf8";
            case POSTGRESQL -> "jdbc:postgresql://" + host + ":" + port + "/" + database;
            case SQLITE -> "jdbc:sqlite:" + database + ".db";
        };
    }

    public String getDriverClassName() {
        return switch (this) {
            case MYSQL, MARIADB -> "com.mysql.cj.jdbc.Driver";
            case POSTGRESQL -> "org.postgresql.Driver";
            case SQLITE -> "org.sqlite.JDBC";
        };
    }

    public String uuidType() {
        return switch (this) {
            case POSTGRESQL -> "UUID";
            default -> "CHAR(36)";
        };
    }

    public String autoIncrement() {
        return switch (this) {
            case POSTGRESQL -> "BIGSERIAL";
            default -> "BIGINT AUTO_INCREMENT";
        };
    }

    public String nowFunction() {
        return switch (this) {
            case POSTGRESQL -> "EXTRACT(EPOCH FROM NOW()) * 1000";
            case SQLITE -> "CAST(strftime('%s','now') * 1000 AS INTEGER)";
            default -> "UNIX_TIMESTAMP() * 1000";
        };
    }

    public String longType() {
        return switch (this) {
            case POSTGRESQL -> "BIGINT";
            default -> "BIGINT";
        };
    }

    public String textType() {
        return switch (this) {
            case POSTGRESQL -> "TEXT";
            default -> "TEXT";
        };
    }
}
