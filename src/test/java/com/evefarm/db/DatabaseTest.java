package com.evefarm.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseTest {

    private Database database;

    @BeforeEach
    void open() throws SQLException {
        database = new Database(":memory:");
        try (Statement statement = database.connection().createStatement()) {
            statement.execute("CREATE TABLE item(id INTEGER)");
            statement.execute("INSERT INTO item VALUES (1), (2), (3)");
        }
    }

    @AfterEach
    void close() {
        database.close();
    }

    private String items() throws SQLException {
        try (Statement statement = database.connection().createStatement();
             ResultSet rs = statement.executeQuery("SELECT group_concat(id, ',') FROM (SELECT id FROM item ORDER BY id)")) {
            rs.next();
            return rs.getString(1);
        }
    }

    private static void replaceWithTen(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM item");
            statement.executeUpdate("INSERT INTO item VALUES (10)");
        }
    }

    @Test
    void finishedWorkIsSaved() throws SQLException {
        database.transaction("Failed", DatabaseTest::replaceWithTen);

        assertEquals("10", items());
        assertTrue(database.connection().getAutoCommit());
    }

    @Test
    void anErrorHalfwayKeepsEverythingAsItWas() throws SQLException {
        IllegalArgumentException failure = new IllegalArgumentException("broken row");

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> database.transaction("Failed", connection -> {
                    replaceWithTen(connection);
                    throw failure;
                }));

        assertSame(failure, thrown);
        assertEquals("1,2,3", items());
        assertTrue(database.connection().getAutoCommit());
    }

    @Test
    void aDatabaseErrorIsReportedWithTheMessageAndRolledBack() throws SQLException {
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> database.transaction("Failed to save the items", connection -> {
                    replaceWithTen(connection);
                    try (Statement statement = connection.createStatement()) {
                        statement.execute("INSERT INTO missing_table VALUES (1)");
                    }
                }));

        assertEquals("Failed to save the items", thrown.getMessage());
        assertInstanceOf(SQLException.class, thrown.getCause());
        assertEquals("1,2,3", items());
    }

    @Test
    void theResultOfTheWorkIsReturned() throws SQLException {
        int count = database.transactionResult("Failed", connection -> {
            replaceWithTen(connection);
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM item")) {
                rs.next();
                return rs.getInt(1);
            }
        });

        assertEquals(1, count);
        assertEquals("10", items());
    }

    @Test
    void laterWritesAreSavedAfterAFailedTransaction() throws SQLException {
        assertThrows(IllegalStateException.class, () -> database.transaction("Failed", connection -> {
            throw new IllegalStateException("stop");
        }));
        try (Statement statement = database.connection().createStatement()) {
            statement.executeUpdate("INSERT INTO item VALUES (4)");
        }

        assertEquals("1,2,3,4", items());
    }
}
