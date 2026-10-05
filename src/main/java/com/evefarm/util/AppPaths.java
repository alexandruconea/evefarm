package com.evefarm.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class AppPaths {

    public static Path appDataDir() {
        String home = System.getProperty("user.home");
        return Paths.get(home, ".evefarm");
    }

    public static Path databaseFile() {
        return appDataDir().resolve("evefarm.db");
    }

    public static Path iconCacheDir() {
        return appDataDir().resolve("icon-cache");
    }

    public static Path defaultGameLogDirectory() {
        String home = System.getProperty("user.home");
        return Paths.get(home, "Documents", "EVE", "logs", "Gamelogs");
    }

    public static String powerShell() {
        return powerShell(System.getenv("SystemRoot"));
    }

    static String powerShell(String systemRoot) {
        if (systemRoot != null && !systemRoot.isBlank()) {
            Path path = Paths.get(systemRoot, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
            if (Files.isRegularFile(path)) {
                return path.toString();
            }
        }
        return "powershell.exe";
    }

    public static Path backupDir() {
        return appDataDir().resolve("backups");
    }

    public static Path pendingRestoreFile() {
        return appDataDir().resolve("pending-restore.db");
    }

    private AppPaths() {
    }
}
