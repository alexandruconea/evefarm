package com.evefarm.util;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledOnOs(OS.LINUX)
@EnabledIfEnvironmentVariable(named = "EVEFARM_KEYRING_TEST", matches = "1")
class LinuxKeyringTest {

    interface Cleanup extends Library {
        Pointer secret_schema_new(String name, int flags, Object... attributes);

        void secret_schema_unref(Pointer schema);

        boolean secret_password_clear_sync(Pointer schema, Pointer cancellable, PointerByReference error,
                                           Object... attributes);
    }

    @Test
    void theKeyIsCreatedFoundAgainAndRemovedInTheDesktopKeyring() {
        String name = "test-key-" + UUID.randomUUID();
        LinuxKeyring keyring = new LinuxKeyring(name);
        assertTrue(keyring.isAvailable(), "libsecret is installed");
        try {
            assertEquals(Optional.empty(), keyring.load());
            assertFalse(keyring.exists());

            String stored = TokenCipher.encrypt(new KeyringProtection(keyring), "refresh-token");

            assertTrue(keyring.exists());
            assertEquals(44, keyring.load().orElseThrow().length(), "a 256-bit key in Base64");
            KeyringProtection afterRestart = new KeyringProtection(new LinuxKeyring(name));
            assertEquals("refresh-token", TokenCipher.decrypt(afterRestart, stored));
        } finally {
            remove(name);
        }
        assertEquals(Optional.empty(), keyring.load());
        assertFalse(keyring.exists());
    }

    private static void remove(String name) {
        Cleanup secret = Native.load("libsecret-1.so.0", Cleanup.class);
        Pointer schema = secret.secret_schema_new("com.evefarm.Secret", 0, "application", 0, "key", 0, null);
        try {
            PointerByReference error = new PointerByReference();
            secret.secret_password_clear_sync(schema, null, error, "application", "com.evefarm", "key", name, null);
            assertNull(error.getValue(), "the test key was removed");
        } finally {
            secret.secret_schema_unref(schema);
        }
    }
}
