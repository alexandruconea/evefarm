package com.evefarm.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenCipherTest {

    @Test
    void secretsAreProtectedOrRejectedButNeverStoredInPlaintext() {
        if (TokenCipher.isProtectionAvailable()) {
            String encrypted = TokenCipher.encrypt("secret-value");
            assertTrue(TokenCipher.isEncrypted(encrypted));
            assertEquals("secret-value", TokenCipher.decrypt(encrypted));
        } else {
            assertThrows(IllegalStateException.class, () -> TokenCipher.encrypt("secret-value"));
            assertThrows(IllegalStateException.class, () -> TokenCipher.decrypt("secret-value"));
        }
    }
}
