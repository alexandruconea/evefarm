package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SettingsDaoTest {

    @Test
    void numbersFallBackToTheDefaultWhenMissingOrBroken() {
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        SettingsDao settings = new SettingsDao(database);
        settings.set("volume", " 80 ");
        settings.set("rate", "72.5");
        settings.set("broken", "abc");
        settings.set("nan", "NaN");

        assertEquals(80, settings.getInt("volume", 100));
        assertEquals(100, settings.getInt("missing", 100));
        assertEquals(100, settings.getInt("broken", 100));
        assertEquals(72.5, settings.getDouble("rate", 0));
        assertEquals(500, settings.getDouble("broken", 500));
        assertEquals(500, settings.getDouble("nan", 500));
        assertEquals(500, settings.getDouble("missing", 500));
    }
}
