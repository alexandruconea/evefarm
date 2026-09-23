package com.evefarm.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppInfoTest {

    @Test
    void versionsCompareNumericallyNotAsText() {
        assertTrue(AppInfo.compareVersions("1.10.0", "1.9.3") > 0);
        assertTrue(AppInfo.compareVersions("v1.4.0", "1.3.0") > 0);
        assertEquals(0, AppInfo.compareVersions("1.4", "1.4.0"));
        assertTrue(AppInfo.compareVersions("1.4.0", "1.4.1") < 0);
        assertEquals(0, AppInfo.compareVersions("1.4.0-rc1", "1.4.0"));
    }

    @Test
    void theBuildStampsTheVersionFromThePom() {
        assertTrue(AppInfo.version().matches("\\d+\\.\\d+\\.\\d+"), "was: " + AppInfo.version());
        assertTrue(AppInfo.userAgent().startsWith("EVEFarm/" + AppInfo.version() + " (+https://github.com/"));
    }
}
