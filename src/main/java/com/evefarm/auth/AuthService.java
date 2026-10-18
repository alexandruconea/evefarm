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
            Instant expiresAt = Instant.now().plusSeconds(tokenResponse.expiresIn());
            tokenDao.upsert(identity.characterId(), tokenResponse.refreshToken(), tokenResponse.accessToken(), expiresAt);

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
        TokenRecord record = tokenDao.find(characterId)
                .orElseThrow(() -> new IllegalStateException("No stored token for character " + characterId));

        if (record.isAccessTokenValid()) {
            return record.accessToken();
        }

        Object refreshLock = refreshLocks.computeIfAbsent(characterId, ignored -> new Object());
        synchronized (refreshLock) {
            record = tokenDao.find(characterId)
                    .orElseThrow(() -> new IllegalStateException("No stored token for character " + characterId));
            if (record.isAccessTokenValid()) {
                return record.accessToken();
            }

            LOG.info("Refreshing access token for character " + characterId);
            TokenResponse refreshed;
            try {
                refreshed = ssoClient.refreshAccessToken(OAuthConfig.DEFAULT, record.refreshToken());
            } catch (LoginRejectedException e) {
                throw new IllegalStateException("Add " + characterName(characterId)
                        + " again (File > Characters... > Add Character...) - EVE no longer accepts its login.");
            }
            Instant expiresAt = Instant.now().plusSeconds(refreshed.expiresIn());
            tokenDao.upsert(characterId, refreshed.refreshToken(), refreshed.accessToken(), expiresAt);
            return refreshed.accessToken();
        }
    }

    private String characterName(long characterId) {
        return characterDao.listAll().stream()
                .filter(character -> character.characterId() == characterId)
                .map(EveCharacter::characterName)
                .findFirst()
                .orElse("character " + characterId);
    }
}
