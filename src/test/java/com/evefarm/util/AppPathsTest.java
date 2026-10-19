package com.evefarm.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void theSystemIsRecognisedFromItsName() {
        assertEquals(Platform.WINDOWS, Platform.of("Windows 11"));
        assertEquals(Platform.LINUX, Platform.of("Linux"));
        assertEquals(Platform.MAC, Platform.of("Mac OS X"));
        assertEquals(Platform.OTHER, Platform.of("FreeBSD"));
        assertEquals(Platform.OTHER, Platform.of(null));
    }

    @Test
    void onLinuxEveIsLookedForInEverySteamLibraryAndWinePrefix() throws IOException {
        Path home = windows.resolve("home");
        Path steam = home.resolve(".local/share/Steam");
        Path otherDrive = windows.resolve("SteamLibrary");
        Files.createDirectories(steam.resolve("steamapps"));
        Files.writeString(steam.resolve("steamapps").resolve("libraryfolders.vdf"), """
                "libraryfolders"
                {
                	"0"
                	{
                		"path"		"%s"
                	}
                	"1"
                	{
                		"path"		"%s"
                	}
                }
                """.formatted(steam.toString().replace("\\", "\\\\"), otherDrive.toString().replace("\\", "\\\\")));

        List<Path> directories = AppPaths.linuxEveUserDirectories(home, "alex");

        Path proton = Path.of("steamapps", "compatdata", "8500", "pfx", "drive_c", "users", "steamuser");
        assertEquals(List.of(
                steam.resolve(proton),
                otherDrive.resolve(proton),
                home.resolve(".steam/steam").resolve(proton),
                home.resolve(".steam/debian-installation").resolve(proton),
                home.resolve(".var/app/com.valvesoftware.Steam/.local/share/Steam").resolve(proton),
                home.resolve("snap/steam/common/.local/share/Steam").resolve(proton),
                home.resolve("Games/eve-online/drive_c/users/alex"),
                home.resolve(".wine/drive_c/users/alex")), directories);
    }

    @Test
    void onLinuxEveIsAlsoFoundInOtherWinePrefixes() throws IOException {
        Path home = windows.resolve("home");
        Path compatData = home.resolve(".local/share/Steam/steamapps/compatdata");
        Path shortcut = eveUser(compatData.resolve("3172381235/pfx"), "steamuser", "AppData/Local/CCP/EVE");
        Path lutris = eveUser(windows.resolve("Wine/eve"), "alex", "AppData/Roaming/EVE Online");
        Path renamed = eveUser(home.resolve("Games/eve-online"), "olduser", "AppData/Local/CCP/EVE");
        Path heroic = eveUser(home.resolve("Games/Heroic/Prefixes/default/EVE Online"), "alex",
                "AppData/Local/CCP/EVE");
        Path bottles = eveUser(home.resolve(".var/app/com.usebottles.bottles/data/bottles/bottles/EVE"), "alex",
                "Documents/EVE");
        eveUser(compatData.resolve("8500/pfx"), "Public", "Documents/EVE");
        Files.createDirectories(compatData.resolve("570/pfx/drive_c/users/steamuser/AppData/Local"));
        Path lutrisGames = Files.createDirectories(home.resolve(".config/lutris/games"));
        Files.writeString(lutrisGames.resolve("eve-online-1758000000.yml"), """
                game:
                  exe: %s/drive_c/EVE/eve-online.exe
                  prefix: '%s'
                system:
                  prefix_command: ''
                """.formatted(windows.resolve("Wine/eve"), windows.resolve("Wine/eve")));
        Files.writeString(lutrisGames.resolve("other.yml"), "game:\n  prefix: relative/prefix\n");

        List<Path> directories = AppPaths.linuxEveUserDirectories(home, "alex");

        assertEquals(List.of(shortcut, lutris, renamed, heroic, bottles),
                directories.stream().filter(Files::isDirectory).toList());
        assertTrue(directories.contains(compatData.resolve("8500/pfx/drive_c/users/steamuser")));
    }

    @Test
    void theGamelogsFolderUsedMostRecentlyIsChosen() throws IOException {
        Path missing = windows.resolve("missing/Gamelogs");
        Path old = Files.createDirectories(windows.resolve("old/Gamelogs"));
        Path current = Files.createDirectories(windows.resolve("current/Gamelogs"));
        Files.setLastModifiedTime(old, FileTime.fromMillis(1_700_000_000_000L));
        Files.setLastModifiedTime(current, FileTime.fromMillis(1_790_000_000_000L));

        assertEquals(current, AppPaths.mostRecentlyUsed(List.of(missing, old, current)));
        assertEquals(old, AppPaths.mostRecentlyUsed(List.of(missing, old)));
        assertEquals(missing, AppPaths.mostRecentlyUsed(List.of(missing, windows.resolve("also-missing"))));
    }

    @Test
    void aChosenGamelogsFolderThatIsGoneGivesWayToTheOneFound() throws IOException {
        Path chosen = Files.createDirectories(windows.resolve("chosen"));
        Path found = Files.createDirectories(windows.resolve("found"));
        Path gone = windows.resolve("gone");
        Path notFound = windows.resolve("not-found");

        assertEquals(chosen, AppPaths.gameLogDirectory(Optional.of(chosen), () -> found));
        assertEquals(found, AppPaths.gameLogDirectory(Optional.of(gone), () -> found));
        assertEquals(gone, AppPaths.gameLogDirectory(Optional.of(gone), () -> notFound));
        assertEquals(found, AppPaths.gameLogDirectory(Optional.empty(), () -> found));
    }

    private static Path eveUser(Path prefix, String user, String eveFolder) throws IOException {
        Path userDirectory = prefix.resolve("drive_c").resolve("users").resolve(user);
        Files.createDirectories(userDirectory.resolve(eveFolder));
        return userDirectory.toRealPath();
    }

    @Test
    void theGamelogsFolderIsLookedForInEachEveFolder() {
        Path home = windows.resolve("home");
        Path proton = home.resolve("proton/steamuser");
        Path wine = home.resolve("wine/alex");

        List<Path> linux = AppPaths.gameLogDirectoryCandidates(Platform.LINUX, home, List.of(proton, wine),
                Optional.empty());
        assertEquals(List.of(
                proton.resolve("Documents/EVE/logs/Gamelogs"),
                proton.resolve("My Documents/EVE/logs/Gamelogs"),
                wine.resolve("Documents/EVE/logs/Gamelogs"),
                wine.resolve("My Documents/EVE/logs/Gamelogs"),
                home.resolve("Documents/EVE/logs/Gamelogs")), linux);

        Path oneDrive = home.resolve("OneDrive/Documents");
        List<Path> windowsCandidates = AppPaths.gameLogDirectoryCandidates(Platform.WINDOWS, home, List.of(home),
                Optional.of(oneDrive));
        assertEquals(List.of(
                oneDrive.resolve("EVE/logs/Gamelogs"),
                home.resolve("Documents/EVE/logs/Gamelogs")), windowsCandidates);
    }

    @Test
    void programsAreFoundOnTheSearchPath() throws IOException {
        Path empty = Files.createDirectories(windows.resolve("empty"));
        Path bin = Files.createDirectories(windows.resolve("bin"));
        Path program = Files.createFile(bin.resolve("xdg-open"));
        if (program.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(program, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
        String searchPath = String.join(File.pathSeparator, "", "relative/bin", empty.toString(), bin.toString());

        assertEquals(Optional.of(program), AppPaths.findExecutable("xdg-open", searchPath));
        assertEquals(Optional.empty(), AppPaths.findExecutable("notify-send", searchPath));
        assertEquals(Optional.empty(), AppPaths.findExecutable("xdg-open", null));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void onLinuxTheDataFolderIsReadableOnlyByItsOwner() throws IOException {
        Path data = Files.createDirectories(windows.resolve(".evefarm"));
        Files.setPosixFilePermissions(data, PosixFilePermissions.fromString("rwxr-xr-x"));

        AppPaths.restrictToOwner(data);

        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(data)));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void onLinuxAWindowsFolderFromARestoredBackupIsNotUsed() {
        assertEquals(Optional.empty(), AppPaths.configuredPath("C:\\Users\\Alex\\Documents\\EVE\\logs\\Gamelogs"));
        assertEquals(Optional.empty(), AppPaths.configuredPath("relative/Gamelogs"));
        assertEquals(Optional.empty(), AppPaths.configuredPath(" "));
        assertEquals(Optional.empty(), AppPaths.configuredPath(null));
        assertEquals(Optional.of(Path.of("/home/alex/Gamelogs")), AppPaths.configuredPath(" /home/alex/Gamelogs "));
        assertEquals(AppPaths.defaultGameLogDirectory(), AppPaths.gameLogDirectory("C:\\Users\\Alex\\Gamelogs"));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void onWindowsALinuxFolderFromARestoredBackupIsNotUsed() {
        assertEquals(Optional.empty(), AppPaths.configuredPath("/home/alex/Gamelogs"));
        assertEquals(Optional.of(Path.of("C:\\Users\\Alex\\Gamelogs")), AppPaths.configuredPath("C:\\Users\\Alex\\Gamelogs"));
    }

    @Test
    void restrictingTheDataFolderKeepsItUsable() throws IOException {
        Path data = Files.createDirectories(windows.resolve("data"));

        AppPaths.restrictToOwner(data);

        assertTrue(Files.isDirectory(data));
    }
}
