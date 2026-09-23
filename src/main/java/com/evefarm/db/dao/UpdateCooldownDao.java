package com.evefarm.db.dao;

import com.evefarm.db.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

public final class UpdateCooldownDao {

    private final Database database;

    public UpdateCooldownDao(Database database) {
        this.database = database;
    }

    public Optional<Instant> findLastRefreshed(String category) {
        String sql = "SELECT last_refreshed_at FROM update_cooldown WHERE category = ?";
        synchronized (database) {
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, category);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(Instant.parse(rs.getString("last_refreshed_at")));
                    }
                    return Optional.empty();
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to read update cooldown for " + category, e);
            }
        }
    }

    public void markRefreshed(String category) {
        String sql = """
                INSERT INTO update_cooldown(category, last_refreshed_at)
                VALUES (?, ?)
                ON CONFLICT(category) DO UPDATE SET last_refreshed_at = excluded.last_refreshed_at
                """;
        synchronized (database) {
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, category);
                ps.setString(2, Instant.now().toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to mark update cooldown for " + category, e);
            }
        }
    }
}
