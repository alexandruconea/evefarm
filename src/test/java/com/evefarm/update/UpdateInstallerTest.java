package com.evefarm.update;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.service.BackupRestoreService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateInstallerTest {

    private static final String VERSION = "1.4.0";
    private static final String ZIP_NAME = "EVEFarm-1.4.0-win64.zip";

    @TempDir
    Path temp;

    private HttpServer server;
    private final Map<String, byte[]> served = new HashMap<>();
    private String privateKey;
    private String publicKey;
    private BackupRestoreService backups;

    @BeforeEach
    void setUp() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        privateKey = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
        publicKey = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());

        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        backups = new BackupRestoreService(database, new SettingsDao(database));

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] bytes = served.get(exchange.getRequestURI().getPath());
            if (bytes == null) {
                exchange.sendResponseHeaders(404, -1);
            } else {
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private String base() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/repo/releases/download/";
    }

    private UpdateInstaller installer() {
        return new UpdateInstaller(backups, publicKey, base(), temp.resolve("work"), temp.resolve("backups"));
    }

    private Path appZip(Map<String, String> entries) throws IOException {
        Path zip = temp.resolve("build").resolve(ZIP_NAME);
        Files.createDirectories(zip.getParent());
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                out.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return zip;
    }

    private Path installedOldVersion() throws IOException {
        Path install = temp.resolve("games").resolve("EVEFarm");
        Files.createDirectories(install.resolve("app"));
        Files.writeString(install.resolve("EVEFarm.exe"), "old launcher");
        Files.writeString(install.resolve("app").resolve("evefarm.jar"), "old jar");
        return install;
    }

    private ReleaseInfo publish(Path zip, String signature) throws IOException {
        byte[] zipBytes = Files.readAllBytes(zip);
        served.put("/repo/releases/download/v1.4.0/" + ZIP_NAME, zipBytes);
        served.put("/repo/releases/download/v1.4.0/" + ZIP_NAME + ".sig", signature.getBytes(StandardCharsets.US_ASCII));
        return new ReleaseInfo(VERSION, "EVE Farm 1.4.0", "", "https://x", ZIP_NAME,
                base() + "v1.4.0/" + ZIP_NAME, zipBytes.length, base() + "v1.4.0/" + ZIP_NAME + ".sig");
    }

    private static final Map<String, String> NEW_APP = Map.of(
            "EVEFarm/EVEFarm.exe", "new launcher",
            "EVEFarm/app/evefarm.jar", "new jar",
            "EVEFarm/runtime/release", "JAVA_VERSION=\"25\"");

    @Test
    void aSignedReleaseIsStagedNextToTheInstallAndTheDataIsBackedUp() throws Exception {
        Path zip = appZip(NEW_APP);
        ReleaseInfo release = publish(zip, ReleaseSignature.sign(privateKey, VERSION, zip));
        Path install = installedOldVersion();

        UpdateInstaller.PreparedUpdate update = installer().prepare(release, install, (step, done, total) -> {
        });

        assertEquals(install.resolveSibling("EVEFarm.update"), update.stagedDir());
        assertEquals("new launcher", Files.readString(update.stagedDir().resolve("EVEFarm.exe")));
        assertEquals("new jar", Files.readString(update.stagedDir().resolve("app").resolve("evefarm.jar")));
        assertEquals("old launcher", Files.readString(install.resolve("EVEFarm.exe")), "the running copy is untouched");
        assertTrue(Files.exists(temp.resolve("backups").resolve("evefarm-before-update-1.4.0.db")));
    }

    @Test
    void aZipThatDoesNotMatchItsSignatureIsRejectedBeforeUnpacking() throws Exception {
        Path zip = appZip(NEW_APP);
        String signature = ReleaseSignature.sign(privateKey, VERSION, zip);
        Path tampered = appZip(Map.of("EVEFarm/EVEFarm.exe", "malware", "EVEFarm/app/evefarm.jar", "x"));
        ReleaseInfo release = publish(tampered, signature);
        Path install = installedOldVersion();

        assertThrows(SecurityException.class, () -> installer().prepare(release, install, (s, d, t) -> {
        }));
        assertFalse(Files.exists(install.resolveSibling("EVEFarm.update")));
    }

    @Test
    void aDownloadFromAnywhereElseIsRefused() throws Exception {
        Path zip = appZip(NEW_APP);
        ReleaseInfo release = publish(zip, ReleaseSignature.sign(privateKey, VERSION, zip));
        ReleaseInfo elsewhere = new ReleaseInfo(VERSION, "", "", "", ZIP_NAME, "https://evil.example/" + ZIP_NAME,
                release.zipSize(), release.signatureUrl());

        IOException error = assertThrows(IOException.class,
                () -> installer().prepare(elsewhere, installedOldVersion(), (s, d, t) -> {
                }));
        assertTrue(error.getMessage().startsWith("Refusing"));
    }

    @Test
    void entriesThatEscapeTheFolderAreRejected() throws Exception {
        Path zip = appZip(Map.of("EVEFarm/EVEFarm.exe", "x", "EVEFarm/../../evil.txt", "boom"));

        assertThrows(IOException.class, () -> UpdateInstaller.extractAppFolder(zip, temp.resolve("out")));
        assertFalse(Files.exists(temp.resolve("evil.txt")));
    }

    @Test
    void aFolderWithReadOnlyFilesIsDeletedCompletely() throws Exception {
        Path install = installedOldVersion();
        assertTrue(install.resolve("EVEFarm.exe").toFile().setReadOnly());

        UpdateInstaller.deleteRecursively(install);

        assertFalse(Files.exists(install), "jpackage marks EVEFarm.exe read-only; it must not block the cleanup");
    }

    @Test
    void anAppFolderNeedsTheLauncherAndTheJar() throws Exception {
        Path install = installedOldVersion();
        assertTrue(UpdateInstaller.isAppFolder(install));
        Files.delete(install.resolve("app").resolve("evefarm.jar"));
        assertFalse(UpdateInstaller.isAppFolder(install));
    }
}
