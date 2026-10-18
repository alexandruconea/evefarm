package com.evefarm.util;

import com.sun.jna.platform.win32.KnownFolders;
import com.sun.jna.platform.win32.Shell32Util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AppPaths {

    private static final String EVE_STEAM_APP_ID = "8500";
    private static final Pattern STEAM_LIBRARY_PATH = Pattern.compile("\"path\"\\s+\"([^\"]+)\"");
    private static final List<String> STEAM_ROOTS = List.of(
            ".local/share/Steam",
            ".steam/steam",
            ".steam/debian-installation",
            ".var/app/com.valvesoftware.Steam/.local/share/Steam");

    public static Path appDataDir() {
        String home = System.getProperty("user.home");
        return Paths.get(home, ".evefarm");
    }

    public static Path createAppDataDir() throws IOException {
        Path dir = Files.createDirectories(appDataDir());
        restrictToOwner(dir);
        return dir;
    }

    static void restrictToOwner(Path dir) throws IOException {
        if (dir.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwx------"));
        }
    }

    public static Path databaseFile() {
        return appDataDir().resolve("evefarm.db");
    }

    public static Path iconCacheDir() {
        return appDataDir().resolve("icon-cache");
    }

    public static Path defaultGameLogDirectory() {
        List<Path> candidates = gameLogDirectoryCandidates(Platform.current(), userHome(), eveUserDirectories(),
                windowsDocumentsFolder());
        return candidates.stream().filter(Files::isDirectory).findFirst().orElse(candidates.getFirst());
    }

    static List<Path> gameLogDirectoryCandidates(Platform platform, Path home, List<Path> eveUserDirectories,
                                                 Optional<Path> windowsDocuments) {
        Set<Path> candidates = new LinkedHashSet<>();
        if (platform == Platform.WINDOWS) {
            windowsDocuments.ifPresent(documents -> candidates.add(gameLogs(documents)));
            candidates.add(gameLogs(home.resolve("Documents")));
            candidates.add(gameLogs(home.resolve("OneDrive").resolve("Documents")));
        } else {
            for (Path userDirectory : eveUserDirectories) {
                candidates.add(gameLogs(userDirectory.resolve("Documents")));
                candidates.add(gameLogs(userDirectory.resolve("My Documents")));
            }
            candidates.add(gameLogs(home.resolve("Documents")));
        }
        return List.copyOf(candidates);
    }

    private static Path gameLogs(Path documents) {
        return documents.resolve("EVE").resolve("logs").resolve("Gamelogs");
    }

    public static List<Path> eveUserDirectories() {
        if (Platform.current() != Platform.LINUX) {
            return List.of(userHome());
        }
        return linuxEveUserDirectories(userHome(), System.getProperty("user.name", ""));
    }

    static List<Path> linuxEveUserDirectories(Path home, String userName) {
        Set<Path> directories = new LinkedHashSet<>();
        for (String root : STEAM_ROOTS) {
            for (Path library : steamLibraries(home.resolve(root))) {
                directories.add(realPathIfPresent(library.resolve("steamapps").resolve("compatdata")
                        .resolve(EVE_STEAM_APP_ID).resolve("pfx").resolve("drive_c").resolve("users")
                        .resolve("steamuser")));
            }
        }
        if (!userName.isBlank()) {
            directories.add(home.resolve("Games").resolve("eve-online").resolve("drive_c").resolve("users")
                    .resolve(userName));
            directories.add(home.resolve(".wine").resolve("drive_c").resolve("users").resolve(userName));
        }
        return List.copyOf(directories);
    }

    static List<Path> steamLibraries(Path steamRoot) {
        List<Path> libraries = new ArrayList<>();
        libraries.add(steamRoot);
        Path libraryFolders = steamRoot.resolve("steamapps").resolve("libraryfolders.vdf");
        if (!Files.isRegularFile(libraryFolders)) {
            return libraries;
        }
        try {
            Matcher library = STEAM_LIBRARY_PATH.matcher(Files.readString(libraryFolders));
            while (library.find()) {
                Path path = Path.of(library.group(1).replace("\\\\", "\\"));
                if (path.isAbsolute() && !libraries.contains(path)) {
                    libraries.add(path);
                }
            }
        } catch (IOException | InvalidPathException e) {
            return libraries;
        }
        return libraries;
    }

    private static Path realPathIfPresent(Path path) {
        try {
            return Files.exists(path) ? path.toRealPath() : path;
        } catch (IOException e) {
            return path;
        }
    }

    private static Optional<Path> windowsDocumentsFolder() {
        if (!Platform.isWindows()) {
            return Optional.empty();
        }
        try {
            String documents = Shell32Util.getKnownFolderPath(KnownFolders.FOLDERID_Documents);
            return documents == null || documents.isBlank() ? Optional.empty() : Optional.of(Path.of(documents));
        } catch (RuntimeException | LinkageError e) {
            return Optional.empty();
        }
    }

    private static Path userHome() {
        return Paths.get(System.getProperty("user.home", ""));
    }

    public static Optional<Path> findExecutable(String name) {
        return findExecutable(name, System.getenv("PATH"));
    }

    static Optional<Path> findExecutable(String name, String searchPath) {
        if (searchPath == null || searchPath.isBlank()) {
            return Optional.empty();
        }
        for (String directory : searchPath.split(Pattern.quote(File.pathSeparator))) {
            Optional<Path> executable = executableIn(directory, name);
            if (executable.isPresent()) {
                return executable;
            }
        }
        return Optional.empty();
    }

    private static Optional<Path> executableIn(String directory, String name) {
        if (directory.isBlank()) {
            return Optional.empty();
        }
        try {
            Path candidate = Path.of(directory).resolve(name);
            return candidate.isAbsolute() && Files.isRegularFile(candidate) && Files.isExecutable(candidate)
                    ? Optional.of(candidate) : Optional.empty();
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
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
