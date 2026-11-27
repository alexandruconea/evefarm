package com.evefarm.ui;

import com.evefarm.db.dao.SettingsDao;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SettingsDialogTest {

    @Test
    void windowsAlsoOffersTheClassicLookAndTheLightThemeComesFirst() {
        assertEquals(List.of("Light", "Dark", "EVE", "Adwaita", "Windows Classic"),
                List.of(SettingsDialog.themeNames(true)));
        assertEquals(SettingsDialog.themeNames(true).length, SettingsDialog.themeKeys(true).size());
        assertEquals(SettingsDao.DEFAULT_LAF_THEME, SettingsDialog.themeKeys(true).getFirst());
        assertEquals(SettingsDao.WINDOWS_CLASSIC_THEME, SettingsDialog.themeKeys(true).getLast());
    }

    @Test
    void linuxHasNoWindowsClassic() {
        assertEquals(SettingsDialog.themeNames(false).length, SettingsDialog.themeKeys(false).size());
        assertFalse(SettingsDialog.themeKeys(false).contains(SettingsDao.WINDOWS_CLASSIC_THEME));
    }
}
