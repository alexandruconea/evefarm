package com.evefarm.update;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.db.dao.SettingsDao;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateServiceTest {

    private static final String RELEASE = """
            {
              "tag_name": "v1.4.0",
              "name": "EVE Farm 1.4.0",
              "body": "- Officer drops\\n- Backups",
              "html_url": "https://github.com/alexandruconea/evefarm/releases/tag/v1.4.0",
              "draft": false,
              "prerelease": false,
              "assets": [
                {"name": "EVEFarm-1.4.0-win64.zip.sha256", "browser_download_url": "https://example/sha", "size": 90},
                {"name": "EVEFarm-1.4.0-win64.zip", "browser_download_url": "https://example/zip", "size": 76600000},
                {"name": "EVEFarm-1.4.0-win64.zip.sig", "browser_download_url": "https://example/sig", "size": 89}
              ]
            }
            """;

    private HttpServer server;
    private SettingsDao settings;
    private volatile int status = 200;
    private volatile String body = RELEASE;

    @BeforeEach
    void setUp() throws Exception {
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        settings = new SettingsDao(database);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/latest", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private UpdateService service() {
        return new UpdateService(settings, "http://127.0.0.1:" + server.getAddress().getPort() + "/latest");
    }

    @Test
    void aNewerReleaseIsFoundWithItsSignedZip() throws Exception {
        ReleaseInfo release = service().findNewerRelease("1.3.0").orElseThrow();

        assertEquals("1.4.0", release.version());
        assertEquals("EVEFarm-1.4.0-win64.zip", release.zipName());
        assertEquals("https://example/zip", release.zipUrl());
        assertEquals("https://example/sig", release.signatureUrl());
        assertEquals(76_600_000, release.zipSize());
        assertTrue(release.notes().contains("Officer drops"));
    }

    @Test
    void theSameOrAnOlderVersionIsNotOffered() throws Exception {
        assertEquals(Optional.empty(), service().findNewerRelease("1.4.0"));
        assertEquals(Optional.empty(), service().findNewerRelease("1.10.0"));
    }

    @Test
    void noReleasesYetIsNotAnError() throws Exception {
        status = 404;
        body = "{\"message\":\"Not Found\"}";
        assertEquals(Optional.empty(), service().findNewerRelease("1.3.0"));
    }

    @Test
    void draftsAndPrereleasesAreIgnoredAndMissingAssetsAreReported() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        assertFalse(UpdateService.parseRelease(mapper.readTree(RELEASE.replace("\"draft\": false", "\"draft\": true")))
                .isPresent());
        assertFalse(UpdateService.parseRelease(
                mapper.readTree(RELEASE.replace("\"prerelease\": false", "\"prerelease\": true"))).isPresent());

        ReleaseInfo manual = UpdateService.parseRelease(mapper.readTree(
                "{\"tag_name\":\"1.3\",\"html_url\":\"https://x\",\"assets\":[{\"name\":\"EVEFarm-1.3.0-win64.zip\"}]}"))
                .orElseThrow();
        assertEquals("1.3", manual.version());
        assertNull(manual.zipUrl(), "a hand-made release without the expected zip name can't be installed automatically");
    }

    @Test
    void aSkippedVersionIsRemembered() throws Exception {
        UpdateService service = service();
        ReleaseInfo release = service.findNewerRelease("1.3.0").orElseThrow();
        assertFalse(service.isSkipped(release));
        service.skip(release);
        assertTrue(service.isSkipped(release));
        assertTrue(service.isAutoCheckEnabled(), "checking is on by default");
    }
}
