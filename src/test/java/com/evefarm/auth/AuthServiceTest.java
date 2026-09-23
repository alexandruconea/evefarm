package com.evefarm.auth;

import com.evefarm.db.dao.CharacterDao;
import com.evefarm.db.dao.TokenDao;
import com.evefarm.model.TokenRecord;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
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

        TokenRecord expired = new TokenRecord(characterId, "old-refresh", "old-access", Instant.EPOCH);
        AtomicReference<TokenRecord> stored = new AtomicReference<>(expired);
        AtomicInteger reads = new AtomicInteger();
        CountDownLatch initialReads = new CountDownLatch(2);
        when(tokens.find(characterId)).thenAnswer(invocation -> {
            if (reads.incrementAndGet() <= 2) {
                initialReads.countDown();
                if (!initialReads.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("both callers did not read the expired token");
                }
            }
            return Optional.of(stored.get());
        });
        when(sso.refreshAccessToken(any(), eq("old-refresh")))
                .thenReturn(new TokenResponse("new-access", "new-refresh", 3600, "Bearer"));
        doAnswer(invocation -> {
            stored.set(new TokenRecord(characterId, invocation.getArgument(1), invocation.getArgument(2),
                    invocation.getArgument(3)));
            return null;
        }).when(tokens).upsert(eq(characterId), any(), any(), any());

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> service.getValidAccessToken(characterId));
            var second = executor.submit(() -> service.getValidAccessToken(characterId));

            assertEquals("new-access", first.get(5, TimeUnit.SECONDS));
            assertEquals("new-access", second.get(5, TimeUnit.SECONDS));
        }

        verify(sso, times(1)).refreshAccessToken(any(), eq("old-refresh"));
        verify(tokens, times(1)).upsert(eq(characterId), eq("new-refresh"), eq("new-access"), any());
    }

    @Test
    void validTokenDoesNotCallTheSsoEndpoint() {
        TokenDao tokens = mock(TokenDao.class);
        EveSsoClient sso = mock(EveSsoClient.class);
        AuthService service = new AuthService(mock(CharacterDao.class), tokens, sso, mock(JwtValidator.class));
        when(tokens.find(anyLong())).thenReturn(Optional.of(
                new TokenRecord(42L, "refresh", "access", Instant.now().plusSeconds(3600))));

        assertEquals("access", service.getValidAccessToken(42L));

        verify(sso, times(0)).refreshAccessToken(any(), any());
    }
}
