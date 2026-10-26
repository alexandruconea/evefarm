package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.model.TokenRecord;
import com.evefarm.util.TokenCipher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

class TokenDaoTest {

    private static final String SEALED = "sealed:";

    private Database database;
    private TokenDao tokens;
    private MockedStatic<TokenCipher> cipher;

    @BeforeEach
    void openDatabase() {
        database = new Database(":memory:");
        MigrationRunner.run(database);
        new CharacterDao(database).upsert(42L, "Malpais Legate", List.of(), null);
        tokens = new TokenDao(database);
        cipher = mockStatic(TokenCipher.class);
        cipher.when(TokenCipher::isProtectionAvailable).thenReturn(true);
        cipher.when(() -> TokenCipher.isEncrypted(anyString()))
                .thenAnswer(call -> call.<String>getArgument(0).startsWith(SEALED));
        cipher.when(() -> TokenCipher.encrypt(anyString())).thenAnswer(call -> {
            assertFalse(Thread.holdsLock(database), "encrypted while holding the database lock");
            return SEALED + call.getArgument(0);
        });
        cipher.when(() -> TokenCipher.decrypt(anyString())).thenAnswer(call -> {
            assertFalse(Thread.holdsLock(database), "decrypted while holding the database lock");
            return call.<String>getArgument(0).substring(SEALED.length());
        });
    }

    @AfterEach
    void closeDatabase() {
        cipher.close();
        database.close();
    }

    @Test
    void aLoginIsSavedAndRefreshedWithoutHoldingTheDatabaseLockForTheKeyring() {
        tokens.saveLogin(42L, "first");
        assertEquals(Optional.of(TokenRecord.active("first")), tokens.find(42L));

        assertTrue(tokens.saveRefreshedLogin(42L, "second"));
        assertEquals(Optional.of(TokenRecord.active("second")), tokens.find(42L));
    }

    @Test
    void aRejectedLoginStaysRejectedUntilTheCharacterLogsInAgain() {
        tokens.saveLogin(42L, "first");

        tokens.markLoginRejected(42L);

        assertEquals(Optional.of(TokenRecord.rejected()), tokens.find(42L));
        assertFalse(tokens.saveRefreshedLogin(42L, "late"), "a refresh doesn't undo the rejection");
        tokens.saveLogin(42L, "again");
        assertEquals(Optional.of(TokenRecord.active("again")), tokens.find(42L));
    }

    @Test
    void aRefreshDoesNotBringBackTheLoginOfARemovedCharacter() {
        tokens.saveLogin(42L, "first");
        new CharacterDao(database).remove(42L);

        assertFalse(tokens.saveRefreshedLogin(42L, "second"));
        assertEquals(Optional.empty(), tokens.find(42L));
    }

    @Test
    void plaintextLoginsFromOldVersionsAreEncrypted() throws SQLException {
        try (PreparedStatement ps = database.connection().prepareStatement("""
                INSERT INTO tokens(character_id, refresh_token, access_token, updated_at)
                VALUES (42, 'plain', 'old-access', '2026-01-01T00:00:00Z')
                """)) {
            ps.executeUpdate();
        }

        tokens.migrateLegacyPlaintextTokens();

        assertEquals(Optional.of(TokenRecord.active("plain")), tokens.find(42L));
        cipher.verify(() -> TokenCipher.encrypt("plain"));
    }
}
