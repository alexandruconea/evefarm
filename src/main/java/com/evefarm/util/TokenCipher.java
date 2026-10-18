package com.evefarm.util;

import java.nio.charset.StandardCharsets;
import java.util.List;

public final class TokenCipher {

    static final byte[] ENTROPY = "EVEFarm.TokenCipher.v1".getBytes(StandardCharsets.UTF_8);
    private static final List<String> PREFIXES = List.of(DpapiProtection.PREFIX, KeyringProtection.PREFIX);
    private static final Protection PROTECTION = forPlatform(Platform.current());

    interface Protection {
        String prefix();

        boolean isAvailable();

        String unavailableMessage();

        String protect(String plaintext);

        String unprotect(String body);

        default void check() {
            if (!isAvailable()) {
                throw new IllegalStateException(unavailableMessage());
            }
        }

        default void preload() {
        }
    }

    static Protection forPlatform(Platform platform) {
        return switch (platform) {
            case WINDOWS -> new DpapiProtection();
            case LINUX -> new KeyringProtection(new LinuxKeyring());
            default -> new Unavailable();
        };
    }

    public static String encrypt(String plaintext) {
        return encrypt(PROTECTION, plaintext);
    }

    static String encrypt(Protection protection, String plaintext) {
        if (plaintext == null) {
            return null;
        }
        protection.check();
        return protection.prefix() + protection.protect(plaintext);
    }

    public static String decrypt(String stored) {
        return decrypt(PROTECTION, stored);
    }

    static String decrypt(Protection protection, String stored) {
        if (stored == null || stored.isEmpty()) {
            return stored;
        }
        if (!protection.prefix().isEmpty() && stored.startsWith(protection.prefix())) {
            if (!protection.isAvailable()) {
                throw new IllegalStateException(protection.unavailableMessage());
            }
            return protection.unprotect(stored.substring(protection.prefix().length()));
        }
        if (isEncrypted(stored)) {
            throw new IllegalStateException("A saved login was protected on another computer or system and "
                    + "can't be read here - add the character again (File > Characters... > Add Character...).");
        }
        if (!protection.isAvailable()) {
            throw new IllegalStateException(protection.unavailableMessage());
        }
        return stored;
    }

    public static boolean isEncrypted(String stored) {
        return stored != null && PREFIXES.stream().anyMatch(stored::startsWith);
    }

    public static boolean isProtectionAvailable() {
        return PROTECTION.isAvailable();
    }

    public static String unavailableMessage() {
        return PROTECTION.unavailableMessage();
    }

    public static void checkAvailable() {
        PROTECTION.check();
    }

    public static void preload() {
        PROTECTION.preload();
    }

    private static final class Unavailable implements Protection {

        @Override
        public String prefix() {
            return "";
        }

        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public String unavailableMessage() {
            return "EVE Farm can keep your EVE logins safe only on Windows and Linux, so it won't store or read "
                    + "them on this system.";
        }

        @Override
        public String protect(String plaintext) {
            throw new IllegalStateException(unavailableMessage());
        }

        @Override
        public String unprotect(String body) {
            throw new IllegalStateException(unavailableMessage());
        }
    }

    private TokenCipher() {
    }
}
