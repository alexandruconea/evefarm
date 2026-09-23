package com.evefarm.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public final class AppLogging {

    private static boolean initialized = false;

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        try {
            Path logDir = AppPaths.appDataDir().resolve("logs");
            Files.createDirectories(logDir);
            FileHandler fileHandler = new FileHandler(logDir.resolve("evefarm.log").toString(), 1_000_000, 3, true);
            fileHandler.setFormatter(new SimpleFormatter());
            fileHandler.setLevel(Level.INFO);
            Logger root = Logger.getLogger("");
            root.addHandler(fileHandler);
            root.setLevel(Level.INFO);
            initialized = true;
        } catch (IOException e) {
            System.err.println("Failed to initialize file logging: " + e.getMessage());
        }
    }

    private AppLogging() {
    }
}
