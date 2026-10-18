package com.evefarm.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledOnOs(OS.LINUX)
@EnabledIfEnvironmentVariable(named = "EVEFARM_LINUX_ARCHIVE", matches = ".+")
class RealLinuxArchiveTest {

    @TempDir
    Path temp;

    @Test
    void theReleaseArchiveUnpacksExactlyLikeTar() throws Exception {
        Path archive = Path.of(System.getenv("EVEFARM_LINUX_ARCHIVE"));
        Path unpackedByTar = Path.of(System.getenv("EVEFARM_LINUX_APP_DIR"));
        Path staged = temp.resolve("EVEFarm.update");

        AppArchive.extract(archive, staged);

        assertTrue(AppLayout.LINUX.isAppFolder(staged));
        assertTrue(Files.isExecutable(staged.resolve("bin").resolve("EVEFarm")));
        assertTrue(Files.isExecutable(staged.resolve("lib").resolve("runtime").resolve("lib").resolve("jspawnhelper")));
        Map<String, String> expected = describe(unpackedByTar);
        assertTrue(expected.size() > 100, "the archive holds the whole app: " + expected.size());
        assertEquals(expected, describe(staged));
    }

    private static Map<String, String> describe(Path root) throws IOException {
        Map<String, String> entries = new TreeMap<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.toList()) {
                String name = root.relativize(path).toString();
                if (Files.isSymbolicLink(path)) {
                    entries.put(name, "link to " + Files.readSymbolicLink(path));
                } else if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    entries.put(name, "folder");
                } else {
                    entries.put(name, Files.size(path) + " bytes" + (Files.isExecutable(path) ? ", executable" : ""));
                }
            }
        }
        return entries;
    }
}
