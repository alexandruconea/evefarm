package com.evefarm.ui;

import com.evefarm.service.BackupRestoreService.Staged;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BackupRestoreFlowTest {

    private static final String STAGED = "Restore staged. The app will now close - reopen it to finish the restore.";

    @Test
    void theRestoreSaysWhereTheGamelogsWentAndWhoToAddAgain() {
        Path logs = Path.of("Gamelogs");

        assertEquals(STAGED, BackupRestoreFlow.stagedMessage(new Staged(List.of(), 0, logs)));
        assertEquals(STAGED + "\n\n1 Gamelog was put back in Gamelogs.",
                BackupRestoreFlow.stagedMessage(new Staged(List.of(), 1, logs)));
        assertEquals(STAGED + "\n\n1,493 Gamelogs were put back in Gamelogs.\n\nAfter it opens, add these "
                        + "characters again (File > Characters... > Add Character...): Barset. Their history is kept.",
                BackupRestoreFlow.stagedMessage(new Staged(List.of("Barset"), 1_493, logs)));
    }
}
