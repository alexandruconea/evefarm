package com.evefarm.db.dao;

import com.evefarm.db.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class LocationCacheDao {

    private final Database database;

    public LocationCacheDao(Database database) {
        this.database = database;
    }

    public Optional<String> findName(long locationId) {
        String sql = "SELECT name FROM location_cache WHERE location_id = ?";
        synchronized (database) {
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, locationId);
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
            for (int start = 0; start < distinct.size(); start += chunkSize) {
                List<Long> chunk = distinct.subList(start, Math.min(start + chunkSize, distinct.size()));
                String placeholders = chunk.stream().map(id -> "?").collect(Collectors.joining(","));
                String sql = "SELECT location_id FROM location_cache WHERE location_id IN (" + placeholders + ")";
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    int index = 1;
                    for (Long id : chunk) {
                        ps.setLong(index++, id);
                    }
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
                    ps.setNull(4, java.sql.Types.INTEGER);
                }
                ps.setString(5, Instant.now().toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to upsert location_cache for " + locationId, e);
            }
        }
    }
}
