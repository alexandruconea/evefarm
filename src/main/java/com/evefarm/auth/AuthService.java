package com.evefarm.auth;

import com.evefarm.db.dao.CharacterDao;
import com.evefarm.db.dao.TokenDao;
import com.evefarm.model.EveCharacter;
import com.evefarm.model.TokenRecord;
import com.evefarm.util.Browser;
import com.evefarm.util.TokenCipher;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public final class AuthService {

    private static final Logger LOG = Logger.getLogger(AuthService.class.getName());
    private static final long CALLBACK_TIMEOUT_SECONDS = 180;

    private final CharacterDao characterDao;
    private final TokenDao tokenDao;
    private final EveSsoClient ssoClient;
    private final JwtValidator jwtValidator;
    private final Map<Long, Object> refreshLocks = new ConcurrentHashMap<>();
    private final Map<Long, AccessToken> accessTokens = new ConcurrentHashMap<>();

    // Access tokens live only in memory: they last 20 minutes, so after a restart each character refreshes once.
    private record AccessToken(String token, Instant expiresAt) {
        boolean usable() {
            return expiresAt.isAfter(Instant.now().plusSeconds(60));
        }

        @Override
        public String toString() {
            return "AccessToken[expiresAt=" + expiresAt + "]";
        }
    }

    public AuthService(CharacterDao characterDao, TokenDao tokenDao) {
        this(characterDao, tokenDao, new EveSsoClient(), new JwtValidator());
    }

    AuthService(CharacterDao characterDao, TokenDao tokenDao, EveSsoClient ssoClient,
                JwtValidator jwtValidator) {
        this.characterDao = characterDao;
        this.tokenDao = tokenDao;
        this.ssoClient = ssoClient;
        this.jwtValidator = jwtValidator;
    }

    public CharacterIdentity startLoginFlow() throws Exception {
        TokenCipher.checkAvailable();
        OAuthConfig config = OAuthConfig.DEFAULT;
        String codeVerifier = PkceUtil.generateCodeVerifier();
        String codeChallenge = PkceUtil.deriveCodeChallenge(codeVerifier);
        String state = PkceUtil.generateState();

        try (LoopbackCallbackServer callbackServer = new LoopbackCallbackServer(config.redirectPort(), state)) {
            URI authorizeUrl = ssoClient.buildAuthorizeUrl(config, state, codeChallenge);
            Browser.open(authorizeUrl);

            String code = callbackServer.awaitAuthorizationCode(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            TokenResponse tokenResponse = ssoClient.exchangeAuthorizationCode(config, code, codeVerifier);
            CharacterIdentity identity = jwtValidator.validate(tokenResponse.accessToken(), config.clientId());
            checkOwnerNotChanged(identity);

            characterDao.upsert(identity.characterId(), identity.characterName(), identity.scopes(),
                    identity.ownerHash());
            tokenDao.saveLogin(identity.characterId(), tokenResponse.refreshToken());
            remember(identity.characterId(), tokenResponse);

            return identity;
        }
    }

    private void checkOwnerNotChanged(CharacterIdentity identity) {
        if (identity.ownerHash() == null) {
            return;
        }
        characterDao.findOwnerHash(identity.characterId()).ifPresent(previousHash -> {
            if (!previousHash.isBlank() && !previousHash.equals(identity.ownerHash())) {
                throw new IllegalStateException(
                        "'" + identity.characterName() + "' now belongs to a different EVE account than "
                                + "when it was first added here (it was likely transferred/sold). "
                                + "Remove it from Characters and add it again to continue tracking it "
                                + "under its new owner - its history so far is kept.");
            }
        });
    }

    public String getValidAccessToken(long characterId) {
        AccessToken cached = accessTokens.get(characterId);
        if (cached != null && cached.usable()) {
            return cached.token();
        }

        Object refreshLock = refreshLocks.computeIfAbsent(characterId, ignored -> new Object());
        synchronized (refreshLock) {
            cached = accessTokens.get(characterId);
            if (cached != null && cached.usable()) {
                return cached.token();
            }
            TokenRecord record = tokenDao.find(characterId).orElseThrow(() -> noLogin(characterId));
            if (record.loginRejected()) {
                throw new LoginExpiredException(characterName(characterId));
            }

            LOG.info("Refreshing access token for character " + characterId);
            TokenResponse refreshed;
            try {
                refreshed = ssoClient.refreshAccessToken(OAuthConfig.DEFAULT, record.refreshToken());
            } catch (LoginRejectedException e) {
                tokenDao.markLoginRejected(characterId);
                throw new LoginExpiredException(characterName(characterId));
            }
            String refreshToken = refreshed.refreshToken() == null || refreshed.refreshToken().isBlank()
                    ? record.refreshToken() : refreshed.refreshToken();
            if (!tokenDao.saveRefreshedLogin(characterId, refreshToken)) {
                throw noLogin(characterId);
            }
            remember(characterId, refreshed);
            return refreshed.accessToken();
        }
    }

    public void forget(long characterId) {
        accessTokens.remove(characterId);
    }

    private void remember(long characterId, TokenResponse response) {
        accessTokens.put(characterId,
                new AccessToken(response.accessToken(), Instant.now().plusSeconds(response.expiresIn())));
    }

    private static IllegalStateException noLogin(long characterId) {
        return new IllegalStateException("No stored token for character " + characterId);
    }

    private String characterName(long characterId) {
        return characterDao.listAll().stream()
                .filter(character -> character.characterId() == characterId)
                .map(EveCharacter::characterName)
                .findFirst()
                .orElse("character " + characterId);
    }
}
