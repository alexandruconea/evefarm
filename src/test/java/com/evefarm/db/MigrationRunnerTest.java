package com.evefarm.db;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class MigrationRunnerTest {

    private static final Map<Integer, String> RELEASED_CHECKSUMS = Map.ofEntries(
            Map.entry(1, "39a52edd3cc640eb21127034aeba6379111f08b768455be616246d4e596f2e71"),
            Map.entry(2, "963fd0c4edf9053bdfb76dd742f7a02f234142effe2d57c215e2473bf7192129"),
            Map.entry(3, "f1c4753aa7f7de92a2414b7927ab1ab356f206824a5383bea37f522b9102054b"),
            Map.entry(4, "11d6a92b80047b79888d3e0e8a0c67bc131d4dbbb6d234d3a8dbfb3fe3333d64"),
            Map.entry(5, "57bb17f5b89a5040eb9fe298433b1f581bc34d95b89b5f1af5e6a040362b96d9"),
            Map.entry(6, "01d1843fa39fc4dca905199f9dba550146a9eb8c158f15e34f2a34a2ff7d8237"),
            Map.entry(7, "7e309b26013f6e3363625c8d3501e6b6ce6d96d78e9a92ee8cd7356166b2748f"),
            Map.entry(8, "41781bf51f9c3862413af2acd5fc8689fc5b7013a5f760969d3a49ed00a10bf1"),
            Map.entry(9, "35d184793ba5a858f74373b5b9e437810b1f9775909eb9df4b5123907e7426b6"),
            Map.entry(10, "b053f97a2b20ede031617528d0944828416b5f7b73f67f32edc45839a50bf931"),
            Map.entry(11, "cd70521961d1ad6fa60bf266b3b88f7b4730669b760020d5f0beb86c273683e4"),
            Map.entry(12, "4c02875ddfa60556f72abb562577a8273fdc5cea7d9ae65c9c2f10f10da42271"),
            Map.entry(13, "6d7b51797f6e7537368d915bab5ddf50acf34b0d61d7872bd6b4f5249c916a73"),
            Map.entry(14, "1c133abedaa13b9ebe5ee9ae7572abc89544d5bb982b2987e583f5c848b33e22"),
            Map.entry(15, "b859f96d84d60e456b8cea54de9c5550b1267f31ed02f3999ee04101301819c4"),
            Map.entry(16, "dbabe815cfe350cd90b705694e796301a3b1d680985a1659cc8fac244823b412"),
            Map.entry(17, "116da5ba10fbde3a623a0bfbe3696e45e838f74e7d577e0d60615442d52b1186"),
            Map.entry(18, "2b525b2b4609604ca5a60bfc4281a1cc58f0ea98ec9fc484408d6558e546f02f"),
            Map.entry(19, "91c1a047e255f62bebf55d344231ab435da7b5561191f45edf8975396a5f2b71"),
            Map.entry(20, "1634f9c0f75b492020fbbd55922dcae64aadf4df37da94be76d4eec4b557678a"),
            Map.entry(21, "de34a0bb24d46955bcbcec0007fb972a99c211f540f0ac4cae1efc994725a098"),
            Map.entry(22, "18b5bb9b4f53c37eb61596caec7135d86e52983fb797b95912bee4319e061222"),
            Map.entry(23, "40ad8b3a4395196bf51075e9f15dc6926c2dee6506e491512d929c1203bf4d41"),
            Map.entry(24, "2622ef4b11558e44ad84ee72dfa2cb910ff7ab1d1dbf07bddaa5e821bc89f7fa"),
            Map.entry(25, "355523e73eab6ff067964361326754f952a295e1eade7b88eaee4b663d08f29d"));

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

    @Test
    void migrationListContainsEverySqlResource() throws Exception {
        try (var files = Files.list(Path.of("src/main/resources/db/migrations"))) {
            List<String> resources = files
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .map(path -> "db/migrations/" + path.getFileName())
                    .sorted((left, right) -> Integer.compare(version(left), version(right)))
                    .toList();
            assertEquals(resources, MigrationRunner.migrationPaths(),
                    "every migration resource must be registered in order");
        }
    }

    @Test
    void releasedMigrationsAreNeverEdited() throws IOException {
        for (String path : MigrationRunner.migrationPaths()) {
            int version = version(path);
            String actual = MigrationRunner.checksum(resource(path));
            String released = RELEASED_CHECKSUMS.get(version);
            if (released == null) {
                fail("V" + version + " is new: add Map.entry(" + version + ", \"" + actual
                        + "\") to RELEASED_CHECKSUMS");
            }
            assertEquals(released, actual, path + " was edited after it shipped; put the change in a new migration");
        }
    }

    @Test
    void checksumIgnoresLineEndingsAndByteOrderMark() {
        String unix = "CREATE TABLE t (id INTEGER);\nSELECT 1;\n";

        assertEquals(MigrationRunner.checksum(unix), MigrationRunner.checksum(unix.replace("\n", "\r\n")));
        assertEquals(MigrationRunner.checksum(unix), MigrationRunner.checksum("﻿" + unix));
    }

    @Test
    void editedAppliedMigrationIsReportedWithoutBlockingStartup() throws SQLException {
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        try (Statement statement = database.connection().createStatement()) {
            statement.executeUpdate("UPDATE schema_version SET checksum = 'tampered' WHERE version = 1");
        }
        List<LogRecord> records = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Logger logger = Logger.getLogger(MigrationRunner.class.getName());
        logger.addHandler(handler);
        try {
            assertDoesNotThrow(() -> MigrationRunner.run(database));
        } finally {
            logger.removeHandler(handler);
        }

        assertTrue(records.stream().anyMatch(record -> record.getLevel() == Level.SEVERE
                && record.getMessage().contains("checksum mismatch")));
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = MigrationRunner.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("Missing " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static int version(String path) {
        String name = Path.of(path).getFileName().toString();
        return Integer.parseInt(name.substring(1, name.indexOf("__")));
    }
}
