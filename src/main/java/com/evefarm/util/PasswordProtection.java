package com.evefarm.util;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public final class PasswordProtection {

    static final String PREFIX = "password:";
    static final int ITERATIONS = 600_000;
    static final int MIN_ITERATIONS = 1_000;
    static final int MAX_ITERATIONS = 10_000_000;
    private static final String KEY_DERIVATION = "PBKDF2WithHmacSHA256";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int SALT_BYTES = 16;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BITS = 256;
    private static final byte[] PURPOSE = "EVEFarm.BackupSecrets.v1".getBytes(StandardCharsets.UTF_8);
    private static final String DAMAGED = "A login in the backup is damaged and can't be restored.";

    private final char[] password;
    private final int iterations;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, SecretKeySpec> keys = new HashMap<>();
    private String salt;

    public PasswordProtection(String password) {
        this(password, ITERATIONS);
    }

    PasswordProtection(String password, int iterations) {
        this.password = password.toCharArray();
        this.iterations = iterations;
    }

    public static boolean isProtected(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    public String protect(String plaintext) {
        if (salt == null) {
            byte[] fresh = new byte[SALT_BYTES];
            random.nextBytes(fresh);
            salt = Base64.getEncoder().encodeToString(fresh);
        }
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key(iterations, salt), new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(PURPOSE);
            byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] stored = new byte[NONCE_BYTES + sealed.length];
            System.arraycopy(nonce, 0, stored, 0, NONCE_BYTES);
            System.arraycopy(sealed, 0, stored, NONCE_BYTES, sealed.length);
            return PREFIX + iterations + ":" + salt + ":" + Base64.getEncoder().encodeToString(stored);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Couldn't protect a secret with the backup password", e);
        }
    }

    public String unprotect(String value) {
        if (!isProtected(value)) {
            throw new IllegalStateException(DAMAGED);
        }
        String[] parts = value.substring(PREFIX.length()).split(":", -1);
        if (parts.length != 3) {
            throw new IllegalStateException(DAMAGED);
        }
        int rounds;
        byte[] stored;
        try {
            rounds = Integer.parseInt(parts[0]);
            Base64.getDecoder().decode(parts[1]);
            stored = Base64.getDecoder().decode(parts[2]);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(DAMAGED, e);
        }
        if (rounds < MIN_ITERATIONS || rounds > MAX_ITERATIONS || stored.length <= NONCE_BYTES) {
            throw new IllegalStateException(DAMAGED);
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key(rounds, parts[1]), new GCMParameterSpec(TAG_BITS, stored, 0,
                    NONCE_BYTES));
            cipher.updateAAD(PURPOSE);
            byte[] plain = cipher.doFinal(stored, NONCE_BYTES, stored.length - NONCE_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (AEADBadTagException e) {
            throw new WrongPasswordException();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Couldn't open a secret with the backup password", e);
        }
    }

    private SecretKeySpec key(int rounds, String encodedSalt) throws GeneralSecurityException {
        String cacheKey = rounds + ":" + encodedSalt;
        SecretKeySpec cached = keys.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        PBEKeySpec spec = new PBEKeySpec(password, Base64.getDecoder().decode(encodedSalt), rounds, KEY_BITS);
        try {
            byte[] derived = SecretKeyFactory.getInstance(KEY_DERIVATION).generateSecret(spec).getEncoded();
            SecretKeySpec key = new SecretKeySpec(derived, "AES");
            keys.put(cacheKey, key);
            return key;
        } finally {
            spec.clearPassword();
        }
    }
}
