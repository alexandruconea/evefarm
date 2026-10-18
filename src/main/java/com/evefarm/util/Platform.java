package com.evefarm.util;

import java.util.Locale;

public enum Platform {
    WINDOWS,
    LINUX,
    MAC,
    OTHER;

    private static final Platform CURRENT = of(System.getProperty("os.name", ""));

    public static Platform current() {
        return CURRENT;
    }

    public static boolean isWindows() {
        return CURRENT == WINDOWS;
    }

    public static boolean isLinux() {
        return CURRENT == LINUX;
    }

    static Platform of(String osName) {
        String name = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        if (name.startsWith("windows")) {
            return WINDOWS;
        }
        if (name.startsWith("linux")) {
            return LINUX;
        }
        if (name.startsWith("mac")) {
            return MAC;
        }
        return OTHER;
    }
}
