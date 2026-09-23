package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.model.TypeInfo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class TypeCacheDao {

    private final Database database;

    public TypeCacheDao(Database database) {
        this.database = database;
    }

    public Optional<TypeInfo> find(int typeId) {
        String sql = "SELECT * FROM type_cache WHERE type_id = ?";
        synchronized (database) {
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, typeId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(new TypeInfo(
                            rs.getInt("type_id"),
                            rs.getString("name"),
                            rs.getString("group_name"),
                            rs.getString("category_name"),
                            rs.getDouble("volume")
                    ));
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to read type_cache for " + typeId, e);
            }
        }
    }

    public Set<Integer> findExistingIds(Collection<Integer> typeIds) {
        List<Integer> distinct = typeIds.stream().distinct().toList();
        Set<Integer> result = new HashSet<>();
        int chunkSize = 500;
        synchronized (database) {
            Connection connection = database.connection();
            for (int start = 0; start < distinct.size(); start += chunkSize) {
                List<Integer> chunk = distinct.subList(start, Math.min(start + chunkSize, distinct.size()));
                String placeholders = chunk.stream().map(id -> "?").collect(Collectors.joining(","));
                String sql = "SELECT type_id FROM type_cache WHERE type_id IN (" + placeholders + ")";
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    int index = 1;
                    for (Integer id : chunk) {
                        ps.setInt(index++, id);
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            result.add(rs.getInt("type_id"));
                        }
                    }
                } catch (SQLException e) {
                    throw new IllegalStateException("Failed to check cached type ids", e);
                }
            }
        }
        return result;
    }

    public void upsert(TypeInfo type, Integer groupId, Integer categoryId) {
        String sql = """
                INSERT INTO type_cache(type_id, name, group_id, group_name, category_id, category_name, volume, cached_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(type_id) DO UPDATE SET
                  name = excluded.name,
                  group_id = excluded.group_id,
                  group_name = excluded.group_name,
                  category_id = excluded.category_id,
                  category_name = excluded.category_name,
                  volume = excluded.volume,
                  cached_at = excluded.cached_at
                """;
        synchronized (database) {
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, type.typeId());
                ps.setString(2, type.name());
                if (groupId != null) {
                    ps.setInt(3, groupId);
                } else {
                    ps.setNull(3, java.sql.Types.INTEGER);
                }
                ps.setString(4, type.groupName());
                if (categoryId != null) {
                    ps.setInt(5, categoryId);
                } else {
                    ps.setNull(5, java.sql.Types.INTEGER);
                }
                ps.setString(6, type.categoryName());
                ps.setDouble(7, type.volume());
                ps.setString(8, Instant.now().toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to upsert type_cache for " + type.typeId(), e);
            }
        }
    }

    public List<Integer> listAllTypeIds() {
        List<Integer> result = new ArrayList<>();
        synchronized (database) {
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement("SELECT type_id FROM type_cache");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getInt("type_id"));
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to list type_cache type ids", e);
            }
        }
        return result;
    }
}
