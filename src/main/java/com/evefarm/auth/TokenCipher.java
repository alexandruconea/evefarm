package com.evefarm.auth;

import com.sun.jna.platform.win32.Crypt32Util;
import com.sun.jna.platform.win32.WinCrypt;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class TokenCipher {

    private static final Logger LOG = Logger.getLogger(TokenCipher.class.getName());
    private static final String PREFIX = "dpapi:";
    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows");
    private static final byte[] ENTROPY = "EVEFarm.TokenCipher.v1".getBytes(StandardCharsets.UTF_8);
    private static final AtomicBoolean WARNED_NON_WINDOWS = new AtomicBoolean(false);

    public static String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        if (!WINDOWS) {
            if (WARNED_NON_WINDOWS.compareAndSet(false, true)) {
                LOG.warning("Not running on Windows - DPAPI is unavailable, so tokens are stored "
                        + "UNENCRYPTED on disk for the rest of this session.");
            }
            return plaintext;
        }
        try {
            byte[] protectedBytes = Crypt32Util.cryptProtectData(
                    plaintext.getBytes(StandardCharsets.UTF_8), ENTROPY,
                    WinCrypt.CRYPTPROTECT_UI_FORBIDDEN, null, null);
            return PREFIX + Base64.getEncoder().encodeToString(protectedBytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt token with DPAPI - refusing to store "
                    + "it unprotected", e);
        }
    }

    public static String decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            return stored;
        }
        byte[] protectedBytes = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
        try {
            byte[] plainBytes = Crypt32Util.cryptUnprotectData(
                    protectedBytes, ENTROPY, WinCrypt.CRYPTPROTECT_UI_FORBIDDEN, null);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception withEntropy) {
            try {
                byte[] plainBytes = Crypt32Util.cryptUnprotectData(protectedBytes);
                return new String(plainBytes, StandardCharsets.UTF_8);
            } catch (Exception withoutEntropy) {
                throw new IllegalStateException("Failed to decrypt a stored token; it may have been "
                        + "created on a different Windows user account or machine", withoutEntropy);
            }
        }
    }

    public static boolean isEncrypted(String stored) {
        return stored != null && stored.startsWith(PREFIX);
    }

    public static boolean isProtectionAvailable() {
        return WINDOWS;
    }

    private TokenCipher() {
    }
}
