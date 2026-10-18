package com.evefarm.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppArchiveTest {

    @TempDir
    Path temp;

    private Path archive(TarFixture.Entry... entries) throws IOException {
        Path archive = temp.resolve("EVEFarm-1.4.0-linux-x64.tar.gz");
        Files.write(archive, TarFixture.tarGz(entries));
        return archive;
    }

    private Path out() {
        return temp.resolve("EVEFarm.update");
    }

    @Test
    void theAppFolderIsUnpackedWithoutItsTopFolder() throws Exception {
        Path archive = archive(
                TarFixture.directory("EVEFarm/"),
                TarFixture.directory("EVEFarm/bin/"),
                TarFixture.file("EVEFarm/bin/EVEFarm", "launcher", 0755),
                TarFixture.file("EVEFarm/lib/app/evefarm.jar", "jar", 0644),
                TarFixture.file("EVEFarm/lib/runtime/conf/empty.txt", "", 0644));

        AppArchive.extract(archive, out());

        assertEquals("launcher", Files.readString(out().resolve("bin").resolve("EVEFarm")));
        assertEquals("jar", Files.readString(out().resolve("lib").resolve("app").resolve("evefarm.jar")));
        assertEquals("", Files.readString(out().resolve("lib/runtime/conf/empty.txt")));
        assertTrue(AppLayout.LINUX.isAppFolder(out()));
    }

    @Test
    void filesBiggerThanOneBlockKeepAllTheirBytes() throws Exception {
        String content = "0123456789abcdef".repeat(700);
        AppArchive.extract(archive(TarFixture.file("EVEFarm/lib/app/evefarm.jar", content, 0644),
                TarFixture.file("EVEFarm/after.txt", "next", 0644)), out());

        assertEquals(content, Files.readString(out().resolve("lib").resolve("app").resolve("evefarm.jar")));
        assertEquals("next", Files.readString(out().resolve("after.txt")));
    }

    @Test
    void longNamesFromPaxAndGnuHeadersAreUsed() throws Exception {
        String deep = "EVEFarm/lib/runtime/legal/" + "x".repeat(90) + "/LICENSE";
        String gnu = "EVEFarm/lib/runtime/conf/" + "y".repeat(90) + "/settings.txt";
        AppArchive.extract(archive(TarFixture.file(deep, "pax", 0644), TarFixture.gnuLongName(gnu, "gnu")), out());

        assertEquals("pax", Files.readString(out().resolve(deep.substring("EVEFarm/".length()))));
        assertEquals("gnu", Files.readString(out().resolve(gnu.substring("EVEFarm/".length()))));
    }

    @Test
    void paxRecordsAreReadByTheirLength() throws Exception {
        byte[] records = concat(TarFixture.paxRecord("path", "EVEFarm/a b=c.txt"), TarFixture.paxRecord("size", "12"));

        assertEquals(Map.of("path", "EVEFarm/a b=c.txt", "size", "12"), AppArchive.paxRecords(records));
        assertThrows(IOException.class, () -> AppArchive.paxRecords("99 path=x\n".getBytes()));
    }

    @Test
    void entriesOutsideTheAppFolderOrEscapingItAreRejected() throws Exception {
        assertThrows(IOException.class, () -> AppArchive.extract(archive(
                TarFixture.file("other/evil.txt", "boom", 0644)), out()));
        assertThrows(IOException.class, () -> AppArchive.extract(archive(
                TarFixture.file("EVEFarm/../../evil.txt", "boom", 0644)), temp.resolve("second")));
        assertFalse(Files.exists(temp.resolve("evil.txt")));
        assertFalse(Files.exists(temp.getParent().resolve("evil.txt")));
    }

    @Test
    void linksToAbsolutePathsOrOutsideTheFolderAreRejected() throws Exception {
        assertThrows(IOException.class, () -> AppArchive.extract(archive(
                TarFixture.link("EVEFarm/lib/passwd", "/etc/passwd")), out()));
        assertThrows(IOException.class, () -> AppArchive.extract(archive(
                TarFixture.link("EVEFarm/lib/up", "../../outside")), temp.resolve("second")));
    }

    @Test
    void hardLinksAndDevicesAreRejected() throws Exception {
        IOException error = assertThrows(IOException.class, () -> AppArchive.extract(archive(
                TarFixture.file("EVEFarm/a.txt", "a", 0644),
                TarFixture.hardLink("EVEFarm/b.txt", "EVEFarm/a.txt")), out()));

        assertTrue(error.getMessage().contains("Unsupported"));
    }

    @Test
    void aDamagedHeaderIsRejected() throws Exception {
        byte[] tar = TarFixture.tar(TarFixture.file("EVEFarm/a.txt", "a", 0644));
        tar[10] ^= 0x01;
        Path archive = temp.resolve("damaged.tar.gz");
        Files.write(archive, TarFixture.gzip(tar));

        IOException error = assertThrows(IOException.class, () -> AppArchive.extract(archive, out()));
        assertTrue(error.getMessage().contains("checksum"));
    }

    @Test
    void aTruncatedArchiveIsRejected() throws Exception {
        byte[] tar = TarFixture.tar(TarFixture.file("EVEFarm/a.txt", "x".repeat(2000), 0644));
        Path archive = temp.resolve("short.tar.gz");
        Files.write(archive, TarFixture.gzip(Arrays.copyOf(tar, 1024)));

        assertThrows(IOException.class, () -> AppArchive.extract(archive, out()));
    }

    @Test
    void theLimitsStopOversizedOrCrowdedArchives() throws Exception {
        Path big = archive(TarFixture.file("EVEFarm/app.jar", "content larger than the limit", 0644));
        IOException tooLarge = assertThrows(IOException.class,
                () -> AppArchive.extractTarGz(big, out(), 8, 10));
        assertTrue(tooLarge.getMessage().contains("too large"));

        Path many = archive(TarFixture.file("EVEFarm/one.txt", "1", 0644),
                TarFixture.file("EVEFarm/two.txt", "2", 0644));
        IOException tooMany = assertThrows(IOException.class,
                () -> AppArchive.extractTarGz(many, temp.resolve("many"), 1024, 1));
        assertTrue(tooMany.getMessage().contains("too many files"));
    }

    @Test
    void theModeKeepsTheExecutableBitButNeverGivesOthersWriteAccess() {
        assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_READ,
                        PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OTHERS_READ,
                        PosixFilePermission.OTHERS_EXECUTE),
                AppArchive.permissions(0777));
        assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ), AppArchive.permissions(0644));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void onLinuxTheLauncherStaysExecutableAndLinksInsideTheFolderWork() throws Exception {
        AppArchive.extract(archive(
                TarFixture.file("EVEFarm/bin/EVEFarm", "launcher", 0755),
                TarFixture.file("EVEFarm/lib/runtime/legal/java.base/LICENSE", "license", 0644),
                TarFixture.link("EVEFarm/lib/runtime/legal/java.sql/LICENSE", "../java.base/LICENSE")), out());

        assertTrue(Files.isExecutable(out().resolve("bin").resolve("EVEFarm")));
        Path link = out().resolve("lib/runtime/legal/java.sql/LICENSE");
        assertTrue(Files.isSymbolicLink(link));
        assertEquals("license", Files.readString(link));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void aLinkThatOnlyLooksInsideTheFolderIsRejected() throws Exception {
        IOException error = assertThrows(IOException.class, () -> AppArchive.extract(archive(
                TarFixture.directory("EVEFarm/lib/"),
                TarFixture.link("EVEFarm/lib/up", ".."),
                TarFixture.link("EVEFarm/lib/up/escape", "../outside.txt")), out()));

        assertTrue(error.getMessage().contains("link"));
        assertFalse(Files.exists(temp.resolve("outside.txt")));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void aLinkThatLeavesTheFolderThroughAnotherLinkIsRejected() throws Exception {
        Files.writeString(temp.resolve("secret.txt"), "not part of the app");

        IOException error = assertThrows(IOException.class, () -> AppArchive.extract(archive(
                TarFixture.link("EVEFarm/here", "."),
                TarFixture.link("EVEFarm/secret", "here/../secret.txt")), out()));

        assertTrue(error.getMessage().contains("Unsafe link"));
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] both = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, both, first.length, second.length);
        return both;
    }
}
