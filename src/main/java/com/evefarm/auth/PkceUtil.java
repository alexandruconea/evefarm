package com.evefarm.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public final class PkceUtil {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    public static String generateCodeVerifier() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    public static String deriveCodeChallenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return URL_ENCODER.encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public static String generateState() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    private PkceUtil() {
    }
}
