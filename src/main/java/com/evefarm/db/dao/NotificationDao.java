package com.evefarm.db.dao;

import com.evefarm.db.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public final class NotificationDao {

    private final Database database;

    public NotificationDao(Database database) {
        this.database = database;
    }

    public Set<String> sentKeys(String kind, long characterId) {
        synchronized (database) {
            try (PreparedStatement ps = database.connection().prepareStatement(
                    "SELECT item_key FROM notification_sent WHERE kind = ? AND character_id = ?")) {
                ps.setString(1, kind);
                ps.setLong(2, characterId);
                Set<String> keys = new LinkedHashSet<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        keys.add(rs.getString("item_key"));
                    }
                }
                return keys;
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to read the sent notifications", e);
            }
        }
    }

    public void replace(String kind, long characterId, Collection<String> sentKeys) {
        String now = Instant.now().toString();
        database.transaction("Failed to save the sent notifications", connection -> {
            Set<String> previous = sentKeys(kind, characterId);
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM notification_sent WHERE kind = ? AND character_id = ? AND item_key = ?")) {
                for (String key : previous) {
                    if (!sentKeys.contains(key)) {
                        delete.setString(1, kind);
                        delete.setLong(2, characterId);
                        delete.setString(3, key);
                        delete.addBatch();
                    }
                }
                delete.executeBatch();
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT OR IGNORE INTO notification_sent(kind, character_id, item_key, sent_at) VALUES (?, ?, ?, ?)")) {
                for (String key : sentKeys) {
                    if (!previous.contains(key)) {
                        insert.setString(1, kind);
                        insert.setLong(2, characterId);
                        insert.setString(3, key);
                        insert.setString(4, now);
                        insert.addBatch();
                    }
                }
                insert.executeBatch();
            }
        });
    }
}
