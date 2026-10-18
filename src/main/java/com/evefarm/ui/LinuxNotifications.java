package com.evefarm.ui;

import com.evefarm.util.AppPaths;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

final class LinuxNotifications {

    private static final Logger LOG = Logger.getLogger(LinuxNotifications.class.getName());

    private LinuxNotifications() {
    }

    static boolean send(String caption, String text) {
        Optional<Path> notifySend = AppPaths.findExecutable("notify-send");
        if (notifySend.isEmpty()) {
            return false;
        }
        try {
            command(notifySend.get(), caption, text, appIcon()).start();
            return true;
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Couldn't show the notification with notify-send", e);
            return false;
        }
    }

    static ProcessBuilder command(Path notifySend, String caption, String text, Optional<Path> icon) {
        List<String> command = new ArrayList<>(List.of(notifySend.toString(), "--app-name=EVE Farm"));
        icon.ifPresent(path -> command.add("--icon=" + path));
        command.addAll(List.of("--", caption, escapeMarkup(text)));
        return new ProcessBuilder(command)
                .redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")))
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD);
    }

    static String escapeMarkup(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static Optional<Path> appIcon() {
        String launcher = System.getProperty("jpackage.app-path");
        if (launcher == null || launcher.isBlank()) {
            return Optional.empty();
        }
        Path bin = Path.of(launcher).toAbsolutePath().getParent();
        if (bin == null || bin.getParent() == null) {
            return Optional.empty();
        }
        Path icon = bin.getParent().resolve("lib").resolve("EVEFarm.png");
        return Files.isRegularFile(icon) ? Optional.of(icon) : Optional.empty();
    }
}
