package com.evefarm.util;

import com.sun.jna.platform.win32.Crypt32Util;
import com.sun.jna.platform.win32.WinCrypt;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

final class DpapiProtection implements TokenCipher.Protection {

    static final String PREFIX = "dpapi:";

    @Override
    public String prefix() {
        return PREFIX;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String unavailableMessage() {
        return "Windows DPAPI isn't available, so EVE Farm won't store or read your EVE logins.";
    }

    @Override
    public String protect(String plaintext) {
        try {
            byte[] protectedBytes = Crypt32Util.cryptProtectData(
                    plaintext.getBytes(StandardCharsets.UTF_8), TokenCipher.ENTROPY,
                    WinCrypt.CRYPTPROTECT_UI_FORBIDDEN, null, null);
            return Base64.getEncoder().encodeToString(protectedBytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt token with DPAPI - refusing to store "
                    + "it unprotected", e);
        }
    }

    @Override
    public String unprotect(String body) {
        byte[] protectedBytes = Base64.getDecoder().decode(body);
        try {
            byte[] plainBytes = Crypt32Util.cryptUnprotectData(
                    protectedBytes, TokenCipher.ENTROPY, WinCrypt.CRYPTPROTECT_UI_FORBIDDEN, null);
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
}
