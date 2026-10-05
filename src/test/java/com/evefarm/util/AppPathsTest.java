package com.evefarm.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppPathsTest {

    @TempDir
    Path windows;

    @Test
    void powerShellIsTakenFromTheWindowsFolderNotFromTheCurrentOne() throws IOException {
        Path powerShell = windows.resolve("System32").resolve("WindowsPowerShell").resolve("v1.0")
                .resolve("powershell.exe");
        Files.createDirectories(powerShell.getParent());
        Files.createFile(powerShell);

        assertEquals(powerShell.toString(), AppPaths.powerShell(windows.toString()));
    }

    @Test
    void withoutTheWindowsFolderTheNameIsUsedAsBefore() {
        assertEquals("powershell.exe", AppPaths.powerShell(null));
        assertEquals("powershell.exe", AppPaths.powerShell(" "));
        assertEquals("powershell.exe", AppPaths.powerShell(windows.toString()));
    }
}
