package com.evefarm.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledOnOs(OS.WINDOWS)
class ApplyUpdateScriptTest {

    @TempDir
    Path temp;

    private Path script() throws IOException {
        Path script = temp.resolve("apply-update.ps1");
        try (InputStream in = ApplyUpdateScriptTest.class.getResourceAsStream("/update/apply-update.ps1")) {
            Files.copy(in, script, StandardCopyOption.REPLACE_EXISTING);
        }
        return script;
    }

    private int run(Path install, Path staged, Path log) throws Exception {
        Process finished = new ProcessBuilder("cmd.exe", "/c", "exit").start();
        finished.waitFor(10, TimeUnit.SECONDS);
        Process process = new ProcessBuilder(List.of("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
                "-File", script().toString(), "-ProcessId", String.valueOf(finished.pid()),
                "-InstallDir", install.toString(), "-StagedDir", staged.toString(), "-LogFile", log.toString(),
                "-NoLaunch"))
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
        assertTrue(process.waitFor(60, TimeUnit.SECONDS), "the script finished");
        return process.exitValue();
    }

    @Test
    void theNewVersionReplacesTheOldOneWhichIsKeptAside() throws Exception {
        Path install = Files.createDirectories(temp.resolve("EVEFarm"));
        Files.writeString(install.resolve("version.txt"), "old");
        Path staged = Files.createDirectories(temp.resolve("EVEFarm.update"));
        Files.writeString(staged.resolve("version.txt"), "new");
        Path log = temp.resolve("updater.log");

        run(install, staged, log);

        assertEquals("new", Files.readString(install.resolve("version.txt")));
        assertEquals("old", Files.readString(temp.resolve("EVEFarm.old").resolve("version.txt")));
        assertFalse(Files.exists(staged));
        assertTrue(Files.readString(log).contains("installed the new version"));
    }

    @Test
    void ifTheSwapFailsTheOldVersionIsPutBack() throws Exception {
        Path install = Files.createDirectories(temp.resolve("EVEFarm"));
        Files.writeString(install.resolve("version.txt"), "old");
        Path log = temp.resolve("updater.log");

        run(install, temp.resolve("missing-staged-folder"), log);

        assertEquals("old", Files.readString(install.resolve("version.txt")));
        assertTrue(Files.readString(log).contains("restored the previous version"));
    }
}
