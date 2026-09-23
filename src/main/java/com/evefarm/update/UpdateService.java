package com.evefarm.update;

import com.evefarm.db.dao.SettingsDao;
import com.evefarm.util.AppInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

public final class UpdateService {

    public static final String LATEST_RELEASE_API =
            "https://api.github.com/repos/" + AppInfo.REPOSITORY + "/releases/latest";

    private final SettingsDao settingsDao;
    private final String latestReleaseApi;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public UpdateService(SettingsDao settingsDao) {
        this(settingsDao, LATEST_RELEASE_API);
    }

    UpdateService(SettingsDao settingsDao, String latestReleaseApi) {
        this.settingsDao = settingsDao;
        this.latestReleaseApi = latestReleaseApi;
    }

    public Optional<ReleaseInfo> findNewerRelease(String currentVersion) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(latestReleaseApi))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", AppInfo.userAgent())
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while checking for updates", e);
        }
        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        if (response.statusCode() / 100 != 2) {
            throw new IOException("GitHub answered HTTP " + response.statusCode() + " when checking for updates");
        }
        return parseRelease(objectMapper.readTree(response.body()))
                .filter(release -> AppInfo.compareVersions(release.version(), currentVersion) > 0);
    }

    static Optional<ReleaseInfo> parseRelease(JsonNode release) {
        if (release.path("draft").asBoolean(false) || release.path("prerelease").asBoolean(false)) {
            return Optional.empty();
        }
        String tag = release.path("tag_name").asText("");
        String version = tag.startsWith("v") || tag.startsWith("V") ? tag.substring(1) : tag;
        if (version.isBlank()) {
            return Optional.empty();
        }
        String zipName = "EVEFarm-" + version + "-win64.zip";
        JsonNode zip = null;
        JsonNode signature = null;
        for (JsonNode asset : release.path("assets")) {
            String name = asset.path("name").asText("");
            if (name.equals(zipName)) {
                zip = asset;
            } else if (name.equals(zipName + ".sig")) {
                signature = asset;
            }
        }
        String title = release.path("name").asText("");
        return Optional.of(new ReleaseInfo(
                version,
                title.isBlank() ? AppInfo.NAME + " " + version : title,
                release.path("body").asText(""),
                release.path("html_url").asText(AppInfo.HOME_PAGE + "/releases"),
                zipName,
                zip == null ? null : zip.path("browser_download_url").asText(null),
                zip == null ? 0 : zip.path("size").asLong(0),
                signature == null ? null : signature.path("browser_download_url").asText(null)));
    }

    public boolean isAutoCheckEnabled() {
        return !"false".equals(settingsDao.getOrDefault(SettingsDao.UPDATE_AUTO_CHECK, "true"));
    }

    public boolean isSkipped(ReleaseInfo release) {
        return release.version().equals(settingsDao.getOrDefault(SettingsDao.UPDATE_SKIPPED_VERSION, ""));
    }

    public void skip(ReleaseInfo release) {
        settingsDao.set(SettingsDao.UPDATE_SKIPPED_VERSION, release.version());
    }
}
