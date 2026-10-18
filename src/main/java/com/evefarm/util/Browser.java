package com.evefarm.util;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class Browser {

    public static void open(String url) throws IOException {
        open(URI.create(url));
    }

    public static void open(URI uri) throws IOException {
        if (!isWebLink(uri)) {
            throw new IOException("Only web links can be opened: " + uri);
        }
        if (Platform.isLinux()) {
            Optional<Path> xdgOpen = AppPaths.findExecutable("xdg-open");
            if (xdgOpen.isPresent()) {
                linuxCommand(xdgOpen.get(), uri).start();
                return;
            }
        }
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(uri);
            return;
        }
        throw new IOException("No browser could be opened for " + uri);
    }

    static boolean isWebLink(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        return (scheme.equals("https") || scheme.equals("http")) && uri.getHost() != null;
    }

    static ProcessBuilder linuxCommand(Path xdgOpen, URI uri) {
        return new ProcessBuilder(List.of(xdgOpen.toString(), uri.toString()))
                .redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")))
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD);
    }

    private Browser() {
    }
}
