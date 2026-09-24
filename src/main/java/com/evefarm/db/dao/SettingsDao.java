package com.evefarm.db.dao;

import com.evefarm.db.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public final class SettingsDao {

    public static final String LAF_THEME = "laf_theme";
    public static final String DEFAULT_LAF_THEME = "system";
    public static final String PRICE_PROVIDER = "price_provider";
    public static final String JANICE_API_KEY = "janice_api_key";
    public static final String DEFAULT_PRICE_MODE = "default_price_mode";
    public static final String HIDDEN_TABS = "hidden_tabs";
    public static final String TAB_ORDER = "tab_order";
    public static final String WINDOW_WIDTH = "window_width";
    public static final String WINDOW_HEIGHT = "window_height";
    public static final String WINDOW_MAXIMIZED = "window_maximized";
    public static final String LP_STORE_TARGET_ISK_PER_LP = "lp_store_target_isk_per_lp";
    public static final String LP_STORE_FAVORITE_CORPORATION_ID = "lp_store_favorite_corporation_id";
    public static final String GAMELOG_DIRECTORY = "gamelog_directory";
    public static final String AGENTS_LAST_IMPORTED_AT = "agents_last_imported_at";
    public static final String NPC_CATALOG_IMPORTED_AT = "npc_catalog_imported_at";
    public static final String BACKUP_COPY_DIRECTORY = "backup_copy_directory";
    public static final String BELT_KILL_MILESTONE = "belt_kill_milestone";
    public static final String MAIN_CHARACTER_ID = "main_character_id";
    public static final String UPDATE_AUTO_CHECK = "update_auto_check";
    public static final String UPDATE_SKIPPED_VERSION = "update_skipped_version";
    public static final String LAST_RUN_VERSION = "last_run_version";

    private final Database database;

    public SettingsDao(Database database) {
        this.database = database;
    }

    public Optional<String> get(String key) {
        String sql = "SELECT value FROM app_settings WHERE key = ?";
        synchronized (database) {
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, key);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.ofNullable(rs.getString("value"));
                    }
                    return Optional.empty();
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to read setting " + key, e);
            }
        }
    }

    public String getOrDefault(String key, String defaultValue) {
        return get(key).orElse(defaultValue);
    }

    public void set(String key, String value) {
        String sql = """
                INSERT INTO app_settings(key, value) VALUES (?, ?)
                ON CONFLICT(key) DO UPDATE SET value = excluded.value
                """;
        synchronized (database) {
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, key);
                ps.setString(2, value);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to write setting " + key, e);
            }
        }
    }
}
