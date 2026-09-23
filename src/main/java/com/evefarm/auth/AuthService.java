package com.evefarm.auth;

import com.evefarm.db.dao.CharacterDao;
import com.evefarm.db.dao.TokenDao;
import com.evefarm.model.TokenRecord;

import java.awt.Desktop;
import java.net.URI;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public final class AuthService {

    private static final Logger LOG = Logger.getLogger(AuthService.class.getName());
    private static final long CALLBACK_TIMEOUT_SECONDS = 180;

    private final CharacterDao characterDao;
    private final TokenDao tokenDao;
    private final EveSsoClient ssoClient = new EveSsoClient();
    private final JwtValidator jwtValidator = new JwtValidator();

    public AuthService(CharacterDao characterDao, TokenDao tokenDao) {
        this.characterDao = characterDao;
        this.tokenDao = tokenDao;
    }

    public CharacterIdentity startLoginFlow() throws Exception {
        OAuthConfig config = OAuthConfig.DEFAULT;
        String codeVerifier = PkceUtil.generateCodeVerifier();
        String codeChallenge = PkceUtil.deriveCodeChallenge(codeVerifier);
        String state = PkceUtil.generateState();

        try (LoopbackCallbackServer callbackServer = new LoopbackCallbackServer(config.redirectPort(), state)) {
            URI authorizeUrl = ssoClient.buildAuthorizeUrl(config, state, codeChallenge);
            openBrowser(authorizeUrl);

            String code = callbackServer.awaitAuthorizationCode(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            TokenResponse tokenResponse = ssoClient.exchangeAuthorizationCode(config, code, codeVerifier);
            CharacterIdentity identity = jwtValidator.validate(tokenResponse.accessToken(), config.clientId());
            checkOwnerNotChanged(identity);

            characterDao.upsert(identity.characterId(), identity.characterName(), null, identity.scopes(),
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
        OAuthConfig config = OAuthConfig.DEFAULT;
        TokenRecord record = tokenDao.find(characterId)
                .orElseThrow(() -> new IllegalStateException("No stored token for character " + characterId));

        if (record.isAccessTokenValid()) {
            return record.accessToken();
        }

        LOG.info("Refreshing access token for character " + characterId);
        TokenResponse refreshed = ssoClient.refreshAccessToken(config, record.refreshToken());
        Instant expiresAt = Instant.now().plusSeconds(refreshed.expiresIn());
        tokenDao.upsert(characterId, refreshed.refreshToken(), refreshed.accessToken(), expiresAt);
        return refreshed.accessToken();
    }

    private void openBrowser(URI uri) throws Exception {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(uri);
        } else {
            throw new IllegalStateException("Cannot open a browser automatically; please open manually: " + uri);
        }
    }
}
