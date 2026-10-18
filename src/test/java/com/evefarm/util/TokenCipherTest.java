package com.evefarm.util;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenCipherTest {

    static final class MemoryKeyStore implements KeyringProtection.KeyStore {
        boolean available = true;
        boolean locked;
        String key;
        RuntimeException failure;
        int loads;
        final List<String> saved = new ArrayList<>();

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public Optional<String> load() {
            loads++;
            if (failure != null) {
                throw failure;
            }
            return locked ? Optional.empty() : Optional.ofNullable(key);
        }

        @Override
        public boolean exists() {
            return locked && key != null;
        }

        @Override
        public void save(String key) {
            saved.add(key);
            this.key = key;
        }
    }

    @Test
    void secretsAreProtectedOrRejectedButNeverStoredInPlaintext() {
        if (TokenCipher.isProtectionAvailable() && Platform.isWindows()) {
            String encrypted = TokenCipher.encrypt("secret-value");
            assertTrue(TokenCipher.isEncrypted(encrypted));
            assertEquals("secret-value", TokenCipher.decrypt(encrypted));
        } else if (!TokenCipher.isProtectionAvailable()) {
            assertThrows(IllegalStateException.class, () -> TokenCipher.encrypt("secret-value"));
            assertThrows(IllegalStateException.class, () -> TokenCipher.decrypt("secret-value"));
        }
    }

    @Test
    void theKeyringKeyEncryptsEachSecretDifferentlyAndDecryptsIt() {
        MemoryKeyStore store = new MemoryKeyStore();
        KeyringProtection keyring = new KeyringProtection(store);

        String first = TokenCipher.encrypt(keyring, "refresh-token");
        String second = TokenCipher.encrypt(keyring, "refresh-token");

        assertTrue(first.startsWith("keyring:"));
        assertTrue(TokenCipher.isEncrypted(first));
        assertNotEquals(first, second, "every secret gets its own nonce");
        assertFalse(first.contains("refresh-token"));
        assertEquals("refresh-token", TokenCipher.decrypt(keyring, first));
        assertEquals("refresh-token", TokenCipher.decrypt(new KeyringProtection(store), second),
                "the key comes back from the keyring after a restart");
        assertEquals(1, store.saved.size(), "one key is created and then reused");
    }

    @Test
    void aSecretChangedInTheDatabaseIsRejected() {
        KeyringProtection keyring = new KeyringProtection(new MemoryKeyStore());
        String stored = TokenCipher.encrypt(keyring, "refresh-token");
        byte[] raw = Base64.getDecoder().decode(stored.substring("keyring:".length()));
        raw[20] ^= 0x01;
        String tampered = "keyring:" + Base64.getEncoder().encodeToString(raw);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> TokenCipher.decrypt(keyring, tampered));
        assertTrue(error.getMessage().contains("add the character again"));
    }

    @Test
    void aLockedKeyringIsNeverReplacedByANewKey() {
        MemoryKeyStore store = new MemoryKeyStore();
        String stored = TokenCipher.encrypt(new KeyringProtection(store), "refresh-token");
        String original = store.key;
        store.locked = true;
        KeyringProtection afterRestart = new KeyringProtection(store);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> TokenCipher.encrypt(afterRestart, "another-token"));

        assertTrue(error.getMessage().contains("locked"));
        assertEquals(original, store.key, "the key that protects the saved logins stays");
        assertEquals(1, store.saved.size());
        store.locked = false;
        afterRestart.check();
        assertEquals("refresh-token", TokenCipher.decrypt(afterRestart, stored));
    }

    @Test
    void readingNeverCreatesAKey() {
        MemoryKeyStore store = new MemoryKeyStore();
        String stored = TokenCipher.encrypt(new KeyringProtection(store), "refresh-token");
        store.key = null;
        store.saved.clear();

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> TokenCipher.decrypt(new KeyringProtection(store), stored));

        assertTrue(error.getMessage().contains("no EVE Farm key"));
        assertTrue(store.saved.isEmpty());
    }

    @Test
    void anUnreachableKeyringIsAskedAgainOnlyAfterAPause() {
        MemoryKeyStore store = new MemoryKeyStore();
        String stored = TokenCipher.encrypt(new KeyringProtection(store), "refresh-token");
        store.failure = new IllegalStateException("EVE Farm couldn't read its key in your desktop keyring");
        MutableClock clock = new MutableClock();
        KeyringProtection keyring = new KeyringProtection(store, clock);
        int loadsBefore = store.loads;

        assertThrows(IllegalStateException.class, () -> TokenCipher.decrypt(keyring, stored));
        assertThrows(IllegalStateException.class, () -> TokenCipher.decrypt(keyring, stored));
        assertEquals(1, store.loads - loadsBefore, "the second read waits instead of asking the keyring again");

        store.failure = null;
        clock.now = clock.now.plus(KeyringProtection.RETRY_AFTER);
        assertEquals("refresh-token", TokenCipher.decrypt(keyring, stored));
    }

    @Test
    void addingACharacterAsksTheKeyringRightAwayEvenAfterAFailure() {
        MemoryKeyStore store = new MemoryKeyStore();
        store.failure = new IllegalStateException("no keyring");
        KeyringProtection keyring = new KeyringProtection(store, new MutableClock());
        assertThrows(IllegalStateException.class, () -> TokenCipher.encrypt(keyring, "x"));

        store.failure = null;
        keyring.check();

        assertEquals("x", TokenCipher.decrypt(keyring, TokenCipher.encrypt(keyring, "x")));
    }

    @Test
    void withoutLibsecretNothingIsStoredOrRead() {
        MemoryKeyStore store = new MemoryKeyStore();
        store.available = false;
        KeyringProtection keyring = new KeyringProtection(store);

        assertThrows(IllegalStateException.class, () -> TokenCipher.encrypt(keyring, "secret"));
        assertThrows(IllegalStateException.class, () -> TokenCipher.decrypt(keyring, "plain-legacy-token"));
        assertThrows(IllegalStateException.class, keyring::check);
        assertTrue(keyring.unavailableMessage().contains("libsecret"));
    }

    @Test
    void aLoginSavedOnTheOtherSystemAsksToAddTheCharacterAgain() {
        KeyringProtection keyring = new KeyringProtection(new MemoryKeyStore());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> TokenCipher.decrypt(keyring, "dpapi:AQAAANCMnd8BFdERjHoAwE"));

        assertTrue(error.getMessage().contains("another computer or system"));
    }

    @Test
    void legacyPlaintextIsReadOnlyWhileProtectionWorks() {
        KeyringProtection keyring = new KeyringProtection(new MemoryKeyStore());

        assertEquals("old-plain-token", TokenCipher.decrypt(keyring, "old-plain-token"));
        assertEquals("", TokenCipher.decrypt(keyring, ""));
        assertNull(TokenCipher.decrypt(keyring, null));
        assertNull(TokenCipher.encrypt(keyring, null));
    }

    @Test
    void otherSystemsRefuseToStoreSecrets() {
        TokenCipher.Protection none = TokenCipher.forPlatform(Platform.MAC);

        assertFalse(none.isAvailable());
        assertThrows(IllegalStateException.class, () -> TokenCipher.encrypt(none, "secret"));
        assertThrows(IllegalStateException.class, () -> TokenCipher.decrypt(none, "secret"));
    }

    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-18T10:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
