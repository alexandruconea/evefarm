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
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledOnOs(OS.LINUX)
class ApplyUpdateShellScriptTest {

    @TempDir
    Path temp;

    private Path script() throws IOException {
        Path script = Files.createDirectories(temp.resolve("work")).resolve("apply-update.sh");
        try (InputStream in = ApplyUpdateShellScriptTest.class.getResourceAsStream("/update/apply-update.sh")) {
            Files.copy(in, script, StandardCopyOption.REPLACE_EXISTING);
        }
        return script;
    }

    private static long finishedProcess() throws Exception {
        Process finished = new ProcessBuilder("true").start();
        assertTrue(finished.waitFor(10, TimeUnit.SECONDS));
        return finished.pid();
    }

    private void run(Path install, Path staged, Path log, long processId, int waitSeconds) throws Exception {
        Process process = new ProcessBuilder("/bin/sh", script().toString(), String.valueOf(processId),
                install.toString(), staged.toString(), log.toString(), "nolaunch", String.valueOf(waitSeconds))
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
        assertTrue(process.waitFor(60, TimeUnit.SECONDS), "the script finished");
    }

    private Path oldVersion(Path parent) throws IOException {
        Path install = Files.createDirectories(parent.resolve("EVEFarm"));
        Files.createDirectories(install.resolve("bin"));
        Files.writeString(install.resolve("version.txt"), "old");
        return install;
    }

    private Path newVersion(Path parent) throws IOException {
        Path staged = Files.createDirectories(parent.resolve("EVEFarm.update"));
        Files.writeString(staged.resolve("version.txt"), "new");
        return staged;
    }

    @Test
    void theNewVersionReplacesTheOldOneWhichIsKeptAside() throws Exception {
        Path install = oldVersion(temp);
        Path staged = newVersion(temp);
        Path log = temp.resolve("updater.log");

        run(install, staged, log, finishedProcess(), 120);

        assertEquals("new", Files.readString(install.resolve("version.txt")));
        assertEquals("old", Files.readString(temp.resolve("EVEFarm.old").resolve("version.txt")));
        assertFalse(Files.exists(staged));
        assertTrue(Files.readString(log).contains("installed the new version"));
        assertFalse(Files.exists(temp.resolve("update-failed.txt")));
    }

    @Test
    void foldersWithSpacesInTheirPathWork() throws Exception {
        Path parent = Files.createDirectories(temp.resolve("My Games").resolve("EVE tools"));
        Path install = oldVersion(parent);
        Path staged = newVersion(parent);
        Path log = temp.resolve("updater.log");

        run(install, staged, log, finishedProcess(), 120);

        assertEquals("new", Files.readString(install.resolve("version.txt")));
    }

    @Test
    void theFilesYouAddedToTheAppFolderSurviveTheUpdate() throws Exception {
        Path install = oldVersion(temp);
        Files.writeString(install.resolve("notes.txt"), "mine");
        Files.writeString(install.resolve(".hidden"), "hidden");
        Path staged = newVersion(temp);

        run(install, staged, temp.resolve("updater.log"), finishedProcess(), 120);

        assertEquals("new", Files.readString(install.resolve("version.txt")));
        assertEquals("mine", Files.readString(install.resolve("notes.txt")));
        assertEquals("hidden", Files.readString(install.resolve(".hidden")));
        assertFalse(Files.exists(temp.resolve("EVEFarm.old").resolve("notes.txt")));
    }

    @Test
    void ifTheNewVersionIsMissingTheOldOneIsLeftAlone() throws Exception {
        Path install = oldVersion(temp);
        Path log = temp.resolve("updater.log");

        run(install, temp.resolve("missing-staged-folder"), log, finishedProcess(), 120);

        assertEquals("old", Files.readString(install.resolve("version.txt")));
        assertTrue(Files.readString(log).contains("update failed"));
        String reason = UpdateInstaller.takeUpdateFailure(temp.resolve("update-failed.txt")).orElseThrow();
        assertTrue(reason.contains("missing-staged-folder"), "the app is told why: " + reason);
    }

    @Test
    void anAppThatDoesNotExitIsNotReplaced() throws Exception {
        Path install = oldVersion(temp);
        Path staged = newVersion(temp);
        Path log = temp.resolve("updater.log");
        Process stillRunning = new ProcessBuilder("sleep", "60").start();
        try {
            run(install, staged, log, stillRunning.pid(), 1);
        } finally {
            stillRunning.destroy();
            stillRunning.waitFor(10, TimeUnit.SECONDS);
        }

        assertEquals("old", Files.readString(install.resolve("version.txt")));
        assertTrue(Files.exists(staged));
        assertTrue(UpdateInstaller.takeUpdateFailure(temp.resolve("update-failed.txt")).orElseThrow()
                .contains("did not exit"));
    }
}
