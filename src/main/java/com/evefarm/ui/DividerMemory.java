package com.evefarm.ui;

import com.evefarm.db.dao.SettingsDao;

import javax.swing.JSplitPane;
import javax.swing.Timer;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

final class DividerMemory {

    private static final int SAVE_DELAY_MILLIS = 500;

    static void install(JSplitPane split, SettingsDao settingsDao, String key, double defaultLocation) {
        int saved = settingsDao.getInt(key, 0);
        if (saved > 0) {
            split.setDividerLocation(saved);
        } else if (defaultLocation >= 1) {
            split.setDividerLocation((int) defaultLocation);
        } else {
            split.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    if (split.getWidth() > 0) {
                        split.removeComponentListener(this);
                        split.setDividerLocation(defaultLocation);
                    }
                }
            });
        }
        Timer saver = new Timer(SAVE_DELAY_MILLIS,
                e -> settingsDao.set(key, String.valueOf(split.getDividerLocation())));
        saver.setRepeats(false);
        split.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, e -> {
            if (split.getWidth() > 0) {
                saver.restart();
            }
        });
    }

    private DividerMemory() {
    }
}
