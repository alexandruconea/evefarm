package com.evefarm.db.dao;

import com.evefarm.db.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class DailyVolumeDao {

    private final Database database;

    public DailyVolumeDao(Database database) {
        this.database = database;
    }

    public Map<Integer, Double> findFetchedSince(long regionId, Collection<Integer> typeIds, Instant since) {
        Set<Integer> wanted = Set.copyOf(typeIds);
        synchronized (database) {
            try (PreparedStatement ps = database.connection().prepareStatement(
                    "SELECT type_id, daily_volume, fetched_at FROM market_daily_volume WHERE region_id = ?")) {
                ps.setLong(1, regionId);
                Map<Integer, Double> result = new HashMap<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int typeId = rs.getInt("type_id");
                        if (wanted.contains(typeId) && !Instant.parse(rs.getString("fetched_at")).isBefore(since)) {
                            result.put(typeId, rs.getDouble("daily_volume"));
                        }
                    }
                }
                return result;
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to read the daily market volumes", e);
            }
        }
    }

    public void save(long regionId, Map<Integer, Double> dailyVolumes, Instant fetchedAt) {
        database.transaction("Failed to save the daily market volumes", connection -> {
            try (PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO market_daily_volume(region_id, type_id, daily_volume, fetched_at)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT(region_id, type_id) DO UPDATE SET
                      daily_volume = excluded.daily_volume,
                      fetched_at = excluded.fetched_at
                    """)) {
                for (Map.Entry<Integer, Double> entry : dailyVolumes.entrySet()) {
                    ps.setLong(1, regionId);
                    ps.setInt(2, entry.getKey());
                    ps.setDouble(3, entry.getValue());
                    ps.setString(4, fetchedAt.toString());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });
    }
}
