package com.evefarm.ui;

import com.evefarm.db.dao.SettingsDao;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class TabVisibility {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static Set<String> readHidden(SettingsDao settingsDao) {
        String json = settingsDao.getOrDefault(SettingsDao.HIDDEN_TABS, "");
        if (json.isBlank()) {
            return new LinkedHashSet<>();
        }
        try {
            String[] keys = MAPPER.readValue(json, String[].class);
            return new LinkedHashSet<>(List.of(keys));
        } catch (Exception e) {
            return new LinkedHashSet<>();
        }
    }

    static void writeHidden(SettingsDao settingsDao, Set<String> hiddenKeys) {
        try {
            settingsDao.set(SettingsDao.HIDDEN_TABS, MAPPER.writeValueAsString(hiddenKeys));
        } catch (Exception e) {
        }
    }

    static List<String> readOrder(SettingsDao settingsDao) {
        String json = settingsDao.getOrDefault(SettingsDao.TAB_ORDER, "");
        if (json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            String[] keys = MAPPER.readValue(json, String[].class);
            return new ArrayList<>(List.of(keys));
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    static void writeOrder(SettingsDao settingsDao, List<String> orderedKeys) {
        try {
            settingsDao.set(SettingsDao.TAB_ORDER, MAPPER.writeValueAsString(orderedKeys));
        } catch (Exception e) {
        }
    }

    private TabVisibility() {
    }
}
