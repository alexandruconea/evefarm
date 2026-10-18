package com.evefarm.update;

import com.evefarm.util.Platform;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

enum AppLayout {
    WINDOWS("EVEFarm.exe", "app/evefarm.jar", "win64.zip"),
    LINUX("bin/EVEFarm", "lib/app/evefarm.jar", "linux-x64.tar.gz");

    private static final Set<String> X64 = Set.of("amd64", "x86_64");

    private final String launcher;
    private final String jar;
    private final String assetSuffix;

    AppLayout(String launcher, String jar, String assetSuffix) {
        this.launcher = launcher;
        this.jar = jar;
        this.assetSuffix = assetSuffix;
    }

    static Optional<AppLayout> current() {
        return forPlatform(Platform.current(), System.getProperty("os.arch", ""));
    }

    static Optional<AppLayout> forPlatform(Platform platform, String arch) {
        return switch (platform) {
            case WINDOWS -> Optional.of(WINDOWS);
            case LINUX -> X64.contains(arch.toLowerCase(Locale.ROOT)) ? Optional.of(LINUX) : Optional.empty();
            default -> Optional.empty();
        };
    }

    String launcher() {
        return launcher;
    }

    String assetName(String version) {
        return "EVEFarm-" + version + "-" + assetSuffix;
    }

    boolean isAppFolder(Path dir) {
        return dir != null && Files.isRegularFile(dir.resolve(launcher)) && Files.isRegularFile(dir.resolve(jar));
    }

    Optional<Path> installDirectory(Path launcherPath) {
        Path dir = launcherPath.toAbsolutePath().getParent();
        if (this == LINUX && dir != null) {
            dir = dir.getParent();
        }
        return isAppFolder(dir) ? Optional.of(dir) : Optional.empty();
    }
}
