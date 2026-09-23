package com.evefarm.db;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationRunnerTest {

    @Test
    void semicolonInsideQuotedStringDoesNotSplit() {
        String sql = "INSERT INTO t(name) VALUES ('a;b''c;d');\nSELECT 1;";
        List<String> statements = MigrationRunner.splitStatements(sql);
        assertEquals(2, statements.size());
        assertEquals("INSERT INTO t(name) VALUES ('a;b''c;d')", statements.get(0));
        assertEquals("\nSELECT 1", statements.get(1));
    }

    @Test
    void semicolonsInsideBeginEndBlockDoNotSplit() {
        String sql = "CREATE TRIGGER trg AFTER INSERT ON t BEGIN UPDATE t SET a=1; UPDATE t SET b=2; END;\nSELECT 2;";
        List<String> statements = MigrationRunner.splitStatements(sql);
        assertEquals(2, statements.size());
        assertEquals("CREATE TRIGGER trg AFTER INSERT ON t BEGIN UPDATE t SET a=1; UPDATE t SET b=2; END",
                statements.get(0));
        assertEquals("\nSELECT 2", statements.get(1));
    }

    @Test
    void semicolonInsideLineCommentDoesNotSplit() {
        String sql = "-- this comment has a ; semicolon in it\nSELECT 1;";
        List<String> statements = MigrationRunner.splitStatements(sql);
        assertEquals(1, statements.size());
        assertEquals("-- this comment has a ; semicolon in it\nSELECT 1", statements.get(0));
    }

    @Test
    void blankInputProducesNoStatements() {
        assertEquals(List.of(), MigrationRunner.splitStatements("   \n  "));
    }

    @Test
    void allBundledMigrationsApplyCleanlyAndAreIdempotent() throws SQLException {
        Database database = new Database(":memory:");

        MigrationRunner.run(database);
        MigrationRunner.run(database);

        Connection connection = database.connection();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM schema_version")) {
            assertTrue(rs.next());
            assertTrue(rs.getInt(1) > 0, "expected at least one migration to have been recorded as applied");
        }
    }
}
