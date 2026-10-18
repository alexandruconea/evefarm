package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocationCacheDaoTest {

    private static final long JITA_4_4 = 60003760L;
    private static final long CITADEL = 1051964920941L;

    private Database database;
    private LocationCacheDao locations;

    @BeforeEach
    void setUp() {
        database = new Database(":memory:");
        MigrationRunner.run(database);
        locations = new LocationCacheDao(database);
    }

    private void savedAgo(long locationId, Duration age) throws SQLException {
        try (PreparedStatement ps = database.connection().prepareStatement(
                "UPDATE location_cache SET cached_at = ? WHERE location_id = ?")) {
            ps.setString(1, Instant.now().minus(age).toString());
            ps.setLong(2, locationId);
            ps.executeUpdate();
        }
    }

    @Test
    void anUnknownLocationIsAskedForAgainAfterADay() throws SQLException {
        locations.upsert(CITADEL, "Unknown Structure #" + CITADEL, "unknown", null);
        assertEquals(Optional.of("Unknown Structure #" + CITADEL), locations.findName(CITADEL));
        assertEquals(Set.of(CITADEL), locations.findExistingIds(List.of(CITADEL)));

        savedAgo(CITADEL, Duration.ofDays(2));

        assertEquals(Optional.empty(), locations.findName(CITADEL));
        assertEquals(Set.of(), locations.findExistingIds(List.of(CITADEL)));
    }

    @Test
    void aKnownLocationStaysCached() throws SQLException {
        locations.upsert(JITA_4_4, "Jita IV - Moon 4 - Caldari Navy Assembly Plant", "station", 30000142L);

        savedAgo(JITA_4_4, Duration.ofDays(400));

        assertEquals(Optional.of("Jita IV - Moon 4 - Caldari Navy Assembly Plant"), locations.findName(JITA_4_4));
        assertEquals(Set.of(JITA_4_4), locations.findExistingIds(List.of(JITA_4_4)));
    }
}
