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
        return run(install, staged, log, temp);
    }

    private int run(Path install, Path staged, Path log, Path workingDirectory, String... extra) throws Exception {
        Process finished = new ProcessBuilder("cmd.exe", "/c", "exit").start();
        finished.waitFor(10, TimeUnit.SECONDS);
        List<String> command = new java.util.ArrayList<>(List.of("powershell.exe", "-NoProfile", "-ExecutionPolicy",
                "Bypass", "-File", script().toString(), "-ProcessId", String.valueOf(finished.pid()),
                "-InstallDir", install.toString(), "-StagedDir", staged.toString(), "-LogFile", log.toString(),
                "-NoLaunch"));
        command.addAll(List.of(extra));
        Process process = new ProcessBuilder(command)
                .directory(workingDirectory.toFile())
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
        assertFalse(Files.exists(temp.resolve("update-failed.txt")));
    }

    @Test
    void theSwapWorksWhenTheScriptStartsInsideTheAppFolder() throws Exception {
        Path install = Files.createDirectories(temp.resolve("EVEFarm"));
        Files.writeString(install.resolve("version.txt"), "old");
        Path staged = Files.createDirectories(temp.resolve("EVEFarm.update"));
        Files.writeString(staged.resolve("version.txt"), "new");
        Path log = temp.resolve("updater.log");

        run(install, staged, log, install);

        assertEquals("new", Files.readString(install.resolve("version.txt")),
                "an app started by double-click runs inside its own folder; that must not block the update");
        assertTrue(Files.readString(log).contains("installed the new version"));
    }

    @Test
    void theFilesYouAddedToTheAppFolderSurviveTheUpdate() throws Exception {
        Path install = Files.createDirectories(temp.resolve("EVEFarm"));
        Files.writeString(install.resolve("version.txt"), "old");
        Files.writeString(install.resolve("EVEFarm.exe - Shortcut.lnk"), "shortcut");
        Path staged = Files.createDirectories(temp.resolve("EVEFarm.update"));
        Files.writeString(staged.resolve("version.txt"), "new");

        run(install, staged, temp.resolve("updater.log"));

        assertEquals("new", Files.readString(install.resolve("version.txt")));
        assertEquals("shortcut", Files.readString(install.resolve("EVEFarm.exe - Shortcut.lnk")));
        assertFalse(Files.exists(temp.resolve("EVEFarm.old").resolve("EVEFarm.exe - Shortcut.lnk")));
    }

    @Test
    void anAppFolderOpenInAnotherProgramIsUpdatedFileByFile() throws Exception {
        Path install = Files.createDirectories(temp.resolve("EVEFarm"));
        Files.writeString(install.resolve("version.txt"), "old");
        Files.createDirectories(install.resolve("app"));
        Files.writeString(install.resolve("app").resolve("evefarm.jar"), "old jar");
        Files.writeString(install.resolve("notes.txt"), "mine");
        Path staged = Files.createDirectories(temp.resolve("EVEFarm.update"));
        Files.writeString(staged.resolve("version.txt"), "new");
        Files.createDirectories(staged.resolve("app"));
        Files.writeString(staged.resolve("app").resolve("evefarm.jar"), "new jar");
        Path log = temp.resolve("updater.log");
        Process blocker = new ProcessBuilder("cmd.exe", "/c", "ping -n 60 127.0.0.1 > nul")
                .directory(install.toFile()).start();
        try {
            run(install, staged, log, temp, "-MoveAttempts", "2");
        } finally {
            blocker.descendants().forEach(ProcessHandle::destroy);
            blocker.destroy();
            blocker.waitFor(10, TimeUnit.SECONDS);
        }

        assertEquals("new", Files.readString(install.resolve("version.txt")));
        assertEquals("new jar", Files.readString(install.resolve("app").resolve("evefarm.jar")));
        assertEquals("mine", Files.readString(install.resolve("notes.txt")));
        assertEquals("old jar", Files.readString(temp.resolve("EVEFarm.old").resolve("app").resolve("evefarm.jar")));
        String written = Files.readString(log);
        assertTrue(written.contains("replacing the files inside it"), written);
        assertTrue(written.contains("installed the new version"), written);
        assertFalse(Files.exists(staged));
    }

    @Test
    void ifTheNewVersionIsMissingTheOldOneIsLeftAlone() throws Exception {
        Path install = Files.createDirectories(temp.resolve("EVEFarm"));
        Files.writeString(install.resolve("version.txt"), "old");
        Path log = temp.resolve("updater.log");

        run(install, temp.resolve("missing-staged-folder"), log);

        assertEquals("old", Files.readString(install.resolve("version.txt")));
        assertTrue(Files.readString(log).contains("update failed"));
        String reason = UpdateInstaller.takeUpdateFailure(temp.resolve("update-failed.txt")).orElseThrow();
        assertTrue(reason.contains("missing-staged-folder"), "the app is told why: " + reason);
        assertFalse(Files.exists(temp.resolve("update-failed.txt")), "the reason is shown only once");
    }
}
