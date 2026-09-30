package com.evefarm.ui;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EveSettingsCopyDialogTest {

    private static final Map<Long, List<Long>> ACCOUNTS = Map.of(
            30089031L, List.of(2124165849L, 2124195413L),
            32231123L, List.of(2124083817L));

    @Test
    void aCharactersAccountIsFoundFromTheLauncherLogs() {
        assertEquals(30089031L, EveSettingsCopyDialog.accountOf(ACCOUNTS, 2124195413L));
        assertEquals(32231123L, EveSettingsCopyDialog.accountOf(ACCOUNTS, 2124083817L));
        assertNull(EveSettingsCopyDialog.accountOf(ACCOUNTS, 99L));
    }
}
