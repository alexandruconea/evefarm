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
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class AppPaths {

    private static final String EVE_STEAM_APP_ID = "8500";
    private static final Pattern STEAM_LIBRARY_PATH = Pattern.compile("\"path\"\\s+\"([^\"]+)\"");
    private static final List<String> STEAM_ROOTS = List.of(
            ".local/share/Steam",
            ".steam/steam",
            ".steam/debian-installation",
            ".var/app/com.valvesoftware.Steam/.local/share/Steam",
            "snap/steam/common/.local/share/Steam");
    private static final List<String> LUTRIS_GAME_CONFIGS = List.of(
            ".config/lutris/games",
            ".local/share/lutris/games",
            ".var/app/net.lutris.Lutris/config/lutris/games",
            ".var/app/net.lutris.Lutris/data/lutris/games");
    private static final Pattern LUTRIS_PREFIX = Pattern.compile(
            "(?m)^[ \\t]+prefix:[ \\t]*['\"]?([^'\"\\r\\n]+?)['\"]?[ \\t]*\\r?$");
    private static final List<String> WINE_PREFIX_FOLDERS = List.of(
            "Games",
            "Games/Heroic/Prefixes",
            "Games/Heroic/Prefixes/default",
            ".local/share/bottles/bottles",
            ".var/app/com.usebottles.bottles/data/bottles/bottles");
    private static final List<String> EVE_USER_FOLDERS = List.of(
            "AppData/Local/CCP/EVE",
            "AppData/Roaming/EVE Online",
            "Documents/EVE");

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
        return mostRecentlyUsed(gameLogDirectoryCandidates(Platform.current(), userHome(), eveUserDirectories(),
                windowsDocumentsFolder()));
    }

    public static Path gameLogDirectory(String configured) {
        return gameLogDirectory(configuredPath(configured), AppPaths::defaultGameLogDirectory);
    }

    static Path gameLogDirectory(Optional<Path> configured, Supplier<Path> detected) {
        if (configured.isPresent() && Files.isDirectory(configured.get())) {
            return configured.get();
        }
        Path found = detected.get();
        return configured.isPresent() && !Files.isDirectory(found) ? configured.get() : found;
    }

    static Path mostRecentlyUsed(List<Path> candidates) {
        return candidates.stream()
                .filter(Files::isDirectory)
                .max(Comparator.comparingLong(AppPaths::lastModified))
                .orElse(candidates.getFirst());
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    public static Optional<Path> configuredPath(String configured) {
        if (configured == null || configured.isBlank()) {
            return Optional.empty();
        }
        try {
            Path path = Path.of(configured.strip());
            return path.isAbsolute() ? Optional.of(path) : Optional.empty();
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
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
        List<Path> prefixes = new ArrayList<>();
        for (String root : STEAM_ROOTS) {
            for (Path library : steamLibraries(home.resolve(root))) {
                Path compatData = library.resolve("steamapps").resolve("compatdata");
                directories.add(realPathIfPresent(compatData.resolve(EVE_STEAM_APP_ID).resolve("pfx")
                        .resolve("drive_c").resolve("users").resolve("steamuser")));
                for (Path game : subdirectories(compatData)) {
                    prefixes.add(game.resolve("pfx"));
                }
            }
        }
        if (!userName.isBlank()) {
            directories.add(realPathIfPresent(home.resolve("Games").resolve("eve-online").resolve("drive_c")
                    .resolve("users").resolve(userName)));
            directories.add(realPathIfPresent(home.resolve(".wine").resolve("drive_c").resolve("users")
                    .resolve(userName)));
        }
        prefixes.add(home.resolve(".wine"));
        prefixes.addAll(lutrisPrefixes(home));
        for (String folder : WINE_PREFIX_FOLDERS) {
            prefixes.addAll(subdirectories(home.resolve(folder)));
        }
        for (Path prefix : prefixes) {
            for (Path user : subdirectories(prefix.resolve("drive_c").resolve("users"))) {
                if (!user.getFileName().toString().equals("Public") && hasEveFolder(user)) {
                    directories.add(realPathIfPresent(user));
                }
            }
        }
        return List.copyOf(directories);
    }

    private static List<Path> lutrisPrefixes(Path home) {
        List<Path> prefixes = new ArrayList<>();
        for (String folder : LUTRIS_GAME_CONFIGS) {
            for (Path config : filesEndingIn(home.resolve(folder), ".yml")) {
                try {
                    Matcher prefix = LUTRIS_PREFIX.matcher(Files.readString(config));
                    while (prefix.find()) {
                        String value = prefix.group(1);
                        Path path = value.startsWith("~/") ? home.resolve(value.substring(2)) : Path.of(value);
                        if (path.isAbsolute()) {
                            prefixes.add(path);
                        }
                    }
                } catch (IOException | InvalidPathException | SecurityException ignored) {
                }
            }
        }
        return prefixes;
    }

    private static boolean hasEveFolder(Path userDirectory) {
        return EVE_USER_FOLDERS.stream().anyMatch(folder -> Files.isDirectory(userDirectory.resolve(folder)));
    }

    private static List<Path> subdirectories(Path directory) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> children = Files.list(directory)) {
            return children.filter(Files::isDirectory).sorted().toList();
        } catch (IOException | SecurityException e) {
            return List.of();
        }
    }

    private static List<Path> filesEndingIn(Path directory, String suffix) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> children = Files.list(directory)) {
            return children.filter(path -> path.getFileName().toString().endsWith(suffix))
                    .filter(Files::isRegularFile)
                    .sorted()
                    .toList();
        } catch (IOException | SecurityException e) {
            return List.of();
        }
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
