package com.evefarm.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class AppInfo {

    public static final String NAME = "EVE Farm";
    public static final String REPOSITORY = "alexandruconea/evefarm";
    public static final String HOME_PAGE = "https://github.com/" + REPOSITORY;
    public static final String DEV_VERSION = "dev";

    private static final String VERSION = loadVersion();

    public static String version() {
        return VERSION;
    }

    public static boolean isDevBuild() {
        return DEV_VERSION.equals(VERSION);
    }

    public static String userAgent() {
        return "EVEFarm/" + VERSION + " (+" + HOME_PAGE + ")";
    }

    public static int compareVersions(String a, String b) {
        int[] left = parse(a);
        int[] right = parse(b);
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            int l = i < left.length ? left[i] : 0;
            int r = i < right.length ? right[i] : 0;
            if (l != r) {
                return Integer.compare(l, r);
            }
        }
        return 0;
    }

    static int[] parse(String version) {
        String cleaned = version == null ? "" : version.trim();
        if (cleaned.startsWith("v") || cleaned.startsWith("V")) {
            cleaned = cleaned.substring(1);
        }
        int suffix = cleaned.indexOf('-');
        if (suffix >= 0) {
            cleaned = cleaned.substring(0, suffix);
        }
        if (cleaned.isEmpty()) {
            return new int[0];
        }
        String[] parts = cleaned.split("\\.");
        int[] numbers = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                numbers[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                numbers[i] = 0;
            }
        }
        return numbers;
    }

    private static String loadVersion() {
        try (InputStream in = AppInfo.class.getResourceAsStream("/evefarm-version.properties")) {
            if (in == null) {
                return DEV_VERSION;
            }
            Properties properties = new Properties();
            properties.load(in);
            String version = properties.getProperty("version", "").trim();
            return version.isEmpty() || version.contains("${") ? DEV_VERSION : version;
        } catch (IOException e) {
            return DEV_VERSION;
        }
    }

    private AppInfo() {
    }
}
