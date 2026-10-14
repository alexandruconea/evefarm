package com.evefarm.db;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrackerLpValueMigrationTest {

    private static final long MALPAIS = 2124165849L;
    private static final long SABINA = 2122998456L;
    private static final long CORPORATE_POLICE_FORCE = 1000043L;

    private Database database;

    @BeforeEach
    void setUp() throws SQLException {
        database = new Database(":memory:");
        MigrationRunner.run(database);
        try (Statement statement = database.connection().createStatement()) {
            statement.executeUpdate("INSERT INTO characters(character_id, character_name, added_at, enabled) VALUES "
                    + "(" + MALPAIS + ", 'Malpais Legate', '2026-09-01T00:00:00Z', 1), "
                    + "(" + SABINA + ", 'Sabina Preda', '2026-09-01T00:00:00Z', 1)");
            statement.executeUpdate("INSERT INTO app_settings(key, value) VALUES "
                    + "('lp_store_favorite_corporation_id', '" + CORPORATE_POLICE_FORCE + "'), "
                    + "('lp_store_target_isk_per_lp', '700.0')");
            statement.executeUpdate("INSERT INTO loyalty_point_history(character_id, corporation_id, loyalty_points, "
                    + "recorded_at) VALUES "
                    + "(" + MALPAIS + ", " + CORPORATE_POLICE_FORCE + ", 14000, '2026-09-29T09:58:20.654402700Z'), "
                    + "(" + MALPAIS + ", " + CORPORATE_POLICE_FORCE + ", 2224638, '2026-10-13T19:08:04.825705800Z'), "
                    + "(" + MALPAIS + ", " + CORPORATE_POLICE_FORCE + ", 2678483, '2026-10-13T21:20:08.9Z'), "
                    + "(" + MALPAIS + ", 1000419, 53000, '2026-09-29T09:58:20.654402700Z')");
        }
        snapshot(1, MALPAIS, "2026-09-24T14:23:12.746175300Z", 1.10e9, 146.60e9);
        snapshot(2, MALPAIS, "2026-10-13T19:42:55.797039200Z", 4.86e9, 139.97e9);
        snapshot(3, MALPAIS, "2026-10-14T05:15:59.451480Z", 9.61e9, 153.54e9);
        snapshot(4, SABINA, "2026-10-14T05:16:00.601329400Z", 0, 11.18e9);
    }

    private void snapshot(long id, long characterId, String capturedAt, double lpValue, double total)
            throws SQLException {
        try (PreparedStatement ps = database.connection().prepareStatement("INSERT INTO tracker_snapshot(id, "
                + "character_id, captured_at, lp_value, total_value) VALUES (?, ?, ?, ?, ?)")) {
            ps.setLong(1, id);
            ps.setLong(2, characterId);
            ps.setString(3, capturedAt);
            ps.setDouble(4, lpValue);
            ps.setDouble(5, total);
            ps.executeUpdate();
        }
    }

    private void migrate() throws IOException, SQLException {
        try (InputStream in = MigrationRunner.class.getResourceAsStream(
                "/db/migrations/V44__tracker_lp_value_at_target.sql");
             Statement statement = database.connection().createStatement()) {
            statement.executeUpdate(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private double[] values(long id) throws SQLException {
        try (Statement statement = database.connection().createStatement();
             ResultSet rs = statement.executeQuery("SELECT lp_value, total_value FROM tracker_snapshot WHERE id = "
                     + id)) {
            rs.next();
            return new double[]{rs.getDouble(1), rs.getDouble(2)};
        }
    }

    @Test
    void lpIsValuedAtTheTargetWithTheLpTheCharacterHadThen() throws Exception {
        migrate();

        assertEquals(2_224_638 * 700.0, values(2)[0], 0.5);
        assertEquals(139.97e9 - 4.86e9 + 2_224_638 * 700.0, values(2)[1], 0.5);
        assertEquals(2_678_483 * 700.0, values(3)[0], 0.5);
        assertEquals(153.54e9 - 9.61e9 + 2_678_483 * 700.0, values(3)[1], 0.5);
    }

    @Test
    void snapshotsFromBeforeTheLpHistoryUseTheFirstLpSaved() throws Exception {
        migrate();

        assertEquals(14_000 * 700.0, values(1)[0], 0.5);
        assertEquals(146.60e9 - 1.10e9 + 14_000 * 700.0, values(1)[1], 0.5);
    }

    @Test
    void charactersWithoutLpAtTheFavoriteCorporationKeepTheirSnapshots() throws Exception {
        migrate();

        assertEquals(0, values(4)[0]);
        assertEquals(11.18e9, values(4)[1], 0.5);
    }

    @Test
    void withoutAFavoriteCorporationNothingChanges() throws Exception {
        try (Statement statement = database.connection().createStatement()) {
            statement.executeUpdate("DELETE FROM app_settings WHERE key = 'lp_store_favorite_corporation_id'");
        }

        migrate();

        assertEquals(9.61e9, values(3)[0], 0.5);
        assertEquals(153.54e9, values(3)[1], 0.5);
    }
}
