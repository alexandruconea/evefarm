package com.evefarm.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordProtectionTest {

    private static final int FAST = PasswordProtection.MIN_ITERATIONS;

    @Test
    void aSecretOpensWithTheSamePasswordOnAnyComputer() {
        String sealed = new PasswordProtection("correct horse battery", FAST).protect("refresh-token");

        assertTrue(PasswordProtection.isProtected(sealed));
        assertFalse(sealed.contains("refresh-token"));
        assertEquals("refresh-token", new PasswordProtection("correct horse battery").unprotect(sealed));
    }

    @Test
    void eachSecretIsSealedDifferently() {
        PasswordProtection protection = new PasswordProtection("correct horse battery", FAST);

        assertNotEquals(protection.protect("refresh-token"), protection.protect("refresh-token"));
    }

    @Test
    void aWrongPasswordIsRefused() {
        String sealed = new PasswordProtection("correct horse battery", FAST).protect("refresh-token");

        assertThrows(WrongPasswordException.class, () -> new PasswordProtection("wrong horse").unprotect(sealed));
    }

    @Test
    void aChangedSecretIsRefused() {
        String sealed = new PasswordProtection("correct horse battery", FAST).protect("refresh-token");
        int last = sealed.length() - 3;
        String changed = sealed.substring(0, last) + (sealed.charAt(last) == 'A' ? 'B' : 'A')
                + sealed.substring(last + 1);

        assertThrows(IllegalStateException.class,
                () -> new PasswordProtection("correct horse battery").unprotect(changed));
    }

    @Test
    void aBackupCannotAskForAnEndlessKeyDerivation() {
        String sealed = new PasswordProtection("correct horse battery", FAST).protect("refresh-token");
        String endless = sealed.replace(PasswordProtection.PREFIX + FAST + ":",
                PasswordProtection.PREFIX + (PasswordProtection.MAX_ITERATIONS + 1) + ":");

        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> new PasswordProtection("correct horse battery").unprotect(endless));
        assertFalse(refused instanceof WrongPasswordException);
    }
}
