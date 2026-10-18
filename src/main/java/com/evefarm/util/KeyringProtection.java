package com.evefarm.util;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

final class KeyringProtection implements TokenCipher.Protection {

    interface KeyStore {
        boolean isAvailable();

        Optional<String> load();

        boolean exists();

        void save(String key);
    }

    static final String PREFIX = "keyring:";
    static final Duration RETRY_AFTER = Duration.ofMinutes(1);
    private static final Logger LOG = Logger.getLogger(KeyringProtection.class.getName());
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String ADD_AGAIN = " - add the character again (File > Characters... > Add Character...).";
    private static final String DAMAGED_KEY = "The EVE Farm key in your keyring is damaged. Delete \"EVE Farm login "
            + "key\" in your keyring app (for example Passwords and Keys or KWallet Manager), then add your "
            + "characters again.";

    private final KeyStore keyStore;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private SecretKeySpec key;
    private IllegalStateException lastFailure;
    private Instant retryAt = Instant.MIN;

    KeyringProtection(KeyStore keyStore) {
        this(keyStore, Clock.systemUTC());
    }

    KeyringProtection(KeyStore keyStore, Clock clock) {
        this.keyStore = keyStore;
        this.clock = clock;
    }

    @Override
    public String prefix() {
        return PREFIX;
    }

    @Override
    public boolean isAvailable() {
        return keyStore.isAvailable();
    }

    @Override
    public String unavailableMessage() {
        return "EVE Farm keeps your EVE logins in your desktop keyring, but libsecret isn't installed. Install "
                + "libsecret and a keyring such as GNOME Keyring or KWallet, then start EVE Farm again.";
    }

    @Override
    public String protect(String plaintext) {
        SecretKeySpec current = key(true, false);
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, current, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(TokenCipher.ENTROPY);
            byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] stored = new byte[NONCE_BYTES + sealed.length];
            System.arraycopy(nonce, 0, stored, 0, NONCE_BYTES);
            System.arraycopy(sealed, 0, stored, NONCE_BYTES, sealed.length);
            return Base64.getEncoder().encodeToString(stored);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Couldn't encrypt a secret with the keyring key", e);
        }
    }

    @Override
    public String unprotect(String body) {
        byte[] stored;
        try {
            stored = Base64.getDecoder().decode(body);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("A saved login is damaged" + ADD_AGAIN, e);
        }
        if (stored.length <= NONCE_BYTES) {
            throw new IllegalStateException("A saved login is damaged" + ADD_AGAIN);
        }
        SecretKeySpec current = key(false, false);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, current, new GCMParameterSpec(TAG_BITS, stored, 0, NONCE_BYTES));
            cipher.updateAAD(TokenCipher.ENTROPY);
            byte[] plain = cipher.doFinal(stored, NONCE_BYTES, stored.length - NONCE_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (AEADBadTagException e) {
            throw new IllegalStateException("A saved login doesn't match the key in your keyring" + ADD_AGAIN, e);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Couldn't decrypt a saved login", e);
        }
    }

    @Override
    public void check() {
        TokenCipher.Protection.super.check();
        key(true, true);
    }

    @Override
    public void preload() {
        if (!isAvailable()) {
            return;
        }
        try {
            key(false, true);
        } catch (IllegalStateException e) {
            LOG.log(Level.INFO, "The keyring key isn't loaded yet: " + e.getMessage());
        }
    }

    private synchronized SecretKeySpec key(boolean createIfMissing, boolean askedByUser) {
        if (key != null) {
            return key;
        }
        if (!keyStore.isAvailable()) {
            throw new IllegalStateException(unavailableMessage());
        }
        if (!askedByUser && lastFailure != null && clock.instant().isBefore(retryAt)) {
            throw new IllegalStateException(lastFailure.getMessage(), lastFailure);
        }
        try {
            key = decode(loadOrCreate(createIfMissing));
            lastFailure = null;
            return key;
        } catch (IllegalStateException e) {
            lastFailure = e;
            retryAt = clock.instant().plus(RETRY_AFTER);
            throw e;
        }
    }

    private String loadOrCreate(boolean createIfMissing) {
        Optional<String> stored = keyStore.load();
        if (stored.isPresent()) {
            return stored.get();
        }
        if (keyStore.exists()) {
            throw new IllegalStateException("Your keyring is locked, so EVE Farm can't read your EVE logins. "
                    + "Unlock it, or allow EVE Farm when your desktop asks, and try again.");
        }
        if (!createIfMissing) {
            throw new IllegalStateException("Your keyring has no EVE Farm key, so saved logins can't be read"
                    + ADD_AGAIN);
        }
        byte[] fresh = new byte[KEY_BYTES];
        random.nextBytes(fresh);
        keyStore.save(Base64.getEncoder().encodeToString(fresh));
        LOG.info("Created the EVE Farm key in the desktop keyring");
        return keyStore.load().orElseThrow(() -> new IllegalStateException(
                "Your keyring didn't keep EVE Farm's key. Check that it is unlocked and try again."));
    }

    private static SecretKeySpec decode(String encoded) {
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(encoded.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(DAMAGED_KEY, e);
        }
        if (raw.length != KEY_BYTES) {
            throw new IllegalStateException(DAMAGED_KEY);
        }
        return new SecretKeySpec(raw, "AES");
    }
}
