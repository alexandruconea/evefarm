package com.evefarm.update;

import com.evefarm.service.BackupRestoreService;
import com.evefarm.util.AppInfo;
import com.evefarm.util.AppPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class UpdateInstaller {

    public static final String TRUSTED_DOWNLOAD_PREFIX =
            "https://github.com/" + AppInfo.REPOSITORY + "/releases/download/";
    private static final long MAX_DOWNLOAD_BYTES = 1024L * 1024 * 1024;
    private static final long MAX_EXTRACTED_BYTES = 2L * 1024 * 1024 * 1024;
    private static final int MAX_ZIP_ENTRIES = 10_000;
    private static final String APP_FOLDER = "EVEFarm";
    private static final String LAUNCHER = "EVEFarm.exe";

    public interface Progress {
        void report(String step, long done, long total);
    }

    public record PreparedUpdate(String version, Path installDir, Path stagedDir) {
    }

    private final BackupRestoreService backupRestoreService;
    private final String publicKey;
    private final String trustedDownloadPrefix;
    private final Path workDir;
    private final Path backupDir;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public UpdateInstaller(BackupRestoreService backupRestoreService) {
        this(backupRestoreService, ReleaseSignature.RELEASE_PUBLIC_KEY, TRUSTED_DOWNLOAD_PREFIX,
                AppPaths.appDataDir().resolve("updates"), AppPaths.backupDir());
    }

    UpdateInstaller(BackupRestoreService backupRestoreService, String publicKey, String trustedDownloadPrefix,
                    Path workDir, Path backupDir) {
        this.backupRestoreService = backupRestoreService;
        this.publicKey = publicKey;
        this.trustedDownloadPrefix = trustedDownloadPrefix;
        this.workDir = workDir;
        this.backupDir = backupDir;
    }

    public static Optional<Path> installDirectory() {
        String launcher = System.getProperty("jpackage.app-path");
        if (launcher == null || launcher.isBlank()) {
            return Optional.empty();
        }
        Path installDir = Path.of(launcher).toAbsolutePath().getParent();
        return isAppFolder(installDir) ? Optional.of(installDir) : Optional.empty();
    }

    static boolean isAppFolder(Path dir) {
        return dir != null && Files.isRegularFile(dir.resolve(LAUNCHER))
                && Files.isRegularFile(dir.resolve("app").resolve("evefarm.jar"));
    }

    public PreparedUpdate prepare(ReleaseInfo release, Path installDir, Progress progress) throws IOException {
        if (release.zipUrl() == null || release.signatureUrl() == null) {
            throw new IOException("Release " + release.version() + " has no signed Windows zip attached.");
        }
        Path downloadDir = workDir.resolve(release.version());
        Files.createDirectories(downloadDir);
        Path zip = downloadDir.resolve(release.zipName());

        progress.report("Downloading " + release.zipName(), 0, release.zipSize());
        download(release.zipUrl(), zip, release.zipSize(), progress);
        String signature = downloadText(release.signatureUrl());

        progress.report("Checking the signature", 0, 0);
        String sha256 = ReleaseSignature.sha256Hex(zip);
        if (!ReleaseSignature.verify(publicKey, release.version(), release.zipName(), sha256, signature)) {
            Files.deleteIfExists(zip);
            throw new SecurityException("The downloaded update isn't signed by the EVE Farm release key - "
                    + "it was not installed.");
        }

        progress.report("Unpacking", 0, 0);
        Path staged = installDir.resolveSibling(installDir.getFileName() + ".update");
        deleteRecursively(staged);
        try {
            extractAppFolder(zip, staged);
        } catch (IOException | RuntimeException e) {
            deleteRecursively(staged);
            throw e;
        }
        if (!isAppFolder(staged)) {
            deleteRecursively(staged);
            throw new IOException("The update doesn't contain " + LAUNCHER + " - it was not installed.");
        }

        progress.report("Backing up your data", 0, 0);
        backupRestoreService.backupTo(backupDir.resolve("evefarm-before-update-" + release.version() + ".db"));

        Files.deleteIfExists(zip);
        return new PreparedUpdate(release.version(), installDir, staged);
    }

    public void launchSwap(PreparedUpdate update) throws IOException {
        Path script = workDir.resolve("apply-update.ps1");
        Files.createDirectories(workDir);
        try (InputStream in = UpdateInstaller.class.getResourceAsStream("/update/apply-update.ps1")) {
            if (in == null) {
                throw new IOException("The update script is missing from this build.");
            }
            Files.copy(in, script, StandardCopyOption.REPLACE_EXISTING);
        }
        Path log = AppPaths.appDataDir().resolve("logs").resolve("updater.log");
        Files.createDirectories(log.getParent());
        swapProcess(script, update, log, ProcessHandle.current().pid()).start();
    }

    static ProcessBuilder swapProcess(Path script, PreparedUpdate update, Path log, long processId) {
        return new ProcessBuilder(List.of("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
                "-WindowStyle", "Hidden", "-File", script.toString(),
                "-ProcessId", String.valueOf(processId),
                "-InstallDir", update.installDir().toString(),
                "-StagedDir", update.stagedDir().toString(),
                "-LogFile", log.toString()))
                .directory(script.getParent().toFile())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD);
    }

    public static void cleanUpAfterUpdate() {
        installDirectory().ifPresent(installDir -> {
            deleteQuietly(installDir.resolveSibling(installDir.getFileName() + ".old"));
            deleteQuietly(installDir.resolveSibling(installDir.getFileName() + ".update"));
        });
        deleteQuietly(AppPaths.appDataDir().resolve("updates"));
    }

    private void download(String url, Path target, long expectedSize, Progress progress) throws IOException {
        try (InputStream in = open(url); OutputStream out = Files.newOutputStream(target)) {
            byte[] buffer = new byte[64 * 1024];
            long total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > MAX_DOWNLOAD_BYTES || (expectedSize > 0 && total > expectedSize)) {
                    throw new IOException("The download is larger than the release says - stopped.");
                }
                out.write(buffer, 0, read);
                progress.report("Downloading", total, expectedSize);
            }
        }
    }

    private String downloadText(String url) throws IOException {
        try (InputStream in = open(url)) {
            byte[] bytes = in.readNBytes(4096);
            return new String(bytes, StandardCharsets.US_ASCII).trim();
        }
    }

    private InputStream open(String url) throws IOException {
        if (!url.startsWith(trustedDownloadPrefix)) {
            throw new IOException("Refusing to download an update from " + url);
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(10))
                .header("User-Agent", AppInfo.userAgent())
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() / 100 != 2) {
                response.body().close();
                throw new IOException("Download failed: HTTP " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while downloading the update", e);
        }
    }

    static void extractAppFolder(Path zip, Path target) throws IOException {
        extractAppFolder(zip, target, MAX_EXTRACTED_BYTES, MAX_ZIP_ENTRIES);
    }

    static void extractAppFolder(Path zip, Path target, long maxExtractedBytes, int maxEntries) throws IOException {
        if (maxExtractedBytes < 1 || maxEntries < 1) {
            throw new IllegalArgumentException("Extraction limits must be positive");
        }
        Path root = target.toAbsolutePath().normalize();
        Files.createDirectories(root);
        long extractedBytes = 0;
        int entryCount = 0;
        byte[] buffer = new byte[64 * 1024];
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (++entryCount > maxEntries) {
                    throw new IOException("The update contains too many files - stopped.");
                }
                String name = entry.getName().replace('\\', '/');
                if (!name.startsWith(APP_FOLDER + "/")) {
                    throw new IOException("Unexpected file in the update: " + name);
                }
                String relative = name.substring(APP_FOLDER.length() + 1);
                if (relative.isEmpty()) {
                    continue;
                }
                Path destination = root.resolve(relative).normalize();
                if (!destination.startsWith(root)) {
                    throw new IOException("Unsafe path in the update: " + name);
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    try (OutputStream out = Files.newOutputStream(destination)) {
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            extractedBytes += read;
                            if (extractedBytes > maxExtractedBytes) {
                                throw new IOException("The unpacked update is too large - stopped.");
                            }
                            out.write(buffer, 0, read);
                        }
                    }
                }
                in.closeEntry();
            }
        }
    }

    static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                try {
                    Files.delete(path);
                } catch (AccessDeniedException e) {
                    if (!path.toFile().setWritable(true)) {
                        throw e;
                    }
                    Files.delete(path);
                }
            }
        }
    }

    private static void deleteQuietly(Path dir) {
        try {
            deleteRecursively(dir);
        } catch (IOException ignored) {
        }
    }
}
