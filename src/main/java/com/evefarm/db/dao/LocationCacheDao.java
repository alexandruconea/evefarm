package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.JdbcUtil;
import com.evefarm.util.Chunks;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class LocationCacheDao {

    static final Duration UNKNOWN_RETRY = Duration.ofDays(1);
    private static final String STILL_VALID = " AND NOT (location_type = 'unknown' AND cached_at < ?)";

    private final Database database;

    public LocationCacheDao(Database database) {
        this.database = database;
    }

    private static String unknownRetryAfter() {
        return Instant.now().minus(UNKNOWN_RETRY).toString();
    }

    public Optional<String> findName(long locationId) {
        String sql = "SELECT name FROM location_cache WHERE location_id = ?" + STILL_VALID;
        synchronized (database) {
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, locationId);
                ps.setString(2, unknownRetryAfter());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.ofNullable(rs.getString("name"));
                    }
                    return Optional.empty();
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to read location_cache for " + locationId, e);
            }
        }
    }

    public Set<Long> findExistingIds(Collection<Long> locationIds) {
        List<Long> distinct = locationIds.stream().distinct().toList();
        Set<Long> result = new HashSet<>();
        int chunkSize = 500;
        synchronized (database) {
            Connection connection = database.connection();
            for (List<Long> chunk : Chunks.of(distinct, chunkSize)) {
                String placeholders = JdbcUtil.placeholders(chunk.size());
                String sql = "SELECT location_id FROM location_cache WHERE location_id IN (" + placeholders + ")"
                        + STILL_VALID;
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    int index = 1;
                    for (Long id : chunk) {
                        ps.setLong(index++, id);
                    }
                    ps.setString(index, unknownRetryAfter());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            result.add(rs.getLong("location_id"));
                        }
                    }
                } catch (SQLException e) {
                    throw new IllegalStateException("Failed to check cached location ids", e);
                }
            }
        }
        return result;
    }

    public void upsert(long locationId, String name, String locationType, Long systemId) {
        String sql = """
                INSERT INTO location_cache(location_id, name, location_type, system_id, cached_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(location_id) DO UPDATE SET
                  name = excluded.name,
                  location_type = excluded.location_type,
                  system_id = excluded.system_id,
                  cached_at = excluded.cached_at
                """;
        synchronized (database) {
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, locationId);
                ps.setString(2, name);
                ps.setString(3, locationType);
                if (systemId != null) {
                    ps.setLong(4, systemId);
                } else {
                    ps.setNull(4, Types.INTEGER);
                }
                ps.setString(5, Instant.now().toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to upsert location_cache for " + locationId, e);
            }
        }
    }
}
