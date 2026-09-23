package com.evefarm.db;

import com.evefarm.util.AppPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class Database {

    private final Connection connection;

    public Database() {
        this(AppPaths.databaseFile().toString());
    }

    public Database(String jdbcPathOrFile) {
        Connection opened = null;
        try {
            Files.createDirectories(AppPaths.appDataDir());
            Class.forName("org.sqlite.JDBC");
            opened = DriverManager.getConnection("jdbc:sqlite:" + jdbcPathOrFile);
            try (Statement statement = opened.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
                statement.execute("PRAGMA journal_mode = WAL");
            }
            this.connection = opened;
        } catch (ClassNotFoundException | SQLException | IOException e) {
            closeQuietly(opened);
            throw new IllegalStateException("Failed to open database", e);
        }
    }

    private static void closeQuietly(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
        }
    }

    public Connection connection() {
        return connection;
    }

    public void close() {
        closeQuietly(connection);
    }
}
