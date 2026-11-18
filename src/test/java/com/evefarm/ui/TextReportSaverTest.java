package com.evefarm.ui;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextReportSaverTest {

    @Test
    void aNameWithoutTheExtensionGetsTxtAdded() {
        assertEquals(Path.of("reports", "spawns.txt"), TextReportSaver.withTxtExtension(new File("reports", "spawns")));
    }

    @Test
    void aNameThatAlreadyEndsInTxtIsKeptWhateverItsCase() {
        assertEquals(Path.of("reports", "Spawns.TXT"),
                TextReportSaver.withTxtExtension(new File("reports", "Spawns.TXT")));
    }
}
