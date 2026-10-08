package com.evefarm.db;

import com.evefarm.util.AppPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class Database {

    public interface Work {
        void run(Connection connection) throws SQLException;
    }

    public interface ResultWork<T> {
        T run(Connection connection) throws SQLException;
    }

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

    public void transaction(String failure, Work work) {
        transactionResult(failure, current -> {
            work.run(current);
            return null;
        });
    }

    public synchronized <T> T transactionResult(String failure, ResultWork<T> work) {
        try {
            connection.setAutoCommit(false);
        } catch (SQLException e) {
            throw new IllegalStateException(failure, e);
        }
        try {
            T result = work.run(connection);
            connection.commit();
            return result;
        } catch (SQLException e) {
            rollback(e);
            throw new IllegalStateException(failure, e);
        } catch (RuntimeException | Error e) {
            rollback(e);
            throw e;
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException ignored) {
            }
        }
    }

    private void rollback(Throwable failure) {
        try {
            connection.rollback();
        } catch (SQLException e) {
            failure.addSuppressed(e);
        }
    }

    public void close() {
        closeQuietly(connection);
    }
}
