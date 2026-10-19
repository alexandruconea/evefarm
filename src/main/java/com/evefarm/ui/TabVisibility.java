package com.evefarm.ui;

import com.evefarm.db.dao.SettingsDao;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

final class TabVisibility {

    private static final Logger LOG = Logger.getLogger(TabVisibility.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static Set<String> readHidden(SettingsDao settingsDao) {
        return new LinkedHashSet<>(read(settingsDao, SettingsDao.HIDDEN_TABS));
    }

    static void writeHidden(SettingsDao settingsDao, Set<String> hiddenKeys) {
        write(settingsDao, SettingsDao.HIDDEN_TABS, hiddenKeys);
    }

    static List<String> readOrder(SettingsDao settingsDao) {
        return new ArrayList<>(read(settingsDao, SettingsDao.TAB_ORDER));
    }

    static void writeOrder(SettingsDao settingsDao, List<String> orderedKeys) {
        write(settingsDao, SettingsDao.TAB_ORDER, orderedKeys);
    }

    private static List<String> read(SettingsDao settingsDao, String key) {
        String json = settingsDao.getOrDefault(key, "");
        if (json.isBlank()) {
            return List.of();
        }
        try {
            return List.of(MAPPER.readValue(json, String[].class));
        } catch (Exception e) {
            return List.of();
        }
    }

    private static void write(SettingsDao settingsDao, String key, Collection<String> keys) {
        try {
            settingsDao.set(key, MAPPER.writeValueAsString(keys));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Couldn't save the tab layout", e);
        }
    }

    private TabVisibility() {
    }
}
