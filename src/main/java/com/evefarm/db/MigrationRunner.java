package com.evefarm.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MigrationRunner {

    private static final Logger LOG = Logger.getLogger(MigrationRunner.class.getName());
    private static final Pattern VERSION_PATTERN = Pattern.compile("V(\\d+)__.*\\.sql");

    private static final List<String> MIGRATIONS = List.of(
            "db/migrations/V1__init.sql",
            "db/migrations/V2__tracker_skill_point_value.sql",
            "db/migrations/V3__tracker_skill_point_filter.sql",
            "db/migrations/V4__wallet_journal.sql",
            "db/migrations/V5__market_orders.sql",
            "db/migrations/V6__saved_table_filter.sql",
            "db/migrations/V7__update_cooldown.sql",
            "db/migrations/V8__wallet_transactions.sql",
            "db/migrations/V9__character_contracts.sql",
            "db/migrations/V10__industry_jobs.sql",
            "db/migrations/V11__price_breakdown.sql",
            "db/migrations/V12__table_column_state.sql",
            "db/migrations/V13__market_order_extra_fields.sql",
            "db/migrations/V14__character_loyalty_points.sql",
            "db/migrations/V15__tracker_lp_value.sql",
            "db/migrations/V16__asset_container_name.sql",
            "db/migrations/V17__character_owner_hash.sql",
            "db/migrations/V18__price_volume.sql",
            "db/migrations/V19__character_kills.sql",
            "db/migrations/V20__character_kills_from_gamelog.sql",
            "db/migrations/V21__character_kills_solar_system.sql",
            "db/migrations/V22__agents.sql",
            "db/migrations/V23__officer_hunting.sql",
            "db/migrations/V24__officer_drops.sql",
            "db/migrations/V25__keep_removed_character_history.sql"
    );

    public static void run(Database database) {
        Connection connection = database.connection();
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS schema_version (
                      version    INTEGER PRIMARY KEY,
                      applied_at TEXT NOT NULL,
                      checksum   TEXT
                    )
                    """);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create schema_version table", e);
        }

        ensureChecksumColumn(connection);
        validateMigrationList();

        for (String resourcePath : MIGRATIONS) {
            int version = parseVersion(resourcePath);
            String sql = readResource(resourcePath);
            String checksum = checksum(sql);
            AppliedMigration applied = findApplied(connection, version);
            if (applied.exists()) {
                if (applied.checksum() == null || applied.checksum().isBlank()) {
                    storeChecksum(connection, version, checksum);
                } else if (!applied.checksum().equals(checksum)) {
                    LOG.severe("Migration checksum mismatch for " + resourcePath
                            + ": it was edited after it was applied to this database. Applied migrations must "
                            + "never be edited; put schema changes in a new migration.");
                }
                continue;
            }
            applyMigration(connection, resourcePath, version, sql, checksum);
        }
    }

    private record AppliedMigration(boolean exists, String checksum) {
    }

    private static AppliedMigration findApplied(Connection connection, int version) {
        String sql = "SELECT checksum FROM schema_version WHERE version = ?";
        try (var ps = connection.prepareStatement(sql)) {
            ps.setInt(1, version);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new AppliedMigration(true, rs.getString("checksum"))
                        : new AppliedMigration(false, null);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to check schema_version", e);
        }
    }

    private static void applyMigration(Connection connection, String resourcePath, int version,
                                       String sql, String checksum) {
        LOG.info("Applying migration " + resourcePath);
        try {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                for (String single : splitStatements(sql)) {
                    String trimmed = single.trim();
                    if (!trimmed.isEmpty()) {
                        statement.execute(trimmed);
                    }
                }
                try (var ps = connection.prepareStatement(
                        "INSERT INTO schema_version(version, applied_at, checksum) VALUES (?, ?, ?)")) {
                    ps.setInt(1, version);
                    ps.setString(2, Instant.now().toString());
                    ps.setString(3, checksum);
                    ps.executeUpdate();
                }
            }
            connection.commit();
        } catch (SQLException e) {
            rollbackQuietly(connection);
            throw new IllegalStateException("Failed to apply migration " + resourcePath, e);
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException ignored) {
            }
        }
    }

    private static void ensureChecksumColumn(Connection connection) {
        boolean found = false;
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA table_info(schema_version)")) {
            while (rs.next()) {
                if ("checksum".equalsIgnoreCase(rs.getString("name"))) {
                    found = true;
                    break;
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to inspect schema_version", e);
        }
        if (!found) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE schema_version ADD COLUMN checksum TEXT");
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to add migration checksums", e);
            }
        }
    }

    private static void storeChecksum(Connection connection, int version, String checksum) {
        try (var ps = connection.prepareStatement(
                "UPDATE schema_version SET checksum = ? WHERE version = ?")) {
            ps.setString(1, checksum);
            ps.setInt(2, version);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to baseline checksum for migration V" + version, e);
        }
    }

    static String checksum(String sql) {
        String normalized = sql.replace("﻿", "").replace("\r\n", "\n").replace('\r', '\n');
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static void validateMigrationList() {
        for (int index = 0; index < MIGRATIONS.size(); index++) {
            String path = MIGRATIONS.get(index);
            int expectedVersion = index + 1;
            if (parseVersion(path) != expectedVersion) {
                throw new IllegalStateException("Migration list must be contiguous and ordered; expected V"
                        + expectedVersion + " but found " + path);
            }
        }
    }

    static List<String> migrationPaths() {
        return MIGRATIONS;
    }

    static List<String> splitStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int beginEndDepth = 0;
        int length = sql.length();
        int i = 0;
        while (i < length) {
            char c = sql.charAt(i);
            if (c == '-' && i + 1 < length && sql.charAt(i + 1) == '-') {
                int end = sql.indexOf('\n', i);
                if (end < 0) {
                    end = length;
                }
                current.append(sql, i, end);
                i = end;
                continue;
            }
            if (c == '\'' || c == '"') {
                char quote = c;
                int end = i + 1;
                while (end < length) {
                    if (sql.charAt(end) == quote) {
                        if (end + 1 < length && sql.charAt(end + 1) == quote) {
                            end += 2;
                            continue;
                        }
                        end++;
                        break;
                    }
                    end++;
                }
                current.append(sql, i, Math.min(end, length));
                i = end;
                continue;
            }
            if (Character.isLetter(c) || c == '_') {
                int wordEnd = i;
                while (wordEnd < length && (Character.isLetterOrDigit(sql.charAt(wordEnd)) || sql.charAt(wordEnd) == '_')) {
                    wordEnd++;
                }
                String word = sql.substring(i, wordEnd);
                if (word.equalsIgnoreCase("BEGIN")) {
                    beginEndDepth++;
                } else if (word.equalsIgnoreCase("END")) {
                    beginEndDepth = Math.max(0, beginEndDepth - 1);
                }
                current.append(word);
                i = wordEnd;
                continue;
            }
            if (c == ';' && beginEndDepth == 0) {
                statements.add(current.toString());
                current.setLength(0);
                i++;
                continue;
            }
            current.append(c);
            i++;
        }
        if (!current.toString().isBlank()) {
            statements.add(current.toString());
        }
        return statements;
    }

    private static void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
        }
    }

    private static int parseVersion(String resourcePath) {
        String fileName = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
        Matcher matcher = VERSION_PATTERN.matcher(fileName);
        if (!matcher.matches()) {
            throw new IllegalStateException("Migration filename does not match V<n>__*.sql: " + fileName);
        }
        return Integer.parseInt(matcher.group(1));
    }

    private static String readResource(String resourcePath) {
        try (InputStream in = MigrationRunner.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Missing migration resource: " + resourcePath);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read migration resource: " + resourcePath, e);
        }
    }

    private MigrationRunner() {
    }
}
