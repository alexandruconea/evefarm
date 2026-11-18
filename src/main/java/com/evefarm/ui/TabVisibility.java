package com.evefarm.ui;

import com.evefarm.db.dao.SettingsDao;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

    static List<String> orderGroups(List<String> savedOrder, Map<String, List<String>> pageKeysByGroup) {
        boolean savedBeforeSubTabs = savedOrder.stream().anyMatch(key -> !pageKeysByGroup.containsKey(key)
                && pageKeysByGroup.values().stream().anyMatch(pageKeys -> pageKeys.contains(key)));
        List<String> order = new ArrayList<>();
        if (!savedBeforeSubTabs) {
            savedOrder.stream().filter(pageKeysByGroup::containsKey).distinct().forEach(order::add);
        }
        String previous = null;
        for (String group : pageKeysByGroup.keySet()) {
            if (!order.contains(group)) {
                order.add(previous == null ? 0 : order.indexOf(previous) + 1, group);
            }
            previous = group;
        }
        return order;
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
