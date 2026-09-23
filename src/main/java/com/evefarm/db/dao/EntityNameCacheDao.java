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

public final class EntityNameCacheDao {

    private final Database database;

    public EntityNameCacheDao(Database database) {
        this.database = database;
    }

    public Optional<String> findName(long entityId) {
        String sql = "SELECT name FROM entity_name_cache WHERE entity_id = ?";
        synchronized (database) {
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, entityId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.ofNullable(rs.getString("name"));
                    }
                    return Optional.empty();
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to read entity_name_cache for " + entityId, e);
            }
        }
    }

    public Set<Long> findExistingIds(Collection<Long> entityIds) {
        List<Long> distinct = entityIds.stream().distinct().toList();
        Set<Long> result = new HashSet<>();
        int chunkSize = 500;
        synchronized (database) {
            Connection connection = database.connection();
            for (int start = 0; start < distinct.size(); start += chunkSize) {
                List<Long> chunk = distinct.subList(start, Math.min(start + chunkSize, distinct.size()));
                String placeholders = chunk.stream().map(id -> "?").collect(Collectors.joining(","));
                String sql = "SELECT entity_id FROM entity_name_cache WHERE entity_id IN (" + placeholders + ")";
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    int index = 1;
                    for (Long id : chunk) {
                        ps.setLong(index++, id);
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            result.add(rs.getLong("entity_id"));
                        }
                    }
                } catch (SQLException e) {
                    throw new IllegalStateException("Failed to check cached entity ids", e);
                }
            }
        }
        return result;
    }

    public void upsert(long entityId, String name, String category) {
        String sql = """
                INSERT INTO entity_name_cache(entity_id, name, category, cached_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(entity_id) DO UPDATE SET
                  name = excluded.name,
                  category = excluded.category,
                  cached_at = excluded.cached_at
                """;
        synchronized (database) {
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, entityId);
                ps.setString(2, name);
                ps.setString(3, category);
                ps.setString(4, Instant.now().toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to upsert entity_name_cache for " + entityId, e);
            }
        }
    }
}
