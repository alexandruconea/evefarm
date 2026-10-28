package com.evefarm.auth;

import com.evefarm.db.dao.CharacterDao;
import com.evefarm.db.dao.TokenDao;
import com.evefarm.model.EveCharacter;
import com.evefarm.model.TokenRecord;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    @Test
    void concurrentCallersShareOneTokenRefreshPerCharacter() throws Exception {
        long characterId = 42L;
        CharacterDao characters = mock(CharacterDao.class);
        TokenDao tokens = mock(TokenDao.class);
        EveSsoClient sso = mock(EveSsoClient.class);
        JwtValidator jwt = mock(JwtValidator.class);
        AuthService service = new AuthService(characters, tokens, sso, jwt);

        List<Thread> callers = new CopyOnWriteArrayList<>();
        when(tokens.find(characterId)).thenReturn(Optional.of(TokenRecord.active("old-refresh")));
        when(sso.refreshAccessToken(any(), eq("old-refresh"))).thenAnswer(invocation -> {
            awaitTheOtherCallerBlocked(callers);
            return new TokenResponse("new-access", "new-refresh", 3600);
        });
        when(tokens.saveRefreshedLogin(characterId, "new-refresh")).thenReturn(true);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<String> caller = () -> {
                callers.add(Thread.currentThread());
                return service.getValidAccessToken(characterId);
            };
            var first = executor.submit(caller);
            var second = executor.submit(caller);

            assertEquals("new-access", first.get(5, TimeUnit.SECONDS));
            assertEquals("new-access", second.get(5, TimeUnit.SECONDS));
        }

        verify(sso, times(1)).refreshAccessToken(any(), eq("old-refresh"));
        verify(tokens, times(1)).saveRefreshedLogin(characterId, "new-refresh");
    }

    private static void awaitTheOtherCallerBlocked(List<Thread> callers) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (callers.size() < 2 || callers.stream()
                .filter(thread -> thread != Thread.currentThread())
                .noneMatch(thread -> thread.getState() == Thread.State.BLOCKED)) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("the second caller never waited for the first refresh");
            }
            Thread.sleep(5);
        }
    }

    @Test
    void aLoginEveNoLongerAcceptsAsksToAddTheCharacterAgain() {
        long characterId = 2124165849L;
        CharacterDao characters = mock(CharacterDao.class);
        TokenDao tokens = mock(TokenDao.class);
        EveSsoClient sso = mock(EveSsoClient.class);
        AuthService service = new AuthService(characters, tokens, sso, mock(JwtValidator.class));
        when(characters.listAll()).thenReturn(List.of(
                new EveCharacter(characterId, "Malpais Legate", List.of(), Instant.EPOCH)));
        when(tokens.find(characterId)).thenReturn(Optional.of(TokenRecord.active("revoked")));
        when(sso.refreshAccessToken(any(), eq("revoked"))).thenThrow(new LoginRejectedException());

        LoginExpiredException error = assertThrows(LoginExpiredException.class,
                () -> service.getValidAccessToken(characterId));

        assertEquals("Add Malpais Legate again (File > Characters... > Add Character...) - EVE no longer accepts "
                + "its login.", error.getMessage());
        verify(tokens).markLoginRejected(characterId);
        verify(tokens, never()).saveRefreshedLogin(anyLong(), any());
    }

    @Test
    void aLoginEveRejectedIsNotSentToEveAgain() {
        long characterId = 2124165849L;
        CharacterDao characters = mock(CharacterDao.class);
        TokenDao tokens = mock(TokenDao.class);
        EveSsoClient sso = mock(EveSsoClient.class);
        AuthService service = new AuthService(characters, tokens, sso, mock(JwtValidator.class));
        when(characters.listAll()).thenReturn(List.of(
                new EveCharacter(characterId, "Malpais Legate", List.of(), Instant.EPOCH)));
        when(tokens.find(characterId)).thenReturn(Optional.of(TokenRecord.rejected()));

        assertThrows(LoginExpiredException.class, () -> service.getValidAccessToken(characterId));

        verify(sso, never()).refreshAccessToken(any(), any());
    }

    @Test
    void aCharacterRemovedDuringARefreshDoesNotGetATokenBack() {
        long characterId = 42L;
        TokenDao tokens = mock(TokenDao.class);
        EveSsoClient sso = mock(EveSsoClient.class);
        AuthService service = new AuthService(mock(CharacterDao.class), tokens, sso, mock(JwtValidator.class));
        when(tokens.find(characterId)).thenReturn(Optional.of(TokenRecord.active("refresh")));
        when(sso.refreshAccessToken(any(), eq("refresh"))).thenReturn(new TokenResponse("new-access", "next", 1199));
        when(tokens.saveRefreshedLogin(characterId, "next")).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> service.getValidAccessToken(characterId));
        assertThrows(IllegalStateException.class, () -> service.getValidAccessToken(characterId),
                "the access token of a removed character isn't kept either");
    }

    @Test
    void aRefreshWithoutANewRefreshTokenKeepsTheOldOne() {
        long characterId = 42L;
        TokenDao tokens = mock(TokenDao.class);
        EveSsoClient sso = mock(EveSsoClient.class);
        AuthService service = new AuthService(mock(CharacterDao.class), tokens, sso, mock(JwtValidator.class));
        when(tokens.find(characterId)).thenReturn(Optional.of(TokenRecord.active("kept-refresh")));
        when(sso.refreshAccessToken(any(), eq("kept-refresh"))).thenReturn(new TokenResponse("new-access", null, 1199));
        when(tokens.saveRefreshedLogin(characterId, "kept-refresh")).thenReturn(true);

        assertEquals("new-access", service.getValidAccessToken(characterId));

        verify(tokens).saveRefreshedLogin(characterId, "kept-refresh");
    }

    @Test
    void tokensNeverShowUpWhenPrinted() {
        String record = TokenRecord.active("refresh-secret").toString();
        String response = new TokenResponse("access-secret", "refresh-secret", 1199).toString();

        assertFalse(record.contains("secret"), record);
        assertFalse(response.contains("secret"), response);
        assertEquals("TokenResponse[expiresIn=1199]", response);
    }

    @Test
    void anAccessTokenIsKeptInMemoryUntilItExpires() {
        TokenDao tokens = mock(TokenDao.class);
        EveSsoClient sso = mock(EveSsoClient.class);
        AuthService service = new AuthService(mock(CharacterDao.class), tokens, sso, mock(JwtValidator.class));
        when(tokens.find(42L)).thenReturn(Optional.of(TokenRecord.active("refresh")));
        when(sso.refreshAccessToken(any(), eq("refresh"))).thenReturn(new TokenResponse("access", "refresh", 1199));
        when(tokens.saveRefreshedLogin(42L, "refresh")).thenReturn(true);

        assertEquals("access", service.getValidAccessToken(42L));
        assertEquals("access", service.getValidAccessToken(42L));

        verify(sso, times(1)).refreshAccessToken(any(), any());
        verify(tokens, times(1)).find(42L);
    }

    @Test
    void aShortLivedAccessTokenIsRefreshedAgain() {
        TokenDao tokens = mock(TokenDao.class);
        EveSsoClient sso = mock(EveSsoClient.class);
        AuthService service = new AuthService(mock(CharacterDao.class), tokens, sso, mock(JwtValidator.class));
        when(tokens.find(42L)).thenReturn(Optional.of(TokenRecord.active("refresh")));
        when(sso.refreshAccessToken(any(), eq("refresh"))).thenReturn(new TokenResponse("access", "refresh", 30));
        when(tokens.saveRefreshedLogin(42L, "refresh")).thenReturn(true);

        service.getValidAccessToken(42L);
        service.getValidAccessToken(42L);

        verify(sso, times(2)).refreshAccessToken(any(), any());
    }

    @Test
    void aForgottenCharacterAsksForItsLoginAgain() {
        TokenDao tokens = mock(TokenDao.class);
        EveSsoClient sso = mock(EveSsoClient.class);
        AuthService service = new AuthService(mock(CharacterDao.class), tokens, sso, mock(JwtValidator.class));
        when(tokens.find(42L)).thenReturn(Optional.of(TokenRecord.active("refresh"))).thenReturn(Optional.empty());
        when(sso.refreshAccessToken(any(), eq("refresh"))).thenReturn(new TokenResponse("access", "refresh", 1199));
        when(tokens.saveRefreshedLogin(42L, "refresh")).thenReturn(true);
        service.getValidAccessToken(42L);

        service.forget(42L);

        assertThrows(IllegalStateException.class, () -> service.getValidAccessToken(42L));
    }
}
